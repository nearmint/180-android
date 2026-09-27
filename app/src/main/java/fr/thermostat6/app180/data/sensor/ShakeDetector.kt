package fr.thermostat6.app180.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.sqrt

/**
 * Détection de secousse — miroir de `ShakeDetectorService`
 * (`apple/180/ShakeDetectorService.swift`).
 *
 * L'iOS lit `userAcceleration` de CoreMotion, c'est-à-dire l'accélération
 * **hors gravité**. L'équivalent Android est `TYPE_LINEAR_ACCELERATION` ; quand
 * l'appareil ne le propose pas, on retombe sur l'accéléromètre brut en
 * retranchant la gravité de la magnitude.
 *
 * Le détecteur ne connaît ni recette ni écran : il émet un signal, rien d'autre.
 */
object ShakeDetector {

    /**
     * Seuil en g, hors gravité (`ShakeDetectorService.swift:14`). Les capteurs
     * Android rendent des m/s² : la conversion vit dans [thresholdMetersPerSec2].
     */
    const val SHAKE_THRESHOLD_G = 2.2

    /** Délai minimal entre deux détections (`:15`). */
    const val SHAKE_COOLDOWN_MILLIS = 2_000L

    /** Période d'échantillonnage, 50 ms comme l'iOS (`:20`), en microsecondes. */
    private const val SAMPLING_PERIOD_MICROS = 50_000

    /** Seuil exprimé dans l'unité des capteurs Android. */
    val thresholdMetersPerSec2: Float
        get() = (SHAKE_THRESHOLD_G * SensorManager.GRAVITY_EARTH).toFloat()

    private val _shakes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Un élément par secousse retenue. */
    val shakes: SharedFlow<Unit> = _shakes.asSharedFlow()

    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null
    /**
     * `null` tant qu'aucune secousse n'a été retenue — équivalent du
     * `.distantPast` iOS (`ShakeDetectorService.swift:13`). Une valeur numérique
     * initiale ferait tomber la toute première secousse dans la fenêtre de garde.
     */
    private var lastShakeAtMillis: Long? = null

    /**
     * Décide si un échantillon déclenche une secousse et met à jour l'horloge
     * anti-rebond.
     *
     * Extrait du listener pour rester vérifiable en test JVM : le calcul de
     * magnitude et la fenêtre de garde sont la partie qui peut se tromper, pas
     * l'abonnement au capteur.
     *
     * @param gravityIncluded `true` quand la mesure vient de l'accéléromètre
     *   brut, dont il faut retirer la gravité pour retrouver la grandeur iOS.
     */
    internal fun accept(
        x: Float,
        y: Float,
        z: Float,
        nowMillis: Long,
        gravityIncluded: Boolean
    ): Boolean {
        val raw = sqrt(x * x + y * y + z * z)
        val magnitude = if (gravityIncluded) raw - SensorManager.GRAVITY_EARTH else raw

        if (magnitude <= thresholdMetersPerSec2) return false

        val last = lastShakeAtMillis
        if (last != null && nowMillis - last <= SHAKE_COOLDOWN_MILLIS) return false

        lastShakeAtMillis = nowMillis
        return true
    }

    /** Réarme le détecteur — usage test uniquement. */
    internal fun resetCooldown() {
        lastShakeAtMillis = null
    }

    /**
     * Abonne le détecteur au capteur. Idempotent : un second appel sans [stop]
     * ne crée pas de second écouteur.
     */
    fun start(context: Context) {
        if (listener != null) return

        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val linear = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensor = linear ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val gravityIncluded = linear == null

        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val shaken = accept(
                    x               = event.values[0],
                    y               = event.values[1],
                    z               = event.values[2],
                    nowMillis       = System.currentTimeMillis(),
                    gravityIncluded = gravityIncluded
                )
                if (shaken) _shakes.tryEmit(Unit)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(newListener, sensor, SAMPLING_PERIOD_MICROS)
        sensorManager = manager
        listener = newListener
    }

    /**
     * Désabonne le détecteur. À appeler dès que l'app passe en arrière-plan :
     * un écouteur de capteur oublié réveille le CPU en continu et vide la
     * batterie sans que rien ne l'utilise.
     */
    fun stop() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
        sensorManager = null
    }
}
