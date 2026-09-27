package fr.thermostat6.app180.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modèles du carnet serveur (`180c/v1/favorites`).
 *
 * Miroir du décodage iOS `apple/180/Models.swift:226-242` et de la construction
 * des corps de requête dans `apple/180/APIService.swift:184-231`.
 *
 * Nullabilité prudente : une réponse tronquée ou vide se décode en carnet vide
 * plutôt qu'en exception, et l'appelant conserve alors son état local.
 */

/** Réponse `GET /180c/v1/favorites` — on ne consomme que les IDs. */
data class FavoritesListResponse(
    @SerializedName("ids")   private val idsRaw: List<Int>? = null,
    @SerializedName("count") val count: Int = 0
) {
    val ids: List<Int> get() = idsRaw.orEmpty()
}

/** Réponse `POST /180c/v1/favorites/sync` — état réconcilié complet. */
data class FavoritesSyncResponse(
    @SerializedName("favorites") private val favoritesRaw: List<FavoriteRef>? = null
) {
    val favorites: List<FavoriteRef> get() = favoritesRaw.orEmpty()

    /** IDs de recette de l'état serveur réconcilié (`APIService.swift:230`). */
    val ids: List<Int> get() = favorites.mapNotNull { it.id }
}

/** Référence minimale d'un favori ; `id` est l'ID de recette. */
data class FavoriteRef(
    @SerializedName("id") val id: Int? = null
)

/** Corps de `POST /180c/v1/favorites` — `{"recipe_id": …}` (`APIService.swift:203`). */
data class AddFavoriteRequest(
    @SerializedName("recipe_id") val recipeId: Int
)

/** Corps de `POST /180c/v1/favorites/sync` — `{"favorites": [...]}` (`APIService.swift:227`). */
data class SyncFavoritesRequest(
    @SerializedName("favorites") val favorites: List<SyncFavoriteItem>
)

/** Élément de réconciliation `{recipe_id, favorited, updated_at}` (`FavoritesManager.swift:89-91`). */
data class SyncFavoriteItem(
    @SerializedName("recipe_id")  val recipeId: Int,
    @SerializedName("favorited")  val favorited: Boolean,
    @SerializedName("updated_at") val updatedAt: String
)
