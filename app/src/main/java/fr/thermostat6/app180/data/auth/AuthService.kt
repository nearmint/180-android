package fr.thermostat6.app180.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.JsonParser
import fr.thermostat6.app180.data.analytics.AnalyticsService
import fr.thermostat6.app180.data.analytics.UmamiEvents
import fr.thermostat6.app180.data.analytics.UmamiParams
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.favorites.FavoritesManager
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.offline.OfflineSyncService
import fr.thermostat6.app180.data.push.PushNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import retrofit2.HttpException
import java.io.IOException

/**
 * Singleton gérant le cycle de vie de l'authentification JWT.
 * Doit être initialisé une seule fois via [init] (ex. dans Application.onCreate).
 *
 * Flux d'état observables via StateFlow pour les ViewModels / Composables.
 */
object AuthService {

    // ── SharedPreferences keys (équivalent UserDefaults iOS) ─────────────────

    private const val TAG = "AuthService"

    private const val AUTH_PREFS_FILE   = "app180_auth"
    private const val KEY_USERNAME      = "saved_username"
    private const val KEY_EMAIL         = "saved_email"
    private const val KEY_FIRST_NAME    = "saved_first_name"
    private const val KEY_LAST_NAME     = "saved_last_name"
    private const val KEY_IS_SUBSCRIBER = "is_subscriber"

    // ── Dépendances internes ──────────────────────────────────────────────────

    private lateinit var keychain: KeychainHelper
    private lateinit var prefs: SharedPreferences

    /** Scope de vie app — survit aux recompositions / rotations. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Le collecteur d'identité push n'est monté qu'une fois ([observePushIdentity]). */
    @Volatile private var isObservingPushIdentity = false

    // ── État observable ───────────────────────────────────────────────────────

    private val _isLoggedIn   = MutableStateFlow(false)
    private val _isSubscriber = MutableStateFlow(false)
    private val _username     = MutableStateFlow("")
    private val _email        = MutableStateFlow("")
    private val _firstName    = MutableStateFlow("")
    private val _lastName     = MutableStateFlow("")

    /**
     * Génération de session : incrémentée à **chaque changement d'identité**
     * (login confirmé, déconnexion).
     *
     * Le paywall ne dépend plus d'un booléen local mais de `recipe_locked`,
     * calculé par le serveur **au moment du fetch** en fonction du jeton envoyé.
     * Les [fr.thermostat6.app180.data.model.Recipe] déjà décodées gardent donc
     * l'état de la session précédente — sans ce signal, il faudrait tuer l'app
     * après un login pour voir le contenu se déverrouiller. Les écrans observent
     * cette valeur et rejouent leur chargement avec le nouveau jeton.
     *
     * Miroir iOS : `contentGeneration`, `apple/180/AuthService.swift:23-32`.
     */
    /**
     * `true` quand [email] provient de `/180c/v1/me`, seule source qui renvoie le
     * `user_email` du compte.
     *
     * Les autres origines ne valent pas preuve : le payload JWT peut ne pas
     * porter la clé `email`, et le cache relu au démarrage peut être périmé
     * (`apple/180/AuthService.swift:34-43`).
     */
    @Volatile private var _emailIsCanonical = false

    /**
     * Identifiant utilisateur WordPress, exposé par `/180c/v1/me`.
     *
     * Sert d'external id OneSignal ; `null` tant que le champ n'est pas servi
     * par le serveur. N'intervient pas dans le flux d'authentification lui-même
     * (miroir `apple/180/AuthService.swift:17-21`).
     */
    private val _userId = MutableStateFlow<Int?>(null)
    val userId: StateFlow<Int?> = _userId.asStateFlow()

    private val _contentGeneration = MutableStateFlow(0)
    val contentGeneration: StateFlow<Int> = _contentGeneration.asStateFlow()

    val isLoggedIn:   StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    val isSubscriber: StateFlow<Boolean> = _isSubscriber.asStateFlow()
    val username:     StateFlow<String>  = _username.asStateFlow()
    val email:        StateFlow<String>  = _email.asStateFlow()
    val firstName:    StateFlow<String>  = _firstName.asStateFlow()
    val lastName:     StateFlow<String>  = _lastName.asStateFlow()

    /**
     * Événements d'erreur destinés à l'UI (toast / message inline).
     * Utiliser [authError].collect { } dans un LaunchedEffect.
     */
    private val _authError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val authError: SharedFlow<String> = _authError.asSharedFlow()

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * À appeler dans Application.onCreate() avant toute utilisation.
     * Restaure l'état depuis le stockage local et migre le legacy si nécessaire.
     */
    fun init(context: Context) {
        keychain = KeychainHelper(context)
        prefs = context.getSharedPreferences(AUTH_PREFS_FILE, Context.MODE_PRIVATE)

        // Migration depuis l'ancien stockage non chiffré
        keychain.migrateFromLegacyIfNeeded()

        // Restaurer l'état depuis le token stocké
        val token = keychain.getToken()
        if (token != null) {
            ApiClient.authToken = token
            _isLoggedIn.value = true
            _username.value     = prefs.getString(KEY_USERNAME, "") ?: ""
            // Valeur de cache : rien ne prouve qu'elle vaut encore `user_email`
            // (AuthService.swift:399-401).
            _email.value        = prefs.getString(KEY_EMAIL, "") ?: ""
            _emailIsCanonical   = false
            _firstName.value    = prefs.getString(KEY_FIRST_NAME, "") ?: ""
            _lastName.value     = prefs.getString(KEY_LAST_NAME, "") ?: ""
            _isSubscriber.value = prefs.getBoolean(KEY_IS_SUBSCRIBER, false)
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authentifie l'utilisateur via JWT.
     *
     * Après succès :
     * 1. JWT stocké dans EncryptedSharedPreferences
     * 2. Payload décodé → email + username mis à jour
     * 3. fetchUserProfile() puis checkSubscriptionStatus() en séquence,
     *    puis publication de la nouvelle génération de contenu
     *
     * @return `Result.success(Unit)`, ou `Result.failure` portant l'[AuthError]
     *   typée — l'appelant y lit le message à afficher sans avoir à
     *   réinterpréter l'échec (AND-07).
     */
    suspend fun login(login: String, password: String): Result<Unit> {
        return try {
            val response = ApiClient.authApi.login(login = login, password = password)

            if (response.success && response.data?.jwt != null) {
                val jwt = response.data.jwt
                applyNewToken(jwt)

                // Extraire email / username depuis le payload JWT
                JwtUtils.decodePayload(jwt)?.let { payload ->
                    _email.value    = payload.email
                    _username.value = payload.username.ifEmpty { login }
                } ?: run {
                    _username.value = login
                }

                // Le statut abonné en cache est celui du compte précédent :
                // invalidé tout de suite, il sera réécrit par
                // checkSubscriptionStatus(). Sans ça, un ancien `true`
                // déverrouillerait l'UI avant vérification (AuthService.swift:121-125).
                _isSubscriber.value = false
                // Nouvelle identité : l'e-mail lu du payload JWT n'est pas une
                // preuve, seul `/me` en sera une (AuthService.swift:118-120).
                _emailIsCanonical = false

                persistUserPrefs()
                _isLoggedIn.value = true

                // Profil puis statut abonné **en séquence**, et seulement ensuite
                // la nouvelle génération : les écrans qui se rechargent voient
                // alors un statut déjà à jour côté serveur (AuthService.swift:139-146).
                scope.launch {
                    supervisorScope {
                        fetchUserProfile()
                        checkSubscriptionStatus()
                        bumpContentGeneration()
                    }
                }
                // Carnet : réconciliation serveur en parallèle du profil, comme
                // l'iOS qui la lance dans sa propre Task (AuthService.swift:147).
                scope.launch { FavoritesManager.syncOnLogin() }

                // Miroir LoginView.swift:129 : « email » si l'identifiant en a
                // la forme, « username » sinon.
                AnalyticsService.login(method = if (login.contains("@")) "email" else "username")
                UmamiTracker.trackEvent(UmamiEvents.LOGIN_SUCCESS)   // LoginView.swift:131

                Result.success(Unit)
            } else {
                // Le message serveur reste en log : il est en anglais, écrit pour
                // un développeur, et l'iOS interdit qu'il atteigne l'utilisateur
                // ou la mesure (`AuthService.swift:516-519`).
                Log.w(TAG, "[Auth] refus du plugin : ${response.data?.message}")
                fail(pluginError(response.data?.errorCode))
            }
        } catch (e: HttpException) {
            // Vérifié au curl côté iOS (`AuthService.swift:151-167`) : le plugin
            // répond **HTTP 400** — pas 200 — pour de mauvais identifiants. Le
            // code HTTP ne distingue donc pas un refus d'identifiants d'une
            // panne : la source de vérité est le corps de la réponse.
            fail(e.loginError())
        } catch (e: Exception) {
            // `Transport` = hors-ligne, timeout, DNS, TLS ; tout le reste est une
            // réponse inexploitable.
            fail(if (e is IOException) AuthError.Transport else AuthError.ServerError)
        }
    }

    /**
     * Point de sortie **unique** d'un échec de connexion : mesure puis remontée.
     *
     * Regrouper les trois branches ici est ce qui garantit qu'un échec produit
     * exactement un `login_error`, et que le message affiché et le `reason`
     * mesuré sont lus sur la même valeur d'[AuthError] — ils ne peuvent plus
     * diverger. Miroir de `LoginView.performLogin()` (`LoginView.swift:134-141`),
     * où les deux dérivent du même `error`.
     */
    private fun fail(error: AuthError): Result<Unit> {
        AnalyticsService.loginFailed(error = error.displayMessage)   // LoginView.swift:134
        UmamiTracker.trackEvent(                                      // LoginView.swift:139
            UmamiEvents.LOGIN_ERROR,
            mapOf(UmamiParams.REASON to error.analyticsReason)
        )
        return Result.failure(error)
    }

    // ── Causes d'échec de connexion ──────────────────────────────────────────
    //
    // La table cause → (message affiché, `reason` mesuré) vit dans [AuthError],
    // miroir de l'enum iOS (`apple/180/AuthService.swift:516-561`).

    /** `errorCode` 48 — le serveur refuse réellement les identifiants. */
    private const val ERROR_CODE_INVALID_CREDENTIALS = 48

    /**
     * Refus structuré du plugin, quel que soit le code HTTP : seul
     * [ERROR_CODE_INVALID_CREDENTIALS] vaut « mauvais identifiants », tout autre
     * code — y compris son absence — reste générique (`AuthService.swift:164-167`).
     */
    private fun pluginError(errorCode: Int?): AuthError =
        if (errorCode == ERROR_CODE_INVALID_CREDENTIALS) {
            AuthError.InvalidCredentials
        } else {
            AuthError.PluginRefused
        }

    /**
     * Cause d'un échec non-2xx, lue dans le corps d'erreur.
     *
     * Un corps portant `success:false` est un refus **structuré** du plugin ;
     * tout le reste — 5xx, page HTML, corps illisible — est une panne de
     * service, jamais imputée aux identifiants (`AuthService.swift:164-172`).
     *
     * Le corps n'est consommé par personne d'autre sur ce chemin, et toute
     * lecture qui échoue retombe sur [AuthError.HttpError] : la lecture de la
     * cause ne doit jamais faire échouer une connexion autrement.
     */
    private fun HttpException.loginError(): AuthError = runCatching {
        val body = response()?.errorBody()?.string().orEmpty()
        val root = JsonParser.parseString(body).asJsonObject
        val isStructuredRefusal = root.get("success")?.asBoolean == false
        if (!isStructuredRefusal) return AuthError.HttpError

        // `errorCode` arrive en Int (cas réel) ; repli String par robustesse,
        // comme le double décodage du Swift (`AuthService.swift:157-158`).
        val errorCode = root.getAsJsonObject("data")
            ?.get("errorCode")?.asString?.toIntOrNull()
        pluginError(errorCode)
    }.getOrDefault(AuthError.HttpError)

    // ── Refresh ───────────────────────────────────────────────────────────────

    /**
     * Appelé au démarrage de l'app (ContentView.LaunchedEffect).
     *
     * Quatre issues, miroir de `refreshTokenIfNeeded()` + `refreshToken()`
     * (`apple/180/AuthService.swift:297-332`, `:344-395`) :
     * 1. jeton **illisible** → purge de session, aucun refresh ne le réparera ;
     * 2. jeton **déjà expiré** → purge de session, sans appeler le serveur ;
     * 3. refus **structuré** non-401/403 → session **conservée** ;
     * 4. **401 / 403** → purge de session.
     */
    suspend fun refreshTokenIfNeeded() {
        val token = keychain.getToken() ?: return

        // Jeton illisible : aucun refresh ne le réparera, et il continuerait à
        // partir en en-tête `Authorization` sur chaque appel — ce qui fait
        // rejeter par le middleware JWT **toutes** les lectures, y compris
        // publiques (`wp/v2/recipe` → HTTP 400). Symptôme observé côté iOS : un
        // accueil sans aucun rail. Retomber en visiteur restaure un état
        // fonctionnel (AuthService.swift:300-310).
        val payload = JwtUtils.decodePayload(token)
        if (payload == null) {
            expireSession()
            return
        }

        // Déjà expiré : le refresh ne le ressuscitera pas — le plugin répond
        // « JWT is too old to be refreshed » avec `success:false` en **HTTP 200**,
        // que la branche 401/403 ne peut pas intercepter (même piège que sur le
        // login). Le jeton restait donc en keychain indéfiniment. On purge ici,
        // en amont, sur le seul critère qui ne dépende pas du plugin : sa date
        // (AuthService.swift:316-326).
        if (payload.isExpired()) {
            expireSession()
            return
        }

        if (!payload.isExpiringSoon(days = 7)) return

        try {
            val response = ApiClient.authApi.refreshToken(jwt = token)
            if (response.success && response.data?.jwt != null) {
                applyNewToken(response.data.jwt)
            }
            // Un refus **structuré** (`success:false`, quel que soit le code
            // HTTP) ne suffit pas à conclure : le plugin refuse aussi de
            // rafraîchir un jeton encore parfaitement valide, passé sa fenêtre
            // de renouvellement. Déconnecter là-dessus couperait une session qui
            // a encore des jours à vivre (AuthService.swift:375-384). Le cas du
            // jeton réellement mort est déjà traité en amont, par sa date.
        } catch (e: HttpException) {
            // Le serveur rejette explicitement l'authentification : la session
            // est morte, quelle que soit la date du jeton (AuthService.swift:385).
            if (e.code() == 401 || e.code() == 403) {
                expireSession()
            }
            // Autres erreurs réseau : fail-open (on garde la session locale)
        } catch (_: Exception) {
            // Pas de réseau → on ne déconnecte pas l'utilisateur
        }
    }

    /**
     * Purge une session qui ne peut plus rien authentifier, et le signale.
     *
     * Séparée de [logout] : même effet, mais l'utilisateur n'a rien demandé — il
     * doit comprendre pourquoi il se retrouve déconnecté. Le message part sur
     * [authError], collecté par la navigation.
     *
     * Miroir d'`expireSession()` (`apple/180/AuthService.swift:339-342`).
     */
    private suspend fun expireSession() {
        logout()
        _authError.emit(SESSION_EXPIRED_MESSAGE)
    }

    /** Libellé exact de l'iOS (`apple/180/AuthService.swift:341`). */
    private const val SESSION_EXPIRED_MESSAGE =
        "Votre session a expiré. Veuillez vous reconnecter."

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * Supprime le token, réinitialise l'état et nettoie le cache local.
     */
    fun logout() {
        keychain.deleteToken()
        ApiClient.authToken = null

        prefs.edit().clear().apply()

        // Le carnet appartient au compte : on le vide pour qu'il ne soit pas
        // poussé dans le compte suivant sur un appareil partagé.
        scope.launch { FavoritesManager.clearLocal() }

        // Les fiches hors ligne appartiennent au même compte, et elles portent
        // du contenu réservé aux abonnés : les laisser sur disque rendrait des
        // recettes premium consultables par le compte suivant. Le toggle est
        // éteint en même temps — le réactiver sans le vouloir retéléchargerait
        // le carnet de quelqu'un d'autre.
        OfflineSyncService.disableAndPurge()

        // Purge de la session web, miroir de `WebSession.clearSession()`
        // (apple/180/AuthService.swift:336). Le WebView interne rouvre donc
        // déconnecté, cohérent avec l'état de l'app.
        //
        // LIMITE PLATEFORME : les Custom Tabs vivent dans le processus de Chrome,
        // dont le cookie jar est inaccessible à l'app. Une page publique ouverte
        // en Custom Tab peut donc rester connectée côté Chrome — comme n'importe
        // quel onglet du navigateur. Seuls les liens authentifiés passent par le
        // WebView, qui est bien purgé ici.
        clearWebSessionCookies()

        // La déliaison de l'identité push n'est plus appelée ici : la remise à
        // zéro des flux ci-dessous la déclenche via [observePushIdentity],
        // comme l'iOS dont le `logout()` ne touche pas non plus à OneSignal.

        _isLoggedIn.value   = false
        _isSubscriber.value = false
        _emailIsCanonical   = false
        _userId.value       = null
        _username.value     = ""
        _email.value        = ""
        _firstName.value    = ""
        _lastName.value     = ""

        // Le contenu en mémoire a été chargé avec le jeton : il doit être
        // refetché **déverrouillé côté visiteur** (symétrique du login).
        // Miroir iOS : AuthService.swift:338-340.
        bumpContentGeneration()
    }

    /**
     * Vide les cookies du WebView interne.
     *
     * Encapsulé et tolérant à l'échec : sur un appareil sans WebView provisionné,
     * `CookieManager.getInstance()` lève — ce qui ne doit pas empêcher la
     * déconnexion d'aboutir.
     */
    private fun clearWebSessionCookies() {
        runCatching {
            android.webkit.CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
        }
    }

    /**
     * Rejoue l'identité et les tags OneSignal à **chaque** changement d'état —
     * pas seulement après une connexion explicite. Miroir d'`observeIdentity`
     * (`apple/180/Services/PushNotificationService.swift:94-112`).
     *
     * Sans ce collecteur, un abonné dont la session est restaurée au lancement
     * gardait `subscription_status = "none"` jusqu'à sa prochaine connexion :
     * les tags n'étaient posés que sur le chemin `login()`, alors que
     * `checkSubscriptionStatus()` est aussi rejoué au démarrage et au retour du
     * réseau (`AppNavigation.kt:197`, `:212`).
     *
     * `combine` réémet dès qu'une des trois sources bouge, et **émet
     * immédiatement** l'état courant à l'abonnement : c'est ce premier passage
     * qui pose les tags de la session restaurée par [init]. Les tags étant
     * idempotents, rejouer `applyTags` sur un simple changement d'`userId` est
     * sans conséquence — un seul collecteur vaut mieux que trois.
     *
     * À monter une seule fois, depuis `Application.onCreate()` et **après**
     * [PushNotificationService.initialize] : les poseurs de tags sont des no-op
     * tant que le SDK n'est pas initialisé.
     */
    fun observePushIdentity() {
        if (isObservingPushIdentity) return
        isObservingPushIdentity = true

        scope.launch {
            combine(_isLoggedIn, _userId, _isSubscriber) { loggedIn, id, subscriber ->
                Triple(loggedIn, id, subscriber)
            }.collect { (loggedIn, id, subscriber) ->
                PushNotificationService.syncExternalId(isLoggedIn = loggedIn, userId = id)
                PushNotificationService.applyTags(isLoggedIn = loggedIn, isSubscriber = subscriber)
            }
        }
    }

    /** Publie un changement d'identité (login confirmé / déconnexion). */
    private fun bumpContentGeneration() {
        _contentGeneration.value = _contentGeneration.value + 1
    }

    // ── Profil utilisateur ────────────────────────────────────────────────────

    /**
     * Charge le profil depuis GET /wp/v2/users/me?context=edit.
     * Met à jour les StateFlows et persiste dans SharedPreferences.
     * Silencieux en cas d'erreur réseau.
     */
    suspend fun fetchUserProfile() {
        try {
            val profile = ApiClient.wordPressApi.getUserProfile()
            _firstName.value = profile.firstName
            _lastName.value  = profile.lastName
            if (profile.email.isNotEmpty()) _email.value = profile.email

            prefs.edit()
                .putString(KEY_FIRST_NAME, profile.firstName)
                .putString(KEY_LAST_NAME, profile.lastName)
                .putString(KEY_EMAIL, profile.email)
                .apply()
        } catch (_: Exception) {
            // Fail silencieux — les données en cache restent valides
        }
    }

    // ── Statut abonnement ─────────────────────────────────────────────────────

    /**
     * Lit le statut abonné sur `GET /wp-json/180c/v1/me`, seule source faisant foi.
     *
     * Remplace l'heuristique provisoire du LOT-04 (charger une recette et lire
     * `!isLocked`) : celle-ci confondait « aucune recette premium dans le lot »
     * avec « utilisateur abonné », et dépendait de l'ordre du catalogue.
     *
     * L'endpoint renvoie aussi l'e-mail canonique du compte (`user_email` côté
     * serveur) : on le retient quand il est non vide, comme l'iOS
     * (`AuthService.swift:220-225`).
     *
     * Échec réseau ou réponse non-200 → le dernier statut connu est **conservé**,
     * jamais dégradé silencieusement en non-abonné (miroir `AuthService.swift:211-231`
     * où les deux chemins d'échec se contentent d'un log et d'un `return`).
     *
     * Miroir iOS : `checkSubscriptionStatus()`, `apple/180/AuthService.swift:204-232`.
     */
    suspend fun checkSubscriptionStatus() {
        if (keychain.getToken() == null) return   // AuthService.swift:205

        try {
            val me = ApiClient.userApi.getMe()
            val wasSubscriber = _isSubscriber.value
            _isSubscriber.value = me.isSubscriber
            // Fin d'abonnement constatée **en ligne** — on ne passe ici que si
            // `/me` a répondu. Aucune horloge locale ne décide de la fin d'un
            // abonnement : hors connexion, l'échec est avalé par le `catch` et
            // le carnet hors ligne est conservé intact.
            if (wasSubscriber && !me.isSubscriber) {
                OfflineSyncService.handleSubscriptionLost()
            }
            _userId.value = me.id
            if (me.email.isNotEmpty()) {
                _email.value = me.email
                // Seule écriture d'e-mail issue de `/180c/v1/me`, donc la seule à
                // valoir `user_email` côté serveur (AuthService.swift:220-225).
                _emailIsCanonical = true
            }
            prefs.edit()
                .putBoolean(KEY_IS_SUBSCRIBER, me.isSubscriber)
                .putString(KEY_EMAIL, _email.value)
                .apply()
        } catch (_: Exception) {
            // Fail silencieux — on conserve la valeur cachée
        }
    }

    /**
     * E-mail du compte **tel que le serveur le valide** (`user_email`).
     *
     * À utiliser pour toute requête où le serveur compare l'e-mail au compte
     * authentifié — c'est le contrat newsletter, qui refuse en `email_mismatch`
     * (403) ou `invalid_email` (400) toute adresse divergente. Tant que la valeur
     * en mémoire n'a pas été confirmée par `/180c/v1/me`, elle est rafraîchie
     * depuis cette source avant d'être renvoyée.
     *
     * @return l'e-mail canonique, ou `null` si aucun n'est résolvable —
     *   l'appelant ne doit alors **pas** émettre la requête plutôt que d'en
     *   envoyer une vide, que le serveur rejette.
     *
     * Miroir de `canonicalEmail()` (`apple/180/AuthService.swift:244-248`).
     */
    suspend fun canonicalEmail(): String? {
        if (_emailIsCanonical && _email.value.isNotEmpty()) return _email.value
        checkSubscriptionStatus()
        return _email.value.takeIf { it.isNotEmpty() }
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private fun applyNewToken(jwt: String) {
        keychain.saveToken(jwt)
        ApiClient.authToken = jwt
    }

    private fun persistUserPrefs() {
        prefs.edit()
            .putString(KEY_USERNAME, _username.value)
            .putString(KEY_EMAIL, _email.value)
            .apply()
    }
}
