package fr.thermostat6.app180.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.thermostat6.app180.navigation.TabRouter
import fr.thermostat6.app180.ui.components.OfflineBanner
import fr.thermostat6.app180.ui.theme._180cTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Bandeau hors ligne — il ne signale pas seulement l'absence de réseau, il
 * oriente vers le seul contenu encore atteignable : le carnet.
 *
 * Le bandeau est monté seul, sans `NetworkMonitor` : sa visibilité est décidée
 * par [fr.thermostat6.app180.ui.components.WithOfflineBanner], ce qui est
 * testé ici c'est ce qu'il dit et ce que fait son CTA.
 */
class OfflineBannerTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun annonce_l_etat_hors_ligne_et_propose_le_carnet() {
        compose.setContent { _180cTheme { OfflineBanner() } }

        compose.onNodeWithText("Vous êtes hors ligne").assertIsDisplayed()
        compose.onNodeWithText("Voir mon carnet").assertIsDisplayed()
    }

    /**
     * Le CTA ne navigue pas lui-même : il émet une demande sur [TabRouter], que
     * `MainScreen` collecte. Sans cette émission, le bandeau proposerait une
     * porte qui ne s'ouvre pas.
     *
     * Le collecteur démarre en `UNDISPATCHED` — il est donc abonné avant le
     * clic. `TabRouter.requests` n'a aucun replay : une émission sans abonné
     * serait perdue, et le test échouerait au hasard.
     */
    @Test
    fun le_cta_demande_l_onglet_carnet() {
        val recues = mutableListOf<Int>()
        val collecte = CoroutineScope(Dispatchers.Unconfined).launch(
            start = CoroutineStart.UNDISPATCHED
        ) {
            TabRouter.requests.collect { recues += it }
        }

        try {
            compose.setContent { _180cTheme { OfflineBanner() } }
            compose.onNodeWithText("Voir mon carnet").performClick()

            compose.waitUntil(timeoutMillis = 5_000) { recues.isNotEmpty() }
            assertEquals(listOf(TabRouter.Tab.FAVORITES), recues)
        } finally {
            collecte.cancel()
        }
    }
}
