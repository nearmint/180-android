package fr.thermostat6.app180.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.thermostat6.app180.data.favorites.FavoritesManager
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.ApiError
import fr.thermostat6.app180.data.network.NetworkMonitor
import fr.thermostat6.app180.data.network.RetryPolicy
import fr.thermostat6.app180.data.offline.OfflineStore
import fr.thermostat6.app180.data.service.RecipeCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Tri local du Carnet (`apple/180/FavoritesView.swift:20-27`). */
enum class FavoritesSortOrder(val id: String, val label: String) {
    RECENT("recent", "Récemment ajoutées"),
    TITLE_AZ("title_asc", "Titre A → Z");

    companion object {
        val DEFAULT = RECENT
        fun fromId(id: String): FavoritesSortOrder = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

data class FavoritesUiState(
    val isLoading: Boolean    = false,
    val isRefreshing: Boolean = false,
    /** Recettes du carnet, dans l'ordre serveur (`created_at DESC`). */
    val recipes: List<Recipe> = emptyList(),
    val sortOrder: FavoritesSortOrder = FavoritesSortOrder.DEFAULT,
    val seasonSlug: String?   = null,
    val categorySlug: String? = null,
    /** Mot-clé de la barre de filtre locale du carnet. */
    val keyword: String       = "",
    /** Panne réseau — distincte d'un carnet vide. */
    val error: String?        = null
) {
    val hasActiveFilters: Boolean
        get() = seasonSlug != null || categorySlug != null || sortOrder != FavoritesSortOrder.DEFAULT

    /** Filtre local actif (mot-clé compris) — pilote les états vides. */
    val hasAnyFilter: Boolean
        get() = hasActiveFilters || keyword.isNotBlank()

    /**
     * Recettes après filtrage **local** puis tri local.
     * Miroir de `filtered` (`apple/180/FavoritesView.swift:31-39`) : le filtrage
     * préserve l'ordre serveur, le tri par titre s'applique par-dessus.
     */
    val visibleRecipes: List<Recipe>
        get() {
            val base = FavoritesFilter.apply(recipes, keyword, seasonSlug, categorySlug)
            return when (sortOrder) {
                FavoritesSortOrder.TITLE_AZ -> base.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.cleanTitle })
                FavoritesSortOrder.RECENT   -> base
            }
        }
}

/** Filtrage local du Carnet sur les termes embarqués. */
object FavoritesFilter {

    /**
     * Applique les filtres en préservant l'ordre d'entrée.
     * Miroir de `RecipeFilterBar.apply` (`apple/180/RecipeFilterBar.swift:108-116`) :
     * mot-clé sur le titre nettoyé (comparaison en minuscules), puis taxonomies,
     * le tout en ET.
     */
    fun apply(
        recipes: List<Recipe>,
        keyword: String = "",
        seasonSlug: String? = null,
        categorySlug: String? = null
    ): List<Recipe> {
        val kw = keyword.trim().lowercase()
        return recipes.filter { recipe ->
            (kw.isEmpty() || recipe.cleanTitle.lowercase().contains(kw)) &&
                (categorySlug == null || categorySlug in recipe.categorySlugs) &&
                (seasonSlug == null || seasonSlug in recipe.seasonSlugs)
        }
    }

    /**
     * Termes **réellement présents** dans les recettes chargées, dédoublonnés,
     * dans l'ordre de première apparition.
     * Miroir de `presentOptions` (`apple/180/FavoritesView.swift:46-55`).
     */
    fun presentTerms(recipes: List<Recipe>, taxonomy: String): List<Pair<String, String>> {
        val seen = LinkedHashMap<String, String>()
        recipes.forEach { recipe ->
            recipe.embeddedTerms(taxonomy).forEach { term ->
                seen.putIfAbsent(term.slug, term.name)
            }
        }
        return seen.map { it.key to it.value }
    }
}

/**
 * Ordre d'affichage du carnet.
 *
 * `FavoritesManager.favoriteIds` est un `Set` : la persistance DataStore perd
 * l'ordre `created_at DESC` que le serveur renvoie. Le tri par défaut du carnet
 * s'appelle pourtant « Récemment ajoutées » et ne trie rien côté client — sans
 * cette reconstruction, l'ordre rendu est celui de `wp/v2` (`orderby=date`),
 * c'est-à-dire l'ordre de **publication**.
 *
 * Miroir de `FavoritesView.swift:224-238` : « on NE re-trie PAS côté client,
 * l'ordre des IDs fait foi ».
 */
object FavoritesOrder {

    /**
     * Ordonne [localIds] selon [serverIds] (`created_at DESC`, le plus récent
     * d'abord).
     *
     * - Un ID local **absent** de la liste serveur passe en tête : c'est un
     *   favori que l'utilisateur vient d'ajouter, dont le `POST /favorites`
     *   n'a pas encore atterri quand le carnet se recharge. Le placer en tête
     *   est à la fois ce que l'utilisateur attend et ce que dira le serveur au
     *   prochain tour.
     * - Un ID serveur **absent** du carnet local est écarté : c'est un retrait
     *   local dont le `DELETE` n'a pas encore atterri.
     *
     * L'appartenance vient donc du local — seul état que l'utilisateur vient de
     * modifier — et l'ordre du serveur. Une liste serveur vide (visiteur non
     * connecté, appel en échec) laisse le carnet **tel quel** : jamais vidé.
     */
    fun resolve(localIds: Set<Int>, serverIds: List<Int>): List<Int> {
        if (serverIds.isEmpty()) return localIds.toList()
        val known = serverIds.toSet()
        return localIds.filter { it !in known } + serverIds.filter { it in localIds }
    }
}

/**
 * ViewModel de l'onglet Favoris.
 *
 * Observe [FavoritesManager.favoriteIds] et recharge les recettes depuis l'API
 * chaque fois que la liste d'IDs change (équivalent du `.task(id: hashValue)` iOS).
 *
 * L'appel passe par [RecipeCatalog.fetchByIds], point unique paginé par 100.
 */
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    /** Expose directement le StateFlow des IDs pour que l'UI puisse l'observer. */
    val favoriteIds: StateFlow<Set<Int>> = FavoritesManager.favoriteIds

    init {
        // Recharge les recettes à chaque changement des IDs favoris…
        viewModelScope.launch {
            FavoritesManager.favoriteIds.collect { ids ->
                fetchFavoriteRecipes(ids)
            }
        }
        // …et à chaque changement de session : l'ordre serveur et le
        // verrouillage des recettes dépendent du jeton
        // (miroir `.task(id:)` de FavoritesView.swift:107).
        viewModelScope.launch {
            AuthService.contentGeneration.drop(1).collect {
                fetchFavoriteRecipes(FavoritesManager.favoriteIds.value)
            }
        }
        // …et à chaque bascule de connectivité : le retour du réseau doit
        // reprendre la main sur la reconstruction locale, et sa perte l'inverse
        // (miroir `.task(id:)` de FavoritesView.swift:118).
        viewModelScope.launch {
            NetworkMonitor.isConnected.drop(1).collect {
                fetchFavoriteRecipes(FavoritesManager.favoriteIds.value)
            }
        }
    }

    // ── Filtres et tri (locaux) ───────────────────────────────────────────────

    /** Saisie de la barre de filtre locale — filtrage purement client. */
    fun onKeywordChange(keyword: String) {
        _uiState.value = _uiState.value.copy(keyword = keyword)
    }

    fun applyFilters(
        sortOrder: FavoritesSortOrder,
        seasonSlug: String?,
        categorySlug: String?
    ) {
        _uiState.value = _uiState.value.copy(
            sortOrder    = sortOrder,
            seasonSlug   = seasonSlug,
            categorySlug = categorySlug
        )
    }

    // ── Chargement ────────────────────────────────────────────────────────────

    private suspend fun fetchFavoriteRecipes(ids: Set<Int>) {
        if (ids.isEmpty()) {
            _uiState.value = FavoritesUiState(isLoading = false, recipes = emptyList())
            return
        }

        _uiState.value = FavoritesUiState(isLoading = true)

        // Hors ligne : le carnet se reconstruit intégralement depuis les fiches
        // téléchargées, sans toucher au réseau (FavoritesView.swift:225-230).
        if (!NetworkMonitor.isConnected.value) {
            loadFromOfflineStore(ids)
            return
        }

        val orderedIds = orderedFavoriteIds(ids)

        runCatching {
            RecipeCatalog.fetchByIds(orderedIds)
        }.onSuccess { recipes ->
            _uiState.value = FavoritesUiState(
                isLoading = false,
                recipes   = RecipeCatalog.ordered(recipes, orderedIds)
            )
        }.onFailure { throwable ->
            // Une panne n'est pas un carnet vide. Le message typé remplace le
            // `emptyList()` silencieux d'avant, qui affichait « aucune recette »
            // sur une simple coupure (FavoritesView.swift:250-253).
            _uiState.value = FavoritesUiState(
                isLoading = false,
                recipes   = emptyList(),
                error     = ApiError.from(throwable).userMessage
            )
        }
    }

    /**
     * IDs du carnet, dans l'ordre serveur quand il est joignable.
     *
     * `GET /180c/v1/favorites` est appelé à **chaque** chargement du carnet, et
     * pas seulement au lancement : c'est la seule source de l'ordre
     * `created_at DESC`, que le `Set` local ne conserve pas
     * (`FavoritesView.swift:226-233`).
     *
     * Hors session ou en échec, on garde l'ordre courant plutôt que de rendre
     * un carnet vide : une panne d'ordonnancement ne doit pas coûter le
     * contenu. L'option retenue laisse `FavoritesManager` inchangé — persister
     * une liste ordonnée toucherait le stockage DataStore et la sémantique de
     * `favoriteIds`, lue par huit fichiers, pour le même résultat visible.
     */
    private suspend fun orderedFavoriteIds(ids: Set<Int>): List<Int> {
        if (ApiClient.authToken == null) return ids.toList()
        val serverIds = runCatching {
            RetryPolicy.withRetry { ApiClient.userApi.getFavorites().ids }
        }.getOrElse { emptyList() }
        return FavoritesOrder.resolve(ids, serverIds)
    }

    /**
     * Reconstruit le carnet depuis l'[OfflineStore].
     *
     * L'ordre serveur (`created_at DESC`) n'est pas disponible hors ligne — les
     * IDs locaux vivent dans un `Set`. On retombe sur la **date de téléchargement
     * décroissante**, qui suit l'ordre d'ajout dans l'immense majorité des cas.
     *
     * Aucune fiche téléchargée alors que le carnet en contient ⇒ erreur typée
     * hors ligne : le carnet n'est pas vide, il est hors de portée.
     *
     * Miroir de `loadFromOfflineStore()` (FavoritesView.swift:263-278).
     */
    private suspend fun loadFromOfflineStore(favoriteIds: Set<Int>) {
        val store = OfflineStore.sharedOrNull
        val orderedIds = store?.allMetadata()
            ?.filter { it.id in favoriteIds }
            ?.sortedByDescending { it.downloadedAt }
            ?.map { it.id }
            .orEmpty()

        val recipes = store?.load(orderedIds).orEmpty()

        _uiState.value = FavoritesUiState(
            isLoading = false,
            recipes   = recipes,
            error     = if (recipes.isEmpty()) ApiError.Offline.userMessage else null
        )
    }

    // ── Pull-to-refresh ─────────────────────────────────────────────────────────

    /** Recharge les recettes favorites depuis l'API (déclenché par pull-to-refresh). */
    fun refresh() {
        viewModelScope.launch {
            val ids = FavoritesManager.favoriteIds.value
            if (ids.isEmpty()) {
                _uiState.value = _uiState.value.copy(recipes = emptyList(), error = null)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val orderedIds = orderedFavoriteIds(ids)
            val recipes = runCatching {
                RecipeCatalog.ordered(RecipeCatalog.fetchByIds(orderedIds), orderedIds)
            }.getOrElse { _uiState.value.recipes }
            _uiState.value = FavoritesUiState(isLoading = false, isRefreshing = false, recipes = recipes)
        }
    }

    // ── Toggle favori ─────────────────────────────────────────────────────────

    /**
     * Bascule le statut favori d'une recette.
     * Déclenche automatiquement un rechargement via [FavoritesManager.favoriteIds].
     */
    fun toggleFavorite(recipeId: Int) {
        viewModelScope.launch {
            // Slug relu dans le carnet déjà chargé : il porte le `recipe_id` de
            // l'event Umami `recipe_favorite`.
            val slug = _uiState.value.recipes.firstOrNull { it.id == recipeId }?.slug
            FavoritesManager.toggle(recipeId, slug = slug)
        }
    }

    fun isFavorite(recipeId: Int): Boolean = FavoritesManager.isFavorite(recipeId)
}
