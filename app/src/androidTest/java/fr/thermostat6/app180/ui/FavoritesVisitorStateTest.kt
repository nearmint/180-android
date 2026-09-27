package fr.thermostat6.app180.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fr.thermostat6.app180.ui.screen.FavoritesVisitorState
import fr.thermostat6.app180.ui.theme._180cTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Carnet hors session — le piège serait d'afficher « Aucun favori », qui se lit
 * comme un carnet vidé par l'app. L'écran doit dire que le carnet appartient au
 * compte, et proposer la connexion (miroir `apple/180/FavoritesView.swift:112-133`).
 */
class FavoritesVisitorStateTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun explique_que_le_carnet_appartient_au_compte() {
        compose.setContent { _180cTheme { FavoritesVisitorState(onLogin = {}) } }

        compose.onNodeWithText("Votre carnet vous attend").assertIsDisplayed()
        compose
            .onNodeWithText("Connectez-vous pour enregistrer", substring = true)
            .assertIsDisplayed()

        // Le libellé de l'état vide **en session** ne doit pas fuiter ici : il
        // ferait croire à un carnet perdu plutôt qu'à une session absente.
        compose.onNodeWithText("Aucun favori pour l'instant").assertDoesNotExist()
    }

    @Test
    fun le_bouton_ouvre_la_connexion() {
        var demandes = 0
        compose.setContent { _180cTheme { FavoritesVisitorState(onLogin = { demandes++ }) } }

        compose.onNodeWithText("Se connecter").performClick()

        assertEquals(1, demandes)
    }
}
