package fr.thermostat6.app180.data.image

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Size
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.service.HomeRepository
import fr.thermostat6.app180.ui.theme.ImageRenderSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Préchargement des visuels visibles à l'ouverture.
 *
 * Miroir de `prefetchAboveTheFold` (`apple/180/SplashView.swift:129-153`) : la
 * photo à la une puis les quatre cartes suivantes sont téléchargées pendant le
 * splash, de sorte que l'accueil s'affiche déjà illustré.
 *
 * Les requêtes portent **exactement** la même taille de décodage que celles des
 * cartes : c'est ce qui fait qu'elles retrouvent l'entrée en cache au lieu de
 * retélécharger. Une taille différente produirait une clé différente, et le
 * préchargement ne servirait à rien.
 */
object ImagePrefetcher {

    /** Nombre de cartes préchargées après la une (`SplashView.swift:143`). */
    private const val CARDS_PREFETCHED = 4

    /**
     * Portée applicative : le préchargement doit survivre à la sortie du splash.
     * Adossée au `SupervisorJob` du singleton, elle n'est jamais annulée.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Charge l'accueil, l'écrit sur disque, puis précharge ses premiers visuels.
     *
     * **Divergence assumée** : l'iOS bloque son splash le temps de l'opération,
     * une barre de progression rendant l'attente lisible
     * (`SplashView.swift:117-125`). Le splash Android est un fondu de durée fixe
     * sans affordance de progression : l'y suspendre passerait pour un gel sur
     * réseau lent. Le travail est donc lancé en tâche de fond et l'écran suit son
     * enchaînement habituel — même bénéfice, sans démarrage à durée indéterminée.
     */
    fun warmAboveTheFold(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val snapshot = runCatching { HomeRepository.loadAndCache() }.getOrNull() ?: return@launch
            prefetch(appContext, snapshot.hydrated, snapshot.recent)
        }
    }

    /**
     * @param hydrated recettes des rails et de la une, dans l'ordre serveur.
     * @param recent   repli quand aucun bloc n'a été hydraté.
     */
    internal fun prefetch(context: Context, hydrated: List<Recipe>, recent: List<Recipe>) {
        val loader = SingletonImageLoader.get(context)
        val density = context.resources.displayMetrics.density

        targets(hydrated, recent).forEach { (url, renderSize) ->
            val widthPx = (renderSize.widthDp * density).toInt()
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(Size(widthPx, Dimension.Undefined))
                    .build()
            )
        }
    }

    /**
     * La une au format large, puis les quatre cartes suivantes
     * (`SplashView.swift:136-146`).
     *
     * Extrait de tout contexte Android pour rester vérifiable en test JVM.
     */
    internal fun targets(
        hydrated: List<Recipe>,
        recent: List<Recipe>
    ): List<Pair<String, ImageRenderSize>> {
        val cards = hydrated.ifEmpty { recent }
        val hero = (hydrated.firstOrNull() ?: recent.firstOrNull())?.imageURL

        return buildList {
            hero?.takeIf { it.isNotBlank() }?.let { add(it to ImageRenderSize.HERO) }
            cards.drop(1).take(CARDS_PREFETCHED).forEach { recipe ->
                recipe.imageURL?.takeIf { it.isNotBlank() }?.let { add(it to ImageRenderSize.CARD) }
            }
        }
    }
}
