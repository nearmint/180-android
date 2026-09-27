package fr.thermostat6.app180.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import fr.thermostat6.app180.ui.theme.Dimens

/**
 * Fond d'une pastille d'icône.
 *
 * L'iOS pose ses icônes d'action sur un disque, jamais nues : `Circle()` rempli
 * en `.systemGray5` sur un écran (`HomeView.swift:144-151`,
 * `RecipeFilterSheet.swift:142-149`), et le verre translucide de la barre de
 * navigation quand l'icône surplombe une photo.
 */
enum class PastilleStyle {
    /** Sur un écran : disque `.systemGray5`, glyphe `.primary`. */
    Surface,

    /** Sur une photo : disque sombre translucide, glyphe blanc. */
    OnImage
}

/**
 * Icône d'action posée sur une pastille circulaire — retour, partage, cloche,
 * filtre.
 *
 * Remplace les icônes nues du constat C11 et centralise le disque translucide
 * qui était recopié trois fois dans la fiche recette.
 *
 * @param showDot pastille d'alerte accent, comme le point de la cloche
 *   (`HomeView.swift:157-160`) et celui du bouton filtres
 *   (`RecipeFilterSheet.swift:152-155`). L'iOS n'y affiche **aucun compteur**.
 * @param tint teinte du glyphe ; `null` retient celle du [style]. Le partage de
 *   la fiche recette est le seul à être accent (skill `ui-parite-ios`).
 */
@Composable
fun IconPastille(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PastilleStyle = PastilleStyle.Surface,
    tint: Color? = null,
    enabled: Boolean = true,
    showDot: Boolean = false
) {
    val background = when (style) {
        PastilleStyle.Surface -> MaterialTheme.colorScheme.surfaceVariant
        PastilleStyle.OnImage -> MaterialTheme.colorScheme.scrim.copy(alpha = ON_IMAGE_ALPHA)
    }
    val contentTint = tint ?: when (style) {
        PastilleStyle.Surface -> MaterialTheme.colorScheme.onSurface
        PastilleStyle.OnImage -> Color.White
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        IconButton(
            onClick  = onClick,
            enabled  = enabled,
            modifier = Modifier
                .size(Dimens.pastilleSize)
                .clip(CircleShape)
                .background(background)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = contentDescription,
                tint               = contentTint,
                modifier           = Modifier.size(Dimens.pastilleIconSize)
            )
        }
        if (showDot) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = Dimens.pastilleDotOffset, y = -Dimens.pastilleDotOffset)
                    .size(Dimens.pastilleDotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** Opacité du disque posé sur une photo — conserve la lisibilité du visuel. */
private const val ON_IMAGE_ALPHA = 0.35f
