package fr.thermostat6.app180.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// ── ColorSchemes 180°C ────────────────────────────────────────────────────────
//
// Mapping documenté dans `docs/design/design-tokens.md` §1.3. Les rôles
// `secondary*` portent l'accent : l'iOS n'a pas de seconde couleur de marque, et
// `secondaryContainer` est la teinte de la pilule d'onglet — c'est de là que
// venait l'olive #554726.

private val Light180ColorScheme = lightColorScheme(
    primary              = Accent180,
    onPrimary            = OnAccent180,
    primaryContainer     = Accent180Soft,
    onPrimaryContainer   = LightOnBackground,
    secondary            = Accent180,
    onSecondary          = OnAccent180,
    secondaryContainer   = Accent180Pill,
    onSecondaryContainer = Accent180,
    background        = LightBackground,
    onBackground      = LightOnBackground,
    surface           = LightSurface,
    onSurface         = LightOnSurface,
    surfaceContainer  = LightSurfaceContainer,
    surfaceVariant    = LightSurfaceVariant,
    onSurfaceVariant  = LightOnSurfaceVariant,
    outline           = LightOutline,
    outlineVariant    = LightOutlineVariant,
    error             = LightError,
    onError           = OnAccent180
)

private val Dark180ColorScheme = darkColorScheme(
    primary              = Accent180,
    onPrimary            = OnAccent180,
    primaryContainer     = Accent180Soft,
    onPrimaryContainer   = DarkOnBackground,
    secondary            = Accent180,
    onSecondary          = OnAccent180,
    secondaryContainer   = Accent180Pill,
    onSecondaryContainer = Accent180,
    background        = DarkBackground,
    onBackground      = DarkOnBackground,
    surface           = DarkSurface,
    onSurface         = DarkOnSurface,
    surfaceContainer  = DarkSurfaceContainer,
    surfaceVariant    = DarkSurfaceVariant,
    onSurfaceVariant  = DarkOnSurfaceVariant,
    outline           = DarkOutline,
    outlineVariant    = DarkOutlineVariant,
    error             = DarkError,
    onError           = OnAccent180
)

// ── Couleurs sémantiques hors rôles Material ──────────────────────────────────

/**
 * Couleurs que le `ColorScheme` Material ne sait pas porter, mais que l'iOS
 * emploie : le rouge du cœur favori, le vert de statut, le fond de la tab bar
 * flottante.
 *
 * Exposées par `MaterialTheme.app180` pour rester lisibles au point d'appel et
 * suivre le mode d'apparence forcé comme le reste du thème.
 */
data class App180Colors(
    /** `.red` — cœur favori (`RecipeCards.swift:190`). */
    val favorite: Color,
    /** `.green` — statut abonné, toast de succès (`ToastView.swift:24`). */
    val success: Color,
    /** Fond translucide de la tab bar flottante et des feuilles. */
    val tabBarSurface: Color,
    /** Pilule neutre de l'onglet actif. */
    val tabBarSelected: Color
)

private val LightApp180Colors = App180Colors(
    favorite       = LightError,
    success        = LightSuccess,
    tabBarSurface  = LightTabBarSurface,
    tabBarSelected = LightTabBarSelected
)

private val DarkApp180Colors = App180Colors(
    favorite       = DarkError,
    success        = DarkSuccess,
    tabBarSurface  = DarkTabBarSurface,
    tabBarSelected = DarkTabBarSelected
)

private val LocalApp180Colors = staticCompositionLocalOf { DarkApp180Colors }

/** Couleurs sémantiques 180°C, en complément de `MaterialTheme.colorScheme`. */
val MaterialTheme.app180: App180Colors
    @Composable @ReadOnlyComposable
    get() = LocalApp180Colors.current

// ── CompositionLocal — mode apparence ─────────────────────────────────────────

/**
 * Fournit le mode d'apparence actuel à tout composant de l'arbre.
 *   0 = auto (suit le système)
 *   1 = clair forcé
 *   2 = sombre forcé
 *
 * Écrit par [_180cTheme], lu par [AccountViewModel] et les composants
 * qui ont besoin de connaître le mode explicitement.
 */
val LocalAppearanceMode = compositionLocalOf { 0 }

// ── Point d'entrée du thème ───────────────────────────────────────────────────

/**
 * Thème Material 3 de l'app 180°C.
 *
 * @param appearanceMode Mode d'apparence : 0=auto, 1=clair, 2=sombre.
 *   À brancher sur [AccountViewModel.appearanceMode] dans [MainActivity].
 * @param content Contenu Compose enfant.
 */
@Composable
fun _180cTheme(
    appearanceMode: Int = 0,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (appearanceMode) {
        1    -> false       // forcé clair
        2    -> true        // forcé sombre
        else -> systemDark  // auto
    }

    val colorScheme = if (useDarkTheme) Dark180ColorScheme else Light180ColorScheme
    val app180      = if (useDarkTheme) DarkApp180Colors else LightApp180Colors

    CompositionLocalProvider(
        LocalAppearanceMode provides appearanceMode,
        LocalApp180Colors provides app180
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = App180Typography
        ) {
            // `Surface` racine : sans elle, `LocalContentColor` reste au noir
            // par défaut et tout texte sans couleur explicite est invisible sur
            // le fond noir — c'est ce qui effaçait les titres de l'onboarding,
            // du login et du splash, seuls écrans hors `Scaffold` (le `Scaffold`
            // des onglets, lui, pose déjà fond et couleur de contenu).
            //
            // Elle garantit aussi que le mode d'apparence **forcé** l'emporte
            // sur le fond de fenêtre XML, qui suit le mode système.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color    = colorScheme.background,
                content  = content
            )
        }
    }
}
