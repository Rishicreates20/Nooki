package com.example.scheduler

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.domain.model.SensoryCue
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Pure Kotlin/JVM 16-bit PCM WAV synthesizer that generates warm wooden marimba chimes
 * and soft fox purring sound effects on-device with zero external network or asset dependencies.
 */
object CozyWaveSynthesizer {

    const val SAMPLE_RATE = 22_050

    /**
     * Generates a complete 16-bit mono PCM `.wav` byte array for the given [SensoryCue].
     */
    fun synthesizeWavBytes(cue: SensoryCue): ByteArray {
        val samples: ShortArray = when (cue) {
            SensoryCue.PET_HEAD_PAT -> synthesizeFoxPurr(
                durationMs = 340,
                baseFreqHz = 128.0,
                endFreqHz = 138.0,
                flutterHz = 22.0,
                gain = 0.55
            )
            SensoryCue.PET_BELLY_SCRITCH -> synthesizeFoxPurr(
                durationMs = 460,
                baseFreqHz = 116.0,
                endFreqHz = 152.0,
                flutterHz = 25.0,
                gain = 0.58
            )
            SensoryCue.PET_PLAY_BOOP -> synthesizeWoodenMarimbaSequence(
                notesHz = doubleArrayOf(523.25, 783.99), // C5 -> G5
                noteDurationsMs = intArrayOf(115, 210),
                gain = 0.65
            )
            SensoryCue.TASK_COMPLETED -> synthesizeWoodenMarimbaSequence(
                notesHz = doubleArrayOf(523.25, 659.25, 783.99, 1046.50), // C5 -> E5 -> G5 -> C6
                noteDurationsMs = intArrayOf(95, 95, 105, 240),
                gain = 0.72
            )
            SensoryCue.KNOT_TIED -> synthesizeWoodenMarimbaSequence(
                notesHz = doubleArrayOf(659.25), // Warm E5 wooden pluck
                noteDurationsMs = intArrayOf(180),
                gain = 0.58
            )
        }
        return encodePcm16MonoWav(samples, SAMPLE_RATE)
    }

    /**
     * Synthesizes a soft, warm fox purr using a low fundamental frequency + 2nd harmonic,
     * amplitude-modulated by a rhythmic purr flutter and shaped with a cozy breath envelope.
     */
    private fun synthesizeFoxPurr(
        durationMs: Int,
        baseFreqHz: Double,
        endFreqHz: Double,
        flutterHz: Double,
        gain: Double
    ): ShortArray {
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / totalSamples.coerceAtLeast(1)
            val freq = baseFreqHz + (endFreqHz - baseFreqHz) * progress

            // Smooth attack and release envelope
            val attack = (progress / 0.15).coerceIn(0.0, 1.0)
            val release = ((1.0 - progress) / 0.25).coerceIn(0.0, 1.0)
            val envelope = attack * release

            // Rhythmic purr flutter (oscillates smoothly between 0.30 and 1.0)
            val flutter = 0.65 + 0.35 * sin(2.0 * PI * flutterHz * t)

            // Warm fundamental + soft octave harmonic + subtle sub-harmonic warmth
            val wave = 0.65 * sin(2.0 * PI * freq * t) +
                0.25 * sin(2.0 * PI * (freq * 2.0) * t) +
                0.10 * sin(2.0 * PI * (freq * 0.5) * t)

            val sampleValue = (wave * flutter * envelope * gain * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = sampleValue.toShort()
        }
        return out
    }

    /**
     * Synthesizes a sequence of warm wooden marimba/kalimba notes.
     * Wooden bar acoustics feature a strong fundamental (`1x`) and a fast-decaying woody
     * 4th harmonic (`4x`) with a rounded attack that avoids harsh metallic clicks.
     */
    private fun synthesizeWoodenMarimbaSequence(
        notesHz: DoubleArray,
        noteDurationsMs: IntArray,
        gain: Double
    ): ShortArray {
        val totalDurationMs = noteDurationsMs.sum()
        val totalSamples = (SAMPLE_RATE * totalDurationMs) / 1000
        val out = ShortArray(totalSamples)

        var cursorSample = 0
        for (idx in notesHz.indices) {
            val freq = notesHz[idx]
            val durMs = noteDurationsMs[idx]
            val noteSamples = ((SAMPLE_RATE * durMs) / 1000).coerceAtMost(totalSamples - cursorSample)

            for (i in 0 until noteSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                // Rounded 4ms wooden mallet attack + natural exponential decay
                val attack = (t / 0.004).coerceIn(0.0, 1.0)
                val bodyDecay = exp(-t * 9.5)
                val woodyOvertoneDecay = exp(-t * 28.0)

                val fundamental = sin(2.0 * PI * freq * t) * bodyDecay
                val woodyHarmonic = 0.28 * sin(2.0 * PI * (freq * 4.0) * t) * woodyOvertoneDecay
                val warmOctave = 0.14 * sin(2.0 * PI * (freq * 2.0) * t) * exp(-t * 14.0)

                val sampleFloat = (fundamental + woodyHarmonic + warmOctave) * attack * gain
                val existing = out[cursorSample + i].toInt()
                val mixed = (existing + (sampleFloat * Short.MAX_VALUE).toInt())
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                out[cursorSample + i] = mixed.toShort()
            }
            cursorSample += noteSamples
        }
        return out
    }

    /**
     * Encodes 16-bit mono PCM samples into a standard RIFF/WAVE byte array.
     */
    fun encodePcm16MonoWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val pcmByteCount = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + pcmByteCount).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + pcmByteCount)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt subchunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // PCM chunk size
        buffer.putShort(1) // AudioFormat = 1 (PCM)
        buffer.putShort(1) // NumChannels = 1 (Mono)
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2) // ByteRate = SampleRate * NumChannels * BitsPerSample/8
        buffer.putShort(2) // BlockAlign = NumChannels * BitsPerSample/8
        buffer.putShort(16) // BitsPerSample = 16

        // data subchunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmByteCount)
        for (sample in samples) {
            buffer.putShort(sample)
        }
        return buffer.array()
    }
}

/**
 * Multi-sensory feedback coordinator combining:
 * 1. Android `VibrationEffect` waveform haptics with distinct patterns per gesture & task completion.
 * 2. Low-latency `SoundPool` audio playback for cozy wooden chimes and soft fox purring.
 */
class NookiSensoryFeedbackManager(context: Context) {

    private val appContext = context.applicationContext
    private var soundPool: SoundPool? = null
    private val cueSoundIds = mutableMapOf<SensoryCue, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()

    init {
        initializeSoundPool()
    }

    private fun initializeSoundPool() {
        runCatching {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build()

            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    synchronized(loadedSoundIds) {
                        loadedSoundIds.add(sampleId)
                    }
                }
            }

            val sfxDir = File(appContext.cacheDir, "nooki_cozy_sfx").apply { mkdirs() }
            for (cue in SensoryCue.entries) {
                val wavFile = File(sfxDir, "nooki_${cue.name.lowercase()}.wav")
                if (!wavFile.exists() || wavFile.length() <= 44L) {
                    val wavBytes = CozyWaveSynthesizer.synthesizeWavBytes(cue)
                    FileOutputStream(wavFile).use { it.write(wavBytes) }
                }
                val soundId = pool.load(wavFile.absolutePath, 1)
                cueSoundIds[cue] = soundId
            }

            soundPool = pool
        }
    }

    /**
     * Triggers the requested [SensoryCue] across both `VibrationEffect` haptics and `SoundPool`
     * audio according to the user's [soundEnabled] and [hapticsEnabled] settings.
     */
    fun performSensoryCue(
        cue: SensoryCue,
        soundEnabled: Boolean = true,
        hapticsEnabled: Boolean = true
    ) {
        if (hapticsEnabled) {
            triggerHapticWaveform(appContext, cue)
        }
        if (soundEnabled) {
            playCozySound(cue)
        }
        NookiSchedulerTelemetry.record(
            source = "SensoryFeedback",
            message = "${cue.displayName} | Haptics=${if (hapticsEnabled) "ON (${cue.waveformTimings.size}-step VibrationEffect)" else "MUTED"} | Audio=${if (soundEnabled) cue.acousticDescription else "MUTED"}"
        )
    }

    private fun playCozySound(cue: SensoryCue) {
        runCatching {
            val pool = soundPool ?: return
            val soundId = cueSoundIds[cue] ?: return
            val volume = when (cue) {
                SensoryCue.TASK_COMPLETED -> 0.85f
                SensoryCue.PET_PLAY_BOOP -> 0.78f
                SensoryCue.KNOT_TIED -> 0.68f
                SensoryCue.PET_HEAD_PAT, SensoryCue.PET_BELLY_SCRITCH -> 0.72f
            }
            pool.play(soundId, volume, volume, 1, 0, 1.0f)
        }
    }

    fun release() {
        runCatching {
            soundPool?.release()
            soundPool = null
        }
    }

    companion object {
        /**
         * Executes a distinct `VibrationEffect` waveform for the given [SensoryCue].
         */
        fun triggerHapticWaveform(context: Context, cue: SensoryCue) {
            runCatching {
                val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val effect = if (vibrator.hasAmplitudeControl()) {
                            VibrationEffect.createWaveform(
                                cue.waveformTimings,
                                cue.waveformAmplitudes,
                                -1
                            )
                        } else {
                            VibrationEffect.createWaveform(cue.waveformTimings, -1)
                        }
                        vibrator.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(cue.waveformTimings.sum().coerceAtLeast(40L))
                    }
                }
            }
        }
    }
}
