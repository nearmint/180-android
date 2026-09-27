package fr.thermostat6.app180

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fr.thermostat6.app180.data.model.AppNotification
import fr.thermostat6.app180.data.service.DiskCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pagination du centre et cache hors-ligne du flux.
 *
 * Les règles miroitées de `NotificationManager`
 * (`apple/180/NotificationManager.swift:134-166`) sont éprouvées ici sous forme
 * de fonctions pures : seuil de déclenchement, `canLoadMore`, dédoublonnage par
 * `id`, et aller-retour du cache disque.
 */
class NotificationPagingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val gson = Gson()
    private val feedType = object : TypeToken<List<AppNotification>>() {}.type

    /** Taille de page du flux (`NotificationsRepository.swift:22`). */
    private val perPage = 20

    @Before
    fun setUp() {
        DiskCache.init(temporaryFolder.newFolder("net-cache"))
    }

    private fun notification(id: Int) = AppNotification(id = id)

    // ── Règle `canLoadMore` ──────────────────────────────────────────────────

    /** Miroir de `canLoadMore = items.count >= perPage` (`:140`, `:161`). */
    private fun canLoadMore(received: Int) = received >= perPage

    @Test
    fun `une page pleine autorise la suivante`() {
        assertTrue(canLoadMore(perPage))
        assertTrue(canLoadMore(perPage + 5))
    }

    @Test
    fun `une page incomplete marque la fin du flux`() {
        assertEquals(false, canLoadMore(perPage - 1))
        assertEquals(false, canLoadMore(0))
    }

    // ── Seuil de déclenchement ───────────────────────────────────────────────

    /** Miroir de `item.id == notifications.last?.id` (`:154`). */
    private fun shouldLoadMore(items: List<AppNotification>, current: AppNotification) =
        current.id == items.lastOrNull()?.id

    @Test
    fun `seule la derniere ligne declenche le chargement`() {
        val items = listOf(notification(1), notification(2), notification(3))

        assertTrue(shouldLoadMore(items, notification(3)))
        assertEquals(false, shouldLoadMore(items, notification(2)))
        assertEquals(false, shouldLoadMore(items, notification(1)))
    }

    @Test
    fun `une liste vide ne declenche rien`() {
        assertEquals(false, shouldLoadMore(emptyList(), notification(1)))
    }

    // ── Dédoublonnage ────────────────────────────────────────────────────────

    /** Miroir de la concaténation filtrée par `existing` (`:162-164`). */
    private fun merge(current: List<AppNotification>, incoming: List<AppNotification>): List<AppNotification> {
        val existing = current.map { it.id }.toSet()
        return current + incoming.filter { it.isValid && it.id !in existing }
    }

    @Test
    fun `la page suivante est concatenee sans doublons`() {
        val current  = listOf(notification(1), notification(2))
        val incoming = listOf(notification(2), notification(3), notification(4))

        assertEquals(listOf(1, 2, 3, 4), merge(current, incoming).map { it.id })
    }

    @Test
    fun `un element invalide est ecarte de la concatenation`() {
        val current  = listOf(notification(1))
        val incoming = listOf(notification(0), notification(2))

        // `id = 0` n'est pas valide : écarté (AppNotification.isValid).
        assertEquals(listOf(1, 2), merge(current, incoming).map { it.id })
    }

    @Test
    fun `une page entierement dupliquee ne change rien`() {
        val current = listOf(notification(1), notification(2))
        assertEquals(listOf(1, 2), merge(current, current).map { it.id })
    }

    // ── Cache hors-ligne ─────────────────────────────────────────────────────

    @Test
    fun `le flux est relu depuis le cache disque`() {
        val feed = listOf(notification(10), notification(11))
        DiskCache.store(feed, key = "notifications-feed-v1", nowMillis = 0L)

        val loaded: List<AppNotification>? = DiskCache.load(
            key          = "notifications-feed-v1",
            typeOfT      = feedType,
            maxAgeMillis = Long.MAX_VALUE,
            nowMillis    = 0L
        )

        assertNotNull(loaded)
        assertEquals(listOf(10, 11), loaded!!.map { it.id })
    }

    @Test
    fun `le cache du flux n'expire pas`() {
        // Pas de TTL côté iOS (NotificationsRepository.swift:57-70) : le flux
        // reste servi hors-ligne quel que soit son âge.
        DiskCache.store(listOf(notification(1)), key = "notifications-feed-v1", nowMillis = 0L)

        val muchLater = 365L * 24 * 60 * 60 * 1000
        val loaded: List<AppNotification>? = DiskCache.load(
            key          = "notifications-feed-v1",
            typeOfT      = feedType,
            maxAgeMillis = Long.MAX_VALUE,
            nowMillis    = muchLater
        )

        assertNotNull(loaded)
    }

    @Test
    fun `un cache absent renvoie une liste vide exploitable`() {
        val loaded: List<AppNotification>? = DiskCache.load(
            key          = "jamais-ecrit",
            typeOfT      = feedType,
            maxAgeMillis = Long.MAX_VALUE
        )
        assertNull(loaded)
        assertEquals(emptyList<AppNotification>(), loaded.orEmpty())
    }
}
