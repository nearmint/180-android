package fr.thermostat6.app180.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.data.analytics.AnalyticsService
import fr.thermostat6.app180.data.analytics.UmamiEvents
import fr.thermostat6.app180.data.analytics.UmamiParams
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.ApiError
import fr.thermostat6.app180.data.network.RetryPolicy
import fr.thermostat6.app180.data.preferences.AppPreferences
import fr.thermostat6.app180.data.taxonomy.TaxonomyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


/** États possibles de l'écran de recherche. */
sealed class SearchState {
    /** Aucune recherche active — affiche l'historique et les sections "Explorer". */
    data object Idle : SearchState()

    /** Requête en cours. */
    data object Searching : SearchState()

    /** Résultats disponibles (après application des filtres). */
    data class Results(val recipes: List<Recipe>, val query: String) : SearchState()

    /** Requête effectuée, aucun résultat. */
    data object Empty : SearchState()

    /** Panne réseau — distincte de [Empty], qui suppose une réponse serveur. */
    data class Error(val message: String) : SearchState()
}

/**
 * ViewModel de l'écran de recherche.
 *
 * - Recherche déclenchée **à la validation du clavier** uniquement
 *   (`.onSubmit(of: .search)`, `apple/180/SearchView.swift:210-212`). Pendant la
 *   frappe, l'écran reste sur l'historique et les listes d'exploration ; vider
 *   le champ y ramène (`SearchView.swift:213-220`).
 * - Aucun filtre de taxonomie : l'iOS n'en propose pas sur la recherche
 *   (`apple/180/SearchView.swift:49-231`), les chips multi-sélection Android
 *   ont donc été retirées au titre de la parité descendante. Les taxonomies
 *   restent explorables via les listes « Explorer par saison / par type ».
 * - [searchHistory] : 5 dernières recherches (DataStore).
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val api get() = ApiClient.wordPressApi

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    /** Texte de recherche courant (piloté par l'UI). */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var lastQuery: String = ""

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Historique des recherches récentes (max 5), persisté dans DataStore. */
    val searchHistory: StateFlow<List<String>> = AppPreferences.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Pipeline de recherche debouncé ──────────────────────────────────────────

    init {
        viewModelScope.launch { TaxonomyStore.loadIfNeeded() }
        // Changement de session : une recherche affichée est relancée pour
        // rafraîchir le verrouillage des recettes (SearchView.swift:226-229).
        viewModelScope.launch {
            AuthService.contentGeneration.drop(1).collect {
                if (lastQuery.isNotEmpty()) runSearch(lastQuery)
            }
        }
    }

    /** Validation du clavier : seul déclencheur d'un appel réseau. */
    fun onSearchSubmit() {
        val query = _query.value.trim()
        if (query.isEmpty()) return
        viewModelScope.launch { runSearch(query) }
    }

    /** Relance une recherche depuis l'historique (`SearchView.swift:65-68`). */
    fun searchFromHistory(query: String) {
        _query.value = query
        viewModelScope.launch { runSearch(query) }
    }

    /**
     * Appelé à chaque frappe. Ne déclenche **aucun** appel réseau : vider le
     * champ ramène à l'état initial, comme l'iOS (`SearchView.swift:213-220`).
     */
    fun onQueryChange(text: String) {
        _query.value = text
        if (text.isEmpty()) {
            _searchState.value = SearchState.Idle
        }
    }

    private suspend fun runSearch(query: String) {
        if (query.isEmpty()) {
            _searchState.value = SearchState.Idle
            return
        }
        // Mesuré au **lancement** de la recherche, avant l'appel réseau : une
        // recherche qui échoue reste une recherche lancée
        // (`apple/180/SearchView.swift:250-255`). Requête normalisée comme sur
        // le site : minuscules, tronquée (`umami-events.js`, `bindSearch`).
        UmamiTracker.trackEvent(
            UmamiEvents.RECIPE_SEARCH,
            mapOf(
                UmamiParams.QUERY to query.lowercase().take(UmamiParams.MAX_QUERY_LENGTH)
            )
        )

        _searchState.value = SearchState.Searching
        lastQuery = query

        runCatching { RetryPolicy.withRetry { api.getRecipes(search = query, perPage = 20) } }
            .onSuccess { results ->
                // Miroir SearchView.swift:248.
                AnalyticsService.search(query = query, resultsCount = results.size)
                if (results.isNotEmpty()) AppPreferences.addSearchQuery(query)
                _searchState.value = if (results.isEmpty()) {
                    SearchState.Empty
                } else {
                    SearchState.Results(results, query)
                }
            }
            // Divergence délibérée avec l'iOS, actée : le Swift avale l'échec en
            // laissant `results` vide (`SearchView.swift:250-255`), ce qui affiche
            // « Aucune recette trouvée » alors que le réseau est tombé. Android
            // distingue les deux.
            .onFailure { throwable ->
                _searchState.value = SearchState.Error(ApiError.from(throwable).userMessage)
            }
    }

    /** Relance la dernière requête (déclenché par pull-to-refresh sur les résultats). */
    fun refresh() {
        val q = lastQuery
        if (q.isEmpty()) return
        viewModelScope.launch {
            _isRefreshing.value = true
            runSearch(q)
            _isRefreshing.value = false
        }
    }

    // ── Effacement / historique ──────────────────────────────────────────────────

    /** Efface la requête et les filtres, retour à l'état initial. */
    fun reset() {
        _query.value = ""
        lastQuery = ""
        _searchState.value = SearchState.Idle
    }

    fun clearHistory() {
        viewModelScope.launch { AppPreferences.clearSearchHistory() }
    }

    fun removeHistoryItem(query: String) {
        viewModelScope.launch { AppPreferences.removeSearchQuery(query) }
    }

}
