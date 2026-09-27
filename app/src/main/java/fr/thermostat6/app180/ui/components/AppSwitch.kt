package fr.thermostat6.app180.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import fr.thermostat6.app180.ui.theme.OnAccent180

/**
 * Interrupteur 180°C — miroir du `Toggle().tint(.accent180)` iOS
 * (`apple/180/AccountView.swift:123`, `:160`, `:560`).
 *
 * Les défauts Material donnaient une piste `primaryContainer` jaune terne et un
 * pouce `onPrimaryContainer` brun (constat C8, mesuré à #FFB94A / #3A2400). La
 * forme iOS est plus simple et ne varie pas : **piste accent, pouce blanc** en
 * position haute ; **piste grise, pouce blanc** en position basse. Aucune
 * bordure — l'iOS n'en dessine pas.
 *
 * Passer par ce composable plutôt que par [Switch] garantit qu'un futur
 * interrupteur ne réintroduise pas la palette Material par défaut.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked         = checked,
        onCheckedChange = onCheckedChange,
        enabled         = enabled,
        colors          = appSwitchColors(),
        modifier        = modifier
    )
}

/**
 * Palette de l'interrupteur, exposée à part pour les rares points d'appel qui
 * ont besoin d'un [Switch] brut (test, aperçu).
 */
@Composable
fun appSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor   = OnAccent180,
    checkedTrackColor   = MaterialTheme.colorScheme.primary,
    checkedBorderColor  = Color.Transparent,
    uncheckedThumbColor = OnAccent180,
    // `.systemGray4` — la piste éteinte de l'iOS.
    uncheckedTrackColor  = MaterialTheme.colorScheme.outline,
    uncheckedBorderColor = Color.Transparent,
    // Désactivé : mêmes couleurs atténuées, comme l'opacité que l'iOS applique
    // à un `Toggle` inerte (newsletter hors abonnement, `AccountView.swift:147`).
    disabledCheckedThumbColor    = OnAccent180.copy(alpha = DISABLED_ALPHA),
    disabledCheckedTrackColor    = MaterialTheme.colorScheme.primary.copy(alpha = DISABLED_ALPHA),
    disabledCheckedBorderColor   = Color.Transparent,
    disabledUncheckedThumbColor  = OnAccent180.copy(alpha = DISABLED_ALPHA),
    disabledUncheckedTrackColor  = MaterialTheme.colorScheme.outline.copy(alpha = DISABLED_ALPHA),
    disabledUncheckedBorderColor = Color.Transparent
)

/** Opacité d'un contrôle inerte — `FavoriteGate` iOS (`RecipeCards.swift:232`). */
private const val DISABLED_ALPHA = 0.35f
