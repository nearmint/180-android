package fr.thermostat6.app180.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.thermostat6.app180.ui.theme.Dimens

// ── Shimmer brush ─────────────────────────────────────────────────────────────

/**
 * Brosse de shimmer animée en translation diagonale.
 * Les couleurs s'adaptent automatiquement au thème clair/sombre via
 * [MaterialTheme.colorScheme].
 */
@Composable
fun shimmerBrush(): Brush {
    val baseColor      = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surface

    val shimmerColors = listOf(baseColor, highlightColor, baseColor)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1200f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start  = Offset(translateAnim - 300f, translateAnim - 300f),
        end    = Offset(translateAnim + 300f, translateAnim + 300f)
    )
}

// ── Composants skeleton ───────────────────────────────────────────────────────

/**
 * Squelette de [FeaturedRecipeCard] — même dimensions (pleine largeur × 340 dp).
 * Affiché dans le slider "À la une" pendant le chargement initial de HomeView.
 */
@Composable
fun SkeletonFeaturedCard(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
    )
}

/**
 * Squelette de [RecipeCard] — même dimensions (200 × 140 dp).
 * Affiché dans les rails horizontaux pendant le chargement initial.
 */
@Composable
fun SkeletonRecipeCard(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Column(modifier = modifier.width(200.dp)) {
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        Spacer(Modifier.height(6.dp))
        // Barre de titre simulée
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
    }
}

/**
 * Rangée de [count] [SkeletonRecipeCard] avec espacement horizontal.
 * Prête à être placée dans un `LazyRow` ou un `Row`.
 */
@Composable
fun SkeletonRecipeRail(
    modifier: Modifier = Modifier,
    count: Int = 4
) {
    Row(
        modifier            = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(count) { SkeletonRecipeCard() }
    }
}

// ── Squelettes de liste verticale ─────────────────────────────────────────────

/** Barre de texte simulée : une ligne d'un bloc de titre ou de légende. */
@Composable
private fun SkeletonTextBar(
    width: Dp?,
    height: Dp,
    brush: Brush,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .let { if (width != null) it.width(width) else it.fillMaxWidth() }
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(brush)
    )
}

/**
 * Squelette de [RecipeRowCard] — miroir de `SkeletonRowCard`
 * (`apple/180/SkeletonView.swift:51-67`).
 *
 * Reprend les métriques de la ligne réelle depuis [Dimens] : vignette carrée,
 * même écart image ↔ texte, même interligne, et la réserve de 48 dp du bouton
 * favori à droite. C'est ce qui évite le saut de mise en page au moment où le
 * contenu arrive — la seule raison d'être d'un squelette plutôt qu'un spinner.
 */
@Composable
fun SkeletonRowCard(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Row(
        modifier              = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.rowImageSpacing)
    ) {
        Box(
            Modifier
                .size(Dimens.rowThumbnailSize)
                .clip(RoundedCornerShape(Dimens.thumbnailRadius))
                .background(brush)
        )
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.rowTextSpacing)
        ) {
            SkeletonTextBar(width = 70.dp, height = 10.dp, brush = brush)
            SkeletonTextBar(width = null,  height = 14.dp, brush = brush)
            SkeletonTextBar(width = 90.dp, height = 12.dp, brush = brush)
        }
        // Réserve du bouton favori, pour que le texte n'ait pas à se réduire
        // quand la ligne réelle le remplace.
        Spacer(Modifier.width(Dimens.minTouchTarget))
    }
}

/**
 * Écran de chargement des listes verticales de recettes — miroir de
 * `SkeletonRecipeList` (`apple/180/SkeletonView.swift:72-86`).
 *
 * Masqué à l'accessibilité : un lecteur d'écran n'a rien à annoncer d'un
 * contenu qui n'existe pas encore (`.accessibilityHidden(true)`, `:84`).
 */
@Composable
fun SkeletonRecipeList(
    modifier: Modifier = Modifier,
    count: Int = 6
) {
    Column(modifier = modifier.clearAndSetSemantics {}) {
        repeat(count) {
            SkeletonRowCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            HorizontalDivider(Modifier.padding(start = 16.dp))
        }
    }
}
