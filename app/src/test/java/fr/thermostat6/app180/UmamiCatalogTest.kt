package fr.thermostat6.app180

import com.google.gson.JsonParser
import fr.thermostat6.app180.data.analytics.UmamiEvents
import fr.thermostat6.app180.data.analytics.UmamiParams
import fr.thermostat6.app180.data.analytics.UmamiScreens
import fr.thermostat6.app180.data.analytics.UmamiTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Verrou anti-dérive cross-plateforme (Umami).**
 *
 * La propriété Umami est partagée entre le site, l'iOS et l'Android : un nom
 * d'event ou un chemin d'écran qui diverge d'une lettre crée une entrée
 * distincte dans les rapports et rend les plateformes incomparables. Chaque
 * chaîne est donc comparée à l'inventaire de référence
 * (`apple/180/UmamiTracker.swift` et ses points d'appel,
 * `web/…/src/js/modules/umami-events.js`).
 *
 * Le corps JSON est verrouillé au même titre : `hostname` et `tag` sont ce qui
 * isole le trafic Android dans la propriété commune, et une page vue doit partir
 * **sans** clé `name`.
 */
class UmamiCatalogTest {

    // ── Noms d'events ────────────────────────────────────────────────────────

    @Test
    fun `noms d'events identiques a iOS et au web`() {
        assertEquals("app_first_open", UmamiEvents.APP_FIRST_OPEN)
        assertEquals("login_success", UmamiEvents.LOGIN_SUCCESS)
        assertEquals("login_error", UmamiEvents.LOGIN_ERROR)
        assertEquals("push_optin", UmamiEvents.PUSH_OPTIN)
        assertEquals("push_open", UmamiEvents.PUSH_OPEN)
        assertEquals("recipe_search", UmamiEvents.RECIPE_SEARCH)
        assertEquals("recipe_favorite", UmamiEvents.RECIPE_FAVORITE)
        assertEquals("recipe_share", UmamiEvents.RECIPE_SHARE)
    }

    @Test
    fun `cles de data identiques a iOS et au web`() {
        assertEquals("reason", UmamiParams.REASON)
        assertEquals("accepted", UmamiParams.ACCEPTED)
        assertEquals("campaign", UmamiParams.CAMPAIGN)
        assertEquals("query", UmamiParams.QUERY)
        assertEquals("recipe_id", UmamiParams.RECIPE_ID)
        assertEquals("channel", UmamiParams.CHANNEL)
        assertEquals("share_sheet", UmamiParams.CHANNEL_SHARE_SHEET)
        // `MAX_QUERY_LENGTH` du module web et `prefix(50)` du Swift.
        assertEquals(50, UmamiParams.MAX_QUERY_LENGTH)
    }

    // ── Mapping écran → url ──────────────────────────────────────────────────

    @Test
    fun `chemins d'ecrans calques sur le site et sur iOS`() {
        assertEquals("/", UmamiScreens.HOME_PATH)
        assertEquals("/recettes", UmamiScreens.RECIPE_LIST_PATH)
        assertEquals("/recherche", UmamiScreens.SEARCH_PATH)
        assertEquals("/carnet", UmamiScreens.FAVORITES_PATH)
        assertEquals("/compte", UmamiScreens.ACCOUNT_PATH)
        assertEquals("/connexion", UmamiScreens.LOGIN_PATH)
        assertEquals("/notifications", UmamiScreens.NOTIFICATIONS_PATH)
        assertEquals("/onboarding", UmamiScreens.ONBOARDING_PATH)
    }

    @Test
    fun `fiche recette privilegie le slug et retombe sur l'id`() {
        assertEquals("/recette/tarte-citron", UmamiScreens.recipePath("tarte-citron", 42))
        assertEquals("/recette/42", UmamiScreens.recipePath(null, 42))
        // Slug vide = slug absent : jamais d'url `/recette/`.
        assertEquals("/recette/42", UmamiScreens.recipePath("", 42))
    }

    // ── Corps de requête ─────────────────────────────────────────────────────

    private fun payloadOf(json: String) =
        JsonParser.parseString(json).asJsonObject.getAsJsonObject("payload")

    @Test
    fun `page vue part sans champ name`() {
        val body = UmamiTracker.buildBody(
            url = "/recettes",
            title = "Recettes",
            referrer = "/",
            name = null,
            data = null,
            language = "fr-FR",
            screen = "412x915"
        )
        val payload = payloadOf(body)

        assertEquals("event", JsonParser.parseString(body).asJsonObject.get("type").asString)
        assertFalse("une page vue ne doit porter aucun `name`", payload.has("name"))
        assertFalse("une page vue ne doit porter aucun `data`", payload.has("data"))
        assertEquals("/recettes", payload.get("url").asString)
        assertEquals("Recettes", payload.get("title").asString)
        assertEquals("/", payload.get("referrer").asString)
        assertEquals("fr-FR", payload.get("language").asString)
        assertEquals("412x915", payload.get("screen").asString)
    }

    @Test
    fun `chaque hit porte l'identite de la propriete commune et le tag Android`() {
        val payload = payloadOf(
            UmamiTracker.buildBody(
                url = "/",
                title = "Accueil",
                referrer = "",
                name = null,
                data = null,
                language = "fr-FR",
                screen = "412x915"
            )
        )
        // ID partagé web / iOS / Android : `apple/180/UmamiTracker.swift` et
        // `_180C_UMAMI_WEBSITE_ID` côté thème.
        assertEquals("94b264d5-6963-4678-9d7f-316583113c91", payload.get("website").asString)
        // Ce couple est la SEULE chose qui distingue les hits Android dans la
        // propriété commune. Le retirer rend le trafic app indissociable du web.
        assertEquals("android.180c.fr", payload.get("hostname").asString)
        assertEquals("android", payload.get("tag").asString)
    }

    // ── Statut abonné (`is_subscriber`) ──────────────────────────────────────

    @Test
    fun `tout event porte le statut abonne en texte`() {
        assertEquals("is_subscriber", UmamiParams.IS_SUBSCRIBER)

        // Un event sans donnée propre le porte quand même : la segmentation
        // doit valoir sur le catalogue entier.
        assertEquals(
            mapOf("is_subscriber" to "false"),
            UmamiTracker.eventData(null, isSubscriber = false)
        )
        assertEquals(
            mapOf("is_subscriber" to "true"),
            UmamiTracker.eventData(emptyMap(), isSubscriber = true)
        )

        // Texte, jamais un booléen JSON : Umami typerait un `true` nu dans une
        // propriété distincte des chaînes et scinderait les rapports.
        val payload = payloadOf(
            UmamiTracker.buildBody(
                url = "/recettes",
                title = "Recettes",
                referrer = "/",
                name = UmamiEvents.RECIPE_SEARCH,
                data = UmamiTracker.eventData(
                    mapOf(UmamiParams.QUERY to "tarte"),
                    isSubscriber = true
                ),
                language = "fr-FR",
                screen = "412x915"
            )
        )
        val data = payload.getAsJsonObject("data")
        assertEquals("tarte", data.get("query").asString)
        assertTrue(
            "`is_subscriber` doit partir en chaîne",
            data.get("is_subscriber").asJsonPrimitive.isString
        )
        assertEquals("true", data.get("is_subscriber").asString)
    }

    @Test
    fun `le statut mesure prime sur une valeur passee par l'appelant`() {
        // Aucun point d'appel ne doit pouvoir se déclarer abonné : la valeur du
        // traceur est posée en dernier.
        assertEquals(
            "false",
            UmamiTracker.eventData(
                mapOf(UmamiParams.IS_SUBSCRIBER to "true"),
                isSubscriber = false
            )[UmamiParams.IS_SUBSCRIBER]
        )
    }

    @Test
    fun `une page vue reste sans data`() {
        // `is_subscriber` est posé par `trackEvent`, pas par `buildBody` : le
        // chemin page vue est inchangé, comme sur le web et l'iOS.
        val payload = payloadOf(
            UmamiTracker.buildBody(
                url = "/recettes",
                title = "Recettes",
                referrer = "/",
                name = null,
                data = null,
                language = "fr-FR",
                screen = "412x915"
            )
        )
        assertFalse(payload.has("data"))
    }

    @Test
    fun `event custom porte name et data`() {
        val payload = payloadOf(
            UmamiTracker.buildBody(
                url = "/recette/tarte-citron",
                title = "Tarte au citron",
                referrer = "/recettes",
                name = UmamiEvents.RECIPE_FAVORITE,
                data = mapOf(UmamiParams.RECIPE_ID to "tarte-citron"),
                language = "fr-FR",
                screen = "412x915"
            )
        )
        assertEquals("recipe_favorite", payload.get("name").asString)
        assertEquals(
            "tarte-citron",
            payload.getAsJsonObject("data").get("recipe_id").asString
        )
    }

    // ── Jeton de session (`x-umami-cache`) ───────────────────────────────────

    @Test
    fun `jeton de session lu dans les trois formes rendues par Umami`() {
        val token = "abcdefghijklmnop"
        assertEquals(token, UmamiTracker.cacheTokenFrom("""{"cache":"$token"}"""))
        assertEquals(token, UmamiTracker.cacheTokenFrom("\"$token\""))
        assertEquals(token, UmamiTracker.cacheTokenFrom(token))
    }

    @Test
    fun `reponses sans jeton exploitable sont ignorees`() {
        // Renvoyer l'une de ces valeurs en header `x-umami-cache` ferait rejeter
        // tous les hits suivants.
        assertNull(UmamiTracker.cacheTokenFrom(""))
        assertNull(UmamiTracker.cacheTokenFrom("ok"))
        assertNull(UmamiTracker.cacheTokenFrom("{}"))
        assertNull(UmamiTracker.cacheTokenFrom("""{"beep":"boop"}"""))
        assertTrue(UmamiTracker.cacheTokenFrom("[]") == null)
    }
}
