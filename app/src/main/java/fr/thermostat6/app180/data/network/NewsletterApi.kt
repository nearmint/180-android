package fr.thermostat6.app180.data.network

import fr.thermostat6.app180.data.model.NewsletterRequest
import fr.thermostat6.app180.data.model.NewsletterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// Base URL : ApiConfig.NEWSLETTER_BASE_URL (origine canonique, https://www.180c.fr/)
// Auth : Bearer JWT injecté par authenticatedHttpClient. Le proxy **exige** un
//        compte authentifié : il compare l'e-mail envoyé au `user_email` du
//        porteur du jeton (refus `email_mismatch` sinon).
interface NewsletterApi {

    // Proxy WP unique pour les trois actions (subscribe / unsubscribe / status).
    // Miroir iOS : `"\(APIConfig.shared.restV1)/newsletter/subscribe"`
    // — apple/180/NewsletterService.swift:41.
    @POST("wp-json/180c/v1/newsletter/subscribe")
    suspend fun call(@Body req: NewsletterRequest): Response<NewsletterResponse>
}
