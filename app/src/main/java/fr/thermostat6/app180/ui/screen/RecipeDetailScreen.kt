package fr.thermostat6.app180.ui.screen

import android.content.Intent
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.runtime.DisposableEffect
import fr.thermostat6.app180.data.analytics.UmamiEvents
import fr.thermostat6.app180.data.analytics.UmamiParams
import fr.thermostat6.app180.data.analytics.UmamiScreens
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.network.ApiError
import fr.thermostat6.app180.data.push.PushPermissionCoordinator
import fr.thermostat6.app180.data.analytics.AnalyticsService
import androidx.compose.runtime.LaunchedEffect
import fr.thermostat6.app180.data.favorites.FavoritesManager
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.navigation.Screen
import fr.thermostat6.app180.ui.components.FavoriteGate
import fr.thermostat6.app180.ui.components.FavoriteHeart
import fr.thermostat6.app180.ui.components.PastilleStyle
import fr.thermostat6.app180.ui.components.IconPastille
import fr.thermostat6.app180.ui.components.CachedAsyncImage
import fr.thermostat6.app180.ui.theme.RecipeStepTitle
import fr.thermostat6.app180.ui.theme.RecipeBody
import fr.thermostat6.app180.ui.theme.RecipeGroupLabel
import fr.thermostat6.app180.ui.theme.RecipeIntro
import fr.thermostat6.app180.ui.theme.Dimens
import fr.thermostat6.app180.ui.theme.ImageRenderSize
import fr.thermostat6.app180.ui.components.RecipeCard
import fr.thermostat6.app180.ui.viewmodel.RecipeDetailState
import fr.thermostat6.app180.ui.viewmodel.RecipeDetailViewModel
import fr.thermostat6.app180.util.accent180
import fr.thermostat6.app180.util.stripHtml
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// ── Écran principal ───────────────────────────────────────────────────────────

/**
 * @param onClose fourni uniquement quand l'écran est présenté en feuille modale
 *   (tirage au sort). Le bouton retour ferme alors la feuille au lieu de
 *   dépiler la navigation de l'onglet, qui n'a pas bougé.
 */
@Composable
fun RecipeDetailScreen(
    recipeId: Int,
    navController: NavController,
    onClose: (() -> Unit)? = null
) {
    val dismiss: () -> Unit = onClose ?: { navController.popBackStack() }

    val vm    = viewModel<RecipeDetailViewModel>(factory = RecipeDetailViewModel.factory(recipeId))
    val state by vm.state.collectAsStateWithLifecycle()
    val favIds by FavoritesManager.favoriteIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope  = rememberCoroutineScope()

    // Un soft-ask ne doit jamais interrompre une lecture de recette
    // (miroir `isRecipeReadingInProgress`, PushPermissionCoordinator.swift:41-43).
    DisposableEffect(Unit) {
        PushPermissionCoordinator.isRecipeReadingInProgress = true
        onDispose { PushPermissionCoordinator.isRecipeReadingInProgress = false }
    }

    when (val s = state) {
        is RecipeDetailState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is RecipeDetailState.Error -> {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Icône, titre et message dérivés du **type** d'erreur : une
                    // coupure réseau ne doit jamais se présenter comme une
                    // recette dépubliée. Miroir de `TypedErrorView` iOS.
                    Icon(
                        imageVector        = when (s.error) {
                            ApiError.Offline -> Icons.Filled.WifiOff
                            else             -> Icons.Filled.ErrorOutline
                        },
                        contentDescription = null,
                        modifier           = Modifier.size(56.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text      = when (s.error) {
                            ApiError.Offline -> "Vous êtes hors ligne"
                            ApiError.Timeout -> "Connexion trop lente"
                            else             -> "Recette indisponible"
                        },
                        style     = MaterialTheme.typography.titleLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text      = when (s.error) {
                            // Hors ligne sur une fiche : le manque est précis —
                            // celle-ci n'a pas été téléchargée — et le dire
                            // oriente vers le carnet plutôt que vers un réessai
                            // voué à échouer.
                            ApiError.Offline ->
                                "Cette recette n'a pas été téléchargée pour la consultation hors ligne."
                            else -> s.error.userMessage
                        },
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { vm.loadRecipe() }) { Text("Réessayer") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = dismiss,
                        colors  = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground
            )
                    ) { Text("Retour") }
                }
            }
        }
        is RecipeDetailState.Loaded -> {
            RecipeDetailContent(
                recipe          = s.recipe,
                recommendations = s.recommendations,
                isFavorite      = s.recipe.id in favIds,
                onFavorite      = {
                    scope.launch { FavoritesManager.toggle(s.recipe.id, slug = s.recipe.slug) }
                },
                onBack          = dismiss,
                // Depuis la feuille modale, une recommandation ferme d'abord la
                // feuille : l'iOS pousse dans la pile de la feuille, mais lui
                // dispose d'une `NavigationStack` dédiée par présentation.
                onRecoClick     = { id ->
                    onClose?.invoke()
                    navController.navigate(Screen.RecipeDetail.createRoute(id))
                },
                onRecoFav       = { id ->
                    val slug = s.recommendations.firstOrNull { it.id == id }?.slug
                    scope.launch { FavoritesManager.toggle(id, slug = slug) }
                }
            )
        }
    }
}

// ── Contenu principal ─────────────────────────────────────────────────────────

@Composable
private fun RecipeDetailContent(
    recipe: Recipe,
    recommendations: List<Recipe>,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onBack: () -> Unit,
    onRecoClick: (Int) -> Unit,
    onRecoFav: (Int) -> Unit
) {
    val favIds by FavoritesManager.favoriteIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val isLoggedIn  by fr.thermostat6.app180.data.auth.AuthService.isLoggedIn.collectAsStateWithLifecycle()

    // Miroir RecipeDetailView.swift:38-42 : vue de recette, et vue de paywall
    // si le contenu est verrouillé.
    LaunchedEffect(recipe.id) {
        AnalyticsService.viewRecipe(
            id        = recipe.id,
            title     = recipe.cleanTitle,
            isPremium = recipe.isPremium
        )
        if (recipe.isLocked) {
            AnalyticsService.paywallView(id = recipe.id, title = recipe.cleanTitle)
        }
        // Page vue Umami : url calquée sur le site (`/recette/{slug}`), repli sur
        // l'id quand le slug manque (`RecipeDetailView.swift:92-97`). Émise ici
        // et non à l'entrée de l'écran : le slug et le titre n'existent qu'une
        // fois la fiche chargée.
        UmamiTracker.trackScreen(
            path  = UmamiScreens.recipePath(recipe.slug, recipe.id),
            title = recipe.cleanTitle
        )
    }

    // Deux mises en page, miroir du `horizontalSizeClass` iOS
    // (`RecipeDetailView.swift:31-36`) : défilement vertical sur téléphone,
    // colonne image + colonne texte défilante sur écran large.
    //
    // ── Écart volontaire, sans miroir iOS ────────────────────────────────────
    // La décision se prend sur la largeur **réellement disponible**, mesurée
    // ici, et non sur la classe de taille de la fenêtre. Raison : sur iPad, le
    // `NavigationSplitView` **est** la barre latérale — le détail occupe tout ce
    // qui reste, et une colonne image de 400 pt y tient toujours. Android, lui,
    // empile un tiroir permanent **et** le contenu : sur un pliable de 852 dp,
    // le volet de contenu tombe à ~480 dp. Une largeur d'image figée à 400 dp
    // n'y laissait qu'une soixantaine de dp au texte, et le titre se cassait
    // caractère par caractère à la verticale.
    //
    // La colonne image est donc une **fraction plafonnée** de l'espace réel, et
    // la fiche repasse en une seule colonne dès que l'image descendrait sous
    // [Dimens.detailImageColumnMinWidth] ou le texte sous
    // [Dimens.detailTextColumnMinWidth] : mieux vaut une colonne lisible que
    // deux à l'étroit.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val imageColumn = (maxWidth * Dimens.detailImageColumnFraction)
            .coerceAtMost(Dimens.detailImageColumnMaxWidth)
        val twoColumns = imageColumn >= Dimens.detailImageColumnMinWidth &&
            maxWidth - imageColumn >= Dimens.detailTextColumnMinWidth

        if (twoColumns) {
            // `HStack(alignment: .top, spacing: 0)` (`RecipeDetailView.swift:85`).
            Row(Modifier.fillMaxSize()) {
                HeroPanel(
                    recipe     = recipe,
                    isFavorite = isFavorite,
                    isLoggedIn = isLoggedIn,
                    onBack     = onBack,
                    onFavorite = onFavorite,
                    // 800 px de décodage sur grand écran (`RecipeDetailView.swift:116`).
                    renderSize = ImageRenderSize.HERO_LARGE,
                    modifier   = Modifier
                        .width(imageColumn)
                        .fillMaxHeight()
                        .heightIn(min = Dimens.detailHeroHeightLarge)
                )
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    ContentPanel(
                        recipe            = recipe,
                        recommendations   = recommendations,
                        favIds            = favIds,
                        isLoggedIn        = isLoggedIn,
                        onRecoClick       = onRecoClick,
                        onRecoFav         = onRecoFav,
                        horizontalPadding = DETAIL_PADDING_REGULAR
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                HeroPanel(
                    recipe     = recipe,
                    isFavorite = isFavorite,
                    isLoggedIn = isLoggedIn,
                    onBack     = onBack,
                    onFavorite = onFavorite,
                    renderSize = ImageRenderSize.HERO,
                    modifier   = Modifier.fillMaxWidth().height(Dimens.featuredImageHeight)
                )
                ContentPanel(
                    recipe            = recipe,
                    recommendations   = recommendations,
                    favIds            = favIds,
                    isLoggedIn        = isLoggedIn,
                    onRecoClick       = onRecoClick,
                    onRecoFav         = onRecoFav,
                    horizontalPadding = DETAIL_PADDING_COMPACT
                )
            }
        }
    }
}

/** Marge du bloc texte sur téléphone. */
private val DETAIL_PADDING_COMPACT = 16.dp

/** Marge du bloc texte sur écran large (`RecipeDetailView.swift:95`). */
private val DETAIL_PADDING_REGULAR = 28.dp

// ── Panneau image (partagé téléphone / écran large) ───────────────────────────

/**
 * Miroir d'`imagePanel` (`RecipeDetailView.swift:113-140`).
 *
 * L'iOS n'obscurcit pas l'image : la lisibilité des boutons vient de leur propre
 * pastille translucide, pas d'un gradient qui ternirait toute la photo.
 */
@Composable
private fun HeroPanel(
    recipe: Recipe,
    isFavorite: Boolean,
    isLoggedIn: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    renderSize: ImageRenderSize,
    modifier: Modifier = Modifier
) {
    val context  = LocalContext.current
    val shareUrl = fr.thermostat6.app180.data.network.ApiConfig.shareUrl(recipe.id)

    Box(modifier) {
        CachedAsyncImage(
            url                = recipe.imageURL,
            // Visuel décoratif : le titre est lu séparément
            // (`RecipeDetailView.swift:122`).
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop,
            renderSize         = renderSize
        )
        // Icônes d'action sur pastille, comme la barre de navigation iOS posée
        // au-dessus du visuel (`RecipeDetailView.swift:99-113`).
        IconPastille(
            icon               = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Retour",
            onClick            = onBack,
            style              = PastilleStyle.OnImage,
            modifier           = Modifier.align(Alignment.TopStart).padding(Dimens.heroIconMargin)
        )
        IconPastille(
            icon               = Icons.Filled.Share,
            contentDescription = "Partager",
            // Seule icône accent de la fiche : c'est l'action mise en avant
            // par l'iOS (`ShareLink` teinté par `.tint(.accent180)`).
            tint               = MaterialTheme.colorScheme.primary,
            onClick            = {
                AnalyticsService.shareRecipe(id = recipe.id, title = recipe.cleanTitle)
                // `createChooser` n'expose pas la destination réellement
                // choisie : l'event est émis à l'OUVERTURE de la feuille, et
                // `channel` vaut donc `share_sheet` — même valeur que l'iOS,
                // qui a la même limite (`RecipeDetailView.swift:108-112`).
                UmamiTracker.trackEvent(
                    UmamiEvents.RECIPE_SHARE,
                    mapOf(UmamiParams.CHANNEL to UmamiParams.CHANNEL_SHARE_SHEET)
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                }
                context.startActivity(Intent.createChooser(intent, "Partager"))
            },
            style    = PastilleStyle.OnImage,
            modifier = Modifier.align(Alignment.TopEnd).padding(Dimens.heroIconMargin)
        )
        // Bouton favori (bas-droite). Aucune animation de rebond : l'iOS n'en a
        // pas (`RecipeDetailView.swift:124-135`), et un effet propre à une seule
        // plateforme fait diverger le langage visuel des deux apps.
        // Hors session le carnet n'a pas de destinataire : bouton désactivé et
        // estompé, et un résidu local n'affiche pas un cœur plein
        // (miroir RecipeCards.swift:191-200 et RecipeDetailView.swift:103-108).
        FavoriteHeart(
            isFavorite = isFavorite,
            onClick    = onFavorite,
            gate       = FavoriteGate(isLoggedIn),
            onImage    = true,
            large      = true,
            modifier   = Modifier.align(Alignment.BottomEnd).padding(Dimens.heroIconMargin)
        )
    }
}

// ── Panneau contenu (partagé téléphone / écran large) ─────────────────────────

/** Miroir de `contentPanel` + `recommendationsSection` (`RecipeDetailView.swift:145`). */
@Composable
private fun ContentPanel(
    recipe: Recipe,
    recommendations: List<Recipe>,
    favIds: Set<Int>,
    isLoggedIn: Boolean,
    onRecoClick: (Int) -> Unit,
    onRecoFav: (Int) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    Column {
        // ── Titre et méta ─────────────────────────────────────────────────────
        Column(Modifier.padding(horizontal = horizontalPadding, vertical = 16.dp)) {
            Text(
                text  = recipe.title.rendered.stripHtml(),
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = formatDate(recipe.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(Modifier.padding(horizontal = horizontalPadding))
        Spacer(Modifier.height(16.dp))

        // ── Contenu selon auth ────────────────────────────────────────────────
        // Source de vérité = `recipe_locked`, calculé par le serveur au moment du
        // fetch en fonction du jeton envoyé (miroir RecipeDetailView.swift:152-157).
        if (recipe.isLocked) {
            PaywallContent(recipe = recipe, isLoggedIn = isLoggedIn, horizontalPadding = horizontalPadding)
        } else {
            RecipeFullContent(recipe = recipe, horizontalPadding = horizontalPadding)
        }

        // ── Recommandations ───────────────────────────────────────────────────
        if (recommendations.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(Modifier.padding(horizontal = horizontalPadding))
            Text(
                text     = "Vous aimerez aussi",
                style    = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp)
            )
            LazyRow(
                contentPadding        = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                items(items = recommendations, key = { it.id }) { reco ->
                    RecipeCard(
                        recipe          = reco,
                        isFavorite      = reco.id in favIds,
                        onClick         = { onRecoClick(reco.id) },
                        onFavoriteClick = { onRecoFav(reco.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Contenu complet (abonné) ──────────────────────────────────────────────────

@Composable
private fun RecipeFullContent(
    recipe: Recipe,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    Column(Modifier.padding(horizontal = horizontalPadding)) {

        // Introduction en italique
        if (recipe.introText.isNotBlank()) {
            Text(
                text  = recipe.introText,
                style = RecipeIntro
            )
            Spacer(Modifier.height(16.dp))
        }

        // Portions
        recipe.servingsText?.let { servings ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.People,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = servings,
                    // `.subheadline` + `.medium` (`RecipeDetailView.swift:236-238`).
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // Ingrédients, par groupes (le libellé de groupe est optionnel côté ACF)
        if (recipe.ingredientGroups.isNotEmpty()) {
            Text("Ingrédients", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            recipe.ingredientGroups.forEach { group ->
                if (group.label.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = group.label,
                        style = RecipeGroupLabel
                    )
                }
                group.lines.forEach { line ->
                    // Puce = un vrai disque accent, comme l'iOS
                    // (`RecipeDetailView.swift:253-256`) : un « • » typographique
                    // change de taille et de calage avec la police.
                    Row(
                        modifier              = Modifier.padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .size(Dimens.bulletSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(line, style = RecipeBody)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Étapes numérotées
        if (recipe.preparationSteps.isNotEmpty()) {
            Text("Préparation", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            // Pastille numérotée puis titre, sans carte ni fond : c'est la forme
            // de l'iOS (`RecipeDetailView.swift:226-247`). La carte grise était
            // une invention Android — le numéro doit se lire d'un coup d'œil
            // pendant qu'on cuisine, pas se fondre dans un aplat.
            recipe.preparationSteps.forEachIndexed { index, step ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Surface(
                            shape    = CircleShape,
                            color    = Color.accent180,
                            modifier = Modifier.size(Dimens.stepBadgeSize)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text       = "${index + 1}",
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color.White
                                )
                            }
                        }
                        if (step.cleanTitle.isNotBlank()) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text     = step.cleanTitle,
                                style    = RecipeStepTitle,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                    if (step.cleanContent.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(step.cleanContent, style = RecipeBody)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

    }
}

// ── Paywall ───────────────────────────────────────────────────────────────────

/**
 * Carte de verrouillage d'une fiche premium.
 *
 * `internal` et non `private` : c'est le seul écran dont la règle d'affichage
 * est un enjeu produit (aucun prix, aucun lien d'achat, CTA de connexion
 * réservé au visiteur), et le monter seul en test évite d'avoir à instancier
 * l'écran complet et son ViewModel réseau.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaywallContent(
    recipe: Recipe,
    isLoggedIn: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    var showLogin by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = horizontalPadding)) {

        // Aperçu éditorial (intro, ou extrait en repli) — pas un CTA.
        // Miroir RecipeDetailView.swift:264-267.
        val intro = recipe.introText.ifBlank { recipe.cleanExcerpt }
        if (intro.isNotBlank()) {
            // Aperçu éditorial en couleur principale : l'iOS ne l'atténue pas
            // (`RecipeDetailView.swift:314-316`, `.font(.body)` sans teinte).
            Text(
                text  = intro,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(20.dp))
        }

        // Carte de verrouillage recentrée sur la CONNEXION.
        // Aucun prix, aucun lien d'achat, aucune URL tappable
        // (miroir RecipeDetailView.swift:256-311).
        Surface(
            shape    = RoundedCornerShape(Dimens.detailCardRadius),
            color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector        = Icons.Filled.Lock,
                    contentDescription = null,
                    tint               = Color.accent180,
                    modifier           = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text       = "Contenu réservé aux abonnés",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text      = "Connectez-vous pour accéder à cette recette en intégralité.",
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (!isLoggedIn) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick  = { showLogin = true },
                        shape    = RoundedCornerShape(Dimens.detailButtonRadius),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Se connecter", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Ligne secondaire discrète, volontairement NON cliquable :
                // « 180c.fr » reste du texte inerte, sans lien ni prix.
                // Wording produit exact (RecipeDetailView.swift:301).
                Spacer(Modifier.height(16.dp))
                Text(
                    text      = "Déjà abonné·e à 180°C ? Connectez-vous. " +
                        "L'abonnement est disponible sur notre site 180c.fr.",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    if (showLogin) {
        LoginScreen(onDismiss = { showLogin = false })
    }
}

// ── Utilitaire de date ────────────────────────────────────────────────────────

private fun formatDate(isoDate: String): String = try {
    val parser    = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val formatter = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
    formatter.format(parser.parse(isoDate)!!)
} catch (_: Exception) {
    isoDate
}
