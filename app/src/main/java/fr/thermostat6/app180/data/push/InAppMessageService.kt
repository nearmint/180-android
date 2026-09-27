package fr.thermostat6.app180.data.push

import com.onesignal.OneSignal
import com.onesignal.inAppMessages.IInAppMessageClickEvent
import com.onesignal.inAppMessages.IInAppMessageClickListener
import com.onesignal.inAppMessages.IInAppMessageDidDismissEvent
import com.onesignal.inAppMessages.IInAppMessageDidDisplayEvent
import com.onesignal.inAppMessages.IInAppMessageLifecycleListener
import com.onesignal.inAppMessages.IInAppMessageWillDismissEvent
import com.onesignal.inAppMessages.IInAppMessageWillDisplayEvent
import fr.thermostat6.app180.data.analytics.AnalyticsService

/**
 * In-App Messages OneSignal.
 *
 * Miroir d'`InAppMessageService` (`apple/180/Services/InAppMessageService.swift`).
 * Mode d'emploi dashboard : `apple/docs/onesignal-in-app-messages.md`.
 *
 * Le module `com.onesignal:in-app-messages` est apporté transitivement par
 * l'artefact agrégat `com.onesignal:OneSignal` — rien à déclarer, contrairement
 * à l'iOS où le produit SPM `OneSignalInAppMessages` doit être lié explicitement
 * sous peine de no-op silencieux.
 */
object InAppMessageService {

    @Volatile private var isRegistered = false

    /**
     * Enregistre les listeners de clic et de cycle de vie. Appelé une seule fois,
     * depuis [PushNotificationService.initialize], après l'init du SDK
     * (miroir `apple/180/Services/PushNotificationService.swift:83`).
     */
    fun register() {
        if (isRegistered) return
        isRegistered = true
        runCatching {
            OneSignal.InAppMessages.addClickListener(ClickListener)
            OneSignal.InAppMessages.addLifecycleListener(LifecycleListener)
        }
    }

    // ── Déclencheurs (triggers) ──────────────────────────────────────────────

    /**
     * Reflète l'état utilisateur en triggers, en miroir des tags posés par
     * [PushNotificationService.applyTags] — d'où l'appel unique depuis cette
     * dernière (`InAppMessageService.swift:49-58`).
     *
     * Les deux mécanismes ne sont **pas** interchangeables : les tags sont
     * évalués côté serveur et servent aux segments push, les triggers sont
     * évalués côté client et sont les seuls utilisables comme conditions
     * d'audience d'un In-App Message. Il faut les deux pour qu'un message du
     * dashboard puisse viser « abonné » ou « visiteur », et ils doivent bouger
     * ensemble.
     */
    fun syncStateTriggers(isLoggedIn: Boolean, isSubscriber: Boolean) {
        runCatching {
            OneSignal.InAppMessages.addTriggers(
                mapOf(
                    "logged_in"           to isLoggedIn.toString(),
                    "subscription_status" to if (isSubscriber) "active" else "none",
                    "env"                 to PushNotificationService.environmentTag,
                    "app_version"         to PushNotificationService.appVersion
                )
            )
        }
    }

    /** Trigger ponctuel, pour déclencher un message sur un écran ou un geste précis. */
    fun setTrigger(key: String, value: String) {
        runCatching { OneSignal.InAppMessages.addTrigger(key, value) }
    }

    fun removeTrigger(key: String) {
        runCatching { OneSignal.InAppMessages.removeTrigger(key) }
    }

    /**
     * Suspend l'affichage des IAM. Levier disponible pour les moments où une
     * interruption serait néfaste (mise à jour forcée, onboarding, lecture d'une
     * recette) — **non activé par défaut** (`InAppMessageService.swift:65-68`).
     */
    var isPaused: Boolean
        get() = runCatching { OneSignal.InAppMessages.paused }.getOrDefault(false)
        set(value) { runCatching { OneSignal.InAppMessages.paused = value } }

    // ── Clic ─────────────────────────────────────────────────────────────────

    private object ClickListener : IInAppMessageClickListener {
        override fun onClick(event: IInAppMessageClickEvent) {
            val actionId: String? = event.result.actionId
            AnalyticsService.inAppMessageClicked(id = event.message.messageId, actionId = actionId)

            // `None` = bouton sans intention de navigation : fermeture, ou lien
            // que le SDK ouvre lui-même via `urlTarget`. On ne route pas —
            // contrairement au tap sur un push, dont le `None` retombe sur le
            // centre de notifications (`InAppMessageService.swift:132`).
            val destination = InAppMessageService.destinationFromActionId(actionId)
            if (destination == NotificationDestination.None) return
            PushClickListener.route(destination)
        }
    }

    // ── Cycle de vie ─────────────────────────────────────────────────────────

    /**
     * Seul `onDidDisplay` porte de la mesure ; les trois autres rappels existent
     * parce que l'interface les exige (`InAppMessageService.swift:141-151`).
     */
    private object LifecycleListener : IInAppMessageLifecycleListener {
        override fun onWillDisplay(event: IInAppMessageWillDisplayEvent) = Unit

        override fun onDidDisplay(event: IInAppMessageDidDisplayEvent) {
            AnalyticsService.inAppMessageDisplayed(id = event.message.messageId)
        }

        override fun onWillDismiss(event: IInAppMessageWillDismissEvent) = Unit

        override fun onDidDismiss(event: IInAppMessageDidDismissEvent) = Unit
    }

    // ── Décision de routage ──────────────────────────────────────────────────

    /**
     * Traduit l'`actionId` d'un bouton d'In-App Message en destination native.
     *
     * Deux formes sont acceptées, toutes deux réduites au triplet
     * `type`/`id`/`url` de [NotificationRouter] — **une seule fonction de
     * décision**, partagée avec les push, plutôt que deux tables de routage qui
     * divergeraient avec le temps :
     *
     * - abrégée — `recipe:123`, `article:456`, `url:https://…` ;
     * - requête — `type=product&id=45&url=https%3A%2F%2Fwww.180c.fr%2Fboutique`,
     *   seule forme capable de porter à la fois un id et une URL, donc requise
     *   par le type `product`.
     *
     * Un `actionId` vide ou non reconnu renvoie [NotificationDestination.None] :
     * contrairement au tap sur un push, l'appelant **n'ouvre alors rien**. Le cas
     * courant est un bouton « Fermer », qui ne doit pas éjecter l'utilisateur
     * vers le centre de notifications.
     *
     * Miroir de `destination(fromActionID:)`
     * (`apple/180/Services/InAppMessageService.swift:87-112`).
     */
    fun destinationFromActionId(actionId: String?): NotificationDestination {
        val raw = actionId?.trim().orEmpty()
        if (raw.isEmpty()) return NotificationDestination.None

        // Forme requête. Tentée en premier, mais **seulement retenue si un champ
        // `type` en sort** : `url:https://…?ref=iam` contient lui aussi un `=`
        // et doit retomber sur la forme abrégée (miroir du `if let type` iOS).
        if (raw.contains('=')) {
            val fields = parseQuery(raw)
            fields["type"]?.let { type ->
                return NotificationRouter.destination(
                    type = type.lowercase(),
                    id   = fields["id"]?.toIntOrNull() ?: 0,
                    url  = fields["url"]
                )
            }
        }

        // Forme abrégée. Découpe sur le **premier** `:` seulement : découper sur
        // le dernier, ou sur tous, amputerait le schéma d'une URL.
        val separator = raw.indexOf(':')
        if (separator < 0) return NotificationDestination.None
        val type  = raw.substring(0, separator).lowercase()
        val value = raw.substring(separator + 1)
        return NotificationRouter.destination(type = type, id = value.toIntOrNull() ?: 0, url = value)
    }

    /**
     * Décode une chaîne de requête `a=1&b=2`, dernière occurrence gagnante.
     *
     * Décodage **percent uniquement**, pas `URLDecoder` : ce dernier applique la
     * convention des formulaires HTML et transformerait un `+` d'URL en espace,
     * là où l'`URLComponents` de l'iOS le laisse intact. Le seul but ici est de
     * transporter une URL percent-encodée en valeur de paramètre.
     */
    private fun parseQuery(raw: String): Map<String, String> =
        raw.split('&')
            .mapNotNull { pair ->
                val separator = pair.indexOf('=')
                if (separator < 0) null
                else pair.substring(0, separator) to percentDecode(pair.substring(separator + 1))
            }
            .toMap()

    /**
     * Décode les séquences `%XX` d'une valeur, en **UTF-8**.
     *
     * Les octets sont accumulés puis décodés d'un bloc : décoder chaque `%XX`
     * isolément casserait tout caractère non-ASCII, qui s'encode sur plusieurs
     * octets (`%C3%A9` = « é »). Une séquence `%` mal formée est laissée telle
     * quelle plutôt que de faire échouer le décodage — l'`actionId` est saisi à
     * la main, une coquille ne doit pas coûter la destination entière.
     */
    private fun percentDecode(value: String): String {
        if (!value.contains('%')) return value
        val bytes = java.io.ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            // `toIntOrNull(16)` accepterait un signe (`%-1`) : on exige deux
            // chiffres hexadécimaux, sinon le `%` est un caractère littéral.
            val hex = if (c == '%' && i + 2 < value.length &&
                value[i + 1].isHexDigit() && value[i + 2].isHexDigit()
            ) {
                value.substring(i + 1, i + 3).toInt(16)
            } else null
            if (hex != null) {
                bytes.write(hex)
                i += 3
            } else {
                bytes.write(c.toString().toByteArray(Charsets.UTF_8))
                i += 1
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
