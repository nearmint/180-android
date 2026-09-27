package fr.thermostat6.app180

import com.google.gson.Gson
import fr.thermostat6.app180.data.model.AddFavoriteRequest
import fr.thermostat6.app180.data.model.FavoritesListResponse
import fr.thermostat6.app180.data.model.FavoritesSyncResponse
import fr.thermostat6.app180.data.model.SyncFavoriteItem
import fr.thermostat6.app180.data.model.SyncFavoritesRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Décodage et encodage du carnet serveur (`180c/v1/favorites`).
 *
 * Les fixtures sont **dérivées du Swift** (`apple/180/Models.swift:226-242`,
 * `apple/180/APIService.swift:184-231`) et non capturées : les routes exigent un
 * JWT, et le brief interdit tout appel authentifié.
 */
class FavoritesDecodingTest {

    private val gson = Gson()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Fixture manquante : $name"
        }.bufferedReader().use { it.readText() }

    // ── GET /favorites ───────────────────────────────────────────────────────

    @Test
    fun `la liste de favoris se decode`() {
        val res = gson.fromJson(fixture("favorites_list.json"), FavoritesListResponse::class.java)

        assertEquals(listOf(13400072, 13400023, 13400019), res.ids)
        assertEquals(3, res.count)
    }

    @Test
    fun `une liste vide ou tronquee se decode en carnet vide`() {
        assertEquals(emptyList<Int>(), gson.fromJson("""{"ids":[],"count":0}""", FavoritesListResponse::class.java).ids)
        assertEquals(emptyList<Int>(), gson.fromJson("{}", FavoritesListResponse::class.java).ids)
        assertEquals(emptyList<Int>(), gson.fromJson("""{"ids":null}""", FavoritesListResponse::class.java).ids)
    }

    // ── POST /favorites/sync ─────────────────────────────────────────────────

    @Test
    fun `l'etat reconcilie se decode`() {
        val res = gson.fromJson(fixture("favorites_sync.json"), FavoritesSyncResponse::class.java)

        assertEquals(3, res.favorites.size)
        assertEquals(listOf(13400072, 13400019, 13399988), res.ids)
    }

    @Test
    fun `une reponse de sync vide ou partielle ne casse pas la lecture des IDs`() {
        assertEquals(emptyList<Int>(), gson.fromJson("{}", FavoritesSyncResponse::class.java).ids)
        assertEquals(emptyList<Int>(), gson.fromJson("""{"favorites":[]}""", FavoritesSyncResponse::class.java).ids)
        // Élément sans `id` : ignoré plutôt que décodé en 0, qui polluerait le carnet.
        assertEquals(
            listOf(42),
            gson.fromJson("""{"favorites":[{"id":42},{}]}""", FavoritesSyncResponse::class.java).ids
        )
    }

    // ── Corps de requête ─────────────────────────────────────────────────────

    @Test
    fun `le corps d'ajout porte recipe_id`() {
        assertEquals("""{"recipe_id":13400072}""", gson.toJson(AddFavoriteRequest(recipeId = 13400072)))
    }

    @Test
    fun `le corps de sync porte favorites recipe_id favorited updated_at`() {
        val body = SyncFavoritesRequest(
            favorites = listOf(
                SyncFavoriteItem(recipeId = 7, favorited = true, updatedAt = "2026-07-25T12:00:00Z")
            )
        )
        val json = gson.toJson(body)

        // Clés exactes attendues par le serveur (FavoritesManager.swift:89-91).
        assertTrue(json.contains(""""favorites":["""))
        assertTrue(json.contains(""""recipe_id":7"""))
        assertTrue(json.contains(""""favorited":true"""))
        assertTrue(json.contains(""""updated_at":"2026-07-25T12:00:00Z""""))
    }
}
