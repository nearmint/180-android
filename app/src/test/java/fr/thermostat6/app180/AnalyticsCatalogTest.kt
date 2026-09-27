package fr.thermostat6.app180

import fr.thermostat6.app180.data.analytics.AnalyticsEvents
import fr.thermostat6.app180.data.analytics.AnalyticsParams
import fr.thermostat6.app180.data.analytics.AnalyticsService
import fr.thermostat6.app180.data.analytics.AnalyticsSink
import fr.thermostat6.app180.data.analytics.AnalyticsUserProperties
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **Verrou anti-dérive cross-plateforme.**
 *
 * Chaque nom d'événement, de paramètre et de propriété est comparé chaîne à
 * chaîne à l'inventaire de l'`AnalyticsService` iOS
 * (`apple/180/AnalyticsService.swift`). Une divergence — même une lettre — crée
 * un événement distinct dans GA4 et rend les deux plateformes incomparables :
 * ce test échoue avant que ça arrive.
 */
class AnalyticsCatalogTest {

    /** Enregistreur en mémoire : aucun Firebase réel n'est sollicité. */
    private class RecordingSink : AnalyticsSink {
        data class Logged(val name: String, val keys: Set<String>, val values: Map<String, Any?>)

        val events = mutableListOf<Logged>()
        val properties = mutableMapOf<String, String?>()

        override fun logEvent(name: String, params: Map<String, Any?>) {
            events += Logged(name, params.keys, params)
        }

        override fun setUserProperty(name: String, value: String?) {
            properties[name] = value
        }
    }

    private lateinit var sink: RecordingSink

    @Before
    fun setUp() {
        sink = RecordingSink()
        AnalyticsService.sink = sink
    }

    @After
    fun tearDown() {
        AnalyticsService.sink = sink
    }

    private fun lastEvent() = sink.events.last()

    // ── Noms d'événements ────────────────────────────────────────────────────

    @Test
    fun `les noms d'evenements sont ceux de l'iOS`() {
        assertEquals("view_recipe", AnalyticsEvents.VIEW_RECIPE)
        assertEquals("paywall_view", AnalyticsEvents.PAYWALL_VIEW)
        assertEquals("share_recipe", AnalyticsEvents.SHARE_RECIPE)
        assertEquals("add_favorite", AnalyticsEvents.ADD_FAVORITE)
        assertEquals("remove_favorite", AnalyticsEvents.REMOVE_FAVORITE)
        assertEquals("search", AnalyticsEvents.SEARCH)
        assertEquals("login", AnalyticsEvents.LOGIN)
        assertEquals("login_failed", AnalyticsEvents.LOGIN_FAILED)
        assertEquals("logout", AnalyticsEvents.LOGOUT)
        assertEquals("signup_click", AnalyticsEvents.SIGNUP_CLICK)
        assertEquals("boutique_click", AnalyticsEvents.BOUTIQUE_CLICK)
        assertEquals("filter_applied", AnalyticsEvents.FILTER_APPLIED)
        assertEquals("newsletter_subscribe", AnalyticsEvents.NEWSLETTER_SUBSCRIBE)
        assertEquals("newsletter_unsubscribe", AnalyticsEvents.NEWSLETTER_UNSUBSCRIBE)
        assertEquals("notification_permission", AnalyticsEvents.NOTIFICATION_PERMISSION)
        assertEquals("notification_opened", AnalyticsEvents.NOTIFICATION_OPENED)
        assertEquals("iam_displayed", AnalyticsEvents.IAM_DISPLAYED)
        assertEquals("iam_clicked", AnalyticsEvents.IAM_CLICKED)
        assertEquals("dark_mode_changed", AnalyticsEvents.DARK_MODE_CHANGED)
        assertEquals("contact_support", AnalyticsEvents.CONTACT_SUPPORT)
        assertEquals("rate_app_click", AnalyticsEvents.RATE_APP_CLICK)
        assertEquals("share_app", AnalyticsEvents.SHARE_APP)
        assertEquals("onboarding_complete", AnalyticsEvents.ONBOARDING_COMPLETE)
    }

    @Test
    fun `les noms de parametres sont ceux de l'iOS`() {
        assertEquals("recipe_id", AnalyticsParams.RECIPE_ID)
        assertEquals("recipe_title", AnalyticsParams.RECIPE_TITLE)
        assertEquals("is_premium", AnalyticsParams.IS_PREMIUM)
        assertEquals("query", AnalyticsParams.QUERY)
        assertEquals("results_count", AnalyticsParams.RESULTS_COUNT)
        assertEquals("method", AnalyticsParams.METHOD)
        assertEquals("error_message", AnalyticsParams.ERROR_MESSAGE)
        assertEquals("source", AnalyticsParams.SOURCE)
        assertEquals("sort_order", AnalyticsParams.SORT_ORDER)
        assertEquals("season", AnalyticsParams.SEASON)
        assertEquals("dish_type", AnalyticsParams.DISH_TYPE)
        assertEquals("granted", AnalyticsParams.GRANTED)
        assertEquals("notification_id", AnalyticsParams.NOTIFICATION_ID)
        assertEquals("notification_type", AnalyticsParams.NOTIFICATION_TYPE)
        assertEquals("mode", AnalyticsParams.MODE)
        assertEquals("message_id", AnalyticsParams.MESSAGE_ID)
        assertEquals("action_id", AnalyticsParams.ACTION_ID)
    }

    @Test
    fun `les proprietes utilisateur sont celles de l'iOS`() {
        assertEquals("user_type", AnalyticsUserProperties.USER_TYPE)
        assertEquals("is_subscriber", AnalyticsUserProperties.IS_SUBSCRIBER)
        assertEquals("newsletter_subscribed", AnalyticsUserProperties.NEWSLETTER_SUBSCRIBED)
        assertEquals("dark_mode", AnalyticsUserProperties.DARK_MODE)
        assertEquals("notifications_enabled", AnalyticsUserProperties.NOTIFICATIONS_ENABLED)
        assertEquals("favorites_count", AnalyticsUserProperties.FAVORITES_COUNT)
    }

    // ── Forme des événements émis ────────────────────────────────────────────

    @Test
    fun `view_recipe porte id, titre et statut premium`() {
        AnalyticsService.viewRecipe(id = 42, title = "Tarte", isPremium = true)

        val event = lastEvent()
        assertEquals("view_recipe", event.name)
        assertEquals(setOf("recipe_id", "recipe_title", "is_premium"), event.keys)
        assertEquals(42, event.values["recipe_id"])
        assertEquals("Tarte", event.values["recipe_title"])
        assertEquals(true, event.values["is_premium"])
    }

    @Test
    fun `les evenements sans parametre n'en portent aucun`() {
        AnalyticsService.logout()
        assertEquals("logout", lastEvent().name)
        assertNull(sink.events.last().values["recipe_id"])
        assertTrue(lastEvent().keys.isEmpty())

        AnalyticsService.boutiqueClick()
        assertTrue(lastEvent().keys.isEmpty())

        AnalyticsService.newsletterSubscribe()
        assertTrue(lastEvent().keys.isEmpty())

        AnalyticsService.onboardingComplete()
        assertTrue(lastEvent().keys.isEmpty())
    }

    @Test
    fun `filter_applied remplace null par all`() {
        AnalyticsService.filterApplied(sortOrder = "date_desc", season = null, dishType = null)

        val event = lastEvent()
        assertEquals("all", event.values["season"])
        assertEquals("all", event.values["dish_type"])
        assertEquals("date_desc", event.values["sort_order"])
    }

    @Test
    fun `filter_applied conserve les slugs fournis`() {
        AnalyticsService.filterApplied(sortOrder = "title_asc", season = "ete", dishType = "plat")

        val event = lastEvent()
        assertEquals("ete", event.values["season"])
        assertEquals("plat", event.values["dish_type"])
    }

    @Test
    fun `notification_opened transmet l'id en chaine`() {
        AnalyticsService.notificationOpened(id = "13400070", type = "recipe")

        val event = lastEvent()
        assertEquals("13400070", event.values["notification_id"])
        assertEquals("recipe", event.values["notification_type"])
    }

    @Test
    fun `iam_displayed ne porte que l'identifiant du message`() {
        AnalyticsService.inAppMessageDisplayed(id = "abc-123")

        val event = lastEvent()
        assertEquals("iam_displayed", event.name)
        assertEquals(setOf("message_id"), event.keys)
        assertEquals("abc-123", event.values["message_id"])
    }

    @Test
    fun `iam_clicked porte le message et l'action`() {
        AnalyticsService.inAppMessageClicked(id = "abc-123", actionId = "recipe:42")

        val event = lastEvent()
        assertEquals("iam_clicked", event.name)
        assertEquals(setOf("message_id", "action_id"), event.keys)
        assertEquals("abc-123", event.values["message_id"])
        assertEquals("recipe:42", event.values["action_id"])
    }

    /**
     * Un bouton sans action et un paramètre absent seraient indiscernables dans
     * GA4 : l'iOS retient la chaîne « none » pour que le second se compte
     * (`apple/180/AnalyticsService.swift:131`).
     */
    @Test
    fun `iam_clicked sans action remonte la chaine none`() {
        AnalyticsService.inAppMessageClicked(id = "abc-123", actionId = null)

        val event = lastEvent()
        assertEquals(setOf("message_id", "action_id"), event.keys)
        assertEquals("none", event.values["action_id"])
    }

    // ── Propriétés dérivées ──────────────────────────────────────────────────

    @Test
    fun `user_type derive de la session, abonne prioritaire`() {
        AnalyticsService.setUserProperties(false, false, false, "auto", false, 0)
        assertEquals("visitor", sink.properties["user_type"])

        AnalyticsService.setUserProperties(true, false, false, "auto", false, 0)
        assertEquals("logged_in", sink.properties["user_type"])

        AnalyticsService.setUserProperties(true, true, false, "auto", false, 0)
        assertEquals("subscriber", sink.properties["user_type"])
    }

    @Test
    fun `les proprietes booleennes et numeriques sont serialisees en chaines`() {
        AnalyticsService.setUserProperties(
            isLoggedIn = true, isSubscriber = true, newsletterSubscribed = false,
            darkMode = "dark", notificationsEnabled = true, favoritesCount = 7
        )

        assertEquals("true", sink.properties["is_subscriber"])
        assertEquals("false", sink.properties["newsletter_subscribed"])
        assertEquals("dark", sink.properties["dark_mode"])
        assertEquals("true", sink.properties["notifications_enabled"])
        assertEquals("7", sink.properties["favorites_count"])
    }

    // ── Hygiène : aucune donnée personnelle ──────────────────────────────────

    @Test
    fun `aucun evenement ne transporte de donnee personnelle`() {
        AnalyticsService.viewRecipe(1, "Tarte", false)
        AnalyticsService.paywallView(1, "Tarte")
        AnalyticsService.shareRecipe(1, "Tarte")
        AnalyticsService.addFavorite(1, "Tarte")
        AnalyticsService.removeFavorite(1, "Tarte")
        AnalyticsService.search("tomate", 3)
        AnalyticsService.login("email")
        AnalyticsService.loginFailed("Identifiants incorrects")
        AnalyticsService.signupClick("account")
        AnalyticsService.filterApplied("date_desc", null, null)
        AnalyticsService.notificationPermission(true)
        AnalyticsService.notificationOpened("1", "recipe")
        AnalyticsService.darkModeChanged("dark")
        AnalyticsService.inAppMessageDisplayed("abc-123")
        AnalyticsService.inAppMessageClicked("abc-123", "recipe:42")

        // Aucun paramètre ne doit nommer une donnée personnelle. `method` vaut
        // « email »/« username » — le **type** d'identifiant, jamais sa valeur.
        val forbidden = listOf("email", "e_mail", "mail", "username", "user_name", "name", "user_id")
        sink.events.forEach { event ->
            event.keys.forEach { key ->
                assertFalse(
                    "paramètre suspect « $key » sur l'événement « ${event.name} »",
                    key in forbidden
                )
            }
        }
    }

    @Test
    fun `login transmet le type d'identifiant, pas sa valeur`() {
        AnalyticsService.login(method = "email")

        val event = lastEvent()
        assertEquals(setOf("method"), event.keys)
        assertEquals("email", event.values["method"])
    }
}
