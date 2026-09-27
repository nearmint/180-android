package fr.thermostat6.app180.data.network

import fr.thermostat6.app180.data.model.AuthResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query

// Base URL : https://www.180c.fr/
//
// Credentials et JWT voyagent dans le **corps** `application/x-www-form-urlencoded`,
// jamais en query string : une URL est journalisée en clair par le serveur, les
// proxies et les WAF, ce qui y déposerait mots de passe et jetons. Seul
// `rest_route` — qui désigne l'endpoint, pas un secret — reste en @Query, le
// plugin Simple JWT Login le lisant depuis l'URL.
// Miroir iOS : AuthService.swift:84 (`["login": username, "password": password]`)
// et AuthService.swift:281 (`["JWT": oldToken]`), tous deux envoyés en httpBody
// avec Content-Type application/x-www-form-urlencoded.
interface AuthApi {

    // 5 — Login JWT
    // POST https://www.180c.fr/?rest_route=/simple-jwt-login/v1/auth
    // Corps : login=…&password=…
    @FormUrlEncoded
    @POST(".")
    suspend fun login(
        @Query("rest_route") restRoute: String = "/simple-jwt-login/v1/auth",
        @Field("login")      login: String,
        @Field("password")   password: String
    ): AuthResponse

    // 5 — Refresh JWT
    // POST https://www.180c.fr/?rest_route=/simple-jwt-login/v1/auth/refresh
    // Corps : JWT=…
    @FormUrlEncoded
    @POST(".")
    suspend fun refreshToken(
        @Query("rest_route") restRoute: String = "/simple-jwt-login/v1/auth/refresh",
        @Field("JWT")        jwt: String
    ): AuthResponse
}
