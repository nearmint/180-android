package fr.thermostat6.app180.data.model

import com.google.gson.annotations.SerializedName

/**
 * Contrat du proxy newsletter `180c/v1/newsletter/subscribe` (contrat apps).
 *
 * La clé d'e-mailing ne quitte jamais le serveur : l'app envoie `{email, action}`
 * + Bearer JWT, le serveur vérifie que l'e-mail correspond au compte authentifié
 * puis relaie côté Mailchimp.
 *
 * Miroir d'`apple/180/NewsletterService.swift:4-124`.
 */

/** Actions acceptées par le proxy (`NewsletterService.swift:43-45`). */
enum class NewsletterAction(val value: String) {
    SUBSCRIBE("subscribe"),
    UNSUBSCRIBE("unsubscribe"),
    STATUS("status")
}

/** Statut d'opt-in normalisé renvoyé par le proxy (`NewsletterService.swift:5-14`). */
enum class NewsletterStatus(val value: String) {
    SUBSCRIBED("subscribed"),
    UNSUBSCRIBED("unsubscribed"),
    PENDING("pending");

    /**
     * `PENDING` compte comme inscrit : le double opt-in place tout membre en
     * attente tant que le lien de confirmation n'est pas cliqué — c'est un
     * opt-in réel, pas une absence d'inscription (`NewsletterService.swift:10-13`).
     */
    val isSubscribed: Boolean get() = this == SUBSCRIBED || this == PENDING

    companion object {
        fun fromValue(value: String?): NewsletterStatus? =
            entries.firstOrNull { it.value == value }
    }
}

/**
 * Corps de requête : `{email, action}`.
 *
 * **`list_id` volontairement OMIS** — le serveur applique alors son audience
 * unique. Embarquer l'ID d'audience dans l'app était fragile : une divergence
 * avec la prod faisait rejeter **toutes** les requêtes en `invalid_list`
 * (`NewsletterService.swift:32-37`).
 */
data class NewsletterRequest(
    @SerializedName("email")  val email: String,
    @SerializedName("action") val action: String
)

/** Réponse du proxy : `{list_id, action, status}` (`NewsletterService.swift:113-124`). */
data class NewsletterResponse(
    @SerializedName("list_id") val listId: String? = null,
    @SerializedName("action")  val action: String? = null,
    @SerializedName("status")  val status: String? = null
)

/** Enveloppe d'erreur REST WordPress : `{"code":"email_mismatch",…}`. */
data class NewsletterServerError(
    @SerializedName("code") val code: String? = null
)

/**
 * Échec d'un appel newsletter, porteur du diagnostic serveur
 * (`NewsletterService.swift:16-22`).
 *
 * [serverCode] nomme la cause : `email_mismatch`, `invalid_email`,
 * `invalid_list`, `rate_limited`, `mailchimp_*`, `jwt_*`…
 */
data class NewsletterError(
    val httpCode: Int? = null,
    val serverCode: String? = null
)
