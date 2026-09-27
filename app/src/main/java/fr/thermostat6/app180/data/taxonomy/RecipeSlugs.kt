package fr.thermostat6.app180.data.taxonomy

/**
 * Slugs des termes des taxonomies `recipe_*`.
 *
 * Plus aucun ID WordPress en dur : un ID de terme appartient au back-office et
 * change d'un environnement à l'autre, alors que le slug est stable et lisible.
 * La résolution slug → ID est faite au runtime par [TaxonomyStore].
 *
 * Miroir iOS : `apple/180/Constants.swift`. Les valeurs sont vérifiées contre
 * les fixtures `taxonomy_recipe_*.json`.
 */

/** Slugs de la taxonomie `recipe_season`. */
object SeasonSlug {
    const val PRINTEMPS = "printemps"
    const val ETE = "ete"
    const val AUTOMNE = "automne"
    const val HIVER = "hiver"
}

/** Slugs de la taxonomie `recipe_category` (types de plat). */
object DishCategorySlug {
    const val ENTREE = "entree"
    const val PLAT = "plat"
    const val DESSERT = "dessert"
    const val APERO = "apero"
    const val ACCOMPAGNEMENT = "accompagnement"
}

/** Slugs de la taxonomie `recipe_publication`. */
object PublicationSlug {
    const val CAHIERS_DELPHINE = "cahiers-de-delphine"
    const val DOUZE_DEGRES_5 = "12degres5"
    const val REVUE_180 = "180c"
    const val SELECTIONS = "selections"
}
