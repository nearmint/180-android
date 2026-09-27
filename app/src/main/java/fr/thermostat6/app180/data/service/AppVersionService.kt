package fr.thermostat6.app180.data.service

import android.content.Context
import fr.thermostat6.app180.data.network.ApiClient

/**
 * Vérifie si la version installée est toujours supportée.
 *
 * Miroir d'`AppVersionService.checkMinimumVersion` (`apple/180/AppVersionService.swift:29-45`) :
 * lecture de `android.min` sur `180c/v1/app-version`, comparaison sémantique, et
 * **fail-open strict** — réseau injoignable, JSON invalide, champ absent ou
 * version illisible laissent l'app démarrer normalement. Bloquer le démarrage
 * sur une donnée douteuse coûterait plus cher que de laisser passer une version
 * périmée une session de plus.
 *
 * La version installée est lue via PackageManager pour éviter BuildConfig.
 */
object AppVersionService {

    /**
     * Message affiché quand la version installée est trop ancienne.
     *
     * L'endpoint ne le fournit pas : il est défini côté app, comme sur iOS
     * (`AppVersionService.swift:26-27`, chaîne recopiée à l'identique).
     */
    const val FORCE_UPDATE_MESSAGE =
        "Une mise à jour est requise pour continuer à utiliser l'app."

    /**
     * Retourne le message de mise à jour forcée si la version est trop ancienne,
     * `null` sinon — y compris sur toute erreur (fail-open).
     */
    suspend fun checkMinimumVersion(context: Context): String? {
        return try {
            val minimum = ApiClient.appVersionApi.getAppVersionInfo().android?.min
                ?: return null
            val currentVersion = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: return null

            if (isVersionLessThan(currentVersion, minimum)) FORCE_UPDATE_MESSAGE else null
        } catch (_: Exception) {
            null // fail-open : on laisse passer si le serveur est inaccessible
        }
    }

    /**
     * Comparaison **sémantique** de deux versions « X.Y.Z » : `true` si [current]
     * est strictement inférieure à [minimum].
     *
     * Segment à segment sur des entiers — `1.0.9 < 1.0.10`, là où une comparaison
     * lexicographique conclurait l'inverse. Les segments absents valent 0
     * (`1.0` == `1.0.0`) et les segments non numériques sont écartés, comme le
     * `compactMap { Int($0) }` du Swift (`AppVersionService.swift:47-57`).
     */
    fun isVersionLessThan(current: String, minimum: String): Boolean {
        val cur = current.split(".").mapNotNull { it.toIntOrNull() }
        val min = minimum.split(".").mapNotNull { it.toIntOrNull() }
        val len = maxOf(cur.size, min.size)
        for (i in 0 until len) {
            val c = cur.getOrElse(i) { 0 }
            val m = min.getOrElse(i) { 0 }
            if (c < m) return true
            if (c > m) return false
        }
        return false
    }
}
