package fr.thermostat6.app180

import com.google.gson.reflect.TypeToken
import fr.thermostat6.app180.data.model.HomeBlock
import fr.thermostat6.app180.data.service.DiskCache
import fr.thermostat6.app180.data.service.HomeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Cache disque de l'accueil : aller-retour, TTL et invalidation.
 *
 * Miroir d'`apple/180/DiskCache.swift` ; TTL de 15 min repris de
 * `HomeService.swift:136-137`.
 */
class DiskCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val snapshotType = TypeToken.get(HomeSnapshot::class.java).type
    private val ttl = 15L * 60L * 1000L

    @Before
    fun setUp() {
        DiskCache.init(temporaryFolder.newFolder("net-cache"))
    }

    private fun snapshot() = HomeSnapshot(
        blocks = listOf(HomeBlock(typeRaw = "rail", titleRaw = "Dernières recettes publiées"))
    )

    @Test
    fun `une valeur ecrite est relue a l'identique`() {
        DiskCache.store(snapshot(), key = "home", nowMillis = 1_000L)

        val loaded: HomeSnapshot? = DiskCache.load("home", snapshotType, ttl, nowMillis = 1_000L)

        assertNotNull(loaded)
        assertEquals(1, loaded!!.blocks.size)
        assertEquals("rail", loaded.blocks.first().type)
        assertEquals("Dernières recettes publiées", loaded.blocks.first().title)
    }

    @Test
    fun `une valeur dans le TTL est servie`() {
        DiskCache.store(snapshot(), key = "home", nowMillis = 0L)

        // 14 min 59 s plus tard : encore valide.
        val loaded: HomeSnapshot? = DiskCache.load("home", snapshotType, ttl, nowMillis = ttl - 1_000L)
        assertNotNull(loaded)
    }

    @Test
    fun `une valeur au-dela du TTL est ignoree`() {
        DiskCache.store(snapshot(), key = "home", nowMillis = 0L)

        // 15 min et 1 ms plus tard : périmée.
        val loaded: HomeSnapshot? = DiskCache.load("home", snapshotType, ttl, nowMillis = ttl + 1L)
        assertNull(loaded)
    }

    @Test
    fun `une cle absente renvoie null`() {
        assertNull(DiskCache.load<HomeSnapshot>("jamais-ecrite", snapshotType, ttl))
    }

    @Test
    fun `un fichier illisible renvoie null sans lever`() {
        DiskCache.store(snapshot(), key = "home", nowMillis = 0L)
        temporaryFolder.root.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .forEach { it.writeText("{ ceci n'est pas du json") }

        assertNull(DiskCache.load<HomeSnapshot>("home", snapshotType, ttl, nowMillis = 0L))
    }

    @Test
    fun `remove invalide l'entree ciblee`() {
        DiskCache.store(snapshot(), key = "home", nowMillis = 0L)
        DiskCache.remove("home")

        assertNull(DiskCache.load<HomeSnapshot>("home", snapshotType, ttl, nowMillis = 0L))
    }

    @Test
    fun `clear vide tout le cache`() {
        DiskCache.store(snapshot(), key = "home", nowMillis = 0L)
        DiskCache.store(snapshot(), key = "autre", nowMillis = 0L)
        DiskCache.clear()

        assertNull(DiskCache.load<HomeSnapshot>("home", snapshotType, ttl, nowMillis = 0L))
        assertNull(DiskCache.load<HomeSnapshot>("autre", snapshotType, ttl, nowMillis = 0L))
    }
}
