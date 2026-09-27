package fr.thermostat6.app180.data.network

import fr.thermostat6.app180.BuildConfig

/**
 * Source unique des URLs du backend (miroir Android d'`APIConfig.swift` côté iOS).
 *
 * L'origine provient EXCLUSIVEMENT du `buildConfigField` `API_ORIGIN` déclaré dans
 * `app/build.gradle.kts` : aucun littéral d'hôte ne doit subsister ailleurs dans
 * l'app. Elle est fixée sur le domaine **canonique** `www.180c.fr`.
 *
 * Pourquoi jamais l'apex `180c.fr` : il répond 301 vers `www`, et une redirection
 * rejoue une requête POST en GET — le corps (identifiants de login, payload
 * d'inscription newsletter) est alors perdu silencieusement et le serveur refuse.
 * Le garde-fou d'`ApiClient` refuse désormais toute redirection, mais la première
 * ligne de défense reste de viser directement le bon hôte.
 */
object ApiConfig {

    /** Origine canonique, sans slash final (ex. `https://www.180c.fr`). */
    val ORIGIN: String = BuildConfig.API_ORIGIN.trimEnd('/')

    // ── Bases Retrofit (slash final obligatoire) ─────────────────────────────
    // Les chemins sont conservés à l'identique : seul l'hôte change.

    /** REST cœur WordPress. */
    val WP_BASE_URL: String = "$ORIGIN/wp-json/wp/v2/"

    /** Racine du site — les routes JWT passent par `?rest_route=`. */
    val AUTH_BASE_URL: String = "$ORIGIN/"

    /** Racine du site — proxy WP `/wp-json/180c/v1/newsletter/subscribe`. */
    val NEWSLETTER_BASE_URL: String = "$ORIGIN/"

    /** Racine du site — endpoint `180c/v1/app-version` (client à timeout court). */
    val APP_VERSION_BASE_URL: String = "$ORIGIN/"

    /**
     * Racine du site — namespace REST custom `180c/v1` (statut abonné `/me`).
     * Miroir d'`APIConfig.restV1` côté iOS (`apple/180/APIConfig.swift:69`).
     */
    val REST_V1_BASE_URL: String = "$ORIGIN/"

    // ── Liens web (navigateur) ───────────────────────────────────────────────

    /** Construit un lien web absolu vers le site (miroir d'`APIConfig.webLink`). */
    fun webLink(path: String): String {
        val normalized = if (path.startsWith("/")) path else "/$path"
        return "$ORIGIN$normalized"
    }

    /** Lien de partage d'une recette, par identifiant de post WordPress. */
    fun shareUrl(postId: Int): String = webLink("/?p=$postId")
}
