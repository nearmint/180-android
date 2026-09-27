package fr.thermostat6.app180

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.thermostat6.app180.navigation.AppNavigation
import fr.thermostat6.app180.ui.components.ToastHost
import androidx.lifecycle.lifecycleScope
import fr.thermostat6.app180.data.deeplink.DeepLinkResolver
import fr.thermostat6.app180.data.push.PushClickListener
import fr.thermostat6.app180.ui.layout.LocalWindowWidthClass
import kotlinx.coroutines.launch
import fr.thermostat6.app180.ui.theme._180cTheme
import fr.thermostat6.app180.ui.viewmodel.AccountViewModel
import androidx.compose.runtime.getValue

/**
 * Activité principale.
 *
 * - Branche [_180cTheme] sur [AccountViewModel.appearanceMode] (0=auto, 1=clair, 2=sombre).
 * - Place [ToastHost] en overlay dans un [Box] racine.
 * - Lance [AppNavigation] comme contenu principal.
 * - Calcule la classe de largeur de fenêtre — l'API officielle exige une
 *   `Activity` — et la fournit à l'arbre via [LocalWindowWidthClass].
 */
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)
        setContent {
            val accountVM      = viewModel<AccountViewModel>()
            val appearanceMode by accountVM.appearanceMode.collectAsStateWithLifecycle()
            val windowSizeClass = calculateWindowSizeClass(this)

            CompositionLocalProvider(
                LocalWindowWidthClass provides windowSizeClass.widthSizeClass
            ) {
                _180cTheme(appearanceMode = appearanceMode) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavigation()
                        ToastHost(Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
    }

    /**
     * L'activité est en `singleTop` : un lien ouvert alors que l'app tourne
     * déjà arrive ici plutôt que de créer une seconde instance.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Un permalien de recette est traduit en destination, puis remis au même
     * routeur que les taps de notification : un seul chemin d'application des
     * destinations, y compris au démarrage à froid où la navigation n'existe pas
     * encore (`AppNavigation.kt`, `pendingDestination`).
     */
    private fun handleDeepLink(intent: Intent?) {
        val url = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString() ?: return
        lifecycleScope.launch {
            PushClickListener.route(DeepLinkResolver.resolve(url))
        }
    }
}
