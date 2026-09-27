package fr.thermostat6.app180.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Équivalent Android de l'énumération `Haptics` iOS
 * (`apple/180/Extensions.swift:91-104`).
 *
 * L'iOS dispose de trois générateurs distincts — impact, notification,
 * sélection — là où Android n'expose qu'un vibreur et un catalogue d'effets
 * prédéfinis. La correspondance retenue :
 *
 * | iOS                          | Android                       |
 * |------------------------------|-------------------------------|
 * | `UIImpactFeedbackGenerator`  | [light] · `EFFECT_TICK`       |
 * | `UINotificationFeedbackGenerator(.success)` | [success] · `EFFECT_DOUBLE_CLICK` |
 * | `UISelectionFeedbackGenerator` | [selection] · `EFFECT_TICK` |
 *
 * [selection] et [light] retombent sur le même effet : `EFFECT_TICK` est le
 * plus discret du catalogue, et Android n'offre rien de plus léger avant
 * l'API 30. Les deux restent des points d'appel distincts pour que la parité
 * avec l'iOS reste lisible côté appelants.
 *
 * Initialiser une fois via [init] dans Application.onCreate().
 */
object HapticFeedbackManager {

    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Vibration légère — utilisée sur toggle favori, shake, etc. */
    fun light() {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vib.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /**
     * Confirmation d'une action aboutie — connexion, déconnexion.
     * Miroir de `Haptics.success()` (`UINotificationFeedbackGenerator.success`) :
     * une double impulsion, distincte du simple tick d'une interaction courante.
     */
    fun success() {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            // Repli API 26-28 : deux impulsions séparées, même intention.
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 30L, 90L, 30L), -1))
        }
    }

    /**
     * Changement de sélection — tap sur une notification, rappel d'une
     * recherche récente. Miroir de `Haptics.selection()`.
     */
    fun selection() {
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vib.vibrate(VibrationEffect.createOneShot(15L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
