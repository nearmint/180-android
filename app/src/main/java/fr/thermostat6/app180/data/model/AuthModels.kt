package fr.thermostat6.app180.data.model

import com.google.gson.annotations.SerializedName

data class AuthResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: AuthData?
)

data class AuthData(
    @SerializedName("jwt")     val jwt: String?,
    @SerializedName("message") val message: String?,
    /**
     * Code de refus du plugin JWT. Seule source de vérité pour distinguer de
     * mauvais identifiants (`48`) d'un refus pour une autre raison — le code
     * HTTP ne le permet pas (`apple/180/AuthService.swift:151-158`).
     *
     * Mesuré, jamais affiché : le message servi par le serveur ne doit pas
     * atteindre l'utilisateur.
     */
    @SerializedName("errorCode") val errorCode: Int? = null
)

data class UserProfile(
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name")  val lastName: String,
    @SerializedName("email")      val email: String
)
