package fr.thermostat6.app180.data.service

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type
import java.util.Base64

/**
 * Cache disque générique clé → valeur sérialisable, horodaté et borné par TTL.
 *
 * Support de la stratégie **stale-while-revalidate** : l'appelant lit d'abord la
 * valeur en cache (affichage immédiat, même légèrement périmée dans la limite du
 * TTL), puis rafraîchit en réseau et réécrit. Les fichiers vivent dans le
 * `cacheDir` de l'app — évincés par le système sous pression disque, jamais
 * sauvegardés.
 *
 * Miroir de `DiskCache` iOS (`apple/180/DiskCache.swift`), enveloppe horodatée
 * comprise : l'âge se calcule sur `storedAt` et non sur la date de modification
 * du fichier, peu fiable après une restauration (`DiskCache.swift:23-24`).
 *
 * [init] prend un répertoire plutôt qu'un `Context` : aucune dépendance au
 * framework Android, donc testable en JVM pure.
 */
object DiskCache {

    private val gson = Gson()

    private var directory: File? = null

    /** Enveloppe horodatée (`DiskCache.swift:25-28`). */
    private data class Envelope<T>(val storedAt: Long, val value: T)

    /** À appeler dans Application.onCreate() avec `File(cacheDir, "net-cache")`. */
    fun init(cacheDirectory: File) {
        directory = cacheDirectory.apply { mkdirs() }
    }

    /** Clé → nom de fichier sûr, sans séparateur (`DiskCache.swift:30-37`). */
    private fun fileFor(dir: File, key: String): File {
        val safe = Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray())
        return File(dir, "$safe.json")
    }

    /** Écrit une valeur, en écrasant la précédente. Échec silencieux. */
    fun <T> store(value: T, key: String, nowMillis: Long = System.currentTimeMillis()) {
        val dir = directory ?: return
        runCatching {
            fileFor(dir, key).writeText(gson.toJson(Envelope(storedAt = nowMillis, value = value)))
        }
    }

    /**
     * Lit une valeur si elle est présente **et plus jeune que [maxAgeMillis]**.
     * Retourne `null` sinon — fichier absent, illisible ou périmé.
     */
    fun <T> load(
        key: String,
        typeOfT: Type,
        maxAgeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): T? {
        val dir = directory ?: return null
        return runCatching {
            val file = fileFor(dir, key)
            if (!file.exists()) return null
            val envelopeType = TypeToken.getParameterized(Envelope::class.java, typeOfT).type
            val envelope = gson.fromJson<Envelope<T>>(file.readText(), envelopeType) ?: return null
            if (nowMillis - envelope.storedAt > maxAgeMillis) null else envelope.value
        }.getOrNull()
    }

    /** Supprime une entrée (invalidation ciblée). */
    fun remove(key: String) {
        val dir = directory ?: return
        runCatching { fileFor(dir, key).delete() }
    }

    /** Vide tout le cache disque JSON (`DiskCache.swift:60-67`). */
    fun clear() {
        val dir = directory ?: return
        runCatching {
            dir.deleteRecursively()
            dir.mkdirs()
        }
    }
}
