package fr.thermostat6.app180.ui.web

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import fr.thermostat6.app180.data.web.WebSession
import fr.thermostat6.app180.ui.components.NetworkErrorState

/**
 * Navigateur intégré pour les liens **authentifiés** du site.
 *
 * Amorce la session (`WebSession.prime`) **avant** de charger la page, puis
 * injecte les cookies WordPress dans le `CookieManager` du WebView : la page
 * s'ouvre déjà connectée. Miroir d'`InAppBrowserView`
 * (`apple/180/InAppBrowserView.swift:162`, `:230`, `:246`).
 *
 * Si l'amorçage échoue, **aucune page n'est ouverte** : on affiche l'erreur
 * plutôt qu'un site déconnecté (`InAppBrowserView.swift:307-310`) — sauf si un
 * [fallbackUrl] est fourni, voir ce paramètre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    title: String,
    onClose: () -> Unit,
    fallbackUrl: String = ""
) {
    val context = LocalContext.current

    var resolvedUrl by remember { mutableStateOf<String?>(null) }
    var failed      by remember { mutableStateOf(false) }
    var isLoading   by remember { mutableStateOf(true) }
    var webView     by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(url) {
        when (val priming = WebSession.prime(url)) {
            is WebSession.Priming.Ready -> {
                // Les cookies obtenus par OkHttp sont poussés dans le store du
                // WebView : c'est ce transfert que les Custom Tabs ne permettent
                // pas, et qui rend l'autologin possible ici.
                val manager = CookieManager.getInstance()
                manager.setAcceptCookie(true)
                priming.cookies.forEach { manager.setCookie(priming.url, it) }
                manager.flush()
                resolvedUrl = priming.url
            }
            // Miroir d'`InAppBrowserView(authenticating:fallback:)`
            // (`InAppBrowserView.swift:229-231`) : quand un repli public est
            // déclaré — le chemin des notifications —, l'échec d'amorçage
            // ouvre la page publique plutôt qu'un écran d'erreur, « une
            // notification tapée doit toujours ouvrir quelque chose »
            // (`ContentView.swift:154`). Sans repli — « Mon compte » —, le
            // refus strict reste la règle : mieux vaut une erreur explicite
            // qu'une page de compte affichée en visiteur.
            WebSession.Priming.Failed ->
                if (fallbackUrl.isNotEmpty()) resolvedUrl = fallbackUrl else failed = true
        }
    }

    // Le retour système remonte l'historique du WebView avant de fermer l'écran.
    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title          = {
                Text(
                    text     = title.ifEmpty { hostOf(fallbackUrl.ifEmpty { url }) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Fermer")
                }
            }
        )

        Box(Modifier.fillMaxSize()) {
            when {
                failed -> NetworkErrorState(
                    message = "Connexion à votre compte impossible. Réessayez.",
                    onRetry = onClose
                )

                resolvedUrl != null -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory  = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                    }
                                }
                                webView = this
                                loadUrl(resolvedUrl!!)
                            }
                        }
                    )
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center).padding(16.dp))
                    }
                }

                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

/**
 * Hôte d'une URL, titre de repli quand l'appelant n'en fournit pas — miroir de
 * `titleHost` (`apple/180/InAppBrowserView.swift:195-200`), qui prend l'hôte de
 * la destination et non celui du lien d'amorçage.
 */
private fun hostOf(url: String): String =
    url.substringAfter("://", "").substringBefore('/').substringBefore(':')
