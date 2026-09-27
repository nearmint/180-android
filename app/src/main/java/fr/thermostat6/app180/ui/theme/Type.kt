package fr.thermostat6.app180.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.thermostat6.app180.R

// ── Familles de polices ───────────────────────────────────────────────────────
//
// Les deux fichiers sont des polices **variables** : un seul `.ttf` par famille
// porte un axe de graisse continu (`wght`). D'où un seul `R.font.…` répété avec
// des graisses différentes — ce n'est pas une erreur de copier-coller. Compose
// dérive le réglage d'axe de la graisse déclarée : le `variationSettings` d'une
// `ResourceFont` vaut par défaut `FontVariation.Settings(weight, style)`, donc
// `wght = weight.weight`. Rien à régler à la main, et pas d'API expérimentale.
//
// Les polices sont embarquées dans l'APK, plus téléchargées par Google Play
// Services : l'app s'affiche dans sa typographie même hors ligne, dès la
// première ouverture, et sur un appareil dépourvu des services Google.

/**
 * Playfair Display — **éditorial uniquement** : titres de recette (carte et
 * fiche), intro, groupes d'ingrédients, étapes.
 * Équivalent iOS : `AppFont.playfair(size, weight:)` (`Extensions.swift:74-83`).
 *
 * Jamais pour un titre d'écran, un titre de section ni un élément d'interface —
 * c'est l'inversion de rôles que le lot dédié corrige.
 *
 * Axe disponible : 400 → 900.
 */
val PlayfairDisplay: FontFamily = FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal),
    Font(R.font.playfair_display, FontWeight.Medium),
    Font(R.font.playfair_display, FontWeight.SemiBold),
    Font(R.font.playfair_display, FontWeight.Bold)
)

/**
 * Oswald — **interface** : titres d'écran et de section, labels de catégorie en
 * capitales, labels de la tab bar, boutons.
 * Équivalent iOS : `AppFont.oswald(size, weight:)` (`Extensions.swift:61-72`).
 *
 * Axe disponible : 200 → 700. Aucune variante italique n'existe pour Oswald ;
 * le seul style italique de l'app (`RecipeDetailScreen.kt`) est donc obtenu
 * par inclinaison synthétique.
 */
val Oswald: FontFamily = FontFamily(
    Font(R.font.oswald, FontWeight.Light),
    Font(R.font.oswald, FontWeight.Normal),
    Font(R.font.oswald, FontWeight.Medium),
    Font(R.font.oswald, FontWeight.SemiBold),
    Font(R.font.oswald, FontWeight.Bold)
)

/**
 * Police **système** — corps utilitaire : lignes de réglages, champs, légendes,
 * compteurs, états vides, textes secondaires.
 *
 * C'est le choix de l'iOS pour ces rôles : le corps de l'app y passe par les
 * styles dynamiques SF (`.subheadline` 19×, `.caption` 22×, `.body` 4×…), et
 * **jamais** par `AppFont`. Oswald, condensé, y est un écart de parité — il est
 * fait pour des titres, pas pour des paragraphes de réglages.
 *
 * Cf. `docs/design/design-tokens.md` §2.3.
 */
val SystemFont: FontFamily = FontFamily.Default

// ── Typographie Material 3 ────────────────────────────────────────────────────

/**
 * Rôles typographiques 180°C — cf. `docs/design/design-tokens.md` §2.5.
 *
 *   display*        → Playfair Bold      : éditorial hors gabarit
 *   headlineLarge   → Playfair Bold 28   : titre de fiche recette
 *   headlineMedium  → Playfair Bold 26   : titre du hero d'accueil
 *   headlineSmall   → Playfair Bold 22   : titre éditorial intermédiaire
 *   titleLarge      → Oswald SemiBold 20 : titre d'écran ET de section
 *   titleMedium     → Système SemiBold 17 : `.headline` iOS
 *   titleSmall      → Oswald Medium 14   : « Voir tout », labels de tab bar
 *   body*           → Système 17 / 15 / 12 : `.body` / `.subheadline` / `.caption`
 *   labelLarge      → Oswald Medium 14   : boutons
 *   labelMedium     → Oswald Medium 12   : labels de tuile
 *   labelSmall      → Oswald SemiBold 11 : labels de catégorie en capitales
 *
 * Les tailles proviennent des occurrences `AppFont.…` et `.font(.…)` relevées
 * dans le dépôt iOS, pas de l'échelle par défaut de Material.
 */
val App180Typography = Typography(
    // ── Display (grandes vues éditorialisées) ─────────────────────────────────
    displayLarge = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize   = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize   = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize   = 36.sp,
        lineHeight = 44.sp
    ),

    // ── Headline — éditorial seul (`RecipeDetailView.swift:197`, `RecipeCards.swift:59`) ──
    headlineLarge = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize   = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize   = 26.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize   = 22.sp,
        lineHeight = 28.sp
    ),

    // ── Title — interface (`HomeView.swift:327`, `RecipeDetailView.swift:247`) ─
    titleLarge = TextStyle(
        fontFamily = Oswald,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 20.sp,
        lineHeight = 28.sp
    ),
    /** `.headline` iOS — 17 pt semibold, police système. */
    titleMedium = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 17.sp,
        lineHeight = 24.sp
    ),
    /** « Voir tout » et labels de tab bar (`HomeView.swift:352`). */
    titleSmall = TextStyle(
        fontFamily = Oswald,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp
    ),

    // ── Body — corps utilitaire, police système (§2.3) ────────────────────────
    /** `.body` iOS — 17 pt. */
    bodyLarge = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Normal,
        fontSize   = 17.sp,
        lineHeight = 24.sp
    ),
    /** `.subheadline` iOS — 15 pt, le corps des lignes de réglages. */
    bodyMedium = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Normal,
        fontSize   = 15.sp,
        lineHeight = 20.sp
    ),
    /** `.caption` iOS — 12 pt, légendes et compteurs. */
    bodySmall = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Normal,
        fontSize   = 12.sp,
        lineHeight = 16.sp
    ),

    // ── Label — boutons, chips, badges ────────────────────────────────────────
    labelLarge = TextStyle(
        fontFamily = Oswald,
        fontWeight = FontWeight.Medium,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Oswald,
        fontWeight = FontWeight.Medium,
        fontSize   = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Oswald,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    )
)

/**
 * Titre d'écran — « Recherche », « Mon compte », « Mon carnet de recettes ».
 *
 * Aucun rôle Material ne convient : `titleLarge` porte déjà les **titres de
 * section** (20 sp, `HomeView.swift:327`), et les `headline*` sont réservés à
 * l'éditorial Playfair. L'iOS rend ces titres via `.navigationTitle`, donc en
 * grand titre système — 34 pt bold (`SearchView.swift:208`,
 * `AccountView.swift:42`, `FavoritesView.swift:90`).
 *
 * On en reprend la **métrique** (34/41, bold) mais en **Oswald**, comme
 * l'imposent le SKILL `ui-parite-ios` et le lot dédié : écart de famille assumé et
 * documenté dans `docs/design/design-tokens.md` §2.4.
 */
val ScreenTitle: TextStyle = TextStyle(
    fontFamily = Oswald,
    fontWeight = FontWeight.Bold,
    fontSize   = 34.sp,
    lineHeight = 41.sp
)

// ── Styles éditoriaux de la fiche recette ─────────────────────────────────────
//
// Playfair, comme l'iOS : `RecipeDetailView.swift:217` (intro), `:247` (libellé
// de groupe), `:258` / `:293` (lignes d'ingrédient et corps d'étape), `:287`
// (titre d'étape). Ces rôles n'ont pas d'équivalent dans l'échelle Material —
// les nommer ici évite d'écrire des `sp` au point d'appel.

/** Introduction de recette — Playfair 17 bold, interligne aéré (`:217-219`). */
val RecipeIntro: TextStyle = TextStyle(
    fontFamily = PlayfairDisplay,
    fontWeight = FontWeight.Bold,
    fontSize   = 17.sp,
    lineHeight = 26.sp
)

/** Libellé d'un groupe d'ingrédients — Playfair 16 bold (`:247`). */
val RecipeGroupLabel: TextStyle = TextStyle(
    fontFamily = PlayfairDisplay,
    fontWeight = FontWeight.Bold,
    fontSize   = 16.sp,
    lineHeight = 22.sp
)

/** Corps éditorial — ligne d'ingrédient, contenu d'étape (`:258`, `:293`). */
val RecipeBody: TextStyle = TextStyle(
    fontFamily = PlayfairDisplay,
    fontWeight = FontWeight.Normal,
    fontSize   = 16.sp,
    lineHeight = 25.sp
)

/** Titre d'une étape de préparation — Playfair 17 bold (`:287`). */
val RecipeStepTitle: TextStyle = TextStyle(
    fontFamily = PlayfairDisplay,
    fontWeight = FontWeight.Bold,
    fontSize   = 17.sp,
    lineHeight = 24.sp
)
