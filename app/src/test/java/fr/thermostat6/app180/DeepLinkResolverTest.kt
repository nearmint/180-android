package fr.thermostat6.app180

import fr.thermostat6.app180.data.deeplink.DeepLinkResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reconnaissance des permaliens interceptés par les App Links.
 *
 * Un lien mal reconnu est plus grave qu'un lien non reconnu : le second retombe
 * dans le navigateur, le premier ouvre la mauvaise recette ou une page vide. Le
 * parsing refuse donc tout ce dont il n'est pas certain.
 */
class DeepLinkResolverTest {

    private fun slug(url: String): String? =
        (DeepLinkResolver.parse(url) as? DeepLinkResolver.Link.RecipeSlug)?.slug

    // ── Liens reconnus ───────────────────────────────────────────────────────

    @Test
    fun `un permalien de recette rend son slug`() {
        assertEquals(
            "pudding-aux-tomates-et-chorizo",
            slug("https://www.180c.fr/recettes/pudding-aux-tomates-et-chorizo/")
        )
    }

    @Test
    fun `la barre oblique finale est optionnelle`() {
        assertEquals("flan-aux-abricots", slug("https://www.180c.fr/recettes/flan-aux-abricots"))
    }

    @Test
    fun `l'apex est accepte au meme titre que www`() {
        assertEquals("flan-aux-abricots", slug("https://180c.fr/recettes/flan-aux-abricots/"))
    }

    @Test
    fun `la casse de l'hote n'a pas d'importance`() {
        assertEquals("flan-aux-abricots", slug("https://WWW.180C.FR/recettes/flan-aux-abricots/"))
    }

    @Test
    fun `parametres et ancre sont ignores`() {
        assertEquals(
            "flan-aux-abricots",
            slug("https://www.180c.fr/recettes/flan-aux-abricots/?utm_source=newsletter#ingredients")
        )
    }

    // ── Liens refusés ────────────────────────────────────────────────────────

    @Test
    fun `un autre domaine est refuse`() {
        // Le point décisif : un hôte qui *contient* le domaine ne l'est pas.
        assertNull(DeepLinkResolver.parse("https://www.180c.fr.evil.com/recettes/flan/"))
        assertNull(DeepLinkResolver.parse("https://example.com/recettes/flan/"))
    }

    @Test
    fun `un sous-domaine est refuse`() {
        assertNull(DeepLinkResolver.parse("https://boutique.180c.fr/recettes/flan/"))
    }

    @Test
    fun `une autre rubrique du site est refusee`() {
        assertNull(DeepLinkResolver.parse("https://www.180c.fr/boutique/abonnement/"))
        assertNull(DeepLinkResolver.parse("https://www.180c.fr/mon-compte/"))
    }

    @Test
    fun `la rubrique recettes sans slug est refusee`() {
        assertNull(DeepLinkResolver.parse("https://www.180c.fr/recettes/"))
        assertNull(DeepLinkResolver.parse("https://www.180c.fr/recettes"))
    }

    @Test
    fun `la racine et le permalien par identifiant ne sont pas interceptes`() {
        // `/?p=123` reste au navigateur : rien dans l'URL ne dit s'il s'agit
        // d'une recette ou d'un article, et deviner ouvrirait la mauvaise vue.
        assertNull(DeepLinkResolver.parse("https://www.180c.fr/"))
        assertNull(DeepLinkResolver.parse("https://www.180c.fr/?p=13400072"))
    }

    @Test
    fun `un schema non HTTP est refuse`() {
        assertNull(DeepLinkResolver.parse("app180://recettes/flan"))
        assertNull(DeepLinkResolver.parse("/recettes/flan"))
        assertNull(DeepLinkResolver.parse(""))
    }
}
