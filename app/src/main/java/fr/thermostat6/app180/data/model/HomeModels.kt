package fr.thermostat6.app180.data.model

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Type

/**
 * Lit un champ en **chaîne sans jamais échouer** : une valeur d'un autre type
 * (nombre, objet, tableau) devient `null` au lieu de faire échouer le décodage
 * du bloc entier.
 *
 * Nécessaire parce que l'adaptateur `String` de Gson lève sur un objet ou un
 * tableau, et que cette exception remonterait jusqu'au `runCatching` de
 * [HomePayloadDeserializer], qui **écarterait le bloc**. Or l'iOS pose la règle
 * inverse (`apple/180/HomeService.swift:129-131`) : « un module ne disparaît pas
 * sur un champ illisible ». Miroir de son `try?`, qui retombe sur le repli.
 */
class LenientStringAdapter : TypeAdapter<String?>() {

    override fun write(out: JsonWriter, value: String?) {
        out.value(value)
    }

    override fun read(reader: JsonReader): String? = when (reader.peek()) {
        JsonToken.STRING -> reader.nextString()
        JsonToken.NULL   -> { reader.nextNull(); null }
        // Nombre, booléen, objet, tableau : consommé et ignoré. Comme le
        // `try? decode(String.self)` iOS, qui échoue sur tout ce qui n'est pas
        // une chaîne et laisse jouer le repli.
        else             -> { reader.skipValue(); null }
    }
}

/**
 * Portée d'affichage d'un module, décidée dans le Home Builder et exposée par le
 * champ `visibility` du contrat.
 *
 * Miroir de `ModuleVisibility` (`apple/180/HomeService.swift:15-28`). La **source
 * de vérité reste le serveur** : le payload par défaut de `/home-recettes` exclut
 * déjà les modules `web_only` (le paramètre `?all=1`, qui les réintègre, n'est pas
 * utilisé par l'app). Le filtre client n'est qu'une sécurité de robustesse, utile
 * pour deux cas réels — un instantané disque écrit avant l'arrivée du filtre
 * serveur, et une régression côté web qui laisserait passer un `web_only`.
 */
enum class ModuleVisibility(val raw: String) {
    WEB_ONLY("web_only"),
    APP_ONLY("app_only"),
    WEB_AND_APP("web_app");

    /** Seul `web_only` est exclu — règle identique côté iOS (parité stricte). */
    val isVisibleInApp: Boolean get() = this != WEB_ONLY

    companion object {
        /**
         * Valeur retenue quand `visibility` est absent, d'un autre type, ou porte
         * une valeur inconnue (`web_only_v2`…) : on **n'exclut rien**. Un champ
         * qu'on ne sait pas lire ne doit jamais faire disparaître un module
         * (`HomeService.swift:20-23`).
         */
        val FALLBACK = WEB_AND_APP

        fun from(raw: String?): ModuleVisibility =
            entries.firstOrNull { it.raw == raw } ?: FALLBACK
    }
}

/**
 * Retire les modules que l'app ne doit pas rendre.
 *
 * L'iOS n'a qu'un point d'application — son décodage, traversé par les trois
 * chemins (réseau, instantané relu, hydratation) parce qu'il conserve le JSON
 * brut en cache (`HomeService.swift:35-38`, `:171-175`). **Android n'a pas cet
 * unique goulot** : `HomeSnapshot` sérialise des blocs déjà décodés
 * (`HomeRepository.kt:41-45`) et `DiskCache` les relit avec un `Gson` nu, sans
 * ce déserialiseur. Le filtre est donc extrait ici et appelé aux **deux**
 * endroits — sinon le chemin instantané resterait non filtré.
 */
fun List<HomeBlock>.visibleInApp(): List<HomeBlock> = filter { it.visibility.isVisibleInApp }

/**
 * Contrat `180c/v1/home-recettes` — composition de l'accueil par la rédaction.
 *
 * Miroir du décodage iOS `apple/180/HomeService.swift:4-117`, noms de champs
 * prouvés par la fixture `home_recettes.json` (capture anonyme réelle).
 *
 * Le décodage est **tolérant** : un bloc malformé est ignoré individuellement
 * plutôt que de vider l'accueil entier (`HomeService.swift:8-10`).
 */
data class HomePayload(
    @SerializedName("page_id") val pageId: Int? = null,
    @SerializedName("blocks")  val blocks: List<HomeBlock> = emptyList()
)

/**
 * Descripteur d'un bloc de l'accueil (ordonné).
 *
 * Les champs présents dépendent du [type] ; l'app rend ce qu'elle gère et ignore
 * le reste, ce qui la garde compatible avec de nouveaux types serveur
 * (`HomeService.swift:39-40, 76-78`). Seul [type] est requis.
 */
data class HomeBlock(
    @SerializedName("type")   private val typeRaw: String? = null,
    @SerializedName("anchor") val anchor: String? = null,
    @SerializedName("title")  private val titleRaw: String? = null,

    /**
     * Portée d'affichage brute. Conservée **nullable** plutôt que résolue en
     * enum à la désérialisation : un instantané écrit par une version
     * antérieure n'a pas le champ, et Gson y laisserait `null` — un enum non
     * nullable exploserait à la première lecture. Ici l'absence retombe
     * naturellement sur [ModuleVisibility.FALLBACK].
     */
    @SerializedName("visibility")
    @JsonAdapter(LenientStringAdapter::class)
    private val visibilityRaw: String? = null,

    // rail
    /**
     * Présentation demandée par le serveur pour un `rail`. `"slider"` (module
     * back-office « Slider de recettes ») ⇒ carrousel plein format ; absent ou
     * inconnu ⇒ rail dense. Champ **additif** : un variant qu'on ne connaît pas
     * ne doit jamais empêcher le rendu (`apple/180/HomeService.swift:83-86`).
     *
     * Lu en tolérant pour la même raison que `visibility` : une valeur d'un
     * autre type ne doit pas faire disparaître le bloc.
     */
    @SerializedName("variant")
    @JsonAdapter(LenientStringAdapter::class)
    val variant: String? = null,
    @SerializedName("source")       val source: String? = null,
    @SerializedName("taxonomy")     val taxonomy: String? = null,
    @SerializedName("term_slug")    val termSlug: String? = null,
    @SerializedName("count")        val count: Int? = null,
    @SerializedName("view_all_url") val viewAllUrl: String? = null,
    @SerializedName("recipe_ids")   val recipeIds: List<Int>? = null,

    // category_tiles
    @SerializedName("terms") val terms: List<HomeTile>? = null,

    // cta_subscribe (décodé pour rester fidèle au contrat ; jamais rendu)
    @SerializedName("body")      val body: String? = null,
    @SerializedName("cta_text")  val ctaText: String? = null,
    @SerializedName("cta_url")   val ctaUrl: String? = null,
    @SerializedName("login_url") val loginUrl: String? = null,

    // search
    @SerializedName("placeholder") val placeholder: String? = null
) {
    /** Type du bloc ; chaîne vide si le serveur ne l'a pas envoyé (bloc rejeté). */
    val type: String get() = typeRaw.orEmpty()

    /** Titre de section ; chaîne vide si absent (`HomeService.swift:82`). */
    val title: String get() = titleRaw.orEmpty()

    /**
     * Portée d'affichage du module. Toujours renseignée : l'absence du champ,
     * une valeur inconnue et une valeur d'un autre type retombent toutes sur
     * [ModuleVisibility.FALLBACK] (`HomeService.swift:79-82`).
     */
    val visibility: ModuleVisibility get() = ModuleVisibility.from(visibilityRaw)

    /** Un bloc sans type n'est pas rendable. */
    val isValid: Boolean get() = type.isNotBlank()
}

/** Tuile de terme d'un bloc `category_tiles` — déjà embarquée, aucun fetch. */
data class HomeTile(
    @SerializedName("slug")  private val slugRaw: String? = null,
    @SerializedName("name")  private val nameRaw: String? = null,
    @SerializedName("url")   val url: String? = null,
    @SerializedName("count") val count: Int = 0
) {
    val slug: String get() = slugRaw.orEmpty()
    val name: String get() = nameRaw.orEmpty()

    /** Une tuile sans slug ni nom n'est pas rendable (`HomeService.swift:112-113`). */
    val isValid: Boolean get() = slug.isNotBlank() && name.isNotBlank()
}

/** Types de blocs rendus par l'app. Tout autre type est ignoré silencieusement. */
object HomeBlockType {
    const val FEATURED = "featured"
    const val RAIL = "rail"
    const val CATEGORY_TILES = "category_tiles"
    const val CARNET = "carnet"

    /**
     * Grille « Toutes les recettes ». Consomme les 16 recettes récentes déjà
     * chargées, **pas** de `recipe_ids` : le serveur n'en sérialise pas pour ce
     * layout (`apple/180/HomeView.swift:263-266`).
     */
    const val GRID_PAGINATED = "grid_paginated"
}

/**
 * Variantes de présentation d'un bloc `rail`.
 *
 * Toute autre valeur — absente comme inconnue — retombe sur le rail dense
 * (`apple/180/HomeView.swift:233-238`).
 */
object HomeBlockVariant {
    const val SLIDER = "slider"
}

/**
 * Décodeur tolérant de [HomePayload].
 *
 * Équivalent Gson du `FailableDecodable` iOS (`HomeService.swift:28-37`) : chaque
 * bloc est décodé isolément et, s'il échoue ou n'a pas de `type`, il est écarté
 * — le reste de l'accueil s'affiche quand même. Un seul bloc cassé côté
 * back-office ne doit pas produire un écran vide.
 *
 * Les modules `web_only` sont retirés ici, au décodage, comme côté iOS
 * (`HomeService.swift:54`). Voir [visibleInApp] : le chemin de relecture de
 * l'instantané ne passant pas par ce déserialiseur, il applique le même filtre
 * de son côté.
 */
class HomePayloadDeserializer : JsonDeserializer<HomePayload> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): HomePayload {
        val root = json.takeIf { it.isJsonObject }?.asJsonObject ?: return HomePayload()

        val pageId = root.get("page_id")
            ?.takeIf { it.isJsonPrimitive }
            ?.runCatching { asInt }?.getOrNull()

        val blocks = root.get("blocks")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { element ->
                runCatching { context.deserialize<HomeBlock>(element, HomeBlock::class.java) }
                    .getOrNull()
                    ?.takeIf { it.isValid }
            }
            .orEmpty()
            .visibleInApp()

        return HomePayload(pageId = pageId, blocks = blocks)
    }
}
