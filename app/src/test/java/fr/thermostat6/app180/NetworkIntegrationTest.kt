package fr.thermostat6.app180

import com.google.gson.Gson
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.WordPressApi
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

/**
 * **Tests d'intégration réseau**, contre un serveur HTTP local.
 *
 * Aucun appel ne sort de la machine : `MockWebServer` sert des réponses écrites
 * ici, ce qui permet de vérifier des comportements — refus d'une redirection,
 * décodage d'une charge utile réelle — qu'aucun test unitaire ne peut atteindre,
 * et sans dépendre de l'état du site.
 */
class NetworkIntegrationTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!
            .bufferedReader().readText()

    /**
     * Client bâti sur **l'intercepteur de production**, pas sur une copie : c'est
     * le garde-fou réellement embarqué qui est mis à l'épreuve.
     */
    private fun guardedClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(ApiClient.redirectGuardInterceptor)
        .build()

    private fun wordPressApi(): WordPressApi = Retrofit.Builder()
        .baseUrl(server.url("/wp-json/wp/v2/"))
        .client(OkHttpClient())
        .addConverterFactory(GsonConverterFactory.create(Gson()))
        .build()
        .create(WordPressApi::class.java)

    // ── Garde-fou anti-redirection (LOT-01) ──────────────────────────────────

    @Test
    fun `une redirection sur POST est refusee, pas suivie`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(301)
                .setHeader("Location", "https://www.180c.fr/wp-json/jwt-auth/v1/token")
        )

        val request = Request.Builder()
            .url(server.url("/wp-json/jwt-auth/v1/token"))
            .post("username=x&password=y".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        try {
            guardedClient().newCall(request).execute()
            fail("la redirection aurait dû être refusée")
        } catch (e: IOException) {
            // Le message doit nommer la cause : sans cela, un hôte non canonique
            // se diagnostique en session de debug plutôt qu'en une ligne de log.
            val message = e.message.orEmpty()
            assertTrue("code HTTP absent du message : $message", message.contains("301"))
            assertTrue("verbe absent du message : $message", message.contains("POST"))
            assertTrue("destination absente du message : $message", message.contains("jwt-auth"))
        }

        // Une seule requête émise : le client n'a pas rejoué la destination.
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `une redirection sur GET est refusee aussi`() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/ailleurs"))

        val request = Request.Builder().url(server.url("/wp-json/wp/v2/recipe")).build()

        try {
            guardedClient().newCall(request).execute()
            fail("la redirection aurait dû être refusée")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("302"))
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `une reponse normale traverse le garde-fou sans encombre`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val request = Request.Builder().url(server.url("/wp-json/wp/v2/recipe")).build()
        guardedClient().newCall(request).execute().use { response ->
            assertEquals(200, response.code)
        }
    }

    // ── Décodage bout en bout ────────────────────────────────────────────────

    @Test
    fun `une liste de recettes est decodee de bout en bout`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(fixture("recipes_embed.json"))
        )

        val recipes = wordPressApi().getRecipes(perPage = 3)

        assertEquals(3, recipes.size)
        val first = recipes.first()
        assertEquals(13400072, first.id)
        assertEquals("Pudding aux tomates et chorizo", first.cleanTitle)
        // Le visuel vient de `_embedded`, pas d'un champ plat : c'est la partie
        // du décodage qui casse en premier si la requête perd `_embed`.
        assertNotNull("visuel absent — `_embed` perdu ?", first.imageURL)

        // La requête émise porte bien les paramètres attendus.
        val recorded = server.takeRequest()
        val path = recorded.path.orEmpty()
        assertTrue("`_embed` absent de $path", path.contains("_embed=true"))
        assertTrue("`per_page` absent de $path", path.contains("per_page=3"))
        assertTrue("chemin inattendu : $path", path.startsWith("/wp-json/wp/v2/recipe"))
    }

    @Test
    fun `une recette unique est decodee avec ses champs ACF`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(fixture("recipe_single_embed.json"))
        )

        val recipe = wordPressApi().getRecipe(recipeId = 13400072)

        assertEquals(13400072, recipe.id)
        assertTrue("le titre devrait être nettoyé du HTML", !recipe.cleanTitle.contains("<"))
        assertTrue("chemin inattendu", server.takeRequest().path.orEmpty().contains("recipe/13400072"))
    }

    @Test
    fun `le permalien resout la recette par slug`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(fixture("recipes_embed.json"))
        )

        wordPressApi().getRecipes(perPage = 1, slug = "pudding-aux-tomates-et-chorizo")

        val path = server.takeRequest().path.orEmpty()
        assertTrue("`slug` absent de $path", path.contains("slug=pudding-aux-tomates-et-chorizo"))
    }

    // ── Erreurs serveur ──────────────────────────────────────────────────────

    @Test
    fun `un 401 remonte en HttpException porteuse du code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"jwt_auth_invalid_token"}"""))

        try {
            wordPressApi().getUserProfile()
            fail("un 401 aurait dû lever")
        } catch (e: HttpException) {
            // C'est précisément ce code que `AuthService.refreshTokenIfNeeded`
            // teste pour forcer la déconnexion (`AuthService.kt:237`).
            assertEquals(401, e.code())
        }
    }

    @Test
    fun `un 500 remonte aussi en HttpException, sans etre confondu avec un 401`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        try {
            wordPressApi().getRecipes()
            fail("un 500 aurait dû lever")
        } catch (e: HttpException) {
            assertEquals(500, e.code())
        }
    }
}
