package fr.thermostat6.app180.data.push

import android.content.Context
import android.content.SharedPreferences
import fr.thermostat6.app180.data.model.AppNotification
import fr.thermostat6.app180.data.network.ApiClient
import com.google.gson.reflect.TypeToken
import fr.thermostat6.app180.data.network.RetryPolicy
import fr.thermostat6.app180.data.service.DiskCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centre de notifications : flux serveur + état de lecture **local**.
 *
 * Miroir de `NotificationManager` (`apple/180/NotificationManager.swift:7-51`).
 * Le serveur ne connaît pas l'état lu/non-lu : il est persisté côté app
 * (`AppNotification.swift:19-20`, `NotificationManager.swift:30-31`).
 */
object NotificationCenter {

    private const val PREFS_FILE = "app180_notifications"
    private const val KEY_READ_IDS = "readNotificationIDs.v2"

    /** Taille de page du flux (`apple/180/Services/NotificationsRepository.swift:22`). */
    private const val PER_PAGE = 20

    /** Clé du cache disque du flux. */
    private const val CACHE_KEY = "notifications-feed-v1"

    /**
     * Le cache du flux n'a **pas de TTL** côté iOS
     * (`NotificationsRepository.swift:57-70`) : il est réécrit à chaque
     * chargement réussi et sert d'affichage optimiste hors-ligne, jamais de
     * source de vérité. On reproduit ce choix — un TTL ferait disparaître le
     * contenu hors-ligne sans rien apporter.
     */
    private val CACHE_MAX_AGE_MS = Long.MAX_VALUE

    private lateinit var prefs: SharedPreferences

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _readIds = MutableStateFlow<Set<Int>>(emptySet())
    val readIds: StateFlow<Set<Int>> = _readIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)

    /**
     * Nombre de notifications non lues — source de la pastille de la cloche
     * (`NotificationManager.swift:18-19`).
     */
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private var page = 1
    private var canLoadMore = true

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        _readIds.value = prefs.getStringSet(KEY_READ_IDS, emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

        // Affichage optimiste depuis le cache disque, avant tout réseau
        // (`NotificationManager.swift:37-38`).
        _notifications.value = loadCache()
        recomputeUnread()
    }

    /**
     * Recharge le flux depuis la première page.
     *
     * Échec → le cache déjà affiché reste ; l'erreur n'est signalée que s'il est
     * vide (`NotificationManager.swift:134-150`).
     */
    suspend fun refresh() {
        _isLoading.value = true
        runCatching { RetryPolicy.withRetry { ApiClient.userApi.getNotifications(page = 1, perPage = PER_PAGE) } }
            .onSuccess { items ->
                val valid = items.filter { it.isValid }
                page = 1
                canLoadMore = items.size >= PER_PAGE
                _notifications.value = valid
                _error.value = null
                saveCache(valid)
                recomputeUnread()
            }
            .onFailure { throwable ->
                _error.value = if (_notifications.value.isEmpty()) {
                    fr.thermostat6.app180.data.network.ApiError.from(throwable).userMessage
                } else {
                    null
                }
            }
        _isLoading.value = false
    }

    /**
     * Pagination : charge la page suivante quand la **dernière ligne** apparaît.
     * Miroir de `loadMoreIfNeeded` (`NotificationManager.swift:152-166`) — même
     * seuil, même dédoublonnage par `id`, même échec silencieux.
     */
    suspend fun loadMoreIfNeeded(current: AppNotification) {
        if (!canLoadMore || _isLoading.value) return
        if (current.id != _notifications.value.lastOrNull()?.id) return

        runCatching {
            RetryPolicy.withRetry { ApiClient.userApi.getNotifications(page = page + 1, perPage = PER_PAGE) }
        }.onSuccess { items ->
            page += 1
            canLoadMore = items.size >= PER_PAGE
            val existing = _notifications.value.map { it.id }.toSet()
            val merged = _notifications.value + items.filter { it.isValid && it.id !in existing }
            _notifications.value = merged
            saveCache(merged)
            recomputeUnread()
        }
        // Échec silencieux : on garde la liste courante.
    }

    private fun saveCache(items: List<AppNotification>) {
        DiskCache.store(items, key = CACHE_KEY)
    }

    private fun loadCache(): List<AppNotification> =
        DiskCache.load<List<AppNotification>>(
            key          = CACHE_KEY,
            typeOfT      = object : TypeToken<List<AppNotification>>() {}.type,
            maxAgeMillis = CACHE_MAX_AGE_MS
        ).orEmpty()

    /** Marque une notification comme lue (`NotificationManager.swift:172-180`). */
    fun markAsRead(id: Int) {
        if (!::prefs.isInitialized) return
        _readIds.value = _readIds.value + id
        persistReadIds()
        recomputeUnread()
    }

    /** Marque tout comme lu (`NotificationManager.swift:182-189`). */
    fun markAllAsRead() {
        if (!::prefs.isInitialized) return
        _readIds.value = _readIds.value + _notifications.value.map { it.id }
        persistReadIds()
        recomputeUnread()
    }

    fun isRead(id: Int): Boolean = id in _readIds.value

    private fun recomputeUnread() {
        _unreadCount.value = _notifications.value.count { it.id !in _readIds.value }
    }

    private fun persistReadIds() {
        prefs.edit()
            .putStringSet(KEY_READ_IDS, _readIds.value.map { it.toString() }.toSet())
            .apply()
    }
}
