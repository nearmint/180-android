package fr.thermostat6.app180.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.thermostat6.app180.data.network.PlayStoreInfo
import fr.thermostat6.app180.ui.web.WebLauncher
import fr.thermostat6.app180.util.accent180

/**
 * Écran bloquant affiché quand la version de l'app est trop ancienne.
 *
 * - Le bouton « retour » système est neutralisé ([BackHandler]).
 * - Le bouton « Mettre à jour » n'apparaît que si la fiche Play Store existe
 *   ([PlayStoreInfo]) ; sinon on invite à chercher l'app, sans lien factice —
 *   miroir de `ForceUpdateView` (`apple/180/ForceUpdateView.swift:31-48`).
 *   Sur un écran par construction sans issue, un lien mort enfermerait
 *   l'utilisateur. Avant publication, cet écran ne se déclenche de toute façon
 *   pas.
 */
@Composable
fun ForceUpdateScreen(message: String) {
    // Bloque le retour système — l'utilisateur ne peut pas contourner cet écran
    BackHandler {}

    val context = LocalContext.current

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector      = Icons.Filled.Warning,
            contentDescription = null,
            tint             = Color.accent180,
            modifier         = Modifier.size(72.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text      = "Mise à jour requise",
            style     = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        val storeUrl = PlayStoreInfo.storeUrl
        if (storeUrl != null) {
            // `openExternally` protège déjà `startActivity` (`WebLauncher.kt:56-62`) :
            // un appareil sans Play Store ni navigateur ne fait pas planter un
            // écran dont on ne peut pas sortir.
            Button(onClick = { WebLauncher.openExternally(context, storeUrl) }) {
                Text("Mettre à jour")
            }
        } else {
            Text(
                text      = "Recherchez « 180°C » sur le Play Store pour mettre à jour.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
