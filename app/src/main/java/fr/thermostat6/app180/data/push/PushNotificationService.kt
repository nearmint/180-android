package fr.thermostat6.app180.data.push

import android.content.Context
import com.onesignal.OneSignal
import fr.thermostat6.app180.BuildConfig

/**
 * Initialisation et identité OneSignal.
 *
 * Miroir de `PushNotificationService` (`apple/180/Services/PushNotificationService.swift:68-139`).
 *
 * **Ne demande jamais la permission** : l'opt-in est délégué à
 * [PushPermissionCoordinator] (`PushNotificationService.swift:69-70`).
 */
object PushNotificationService {

    /**
     * App ID OneSignal — **partagé avec l'iOS**, les deux plateformes visent la
     * même application OneSignal.
     *
     * Source : `apple/Config/Release.xcconfig:17` (et `Local.xcconfig:13`,
     * valeur identique). Ce n'est pas un secret, comme le note le commentaire
     * du xcconfig ligne 16.
     */
    private const val ONESIGNAL_APP_ID = "f06a0870-a09a-4d27-af30-a26aea31ded9"

    @Volatile private var isInitialized = false

    /** Un seul avertissement si `/me` ne sert pas encore l'`id`. */
    @Volatile private var didWarnMissingUserId = false

    /** Version courte de l'app, posée en tag `app_version`. */
    val appVersion: String get() = BuildConfig.VERSION_NAME

    /**
     * Environnement de distribution, posé en tag `env`.
     *
     * **ÉCART ASSUMÉ AVEC L'iOS.** Le dashboard OneSignal est partagé par les
     * deux apps, et l'iOS y pose trois valeurs — `debug` / `testflight` /
     * `appstore` (`apple/180/Services/PushNotificationService.swift:18-47`).
     * Android n'en pose que deux.
     *
     * La raison est une limite de plateforme, pas un oubli : le pendant Android
     * de TestFlight est la piste interne / de test fermé de Play, qu'**aucune
     * API runtime n'expose**. `PackageManager.getInstallSourceInfo()` dit au
     * mieux « installé par le Play Store », jamais depuis quelle piste ; et le
     * module n'a pas de variante de build dédiée d'où la déduire. Seul Play
     * Integrity le distinguerait, au prix d'une dépendance supplémentaire et
     * d'une vérification côté serveur — hors de proportion pour un tag de
     * segmentation.
     *
     * Conséquence pour le dashboard : un segment `env = testflight` ne remonte
     * que de l'iOS, et `env = production` ne remonte que d'Android. Un segment
     * qui doit viser les deux plateformes en production doit donc accepter
     * `appstore` **ou** `production`. Le vocabulaire n'est volontairement pas
     * unifié sur une troisième valeur inutilisable : un `internal` qu'aucune
     * build ne porterait jamais serait un segment mort, plus trompeur que
     * l'écart lui-même.
     */
    val environmentTag: String get() = if (BuildConfig.DEBUG) "debug" else "production"

    /**
     * Initialise le SDK. Appelé une seule fois depuis `Application.onCreate()`,
     * pendant de l'`AppDelegate.didFinishLaunchingWithOptions` iOS
     * (`apple/180/AppDelegate.swift:18`).
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true

        OneSignal.initWithContext(context.applicationContext, ONESIGNAL_APP_ID)
        OneSignal.Notifications.addClickListener(PushClickListener)
        InAppMessageService.register()
        // Les tags ne sont **pas** posés ici : ils le sont par le collecteur
        // d'état monté juste après (`AuthService.observePushIdentity`), qui les
        // pose avec le statut réellement restauré au lieu d'un `false` codé en
        // dur. Miroir d'`observeIdentity` (`PushNotificationService.swift:85`).
    }

    /**
     * Lie (`login`) ou délie (`logout`) l'external id selon la session.
     *
     * Si l'identifiant utilisateur n'est pas encore servi par `/180c/v1/me`, on
     * **n'appelle pas** `login` mais on conserve les tags, avec un avertissement
     * unique — miroir de `syncExternalId`
     * (`PushNotificationService.swift:113-130`).
     */
    fun syncExternalId(isLoggedIn: Boolean, userId: Int?) {
        if (!isInitialized) return

        if (!isLoggedIn) {
            runCatching { OneSignal.logout() }
            return
        }

        if (userId != null) {
            runCatching { OneSignal.login(userId.toString()) }
        } else if (!didWarnMissingUserId) {
            didWarnMissingUserId = true
            // `id` absent de /180c/v1/me → login OneSignal ignoré, tags conservés.
        }
    }

    /**
     * (Re)pose les tags `env`, `subscription_status`, `app_version`, et reflète
     * le même état en triggers In-App Message.
     *
     * Les tags ciblent les push (évalués côté serveur), les triggers ciblent les
     * IAM (évalués côté client) : les deux doivent bouger ensemble, d'où l'appel
     * unique ici — le seul endroit d'où l'état utilisateur est déjà rediffusé à
     * chaque changement (`PushNotificationService.swift:133-144`).
     */
    fun applyTags(isLoggedIn: Boolean, isSubscriber: Boolean) {
        if (!isInitialized) return
        runCatching {
            OneSignal.User.addTag("env", environmentTag)
            OneSignal.User.addTag("subscription_status", if (isSubscriber) "active" else "none")
            OneSignal.User.addTag("app_version", appVersion)
        }
        InAppMessageService.syncStateTriggers(isLoggedIn = isLoggedIn, isSubscriber = isSubscriber)
    }
}
