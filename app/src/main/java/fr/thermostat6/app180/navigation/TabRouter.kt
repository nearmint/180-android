package fr.thermostat6.app180.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Demandes de bascule d'onglet émises depuis n'importe où dans l'arbre.
 *
 * L'onglet sélectionné est un état local de `MainScreen`, ce qui convient tant
 * que seul la barre de navigation le change. Le bandeau hors ligne, lui, est un
 * composant réutilisable monté dans trois écrans différents : il ne peut pas
 * atteindre cet état, et le lui faire descendre en paramètre par trois chaînes
 * de composables serait plus fragile que ce petit routeur.
 *
 * Même parti pris que `PushClickListener.pendingDestination`, déjà en place pour
 * le routage des notifications. Miroir de `TabRouter.shared`
 * (`apple/180/OfflineBanner.swift`).
 *
 * `SharedFlow` et non `StateFlow` : une demande est un **événement**. Avec un
 * état, revenir manuellement sur un autre onglet puis recomposer rejouerait la
 * dernière demande.
 */
object TabRouter {

    /** Index des onglets, aligné sur `BOTTOM_TABS` (`AppNavigation.kt`). */
    object Tab {
        const val HOME = 0
        const val SEARCH = 1
        const val FAVORITES = 2
        const val ACCOUNT = 3
    }

    private val _requests = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Index d'onglet demandé. À collecter dans `MainScreen`. */
    val requests: SharedFlow<Int> = _requests.asSharedFlow()

    /** Demande la bascule vers [tabIndex]. Ne suspend jamais l'appelant. */
    fun select(tabIndex: Int) {
        _requests.tryEmit(tabIndex)
    }
}
