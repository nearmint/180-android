package fr.thermostat6.app180.data.web

import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.ApiConfig
import okhttp3.FormBody
import okhttp3.Request

/**
 * Pont de session web ↔ app.
 *
 * Un WebView neuf ne partage pas le JWT de l'app. On amorce donc la session côté
 * OkHttp — qui atteint déjà le site pour tout le reste de l'API — afin de
 * récupérer les cookies WordPress, puis on les injecte dans le cookie store du
 * WebView avant le chargement. La page s'ouvre alors **déjà connectée**.
 *
 * Miroir d'`enum WebSession` (`apple/180/InAppBrowserView.swift:14-134`).
 *
 * Le JWT n'est **jamais** dans l'URL : il part en corps POST `jwt=…`
 * (`InAppBrowserView.swift:18-19`, `:52`). Seuls le marqueur `180c_app_login` et
 * la destination `redirect` — un chemin relatif, pas un secret — y figurent.
 */
object WebSession {

    /** Marqueur de lien autologin (`APIConfig.swift:99`). */
    const val AUTOLOGIN_PARAM = "180c_app_login"

    /** Paramètre portant la destination finale (`APIConfig.swift:99`). */
    const val REDIRECT_PARAM = "redirect"

    /**
     * Préfixes des cookies d'authentification WordPress.
     *
     * Leur présence prouve que la session web est réellement ouverte.
     * `wordpress_test_cookie`, posé par WP sur n'importe quelle réponse, est
     * volontairement exclu (`InAppBrowserView.swift:92-101`).
     */
    private val SESSION_COOKIE_PREFIXES = listOf("wordpress_logged_in_", "wordpress_sec_")

    /** Issue de l'amorçage. */
    sealed class Priming {
        /** Page à charger ; [cookies] est vide pour un visiteur. */
        data class Ready(val url: String, val cookies: List<String>) : Priming()

        /**
         * Amorçage d'un lien authentifié échoué (jeton invalide, réseau) :
         * l'appelant n'ouvre **pas** de page déconnectée (`InAppBrowserView.swift:23-26`).
         */
        data object Failed : Priming()
    }

    /**
     * Construit un lien web **auto-connecté** vers [path].
     *
     * Miroir d'`APIConfig.autoLoginLink` (`apple/180/APIConfig.swift:95-100`) :
     * sans jeton, on renvoie un lien web simple.
     */
    fun autoLoginLink(path: String, token: String?): String {
        if (token.isNullOrEmpty()) return ApiConfig.webLink(path)
        val normalized = if (path.startsWith("/")) path else "/$path"
        return "${ApiConfig.ORIGIN}/?$AUTOLOGIN_PARAM=1&$REDIRECT_PARAM=${percentEncodeQuery(normalized)}"
    }

    /**
     * Lien d'ouverture d'une **page produit** de notification.
     *
     * Miroir de `ContentView.swift:143-161`, et **point unique** partagé par les
     * deux entrées (tap d'un push, ligne du centre) pour qu'elles ne divergent
     * pas :
     * - connecté → lien autologin vers le chemin relatif de la page ;
     * - visiteur → la page publique, telle quelle. Une page produit est
     *   publique : « une notification tapée doit toujours ouvrir quelque chose »,
     *   la connexion se fera depuis le web au moment d'acheter.
     *
     * [url] est déjà normalisée sur l'hôte canonique par
     * [fr.thermostat6.app180.data.push.NotificationRouter] (`:79-88`).
     */
    fun productLink(url: String, token: String?): String =
        autoLoginLink(relativeTarget(url), token)

    /**
     * Chemin relatif d'une URL du site — **chemin + requête + fragment** —, tel
     * qu'[autoLoginLink] l'attend pour le placer en `redirect=`.
     *
     * Miroir de `relativeTarget(of:)` (`apple/180/ContentView.swift:170-178`) :
     * une URL absolue est réduite à ce qui suit l'hôte, un hôte nu donne `/`.
     * Une entrée déjà relative est renvoyée normalisée sur un `/` initial.
     *
     * L'hôte est écarté sans être vérifié : les seules URL passées ici sont
     * déjà contraintes au domaine canonique par
     * [fr.thermostat6.app180.data.push.NotificationRouter] (`:79-88`).
     */
    fun relativeTarget(url: String): String {
        val withoutScheme = when {
            url.startsWith("https://") -> url.removePrefix("https://")
            url.startsWith("http://")  -> url.removePrefix("http://")
            else -> return if (url.startsWith("/")) url else "/$url"
        }
        // Fin de l'autorité : premier `/`, `?` ou `#` rencontré.
        val cut = withoutScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (cut < 0) return "/"
        val target = withoutScheme.substring(cut)
        return if (target.startsWith("/")) target else "/$target"
    }

    /**
     * Encodage miroir d'`addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)`
     * (`APIConfig.swift:98`) : les caractères autorisés en composante de requête
     * passent tels quels — `/` compris —, le reste est percent-encodé.
     */
    private fun percentEncodeQuery(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && byte.toInt() in 0..127 || c in QUERY_ALLOWED) {
                append(c)
            } else {
                append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }

    /** Sous-ensemble d'`urlQueryAllowed` (RFC 3986, composante query). */
    private const val QUERY_ALLOWED = "-._~/?:@!$&'()*+,;="

    /** Décodage percent, pour relire le paramètre `redirect`. */
    private fun percentDecode(value: String): String {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '%' && i + 2 < value.length -> {
                    val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex != null) { out.write(hex); i += 3 } else { out.write(c.code); i++ }
                }
                else -> { out.write(c.code); i++ }
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /** Valeur brute d'un paramètre de requête, `null` s'il est absent. */
    private fun queryParam(url: String, name: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return null
        return query.split('&')
            .firstOrNull { it == name || it.startsWith("$name=") }
            ?.substringAfter('=', "")
            ?.let { percentDecode(it) }
    }

    /** `true` si [url] est un lien autologin (`InAppBrowserView.swift:28-31`). */
    fun isAutoLoginLink(url: String): Boolean = queryParam(url, AUTOLOGIN_PARAM) != null

    /**
     * Résout la destination finale d'un lien autologin.
     *
     * Miroir de `resolveTarget` (`InAppBrowserView.swift:105-110`) : un `redirect`
     * absolu est pris tel quel, un chemin relatif est recollé sur l'origine.
     */
    fun resolveTarget(url: String): String {
        val redirect = queryParam(url, REDIRECT_PARAM)?.takeIf { it.isNotEmpty() } ?: return url
        // Destination absolue : prise telle quelle (InAppBrowserView.swift:106).
        if (redirect.startsWith("http://") || redirect.startsWith("https://")) return redirect
        val normalized = if (redirect.startsWith("/")) redirect else "/$redirect"
        return "${ApiConfig.ORIGIN}$normalized"
    }

    /**
     * Amorce la session web si [url] est un lien autologin, sinon renvoie l'URL
     * telle quelle. Miroir de `prime` (`InAppBrowserView.swift:27-50`).
     */
    suspend fun prime(url: String): Priming {
        if (!isAutoLoginLink(url)) return Priming.Ready(url, emptyList())

        val destination = resolveTarget(url)

        // Visiteur : on ouvre la page publique, sans amorçage.
        val token = ApiClient.authToken
        if (token.isNullOrEmpty()) return Priming.Ready(destination, emptyList())

        val cookies = postLogin(token)
        return if (cookies.isEmpty()) Priming.Failed else Priming.Ready(destination, cookies)
    }

    /**
     * POST `jwt=<token>` en form-urlencoded, **hors URL**, vers `?180c_app_login=1`.
     * L'endpoint valide le jeton et répond `Set-Cookie`.
     *
     * On ne statue **pas** sur le code HTTP : l'endpoint termine par une
     * redirection, et le garde-fou anti-redirection du LOT-01 la refuse — la
     * réponse remonte donc en 3xx, ce qu'un test `== 200` prendrait à tort pour
     * un échec. À l'inverse, un 200 peut être une page d'erreur sans cookie.
     * **La présence du cookie de session est le seul critère fiable**
     * (`InAppBrowserView.swift:56-62`).
     *
     * @return les en-têtes `Set-Cookie` de session, vides si l'amorçage a échoué.
     */
    private fun postLogin(token: String): List<String> {
        val loginUrl = "${ApiConfig.ORIGIN}/?$AUTOLOGIN_PARAM=1"
        val request = Request.Builder()
            .url(loginUrl)
            .post(FormBody.Builder().add("jwt", token).build())
            .build()

        return runCatching {
            ApiClient.webSessionHttpClient.newCall(request).execute().use { response ->
                response.headers("Set-Cookie").filter { it.isSessionCookie() }
            }
        }.getOrDefault(emptyList())
    }

    private fun String.isSessionCookie(): Boolean =
        SESSION_COOKIE_PREFIXES.any { this.startsWith(it) }
}
