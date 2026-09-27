package fr.thermostat6.app180.data.service

import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RecipePaging
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.RetryPolicy

/**
 * Point unique de récupération de recettes par IDs.
 *
 * Miroir d'`APIService.fetchRecipesByIDs` (`apple/180/APIService.swift:157-182`).
 * Trois appelants dupliquaient la même boucle `include=` ; le seul qui ne
 * paginait pas — l'hydratation de l'accueil — laissait un compte de plus de 100
 * favoris ou une composition de plus de 100 `recipe_ids` retomber sur un 400
 * serveur transformé en liste vide. Regrouper la boucle ici est ce qui empêche
 * une quatrième rechute.
 */
object RecipeCatalog {

    /**
     * Recettes correspondant aux [ids], **paginées par
     * [RecipePaging.MAX_PER_PAGE]**.
     *
     * `per_page` était calé sur le nombre d'IDs : au-delà de 100, `wp/v2`
     * répond 400. Un utilisateur n'a pas à voir sa collection cesser de se
     * charger parce qu'elle a franchi un seuil qu'aucun écran ne mentionne.
     *
     * **Sémantique « tout ou rien »** : une page en échec propage son erreur et
     * abandonne le lot (`APIService.swift:169-170`). Rendre un lot amputé
     * ferait passer une panne pour un carnet raccourci ; c'est à l'appelant,
     * qui seul connaît son écran, de décider quoi en montrer.
     *
     * Le retry borné s'applique **par page** : une coupure sur la deuxième page
     * ne fait pas rejouer la première.
     *
     * L'ordre de retour n'est pas garanti par l'API (`include` ne trie pas) :
     * les appelants qui en dépendent re-mappent par [ordered].
     */
    suspend fun fetchByIds(ids: List<Int>): List<Recipe> =
        fetchByIds(ids) { page ->
            RetryPolicy.withRetry {
                ApiClient.wordPressApi.getRecipes(
                    include = page.joinToString(","),
                    perPage = page.size
                )
            }
        }

    /**
     * Variante testable : la récupération d'une page est injectée.
     *
     * Le découpage et la concaténation sont la partie qui a cassé en
     * production ; les isoler du réseau est ce qui les rend vérifiables.
     */
    internal suspend fun fetchByIds(
        ids: List<Int>,
        fetchPage: suspend (List<Int>) -> List<Recipe>
    ): List<Recipe> {
        if (ids.isEmpty()) return emptyList()
        val out = ArrayList<Recipe>(ids.size)
        RecipePaging.chunk(ids).forEach { page -> out += fetchPage(page) }
        return out
    }

    /**
     * Réordonne les recettes selon l'ordre des IDs demandés.
     *
     * Un ID absent (recette dépubliée) est simplement ignoré et ne casse ni le
     * rail ni le carnet (`HomeView.swift:479`, `:507-511`,
     * `FavoritesView.swift:236-238`). En cas de doublon dans la réponse, la
     * première occurrence gagne — miroir du `uniquingKeysWith: { first, _ in first }`
     * iOS.
     */
    fun ordered(recipes: List<Recipe>, ids: List<Int>): List<Recipe> {
        val byId = LinkedHashMap<Int, Recipe>(recipes.size)
        recipes.forEach { byId.putIfAbsent(it.id, it) }
        return ids.mapNotNull { byId[it] }
    }
}
