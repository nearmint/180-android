package fr.thermostat6.app180

import fr.thermostat6.app180.data.image.ImageCacheManager
import fr.thermostat6.app180.data.image.ImagePrefetcher
import fr.thermostat6.app180.data.model.Embedded
import fr.thermostat6.app180.data.model.EmbeddedMedia
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RenderedContent
import fr.thermostat6.app180.ui.theme.ImageRenderSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Verrou des tailles de décodage.**
 *
 * L'iOS choisit la largeur de rendu par style de carte (`maxRenderWidth`,
 * `apple/180/RecipeCards.swift:101-108`). Décoder plus large gaspille mémoire et
 * bande passante ; décoder plus étroit rend une photo floue. Ce test fige la
 * table pour que la valeur ne dérive pas silencieusement.
 */
class ImageRenderSizeTest {

    // ── Table de correspondance style → largeur ──────────────────────────────

    @Test
    fun `les largeurs de decodage sont celles de l'iOS`() {
        // `RecipeCards.swift:104` — vignette de la carte ligne
        assertEquals(120, ImageRenderSize.THUMBNAIL.widthDp)
        // `:105` — cartes rail et grille
        assertEquals(240, ImageRenderSize.CARD.widthDp)
        // `:106` — carte « à la une », et hero de détail compact
        assertEquals(450, ImageRenderSize.HERO.widthDp)
        // `RecipeDetailView.swift:116` — hero de détail sur grand écran
        assertEquals(800, ImageRenderSize.HERO_LARGE.widthDp)
    }

    @Test
    fun `les largeurs croissent avec la taille d'affichage`() {
        val widths = ImageRenderSize.entries.map { it.widthDp }
        assertEquals(widths.sorted(), widths)
        assertTrue("aucune largeur nulle ou négative", widths.all { it > 0 })
    }

    // ── Plafonds de cache ────────────────────────────────────────────────────

    @Test
    fun `les plafonds de cache sont ceux de l'iOS`() {
        // `CachedAsyncImage.swift:133` — totalCostLimit = 50 * 1024 * 1024
        assertEquals(50L * 1024L * 1024L, ImageCacheManager.MEMORY_CACHE_BYTES)
        // `:139` — même nom de répertoire des deux côtés
        assertEquals("image-cache", ImageCacheManager.DISK_CACHE_DIRECTORY)
    }

    // ── Sélection du préchargement above-the-fold ────────────────────────────

    private fun recipe(id: Int, image: String? = "https://www.180c.fr/$id.jpg") = Recipe(
        id       = id,
        date     = "2026-01-01T00:00:00",
        title    = RenderedContent("Recette $id"),
        embedded = image?.let { Embedded(wpFeaturedmedia = listOf(EmbeddedMedia(sourceURL = it))) }
    )

    @Test
    fun `le prechargement vise la une puis quatre cartes`() {
        val hydrated = (1..10).map { recipe(it) }

        val targets = ImagePrefetcher.targets(hydrated = hydrated, recent = emptyList())

        // `SplashView.swift:138-146` : 1 hero + 4 cartes, jamais plus.
        assertEquals(5, targets.size)
        assertEquals("https://www.180c.fr/1.jpg" to ImageRenderSize.HERO, targets.first())
        assertEquals(
            listOf(2, 3, 4, 5).map { "https://www.180c.fr/$it.jpg" to ImageRenderSize.CARD },
            targets.drop(1)
        )
    }

    @Test
    fun `sans blocs hydrates le prechargement se rabat sur les recentes`() {
        val recent = (1..3).map { recipe(it) }

        val targets = ImagePrefetcher.targets(hydrated = emptyList(), recent = recent)

        // `SplashView.swift:136-142` : `hydrated.first ?? recent.first`, puis
        // `hydrated.isEmpty ? recent : hydrated`.
        assertEquals(3, targets.size)
        assertEquals(ImageRenderSize.HERO, targets.first().second)
        assertTrue(targets.drop(1).all { it.second == ImageRenderSize.CARD })
    }

    @Test
    fun `les recettes sans visuel sont ignorees`() {
        val hydrated = listOf(
            recipe(1),
            recipe(2, image = null),
            recipe(3, image = ""),
            recipe(4)
        )

        val targets = ImagePrefetcher.targets(hydrated = hydrated, recent = emptyList())

        // Une URL absente ou vide ne doit pas produire de requête à vide.
        assertEquals(
            listOf(
                "https://www.180c.fr/1.jpg" to ImageRenderSize.HERO,
                "https://www.180c.fr/4.jpg" to ImageRenderSize.CARD
            ),
            targets
        )
    }

    @Test
    fun `une liste vide ne precharge rien`() {
        assertTrue(ImagePrefetcher.targets(hydrated = emptyList(), recent = emptyList()).isEmpty())
    }
}
