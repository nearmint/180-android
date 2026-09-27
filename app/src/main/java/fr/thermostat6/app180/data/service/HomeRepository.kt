package fr.thermostat6.app180.data.service

import fr.thermostat6.app180.data.model.HomeBlock
import fr.thermostat6.app180.data.model.HomeBlockType
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.visibleInApp
import com.google.gson.reflect.TypeToken
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.RetryPolicy

/** Nombre de recettes de la section « Toutes les recettes » (HomeView.swift:189). */
private const val RECENT_RECIPES_COUNT = 16

/** Clé de l'instantané d'accueil sur disque (`HomeService.swift:135`). */
private const val SNAPSHOT_KEY = "home-snapshot-v1"

/** TTL de l'instantané : 15 min, demande produit (`HomeService.swift:136-137`). */
private const val SNAPSHOT_TTL_MILLIS = 15L * 60L * 1000L

/**
 * Accueil chargé : blocs dans l'ordre serveur + recettes hydratées par bloc.
 *
 * [hydratedByBlock] est indexé par la position du bloc dans [blocks], comme la
 * table `hydrated` de l'iOS (`apple/180/HomeView.swift:475-482`).
 */
data class HomeContent(
    val blocks: List<HomeBlock> = emptyList(),
    val hydratedByBlock: Map<Int, List<Recipe>> = emptyMap(),
    val recentRecipes: List<Recipe> = emptyList(),
    /**
     * `true` dès qu'une composition a pu être **décodée**, réseau ou instantané.
     *
     * Distingue les deux façons d'obtenir zéro bloc : une composition
     * réellement vide en back-office — écran vide, légitime — et un payload
     * jamais obtenu, réseau ou serveur en panne, qui doit produire un état
     * d'erreur. Miroir de `hasLoadedComposition` (`apple/180/HomeView.swift:35`,
     * `:486-497`) : « sans ce témoin, retirer tous les modules dans WordPress
     * afficherait un mur d'erreur trompeur ».
     */
    val hasLoadedComposition: Boolean = false
)

/**
 * Instantané d'accueil sérialisé sur disque.
 *
 * Miroir de `HomeCacheSnapshot` (`apple/180/HomeService.swift:119-129`). Les
 * blocs sont conservés **décodés** plutôt qu'en JSON brut : le décodeur tolérant
 * a déjà écarté les blocs invalides, inutile de refaire ce travail au relecture.
 */
data class HomeSnapshot(
    val blocks: List<HomeBlock> = emptyList(),
    val hydrated: List<Recipe> = emptyList(),
    val recent: List<Recipe> = emptyList()
)

/**
 * Charge l'accueil piloté serveur (`180c/v1/home-recettes`).
 *
 * Miroir d'`apple/180/HomeService.swift:185-214` :
 * - les recettes récentes sont chargées **en parallèle et indépendamment** du
 *   home-builder, ce qui permet un repli si celui-ci échoue ;
 * - les `recipe_ids` de tous les blocs `rail` et `featured` sont réunis et
 *   dédoublonnés, puis hydratés en un lot batché **paginé par 100**
 *   ([RecipeCatalog.fetchByIds]) ;
 * - chaque bloc est ensuite réordonné selon ses propres IDs, l'API `include` ne
 *   garantissant pas l'ordre (`HomeView.swift:507-511`).
 */
object HomeRepository {

    private val wordPressApi get() = ApiClient.wordPressApi
    private val restApi get() = ApiClient.userApi

    /**
     * Dernier instantané, pour un affichage immédiat au lancement. `null` s'il
     * n'y en a pas (`HomeService.swift:173-176`).
     *
     * @param ignoreTtl hors connexion, l'âge de l'instantané ne doit plus rien
     *   décider. Le TTL de 15 minutes existe pour éviter de servir du contenu
     *   périmé **alors qu'on peut aller en chercher du frais** ; hors réseau
     *   cette alternative n'existe pas, et l'appliquer quand même ne troque pas
     *   du contenu daté contre du contenu à jour — il le troque contre rien. Le
     *   bandeau hors ligne, affiché au-dessus, dit déjà que le contenu peut
     *   avoir vieilli.
     *
     *   La décision est passée en paramètre plutôt que lue ici : le dépôt reste
     *   ignorant de la connectivité, et la règle est visible au point d'appel.
     */
    fun cachedContent(ignoreTtl: Boolean = false): HomeContent? {
        val snapshot: HomeSnapshot = DiskCache.load(
            key          = SNAPSHOT_KEY,
            typeOfT      = TypeToken.get(HomeSnapshot::class.java).type,
            maxAgeMillis = if (ignoreTtl) Long.MAX_VALUE else SNAPSHOT_TTL_MILLIS
        ) ?: return null
        return snapshot.toContent()
    }

    /**
     * @param recent recettes récentes déjà chargées en parallèle par l'appelant.
     */
    suspend fun loadHome(recent: List<Recipe>): HomeContent =
        // Home-builder injoignable : `hasLoadedComposition` reste faux et l'UI
        // pose un état d'erreur. Le cache n'est **pas** réécrit, faute de blocs
        // (HomeService.swift:206-213). Les recettes récentes sont tout de même
        // transportées : elles alimentent le module `grid_paginated` dès qu'une
        // composition revient, mais ne sont jamais rendues hors de lui.
        loadSnapshot(recent)?.toContent() ?: HomeContent(recentRecipes = recent)

    /**
     * Charge l'accueil complet et l'écrit sur disque, puis rend l'instantané.
     *
     * Miroir de `HomeService.loadAndCache()` (`HomeService.swift:185-214`) : sert
     * le préchauffage au lancement, qui a besoin des visuels à plat et non de la
     * table indexée par bloc. `null` si le home-builder n'a rien rendu.
     */
    suspend fun loadAndCache(): HomeSnapshot? = loadSnapshot(loadRecentRecipes())

    /**
     * Charge la composition, ou `null` si le payload n'a **pas pu être obtenu**.
     *
     * Une composition vide n'est pas un échec : un `HomeSnapshot` à zéro bloc est
     * rendu, et c'est `HomeContent.hasLoadedComposition` qui portera la
     * différence. Sans cette distinction, vider la composition en back-office
     * afficherait un mur d'erreur au lieu de l'écran vide demandé
     * (`HomeView.swift:57-60`).
     */
    private suspend fun loadSnapshot(recent: List<Recipe>): HomeSnapshot? {
        val blocks = runCatching { RetryPolicy.withRetry { restApi.getHome().blocks } }
            .getOrNull() ?: return null

        val ids = blocks
            .filter { it.type == HomeBlockType.RAIL || it.type == HomeBlockType.FEATURED }
            .flatMap { it.recipeIds.orEmpty() }
            .distinct()

        // Hydratation absorbée en liste vide : l'accueil a déjà sa composition,
        // et un rail non hydraté vaut mieux qu'une page d'erreur
        // (miroir `(try? …) ?? []`, `HomeService.swift:262`). C'est la
        // pagination de [RecipeCatalog.fetchByIds] qui empêche désormais qu'une
        // composition de plus de 100 `recipe_ids` tombe ici systématiquement.
        val hydrated = runCatching { RecipeCatalog.fetchByIds(ids) }.getOrElse { emptyList() }

        val snapshot = HomeSnapshot(blocks = blocks, hydrated = hydrated, recent = recent)
        DiskCache.store(value = snapshot, key = SNAPSHOT_KEY)
        return snapshot
    }

    /** Invalide l'instantané (changement d'identité). */
    fun invalidateSnapshot() = DiskCache.remove(SNAPSHOT_KEY)

    /**
     * Reconstruit la table d'hydratation par bloc depuis un instantané plat
     * (`HomeView.swift:472-489`).
     */
    private fun HomeSnapshot.toContent(): HomeContent {
        // Second point d'application du filtre `web_only`. L'iOS n'en a qu'un —
        // son décodage, traversé aussi par la relecture puisqu'il garde le JSON
        // brut (`HomeService.swift:35-38`). Ici l'instantané contient des blocs
        // **déjà décodés**, relus par un `Gson` nu sur lequel
        // `HomePayloadDeserializer` n'est pas enregistré (`DiskCache.kt:27`) :
        // sans ce filtre, un instantané écrit avant l'arrivée du filtre serveur
        // ferait rendre un module réservé au site.
        //
        // Le filtre passe **avant** l'indexation : `hydratedByBlock` est indexé
        // par position, et les positions doivent être celles des blocs
        // réellement rendus.
        val visible = blocks.visibleInApp()

        val byBlock = visible.withIndex()
            .mapNotNull { (index, block) ->
                val blockIds = block.recipeIds.orEmpty()
                if (blockIds.isEmpty()) null else index to RecipeCatalog.ordered(hydrated, blockIds)
            }
            .toMap()

        return HomeContent(
            blocks               = visible,
            hydratedByBlock      = byBlock,
            recentRecipes        = recent,
            // Un instantané existe : une composition a bien été décodée un jour,
            // fût-elle vide.
            hasLoadedComposition = true
        )
    }

    /** Les [RECENT_RECIPES_COUNT] dernières recettes publiées. */
    suspend fun loadRecentRecipes(): List<Recipe> =
        runCatching { RetryPolicy.withRetry { wordPressApi.getRecipes(perPage = RECENT_RECIPES_COUNT) } }
            .getOrElse { emptyList() }
}
