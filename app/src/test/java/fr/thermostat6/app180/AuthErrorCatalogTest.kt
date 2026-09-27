package fr.thermostat6.app180

import fr.thermostat6.app180.data.auth.AuthError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Verrou de la table cause → (message affiché, `reason` mesuré).**
 *
 * Les deux colonnes servent des publics opposés et ne doivent jamais se
 * confondre : le message part à l'utilisateur, en français, défini par l'app ;
 * le `reason` part dans Umami, générique, et se compare à celui de l'iOS.
 * Les faire dériver d'une même valeur ne suffit pas à les protéger — c'est ce
 * test qui fige les chaînes.
 *
 * Référence : `AuthError` iOS (`apple/180/AuthService.swift:516-561`).
 */
class AuthErrorCatalogTest {

    /** Table de référence, recopiée de l'enum iOS — pas dérivée du code testé. */
    private val reference = listOf(
        Triple(AuthError.InvalidCredentials, "Identifiants incorrects", "invalid_credentials"),
        Triple(AuthError.PluginRefused, "Erreur de connexion, réessayez plus tard.", "plugin_refused"),
        Triple(AuthError.HttpError, "Le service est momentanément indisponible.", "http_error"),
        Triple(AuthError.Transport, "Vérifiez votre connexion internet.", "transport"),
        Triple(AuthError.ServerError, "Le service est momentanément indisponible.", "server_error")
    )

    @Test
    fun `chaque cause porte le message iOS et le reason iOS`() {
        reference.forEach { (error, message, reason) ->
            assertEquals(message, error.displayMessage)
            assertEquals(reason, error.analyticsReason)
        }
    }

    /**
     * Une cause ajoutée sans entrée dans la table ci-dessus passerait inaperçue
     * et pourrait afficher n'importe quoi : le compte des sous-types scellés la
     * fait échouer ici.
     *
     * Réflexion Java, et non `sealedSubclasses` : cette dernière exige
     * `kotlin-reflect`, absent du classpath — et qui ne mérite pas d'y être
     * ajouté pour un décompte.
     */
    @Test
    fun `aucune cause hors table`() {
        val causes = AuthError::class.java.declaredClasses
            .filter { AuthError::class.java.isAssignableFrom(it) }

        assertEquals(causes.map { it.simpleName }.sorted().toString(), reference.size, causes.size)
    }

    /**
     * Régression AND-07 : « Identifiants incorrects » n'est légitime que pour un
     * refus explicite du serveur. C'est précisément l'affichage systématique de
     * ce message qui envoyait, en mode avion, retaper un mot de passe valable.
     */
    @Test
    fun `seul un refus d identifiants parle d identifiants`() {
        reference
            .filterNot { (error, _, _) -> error == AuthError.InvalidCredentials }
            .forEach { (error, _, _) ->
                assertFalse(
                    "${error.analyticsReason} ne doit pas parler d'identifiants",
                    error.displayMessage.contains("dentifiants")
                )
            }
    }

    /** Aucun message technique ni anglais ne doit atteindre l'utilisateur. */
    @Test
    fun `les messages affiches sont en francais et sans detail technique`() {
        val interdits = listOf("Erreur serveur (", "HTTP", "JWT", "null", "credentials", "Wrong")

        reference.forEach { (error, _, _) ->
            assertTrue(error.displayMessage.isNotBlank())
            interdits.forEach { motif ->
                assertFalse(
                    "« ${error.displayMessage} » ne doit pas contenir « $motif »",
                    error.displayMessage.contains(motif)
                )
            }
        }
    }

    /** Le `reason` reste un identifiant stable, jamais un texte d'interface. */
    @Test
    fun `les reasons sont des identifiants stables`() {
        reference.forEach { (error, _, _) ->
            assertTrue(
                "« ${error.analyticsReason} » doit rester en snake_case ASCII",
                error.analyticsReason.matches(Regex("[a-z_]+"))
            )
            assertFalse(error.analyticsReason == error.displayMessage)
        }
    }
}
