package fr.thermostat6.app180

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache as ImageDiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.data.favorites.FavoritesManager
import fr.thermostat6.app180.data.image.ImageCacheManager
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.NetworkMonitor
import fr.thermostat6.app180.data.offline.OfflineImageFetcher
import fr.thermostat6.app180.data.offline.OfflineStore
import fr.thermostat6.app180.data.offline.OfflineSyncService
import fr.thermostat6.app180.data.preferences.AppPreferences
import fr.thermostat6.app180.data.push.NotificationCenter
import fr.thermostat6.app180.data.push.PushNotificationService
import fr.thermostat6.app180.data.push.PushPermissionCoordinator
import fr.thermostat6.app180.data.push.PushSoftAskPresenter
import fr.thermostat6.app180.data.service.DiskCache
import fr.thermostat6.app180.util.HapticFeedbackManager

/**
 * Point d'entrée de l'application.
 * Initialise tous les singletons qui requièrent un [Context] ou [Application].
 *
 * Enregistré dans AndroidManifest.xml via android:name=".App180Application".
 *
 * Ordre d'initialisation :
 *   0. ApiClient.initCache   — cache HTTP protocolaire (avant tout usage réseau)
 *   0. DiskCache             — cache disque de l'accueil (stale-while-revalidate)
 *   0. NetworkMonitor        — verdict de connectivité, lu dès la 1ʳᵉ composition
 *   1. HapticFeedbackManager — requis par FavoritesManager.toggle()
 *   2. AuthService           — restaure le JWT depuis EncryptedSharedPreferences
 *   3. FavoritesManager      — charge les IDs favoris depuis DataStore
 *   4. AppPreferences        — préférences globales (onboarding, apparence, historique)
 *   5. UmamiTracker          — mesure Umami (écran, locale, User-Agent figés ici)
 *   6. Push                  — OneSignal, puis collecteur d'identité AuthService
 *
 * Fournit aussi l'[ImageLoader] Coil applicatif : source unique, toutes les
 * requêtes image de l'app le traversent.
 */
class App180Application : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // Avant tout le reste : AuthService touche ApiClient dès son init, et le
        // cache doit être posé avant que les clients OkHttp soient construits.
        ApiClient.initCache(java.io.File(cacheDir, "http-cache"))
        DiskCache.init(java.io.File(cacheDir, "net-cache"))
        // Avant les écrans : le premier verdict de connectivité doit être
        // disponible dès la première composition (bandeau hors ligne).
        NetworkMonitor.init(this)
        // `filesDir` et non `cacheDir` : ce que l'utilisateur a explicitement
        // téléchargé ne doit pas être évincé sous pression disque.
        OfflineStore.init(filesDir)
        HapticFeedbackManager.init(this)
        AuthService.init(this)
        FavoritesManager.init(this)
        AppPreferences.init(this)
        // **Avant** OneSignal : un tap de notification au démarrage à froid passe
        // par `PushClickListener`, qui émet `push_open`. Le traceur doit déjà
        // connaître l'appareil, sinon le hit est jeté.
        //
        // Le statut abonné est passé en lambda, relue à chaque event : c'est
        // `AuthService` qui fait foi (restauré ici depuis le cache, puis confirmé
        // par `/180c/v1/me`), et le traceur n'a ainsi aucune dépendance sur lui.
        UmamiTracker.init(this) { AuthService.isSubscriber.value }
        // Après AppPreferences (il y lit son toggle) et après FavoritesManager
        // (il s'abonne à ses signaux d'ajout / retrait).
        OfflineSyncService.init()
        PushPermissionCoordinator.init(this)
        NotificationCenter.init(this)
        PushSoftAskPresenter.init(this)
        PushNotificationService.initialize(this)
        // **Après** l'init OneSignal : le collecteur pose les tags dès son
        // abonnement, et ils seraient perdus si le SDK n'était pas encore prêt.
        // C'est lui, et non le seul chemin `login()`, qui tient
        // `subscription_status` à jour (miroir `observeIdentity`,
        // `apple/180/Services/PushNotificationService.swift:85`).
        AuthService.observePushIdentity()
        ImageCacheManager.init(this)
        // `app_first_open` — une seule fois dans la vie de l'installation
        // (miroir `AppDelegate.swift:21`).
        UmamiTracker.trackFirstOpenIfNeeded()
    }

    /**
     * `ImageLoader` unique, dimensionné en miroir de l'iOS
     * (`apple/180/CachedAsyncImage.swift:132-141`).
     *
     * Divergence assumée : l'iOS borne aussi son `NSCache` à 100 entrées
     * (`countLimit`, `:132`). Coil ne borne qu'en octets — le plafond de 50 Mo,
     * seul commun aux deux, reste la contrainte effective.
     *
     * Le réseau reste celui de Coil et non l'OkHttp d'`ApiClient` : ce dernier
     * porte l'authentification et le garde-fou anti-redirection destinés à
     * l'API, hors de propos pour des visuels publics servis par le CDN.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            // Cran « store hors ligne », intercalé entre le cache mémoire (géré
            // par le moteur, en amont de tout fetcher) et le fetcher réseau, qui
            // porte le cache disque. Un visuel explicitement téléchargé prime
            // ainsi sur une copie opportuniste évinçable.
            .components { add(OfflineImageFetcher.Factory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(ImageCacheManager.MEMORY_CACHE_BYTES)
                    .build()
            }
            .diskCache {
                ImageDiskCache.Builder()
                    .directory(java.io.File(cacheDir, ImageCacheManager.DISK_CACHE_DIRECTORY))
                    .maxSizePercent(ImageCacheManager.DISK_CACHE_PERCENT)
                    .build()
            }
            .build()
}
