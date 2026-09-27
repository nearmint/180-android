package fr.thermostat6.app180.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RecipeTaxonomy
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.ApiError
import fr.thermostat6.app180.data.network.NetworkMonitor
import fr.thermostat6.app180.data.network.RetryPolicy
import fr.thermostat6.app180.data.offline.OfflineStore
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/** Nombre maximum de recommandations affichées sous la recette. */
private const val MAX_RECOMMENDATIONS = 6

/** En dessous de ce nombre, on complète les recommandations par la saison. */
private const val RECOMMENDATION_TOP_UP_THRESHOLD = 5

sealed class RecipeDetailState {
    data object Loading : RecipeDetailState()
    data class Loaded(
        val recipe: Recipe,
        val recommendations: List<Recipe>
    ) : RecipeDetailState()
    /**
     * Échec typé. On porte l'[ApiError] et non un message déjà aplati : l'écran
     * doit pouvoir distinguer « hors ligne » d'« indisponible » — c'est
     * précisément ce qui évite de présenter une coupure réseau comme une recette
     * dépubliée. Miroir de `TypedErrorView` (`apple/180/OfflineBanner.swift`).
     */
    data class Error(val error: ApiError) : RecipeDetailState()
}

/**
 * ViewModel de l'écran de détail d'une recette.
 *
 * - Charge la recette via son [recipeId] (`wp/v2/recipe/{id}?_embed`).
 * - Charge jusqu'à [MAX_RECOMMENDATIONS] recettes voisines : d'abord par saison,
 *   puis complétées par type de plat.
 *
 * Le contenu n'est plus parsé : il provient des champs ACF structurés portés par
 * [Recipe]. L'ancienne liste d'exclusion d'IDs de catégories (38, 76) n'a plus
 * d'objet — ces IDs appartenaient à la taxonomie `category` des `posts`, que le
 * CPT `recipe` n'utilise pas.
 *
 * Instancier via [factory] pour injecter [recipeId].
 */
class RecipeDetailViewModel(
    application: Application,
    private val recipeId: Int
) : AndroidViewModel(application) {

    private val api get() = ApiClient.wordPressApi

    private val _state = MutableStateFlow<RecipeDetailState>(RecipeDetailState.Loading)
    val state: StateFlow<RecipeDetailState> = _state.asStateFlow()

    init {
        loadRecipe()

        // Changement de session (login / logout) : la recette en mémoire porte
        // encore le `recipe_locked` de la session précédente. On la refetche avec
        // le nouveau jeton pour que le paywall se réévalue sans redémarrer l'app.
        // La valeur initiale est ignorée (premier affichage déjà couvert par
        // loadRecipe()). Miroir RecipeDetailView.swift:50-53.
        viewModelScope.launch {
            AuthService.contentGeneration.drop(1).collect { loadRecipe() }
        }

        // Bascule de connectivité : le retour du réseau doit reprendre la main
        // sur la copie locale (et rapporter les recommandations), sa perte
        // basculer sur le store plutôt que laisser un mur d'erreur.
        viewModelScope.launch {
            NetworkMonitor.isConnected.drop(1).collect { loadRecipe() }
        }
    }

    // ── Chargement ────────────────────────────────────────────────────────────

    fun loadRecipe() {
        viewModelScope.launch {
            _state.value = RecipeDetailState.Loading

            // Hors ligne : la fiche se rend intégralement depuis la copie
            // téléchargée. Aucune requête n'est émise — trois appels voués à
            // expirer ne feraient que retarder l'affichage.
            if (!NetworkMonitor.isConnected.value) {
                _state.value = loadFromOfflineStore()
                    ?: RecipeDetailState.Error(ApiError.Offline)
                return@launch
            }

            try {
                supervisorScope {
                    val recipeDef = async { RetryPolicy.withRetry { api.getRecipe(recipeId) } }

                    val recipe = recipeDef.await()

                    val recsDef = async { loadRecommendations(recipe) }
                    val recommendations = recsDef.await()

                    _state.value = RecipeDetailState.Loaded(
                        recipe          = recipe,
                        recommendations = recommendations
                    )
                }
            } catch (e: Exception) {
                // Le réseau reste prioritaire, mais s'il tombe pendant la
                // consultation (portail captif, serveur à terre), une copie
                // locale vaut mieux qu'un mur d'erreur.
                _state.value = loadFromOfflineStore()
                    ?: RecipeDetailState.Error(ApiError.from(e))
            }
        }
    }

    /**
     * Fiche téléchargée, ou `null` si elle n'est pas dans le store.
     *
     * Les recommandations ne sont pas téléchargées : hors ligne, la fiche se rend
     * seule. C'est un écart assumé et volontaire — embarquer les voisines de
     * chaque favori multiplierait le poids du carnet sans que l'utilisateur l'ait
     * demandé.
     *
     * Miroir de `loadOfflineCopy()` (`apple/180/RecipeDetailView.swift:393-402`).
     */
    private suspend fun loadFromOfflineStore(): RecipeDetailState.Loaded? {
        val store = OfflineStore.sharedOrNull ?: return null
        if (!store.has(recipeId)) return null
        val recipe = store.load(recipeId) ?: return null
        return RecipeDetailState.Loaded(recipe = recipe, recommendations = emptyList())
    }

    // ── Recommandations ───────────────────────────────────────────────────────

    /**
     * Charge jusqu'à [MAX_RECOMMENDATIONS] recettes voisines.
     *
     * Critère miroité de l'iOS (`apple/180/RecipeDetailView.swift:360-393`) :
     * 1. même type de plat (`recipe_category`) en priorité, `per_page=6` ;
     * 2. complété par la même saison (`recipe_season`) si moins de
     *    [RECOMMENDATION_TOP_UP_THRESHOLD] résultats ;
     * 3. dédoublonnage incluant la recette courante, puis troncature à 6.
     *
     * Les IDs de termes proviennent de la recette elle-même (déjà résolus par
     * le serveur) : aucun appel de résolution supplémentaire n'est nécessaire.
     */
    private suspend fun loadRecommendations(recipe: Recipe): List<Recipe> {
        val result = mutableListOf<Recipe>()

        suspend fun appendFrom(taxonomy: String, termId: Int) {
            val batch = runCatching {
                api.getRecipes(
                    perPage         = MAX_RECOMMENDATIONS,
                    taxonomyFilters = mapOf(taxonomy to termId)
                )
            }.getOrElse { emptyList() }

            result += batch.filter { r ->
                r.id != recipe.id && result.none { it.id == r.id }
            }
        }

        // Étape 1 : même type de plat (RecipeDetailView.swift:365-370)
        recipe.recipeCategory?.firstOrNull()?.let { appendFrom(RecipeTaxonomy.CATEGORY, it) }

        // Étape 2 : complété par la saison (RecipeDetailView.swift:373-378)
        if (result.size < RECOMMENDATION_TOP_UP_THRESHOLD) {
            recipe.recipeSeason?.firstOrNull()?.let { appendFrom(RecipeTaxonomy.SEASON, it) }
        }

        return result.take(MAX_RECOMMENDATIONS)
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        fun factory(recipeId: Int): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    val app = extras[APPLICATION_KEY]!!
                    return RecipeDetailViewModel(app, recipeId) as T
                }
            }
    }
}
