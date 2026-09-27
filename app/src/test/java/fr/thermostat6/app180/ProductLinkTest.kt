package fr.thermostat6.app180

import fr.thermostat6.app180.data.web.WebSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ouverture d'une notification de type `product`.
 *
 * Couvre l'extraction du chemin relatif — miroir de `relativeTarget(of:)`
 * (`apple/180/ContentView.swift:170-178`) — et la construction du lien
 * d'ouverture, connecté comme visiteur (`apple/180/ContentView.swift:143-161`).
 *
 * Fonctions **pures** : ni réseau, ni framework Android, comme
 * [WebSessionTest] dont ce fichier prolonge la couverture.
 */
class ProductLinkTest {

    private val origin = "https://www.180c.fr"

    // ── Chemin relatif ───────────────────────────────────────────────────────

    @Test
    fun `un chemin nu est extrait tel quel`() {
        assertEquals("/boutique/hors-serie/", WebSession.relativeTarget("$origin/boutique/hors-serie/"))
    }

    @Test
    fun `la requete est conservee`() {
        assertEquals(
            "/boutique/?variante=12",
            WebSession.relativeTarget("$origin/boutique/?variante=12")
        )
    }

    @Test
    fun `le fragment est conserve`() {
        assertEquals("/boutique/#formats", WebSession.relativeTarget("$origin/boutique/#formats"))
    }

    @Test
    fun `requete et fragment sont conserves ensemble et dans l'ordre`() {
        assertEquals(
            "/boutique/?variante=12#formats",
            WebSession.relativeTarget("$origin/boutique/?variante=12#formats")
        )
    }

    @Test
    fun `un hote nu donne la racine`() {
        assertEquals("/", WebSession.relativeTarget(origin))
        assertEquals("/", WebSession.relativeTarget("$origin/"))
    }

    @Test
    fun `une requete ou un fragment colles a l'hote sont recolles sur la racine`() {
        // Miroir du `comps.path.isEmpty ? "/"` de `relativeTarget(of:)`.
        assertEquals("/?p=42", WebSession.relativeTarget("$origin?p=42"))
        assertEquals("/#haut", WebSession.relativeTarget("$origin#haut"))
    }

    @Test
    fun `le schema http est traite comme https`() {
        assertEquals("/boutique/", WebSession.relativeTarget("http://www.180c.fr/boutique/"))
    }

    @Test
    fun `une entree deja relative est normalisee sur un slash initial`() {
        assertEquals("/boutique/", WebSession.relativeTarget("/boutique/"))
        assertEquals("/boutique/", WebSession.relativeTarget("boutique/"))
        assertEquals("/", WebSession.relativeTarget(""))
    }

    // ── Lien d'ouverture ─────────────────────────────────────────────────────

    @Test
    fun `sans jeton la page produit publique est ouverte telle quelle`() {
        val link = WebSession.productLink("$origin/boutique/hors-serie/", token = null)

        assertEquals("$origin/boutique/hors-serie/", link)
        // Aucun amorçage : ce n'est pas un lien autologin.
        assertFalse(WebSession.isAutoLoginLink(link))
    }

    @Test
    fun `un jeton vide vaut absence de jeton`() {
        assertEquals(
            "$origin/boutique/",
            WebSession.productLink("$origin/boutique/", token = "")
        )
    }

    @Test
    fun `avec jeton le lien est un autologin vers la page produit`() {
        val link = WebSession.productLink("$origin/boutique/hors-serie/", token = "jwt-abc")

        assertTrue(WebSession.isAutoLoginLink(link))
        assertTrue(link.startsWith("$origin/?180c_app_login=1&redirect="))
        assertTrue(link.endsWith("redirect=/boutique/hors-serie/"))
        // Le JWT n'apparaît JAMAIS dans l'URL (`InAppBrowserView.swift:18-19`).
        assertFalse(link.contains("jwt-abc"))
        assertFalse(link.contains("jwt="))
    }

    @Test
    fun `la destination du lien autologin est bien la page produit`() {
        val productUrl = "$origin/boutique/?variante=12#formats"
        val link = WebSession.productLink(productUrl, token = "jwt-abc")

        assertEquals(productUrl, WebSession.resolveTarget(link))
    }

    @Test
    fun `aucun lien produit ne pointe vers un parcours d'abonnement`() {
        // Exception Google Play : la page produit est la boutique, jamais un
        // parcours d'abonnement.
        val link = WebSession.productLink("$origin/boutique/hors-serie/", token = "jwt-abc")
        assertFalse(link.contains("abonnement/"))
    }
}
