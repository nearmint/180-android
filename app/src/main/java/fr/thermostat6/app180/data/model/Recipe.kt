package fr.thermostat6.app180.data.model

import com.google.gson.annotations.SerializedName
import fr.thermostat6.app180.data.model.RecipeTaxonomy.CATEGORY
import fr.thermostat6.app180.data.model.RecipeTaxonomy.PUBLICATION
import fr.thermostat6.app180.data.model.RecipeTaxonomy.SEASON
import fr.thermostat6.app180.util.stripHtml

/**
 * Recette décodée depuis `wp/v2/recipe?_embed` (CPT `recipe`, champs ACF).
 *
 * Le contenu n'est plus reconstruit en parsant `content.rendered` : il est lu
 * directement dans les champs structurés exposés en REST par le thème. Les noms
 * de champs sont ceux **déclarés par le serveur** — voir la fixture
 * `recipe_rest_schema.json`, capturée depuis `/wp-json/wp/v2/`.
 *
 * Miroir iOS : `apple/180/Models.swift:10-124`.
 */
data class Recipe(
    @SerializedName("id")      val id: Int,
    /**
     * Slug WordPress. Porte l'url de mesure de la fiche (`/recette/{slug}`),
     * calquée sur le site ; repli sur l'`id` s'il manque
     * (`apple/180/Models.swift:15-16`).
     *
     * Optionnel : les instantanés d'accueil déjà écrits sur disque
     * ([HomeSnapshot]) et le carnet hors ligne ont été sérialisés avant que ce
     * champ existe — les relire ne doit pas échouer.
     */
    @SerializedName("slug")    val slug: String? = null,
    @SerializedName("date")    val date: String,
    /**
     * Date de dernière modification côté serveur (`modified`, heure du site).
     *
     * Optionnelle : elle n'était pas décodée avant la mise en cache hors ligne,
     * et les instantanés d'accueil déjà écrits sur disque ([HomeSnapshot]) ne la
     * portent pas — les relire ne doit pas échouer. Sert de jeton de fraîcheur à
     * la réconciliation du carnet hors ligne (`apple/180/Models.swift:19`).
     */
    @SerializedName("modified") val modified: String? = null,
    @SerializedName("link")    val link: String? = null,
    @SerializedName("title")   val title: RenderedContent,
    @SerializedName("excerpt") val excerpt: RenderedContent? = null,
    /**
     * Corps Gutenberg rendu. **Jamais affiché** : le rendu lit les champs ACF
     * structurés. Décodé uniquement pour y détecter d'éventuelles images de
     * contenu à embarquer hors ligne. Optionnel — le serveur le tronque pour les
     * recettes verrouillées (`apple/180/Models.swift:26`).
     */
    @SerializedName("content") val content: RenderedContent? = null,

    // ── Champs ACF structurés (lecture seule, register_rest_field) ────────────
    @SerializedName("recipe_intro")       val recipeIntro: String? = null,
    @SerializedName("servings")           val servings: Int? = null,
    @SerializedName("servings_unit")      val servingsUnit: String? = null,
    @SerializedName("recipe_is_premium")  val recipeIsPremium: Boolean? = null,
    @SerializedName("recipe_locked")      val recipeLocked: Boolean? = null,
    @SerializedName("ingredients_groups") val ingredientsGroups: List<IngredientGroup>? = null,
    @SerializedName("steps")              val steps: List<RecipeStep>? = null,
    /** ID du produit WooCommerce (numéro papier) dont la recette est issue. */
    @SerializedName("source_issue")       val sourceIssue: Int? = null,

    // ── Termes de taxonomie (IDs) — exposés nativement par le CPT ─────────────
    @SerializedName("recipe_category")    val recipeCategory: List<Int>? = null,
    @SerializedName("recipe_season")      val recipeSeason: List<Int>? = null,
    @SerializedName("recipe_publication") val recipePublication: List<Int>? = null,
    @SerializedName("recipe_tag")         val recipeTag: List<Int>? = null,

    @SerializedName("featured_media")     val featuredMedia: Int? = null,
    @SerializedName("_embedded")          val embedded: Embedded? = null
) {

    // ── Helpers d'affichage ──────────────────────────────────────────────────

    val cleanTitle: String get() = title.rendered.stripHtml()

    val cleanExcerpt: String get() = (excerpt?.rendered ?: "").stripHtml()

    val imageURL: String? get() = embedded?.wpFeaturedmedia?.firstOrNull()?.sourceURL

    /** Tous les termes embarqués d'une taxonomie donnée (via `_embed`). */
    fun embeddedTerms(taxonomy: String): List<EmbeddedTerm> =
        embedded?.wpTerm.orEmpty().flatten().filter { it.taxonomy == taxonomy }

    private fun embeddedTerm(taxonomy: String): EmbeddedTerm? =
        embeddedTerms(taxonomy).firstOrNull()

    /** Slugs des termes embarqués (filtrage local). */
    val categorySlugs: List<String> get() = embeddedTerms(CATEGORY).map { it.slug }
    val seasonSlugs: List<String> get() = embeddedTerms(SEASON).map { it.slug }

    /** Nom du type de plat (`recipe_category`) pour le label carte. */
    val categoryName: String? get() = embeddedTerm(CATEGORY)?.name

    /** Nom de la saison (`recipe_season`) pour le label carte. */
    val seasonName: String? get() = embeddedTerm(SEASON)?.name

    /** Nom de la publication d'origine (`recipe_publication`). */
    val publicationName: String? get() = embeddedTerm(PUBLICATION)?.name

    val isPremium: Boolean get() = recipeIsPremium ?: true

    /**
     * Contenu premium verrouillé pour l'utilisateur courant.
     *
     * Source de vérité = `recipe_locked` renvoyé par l'API (gating serveur). En
     * l'absence du champ, repli sur l'état premium — verrouillé par défaut, pour
     * qu'un champ manquant ne puisse jamais faire fuiter du contenu réservé.
     */
    val isLocked: Boolean get() = recipeLocked ?: isPremium

    /** Introduction éditoriale nettoyée. */
    val introText: String get() = (recipeIntro ?: "").stripHtml()

    /** Libellé de portions, ex. « Pour 4 personnes ». */
    val servingsText: String?
        get() {
            val count = servings ?: return null
            if (count <= 0) return null
            val unit = servingsUnit?.takeIf { it.isNotEmpty() } ?: "personnes"
            return "Pour $count $unit"
        }

    /** Groupes d'ingrédients non vides. */
    val ingredientGroups: List<IngredientGroup>
        get() = ingredientsGroups.orEmpty().filter { it.lines.isNotEmpty() }

    /** Étapes de préparation porteuses de contenu. */
    val preparationSteps: List<RecipeStep>
        get() = steps.orEmpty().filter { it.cleanContent.isNotEmpty() || it.cleanTitle.isNotEmpty() }
}

/** `rest_base` des taxonomies du CPT `recipe` (cf. fixture `taxonomies.json`). */
object RecipeTaxonomy {
    /** Type de plat (hiérarchique) — Entrée, Plat, Dessert, Apéro… */
    const val CATEGORY = "recipe_category"
    /** Saison (plate) — Printemps, Été, Automne, Hiver, Toute saison. */
    const val SEASON = "recipe_season"
    /** Publication d'origine (plate) — Les Cahiers de Delphine, 180°C, 12°5… */
    const val PUBLICATION = "recipe_publication"
}

// ── Repeaters ACF ────────────────────────────────────────────────────────────

/** Groupe d'ingrédients (`ingredients_groups`) : libellé optionnel + lignes. */
data class IngredientGroup(
    @SerializedName("group_label") val groupLabel: String? = null,
    @SerializedName("items")       val items: List<IngredientItem>? = null
) {
    val label: String get() = groupLabel.orEmpty()

    /** Lignes d'ingrédients en texte simple, vides écartées. */
    val lines: List<String>
        get() = items.orEmpty().mapNotNull { it.line?.takeIf { l -> l.isNotBlank() } }
}

/** Une ligne d'ingrédient (`items[].line`). */
data class IngredientItem(
    @SerializedName("line") val line: String? = null
)

/** Étape de préparation (`steps`) : titre optionnel + contenu HTML. */
data class RecipeStep(
    @SerializedName("step_title")   val stepTitle: String? = null,
    @SerializedName("step_content") val stepContent: String? = null
) {
    val cleanTitle: String get() = (stepTitle ?: "").stripHtml()
    val cleanContent: String get() = (stepContent ?: "").stripHtml()
}

// ── Embed (`_embed`) ─────────────────────────────────────────────────────────

data class Embedded(
    @SerializedName("wp:featuredmedia") val wpFeaturedmedia: List<EmbeddedMedia>? = null,
    /** `wp:term` — termes groupés par taxonomie (un sous-tableau par taxonomie). */
    @SerializedName("wp:term")          val wpTerm: List<List<EmbeddedTerm>>? = null
)

data class EmbeddedMedia(
    @SerializedName("source_url") val sourceURL: String
)

/** Terme de taxonomie embarqué (`_embed` → `wp:term`). */
data class EmbeddedTerm(
    @SerializedName("id")       val id: Int? = null,
    @SerializedName("name")     val name: String,
    @SerializedName("slug")     val slug: String,
    @SerializedName("taxonomy") val taxonomy: String
)

data class RenderedContent(
    @SerializedName("rendered") val rendered: String
)
