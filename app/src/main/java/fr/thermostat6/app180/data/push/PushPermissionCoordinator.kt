package fr.thermostat6.app180.data.push

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.onesignal.OneSignal
import fr.thermostat6.app180.data.analytics.UmamiEvents
import fr.thermostat6.app180.data.analytics.UmamiParams
import fr.thermostat6.app180.data.analytics.UmamiTracker

/**
 * Politique d'opt-in push : soft-ask maison **avant** la demande système.
 *
 * Miroir de `PushPermissionCoordinator`
 * (`apple/180/Services/PushPermissionCoordinator.swift:22-140`).
 *
 * Le soft-ask existe parce que la demande système n'est présentable **qu'une
 * fois** : la poser à froid, c'est convertir un « pas maintenant » en refus
 * définitif. On la déclenche donc seulement après un signal d'intérêt.
 */
object PushPermissionCoordinator {

    private const val PREFS_FILE = "app180_push"
    private const val KEY_PROMPT_SHOWN = "push.hasSystemPromptBeenShown"
    private const val KEY_DECLINE_COUNT = "push.softAskDeclineCount"
    private const val KEY_LAST_SOFT_ASK = "push.lastSoftAskDate"

    /** Fenêtre de rappel minimale entre deux soft-asks — 30 jours (`:34`). */
    private const val COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000

    /** Au-delà, plus aucun soft-ask ne réapparaît (`:77`). */
    private const val MAX_DECLINES = 2

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    /** Réinitialisé à chaque lancement (état en mémoire, `:37`). */
    private var sessionSoftAskShown = false

    /**
     * Vrai tant qu'une recette est en cours de lecture : un soft-ask ne doit
     * jamais interrompre une lecture (`:41-43`).
     */
    @Volatile var isRecipeReadingInProgress = false

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    private val isReady: Boolean get() = ::prefs.isInitialized

    // ── État persisté ────────────────────────────────────────────────────────

    var hasSystemPromptBeenShown: Boolean
        get() = isReady && prefs.getBoolean(KEY_PROMPT_SHOWN, false)
        private set(value) { if (isReady) prefs.edit().putBoolean(KEY_PROMPT_SHOWN, value).apply() }

    private var declineCount: Int
        get() = if (isReady) prefs.getInt(KEY_DECLINE_COUNT, 0) else 0
        set(value) { if (isReady) prefs.edit().putInt(KEY_DECLINE_COUNT, value).apply() }

    private var lastSoftAskAt: Long
        get() = if (isReady) prefs.getLong(KEY_LAST_SOFT_ASK, 0L) else 0L
        set(value) { if (isReady) prefs.edit().putLong(KEY_LAST_SOFT_ASK, value).apply() }

    /** Vrai si un soft-ask a déjà été présenté au moins une fois (`:46`). */
    val hasEverShownSoftAsk: Boolean get() = lastSoftAskAt != 0L

    // ── Décision ─────────────────────────────────────────────────────────────

    /**
     * Autorise l'affichage d'un soft-ask uniquement si **toutes** les conditions
     * sont réunies — miroir de `shouldShowSoftAsk` (`:72-85`) :
     * permission encore indéterminée, moins de [MAX_DECLINES] refus, aucun
     * soft-ask déjà montré dans la session, dernier soft-ask absent ou au-delà
     * du cooldown, et aucune lecture de recette en cours.
     *
     * Extrait en fonction pure pour être testable sans framework.
     */
    fun shouldShowSoftAsk(
        isPermissionUndetermined: Boolean,
        declineCount: Int = this.declineCount,
        sessionAlreadyShown: Boolean = sessionSoftAskShown,
        lastSoftAskAt: Long = this.lastSoftAskAt,
        nowMillis: Long = System.currentTimeMillis(),
        isReadingRecipe: Boolean = isRecipeReadingInProgress
    ): Boolean {
        if (!isPermissionUndetermined) return false
        if (declineCount >= MAX_DECLINES) return false
        if (sessionAlreadyShown) return false
        if (lastSoftAskAt != 0L && nowMillis - lastSoftAskAt < COOLDOWN_MS) return false
        if (isReadingRecipe) return false
        return true
    }

    /** À la présentation : marque la session et horodate (`:87-90`). */
    fun markSoftAskShown() {
        sessionSoftAskShown = true
        lastSoftAskAt = System.currentTimeMillis()
    }

    /** « Plus tard » : incrémente le compteur et réarme le cooldown (`:94-96`). */
    fun registerSoftAskDeclined() {
        declineCount += 1
        lastSoftAskAt = System.currentTimeMillis()
    }

    fun markSystemPromptShown() {
        hasSystemPromptBeenShown = true
    }

    /**
     * À appeler au retour du prompt système `POST_NOTIFICATIONS`.
     *
     * Point de mesure **UNIQUE** de l'opt-in : tous les chemins (soft-ask,
     * bascule « Mon compte ») passent par ici, comme l'iOS qui n'a qu'un
     * `requestSystemPermission` (`PushPermissionCoordinator.swift:105-114`).
     * Un second point d'émission compterait deux fois le même consentement.
     *
     * @return la permission telle que le système la voit après le prompt.
     */
    fun onSystemPromptResult(context: Context): Boolean {
        markSystemPromptShown()
        val granted = isPermissionGranted(context)
        UmamiTracker.trackEvent(
            UmamiEvents.PUSH_OPTIN,
            mapOf(UmamiParams.ACCEPTED to granted.toString())
        )
        return granted
    }

    // ── CTA d'activation ─────────────────────────────────────────────────────

    /** Geste à effectuer quand l'utilisateur demande explicitement l'activation. */
    enum class ActivationAction {
        /** Permission encore indéterminée : prompt système (l'Activity le porte). */
        REQUEST_PERMISSION,

        /** Permission déjà accordée, abonnement coupé : opt-in direct, sans prompt. */
        OPT_IN,

        /** Permission refusée : l'app ne peut plus la redemander, on ouvre les réglages. */
        OPEN_SETTINGS
    }

    /**
     * Décision du CTA d'activation — miroir d'`handleActivationCTA`
     * (`apple/180/NotificationManager.swift:104-118`).
     *
     * Jamais de soft-ask ici : l'utilisateur vient explicitement activer.
     *
     * Le cas [ActivationAction.OPT_IN] est celui qui rend le bouton « Activer »
     * opérant pour un utilisateur autorisé **mais désabonné** depuis Mon compte —
     * lui présenter un prompt système déjà accordé n'afficherait rien.
     *
     * En deçà d'Android 13, [isPermissionGranted] est toujours vrai : le chemin est
     * donc [ActivationAction.OPT_IN], sans prompt — et sans `push_optin`, qui
     * mesure une réponse au prompt système, pas un état par défaut.
     *
     * Extrait en fonction pure pour être testable sans framework, comme
     * [shouldShowSoftAsk].
     */
    fun activationAction(isGranted: Boolean, hasPromptBeenShown: Boolean): ActivationAction = when {
        isGranted          -> ActivationAction.OPT_IN
        hasPromptBeenShown -> ActivationAction.OPEN_SETTINGS
        else               -> ActivationAction.REQUEST_PERMISSION
    }

    fun activationAction(context: Context): ActivationAction =
        activationAction(isPermissionGranted(context), hasSystemPromptBeenShown)

    // ── Permission système ───────────────────────────────────────────────────

    /**
     * `POST_NOTIFICATIONS` n'existe qu'à partir d'Android 13 ; en deçà la
     * notification est autorisée par défaut à l'installation.
     */
    val requiresRuntimePermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun isPermissionGranted(context: Context): Boolean {
        if (!requiresRuntimePermission) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Permission encore jamais demandée. Android n'expose pas d'état
     * `notDetermined` : on le déduit du drapeau posé à la première demande.
     */
    fun isPermissionUndetermined(context: Context): Boolean =
        !isPermissionGranted(context) && !hasSystemPromptBeenShown

    /** Ouvre les réglages de notifications de l'app (`:114-120`). */
    fun openSystemSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    // ── Abonnement OneSignal ─────────────────────────────────────────────────

    /** Abonnement OneSignal actif (`:isOptedIn`). */
    val isOptedIn: Boolean
        get() = runCatching { OneSignal.User.pushSubscription.optedIn }.getOrDefault(false)

    fun optIn() {
        runCatching { OneSignal.User.pushSubscription.optIn() }
    }

    fun optOut() {
        runCatching { OneSignal.User.pushSubscription.optOut() }
    }
}
