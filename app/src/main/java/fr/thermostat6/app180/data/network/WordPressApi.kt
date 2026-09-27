package fr.thermostat6.app180.data.network

import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RecipeFreshness
import fr.thermostat6.app180.data.model.Term
import fr.thermostat6.app180.data.model.UserProfile
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface WordPressApi {

    // 4.1 — Liste de recettes (CPT `recipe`, champs ACF + `_embed`)
    // 4.2 — Recettes par IDs (favoris) : passer include="id1,id2,id3"
    // 4.4 — Recherche plein texte : search=query
    //
    // Miroir iOS : APIService.swift:88 et :110 — mêmes paramètres, même ordre.
    // Le filtrage par taxonomie passe par [taxonomyFilters] (clé = rest_base,
    // valeur = ID de terme résolu au runtime via TaxonomyStore) : plus aucun ID
    // en dur ni nom de taxonomie codé dans la signature.
    @GET("recipe")
    suspend fun getRecipes(
        @Query("_embed")   embed: Boolean = true,
        @Query("per_page") perPage: Int = 20,
        @Query("page")     page: Int = 1,
        @Query("orderby")  orderBy: String = "date",
        @Query("order")    order: String = "desc",
        @Query(value = "include", encoded = true) include: String? = null,
        @Query("search")   search: String? = null,
        /** Permalien → recette : `?slug=` rend au plus un élément (App Links). */
        @Query("slug")     slug: String? = null,
        @QueryMap          taxonomyFilters: Map<String, Int> = emptyMap()
    ): List<Recipe>

    // 4.3 — Recette unique par ID (détail)
    @GET("recipe/{recipeId}")
    suspend fun getRecipe(
        @Path("recipeId") recipeId: Int,
        @Query("_embed")  embed: Boolean = true
    ): Recipe

    // 4.6 — Sonde de fraîcheur du carnet hors ligne.
    //
    // `_fields=id,modified` (incompatible avec `_embed`) ramène la seule donnée
    // dont la réconciliation a besoin pour décider d'un re-téléchargement.
    // L'appelant DOIT paginer par [RecipePaging.MAX_PER_PAGE] : au-delà
    // de 100, `wp/v2` répond 400.
    //
    // Miroir iOS : APIService.swift:178-196.
    @GET("recipe")
    suspend fun getRecipeFreshness(
        @Query(value = "include", encoded = true) include: String,
        @Query("per_page") perPage: Int,
        @Query(value = "_fields", encoded = true) fields: String = "id,modified"
    ): List<RecipeFreshness>

    // 4.5 — Termes d'une taxonomie `recipe_*` (résolution slug → ID, filtres).
    // `_fields` (incompatible avec `_embed`) limite la charge utile.
    // Miroir iOS : APIService.swift:138-147.
    @GET("{taxonomy}")
    suspend fun getTerms(
        @Path("taxonomy") taxonomy: String,
        @Query("per_page") perPage: Int = 100,
        @Query(value = "_fields", encoded = true) fields: String = "id,name,slug,count,parent"
    ): List<Term>

    // 5 — Profil utilisateur (requiert Authorization: Bearer)
    @GET("users/me")
    suspend fun getUserProfile(
        @Query("context") context: String = "edit"
    ): UserProfile
}
