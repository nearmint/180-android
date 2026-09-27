package fr.thermostat6.app180.data.favorites

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.thermostat6.app180.data.analytics.AnalyticsService
import fr.thermostat6.app180.data.analytics.UmamiEvents
import fr.thermostat6.app180.data.analytics.UmamiParams
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.model.AddFavoriteRequest
import fr.thermostat6.app180.data.model.SyncFavoriteItem
import fr.thermostat6.app180.data.model.SyncFavoritesRequest
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.util.HapticFeedbackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Extension de contexte pour le DataStore des favoris
private val Context.favoritesDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "favorites")

/**
 * Carnet synchronisé serveur (`180c/v1/favorites`) avec cache local hors-ligne.
 *
 * - Source de vérité : le serveur dès que l'utilisateur est connecté.
 * - DataStore : cache optimiste, conservé hors-ligne.
 *
 * Le carnet est un **service de compte** : hors session il n'existe aucun
 * destinataire pour un favori, et toute écriture est refusée
 * (miroir `apple/180/FavoritesManager.swift:33-37`).
 *
 * Initialiser via [init] dans Application.onCreate().
 */
object FavoritesManager {

    /**
     * Clé courante du carnet local.
     *
     * Le suffixe de version est délibéré : la clé historique
     * [LEGACY_FAVORITE_IDS_KEY] contenait des IDs du modèle `posts`, rendus
     * immappables par la migration vers le CPT `recipe`. Les pousser
     * vers `/favorites/sync` polluerait la table de production partagée avec
     * l'iOS et le site — ils sont donc purgés sans jamais être lus (§[purgeLegacyStoreIfNeeded]).
     */
    private val FAVORITE_IDS_KEY = stringSetPreferencesKey("favorite_recipe_ids_v2")

    /** Clé historique — supprimée au premier lancement, jamais lue comme source d'IDs. */
    private val LEGACY_FAVORITE_IDS_KEY = stringSetPreferencesKey("favorite_recipe_ids")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var dataStore: DataStore<Preferences>

    private val api get() = ApiClient.userApi

    /** Horodatage ISO 8601 UTC, format attendu par `/sync` (FavoritesManager.swift:88). */
    private val iso8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ── État observable ───────────────────────────────────────────────────────

    /**
     * Émis lorsqu'un favori est ajouté par une **action utilisateur** — jamais
     * lors d'une synchronisation serveur.
     *
     * Miroir du `PassthroughSubject` `didAddFavorite`
     * (`apple/180/FavoritesManager.swift:19-21`). Côté iOS il a **deux**
     * consommateurs : le soft-ask push et la couche analytics.
     *
     * `extraBufferCapacity = 1` : l'émission ne suspend jamais l'appelant même
     * sans collecteur actif.
     */
    private val _didAddFavorite = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val didAddFavorite: SharedFlow<Int> = _didAddFavorite.asSharedFlow()

    /**
     * Émis lorsqu'un favori est retiré par une **action utilisateur** — jamais
     * lors d'une synchronisation serveur.
     *
     * Symétrique de [didAddFavorite] (`apple/180/FavoritesManager.swift:28,54`).
     * Sans lui, le carnet hors ligne ne pourrait pas refléter un retrait
     * immédiatement : la fiche resterait sur disque jusqu'au prochain cycle de
     * réconciliation complet, donc potentiellement jusqu'au prochain lancement,
     * et jamais du tout si l'utilisateur est hors connexion.
     *
     * `extraBufferCapacity = 1` : l'émission ne suspend jamais l'appelant même
     * sans collecteur actif.
     */
    private val _didRemoveFavorite = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val didRemoveFavorite: SharedFlow<Int> = _didRemoveFavorite.asSharedFlow()

    private val _favoriteIds = MutableStateFlow<Set<Int>>(emptySet())

    /**
     * Set des IDs de recettes favorites.
     * À collecter dans un ViewModel ou un Composable via collectAsStateWithLifecycle().
     */
    val favoriteIds: StateFlow<Set<Int>> = _favoriteIds.asStateFlow()

    private val isLoggedIn: Boolean get() = ApiClient.authToken != null

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * À appeler dans Application.onCreate() après HapticFeedbackManager.init()
     * et AuthService.init() (le jeton doit être restauré avant tout appel serveur).
     */
    fun init(context: Context) {
        dataStore = context.applicationContext.favoritesDataStore
        scope.launch {
            purgeLegacyStoreIfNeeded()
            observeDataStore()
        }
    }

    /**
     * Supprime le carnet legacy au premier passage.
     *
     * L'ancienne clé n'est jamais relue comme source d'IDs : elle est effacée,
     * point. Un ID `posts` envoyé à `/favorites/sync` créerait une entrée
     * fantôme dans la table serveur, visible depuis l'iOS et le site.
     */
    private suspend fun purgeLegacyStoreIfNeeded() {
        dataStore.edit { prefs ->
            if (prefs.contains(LEGACY_FAVORITE_IDS_KEY)) {
                prefs.remove(LEGACY_FAVORITE_IDS_KEY)
            }
        }
    }

    /** Écoute en continu le DataStore et met à jour le StateFlow. */
    private suspend fun observeDataStore() {
        dataStore.data.collect { prefs ->
            _favoriteIds.value = prefs.readFavoriteIds()
        }
    }

    private fun Preferences.readFavoriteIds(): Set<Int> =
        this[FAVORITE_IDS_KEY]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    // ── API publique ──────────────────────────────────────────────────────────

    /**
     * Ajoute ou retire une recette du carnet.
     *
     * Hors session : **no-op silencieux**. L'UI désactive déjà le bouton ; ce
     * garde-fou couvre les autres chemins d'appel (FavoritesManager.swift:34-37).
     *
     * En session : écriture locale optimiste + haptic, puis propagation serveur
     * best-effort. Un échec réseau conserve l'état local, réconcilié au prochain
     * sync — pas de rollback (FavoritesManager.swift:51-64).
     *
     * @param slug slug WordPress de la recette, utilisé comme `recipe_id` dans
     *   l'event Umami `recipe_favorite` (parité iOS,
     *   `FavoritesManager.swift:40-43`). Repli sur l'identifiant numérique quand
     *   il est absent — cas des appelants qui ne disposent que de l'id.
     */
    suspend fun toggle(recipeId: Int, title: String = "", slug: String? = null) {
        if (!isLoggedIn) return

        val isAdding = recipeId !in _favoriteIds.value

        dataStore.edit { prefs ->
            val current = prefs[FAVORITE_IDS_KEY] ?: emptySet()
            val idStr = recipeId.toString()
            prefs[FAVORITE_IDS_KEY] = if (idStr in current) current - idStr else current + idStr
        }
        HapticFeedbackManager.light()

        // Miroir FavoritesManager.swift:40-47 : l'ajout nourrit **deux**
        // consommateurs — le soft-ask push et l'analytics.
        if (isAdding) {
            AnalyticsService.addFavorite(id = recipeId, title = title)
            // Seul l'AJOUT est mesuré côté Umami (pas le retrait), par parité
            // avec le web et l'iOS (`FavoritesManager.swift:40-43`).
            UmamiTracker.trackEvent(
                UmamiEvents.RECIPE_FAVORITE,
                mapOf(
                    UmamiParams.RECIPE_ID to (slug?.takeIf { it.isNotEmpty() } ?: recipeId.toString())
                )
            )
            _didAddFavorite.tryEmit(recipeId)
        } else {
            AnalyticsService.removeFavorite(id = recipeId, title = title)
            _didRemoveFavorite.tryEmit(recipeId)
        }

        runCatching {
            if (isAdding) {
                api.addFavorite(AddFavoriteRequest(recipeId = recipeId))
            } else {
                api.deleteFavorite(recipeId)
            }
        }
    }

    /** Retourne true si la recette est dans le carnet (lecture synchrone du StateFlow). */
    fun isFavorite(recipeId: Int): Boolean = recipeId in _favoriteIds.value

    /**
     * Rafraîchit depuis le serveur (au lancement, si connecté).
     * Hors-ligne ou en échec : le cache local est conservé.
     * Miroir `FavoritesManager.swift:73-82`.
     */
    suspend fun refreshFromServer() {
        if (!isLoggedIn) return
        runCatching { api.getFavorites().ids }
            .onSuccess { persist(it.toSet()) }
    }

    /**
     * À la connexion : pousse le carnet local puis adopte l'état serveur
     * réconcilié (union last-write-wins).
     * Miroir `FavoritesManager.swift:86-99`.
     */
    suspend fun syncOnLogin() {
        if (!isLoggedIn) return
        val now = iso8601.format(Date())
        val items = _favoriteIds.value.map { id ->
            SyncFavoriteItem(recipeId = id, favorited = true, updatedAt = now)
        }
        runCatching { api.syncFavorites(SyncFavoritesRequest(favorites = items)).ids }
            .onSuccess { persist(it.toSet()) }
    }

    /**
     * Vide le carnet local à la déconnexion.
     *
     * Le carnet est un service **de compte**. Ses IDs locaux ne doivent pas
     * survivre à la session : sur un appareil partagé, le login suivant les
     * pousserait dans le carnet du compte entrant via [syncOnLogin], dont la
     * réconciliation est une union last-write-wins.
     *
     * Comportement **identique à l'iOS**, qui purge au logout avec le même
     * raisonnement (`apple/180/AuthService.swift:419-422`).
     */
    suspend fun clearLocal() {
        dataStore.edit { prefs -> prefs.remove(FAVORITE_IDS_KEY) }
    }

    private suspend fun persist(ids: Set<Int>) {
        dataStore.edit { prefs ->
            prefs[FAVORITE_IDS_KEY] = ids.map { it.toString() }.toSet()
        }
    }
}
