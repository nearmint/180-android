package fr.thermostat6.app180.data.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Destinataire des événements. Extrait en interface pour que les points d'appel
 * soient vérifiables sans Firebase réel (cf. `AnalyticsCatalogTest`).
 */
interface AnalyticsSink {
    /**
     * [params] est une carte plutôt qu'un `Bundle` : le `Bundle` est une classe
     * du framework Android, indisponible en test JVM. La conversion vit dans
     * l'implémentation Firebase, laissant le catalogue vérifiable sans appareil.
     */
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap())
    fun setUserProperty(name: String, value: String?)
}

/** Implémentation réelle, adossée à Firebase Analytics. */
private object FirebaseSink : AnalyticsSink {
    private val firebase: FirebaseAnalytics by lazy { Firebase.analytics }

    override fun logEvent(name: String, params: Map<String, Any?>) {
        runCatching { firebase.logEvent(name, params.toBundle()) }
    }

    private fun Map<String, Any?>.toBundle(): Bundle? {
        if (isEmpty()) return null
        return Bundle().apply {
            forEach { (key, value) ->
                when (value) {
                    is Int     -> putInt(key, value)
                    is Long    -> putLong(key, value)
                    is Double  -> putDouble(key, value)
                    is Boolean -> putBoolean(key, value)
                    else       -> putString(key, value?.toString())
                }
            }
        }
    }

    override fun setUserProperty(name: String, value: String?) {
        runCatching { firebase.setUserProperty(name, value) }
    }
}

/**
 * Couche analytics — **miroir exact** de l'`AnalyticsService` iOS
 * (`apple/180/AnalyticsService.swift`).
 *
 * Une fonction par événement, aucun nom composé à la volée : c'est ce qui
 * garantit que les deux plateformes remontent les mêmes clés dans GA4.
 *
 * Aucune donnée personnelle n'est envoyée — ni e-mail, ni nom, ni identifiant de
 * compte. Les seuls textes transmis sont du contenu éditorial (titre de recette),
 * une requête de recherche, ou un message d'erreur **défini par l'app**, jamais
 * un message serveur brut.
 */
object AnalyticsService {

    /** Substituable en test ; en production, Firebase. */
    @Volatile
    var sink: AnalyticsSink = FirebaseSink

    // ── Recettes ─────────────────────────────────────────────────────────────

    /** `AnalyticsService.swift:8-14` */
    fun viewRecipe(id: Int, title: String, isPremium: Boolean) =
        sink.logEvent(
            AnalyticsEvents.VIEW_RECIPE,
            mapOf(
                AnalyticsParams.RECIPE_ID to id,
                AnalyticsParams.RECIPE_TITLE to title,
                AnalyticsParams.IS_PREMIUM to isPremium
            )
        )

    /** `:16-21` */
    fun paywallView(id: Int, title: String) =
        sink.logEvent(
            AnalyticsEvents.PAYWALL_VIEW,
            mapOf(AnalyticsParams.RECIPE_ID to id, AnalyticsParams.RECIPE_TITLE to title)
        )

    /** `:23-28` */
    fun shareRecipe(id: Int, title: String) =
        sink.logEvent(
            AnalyticsEvents.SHARE_RECIPE,
            mapOf(AnalyticsParams.RECIPE_ID to id, AnalyticsParams.RECIPE_TITLE to title)
        )

    // ── Favoris ──────────────────────────────────────────────────────────────

    /** `:32-37` */
    fun addFavorite(id: Int, title: String) =
        sink.logEvent(
            AnalyticsEvents.ADD_FAVORITE,
            mapOf(AnalyticsParams.RECIPE_ID to id, AnalyticsParams.RECIPE_TITLE to title)
        )

    /** `:39-44` */
    fun removeFavorite(id: Int, title: String) =
        sink.logEvent(
            AnalyticsEvents.REMOVE_FAVORITE,
            mapOf(AnalyticsParams.RECIPE_ID to id, AnalyticsParams.RECIPE_TITLE to title)
        )

    // ── Recherche ────────────────────────────────────────────────────────────

    /** `:48-53` */
    fun search(query: String, resultsCount: Int) =
        sink.logEvent(
            AnalyticsEvents.SEARCH,
            mapOf(AnalyticsParams.QUERY to query, AnalyticsParams.RESULTS_COUNT to resultsCount)
        )

    // ── Auth ─────────────────────────────────────────────────────────────────

    /** `:57-61` — `method` vaut « email » ou « username » (`LoginView.swift:129`). */
    fun login(method: String) =
        sink.logEvent(AnalyticsEvents.LOGIN, mapOf(AnalyticsParams.METHOD to method))

    /** `:63-67` — message d'erreur **défini par l'app**, jamais serveur brut. */
    fun loginFailed(error: String) =
        sink.logEvent(AnalyticsEvents.LOGIN_FAILED, mapOf(AnalyticsParams.ERROR_MESSAGE to error))

    /** `:69-71` */
    fun logout() = sink.logEvent(AnalyticsEvents.LOGOUT)

    // ── Conversion ───────────────────────────────────────────────────────────

    /**
     * `:75-79`
     *
     * **Sans appelant par décision produit** — ne pas retirer comme code mort :
     * la Play Store interdit d'orienter vers une inscription payante hors
     * facturation Google. L'événement reste défini pour que la parité du
     * catalogue avec l'iOS tienne, et pour le jour où un point d'entrée
     * conforme existera.
     */
    fun signupClick(source: String) =
        sink.logEvent(AnalyticsEvents.SIGNUP_CLICK, mapOf(AnalyticsParams.SOURCE to source))

    /** `:81-83` */
    fun boutiqueClick() = sink.logEvent(AnalyticsEvents.BOUTIQUE_CLICK)

    // ── Filtres ──────────────────────────────────────────────────────────────

    /** `:87-93` — `null` devient « all », comme le `?? "all"` du Swift. */
    fun filterApplied(sortOrder: String, season: String?, dishType: String?) =
        sink.logEvent(
            AnalyticsEvents.FILTER_APPLIED,
            mapOf(
                AnalyticsParams.SORT_ORDER to sortOrder,
                AnalyticsParams.SEASON to (season ?: "all"),
                AnalyticsParams.DISH_TYPE to (dishType ?: "all")
            )
        )

    // ── Newsletter ───────────────────────────────────────────────────────────

    /** `:97-99` */
    fun newsletterSubscribe() = sink.logEvent(AnalyticsEvents.NEWSLETTER_SUBSCRIBE)

    /** `:101-103` */
    fun newsletterUnsubscribe() = sink.logEvent(AnalyticsEvents.NEWSLETTER_UNSUBSCRIBE)

    // ── Notifications ────────────────────────────────────────────────────────

    /** `:107-111` */
    fun notificationPermission(granted: Boolean) =
        sink.logEvent(
            AnalyticsEvents.NOTIFICATION_PERMISSION,
            mapOf(AnalyticsParams.GRANTED to granted)
        )

    /** `:113-118` — `id` est transmis en **chaîne** côté iOS (`NotificationsView.swift:122`). */
    fun notificationOpened(id: String, type: String) =
        sink.logEvent(
            AnalyticsEvents.NOTIFICATION_OPENED,
            mapOf(
                AnalyticsParams.NOTIFICATION_ID to id,
                AnalyticsParams.NOTIFICATION_TYPE to type
            )
        )

    // ── In-App Messages ──────────────────────────────────────────────────────

    /**
     * `:122-126`. Double les statistiques du dashboard OneSignal, et permet de
     * croiser les IAM avec le reste des parcours dans Analytics.
     */
    fun inAppMessageDisplayed(id: String) =
        sink.logEvent(AnalyticsEvents.IAM_DISPLAYED, mapOf(AnalyticsParams.MESSAGE_ID to id))

    /**
     * `:128-133` — `action_id` vaut la chaîne `"none"` quand le bouton n'en porte
     * pas, comme l'iOS. Un paramètre absent et un bouton sans action seraient
     * indiscernables dans GA4 ; ici le second se compte.
     */
    fun inAppMessageClicked(id: String, actionId: String?) =
        sink.logEvent(
            AnalyticsEvents.IAM_CLICKED,
            mapOf(
                AnalyticsParams.MESSAGE_ID to id,
                AnalyticsParams.ACTION_ID to (actionId ?: "none")
            )
        )

    // ── Paramètres ───────────────────────────────────────────────────────────

    /** `:137-141` — « auto » · « light » · « dark » (`AccountView.swift:264`). */
    fun darkModeChanged(mode: String) =
        sink.logEvent(AnalyticsEvents.DARK_MODE_CHANGED, mapOf(AnalyticsParams.MODE to mode))

    /** `:143-145` */
    fun contactSupport() = sink.logEvent(AnalyticsEvents.CONTACT_SUPPORT)

    /**
     * `:147-149`
     *
     * **Sans appelant tant que la fiche Play n'existe pas** — ne pas retirer
     * comme code mort : les deux entrées « Noter l'app » et « Partager l'app »
     * de l'écran Compte attendent l'URL de la fiche
     * (`TODO`, `ui/screen/AccountScreen.kt`).
     */
    fun rateAppClick() = sink.logEvent(AnalyticsEvents.RATE_APP_CLICK)

    /** `:151-153` — même attente que [rateAppClick], ne pas retirer. */
    fun shareApp() = sink.logEvent(AnalyticsEvents.SHARE_APP)

    /** `:155-157` */
    fun onboardingComplete() = sink.logEvent(AnalyticsEvents.ONBOARDING_COMPLETE)

    // ── Propriétés utilisateur ───────────────────────────────────────────────

    /**
     * `:161-172`. `user_type` est dérivé, pas cumulatif : abonné prime sur
     * connecté, connecté sur visiteur.
     */
    fun setUserProperties(
        isLoggedIn: Boolean,
        isSubscriber: Boolean,
        newsletterSubscribed: Boolean,
        darkMode: String,
        notificationsEnabled: Boolean,
        favoritesCount: Int
    ) {
        val userType = when {
            isSubscriber -> AnalyticsUserProperties.TYPE_SUBSCRIBER
            isLoggedIn   -> AnalyticsUserProperties.TYPE_LOGGED_IN
            else         -> AnalyticsUserProperties.TYPE_VISITOR
        }

        sink.setUserProperty(AnalyticsUserProperties.USER_TYPE, userType)
        sink.setUserProperty(AnalyticsUserProperties.IS_SUBSCRIBER, isSubscriber.toString())
        sink.setUserProperty(AnalyticsUserProperties.NEWSLETTER_SUBSCRIBED, newsletterSubscribed.toString())
        sink.setUserProperty(AnalyticsUserProperties.DARK_MODE, darkMode)
        sink.setUserProperty(AnalyticsUserProperties.NOTIFICATIONS_ENABLED, notificationsEnabled.toString())
        sink.setUserProperty(AnalyticsUserProperties.FAVORITES_COUNT, favoritesCount.toString())
    }
}
