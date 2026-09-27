package fr.thermostat6.app180.data.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

/**
 * Utilitaire de décodage du payload JWT (sans vérification de signature).
 * Le JWT WordPress simple-jwt-login contient email, username et exp en claims racine.
 *
 * Volontairement **sans aucune API du framework Android** : `android.util.Base64`
 * et `org.json.JSONObject` ne sont que des stubs sur le classpath des tests
 * unitaires JVM (`Method ... not mocked`), ce qui rendait le cycle de vie du
 * jeton — le cœur d'AND-05 — impossible à couvrir sans instrumentation.
 * `java.util.Base64` (JDK, API 26 = notre minSdk) et Gson (déjà dépendance de
 * l'app, Java pur) donnent le même résultat et s'exécutent en test JVM.
 */
object JwtUtils {

    data class JwtPayload(
        val email: String,
        val username: String,
        /**
         * Timestamp Unix (secondes) d'expiration.
         *
         * Vaut `0` quand le claim `exp` est absent ou illisible : un jeton sans
         * échéance est alors jugé **expiré** par [isExpired], donc purgé plutôt
         * que conservé indéfiniment. Miroir du `guard let exp else { return nil }`
         * de l'iOS (`apple/180/AuthService.swift:290-292`), dont le résultat est
         * le même — la session est purgée.
         */
        val exp: Long
    ) {
        /**
         * Retourne true si le token expire dans moins de [days] jours.
         * Seuil spec : 7 jours.
         */
        fun isExpiringSoon(days: Int = 7): Boolean {
            val nowSeconds = System.currentTimeMillis() / 1000L
            val thresholdSeconds = nowSeconds + days.toLong() * 24 * 60 * 60
            return exp < thresholdSeconds
        }

        /** Retourne true si le token est déjà expiré. */
        fun isExpired(): Boolean {
            val nowSeconds = System.currentTimeMillis() / 1000L
            return exp < nowSeconds
        }
    }

    /**
     * Décode la partie payload d'un JWT (Base64URL → JSON) et en extrait
     * email, username et exp.
     * Retourne null si le JWT est malformé ou le décodage échoue.
     */
    fun decodePayload(jwt: String): JwtPayload? {
        return try {
            val parts = jwt.split(".")
            if (parts.size != 3) return null

            // Base64URL → Base64 standard (+ padding). La conversion, plutôt que
            // le décodeur `getUrlDecoder()`, reproduit exactement l'iOS
            // (`AuthService.swift:277-284`) : un payload déjà en base64 standard
            // reste décodable, là où le décodeur URL le rejetterait.
            val base64 = parts[1]
                .replace('-', '+')
                .replace('_', '/')
                .let { padBase64(it) }

            val bytes = Base64.getDecoder().decode(base64)
            val json = JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject

            JwtPayload(
                email    = json.optString("email"),
                username = json.optString("username"),
                exp      = json.optLong("exp")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun padBase64(s: String): String {
        val pad = (4 - s.length % 4) % 4
        return s + "=".repeat(pad)
    }

    /** Chaîne du claim [key], `""` s'il est absent ou n'est pas une primitive. */
    private fun JsonObject.optString(key: String): String =
        runCatching { get(key)?.asString.orEmpty() }.getOrDefault("")

    /**
     * Entier du claim [key], `0` s'il est absent ou illisible.
     *
     * `exp` arrive en nombre (cas normal) ; le repli sur une chaîne numérique
     * reprend la précaution de l'iOS (`AuthService.swift:290-291`), déjà
     * appliquée à l'`errorCode` du login.
     */
    private fun JsonObject.optLong(key: String): Long =
        runCatching { get(key)?.asLong ?: 0L }
            .recoverCatching { get(key)?.asString?.toLongOrNull() ?: 0L }
            .getOrDefault(0L)
}
