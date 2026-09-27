package fr.thermostat6.app180

import com.google.gson.Gson
import fr.thermostat6.app180.data.model.NewsletterRequest
import fr.thermostat6.app180.data.model.NewsletterResponse
import fr.thermostat6.app180.data.model.NewsletterServerError
import fr.thermostat6.app180.data.model.NewsletterStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrat du proxy newsletter `180c/v1/newsletter/subscribe`.
 *
 * Fixture **dérivée du Swift** (`apple/180/NewsletterService.swift:113-124`) et
 * non capturée : le proxy exige un JWT, et le brief interdit tout appel live.
 */
class NewsletterContractTest {

    private val gson = Gson()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Fixture manquante : $name"
        }.bufferedReader().use { it.readText() }

    // ── Réponse ──────────────────────────────────────────────────────────────

    @Test
    fun `la reponse du proxy se decode`() {
        val res = gson.fromJson(fixture("newsletter_status.json"), NewsletterResponse::class.java)

        assertEquals("a1b2c3d4e5", res.listId)
        assertEquals("status", res.action)
        assertEquals("subscribed", res.status)
    }

    @Test
    fun `une reponse tronquee ne leve pas`() {
        assertNull(gson.fromJson("{}", NewsletterResponse::class.java).status)
        assertNull(gson.fromJson("""{"status":null}""", NewsletterResponse::class.java).status)
    }

    @Test
    fun `l'enveloppe d'erreur porte le code serveur`() {
        val err = gson.fromJson(
            """{"code":"email_mismatch","message":"…"}""",
            NewsletterServerError::class.java
        )
        assertEquals("email_mismatch", err.code)
    }

    // ── Statuts ──────────────────────────────────────────────────────────────

    @Test
    fun `pending compte comme inscrit`() {
        // Le double opt-in place tout nouveau membre en attente : c'est un
        // opt-in réel (NewsletterService.swift:10-13).
        assertTrue(NewsletterStatus.PENDING.isSubscribed)
        assertTrue(NewsletterStatus.SUBSCRIBED.isSubscribed)
        assertFalse(NewsletterStatus.UNSUBSCRIBED.isSubscribed)
    }

    @Test
    fun `les statuts se resolvent depuis leur valeur serveur`() {
        assertEquals(NewsletterStatus.SUBSCRIBED, NewsletterStatus.fromValue("subscribed"))
        assertEquals(NewsletterStatus.UNSUBSCRIBED, NewsletterStatus.fromValue("unsubscribed"))
        assertEquals(NewsletterStatus.PENDING, NewsletterStatus.fromValue("pending"))
        // Statut inconnu → null, l'appelant échoue en `decode_error` plutôt que
        // de deviner (NewsletterService.swift:79-82).
        assertNull(NewsletterStatus.fromValue("autre_chose"))
        assertNull(NewsletterStatus.fromValue(null))
    }

    // ── Corps de requête ─────────────────────────────────────────────────────

    @Test
    fun `le corps porte email et action, sans list_id`() {
        val json = gson.toJson(NewsletterRequest(email = "a@example.com", action = "subscribe"))

        assertEquals("""{"email":"a@example.com","action":"subscribe"}""", json)
        // `list_id` volontairement OMIS : le serveur applique son audience unique
        // (NewsletterService.swift:32-37).
        assertFalse(json.contains("list_id"))
        // Héritages INIT-89 sans équivalent iOS, supprimés.
        assertFalse(json.contains("consent"))
        assertFalse(json.contains("source"))
        assertFalse(json.contains("lang"))
    }
}
