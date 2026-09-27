package fr.thermostat6.app180.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fr.thermostat6.app180.data.analytics.UmamiScreens
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.image.ImageCacheManager
import fr.thermostat6.app180.data.model.RecipeTaxonomy
import fr.thermostat6.app180.data.model.Term
import fr.thermostat6.app180.data.taxonomy.TaxonomyStore
import fr.thermostat6.app180.data.favorites.FavoritesManager
import fr.thermostat6.app180.navigation.Screen
import fr.thermostat6.app180.ui.theme.ScreenTitle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.vector.ImageVector
import fr.thermostat6.app180.ui.theme.Dimens
import fr.thermostat6.app180.ui.components.SectionHeader
import fr.thermostat6.app180.ui.components.SearchField
import fr.thermostat6.app180.ui.components.NetworkErrorState
import fr.thermostat6.app180.ui.components.RecipeRowCard
import fr.thermostat6.app180.ui.components.SkeletonRecipeList
import fr.thermostat6.app180.ui.components.WithOfflineBanner
import fr.thermostat6.app180.ui.viewmodel.SearchState
import fr.thermostat6.app180.ui.viewmodel.SearchViewModel
import fr.thermostat6.app180.util.HapticFeedbackManager
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController) {
    val vm            = viewModel<SearchViewModel>()
    val searchState   by vm.searchState.collectAsStateWithLifecycle()
    val query         by vm.query.collectAsStateWithLifecycle()
    val history       by vm.searchHistory.collectAsStateWithLifecycle()
    val isRefreshing  by vm.isRefreshing.collectAsStateWithLifecycle()
    val favIds        by FavoritesManager.favoriteIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val focusManager  = LocalFocusManager.current
    val seasons       by TaxonomyStore.seasons.collectAsStateWithLifecycle()
    val dishTypes     by TaxonomyStore.dishCategories.collectAsStateWithLifecycle()
    val scope         = rememberCoroutineScope()

    // Page vue Umami (`apple/180/SearchView.swift:233`).
    LaunchedEffect(Unit) {
        UmamiTracker.trackScreen(UmamiScreens.SEARCH_PATH, UmamiScreens.SEARCH_TITLE)
    }

    WithOfflineBanner(modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {

        // Titre d'écran, comme le `navigationTitle("Recherche")` de l'iOS
        // (`apple/180/SearchView.swift:208`) : la barre de recherche s'épingle
        // dessous, elle ne le remplace pas.
        Text(
            text     = "Recherche",
            style    = ScreenTitle,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        // ── Barre de recherche ────────────────────────────────────────────────
        SearchField(
            value         = query,
            onValueChange = { if (it.isEmpty()) vm.reset() else vm.onQueryChange(it) },
            placeholder   = "Chercher une recette…",
            // Seul déclencheur d'une recherche (SearchView.swift:210-212).
            onSubmit      = {
                vm.onSearchSubmit()
                focusManager.clearFocus()
            },
            modifier      = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ── Contenu selon l'état ──────────────────────────────────────────────
        when (val s = searchState) {

            is SearchState.Idle -> {
                IdleContent(
                    seasons       = seasons,
                    dishTypes     = dishTypes,
                    history       = history,
                    onHistoryTap  = { q ->
                        // `apple/180/SearchView.swift:69`
                        HapticFeedbackManager.selection()
                        vm.searchFromHistory(q)
                        focusManager.clearFocus()
                    },
                    onClearHistory = { vm.clearHistory() },
                    onRemoveItem  = { vm.removeHistoryItem(it) },
                    onSeasonTap   = { slug, name ->
                        navController.navigate(
                            Screen.RecipeList.createRoute(title = name, seasonSlug = slug)
                        )
                    },
                    onTypeTap = { slug, name ->
                        navController.navigate(
                            Screen.RecipeList.createRoute(title = name, categorySlug = slug)
                        )
                    }
                )
            }

            is SearchState.Searching -> {
                // Squelette plutôt qu'un spinner : la page annonce sa forme
                // pendant le chargement (`apple/180/SearchView.swift:147-151`).
                SkeletonRecipeList(modifier = Modifier.fillMaxSize())
            }

            is SearchState.Results -> {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        // Miroir du compteur iOS (SearchView.swift:157).
                        text     = "${s.recipes.size} recette${if (s.recipes.size > 1) "s" else ""} " +
                            "trouvée${if (s.recipes.size > 1) "s" else ""}",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        // `apple/180/SearchView.swift:180-183`
                        onRefresh    = {
                            ImageCacheManager.clear()
                            vm.refresh()
                        },
                        modifier     = Modifier.fillMaxSize()
                    ) {
                        // Les deux branches de l'iOS — `List` compacte et
                        // `ScrollView` sur écran large — rendent la **même**
                        // carte `.row` (`SearchView.swift:176-197`) : les
                        // résultats de recherche ne sont jamais une grille.
                        LazyColumn(
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            items(items = s.recipes, key = { it.id }) { recipe ->
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
                        }
                    }
                }
            }

            is SearchState.Empty -> {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector        = Icons.Filled.SearchOff,
                            contentDescription = null,
                            modifier           = Modifier.size(64.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text      = "Aucune recette trouvée",
                            style     = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text      = "Essayez avec d'autres mots-clés.",
                            style     = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Panne réseau — distincte de « aucun résultat ».
            is SearchState.Error -> {
                NetworkErrorState(message = s.message, onRetry = { vm.refresh() })
            }
        }
    }
    }
}

// ── Rangée de chips de filtre (saison + type, multi-sélection) ─────────────────

@Composable
private fun IdleContent(
    seasons: List<Term>,
    dishTypes: List<Term>,
    history: List<String>,
    onHistoryTap: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onSeasonTap: (String, String) -> Unit,
    onTypeTap: (String, String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {

        // Historique
        if (history.isNotEmpty()) {
            item {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text("Recherches récentes", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClearHistory) { Text("Effacer") }
                }
            }
            items(history) { q ->
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .clickable { onHistoryTap(q) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = Icons.Filled.History,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(20.dp)
                    )
                    Text(
                        text     = q,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        style    = MaterialTheme.typography.bodyLarge
                    )
                    IconButton(onClick = { onRemoveItem(q) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Supprimer",
                            modifier = Modifier.size(16.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
        // Sans historique, l'iOS n'affiche **rien** avant « Explorer par… »
        // (`SearchView.swift:59-96`) : la suggestion « Essayez "tomate"… » était
        // une invention Android, retirée ici.

        // Explorer par saison — **liste** de lignes, pas des chips : l'iOS
        // navigue vers `RecipeListView` depuis une ligne à icône accent et
        // chevron (`apple/180/SearchView.swift:97-118`). Une chip dit « filtre
        // à cocher » ; ici le tap ouvre un écran.
        item { SectionHeader("Explorer par saison") }
        items(seasons, key = { "season-${it.slug}" }) { season ->
            TaxonomyRow(
                icon    = resolveSeasonIcon(season.slug),
                label   = season.name,
                onClick = { onSeasonTap(season.slug, season.name) }
            )
        }

        // Explorer par type
        item { SectionHeader("Explorer par type") }
        items(dishTypes, key = { "dish-${it.slug}" }) { type ->
            TaxonomyRow(
                icon    = resolveDishIcon(type.slug),
                label   = type.name,
                onClick = { onTypeTap(type.slug, type.name) }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * Ligne « Explorer par… » — icône accent en colonne de 36 dp, libellé, chevron.
 *
 * Miroir des lignes de `SearchView.swift:98-118` / `:126-143`.
 */
@Composable
private fun TaxonomyRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = Dimens.taxonomyRowVerticalPadding)
            .heightIn(min = Dimens.minTouchTarget),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.taxonomyRowSpacing)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.width(Dimens.taxonomyRowIconColumn)
        )
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(Dimens.seeAllChevronSize)
        )
    }
}
