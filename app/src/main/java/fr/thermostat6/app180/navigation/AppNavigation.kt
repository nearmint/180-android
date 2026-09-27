package fr.thermostat6.app180.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.graphics.Color
import fr.thermostat6.app180.ui.theme.app180
import fr.thermostat6.app180.ui.theme.ScreenTitle
import fr.thermostat6.app180.ui.layout.LocalWindowWidthClass
import fr.thermostat6.app180.ui.layout.isRegularWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.data.push.NotificationCenter
import fr.thermostat6.app180.data.push.NotificationDestination
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import fr.thermostat6.app180.data.push.PushPermissionCoordinator
import fr.thermostat6.app180.data.push.PushSoftAskPresenter
import fr.thermostat6.app180.data.push.PushState
import fr.thermostat6.app180.ui.components.FloatingTabBar
import fr.thermostat6.app180.ui.components.PushSoftAskSheet
import fr.thermostat6.app180.ui.components.TabBarItem
import fr.thermostat6.app180.ui.components.RandomRecipeSheet
import fr.thermostat6.app180.ui.components.ToastManager
import fr.thermostat6.app180.ui.components.ToastType
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import fr.thermostat6.app180.data.sensor.ShakeDetector
import fr.thermostat6.app180.data.service.RandomRecipeService
import fr.thermostat6.app180.util.HapticFeedbackManager
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.thermostat6.app180.data.analytics.AnalyticsService
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.ApiConfig
import fr.thermostat6.app180.data.network.NetworkMonitor
import fr.thermostat6.app180.data.offline.OfflineSyncService
import fr.thermostat6.app180.data.preferences.AppPreferences
import fr.thermostat6.app180.data.push.PushClickListener
import fr.thermostat6.app180.data.web.WebSession
import fr.thermostat6.app180.ui.web.WebLauncher
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.thermostat6.app180.data.favorites.FavoritesManager
import fr.thermostat6.app180.ui.screen.AccountScreen
import fr.thermostat6.app180.ui.screen.FavoritesScreen
import fr.thermostat6.app180.ui.screen.ForceUpdateScreen
import fr.thermostat6.app180.ui.screen.HomeScreen
import fr.thermostat6.app180.ui.screen.NotificationsScreen
import fr.thermostat6.app180.ui.screen.OnboardingScreen
import fr.thermostat6.app180.ui.screen.RecipeDetailScreen
import fr.thermostat6.app180.ui.screen.RecipeListScreen
import fr.thermostat6.app180.ui.web.WebViewScreen
import fr.thermostat6.app180.ui.screen.SearchScreen
import fr.thermostat6.app180.ui.screen.SplashScreen

// ── Destination racine "main" ─────────────────────────────────────────────────

private const val MAIN_ROUTE = "main"

// ── Définition des onglets ────────────────────────────────────────────────────

private data class BottomTab(
    val label: String,
    val icon: ImageVector,
    val startRoute: String
)

private val BOTTOM_TABS = listOf(
    BottomTab("Accueil",   Icons.Filled.Home,     Screen.Home.route),
    BottomTab("Recherche", Icons.Filled.Search,   Screen.Search.route),
    BottomTab("Favoris",   Icons.Filled.Favorite, Screen.Favorites.route),
    BottomTab("Compte",    Icons.Filled.Person,   Screen.Account.route)
)

// ── Point d'entrée de la navigation ──────────────────────────────────────────

/**
 * NavHost racine de l'app.
 *
 * Flux :
 *   Splash → (AppVersionService + hasCompletedOnboarding ?)
 *             ├── version trop ancienne → ForceUpdate (bloquant)
 *             ├── 1ère ouverture       → Onboarding → Main
 *             └── déjà onboardé        → Main
 *
 * [Main] est un [Scaffold] avec une [NavigationBar] à 4 onglets, chacun gérant
 * son propre [NavHostController] pour un back-stack indépendant.
 */
@Composable
fun AppNavigation() {
    val rootNavController = rememberNavController()

    // Soft-ask push : deux points d'entrée, miroir de PushSoftAskPresenter
    // (apple/180/Services/PushSoftAskPresenter.swift:25-41).
    val softAskShown by PushSoftAskPresenter.isShown.collectAsStateWithLifecycle()
    val softAskContext = LocalContext.current
    val softAskPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Point de mesure unique de l'opt-in Umami (`push_optin`), partagé avec
        // la bascule de l'écran Compte.
        val granted = PushPermissionCoordinator.onSystemPromptResult(softAskContext)
        AnalyticsService.notificationPermission(granted = granted)
        if (granted) PushPermissionCoordinator.optIn()
        PushState.refresh(softAskContext)
    }

    // Point d'entrée 1 : première mise en favori (:25-30).
    LaunchedEffect(Unit) {
        FavoritesManager.didAddFavorite.collect {
            PushSoftAskPresenter.onFavoriteAdded(softAskContext)
        }
    }

    // Point d'entrée 2 : au 3ᵉ lancement (:35-41).
    LaunchedEffect(Unit) {
        PushSoftAskPresenter.registerLaunchAndMaybePrompt(softAskContext)
    }

    if (softAskShown) {
        PushSoftAskSheet(
            onActivate = {
                PushSoftAskPresenter.activate()
                if (PushPermissionCoordinator.requiresRuntimePermission) {
                    softAskPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PushPermissionCoordinator.optIn()
                    PushState.refresh(softAskContext)
                }
            },
            onDismiss  = { PushSoftAskPresenter.decline() }
        )
    }

    // Propriétés utilisateur, réévaluées à chaque changement d'identité ou de
    // carnet (miroir ContentView.swift:230).
    val propsLoggedIn   by AuthService.isLoggedIn.collectAsStateWithLifecycle()
    val propsSubscriber by AuthService.isSubscriber.collectAsStateWithLifecycle()
    val propsFavorites  by FavoritesManager.favoriteIds.collectAsStateWithLifecycle()
    val propsPush       by PushState.isPushEffectivelyEnabled.collectAsStateWithLifecycle()
    val propsAppearance by AppPreferences.appearanceMode.collectAsStateWithLifecycle(initialValue = 0)
    val propsNewsletter by AppPreferences.newsletterSubscribed.collectAsStateWithLifecycle(initialValue = false)

    LaunchedEffect(propsLoggedIn, propsSubscriber, propsFavorites.size, propsPush, propsAppearance, propsNewsletter) {
        AnalyticsService.setUserProperties(
            isLoggedIn           = propsLoggedIn,
            isSubscriber         = propsSubscriber,
            newsletterSubscribed = propsNewsletter,
            darkMode             = when (propsAppearance) {
                1    -> "light"
                2    -> "dark"
                else -> "auto"
            },
            notificationsEnabled = propsPush,
            favoritesCount       = propsFavorites.size
        )
    }

    // Fin de session subie — l'utilisateur n'a pas demandé à être déconnecté, il
    // doit comprendre pourquoi (miroir `expireSession()`, AuthService.swift:341).
    //
    // ORDRE CRITIQUE : ce collecteur doit être enregistré **avant** le
    // LaunchedEffect qui appelle `refreshTokenIfNeeded()`. `authError` est un
    // SharedFlow sans replay, et le chemin « jeton illisible » émet sans aucun
    // point de suspension préalable — un collecteur enregistré après perdrait
    // le message. Les LaunchedEffect d'une même composition sont lancés dans
    // l'ordre du source.
    LaunchedEffect(Unit) {
        AuthService.authError.collect { message ->
            ToastManager.show(message, ToastType.INFO)
        }
    }

    LaunchedEffect(Unit) {
        AuthService.refreshTokenIfNeeded()
        // Statut abonné revérifié au lancement, **seulement si connecté**. Sans
        // cette relecture, un abonnement expiré depuis la dernière session
        // laisserait le carnet hors ligne actif indéfiniment — la valeur en
        // cache (`is_subscriber`) survit aux redémarrages. Hors connexion on ne
        // touche à rien : aucune horloge locale ne décide de la fin d'un
        // abonnement.
        if (NetworkMonitor.isConnected.value) {
            AuthService.checkSubscriptionStatus()
        }
        // Carnet rafraîchi depuis le serveur au lancement, si connecté
        // (miroir apple/180/ContentView.swift:53).
        FavoritesManager.refreshFromServer()
        NotificationCenter.refresh()
        // Réconciliation du carnet hors ligne — no-op si le toggle est éteint,
        // l'utilisateur non éligible ou l'appareil hors ligne.
        OfflineSyncService.reconcileIfNeeded()
    }

    // Retour du réseau : on rejoue ce que la coupure a empêché. Le statut abonné
    // d'abord — il conditionne l'éligibilité — puis la réconciliation.
    LaunchedEffect(Unit) {
        NetworkMonitor.isConnected.drop(1).filter { it }.collect {
            AuthService.checkSubscriptionStatus()
            FavoritesManager.refreshFromServer()
            OfflineSyncService.reconcileIfNeeded()
        }
    }

    // Information discrète de fin d'abonnement — un toast, pas une alerte
    // bloquante : l'utilisateur n'a rien à décider.
    LaunchedEffect(Unit) {
        OfflineSyncService.subscriptionLostNotice.collect { message ->
            ToastManager.show(message, ToastType.INFO)
        }
    }

    NavHost(
        navController    = rootNavController,
        startDestination = Screen.Splash.route
    ) {

        // ── Splash ────────────────────────────────────────────────────────────
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToMain = {
                    rootNavController.navigate(MAIN_ROUTE) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    rootNavController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToForceUpdate = { message ->
                    rootNavController.navigate(Screen.ForceUpdate.createRoute(message)) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // ── ForceUpdate ───────────────────────────────────────────────────────
        composable(
            route     = Screen.ForceUpdate.route,
            arguments = listOf(navArgument("message") { type = NavType.StringType })
        ) { backStack ->
            ForceUpdateScreen(message = backStack.arguments?.getString("message") ?: "")
        }

        // ── Onboarding ────────────────────────────────────────────────────────
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onDone = {
                    rootNavController.navigate(MAIN_ROUTE) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Main (Scaffold + BottomNavBar) ────────────────────────────────────
        composable(MAIN_ROUTE) {
            MainScreen()
        }
    }
}

// ── Écran principal avec barre de navigation ──────────────────────────────────

@Composable
private fun MainScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val homeNavController      = rememberNavController()
    val searchNavController    = rememberNavController()
    val favoritesNavController = rememberNavController()
    val accountNavController   = rememberNavController()

    val tabControllers = listOf(
        homeNavController,
        searchNavController,
        favoritesNavController,
        accountNavController
    )

    // Destination en attente d'un tap de notification. Mémorisée par
    // PushClickListener jusqu'à ce que la navigation existe — c'est ce qui fait
    // fonctionner le démarrage à froid (miroir `pendingDestination`,
    // apple/180/Navigation/NotificationRouter.swift:38-41).
    //
    // Le routage passe par l'onglet Accueil : c'est son NavHost qui porte le
    // détail recette, le centre de notifications et le navigateur intégré.
    val pendingDestination by PushClickListener.pendingDestination.collectAsStateWithLifecycle()
    val pushContext = LocalContext.current

    // ── Secouer pour une recette au hasard ────────────────────────────────────
    // Miroir de `ContentView.swift:66-84` : retour haptique dès la détection,
    // puis tirage, puis présentation de la fiche complète en feuille modale.
    var randomRecipeId by rememberSaveable { mutableStateOf<Int?>(null) }
    var isDrawing by rememberSaveable { mutableStateOf(false) }

    // Le capteur ne tourne que pendant que l'app est au premier plan. L'iOS ne
    // rappelle jamais son `stop()` (`ShakeDetectorService.swift:34`, sans
    // appelant) ; un écouteur de capteur oublié réveille le CPU en continu, on
    // le désabonne donc explicitement.
    LifecycleResumeEffect(Unit) {
        ShakeDetector.start(pushContext)
        onPauseOrDispose { ShakeDetector.stop() }
    }

    // ── Retour au premier plan ────────────────────────────────────────────────
    // L'utilisateur a pu couper les notifications dans les Réglages système
    // pendant que l'app était en arrière-plan : l'état est relu à chaque retour,
    // miroir de l'observateur `willEnterForeground`
    // (`apple/180/NotificationManager.swift:42-50`).
    //
    // L'iOS y rappelle aussi `updateBadge()` ; côté Android la pastille dérive
    // d'un `StateFlow` recalculé à chaque changement de liste ou de lecture
    // (`NotificationCenter.kt:158`), elle n'a rien à relire.
    LifecycleResumeEffect(Unit) {
        PushState.refresh(pushContext)
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        ShakeDetector.shakes.collect {
            // Garde de réentrance (`ContentView.swift:67`) : une seconde secousse
            // pendant le chargement ne relance pas un tirage.
            if (isDrawing) return@collect
            HapticFeedbackManager.light()
            isDrawing = true
            randomRecipeId = RandomRecipeService.draw()?.id
            isDrawing = false
        }
    }

    LaunchedEffect(pendingDestination) {
        val destination = pendingDestination ?: return@LaunchedEffect
        when (destination) {
            is NotificationDestination.Recipe -> {
                selectedTab = 0
                homeNavController.navigate(Screen.RecipeDetail.createRoute(destination.id))
            }
            is NotificationDestination.Product -> {
                selectedTab = 0
                // Connecté : webview authentifiée, avec repli sur la page
                // publique si l'amorçage échoue. Visiteur : la page publique
                // directement (`apple/180/ContentView.swift:143-161`).
                // Titre laissé vide : le navigateur retombe sur l'hôte de la
                // destination, comme `titleHost` (`InAppBrowserView.swift:195-200`).
                homeNavController.navigate(
                    Screen.Web.createRoute(
                        url      = WebSession.productLink(destination.url, ApiClient.authToken),
                        title    = "",
                        fallback = destination.url
                    )
                )
            }
            is NotificationDestination.Url ->
                WebLauncher.openPublic(pushContext, destination.url)

            // Pas de vue article native — des deux côtés : ouverture web via
            // le permalien `?p=ID` (miroir ContentView.swift:127-129).
            is NotificationDestination.Article ->
                WebLauncher.openPublic(pushContext, ApiConfig.webLink("/?p=${destination.id}"))

            // Cible inconnue → centre de notifications, jamais de crash
            // (NotificationRouter.swift:44-46).
            NotificationDestination.None -> {
                selectedTab = 0
                homeNavController.navigate(Screen.Notifications.route)
            }
        }
        PushClickListener.consume()
    }

    // Un tap sur l'onglet courant ramène à sa racine ; sinon on change d'onglet.
    // Comportement identique quelle que soit la forme de la navigation.
    val onSelectTab: (Int) -> Unit = { index ->
        if (selectedTab == index) {
            tabControllers[index].popBackStack(
                route     = BOTTOM_TABS[index].startRoute,
                inclusive = false
            )
        } else {
            selectedTab = index
        }
    }

    // Demandes de bascule venues d'ailleurs dans l'arbre — aujourd'hui le CTA
    // « Voir mon carnet » du bandeau hors ligne. On passe par `onSelectTab`
    // plutôt que d'écrire `selectedTab` : un utilisateur déjà sur le Carnet,
    // enfoncé dans une fiche, doit remonter à la racine de l'onglet et non
    // rester bloqué sur une page qu'il vient de demander à quitter.
    LaunchedEffect(Unit) {
        TabRouter.requests.collect { index -> onSelectTab(index) }
    }

    val tabContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> HomeTabNavHost(homeNavController)
                1 -> SearchTabNavHost(searchNavController)
                2 -> FavoritesTabNavHost(favoritesNavController)
                3 -> AccountTabNavHost(accountNavController)
            }

            randomRecipeId?.let { drawnId ->
                RandomRecipeSheet(
                    recipeId      = drawnId,
                    navController = tabControllers[selectedTab],
                    onDismiss     = { randomRecipeId = null }
                )
            }
        }
    }

    // Miroir de la bascule `horizontalSizeClass` de l'iOS
    // (`apple/180/ContentView.swift:41-46`) : `TabView` en compact,
    // `NavigationSplitView` avec barre latérale en regular. Côté Android, la
    // barre latérale prend la forme d'un rail sur écran moyen et d'un tiroir
    // permanent sur écran large — le rail n'a pas la place d'afficher un titre.
    val widthClass = LocalWindowWidthClass.current

    when {
        widthClass == WindowWidthSizeClass.Expanded -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        // Fond du thème : le défaut Material est
                        // `surfaceContainerLow`, une teinte **dérivée** que le
                        // schéma 180°C ne définit pas — elle retombait sur le
                        // gris violacé du gabarit.
                        drawerContainerColor = MaterialTheme.colorScheme.background,
                        modifier             = Modifier.padding(end = 12.dp)
                    ) {
                        // `navigationTitle("180°C")` de la barre latérale iOS
                        // (`ContentView.swift:198`).
                        Text(
                            text     = "180°C",
                            style    = ScreenTitle,
                            modifier = Modifier.padding(24.dp)
                        )
                        BOTTOM_TABS.forEachIndexed { index, tab ->
                            NavigationDrawerItem(
                                selected = selectedTab == index,
                                onClick  = { onSelectTab(index) },
                                icon     = { Icon(tab.icon, contentDescription = null) },
                                label    = { Text(tab.label) },
                                colors   = sidebarDrawerColors(),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            ) {
                tabContent(Modifier)
            }
        }

        widthClass.isRegularWidth -> {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                    BOTTOM_TABS.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick  = { onSelectTab(index) },
                            icon     = { Icon(tab.icon, contentDescription = tab.label) },
                            label    = { Text(tab.label) },
                            colors   = sidebarRailColors()
                        )
                    }
                }
                tabContent(Modifier.weight(1f))
            }
        }

        else -> {
            Scaffold(
                bottomBar = {
                    FloatingTabBar(
                        items         = BOTTOM_TABS.map { TabBarItem(it.label, it.icon) },
                        selectedIndex = selectedTab,
                        onSelect      = onSelectTab
                    )
                }
            ) { innerPadding ->
                tabContent(Modifier.padding(innerPadding))
            }
        }
    }
}

// ── NavHost par onglet ────────────────────────────────────────────────────────

@Composable
private fun HomeTabNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }
        // Navigateur intégré : cible des notifications de type `product`.
        webComposable(navController)

        composable(Screen.Notifications.route) {
            NotificationsScreen(navController)
        }
        recipeDetailComposable(navController)
        recipeListComposable(navController)
    }
}

@Composable
private fun SearchTabNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Search.route) {
        composable(Screen.Search.route) {
            SearchScreen(navController)
        }
        recipeDetailComposable(navController)
        recipeListComposable(navController)
    }
}

@Composable
private fun FavoritesTabNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Favorites.route) {
        composable(Screen.Favorites.route) {
            FavoritesScreen(navController)
        }
        recipeDetailComposable(navController)
        recipeListComposable(navController)
    }
}

@Composable
private fun AccountTabNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Account.route) {
        composable(Screen.Account.route) {
            AccountScreen(
                navController = navController,
                onOpenWeb     = { url, title ->
                    navController.navigate(Screen.Web.createRoute(url, title))
                }
            )
        }
        webComposable(navController)
        // La feuille « recette au hasard » est montée au-dessus de l'onglet
        // courant et lui emprunte son NavController (`:392-398`) : sans cette
        // destination, un tap sur une recommandation depuis l'onglet Compte
        // levait une IllegalArgumentException.
        recipeDetailComposable(navController)
    }
}

// ── Navigateur intégré (liens authentifiés) ───────────────────────────────────

private fun androidx.navigation.NavGraphBuilder.webComposable(navController: NavController) {
    composable(
        route     = Screen.Web.route,
        arguments = listOf(
            navArgument("url")   { type = NavType.StringType },
            navArgument("title") {
                type         = NavType.StringType
                nullable     = true
                defaultValue = null
            },
            navArgument("fallback") {
                type         = NavType.StringType
                nullable     = true
                defaultValue = null
            }
        )
    ) { backStack ->
        val args     = backStack.arguments
        val url      = args?.getString("url").orEmpty()
        val title    = args?.getString("title").orEmpty()
        val fallback = args?.getString("fallback").orEmpty()
        WebViewScreen(
            url         = url,
            title       = title,
            onClose     = { navController.popBackStack() },
            fallbackUrl = fallback
        )
    }
}

// ── Destinations partagées (RecipeList + RecipeDetail) ────────────────────────

/**
 * Détail d'une recette, déclaré dans **les quatre** onglets.
 *
 * Trois entrées y mènent : la navigation interne à l'onglet, le routage d'une
 * notification `recipe` (`:337`), et la feuille « recette au hasard » — celle-ci
 * est présentée par-dessus l'onglet **courant**, quel qu'il soit, et lui emprunte
 * son NavController (`:392-398`). Un onglet qui ne déclare pas cette destination
 * fait donc planter un tap sur une recommandation depuis la feuille.
 *
 * Factorisé plutôt que répété une quatrième fois : l'oubli de cet exemplaire sur
 * l'onglet Compte était précisément le défaut corrigé ici.
 */
private fun androidx.navigation.NavGraphBuilder.recipeDetailComposable(
    navController: NavController
) {
    composable(
        route     = Screen.RecipeDetail.route,
        arguments = listOf(navArgument("recipeId") { type = NavType.IntType })
    ) { backStack ->
        val recipeId = backStack.arguments?.getInt("recipeId") ?: return@composable
        RecipeDetailScreen(recipeId = recipeId, navController = navController)
    }
}

private fun androidx.navigation.NavGraphBuilder.recipeListComposable(
    navController: NavController
) {
    composable(
        route     = Screen.RecipeList.route,
        arguments = listOf(
            navArgument("title")      { type = NavType.StringType },
            navArgument("categorySlug") {
                type         = NavType.StringType
                nullable     = true
                defaultValue = null
            },
            navArgument("seasonSlug") {
                type         = NavType.StringType
                nullable     = true
                defaultValue = null
            },
            navArgument("publicationSlug") {
                type         = NavType.StringType
                nullable     = true
                defaultValue = null
            },
            navArgument("showFilters") {
                type         = NavType.BoolType
                defaultValue = false
            }
        )
    ) { backStack ->
        val args        = backStack.arguments
        val title       = args?.getString("title") ?: ""
        val categorySlug    = args?.getString("categorySlug")?.takeIf { it.isNotEmpty() }
        val seasonSlug      = args?.getString("seasonSlug")?.takeIf { it.isNotEmpty() }
        val publicationSlug = args?.getString("publicationSlug")?.takeIf { it.isNotEmpty() }
        val showFilters = args?.getBoolean("showFilters") ?: false
        RecipeListScreen(
            title       = title,
            categorySlug    = categorySlug,
            seasonSlug      = seasonSlug,
            publicationSlug = publicationSlug,
            showFilters = showFilters,
            navController = navController
        )
    }
}

// ── Sélection des barres latérales ────────────────────────────────────────────
//
// L'iOS ne spécifie **rien** ici : sa barre latérale est un `List(selection:)`
// dans un `NavigationSplitView` (`apple/180/ContentView.swift:202-210`), dont le
// style de sélection appartient au système et n'est teinté que par le
// `.tint(.accent180)` global (`:51`). Il n'y a donc pas de valeur à recopier.
//
// À défaut de référence exploitable, les deux barres latérales reprennent le
// langage du [FloatingTabBar] du téléphone : **actif = glyphe et libellé accent
// sur une surbrillance neutre, inactifs blancs**. C'est la seule façon que
// téléphone et écran large parlent le même design system.
//
// Les défauts Material peignaient l'indicateur en `secondaryContainer`, soit
// l'accent à 22 %, qui compose en brun `#4E3C24` sur fond noir — l'olive que le
// constat C7 de la recette du 29/08 reprochait déjà à la barre du téléphone.

@Composable
private fun sidebarDrawerColors() = NavigationDrawerItemDefaults.colors(
    selectedContainerColor   = MaterialTheme.app180.tabBarSelected,
    unselectedContainerColor = Color.Transparent,
    selectedIconColor        = MaterialTheme.colorScheme.primary,
    selectedTextColor        = MaterialTheme.colorScheme.primary,
    unselectedIconColor      = MaterialTheme.colorScheme.onBackground,
    unselectedTextColor      = MaterialTheme.colorScheme.onBackground
)

@Composable
private fun sidebarRailColors() = NavigationRailItemDefaults.colors(
    selectedIconColor   = MaterialTheme.colorScheme.primary,
    selectedTextColor   = MaterialTheme.colorScheme.primary,
    indicatorColor      = MaterialTheme.app180.tabBarSelected,
    unselectedIconColor = MaterialTheme.colorScheme.onBackground,
    unselectedTextColor = MaterialTheme.colorScheme.onBackground
)
