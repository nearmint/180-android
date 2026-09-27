package fr.thermostat6.app180.navigation

import android.net.Uri

/**
 * Routes de navigation de l'app 180°C.
 *
 * Chaque objet expose :
 *   - [route] : template de route à enregistrer dans le NavHost
 *   - [createRoute] (quand des arguments sont requis) : helper pour construire
 *     la route concrète à passer à NavController.navigate()
 */
sealed class Screen(val route: String) {

    // ── Flux de démarrage ─────────────────────────────────────────────────────

    /** Écran splash animé + vérification de version. */
    data object Splash : Screen("splash")

    /** Onboarding 2 pages (features + connexion) — affiché à la 1ère ouverture. */
    data object Onboarding : Screen("onboarding")

    // ── Onglets principaux ────────────────────────────────────────────────────

    data object Home      : Screen("home")
    data object Search    : Screen("search")
    data object Favorites : Screen("favorites")
    data object Account   : Screen("account")

    // ── Écrans secondaires ────────────────────────────────────────────────────

    /** Centre de notifications (push depuis HomeView). */
    data object Notifications : Screen("notifications")

    /**
     * Navigateur intégré pour les liens **authentifiés** du site.
     *
     * Les liens publics passent par un Custom Tab (hors navigation Compose) ;
     * seuls les liens nécessitant l'autologin ouvrent cet écran, qui peut
     * injecter les cookies WordPress dans son WebView.
     */
    data object Web : Screen("web/{url}?title={title}&fallback={fallback}") {
        /**
         * @param fallback page **publique** à charger si l'amorçage de session
         *   échoue. Vide = refus strict d'ouvrir une page déconnectée
         *   (`apple/180/InAppBrowserView.swift:307-310`) ; renseigné = repli,
         *   pour qu'une notification tapée ouvre toujours quelque chose
         *   (`apple/180/ContentView.swift:154`).
         */
        fun createRoute(url: String, title: String, fallback: String = ""): String =
            "web/${Uri.encode(url)}?title=${Uri.encode(title)}&fallback=${Uri.encode(fallback)}"
    }

    /**
     * Écran bloquant de mise à jour forcée.
     *
     * Route template : `force_update/{message}`
     * Argument : message serveur encodé en URL.
     */
    data object ForceUpdate : Screen("force_update/{message}") {
        fun createRoute(message: String) = "force_update/${Uri.encode(message)}"
    }

    /**
     * Détail d'une recette.
     *
     * Route template : `recipe/{recipeId}`
     * Argument : `recipeId` (Int)
     */
    data object RecipeDetail : Screen("recipe/{recipeId}") {
        fun createRoute(recipeId: Int) = "recipe/$recipeId"
    }

    /**
     * Liste filtrée de recettes.
     *
     * Route template : `recipe_list/{title}?categorySlug=&seasonSlug=&publicationSlug=&showFilters=`
     *
     * Les filtres voyagent en **slugs**, comme les liens iOS
     * (`RecipeListView(title:categorySlug:seasonSlug:publicationSlug:)`,
     * `apple/180/RecipeListView.swift:7-9`). Un slug est stable et lisible ;
     * un ID de terme dépend du back-office et ne survit pas à un deeplink.
     * La résolution slug → ID se fait dans l'écran de destination.
     */
    data object RecipeList : Screen(
        "recipe_list/{title}?categorySlug={categorySlug}&seasonSlug={seasonSlug}" +
            "&publicationSlug={publicationSlug}&showFilters={showFilters}"
    ) {
        /**
         * @param title           Titre affiché dans la toolbar (encodé pour URL).
         * @param categorySlug    Slug `recipe_category` (null = aucun filtre).
         * @param seasonSlug      Slug `recipe_season` (null = aucun filtre).
         * @param publicationSlug Slug `recipe_publication` (null = aucun filtre).
         * @param showFilters     Affiche le bouton filtres dans la toolbar.
         */
        fun createRoute(
            title: String,
            categorySlug: String? = null,
            seasonSlug: String? = null,
            publicationSlug: String? = null,
            showFilters: Boolean = false
        ): String {
            val enc = Uri.encode(title)
            return "recipe_list/$enc" +
                "?categorySlug=${categorySlug ?: ""}" +
                "&seasonSlug=${seasonSlug ?: ""}" +
                "&publicationSlug=${publicationSlug ?: ""}" +
                "&showFilters=$showFilters"
        }
    }
}
