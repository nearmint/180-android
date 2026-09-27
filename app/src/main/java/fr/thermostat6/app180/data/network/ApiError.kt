package fr.thermostat6.app180.data.network

import com.google.gson.JsonParseException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Erreur d'API typée — miroir de l'`enum APIError` iOS
 * (`apple/180/Networking.swift:92-145`).
 *
 * Distinguer hors-ligne / délai dépassé / HTTP / décodage permet un message
 * pertinent pour l'utilisateur et un retry ciblé : rejouer une requête n'a de
 * sens que sur une panne transitoire.
 *
 * **Tous les messages sont définis côté app, en français** — aucun message
 * serveur brut n'atteint l'utilisateur.
 */
sealed class ApiError : Exception() {

    /** Pas de connexion Internet (`Networking.swift:94`). */
    data object Offline : ApiError()

    /** Délai dépassé (`Networking.swift:95`). */
    data object Timeout : ApiError()

    /** Réponse HTTP non 2xx, code porté (`Networking.swift:96`). */
    data class Http(val code: Int) : ApiError()

    /** Panne serveur non qualifiée (`Networking.swift:97`). */
    data object ServerError : ApiError()

    /** Réponse illisible (`Networking.swift:98`). */
    data object DecodingError : ApiError()

    /**
     * Description technique — logs et diagnostic.
     * Chaînes recopiées d'`errorDescription` (`Networking.swift:100-109`).
     */
    val description: String
        get() = when (this) {
            Offline       -> "Pas de connexion Internet"
            Timeout       -> "La requête a expiré"
            is Http       -> "Erreur serveur ($code)"
            ServerError   -> "Erreur serveur"
            DecodingError -> "Erreur de décodage"
        }

    /**
     * Message court orienté utilisateur (états d'erreur, toasts).
     * Chaînes recopiées d'`userMessage` (`Networking.swift:111-118`).
     */
    val userMessage: String
        get() = when (this) {
            Offline -> "Pas de connexion Internet. Vérifiez votre réseau."
            Timeout -> "La connexion est trop lente. Réessayez."
            else    -> "Impossible de charger les données. Réessayez plus tard."
        }

    /**
     * Erreur transitoire → un retry léger peut réussir (GET idempotents).
     * Miroir d'`isTransient` (`Networking.swift:120-127`) : hors-ligne et délai
     * dépassé toujours, HTTP seulement à partir de 500. Un 4xx est un refus
     * durable : le rejouer ne ferait que retarder l'erreur.
     */
    val isTransient: Boolean
        get() = when (this) {
            Offline, Timeout -> true
            is Http          -> code >= 500
            else             -> false
        }

    override val message: String get() = description

    companion object {
        /**
         * Normalise n'importe quelle erreur réseau ou de décodage en [ApiError].
         * Miroir de `APIError.from(_:)` (`Networking.swift:129-145`).
         *
         * La `IOException` levée par le garde-fou anti-redirection du LOT-01 se
         * classe en [ServerError], **pas** en [Offline] : elle n'est pas
         * transitoire et ne doit donc jamais être rejouée.
         */
        fun from(throwable: Throwable): ApiError = when (throwable) {
            is ApiError                -> throwable
            is JsonParseException      -> DecodingError
            is SocketTimeoutException  -> Timeout
            is UnknownHostException    -> Offline
            is HttpException           -> Http(throwable.code())
            is IOException             -> fromIoException(throwable)
            else                       -> ServerError
        }

        /**
         * Une `IOException` couvre aussi bien une coupure réseau que le refus de
         * redirection du LOT-01. On ne la classe hors-ligne que sur les causes de
         * connectivité effectives, miroir de la liste `URLError` du Swift
         * (`Networking.swift:137-138` : `notConnectedToInternet`,
         * `networkConnectionLost`, `dataNotAllowed`, `cannotConnectToHost`,
         * `cannotFindHost`).
         */
        private fun fromIoException(error: IOException): ApiError {
            val text = (error.message ?: "").lowercase()
            val looksOffline = OFFLINE_MARKERS.any { it in text }
            return if (looksOffline) Offline else ServerError
        }

        private val OFFLINE_MARKERS = listOf(
            "unable to resolve host",
            "failed to connect",
            "network is unreachable",
            "no route to host",
            "connection reset",
            "connection refused",
            "software caused connection abort"
        )
    }
}
