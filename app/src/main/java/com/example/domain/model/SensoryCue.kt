package com.example.domain.model

/**
 * Semantic multi-sensory cues triggered when the user pets/plays with the 3D Nooki model,
 * ties a new knot, or marks a task/reminder as complete.
 *
 * Each cue defines:
 * - Distinct [waveformTimings] and [waveformAmplitudes] for Android's `VibrationEffect.createWaveform`
 * - Acoustic synthesis profile for the `SoundPool` cozy sound effects manager
 */
enum class SensoryCue(
    val displayName: String,
    val acousticDescription: String,
    val waveformTimings: LongArray,
    val waveformAmplitudes: IntArray
) {
    /**
     * Triggered on single-tap Head Pat (`PetGestureType.HEAD_PAT`).
     * Soft double-tap heartbeat pulse + warm low-frequency fox purr.
     */
    PET_HEAD_PAT(
        displayName = "Cozy Head Pat Purr",
        acousticDescription = "Soft fox purr (130 Hz warm harmonic envelope)",
        waveformTimings = longArrayOf(0L, 28L, 45L, 42L),
        waveformAmplitudes = intArrayOf(0, 115, 0, 165)
    ),

    /**
     * Triggered on double-tap 3D Play Spin & Hop (`PetGestureType.PLAY_BOOP`).
     * Playful rising 3-step bounce + two-note wooden marimba interval (C5 -> G5).
     */
    PET_PLAY_BOOP(
        displayName = "Playful Hop & Wooden Marimba",
        acousticDescription = "Warm wooden marimba hop (C5 ➔ G5)",
        waveformTimings = longArrayOf(0L, 30L, 35L, 45L, 40L, 65L),
        waveformAmplitudes = intArrayOf(0, 130, 0, 185, 0, 240)
    ),

    /**
     * Triggered on drag/rub across the 3D model (`PetGestureType.BELLY_SCRITCH`).
     * Low-amplitude rhythmic purr cadence + extended soft fox purr trill.
     */
    PET_BELLY_SCRITCH(
        displayName = "Rhythmic Belly Scritch Purr",
        acousticDescription = "Rhythmic fox purr trill (115 Hz–155 Hz)",
        waveformTimings = longArrayOf(0L, 22L, 28L, 26L, 28L, 30L, 28L, 24L),
        waveformAmplitudes = intArrayOf(0, 95, 0, 125, 0, 145, 0, 110)
    ),

    /**
     * Triggered when marking a scheduled reminder or To-Do task as Done.
     * Crisp knot-untie snap followed by celebratory bloom + ascending 4-note wooden chime (C5 -> E5 -> G5 -> C6).
     */
    TASK_COMPLETED(
        displayName = "Knot Untied & Cozy Chime Triad",
        acousticDescription = "Ascending wooden chime triad (C5 ➔ E5 ➔ G5 ➔ C6)",
        waveformTimings = longArrayOf(0L, 35L, 50L, 95L),
        waveformAmplitudes = intArrayOf(0, 190, 0, 255)
    ),

    /**
     * Triggered when tying a new knot / adding a To-Do task.
     * Gentle single wooden pluck (E5) + light tactile tick.
     */
    KNOT_TIED(
        displayName = "Knot Tied Wooden Pluck",
        acousticDescription = "Subtle wooden kalimba pluck (E5)",
        waveformTimings = longArrayOf(0L, 24L),
        waveformAmplitudes = intArrayOf(0, 140)
    );

    companion object {
        fun fromPetGesture(gesture: PetGestureType): SensoryCue = when (gesture) {
            PetGestureType.HEAD_PAT -> PET_HEAD_PAT
            PetGestureType.PLAY_BOOP -> PET_PLAY_BOOP
            PetGestureType.BELLY_SCRITCH -> PET_BELLY_SCRITCH
        }
    }
}
