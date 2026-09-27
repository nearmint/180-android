package fr.thermostat6.app180

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RecipeFreshness
import fr.thermostat6.app180.data.model.RecipePaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sonde de fraîcheur du carnet hors ligne : décodage de `_fields=id,modified`
 * et découpage en pages acceptées par `wp/v2`.
 *
 * Le découpage n'est pas un détail d'implémentation : un `per_page` supérieur à
 * 100 fait répondre 400 au serveur, donc échouer la réconciliation entière.
 */
class RecipeFreshnessTest {

    private val gson = Gson()

    private fun freshnessList(json: String): List<RecipeFreshness> =
        gson.fromJson(json, object : TypeToken<List<RecipeFreshness>>() {}.type)

    // ── Décodage de la sonde ─────────────────────────────────────────────────

    @Test
    fun `la reponse allegee se decode`() {
        val entries = freshnessList(
            """[{"id":13400072,"modified":"2026-07-20T09:15:00"},
                {"id":13400073,"modified":"2026-07-21T11:00:00"}]"""
        )

        assertEquals(2, entries.size)
        assertEquals(13400072, entries[0].id)
        assertEquals("2026-07-20T09:15:00", entries[0].modified)
        assertEquals("2026-07-21T11:00:00", entries[1].modified)
    }

    @Test
    fun `un modified absent se decode en null sans echouer`() {
        val entries = freshnessList("""[{"id":42}]""")

        assertEquals(1, entries.size)
        assertNull("date absente attendue en null", entries[0].modified)
    }

    // ── Champs ajoutés au modèle Recipe ──────────────────────────────────────

    @Test
    fun `la recette decode modified et content`() {
        val recipe: Recipe = gson.fromJson(
            """{"id":7,"date":"2026-07-01T10:00:00",
                "modified":"2026-07-22T08:30:00",
                "title":{"rendered":"Tarte"},
                "content":{"rendered":"<p>corps</p>"}}""",
            Recipe::class.java
        )

        assertEquals("2026-07-22T08:30:00", recipe.modified)
        assertEquals("<p>corps</p>", recipe.content?.rendered)
    }

    @Test
    fun `une recette sans modified ni content reste decodable`() {
        // Les instantanés d'accueil déjà écrits sur disque ne portent pas ces
        // champs : les relire ne doit jamais échouer.
        val recipe: Recipe = gson.fromJson(
            """{"id":7,"date":"2026-07-01T10:00:00","title":{"rendered":"Tarte"}}""",
            Recipe::class.java
        )

        assertNull(recipe.modified)
        assertNull(recipe.content)
    }

    // ── Découpage en pages ───────────────────────────────────────────────────

    @Test
    fun `une liste vide ne produit aucune page`() {
        assertTrue(RecipePaging.chunk(emptyList()).isEmpty())
    }

    @Test
    fun `un seul id produit une page d'un element`() {
        val pages = RecipePaging.chunk(listOf(7))

        assertEquals(listOf(listOf(7)), pages)
    }

    @Test
    fun `une liste plus courte que la borne tient en une page`() {
        val pages = RecipePaging.chunk((1..37).toList())

        assertEquals(1, pages.size)
        assertEquals(37, pages[0].size)
    }

    @Test
    fun `exactement cent ids tiennent en une seule page`() {
        val pages = RecipePaging.chunk((1..100).toList())

        assertEquals(1, pages.size)
        assertEquals(100, pages[0].size)
    }

    @Test
    fun `cent-et-un ids basculent sur deux pages`() {
        // Le seuil exact : c'est là que le carnet cessait de se charger.
        val pages = RecipePaging.chunk((1..101).toList())

        assertEquals(listOf(100, 1), pages.map { it.size })
    }

    @Test
    fun `les doublons ne sont pas dedupliques`() {
        // La déduplication appartient à l'appelant : le découpage doit rester une
        // transformation strictement mécanique, sans décision sur le contenu.
        val pages = RecipePaging.chunk(listOf(5, 5, 7), maxPerPage = 2)

        assertEquals(listOf(5, 5, 7), pages.flatten())
    }

    @Test
    fun `au dela de cent ids la liste est paginee sans perte ni reordonnancement`() {
        val ids = (1..250).toList()

        val pages = RecipePaging.chunk(ids)

        assertEquals(3, pages.size)
        assertEquals(listOf(100, 100, 50), pages.map { it.size })
        assertEquals("ordre et contenu préservés", ids, pages.flatten())
        assertTrue(
            "aucune page ne doit dépasser la borne serveur",
            pages.all { it.size <= RecipePaging.MAX_PER_PAGE }
        )
    }
}
