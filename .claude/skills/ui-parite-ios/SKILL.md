---
name: ui-parite-ios
description: >
  Système de design 180°C et règles de parité visuelle avec l'app iOS pour l'app
  Android (Jetpack Compose). Utiliser pour TOUT travail touchant l'UI Android :
  création ou modification d'un écran, d'un composable, du thème (Color.kt,
  Type.kt, Theme.kt, Dimens.kt), d'une chaîne visible par l'utilisateur, d'un
  layout ou d'une animation. Déclencher aussi pour les revues d'écrans, les
  captures comparatives et toute question de wording, couleur, typographie ou
  espacement.
---

# Parité visuelle iOS — app Android 180°C

## Principe unique

L'app iOS (dépôt iOS `nearmint/180-ios`, SwiftUI) est la **source de vérité visuelle**. On ne style jamais de mémoire ni « au goût » : avant de toucher un écran Android, ouvrir la vue SwiftUI équivalente et en extraire les valeurs exactes — couleurs, polices, tailles, graisses, espacements, rayons, wordings. En cas de conflit entre ce document et le code iOS, **le code iOS gagne**. Le dépôt iOS est en lecture seule, jamais modifié.

Correspondance des écrans :

| Android | iOS |
|---|---|
| `ui/screen/HomeScreen.kt` | `apple/180/HomeView.swift` (+ `RecipeSlider.swift`) |
| `ui/screen/SearchScreen.kt` | `apple/180/SearchView.swift` |
| `ui/screen/FavoritesScreen.kt` | `apple/180/FavoritesView.swift` |
| `ui/screen/AccountScreen.kt` | `apple/180/AccountView.swift` |
| `ui/screen/RecipeDetailScreen.kt` | `apple/180/RecipeDetailView.swift` |
| `ui/screen/OnboardingScreen.kt` | `apple/180/OnboardingView.swift` |
| `ui/screen/LoginScreen.kt` | `apple/180/LoginView.swift` |
| `ui/screen/SplashScreen.kt` | `apple/180/SplashView.swift` |
| `ui/screen/NotificationsScreen.kt` | `apple/180/NotificationsView.swift` |

## Rôles typographiques (non négociables)

- **Oswald** (condensé) : titres d'écran (« Recherche », « Mon compte »…), titres de section (« Explorer par saison », « Ingrédients »…), labels de catégorie en capitales (« ENTRÉE », « PLAT »…), labels de la tab bar, boutons.
- **Playfair Display** : contenu éditorial uniquement — titres de recettes (cartes et fiche), description de recette. Jamais pour un titre d'écran, de section, ou un élément d'interface.
- **Corps utilitaire** (lignes de réglages, champs, légendes, textes secondaires) : la police que l'iOS emploie pour ces rôles — à vérifier dans le code iOS (police système SF ↔ équivalent Android), pas à supposer.

Toute inversion de ces rôles (Playfair en titre d'écran, Oswald en éditorial) est un défaut à corriger, jamais une variante acceptable.

## Couleurs

Les valeurs canoniques sont celles du code iOS (Assets.xcassets, extensions `Color`) : les extraire et les répliquer dans `ui/theme/Color.kt`. Aucun hex ne vit ailleurs que dans `Color.kt`. Intentions de la palette :

- Fond : **noir pur** — pas de charbon `#1C1B1F`.
- Texte principal : **blanc pur** ; secondaire : gris iOS.
- Accent : **`#FFAE3A`** — liens « Voir tout › », icônes de ligne, toggles ON, onglet actif, indicateurs de pagination, puces de listes.
- Favori : **cœur rouge** (valeur exacte côté iOS), sur pastille circulaire sombre translucide quand il est en surimpression d'une image.
- Surfaces (cartes de réglages, champs) : gris très sombre, **rempli**.

Aucune teinte crème/beige ni olive/brun : ce sont les artefacts du thème Material initial, pas le design system.

## Composants canoniques

- **Tab bar** : pilule flottante détachée des bords, fond sombre translucide ; onglet actif = pilule + icône/label accent ; inactifs blancs. Jamais de barre pleine largeur ni de pilule olive.
- **Toggles** : ON = piste `#FFAE3A` + pouce blanc ; OFF = piste grise + pouce blanc (`SwitchDefaults.colors` — jamais les défauts Material).
- **Champs de recherche** : fond gris sombre rempli, forme pilule, icône loupe, placeholder gris — jamais outlined.
- **Icônes sur image** (retour, partage, cloche, filtre) : pastille circulaire sombre translucide ; le partage de la fiche recette est accent.
- **En-têtes de section** : titre Oswald + lien « Voir tout › » accent, avec chevron.
- **Slider d'accueil** (`variant: "slider"`) : hero **pleine largeur**, une carte visible, indicateurs de page (points, actif = barre accent) — jamais un rail 2 colonnes.
- **Cartes de réglages (Compte)** : groupes arrondis, icône accent en tête de ligne, en-têtes de section en casse de phrase (pas de MAJUSCULES).

## Wordings

Toute chaîne visible doit être **identique à l'iOS au caractère près** — ponctuation, points de suspension, espaces insécables françaises comprises. Source : littéraux SwiftUI / Localizable du dépôt iOS. Écarts connus à ne pas réintroduire : « Chercher une recette… » (pas « Rechercher une recette… »), « Chercher dans le carnet… ». Seules exceptions : vocabulaire imposé par la plateforme, documenté en commentaire avec la référence iOS.

## Interdits produit

- **Jamais de recadrage (crop) d'images** — la règle vise les **fichiers image** : ne jamais rogner
  une source ni produire un dérivé rogné. Elle **ne vise pas le cadrage d'affichage** : l'iOS pose
  ses visuels en `.scaledToFill()` + `.clipped()` dans un cadre à taille fixe
  (`CachedAsyncImage.swift:8`, `RecipeCards.swift:50-54`, `RecipeDetailView.swift:163-170`), et son
  miroir Compose est `ContentScale.Crop`. Ce cadrage **préserve le ratio** : c'est lui qui est
  demandé, là où `ContentScale.FillBounds` — qui déforme — reste interdit. Une case de DoD qui
  demande « aucun `ContentScale.Crop` » se lit donc « aucun `Crop` **ajouté**, aucune image
  déformée » : les occurrences qui reproduisent un `.clipped()` iOS sont conformes.
  (Arbitrage produit, 29/08/2026, à la clôture du lot dédié.)
- **Aucun lien, bouton ou parcours d'achat d'abonnement** (exception Google Play permanente). La « Boutique 180°C » est autorisée : c'est la boutique, pas l'abonnement.
- Aucune valeur en dur (hex, `sp`, `dp` signifiants) hors `Color.kt` / `Type.kt` / `Dimens.kt`.

## Méthode de travail imposée

1. Lire la vue SwiftUI équivalente ; relever tokens, structures et wordings.
2. Vérifier que chaque token existe dans le thème Android ; l'ajouter sinon — une seule source.
3. Implémenter sans valeur en dur.
4. Capturer l'écran (`adb exec-out screencap -p > capture.png`) et comparer à la référence iOS ; en cas de doute sur une couleur, échantillonner le pixel.
5. Consigner tout écart volontaire (contrainte plateforme) en commentaire, avec la référence iOS exacte.
