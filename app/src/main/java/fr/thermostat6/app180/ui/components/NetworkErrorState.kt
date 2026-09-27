package fr.thermostat6.app180.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.thermostat6.app180.data.network.ApiError

/**
 * État d'erreur réseau sobre, partagé par les écrans qui distinguent une panne
 * d'un résultat vide.
 *
 * Même langage visuel que le mur d'erreur de l'accueil (icône hors-ligne, titre,
 * pas d'illustration) — le [message] provient d'`ApiError.userMessage`, donc des
 * chaînes françaises de l'iOS.
 */
@Composable
fun NetworkErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier         = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = Icons.Filled.WifiOff,
                contentDescription = null,
                modifier           = Modifier.size(56.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text      = message,
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onRetry) { Text("Réessayer") }
            }
        }
    }
}

/**
 * État d'erreur **typé** : icône, titre et message dérivés de la nature de la
 * panne, plus une reprise explicite.
 *
 * Miroir de `TypedErrorView` (`apple/180/OfflineBanner.swift:84-128`). Le
 * [NetworkErrorState] ci-dessus garde une icône hors-ligne figée quelle que soit
 * la cause ; il reste en place pour les écrans qui l'utilisent déjà, mais une
 * coupure réseau et une panne serveur ne doivent pas se présenter à
 * l'identique — c'est le sens de ce composant.
 *
 * @param onRetry action de reprise. Absente ⇒ aucun bouton, l'écran se
 *   rechargeant autrement (tirer-pour-rafraîchir).
 */
@Composable
fun TypedErrorState(
    error: ApiError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier            = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector        = error.icon,
            contentDescription = null,
            modifier           = Modifier.size(56.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text      = error.title,
            style     = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text      = error.userMessage,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text("Réessayer") }
        }
    }
}

/** Titre de l'état d'erreur (`OfflineBanner.swift:114-120`). */
private val ApiError.title: String
    get() = when (this) {
        ApiError.Offline -> "Vous êtes hors ligne"
        ApiError.Timeout -> "Connexion trop lente"
        else             -> "Chargement impossible"
    }

/** Icône de l'état d'erreur (`OfflineBanner.swift:122-128`). */
private val ApiError.icon: ImageVector
    get() = when (this) {
        ApiError.Offline -> Icons.Filled.WifiOff
        ApiError.Timeout -> Icons.Filled.Schedule
        else             -> Icons.Filled.ErrorOutline
    }
