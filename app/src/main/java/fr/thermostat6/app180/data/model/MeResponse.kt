package fr.thermostat6.app180.data.model

import com.google.gson.annotations.SerializedName

/**
 * Réponse de `GET /wp-json/180c/v1/me` — statut abonné faisant autorité.
 *
 * Miroir exact du décodage iOS `apple/180/AuthService.swift:410-424` :
 * `is_subscriber`, `email`, `display_name`, `id`.
 *
 * Nullabilité prudente : chaque champ peut manquer si le thème ne le sert pas
 * encore (le Swift déclare déjà `id` optionnel, `AuthService.swift:414-416`).
 * [isSubscriber] retombe sur **non-abonné**, jamais l'inverse — un champ absent
 * ne doit pas déverrouiller du contenu premium.
 */
data class MeResponse(
    @SerializedName("is_subscriber") private val isSubscriberRaw: Boolean? = null,
    @SerializedName("email")         private val emailRaw: String? = null,
    @SerializedName("display_name")  val displayName: String? = null,
    /** ID utilisateur WordPress ; absent tant que le thème ne l'ajoute pas. */
    @SerializedName("id")            val id: Int? = null
) {
    /** Statut abonné, non-abonné par défaut. */
    val isSubscriber: Boolean get() = isSubscriberRaw ?: false

    /** E-mail du compte (`user_email` côté serveur), chaîne vide si absent. */
    val email: String get() = emailRaw.orEmpty()
}
