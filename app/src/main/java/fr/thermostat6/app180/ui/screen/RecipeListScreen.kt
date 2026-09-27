package fr.thermostat6.app180.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fr.thermostat6.app180.data.analytics.UmamiScreens
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.model.Term
import fr.thermostat6.app180.data.taxonomy.TaxonomyStore
import fr.thermostat6.app180.ui.components.IconPastille
import fr.thermostat6.app180.ui.components.RecipeFilterOption
import fr.thermostat6.app180.ui.components.RecipeFilterSelection
import fr.thermostat6.app180.ui.components.RecipeFilterSheet
import fr.thermostat6.app180.ui.components.RecipeSortOption
import fr.thermostat6.app180.ui.screen.resolveDishIcon
import fr.thermostat6.app180.ui.screen.resolveSeasonIcon
import fr.thermostat6.app180.data.favorites.FavoritesManager
import fr.thermostat6.app180.navigation.Screen
import fr.thermostat6.app180.ui.components.RecipeRowCard
import fr.thermostat6.app180.ui.components.SkeletonRecipeList
import fr.thermostat6.app180.ui.viewmodel.RecipeListUiState
import fr.thermostat6.app180.ui.viewmodel.RecipeListViewModel
import fr.thermostat6.app180.ui.viewmodel.SortOrder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    title: String,
    categorySlug: String?,
    seasonSlug: String?,
    publicationSlug: String?,
    showFilters: Boolean,
    navController: NavController
) {
    val vm      = viewModel<RecipeListViewModel>(
        factory = RecipeListViewModel.factory(categorySlug, seasonSlug, publicationSlug)
    )
    val state   by vm.uiState.collectAsStateWithLifecycle()
    val favIds  by FavoritesManager.favoriteIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope   = rememberCoroutineScope()

    val seasons   by TaxonomyStore.seasons.collectAsStateWithLifecycle()
    val dishTypes by TaxonomyStore.dishCategories.collectAsStateWithLifecycle()

    val lazyState      = rememberLazyListState()
    var showFilterSheet by remember { mutableStateOf(false) }

    // Page vue Umami. Toutes les listes partagent l'url `/recettes` (parité web
    // / iOS) ; c'est le titre qui porte le filtre d'entrée — « Été »,
    // « Desserts »… (`apple/180/RecipeListView.swift:118`).
    LaunchedEffect(title) {
        UmamiTracker.trackScreen(UmamiScreens.RECIPE_LIST_PATH, title)
    }

    // Infinite scroll
    LaunchedEffect(lazyState) {
        snapshotFlow { lazyState.layoutInfo }.collect { layout ->
            val total   = layout.totalItemsCount
            val visible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (total > 0 && visible >= total - 3 && state.canLoadMore && !state.isLoading && !state.isRefreshing) {
                vm.loadMore()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {

        // ── TopAppBar ─────────────────────────────────────────────────────────
        TopAppBar(
            title           = { Text(title, maxLines = 1) },
            navigationIcon  = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
            },
            actions         = {
                if (showFilters) {
                    // Même bouton filtres que le Carnet : disque `.systemGray5`
                    // et point accent, jamais une icône nue avec un `Badge`
                    // Material (`apple/180/RecipeFilterSheet.swift:136-156`).
                    IconPastille(
                        icon               = Icons.Filled.FilterList,
                        contentDescription = "Filtres",
                        onClick            = { showFilterSheet = true },
                        showDot            = state.hasActiveFilters,
                        modifier           = Modifier.padding(end = 8.dp)
                    )
                }
            }
        )

        // ── Contenu ───────────────────────────────────────────────────────────
        when {
            state.isLoading && state.recipes.isEmpty() -> {
                // Squelette de **lignes** : la liste est rendue en
                // `RecipeRowCard`, pas en cartes de rail. L'ancien squelette
                // étirait une carte de 200 dp sur toute la largeur et ne
                // ressemblait à rien de ce qui allait s'afficher
                // (`apple/180/RecipeListView.swift:90-99`).
                SkeletonRecipeList(modifier = Modifier.fillMaxSize())
            }

            !state.isLoading && state.recipes.isEmpty() -> {
                // État vide
                Box(
                    modifier         = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            modifier           = Modifier.size(64.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text      = "Aucune recette",
                            style     = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        // Description de l'état vide, absente côté Android
                        // (`RecipeListView.swift:100-106`) : « Aucune recette »
                        // seul ne dit pas si la liste est vide ou en panne.
                        Text(
                            text      = "Aucune recette trouvée dans cette catégorie.",
                            style     = MaterialTheme.typography.bodyMedium,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (state.hasActiveFilters) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    vm.applyFilters(
                                        sortOrder    = SortOrder.DATE_DESC,
                                        seasonSlug   = null,
                                        categorySlug = null
                                    )
                                }
                            ) { Text("Réinitialiser les filtres") }
                        }
                    }
                }
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh    = { vm.refresh() },
                    modifier     = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state          = lazyState,
                        modifier       = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // En-tête : décompte des recettes chargées
                        item {
                            Text(
                                text     = "${state.recipes.size} recette${if (state.recipes.size > 1) "s" else ""}",
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(items = state.recipes, key = { it.id }) { recipe ->
                            // Gouttière de 16 dp et séparateur : l'iOS pose
                            // `listRowInsets(leading: 16, trailing: 16)` sur une
                            // `List(.plain)`, qui trace ses propres séparateurs
                            // (`apple/180/RecipeListView.swift:46-63`). Sans eux,
                            // vignette et cœur touchaient les bords de l'écran.
                            RecipeRowCard(
                                recipe          = recipe,
                                isFavorite      = recipe.id in favIds,
                                onClick         = { navController.navigate(Screen.RecipeDetail.createRoute(recipe.id)) },
                                onFavoriteClick = {
                                    scope.launch {
                                        FavoritesManager.toggle(recipe.id, slug = recipe.slug)
                                    }
                                },
                                modifier        = Modifier.padding(horizontal = 16.dp)
                            )
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                        }
                        // Pied de liste : indicateur de chargement ou fin de liste
                        item {
                            if (state.canLoadMore) {
                                Box(
                                    modifier         = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                            } else {
                                Text(
                                    text      = "Vous avez tout vu",
                                    style     = MaterialTheme.typography.bodySmall,
                                    color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier  = Modifier.fillMaxWidth().padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── BottomSheet filtres (composant partagé avec le Carnet) ────────────────
    if (showFilterSheet) {
        RecipeFilterSheet(
            sortOptions   = LIST_SORT_OPTIONS,
            seasons       = seasons.map {
                RecipeFilterOption(it.slug, it.name, resolveSeasonIcon(it.slug))
            },
            categories    = dishTypes.map {
                RecipeFilterOption(it.slug, it.name, resolveDishIcon(it.slug))
            },
            selection     = RecipeFilterSelection(
                sortId       = state.sortOrder.id,
                seasonSlug   = state.seasonSlug,
                categorySlug = state.categorySlug
            ),
            defaultSortId = SortOrder.DATE_DESC.id,
            onApply       = { applied ->
                vm.applyFilters(
                    sortOrder    = SortOrder.fromId(applied.sortId),
                    seasonSlug   = applied.seasonSlug,
                    categorySlug = applied.categorySlug
                )
                showFilterSheet = false
            },
            onDismiss     = { showFilterSheet = false }
        )
    }
}

/** Options de tri de « Toutes les recettes » (tri serveur, RecipeListView.swift:26-30). */
private val LIST_SORT_OPTIONS = listOf(
    RecipeSortOption(SortOrder.DATE_DESC.id, SortOrder.DATE_DESC.label),
    RecipeSortOption(SortOrder.DATE_ASC.id,  SortOrder.DATE_ASC.label),
    RecipeSortOption(SortOrder.TITLE_AZ.id,  SortOrder.TITLE_AZ.label)
)
