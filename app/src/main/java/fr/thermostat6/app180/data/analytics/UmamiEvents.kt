package fr.thermostat6.app180.data.analytics

/**
 * Catalogue des events Umami — **miroir caractère par caractère** de l'iOS
 * (`apple/180/UmamiTracker.swift` et ses points d'appel) et du site
 * (`web/…/src/js/modules/umami-events.js`).
 *
 * Les noms vivent ici en constantes, jamais en littéraux dans les écrans : une
 * faute de frappe côté Android créerait un event distinct dans la propriété
 * commune et casserait silencieusement la comparabilité cross-plateforme. Le
 * test `UmamiCatalogTest` verrouille chaque chaîne.
 *
 * Distinct d'[AnalyticsEvents] (Firebase) : deux outils, deux catalogues, aucun
 * état partagé.
 */
object UmamiEvents {

    /** Toute première ouverture de l'app (drapeau `SharedPreferences`). */
    const val APP_FIRST_OPEN = "app_first_open"

    /** Connexion réussie (`apple/180/LoginView.swift:131`). */
    const val LOGIN_SUCCESS = "login_success"

    /** Échec de connexion (`LoginView.swift:140`). */
    const val LOGIN_ERROR = "login_error"

    /** Réponse à la demande d'autorisation des notifications
     *  (`apple/180/Services/PushPermissionCoordinator.swift:113`). */
    const val PUSH_OPTIN = "push_optin"

    /** App ouverte depuis une notification
     *  (`apple/180/Navigation/NotificationRouter.swift:139`). */
    const val PUSH_OPEN = "push_open"

    /** Lancement d'une recherche (`apple/180/SearchView.swift:253`). */
    const val RECIPE_SEARCH = "recipe_search"

    /** Ajout d'une recette au carnet — jamais le retrait
     *  (`apple/180/FavoritesManager.swift:55`). */
    const val RECIPE_FAVORITE = "recipe_favorite"

    /** Partage d'une recette (`apple/180/RecipeDetailView.swift:112`). */
    const val RECIPE_SHARE = "recipe_share"
}

/** Clés de `data`, miroir des dictionnaires passés à `trackEvent` côté Swift. */
object UmamiParams {
    /**
     * Statut abonné de la session — posé par [UmamiTracker.trackEvent] sur
     * **tous** les events, jamais par un appelant.
     *
     * Valeurs `"true"` / `"false"` en texte (jamais un booléen JSON), source
     * `AuthService.isSubscriber`, lui-même alimenté par `GET /180c/v1/me`.
     *
     * ⚠️ Divergence connue : Android est pour l'instant seul à porter cette
     * propriété. Le web (`umami-events.js`) et l'iOS (`UmamiTracker.swift`) ne
     * l'émettent pas — côté iOS elle n'existe qu'en propriété utilisateur
     * Firebase (`AnalyticsService.swift:167`). Une segmentation par abonnement
     * ne vaut donc que sur le trafic `tag=android` tant que les deux autres
     * plateformes n'ont pas suivi.
     */
    const val IS_SUBSCRIBER = "is_subscriber"

    /** [UmamiEvents.LOGIN_ERROR] — cause générique, jamais un message serveur. */
    const val REASON = "reason"

    /** [UmamiEvents.PUSH_OPTIN] — `"true"` / `"false"`. */
    const val ACCEPTED = "accepted"

    /** [UmamiEvents.PUSH_OPEN] — nom de template, titre, ou id de la notification. */
    const val CAMPAIGN = "campaign"

    /** [UmamiEvents.RECIPE_SEARCH] — minuscules, tronquée à 50 caractères. */
    const val QUERY = "query"

    /** [UmamiEvents.RECIPE_FAVORITE] — slug de la recette, repli sur l'id. */
    const val RECIPE_ID = "recipe_id"

    /** [UmamiEvents.RECIPE_SHARE] — destination du partage. */
    const val CHANNEL = "channel"

    /** Longueur maximale d'une requête transmise (`SearchView.swift:254`,
     *  `umami-events.js` `MAX_QUERY_LENGTH`). */
    const val MAX_QUERY_LENGTH = 50

    /**
     * Seule valeur de [CHANNEL] émise par les apps.
     *
     * Ni `Intent.createChooser` ni `ShareLink` n'exposent la destination
     * réellement choisie sans instrumenter un `IntentSender` : l'event est émis
     * à l'**ouverture** de la feuille de partage, des deux côtés
     * (`RecipeDetailView.swift:108-112`).
     */
    const val CHANNEL_SHARE_SHEET = "share_sheet"
}

/**
 * Mapping écran → (`url`, `title`), calqué sur le site et sur l'iOS.
 *
 * Les chemins sont courts et en français : ce sont les mêmes que ceux du site,
 * pour que les rapports « pages les plus vues » agrègent web et apps.
 */
object UmamiScreens {

    /** `apple/180/HomeView.swift:98` */
    const val HOME_PATH = "/"
    const val HOME_TITLE = "Accueil"

    /** `apple/180/RecipeListView.swift:118` — toutes les listes partagent cette
     *  url ; c'est le **titre** qui porte le filtre d'entrée (« Été », « Desserts »…). */
    const val RECIPE_LIST_PATH = "/recettes"

    /** `apple/180/SearchView.swift:233` */
    const val SEARCH_PATH = "/recherche"
    const val SEARCH_TITLE = "Recherche"

    /** `apple/180/FavoritesView.swift:125` */
    const val FAVORITES_PATH = "/carnet"
    const val FAVORITES_TITLE = "Carnet"

    /** `apple/180/AccountView.swift:55` */
    const val ACCOUNT_PATH = "/compte"
    const val ACCOUNT_TITLE = "Compte"

    /** `apple/180/LoginView.swift:86` */
    const val LOGIN_PATH = "/connexion"
    const val LOGIN_TITLE = "Connexion"

    /** `apple/180/NotificationsView.swift:25` */
    const val NOTIFICATIONS_PATH = "/notifications"
    const val NOTIFICATIONS_TITLE = "Notifications"

    /** `apple/180/OnboardingView.swift:36` */
    const val ONBOARDING_PATH = "/onboarding"
    const val ONBOARDING_TITLE = "Onboarding"

    /**
     * Fiche recette : url calquée sur le site (`/recette/{slug}`), repli sur
     * l'id quand le slug manque — recette relue d'un cache antérieur à son
     * décodage (`apple/180/RecipeDetailView.swift:92-97`).
     */
    fun recipePath(slug: String?, id: Int): String =
        "/recette/${slug?.takeIf { it.isNotEmpty() } ?: id}"
}
