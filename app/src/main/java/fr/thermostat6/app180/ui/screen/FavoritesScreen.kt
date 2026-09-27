package fr.thermostat6.app180.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.data.analytics.UmamiScreens
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.image.ImageCacheManager
import fr.thermostat6.app180.navigation.Screen
import fr.thermostat6.app180.ui.theme.ScreenTitle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import fr.thermostat6.app180.ui.layout.LocalWindowWidthClass
import fr.thermostat6.app180.ui.layout.isRegularWidth
import fr.thermostat6.app180.ui.components.RecipeRowCard
import fr.thermostat6.app180.ui.components.IconPastille
import fr.thermostat6.app180.ui.components.SearchField
import fr.thermostat6.app180.ui.components.RecipeGridCard
import fr.thermostat6.app180.ui.components.SkeletonRecipeList
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.IconButton
import fr.thermostat6.app180.data.model.RecipeTaxonomy
import fr.thermostat6.app180.ui.components.NetworkErrorState
import fr.thermostat6.app180.ui.components.RecipeFilterOption
import fr.thermostat6.app180.ui.components.RecipeFilterSelection
import fr.thermostat6.app180.ui.components.RecipeFilterSheet
import fr.thermostat6.app180.ui.components.RecipeSortOption
import fr.thermostat6.app180.ui.viewmodel.FavoritesFilter
import fr.thermostat6.app180.ui.viewmodel.FavoritesSortOrder
import fr.thermostat6.app180.ui.viewmodel.FavoritesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(navController: NavController) {
    val vm         = viewModel<FavoritesViewModel>()
    val state      by vm.uiState.collectAsStateWithLifecycle()
    val favIds     by vm.favoriteIds.collectAsStateWithLifecycle()
    val isLoggedIn by AuthService.isLoggedIn.collectAsStateWithLifecycle()
    // Grille sur écran large, liste sur téléphone — la bascule de l'iOS
    // (`apple/180/FavoritesView.swift:175-191`).
    val isRegular  = LocalWindowWidthClass.current.isRegularWidth

    var showLogin       by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Page vue Umami (`apple/180/FavoritesView.swift:125`).
    LaunchedEffect(Unit) {
        UmamiTracker.trackScreen(UmamiScreens.FAVORITES_PATH, UmamiScreens.FAVORITES_TITLE)
    }

    val visible = state.visibleRecipes

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text  = "Mon carnet de recettes",
                style = ScreenTitle
            )
            // Bouton filtres : n'apparaît que s'il y a des favoris à filtrer
            // (miroir FavoritesView.swift:81-87).
            if (isLoggedIn && state.recipes.isNotEmpty()) {
                IconPastille(
                    icon               = Icons.Filled.FilterList,
                    contentDescription = "Filtres",
                    onClick            = { showFilterSheet = true },
                    showDot            = state.hasActiveFilters
                )
            }
        }

        // Barre de filtre locale — champ mot-clé, miroir de `RecipeFilterBar`
        // (apple/180/RecipeFilterBar.swift:19-42). N'apparaît que s'il y a des
        // favoris à filtrer.
        if (isLoggedIn && state.recipes.isNotEmpty()) {
            SearchField(
                value         = state.keyword,
                onValueChange = { vm.onKeywordChange(it) },
                placeholder   = "Chercher dans le carnet…",
                // Filtrage local au fil de la frappe : pas de soumission.
                modifier      = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
        }

        when {
            // Visiteur : le carnet est synchronisé au compte, il n'y a rien à
            // afficher hors session. On explique et on propose la connexion
            // plutôt qu'un « Aucun favori » qui laisserait croire à un bug
            // (miroir apple/180/FavoritesView.swift:112-133).
            !isLoggedIn -> FavoritesVisitorState(onLogin = { showLogin = true })

            state.error != null && state.recipes.isEmpty() -> {
                NetworkErrorState(message = state.error!!, onRetry = { vm.refresh() })
            }

            state.isLoading && state.recipes.isEmpty() -> {
                // Chargement **initial** seulement : un rafraîchissement garde
                // son indicateur, porté par le `PullToRefreshBox` plus bas
                // (`apple/180/FavoritesView.swift:74-78`).
                SkeletonRecipeList(modifier = Modifier.fillMaxSize())
            }

            visible.isEmpty() -> {
                // État vide
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector        = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            modifier           = Modifier.size(72.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text      = if (state.hasAnyFilter) "Aucune recette"
                                        else "Aucun favori pour l'instant",
                            style     = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text      = if (state.hasAnyFilter) "Aucun favori ne correspond à ces filtres."
                                        else "Appuyez sur le cœur d'une recette pour la retrouver ici.",
                            style     = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                // Grille 2 colonnes + pull-to-refresh. Le retrait se fait via le
                // bouton cœur inline de chaque carte (animation de rebond).
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    // `apple/180/FavoritesView.swift:171-174`
                    onRefresh    = {
                        ImageCacheManager.clear()
                        vm.refresh()
                    },
                    modifier     = Modifier.fillMaxSize()
                ) {
                    // Téléphone : **liste** de lignes séparées, comme l'iOS
                    // (`FavoritesView.swift:184-191`). La grille 2 colonnes
                    // absorbée au LOT-05 est révoquée — elle ne subsiste que
                    // sur écran large, où l'iOS bascule lui aussi en grille
                    // (`FavoritesView.swift:175-183`).
                    if (isRegular) {
                        LazyVerticalGrid(
                            columns               = GridCells.Fixed(2),
                            modifier              = Modifier.fillMaxSize(),
                            contentPadding        = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement   = Arrangement.spacedBy(14.dp)
                        ) {
                            items(items = visible, key = { it.id }) { recipe ->
                                RecipeGridCard(
                                    recipe          = recipe,
                                    isFavorite      = recipe.id in favIds,
                                    onClick         = {
                                        navController.navigate(Screen.RecipeDetail.createRoute(recipe.id))
                                    },
                                    onFavoriteClick = { vm.toggleFavorite(recipe.id) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier       = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            items(items = visible, key = { it.id }) { recipe ->
                                RecipeRowCard(
                                    recipe          = recipe,
                                    isFavorite      = recipe.id in favIds,
                                    onClick         = {
                                        navController.navigate(Screen.RecipeDetail.createRoute(recipe.id))
                                    },
                                    onFavoriteClick = { vm.toggleFavorite(recipe.id) },
                                    modifier        = Modifier.padding(horizontal = 16.dp)
                                )
                                HorizontalDivider(Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogin) {
        LoginScreen(onDismiss = { showLogin = false })
    }

    // Feuille de filtres/tri — mêmes composant et UX que « Toutes les recettes »
    // (miroir FavoritesView.swift:90-103).
    if (showFilterSheet) {
        RecipeFilterSheet(
            sortOptions   = FavoritesSortOrder.entries.map { RecipeSortOption(it.id, it.label) },
            seasons       = FavoritesFilter.presentTerms(state.recipes, RecipeTaxonomy.SEASON)
                .map { (slug, name) -> RecipeFilterOption(slug, name, resolveSeasonIcon(slug)) },
            categories    = FavoritesFilter.presentTerms(state.recipes, RecipeTaxonomy.CATEGORY)
                .map { (slug, name) -> RecipeFilterOption(slug, name, resolveDishIcon(slug)) },
            selection     = RecipeFilterSelection(
                sortId       = state.sortOrder.id,
                seasonSlug   = state.seasonSlug,
                categorySlug = state.categorySlug
            ),
            defaultSortId = FavoritesSortOrder.DEFAULT.id,
            onApply       = { applied ->
                vm.applyFilters(
                    sortOrder    = FavoritesSortOrder.fromId(applied.sortId),
                    seasonSlug   = applied.seasonSlug,
                    categorySlug = applied.categorySlug
                )
                showFilterSheet = false
            },
            onDismiss     = { showFilterSheet = false }
        )
    }
}

/**
 * État visiteur du carnet : hors session il n'y a rien à afficher, puisque le
 * carnet est synchronisé au compte. On explique et on propose la connexion
 * plutôt qu'un « Aucun favori » qui laisserait croire à un bug
 * (miroir `apple/180/FavoritesView.swift:112-133`).
 *
 * Extrait de [FavoritesScreen] pour être montable seul : l'écran complet exige
 * un `NavController` et un ViewModel qui appelle le réseau, là où cet état,
 * lui, ne dépend d'aucune donnée.
 */
@Composable
internal fun FavoritesVisitorState(onLogin: () -> Unit) {
    Box(
        modifier         = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                modifier           = Modifier.size(72.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text      = "Votre carnet vous attend",
                style     = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Connectez-vous pour enregistrer vos recettes " +
                    "et les retrouver sur tous vos appareils.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onLogin) { Text("Se connecter") }
        }
    }
}
