package fr.thermostat6.app180

import fr.thermostat6.app180.data.push.InAppMessageService
import fr.thermostat6.app180.data.push.NotificationDestination
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Traduction de l'`actionId` d'un bouton d'In-App Message en destination.
 *
 * Ce n'est pas un détail d'implémentation : l'`actionId` est saisi à la main
 * dans le dashboard OneSignal, hors de toute validation à la compilation. Une
 * forme mal reconnue n'échoue pas bruyamment — elle envoie l'utilisateur au
 * mauvais endroit, ou nulle part.
 *
 * Portage d'`apple/180Tests/InAppMessageActionTests.swift`.
 */
class InAppMessageActionTest {

    private fun destination(actionId: String?) =
        InAppMessageService.destinationFromActionId(actionId)

    // ── Forme abrégée ────────────────────────────────────────────────────────

    @Test
    fun `la forme abregee recipe cible la recette`() {
        assertEquals(NotificationDestination.Recipe(123), destination("recipe:123"))
    }

    @Test
    fun `la forme abregee article cible l'article`() {
        assertEquals(NotificationDestination.Article(456), destination("article:456"))
    }

    @Test
    fun `le type est insensible a la casse`() {
        assertEquals(NotificationDestination.Recipe(123), destination("Recipe:123"))
    }

    @Test
    fun `les espaces autour de l'actionId sont toleres`() {
        assertEquals(NotificationDestination.Recipe(123), destination("  recipe:123  "))
    }

    /**
     * Le découpage se fait sur le **premier** `:` : découper sur le dernier, ou
     * sur tous, amputerait le schéma de l'URL. Cet actionId contient aussi un
     * `=` — il ne doit pas pour autant être lu comme une forme requête.
     */
    @Test
    fun `une url abregee conserve son schema malgre le separateur`() {
        assertEquals(
            NotificationDestination.Url("https://www.180c.fr/boutique?ref=iam"),
            destination("url:https://www.180c.fr/boutique?ref=iam")
        )
    }

    // ── Forme requête ────────────────────────────────────────────────────────

    @Test
    fun `la forme requete cible la recette`() {
        assertEquals(NotificationDestination.Recipe(123), destination("type=recipe&id=123"))
    }

    /** Seule la forme requête peut transporter une URL percent-encodée. */
    @Test
    fun `la forme requete decode une url percent-encodee`() {
        assertEquals(
            NotificationDestination.Url("https://www.180c.fr/page?a=b"),
            destination("type=url&url=https%3A%2F%2Fwww.180c.fr%2Fpage%3Fa%3Db")
        )
    }

    /**
     * Divergence de plateforme couverte ici : `URLDecoder` aurait transformé le
     * `+` en espace, là où l'`URLComponents` de l'iOS le laisse intact.
     */
    @Test
    fun `un plus dans une url n'est pas transforme en espace`() {
        assertEquals(
            NotificationDestination.Url("https://www.180c.fr/a+b"),
            destination("type=url&url=https%3A%2F%2Fwww.180c.fr%2Fa+b")
        )
    }

    /** Le percent-décodage travaille en UTF-8 : « é » s'encode sur deux octets. */
    @Test
    fun `le percent-decodage restitue les caracteres accentues`() {
        assertEquals(
            NotificationDestination.Url("https://www.180c.fr/recette/crème"),
            destination("type=url&url=https%3A%2F%2Fwww.180c.fr%2Frecette%2Fcr%C3%A8me")
        )
    }

    @Test
    fun `un id non numerique en forme requete ne cible rien`() {
        assertEquals(NotificationDestination.None, destination("type=recipe&id=abc"))
    }

    // ── Dégradations ─────────────────────────────────────────────────────────

    @Test
    fun `un actionId absent ou vide ne cible rien`() {
        listOf(null, "", "   ").forEach { actionId ->
            assertEquals(
                "actionId=${actionId ?: "null"}",
                NotificationDestination.None,
                destination(actionId)
            )
        }
    }

    /**
     * Cas le plus courant en production : un bouton « Fermer » sans intention de
     * navigation. Il ne doit surtout pas router.
     */
    @Test
    fun `un actionId libre sans separateur ne cible rien`() {
        assertEquals(NotificationDestination.None, destination("fermer"))
    }

    @Test
    fun `un type inconnu ne cible rien`() {
        assertEquals(NotificationDestination.None, destination("podcast:12"))
        assertEquals(NotificationDestination.None, destination("type=podcast&id=12"))
    }

    @Test
    fun `un id nul ou negatif ne cible rien`() {
        assertEquals(NotificationDestination.None, destination("recipe:0"))
        assertEquals(NotificationDestination.None, destination("recipe:-3"))
    }

    // ── Produit ──────────────────────────────────────────────────────────────

    /**
     * Absent des tests iOS, qui écartent `product` parce que sa validation
     * dépend de l'`Info.plist` du bundle hôte. Sur Android `ApiConfig.ORIGIN`
     * est un `buildConfigField`, disponible en test JVM : on verrouille donc ici
     * que le type `product` reste conforme à l'exception Google Play — il ouvre
     * la **boutique**, en webview authentifiée, jamais un parcours d'achat
     * d'abonnement.
     */
    @Test
    fun `product route vers la boutique et normalise l'hote`() {
        assertEquals(
            NotificationDestination.Product(45, "https://www.180c.fr/boutique/"),
            destination("type=product&id=45&url=https%3A%2F%2F180c.fr%2Fboutique%2F")
        )
    }

    /** Hors domaine : rien ne s'ouvre, comme pour un push. */
    @Test
    fun `product hors domaine ne cible rien`() {
        assertEquals(
            NotificationDestination.None,
            destination("type=product&id=45&url=https%3A%2F%2Fevil.example%2Fboutique%2F")
        )
    }
}
