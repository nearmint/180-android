package fr.thermostat6.app180

import com.google.gson.Gson
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.offline.OfflineImageExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extraction des visuels à embarquer hors ligne.
 *
 * La détection dans le corps et les étapes est **défensive** : l'éditorial n'y
 * met aucune image aujourd'hui, mais la fonctionnalité ne doit pas se périmer
 * le jour où il en ajoutera.
 *
 * Transposition des 7 tests de `apple/180Tests/OfflineSyncTests.swift`
 * (`OfflineImageExtractionTests`).
 */
class OfflineImageExtractionTest {

    private val gson = Gson()

    private fun recipe(json: String): Recipe = gson.fromJson(json, Recipe::class.java)

    @Test
    fun `la photo principale est extraite depuis l'embed`() {
        val r = recipe(
            """{"id":1,"date":"2026-01-01T00:00:00","title":{"rendered":"A"},
                "_embedded":{"wp:featuredmedia":[{"source_url":"https://www.180c.fr/photo.webp"}]}}"""
        )

        assertEquals(listOf("https://www.180c.fr/photo.webp"), OfflineImageExtractor.imageUrls(r))
    }

    @Test
    fun `fiche sans visuel aucune URL aucun crash`() {
        val r = recipe("""{"id":1,"date":"2026-01-01T00:00:00","title":{"rendered":"A"}}""")

        assertTrue(OfflineImageExtractor.imageUrls(r).isEmpty())
    }

    @Test
    fun `les images du corps et des etapes sont detectees`() {
        val r = recipe(
            """{"id":1,"date":"2026-01-01T00:00:00","title":{"rendered":"A"},
                "content":{"rendered":"<p>Texte</p><img src=\"https://www.180c.fr/corps.jpg\" alt=\"x\">"},
                "steps":[{"step_title":"S1","step_content":"<img src='https://www.180c.fr/etape.png'>Suite"}],
                "_embedded":{"wp:featuredmedia":[{"source_url":"https://www.180c.fr/hero.webp"}]}}"""
        )

        val urls = OfflineImageExtractor.imageUrls(r)

        // La photo principale reste en tête : c'est elle qui est rendue.
        assertEquals("https://www.180c.fr/hero.webp", urls.first())
        assertTrue(urls.contains("https://www.180c.fr/corps.jpg"))
        assertTrue(urls.contains("https://www.180c.fr/etape.png"))
    }

    @Test
    fun `une meme URL n'est collectee qu'une fois`() {
        val r = recipe(
            """{"id":1,"date":"2026-01-01T00:00:00","title":{"rendered":"A"},
                "content":{"rendered":"<img src=\"https://www.180c.fr/hero.webp\"><img src=\"https://www.180c.fr/hero.webp\">"},
                "_embedded":{"wp:featuredmedia":[{"source_url":"https://www.180c.fr/hero.webp"}]}}"""
        )

        assertEquals(1, OfflineImageExtractor.imageUrls(r).size)
    }

    @Test
    fun `les URI data et les schemas non HTTP sont ignores`() {
        val r = recipe(
            """{"id":1,"date":"2026-01-01T00:00:00","title":{"rendered":"A"},
                "content":{"rendered":"<img src=\"data:image/gif;base64,R0lGODlh\"><img src=\"https://www.180c.fr/ok.jpg\">"}}"""
        )

        assertEquals(listOf("https://www.180c.fr/ok.jpg"), OfflineImageExtractor.imageUrls(r))
    }

    @Test
    fun `extraction HTML brute guillemets simples ou doubles casse libre`() {
        val html = """<IMG SRC = "https://a.fr/1.jpg"><img src='https://a.fr/2.jpg'>"""

        assertEquals(
            listOf("https://a.fr/1.jpg", "https://a.fr/2.jpg"),
            OfflineImageExtractor.imageSources(html)
        )
    }

    @Test
    fun `HTML sans image donne une liste vide`() {
        assertTrue(OfflineImageExtractor.imageSources("").isEmpty())
        assertTrue(OfflineImageExtractor.imageSources("<p>Aucune image ici</p>").isEmpty())
    }
}
