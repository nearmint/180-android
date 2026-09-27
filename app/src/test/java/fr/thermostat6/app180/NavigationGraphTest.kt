package fr.thermostat6.app180

import fr.thermostat6.app180.navigation.Screen
import fr.thermostat6.app180.navigation.TabRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verrou sur les **templates de routes** et sur les routes concrètes qu'ils
 * engendrent.
 *
 * Le lot dédié corrige deux crashs de même nature : une route demandée à un
 * NavHost qui ne la déclare pas (`IllegalArgumentException`). Le graphe Compose
 * lui-même n'est pas atteignable depuis un test JVM pur — ni Robolectric ni
 * `androidx.navigation.testing` ne sont au classpath de `testDebugUnitTest`.
 * Ce qui reste testable est le vecteur de rechute réel : un template renommé
 * d'un côté et pas de l'autre.
 *
 * Deux `createRoute` sont volontairement hors périmètre : `Screen.Web` et
 * `Screen.RecipeList` appellent `android.net.Uri.encode`, qui n'est pas
 * implémenté dans le `android.jar` de test — leur template reste vérifié.
 */
class NavigationGraphTest {

    // ── Stabilité des templates ──────────────────────────────────────────────

    @Test
    fun `templates des onglets inchanges`() {
        assertEquals("home", Screen.Home.route)
        assertEquals("search", Screen.Search.route)
        assertEquals("favorites", Screen.Favorites.route)
        assertEquals("account", Screen.Account.route)
    }

    @Test
    fun `templates des ecrans secondaires inchanges`() {
        assertEquals("splash", Screen.Splash.route)
        assertEquals("onboarding", Screen.Onboarding.route)
        assertEquals("notifications", Screen.Notifications.route)
        assertEquals("force_update/{message}", Screen.ForceUpdate.route)
        assertEquals("recipe/{recipeId}", Screen.RecipeDetail.route)
        assertEquals("web/{url}?title={title}&fallback={fallback}", Screen.Web.route)
        assertEquals(
            "recipe_list/{title}?categorySlug={categorySlug}&seasonSlug={seasonSlug}" +
                "&publicationSlug={publicationSlug}&showFilters={showFilters}",
            Screen.RecipeList.route
        )
    }

    // ── Cohérence route construite ↔ template ────────────────────────────────

    @Test
    fun `createRoute de RecipeDetail respecte son propre template`() {
        assertEquals("recipe/42", Screen.RecipeDetail.createRoute(42))
        assertTrue(
            "La route construite doit correspondre au template déclaré",
            Screen.RecipeDetail.createRoute(42) matches templateRegex(Screen.RecipeDetail.route)
        )
    }

    /**
     * Traduit un template de route en expression régulière : chaque `{arg}`
     * devient un segment quelconque sans `/`. C'est, en simplifié, ce que fait
     * `NavDestination.matchDeepLink` pour décider si une route est servie par
     * une destination — donc ce qui sépare une navigation d'un crash.
     */
    private fun templateRegex(template: String): Regex {
        val placeholder = Regex("""\{[^}]+}""")
        var cursor = 0
        val pattern = buildString {
            placeholder.findAll(template).forEach { match ->
                append(Regex.escape(template.substring(cursor, match.range.first)))
                append("[^/]+")
                cursor = match.range.last + 1
            }
            append(Regex.escape(template.substring(cursor)))
        }
        return Regex(pattern)
    }

    // ── Indices d'onglets ────────────────────────────────────────────────────

    /**
     * `TabRouter` adresse les onglets par **index**, pas par route : le rail
     * Carnet de l'accueil (AND-01) et le bandeau hors ligne demandent tous deux
     * l'index 2. Réordonner `BOTTOM_TABS` sans toucher ces constantes enverrait
     * silencieusement l'utilisateur sur le mauvais onglet — un crash en moins,
     * mais une mauvaise destination à la place.
     */
    @Test
    fun `indices d'onglets alignes sur l'ordre de la barre`() {
        assertEquals(0, TabRouter.Tab.HOME)
        assertEquals(1, TabRouter.Tab.SEARCH)
        assertEquals(2, TabRouter.Tab.FAVORITES)
        assertEquals(3, TabRouter.Tab.ACCOUNT)
    }
}
