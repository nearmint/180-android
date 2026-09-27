package fr.thermostat6.app180.data.model

import com.google.gson.annotations.SerializedName

/**
 * Réponse allégée de la sonde de fraîcheur (`_fields=id,modified`).
 *
 * Miroir de `RecipeFreshness` (`apple/180/APIService.swift:269-272`).
 *
 * `_fields` réduit la réponse à quelques dizaines d'octets par recette (contre
 * ~10 Ko avec `_embed`) : la réconciliation du carnet hors ligne décide ainsi
 * **quoi** re-télécharger sans rapatrier ce qu'elle possède déjà.
 */
data class RecipeFreshness(
    @SerializedName("id")       val id: Int,
    @SerializedName("modified") val modified: String? = null
)

/**
 * Découpage des IDs en pages acceptées par `wp/v2`.
 *
 * Logique **pure**, isolée du réseau pour être testable : c'est elle qui évite
 * le 400 que produit un `per_page` supérieur à la borne serveur.
 *
 * Sert la sonde de fraîcheur **et** le chargement du carnet — d'où le nom
 * générique : la borne serveur ne dépend pas de ce qu'on va chercher.
 *
 * Miroir iOS : `RecipePaging` (`apple/180/RecipePaging.swift`).
 */
object RecipePaging {

    /**
     * Nombre maximum d'éléments par page accepté par `wp/v2` — au-delà, le
     * serveur répond 400 (`apple/180/APIService.swift:167`).
     */
    const val MAX_PER_PAGE = 100

    /**
     * Découpe [ids] en pages d'au plus [MAX_PER_PAGE] éléments, **dans l'ordre
     * d'entrée**. Une liste vide ne produit aucune page.
     */
    fun chunk(ids: List<Int>, maxPerPage: Int = MAX_PER_PAGE): List<List<Int>> {
        require(maxPerPage > 0) { "maxPerPage doit être strictement positif" }
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(maxPerPage)
    }
}
