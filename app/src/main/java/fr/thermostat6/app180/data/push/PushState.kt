package fr.thermostat6.app180.data.push

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État observable de l'abonnement push.
 *
 * Miroir des `@Published` de `NotificationManager`
 * (`apple/180/NotificationManager.swift:10-27`), rafraîchi à la demande —
 * pas d'observateur permanent, comme côté iOS où `checkStatus()` est appelé
 * à l'apparition de l'écran et au retour au premier plan.
 */
object PushState {

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    /** Permission refusée **après** avoir été demandée (`:11`). */
    private val _isDenied = MutableStateFlow(false)
    val isDenied: StateFlow<Boolean> = _isDenied.asStateFlow()

    private val _isOptedIn = MutableStateFlow(false)
    val isOptedIn: StateFlow<Boolean> = _isOptedIn.asStateFlow()

    /**
     * Abonnement push **effectif** : autorisation système accordée **ET**
     * abonnement OneSignal actif. Source unique de vérité pour la pastille de la
     * cloche (`NotificationManager.swift:21-27`).
     */
    private val _isPushEffectivelyEnabled = MutableStateFlow(false)
    val isPushEffectivelyEnabled: StateFlow<Boolean> = _isPushEffectivelyEnabled.asStateFlow()

    /** Relit l'état système et l'abonnement SDK (`NotificationManager.swift:55-64`). */
    fun refresh(context: Context) {
        val granted = PushPermissionCoordinator.isPermissionGranted(context)
        _isPermissionGranted.value = granted
        _isDenied.value = !granted && PushPermissionCoordinator.hasSystemPromptBeenShown
        _isOptedIn.value = PushPermissionCoordinator.isOptedIn
        _isPushEffectivelyEnabled.value = granted && _isOptedIn.value
    }
}
