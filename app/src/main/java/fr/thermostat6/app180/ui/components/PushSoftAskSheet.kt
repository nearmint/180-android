package fr.thermostat6.app180.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.thermostat6.app180.util.accent180

/**
 * « Soft-ask » maison, présenté **avant** le prompt système, au moment de plus
 * forte probabilité d'acceptation.
 *
 * Miroir de `PushSoftAskSheet`
 * (`apple/180/Views/Notifications/PushSoftAskSheet.swift:11-62`) — textes repris
 * caractère par caractère.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushSoftAskSheet(
    onActivate: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector        = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint               = Color.accent180,
                modifier           = Modifier.size(52.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text      = "Ne ratez plus une recette",
                style     = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text      = "Recevez les nouvelles recettes et les numéros de 180°C " +
                    "dès leur parution. Une notification par semaine, pas plus.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick  = onActivate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Activer les notifications", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text  = "Plus tard",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
