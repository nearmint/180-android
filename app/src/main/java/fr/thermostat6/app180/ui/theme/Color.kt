package fr.thermostat6.app180.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Palette 180°C — miroir du design system iOS.
 *
 * Source de vérité : le code du dépôt iOS `nearmint/180-ios` (lecture seule). L'iOS ne
 * définit qu'**une** couleur de marque (`Color.accent180`,
 * `apple/180/Extensions.swift:8`) ; tout le reste est une couleur système UIKit.
 * Comme Compose n'a pas d'équivalent dynamique, les valeurs canoniques d'Apple
 * sont recopiées ici, mode par mode.
 *
 * La table de correspondance complète vit dans `docs/design/design-tokens.md`.
 * Aucun hexadécimal ne doit exister ailleurs que dans ce fichier.
 */

// ── Accent 180°C ──────────────────────────────────────────────────────────────

/**
 * Orange signature — #FFAE3A (RGB 255/174/58), `Extensions.swift:8`.
 *
 * Identique en clair et en sombre : l'iOS n'a **aucune** variante claircie.
 * L'ancien `DarkPrimary = #FFB94A` était un artefact du gabarit Material.
 */
val Accent180 = Color(0xFFFFAE3A)

/** Accent à 12 % — fond des tuiles saison/type (`HomeView.swift:429`). */
val Accent180Soft = Color(0x1FFFAE3A)

/** Accent à 22 % — conteneur des chips et segments sélectionnés. */
val Accent180Pill = Color(0x38FFAE3A)

// ── Palette sombre — couleurs système iOS en mode sombre ──────────────────────

/** `.systemBackground` — **noir pur**, pas le charbon Material #1C1B1F. */
val DarkBackground = Color(0xFF000000)

/** `.label` — blanc pur (`RecipeCards.swift:60`, `.foregroundColor(.primary)`). */
val DarkOnBackground = Color(0xFFFFFFFF)

val DarkSurface   = Color(0xFF000000)
val DarkOnSurface = Color(0xFFFFFFFF)

/** `.secondarySystemBackground` — cartes de réglages, bandeau hors ligne. */
val DarkSurfaceContainer = Color(0xFF1C1C1E)

/** `.systemGray5` — champs remplis et pastilles d'icône (`HomeView.swift:145`). */
val DarkSurfaceVariant = Color(0xFF2C2C2E)

/** `.secondaryLabel` / `.systemGray` — saison, légendes, chevrons. */
val DarkOnSurfaceVariant = Color(0xFF8E8E93)

/** `.systemGray4` — points inactifs du slider (`RecipeSlider.swift:98`). */
val DarkOutline = Color(0xFF3A3A3C)

val DarkOutlineVariant = Color(0xFF2C2C2E)

/** `.red` en mode sombre — cœur favori, toast d'erreur. */
val DarkError = Color(0xFFFF453A)

/** `.green` en mode sombre — statut abonné, toast de succès. */
val DarkSuccess = Color(0xFF30D158)

/** Fond de la tab bar flottante — `.secondarySystemBackground` translucide. */
val DarkTabBarSurface = Color(0xF21C1C1E)

/**
 * Pilule de l'onglet actif — surbrillance **neutre**, pas une teinte.
 *
 * L'accent vit sur le glyphe et le libellé ; le teinter aussi produit un brun
 * (accent à 22 % sur fond sombre = #4E3C24), c'est-à-dire exactement l'olive
 * que le constat C7 reprochait à l'indicateur Material.
 */
val DarkTabBarSelected = Color(0x24FFFFFF)

// ── Palette claire — couleurs système iOS en mode clair ───────────────────────

val LightBackground   = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF000000)

val LightSurface   = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF000000)

/** `.secondarySystemBackground` clair. */
val LightSurfaceContainer = Color(0xFFF2F2F7)

/** `.systemGray5` clair. */
val LightSurfaceVariant = Color(0xFFE5E5EA)

/** `.secondaryLabel` clair. */
val LightOnSurfaceVariant = Color(0xFF8A8A8E)

/** `.systemGray4` clair. */
val LightOutline = Color(0xFFD1D1D6)

val LightOutlineVariant = Color(0xFFE5E5EA)

/** `.red` en mode clair. */
val LightError = Color(0xFFFF3B30)

/** `.green` en mode clair. */
val LightSuccess = Color(0xFF34C759)

/** Fond de la tab bar flottante en mode clair. */
val LightTabBarSurface = Color(0xF2F2F2F7)

/** Pilule de l'onglet actif en mode clair — surbrillance neutre. */
val LightTabBarSelected = Color(0x14000000)

// ── Couleurs constantes (identiques dans les deux modes) ──────────────────────

/**
 * Texte et glyphes posés sur l'accent — **blanc**, comme le bouton « Voir
 * toutes les recettes » de l'iOS (`HomeView.swift:314`) et le pouce d'un
 * `Toggle` actif. L'ancien brun #3A2400 venait du gabarit Material.
 */
val OnAccent180 = Color(0xFFFFFFFF)

/**
 * Cœur non favori posé sur une photo — blanc (`RecipeCards.swift:190`).
 * Hors image, le cœur non favori est gris (`onSurfaceVariant`).
 */
val FavoriteOnImage = Color(0xFFFFFFFF)
