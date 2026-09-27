package fr.thermostat6.app180.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Source **unique** de vérité de la connectivité, adossée à
 * [ConnectivityManager.registerDefaultNetworkCallback].
 *
 * Miroir de `NetworkMonitor` (`apple/180/NetworkMonitor.swift`).
 *
 * Deux mécanismes coexistent volontairement dans l'app et ne se recouvrent pas :
 *
 * - [NetworkMonitor] pilote l'UI **proactive** — bandeau hors ligne, gating des
 *   cycles de synchronisation. Il répond à « y a-t-il un chemin réseau ? » avant
 *   même qu'une requête soit émise.
 * - [ApiError] pilote l'UI **réactive** — l'échec d'une requête déjà partie,
 *   typé [ApiError.Offline] / [ApiError.Timeout] / [ApiError.Http]…
 *
 * Un chemin disponible ne garantit pas qu'une requête aboutira (portail captif,
 * serveur à terre) : le monitor ne remplace donc **jamais** la taxonomie
 * d'erreurs.
 *
 * ## Choix de la capacité observée
 *
 * On teste [NetworkCapabilities.NET_CAPABILITY_INTERNET] et **non**
 * `NET_CAPABILITY_VALIDATED`. C'est la parité fidèle avec `path.status ==
 * .satisfied` côté iOS, qui reste satisfait derrière un portail captif. Exiger
 * la validation ferait afficher « hors ligne » sur un réseau d'hôtel où le
 * carnet en ligne reste, lui, parfaitement inaccessible — mais où l'erreur
 * typée décrit déjà mieux la situation.
 *
 * ## Hystérésis
 *
 * Déléguée à [ConnectivityHysteresis] (logique pure et testée). Ce singleton
 * n'en est que le bras armé : il tient l'horloge et le [StateFlow].
 */
object NetworkMonitor {

    private const val TAG = "NetworkMonitor"

    /** Délai d'amortissement avant de publier une perte (`NetworkMonitor.swift:44`). */
    const val OFFLINE_GRACE_MILLIS = 1_500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * `true` tant qu'un chemin réseau est disponible.
     *
     * La valeur initiale est le verdict **synchrone** lu au moment de [init] :
     * contrairement à l'iOS, qui démarre optimiste et se corrige en quelques
     * millisecondes, Android sait répondre tout de suite. Une app lancée en mode
     * avion n'affiche donc jamais, même brièvement, un état « en ligne ».
     */
    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var hysteresis = ConnectivityHysteresis(initiallyConnected = true)

    /** Publication différée d'une perte, annulable si le réseau revient. */
    private var pendingLossJob: Job? = null

    private var connectivityManager: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** `true` une fois [init] passé — [init] est idempotent. */
    private var isStarted = false

    /**
     * À appeler dans `Application.onCreate()`.
     *
     * L'enregistrement du callback est encapsulé : sur un appareil bridé, il
     * peut lever, et l'absence de monitor ne doit jamais empêcher l'app de
     * démarrer. On reste alors sur le dernier verdict synchrone connu.
     */
    @Synchronized
    fun init(context: Context) {
        if (isStarted) return
        isStarted = true

        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager = manager

        // Verdict initial synchrone, avant tout callback.
        val initial = manager?.let { currentlySatisfied(it) } ?: true
        _isConnected.value = initial
        hysteresis = ConnectivityHysteresis(initiallyConnected = initial)

        if (manager == null) return

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = submit(true)

            /**
             * Perte du réseau par défaut : le verdict est **false**, sans
             * re-interroger le système.
             *
             * Re-lire `activeNetwork` ici serait une course perdue d'avance :
             * au moment où le callback arrive, le système renvoie encore le
             * réseau en cours de démolition, capacité Internet comprise. La
             * perte se retrouverait alors publiée comme un « toujours en
             * ligne », de façon intermittente selon qui gagne la course.
             *
             * Un basculement Wi-Fi → cellulaire produit bien un `onLost` sur
             * l'ancien réseau, mais l'`onAvailable` du nouveau arrive dans la
             * foulée : c'est exactement ce que le délai d'amortissement de
             * [ConnectivityHysteresis] absorbe.
             */
            override fun onLost(network: Network) = submit(false)

            override fun onUnavailable() = submit(false)

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) = submit(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        }

        runCatching { manager.registerDefaultNetworkCallback(networkCallback) }
            .onSuccess { callback = networkCallback }
            .onFailure { Log.w(TAG, "[Réseau] monitor indisponible : ${it.message}") }
    }

    /** Verdict synchrone : le réseau par défaut porte-t-il un accès Internet ? */
    private fun currentlySatisfied(manager: ConnectivityManager): Boolean {
        val active = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Passe un verdict de chemin à l'hystérésis et exécute ce qu'elle demande. */
    @Synchronized
    private fun submit(satisfied: Boolean) {
        apply(hysteresis.onPath(satisfied))
    }

    private fun apply(effects: List<ConnectivityHysteresis.Effect>) {
        effects.forEach { effect ->
            when (effect) {
                is ConnectivityHysteresis.Effect.Publish -> {
                    _isConnected.value = effect.connected
                    Log.i(TAG, "[Réseau] ${if (effect.connected) "en ligne" else "hors ligne"}")
                }

                ConnectivityHysteresis.Effect.CancelPendingLoss -> {
                    pendingLossJob?.cancel()
                    pendingLossJob = null
                }

                ConnectivityHysteresis.Effect.SchedulePendingLoss -> {
                    pendingLossJob?.cancel()
                    pendingLossJob = scope.launch {
                        delay(OFFLINE_GRACE_MILLIS)
                        synchronized(NetworkMonitor) {
                            pendingLossJob = null
                            apply(hysteresis.onGraceElapsed())
                        }
                    }
                }
            }
        }
    }
}
