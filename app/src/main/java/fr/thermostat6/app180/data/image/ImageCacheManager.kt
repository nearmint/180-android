package fr.thermostat6.app180.data.image

import android.content.Context
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Caches d'images : dimensionnement et invalidation.
 *
 * Miroir d'`ImageCacheManager` (`apple/180/CachedAsyncImage.swift:115-243`).
 *
 * Les deux caches sont **distincts** de ceux déjà en place et ne doivent jamais
 * être confondus :
 *   - [fr.thermostat6.app180.data.service.DiskCache] — instantané JSON de
 *     l'accueil (stale-while-revalidate) ;
 *   - le `okhttp3.Cache` d'`ApiClient` — cache protocolaire des réponses REST.
 * Vider les visuels ne touche ni l'un ni l'autre.
 */
object ImageCacheManager {

    /**
     * Plafond mémoire : 50 Mo, valeur du `totalCostLimit` iOS
     * (`CachedAsyncImage.swift:133`).
     */
    const val MEMORY_CACHE_BYTES = 50L * 1024L * 1024L

    /**
     * Répertoire disque des bitmaps, dans le cache applicatif — même nom que
     * l'iOS (`CachedAsyncImage.swift:139`).
     */
    const val DISK_CACHE_DIRECTORY = "image-cache"

    /**
     * Part de l'espace libre allouée au cache disque.
     *
     * L'iOS ne borne pas son répertoire (`CachedAsyncImage.swift:135-141`) ;
     * Coil exige une borne. On retient sa valeur par défaut, 2 % de l'espace
     * disponible : la plus prudente, et elle s'adapte aux appareils modestes.
     */
    const val DISK_CACHE_PERCENT = 0.02

    /**
     * Compteur d'invalidation. Les vues image l'observent pour se recharger
     * **même à URL inchangée** après un vidage — sans lui, un pull-to-refresh
     * afficherait le visuel précédent (`CachedAsyncImage.swift:120-122`, `:44`).
     */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    private var appContext: Context? = null

    /** Portée dédiée au vidage disque : E/S hors thread principal (`:236`). */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Vide mémoire **et** disque, puis invalide les vues (`:234-243`).
     *
     * La mémoire est purgée immédiatement, le disque en tâche de fond : comme
     * l'iOS, l'appelant n'attend pas la fin des E/S pour rendre la main.
     */
    fun clear() {
        val context = appContext ?: return
        val loader = SingletonImageLoader.get(context)

        loader.memoryCache?.clear()
        ioScope.launch { runCatching { loader.diskCache?.clear() } }

        _generation.value += 1
    }
}
