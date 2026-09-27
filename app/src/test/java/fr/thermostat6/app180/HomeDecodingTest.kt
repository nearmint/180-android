package fr.thermostat6.app180

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import fr.thermostat6.app180.data.model.HomeBlock
import fr.thermostat6.app180.data.model.HomeBlockType
import fr.thermostat6.app180.data.model.HomeBlockVariant
import fr.thermostat6.app180.data.model.ModuleVisibility
import fr.thermostat6.app180.data.model.visibleInApp
import fr.thermostat6.app180.data.model.HomePayload
import fr.thermostat6.app180.data.model.HomePayloadDeserializer
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.service.RecipeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrat `180c/v1/home-recettes` — décodage **tolérant** et réordonnancement.
 *
 * `home_recettes.json` est une capture anonyme **réelle** ; la variante
 * `_malformed` en est une copie altérée pour éprouver la tolérance.
 * Miroir d'`apple/180Tests/HomeDecodingTests.swift`.
 */
class HomeDecodingTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(HomePayload::class.java, HomePayloadDeserializer())
        .create()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Fixture manquante : $name"
        }.bufferedReader().use { it.readText() }

    private fun payload(name: String): HomePayload =
        gson.fromJson(fixture(name), HomePayload::class.java)

    // ── Portée d'affichage (AND-08) ──────────────────────────────────────────

    private fun visibilityBlocks() = payload("home_recettes_visibility.json").blocks

    @Test
    fun `un module web_only est exclu au decodage`() {
        // Le seul cas exclu du contrat (`apple/180/HomeService.swift:26-27`).
        assertFalse(visibilityBlocks().any { it.title == "Réservé au site" })
    }

    @Test
    fun `app_only et web_app sont conserves`() {
        val titles = visibilityBlocks().map { it.title }

        assertTrue("Explicitement web_app" in titles)
        assertTrue("Réservé à l'app" in titles)
        assertEquals(
            ModuleVisibility.APP_ONLY,
            visibilityBlocks().first { it.title == "Réservé à l'app" }.visibility
        )
    }

    @Test
    fun `un champ visibility absent vaut visible`() {
        val block = visibilityBlocks().first { it.title == "Champ absent" }

        // « Un champ qu'on ne sait pas lire ne doit jamais faire disparaître un
        // module » (`HomeService.swift:20-23`).
        assertEquals(ModuleVisibility.WEB_AND_APP, block.visibility)
        assertTrue(block.visibility.isVisibleInApp)
    }

    @Test
    fun `une valeur de visibility inconnue conserve le module`() {
        val block = visibilityBlocks().first { it.title == "Valeur inconnue" }

        // `web_only_v2` ressemble à `web_only` mais reste inconnu : on n'exclut
        // rien sur une valeur qu'on ne sait pas lire.
        assertEquals(ModuleVisibility.WEB_AND_APP, block.visibility)
    }

    @Test
    fun `un visibility d'un type inattendu conserve le module`() {
        val titles = visibilityBlocks().map { it.title }

        // Nombre, objet, tableau, null : l'adaptateur String de Gson lèverait,
        // et l'exception écarterait le bloc entier via le `runCatching` du
        // déserialiseur. `LenientStringAdapter` l'en empêche — miroir du `try?`
        // iOS (`HomeService.swift:129-131`).
        listOf(
            "Visibility numérique", "Visibility objet",
            "Visibility tableau", "Visibility nulle"
        ).forEach { title ->
            assertTrue("bloc « $title » écarté à tort", title in titles)
            assertEquals(
                ModuleVisibility.WEB_AND_APP,
                visibilityBlocks().first { it.title == title }.visibility
            )
        }
    }

    @Test
    fun `une valeur ignoree ne decale pas la lecture des blocs suivants`() {
        // Le lecteur tolérant doit **consommer** la valeur qu'il ignore : sans
        // `skipValue`, le curseur du flux se décalerait et casserait la suite du
        // tableau.
        val titles = visibilityBlocks().map { it.title }

        assertEquals("Visibility tableau", titles[titles.indexOf("Visibility objet") + 1])
        assertTrue("Carrousel" in titles)
    }

    // ── Variante de rail (AND-09) ────────────────────────────────────────────

    @Test
    fun `la variante slider est decodee`() {
        assertEquals(
            HomeBlockVariant.SLIDER,
            visibilityBlocks().first { it.title == "Carrousel" }.variant
        )
    }

    @Test
    fun `une variante absente ou inconnue retombe sur le rail dense`() {
        val blocks = visibilityBlocks()

        // Absente : le rendu teste `variant == SLIDER`, donc `null` suffit.
        assertEquals(null, blocks.first { it.title == "Champ absent" }.variant)
        // Inconnue : décodée telle quelle, mais différente de `slider`.
        val unknown = blocks.first { it.title == "Variante inconnue" }
        assertEquals("mosaic_v2", unknown.variant)
        assertFalse(unknown.variant == HomeBlockVariant.SLIDER)
        // D'un autre type : ignorée, sans faire disparaître le bloc.
        assertEquals(null, blocks.first { it.title == "Variante numérique" }.variant)
    }

    // ── Relecture d'instantané (AND-08) ──────────────────────────────────────

    @Test
    fun `un instantane ecrit sans le champ visibility ne perd aucun module`() {
        // `DiskCache` relit avec un `Gson` **nu**, sans `HomePayloadDeserializer`
        // (`DiskCache.kt:27`) : c'est ce chemin-là qu'on éprouve, celui d'un
        // instantané écrit par une version antérieure au champ.
        val blocks = snapshotBlocks("""[{"type":"rail","title":"Ancien module"}]""")

        assertEquals(listOf("Ancien module"), blocks.map { it.title })
        assertEquals(ModuleVisibility.WEB_AND_APP, blocks.single().visibility)
        assertEquals(1, blocks.visibleInApp().size)
    }

    @Test
    fun `le filtre de relecture ecarte un web_only deja serialise`() {
        // Second point d'application du filtre : une régression serveur ayant
        // laissé passer un `web_only`, celui-ci a pu être sérialisé.
        val blocks = snapshotBlocks(
            """[
                {"type":"rail","title":"Gardé"},
                {"type":"rail","title":"À exclure","visibility":"web_only"}
            ]"""
        )

        assertEquals(2, blocks.size)
        assertEquals(listOf("Gardé"), blocks.visibleInApp().map { it.title })
    }

    @Test
    fun `un bloc survit a un aller-retour de serialisation d'instantane`() {
        val original = payload("home_recettes.json").blocks

        val roundTrip = snapshotBlocks(Gson().toJson(original))

        assertEquals(original.map { it.type }, roundTrip.map { it.type })
        assertEquals(original.map { it.title }, roundTrip.map { it.title })
        assertEquals(original.map { it.recipeIds }, roundTrip.map { it.recipeIds })
        assertTrue(roundTrip.all { it.visibility.isVisibleInApp })
    }

    /** Relit des blocs comme le fait `DiskCache` : Gson nu, sans déserialiseur. */
    private fun snapshotBlocks(json: String): List<HomeBlock> =
        Gson().fromJson(json, Array<HomeBlock>::class.java).toList()

    // ── Fixture réelle ───────────────────────────────────────────────────────

    @Test
    fun `la home reelle se decode dans l'ordre serveur`() {
        val home = payload("home_recettes.json")

        assertEquals(13136894, home.pageId)
        assertEquals(
            listOf(
                "featured", "search", "category_tiles", "rail",
                "category_tiles", "carnet", "cta_subscribe", "grid_paginated"
            ),
            home.blocks.map { it.type }
        )
    }

    @Test
    fun `les champs des blocs rail et featured sont decodes`() {
        val home = payload("home_recettes.json")

        val featured = home.blocks.first { it.type == HomeBlockType.FEATURED }
        assertEquals(listOf(13400072), featured.recipeIds)
        assertEquals("", featured.title)

        val rail = home.blocks.first { it.type == HomeBlockType.RAIL }
        assertEquals("Dernières recettes publiées", rail.title)
        assertEquals("recent", rail.source)
        assertEquals(8, rail.count)
        assertEquals("https://www.180c.fr/recettes/", rail.viewAllUrl)
        assertEquals(8, rail.recipeIds?.size)
    }

    @Test
    fun `les tuiles de taxonomie sont embarquees sans fetch`() {
        val tiles = payload("home_recettes.json").blocks
            .first { it.type == HomeBlockType.CATEGORY_TILES }

        assertEquals("recipe_category", tiles.taxonomy)
        assertEquals("Explorez les recettes", tiles.title)
        val terms = tiles.terms.orEmpty()
        assertTrue(terms.isNotEmpty())
        assertEquals("plat", terms.first().slug)
        assertEquals("Plat", terms.first().name)
        assertEquals(681, terms.first().count)
    }

    @Test
    fun `le bloc carnet est present et sans donnees`() {
        val carnet = payload("home_recettes.json").blocks
            .first { it.type == HomeBlockType.CARNET }

        // Le rail est hydraté côté app depuis FavoritesManager (HomeView.swift:492-505).
        assertNotNull(carnet)
        assertEquals(null, carnet.recipeIds)
    }

    // ── Tolérance par bloc ───────────────────────────────────────────────────

    @Test
    fun `un bloc malforme est ignore sans vider l'accueil`() {
        val home = payload("home_recettes_malformed.json")

        // Bloc sans `type` et élément non-objet écartés ; les autres survivent.
        assertEquals(
            listOf("featured", "type_inconnu_du_futur", "rail", "category_tiles"),
            home.blocks.map { it.type }
        )
        assertTrue(home.blocks.all { it.isValid })
        assertEquals(13136894, home.pageId)
    }

    @Test
    fun `un type de bloc inconnu est decode mais reste non rendable`() {
        val home = payload("home_recettes_malformed.json")
        val unknown = home.blocks.first { it.type == "type_inconnu_du_futur" }

        // Décodé (forward-compatible), mais hors des types que l'écran rend.
        assertTrue(unknown.isValid)
        assertFalse(
            unknown.type in listOf(
                HomeBlockType.FEATURED, HomeBlockType.RAIL,
                HomeBlockType.CATEGORY_TILES, HomeBlockType.CARNET
            )
        )
    }

    @Test
    fun `une tuile sans slug est ecartee par isValid`() {
        val tiles = payload("home_recettes_malformed.json").blocks
            .first { it.type == HomeBlockType.CATEGORY_TILES }
            .terms.orEmpty()

        assertEquals(3, tiles.size)
        assertEquals(listOf("plat", "dessert"), tiles.filter { it.isValid }.map { it.slug })
    }

    @Test
    fun `un payload vide ou aberrant ne leve pas d'exception`() {
        assertEquals(emptyList<Any>(), payload2("{}").blocks)
        assertEquals(emptyList<Any>(), payload2("""{"blocks":null}""").blocks)
        assertEquals(emptyList<Any>(), payload2("""{"blocks":"pas-un-tableau"}""").blocks)
        assertEquals(emptyList<Any>(), payload2("[]").blocks)
    }

    private fun payload2(json: String): HomePayload =
        gson.fromJson(json, HomePayload::class.java)

    // ── Réordonnancement après hydratation batchée ───────────────────────────

    @Test
    fun `l'hydratation restitue l'ordre des recipe_ids`() {
        val ids = listOf(30, 10, 20)
        // L'API `include` ne garantit pas l'ordre : on simule une réponse WP
        // renvoyée dans un ordre différent (HomeView.swift:507-511).
        val fromServer = listOf(recipe(10), recipe(20), recipe(30))

        assertEquals(ids, RecipeCatalog.ordered(fromServer, ids).map { it.id })
    }

    @Test
    fun `un id absent est ignore sans casser le rail`() {
        val ids = listOf(10, 999, 20)
        val fromServer = listOf(recipe(20), recipe(10))

        assertEquals(listOf(10, 20), RecipeCatalog.ordered(fromServer, ids).map { it.id })
    }

    private fun recipe(id: Int): Recipe = Recipe(
        id    = id,
        date  = "2026-01-01T00:00:00",
        title = fr.thermostat6.app180.data.model.RenderedContent("Recette $id")
    )
}
