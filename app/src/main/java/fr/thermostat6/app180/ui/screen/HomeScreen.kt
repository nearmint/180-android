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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import fr.thermostat6.app180.data.analytics.UmamiScreens
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.image.ImageCacheManager
import fr.thermostat6.app180.ui.components.SectionHeader
import fr.thermostat6.app180.ui.components.IconPastille
import fr.thermostat6.app180.ui.components.AppLogo
import fr.thermostat6.app180.data.model.RecipeTaxonomy
import fr.thermostat6.app180.data.taxonomy.PublicationSlug
import fr.thermostat6.app180.data.taxonomy.TaxonomyStore
import fr.thermostat6.app180.data.favorites.FavoritesManager
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.navigation.Screen
import fr.thermostat6.app180.navigation.TabRouter
import fr.thermostat6.app180.ui.components.FeaturedRecipeCard
import fr.thermostat6.app180.ui.components.RecipeCard
import fr.thermostat6.app180.ui.components.RecipeRowCard
import fr.thermostat6.app180.ui.components.RecipeSlider
import fr.thermostat6.app180.ui.components.SkeletonFeaturedCard
import fr.thermostat6.app180.ui.components.SkeletonRecipeRail
import fr.thermostat6.app180.ui.components.TypedErrorState
import fr.thermostat6.app180.ui.components.WithOfflineBanner
import fr.thermostat6.app180.ui.theme.Dimens
import fr.thermostat6.app180.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import fr.thermostat6.app180.data.model.HomeBlock
import fr.thermostat6.app180.data.model.HomeBlockType
import fr.thermostat6.app180.data.model.HomeBlockVariant
import fr.thermostat6.app180.data.model.HomeTile
import fr.thermostat6.app180.ui.components.RecipeGridCard
import fr.thermostat6.app180.data.push.NotificationCenter
import fr.thermostat6.app180.data.push.PushState
import fr.thermostat6.app180.ui.web.WebLauncher
import fr.thermostat6.app180.util.accent180

// ── Mapping slug de terme → ImageVector ──────────────────────────────────────
// Les termes sont chargés dynamiquement : l'icône est choisie sur le **slug**,
// stable, avec un repli générique pour tout terme ajouté en back-office.
// Miroir iOS : TaxonomyStore.swift → enum TaxonomyIcon.

internal fun resolveSeasonIcon(slug: String): ImageVector = when (slug) {
    "printemps" -> Icons.Filled.LocalFlorist
    "ete"       -> Icons.Filled.WbSunny
    "automne"   -> Icons.Filled.Park
    "hiver"     -> Icons.Filled.AcUnit
    else        -> Icons.Filled.Eco
}

internal fun resolveDishIcon(slug: String): ImageVector = when (slug) {
    "entree"          -> Icons.Filled.RestaurantMenu
    "plat"            -> Icons.Filled.DinnerDining
    "dessert"         -> Icons.Filled.Cake
    "accompagnement"  -> Icons.Filled.RiceBowl
    "apero"           -> Icons.Filled.LocalBar
    "boisson"         -> Icons.Filled.LocalCafe
    "petit-dejeuner"  -> Icons.Filled.FreeBreakfast
    else              -> Icons.Filled.Restaurant
}

// ── Écran principal ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val vm      = viewModel<HomeViewModel>()
    val state   by vm.uiState.collectAsStateWithLifecycle()
    val favIds  by FavoritesManager.favoriteIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    val scope   = rememberCoroutineScope()

    // Page vue Umami, une fois par apparition de l'écran — l'onglet est démonté
    // quand on le quitte, revenir dessus recompte une vue, comme le `.onAppear`
    // d'un `TabView` iOS (`apple/180/HomeView.swift:98`).
    LaunchedEffect(Unit) {
        UmamiTracker.trackScreen(UmamiScreens.HOME_PATH, UmamiScreens.HOME_TITLE)
    }

    // Slug par id, pour l'event Umami `recipe_favorite` : les rappels de carte
    // ne transportent qu'un identifiant, le slug se relit dans l'état de l'écran.
    val slugById = remember(state) {
        (state.hydratedByBlock.values.flatten() + state.carnetRecipes + state.recentRecipes)
            .mapNotNull { recipe -> recipe.slug?.let { recipe.id to it } }
            .toMap()
    }

    val onFav: (Int) -> Unit    = { id ->
        scope.launch { FavoritesManager.toggle(id, slug = slugById[id]) }
    }
    val onRecipe: (Int) -> Unit = { id -> navController.navigate(Screen.RecipeDetail.createRoute(id)) }

    val openAllRecipes = {
        navController.navigate(
            Screen.RecipeList.createRoute(title = "Toutes les recettes", showFilters = true)
        )
    }

    // Bandeau au-dessus du tirer-pour-rafraîchir : il doit rester visible
    // pendant le geste, et ne pas se faire recouvrir par l'indicateur.
    WithOfflineBanner(modifier = Modifier.fillMaxSize()) {
    // En-tête **épinglé** hors du contenu défilable, comme le `safeAreaInset` de
    // l'iOS (`HomeView.swift:70-76`) : il ne partage plus la surface de scroll ni
    // le hit-testing avec la carte à la une, et reste visible en défilant.
    Column(Modifier.fillMaxSize()) {
    HomeHeader(onNotificationsClick = { navController.navigate(Screen.Notifications.route) })
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        // Vide les visuels avant de recharger : un tirer-pour-rafraîchir doit
        // rapporter les photos du serveur, pas celles du cache
        // (`apple/180/HomeView.swift:61-63`).
        onRefresh    = {
            ImageCacheManager.clear()
            vm.refreshAll()
        },
        modifier     = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {

            if (state.isLoading && state.blocks.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) { SkeletonFeaturedCard() }
                    SkeletonRecipeRail()
                }
            } else if (state.error != null && state.blocks.isEmpty()) {
                // Mur d'erreur **typé**, avec reprise. N'apparaît que si aucune
                // composition n'a jamais pu être décodée : un accueil périmé
                // déjà à l'écran n'est jamais remplacé par une erreur
                // (`HomeViewModel`, miroir `HomeView.swift:486-497`).
                item {
                    TypedErrorState(
                        error   = state.error!!,
                        onRetry = { vm.loadAll() }
                    )
                }
            } else {

                // Blocs rendus dans l'ordre exact envoyé par le serveur
                // (miroir HomeView.swift:39-41).
                itemsIndexed(state.blocks) { index, block ->
                    HomeBlockView(
                        block          = block,
                        recipes        = state.hydratedByBlock[index].orEmpty(),
                        carnetRecipes  = state.carnetRecipes,
                        recentRecipes  = state.recentRecipes,
                        favIds         = favIds,
                        onRecipe       = onRecipe,
                        onFav          = onFav,
                        onAllRecipes   = openAllRecipes,
                        // « Voir tout » du Carnet : **bascule d'onglet**, pas une
                        // navigation. `favorites` n'est pas une destination du
                        // graphe de l'onglet Accueil — y naviguer levait une
                        // IllegalArgumentException. Miroir de
                        // `HomeView.swift:358-377` (`TabRouter.shared.selected`).
                        //
                        // Comme sur iOS (`TabRouter.swift:10`, simple `@Published
                        // var selected`), la pile du Carnet est **conservée** :
                        // basculer depuis l'Accueil ne la remonte pas à sa racine.
                        onCarnet       = { TabRouter.select(TabRouter.Tab.FAVORITES) },
                        onTile         = { taxonomy, slug, title ->
                            navController.navigate(
                                Screen.RecipeList.createRoute(
                                    title           = title,
                                    categorySlug    = slug.takeIf { taxonomy == RecipeTaxonomy.CATEGORY },
                                    seasonSlug      = slug.takeIf { taxonomy == RecipeTaxonomy.SEASON },
                                    publicationSlug = slug.takeIf { taxonomy == RecipeTaxonomy.PUBLICATION }
                                )
                            )
                        },
                        // Lien public d'un rail → Custom Tab (remplace l'ouverture
                        // externe posée au lot dédié).
                        onWebLink      = { url -> WebLauncher.openPublic(context, url, toolbarColor) }
                    )
                }

            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }
    }
    }
}

// ── Rendu d'un bloc ───────────────────────────────────────────────────────────

/**
 * Rend un bloc serveur selon son type.
 *
 * Miroir de `blockView` (apple/180/HomeView.swift:208-280). Les types non gérés
 * — `search`, `cta_subscribe`, et tout type futur — sont ignorés silencieusement.
 * `cta_subscribe` est explicitement masqué côté iOS (« module abonnement toujours
 * masqué — demande produit », HomeView.swift:277-279) et le reste ici.
 *
 * L'accueil est **intégralement** piloté par le Home Builder : aucune section
 * n'est ajoutée en dur après les blocs, et une composition vide en back-office
 * donne un écran vide plutôt qu'un contenu de repli que personne n'a demandé
 * (`HomeView.swift:57-60`).
 */
@Composable
private fun HomeBlockView(
    block: HomeBlock,
    recipes: List<Recipe>,
    carnetRecipes: List<Recipe>,
    recentRecipes: List<Recipe>,
    favIds: Set<Int>,
    onRecipe: (Int) -> Unit,
    onFav: (Int) -> Unit,
    onAllRecipes: () -> Unit,
    onCarnet: () -> Unit,
    onTile: (taxonomy: String, slug: String, title: String) -> Unit,
    onWebLink: (String) -> Unit
) {
    when (block.type) {

        HomeBlockType.FEATURED -> {
            // L'iOS ne rend que la première recette du bloc (HomeView.swift:191).
            recipes.firstOrNull()?.let { recipe ->
                Spacer(Modifier.height(16.dp))
                Box(Modifier.padding(horizontal = 16.dp)) {
                    FeaturedRecipeCard(
                        recipe          = recipe,
                        isFavorite      = recipe.id in favIds,
                        onClick         = { onRecipe(recipe.id) },
                        onFavoriteClick = { onFav(recipe.id) }
                    )
                }
            }
        }

        HomeBlockType.RAIL -> {
            if (recipes.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.homeBlockSpacing))
                // « Voir tout » : le rail serveur `recent` ouvre l'écran natif
                // « Toutes les recettes » ; les autres suivent leur `view_all_url`
                // web (HomeView.swift:288-303).
                val onSeeAll: (() -> Unit)? = when {
                    block.source == "recent" -> onAllRecipes
                    block.viewAllUrl != null -> ({ onWebLink(block.viewAllUrl) })
                    else                     -> null
                }
                if (onSeeAll != null) {
                    SectionHeader(title = block.title, onSeeAll = onSeeAll)
                } else {
                    SectionHeader(block.title)
                }
                // `variant: "slider"` (module back-office « Slider de recettes ») :
                // mêmes données qu'un rail, présentation en carrousel de heros.
                // Tout autre variant — **y compris un futur variant inconnu** —
                // garde le rail dense (`apple/180/HomeView.swift:229-239`).
                if (block.variant == HomeBlockVariant.SLIDER) {
                    RecipeSlider(recipes = recipes, favIds = favIds, onRecipe = onRecipe, onFav = onFav)
                } else {
                    RecipeRail(recipes = recipes, favIds = favIds, onRecipe = onRecipe, onFav = onFav)
                }
            }
        }

        HomeBlockType.CATEGORY_TILES -> {
            val tiles = block.terms.orEmpty().filter { it.isValid }
            if (tiles.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.homeBlockSpacing))
                SectionHeader(block.title)
                CategoryTilesRow(
                    tiles    = tiles,
                    taxonomy = block.taxonomy,
                    onTile   = onTile
                )
            }
        }

        HomeBlockType.CARNET -> {
            // Réservé aux abonnés, et masqué si le carnet est vide : la liste est
            // vidée en amont par le ViewModel (HomeView.swift:222, :492-496).
            if (carnetRecipes.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.homeBlockSpacing))
                SectionHeader(
                    title    = block.title.ifBlank { "Mon carnet de recettes" },
                    onSeeAll = onCarnet
                )
                RecipeRail(recipes = carnetRecipes, favIds = favIds, onRecipe = onRecipe, onFav = onFav)
            }
        }

        HomeBlockType.GRID_PAGINATED -> {
            // Grille « Toutes les recettes » — le module qui occupait le bas de
            // l'accueil, désormais rendu **seulement** s'il est composé en
            // back-office (`apple/180/HomeView.swift:259-274`). Il consomme les
            // recettes récentes déjà chargées, pas des `recipe_ids`.
            if (recentRecipes.isNotEmpty()) {
                Spacer(Modifier.height(Dimens.homeBlockSpacing))
                SectionHeader(block.title.ifBlank { "Toutes les recettes" })
                AllRecipesGrid(
                    recipes  = recentRecipes,
                    favIds   = favIds,
                    onRecipe = onRecipe,
                    onFav    = onFav
                )
                Spacer(Modifier.height(16.dp))
                // L'app ne pagine pas dans l'accueil : la suite passe par
                // l'écran natif « Toutes les recettes », filtrable et triable.
                // Le bouton **appartient au module** — il disparaît avec lui
                // (`HomeView.swift:268-272`, `:303-319`).
                Button(
                    onClick  = onAllRecipes,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("Voir toutes les recettes")
                }
            }
        }

        else -> Unit
    }
}

/**
 * Grille à deux colonnes des recettes récentes — corps du module
 * `grid_paginated` (`apple/180/HomeView.swift:321-338`).
 *
 * Rendue en `Column` de `Row` et non en `LazyVerticalGrid` : le bloc est déjà
 * un élément d'une `LazyColumn`, qui refuse un enfant défilable de même axe.
 * La liste est bornée à 16 entrées, le rendu non paresseux ne coûte rien.
 */
@Composable
private fun AllRecipesGrid(
    recipes: List<Recipe>,
    favIds: Set<Int>,
    onRecipe: (Int) -> Unit,
    onFav: (Int) -> Unit
) {
    Column {
        recipes.chunked(2).forEach { row ->
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { recipe ->
                    Box(Modifier.weight(1f)) {
                        RecipeGridCard(
                            recipe          = recipe,
                            isFavorite      = recipe.id in favIds,
                            onClick         = { onRecipe(recipe.id) },
                            onFavoriteClick = { onFav(recipe.id) }
                        )
                    }
                }
                // Ligne impaire : la dernière carte ne s'étale pas sur toute la
                // largeur.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── Tuiles de taxonomie ───────────────────────────────────────────────────────

/**
 * Rangée de tuiles d'un bloc `category_tiles`.
 *
 * Les termes sont **déjà embarqués** dans le bloc (aucun fetch) ; seul l'ID du
 * terme est résolu depuis son slug pour construire la route de liste
 * (miroir `tilesRow` / `tileLink`, HomeView.swift:377-419).
 */
@Composable
private fun CategoryTilesRow(
    tiles: List<HomeTile>,
    taxonomy: String?,
    onTile: (taxonomy: String, slug: String, title: String) -> Unit
) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tiles, key = { it.slug }) { tile ->
            HomeTileItem(
                name    = tile.name,
                icon    = resolveTileIcon(tile.slug, taxonomy),
                // Navigation directe par slug : plus de résolution au clic,
                // donc plus de tap sans effet si le store n'est pas chargé.
                onClick = { taxonomy?.let { onTile(it, tile.slug, tile.name) } }
            )
        }
    }
}

/** Icône d'une tuile, choisie sur le slug (miroir `tileIcon`, HomeView.swift:421-427). */
private fun resolveTileIcon(slug: String, taxonomy: String?): ImageVector = when (taxonomy) {
    RecipeTaxonomy.SEASON   -> resolveSeasonIcon(slug)
    RecipeTaxonomy.CATEGORY -> resolveDishIcon(slug)
    else                    -> Icons.Filled.GridView
}

@Composable
private fun HomeTileItem(name: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        modifier            = Modifier.width(90.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier         = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.accent180.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = Color.accent180,
                modifier           = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text     = name,
            style    = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── En-tête ───────────────────────────────────────────────────────────────────

@Composable
private fun HomeHeader(onNotificationsClick: () -> Unit) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AppLogo(modifier = Modifier.height(Dimens.homeLogoHeight))
        // Pastille à **double déclencheur**, miroir iOS (HomeView.swift:132) :
        // au moins une notification non lue OU abonnement push non effectif —
        // même signalétique, source de vérité unique.
        val unreadCount     by NotificationCenter.unreadCount.collectAsStateWithLifecycle()
        val pushEffective   by PushState.isPushEffectivelyEnabled.collectAsStateWithLifecycle()
        val showBadge       = unreadCount > 0 || !pushEffective
        // Le point accent remplace le `Badge` Material et son compteur :
        // l'iOS n'affiche qu'une pastille de 10 pt, sans chiffre
        // (`HomeView.swift:157-160`).
        IconPastille(
            icon               = Icons.Filled.Notifications,
            contentDescription = "Notifications",
            onClick            = onNotificationsClick,
            showDot            = showBadge
        )
    }
}

// ── Rail horizontal de RecipeCard ─────────────────────────────────────────────

@Composable
private fun RecipeRail(
    recipes: List<Recipe>,
    favIds: Set<Int>,
    onRecipe: (Int) -> Unit,
    onFav: (Int) -> Unit
) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = recipes, key = { it.id }) { recipe ->
            RecipeCard(
                recipe          = recipe,
                isFavorite      = recipe.id in favIds,
                onClick         = { onRecipe(recipe.id) },
                onFavoriteClick = { onFav(recipe.id) }
            )
        }
    }
}

// ── Titres de section ─────────────────────────────────────────────────────────


