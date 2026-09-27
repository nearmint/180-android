package fr.thermostat6.app180

import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RecipePaging
import fr.thermostat6.app180.data.model.RenderedContent
import fr.thermostat6.app180.data.service.RecipeCatalog
import fr.thermostat6.app180.ui.viewmodel.FavoritesOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pagination du lot `include=` et ordre de restitution du carnet.
 *
 * Les deux défauts couverts ici étaient silencieux : au-delà de 100 IDs le
 * carnet se vidait sans message, et en deçà il s'affichait dans l'ordre de
 * **publication** sous une étiquette « Récemment ajoutées ».
 */
class FavoritesOrderTest {

    private fun recipe(id: Int): Recipe = Recipe(
        id    = id,
        date  = "2026-01-01T00:00:00",
        title = RenderedContent("Recette $id")
    )

    // ── Pagination du lot ────────────────────────────────────────────────────

    /** Récupère les pages demandées et rend une recette par ID. */
    private fun catalog(ids: List<Int>): Pair<List<Recipe>, List<List<Int>>> {
        val seen = mutableListOf<List<Int>>()
        val recipes = runBlocking {
            RecipeCatalog.fetchByIds(ids) { page ->
                seen += page
                page.map { recipe(it) }
            }
        }
        return recipes to seen
    }

    @Test
    fun `aucun id ne declenche aucun appel`() {
        val (recipes, pages) = catalog(emptyList())

        assertTrue(recipes.isEmpty())
        assertTrue("une liste vide ne doit toucher au réseau en rien", pages.isEmpty())
    }

    @Test
    fun `un seul id tient en une page`() {
        val (recipes, pages) = catalog(listOf(7))

        assertEquals(listOf(listOf(7)), pages)
        assertEquals(listOf(7), recipes.map { it.id })
    }

    @Test
    fun `cent ids passent encore en une seule page`() {
        val ids = (1..100).toList()

        val (recipes, pages) = catalog(ids)

        assertEquals(1, pages.size)
        assertEquals(100, pages[0].size)
        assertEquals(ids, recipes.map { it.id })
    }

    @Test
    fun `cent-et-un ids basculent sur deux pages sans perte`() {
        // Le seuil exact : c'est là que `per_page` dépassait la borne `wp/v2`,
        // que le serveur répondait 400 et que le carnet entier restait vide.
        val ids = (1..101).toList()

        val (recipes, pages) = catalog(ids)

        assertEquals(listOf(100, 1), pages.map { it.size })
        assertEquals(ids, recipes.map { it.id })
    }

    @Test
    fun `deux-cent-cinquante ids se paginent en trois appels concatenes dans l'ordre`() {
        val ids = (1..250).toList()

        val (recipes, pages) = catalog(ids)

        assertEquals(listOf(100, 100, 50), pages.map { it.size })
        assertEquals("les pages couvrent les IDs demandés, en ordre", ids, pages.flatten())
        assertEquals("la concaténation suit l'ordre des pages", ids, recipes.map { it.id })
        assertTrue(
            "aucune page ne doit dépasser la borne serveur",
            pages.all { it.size <= RecipePaging.MAX_PER_PAGE }
        )
    }

    @Test
    fun `une page en echec abandonne le lot entier`() {
        // Sémantique « tout ou rien » (APIService.swift:169-170) : rendre un lot
        // amputé ferait passer une panne pour un carnet raccourci. L'appelant,
        // seul à connaître son écran, décide quoi en montrer.
        val attempted = mutableListOf<List<Int>>()

        try {
            runBlocking {
                RecipeCatalog.fetchByIds((1..250).toList()) { page ->
                    attempted += page
                    if (attempted.size == 2) throw IllegalStateException("page 2 KO")
                    page.map { recipe(it) }
                }
            }
            fail("l'échec d'une page doit se propager")
        } catch (expected: IllegalStateException) {
            assertEquals("page 2 KO", expected.message)
        }

        assertEquals("la troisième page ne doit pas être demandée", 2, attempted.size)
    }

    // ── Re-mapping par la liste d'IDs ────────────────────────────────────────

    @Test
    fun `les recettes sont restituees dans l'ordre des ids demandes`() {
        // `include` ne trie pas : on simule une réponse WP en ordre de publication.
        val fromServer = listOf(recipe(10), recipe(20), recipe(30))

        val ordered = RecipeCatalog.ordered(fromServer, listOf(30, 10, 20))

        assertEquals(listOf(30, 10, 20), ordered.map { it.id })
    }

    @Test
    fun `un id absent de la reponse est ignore`() {
        // Recette dépubliée : elle disparaît de la liste sans la casser.
        val ordered = RecipeCatalog.ordered(listOf(recipe(20), recipe(10)), listOf(10, 999, 20))

        assertEquals(listOf(10, 20), ordered.map { it.id })
    }

    @Test
    fun `un id demande deux fois est rendu a chaque position`() {
        val ordered = RecipeCatalog.ordered(listOf(recipe(10)), listOf(10, 10))

        assertEquals(listOf(10, 10), ordered.map { it.id })
    }

    // ── Ordre du carnet ──────────────────────────────────────────────────────

    @Test
    fun `l'ordre serveur fait foi et n'est pas re-trie`() {
        // `created_at DESC` : le plus récemment ajouté d'abord — pas l'ordre de
        // publication, pas l'ordre du `Set` local.
        val resolved = FavoritesOrder.resolve(
            localIds  = setOf(10, 20, 30),
            serverIds = listOf(30, 10, 20)
        )

        assertEquals(listOf(30, 10, 20), resolved)
    }

    @Test
    fun `un favori tout juste ajoute passe en tete`() {
        // Le `POST /favorites` n'a pas encore atterri quand le carnet recharge :
        // l'ID est local mais absent de la réponse serveur. C'est le plus
        // récent, donc la tête de liste.
        val resolved = FavoritesOrder.resolve(
            localIds  = setOf(10, 20, 99),
            serverIds = listOf(20, 10)
        )

        assertEquals(listOf(99, 20, 10), resolved)
    }

    @Test
    fun `un favori tout juste retire n'apparait plus`() {
        // Symétrique : le `DELETE` n'a pas encore atterri, mais le geste de
        // l'utilisateur fait foi sur l'appartenance.
        val resolved = FavoritesOrder.resolve(
            localIds  = setOf(10),
            serverIds = listOf(20, 10)
        )

        assertEquals(listOf(10), resolved)
    }

    @Test
    fun `une liste serveur vide ne vide jamais le carnet`() {
        // Appel en échec ou visiteur hors session : on garde ce qu'on a.
        val resolved = FavoritesOrder.resolve(
            localIds  = setOf(10, 20),
            serverIds = emptyList()
        )

        assertEquals(setOf(10, 20), resolved.toSet())
    }

    @Test
    fun `un carnet local vide reste vide malgre un ordre serveur`() {
        // Purge à la déconnexion : le serveur peut encore répondre, l'état local
        // fait foi sur l'appartenance.
        assertTrue(FavoritesOrder.resolve(emptySet(), listOf(10, 20)).isEmpty())
    }
}
