<div align="center">

<img src="docs/nooki-icon.png" alt="Nooki app icon" width="240" />

# Nooki: The Desk-Pet Reminder

**A native Android reminder app with a real-time 3D companion who reacts to your schedule.**

[![Build APK](https://github.com/Rishicreates20/Nooki/actions/workflows/android-apk.yml/badge.svg)](https://github.com/Rishicreates20/Nooki/actions/workflows/android-apk.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-7.0%2B%20%C2%B7%20target%2036-3DDC84?logo=android&logoColor=white)
![3D](https://img.shields.io/badge/3D-SceneView%20%2F%20Filament-F2A33A)

</div>

---

<p align="center">
  <img src="docs/images/nooki-poster-square-1080.png" alt="Nooki poster: reminders with a tiny friend on your desk" width="560" />
</p>

Nooki sits on your desk, which here means your home screen. It naps when nothing is due, perks up
when a reminder is coming, and gets visibly flustered when tasks pile up. Reminders fire on time
through exact alarms, and they survive reboots, time-zone changes and app updates.

Tell it what's coming in plain words, like *"physics exam on friday at 10am"* or
*"claude limit resets in 5h"*, and Nooki works out the time, what kind of thing it is, and when to
give you a heads-up.

> [!NOTE]
> Plain-language quick add and workflow tracking (exams, deadlines, AI limit resets, bills, habits,
> heads-ups, repeats and countdowns) ship with
> [PR #3](https://github.com/Rishicreates20/Nooki/pull/3), which is still in review. Builds from
> `main` don't have them yet.

<p align="center">
  <img src="docs/nooki-pet-moods.png" alt="Nooki's four moods: Sleeping, Happy, Alert, Overwhelmed" />
  <br/>
  <sub>Sleeping · Happy · Alert · Overwhelmed. These are rendered from the bundled glTF model.</sub>
</p>

## Contents

- [How to use Nooki](#how-to-use-nooki)
- [Get the app](#get-the-app)
- [Features](#features)
- [Workflow tracking](#workflow-tracking)
- [Tech stack](#tech-stack) · [Architecture](#architecture)
- [Custom 3D models](#custom-3d-models) · [Tests](#tests) · [Roadmap](#roadmap)
- [Share Nooki](#share-nooki)

## How to use Nooki

<table>
<tr>
<td width="50%"><img src="docs/images/nooki-howto-square-1080.png" alt="How to use Nooki in five steps" /></td>
<td>

1. **Get the app.** Download the APK (see [Get the app](#get-the-app)) and tap it to install.
   Allow installs from your browser or file manager if Android asks.
2. **Allow the asks.** If Nooki shows **Allow Notifications** or **Enable Exact Alarms**, tap it
   so reminders ring on time. Each button only appears while that permission is missing, and older
   Android versions need neither.
3. **Tell Nooki.** Type *"physics exam on friday at 10am"* or *"claude limit resets in 5h"* and
   tap **Remind me**. Nooki spots exams, deadlines, bills and habits on its own.
   *(Quick add ships with [#3](https://github.com/Rishicreates20/Nooki/pull/3). On `main` you pick
   the title and time yourself.)*
4. **Nooki keeps watch.** It sends heads-ups before exams, deadlines and bills, shows a countdown on
   every card, and its mood follows what's due. Tap it for a happy bounce.
5. **When it rings,** tap **Complete** (+XP) or **Snooze 5m** on the notification, or swipe it
   away to dismiss.

</td>
</tr>
</table>

## Get the app

### Download a build

Every push to `main` (and every pull request) builds APKs on GitHub. Open the latest successful
[Build APK run](https://github.com/Rishicreates20/Nooki/actions/workflows/android-apk.yml),
download the `nooki-apk-…` artifact, unzip it on your phone and open `app-debug.apk`.

### Build it yourself

```bash
git clone https://github.com/Rishicreates20/Nooki.git && cd Nooki
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release signing, per-ABI APKs and sideloading troubleshooting are covered in
[docs/BUILD_AND_INSTALL.md](docs/BUILD_AND_INSTALL.md).

## Features

- **Plain-language quick add** *([#3](https://github.com/Rishicreates20/Nooki/pull/3))*. Type a
  reminder the way you'd text a friend, like "physics exam on friday at 10am", and Nooki works out
  the date, time, kind and repeat. If you leave out the time, it asks.
- **Workflow tracking** *([#3](https://github.com/Rishicreates20/Nooki/pull/3))*. Exams, deadlines,
  AI limit resets, bills and habits each get their own heads-ups, repeats, countdown and mood rules.
  See [Workflow tracking](#workflow-tracking).
- **Real-time 3D companion.** An original glTF model rendered with SceneView (Filament). It has
  four mood animations, a spring squash-and-stretch with haptics when you tap it, and a lean that
  follows your phone's tilt via the gravity sensor.
- **Moods driven by your schedule.** `PetMoodResolver` derives Nooki's state from the reminders
  stored in Room, and that state drives the pet, the card colors and the mood badge. Tapping the
  pet briefly plays a Happy reaction on top of the current mood.
- **Exact, reliable alarms.** Uses `AlarmManager.setAlarmClock`, which wakes the device through
  Doze. If the Android 12+ exact-alarm permission is revoked, it falls back to
  `setAndAllowWhileIdle`. Alarms are restored after boot, package replace and time/time-zone
  changes.
- **Actionable notifications.** Complete (+XP) or Snooze 5m straight from the notification;
  swiping it away dismisses the reminder.
- **XP and levels.** Completing reminders earns XP based on priority.
- **Considered design.** Asymmetric card shapes, a mood badge whose `RoundedPolygon` shape morphs
  between moods, contextual color shifts, spring physics, and Fredoka + Plus Jakarta Sans
  typography.
- **Adaptive icon** with a themed (monochrome) layer for Android 13+.

## Workflow tracking

> Ships with [PR #3](https://github.com/Rishicreates20/Nooki/pull/3).

Nooki reads the kind of reminder from your words and treats each one differently:

| Kind | Say something like | Heads-ups before it | How it affects Nooki |
| --- | --- | --- | --- |
| Exam | "physics exam on friday at 10am" | 1 week, 1 day, 2 hours | Awake a day ahead, Alert in the last 3 hours. A missed one counts double. |
| Deadline | "assignment due monday 11:59pm" | 3 days, 1 day, 3 hours | Awake a day ahead, Alert in the last 3 hours. A missed one counts double. |
| AI limit reset | "claude limit resets in 5h" | none | Good news only. It never stresses Nooki, and Nooki is Happy when it rings. |
| Bill or renewal | "netflix renews on the 15th" | 3 days, 1 day | Awake a day ahead, Alert in the last hour. A missed one counts double. |
| Habit | "take vitamins every day at 9pm" | none | Same as a normal task. |

- **Your own heads-ups.** Add them in plain words: *"maths exam on dec 3, remind me 2 days before
  and 1 hour before"*.
- **Repeats.** Every day, every weekday, every week, every month or every year, keeping the same
  time. A monthly bill on the 31st lands on the last day of shorter months.
- **Countdowns.** Every card shows what's left, like "2 days left", "5 h left" or "12 min left".
  Heads-up notifications are quieter than the alarm itself.

## Tech stack

| Area | Choice |
| --- | --- |
| Language / UI | Kotlin 2.2, Jetpack Compose, Material 3 |
| Persistence | Room (KSP) with reactive `Flow`s, the single source of truth |
| Scheduling | `AlarmManager.setAlarmClock` with an inexact fallback, `BroadcastReceiver`s with `goAsync()` |
| 3D | SceneView 2.3 on Google Filament + gltfio |
| Shapes | `androidx.graphics:graphics-shapes` (`RoundedPolygon`, `Morph`) |
| Build | AGP 9, Gradle 9.3, R8 + resource shrinking, optional ABI splits |
| CI | GitHub Actions: unit tests, lint, and debug + release APK artifacts |

## Architecture

```
Room (ReminderDao) ──Flow──▶ ReminderRepository ──▶ Compose UI (state hoisted, UDF)
        ▲                          │                      │
        │                          ▼                      ▼
 Boot/Time receivers ◀── ExactAlarmScheduler      PetMoodResolver ──▶ NookiPet3D + MoodBadge
 Alarm/Action receivers ─────────────┘
```

- `data/`: Room entity, DAO, database and repository. Every state change goes through Room.
- `scheduler/`: exact alarm scheduling, notification channel, and the alarm, action and boot
  receivers.
- `pet/`: `PetMood` + `PetMoodResolver` (pure, unit-tested) and the `NookiPet3D` composable.
- `ui/`: theme, typography and reusable components.
- `tools/`: stdlib-only Python scripts that generate the 3D model and the launcher icon.

## Custom 3D models

Drop your own `.glb` into `app/src/main/assets/models/` and name its clips `Sleeping`, `Happy`,
`Alert` and `Overwhelmed` (common aliases like `Idle` or `Jump` also work). See
[the models README](app/src/main/assets/models/README.md). To regenerate the bundled pet or the icon:

```bash
python3 tools/pet-model/generate_nooki_glb.py
python3 tools/icon/generate_icon.py
```

## Tests

```bash
./gradlew :app:testDebugUnitTest   # includes PetMoodResolverTest
./gradlew :app:lintDebug           # report: app/build/reports/lint-results-debug.html
```

## Roadmap

- [ ] Full UI theme overhaul (tonal elevation, glass surfaces, tactile components)
- [ ] Home-screen Glance widget showing Nooki's mood
- [ ] Guided setup for OEM battery restrictions ([dontkillmyapp.com](https://dontkillmyapp.com))

## Share Nooki

Ready-made images for social posts, sized for feeds (1080×1080) and stories (1080×1920). Click any
of them to open the full-size PNG.

<p align="center">
  <a href="docs/images/nooki-poster-story-1080x1920.png"><img src="docs/images/nooki-poster-story-1080x1920.png" alt="Nooki story poster" width="260" /></a>
  &nbsp;
  <a href="docs/images/nooki-howto-story-1080x1920.png"><img src="docs/images/nooki-howto-story-1080x1920.png" alt="How to use Nooki, story format" width="260" /></a>
</p>

| Image | Size | Use it for |
| --- | --- | --- |
| [nooki-poster-square-1080.png](docs/images/nooki-poster-square-1080.png) | 1080×1080 | Feed post introducing Nooki |
| [nooki-poster-story-1080x1920.png](docs/images/nooki-poster-story-1080x1920.png) | 1080×1920 | Story or Reel cover |
| [nooki-howto-square-1080.png](docs/images/nooki-howto-square-1080.png) | 1080×1080 | Feed post with the five setup steps |
| [nooki-howto-story-1080x1920.png](docs/images/nooki-howto-story-1080x1920.png) | 1080×1920 | Story with the five setup steps |

---

<sub>Built by <a href="https://github.com/Rishicreates20">Rishi</a>. The model, icon and code in
this repository are original.</sub>
