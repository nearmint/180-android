package fr.thermostat6.app180

import fr.thermostat6.app180.data.offline.OfflineRecipeMeta
import fr.thermostat6.app180.data.offline.OfflineSyncPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diff de réconciliation du carnet hors ligne.
 *
 * C'est la seule pièce où une erreur se traduit directement par du contenu
 * manquant, périmé, ou re-téléchargé en boucle.
 *
 * Transposition des 11 tests de `apple/180Tests/OfflineSyncTests.swift`
 * (`OfflineSyncPlannerTests`).
 */
class OfflineSyncPlannerTest {

    private fun meta(id: Int, modified: String?) =
        OfflineRecipeMeta(id = id, downloadedAt = 0L, modified = modified, imageCount = 1)

    @Test
    fun `premier cycle tout est a telecharger dans l'ordre serveur`() {
        val plan = OfflineSyncPlanner.plan(
            serverIds      = listOf(30, 10, 20),
            serverModified = mapOf(30 to "d3", 10 to "d1", 20 to "d2"),
            local          = emptyList()
        )

        assertEquals(listOf(30, 10, 20), plan.toDownload)
        assertTrue(plan.toDelete.isEmpty())
        assertTrue(plan.upToDate.isEmpty())
    }

    @Test
    fun `rien a faire quand tout est present et a jour`() {
        val plan = OfflineSyncPlanner.plan(
            serverIds      = listOf(1, 2),
            serverModified = mapOf(1 to "d1", 2 to "d2"),
            local          = listOf(meta(1, "d1"), meta(2, "d2"))
        )

        assertTrue(plan.toDownload.isEmpty())
        assertTrue(plan.toDelete.isEmpty())
        assertEquals(listOf(1, 2), plan.upToDate)
    }

    @Test
    fun `favori retire cote serveur donne une suppression locale`() {
        val plan = OfflineSyncPlanner.plan(
            serverIds      = listOf(1),
            serverModified = mapOf(1 to "d1"),
            local          = listOf(meta(1, "d1"), meta(2, "d2"))
        )

        assertEquals(listOf(2), plan.toDelete)
        assertTrue(plan.toDownload.isEmpty())
    }

    @Test
    fun `favori ajoute cote serveur donne un telechargement`() {
        val plan = OfflineSyncPlanner.plan(
            serverIds      = listOf(1, 2),
            serverModified = mapOf(1 to "d1", 2 to "d2"),
            local          = listOf(meta(1, "d1"))
        )

        assertEquals(listOf(2), plan.toDownload)
        assertEquals(listOf(1), plan.upToDate)
        assertTrue(plan.toDelete.isEmpty())
    }

    @Test
    fun `modified serveur plus recent declenche un re-telechargement`() {
        val plan = OfflineSyncPlanner.plan(
            serverIds      = listOf(1, 2),
            serverModified = mapOf(1 to "2026-07-24T16:44:07", 2 to "d2"),
            local          = listOf(meta(1, "2026-07-03T00:38:18"), meta(2, "d2"))
        )

        assertEquals(listOf(1), plan.toDownload)
        assertEquals(listOf(2), plan.upToDate)
    }

    @Test
    fun `fiche locale sans jeton de fraicheur est re-telechargee`() {
        val plan = OfflineSyncPlanner.plan(
            serverIds      = listOf(1),
            serverModified = mapOf(1 to "d1"),
            local          = listOf(meta(1, modified = null))
        )

        assertEquals(listOf(1), plan.toDownload)
    }

    @Test
    fun `date serveur indisponible conserve la copie locale sans boucle`() {
        // Cas typique : la sonde a échoué. Re-télécharger ici produirait un
        // cycle qui retélécharge tout, à chaque lancement.
        val plan = OfflineSyncPlanner.plan(
            serverIds      = listOf(1, 2),
            serverModified = emptyMap(),
            local          = listOf(meta(1, "d1"), meta(2, "d2"))
        )

        assertTrue(plan.toDownload.isEmpty())
        assertEquals(listOf(1, 2), plan.upToDate)
        assertTrue(plan.toDelete.isEmpty())
    }

    @Test
    fun `sonde en panne les manquants sont quand meme telecharges`() {
        val plan = OfflineSyncPlanner.plan(
            serverIds      = listOf(1, 2),
            serverModified = emptyMap(),
            local          = listOf(meta(1, "d1"))
        )

        assertEquals(listOf(2), plan.toDownload)
        assertEquals(listOf(1), plan.upToDate)
    }

    @Test
    fun `un echec de telechargement est repris au cycle suivant`() {
        // Un échec n'écrit rien : la fiche reste absente du store, donc le cycle
        // suivant la replanifie sans mécanisme de reprise dédié.
        val plan = OfflineSyncPlanner.plan(
            serverIds      = listOf(1, 2, 3),
            serverModified = mapOf(1 to "d1", 2 to "d2", 3 to "d3"),
            local          = listOf(meta(1, "d1"), meta(3, "d3"))
        )

        assertEquals(listOf(2), plan.toDownload)
    }

    @Test
    fun `carnet vide cote serveur supprime tout`() {
        val plan = OfflineSyncPlanner.plan(
            serverIds      = emptyList(),
            serverModified = emptyMap(),
            local          = listOf(meta(1, "d1"), meta(2, "d2"))
        )

        assertEquals(listOf(1, 2), plan.toDelete)
        assertTrue(plan.toDownload.isEmpty())
    }

    @Test
    fun `doublons dans la liste serveur neutralises`() {
        val plan = OfflineSyncPlanner.plan(
            serverIds      = listOf(5, 5, 7),
            serverModified = mapOf(5 to "d5", 7 to "d7"),
            local          = emptyList()
        )

        assertEquals(listOf(5, 7), plan.toDownload)
    }
}
