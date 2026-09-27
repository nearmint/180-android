package fr.thermostat6.app180

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RecipeTaxonomy
import fr.thermostat6.app180.data.model.Term
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Décodage du CPT `recipe` sur les **fixtures réelles** capturées depuis
 * `https://www.180c.fr` (cf. `app/src/test/resources/fixtures/`).
 *
 * Miroir Android de `apple/180Tests/RecipeDecodingTests.swift`.
 */
class RecipeDecodingTest {

    private val gson = Gson()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Fixture manquante : $name"
        }.bufferedReader().use { it.readText() }

    private fun recipeList(name: String): List<Recipe> =
        gson.fromJson(fixture(name), object : TypeToken<List<Recipe>>() {}.type)

    private fun termList(name: String): List<Term> =
        gson.fromJson(fixture(name), object : TypeToken<List<Term>>() {}.type)

    // ── Liste de recettes ────────────────────────────────────────────────────

    @Test
    fun `la liste de recettes se decode`() {
        val recipes = recipeList("recipes_embed.json")

        assertEquals(3, recipes.size)
        recipes.forEach { recipe ->
            assertTrue("id positif attendu", recipe.id > 0)
            assertTrue("titre non vide attendu", recipe.cleanTitle.isNotBlank())
            assertTrue("date non vide attendue", recipe.date.isNotBlank())
        }
    }

    // ── Recette unique + champs ACF ──────────────────────────────────────────

    @Test
    fun `les champs ACF de la recette unique se decodent`() {
        val recipe = gson.fromJson(fixture("recipe_single_embed.json"), Recipe::class.java)

        assertEquals(13400072, recipe.id)
        assertEquals("Pudding aux tomates et chorizo", recipe.cleanTitle)

        // recipe_intro est renseigné même sur une recette verrouillée
        assertTrue(recipe.introText.startsWith("Le pain sèche si vite"))

        // servings_unit présent, servings null dans cette fixture
        assertEquals("4 personnes", recipe.servingsUnit)
        assertNull(recipe.servings)
        // servings null ⇒ pas de libellé de portions fabriqué
        assertNull(recipe.servingsText)

        assertEquals(true, recipe.recipeIsPremium)
        assertEquals(true, recipe.recipeLocked)
        assertNull(recipe.sourceIssue)
    }

    @Test
    fun `une recette verrouillee expose des repeaters vides sans planter`() {
        val recipe = gson.fromJson(fixture("recipe_single_embed.json"), Recipe::class.java)

        // Le serveur vide le contenu premium pour un appelant anonyme.
        assertTrue(recipe.isLocked)
        assertTrue(recipe.ingredientGroups.isEmpty())
        assertTrue(recipe.preparationSteps.isEmpty())
    }

    // ── Termes embarqués (`wp:term`) ─────────────────────────────────────────

    @Test
    fun `les termes embarques sont decodes par taxonomie`() {
        val recipe = gson.fromJson(fixture("recipe_single_embed.json"), Recipe::class.java)

        assertEquals("Plat", recipe.categoryName)
        assertEquals("Été", recipe.seasonName)
        assertEquals("Les Cahiers de Delphine", recipe.publicationName)

        assertEquals(listOf("plat"), recipe.categorySlugs)
        assertEquals(listOf("ete"), recipe.seasonSlugs)

        // Les IDs de termes natifs restent disponibles pour le filtrage serveur.
        assertEquals(listOf(156), recipe.recipeCategory)
        assertEquals(listOf(151), recipe.recipeSeason)
        assertEquals(listOf(162), recipe.recipePublication)
    }

    @Test
    fun `l'image a la une provient de l'embed`() {
        val recipe = gson.fromJson(fixture("recipe_single_embed.json"), Recipe::class.java)
        assertNotNull(recipe.imageURL)
        assertTrue(recipe.imageURL!!.startsWith("https://"))
    }

    // ── Repli de verrouillage ────────────────────────────────────────────────

    @Test
    fun `recipe_locked absent retombe sur l'etat premium`() {
        val premiumSansLock = """{"id":1,"date":"2026-01-01T00:00:00","title":{"rendered":"X"},
            "recipe_is_premium":true}"""
        val gratuiteSansLock = """{"id":2,"date":"2026-01-01T00:00:00","title":{"rendered":"Y"},
            "recipe_is_premium":false}"""
        val riendutout = """{"id":3,"date":"2026-01-01T00:00:00","title":{"rendered":"Z"}}"""

        assertTrue(gson.fromJson(premiumSansLock, Recipe::class.java).isLocked)
        assertFalse(gson.fromJson(gratuiteSansLock, Recipe::class.java).isLocked)
        // Aucun signal : verrouillé par défaut, jamais de fuite de contenu.
        assertTrue(gson.fromJson(riendutout, Recipe::class.java).isLocked)
    }

    // ── Repeaters peuplés (structure déclarée par le serveur) ────────────────

    @Test
    fun `les repeaters peuples se decodent selon le schema serveur`() {
        // Structure conforme à `recipe_rest_schema.json` (ingredients_groups →
        // group_label + items[].line ; steps → step_title + step_content).
        val json = """{"id":4,"date":"2026-01-01T00:00:00","title":{"rendered":"T"},
            "servings":4,"servings_unit":"personnes","recipe_locked":false,
            "ingredients_groups":[
              {"group_label":"Pâte","items":[{"line":"200 g de farine"},{"line":"  "}]},
              {"group_label":"","items":[]}
            ],
            "steps":[
              {"step_title":"Mélanger","step_content":"<p>Fouetter le tout.</p>"},
              {"step_title":"","step_content":""}
            ]}"""

        val recipe = gson.fromJson(json, Recipe::class.java)

        // Groupe sans ligne exploitable écarté ; ligne blanche écartée.
        assertEquals(1, recipe.ingredientGroups.size)
        assertEquals("Pâte", recipe.ingredientGroups.first().label)
        assertEquals(listOf("200 g de farine"), recipe.ingredientGroups.first().lines)

        // Étape vide écartée ; le HTML de l'étape est nettoyé.
        assertEquals(1, recipe.preparationSteps.size)
        assertEquals("Mélanger", recipe.preparationSteps.first().cleanTitle)
        assertEquals("Fouetter le tout.", recipe.preparationSteps.first().cleanContent)

        assertEquals("Pour 4 personnes", recipe.servingsText)
        assertFalse(recipe.isLocked)
    }

    // ── Taxonomies ───────────────────────────────────────────────────────────

    @Test
    fun `les listes de termes se decodent et resolvent les slugs`() {
        val seasons = termList("taxonomy_recipe_season.json")
        val categories = termList("taxonomy_recipe_category.json")

        assertEquals(5, seasons.size)
        assertEquals(7, categories.size)

        val seasonBySlug = seasons.associate { it.slug to it.id }
        assertEquals(150, seasonBySlug["printemps"])
        assertEquals(151, seasonBySlug["ete"])

        val categoryBySlug = categories.associate { it.slug to it.id }
        assertEquals(156, categoryBySlug["plat"])

        // Slug inconnu → absent : l'appelant retire le filtre plutôt que
        // d'envoyer un ID faux, qui renverrait des résultats sans rapport.
        assertNull(seasonBySlug["saison-inexistante"])
    }

    @Test
    fun `les taxonomies du CPT recipe sont celles declarees par le serveur`() {
        val taxonomies: Map<String, Map<String, Any>> =
            gson.fromJson(fixture("taxonomies.json"), object : TypeToken<Map<String, Map<String, Any>>>() {}.type)

        val forRecipe = taxonomies.filterValues { taxo ->
            @Suppress("UNCHECKED_CAST")
            (taxo["types"] as? List<String>)?.contains("recipe") == true
        }.keys

        assertTrue(RecipeTaxonomy.CATEGORY in forRecipe)
        assertTrue(RecipeTaxonomy.SEASON in forRecipe)
        assertTrue(RecipeTaxonomy.PUBLICATION in forRecipe)
        // `recipe_type`, mentionné dans apple/CLAUDE.md, n'existe pas côté serveur.
        assertFalse("recipe_type" in forRecipe)
    }
}
