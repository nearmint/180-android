package fr.thermostat6.app180.data.offline

import android.util.Log
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.data.favorites.FavoritesManager
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RecipePaging
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.ApiError
import fr.thermostat6.app180.data.network.NetworkMonitor
import fr.thermostat6.app180.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.coroutineContext

/**
 * Téléchargement et maintien à jour des fiches du carnet consultables hors ligne.
 *
 * ## Cycle de vie
 *
 * - **Activation du toggle** : cycle complet (liste serveur → sonde → diff →
 *   téléchargement séquentiel), progression observable.
 * - **Incrémental** : un favori ajouté est téléchargé aussitôt, un favori retiré
 *   est supprimé aussitôt — y compris hors ligne, la suppression étant locale.
 * - **Réconciliation** au lancement et au retour du réseau, si le toggle est
 *   actif et l'utilisateur éligible.
 *
 * ## Garde-fous
 *
 * - **Éligibilité revérifiée au démarrage de chaque cycle** : logué *et* abonné.
 *   Ce n'est pas qu'une règle produit — le serveur retire ingrédients et étapes
 *   des recettes verrouillées (`recipe_locked`), donc une fiche téléchargée hors
 *   abonnement serait une coquille vide. Une fiche verrouillée est d'ailleurs
 *   refusée à l'écriture, même si tout le reste passait.
 * - **Jamais bloquant** : tout se joue dans une tâche d'arrière-plan annulable ;
 *   un échec individuel ne vide pas la file, il sera repris au cycle suivant (la
 *   fiche non écrite réapparaît en « absente » dans le plan).
 *
 * Miroir de `OfflineSyncService` (`apple/180/OfflineSyncService.swift`).
 */
object OfflineSyncService {

    private const val TAG = "OfflineSync"

    /** Progression d'un cycle. */
    sealed interface SyncState {
        data object Idle : SyncState
        /** Cycle en cours : [done] fiches traitées sur [total]. */
        data class Running(val done: Int, val total: Int) : SyncState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isEnabled = MutableStateFlow(false)

    /**
     * Toggle utilisateur. Persisté, remis à `false` à la déconnexion et à la
     * perte d'abonnement — **jamais** sur une simple absence de réseau.
     */
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)

    /** Taille occupée, rafraîchie après chaque cycle et à la demande de l'UI. */
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private var syncJob: Job? = null

    /** Client dédié aux visuels : publics, servis par le CDN, aucun jeton à joindre. */
    private val imageClient: OkHttpClient by lazy { OkHttpClient() }

    private val store: OfflineStore? get() = OfflineStore.sharedOrNull

    // ── Initialisation ───────────────────────────────────────────────────────

    /** À appeler dans `Application.onCreate()`, après [AppPreferences.init]. */
    fun init() {
        scope.launch {
            _isEnabled.value = AppPreferences.offlineRecipesEnabled.first()
            refreshCacheSize()
        }
        observeFavorites()
    }

    // ── Éligibilité ──────────────────────────────────────────────────────────

    /**
     * Logué **et** abonné. Revérifié au démarrage de chaque cycle : un statut
     * peut tomber pendant qu'une file de téléchargement s'écoule.
     */
    private val isEligible: Boolean
        get() = AuthService.isLoggedIn.value && AuthService.isSubscriber.value

    // ── Toggle ───────────────────────────────────────────────────────────────

    /**
     * Bascule la fonctionnalité. `true` déclenche un cycle complet, `false`
     * annule toute synchronisation en cours et purge le disque.
     */
    fun setEnabled(enabled: Boolean) {
        if (enabled == _isEnabled.value) return

        if (enabled) {
            if (!isEligible) return
            _isEnabled.value = true
            scope.launch { AppPreferences.setOfflineRecipesEnabled(true) }
            startCycle()
        } else {
            _isEnabled.value = false
            scope.launch { AppPreferences.setOfflineRecipesEnabled(false) }
            purge()
        }
    }

    /**
     * Annule la synchronisation et vide le store, **sans toucher au toggle**.
     * C'est le bouton « Vider le cache » : l'utilisateur récupère de la place
     * sans renoncer à la fonctionnalité.
     */
    fun purge() {
        syncJob?.cancel()
        syncJob = null
        _state.value = SyncState.Idle
        scope.launch {
            store?.deleteAll()
            _cacheSizeBytes.value = 0L
        }
    }

    /** Purge **et** extinction du toggle : déconnexion, fin d'abonnement. */
    fun disableAndPurge() {
        _isEnabled.value = false
        scope.launch { AppPreferences.setOfflineRecipesEnabled(false) }
        purge()
    }

    /**
     * Fin d'abonnement constatée lors d'un rafraîchissement **en ligne** du
     * statut (`/180c/v1/me`).
     *
     * Jamais déclenché hors connexion : aucune horloge locale ne décide de la
     * fin d'un abonnement, sans quoi un utilisateur à jour perdrait son carnet
     * dans un train.
     *
     * Émet sur [subscriptionLostNotice] uniquement si quelque chose a
     * effectivement été purgé : il n'y a rien à dire à qui n'avait rien
     * téléchargé.
     */
    fun handleSubscriptionLost() {
        if (!_isEnabled.value) return
        disableAndPurge()
        _subscriptionLostNotice.tryEmit(SUBSCRIPTION_LOST_MESSAGE)
    }

    /** Libellé de l'information discrète affichée à la fin d'un abonnement. */
    const val SUBSCRIPTION_LOST_MESSAGE =
        "Votre abonnement a pris fin : les recettes téléchargées ont été supprimées."

    private val _subscriptionLostNotice = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /**
     * Information à présenter — un toast, pas une alerte bloquante :
     * l'utilisateur n'a rien à décider ici.
     *
     * Passer par un flux plutôt qu'appeler `ToastManager` d'ici garde la couche
     * données ignorante de l'UI ; c'est la navigation qui présente.
     */
    val subscriptionLostNotice: SharedFlow<String> = _subscriptionLostNotice.asSharedFlow()

    // ── Déclencheurs ─────────────────────────────────────────────────────────

    /** Réconciliation au lancement et au retour du réseau. */
    fun reconcileIfNeeded() {
        if (!_isEnabled.value || !isEligible || !NetworkMonitor.isConnected.value) return
        startCycle()
    }

    /** Lance un cycle, en remplaçant celui éventuellement en cours. */
    private fun startCycle() {
        syncJob?.cancel()
        syncJob = scope.launch {
            runCycle()
            syncJob = null
        }
    }

    /** Recalcule la taille occupée (écran compte). */
    fun refreshCacheSize() {
        scope.launch { _cacheSizeBytes.value = store?.totalSizeBytes() ?: 0L }
    }

    // ── Synchronisation incrémentale ─────────────────────────────────────────

    private fun observeFavorites() {
        scope.launch {
            FavoritesManager.didAddFavorite.collect { id -> favoriteAdded(id) }
        }
        scope.launch {
            FavoritesManager.didRemoveFavorite.collect { id -> favoriteRemoved(id) }
        }
    }

    private fun favoriteAdded(id: Int) {
        if (!_isEnabled.value || !isEligible || !NetworkMonitor.isConnected.value) return
        // Tâche isolée : elle ne doit ni annuler ni être annulée par un cycle
        // complet en cours.
        scope.launch {
            download(id)
            _cacheSizeBytes.value = store?.totalSizeBytes() ?: 0L
        }
    }

    private fun favoriteRemoved(id: Int) {
        if (!_isEnabled.value) return
        // Aucune condition de réseau : retirer du disque est une opération
        // locale, et le carnet hors ligne doit refléter le geste immédiatement.
        scope.launch {
            store?.delete(id)
            _cacheSizeBytes.value = store?.totalSizeBytes() ?: 0L
        }
    }

    // ── Cycle complet ────────────────────────────────────────────────────────

    private suspend fun runCycle() {
        val store = this.store ?: return
        if (!_isEnabled.value || !isEligible || !NetworkMonitor.isConnected.value) return

        val serverIds = runCatching { ApiClient.userApi.getFavorites().ids }.getOrElse {
            Log.e(TAG, "[Offline] liste des favoris indisponible — cycle abandonné")
            return
        }

        // La sonde est un confort : si elle échoue, on considère toutes les
        // dates serveur inconnues (règle 4 → on conserve l'existant, on
        // télécharge les manquants). Jamais de purge sur une panne de sonde.
        val serverModified = fetchModifiedDates(serverIds)

        val plan = OfflineSyncPlanner.plan(
            serverIds      = serverIds,
            serverModified = serverModified,
            local          = store.allMetadata()
        )
        Log.i(
            TAG,
            "[Offline] plan : ${plan.toDownload.size} à télécharger, " +
                "${plan.toDelete.size} à supprimer, ${plan.upToDate.size} à jour"
        )

        plan.toDelete.forEach { store.delete(it) }

        val total = plan.toDownload.size
        if (total == 0) {
            _state.value = SyncState.Idle
            _cacheSizeBytes.value = store.totalSizeBytes()
            return
        }

        _state.value = SyncState.Running(done = 0, total = total)
        for ((index, id) in plan.toDownload.withIndex()) {
            coroutineContext.ensureActive()
            // Le statut peut tomber pendant que la file s'écoule. On **sort** :
            // poursuivre publierait une progression mensongère (`done` avance
            // alors que plus rien n'est téléchargé). Miroir du `break` iOS
            // (`apple/180/OfflineSyncService.swift:300`).
            if (!isEligible) break
            download(id)
            _state.value = SyncState.Running(done = index + 1, total = total)
        }
        _state.value = SyncState.Idle
        _cacheSizeBytes.value = store.totalSizeBytes()
    }

    /**
     * Dates de dernière modification des recettes demandées, sans leur contenu.
     *
     * Paginé par [RecipePaging.MAX_PER_PAGE] : au-delà de 100, `wp/v2`
     * répond 400. Une page en échec est simplement absente du résultat — les
     * fiches concernées seront traitées comme « date inconnue ».
     */
    private suspend fun fetchModifiedDates(ids: List<Int>): Map<Int, String> {
        if (ids.isEmpty()) return emptyMap()
        val out = mutableMapOf<Int, String>()
        RecipePaging.chunk(ids).forEach { page ->
            runCatching {
                ApiClient.wordPressApi.getRecipeFreshness(
                    include = page.joinToString(","),
                    perPage = page.size
                )
            }.onSuccess { entries ->
                entries.forEach { entry -> entry.modified?.let { out[entry.id] = it } }
            }.onFailure {
                Log.w(TAG, "[Offline] sonde de fraîcheur en échec : ${ApiError.from(it).description}")
            }
        }
        return out
    }

    // ── Téléchargement d'une fiche ───────────────────────────────────────────

    /**
     * Télécharge une fiche et ses visuels. Un échec est journalisé et **n'écrit
     * rien** : la fiche restera « absente » et sera reprise au cycle suivant.
     */
    private suspend fun download(id: Int) {
        val store = this.store ?: return
        val recipe: Recipe = runCatching {
            ApiClient.wordPressApi.getRecipes(include = id.toString(), perPage = 1).firstOrNull()
        }.getOrElse {
            Log.e(TAG, "[Offline] téléchargement de $id échoué : ${ApiError.from(it).description}")
            return
        } ?: run {
            Log.e(TAG, "[Offline] recette $id introuvable côté serveur")
            return
        }

        // Filet de sécurité : une fiche verrouillée arrive sans ingrédients ni
        // étapes (gating serveur). L'écrire produirait un carnet hors ligne
        // rempli de coquilles vides.
        if (recipe.isLocked) {
            Log.e(TAG, "[Offline] recette $id verrouillée — non téléchargée")
            return
        }

        val images = mutableMapOf<String, ByteArray>()
        OfflineImageExtractor.imageUrls(recipe).forEach { url ->
            coroutineContext.ensureActive()
            fetchImageBytes(url)?.let { images[url] = it }
        }

        store.save(recipe, images)
    }

    /**
     * Octets d'une image, ou `null` si indisponible. Une image manquante
     * n'empêche jamais l'enregistrement de la fiche.
     */
    private suspend fun fetchImageBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            imageClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.bytes()?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }
}
