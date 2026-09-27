package fr.thermostat6.app180.data.push

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Orchestre l'affichage du soft-ask selon ses points d'entrée et **délègue la
 * décision** à [PushPermissionCoordinator].
 *
 * Miroir de `PushSoftAskPresenter`
 * (`apple/180/Services/PushSoftAskPresenter.swift:13-60`).
 *
 * Deux déclencheurs, aucun autre :
 * 1. **première mise en favori** — action utilisateur, moment de plus forte
 *    probabilité d'acceptation (`:25-30`) ;
 * 2. **troisième lancement**, si le soft-ask n'a jamais été montré (`:33-41`).
 *
 * Le presenter ne décide de rien : toutes les règles anti-fatigue vivent dans le
 * coordinateur, consulté et jamais dupliqué.
 */
object PushSoftAskPresenter {

    private const val PREFS_FILE = "app180_push"
    private const val KEY_LAUNCH_COUNT = "push.launchCount"

    /** Nombre de lancements avant la tentative du 2ᵉ point d'entrée (`:39`). */
    private const val LAUNCH_THRESHOLD = 3

    private var prefs: SharedPreferences? = null

    private val _isShown = MutableStateFlow(false)

    /** Pilote la feuille attachée à la racine de la navigation. */
    val isShown: StateFlow<Boolean> = _isShown.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    /**
     * Point d'entrée 1 : après une mise en favori (`:25-30`).
     * Consommé depuis l'émission `didAddFavorite` de `FavoritesManager`.
     */
    fun onFavoriteAdded(context: Context) {
        maybePresent(context)
    }

    /**
     * Point d'entrée 2 : à appeler une fois au lancement. Au [LAUNCH_THRESHOLD]ᵉ
     * lancement, si le soft-ask n'a jamais été montré, tente de le présenter
     * (`:35-41`).
     */
    fun registerLaunchAndMaybePrompt(context: Context) {
        val store = prefs ?: return
        val count = store.getInt(KEY_LAUNCH_COUNT, 0) + 1
        store.edit().putInt(KEY_LAUNCH_COUNT, count).apply()

        if (count < LAUNCH_THRESHOLD) return
        if (PushPermissionCoordinator.hasEverShownSoftAsk) return
        maybePresent(context)
    }

    /** Décision déléguée au coordinateur (`:43-47`). */
    private fun maybePresent(context: Context) {
        val allowed = PushPermissionCoordinator.shouldShowSoftAsk(
            isPermissionUndetermined = PushPermissionCoordinator.isPermissionUndetermined(context)
        )
        if (!allowed) return
        PushPermissionCoordinator.markSoftAskShown()
        _isShown.value = true
    }

    /**
     * « Activer les notifications » : ferme la feuille et laisse l'appelant
     * déclencher la demande système (`:49-53`) — sur Android seule une Activity
     * peut la présenter.
     */
    fun activate() {
        _isShown.value = false
    }

    /** « Plus tard » : enregistre le refus et ferme la feuille (`:55-59`). */
    fun decline() {
        PushPermissionCoordinator.registerSoftAskDeclined()
        _isShown.value = false
    }
}
