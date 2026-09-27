package fr.thermostat6.app180.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Constantes de mise en page des cartes recette, miroir des valeurs iOS.
 *
 * Source unique : aucun rayon ni hauteur de cadre ne doit être écrit en dur
 * dans un composable de carte. Une valeur dupliquée finit toujours par diverger.
 */
object Dimens {

    // ── Rayons ───────────────────────────────────────────────────────────────

    /** Rayon des cartes (`AppRadius.card`, `apple/180/RecipeCards.swift:5`). */
    val cardRadius = 14.dp

    /** Rayon de la vignette de la carte ligne (`RecipeCards.swift:73`). */
    val thumbnailRadius = 10.dp

    // ── Cadres d'image ───────────────────────────────────────────────────────
    // Hauteurs fixes plutôt que ratios : c'est ce que fait l'iOS
    // (`.frame(height:)`, `RecipeCards.swift:49`), et cela garantit des rangées
    // alignées quel que soit le format de la photo servie.

    /** Hauteur d'image des cartes rail et grille (`RecipeCards.swift:49`). */
    val cardImageHeight = 150.dp

    /** Hauteur d'image de la carte « à la une » (`RecipeCards.swift:49`). */
    val featuredImageHeight = 300.dp

    /** Côté de la vignette carrée de la carte ligne (`RecipeCards.swift:71`). */
    val rowThumbnailSize = 96.dp

    /** Largeur fixe de la carte du rail horizontal (`RecipeCards.swift:41`). */
    val railCardWidth = 200.dp

    // ── Espacements ──────────────────────────────────────────────────────────

    /** Interligne du bloc texte des cartes verticales (`RecipeCards.swift:45`). */
    val cardTextSpacing = 6.dp

    /** Interligne du bloc texte de la carte ligne (`RecipeCards.swift:75`). */
    val rowTextSpacing = 4.dp

    /** Écart image ↔ texte de la carte ligne (`RecipeCards.swift:69`). */
    val rowImageSpacing = 12.dp

    // ── Détail recette ───────────────────────────────────────────────────────

    /** Rayon de la carte du paywall (`RecipeDetailView.swift:310`). */
    val detailCardRadius = 16.dp

    /** Rayon du bouton « Se connecter » du paywall (`RecipeDetailView.swift:294`). */
    val detailButtonRadius = 12.dp

    /** Diamètre de la puce d'un ingrédient (`RecipeDetailView.swift:253-255`). */
    val bulletSize = 6.dp

    /** Diamètre de la pastille numérotée d'une étape (`RecipeDetailView.swift:232`). */
    val stepBadgeSize = 28.dp

    /**
     * Largeur **maximale** de la colonne image du détail en écran large
     * (`RecipeDetailView.swift:88` fixe 400 pt ; ici c'est un plafond, cf.
     * [detailImageColumnFraction]).
     */
    val detailImageColumnMaxWidth = 400.dp

    /**
     * Largeur **minimale** en deçà de laquelle une colonne image ne vaut plus la
     * place qu'elle prend : sous ce seuil, la fiche repasse en une colonne.
     */
    val detailImageColumnMinWidth = 240.dp

    /**
     * Largeur minimale de la colonne texte. Calée sur la largeur utile d'un
     * téléphone (≈ 360 dp moins les marges) : en dessous, les titres Playfair se
     * cassent mot à mot, puis caractère à caractère.
     */
    val detailTextColumnMinWidth = 320.dp

    /** Part de la largeur disponible allouée à la colonne image. */
    const val detailImageColumnFraction = 0.4f

    /** Hauteur minimale du hero de détail en écran large (`RecipeDetailView.swift:119`). */
    val detailHeroHeightLarge = 500.dp

    /** Marge des icônes d'action posées sur le hero de la fiche. */
    val heroIconMargin = 12.dp

    // ── Splash ───────────────────────────────────────────────────────────────
    // `SplashView.swift:94-101`, `:105-110`, `:120-131`.

    /** Hauteur du logo du splash. */
    val splashLogoHeight = 80.dp

    /** Épaisseur de la barre de progression du splash. */
    val splashProgressHeight = 3.dp

    /** Gouttière latérale de la barre de progression. */
    val splashProgressHorizontalPadding = 60.dp

    /** Distance entre la barre de progression et le bas de l'écran. */
    val splashProgressBottomPadding = 60.dp

    // ── Onboarding ───────────────────────────────────────────────────────────
    // `OnboardingView.swift:41-96` (fonctionnalités) et `:99-151` (connexion).

    /** Hauteur du logo des pages d'onboarding (`OnboardingView.swift:50`). */
    val onboardingLogoHeight = 70.dp

    /** Gouttière latérale des pages d'onboarding (`:75`, `:92`). */
    val onboardingHorizontalPadding = 30.dp

    /** Écart entre deux rangées de fonctionnalité (`:53`). */
    val onboardingRowSpacing = 28.dp

    /** Diamètre de la pastille d'icône d'une rangée (`:164`). */
    val onboardingIconCircle = 44.dp

    /** Rayon des boutons d'appel à l'action (`:90`, `:132`). */
    val ctaButtonRadius = 14.dp

    // ── Lignes de réglages (Mon compte) ──────────────────────────────────────

    /** Taille de l'icône accent en tête d'une ligne de réglage. */
    val settingsRowIconSize = 22.dp

    /** Diamètre de l'avatar de la carte profil (`AccountView.swift:70`). */
    val profileAvatarSize = 50.dp

    // ── Lignes de navigation par taxonomie (Recherche) ───────────────────────
    // `SearchView.swift:98-118` : colonne d'icône de 36 pt, écart 14, respiration
    // verticale de 6.

    /** Largeur de la colonne d'icône d'une ligne de taxonomie. */
    val taxonomyRowIconColumn = 36.dp

    /** Écart icône ↔ libellé. */
    val taxonomyRowSpacing = 14.dp

    /** Respiration verticale d'une ligne de taxonomie. */
    val taxonomyRowVerticalPadding = 6.dp

    // ── Accueil ──────────────────────────────────────────────────────────────

    /** Hauteur du logo dans l'en-tête d'accueil (`HomeView.swift:130`). */
    val homeLogoHeight = 54.dp

    /**
     * Écart entre deux blocs de l'accueil.
     *
     * L'iOS empile les blocs dans un `VStack(spacing: 36)` (`HomeView.swift:52`).
     * Ici l'en-tête de section porte déjà 8 dp de respiration haute : 28 + 8 = 36.
     */
    val homeBlockSpacing = 28.dp

    // ── Indicateur de page du slider ─────────────────────────────────────────
    // Frise de pagination du carrousel (`RecipeSlider.swift:93-121`).

    /** Diamètre d'un point inactif. */
    val sliderDotSize = 6.dp

    /** Largeur de la pastille allongée de la page courante. */
    val sliderDotActiveWidth = 20.dp

    /** Cible tactile autour d'un point, sans l'écarter visuellement. */
    val sliderDotTouchWidth = 24.dp

    /** Hauteur de la cible tactile d'un point. */
    val sliderDotTouchHeight = 28.dp

    /** Écart entre deux cibles de la frise (`HStack(spacing: 8)`). */
    val sliderDotSpacing = 8.dp

    // ── En-têtes de section ──────────────────────────────────────────────────

    /** Gouttière latérale commune des écrans (`HomeView.swift:346`, `.padding(.horizontal)`). */
    val screenMargin = 16.dp

    /** Respiration verticale d'un en-tête et écart titre ↔ lien (`HomeView.swift:325`). */
    val sectionHeaderSpacing = 8.dp

    /** Écart « Voir tout » ↔ chevron (`HomeView.swift:351`). */
    val seeAllChevronSpacing = 2.dp

    /** Taille du chevron de « Voir tout » (`.caption`, `HomeView.swift:353`). */
    val seeAllChevronSize = 16.dp

    // ── Pastilles d'icône ────────────────────────────────────────────────────
    // Disque des icônes d'action (cloche, filtre, retour, partage).
    // L'iOS retient 44 pt pour la cloche (`HomeView.swift:146`) et 36 pt pour le
    // bouton filtres (`RecipeFilterSheet.swift:144`) ; une seule taille suffit
    // ici, calée sur la cible tactile Material.

    /** Diamètre de la pastille. */
    val pastilleSize = 44.dp

    /** Taille du glyphe posé sur la pastille. */
    val pastilleIconSize = 22.dp

    /** Diamètre du point d'alerte accent (`HomeView.swift:159`). */
    val pastilleDotSize = 10.dp

    /** Décalage du point d'alerte vers le coin (`HomeView.swift:160`). */
    val pastilleDotOffset = 2.dp

    // ── Champ de recherche ───────────────────────────────────────────────────

    /**
     * Hauteur du champ de recherche rempli. La barre système iOS mesure ~36 pt
     * (`SearchView.swift:214`) ; on retient 44 dp pour rester au-dessus de la
     * cible tactile confortable sans retomber sur les 56 dp d'un `TextField`
     * Material.
     */
    val searchFieldHeight = 44.dp

    // ── Tab bar flottante ────────────────────────────────────────────────────
    // L'iOS 26 rend le `TabView` natif en pilule détachée des bords ; ces
    // valeurs en reprennent la géométrie (`ContentView.swift:176-190`).

    /** Marge entre la pilule et les bords de l'écran. */
    val tabBarMargin = 12.dp

    /** Respiration interne de la pilule et entre les onglets. */
    val tabBarPadding = 6.dp

    /** Hauteur d'un onglet (icône + libellé), au-dessus de [minTouchTarget]. */
    val tabBarCellHeight = 52.dp

    /** Taille du glyphe d'un onglet. */
    val tabBarIconSize = 24.dp

    // ── Accessibilité ────────────────────────────────────────────────────────

    /**
     * Cible tactile minimale. L'iOS retient 44 pt (`RecipeCards.swift:157`) ;
     * Material impose 48 dp, valeur plus exigeante donc retenue ici.
     */
    val minTouchTarget = 48.dp

    /** Taille dessinée de l'icône favori en ligne (`.title3`, `RecipeCards.swift:207`). */
    val favoriteIconSize = 24.dp

    /** Taille du cœur en surimpression (`.subheadline`, `RecipeCards.swift:191`). */
    val favoriteOnImageIconSize = 18.dp

    /**
     * Diamètre de la pastille du cœur en surimpression : glyphe + `padding(8)`
     * de l'iOS (`RecipeCards.swift:192`).
     */
    val favoritePastilleSize = 34.dp

    /** Cœur du hero de la fiche — `.title2` + `padding(12)` (`RecipeDetailView.swift:178-180`). */
    val favoriteLargeIconSize = 22.dp

    /** Pastille du cœur du hero de la fiche. */
    val favoriteLargePastilleSize = 46.dp
}

/**
 * Largeur de décodage des visuels, par emplacement.
 *
 * Miroir de `maxRenderWidth` (`apple/180/RecipeCards.swift:101-108`) et des
 * tailles du hero de détail (`RecipeDetailView.swift:116`).
 *
 * Décoder à pleine résolution une photo affichée dans une vignette de 96 dp
 * gaspille mémoire et bande passante pour un rendu identique : Coil ne décode
 * que ce qui sera réellement dessiné.
 */
enum class ImageRenderSize(val widthDp: Int) {
    /** Vignette de la carte ligne — 96 dp affichés (`RecipeCards.swift:104`). */
    THUMBNAIL(120),

    /** Cartes rail et grille (`RecipeCards.swift:105`). */
    CARD(240),

    /** Carte « à la une » et hero de détail (`RecipeCards.swift:106`). */
    HERO(450),

    /** Hero de détail sur grand écran (`RecipeDetailView.swift:116`). */
    HERO_LARGE(800)
}
