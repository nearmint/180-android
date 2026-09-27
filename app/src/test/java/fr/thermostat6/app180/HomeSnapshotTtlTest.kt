package fr.thermostat6.app180

import fr.thermostat6.app180.data.model.HomeBlock
import fr.thermostat6.app180.data.service.DiskCache
import fr.thermostat6.app180.data.service.HomeRepository
import fr.thermostat6.app180.data.service.HomeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Contournement du TTL de l'accueil hors connexion.
 *
 * Le TTL de 15 min existe pour éviter de servir du périmé **quand on peut aller
 * chercher du frais**. Hors réseau cette alternative n'existe pas : l'appliquer
 * quand même ne troque pas du contenu daté contre du contenu à jour, il le
 * troque contre un mur d'erreur.
 */
class HomeSnapshotTtlTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** Même clé que `HomeRepository.SNAPSHOT_KEY`, privée côté production. */
    private val snapshotKey = "home-snapshot-v1"

    private val fifteenMinutes = 15L * 60L * 1000L

    @Before
    fun setUp() {
        DiskCache.init(File(temporaryFolder.root, "net-cache"))
    }

    private fun storeSnapshot(ageMillis: Long) {
        DiskCache.store(
            value     = HomeSnapshot(blocks = listOf(HomeBlock(anchor = "rail-saison"))),
            key       = snapshotKey,
            nowMillis = System.currentTimeMillis() - ageMillis
        )
    }

    @Test
    fun `un instantane frais est servi dans les deux modes`() {
        storeSnapshot(ageMillis = 60_000)

        assertNotNull("en ligne", HomeRepository.cachedContent())
        assertNotNull("hors ligne", HomeRepository.cachedContent(ignoreTtl = true))
    }

    @Test
    fun `un instantane perime est refuse en ligne`() {
        storeSnapshot(ageMillis = fifteenMinutes + 60_000)

        assertNull(
            "en ligne, le TTL doit continuer de s'appliquer",
            HomeRepository.cachedContent()
        )
    }

    @Test
    fun `un instantane perime est servi hors ligne`() {
        // Le cas qui produisait un mur d'erreur alors que le contenu était là.
        storeSnapshot(ageMillis = fifteenMinutes + 60_000)

        val content = HomeRepository.cachedContent(ignoreTtl = true)

        assertNotNull(content)
        assertEquals(1, content!!.blocks.size)
    }

    @Test
    fun `un instantane tres ancien reste servi hors ligne`() {
        // Aucune borne haute : à trois mois comme à une heure, un accueil daté
        // vaut mieux que rien puisqu'on ne peut pas aller chercher mieux.
        storeSnapshot(ageMillis = 90L * 24L * 3_600_000L)

        assertNotNull(HomeRepository.cachedContent(ignoreTtl = true))
        assertNull(HomeRepository.cachedContent())
    }

    @Test
    fun `aucun instantane sur disque donne null dans les deux modes`() {
        assertNull(HomeRepository.cachedContent())
        assertNull(HomeRepository.cachedContent(ignoreTtl = true))
    }
}
