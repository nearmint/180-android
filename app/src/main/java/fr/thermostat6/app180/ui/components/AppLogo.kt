package fr.thermostat6.app180.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import fr.thermostat6.app180.R

/**
 * Logo 180°C, teinté selon le mode d'apparence effectif.
 *
 * `res/drawable/logo.png` est l'asset iOS à l'octet près
 * (`apple/180/logo.png`, md5 `1fefa4632d23b2fdfa257ff85cdb508f`) : une encre
 * **100 % noire** sur fond transparent. Affiché tel quel sur `DarkBackground`
 * (`ui/theme/Color.kt:43`, `#1C1B1F`), il devient invisible. L'iOS résout le
 * problème en le traitant comme un gabarit — `.renderingMode(.template)` plus
 * `.foregroundColor(colorScheme == .dark ? .white : .black)`
 * (`apple/180/SplashView.swift:94-99`, `OnboardingView.swift:45-51` et `:103-107`,
 * `HomeView.swift:125-133`) ; le [ColorFilter.tint] ci-dessous en est l'équivalent
 * Compose.
 *
 * La teinte est prise sur `MaterialTheme.colorScheme.onBackground` plutôt que
 * recalculée depuis `isSystemInDarkTheme()`. C'est la seule source correcte :
 * le mode est **piloté par l'utilisateur** (auto / clair / sombre) et le schéma
 * de couleurs est déjà choisi en conséquence par `_180cTheme`
 * (`ui/theme/Theme.kt:76-83`). Interroger le système directement mentirait dès
 * que l'utilisateur force un mode depuis Mon compte. Autre bénéfice : le logo
 * porte exactement l'encre du texte qui l'entoure, `#1C1B1F` en clair et
 * `#E6E1E5` en sombre, au lieu des noir et blanc purs de l'iOS.
 *
 * Seule la taille varie d'un emplacement à l'autre ; l'image n'est jamais
 * recadrée — [ContentScale.Fit] préserve les proportions 315 × 150 de l'asset.
 *
 * @param modifier Contrainte de taille de l'emplacement appelant. Doit borner au
 *   moins une dimension : sans contrainte, l'image occupe sa taille intrinsèque.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter            = painterResource(R.drawable.logo),
        contentDescription = "180°C",
        modifier           = modifier,
        contentScale       = ContentScale.Fit,
        colorFilter        = ColorFilter.tint(MaterialTheme.colorScheme.onBackground)
    )
}
