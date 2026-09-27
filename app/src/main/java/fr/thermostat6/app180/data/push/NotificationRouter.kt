package fr.thermostat6.app180.data.push

import fr.thermostat6.app180.data.model.NotificationTarget
import fr.thermostat6.app180.data.network.ApiConfig

/**
 * Destination d'un tap de notification — push **ou** ligne du centre.
 *
 * Miroir de `NotificationDestination` (`apple/180/Navigation/NotificationRouter.swift:17-30`).
 */
sealed class NotificationDestination {
    data class Recipe(val id: Int) : NotificationDestination()
    data class Article(val id: Int) : NotificationDestination()

    /** Lien quelconque, potentiellement hors domaine — ouverture publique. */
    data class Url(val url: String) : NotificationDestination()

    /**
     * Page produit à ouvrir en **webview authentifiée** (amorçage `WebSession`).
     * Distinct d'[Url] : l'URL est garantie sur le domaine du site et normalisée
     * sur l'hôte canonique.
     */
    data class Product(val id: Int, val url: String) : NotificationDestination()

    /** Aucune cible exploitable → ouverture du centre de notifications. */
    data object None : NotificationDestination()
}

/**
 * Décide la destination d'un tap.
 *
 * **Fonction de parsing unique**, alimentée par deux adaptateurs (payload d'un
 * push / objet `target` du centre) — pas deux chemins de décision qui
 * divergeraient avec le temps.
 *
 * Miroir de `NotificationRouter` (`apple/180/Navigation/NotificationRouter.swift`).
 */
object NotificationRouter {

    /** Décision à partir des champs bruts. */
    fun destination(type: String?, id: Int, url: String?): NotificationDestination =
        when (type) {
            "recipe"  -> if (id > 0) NotificationDestination.Recipe(id) else NotificationDestination.None
            "article" -> if (id > 0) NotificationDestination.Article(id) else NotificationDestination.None
            "url"     -> url?.takeIf { it.isNotBlank() }
                ?.let { NotificationDestination.Url(it) } ?: NotificationDestination.None
            "product" -> productDestination(id, url)
            else      -> NotificationDestination.None
        }

    /** Adaptateur : objet `target` typé du centre. */
    fun destination(target: NotificationTarget?): NotificationDestination =
        if (target == null) NotificationDestination.None
        else destination(target.type, target.id, target.url)

    /**
     * Adaptateur : données additionnelles d'un push, plates ou imbriquées sous
     * `target` / `custom_data` selon le mapping OneSignal
     * (`NotificationRouter.swift:normalize`).
     */
    fun destinationFromPush(data: Map<String, Any?>?): NotificationDestination {
        val dict = normalize(data)
        return destination(
            type = dict["type"] as? String,
            id   = intValue(dict["id"]) ?: 0,
            url  = dict["url"] as? String
        )
    }

    /**
     * Destination produit **défensive** : l'URL doit être présente, absolue et
     * **dans le domaine du site** — sinon [NotificationDestination.None], repli
     * sur le centre plutôt qu'une ouverture hasardeuse.
     *
     * L'hôte est forcé sur le canonique : une URL apex verrait sinon son POST
     * d'amorçage dégradé par le 301 apex→www, vidant le jeton du corps
     * (`NotificationRouter.swift:productDestination`).
     */
    private fun productDestination(id: Int, url: String?): NotificationDestination {
        val raw = url?.takeIf { it.isNotBlank() } ?: return NotificationDestination.None
        val host = hostOf(raw) ?: return NotificationDestination.None
        val canonicalHost = hostOf(ApiConfig.ORIGIN) ?: return NotificationDestination.None
        if (!isSameSite(host, canonicalHost)) return NotificationDestination.None

        val afterScheme = raw.substringAfter("://")
        val pathAndBeyond = if (afterScheme.contains('/')) afterScheme.substringAfter('/') else ""
        return NotificationDestination.Product(id, "${ApiConfig.ORIGIN}/$pathAndBeyond")
    }

    /**
     * Deux hôtes appartiennent-ils au même site ? Comparaison insensible à la
     * casse et au préfixe `www.` ; tout autre hôte (sous-domaine, tiers) est
     * rejeté (`NotificationRouter.swift:isSameSite`).
     */
    private fun isSameSite(a: String, b: String): Boolean = core(a) == core(b)

    private fun core(host: String): String = host.lowercase().removePrefix("www.")

    private fun hostOf(url: String): String? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return url.substringAfter("://").substringBefore('/').substringBefore(':')
            .takeIf { it.isNotBlank() }
    }

    private fun normalize(data: Map<String, Any?>?): Map<String, Any?> {
        if (data == null) return emptyMap()
        @Suppress("UNCHECKED_CAST")
        (data["target"] as? Map<String, Any?>)?.let { return it }
        @Suppress("UNCHECKED_CAST")
        (data["custom_data"] as? Map<String, Any?>)?.let { return it }
        return data
    }

    private fun intValue(any: Any?): Int? = when (any) {
        is Int    -> any
        is Number -> any.toInt()
        is String -> any.toIntOrNull()
        else      -> null
    }
}
