# Nooki Multi-Sensory Feedback Engine: Haptics & Cozy Soundscapes

Delivers tactile `VibrationEffect` waveforms and a low-latency `SoundPool` audio synthesis manager that responds to 3D pet gestures (`HEAD_PAT`, `PLAY_BOOP`, `BELLY_SCRITCH`) and reminder completions with distinct haptic rhythms, warm wooden marimba chimes, and soft fox purring—paired with quick mute toggles in the home header and User Profile settings.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product and sensory design decisions incorporate your confirmed preferences from the clarification step:

- **Confirmed Decision 1 — Distinct Haptic Patterns per Gesture & Completion**: Each interaction triggers a custom-tailored `VibrationEffect` waveform (with `Composition` primitive fallbacks on supported Android versions) rather than a generic buzz:
  - *Head Pat (`HEAD_PAT`)*: Soft double-tap heartbeat pulse (`[0, 28, 45, 42] ms`, gentle amplitudes).
  - *Play Hop & Spin (`PLAY_BOOP`)*: Playful rising 3-step bounce (`[0, 30, 35, 45, 40, 65] ms`, ascending amplitudes).
  - *Belly Scritch (`BELLY_SCRITCH`)*: Low-amplitude rhythmic purr cadence (`[0, 22, 28, 26, 28, 30, 28, 24] ms`, warm subtle vibration).
  - *Task / Knot Completion*: Crisp "knot untie" snap followed by a celebratory bloom (`[0, 35, 50, 95] ms`, peak amplitude `255`).
- **Confirmed Decision 2 — Warm Wooden Chime & Soft Fox Purr Audio Character**: Uses Android's low-latency `SoundPool` backed by deterministically synthesized 16-bit PCM WAV samples generated on-device at startup (zero external network fetches, zero asset bloat):
  - *Cozy Fox Purr (`HEAD_PAT` / `BELLY_SCRITCH`)*: Warm low-frequency harmonic purr (`110 Hz–165 Hz` with gentle amplitude modulation and soft breath envelope).
  - *Playful Wooden Hop (`PLAY_BOOP`)*: Dual-note wooden marimba interval (`C5 -> G5` with warm exponential decay).
  - *Task Completion Chime*: Three-note ascending wooden kalimba/marimba triad (`C5 -> E5 -> G5 -> C6`) evoking untying a knot on the thread.
  - *Knot Tied Confirmation*: Subtle single wooden pluck (`E5`) when saving a new reminder or To-Do item.
- **Confirmed Decision 3 — Quick Mute Toggles + Profile Settings**: Adds instant one-tap **Sound** and **Haptics** toggle chips in the top home header bar alongside dedicated sensory controls persisted in the **User Profile** (`UserProfileEntity`).

---

## 1. Overview & Core Concept

- **What It Does**: Connects Nooki's reactive MVI state machine and 3D viewport directly to a unified multi-sensory feedback layer (`NookiSensoryFeedbackManager`). Every time the user pats Nooki's head, double-taps for a 3D spin, rubs Nooki's belly, ties a new knot, or marks a task as Done, the app responds with synchronized haptics and cozy acoustic feedback.
- **Target Audience / Persona**: Users who want a calm, tactile, and emotionally grounding desk companion that rewards daily consistency without harsh or jarring alert noises.
- **Key Value**: Transforms abstract task checking and 3D touch gestures into physical, rewarding moments while respecting quiet environments through one-tap mute controls.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Petting & Playing with 3D Nooki**:
     - Single-tapping Nooki triggers the ear-flutter animation, heart/star particles, a soft double-tap heartbeat vibration, and a warm acoustic purr.
     - Double-tapping Nooki triggers the 360° 3D spin and hop, a 3-step rising bounce vibration, and a playful two-note wooden marimba hop sound.
     - Dragging/rubbing across Nooki triggers live sparkle trails, a rhythmic purring vibration cadence, and a cozy purr trill.
  2. **Completing a Scheduled Reminder or To-Do Task**:
     - Tapping a knot on The Thread or checking off a task in the **To-Do & Done Tasks** section emits a crisp knot-untie haptic snap and plays the ascending wooden chime triad while Nooki celebrates.
  3. **Quick Mute & Profile Sensory Controls**:
     - Tapping the compact Sound or Haptics icon pill in the home header immediately toggles audio or vibration on/off with visual status feedback.
     - Opening the **Tasks & Profile** tab provides full sensory toggles and a **"Test Cozy Chime & Purr"** preview button inside the User Profile card.
- **Visual Identity & Theme**:
  - *Color Palette & Mood*: Preserves the Night Indigo (`#12153A`) and Neel Indigo (`#1C2254`) surfaces with Marigold (`#FFAE1A`) active audio indicators, Leaf Green (`#1F9D6B`) completion accents, and Washed Cotton (`#EEF0FA`) typography.
  - *Typography & Hierarchy*: `Baloo 2` for headers and badges; `Mukta` for sensory status labels and descriptions.
- **Interactive Feedback & Motion**:
  - Sensory toggle pills animate smoothly between active (`Marigold` / `LeafGreen` border) and muted (`WashedCotton` 45% alpha) states, logging real-time sensory events to the Reliability & Telemetry feed so users can inspect every triggered waveform and sound cue.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: On-Device Procedural PCM WAV Synthesis + `SoundPool`**
  - *Chosen Approach*: Generate compact, studio-clean 16-bit mono PCM `.wav` files in the app's cache directory on initialization (warm wooden marimba harmonics and amplitude-modulated fox purrs) and load them into Android's `SoundPool` with `USAGE_ASSISTANCE_SONIFICATION`.
  - *Why*: Guarantees sub-15ms playback latency on repeated taps, works 100% offline with zero network permissions, never fails due to missing external media codecs, and produces authentic warm acoustic envelopes.
  - *Alternatives Considered*: `ToneGenerator` (rejected because DTMF/beep tones sound harsh and robotic) or `MediaPlayer` (rejected due to higher initialization latency on rapid 3D petting gestures).
- **Decision 2: Semantic `SensoryCue` Side Effects in the MVI Pipeline**
  - *Chosen Approach*: Replace the single generic `TriggerHapticPulse` side effect with a rich `DeskSideEffect.PlaySensoryCue(val cue: SensoryCue)` event emitted by `DeskPetViewModel`, gated by the user's persisted `soundEnabled` and `hapticsEnabled` preferences in `UserProfileEntity`.
  - *Why*: Keeps `DeskPetViewModel` pure Kotlin and unit-testable while ensuring every gesture and task completion dispatches the exact matching haptic waveform and sound effect.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                            │
│                                                                            │
│  ┌──────────────────────────┐   ┌───────────────────────────────────────┐  │
│  │   Nooki3DCenterpiece     │   │  Home Header & UserProfileCard        │  │
│  │  • Tap -> HEAD_PAT       │   │  • Quick Sound / Haptics Mute Pills   │  │
│  │  • Double-Tap -> BOOP    │   │  • Profile Sensory Settings & Preview │  │
│  │  • Drag -> BELLY_SCRITCH │   │  • Thread Knots & To-Do / Done Tasks  │  │
│  └────────────┬─────────────┘   └───────────────────┬───────────────────┘  │
└───────────────┼─────────────────────────────────────┼──────────────────────┘
                │ DeskIntent                          │ DeskIntent
                ▼                                     ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   DeskPetViewModel (MVI StateFlow)                         │
│  • Combines Room Flows (Reminders + UserProfile sensory preferences)       │
│  • Emits DeskSideEffect.PlaySensoryCue(cue: SensoryCue)                    │
└───────────────┬─────────────────────────────────────┬──────────────────────┘
                │                                     │
                ▼                                     ▼
┌───────────────────────────────────┐ ┌──────────────────────────────────────┐
│   Room Database (AppDatabase)     │ │     NookiSensoryFeedbackManager      │
│  • user_profile table:            │ │  ┌────────────────────────────────┐  │
│    - sound_enabled (Boolean)      │ │  │ HapticEngine (VibrationEffect) │  │
│    - haptics_enabled (Boolean)    │ │  │ • Custom Waveforms & Timings   │  │
│  • reminders table:               │ │  └────────────────────────────────┘  │
│    - status & completed_day_epoch │ │  ┌────────────────────────────────┐  │
└───────────────────────────────────┘ │  │ CozySoundEffectsManager        │  │
                                      │  │ • SoundPool + Warm Chime/Purr  │  │
                                      │  └────────────────────────────────┘  │
                                      └──────────────────────────────────────┘
```

### Data Model & State

- **`SensoryCue` Enum**:
  - `PET_HEAD_PAT`: Soft double-pulse vibration + warm acoustic purr.
  - `PET_PLAY_BOOP`: Rising 3-step bounce vibration + two-note wooden marimba hop (`C5 -> G5`).
  - `PET_BELLY_SCRITCH`: Rhythmic purr vibration cadence + extended soft fox purr trill.
  - `TASK_COMPLETED`: Crisp knot-untie snap + ascending 4-note wooden chime triad (`C5 -> E5 -> G5 -> C6`).
  - `KNOT_TIED`: Gentle single tap + warm wooden pluck (`E5`).
- **`UserProfileEntity` Sensory Fields**:
  - Extends `UserProfileEntity` (with a safe Room schema migration) to include `soundEnabled: Boolean = true` and `hapticsEnabled: Boolean = true` so user preferences survive app restarts.

### Interactive Component & State Mapping

- **3D Pet Gestures (`TriggerPetGesture`)**: Maps `HEAD_PAT` -> `SensoryCue.PET_HEAD_PAT`, `PLAY_BOOP` -> `SensoryCue.PET_PLAY_BOOP`, and `BELLY_SCRITCH` -> `SensoryCue.PET_BELLY_SCRITCH`.
- **Task Completion (`CompleteReminder`)**: Emits `SensoryCue.TASK_COMPLETED` when untying a knot on The Thread or checking off an item in the To-Do Tasks list.
- **Task / Breath Creation (`SaveBreathInput`, `QuickAddTodoTask`, `ScheduleReminder`)**: Emits `SensoryCue.KNOT_TIED`.
- **Quick Mute Toggles (`ToggleSoundEnabled`, `ToggleHapticsEnabled`)**: Updates `UserProfileEntity` in Room, updates `DeskUiState` immediately, and plays a confirmation cue when toggled on.
