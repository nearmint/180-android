package fr.thermostat6.app180.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import fr.thermostat6.app180.data.analytics.UmamiScreens
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.model.AppNotification
import fr.thermostat6.app180.data.analytics.AnalyticsService
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.ApiConfig
import fr.thermostat6.app180.data.push.NotificationCenter
import fr.thermostat6.app180.data.push.NotificationDestination
import fr.thermostat6.app180.data.push.NotificationRouter
import fr.thermostat6.app180.data.push.PushPermissionCoordinator
import fr.thermostat6.app180.data.push.PushState
import fr.thermostat6.app180.data.web.WebSession
import fr.thermostat6.app180.navigation.Screen
import fr.thermostat6.app180.ui.theme.Dimens
import fr.thermostat6.app180.ui.components.CachedAsyncImage
import fr.thermostat6.app180.ui.components.NetworkErrorState
import fr.thermostat6.app180.ui.theme.ImageRenderSize
import fr.thermostat6.app180.ui.web.WebLauncher
import fr.thermostat6.app180.util.HapticFeedbackManager
import fr.thermostat6.app180.util.accent180
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Centre de notifications.
 *
 * Le flux vient du serveur ; l'état lu/non-lu est **local**, comme sur iOS
 * (`apple/180/NotificationManager.swift:30-31`) — le feed ne le porte pas.
 * Un tap route via [NotificationRouter], la même fonction de décision que pour
 * les taps de push : un seul chemin, pas deux qui divergeraient.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavController) {
    val items     by NotificationCenter.notifications.collectAsStateWithLifecycle()
    val readIds   by NotificationCenter.readIds.collectAsStateWithLifecycle()
    val isLoading by NotificationCenter.isLoading.collectAsStateWithLifecycle()
    val error     by NotificationCenter.error.collectAsStateWithLifecycle()
    val context   = LocalContext.current

    // Même notion que la pastille de la cloche : l'invitation et le bandeau
    // s'affichent aussi après un opt-out depuis Mon compte, pas seulement quand
    // l'autorisation système manque (`apple/180/NotificationsView.swift:102`).
    val pushEnabled by PushState.isPushEffectivelyEnabled.collectAsStateWithLifecycle()
    val pushDenied  by PushState.isDenied.collectAsStateWithLifecycle()

    // Refermé pour la session d'écran seulement, comme `@State bannerDismissed`
    // (`NotificationsView.swift:5`) : le bandeau se réarme à la prochaine
    // ouverture. Il est inline et non modal — rien ne justifie de le taire
    // définitivement.
    var bannerDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { NotificationCenter.refresh() }

    // Relu à l'apparition, comme `checkStatus()` (`NotificationsView.swift:22`).
    // Le retour depuis les réglages système est couvert par le
    // `LifecycleResumeEffect` racine (`navigation/AppNavigation.kt:330-333`).
    LaunchedEffect(Unit) { PushState.refresh(context) }

    // La permission runtime ne peut être demandée que par l'Activity.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Point de mesure **unique** de `push_optin`, partagé avec le soft-ask
        // (`AppNavigation.kt:117`) et l'écran Compte
        // (`AccountViewModel.kt:113`) : aucun second point n'est introduit ici.
        val granted = PushPermissionCoordinator.onSystemPromptResult(context)
        AnalyticsService.notificationPermission(granted = granted)
        if (granted) PushPermissionCoordinator.optIn()
        PushState.refresh(context)
    }

    // Abonnement OneSignal puis relecture de l'état : deux branches du CTA y
    // aboutissent, une seule écriture.
    val optInNow: () -> Unit = {
        PushPermissionCoordinator.optIn()
        PushState.refresh(context)
    }

    // Miroir d'`handleActivationCTA` (`apple/180/NotificationManager.swift:104-118`),
    // partagé par l'écran d'invitation et le bandeau inline.
    val onActivate: () -> Unit = {
        when (PushPermissionCoordinator.activationAction(context)) {
            // La garde de version est redondante avec `activationAction` — en
            // deçà d'Android 13 la permission est toujours accordée, donc le
            // chemin est OPT_IN —, mais elle rend la condition de version
            // lisible par lint, comme aux deux autres points de demande
            // (`AppNavigation.kt:139`, `AccountScreen.kt:117`).
            PushPermissionCoordinator.ActivationAction.REQUEST_PERMISSION ->
                if (PushPermissionCoordinator.requiresRuntimePermission) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    optInNow()
                }

            // Autorisé mais désabonné : opt-in direct, **sans** prompt.
            PushPermissionCoordinator.ActivationAction.OPT_IN -> optInNow()

            PushPermissionCoordinator.ActivationAction.OPEN_SETTINGS ->
                PushPermissionCoordinator.openSystemSettings(context)
        }
    }

    // Page vue Umami (`apple/180/NotificationsView.swift:25`).
    LaunchedEffect(Unit) {
        UmamiTracker.trackScreen(
            UmamiScreens.NOTIFICATIONS_PATH,
            UmamiScreens.NOTIFICATIONS_TITLE
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title          = { Text("Notifications") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
            },
            actions        = {
                if (items.any { it.id !in readIds }) {
                    // `apple/180/NotificationsView.swift:110`
                    TextButton(onClick = {
                        NotificationCenter.markAllAsRead()
                        HapticFeedbackManager.light()
                    }) {
                        Text("Tout marquer comme lu")
                    }
                }
            }
        )

        when {
            error != null && items.isEmpty() ->
                NetworkErrorState(message = error!!)

            isLoading && items.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            // Flux vide : invitation à activer si l'abonnement n'est pas
            // effectif, sinon l'état vide neutre (`NotificationsView.swift:12-16`).
            items.isEmpty() ->
                if (pushEnabled) EmptyNotifications()
                else InactiveNotifications(isDenied = pushDenied, onActivate = onActivate)

            else -> LazyColumn(Modifier.fillMaxSize()) {
                // Bandeau discret en tête de liste quand un flux existe sans
                // abonnement effectif (`NotificationsView.swift:100-105`).
                if (!pushEnabled && !bannerDismissed) {
                    item(key = "activation-banner") {
                        InlineActivationBanner(
                            onActivate = onActivate,
                            onDismiss  = { bannerDismissed = true }
                        )
                        HorizontalDivider()
                    }
                }

                items(items = items, key = { it.id }) { notification ->
                    // Charge la page suivante quand la dernière ligne apparaît
                    // (miroir NotificationManager.swift:152-155).
                    LaunchedEffect(notification.id) {
                        NotificationCenter.loadMoreIfNeeded(notification)
                    }
                    NotificationRow(
                        notification = notification,
                        isRead       = notification.id in readIds,
                        onClick      = {
                            // Miroir NotificationsView.swift:122 : `id` en chaîne.
                            AnalyticsService.notificationOpened(
                                id   = notification.id.toString(),
                                type = notification.target?.type.orEmpty()
                            )
                            NotificationCenter.markAsRead(notification.id)
                            // `apple/180/NotificationsView.swift:126`
                            HapticFeedbackManager.selection()
                            when (val target = NotificationRouter.destination(notification.target)) {
                                is NotificationDestination.Recipe ->
                                    navController.navigate(Screen.RecipeDetail.createRoute(target.id))

                                // Connecté : webview authentifiée, avec repli sur
                                // la page publique si l'amorçage échoue.
                                // Visiteur : la page publique directement
                                // (`apple/180/ContentView.swift:143-161`).
                                is NotificationDestination.Product ->
                                    navController.navigate(
                                        Screen.Web.createRoute(
                                            url      = WebSession.productLink(
                                                target.url,
                                                ApiClient.authToken
                                            ),
                                            title    = notification.title,
                                            fallback = target.url
                                        )
                                    )

                                is NotificationDestination.Url ->
                                    WebLauncher.openPublic(context, target.url)

                                // Pas de vue article native — des deux côtés :
                                // ouverture web via le permalien `?p=ID`
                                // (miroir ContentView.swift:127-129).
                                is NotificationDestination.Article ->
                                    WebLauncher.openPublic(context, ApiConfig.webLink("/?p=${target.id}"))

                                NotificationDestination.None -> Unit
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(start = 88.dp))
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    isRead: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            CachedAsyncImage(
                url                = notification.imageUrl,
                contentDescription = null,
                modifier           = Modifier.fillMaxSize(),
                renderSize         = ImageRenderSize.THUMBNAIL
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text       = notification.title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text     = notification.body,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            notification.sentAt?.let { sentAt ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = formatSentAt(sentAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // Pastille de non-lu.
        if (!isRead) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.accent180)
            )
        }
    }
}

@Composable
private fun EmptyNotifications() {
    Box(
        modifier         = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = Icons.Filled.Notifications,
                contentDescription = null,
                modifier           = Modifier.size(64.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text      = "Aucune notification",
                style     = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Écran d'invitation : abonnement push non effectif **et** flux vide.
 *
 * Miroir d'`inactiveState` (`apple/180/NotificationsView.swift:32-73`), libellés
 * repris à l'identique — « iPhone » mis à part.
 */
@Composable
private fun InactiveNotifications(
    isDenied: Boolean,
    onActivate: () -> Unit
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Filled.NotificationsOff,
            contentDescription = null,
            modifier           = Modifier.size(60.dp),
            tint               = Color.accent180
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text      = "Restez informé",
            style     = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text      = "Activez les notifications pour être prévenu de la publication " +
                "des nouvelles recettes.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick  = onActivate,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(Dimens.detailButtonRadius),
            colors   = ButtonDefaults.buttonColors(
                containerColor = Color.accent180,
                contentColor   = Color.White
            )
        ) {
            Text("Activer les notifications", fontWeight = FontWeight.SemiBold)
        }

        // Le prompt système n'est plus présentable une fois refusé : on le dit,
        // et le bouton ci-dessus ouvre les réglages (`NotificationsView.swift:63-69`).
        if (isDenied) {
            Spacer(Modifier.height(24.dp))
            Text(
                text      = "Vous avez refusé les notifications. Vous pouvez les activer " +
                    "depuis les Réglages de votre téléphone.",
                style     = MaterialTheme.typography.labelMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Bandeau refermable en tête de liste : un flux existe, mais l'abonnement push
 * n'est pas effectif (`apple/180/NotificationsView.swift:140-165`).
 */
@Composable
private fun InlineActivationBanner(
    onActivate: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = Icons.Filled.NotificationsActive,
            contentDescription = null,
            tint               = Color.accent180
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text       = "Activez les notifications",
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = "Soyez alerté en direct des nouvelles recettes.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = onActivate) {
            Text(
                text       = "Activer",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = Color.accent180
            )
        }

        IconButton(onClick = onDismiss) {
            Icon(
                imageVector        = Icons.Filled.Close,
                contentDescription = "Masquer",
                modifier           = Modifier.size(16.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** `sent_at` est en ISO 8601 UTC (cf. fixture `notifications.json`). */
private fun formatSentAt(iso: String): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val formatter = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
    formatter.format(parser.parse(iso)!!)
} catch (_: Exception) {
    iso
}
