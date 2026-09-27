package fr.thermostat6.app180.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RecipeTaxonomy
import fr.thermostat6.app180.data.analytics.AnalyticsService
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.ApiError
import fr.thermostat6.app180.data.network.RetryPolicy
import fr.thermostat6.app180.data.taxonomy.TaxonomyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Ordre de tri disponible dans la feuille de filtres.
 * Les valeurs [orderBy] et [order] sont envoyées directement à l'API WordPress.
 */
enum class SortOrder(
    val id: String,
    val orderBy: String,
    val order: String,
    val label: String
) {
    DATE_DESC("date_desc", "date",  "desc", "Plus récentes"),
    DATE_ASC( "date_asc",  "date",  "asc",  "Plus anciennes"),
    TITLE_AZ( "title_asc", "title", "asc",  "Titre A → Z");

    companion object {
        /** Tri par défaut si l'id est inconnu (`RecipeListView.swift:37`). */
        fun fromId(id: String): SortOrder = entries.firstOrNull { it.id == id } ?: DATE_DESC
    }
}

data class RecipeListUiState(
    val isLoading: Boolean      = true,
    val isRefreshing: Boolean   = false,
    val recipes: List<Recipe>   = emptyList(),
    val canLoadMore: Boolean    = true,
    val currentPage: Int        = 1,
    val sortOrder: SortOrder    = SortOrder.DATE_DESC,
    /** Slug `recipe_season` sélectionné dans la feuille (null = aucun). */
    val seasonSlug: String?     = null,
    /** Slug `recipe_category` sélectionné dans la feuille (null = aucun). */
    val categorySlug: String?   = null,
    val error: String?          = null
) {
    /** True si au moins un filtre (tri hors défaut, saison ou type) est actif. */
    val hasActiveFilters: Boolean
        get() = sortOrder != SortOrder.DATE_DESC || seasonSlug != null || categorySlug != null
}

/**
 * ViewModel de la liste paginée de recettes avec filtres.
 *
 * Le couple [initialTaxonomy] / [initialTermId] provient des arguments de
 * navigation (`rest_base` + ID de terme déjà résolu). Les filtres de la feuille
 * (tri, saison, type de plat) s'appliquent par-dessus et rechargent la page 1.
 *
 * Les filtres sont cumulatifs côté serveur : chaque taxonomie contribue un
 * paramètre `?{rest_base}={id}` distinct. Un filtre de la feuille portant sur la
 * même taxonomie que le filtre de navigation le remplace.
 *
 * Instancier via [factory].
 */
class RecipeListViewModel(
    application: Application,
    private val initialCategorySlug: String?,
    private val initialSeasonSlug: String?,
    private val initialPublicationSlug: String?,
) : AndroidViewModel(application) {

    private val api get() = ApiClient.wordPressApi

    private val _uiState = MutableStateFlow(RecipeListUiState())
    val uiState: StateFlow<RecipeListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { TaxonomyStore.loadIfNeeded() }
        fetchRecipes(page = 1)
        // Rejoué à chaque changement de session : les recettes en mémoire
        // portent le `recipe_locked` de l'ancien jeton (miroir `.task(id:)` de
        // RecipeListView.swift:108-115). Sans ce rejeu, revenir sur la liste
        // après une connexion depuis le paywall d'une fiche laisse les cartes
        // verrouillées de la session précédente.
        viewModelScope.launch {
            AuthService.contentGeneration.drop(1).collect {
                // `fetchRecipes(page = 1)` remet `currentPage` à 1 et recalcule
                // `canLoadMore` : la pagination repart d'une base saine.
                fetchRecipes(page = 1)
            }
        }
    }

    // ── Chargement ────────────────────────────────────────────────────────────

    private fun fetchRecipes(page: Int, isRefresh: Boolean = false) {
        val state = _uiState.value
        viewModelScope.launch {
            if (page == 1) {
                _uiState.value = if (isRefresh) {
                    state.copy(isRefreshing = true)
                } else {
                    state.copy(isLoading = true, recipes = emptyList())
                }
            }

            val recipes = runCatching {
                RetryPolicy.withRetry { api.getRecipes(
                    perPage         = 20,
                    page            = page,
                    orderBy         = state.sortOrder.orderBy,
                    order           = state.sortOrder.order,
                    taxonomyFilters = resolveTaxonomyFilters(state)
                ) }
            }.getOrElse { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading    = false,
                    isRefreshing = false,
                    error        = ApiError.from(throwable).userMessage
                )
                return@launch
            }

            _uiState.value = if (page == 1) {
                _uiState.value.copy(
                    isLoading    = false,
                    isRefreshing = false,
                    recipes      = recipes,
                    canLoadMore  = recipes.size == 20,
                    currentPage  = 1,
                    error        = null
                )
            } else {
                _uiState.value.copy(
                    isLoading   = false,
                    recipes     = _uiState.value.recipes + recipes,
                    canLoadMore = recipes.size == 20,
                    currentPage = page
                )
            }
        }
    }

    /**
     * Résout les slugs actifs en IDs de termes pour la requête.
     *
     * Le filtre de la feuille prend le pas sur celui de la navigation pour une
     * même taxonomie, miroir des `combinedSeasonSlug` / `combinedCategorySlug`
     * iOS (`apple/180/RecipeListView.swift:119-120`).
     *
     * Un slug introuvable est **omis** du filtre plutôt que traduit en ID
     * arbitraire : mieux vaut une liste non filtrée qu'une liste sans rapport.
     */
    private suspend fun resolveTaxonomyFilters(state: RecipeListUiState): Map<String, Int> {
        val wanted = listOfNotNull(
            (state.categorySlug ?: initialCategorySlug)?.let { RecipeTaxonomy.CATEGORY to it },
            (state.seasonSlug ?: initialSeasonSlug)?.let { RecipeTaxonomy.SEASON to it },
            initialPublicationSlug?.let { RecipeTaxonomy.PUBLICATION to it }
        )
        return wanted.mapNotNull { (taxonomy, slug) ->
            TaxonomyStore.termId(taxonomy, slug)?.let { taxonomy to it }
        }.toMap()
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    fun loadMore() {
        val state = _uiState.value
        if (!state.canLoadMore || state.isLoading || state.isRefreshing) return
        fetchRecipes(page = state.currentPage + 1)
    }

    /** Recharge la liste depuis la page 1 (déclenché par pull-to-refresh). */
    fun refresh() = fetchRecipes(page = 1, isRefresh = true)

    // ── Filtres ───────────────────────────────────────────────────────────────

    fun setSortOrder(order: SortOrder) {
        _uiState.value = _uiState.value.copy(sortOrder = order)
        fetchRecipes(page = 1)
    }

    /**
     * Applique la sélection de la feuille de filtres en un seul rechargement.
     * Miroir d'`onApply` (`apple/180/RecipeListView.swift:87`) : la feuille
     * modifie des sélections, l'écran ne recharge qu'à la validation.
     */
    fun applyFilters(sortOrder: SortOrder, seasonSlug: String?, categorySlug: String?) {
        // Miroir RecipeListView.swift:147 ; `null` devient « all ».
        AnalyticsService.filterApplied(
            sortOrder = sortOrder.id,
            season    = seasonSlug,
            dishType  = categorySlug
        )
        _uiState.value = _uiState.value.copy(
            sortOrder    = sortOrder,
            seasonSlug   = seasonSlug,
            categorySlug = categorySlug
        )
        fetchRecipes(page = 1)
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        fun factory(
            categorySlug: String?,
            seasonSlug: String?,
            publicationSlug: String?
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    val app = extras[APPLICATION_KEY]!!
                    return RecipeListViewModel(app, categorySlug, seasonSlug, publicationSlug) as T
                }
            }
    }
}
