package fr.thermostat6.app180.data.network

import fr.thermostat6.app180.data.model.AddFavoriteRequest
import fr.thermostat6.app180.data.model.FavoritesListResponse
import fr.thermostat6.app180.data.model.FavoritesSyncResponse
import fr.thermostat6.app180.data.model.HomePayload
import fr.thermostat6.app180.data.model.AppNotification
import fr.thermostat6.app180.data.model.MeResponse
import fr.thermostat6.app180.data.model.SyncFavoritesRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Base URL : ApiConfig.REST_V1_BASE_URL (origine canonique, https://www.180c.fr/)
// Namespace REST custom `180c/v1` : endpoints liés au compte (statut abonné, carnet).
// Auth : le Bearer JWT est injecté par authenticatedHttpClient, comme côté iOS
//        où chaque requête passe par `authenticatedRequest`
//        (apple/180/APIService.swift:20-26).
interface UserApi {

    // 6 — Statut abonné faisant autorité.
    // Miroir iOS : `"\(APIConfig.shared.restV1)/me"` — apple/180/AuthService.swift:206,
    // avec restV1 = "{origine}/wp-json/180c/v1" (apple/180/APIConfig.swift:69).
    @GET("wp-json/180c/v1/me")
    suspend fun getMe(): MeResponse

    // 8 — Composition de l'accueil pilotée par la rédaction.
    // Miroir iOS : `"\(APIConfig.shared.restV1)/home-recettes"` — apple/180/HomeService.swift:143.
    @GET("wp-json/180c/v1/home-recettes")
    suspend fun getHome(): HomePayload

    // 13 — Centre de notifications (feed serveur).
    // Le flux est public : la capture anonyme du LOT-13 répond 200.
    @GET("wp-json/180c/v1/notifications")
    suspend fun getNotifications(
        @Query("page")     page: Int = 1,
        @Query("per_page") perPage: Int = 20
    ): List<AppNotification>

    // ── Carnet (favoris serveur) ─────────────────────────────────────────────

    // 7.1 — Liste des favoris du compte authentifié.
    // Miroir iOS : APIService.swift:187-193.
    @GET("wp-json/180c/v1/favorites")
    suspend fun getFavorites(): FavoritesListResponse

    // 7.2 — Ajout d'un favori. Corps JSON `{"recipe_id": …}`.
    // Miroir iOS : APIService.swift:196-205.
    @POST("wp-json/180c/v1/favorites")
    suspend fun addFavorite(@Body body: AddFavoriteRequest)

    // 7.3 — Retrait d'un favori, ID en segment de chemin.
    // Miroir iOS : APIService.swift:208-215.
    @DELETE("wp-json/180c/v1/favorites/{recipeId}")
    suspend fun deleteFavorite(@Path("recipeId") recipeId: Int)

    // 7.4 — Réconciliation last-write-wins ; renvoie l'état serveur.
    // Miroir iOS : APIService.swift:220-231.
    @POST("wp-json/180c/v1/favorites/sync")
    suspend fun syncFavorites(@Body body: SyncFavoritesRequest): FavoritesSyncResponse
}
