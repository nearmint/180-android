package fr.thermostat6.app180.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.thermostat6.app180.data.image.ImagePrefetcher
import fr.thermostat6.app180.data.preferences.AppPreferences
import fr.thermostat6.app180.data.service.AppVersionService
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import fr.thermostat6.app180.ui.theme.Dimens
import fr.thermostat6.app180.ui.components.AppLogo
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Écran splash animé.
 *
 * Comportement :
 * 1. Fade-in du logo en 0,8 s.
 * 2. En parallèle : lecture de [AppPreferences.hasCompletedOnboarding] et
 *    appel [AppVersionService.checkMinimumVersion] (fail-open).
 * 3. Durée minimale 1,5 s au total.
 * 4. Routing : ForceUpdate > Onboarding > Main.
 *
 * En tâche de fond, l'accueil est chargé et ses premiers visuels préchargés
 * (`apple/180/SplashView.swift:117-125`) : l'accueil s'affiche déjà illustré.
 */
@Composable
fun SplashScreen(
    onNavigateToMain: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToForceUpdate: (String) -> Unit
) {
    val context  = LocalContext.current
    val alpha    = remember { Animatable(0f) }
    // Progression **réelle** du préchargement, jalonnée comme l'iOS
    // (`SplashView.swift:114-131` : 0,3 après la version, 0,75 après l'accueil,
    // 1,0 après les visuels). Aucune animation décorative : la barre n'avance
    // que sur une étape effectivement franchie.
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val startMs = System.currentTimeMillis()

        // Fade-in logo (0,8 s) en parallèle avec les vérifications
        val animJob = async { alpha.animateTo(1f, tween(durationMillis = 800)) }

        // Lecture DataStore + vérification de version en parallèle
        val onboardedJob     = async { AppPreferences.hasCompletedOnboarding.first() }
        val forceMessageJob  = async { AppVersionService.checkMinimumVersion(context) }

        // Préchauffage accueil + visuels above-the-fold. Volontairement non
        // attendu : le routing ne doit pas dépendre du réseau.
        ImagePrefetcher.warmAboveTheFold(context)
        progress.animateTo(0.3f, tween(durationMillis = 300))

        val onboarded    = onboardedJob.await()
        val forceMessage = forceMessageJob.await()
        progress.animateTo(0.75f, tween(durationMillis = 300))
        animJob.await()

        // Garantit 1,5 s minimum
        val elapsed = System.currentTimeMillis() - startMs
        if (elapsed < 1_500L) delay(1_500L - elapsed)
        progress.animateTo(1f, tween(durationMillis = 300))

        when {
            forceMessage != null -> onNavigateToForceUpdate(forceMessage)
            !onboarded           -> onNavigateToOnboarding()
            else                 -> onNavigateToMain()
        }
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        AppLogo(
            modifier = Modifier
                .height(Dimens.splashLogoHeight)
                .alpha(alpha.value)
        )

        // Barre de progression fine, ancrée en bas (`SplashView.swift:105-110`).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    horizontal = Dimens.splashProgressHorizontalPadding,
                    vertical   = Dimens.splashProgressBottomPadding
                )
                .height(Dimens.splashProgressHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                // Décorative : la progression n'apporte rien à un lecteur d'écran
                // (`.accessibilityHidden(true)`, `SplashView.swift:80`).
                .clearAndSetSemantics {}
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
