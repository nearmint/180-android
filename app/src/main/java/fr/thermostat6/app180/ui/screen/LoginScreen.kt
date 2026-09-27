package fr.thermostat6.app180.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import fr.thermostat6.app180.ui.theme.Dimens
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.thermostat6.app180.data.analytics.UmamiScreens
import fr.thermostat6.app180.data.analytics.UmamiTracker
import fr.thermostat6.app180.data.network.ApiConfig
import fr.thermostat6.app180.ui.components.AppLogo
import fr.thermostat6.app180.ui.web.WebLauncher
import fr.thermostat6.app180.data.auth.AuthError
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.ui.theme.app180
import fr.thermostat6.app180.util.HapticFeedbackManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── État interne du formulaire ────────────────────────────────────────────────

private sealed interface LoginState {
    data object Idle    : LoginState
    data object Loading : LoginState
    data class  Error(val message: String) : LoginState
    data object Success : LoginState
}

// ── Composable principal ──────────────────────────────────────────────────────

/**
 * Formulaire de connexion dans un [ModalBottomSheet].
 *
 * @param onDismiss Appelé quand la sheet est fermée (après succès ou annulation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val scope        = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var state    by remember { mutableStateOf<LoginState>(LoginState.Idle) }

    // Page vue Umami. La connexion est une feuille modale des deux côtés, elle
    // compte quand même comme un écran (`apple/180/LoginView.swift:86`).
    LaunchedEffect(Unit) {
        UmamiTracker.trackScreen(UmamiScreens.LOGIN_PATH, UmamiScreens.LOGIN_TITLE)
    }

    // Auto-dismiss 1,5 s après le succès
    LaunchedEffect(state) {
        if (state is LoginState.Success) {
            // Posé à l'apparition de l'écran de succès, comme l'iOS
            // (`apple/180/LoginView.swift:110`).
            HapticFeedbackManager.success()
            delay(1_500L)
            onDismiss()
        }
    }

    val doLogin = {
        if (username.isNotBlank() && password.isNotBlank() && state !is LoginState.Loading) {
            state = LoginState.Loading
            scope.launch {
                val result = AuthService.login(username.trim(), password)
                state = result.fold(
                    onSuccess = { LoginState.Success },
                    // La cause réelle, telle qu'`AuthService` l'a typée : une
                    // panne réseau ou une indisponibilité du service ne doit pas
                    // s'afficher en « identifiants incorrects », qui envoie
                    // l'utilisateur retaper un mot de passe pourtant valable
                    // (AND-07). Le repli ne sert qu'au cas — impossible
                    // aujourd'hui — d'un échec non typé.
                    onFailure = { error ->
                        LoginState.Error(
                            (error as? AuthError)?.displayMessage
                                ?: AuthError.ServerError.displayMessage
                        )
                    }
                )
            }
        }
    }

    // Le prénom arrive **après** la connexion : `AuthService.login()` rend la
    // main dès le jeton obtenu et charge le profil dans sa propre coroutine
    // (AuthService.kt:194-200). L'écran de succès l'observe donc plutôt que de
    // le lire une fois, exactement comme l'iOS qui lit `auth.firstName` dans son
    // corps de vue (`apple/180/LoginView.swift:104`).
    val firstName by AuthService.firstName.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state is LoginState.Success) {
                // Écran de succès : il **remplace** le formulaire, comme
                // l'`.overlay` plein écran de l'iOS
                // (`apple/180/LoginView.swift:92-118`).
                LoginSuccess(
                    // « Bienvenue, {prénom} », repli sur l'identifiant saisi
                    // tant que le profil n'est pas revenu (`LoginView.swift:104`).
                    displayName = firstName.ifBlank { username.trim() }
                )
                Spacer(Modifier.height(48.dp))
                return@Column
            }

            // ── En-tête ───────────────────────────────────────────────────────
            // Logo puis sous-titre, miroir de `LoginView.swift:19-30`. Le titre
            // « Se connecter » qui tenait cette place faisait doublon avec le
            // bouton du même nom, et l'iOS n'en pose aucun dans le contenu.
            Spacer(Modifier.height(8.dp))

            // 64 dp, la hauteur exacte du logo iOS (`LoginView.swift:24`).
            AppLogo(modifier = Modifier.height(64.dp))

            Spacer(Modifier.height(16.dp))

            Text(
                text      = "Connectez-vous pour accéder à toutes les recettes 180°C",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // ── Champ username ────────────────────────────────────────────────
            // Champs **remplis**, coins 12, avec un placeholder plutôt qu'un
            // libellé flottant : c'est la forme de l'iOS
            // (`apple/180/LoginView.swift:32-44`), et elle s'accorde au champ de
            // recherche du lot dédié.
            TextField(
                value         = username,
                onValueChange = { username = it },
                placeholder   = { Text("Nom d'utilisateur ou email") },
                singleLine    = true,
                enabled       = state !is LoginState.Loading,
                shape         = RoundedCornerShape(Dimens.detailButtonRadius),
                colors        = filledFieldColors(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // ── Champ mot de passe ────────────────────────────────────────────
            TextField(
                value                  = password,
                onValueChange          = { password = it },
                placeholder            = { Text("Mot de passe") },
                singleLine             = true,
                enabled                = state !is LoginState.Loading,
                shape                  = RoundedCornerShape(Dimens.detailButtonRadius),
                colors                 = filledFieldColors(),
                visualTransformation   = PasswordVisualTransformation(),
                keyboardOptions        = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions        = KeyboardActions(onDone = { focusManager.clearFocus(); doLogin() }),
                modifier               = Modifier.fillMaxWidth()
            )

            // ── Message d'erreur ──────────────────────────────────────────────
            if (state is LoginState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = (state as LoginState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Bouton principal ──────────────────────────────────────────────
            when (state) {
                is LoginState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                }
                else -> {
                    Button(
                        onClick  = doLogin,
                        enabled  = username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Se connecter")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Mot de passe oublié ───────────────────────────────────────────
            // Chemin exact de l'iOS : `/mon-compte/lost-password/`
            // (apple/180/LoginView.swift:79). Page publique → Custom Tab.
            val ctx = LocalContext.current
            val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
            TextButton(onClick = {
                WebLauncher.openPublic(ctx, ApiConfig.webLink("/mon-compte/lost-password/"), toolbarColor)
            }) {
                Text("Mot de passe oublié ?")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Écran de succès ───────────────────────────────────────────────────────────

/**
 * Confirmation de connexion, miroir de l'`.overlay` iOS
 * (`apple/180/LoginView.swift:92-118`) : pastille verte, « Connexion réussie ! »
 * et l'accueil nominatif.
 *
 * Purement décoratif : la session est déjà acquise quand cet écran s'affiche
 * (`AuthService.login()` a rendu un succès avant le passage à
 * [LoginState.Success]). Le délai de 1,5 s avant fermeture n'est qu'un temps
 * d'affichage, jamais une condition de la connexion.
 */
@Composable
private fun LoginSuccess(displayName: String) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector        = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint               = MaterialTheme.app180.success,
            modifier           = Modifier.size(60.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text      = "Connexion réussie !",
            style     = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text      = "Bienvenue, $displayName",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Couleurs d'un champ rempli sans ligne de soulignement — `secondarySystemBackground`
 * de l'iOS (`LoginView.swift:37`, `:43`).
 */
@Composable
private fun filledFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledContainerColor  = MaterialTheme.colorScheme.surfaceContainer,
    focusedIndicatorColor   = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor  = Color.Transparent
)
