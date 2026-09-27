package fr.thermostat6.app180.data.offline

import fr.thermostat6.app180.data.model.Recipe
import java.net.URI

/**
 * Diff entre l'état serveur du carnet et le contenu du store local.
 *
 * Isolé du service pour être testable sans réseau ni disque : c'est la seule
 * pièce où une erreur se traduirait par du contenu manquant ou périmé.
 *
 * Miroir de `OfflineSyncPlanner` (`apple/180/OfflineSyncService.swift`).
 */
object OfflineSyncPlanner {

    data class Plan(
        /** À télécharger, **dans l'ordre serveur** (favoris récents d'abord). */
        val toDownload: List<Int> = emptyList(),
        /** À supprimer du disque (retirés du carnet, ou dépubliés). */
        val toDelete: List<Int> = emptyList(),
        /** Déjà présents et à jour — aucun octet réseau. */
        val upToDate: List<Int> = emptyList()
    )

    /**
     * Construit le plan d'un cycle de réconciliation.
     *
     * @param serverIds favoris du compte, ordre serveur (`created_at DESC`).
     * @param serverModified `[id: modified]` renvoyé par la sonde de fraîcheur.
     *   Un ID absent = date indisponible (recette dépubliée, champ muet).
     * @param local métadonnées des fiches déjà sur disque.
     *
     * Règles, de la plus prioritaire à la moins :
     * 1. Fiche locale absente du carnet serveur ⇒ **suppression**.
     * 2. Favori sans fiche locale ⇒ **téléchargement** (couvre aussi la reprise
     *    des échecs : un téléchargement raté n'écrit rien, donc reste absent).
     * 3. Fiche locale sans jeton `modified` ⇒ **téléchargement** (fraîcheur
     *    invérifiable : on ne conserve jamais du contenu qu'on ne sait pas dater).
     * 4. Date serveur indisponible ⇒ **on garde** la version locale.
     *    Re-télécharger à chaque cycle une fiche qu'on ne saura jamais comparer
     *    produirait une boucle de téléchargement permanente.
     * 5. Dates différentes ⇒ **téléchargement** ; identiques ⇒ rien.
     */
    fun plan(
        serverIds: List<Int>,
        serverModified: Map<Int, String>,
        local: List<OfflineRecipeMeta>
    ): Plan {
        val localById = local.associateBy { it.id }
        val serverSet = serverIds.toSet()

        val toDelete = local.map { it.id }
            .filter { it !in serverSet }
            .sorted()

        val toDownload = mutableListOf<Int>()
        val upToDate = mutableListOf<Int>()

        // Ordre serveur préservé, doublons neutralisés.
        val seen = mutableSetOf<Int>()
        serverIds.forEach { id ->
            if (!seen.add(id)) return@forEach

            val meta = localById[id]
            when {
                meta == null                    -> toDownload += id   // règle 2
                meta.modified == null           -> toDownload += id   // règle 3
                serverModified[id] == null      -> upToDate += id     // règle 4
                meta.modified == serverModified[id] -> upToDate += id // règle 5
                else                            -> toDownload += id   // règle 5
            }
        }

        return Plan(toDownload = toDownload, toDelete = toDelete, upToDate = upToDate)
    }
}

/**
 * Extraction des visuels à embarquer pour une fiche — **logique pure**.
 *
 * Miroir de `OfflineSyncService.imageURLs(in:)` et `imageSources(inHTML:)`
 * (`apple/180/OfflineSyncService.swift`).
 */
object OfflineImageExtractor {

    /**
     * Valeurs des attributs `src` des balises `<img>` d'un fragment HTML.
     * Guillemets simples ou doubles, casse libre, espaces tolérés.
     */
    private val IMG_SRC = Regex(
        """<img[^>]+src\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Visuels à embarquer : photo principale **puis** images éventuelles du
     * contenu et des étapes.
     *
     * Aujourd'hui le contenu n'en porte aucune — le rendu lit les champs ACF et
     * le corps Gutenberg des recettes premium est tronqué côté serveur. La
     * détection est donc **défensive** : coût nul tant qu'il n'y a rien à
     * trouver, et la fonctionnalité ne se périme pas si l'éditorial ajoute un
     * jour des visuels d'étapes.
     *
     * @return URLs absolues http(s), dédupliquées, dans l'ordre de découverte.
     */
    fun imageUrls(recipe: Recipe): List<String> {
        // Volontairement nommée `collect` et non `append` : à l'intérieur d'un
        // `buildString`, un `append` local masque `StringBuilder.append` et le
        // fragment HTML reste vide — panne silencieuse, aucune image de corps
        // détectée.
        val seen = LinkedHashSet<String>()

        fun collect(candidate: String?) {
            if (candidate.isNullOrEmpty()) return
            val scheme = runCatching { URI(candidate).scheme }.getOrNull()?.lowercase()
            // Les URI `data:` et les chemins relatifs (schéma absent) sont
            // écartés ici : rien à télécharger, et un `data:` est déjà embarqué.
            if (scheme != "http" && scheme != "https") return
            seen += candidate
        }

        collect(recipe.imageURL)

        val html = buildString {
            append(recipe.content?.rendered.orEmpty())
            append(recipe.recipeIntro.orEmpty())
            recipe.steps.orEmpty().forEach { append(it.stepContent.orEmpty()) }
        }
        imageSources(html).forEach(::collect)

        return seen.toList()
    }

    /** Sources brutes des `<img>` d'un fragment HTML, dans l'ordre d'apparition. */
    fun imageSources(html: String): List<String> {
        if (html.isEmpty()) return emptyList()
        return IMG_SRC.findAll(html).map { it.groupValues[1] }.toList()
    }
}
