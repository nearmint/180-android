package fr.thermostat6.app180.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import fr.thermostat6.app180.data.image.ImageCacheManager
import fr.thermostat6.app180.ui.theme.ImageRenderSize
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect

/**
 * Wrapper Coil 3 avec :
 *   - Placeholder gris pendant le chargement (ProgressIndicator centré)
 *   - Retry x2 avec délai exponentiel (0,5 s × tentative) en cas d'erreur
 *   - Icône restaurant (fork.knife) après épuisement des tentatives
 *   - CrossFade 300 ms
 *
 * Équivalent iOS : CachedAsyncImage + retry x2 + NSCache mémoire (géré par Coil).
 *
 * Observe [ImageCacheManager.generation] : après un vidage des caches, la vue se
 * recharge même si l'URL n'a pas bougé (`apple/180/CachedAsyncImage.swift:44`).
 */
@Composable
fun CachedAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /**
     * Largeur de décodage. Miroir du `maxRenderWidth` iOS : Coil ne décode que
     * la taille réellement affichée. La hauteur est laissée libre — le ratio de
     * la source est préservé, puis borné par le cadre.
     */
    renderSize: ImageRenderSize = ImageRenderSize.CARD
) {
    val density = LocalDensity.current
    val renderWidthPx = with(density) { renderSize.widthDp.dp.roundToPx() }
    val generation by ImageCacheManager.generation.collectAsState()
    // Réinitialisé à 0 chaque fois que l'URL change, ou après un vidage du cache
    var retries by remember(url, generation) { mutableIntStateOf(0) }

    // `key` force la recréation du SubcomposeAsyncImage à chaque tentative et à
    // chaque invalidation de cache.
    key(retries, generation) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .size(Size(renderWidthPx, coil3.size.Dimension.Undefined))
                .crossfade(300)
                .build(),
            contentDescription = contentDescription,
            modifier           = modifier,
            contentScale       = contentScale,
            loading = {
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.primary
                    )
                }
            },
            error = {
                if (retries < 2) {
                    // Attente exponentielle puis incrément → nouvelle tentative via key()
                    LaunchedEffect(retries) {
                        delay(500L * (retries + 1))
                        retries++
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    // Échec définitif — placeholder icône
                    Box(
                        modifier         = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Restaurant,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.35f),
                            modifier           = Modifier.size(32.dp)
                        )
                    }
                }
            }
        )
    }
}
