package fr.thermostat6.app180.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.ui.theme.Dimens
import fr.thermostat6.app180.ui.theme.FavoriteOnImage
import fr.thermostat6.app180.ui.theme.app180

// ── Gating du cœur (carnet réservé aux comptes) ───────────────────────────────

/** Opacité du cœur hors session (`apple/180/RecipeCards.swift:197`). */
private const val FAVORITE_DISABLED_ALPHA = 0.35f

/** Indice d'accessibilité hors session (`apple/180/RecipeCards.swift:198`). */
private const val FAVORITE_DISABLED_HINT = "Connectez-vous pour utiliser les favoris"

/**
 * État du cœur pour la session courante.
 *
 * Miroir du `ViewModifier` `FavoriteGate` (`apple/180/RecipeCards.swift:191-200`) :
 * hors session le bouton reste **visible mais désactivé**, estompé, et porte un
 * indice d'accessibilité expliquant pourquoi.
 */
class FavoriteGate(val isLoggedIn: Boolean) {
    val alpha: Float get() = if (isLoggedIn) 1f else FAVORITE_DISABLED_ALPHA

    /**
     * Favori **du point de vue de l'affichage** : hors session un résidu local ne
     * doit pas afficher un cœur plein sur un bouton par ailleurs désactivé
     * (`apple/180/RecipeDetailView.swift:103-108`).
     */
    fun showsAsFavorite(isFavorite: Boolean): Boolean = isLoggedIn && isFavorite
}

@Composable
fun rememberFavoriteGate(): FavoriteGate {
    val isLoggedIn by AuthService.isLoggedIn.collectAsStateWithLifecycle()
    return FavoriteGate(isLoggedIn)
}

fun Modifier.favoriteGate(gate: FavoriteGate): Modifier = this
    .alpha(gate.alpha)
    .semantics { if (!gate.isLoggedIn) stateDescription = FAVORITE_DISABLED_HINT }

// ── Cœur favori ───────────────────────────────────────────────────────────────

/**
 * Cœur favori — miroir de `favoriteButton` / `favoriteIcon`
 * (`apple/180/RecipeCards.swift:186-211`).
 *
 * Deux formes, une seule règle de couleur : **rouge** quand la recette est au
 * carnet. Sinon blanc en surimpression d'une photo, gris dans une ligne de
 * liste. L'orange du constat C10 était un emprunt à l'accent, qui ne signale
 * rien ici — l'accent dit « action », le rouge dit « favori ».
 *
 * @param onImage `true` pose le cœur sur une pastille sombre translucide, comme
 *   le `.background(.ultraThinMaterial).clipShape(Circle())` de l'iOS
 *   (`RecipeCards.swift:193-194`) : sans elle, un cœur blanc disparaît sur une
 *   photo claire.
 * @param large glyphe et pastille du hero de la fiche recette, plus généreux que
 *   sur une carte (`.title2` + `padding(12)`, `RecipeDetailView.swift:177-182`).
 */
@Composable
fun FavoriteHeart(
    isFavorite: Boolean,
    onClick: () -> Unit,
    gate: FavoriteGate,
    modifier: Modifier = Modifier,
    onImage: Boolean = false,
    large: Boolean = false
) {
    val shown = gate.showsAsFavorite(isFavorite)
    val tint = when {
        shown   -> MaterialTheme.app180.favorite
        onImage -> FavoriteOnImage
        else    -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier         = modifier.sizeIn(
            minWidth  = Dimens.minTouchTarget,
            minHeight = Dimens.minTouchTarget
        ),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick  = onClick,
            enabled  = gate.isLoggedIn,
            modifier = Modifier
                .then(
                    if (onImage) {
                        Modifier
                            .size(
                                if (large) Dimens.favoriteLargePastilleSize
                                else Dimens.favoritePastilleSize
                            )
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.scrim.copy(alpha = ON_IMAGE_ALPHA)
                            )
                    } else {
                        Modifier
                    }
                )
                .favoriteGate(gate)
        ) {
            Icon(
                imageVector        = if (shown) Icons.Filled.Favorite else Icons.Outlined.Favorite,
                contentDescription = if (shown) "Retirer des favoris" else "Ajouter aux favoris",
                tint               = tint,
                modifier           = Modifier.size(
                    when {
                        large   -> Dimens.favoriteLargeIconSize
                        onImage -> Dimens.favoriteOnImageIconSize
                        else    -> Dimens.favoriteIconSize
                    }
                )
            )
        }
    }
}

/** Opacité de la pastille posée sur une photo — cf. [IconPastille]. */
private const val ON_IMAGE_ALPHA = 0.35f
