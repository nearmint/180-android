package fr.thermostat6.app180.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.thermostat6.app180.data.auth.AuthService
import fr.thermostat6.app180.data.favorites.FavoritesManager
import fr.thermostat6.app180.data.model.HomeBlock
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.network.ApiError
import fr.thermostat6.app180.data.network.NetworkMonitor
import fr.thermostat6.app180.data.service.HomeRepository
import fr.thermostat6.app180.data.service.RecipeCatalog
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** Blocs dans l'ordre exact envoyé par le serveur. */
    val blocks: List<HomeBlock> = emptyList(),
    /** Recettes hydratées, indexées par position de bloc. */
    val hydratedByBlock: Map<Int, List<Recipe>> = emptyMap(),
    /** Rail Carnet — alimenté par les favoris, réservé aux abonnés. */
    val carnetRecipes: List<Recipe> = emptyList(),
    /** Corps du module `grid_paginated` : les 16 dernières recettes publiées. */
    val recentRecipes: List<Recipe> = emptyList(),
    /**
     * `true` dès qu'une composition a pu être décodée, réseau ou instantané.
     *
     * Sépare l'écran vide légitime — la rédaction a retiré tous les modules —
     * du mur d'erreur. Miroir de `hasLoadedComposition`
     * (`apple/180/HomeView.swift:31-35`).
     */
    val hasLoadedComposition: Boolean = false,
    /**
     * Nature de la panne, pour un état d'erreur **typé** plutôt qu'un message
     * figé. Non nul **uniquement** quand aucune composition n'a jamais pu être
     * décodée. Miroir de `loadErrorKind` (`apple/180/HomeView.swift:36-40`).
     */
    val error: ApiError? = null
)

/**
 * ViewModel de l'écran d'accueil, **piloté par le serveur**.
 *
 * L'état est la liste ordonnée des blocs de `180c/v1/home-recettes` : la
 * rédaction compose l'accueil depuis le back-office, sans nouvelle version
 * d'app. Les rails codés en dur posés en interim au LOT-04 (à la une tiré côté
 * app, dernières recettes, saison courante, Cahiers de Delphine) ont disparu —
 * si le serveur veut un rail de ce type, il l'envoie.
 *
 * Miroir iOS : `apple/180/HomeView.swift` + `HomeService.swift`.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadAll()

        // Le carnet dépend du compte : on le recharge à chaque changement de
        // favoris (miroir `.task(id:)` de HomeView.swift:107) et à chaque
        // changement de session.
        viewModelScope.launch {
            FavoritesManager.favoriteIds.drop(1).collect { reloadCarnet() }
        }
        viewModelScope.launch {
            AuthService.contentGeneration.drop(1).collect {
                // Changement d'identité : l'instantané porte le verrouillage de
                // la session précédente, il ne doit pas être re-servi tel quel.
                HomeRepository.invalidateSnapshot()
                loadAll()
            }
        }
    }

    // ── Chargement ────────────────────────────────────────────────────────────

    fun loadAll(isRefresh: Boolean = false) {
        viewModelScope.launch {
            // 1. Stale-while-revalidate : rendu immédiat depuis le dernier
            //    instantané disque (miroir HomeView.swift:431-435). En ligne il
            //    doit avoir moins de 15 min ; hors ligne son âge est ignoré,
            //    faute de pouvoir aller chercher mieux — un accueil daté vaut
            //    mieux qu'un mur d'erreur, et le bandeau le dit déjà.
            val cached = if (isRefresh) {
                null
            } else {
                HomeRepository.cachedContent(ignoreTtl = !NetworkMonitor.isConnected.value)
            }
            _uiState.value = when {
                isRefresh     -> _uiState.value.copy(isRefreshing = true)
                cached != null -> _uiState.value.copy(
                    isLoading            = false,
                    blocks               = cached.blocks,
                    hydratedByBlock      = cached.hydratedByBlock,
                    recentRecipes        = cached.recentRecipes,
                    hasLoadedComposition = cached.hasLoadedComposition,
                    error                = null
                )
                else          -> _uiState.value.copy(isLoading = true, error = null)
            }

            // 2. Revalidation réseau (réécrit l'instantané).
            supervisorScope {
                // Recettes récentes chargées en parallèle et **indépendamment**
                // du home-builder (HomeService.swift:186-189). Elles ne sont pas
                // un contenu de repli : elles alimentent le corps du module
                // `grid_paginated` et ne sont jamais rendues hors de lui.
                val recentDef = async { HomeRepository.loadRecentRecipes() }
                val carnetDef = async { loadCarnetRecipes() }

                val content = HomeRepository.loadHome(recent = recentDef.await())
                val carnet = carnetDef.await()

                val previous = _uiState.value
                _uiState.value = when {
                    // Une composition est revenue. Zéro bloc est un résultat
                    // légitime — la rédaction a vidé la page — et non un échec.
                    content.hasLoadedComposition -> HomeUiState(
                        isLoading            = false,
                        isRefreshing         = false,
                        blocks               = content.blocks,
                        hydratedByBlock      = content.hydratedByBlock,
                        carnetRecipes        = carnet,
                        recentRecipes        = content.recentRecipes,
                        hasLoadedComposition = true,
                        error                = null
                    )

                    // Aucune composition n'a **jamais** pu être décodée, ni
                    // depuis l'instantané ni depuis le réseau → mur d'erreur
                    // typé (HomeView.swift:486-493).
                    !previous.hasLoadedComposition -> previous.copy(
                        isLoading     = false,
                        isRefreshing  = false,
                        carnetRecipes = carnet,
                        // Le dépôt absorbe l'erreur — il rend un instantané ou
                        // rien — donc c'est le `NetworkMonitor` qui tranche
                        // entre « hors ligne » et « le serveur ne répond pas »
                        // (HomeView.swift:37-39, :492).
                        error         = if (NetworkMonitor.isConnected.value) {
                            ApiError.ServerError
                        } else {
                            ApiError.Offline
                        }
                    )

                    // Une composition, même périmée, est déjà à l'écran : on la
                    // garde plutôt que de la remplacer par un mur d'erreur
                    // (HomeView.swift:494-496).
                    else -> previous.copy(
                        isLoading     = false,
                        isRefreshing  = false,
                        carnetRecipes = carnet,
                        error         = null
                    )
                }
            }
        }
    }

    /** Recharge toutes les sections (pull-to-refresh). */
    fun refreshAll() = loadAll(isRefresh = true)

    // ── Rail Carnet ───────────────────────────────────────────────────────────

    private fun reloadCarnet() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(carnetRecipes = loadCarnetRecipes())
        }
    }

    /**
     * Recettes du rail Carnet.
     *
     * Réservé aux **abonnés** : `HomeView.swift:222` conditionne le rendu à
     * `auth.isSubscriber`, et `loadCarnet()` (`:563-576`) court-circuite l'appel
     * pour tout autre visiteur. Les IDs viennent du carnet synchronisé du LOT-07,
     * et l'ordre du carnet est préservé (`HomeView.swift:575`).
     *
     * L'échec est absorbé en liste vide : le rail disparaît, le reste de
     * l'accueil est intact — miroir de `(try? …) ?? []`
     * (`HomeView.swift:574`). Au-delà de 100 favoris, cet échec était
     * systématique (`per_page` > 100 ⇒ 400) : c'est la pagination de
     * [RecipeCatalog.fetchByIds] qui le lève.
     */
    private suspend fun loadCarnetRecipes(): List<Recipe> {
        if (!AuthService.isSubscriber.value) return emptyList()
        val ids = FavoritesManager.favoriteIds.value.toList()
        if (ids.isEmpty()) return emptyList()
        val recipes = runCatching { RecipeCatalog.fetchByIds(ids) }.getOrElse { emptyList() }
        return RecipeCatalog.ordered(recipes, ids)
    }
}
