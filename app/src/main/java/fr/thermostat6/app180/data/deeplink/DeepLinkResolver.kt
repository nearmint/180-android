package fr.thermostat6.app180.data.deeplink

import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.ApiConfig
import fr.thermostat6.app180.data.push.NotificationDestination

/** Segment de chemin des permaliens de recette (`recipes_embed.json`, champ `link`). */
private const val RECIPE_PATH_SEGMENT = "recettes"

/**
 * Traduit un lien web du site en destination applicative.
 *
 * Un permalien de recette est de la forme
 * `https://www.180c.fr/recettes/<slug>/`. L'app navigue par **identifiant**, pas
 * par slug : la résolution passe donc par `wp/v2/recipe?slug=…`, qui rend au plus
 * un élément.
 *
 * Le parsing est volontairement séparé de l'appel réseau — c'est la partie qui
 * peut se tromper sur un lien tordu, et elle doit être vérifiable sans serveur.
 */
object DeepLinkResolver {

    /** Lien reconnu. Tout le reste reste l'affaire du navigateur. */
    internal sealed class Link {
        data class RecipeSlug(val slug: String) : Link()
    }

    /**
     * `null` si le lien n'est pas un permalien de recette du site : hôte tiers,
     * autre rubrique, slug vide. Rien n'est deviné.
     */
    internal fun parse(url: String): Link? {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return null

        val afterScheme = url.substringAfter("://")
        val host = afterScheme.substringBefore('/').substringBefore(':')
        if (!isSameSite(host)) return null

        val path = afterScheme
            .substringAfter('/', "")
            .substringBefore('?')
            .substringBefore('#')

        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.size < 2 || segments[0] != RECIPE_PATH_SEGMENT) return null

        return Link.RecipeSlug(segments[1])
    }

    /**
     * Résout le lien en destination.
     *
     * Repli sur [NotificationDestination.None] — donc le centre de notifications
     * — dès que la résolution échoue : c'est déjà la convention défensive du
     * routeur de notifications, et rouvrir le lien dans un navigateur risquerait
     * de relancer l'App Link en boucle.
     */
    suspend fun resolve(url: String): NotificationDestination {
        val link = parse(url) ?: return NotificationDestination.None

        return when (link) {
            is Link.RecipeSlug -> {
                val id = runCatching {
                    ApiClient.wordPressApi.getRecipes(perPage = 1, slug = link.slug)
                        .firstOrNull()?.id
                }.getOrNull()

                if (id != null && id > 0) NotificationDestination.Recipe(id)
                else NotificationDestination.None
            }
        }
    }

    /** Insensible à la casse et au préfixe `www.`, comme `NotificationRouter`. */
    private fun isSameSite(host: String): Boolean {
        val canonical = ApiConfig.ORIGIN.substringAfter("://").substringBefore('/')
        return host.lowercase().removePrefix("www.") == canonical.lowercase().removePrefix("www.")
    }
}
