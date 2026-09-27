package fr.thermostat6.app180

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fr.thermostat6.app180.data.model.AppNotification
import fr.thermostat6.app180.data.model.NotificationTarget
import fr.thermostat6.app180.data.push.NotificationDestination
import fr.thermostat6.app180.data.push.NotificationRouter
import fr.thermostat6.app180.data.push.PushPermissionCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Décodage du centre de notifications et **table de routage complète**.
 *
 * La fixture `notifications.json` est une capture anonyme **réelle** (le flux est
 * public). Le routeur est une fonction pure — testée sans framework Android.
 *
 * Miroir d'`apple/180/Navigation/NotificationRouter.swift`.
 */
class NotificationRoutingTest {

    private val gson = Gson()

    private fun feed(): List<AppNotification> {
        val raw = checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/notifications.json")) {
            "Fixture manquante"
        }.bufferedReader().use { it.readText() }
        val root = gson.fromJson(raw, com.google.gson.JsonObject::class.java)
        return gson.fromJson(
            root.getAsJsonArray("items"),
            object : TypeToken<List<AppNotification>>() {}.type
        )
    }

    // ── Décodage du flux ─────────────────────────────────────────────────────

    @Test
    fun `le flux reel se decode`() {
        val items = feed()

        assertEquals(2, items.size)
        val first = items.first()
        assertEquals(13400070, first.id)
        assertEquals("Nems aux porcs et crevettes", first.title)
        assertTrue(first.body.startsWith("Quel plaisir"))
        assertTrue(first.imageUrl!!.startsWith("https://"))
        assertEquals("2026-07-23T14:04:40Z", first.sentAt)
        assertEquals("recipe", first.target?.type)
        assertEquals(13136868, first.target?.id)
    }

    @Test
    fun `un element tronque n'est pas valide`() {
        assertFalse(gson.fromJson("{}", AppNotification::class.java).isValid)
        assertTrue(gson.fromJson("""{"id":7}""", AppNotification::class.java).isValid)
        // Titre et corps absents se décodent en chaînes vides, sans lever.
        assertEquals("", gson.fromJson("""{"id":7}""", AppNotification::class.java).title)
    }

    @Test
    fun `la cible du flux route vers le detail recette`() {
        val destination = NotificationRouter.destination(feed().first().target)
        assertEquals(NotificationDestination.Recipe(13136868), destination)
    }

    // ── Table de routage ─────────────────────────────────────────────────────

    @Test
    fun `recipe et article routent sur leur id`() {
        assertEquals(
            NotificationDestination.Recipe(42),
            NotificationRouter.destination("recipe", 42, null)
        )
        assertEquals(
            NotificationDestination.Article(7),
            NotificationRouter.destination("article", 7, null)
        )
    }

    @Test
    fun `un id nul ou negatif retombe sur none`() {
        assertEquals(NotificationDestination.None, NotificationRouter.destination("recipe", 0, null))
        assertEquals(NotificationDestination.None, NotificationRouter.destination("article", -1, null))
    }

    @Test
    fun `url route vers une ouverture publique`() {
        assertEquals(
            NotificationDestination.Url("https://exemple.fr/page"),
            NotificationRouter.destination("url", 0, "https://exemple.fr/page")
        )
        // URL absente ou vide → none.
        assertEquals(NotificationDestination.None, NotificationRouter.destination("url", 0, null))
        assertEquals(NotificationDestination.None, NotificationRouter.destination("url", 0, "  "))
    }

    @Test
    fun `product exige une url du site et normalise l'hote`() {
        // Apex accepté mais **normalisé** sur www : sinon le POST d'amorçage
        // serait dégradé par le 301 apex→www.
        assertEquals(
            NotificationDestination.Product(9, "https://www.180c.fr/boutique/abo/"),
            NotificationRouter.destination("product", 9, "https://180c.fr/boutique/abo/")
        )
        assertEquals(
            NotificationDestination.Product(9, "https://www.180c.fr/boutique/"),
            NotificationRouter.destination("product", 9, "https://www.180c.fr/boutique/")
        )
    }

    @Test
    fun `product hors domaine est rejete`() {
        assertEquals(
            NotificationDestination.None,
            NotificationRouter.destination("product", 9, "https://evil.example/boutique/")
        )
        // Sous-domaine : rejeté aussi.
        assertEquals(
            NotificationDestination.None,
            NotificationRouter.destination("product", 9, "https://shop.180c.fr/x/")
        )
        // URL relative ou absente : rejetée.
        assertEquals(NotificationDestination.None, NotificationRouter.destination("product", 9, "/boutique/"))
        assertEquals(NotificationDestination.None, NotificationRouter.destination("product", 9, null))
    }

    @Test
    fun `un type inconnu retombe sur none`() {
        assertEquals(NotificationDestination.None, NotificationRouter.destination("none", 1, null))
        assertEquals(NotificationDestination.None, NotificationRouter.destination("futur_type", 1, null))
        assertEquals(NotificationDestination.None, NotificationRouter.destination(null, 1, null))
        assertEquals(NotificationDestination.None, NotificationRouter.destination(null))
    }

    // ── Payload de push ──────────────────────────────────────────────────────

    @Test
    fun `le payload plat est lu directement`() {
        assertEquals(
            NotificationDestination.Recipe(11),
            NotificationRouter.destinationFromPush(mapOf("type" to "recipe", "id" to 11))
        )
    }

    @Test
    fun `le payload imbrique sous target ou custom_data est normalise`() {
        val underTarget = mapOf("target" to mapOf("type" to "recipe", "id" to 12))
        val underCustom = mapOf("custom_data" to mapOf("type" to "article", "id" to 13))

        assertEquals(NotificationDestination.Recipe(12), NotificationRouter.destinationFromPush(underTarget))
        assertEquals(NotificationDestination.Article(13), NotificationRouter.destinationFromPush(underCustom))
    }

    @Test
    fun `un id transmis en chaine est accepte`() {
        // OneSignal sérialise volontiers les nombres en chaînes.
        assertEquals(
            NotificationDestination.Recipe(14),
            NotificationRouter.destinationFromPush(mapOf("type" to "recipe", "id" to "14"))
        )
    }

    @Test
    fun `un payload vide retombe sur none`() {
        assertEquals(NotificationDestination.None, NotificationRouter.destinationFromPush(null))
        assertEquals(NotificationDestination.None, NotificationRouter.destinationFromPush(emptyMap()))
    }

    // ── Politique de soft-ask ────────────────────────────────────────────────

    @Test
    fun `le soft-ask n'apparait que si toutes les conditions sont reunies`() {
        assertTrue(
            PushPermissionCoordinator.shouldShowSoftAsk(
                isPermissionUndetermined = true,
                declineCount             = 0,
                sessionAlreadyShown      = false,
                lastSoftAskAt            = 0L,
                nowMillis                = 1_000L,
                isReadingRecipe          = false
            )
        )
    }

    @Test
    fun `chaque condition manquante bloque le soft-ask`() {
        fun ask(
            undetermined: Boolean = true,
            declines: Int = 0,
            shown: Boolean = false,
            last: Long = 0L,
            now: Long = 1_000L,
            reading: Boolean = false
        ) = PushPermissionCoordinator.shouldShowSoftAsk(undetermined, declines, shown, last, now, reading)

        assertFalse("permission déjà tranchée", ask(undetermined = false))
        assertFalse("2 refus atteints", ask(declines = 2))
        assertFalse("déjà montré dans la session", ask(shown = true))
        assertFalse("lecture en cours", ask(reading = true))
        // Cooldown de 30 jours non écoulé.
        val oneDay = 24L * 60 * 60 * 1000
        assertFalse("cooldown non écoulé", ask(last = oneDay, now = oneDay + oneDay))
        assertTrue("cooldown écoulé", ask(last = 1L, now = 1L + 31 * oneDay))
    }
}
