package fr.thermostat6.app180.data.push

import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import fr.thermostat6.app180.data.analytics.UmamiEvents
import fr.thermostat6.app180.data.analytics.UmamiParams
import fr.thermostat6.app180.data.analytics.UmamiTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Écoute les taps sur une notification push et publie la destination.
 *
 * La destination est **mémorisée** jusqu'à ce que l'UI soit prête : c'est ce qui
 * fait fonctionner le démarrage à froid, où le tap précède l'existence de la
 * hiérarchie de composables. Miroir de `pendingDestination`
 * (`apple/180/Navigation/NotificationRouter.swift:38-41`).
 */
object PushClickListener : INotificationClickListener {

    private val _pendingDestination = MutableStateFlow<NotificationDestination?>(null)

    /** Destination en attente d'application par la navigation. */
    val pendingDestination: StateFlow<NotificationDestination?> = _pendingDestination.asStateFlow()

    override fun onClick(event: INotificationClickEvent) {
        // `push_open` : nom de template OneSignal en priorité (c'est la
        // « campagne » au sens dashboard), sinon le titre affiché, sinon l'id du
        // message. Aucun des trois n'est une donnée personnelle. Miroir de
        // `NotificationRouter.swift:134-142`.
        val campaign = event.notification.templateName
            ?: event.notification.title
            ?: event.notification.notificationId
        UmamiTracker.trackEvent(
            UmamiEvents.PUSH_OPEN,
            campaign?.takeIf { it.isNotEmpty() }?.let { mapOf(UmamiParams.CAMPAIGN to it) }
        )

        val data = event.notification.additionalData?.toMap()
        route(NotificationRouter.destinationFromPush(data))
    }

    /** Demande le routage. `None` déclenchera l'ouverture du centre. */
    fun route(destination: NotificationDestination) {
        _pendingDestination.value = destination
    }

    /** À appeler une fois la destination consommée par la navigation. */
    fun consume() {
        _pendingDestination.value = null
    }

    /** Aplatit le JSON OneSignal en carte lisible par le routeur. */
    private fun JSONObject.toMap(): Map<String, Any?> =
        keys().asSequence().associateWith { key ->
            when (val value = opt(key)) {
                is JSONObject -> value.toMap()
                else          -> value
            }
        }
}
