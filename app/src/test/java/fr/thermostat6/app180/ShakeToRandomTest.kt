package fr.thermostat6.app180

import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.data.model.RenderedContent
import fr.thermostat6.app180.data.sensor.ShakeDetector
import fr.thermostat6.app180.data.service.RandomRecipeService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Seuil, anti-rebond et tirage — la partie du « secouer pour une recette » qui
 * peut se tromper sans qu'aucun capteur ne soit en cause.
 *
 * Contrats iOS : `apple/180/ShakeDetectorService.swift:14-15` (2,2 g hors
 * gravité, 2 s de garde) et `APIService.swift:166-176` (tirage côté app).
 */
class ShakeToRandomTest {

    @Before
    fun setUp() {
        ShakeDetector.resetCooldown()
    }

    // ── Seuil ────────────────────────────────────────────────────────────────

    @Test
    fun `le seuil vaut 2,2 g exprime en metres par seconde carree`() {
        // 2,2 × 9,80665 ≈ 21,57 m/s²
        assertEquals(21.57f, ShakeDetector.thresholdMetersPerSec2, 0.01f)
        assertEquals(2.2, ShakeDetector.SHAKE_THRESHOLD_G, 0.0001)
        assertEquals(2_000L, ShakeDetector.SHAKE_COOLDOWN_MILLIS)
    }

    @Test
    fun `un mouvement sous le seuil ne declenche rien`() {
        // 10 m/s² sur un seul axe : bien en dessous des ~21,6 attendus.
        assertFalse(ShakeDetector.accept(10f, 0f, 0f, nowMillis = 1_000L, gravityIncluded = false))
    }

    @Test
    fun `un mouvement au-dessus du seuil declenche`() {
        assertTrue(ShakeDetector.accept(25f, 0f, 0f, nowMillis = 1_000L, gravityIncluded = false))
    }

    @Test
    fun `la magnitude combine les trois axes`() {
        // 13 sur trois axes : √(3 × 13²) ≈ 22,5, au-dessus du seuil, alors
        // qu'aucun axe pris seul ne l'atteindrait.
        assertTrue(ShakeDetector.accept(13f, 13f, 13f, nowMillis = 1_000L, gravityIncluded = false))
    }

    @Test
    fun `la gravite est retranchee quand la mesure la contient`() {
        // Appareil au repos, accéléromètre brut : 9,81 m/s² vers le bas. Sans
        // soustraction, un téléphone posé sur la table déclencherait un tirage
        // dès que le seuil serait abaissé — ici il ne doit rien déclencher.
        assertFalse(ShakeDetector.accept(0f, 0f, 9.81f, nowMillis = 1_000L, gravityIncluded = true))

        // Même magnitude brute, mais mesure déjà nettoyée de la gravité :
        // toujours sous le seuil, la différence ne joue pas dans ce sens.
        assertFalse(ShakeDetector.accept(0f, 0f, 9.81f, nowMillis = 1_000L, gravityIncluded = false))

        // 31,4 brut − 9,81 ≈ 21,6 : juste au-dessus une fois la gravité ôtée.
        assertTrue(ShakeDetector.accept(0f, 0f, 32f, nowMillis = 1_000L, gravityIncluded = true))
    }

    // ── Anti-rebond ──────────────────────────────────────────────────────────

    @Test
    fun `deux secousses rapprochees ne comptent que pour une`() {
        assertTrue(ShakeDetector.accept(30f, 0f, 0f, nowMillis = 10_000L, gravityIncluded = false))
        // 1,5 s plus tard : dans la fenêtre de garde de 2 s.
        assertFalse(ShakeDetector.accept(30f, 0f, 0f, nowMillis = 11_500L, gravityIncluded = false))
    }

    @Test
    fun `une secousse au-dela du delai de garde compte`() {
        assertTrue(ShakeDetector.accept(30f, 0f, 0f, nowMillis = 10_000L, gravityIncluded = false))
        assertFalse(ShakeDetector.accept(30f, 0f, 0f, nowMillis = 11_999L, gravityIncluded = false))
        assertTrue(ShakeDetector.accept(30f, 0f, 0f, nowMillis = 12_001L, gravityIncluded = false))
    }

    @Test
    fun `un mouvement faible ne consomme pas la fenetre de garde`() {
        assertFalse(ShakeDetector.accept(5f, 0f, 0f, nowMillis = 10_000L, gravityIncluded = false))
        // La secousse suivante ne doit pas être avalée par une garde armée à tort.
        assertTrue(ShakeDetector.accept(30f, 0f, 0f, nowMillis = 10_100L, gravityIncluded = false))
    }

    // ── Tirage ───────────────────────────────────────────────────────────────

    private fun recipe(id: Int) = Recipe(
        id    = id,
        date  = "2026-01-01T00:00:00",
        title = RenderedContent("Recette $id")
    )

    @Test
    fun `le tirage rend null sur un lot vide`() {
        assertNull(RandomRecipeService.pick(emptyList()))
    }

    @Test
    fun `le tirage reste dans le lot`() {
        val pool = (1..20).map { recipe(it) }
        repeat(100) {
            val drawn = RandomRecipeService.pick(pool)
            assertTrue("recette hors du lot : ${drawn?.id}", drawn in pool)
        }
    }

    @Test
    fun `le tirage est reproductible a graine fixee`() {
        val pool = (1..50).map { recipe(it) }
        val first = RandomRecipeService.pick(pool, Random(42))
        val second = RandomRecipeService.pick(pool, Random(42))
        assertEquals(first?.id, second?.id)
    }

    @Test
    fun `le tirage ne se fige pas sur un seul element`() {
        val pool = (1..50).map { recipe(it) }
        val drawn = (1..40).mapNotNull { RandomRecipeService.pick(pool)?.id }.toSet()
        // Sur 40 tirages dans 50 recettes, un tirage réellement aléatoire donne
        // presque toujours bien plus de 5 valeurs distinctes.
        assertTrue("tirage suspect, ${drawn.size} valeur(s) distincte(s)", drawn.size > 5)
    }
}
