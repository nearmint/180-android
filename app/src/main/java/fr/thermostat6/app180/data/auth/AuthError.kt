package fr.thermostat6.app180.data.auth

/**
 * Cause typée d'un échec de connexion.
 *
 * **Tous les messages sont définis côté app, en français** : aucun message
 * serveur brut (ex. « Wrong user credentials. ») ne doit jamais atteindre
 * l'utilisateur. Le message serveur et l'`errorCode` restent en log uniquement.
 *
 * Le type porte les deux dérivés de la cause — ce qui s'affiche et ce qui se
 * mesure — pour qu'ils ne puissent plus diverger : le point d'échec unique de
 * [AuthService.login] les lit tous les deux sur la même valeur.
 *
 * Hérite d'[Exception] afin d'être transporté tel quel par le `Result.failure`
 * de `login()`, dont la signature reste inchangée pour ses appelants.
 *
 * Miroir d'`AuthError` (`apple/180/AuthService.swift:516-561`). Le cas
 * `invalidURL` de l'iOS n'a pas d'équivalent ici : les URL sont construites par
 * Retrofit à la compilation, jamais depuis une chaîne au point d'appel.
 *
 * @property displayMessage message affiché à l'utilisateur
 *   (`AuthError.errorDescription`, `:536-545`).
 * @property analyticsReason libellé **générique** pour la mesure `login_error`
 *   (`AuthError.analyticsReason`, `:552-560`). Volontairement distinct du
 *   message : aucun texte d'interface ni serveur ne part dans les statistiques,
 *   seulement une cause stable et comparable entre plateformes.
 */
sealed class AuthError(
    val displayMessage: String,
    val analyticsReason: String
) : Exception(displayMessage) {

    /**
     * `success:false` + `errorCode: 48` — le serveur refuse réellement les
     * identifiants. **Seul** cas légitime de ce message.
     */
    data object InvalidCredentials : AuthError(
        displayMessage  = "Identifiants incorrects",
        analyticsReason = "invalid_credentials"
    )

    /**
     * `success:false` avec un autre `errorCode` — refus du plugin pour une
     * raison qui n'est pas une faute de saisie.
     */
    data object PluginRefused : AuthError(
        displayMessage  = "Erreur de connexion, réessayez plus tard.",
        analyticsReason = "plugin_refused"
    )

    /** Code HTTP hors 2xx sans refus structuré (5xx, maintenance, page HTML…). */
    data object HttpError : AuthError(
        displayMessage  = "Le service est momentanément indisponible.",
        analyticsReason = "http_error"
    )

    /** Erreur de transport : hors-ligne, timeout, DNS, TLS. */
    data object Transport : AuthError(
        displayMessage  = "Vérifiez votre connexion internet.",
        analyticsReason = "transport"
    )

    /** Réponse inattendue non exploitable (corps illisible, décodage impossible). */
    data object ServerError : AuthError(
        displayMessage  = "Le service est momentanément indisponible.",
        analyticsReason = "server_error"
    )
}
