package fr.thermostat6.app180.data.taxonomy

import fr.thermostat6.app180.data.model.RecipeTaxonomy
import fr.thermostat6.app180.data.model.Term
import fr.thermostat6.app180.data.network.ApiClient
import fr.thermostat6.app180.data.network.RetryPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Résout dynamiquement les termes des taxonomies `recipe_*` (slug → ID) et
 * expose les listes de termes réelles.
 *
 * Les IDs de termes WordPress en dur (cat 38, tags 33–36/19…) ont disparu avec
 * la migration vers le CPT `recipe` : un ID de terme est une donnée de
 * back-office, pas une constante d'application. Les écrans raisonnent désormais
 * en **slugs**, ce magasin charge les termes une fois par taxonomie, les met en
 * cache mémoire, et fournit la correspondance.
 *
 * Slug introuvable → `null` : l'appelant retire alors le filtre de la requête
 * plutôt que d'envoyer un ID faux, qui renverrait des résultats sans rapport.
 *
 * Miroir iOS : `apple/180/RecipeTaxonomyResolver.swift` + `TaxonomyStore.swift`.
 * Périmètre volontairement minimal (LOT-04) : le tri des listes, l'invalidation
 * et les dégradations d'UI relèvent du LOT-10.
 */
object TaxonomyStore {

    private val api get() = ApiClient.wordPressApi

    /** taxonomie → (slug → ID de terme). Rempli à la première demande. */
    private val slugMaps = mutableMapOf<String, Map<String, Int>>()
    private val mutex = Mutex()

    private val _seasons = MutableStateFlow<List<Term>>(emptyList())
    val seasons: StateFlow<List<Term>> = _seasons.asStateFlow()

    private val _dishCategories = MutableStateFlow<List<Term>>(emptyList())
    val dishCategories: StateFlow<List<Term>> = _dishCategories.asStateFlow()

    // ── Résolution slug → ID ─────────────────────────────────────────────────

    /** ID du terme portant [slug] dans [taxonomy], ou `null` s'il n'existe pas. */
    suspend fun termId(taxonomy: String, slug: String): Int? = slugMap(taxonomy)[slug]

    /**
     * Carte slug → ID d'une taxonomie, chargée puis mise en cache.
     *
     * Un échec réseau n'est **pas** mis en cache : on renvoie une carte vide et
     * la prochaine demande retentera, plutôt que de figer l'app sans filtres
     * jusqu'au prochain lancement.
     */
    suspend fun slugMap(taxonomy: String): Map<String, Int> {
        slugMaps[taxonomy]?.let { return it }

        return mutex.withLock {
            slugMaps[taxonomy]?.let { return@withLock it }

            val terms = fetchTerms(taxonomy) ?: return@withLock emptyMap()
            val map = terms.associate { it.slug to it.id }
            slugMaps[taxonomy] = map
            cacheTermList(taxonomy, terms)
            map
        }
    }

    // ── Listes de termes pour l'UI ───────────────────────────────────────────

    /**
     * Charge saisons et types de plat si ce n'est pas déjà fait.
     * L'ordre est celui renvoyé par l'API (le tri est arbitré au LOT-10).
     */
    suspend fun loadIfNeeded() {
        slugMap(RecipeTaxonomy.SEASON)
        slugMap(RecipeTaxonomy.CATEGORY)
    }

    private fun cacheTermList(taxonomy: String, terms: List<Term>) {
        when (taxonomy) {
            RecipeTaxonomy.SEASON   -> _seasons.value = orderedSeasons(terms)
            RecipeTaxonomy.CATEGORY -> _dishCategories.value = orderedDishCategories(terms)
        }
    }

    // ── Ordres d'affichage ───────────────────────────────────────────────────

    /**
     * Ordre chronologique des saisons ; tout slug hors liste passe en fin.
     * Miroir de `seasonOrder` (`apple/180/TaxonomyStore.swift:38`).
     */
    private val SEASON_ORDER = listOf("printemps", "ete", "automne", "hiver", "toute-saison")

    /**
     * Saisons en ordre chronologique plutôt qu'alphabétique — sinon la liste
     * s'affiche « Automne, Été, Hiver, Printemps », ce qui n'a aucun sens pour
     * l'utilisateur. Les slugs inconnus (terme ajouté en back-office) sont
     * relégués en fin et départagés par nom, comme l'iOS
     * (`TaxonomyStore.swift:40-46`).
     */
    internal fun orderedSeasons(terms: List<Term>): List<Term> =
        terms.sortedWith(
            compareBy<Term> { SEASON_ORDER.indexOf(it.slug).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
                .thenBy { it.name }
        )

    /**
     * Types de plat par nombre de recettes décroissant — les catégories les
     * mieux fournies en tête (`TaxonomyStore.swift:33`).
     *
     * `sortedByDescending` est **stable** en Kotlin : à `count` égal, l'ordre
     * renvoyé par l'API est préservé. Le `sorted(by:)` de Swift ne le garantit
     * pas ; c'est le seul écart, et il ne porte que sur des égalités.
     */
    internal fun orderedDishCategories(terms: List<Term>): List<Term> =
        terms.sortedByDescending { it.count }

    private suspend fun fetchTerms(taxonomy: String): List<Term>? =
        runCatching { RetryPolicy.withRetry { api.getTerms(taxonomy = taxonomy) } }.getOrNull()
}
