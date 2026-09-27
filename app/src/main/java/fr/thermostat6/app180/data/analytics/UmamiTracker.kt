package fr.thermostat6.app180.data.analytics

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.thermostat6.app180.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Client Umami maison : Umami ne publie aucun SDK mobile, on parle donc
 * directement à l'API Umami Cloud (`POST /api/send`, sans authentification).
 *
 * ⚠️ PROPRIÉTÉ UMAMI COMMUNE — le [WEBSITE_ID] est partagé avec le site web et
 * l'app iOS. Les noms d'events et le mapping écran → URL doivent rester
 * STRICTEMENT identiques entre les trois plateformes : toute divergence casse
 * les rapports agrégés. Les hits Android se distinguent par [HOSTNAME] et [TAG],
 * jamais par un nom d'event dérivé.
 *
 * Miroir de `UmamiTracker` (`apple/180/UmamiTracker.swift`).
 *
 * Aucune donnée personnelle n'est transmise : ni e-mail, ni identifiant
 * utilisateur, ni jeton.
 *
 * Coexiste avec [AnalyticsService] (Firebase) sans aucun état partagé : les deux
 * outils mesurent en parallèle, avec deux catalogues d'events distincts.
 */
object UmamiTracker {

    // ── Constantes de la propriété Umami ─────────────────────────────────────

    private const val ENDPOINT = "https://cloud.umami.is/api/send"

    /**
     * Identifiant de la propriété Umami — **commun web / iOS / Android**.
     *
     * Sources : `apple/180/UmamiTracker.swift` (`websiteID`) et
     * `web/…/inc/analytics/umami.php` (`_180C_UMAMI_WEBSITE_ID`).
     */
    private const val WEBSITE_ID = "94b264d5-6963-4678-9d7f-316583113c91"

    /**
     * Hôte virtuel des hits Android. Fixe sur chaque requête : c'est lui qui
     * isole le trafic de cette app dans la propriété commune. Ne jamais l'omettre.
     */
    private const val HOSTNAME = "android.180c.fr"

    /** Étiquette de plateforme, fixe sur chaque requête (même rôle que [HOSTNAME]). */
    private const val TAG = "android"

    /**
     * User-Agent au format navigateur, construit depuis l'appareil réel.
     *
     * **Obligatoire** : avec l'UA par défaut d'OkHttp (`okhttp/4.x`), Umami
     * classe le hit comme bot et le jette. Il est donc posé explicitement sur
     * chaque requête.
     */
    private const val USER_AGENT_TEMPLATE =
        "Mozilla/5.0 (Linux; Android %s; %s) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private const val PREFS_FILE = "app180_umami"

    /** Drapeau de première ouverture (`app_first_open`). */
    private const val KEY_FIRST_OPEN = "umami.hasTrackedFirstOpen"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Tracking coupé en DEBUG pour ne pas polluer les statistiques. Constante
     * **runtime** (et non un `#if` autour du corps d'envoi) : tout le chemin
     * d'envoi reste ainsi compilé dans les deux configurations, une erreur de
     * compilation ne peut pas se cacher dans la branche Release.
     */
    private val isEnabled: Boolean = !BuildConfig.DEBUG

    // ── Client HTTP dédié ────────────────────────────────────────────────────

    /**
     * Volontairement distinct des clients d'[fr.thermostat6.app180.data.network.ApiClient] :
     * (1) ce sont des POST fire-and-forget qui n'ont rien à faire dans le cache
     * HTTP applicatif, (2) un hit perdu ne doit pas mobiliser une connexion
     * pendant 30 s, (3) le client applicatif porte le Bearer JWT et le garde-fou
     * anti-redirection destinés à l'API, hors de propos ici.
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── État de session ──────────────────────────────────────────────────────

    private var prefs: SharedPreferences? = null

    /** Métadonnées de l'appareil, lues une fois à l'`init` puis figées. */
    @Volatile private var device: UmamiDevice? = null

    /**
     * Statut abonné de la session, **relu à chaque event**.
     *
     * Injecté en lambda plutôt que lu directement sur
     * [fr.thermostat6.app180.data.auth.AuthService] : celui-ci appelle déjà
     * [trackEvent] (`login_success`, `login_error`), une dépendance en sens
     * inverse fermerait le cycle. Le paramètre d'[init] est **obligatoire** —
     * un défaut silencieux ferait partir toute la mesure en visiteur sans que
     * rien ne le signale.
     *
     * Évaluation paresseuse, donc pas de valeur figée au lancement : login,
     * réponse de `/180c/v1/me` et déconnexion sont pris en compte dès l'event
     * suivant, sans réinitialiser le traceur.
     */
    @Volatile private var subscriberStatus: () -> Boolean = { false }

    /**
     * Jeton renvoyé par Umami dans le champ `cache` de la réponse, réémis en
     * header `x-umami-cache` sur tous les appels suivants : c'est lui qui
     * regroupe les hits en une même session côté Umami (indispensable aux
     * funnels). Durée de vie = session app, aucune persistance disque.
     */
    @Volatile private var cacheToken: String? = null

    /**
     * Dernier écran vu (`url` et `title` des events custom) et écran précédent
     * (`referrer`).
     *
     * Muté **synchronement** dans [trackScreen], sur le thread appelant, avant
     * tout envoi : un `trackEvent` émis juste après un `trackScreen` se rattache
     * ainsi toujours au bon écran, quel que soit l'ordonnancement des coroutines
     * d'envoi. (L'acteur iOS s'en remet, lui, à l'ordre d'exécution des `Task`.)
     */
    private val screenLock = Any()
    private var currentPath: String? = null
    private var currentTitle: String = ""
    private var previousPath: String = ""

    // ── Initialisation ───────────────────────────────────────────────────────

    /**
     * À appeler dans `Application.onCreate()`. Résout les métadonnées de
     * l'appareil : taille d'écran en dp, locale réelle, User-Agent.
     *
     * @param isSubscriber source du statut abonné de la session courante,
     *   consultée à chaque event (voir [subscriberStatus]).
     */
    fun init(context: Context, isSubscriber: () -> Boolean) {
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        device = UmamiDevice.from(app)
        subscriberStatus = isSubscriber
    }

    // ── API publique (fire-and-forget) ───────────────────────────────────────

    /**
     * Page vue. Payload **sans** champ `name`.
     *
     * @param path  chemin court calqué sur le site (`/recettes`, `/recette/tarte-citron`).
     * @param title titre lisible de l'écran.
     */
    fun trackScreen(path: String, title: String) {
        val referrer = synchronized(screenLock) {
            currentPath?.let { previousPath = it }
            currentPath = path
            currentTitle = title
            previousPath
        }
        enqueue(url = path, title = title, referrer = referrer, name = null, data = null)
    }

    /**
     * Event custom, rattaché au dernier écran vu. Payload **avec** champ `name`.
     *
     * Tout event porte [UmamiParams.IS_SUBSCRIBER] en plus des propriétés de
     * l'appelant : la segmentation abonné / non-abonné doit valoir sur le
     * catalogue entier, pas sur une poignée d'events choisis.
     *
     * @param name nom exact en snake_case, commun aux trois plateformes.
     * @param data propriétés de l'event (valeurs texte uniquement, jamais de
     *             donnée personnelle).
     */
    fun trackEvent(name: String, data: Map<String, String>? = null) {
        val (path, title, referrer) = synchronized(screenLock) {
            // Un event émis avant tout écran (ex. `app_first_open` au lancement)
            // se rattache à la racine plutôt que de partir sans `url`.
            Triple(currentPath ?: "/", currentTitle, previousPath)
        }
        enqueue(
            url = path,
            title = title,
            referrer = referrer,
            name = name,
            // Statut lu ici, sur le thread appelant, et non dans la coroutine
            // d'envoi : l'event porte le statut qu'avait la session au moment où
            // il s'est produit, pas celui du moment où le réseau l'a écoulé.
            data = eventData(data, subscriberStatus())
        )
    }

    /**
     * Données d'un event : celles de l'appelant, plus [UmamiParams.IS_SUBSCRIBER].
     *
     * Le statut est posé **en dernier**, donc jamais écrasable par un appelant
     * qui utiliserait la même clé, et encodé en **texte** (`"true"` / `"false"`)
     * comme toute valeur de `data` : Umami range un booléen JSON dans un type de
     * propriété distinct des chaînes, ce qui scinderait les rapports.
     *
     * Les events émis avant toute connexion (`app_first_open`, `login_error`, et
     * `login_success` lui-même — le statut n'est confirmé par `/180c/v1/me`
     * qu'après) partent en `"false"` : c'est la lecture juste, la session n'était
     * pas encore abonnée au moment du hit.
     *
     * Fonction pure — sans réseau ni `Context` — pour être verrouillée par
     * `UmamiCatalogTest` sur la JVM.
     */
    internal fun eventData(
        data: Map<String, String>?,
        isSubscriber: Boolean
    ): Map<String, String> = LinkedHashMap(data.orEmpty()).apply {
        put(UmamiParams.IS_SUBSCRIBER, if (isSubscriber) "true" else "false")
    }

    /**
     * `app_first_open` : émis **une seule fois** dans la vie de l'installation,
     * gardé par un drapeau `SharedPreferences`.
     */
    fun trackFirstOpenIfNeeded() {
        // En DEBUG on ne consomme pas le drapeau : sinon un build de dev lancé
        // avant l'installation d'une Release sur le même appareil brûlerait la
        // première ouverture, qui ne serait alors jamais mesurée.
        if (!isEnabled) return

        val store = prefs ?: return
        if (store.getBoolean(KEY_FIRST_OPEN, false)) return
        store.edit().putBoolean(KEY_FIRST_OPEN, true).apply()
        trackEvent(UmamiEvents.APP_FIRST_OPEN)
    }

    // ── Envoi ────────────────────────────────────────────────────────────────

    private fun enqueue(
        url: String,
        title: String,
        referrer: String,
        name: String?,
        data: Map<String, String>?
    ) {
        val device = this.device ?: return
        val body = buildBody(
            url = url,
            title = title,
            referrer = referrer,
            name = name,
            data = data,
            language = device.language,
            screen = device.screen
        )
        if (!isEnabled) return

        // Fire-and-forget : jamais de blocage UI, échec silencieux si le réseau
        // est KO (pas de retry, pas de file persistante en v1), et aucun crash
        // imputable au tracking — `runCatching` couvre jusqu'aux erreurs de
        // construction de requête.
        scope.launch {
            runCatching {
                val request = Request.Builder()
                    .url(ENDPOINT)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", device.userAgent)
                    .apply { cacheToken?.let { header("x-umami-cache", it) } }
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    cacheTokenFrom(payload)?.let { cacheToken = it }
                }
            }
        }
    }

    // ── Corps de requête ─────────────────────────────────────────────────────

    /**
     * Construit le corps JSON du hit. Extrait en fonction pure — sans réseau ni
     * `Context` — pour être verrouillé par `UmamiPayloadTest` sur la JVM.
     *
     * Les champs optionnels absents ne sont pas encodés : une page vue part donc
     * bien **sans** clé `name`.
     */
    internal fun buildBody(
        url: String,
        title: String,
        referrer: String,
        name: String?,
        data: Map<String, String>?,
        language: String,
        screen: String
    ): String {
        val payload = JsonObject().apply {
            addProperty("website", WEBSITE_ID)
            addProperty("hostname", HOSTNAME)
            addProperty("tag", TAG)
            addProperty("language", language)
            addProperty("screen", screen)
            addProperty("url", url)
            addProperty("title", title)
            addProperty("referrer", referrer)
            name?.let { addProperty("name", it) }
            data?.takeIf { it.isNotEmpty() }?.let { entries ->
                add("data", JsonObject().apply {
                    entries.forEach { (key, value) -> addProperty(key, value) }
                })
            }
        }
        return JsonObject().apply {
            addProperty("type", "event")
            add("payload", payload)
        }.toString()
    }

    /**
     * Extrait le jeton de session de la réponse. Umami rend selon les versions un
     * objet `{"cache":"…"}`, une chaîne JSON entre guillemets, ou le jeton brut :
     * les trois formes sont acceptées, tout le reste est ignoré.
     */
    internal fun cacheTokenFrom(body: String): String? {
        runCatching {
            val element = JsonParser.parseString(body)
            if (element.isJsonObject) {
                val token = element.asJsonObject.get("cache")
                    ?.takeIf { it.isJsonPrimitive }?.asString
                return token?.takeIf { it.isNotEmpty() }
            }
        }

        var raw = body.trim()
        if (raw.length > 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length - 1)
        }
        // Garde-fou : un corps vide, un `ok` ou un JSON sans champ `cache` ne
        // sont pas des jetons — les renvoyer en header ferait rejeter les hits.
        if (raw.length <= 8 || raw.startsWith("{") || raw.startsWith("[")) return null
        return raw
    }

    // ── Métadonnées de l'appareil ────────────────────────────────────────────

    private data class UmamiDevice(
        /** Taille de l'écran en dp, `"412x915"`. */
        val screen: String,
        /** Locale réelle de l'appareil, format BCP-47 (`"fr-FR"`). */
        val language: String,
        val userAgent: String
    ) {
        companion object {
            fun from(context: Context): UmamiDevice {
                val metrics = context.resources.displayMetrics
                val density = metrics.density.takeIf { it > 0f } ?: 1f
                val widthDp = (metrics.widthPixels / density).roundToInt()
                val heightDp = (metrics.heightPixels / density).roundToInt()

                // Portrait normalisé, comme l'iOS : le petit côté est la largeur,
                // quelle que soit l'orientation au moment de la lecture.
                val screen = "${min(widthDp, heightDp)}x${max(widthDp, heightDp)}"

                val language = Locale.getDefault().toLanguageTag()
                    .takeIf { it.isNotEmpty() && it != "und" } ?: "fr-FR"

                val userAgent = String.format(
                    Locale.US,
                    USER_AGENT_TEMPLATE,
                    Build.VERSION.RELEASE.orEmpty().ifEmpty { "13" },
                    Build.MODEL.orEmpty().ifEmpty { "Android" }
                )

                return UmamiDevice(screen = screen, language = language, userAgent = userAgent)
            }
        }
    }
}
