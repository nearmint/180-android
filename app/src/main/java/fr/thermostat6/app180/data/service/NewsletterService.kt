package fr.thermostat6.app180.data.service

import com.google.gson.Gson
import fr.thermostat6.app180.data.model.NewsletterAction
import fr.thermostat6.app180.data.model.NewsletterError
import fr.thermostat6.app180.data.model.NewsletterRequest
import fr.thermostat6.app180.data.model.NewsletterServerError
import fr.thermostat6.app180.data.model.NewsletterStatus
import fr.thermostat6.app180.data.network.ApiClient
import java.util.Locale

/**
 * Service newsletter — proxy serveur `180c/v1/newsletter/subscribe`.
 *
 * Miroir d'`apple/180/NewsletterService.swift:38-104`. L'app envoie
 * `{email, action}` + Bearer JWT ; le serveur vérifie la correspondance avec le
 * compte authentifié puis relaie côté Mailchimp.
 *
 * Remplace l'implémentation (corps `{email, consent, source, lang}`,
 * statut métier déduit du code HTTP) : le contrat réel porte le statut dans la
 * réponse et couvre les trois actions, pas seulement l'inscription.
 */
object NewsletterService {

    private val gson = Gson()

    /** Lecture du statut d'opt-in (lecture seule serveur). */
    suspend fun status(email: String): Result<NewsletterStatus> = call(NewsletterAction.STATUS, email)

    /** Inscription — le double opt-in serveur place un nouveau membre en `pending`. */
    suspend fun subscribe(email: String): Result<NewsletterStatus> = call(NewsletterAction.SUBSCRIBE, email)

    /** Désinscription. */
    suspend fun unsubscribe(email: String): Result<NewsletterStatus> = call(NewsletterAction.UNSUBSCRIBE, email)

    /**
     * Appel proxy authentifié. Renvoie le statut normalisé, ou un
     * [NewsletterError] portant le code de refus serveur
     * (`NewsletterService.swift:49-88`).
     */
    private suspend fun call(action: NewsletterAction, email: String): Result<NewsletterStatus> {
        if (ApiClient.authToken.isNullOrEmpty()) {
            return Result.failure(NewsletterException(NewsletterError(serverCode = "no_token")))
        }

        return try {
            val response = ApiClient.newsletterApi.call(
                NewsletterRequest(
                    email  = email.lowercase(Locale.ROOT),
                    action = action.value
                )
            )

            if (!response.isSuccessful) {
                val serverCode = runCatching {
                    gson.fromJson(response.errorBody()?.string(), NewsletterServerError::class.java)?.code
                }.getOrNull()
                return Result.failure(
                    NewsletterException(NewsletterError(response.code(), serverCode))
                )
            }

            val status = NewsletterStatus.fromValue(response.body()?.status)
                ?: return Result.failure(
                    NewsletterException(NewsletterError(response.code(), "decode_error"))
                )
            Result.success(status)
        } catch (_: Exception) {
            Result.failure(NewsletterException(NewsletterError(serverCode = "network")))
        }
    }
}

/** Porte un [NewsletterError] à travers un [Result]. */
class NewsletterException(val error: NewsletterError) : Exception(error.serverCode)
