package fr.thermostat6.app180

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fr.thermostat6.app180.data.model.Term
import fr.thermostat6.app180.data.taxonomy.TaxonomyStore
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ordres d'affichage des taxonomies, éprouvés sur les **fixtures réelles**.
 *
 * Miroir d'`orderedSeasons` / du tri par `count`
 * (`apple/180/TaxonomyStore.swift:32-46`).
 */
class TaxonomyOrderingTest {

    private val gson = Gson()

    private fun terms(name: String): List<Term> =
        gson.fromJson(
            checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
                "Fixture manquante : $name"
            }.bufferedReader().use { it.readText() },
            object : TypeToken<List<Term>>() {}.type
        )

    // ── Saisons ──────────────────────────────────────────────────────────────

    @Test
    fun `les saisons reelles sortent en ordre chronologique`() {
        val ordered = TaxonomyStore.orderedSeasons(terms("taxonomy_recipe_season.json"))

        // L'API les renvoie alphabétiquement (Automne, Été, Hiver, Printemps…) ;
        // l'ordre chronologique est le seul qui ait du sens pour l'utilisateur.
        assertEquals(
            listOf("printemps", "ete", "automne", "hiver", "toute-saison"),
            ordered.map { it.slug }
        )
    }

    @Test
    fun `toute-saison ferme la marche des saisons connues`() {
        val ordered = TaxonomyStore.orderedSeasons(terms("taxonomy_recipe_season.json"))
        assertEquals("toute-saison", ordered.last().slug)
    }

    @Test
    fun `un slug inconnu est relegue en fin et departage par nom`() {
        val injected = terms("taxonomy_recipe_season.json") + listOf(
            Term(id = 900, count = 5, name = "Zzz saison", slug = "saison-zzz"),
            Term(id = 901, count = 5, name = "Aaa saison", slug = "saison-aaa")
        )

        val ordered = TaxonomyStore.orderedSeasons(injected)

        assertEquals(
            listOf("printemps", "ete", "automne", "hiver", "toute-saison", "saison-aaa", "saison-zzz"),
            ordered.map { it.slug }
        )
    }

    // ── Types de plat ────────────────────────────────────────────────────────

    @Test
    fun `les types de plat sortent par nombre de recettes decroissant`() {
        val ordered = TaxonomyStore.orderedDishCategories(terms("taxonomy_recipe_category.json"))

        assertEquals(
            listOf("plat", "dessert", "entree", "accompagnement", "apero", "petit-dejeuner", "boisson"),
            ordered.map { it.slug }
        )
        // Décroissance stricte vérifiée de proche en proche.
        ordered.zipWithNext { a, b -> assertEquals(true, a.count >= b.count) }
    }

    @Test
    fun `a count egal l'ordre de l'API est preserve`() {
        val tied = listOf(
            Term(id = 1, count = 10, name = "Bravo", slug = "b"),
            Term(id = 2, count = 10, name = "Alpha", slug = "a"),
            Term(id = 3, count = 99, name = "Tete", slug = "t")
        )

        // `sortedByDescending` est stable : « b » reste avant « a ».
        assertEquals(listOf("t", "b", "a"), TaxonomyStore.orderedDishCategories(tied).map { it.slug })
    }
}
