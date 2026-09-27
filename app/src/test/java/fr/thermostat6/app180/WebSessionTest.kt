package fr.thermostat6.app180

import fr.thermostat6.app180.data.web.WebSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Construction des liens autologin et résolution de leur destination.
 *
 * Miroir d'`APIConfig.autoLoginLink` (`apple/180/APIConfig.swift:95-100`) et de
 * `WebSession.prime`/`resolveTarget` (`apple/180/InAppBrowserView.swift:27-50`,
 * `:105-110`).
 *
 * Ces fonctions sont **pures** — ni réseau, ni framework Android : aucun appel
 * live n'est effectué, conformément à l'interdiction de tester l'autologin en
 * vrai (il implique un jeton), et aucune dépendance de test n'est ajoutée.
 */
class WebSessionTest {

    private val origin = "https://www.180c.fr"

    // ── Construction du lien ─────────────────────────────────────────────────

    @Test
    fun `sans jeton le lien reste un lien web simple`() {
        assertEquals(
            "$origin/mon-compte/",
            WebSession.autoLoginLink("/mon-compte/", token = null)
        )
        assertEquals(
            "$origin/mon-compte/",
            WebSession.autoLoginLink("/mon-compte/", token = "")
        )
    }

    @Test
    fun `avec jeton le lien porte le marqueur et la destination`() {
        val link = WebSession.autoLoginLink("/mon-compte/#abonnement", token = "jwt-abc")

        assertTrue(link.startsWith("$origin/?180c_app_login=1&redirect="))
        // Le JWT n'apparaît JAMAIS dans l'URL (InAppBrowserView.swift:18-19).
        assertFalse(link.contains("jwt-abc"))
        assertFalse(link.contains("jwt="))
    }

    @Test
    fun `un chemin sans slash initial est normalise`() {
        val link = WebSession.autoLoginLink("mon-compte/", token = "t")
        // `/` fait partie des caractères autorisés en query (miroir urlQueryAllowed).
        assertTrue(link.endsWith("redirect=/mon-compte/"))
    }

    // ── Détection ────────────────────────────────────────────────────────────

    @Test
    fun `un lien autologin est reconnu`() {
        assertTrue(WebSession.isAutoLoginLink("$origin/?180c_app_login=1&redirect=%2Fx"))
        assertFalse(WebSession.isAutoLoginLink("$origin/mentions-legales/"))
        assertFalse(WebSession.isAutoLoginLink("pas une url"))
    }

    // ── Résolution de la destination ─────────────────────────────────────────

    @Test
    fun `la destination relative est recollee sur l'origine`() {
        assertEquals(
            "$origin/mon-compte/",
            WebSession.resolveTarget("$origin/?180c_app_login=1&redirect=%2Fmon-compte%2F")
        )
    }

    @Test
    fun `une destination absolue est prise telle quelle`() {
        assertEquals(
            "https://www.180c.fr/boutique/",
            WebSession.resolveTarget(
                "$origin/?180c_app_login=1&redirect=https%3A%2F%2Fwww.180c.fr%2Fboutique%2F"
            )
        )
    }

    @Test
    fun `sans parametre redirect l'url est renvoyee telle quelle`() {
        val url = "$origin/?180c_app_login=1"
        assertEquals(url, WebSession.resolveTarget(url))
    }

    @Test
    fun `l'aller-retour construction puis resolution retrouve le chemin`() {
        val path = "/mon-compte/#abonnement"
        val link = WebSession.autoLoginLink(path, token = "t")
        assertEquals("$origin$path", WebSession.resolveTarget(link))
    }
}
