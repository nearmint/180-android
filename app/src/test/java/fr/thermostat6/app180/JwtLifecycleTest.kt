package fr.thermostat6.app180

import fr.thermostat6.app180.data.auth.JwtUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Lecture de l'expiration d'un JWT — le seul critère qui décide si une session
 * est encore vivante, indépendamment de ce que répond le plugin serveur.
 *
 * Un faux négatif ici (jeton valide jugé illisible) déconnecte un abonné à
 * tort ; un faux positif (jeton mort jugé lisible et vivant) le laisse envoyer
 * un `Authorization` que le middleware JWT rejette, ce qui fait échouer
 * **toutes** les lectures et vide l'accueil de ses rails.
 *
 * Miroir d'`apple/180Tests/TokenExpirationTests.swift`.
 */
class JwtLifecycleTest {

    private val day = 86_400L

    private fun now() = System.currentTimeMillis() / 1000L

    /**
     * Forge un JWT `header.payload.signature` en **base64url** (RFC 7515),
     * comme les émet le serveur : `+`/`/` remplacés, padding `=` retiré.
     */
    private fun makeJwt(payload: String): String {
        val encode = { raw: String ->
            Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
        }
        return "${encode("""{"typ":"JWT","alg":"HS256"}""")}.${encode(payload)}.signature"
    }

    // ── Décodage ─────────────────────────────────────────────────────────────

    @Test
    fun `jeton valide - claims restitues`() {
        val exp = now() + 30 * day
        val token = makeJwt("""{"exp":$exp,"email":"jean@example.com","username":"jean"}""")

        val payload = JwtUtils.decodePayload(token)

        assertNotNull(payload)
        assertEquals(exp, payload!!.exp)
        assertEquals("jean@example.com", payload.email)
        assertEquals("jean", payload.username)
    }

    /**
     * Cœur de la régression : un payload contenant `-` ou `_` (base64url) était
     * illisible avec un décodage base64 standard. Le jeton n'était alors ni
     * rafraîchi ni purgé — il survivait indéfiniment en keychain.
     */
    @Test
    fun `payload base64url avec tirets et underscores - decode`() {
        val exp = now() + day
        val token = makeJwt("""{"exp":$exp,"email":"a+b/c?d=e@example.com","n":"~ÿ"}""")

        val encodedPayload = token.split(".")[1]
        assertTrue(
            "le payload forgé doit exercer le base64url",
            encodedPayload.contains("-") || encodedPayload.contains("_")
        )
        assertNotNull(JwtUtils.decodePayload(token))
    }

    /**
     * Revers du cas précédent, et raison pour laquelle le décodage convertit
     * base64url → base64 standard au lieu d'employer `getUrlDecoder()`
     * (`JwtUtils.kt:61-64`, miroir d'`AuthService.swift:277-284`) : un payload
     * déjà encodé en base64 **standard** — donc porteur de `+` ou `/` — reste
     * lisible, là où le décodeur URL le rejetterait. Le serveur n'est pas censé
     * en émettre, mais un jeton illisible coûte une déconnexion à tort.
     */
    @Test
    fun `payload en base64 standard - decode aussi`() {
        // `exp` figé (1er janvier 2100) : c'est l'encodage exact qui est
        // exercé ici, il ne doit pas varier avec l'horloge du test.
        val json = """{"exp":4102444800,"username":"Zoé","email":"zoe@example.com"}"""
        val standard = Base64.getEncoder().encodeToString(json.toByteArray())
        assertTrue(
            "le payload forgé doit exercer le base64 standard",
            standard.contains("+") || standard.contains("/")
        )

        val payload = JwtUtils.decodePayload("header.$standard.signature")

        assertNotNull(payload)
        assertEquals(4_102_444_800L, payload!!.exp)
        assertEquals("Zoé", payload.username)
        assertFalse(payload.isExpired())
    }

    @Test
    fun `exp en chaine numerique - accepte par robustesse`() {
        val exp = now() + day
        val payload = JwtUtils.decodePayload(makeJwt("""{"exp":"$exp"}"""))

        assertNotNull(payload)
        assertEquals(exp, payload!!.exp)
        assertFalse(payload.isExpired())
    }

    @Test
    fun `jetons illisibles - null, la session sera purgee`() {
        val cas = listOf(
            "chaîne vide"            to "",
            "pas trois segments"     to "abc.def",
            "quatre segments"        to "a.b.c.d",
            "payload non base64"     to "aaa.??????.ccc",
            "payload non JSON"       to makeJwt("pas du json"),
            "payload JSON non objet" to makeJwt("[1,2]")
        )

        cas.forEach { (nom, token) ->
            assertNull("$nom devrait être illisible", JwtUtils.decodePayload(token))
        }
    }

    /**
     * L'iOS renvoie `nil` pour ces deux cas et purge la session
     * (`AuthService.swift:290-292`). Android décode le payload — il porte aussi
     * l'e-mail et le nom, utiles au login — mais retient `exp = 0`, ce qui le
     * rend **expiré** : l'issue est la même, la session est purgée.
     */
    @Test
    fun `exp absent ou non numerique - considere expire`() {
        listOf(
            """{"id":42}""",
            """{"exp":"jamais"}""",
            """{"exp":null}"""
        ).forEach { json ->
            val payload = JwtUtils.decodePayload(makeJwt(json))
            assertNotNull(json, payload)
            assertEquals(json, 0L, payload!!.exp)
            assertTrue("$json doit être jugé expiré", payload.isExpired())
            assertTrue("$json doit être jugé expirant", payload.isExpiringSoon(days = 7))
        }
    }

    // ── Expiration ───────────────────────────────────────────────────────────

    /**
     * Les bornes sont approchées à une minute près, jamais à la seconde :
     * l'horloge avance entre la forge du jeton et la lecture, et un test calé
     * exactement sur le seuil basculerait au hasard.
     */
    private val margin = 60L

    @Test
    fun `isExpired - avant et apres l echeance`() {
        assertFalse(payloadWithExp(now() + margin).isExpired())
        assertTrue(payloadWithExp(now() - margin).isExpired())
    }

    @Test
    fun `isExpiringSoon 7 jours - de part et d autre du seuil`() {
        // Juste en deçà du seuil : le refresh doit être tenté.
        assertTrue(payloadWithExp(now() + 7 * day - margin).isExpiringSoon(days = 7))
        // Juste au-delà : rien à faire, la session a encore des jours à vivre.
        assertFalse(payloadWithExp(now() + 7 * day + margin).isExpiringSoon(days = 7))
    }

    @Test
    fun `isExpiringSoon - un jeton deja expire expire aussi bientot`() {
        val payload = payloadWithExp(now() - day)
        assertTrue(payload.isExpired())
        assertTrue(payload.isExpiringSoon(days = 7))
    }

    private fun payloadWithExp(exp: Long): JwtUtils.JwtPayload {
        val payload = JwtUtils.decodePayload(makeJwt("""{"exp":$exp}"""))
        assertNotNull("jeton forgé avec exp=$exp", payload)
        return payload!!
    }
}
