package fr.thermostat6.app180.ui

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RenderedContent
import fr.thermostat6.app180.ui.screen.PaywallContent
import fr.thermostat6.app180.ui.theme._180cTheme
import org.junit.Rule
import org.junit.Test

/**
 * Paywall d'une fiche verrouillée — l'écran le plus contraint de l'app côté
 * boutique : **aucun prix, aucun lien d'achat, aucune URL tappable**, sous
 * peine de facturation hors Google Play. Le CTA porte sur la connexion, pas sur
 * l'abonnement (miroir `apple/180/RecipeDetailView.swift:256-311`).
 *
 * La recette est construite à la main : rien ici ne dépend du réseau.
 */
class RecipePaywallTest {

    @get:Rule
    val compose = createComposeRule()

    private val recetteVerrouillee = Recipe(
        id           = 1234,
        date         = "2026-01-15T09:00:00",
        title        = RenderedContent("Tarte aux pommes"),
        recipeIntro  = "Une tarte de saison, pâte brisée maison.",
        recipeLocked = true
    )

    private fun afficher(isLoggedIn: Boolean) {
        compose.setContent {
            _180cTheme {
                PaywallContent(
                    recipe            = recetteVerrouillee,
                    isLoggedIn        = isLoggedIn,
                    horizontalPadding = 16.dp
                )
            }
        }
    }

    @Test
    fun visiteur_voit_l_apercu_le_verrou_et_le_bouton_de_connexion() {
        afficher(isLoggedIn = false)

        // L'aperçu éditorial reste lisible : la fiche verrouillée n'est pas un mur.
        compose.onNodeWithText("Une tarte de saison", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Contenu réservé aux abonnés").assertIsDisplayed()
        compose.onNodeWithText("Se connecter").assertIsDisplayed()
    }

    /**
     * Déjà connecté mais non abonné : la carte explique toujours le verrou, mais
     * proposer « Se connecter » à qui l'est déjà enverrait l'utilisateur dans un
     * formulaire sans issue.
     */
    @Test
    fun utilisateur_connecte_ne_se_voit_pas_proposer_de_se_reconnecter() {
        afficher(isLoggedIn = true)

        compose.onNodeWithText("Contenu réservé aux abonnés").assertIsDisplayed()
        compose.onNodeWithText("Se connecter").assertDoesNotExist()
    }

    /**
     * La mention du site est du **texte inerte**. La rendre cliquable ferait de
     * l'écran un point de vente hors facturation Google Play.
     */
    @Test
    fun la_mention_du_site_n_est_pas_cliquable() {
        afficher(isLoggedIn = false)

        compose
            .onNodeWithText("L'abonnement est disponible sur notre site", substring = true)
            .assertIsDisplayed()
            .assertHasNoClickAction()
    }
}
