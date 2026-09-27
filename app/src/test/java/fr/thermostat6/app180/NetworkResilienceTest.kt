package fr.thermostat6.app180

import com.google.gson.Gson
import com.google.gson.JsonParseException
import fr.thermostat6.app180.data.model.AppVersionInfo
import fr.thermostat6.app180.data.network.ApiError
import fr.thermostat6.app180.data.network.RetryPolicy
import fr.thermostat6.app180.data.service.AppVersionService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Robustesse réseau : erreurs typées, retry borné, comparaison de versions.
 *
 * Les messages sont comparés **chaîne à chaîne** aux valeurs recopiées du Swift
 * (`apple/180/Networking.swift:100-127`) : une reformulation involontaire côté
 * Android casserait le test.
 */
class NetworkResilienceTest {

    private val gson = Gson()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Fixture manquante : $name"
        }.bufferedReader().use { it.readText() }

    private fun httpException(code: Int) = HttpException(
        Response.error<Any>(code, "".toResponseBody("application/json".toMediaType()))
    )

    // ── Correspondance erreur → ApiError ─────────────────────────────────────

    @Test
    fun `les exceptions reseau se normalisent dans le bon cas`() {
        assertEquals(ApiError.Timeout, ApiError.from(SocketTimeoutException()))
        assertEquals(ApiError.Offline, ApiError.from(UnknownHostException("host")))
        assertEquals(ApiError.DecodingError, ApiError.from(JsonParseException("bad json")))
        assertEquals(ApiError.Http(404), ApiError.from(httpException(404)))
        assertEquals(ApiError.Http(503), ApiError.from(httpException(503)))
        assertEquals(ApiError.ServerError, ApiError.from(IllegalStateException("?")))
    }

    @Test
    fun `une IOException de connectivite se classe hors-ligne`() {
        assertEquals(ApiError.Offline, ApiError.from(IOException("Unable to resolve host www.180c.fr")))
        assertEquals(ApiError.Offline, ApiError.from(IOException("Failed to connect to /127.0.0.1:443")))
    }

    @Test
    fun `le refus de redirection du lot dédié n'est pas classe hors-ligne`() {
        // Message produit par le garde-fou anti-redirection (ApiClient.kt).
        val guard = IOException(
            "Redirection inattendue (301) sur POST https://www.180c.fr/ → https://www.180c.fr/x."
        )
        val mapped = ApiError.from(guard)

        assertEquals(ApiError.ServerError, mapped)
        // Non transitoire ⇒ jamais rejoué : l'erreur remonte au premier essai.
        assertFalse(mapped.isTransient)
    }

    @Test
    fun `un ApiError deja type est renvoye tel quel`() {
        assertEquals(ApiError.Timeout, ApiError.from(ApiError.Timeout))
    }

    // ── Messages français, comparés au Swift ─────────────────────────────────

    @Test
    fun `les messages utilisateur sont ceux de l'iOS`() {
        assertEquals(
            "Pas de connexion Internet. Vérifiez votre réseau.",
            ApiError.Offline.userMessage
        )
        assertEquals(
            "La connexion est trop lente. Réessayez.",
            ApiError.Timeout.userMessage
        )
        // `default:` du Swift — tous les autres cas partagent ce message.
        val fallback = "Impossible de charger les données. Réessayez plus tard."
        assertEquals(fallback, ApiError.ServerError.userMessage)
        assertEquals(fallback, ApiError.DecodingError.userMessage)
        assertEquals(fallback, ApiError.Http(500).userMessage)
    }

    @Test
    fun `les descriptions techniques sont celles de l'iOS`() {
        assertEquals("Pas de connexion Internet", ApiError.Offline.description)
        assertEquals("La requête a expiré", ApiError.Timeout.description)
        assertEquals("Erreur serveur (503)", ApiError.Http(503).description)
        assertEquals("Erreur serveur", ApiError.ServerError.description)
        assertEquals("Erreur de décodage", ApiError.DecodingError.description)
    }

    @Test
    fun `seules les erreurs transitoires sont rejouables`() {
        assertTrue(ApiError.Offline.isTransient)
        assertTrue(ApiError.Timeout.isTransient)
        assertTrue(ApiError.Http(500).isTransient)
        assertTrue(ApiError.Http(503).isTransient)
        // Un 4xx est un refus durable.
        assertFalse(ApiError.Http(404).isTransient)
        assertFalse(ApiError.Http(401).isTransient)
        assertFalse(ApiError.DecodingError.isTransient)
        assertFalse(ApiError.ServerError.isTransient)
    }

    // ── Grille de décision du retry ──────────────────────────────────────────

    @Test
    fun `un GET transitoire est rejoue au plus deux fois`() {
        assertTrue(RetryPolicy.shouldRetry(isRead = true, attempt = 0, error = ApiError.Timeout))
        assertTrue(RetryPolicy.shouldRetry(isRead = true, attempt = 1, error = ApiError.Timeout))
        // 3 essais au total : la 3e tentative ne rejoue plus.
        assertFalse(RetryPolicy.shouldRetry(isRead = true, attempt = 2, error = ApiError.Timeout))
    }

    @Test
    fun `un GET en 404 n'est jamais rejoue`() {
        assertFalse(RetryPolicy.shouldRetry(isRead = true, attempt = 0, error = ApiError.Http(404)))
    }

    @Test
    fun `une ecriture n'est jamais rejouee meme sur erreur transitoire`() {
        assertFalse(RetryPolicy.shouldRetry(isRead = false, attempt = 0, error = ApiError.Timeout))
        assertFalse(RetryPolicy.shouldRetry(isRead = false, attempt = 0, error = ApiError.Offline))
        assertFalse(RetryPolicy.shouldRetry(isRead = false, attempt = 0, error = ApiError.Http(500)))
    }

    @Test
    fun `une redirection refusee n'est jamais rejouee`() {
        val guard = ApiError.from(IOException("Redirection inattendue (301) sur POST …"))
        assertFalse(RetryPolicy.shouldRetry(isRead = true, attempt = 0, error = guard))
    }

    @Test
    fun `le backoff est de 1s puis 3s`() {
        assertEquals(2, RetryPolicy.MAX_RETRIES)
        assertEquals(listOf(1_000L, 3_000L), RetryPolicy.BACKOFF_MS.toList())
    }

    // ── app-version ──────────────────────────────────────────────────────────

    @Test
    fun `la fixture app-version reelle se decode sur le bloc android`() {
        val info = gson.fromJson(fixture("app_version.json"), AppVersionInfo::class.java)

        assertEquals("1.0.0", info.android?.min)
        assertEquals("1.0.0", info.android?.current)
    }

    @Test
    fun `la comparaison de versions est semantique`() {
        // Le cas que la comparaison lexicographique rate.
        assertTrue(AppVersionService.isVersionLessThan("1.0.9", "1.0.10"))
        assertFalse(AppVersionService.isVersionLessThan("1.0.10", "1.0.9"))

        assertTrue(AppVersionService.isVersionLessThan("1.0.0", "1.1.0"))
        assertTrue(AppVersionService.isVersionLessThan("0.9.9", "1.0.0"))
        assertFalse(AppVersionService.isVersionLessThan("2.0.0", "1.9.9"))
        // Égalité : la version installée n'est pas inférieure.
        assertFalse(AppVersionService.isVersionLessThan("1.0.0", "1.0.0"))
    }

    @Test
    fun `les segments manquants valent zero`() {
        assertFalse(AppVersionService.isVersionLessThan("1.0", "1.0.0"))
        assertFalse(AppVersionService.isVersionLessThan("1", "1.0.0"))
        assertTrue(AppVersionService.isVersionLessThan("1.0", "1.0.1"))
    }

    @Test
    fun `les segments non numeriques sont ecartes sans planter`() {
        assertFalse(AppVersionService.isVersionLessThan("1.0.0-beta", "1.0.0"))
        assertFalse(AppVersionService.isVersionLessThan("", ""))
    }

    @Test
    fun `un payload app-version invalide ne fournit aucun minimum`() {
        // Fail-open : sans `android.min`, le service ne peut rien bloquer.
        assertNull(gson.fromJson("{}", AppVersionInfo::class.java).android?.min)
        assertNull(gson.fromJson("""{"ios":{"min":"9.9.9"}}""", AppVersionInfo::class.java).android?.min)
        assertNull(gson.fromJson("""{"android":{}}""", AppVersionInfo::class.java).android?.min)
    }

    @Test
    fun `le message de mise a jour forcee est celui de l'iOS`() {
        assertEquals(
            "Une mise à jour est requise pour continuer à utiliser l'app.",
            AppVersionService.FORCE_UPDATE_MESSAGE
        )
    }
}
