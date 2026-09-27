package fr.thermostat6.app180.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.thermostat6.app180.data.analytics.UmamiScreens
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.data.analytics.AnalyticsService
import fr.thermostat6.app180.data.preferences.AppPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import fr.thermostat6.app180.ui.theme.Dimens
import fr.thermostat6.app180.ui.components.AppLogo
import fr.thermostat6.app180.util.HapticFeedbackManager
import kotlinx.coroutines.launch

private data class Feature(val icon: ImageVector, val title: String, val description: String)

/**
 * Rangées de la première page, **au mot près** comme l'iOS
 * (`apple/180/OnboardingView.swift:53-74`). Seul « iPhone » devient
 * « téléphone » — vocabulaire imposé par la plateforme.
 */
private val FEATURES = listOf(
    Feature(
        Icons.Outlined.Restaurant,
        "Les recettes 180°C",
        "Retrouvez l'intégralité des recettes publiées dans 180°C, Les Cahiers de Delphine et 12°5."
    ),
    Feature(
        Icons.Outlined.Favorite,
        "Votre carnet de recettes",
        "Sauvegardez vos recettes favorites et retrouvez-les en un instant."
    ),
    Feature(
        Icons.Outlined.Notifications,
        "Ne manquez rien",
        "Recevez une notification à chaque nouvelle recette publiée."
    ),
    Feature(
        Icons.Outlined.Shuffle,
        "Secouez pour découvrir",
        "Secouez votre téléphone pour accéder instantanément à une recette choisie au hasard."
    )
)

/**
 * Onboarding 2 pages :
 *  - Page 0 : présentation des 4 fonctionnalités + bouton "Continuer"
 *  - Page 1 : invite à se connecter ou continuer sans compte
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope      = rememberCoroutineScope()
    var showLogin  by remember { mutableStateOf(false) }

    // Page vue Umami (`apple/180/OnboardingView.swift:36`).
    LaunchedEffect(Unit) {
        UmamiTracker.trackScreen(UmamiScreens.ONBOARDING_PATH, UmamiScreens.ONBOARDING_TITLE)
    }

    // Les **deux** sorties de l'onboarding passent par ici — « Continuer sans
    // compte » et la fermeture de la feuille de connexion après un succès. C'est
    // ce qui rend l'émission exhaustive sans la dupliquer, là où l'iOS doit la
    // poser sur ses deux chemins (`apple/180/OnboardingView.swift:30`, `:137`).
    val completeOnboarding: () -> Unit = {
        scope.launch {
            AnalyticsService.onboardingComplete()
            AppPreferences.setOnboardingCompleted()
            onDone()
        }
    }

    HorizontalPager(
        state    = pagerState,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> FeaturesPage(
                onContinue = { scope.launch { pagerState.animateScrollToPage(1) } }
            )
            1 -> ConnectionPage(
                onLogin       = { showLogin = true },
                onSkip        = completeOnboarding
            )
        }
    }

    // ── LoginSheet ────────────────────────────────────────────────────────────
    if (showLogin) {
        LoginScreen(
            onDismiss = {
                showLogin = false
                // Si la connexion a réussi, on considère l'onboarding terminé
                if (AuthService.isLoggedIn.value) {
                    completeOnboarding()
                }
            }
        )
    }
}

// ── Page 0 — Fonctionnalités ──────────────────────────────────────────────────

/**
 * Le contenu — logo, titre, sous-titre, quatre rangées — dépasse la hauteur
 * utile d'un écran court (constaté sur 360 × 640 dp) : sans défilement, le
 * bouton final était mesuré sur la place résiduelle, comprimé à ~26 dp, et son
 * libellé entièrement rogné. Il ne restait qu'une barre orange muette.
 *
 * D'où le [verticalScroll] plutôt qu'une réduction du contenu, et la hauteur
 * minimale du bouton. Le `Spacer(weight(1f))` qui poussait le bouton en bas ne
 * peut pas cohabiter avec un défilement (poids indéfini dans une contrainte de
 * hauteur infinie) : il cède la place à un espacement fixe.
 *
 * L'iOS tient sans défiler (`apple/180/OnboardingView.swift:41-96`) parce que
 * sa page n'a ni titre ni sous-titre au-dessus des rangées.
 */
@Composable
private fun FeaturesPage(onContinue: () -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.onboardingHorizontalPadding, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppLogo(modifier = Modifier.height(Dimens.onboardingLogoHeight))

        Spacer(Modifier.height(32.dp))

        // Ni titre ni sous-titre : l'iOS entre directement dans les rangées
        // (`OnboardingView.swift:45-74`). « Bienvenue sur 180°C » et « Le
        // magazine de cuisine française » étaient des ajouts Android.
        FEATURES.forEachIndexed { index, feature ->
            if (index > 0) Spacer(Modifier.height(Dimens.onboardingRowSpacing))
            FeatureRow(feature)
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick  = onContinue,
            shape    = RoundedCornerShape(Dimens.ctaButtonRadius),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
        ) {
            Text("Continuer")
        }
    }
}

/**
 * Rangée de fonctionnalité — icône accent sur pastille accent atténuée, titre
 * gras, description secondaire (`apple/180/OnboardingView.swift:154-178`).
 *
 * Alignement en **haut** : une description sur deux lignes ne doit pas décaler
 * l'icône vers le bas (`HStack(alignment: .top)`, `:160`).
 */
@Composable
private fun FeatureRow(feature: Feature) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(Dimens.onboardingIconCircle)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = feature.icon,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text       = feature.title,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text  = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Page 1 — Connexion ────────────────────────────────────────────────────────

/**
 * Page 2 — miroir de `loginPage` (`apple/180/OnboardingView.swift:99-151`) :
 * logo, « Connexion », une phrase d'explication, puis le couple bouton accent /
 * lien discret.
 *
 * « Continuer sans compte » est un **lien**, pas un bouton contour : l'iOS le
 * rend en texte secondaire (`:139-144`), pour que le contraste avec l'appel à
 * l'action principal soit lisible d'un coup d'œil.
 */
@Composable
private fun ConnectionPage(
    onLogin: () -> Unit,
    onSkip:  () -> Unit
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.onboardingHorizontalPadding, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppLogo(modifier = Modifier.height(Dimens.onboardingLogoHeight))

        Spacer(Modifier.height(16.dp))

        Text(
            text      = "Connexion",
            style     = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text      = "Connectez-vous pour accéder à toutes les recettes, avec votre abonnement 180°C.",
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick  = onLogin,
            shape    = RoundedCornerShape(Dimens.ctaButtonRadius),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
        ) {
            Text("Se connecter")
        }

        Spacer(Modifier.height(16.dp))

        TextButton(
            // `apple/180/OnboardingView.swift:136` : la sortie sans compte est
            // le seul des deux boutons de la page à porter un retour haptique.
            onClick  = { HapticFeedbackManager.light(); onSkip() },
            colors   = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continuer sans compte", fontWeight = FontWeight.Medium)
        }
    }
}
