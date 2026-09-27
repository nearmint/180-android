package fr.thermostat6.app180.data.network

import fr.thermostat6.app180.data.model.AppVersionInfo
import retrofit2.http.GET
import retrofit2.http.Headers

// Base URL : ApiConfig.APP_VERSION_BASE_URL (origine canonique, https://www.180c.fr/)
// Timeout 5 s + Cache-Control: no-cache (configurés dans ApiClient.appVersionHttpClient)
interface AppVersionApi {

    // 9 — Vérification de la version minimale.
    // Endpoint REST `180c/v1`, qui remplace l'ancien fichier statique
    // `/app-version.json`. Miroir iOS : `"\(APIConfig.shared.restV1)/app-version"`
    // — apple/180/AppVersionService.swift:24, avec `Cache-Control: no-cache`
    // posé ligne 33 et un timeout de 5 s ligne 32.
    @Headers("Cache-Control: no-cache")
    @GET("wp-json/180c/v1/app-version")
    suspend fun getAppVersionInfo(): AppVersionInfo
}
