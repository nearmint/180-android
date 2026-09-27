package fr.thermostat6.app180

import fr.thermostat6.app180.data.model.Embedded
import fr.thermostat6.app180.data.model.EmbeddedMedia
import fr.thermostat6.app180.data.model.IngredientGroup
import fr.thermostat6.app180.data.model.IngredientItem
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RecipeStep
import fr.thermostat6.app180.data.model.RenderedContent
import fr.thermostat6.app180.data.offline.OfflineStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Persistance des fiches hors ligne.
 *
 * Transposition des 12 tests de `apple/180Tests/OfflineStoreTests.swift`. Le
 * store prend sa racine en paramètre — même parti pris que
 * [fr.thermostat6.app180.data.service.DiskCache] — donc ces tests tournent en
 * JVM pure, sans Robolectric ni émulateur.
 */
class OfflineStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun store(directoryName: String = "offline") =
        OfflineStore(File(temporaryFolder.root, directoryName))

    private fun recipe(
        id: Int = 13400072,
        modified: String? = "2026-07-20T09:15:00",
        imageUrl: String? = "https://www.180c.fr/img/tarte.webp"
    ) = Recipe(
        id       = id,
        date     = "2026-07-01T10:00:00",
        modified = modified,
        title    = RenderedContent("Tarte aux figues"),
        excerpt  = RenderedContent("Une tarte de fin d'été"),
        content  = RenderedContent("<p>corps</p>"),
        recipeIntro  = "Introduction éditoriale",
        servings     = 6,
        servingsUnit = "personnes",
        recipeLocked = false,
        ingredientsGroups = listOf(
            IngredientGroup(
                groupLabel = "Pâte",
                items      = listOf(IngredientItem("250 g de farine"), IngredientItem("125 g de beurre"))
            )
        ),
        steps = listOf(RecipeStep("Préparer", "<p>Mélanger la farine et le beurre.</p>")),
        embedded = imageUrl?.let { Embedded(wpFeaturedmedia = listOf(EmbeddedMedia(it))) }
    )

    // ── Aller-retour ─────────────────────────────────────────────────────────

    @Test
    fun `save puis load restitue la fiche complete`() = runBlocking {
        val store = store()
        val original = recipe()

        store.save(original)
        val reloaded = store.load(original.id)

        assertNotNull(reloaded)
        assertEquals(original.id, reloaded!!.id)
        assertEquals("Tarte aux figues", reloaded.cleanTitle)
        assertEquals("Introduction éditoriale", reloaded.introText)
        assertEquals("Pour 6 personnes", reloaded.servingsText)
        assertEquals(1, reloaded.ingredientGroups.size)
        assertEquals(listOf("250 g de farine", "125 g de beurre"), reloaded.ingredientGroups[0].lines)
        assertEquals(1, reloaded.preparationSteps.size)
        assertEquals("Mélanger la farine et le beurre.", reloaded.preparationSteps[0].cleanContent)
        assertEquals("https://www.180c.fr/img/tarte.webp", reloaded.imageURL)
        assertFalse("le verrouillage doit survivre à l'aller-retour", reloaded.isLocked)
    }

    @Test
    fun `has est vrai apres save, faux avant`() = runBlocking {
        val store = store()

        assertFalse(store.has(13400072))
        store.save(recipe())
        assertTrue(store.has(13400072))
    }

    @Test
    fun `load d'une fiche absente renvoie null`() = runBlocking {
        assertNull(store().load(999_999))
    }

    // ── Images ───────────────────────────────────────────────────────────────

    @Test
    fun `les octets d'image sont restitues a l'identique`() = runBlocking {
        // Aucune recompression, aucun recadrage : le rendu hors ligne doit être
        // strictement celui du online.
        val store = store()
        val url = "https://www.180c.fr/img/tarte.webp"
        val bytes = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x00, 0x1F, 0x2E.toByte(), 0x7F)

        store.save(recipe(), images = mapOf(url to bytes))

        assertArrayEquals(bytes, store.imageData(url))
    }

    @Test
    fun `une URL d'image inconnue ne renvoie rien`() = runBlocking {
        val store = store()
        store.save(recipe(), images = mapOf("https://www.180c.fr/a.webp" to byteArrayOf(1, 2, 3)))

        assertNull(store.imageData("https://www.180c.fr/inconnue.webp"))
    }

    // ── Métadonnées ──────────────────────────────────────────────────────────

    @Test
    fun `les metadonnees portent le modified serveur`() = runBlocking {
        val store = store()

        store.save(recipe(modified = "2026-07-22T08:30:00"), nowMillis = 1_700_000_000_000L)

        val meta = store.metadata(13400072)
        assertNotNull(meta)
        assertEquals("2026-07-22T08:30:00", meta!!.modified)
        assertEquals(1_700_000_000_000L, meta.downloadedAt)
        assertEquals(0, meta.imageCount)
    }

    // ── Suppression ──────────────────────────────────────────────────────────

    @Test
    fun `delete retire la fiche, ses images et son entree d'index`() = runBlocking {
        val store = store()
        val url = "https://www.180c.fr/img/tarte.webp"
        store.save(recipe(id = 1), images = mapOf(url to byteArrayOf(9, 9, 9)))
        store.save(recipe(id = 2, imageUrl = null))

        store.delete(1)

        assertFalse(store.has(1))
        assertNull(store.load(1))
        assertNull("les octets d'image doivent disparaître avec la fiche", store.imageData(url))
        assertTrue("la suppression doit être ciblée", store.has(2))
        assertEquals(listOf(2), store.allMetadata().map { it.id })
    }

    @Test
    fun `deleteAll vide le store et le laisse reutilisable`() = runBlocking {
        val store = store()
        store.save(recipe(id = 1))
        store.save(recipe(id = 2))

        store.deleteAll()

        assertTrue(store.storedIds().isEmpty())
        assertEquals(0L, store.totalSizeBytes())

        // Réutilisable immédiatement, sans réinstanciation.
        store.save(recipe(id = 3))
        assertTrue(store.has(3))
        assertNotNull(store.load(3))
    }

    // ── Taille ───────────────────────────────────────────────────────────────

    @Test
    fun `totalSizeBytes croit avec le contenu ecrit`() = runBlocking {
        val store = store()
        assertEquals(0L, store.totalSizeBytes())

        store.save(recipe(id = 1))
        val afterFirst = store.totalSizeBytes()
        assertTrue("la première fiche doit peser", afterFirst > 0L)

        store.save(recipe(id = 2), images = mapOf("https://www.180c.fr/b.webp" to ByteArray(4_096)))
        val afterSecond = store.totalSizeBytes()

        assertTrue("la taille doit croître", afterSecond > afterFirst)
        assertTrue("les octets d'image doivent être comptés", afterSecond - afterFirst >= 4_096)
    }

    // ── Persistance de l'index ───────────────────────────────────────────────

    @Test
    fun `l'index survit a une reouverture du store`() = runBlocking {
        val directory = File(temporaryFolder.root, "persistant")
        OfflineStore(directory).save(recipe(id = 77, modified = "2026-07-25T12:00:00"))

        val reopened = OfflineStore(directory)

        assertTrue(reopened.has(77))
        assertEquals("2026-07-25T12:00:00", reopened.metadata(77)?.modified)
        assertNotNull("la fiche elle-même doit être relisible", reopened.load(77))
    }

    // ── Exclusion de sauvegarde ──────────────────────────────────────────────

    @Test
    fun `le repertoire est exclu de la sauvegarde automatique`() {
        // Équivalent Android de l'attribut d'inode `isExcludedFromBackup` iOS :
        // l'exclusion est déclarative, et les DEUX canaux doivent être couverts
        // (sauvegarde cloud sous API 31, extraction au-delà).
        val extraction = resource("data_extraction_rules.xml")
        val fullBackup = resource("backup_rules.xml")
        val expected = """<exclude domain="file" path="${OfflineStore.DIRECTORY_NAME}" />"""

        assertTrue(
            "backup_rules.xml doit exclure ${OfflineStore.DIRECTORY_NAME}",
            fullBackup.contains(expected)
        )
        assertEquals(
            "data_extraction_rules.xml doit l'exclure de cloud-backup ET device-transfer",
            2,
            Regex(Regex.escape(expected)).findAll(extraction).count()
        )
    }

    private fun resource(name: String): String {
        // Gradle exécute les tests unitaires avec le répertoire du module pour
        // dossier courant ; on remonte tant que `src/main/res` n'est pas trouvé.
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "src/main/res/xml/$name")
            if (candidate.exists()) return candidate.readText()
            directory = directory.parentFile
        }
        throw AssertionError("Ressource introuvable : $name")
    }

    // ── Formatage ────────────────────────────────────────────────────────────

    @Test
    fun `taille formatee lisible`() {
        assertEquals("0 o", OfflineStore.formatted(0))
        assertEquals("999 o", OfflineStore.formatted(999))
        assertEquals("1,0 ko", OfflineStore.formatted(1_000))
        assertEquals("12,4 Mo", OfflineStore.formatted(12_400_000))
        assertEquals("1,5 Go", OfflineStore.formatted(1_500_000_000))
    }
}
