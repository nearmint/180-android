package fr.thermostat6.app180.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.thermostat6.app180.ui.theme.app180
import fr.thermostat6.app180.util.accent180
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Types ─────────────────────────────────────────────────────────────────────

enum class ToastType { SUCCESS, ERROR, INFO }

data class ToastData(val message: String, val type: ToastType)

// ── Manager singleton ─────────────────────────────────────────────────────────

/**
 * Singleton gérant le toast overlay global de l'app.
 * Équivalent iOS : ToastManager.shared.
 *
 * Usage :
 *   ToastManager.show("Message envoyé", ToastType.SUCCESS)
 *
 * Le composable [ToastHost] doit être placé à la racine du Scaffold pour
 * intercepter tous les toasts, quelle que soit la vue émettrice.
 */
object ToastManager {

    private val _current = MutableStateFlow<ToastData?>(null)

    /** Toast actuellement affiché, ou null si aucun. */
    val current: StateFlow<ToastData?> = _current.asStateFlow()

    /**
     * Affiche un toast.
     * @param message Texte à afficher.
     * @param type    Type visuel : SUCCESS (vert), ERROR (orange-rouge), INFO (orange).
     */
    fun show(message: String, type: ToastType = ToastType.INFO) {
        _current.value = ToastData(message, type)
    }

    /** Masque le toast actuel. Appelé automatiquement après 3 s. */
    fun dismiss() {
        _current.value = null
    }
}

// ── Composable host ───────────────────────────────────────────────────────────

/**
 * Overlay toast à placer dans un [Box] à la racine du Scaffold.
 *
 * ```kotlin
 * // Dans MainActivity / AppNavigation :
 * Box(Modifier.fillMaxSize()) {
 *     _180cTheme { AppNavigation() }
 *     ToastHost(Modifier.align(Alignment.BottomCenter))
 * }
 * ```
 *
 * Le toast s'auto-masque après 3 secondes.
 */
@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val toast by ToastManager.current.collectAsStateWithLifecycle()

    // Auto-dismiss après 3 s
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3_000L)
            ToastManager.dismiss()
        }
    }

    Box(
        modifier         = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter   = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit    = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
        ) {
            toast?.let { data ->
                ToastCard(data = data)
            }
        }
    }
}

// ── Carte toast ───────────────────────────────────────────────────────────────

@Composable
private fun ToastCard(data: ToastData) {
    // Teintes du thème, miroir de `ToastView.swift:21-27` (`.green`, `.red`,
    // `.accent180`). L'iOS colore l'icône sur un fond translucide ; le fond
    // plein d'ici est un écart de structure, renvoyé au LOT-11.
    val (backgroundColor, icon) = when (data.type) {
        ToastType.SUCCESS -> Pair(MaterialTheme.app180.success,   Icons.Filled.CheckCircle)
        ToastType.ERROR   -> Pair(MaterialTheme.colorScheme.error, Icons.Filled.Warning)
        ToastType.INFO    -> Pair(Color.accent180,                 Icons.Filled.Info)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape  = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier         = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector      = icon,
                contentDescription = null,
                tint             = Color.White
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text  = data.message,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
