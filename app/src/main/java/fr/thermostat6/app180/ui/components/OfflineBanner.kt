package fr.thermostat6.app180.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.thermostat6.app180.data.network.NetworkMonitor
import fr.thermostat6.app180.navigation.TabRouter
import fr.thermostat6.app180.util.HapticFeedbackManager

/**
 * Bandeau persistant affiché tant que l'appareil est hors ligne.
 *
 * Il ne remplace pas les états d'erreur des écrans : il **explique** (le contenu
 * qui manque n'est pas cassé, il est hors de portée) et **oriente** (le carnet,
 * lui, reste consultable). Le CTA est proposé même si rien n'a été téléchargé :
 * le carnet gère son propre état vide, et une porte visible vaut mieux qu'une
 * porte qu'on retire au moment où elle servirait.
 *
 * Miroir d'`OfflineBanner` (`apple/180/OfflineBanner.swift`).
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.WifiOff,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp),
                    tint               = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text       = "Vous êtes hors ligne",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            TextButton(onClick = {
                HapticFeedbackManager.light()
                TabRouter.select(TabRouter.Tab.FAVORITES)
            }) {
                Text(
                    text       = "Voir mon carnet",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        HorizontalDivider()
    }
}

/**
 * Épingle le bandeau au-dessus du contenu tant que la connectivité est perdue.
 *
 * À poser sur l'Accueil, la Recherche et Mon compte. Le Carnet en est
 * volontairement dépourvu : c'est la destination du CTA, y afficher « Voir mon
 * carnet » proposerait d'aller là où l'on est déjà.
 *
 * L'équivalent iOS est un `ViewModifier` (`.offlineBanner()`) ; Compose n'ayant
 * pas de `safeAreaInset`, on encadre explicitement le contenu.
 */
@Composable
fun WithOfflineBanner(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isConnected by NetworkMonitor.isConnected.collectAsStateWithLifecycle()

    Column(modifier) {
        AnimatedVisibility(
            visible = !isConnected,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            OfflineBanner()
        }
        content()
    }
}
