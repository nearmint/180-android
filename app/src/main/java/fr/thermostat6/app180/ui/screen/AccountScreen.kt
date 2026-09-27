package fr.thermostat6.app180.ui.screen

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.CreditCard
import fr.thermostat6.app180.BuildConfig
import fr.thermostat6.app180.ui.theme.Dimens
import fr.thermostat6.app180.ui.theme.ScreenTitle
import fr.thermostat6.app180.ui.theme.app180
import fr.thermostat6.app180.util.accent180
import fr.thermostat6.app180.data.analytics.UmamiScreens
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.analytics.AnalyticsService
import fr.thermostat6.app180.data.network.ApiConfig
import fr.thermostat6.app180.data.offline.OfflineStore
import fr.thermostat6.app180.data.offline.OfflineSyncService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import fr.thermostat6.app180.data.push.PushPermissionCoordinator
import fr.thermostat6.app180.data.push.PushState
import fr.thermostat6.app180.data.web.WebSession
import fr.thermostat6.app180.ui.components.AppSwitch
import fr.thermostat6.app180.ui.components.WithOfflineBanner
import fr.thermostat6.app180.ui.web.WebLauncher
import fr.thermostat6.app180.ui.viewmodel.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navController: NavController,
    onOpenWeb: (url: String, title: String) -> Unit
) {
    val vm                = viewModel<AccountViewModel>()
    val isLoggedIn        by vm.isLoggedIn.collectAsStateWithLifecycle()
    val isSubscriber      by vm.isSubscriber.collectAsStateWithLifecycle()
    val username          by vm.username.collectAsStateWithLifecycle()
    val email             by vm.email.collectAsStateWithLifecycle()
    val firstName         by vm.firstName.collectAsStateWithLifecycle()
    val lastName          by vm.lastName.collectAsStateWithLifecycle()
    val newsletterOn      by vm.isNewsletterSubscribed.collectAsStateWithLifecycle()
    val newsletterBusy    by vm.isLoadingNewsletter.collectAsStateWithLifecycle()
    val pushEnabled       by PushState.isPushEffectivelyEnabled.collectAsStateWithLifecycle()
    val pushDenied        by PushState.isDenied.collectAsStateWithLifecycle()
    val newsletterErrorCode by vm.newsletterErrorCode.collectAsStateWithLifecycle()
    val appearanceMode    by vm.appearanceMode.collectAsStateWithLifecycle()
    val context           = LocalContext.current

    // Page vue Umami (`apple/180/AccountView.swift:55`).
    LaunchedEffect(Unit) {
        UmamiTracker.trackScreen(UmamiScreens.ACCOUNT_PATH, UmamiScreens.ACCOUNT_TITLE)
    }

    // Lecture du statut d'opt-in serveur à l'ouverture (AccountView.swift:47).
    LaunchedEffect(isLoggedIn) { if (isLoggedIn) vm.hydrateNewsletter() }
    // Rafraîchi à l'apparition, comme `checkStatus()` iOS (AccountView.swift:120).
    LaunchedEffect(Unit) { PushState.refresh(context) }

    // La permission runtime ne peut être demandée que par l'Activity : le
    // ViewModel pose l'intention, l'écran la satisfait.
    val pushPermissionRequest by vm.pushPermissionRequest.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.onSystemPromptResult(context) }

    LaunchedEffect(pushPermissionRequest) {
        if (pushPermissionRequest) {
            if (PushPermissionCoordinator.requiresRuntimePermission) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // < Android 13 : rien à demander, l'opt-in suffit.
                vm.onPushPermissionHandled(context)
            }
        }
    }

    var showLogin        by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val isLoggingOut     by vm.isLoggingOut.collectAsStateWithLifecycle()

    WithOfflineBanner(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Text(
            text     = "Mon compte",
            style    = ScreenTitle,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        if (!isLoggedIn) {
            NotLoggedInContent(
                onLogin     = { showLogin = true },
                onBoutique  = {
                    AnalyticsService.boutiqueClick()   // AccountView.swift:94
                    onOpenWeb(ApiConfig.webLink("/boutique/"), "Boutique 180°C")
                }
            )
            Spacer(Modifier.height(16.dp))
            HelpSection(
                context             = context,
                username            = "",
                email               = "",
                isSubscriber        = false,
                newsletterOn        = false,
                newsletterErrorCode = null
            )
            Spacer(Modifier.height(16.dp))
            SettingsSection(title = "Paramètres") {
                Column(Modifier.padding(16.dp)) {
                    SettingsRowLabel("Apparence", Icons.Filled.DarkMode)
                    Spacer(Modifier.height(8.dp))
                    AppearancePicker(selected = appearanceMode, onSelect = { vm.setAppearanceMode(it) })
                }
            }
            Spacer(Modifier.height(16.dp))
            LegalSection(onOpen = { url, title -> onOpenWeb(url, title) })
        } else {
            ProfileSection(
                firstName            = firstName,
                lastName             = lastName,
                username             = username,
                email                = email,
                isSubscriber         = isSubscriber,
                onManageSubscription = {
                    onOpenWeb(
                        WebSession.autoLoginLink("/mon-compte/#abonnement", vm.currentToken()),
                        "Mon abonnement"
                    )
                },
                onBoutique           = {
                    AnalyticsService.boutiqueClick()   // AccountView.swift:100
                    onOpenWeb(ApiConfig.webLink("/boutique/"), "Boutique 180°C")
                }
            )

            Spacer(Modifier.height(16.dp))

            // Section toujours visible ; le toggle est désactivé hors
            // abonnement, avec la mention iOS (AccountView.swift:135-155).
            SettingsSection(title = "Notifications et newsletters") {
                // Toggle push en tête de section (AccountView.swift:109-115).
                // Android ne permet pas non plus de révoquer la permission depuis
                // l'app : ce contrôle pilote l'abonnement OneSignal.
                SettingsToggleRow(
                    label    = "Notifications push",
                    icon     = Icons.Filled.NotificationsActive,
                    checked  = pushEnabled,
                    enabled  = !pushDenied,
                    onToggle = { vm.setPushSubscription(!pushEnabled, context) }
                )
                if (pushDenied) {
                    TextButton(
                        onClick  = { vm.openNotificationSettings(context) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Activer dans les Réglages", style = MaterialTheme.typography.bodySmall)
                    }
                }
                SettingsToggleRow(
                    label    = "Les Cahiers de Delphine",
                    icon     = Icons.Filled.MailOutline,
                    checked  = newsletterOn,
                    // Désactivé hors abonnement **et** pendant une mutation
                    // (AccountView.swift:148).
                    enabled  = isSubscriber && !newsletterBusy,
                    busy     = newsletterBusy,
                    onToggle = { vm.setNewsletterSubscribed(!newsletterOn) }
                )
                if (!isSubscriber) {
                    Text(
                        text     = "Réservé aux abonnés 180°C",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )
                }

            }
            Spacer(Modifier.height(16.dp))

            // Recettes hors ligne — logué **et** abonné uniquement. On est déjà
            // dans la branche « logué » ; `isSubscriber` est la moitié
            // manquante. C'est la même condition que l'`isEligible` interne
            // d'`OfflineSyncService`, mais **collectée** : une lecture directe
            // de `AuthService.isSubscriber.value` ne recomposerait pas quand
            // l'abonnement tombe, et la section resterait affichée.
            if (isSubscriber) {
                OfflineRecipesSection()
                Spacer(Modifier.height(16.dp))
            }

            HelpSection(
                context             = context,
                username            = username,
                email               = email,
                isSubscriber        = isSubscriber,
                newsletterOn        = newsletterOn,
                newsletterErrorCode = newsletterErrorCode
            )

            Spacer(Modifier.height(16.dp))

            SettingsSection(title = "Paramètres") {
                Column(Modifier.padding(16.dp)) {
                    SettingsRowLabel("Apparence", Icons.Filled.DarkMode)
                    Spacer(Modifier.height(8.dp))
                    AppearancePicker(selected = appearanceMode, onSelect = { vm.setAppearanceMode(it) })
                }
                TextButton(
                    onClick  = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Se déconnecter", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(16.dp))

            LegalSection(onOpen = { url, title -> onOpenWeb(url, title) })
        }

        // Footer version
        Spacer(Modifier.height(24.dp))
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        } catch (_: Exception) { "—" }
        Column(
            modifier            = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = "Version $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = "© 2026 Éditions Thermostat 6",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    }

    if (showLogin) {
        LoginScreen(onDismiss = { showLogin = false })
    }

    // Écran de transition « À bientôt ! », superposé au contenu du compte.
    // Miroir du `logoutOverlay` iOS (`apple/180/AccountView.swift:623-654`) :
    // l'état est déjà purgé, l'écran ne fait que couvrir la bascule.
    if (isLoggingOut) {
        LogoutTransition()
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title            = { Text("Se déconnecter") },
            text             = { Text("Êtes-vous sûr de vouloir vous déconnecter ?") },
            confirmButton    = {
                TextButton(onClick = { showLogoutDialog = false; vm.logout() }) {
                    Text("Se déconnecter", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Annuler") }
            }
        )
    }
}

// ── Centre d'aide ─────────────────────────────────────────────────────────────

/**
 * Miroir de la section « Centre d'aide » (`apple/180/AccountView.swift:158-248`).
 *
 * « Partager l'app » et « Notez l'app » sont masqués tant que la fiche Play
 * n'existe pas — l'iOS fait de même via `AppStoreInfo.isConfigured`
 * (`AccountView.swift:218-246`), pas de lien factice.
 */
@Composable
private fun HelpSection(
    context: android.content.Context,
    username: String,
    email: String,
    isSubscriber: Boolean,
    newsletterOn: Boolean,
    newsletterErrorCode: String?
) {
    SettingsSection(title = "Centre d'aide") {
        SettingsLinkRow("Contacter la rédaction", Icons.Filled.MailOutline) {
            WebLauncher.openMail(
                context,
                "mailto:${BuildConfig.CONTACT_EDITORIAL_EMAIL}?subject=" +
                    Uri.encode("[App 180°C] Contact rédaction")
            )
        }
        SettingsLinkRow("Contacter le support", Icons.AutoMirrored.Filled.HelpOutline) {
            AnalyticsService.contactSupport()   // AccountView.swift:174
            WebLauncher.openMail(
                context,
                "mailto:${BuildConfig.CONTACT_SUPPORT_EMAIL}" +
                    "?subject=" + Uri.encode("[App 180°C] Demande de support") +
                    "&body=" + Uri.encode(
                        supportDiagnostics(
                            context, username, email, isSubscriber, newsletterOn, newsletterErrorCode
                        )
                    )
            )
        }
        // TODO(LOT-18) : « Partager l'app » et « Notez l'app », masqués tant que
        // la fiche Play Store n'existe pas (miroir AppStoreInfo.isConfigured).
    }
}

/**
 * Bloc de diagnostic joint au mail de support.
 * Miroir de `debugInfo` (`apple/180/AccountView.swift:188-200`).
 */
private fun supportDiagnostics(
    context: android.content.Context,
    username: String,
    email: String,
    isSubscriber: Boolean,
    newsletterOn: Boolean,
    newsletterErrorCode: String? = null
): String {
    val appVersion = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    } catch (_: Exception) {
        "—"
    }
    return buildString {
        append("\n\n---\n")
        append("⚠️ Informations techniques – Ne pas effacer ⚠️\n")
        append("App : 180°C v$appVersion\n")
        append("Android : ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
        append("Appareil : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
        if (username.isNotEmpty() || email.isNotEmpty()) {
            append("Utilisateur : $username ($email)\n")
            append("Abonnement : ${if (isSubscriber) "Abonné" else "Non abonné"}\n")
            // Dernier code de refus serveur : rend la cause lisible en release,
            // sans exposer de code dans l'UI courante (AccountView.swift:183-186, :198).
            val refus = newsletterErrorCode?.let { " · dernier refus: $it" }.orEmpty()
            append("Newsletter : ${if (newsletterOn) "Inscrit" else "Non inscrit"}$refus\n")
        }
        append("---")
    }
}

// ── Informations légales ──────────────────────────────────────────────────────

/** Miroir de la section « Informations légales » (`AccountView.swift:293-319`). */
@Composable
private fun LegalSection(onOpen: (url: String, title: String) -> Unit) {
    SettingsSection(title = "Informations légales") {
        SettingsLinkRow("Mentions légales", Icons.Filled.Description) {
            onOpen(ApiConfig.webLink("/mentions-legales/"), "Mentions légales")
        }
        SettingsLinkRow("Conditions Générales d'Utilisation", Icons.Filled.Description) {
            onOpen(ApiConfig.webLink("/cgv/"), "Conditions Générales d'Utilisation")
        }
        SettingsLinkRow("Politique de confidentialité", Icons.Filled.Description) {
            onOpen(ApiConfig.webLink("/politique-confidentialite/"), "Politique de confidentialité")
        }
    }
}

// ── État non connecté ─────────────────────────────────────────────────────────

/**
 * État non connecté — **même carte que l'état connecté** : une ligne de profil
 * puis les liens directs (`AccountView.swift:348-393`).
 *
 * L'ancien héros centré (avatar géant, deux boutons pleine largeur) était une
 * invention Android : l'iOS ne change pas de langage visuel selon la session,
 * seul le contenu de la première section change.
 */
@Composable
private fun NotLoggedInContent(onLogin: () -> Unit, onBoutique: () -> Unit) {
    SettingsSection {
        AccountHeaderRow(
            title    = "Non connecté",
            subtitle = "Connectez-vous pour accéder à toutes les recettes"
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        SettingsLinkRow("Se connecter", Icons.Filled.Login, onClick = onLogin)
        // « S'abonner » (AccountView.swift:365-372) volontairement NON repris :
        // aucun lien d'achat dans l'app (conformité Google Play, décision du
        // 25/07). C'est le seul écart assumé avec la section 1 de l'iOS.
        SettingsLinkRow("Boutique 180°C", Icons.Filled.ShoppingBag, onClick = onBoutique)
    }
}

/**
 * Ligne d'en-tête de la carte compte : avatar, nom, statut.
 *
 * Miroir du premier `HStack` de la section 1 (`AccountView.swift:68-79` connecté,
 * `:351-364` visiteur) : glyphe de personne **gris** sur disque sombre — jamais
 * des initiales sur pastille accent, qui n'existent nulle part côté iOS.
 */
@Composable
private fun AccountHeaderRow(
    title: String,
    subtitle: String,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector        = Icons.Filled.AccountCircle,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(Dimens.profileAvatarSize)
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor
            )
        }
    }
}

// ── Section profil ────────────────────────────────────────────────────────────

/**
 * Carte compte de l'utilisateur connecté — miroir de la section 1 de l'iOS
 * (`AccountView.swift:66-110`) : ligne de profil, puis les liens directs
 * (abonnement, boutique) **dans la même carte**, sans en-tête de section.
 *
 * « S'abonner » (`AccountView.swift:87-94`) n'est pas repris : aucun lien
 * d'achat dans l'app (exception Google Play permanente).
 */
@Composable
private fun ProfileSection(
    firstName: String,
    lastName: String,
    username: String,
    email: String,
    isSubscriber: Boolean,
    onManageSubscription: () -> Unit,
    onBoutique: () -> Unit
) {
    val displayName = listOf(firstName, lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { username }

    SettingsSection {
        AccountHeaderRow(
            title         = displayName.ifBlank { email },
            // L'iOS n'affiche que le statut d'abonnement sous le nom
            // (`AccountView.swift:75-78`) : vert s'il est abonné, gris sinon.
            subtitle      = if (isSubscriber) "Abonné" else "Non abonné",
            subtitleColor = if (isSubscriber) MaterialTheme.app180.success
                            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        if (isSubscriber) {
            // Seul lien **auto-connecté** de l'app : la page s'ouvre déjà
            // authentifiée grâce à l'amorçage de session (AccountView.swift:80-86).
            SettingsLinkRow("Gérer mon abonnement", Icons.Filled.CreditCard, onClick = onManageSubscription)
        }
        SettingsLinkRow("Boutique 180°C", Icons.Filled.ShoppingBag, onClick = onBoutique)
    }
}

/**
 * Libellé de réglage précédé de son icône accent, pour les lignes qui ne sont ni
 * un lien ni un interrupteur — « Apparence » (`AccountView.swift:471-476`).
 */
@Composable
private fun SettingsRowLabel(label: String, icon: ImageVector) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(Dimens.settingsRowIconSize)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Composants utilitaires ────────────────────────────────────────────────────

// ── Recettes hors ligne ──────────────────────────────────────────────────────

/**
 * Section « Recettes hors ligne » — toggle, progression, espace occupé, purge.
 *
 * Miroir de `offlineSection` (`apple/180/AccountView.swift:526-618`).
 *
 * La position du toggle est dérivée de l'état **réel** du service, jamais d'un
 * état local optimiste (même parti pris que le toggle push). L'extinction n'est
 * pas appliquée par le setter : elle ouvre une confirmation, car elle efface des
 * fiches téléchargées. Tant que l'utilisateur n'a pas confirmé, `isEnabled`
 * reste vrai et le toggle revient de lui-même en position haute.
 *
 * Aucun lien ni bouton d'achat d'abonnement ici, y compris dans les états
 * dégradés : c'est une contrainte Google Play, pas une préférence de design.
 */
@Composable
private fun OfflineRecipesSection() {
    val enabled   by OfflineSyncService.isEnabled.collectAsStateWithLifecycle()
    val syncState by OfflineSyncService.state.collectAsStateWithLifecycle()
    val sizeBytes by OfflineSyncService.cacheSizeBytes.collectAsStateWithLifecycle()

    var showDisableDialog by remember { mutableStateOf(false) }
    var showClearDialog   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { OfflineSyncService.refreshCacheSize() }

    SettingsSection(title = "Recettes hors ligne") {
        SettingsToggleRow(
            label    = "Télécharger mon carnet",
            icon     = Icons.Filled.DownloadForOffline,
            checked  = enabled,
            onToggle = {
                if (enabled) showDisableDialog = true else OfflineSyncService.setEnabled(true)
            }
        )

        val running = syncState as? OfflineSyncService.SyncState.Running
        if (running != null && running.total > 0) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                LinearProgressIndicator(
                    progress = { running.done.toFloat() / running.total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = "Téléchargement… ${running.done}/${running.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (enabled) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Espace occupé", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text  = OfflineStore.formatted(sizeBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick  = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Vider le cache", color = MaterialTheme.colorScheme.error)
            }
        }

        Text(
            text     = "Les recettes de votre carnet sont téléchargées sur cet appareil, " +
                "texte et photos, pour être consultées sans connexion.",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
    }

    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title            = { Text("Supprimer les recettes téléchargées ?") },
            text             = {
                Text(
                    "Votre carnet ne sera plus consultable hors connexion. " +
                        "Vos favoris, eux, sont conservés."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisableDialog = false
                    OfflineSyncService.setEnabled(false)
                }) {
                    Text("Désactiver et supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) { Text("Annuler") }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title            = { Text("Vider le cache hors ligne ?") },
            text             = {
                Text(
                    "Les fiches téléchargées sont supprimées de cet appareil " +
                        "et le téléchargement est désactivé."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    OfflineSyncService.disableAndPurge()
                }) {
                    Text("Vider", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Annuler") }
            }
        )
    }
}

/**
 * Groupe de réglages en carte.
 *
 * @param title en-tête de section ; `null` pour une section sans en-tête —
 *   c'est le cas de la première section de l'iOS, qui porte le profil et ses
 *   liens directs (`AccountView.swift:64`, `:349`).
 */
@Composable
private fun SettingsSection(title: String? = null, content: @Composable () -> Unit) {
    Column {
        // Casse de phrase, comme les `Section("…")` de l'iOS : depuis iOS 15 une
        // liste groupée rend son en-tête tel quel, sans capitalisation
        // (`AccountView.swift:112`, `:171`, `:263`, `:306`).
        if (title != null) {
            Text(
                text     = title,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) { content() }
    }
}

/**
 * Ligne à interrupteur — icône accent, libellé, interrupteur.
 *
 * Chaque `Toggle` de l'iOS porte son icône accent
 * (`AccountView.swift:117-121`, `:143-147`, `:554-558`) ; l'icône passe au gris
 * quand la ligne est inerte, comme le fait l'iOS pour la newsletter hors
 * abonnement (`AccountView.swift:145`).
 */
@Composable
private fun SettingsToggleRow(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    busy: Boolean = false,
    onToggle: () -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = if (enabled) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(Dimens.settingsRowIconSize)
        )
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = if (enabled) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (busy) {
            // Indicateur de mutation en cours (AccountView.swift:142-145).
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        AppSwitch(checked = checked, enabled = enabled, onCheckedChange = { onToggle() })
    }
}

/**
 * Ligne de réglage — **icône accent, libellé, chevron**.
 *
 * Chaque ligne de « Mon compte » porte son icône côté iOS
 * (`AccountView.swift:102-108`, `:171-215`, `:293-319`) : c'est ce qui distingue
 * une carte de réglages d'une simple liste de liens. Le libellé est en couleur
 * principale (`.foregroundColor(.primary)`), jamais en gris.
 */
@Composable
private fun SettingsLinkRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(Dimens.settingsRowIconSize)
        )
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearancePicker(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf("Auto", "Clair", "Sombre")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selected == index,
                onClick  = { onSelect(index) },
                shape    = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) { Text(label) }
        }
    }
}

// ── Écran de transition de déconnexion ────────────────────────────────────────

/**
 * « À bientôt ! » — miroir de `logoutOverlay`
 * (`apple/180/AccountView.swift:626-654`).
 *
 * Purement décoratif : la session est purgée **avant** que cet écran
 * n'apparaisse (`AccountViewModel.logout()`). Il couvre la bascule de l'écran
 * en mode visiteur, il ne la conditionne pas.
 *
 * Le fond est opaque et les clics sont absorbés : rien de ce qui est dessous
 * ne doit rester lisible ni cliquable pendant la bascule.
 */
@Composable
private fun LogoutTransition() {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Absorbe les touchers sans se déclarer cliquable : un
            // `clickable` désactivé ne consomme pas l'événement, et un
            // `clickable` actif ferait annoncer un bouton fantôme par
            // TalkBack.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = Icons.Filled.WavingHand,
                contentDescription = null,
                tint               = Color.accent180,
                modifier           = Modifier.size(50.dp)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text  = "À bientôt !",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
