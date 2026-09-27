package fr.thermostat6.app180.data.model

import com.google.gson.annotations.SerializedName

/**
 * Élément du centre de notifications (`GET 180c/v1/notifications`).
 *
 * Miroir d'`AppNotification` (`apple/180/Models/AppNotification.swift:12-32`),
 * champs prouvés par la fixture `notifications.json` (capture anonyme réelle).
 *
 * [isRead] est un **état local**, absent du flux serveur (`AppNotification.swift:19-20`).
 */
data class AppNotification(
    @SerializedName("id")        val id: Int = 0,
    @SerializedName("title")     private val titleRaw: String? = null,
    @SerializedName("body")      private val bodyRaw: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("target")    val target: NotificationTarget? = null,
    @SerializedName("sent_at")   val sentAt: String? = null
) {
    val title: String get() = titleRaw.orEmpty()
    val body: String get() = bodyRaw.orEmpty()

    /** Un élément sans identifiant n'est pas exploitable. */
    val isValid: Boolean get() = id > 0
}

/** Cible d'une notification (`AppNotification.swift:22-26`). */
data class NotificationTarget(
    @SerializedName("type") private val typeRaw: String? = null,
    @SerializedName("id")   val id: Int = 0,
    @SerializedName("url")  val url: String? = null
) {
    /** `recipe` · `article` · `url` · `product` · `none`. */
    val type: String get() = typeRaw.orEmpty()
}
