package fr.thermostat6.app180

import com.google.gson.Gson
import fr.thermostat6.app180.data.model.MeResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Décodage de la réponse `GET /wp-json/180c/v1/me`.
 *
 * La fixture est **dérivée du Swift** (`apple/180/AuthService.swift:410-424`) et
 * non capturée : l'endpoint exige un JWT, et le brief interdit tout appel
 * authentifié. Les clés testées sont donc celles des `CodingKeys` iOS.
 */
class MeResponseDecodingTest {

    private val gson = Gson()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Fixture manquante : $name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `la reponse d'un abonne se decode`() {
        val me = gson.fromJson(fixture("me_subscriber.json"), MeResponse::class.java)

        assertTrue(me.isSubscriber)
        assertEquals("abonne@example.com", me.email)
        assertEquals("Jean Dupont", me.displayName)
        assertEquals(4242, me.id)
    }

    @Test
    fun `un non-abonne se decode`() {
        val json = """{"is_subscriber":false,"email":"visiteur@example.com","display_name":"Visiteur","id":7}"""
        val me = gson.fromJson(json, MeResponse::class.java)

        assertFalse(me.isSubscriber)
        assertEquals("visiteur@example.com", me.email)
    }

    // ── Replis sûrs ──────────────────────────────────────────────────────────

    @Test
    fun `is_subscriber absent retombe sur non-abonne`() {
        val me = gson.fromJson("""{"email":"x@example.com","display_name":"X"}""", MeResponse::class.java)

        assertFalse("un champ absent ne doit jamais déverrouiller du premium", me.isSubscriber)
        assertEquals("x@example.com", me.email)
    }

    @Test
    fun `is_subscriber null retombe sur non-abonne`() {
        val me = gson.fromJson("""{"is_subscriber":null,"email":null}""", MeResponse::class.java)

        assertFalse(me.isSubscriber)
        assertEquals("", me.email)
    }

    @Test
    fun `une reponse vide retombe sur non-abonne`() {
        val me = gson.fromJson("{}", MeResponse::class.java)

        assertFalse(me.isSubscriber)
        assertEquals("", me.email)
        assertNull(me.displayName)
        assertNull(me.id)
    }

    @Test
    fun `id absent reste null sans invalider le reste`() {
        // `id` est déjà optionnel côté iOS (AuthService.swift:414-416) : le thème
        // ne le sert pas nécessairement.
        val me = gson.fromJson(
            """{"is_subscriber":true,"email":"a@example.com","display_name":"A"}""",
            MeResponse::class.java
        )

        assertNull(me.id)
        assertTrue(me.isSubscriber)
    }
}
