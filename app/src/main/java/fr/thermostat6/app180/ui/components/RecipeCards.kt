package fr.thermostat6.app180.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.offline.OfflineStore
import fr.thermostat6.app180.data.offline.OfflineSyncService
import fr.thermostat6.app180.ui.theme.Dimens
import fr.thermostat6.app180.ui.theme.ImageRenderSize
import fr.thermostat6.app180.ui.theme.Oswald
import fr.thermostat6.app180.ui.theme.PlayfairDisplay
import fr.thermostat6.app180.util.accent180

/*
 * Langage visuel des cartes recette — miroir d'`apple/180/RecipeCards.swift:8-132`.
 *
 * Structure commune, non négociable : **image en tête** à cadre fixe, puis SOUS
 * l'image le bloc texte — label type de plat · titre · saison.
 *
 * **Jamais de texte en overlay sur la photo** (contrainte éditoriale,
 * `RecipeCards.swift:12-13`) : ni titre incrusté, ni gradient d'obscurcissement.
 * Une photo culinaire se regarde ; y poser du texte oblige à l'assombrir, ce qui
 * abîme précisément ce qu'on cherche à montrer. Seul le bouton favori (icône)
 * reste en incrustation.
 */

// ── FeaturedRecipeCard ────────────────────────────────────────────────────────

/**
 * Carte « à la une » — pleine largeur, image 300 dp.
 * Le label saison y est volontairement masqué (`RecipeCards.swift:123-126`).
 */
@Composable
fun FeaturedRecipeCard(
    recipe: Recipe,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gate = rememberFavoriteGate()
    Column(
        modifier            = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardTextSpacing)
    ) {
        CardImage(
            recipe          = recipe,
            gate            = gate,
            isFavorite      = isFavorite,
            onFavoriteClick = onFavoriteClick,
            renderSize      = ImageRenderSize.HERO,
            modifier        = Modifier.fillMaxWidth().height(Dimens.featuredImageHeight)
        )
        CategoryLabel(recipe)
        RecipeTitle(recipe, fontSize = 26)
        // Pas de SeasonLabel : le hero ne porte pas le tag saison.
    }
}

// ── RecipeCard (rail horizontal) ──────────────────────────────────────────────

/** Carte du rail horizontal — largeur fixe 200 dp, image 150 dp. */
@Composable
fun RecipeCard(
    recipe: Recipe,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gate = rememberFavoriteGate()
    Column(
        modifier            = modifier
            .width(Dimens.railCardWidth)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardTextSpacing)
    ) {
        CardImage(
            recipe          = recipe,
            gate            = gate,
            isFavorite      = isFavorite,
            onFavoriteClick = onFavoriteClick,
            renderSize      = ImageRenderSize.CARD,
            modifier        = Modifier.fillMaxWidth().height(Dimens.cardImageHeight)
        )
        CategoryLabel(recipe)
        RecipeTitle(recipe, fontSize = 18)
        SeasonLabel(recipe)
    }
}

// ── RecipeGridCard (cellule de grille) ────────────────────────────────────────

/** Cellule de grille — largeur flexible, image 150 dp. */
@Composable
fun RecipeGridCard(
    recipe: Recipe,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gate = rememberFavoriteGate()
    Column(
        modifier            = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardTextSpacing)
    ) {
        CardImage(
            recipe          = recipe,
            gate            = gate,
            isFavorite      = isFavorite,
            onFavoriteClick = onFavoriteClick,
            renderSize      = ImageRenderSize.CARD,
            modifier        = Modifier.fillMaxWidth().height(Dimens.cardImageHeight)
        )
        CategoryLabel(recipe)
        RecipeTitle(recipe, fontSize = 18)
        SeasonLabel(recipe)
    }
}

// ── RecipeRowCard (ligne : recherche, listes) ─────────────────────────────────

/**
 * Ligne horizontale — vignette carrée 96 dp à gauche, texte au centre, cœur à
 * droite. Le cœur n'est pas en incrustation ici (`RecipeCards.swift:68-89`).
 */
@Composable
fun RecipeRowCard(
    recipe: Recipe,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gate = rememberFavoriteGate()

    Row(
        modifier              = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.rowImageSpacing),
        verticalAlignment     = Alignment.Top
    ) {
        Box(
            Modifier
                .size(Dimens.rowThumbnailSize)
                .clip(RoundedCornerShape(Dimens.thumbnailRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            CachedAsyncImage(
                url                = recipe.imageURL,
                contentDescription = null,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
                renderSize         = ImageRenderSize.THUMBNAIL
            )
        }

        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.rowTextSpacing)
        ) {
            CategoryLabel(recipe)
            RecipeTitle(recipe, fontSize = 17)
            SeasonLabel(recipe)
        }

        Spacer(Modifier.width(8.dp))

        FavoriteHeart(
            isFavorite = isFavorite,
            onClick    = onFavoriteClick,
            gate       = gate
        )
    }
}

// ── Briques partagées ─────────────────────────────────────────────────────────

/**
 * Cadre d'image des cartes verticales.
 *
 * `ContentScale.Crop` sur un cadre à hauteur fixe : remplissage centré **borné
 * au cadre**, miroir du `scaledToFill` + `clipped` iOS (`RecipeCards.swift:50`).
 * Le cœur est la seule incrustation admise.
 */
@Composable
private fun CardImage(
    recipe: Recipe,
    gate: FavoriteGate,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    renderSize: ImageRenderSize,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        CachedAsyncImage(
            url                = recipe.imageURL,
            // Visuel décoratif : titre et labels sont lus séparément par le
            // lecteur d'écran (`RecipeCards.swift:94`, `:98`).
            contentDescription = null,
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop,
            renderSize         = renderSize
        )

        // Seule incrustation admise (`RecipeCards.swift:52`).
        FavoriteHeart(
            isFavorite = isFavorite,
            onClick    = onFavoriteClick,
            gate       = gate,
            onImage    = true,
            modifier   = Modifier.align(Alignment.TopEnd)
        )
    }
}

/**
 * Label type de plat — Oswald 11 semibold, capitales, tracking 0,6, teinte
 * accent (`RecipeCards.swift:111-118`).
 */
@Composable
private fun CategoryLabel(recipe: Recipe) {
    val category = recipe.categoryName
    if (category.isNullOrEmpty()) return
    Text(
        text          = category.uppercase(),
        fontFamily    = Oswald,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color         = Color.accent180,
        maxLines      = 1,
        overflow      = TextOverflow.Ellipsis
    )
}

/** Titre — Playfair bold, 2 lignes maximum (`RecipeCards.swift:55-60`). */
@Composable
private fun RecipeTitle(recipe: Recipe, fontSize: Int) {
    Text(
        text       = recipe.cleanTitle,
        fontFamily = PlayfairDisplay,
        fontSize   = fontSize.sp,
        fontWeight = FontWeight.Bold,
        color      = MaterialTheme.colorScheme.onSurface,
        maxLines   = 2,
        overflow   = TextOverflow.Ellipsis
    )
}

/**
 * Saison — Oswald 12, teinte secondaire (`RecipeCards.swift:127-130`), suivie du
 * marqueur « disponible hors ligne ».
 */
@Composable
private fun SeasonLabel(recipe: Recipe) {
    val season = recipe.seasonName
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!season.isNullOrEmpty()) {
            Text(
                text       = season,
                fontFamily = Oswald,
                fontSize   = 12.sp,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(5.dp))
        }
        OfflineBadge(recipe)
    }
}

/**
 * Marqueur discret « disponible hors ligne ».
 *
 * Une icône, pas un libellé : sur une carte étroite, un texte concurrencerait le
 * titre pour une information secondaire. Le libellé complet reste annoncé aux
 * lecteurs d'écran via `contentDescription`.
 *
 * Absent de la carte « à la une », comme le tag saison — le hero ne porte aucune
 * métadonnée. Affiché en ligne comme hors ligne : c'est justement **avant** de
 * perdre le réseau qu'il est utile de savoir ce qu'on emporte.
 *
 * L'état n'est pas recalculé en temps réel pendant un cycle de téléchargement
 * (hors scope, parité iOS) : il se met à jour à la prochaine recomposition de la
 * carte.
 *
 * Miroir d'`offlineBadge` (`apple/180/RecipeCards.swift:143-159`).
 */
@Composable
private fun OfflineBadge(recipe: Recipe) {
    val offlineEnabled by OfflineSyncService.isEnabled.collectAsStateWithLifecycle()
    if (!offlineEnabled) return
    if (OfflineStore.sharedOrNull?.has(recipe.id) != true) return

    Icon(
        imageVector        = Icons.Filled.DownloadForOffline,
        contentDescription = "Disponible hors ligne",
        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier           = Modifier.size(14.dp)
    )
}
