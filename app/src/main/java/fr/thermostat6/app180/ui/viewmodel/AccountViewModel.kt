package fr.thermostat6.app180.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.BuildConfig
import fr.thermostat6.app180.data.analytics.AnalyticsService
import fr.thermostat6.app180.data.preferences.AppPreferences
import fr.thermostat6.app180.data.push.PushPermissionCoordinator
import fr.thermostat6.app180.data.push.PushState
import fr.thermostat6.app180.data.service.NewsletterException
import fr.thermostat6.app180.data.service.NewsletterService
import fr.thermostat6.app180.ui.components.ToastManager
import fr.thermostat6.app180.ui.components.ToastType
import fr.thermostat6.app180.util.HapticFeedbackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel de l'onglet Compte.
 *
 * - Observe [AuthService] pour l'état de connexion et les infos profil.
 * - Gère le toggle newsletter (réservé abonnés) via le proxy WP [NewsletterService].
 * - Lit/écrit [AppPreferences.appearanceMode] (0=auto, 1=clair, 2=sombre).
 *
 * Initialiser [AuthService] et [AppPreferences] dans Application.onCreate()
 * avant d'instancier ce ViewModel.
 */
class AccountViewModel(application: Application) : AndroidViewModel(application) {

    // ── Auth (délégué à AuthService) ──────────────────────────────────────────

    val isLoggedIn:   StateFlow<Boolean> = AuthService.isLoggedIn
    val isSubscriber: StateFlow<Boolean> = AuthService.isSubscriber

    /** Jeton courant, pour construire un lien web auto-connecté. */
    fun currentToken(): String? = fr.thermostat6.app180.data.network.ApiClient.authToken
    val username:     StateFlow<String>  = AuthService.username
    val email:        StateFlow<String>  = AuthService.email
    val firstName:    StateFlow<String>  = AuthService.firstName
    val lastName:     StateFlow<String>  = AuthService.lastName

    // ── Newsletter ────────────────────────────────────────────────────────────

    private val _isNewsletterSubscribed = MutableStateFlow(false)
    val isNewsletterSubscribed: StateFlow<Boolean> = _isNewsletterSubscribed.asStateFlow()

    private val _isLoadingNewsletter = MutableStateFlow(false)
    val isLoadingNewsletter: StateFlow<Boolean> = _isLoadingNewsletter.asStateFlow()

    /**
     * Dernier code de refus serveur, joint au diagnostic du mail de support :
     * la cause d'un échec reste lisible sans build debug
     * (`apple/180/NewsletterStore.swift:20-22`).
     */
    private val _newsletterErrorCode = MutableStateFlow<String?>(null)
    val newsletterErrorCode: StateFlow<String?> = _newsletterErrorCode.asStateFlow()

    /** Dernière valeur confirmée par le serveur, pour le rollback. */
    private var serverNewsletterValue: Boolean? = null

    // ── Push ──────────────────────────────────────────────────────────────────

    /**
     * Bascule l'abonnement push depuis Mon compte.
     *
     * Miroir de `setPushSubscription` (`apple/180/NotificationManager.swift:74-99`) :
     * permission jamais demandée → prompt système puis opt-in **seulement** si
     * accordée ; permission accordée → opt-in/opt-out ; permission refusée →
     * ouverture des réglages (le toggle est de toute façon désactivé).
     *
     * La demande de permission runtime doit venir de l'Activity : le ViewModel
     * pose l'intention, l'écran la satisfait.
     */
    fun setPushSubscription(enabled: Boolean, context: android.content.Context) {
        viewModelScope.launch {
            when {
                PushPermissionCoordinator.isPermissionUndetermined(context) ->
                    _pushPermissionRequest.value = true

                PushPermissionCoordinator.isPermissionGranted(context) ->
                    if (enabled) PushPermissionCoordinator.optIn() else PushPermissionCoordinator.optOut()

                else -> PushPermissionCoordinator.openSystemSettings(context)
            }
            PushState.refresh(context)
        }
    }

    /** Ligne « Activer dans les Réglages » (`NotificationManager.swift:122-131`). */
    fun openNotificationSettings(context: android.content.Context) {
        PushPermissionCoordinator.openSystemSettings(context)
    }

    /** Demande de permission runtime à satisfaire par l'Activity. */
    private val _pushPermissionRequest = MutableStateFlow(false)
    val pushPermissionRequest: StateFlow<Boolean> = _pushPermissionRequest.asStateFlow()

    /**
     * Retour du prompt système `POST_NOTIFICATIONS` (Android 13+).
     *
     * Passe par [PushPermissionCoordinator.onSystemPromptResult], point de
     * mesure unique de `push_optin` — partagé avec le soft-ask.
     */
    fun onSystemPromptResult(context: android.content.Context) {
        _pushPermissionRequest.value = false
        applyPermission(context, PushPermissionCoordinator.onSystemPromptResult(context))
    }

    /**
     * Sous Android 13 il n'y a **aucun** prompt à présenter : la notification
     * est autorisée à l'installation. Rien n'a donc été consenti à cet instant
     * et `push_optin` n'est pas émis — l'event mesure une réponse de
     * l'utilisateur, pas un état par défaut.
     */
    fun onPushPermissionHandled(context: android.content.Context) {
        _pushPermissionRequest.value = false
        PushPermissionCoordinator.markSystemPromptShown()
        applyPermission(context, PushPermissionCoordinator.isPermissionGranted(context))
    }

    private fun applyPermission(context: android.content.Context, granted: Boolean) {
        AnalyticsService.notificationPermission(granted = granted)
        if (granted) PushPermissionCoordinator.optIn()
        PushState.refresh(context)
    }

    // ── Apparence ─────────────────────────────────────────────────────────────

    /** 0 = auto, 1 = clair, 2 = sombre. Persisté dans DataStore. */
    val appearanceMode: StateFlow<Int> = AppPreferences.appearanceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        // Valeur locale affichée immédiatement, puis confirmée par le serveur
        // via hydrateNewsletter() — le statut d'opt-in fait autorité côté serveur.
        viewModelScope.launch {
            _isNewsletterSubscribed.value = AppPreferences.newsletterSubscribed.first()
        }
        // Rejoué à chaque changement d'identité, comme l'iOS qui rappelle
        // `hydrate()` sur `.task` de l'écran compte (AccountView.swift:47).
        viewModelScope.launch {
            AuthService.contentGeneration.drop(1).collect { hydrateNewsletter() }
        }
    }

    // ── Newsletter ────────────────────────────────────────────────────────────

    /**
     * Lit le statut d'opt-in serveur à l'ouverture de l'écran et à chaque
     * changement de session.
     *
     * Un statut illisible **laisse l'état inchangé** — jamais de faux OFF
     * (miroir `NewsletterStore.hydrate`, `apple/180/NewsletterStore.swift:39-51`).
     */
    fun hydrateNewsletter() {
        viewModelScope.launch {
            if (!AuthService.isLoggedIn.value) return@launch
            val email = AuthService.canonicalEmail() ?: return@launch

            NewsletterService.status(email)
                .onSuccess { applyNewsletterStatus(it.isSubscribed) }
                .onFailure { _newsletterErrorCode.value = it.serverCodeOrNull() }
        }
    }

    /**
     * Applique un geste utilisateur sur le toggle : optimiste, puis réconcilié
     * avec la réponse serveur ou annulé.
     *
     * Miroir de `NewsletterStore.toggle(to:)` (`NewsletterStore.swift:54-87`).
     */
    fun setNewsletterSubscribed(desired: Boolean) {
        if (_isLoadingNewsletter.value || !AuthService.isLoggedIn.value) return
        // Rien à faire si l'état visé est déjà l'état serveur (double tap, écho).
        if (desired == serverNewsletterValue) return

        viewModelScope.launch {
            val email = AuthService.canonicalEmail()
            if (email == null) {
                ToastManager.show(newsletterFailureMessage(null), ToastType.ERROR)
                return@launch
            }

            _isLoadingNewsletter.value = true
            _isNewsletterSubscribed.value = desired   // optimiste : retour visuel immédiat

            val result = if (desired) {
                NewsletterService.subscribe(email)
            } else {
                NewsletterService.unsubscribe(email)
            }

            result
                .onSuccess {
                    applyNewsletterStatus(it.isSubscribed)
                    // Miroir NewsletterStore.swift:74-78.
                    if (desired) {
                        AnalyticsService.newsletterSubscribe()
                    } else {
                        AnalyticsService.newsletterUnsubscribe()
                    }
                }
                .onFailure { throwable ->
                    // Rollback visuel sur la dernière valeur serveur connue.
                    _isNewsletterSubscribed.value = serverNewsletterValue ?: !desired
                    _newsletterErrorCode.value = throwable.serverCodeOrNull()
                    ToastManager.show(
                        newsletterFailureMessage(throwable.serverCodeOrNull()),
                        ToastType.ERROR
                    )
                }

            _isLoadingNewsletter.value = false
        }
    }

    private fun applyNewsletterStatus(subscribed: Boolean) {
        serverNewsletterValue = subscribed
        _isNewsletterSubscribed.value = subscribed
        // Persiste l'état réel : lu par le diagnostic du mail de support.
        viewModelScope.launch { AppPreferences.setNewsletterSubscribed(subscribed) }
    }

    private fun Throwable.serverCodeOrNull(): String? =
        (this as? NewsletterException)?.error?.serverCode

    /**
     * Message d'échec. En **debug** il porte le code serveur ; en release, message
     * générique — aucun code interne ne fuite à l'utilisateur
     * (`NewsletterStore.swift:98-106`).
     */
    private fun newsletterFailureMessage(serverCode: String?): String {
        val base = "Impossible de mettre à jour votre inscription newsletter."
        return if (BuildConfig.DEBUG) "$base (${serverCode ?: "sans code"})" else base
    }

    // ── Apparence ─────────────────────────────────────────────────────────────

    fun setAppearanceMode(mode: Int) {
        // Miroir AccountView.swift:263-266 : « auto » · « light » · « dark ».
        AnalyticsService.darkModeChanged(
            mode = when (mode) {
                1    -> "light"
                2    -> "dark"
                else -> "auto"
            }
        )
        viewModelScope.launch { AppPreferences.setAppearanceMode(mode) }
    }

    // ── Auth actions ──────────────────────────────────────────────────────────

    /**
     * Vrai pendant l'affichage de l'écran « À bientôt ! ».
     *
     * Un **temps d'affichage**, jamais une étape de la déconnexion : la purge
     * a déjà eu lieu quand ce drapeau passe à vrai.
     */
    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    /**
     * Déconnecte l'utilisateur (efface token + prefs) et affiche l'écran de
     * transition pendant 1,2 s.
     *
     * L'ordre est celui de l'iOS (`apple/180/AccountView.swift:640-652`) : le
     * retour haptique, la mesure, puis la purge **immédiate**. Les onglets
     * basculent en visiteur tout de suite, derrière l'écran ; si la coroutine
     * qui le retire n'était jamais reprise — ViewModel détruit, processus tué —
     * la déconnexion resterait acquise.
     */
    fun logout() {
        HapticFeedbackManager.success()   // AccountView.swift:640
        AnalyticsService.logout()         // AccountView.swift:535, :641
        AuthService.logout()              // AccountView.swift:647
        _isLoggingOut.value = true
        viewModelScope.launch {
            delay(1_200L)                 // AccountView.swift:648
            _isLoggingOut.value = false
        }
    }
}
