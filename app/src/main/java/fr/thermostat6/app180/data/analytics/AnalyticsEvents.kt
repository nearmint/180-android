package fr.thermostat6.app180.data.analytics

/**
 * Catalogue des événements analytics — **miroir caractère par caractère** de
 * l'`AnalyticsService` iOS (`apple/180/AnalyticsService.swift`).
 *
 * Les noms vivent ici en constantes, jamais en littéraux dans les écrans : une
 * faute de frappe côté Android créerait un événement distinct dans GA4 et
 * casserait silencieusement la comparabilité cross-plateforme. Le test
 * `AnalyticsCatalogTest` verrouille chaque chaîne.
 */
object AnalyticsEvents {

    // ── Recettes ─────────────────────────────────────────────────────────────
    /** `AnalyticsService.swift:8-14` */
    const val VIEW_RECIPE = "view_recipe"
    /** `:16-21` */
    const val PAYWALL_VIEW = "paywall_view"
    /** `:23-28` */
    const val SHARE_RECIPE = "share_recipe"

    // ── Favoris ──────────────────────────────────────────────────────────────
    /** `:32-37` */
    const val ADD_FAVORITE = "add_favorite"
    /** `:39-44` */
    const val REMOVE_FAVORITE = "remove_favorite"

    // ── Recherche ────────────────────────────────────────────────────────────
    /** `:48-53` */
    const val SEARCH = "search"

    // ── Auth ─────────────────────────────────────────────────────────────────
    /** `:57-61` */
    const val LOGIN = "login"
    /** `:63-67` */
    const val LOGIN_FAILED = "login_failed"
    /** `:69-71` */
    const val LOGOUT = "logout"

    // ── Conversion ───────────────────────────────────────────────────────────
    /** `:75-79` */
    const val SIGNUP_CLICK = "signup_click"
    /** `:81-83` */
    const val BOUTIQUE_CLICK = "boutique_click"

    // ── Filtres ──────────────────────────────────────────────────────────────
    /** `:87-93` */
    const val FILTER_APPLIED = "filter_applied"

    // ── Newsletter ───────────────────────────────────────────────────────────
    /** `:97-99` */
    const val NEWSLETTER_SUBSCRIBE = "newsletter_subscribe"
    /** `:101-103` */
    const val NEWSLETTER_UNSUBSCRIBE = "newsletter_unsubscribe"

    // ── Notifications ────────────────────────────────────────────────────────
    /** `:107-111` */
    const val NOTIFICATION_PERMISSION = "notification_permission"
    /** `:113-118` */
    const val NOTIFICATION_OPENED = "notification_opened"

    // ── In-App Messages ──────────────────────────────────────────────────────
    /** `:122-126` */
    const val IAM_DISPLAYED = "iam_displayed"
    /** `:128-133` */
    const val IAM_CLICKED = "iam_clicked"

    // ── Paramètres ───────────────────────────────────────────────────────────
    /** `:137-141` */
    const val DARK_MODE_CHANGED = "dark_mode_changed"
    /** `:143-145` */
    const val CONTACT_SUPPORT = "contact_support"
    /** `:147-149` */
    const val RATE_APP_CLICK = "rate_app_click"
    /** `:151-153` */
    const val SHARE_APP = "share_app"
    /** `:155-157` */
    const val ONBOARDING_COMPLETE = "onboarding_complete"
}

/** Noms de paramètres, miroir des clés de `parameters:` côté Swift. */
object AnalyticsParams {
    const val RECIPE_ID = "recipe_id"
    const val RECIPE_TITLE = "recipe_title"
    const val IS_PREMIUM = "is_premium"
    const val QUERY = "query"
    const val RESULTS_COUNT = "results_count"
    const val METHOD = "method"
    const val ERROR_MESSAGE = "error_message"
    const val SOURCE = "source"
    const val SORT_ORDER = "sort_order"
    const val SEASON = "season"
    const val DISH_TYPE = "dish_type"
    const val GRANTED = "granted"
    const val NOTIFICATION_ID = "notification_id"
    const val NOTIFICATION_TYPE = "notification_type"
    const val MODE = "mode"
    const val MESSAGE_ID = "message_id"
    const val ACTION_ID = "action_id"
}

/** Propriétés utilisateur (`AnalyticsService.swift:161-172`). */
object AnalyticsUserProperties {
    const val USER_TYPE = "user_type"
    const val IS_SUBSCRIBER = "is_subscriber"
    const val NEWSLETTER_SUBSCRIBED = "newsletter_subscribed"
    const val DARK_MODE = "dark_mode"
    const val NOTIFICATIONS_ENABLED = "notifications_enabled"
    const val FAVORITES_COUNT = "favorites_count"

    /** Valeurs de [USER_TYPE] (`:162-164`). */
    const val TYPE_VISITOR = "visitor"
    const val TYPE_LOGGED_IN = "logged_in"
    const val TYPE_SUBSCRIBER = "subscriber"
}
