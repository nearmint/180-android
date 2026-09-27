package fr.thermostat6.app180.data.model

import com.google.gson.annotations.SerializedName

/**
 * Terme générique d'une taxonomie `recipe_*` (`wp/v2/recipe_category`, …).
 *
 * `parent` est nullable : les taxonomies plates (saison, publication) n'exposent
 * pas ce champ en REST, contrairement aux hiérarchiques.
 *
 * Miroir iOS : `apple/180/Models.swift:218-224`.
 */
data class Term(
    @SerializedName("id")     val id: Int,
    @SerializedName("count")  val count: Int = 0,
    @SerializedName("name")   val name: String,
    @SerializedName("slug")   val slug: String,
    @SerializedName("parent") val parent: Int? = null
)
