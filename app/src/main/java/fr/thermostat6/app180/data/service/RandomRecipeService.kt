package fr.thermostat6.app180.data.service

import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.RetryPolicy
import kotlin.random.Random

/** Taille du lot dans lequel on pioche (`apple/180/APIService.swift:173`). */
private const val RANDOM_POOL_SIZE = 100

/**
 * Tirage d'une recette au hasard — miroir de `fetchRandomRecipe`
 * (`apple/180/APIService.swift:166-176`).
 *
 * Le tirage se fait **côté app**, jamais par `orderby=rand` : cette valeur
 * n'appartient pas à l'énumération acceptée par `wp/v2/recipe`, WordPress rejette
 * la requête en 400 et le shake échouerait en silence. On récupère un lot de
 * recettes récentes et on y pioche localement.
 */
object RandomRecipeService {

    private val wordPressApi get() = ApiClient.wordPressApi

    /** `null` si le lot est vide ou si le réseau a échoué. */
    suspend fun draw(random: Random = Random.Default): Recipe? {
        val pool = runCatching {
            RetryPolicy.withRetry { wordPressApi.getRecipes(perPage = RANDOM_POOL_SIZE) }
        }.getOrElse { emptyList() }

        return pick(pool, random)
    }

    /**
     * Pioche pure, extraite pour être vérifiable sans réseau.
     * Miroir de `randomElement()` (`APIService.swift:174`).
     */
    internal fun pick(pool: List<Recipe>, random: Random = Random.Default): Recipe? =
        if (pool.isEmpty()) null else pool[random.nextInt(pool.size)]
}
