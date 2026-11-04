package com.example.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.R

/**
 * Nooki's sound and touch feedback (PRD FR-4).
 *
 * Sounds go through [SoundPool], which decodes the short Ogg clips to PCM in memory once, so a tap
 * plays with no decoder start-up lag (MediaPlayer takes 150 ms or more). Haptics use the
 * predefined EFFECT_TICK / EFFECT_CLICK on Android 10+, and a short pulse before that.
 */
class PetFeedback(context: Context) {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val popId = soundPool.load(appContext, R.raw.sound_pet_pop, 1)
    private val sparkleId = soundPool.load(appContext, R.raw.sound_pet_sparkle, 1)

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** Light tick: a chip tap, snooze, or a keystroke-sized confirmation. */
    fun tap() {
        play(popId, 0.7f)
        haptic(predefined = VibrationEffect.EFFECT_TICK, fallbackMillis = 10)
    }

    /** Definite action: a reminder was saved. */
    fun confirm() {
        play(popId, 0.9f)
        haptic(predefined = VibrationEffect.EFFECT_CLICK, fallbackMillis = 20)
    }

    /** Celebration: a task is done. */
    fun celebrate() {
        play(sparkleId, 0.85f)
        haptic(predefined = VibrationEffect.EFFECT_CLICK, fallbackMillis = 25)
    }

    fun release() = soundPool.release()

    private fun play(soundId: Int, volume: Float) {
        // Returns 0 (and plays nothing) if the clip hasn't finished loading; never throws.
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    private fun haptic(predefined: Int, fallbackMillis: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    v.vibrate(VibrationEffect.createPredefined(predefined))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    v.vibrate(VibrationEffect.createOneShot(fallbackMillis, VibrationEffect.DEFAULT_AMPLITUDE))
                else -> {
                    @Suppress("DEPRECATION")
                    v.vibrate(fallbackMillis)
                }
            }
        }
    }
}
