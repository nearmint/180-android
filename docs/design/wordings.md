# Glossaire de wordings — iOS ↔ Android

Extraction du 29/08/2026, LOT-10. Règle du SKILL `ui-parite-ios` : toute chaîne
visible est **identique à l'iOS au caractère près** (ponctuation, points de
suspension `…` en un seul caractère, espaces insécables). Seules exceptions
admises : le vocabulaire imposé par la plateforme, documenté ci-dessous.

Méthode : littéraux SwiftUI de `180-ios/180/*.swift` comparés aux
littéraux Kotlin de `app/src/main/java/fr/thermostat6/app180/ui/`.

---

## 1. Écarts à corriger dans ce lot

| # | Chaîne iOS (exacte) | Référence iOS | Chaîne Android actuelle | Fichier:ligne Android |
|---|---|---|---|---|
| W1 | `Chercher une recette…` | `SearchView.swift:217` | `Rechercher une recette…` | `ui/screen/SearchScreen.kt:94` |
| W2 | `Chercher dans le carnet…` | `FavoritesView.swift:95` | `Rechercher dans le carnet` | `ui/screen/FavoritesScreen.kt:110` |
| W3 | `Recherches récentes` | `SearchView.swift:62` | `Dernières recherches` | `ui/screen/SearchScreen.kt:259` |
| W4 | `Aucune recette` | `FavoritesView.swift:170` | `Aucun résultat` | `ui/screen/FavoritesScreen.kt:162` |

W2 porte **deux** écarts : le verbe et les points de suspension manquants.

## 2. Casse des en-têtes de section (« Mon compte »)

L'iOS déclare ses sections en **casse de phrase** — `Section("Paramètres")`,
`Section("Notifications et newsletters")`, `Section("Centre d'aide")`,
`Section("Informations légales")` (`AccountView.swift:112`, `:171`, `:263`,
`:306`), et `Text("Recettes hors ligne")` en `header:`
(`AccountView.swift:592`). Depuis iOS 15, une `List` en style groupé les rend
**telles quelles**, sans capitalisation.

Android les met en capitales : `title.uppercase()`
(`ui/screen/AccountScreen.kt:669`). Le SKILL le nomme explicitement
(« en-têtes de section en casse de phrase (pas de MAJUSCULES) »). → **Retirer
l'appel `.uppercase()`.** Les huit titres passés à `SettingsSection`
(`AccountScreen.kt:161`, `:184`, `:197`, `:258`, `:274`, `:355`, `:421`,
`:559`) sont déjà à la bonne casse et n'ont pas à changer.

## 3. Chaînes conformes — vérifiées, aucune action

`Voir tout`, `Voir toutes les recettes`, `Mon carnet de recettes`,
`Mon compte`, `Notifications`, `Recherche`, `Accueil`, `Favoris`, `Compte`,
`Explorer par saison`, `Explorer par type`, `Aucune recette trouvée`,
`Essayez avec d'autres mots-clés.`, `Aucun favori pour l'instant`,
`Appuyez sur le cœur d'une recette pour la retrouver ici.`,
`Aucun favori ne correspond à ces filtres.`, `Ajouter aux favoris`,
`Retirer des favoris`, `Connectez-vous pour utiliser les favoris`,
`Notifications push`, `Les Cahiers de Delphine`, `Télécharger mon carnet`,
`Vider le cache`, `Recettes hors ligne`, `Centre d'aide`,
`Informations légales`, `Boutique 180°C`, `Filtres`, `Appliquer`,
`Réinitialiser les filtres`, `Se connecter`, `Se déconnecter`, `Connexion`,
`Réessayer`, `Vous êtes hors ligne`, `Disponible hors ligne`,
`Recettes en vedette`, `Vous aimerez aussi`, `Ingrédients`, `Préparation`,
`Mise à jour requise`, `Mettre à jour`, `Votre carnet vous attend`.

## 4. Écarts de plateforme assumés

| Android | iOS | Motif |
|---|---|---|
| `Recherchez « 180°C » sur le Play Store pour mettre à jour.` | `…sur l'App Store…` | magasin de la plateforme (`ForceUpdateView.swift:24`) |
| `…depuis les Réglages de votre téléphone.` | `…de votre iPhone.` | appareil de la plateforme (`NotificationsView.swift`) |
| `Secouez votre téléphone pour découvrir une recette au hasard.` | `…votre iPhone…` | idem |

## 5. Chaînes hors périmètre du LOT-10

Différences liées à des **écrans dont la structure diffère encore** — traitées
au LOT-11, pas ici :

- `Essayez « tomate » ou « tarte »` (`SearchScreen.kt:313`) — état initial de
  recherche propre à Android ; l'iOS n'a pas d'équivalent, il affiche
  directement l'historique puis les sections « Explorer par… ».
- `À la une`, `Explorer`, `Recettes`, `Surprise` — libellés de blocs d'accueil
  dont le découpage suit encore le contrat Android.
- Écran d'onboarding (`Bienvenue sur 180°C`, `Ne ratez plus une recette`,
  `Des centaines de recettes françaises testées et approuvées.`) — la structure
  iOS de `OnboardingView.swift` est reprise au LOT-11.
