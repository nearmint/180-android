package fr.thermostat6.app180.data.network

/**
 * Machine à états de l'hystérésis de connectivité — **logique pure**, sans
 * horloge ni framework Android.
 *
 * Miroir de `NetworkMonitor.apply(satisfied:)` (`apple/180/NetworkMonitor.swift:63-88`).
 *
 * Le délai lui-même n'est pas géré ici : la machine se contente de **demander**
 * la planification ou l'annulation d'une publication différée
 * ([Effect.SchedulePendingLoss] / [Effect.CancelPendingLoss]), et l'appelant lui
 * rend la main via [onGraceElapsed] quand le délai s'est écoulé. C'est ce qui la
 * rend testable en JVM pure, sans avance de temps virtuel.
 *
 * ## Règles
 *
 * 1. **Premier verdict** : appliqué tel quel, sans amortissement — une app
 *    ouverte en mode avion doit afficher son bandeau d'emblée, pas une seconde
 *    et demie plus tard.
 * 2. **Retour en ligne** : publié immédiatement. Rien à amortir, on ne fait
 *    qu'enlever un bandeau.
 * 3. **Perte** : différée. Sans ce délai, un basculement Wi-Fi → cellulaire ou
 *    une micro-coupure ferait clignoter le bandeau. Une perte déjà en attente
 *    n'est pas replanifiée.
 * 4. Rien n'est publié si la valeur ne change pas : les vues ne doivent pas être
 *    notifiées à chaque changement d'interface ou de passerelle sans transition
 *    réelle.
 */
class ConnectivityHysteresis(initiallyConnected: Boolean) {

    /** Action demandée à l'appelant. */
    sealed interface Effect {
        /** Publier la nouvelle valeur de connectivité. */
        data class Publish(val connected: Boolean) : Effect

        /** Armer le délai d'amortissement avant de publier une perte. */
        data object SchedulePendingLoss : Effect

        /** Désarmer un délai en cours : le réseau est revenu entre-temps. */
        data object CancelPendingLoss : Effect
    }

    var isConnected: Boolean = initiallyConnected
        private set

    /** `true` tant qu'aucun verdict de chemin n'a encore été reçu. */
    private var hasSettledFirstPath = false

    /** `true` quand une perte attend la fin du délai d'amortissement. */
    private var hasPendingLoss = false

    /** Verdict de chemin réseau (disponible / indisponible). */
    fun onPath(satisfied: Boolean): List<Effect> {
        // Règle 1 — premier verdict, appliqué sans amortissement.
        if (!hasSettledFirstPath) {
            hasSettledFirstPath = true
            val effects = mutableListOf<Effect>()
            if (hasPendingLoss) {
                hasPendingLoss = false
                effects += Effect.CancelPendingLoss
            }
            return effects + publish(satisfied)
        }

        // Règle 2 — retour en ligne, publié immédiatement.
        if (satisfied) {
            val effects = mutableListOf<Effect>()
            if (hasPendingLoss) {
                hasPendingLoss = false
                effects += Effect.CancelPendingLoss
            }
            return effects + publish(true)
        }

        // Règle 3 — perte différée. Déjà hors ligne, ou perte déjà en attente :
        // rien à faire.
        if (!isConnected || hasPendingLoss) return emptyList()
        hasPendingLoss = true
        return listOf(Effect.SchedulePendingLoss)
    }

    /** Le délai d'amortissement s'est écoulé sans retour du réseau. */
    fun onGraceElapsed(): List<Effect> {
        if (!hasPendingLoss) return emptyList()
        hasPendingLoss = false
        return publish(false)
    }

    /** Règle 4 — publication uniquement en cas de changement effectif. */
    private fun publish(value: Boolean): List<Effect> {
        if (isConnected == value) return emptyList()
        isConnected = value
        return listOf(Effect.Publish(value))
    }
}
