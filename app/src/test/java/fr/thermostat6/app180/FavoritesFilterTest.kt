package fr.thermostat6.app180

import fr.thermostat6.app180.data.model.Embedded
import fr.thermostat6.app180.data.model.EmbeddedTerm
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RecipeTaxonomy
import fr.thermostat6.app180.data.model.RenderedContent
import fr.thermostat6.app180.ui.viewmodel.FavoritesFilter
import fr.thermostat6.app180.ui.viewmodel.FavoritesSortOrder
import fr.thermostat6.app180.ui.viewmodel.FavoritesUiState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Filtrage et tri **locaux** du Carnet.
 *
 * Miroir de `RecipeFilterBar.apply` (`apple/180/RecipeFilterBar.swift:108-116`),
 * de `presentOptions` (`FavoritesView.swift:46-55`) et du tri de
 * `filtered` (`FavoritesView.swift:31-39`).
 */
class FavoritesFilterTest {

    private fun recipe(
        id: Int,
        title: String,
        category: Pair<String, String>? = null,
        season: Pair<String, String>? = null
    ): Recipe {
        val terms = listOfNotNull(
            category?.let { EmbeddedTerm(name = it.second, slug = it.first, taxonomy = RecipeTaxonomy.CATEGORY) },
            season?.let { EmbeddedTerm(name = it.second, slug = it.first, taxonomy = RecipeTaxonomy.SEASON) }
        )
        return Recipe(
            id       = id,
            date     = "2026-01-01T00:00:00",
            title    = RenderedContent(title),
            embedded = Embedded(wpTerm = listOf(terms))
        )
    }

    private val carnet = listOf(
        recipe(1, "Tarte aux pommes", "dessert" to "Dessert", "automne" to "Automne"),
        recipe(2, "Ratatouille",      "plat" to "Plat",       "ete" to "Été"),
        recipe(3, "Bœuf bourguignon", "plat" to "Plat",       "hiver" to "Hiver"),
        recipe(4, "Apéro dînatoire",  "apero" to "Apéro")
    )

    // ── Filtrage ─────────────────────────────────────────────────────────────

    @Test
    fun `sans filtre l'ordre serveur est preserve`() {
        assertEquals(
            listOf(1, 2, 3, 4),
            FavoritesFilter.apply(carnet, seasonSlug = null, categorySlug = null).map { it.id }
        )
    }

    @Test
    fun `le filtre de categorie ne garde que les recettes concernees`() {
        assertEquals(
            listOf(2, 3),
            FavoritesFilter.apply(carnet, seasonSlug = null, categorySlug = "plat").map { it.id }
        )
    }

    @Test
    fun `le filtre de saison ne garde que les recettes concernees`() {
        assertEquals(
            listOf(2),
            FavoritesFilter.apply(carnet, seasonSlug = "ete", categorySlug = null).map { it.id }
        )
    }

    @Test
    fun `les deux filtres se combinent en ET`() {
        assertEquals(
            listOf(3),
            FavoritesFilter.apply(carnet, seasonSlug = "hiver", categorySlug = "plat").map { it.id }
        )
        assertEquals(
            emptyList<Int>(),
            FavoritesFilter.apply(carnet, seasonSlug = "ete", categorySlug = "dessert").map { it.id }
        )
    }

    @Test
    fun `le mot-cle filtre sur le titre sans tenir compte de la casse`() {
        assertEquals(
            listOf(1),
            FavoritesFilter.apply(carnet, keyword = "TARTE").map { it.id }
        )
        assertEquals(
            listOf(2),
            FavoritesFilter.apply(carnet, keyword = "  ratat  ").map { it.id }
        )
        assertEquals(
            emptyList<Int>(),
            FavoritesFilter.apply(carnet, keyword = "introuvable").map { it.id }
        )
    }

    @Test
    fun `le mot-cle se combine en ET avec les taxonomies`() {
        assertEquals(
            listOf(3),
            FavoritesFilter.apply(carnet, keyword = "b", categorySlug = "plat").map { it.id }
        )
        assertEquals(
            emptyList<Int>(),
            FavoritesFilter.apply(carnet, keyword = "tarte", categorySlug = "plat").map { it.id }
        )
    }

    @Test
    fun `une recette sans le terme filtre est ecartee`() {
        // La recette 4 n'a pas de saison : tout filtre saison l'exclut.
        assertEquals(
            emptyList<Int>(),
            FavoritesFilter.apply(listOf(carnet[3]), seasonSlug = "ete").map { it.id }
        )
    }

    // ── Options proposées ────────────────────────────────────────────────────

    @Test
    fun `seuls les termes reellement presents sont proposes`() {
        assertEquals(
            listOf("dessert" to "Dessert", "plat" to "Plat", "apero" to "Apéro"),
            FavoritesFilter.presentTerms(carnet, RecipeTaxonomy.CATEGORY)
        )
        // « printemps » n'est dans aucun favori : il n'est pas proposé.
        assertEquals(
            listOf("automne" to "Automne", "ete" to "Été", "hiver" to "Hiver"),
            FavoritesFilter.presentTerms(carnet, RecipeTaxonomy.SEASON)
        )
    }

    // ── Tri ──────────────────────────────────────────────────────────────────

    @Test
    fun `le tri par defaut preserve l'ordre serveur`() {
        val state = FavoritesUiState(recipes = carnet, sortOrder = FavoritesSortOrder.RECENT)
        assertEquals(listOf(1, 2, 3, 4), state.visibleRecipes.map { it.id })
    }

    @Test
    fun `le tri par titre est alphabetique insensible a la casse`() {
        val state = FavoritesUiState(recipes = carnet, sortOrder = FavoritesSortOrder.TITLE_AZ)
        assertEquals(
            listOf("Apéro dînatoire", "Bœuf bourguignon", "Ratatouille", "Tarte aux pommes"),
            state.visibleRecipes.map { it.cleanTitle }
        )
    }

    @Test
    fun `le tri s'applique par-dessus le filtrage`() {
        val state = FavoritesUiState(
            recipes      = carnet,
            sortOrder    = FavoritesSortOrder.TITLE_AZ,
            categorySlug = "plat"
        )
        assertEquals(listOf("Bœuf bourguignon", "Ratatouille"), state.visibleRecipes.map { it.cleanTitle })
    }

    @Test
    fun `hasActiveFilters distingue l'etat par defaut`() {
        assertEquals(false, FavoritesUiState(recipes = carnet).hasActiveFilters)
        assertEquals(true, FavoritesUiState(recipes = carnet, seasonSlug = "ete").hasActiveFilters)
        assertEquals(true, FavoritesUiState(recipes = carnet, categorySlug = "plat").hasActiveFilters)
        assertEquals(
            true,
            FavoritesUiState(recipes = carnet, sortOrder = FavoritesSortOrder.TITLE_AZ).hasActiveFilters
        )
    }
}
