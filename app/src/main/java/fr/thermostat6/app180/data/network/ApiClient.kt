package fr.thermostat6.app180.data.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import fr.thermostat6.app180.BuildConfig
import fr.thermostat6.app180.data.model.HomePayload
import fr.thermostat6.app180.data.model.HomePayloadDeserializer
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    // Toutes les bases dérivent d'ApiConfig — aucun littéral d'URL ici.

    // Timeouts communs. Sans borne explicite, OkHttp laisse pendre une requête
    // (readTimeout 10 s par défaut mais aucun plafond global), ce qui fige les
    // écrans de chargement hors couverture. Miroir des bornes iOS de
    // `Networking.swift` : 15 s par requête, 30 s pour la ressource complète.
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS    = 15L
    private const val CALL_TIMEOUT_SECONDS    = 30L

    // Vérification de version : doit échouer vite pour ne pas retarder le splash.
    private const val APP_VERSION_TIMEOUT_SECONDS      = 5L
    private const val APP_VERSION_CALL_TIMEOUT_SECONDS = 10L

    /**
     * Taille du cache HTTP disque — 128 Mo, miroir de l'`URLCache` iOS
     * (`apple/180/Networking.swift:20-23`).
     */
    private const val HTTP_CACHE_SIZE_BYTES = 128L * 1024L * 1024L

    // Token JWT courant — à renseigner depuis AuthService après login / refresh
    @Volatile var authToken: String? = null

    /**
     * Cache HTTP **protocolaire** : c'est le serveur qui décide, via ses en-têtes
     * `Cache-Control`/`ETag`, de ce qui est réutilisable et pour combien de temps
     * — équivalent du `useProtocolCachePolicy` iOS (`Networking.swift:24`).
     *
     * Distinct du cache applicatif de l'accueil ([DiskCache], TTL fixe de 15 min) :
     * celui-ci profite à toutes les lectures REST portant des en-têtes de cache.
     *
     * Doit être posé **avant** toute utilisation des clients, donc en tout premier
     * dans `Application.onCreate()`.
     */
    @Volatile private var httpCache: Cache? = null

    /** À appeler en premier dans Application.onCreate(). */
    fun initCache(cacheDirectory: File) {
        if (httpCache == null) {
            httpCache = Cache(cacheDirectory, HTTP_CACHE_SIZE_BYTES)
        }
    }

    // ── Logging ─────────────────────────────────────────────────────────────

    /**
     * Journal HTTP **muet en release**.
     *
     * En `Level.BODY` l'intercepteur écrit dans logcat l'URL complète, tous les
     * en-têtes et le corps de chaque échange — soit le Bearer JWT et les réponses
     * de profil utilisateur. Logcat étant lisible par `adb` sur un appareil de
     * production, ces traces ne doivent exister qu'en build debug.
     *
     * `redactHeader` s'applique dans **tous les cas**, debug compris : même sur un
     * poste de dev, un jeton copié depuis un log finit par circuler.
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("Authorization")
    }

    // ── Garde-fou anti-redirection ──────────────────────────────────────────

    /**
     * Refuse toute réponse 3xx et la transforme en échec explicite.
     *
     * Miroir de `AppSessionDelegate` côté iOS (`Networking.swift`) : par défaut un
     * client HTTP suit un 301/302 et **rejoue la requête en GET**, perdant le corps
     * — donc les identifiants d'un login ou le payload d'une inscription
     * newsletter. L'échec se présente alors comme un refus d'authentification
     * trompeur. Ici la redirection est refusée et le message nomme la cause, le
     * verbe, l'URL demandée et la destination proposée : un hôte non canonique se
     * diagnostique en une ligne de log au lieu d'une session de debug.
     */
    internal val redirectGuardInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.isRedirect) {
            val location = response.header("Location") ?: "(en-tête Location absent)"
            val code = response.code
            response.close()
            throw IOException(
                "Redirection inattendue ($code) sur ${request.method} ${request.url} → $location. " +
                    "L'origine canonique est ${ApiConfig.ORIGIN} ; suivre cette redirection " +
                    "dégraderait un POST en GET et en perdrait le corps."
            )
        }
        response
    }

    // ── OkHttp clients ──────────────────────────────────────────────────────

    /**
     * Socle commun : redirections désactivées (le garde-fou ci-dessus les rend
     * visibles) et timeouts bornés.
     */
    private fun hardenedClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .apply { httpCache?.let { cache(it) } }
        .addInterceptor(redirectGuardInterceptor)

    /** Client avec injection du Bearer JWT (recettes, profil, favoris…) */
    private val authenticatedHttpClient: OkHttpClient = hardenedClientBuilder()
        .addInterceptor { chain ->
            val token = authToken
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .addInterceptor(loggingInterceptor)
        .build()

    /** Client sans auth (login, refresh) */
    private val plainHttpClient: OkHttpClient = hardenedClientBuilder()
        .addInterceptor(loggingInterceptor)
        .build()

    /**
     * Client dédié à l'amorçage de session web (`?180c_app_login=1`).
     *
     * Seul client **sans** le garde-fou anti-redirection : cet endpoint termine
     * volontairement par un 302, et c'est cette réponse qu'il faut lire pour en
     * extraire les `Set-Cookie`. Le garde-fou lèverait une IOException avant.
     * `followRedirects(false)` reste posé : on ne suit pas la redirection, on
     * l'observe — exactement ce que fait `AppSessionDelegate` côté iOS, qui
     * refuse le 3xx sur POST et rend la réponse telle quelle
     * (`apple/180/Networking.swift:56-62`).
     *
     * Aucun autre appel ne passe par ce client.
     */
    val webSessionHttpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    /** Client no-cache + timeout 5 s (vérification de version) */
    private val appVersionHttpClient: OkHttpClient = hardenedClientBuilder()
        .connectTimeout(APP_VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(APP_VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(APP_VERSION_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    // ── Retrofit instances ──────────────────────────────────────────────────

    private val wordPressRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(ApiConfig.WP_BASE_URL)
        .client(authenticatedHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val authRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(ApiConfig.AUTH_BASE_URL)
        .client(plainHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /** Inscription newsletter via proxy WP — JWT transparent (logged) ou anonyme. */
    private val newsletterRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(ApiConfig.NEWSLETTER_BASE_URL)
        .client(authenticatedHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /**
     * Gson du namespace `180c/v1`, doté du décodeur tolérant de l'accueil : un
     * bloc malformé est écarté sans faire échouer toute la home
     * (miroir `FailableDecodable`, apple/180/HomeService.swift:28-37).
     */
    private val restV1Gson: Gson = GsonBuilder()
        .registerTypeAdapter(HomePayload::class.java, HomePayloadDeserializer())
        .create()

    /** Namespace REST custom `180c/v1` — JWT injecté (statut abonné, carnet, accueil). */
    private val userRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(ApiConfig.REST_V1_BASE_URL)
        .client(authenticatedHttpClient)
        .addConverterFactory(GsonConverterFactory.create(restV1Gson))
        .build()

    private val appVersionRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(ApiConfig.APP_VERSION_BASE_URL)
        .client(appVersionHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // ── API interfaces ───────────────────────────────────────────────────────

    val wordPressApi: WordPressApi  = wordPressRetrofit.create(WordPressApi::class.java)
    val authApi: AuthApi            = authRetrofit.create(AuthApi::class.java)
    val newsletterApi: NewsletterApi = newsletterRetrofit.create(NewsletterApi::class.java)
    val userApi: UserApi            = userRetrofit.create(UserApi::class.java)
    val appVersionApi: AppVersionApi = appVersionRetrofit.create(AppVersionApi::class.java)
}
