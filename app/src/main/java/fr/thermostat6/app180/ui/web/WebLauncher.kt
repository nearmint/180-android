package fr.thermostat6.app180.ui.web

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Ouverture des liens web du site.
 *
 * Deux chemins, dictés par une contrainte de plateforme :
 *
 * - **Lien public** → Chrome Custom Tab. Léger, thémé, familier, et il partage
 *   la session Chrome de l'utilisateur.
 * - **Lien authentifié** → WebView interne ([WebViewScreen]). Les Custom Tabs
 *   vivent dans le processus de Chrome : l'app ne peut **pas** y injecter les
 *   cookies WordPress obtenus par [fr.thermostat6.app180.data.web.WebSession].
 *   L'autologin y serait donc impossible. Le WebView, lui, expose un
 *   `CookieManager` que l'app alimente — c'est le pendant Android exact de
 *   l'injection de cookies dans le `WKWebView` iOS
 *   (`apple/180/InAppBrowserView.swift:246`).
 *
 * Côté iOS le `WebLink` présente toujours un `WKWebView`
 * (`InAppBrowserView.swift:283-320`) : le chemin authentifié est donc le miroir
 * fidèle, le Custom Tab un choix Android pour les pages qui n'ont rien à
 * authentifier.
 */
object WebLauncher {

    /**
     * Ouvre un lien **public** dans un Custom Tab.
     * Repli sur le navigateur externe si aucun navigateur compatible n'est
     * installé — un lien doit toujours s'ouvrir.
     */
    fun openPublic(context: Context, url: String, toolbarColor: Int? = null) {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .apply {
                if (toolbarColor != null) {
                    setDefaultColorSchemeParams(
                        androidx.browser.customtabs.CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(toolbarColor)
                            .build()
                    )
                }
            }
            .build()

        runCatching { intent.launchUrl(context, url.toUri()) }
            .onFailure { openExternally(context, url) }
    }

    /** Repli : délègue au navigateur du système. */
    fun openExternally(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { error ->
            if (error !is ActivityNotFoundException) throw error
        }
    }

    /** Ouvre un `mailto:` dans l'app mail (jamais dans un navigateur). */
    fun openMail(context: Context, mailto: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(mailto)))
        }
    }
}
