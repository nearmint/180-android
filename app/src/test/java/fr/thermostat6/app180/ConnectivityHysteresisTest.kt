package fr.thermostat6.app180

import fr.thermostat6.app180.data.network.ConnectivityHysteresis
import fr.thermostat6.app180.data.network.ConnectivityHysteresis.Effect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hystérésis de connectivité — parité comportementale avec
 * `apple/180/NetworkMonitor.swift:63-95`.
 *
 * Le délai n'est pas simulé : la machine demande une planification, le test
 * décide s'il la laisse arriver à terme ([ConnectivityHysteresis.onGraceElapsed])
 * ou si le réseau revient avant.
 */
class ConnectivityHysteresisTest {

    // ── Premier verdict ──────────────────────────────────────────────────────

    @Test
    fun `le premier verdict hors ligne est publie immediatement`() {
        // App ouverte en mode avion : le bandeau doit apparaître d'emblée.
        val gate = ConnectivityHysteresis(initiallyConnected = true)

        val effects = gate.onPath(satisfied = false)

        assertEquals(listOf(Effect.Publish(false)), effects)
        assertFalse(gate.isConnected)
    }

    @Test
    fun `le premier verdict en ligne ne republie rien si deja en ligne`() {
        val gate = ConnectivityHysteresis(initiallyConnected = true)

        assertEquals(emptyList<Effect>(), gate.onPath(satisfied = true))
        assertTrue(gate.isConnected)
    }

    @Test
    fun `le premier verdict en ligne est publie si l'etat initial etait hors ligne`() {
        val gate = ConnectivityHysteresis(initiallyConnected = false)

        assertEquals(listOf(Effect.Publish(true)), gate.onPath(satisfied = true))
        assertTrue(gate.isConnected)
    }

    // ── Perte différée ───────────────────────────────────────────────────────

    @Test
    fun `une perte en cours d'usage est differee et non publiee tout de suite`() {
        val gate = settled(connected = true)

        val effects = gate.onPath(satisfied = false)

        assertEquals(listOf(Effect.SchedulePendingLoss), effects)
        assertTrue("la perte ne doit pas être publiée avant le délai", gate.isConnected)
    }

    @Test
    fun `la perte est publiee une fois le delai ecoule`() {
        val gate = settled(connected = true)
        gate.onPath(satisfied = false)

        val effects = gate.onGraceElapsed()

        assertEquals(listOf(Effect.Publish(false)), effects)
        assertFalse(gate.isConnected)
    }

    @Test
    fun `un retour du reseau avant la fin du delai annule la perte`() {
        // Micro-coupure ou bascule Wi-Fi vers cellulaire : le bandeau ne doit
        // jamais clignoter.
        val gate = settled(connected = true)
        gate.onPath(satisfied = false)

        val effects = gate.onPath(satisfied = true)

        assertEquals(listOf(Effect.CancelPendingLoss), effects)
        assertTrue(gate.isConnected)
        assertEquals(
            "le délai annulé ne doit plus rien publier",
            emptyList<Effect>(),
            gate.onGraceElapsed()
        )
    }

    @Test
    fun `une perte deja en attente n'est pas replanifiee`() {
        val gate = settled(connected = true)
        gate.onPath(satisfied = false)

        assertEquals(emptyList<Effect>(), gate.onPath(satisfied = false))
    }

    @Test
    fun `une perte alors qu'on est deja hors ligne ne planifie rien`() {
        val gate = settled(connected = true)
        gate.onPath(satisfied = false)
        gate.onGraceElapsed()

        assertEquals(emptyList<Effect>(), gate.onPath(satisfied = false))
        assertFalse(gate.isConnected)
    }

    // ── Retour en ligne ──────────────────────────────────────────────────────

    @Test
    fun `le retour en ligne est publie immediatement sans amortissement`() {
        val gate = settled(connected = true)
        gate.onPath(satisfied = false)
        gate.onGraceElapsed()

        val effects = gate.onPath(satisfied = true)

        assertEquals(listOf(Effect.Publish(true)), effects)
        assertTrue(gate.isConnected)
    }

    // ── Anti-bruit ───────────────────────────────────────────────────────────

    @Test
    fun `un chemin satisfait repete ne republie rien`() {
        // Changement d'interface ou de passerelle sans transition réelle : les
        // vues ne doivent pas être notifiées.
        val gate = settled(connected = true)

        repeat(5) { assertEquals(emptyList<Effect>(), gate.onPath(satisfied = true)) }
    }

    @Test
    fun `une bascule wifi vers cellulaire ne publie aucune perte`() {
        // Contrat dont dépend `NetworkMonitor.onLost` : celui-ci soumet `false`
        // sans re-interroger le système (re-lire `activeNetwork` pendant la
        // démolition du réseau renvoie encore l'ancien, capacité Internet
        // comprise, et publierait la perte comme un « toujours en ligne »).
        // C'est donc ici que l'amortissement doit absorber la transition.
        val gate = settled(connected = true)

        gate.onPath(satisfied = false)   // onLost sur l'ancien réseau
        gate.onPath(satisfied = true)    // onAvailable sur le nouveau

        assertTrue("aucune perte ne doit être publiée", gate.isConnected)
        assertEquals(
            "le délai annulé ne doit rien publier après coup",
            emptyList<Effect>(),
            gate.onGraceElapsed()
        )
    }

    @Test
    fun `une bascule complete ne produit que deux publications`() {
        val gate = settled(connected = true)
        val published = mutableListOf<Effect>()

        published += gate.onPath(satisfied = false)      // planifie
        published += gate.onGraceElapsed()               // publie false
        published += gate.onPath(satisfied = true)       // publie true
        published += gate.onPath(satisfied = true)       // rien

        assertEquals(
            listOf(
                Effect.SchedulePendingLoss,
                Effect.Publish(false),
                Effect.Publish(true)
            ),
            published
        )
    }

    /** Machine ayant déjà encaissé son premier verdict, donc en régime permanent. */
    private fun settled(connected: Boolean) =
        ConnectivityHysteresis(initiallyConnected = connected).apply { onPath(connected) }
}
