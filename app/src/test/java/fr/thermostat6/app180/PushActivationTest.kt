package fr.thermostat6.app180

import fr.thermostat6.app180.data.push.PushPermissionCoordinator
import fr.thermostat6.app180.data.push.PushPermissionCoordinator.ActivationAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Matrice du CTA « Activer les notifications » — miroir d'`handleActivationCTA`
 * (`apple/180/NotificationManager.swift:104-118`).
 *
 * Fonction **pure** : `activationAction(isGranted, hasPromptBeenShown)` est
 * testée sans `Context`, comme `shouldShowSoftAsk`.
 */
class PushActivationTest {

    @Test
    fun `permission indeterminee declenche le prompt systeme`() {
        assertEquals(
            ActivationAction.REQUEST_PERMISSION,
            PushPermissionCoordinator.activationAction(
                isGranted = false,
                hasPromptBeenShown = false
            )
        )
    }

    @Test
    fun `permission accordee mais desabonne fait un opt-in direct`() {
        // Le cas d'un opt-out depuis Mon compte : reposer le prompt système
        // n'afficherait rien, seul l'opt-in OneSignal est opérant.
        assertEquals(
            ActivationAction.OPT_IN,
            PushPermissionCoordinator.activationAction(
                isGranted = true,
                hasPromptBeenShown = true
            )
        )
    }

    @Test
    fun `permission accordee sans prompt prealable fait aussi un opt-in direct`() {
        // En deçà d'Android 13 : aucune permission runtime, donc aucun prompt.
        assertEquals(
            ActivationAction.OPT_IN,
            PushPermissionCoordinator.activationAction(
                isGranted = true,
                hasPromptBeenShown = false
            )
        )
    }

    @Test
    fun `permission refusee ouvre les reglages systeme`() {
        assertEquals(
            ActivationAction.OPEN_SETTINGS,
            PushPermissionCoordinator.activationAction(
                isGranted = false,
                hasPromptBeenShown = true
            )
        )
    }
}
