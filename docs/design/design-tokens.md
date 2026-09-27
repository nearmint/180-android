# Tokens de design — correspondance iOS → Android

Extraction du 29/08/2026. **Source de vérité : le code du dépôt
iOS `nearmint/180-ios` (lecture seule).** Ce document est le contrat entre les
valeurs iOS et les tokens `ui/theme/` ; aucune valeur ne doit exister ailleurs.

Convention de lecture : `fichier:ligne` renvoie au dépôt iOS pour la colonne
« iOS », au dépôt Android pour la colonne « Android ».

---

## 1. Couleurs

### 1.1 Accent

| Token | Valeur iOS | Référence iOS | Token Android | État |
|---|---|---|---|---|
| Accent 180°C | `Color(red: 255/255, green: 174/255, blue: 58/255)` = **#FFAE3A** | `Extensions.swift:8` | `Accent180` | ✅ existe (`Color.kt:8`) |
| Teinte globale de l'app | `.tint(.accent180)` sur toute la hiérarchie | `ContentView.swift:50` | `colorScheme.primary` | ❌ vaut `#FFB94A` en sombre (`Color.kt:33`) → **corriger** |
| Accent atténué (tuiles) | `Color.accent180.opacity(0.12)` | `HomeView.swift:429` | `Accent180Soft` | ➕ à créer |

L'iOS n'a **aucune** variante claircie de l'accent : `#FFAE3A` est identique en
clair et en sombre. Le `DarkPrimary = #FFB94A` d'Android est un artefact du
gabarit Material.

### 1.2 Couleurs système iOS employées

Aucun `Assets.xcassets` de couleur côté iOS (`AccentColor.colorset` est vide) :
tout ce qui n'est pas `accent180` est une **couleur système UIKit**. Leurs
valeurs canoniques sont reprises ci-dessous, puisque Android n'a pas
d'équivalent dynamique.

| Rôle iOS | Usage relevé | Réf. iOS | Sombre | Clair |
|---|---|---|---|---|
| `.systemBackground` | fond d'écran, fond des squelettes | `SkeletonView.swift:41` | **#000000** | #FFFFFF |
| `.secondarySystemBackground` | bandeau hors ligne, cellules de liste groupée | `OfflineBanner.swift:39` | **#1C1C1E** | #F2F2F7 |
| `.primary` (label) | texte principal, icônes de pastille | `RecipeCards.swift:60`, `HomeView.swift:150` | **#FFFFFF** | #000000 |
| `.secondary` (secondaryLabel) | saison, légendes, chevrons | `RecipeCards.swift:137` | **#8E8E93** ≈ #EBEBF5 @60 % | #8A8A8E |
| `.systemGray4` | points inactifs du slider, squelettes | `RecipeSlider.swift:98` | **#3A3A3C** | #D1D1D6 |
| `.systemGray5` | pastilles d'icône (cloche, filtre), champs remplis | `HomeView.swift:145`, `RecipeFilterSheet.swift:143` | **#2C2C2E** | #E5E5EA |
| `.gray` (systemGray) | cœur non favori en ligne, icônes d'historique | `RecipeCards.swift:206` | **#8E8E93** | #8E8E93 |
| `.red` | cœur favori, toast d'erreur | `RecipeCards.swift:190`, `ToastView.swift:23` | **#FF453A** | #FF3B30 |
| `.green` | statut abonné, toast de succès | `AccountView.swift:...`, `ToastView.swift:24` | **#30D158** | #34C759 |
| `.white` | cœur non favori sur image, texte sur bouton accent | `RecipeCards.swift:190`, `HomeView.swift:314` | #FFFFFF | #FFFFFF |
| `.ultraThinMaterial` | fond de la pastille du cœur, fond du toast | `RecipeCards.swift:194`, `ToastView.swift:43` | translucide sombre | translucide clair |

### 1.3 Mapping vers le `ColorScheme` Material 3

| Rôle Material | Sombre (iOS) | Clair (iOS) | Remplace |
|---|---|---|---|
| `primary` | #FFAE3A | #FFAE3A | `DarkPrimary` #FFB94A / `LightPrimary` #FFAE3A |
| `onPrimary` | #FFFFFF | #FFFFFF | #3A2400 (brun) |
| `primaryContainer` | accent 12 % | accent 12 % | #563500 / #FFE0A8 |
| `secondary` | #FFAE3A | #FFAE3A | #D9C4A0 (beige) / #736B4B (olive) |
| `secondaryContainer` | accent 22 % | accent 22 % | #554726 (olive) — chips et segments sélectionnés |
| `background` | **#000000** | #FFFFFF | #1C1B1F (charbon) / #FFFBFF |
| `onBackground` | **#FFFFFF** | #000000 | #E6E1E5 (crème) / #1C1B1F |
| `surface` | #000000 | #FFFFFF | idem background |
| `onSurface` | #FFFFFF | #000000 | #E6E1E5 (crème) |
| `surfaceContainer` | #1C1C1E | #F2F2F7 | (absent) |
| `surfaceVariant` | #2C2C2E | #E5E5EA | #4F4539 (brun) / #F0E0C8 (crème) |
| `onSurfaceVariant` | #8E8E93 | #8A8A8E | #D3C4B4 (beige) / #4F4539 |
| `outline` | #3A3A3C | #D1D1D6 | #9C8E7F / #817568 |
| `error` | #FF453A | #FF3B30 | défaut Material |

Tokens hors `ColorScheme`, portés par `MaterialTheme.app180` : `favorite`
(rouge du cœur), `success` (vert de statut), `tabBarSurface` (fond translucide
de la pilule flottante), `tabBarSelected` (surbrillance de l'onglet actif).
S'y ajoutent les constantes `Accent180Soft`, `Accent180Pill`, `OnAccent180` et
`FavoriteOnImage`.

**Pilule de l'onglet actif — surbrillance neutre.** L'accent vit sur le glyphe
et le libellé ; le porter aussi sur la pastille donne un brun (accent à 22 % sur
fond sombre = #4E3C24), c'est-à-dire l'olive même que le constat C7 reprochait à
l'indicateur Material. La pilule est donc un voile blanc à 14 %
(`DarkTabBarSelected`), mesuré à #3B3B3C sur capture.

**Couleur de contenu par défaut.** `_180cTheme` enveloppe son contenu dans une
`Surface` : sans elle, `LocalContentColor` reste au noir Material et tout texte
sans couleur explicite disparaît sur le fond noir. Le défaut existait déjà sur
`main` (titres de l'onboarding quasi illisibles sur le charbon) ; le noir pur l'a
rendu total. Le `Scaffold` des onglets, lui, posait déjà fond et couleur de
contenu — d'où des écrans à onglets épargnés et des écrans hors pile touchés
(onboarding, login, splash, mise à jour forcée).

### 1.4 Fenêtre de lancement

| Fichier Android | Actuel | Cible |
|---|---|---|
| `res/values-night/colors.xml:5` | `#1C1B1F` | `#000000` |
| `res/values/colors.xml` | `#FFFBFF` | `#FFFFFF` |

---

## 2. Typographie

### 2.1 Polices embarquées

iOS enregistre les polices **par code** (`CTFontManagerRegisterFontsForURL`),
il n'y a donc pas de clé `UIAppFonts` dans `Config/Info.plist` :
`_80App.swift:19-25` liste `Oswald-VariableFont_wght.ttf`,
`PlayfairDisplay-VariableFont_wght.ttf` et
`PlayfairDisplay-Italic-VariableFont_wght.ttf`.

Android embarque déjà `res/font/oswald.ttf` et `res/font/playfair_display.ttf`
(polices variables, axe `wght`). **Aucune police à ajouter.** L'italique
Playfair n'est pas utilisé côté Android (inclinaison synthétique,
`Type.kt:41-45`) — inchangé par ce lot.

### 2.2 Rôles relevés dans le code iOS

| Rôle | Police iOS | Taille / graisse | Référence iOS |
|---|---|---|---|
| Titre de section (rail, « Explorer par… ») | Oswald | 20 / semibold | `HomeView.swift:327`, `:607` |
| Lien « Voir tout » | Oswald | 14 / regular | `HomeView.swift:352`, `:369` |
| Label de catégorie (capitales) | Oswald | 11 / semibold, tracking 0,6 | `RecipeCards.swift:123` |
| Label de saison | Oswald | 12 / regular | `RecipeCards.swift:137` |
| Label de tuile (saison/type) | Oswald | 12 / medium | `HomeView.swift:434` |
| Titre de recette — hero | Playfair | 26 / bold | `RecipeCards.swift:59` |
| Titre de recette — carte | Playfair | 18 / bold | `RecipeCards.swift:59` |
| Titre de recette — ligne | Playfair | 17 / bold | `RecipeCards.swift:84` |
| Titre de fiche recette | Playfair | 28 / bold | `RecipeDetailView.swift:197` |
| Description / intro de recette | Playfair | 17 / regular | `RecipeDetailView.swift:217` |
| Sous-titre éditorial (groupe d'ingrédients) | Playfair | 16 / bold | `RecipeDetailView.swift:247` |
| Corps de recette (ingrédient, étape) | Playfair | 16 / regular | `RecipeDetailView.swift:258`, `:293` |
| Écran de mise à jour forcée | Playfair | 28 / bold | `ForceUpdateView.swift:18` |

### 2.3 Corps utilitaire

Tout le reste de l'iOS (lignes de réglages, champs, légendes, compteurs,
états vides, boutons) emploie la **police système SF** via les styles
dynamiques : 22 × `.caption`, 19 × `.subheadline`, 12 × `.title3`,
5 × `.headline`, 4 × `.body`, 4 × `.caption2`, 3 × `.title2`, 2 × `.title`.

Équivalent Android : la police **système** (`FontFamily.Default`, Roboto), et
non Oswald. C'est le correctif du constat **C6** — Oswald condensé sur des
lignes de réglages est un écart, pas une variante.

Correspondance des styles dynamiques iOS → tailles Compose retenues :

| Style iOS | pt | Rôle Material 3 | sp |
|---|---|---|---|
| `.title` | 28 | `headlineMedium` (utilitaire) | 28 |
| `.title2` | 22 | `headlineSmall` (utilitaire) | 22 |
| `.title3` | 20 | `titleLarge` | 20 |
| `.headline` | 17 semibold | `titleMedium` | 17 |
| `.body` | 17 | `bodyLarge` | 17 |
| `.subheadline` | 15 | `bodyMedium` | 15 |
| `.caption` | 12 | `bodySmall` | 12 |
| `.caption2` | 11 | `labelSmall` | 11 |

### 2.4 Titres d'écran — écart volontaire documenté

Côté iOS, les titres d'écran passent par `.navigationTitle(…)`
(`SearchView.swift:208` « Recherche », `AccountView.swift:42` « Mon compte »,
`FavoritesView.swift:90` « Mon carnet de recettes ») et **aucune apparence de
barre de navigation n'est surchargée** (aucune occurrence de
`UINavigationBarAppearance` / `titleTextAttributes` dans le projet) : ils sont
donc rendus en **SF Bold**, la police système.

Le SKILL (`ui-parite-ios`, « Rôles typographiques — non négociables ») et le
cahier des charges (§5, étape 2) imposent tous deux **Oswald** pour les titres
d'écran. C'est la règle retenue : Oswald, pas Playfair (constat **C4**), et pas
la police système. L'écart avec le rendu littéral de l'iOS est assumé — il
porte sur la *famille*, jamais sur le poids ni la casse — et consigné ici comme
le demande la méthode du skill.

### 2.5 Mapping typographique cible

| Rôle Material | Police | Taille / graisse | Usage |
|---|---|---|---|
| `displayLarge/Medium/Small` | Playfair Bold / SemiBold | 57 / 45 / 36 | éditorial hors gabarit (inchangé) |
| `headlineLarge` | Playfair Bold | 28 | titre de fiche recette |
| `headlineMedium` | Playfair Bold | 26 | titre du hero |
| `headlineSmall` | Playfair Bold | 18 | titre de carte |
| `titleLarge` | **Oswald SemiBold 20** | 20 | titre de section |
| `titleMedium` | **Système SemiBold 17** | 17 | `.headline` iOS |
| `titleSmall` | Oswald Medium 14 | 14 | « Voir tout », labels de tab bar |
| `bodyLarge` | **Système Regular 17** | 17 | `.body` iOS |
| `bodyMedium` | **Système Regular 15** | 15 | `.subheadline` iOS — lignes de réglages |
| `bodySmall` | **Système Regular 12** | 12 | `.caption` iOS |
| `labelLarge` | Oswald Medium 14 | 14 | boutons |
| `labelMedium` | Oswald Medium 12 | 12 | labels de tuile |
| `labelSmall` | Oswald SemiBold 11 | 11 | labels de catégorie en capitales |

Aucun rôle Material ne convient au **titre d'écran** ni au **corps éditorial**
de la fiche recette ; `Type.kt` les nomme explicitement, pour qu'aucun `sp` ne
soit écrit au point d'appel :

| Style | Police | Taille | Usage | Réf. iOS |
|---|---|---|---|---|
| `ScreenTitle` | Oswald Bold | 34 / 41 | « Recherche », « Mon compte », « Mon carnet de recettes » | métrique du grand titre `.navigationTitle` (§2.4) |
| `RecipeIntro` | Playfair Bold | 17 / 26 | introduction de recette | `RecipeDetailView.swift:217-219` |
| `RecipeGroupLabel` | Playfair Bold | 16 / 22 | libellé de groupe d'ingrédients | `:247` |
| `RecipeBody` | Playfair Regular | 16 / 25 | ligne d'ingrédient, contenu d'étape | `:258`, `:293` |
| `RecipeStepTitle` | Playfair Bold | 17 / 24 | titre d'étape | `:287` |

---

## 3. Composants

| Composant | Spécification iOS | Référence iOS | État Android |
|---|---|---|---|
| Tab bar | `TabView` natif + `.tint(.accent180)` — rendu iOS 26 : pilule flottante translucide, actif accent | `ContentView.swift:50`, `:176-190` | `NavigationBar` Material pleine largeur, pilule `secondaryContainer` olive (`AppNavigation.kt:484`) — **C7** |
| Toggle | `Toggle().tint(.accent180)` — piste ON accent, pouce blanc | `AccountView.swift:123`, `:160`, `:560` | `Switch(...)` sans `colors` (`AccountScreen.kt:705`) — **C8** |
| Champ de recherche | `.searchable(…)` natif — rempli gris, pilule, loupe | `SearchView.swift:214-218`, `FavoritesView.swift:92-96` | `OutlinedTextField` rectangulaire (`SearchScreen.kt:92`, `FavoritesScreen.kt:107`) — **C9** |
| Pastille d'icône | `Circle().fill(Color(.systemGray5))` 44×44 (cloche) / 36×36 (filtre), icône `.primary` | `HomeView.swift:144-151`, `RecipeFilterSheet.swift:142-149` | cloche nue (`HomeScreen.kt:504`), filtre nu (`FavoritesScreen.kt:96`) ; fiche recette : `Color.Black @35 %` ad hoc (`RecipeDetailScreen.kt:331`, `:345`, `:378`) — **C11** |
| Cœur en surimpression | `heart.fill`/`heart`, **rouge** si favori sinon **blanc**, padding 8, fond `.ultraThinMaterial`, `clipShape(Circle())` | `RecipeCards.swift:188-195` | accent si favori sinon blanc, **sans pastille** (`RecipeCards.kt:282`) — **C10** |
| Cœur en ligne | rouge si favori sinon **gris**, `.title3`, sans pastille | `RecipeCards.swift:204-208` | accent sinon `onSurfaceVariant` (`RecipeCards.kt:228`) — **C10** |
| En-tête de section | Oswald 20 semibold + « Voir tout » Oswald 14 accent + `chevron.right` `.caption`, espacement 2 | `HomeView.swift:325-355`, `:604-620` | Playfair `headlineSmall` + « Voir tout » `labelLarge` sans chevron (`HomeScreen.kt:544`, `:558-565`) — **C5**, **C12** |
| Indicateur de page | `Capsule()` — courant accent 20×6, autres `systemGray4` 6×6 ; cible 24×28 ; espacement 8 | `RecipeSlider.swift:95-107` | conforme sauf la couleur inactive (`onSurfaceVariant @30 %`) et l'espacement (`RecipeSlider.kt:141`) |
| Bouton accent pleine largeur | fond `accent180`, texte **blanc**, rayon 12 | `HomeView.swift:305-317` | `Button` Material (`HomeScreen.kt:365`) |
| Badge de la cloche | `Circle().fill(.accent180)` 10×10 | `HomeView.swift:157-160` | `Badge()` Material (`HomeScreen.kt:502`) |
| Badge de filtre actif | `Circle().fill(.accent180)` 8×8 | `RecipeFilterSheet.swift:152-155` | `Badge()` Material (`FavoritesScreen.kt:95`) |

Rayons et cadres (`Dimens.kt`) : déjà alignés sur `RecipeCards.swift`
(`AppRadius.card = 14`, vignette 10, hero 300, carte 150, ligne 96, rail 200).
**Rien à corriger.**

---

## 4. Valeurs en dur hors thème (Phase 0, point 3)

`rg -n "Color\(0x" app/src/main --type kotlin` hors `ui/theme/` :

| Fichier:ligne | Valeur | Cible |
|---|---|---|
| `ui/components/ToastView.kt:127` | `#2E7D32` | `SuccessGreen` (iOS `.green`) |
| `ui/components/ToastView.kt:128` | `#C62828` | `colorScheme.error` (iOS `.red`) |
| `ui/screen/AccountScreen.kt:515` | `#2E7D32` | `SuccessGreen` |
| `ui/screen/AccountScreen.kt:522` | `#2E7D32` | `SuccessGreen` |
| `ui/screen/LoginScreen.kt:281` | `#2E7D32` | `SuccessGreen` |
| `util/Extensions.kt:40` | `#FFAE3A` | duplicata de `Accent180` → réexporter le token du thème |

Valeurs ad hoc non hexadécimales, à remplacer par un token :
`Color.Black.copy(alpha = 0.35f)` × 3 (`RecipeDetailScreen.kt:331`, `:345`,
`:378`) et `Color.White` (`RecipeCards.kt:282`).
