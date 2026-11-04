<div align="center">

<img src="docs/nooki-icon.png" alt="Nooki app icon" width="320" />

# Nooki

**A small fox who pays attention for you, so nothing you missed, forgot or promised slips away.**

[![Build APK](https://github.com/Rishicreates20/Nooki/actions/workflows/android-apk.yml/badge.svg)](https://github.com/Rishicreates20/Nooki/actions/workflows/android-apk.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-7.0%2B%20%C2%B7%20target%2036-3DDC84?logo=android&logoColor=white)
![Privacy](https://img.shields.io/badge/internet%20permission-none-1F9D6B)

</div>

---

Most reminder apps only know what you type into them. People don't forget because they lack a list.
They forget because a call came in during a meeting, a bill arrived by SMS, a friend borrowed money,
or the passport went into "a safe place". Nooki answers three questions on one screen:

1. **What did I miss?** Calls, unopened messages, reminders nobody acted on.
2. **What did I forget?** Money out with people, things lent, tasks that keep slipping.
3. **What do I have to do?** Today, then what's coming up.

The full brief is in [docs/NOOKI_PRODUCT_PROMPT.md](docs/NOOKI_PRODUCT_PROMPT.md).

## What it does

- **Tell Nooki.** One line, typed or spoken, in English, Hinglish or Hindi. "kal subah 9 baje doctor",
  "pay rent on the 1st every month", "passport is in the blue bag", "Rahul owes me 500",
  "where is my passport". You see what Nooki understood as you type.
- **Reach it from anywhere.** The share sheet of any app, the text selection menu, a quick settings
  tile, a long press on the icon, and a home-screen widget.
- **Nooki notices.** With permission, it reads notifications on the phone and picks out missed
  calls, messages left unopened for 2 hours, bills, parcels and appointments.
- **Remember.** Where you put things, and who has your money or your stuff, with a running total.
- **Dates that matter.** Exams, bills, birthdays and expiries set up their own warnings, 30, 7 and 1
  days ahead where it makes sense. Birthdays repeat without being asked.
- **Nothing quietly disappears.** A swiped notification moves to "You missed". An ignored monthly
  bill still books next month. Medicine keeps asking until it's confirmed.
- **A daily rhythm.** A morning brief and an evening wrap with one tap to move what's left to
  tomorrow. Both stay silent when there's nothing to say.
- **Nooki herself.** Her mood comes from your real day, and she lives in 3D on her own tab.

## Privacy

Nooki ships **without the internet permission**, and CI fails the build if one ever appears
([check_no_internet.sh](tools/ci/check_no_internet.sh)). Everything stays on the phone, and app data
is excluded from cloud backup.

When noticing is on, notification text exists in memory only for the length of one check.
Message content is never saved, because there is no column to hold it. OTPs, passwords and bank
balances are dropped before anything can be written, which is covered by
[unit tests](app/src/test/java/com/example/notice/NotificationClassifierTest.kt) and by an
[emulator check](tools/ci/emulator_smoke.sh) that searches the real database file.

## Design

The signature is a thread. People once tied a knot in a handkerchief or the end of a sari so they
wouldn't forget, so here every item is a knot on one thread that falls from Nooki's collar. Tap a
knot to untie it. The thread changes colour with meaning: sindoor for missed, marigold for forgotten,
indigo for to do. Type is Baloo 2 and Mukta, both drawn for Latin and Devanagari together, so Hindi
never looks like a fallback.

## Tech stack

| Area | Choice |
| --- | --- |
| Language / UI | Kotlin 2.2, Jetpack Compose, Material 3, MVI state |
| Persistence | Room with reactive `Flow`s as the single source of truth, DataStore for settings |
| Scheduling | `AlarmManager` with three precision tiers, `BroadcastReceiver`s with `goAsync()` |
| Noticing | `NotificationListenerService` with an on-device classifier |
| Surfaces | Glance widget, `TileService`, share and process-text intents, app shortcuts |
| 3D | SceneView on Google Filament + gltfio |
| Build | AGP 9, Gradle 9.3, R8 + resource shrinking, optional ABI splits |
| CI | Unit tests, lint, privacy gate, rendered screens, and a real emulator run |

## Architecture

```
 Share / tile / voice / widget ─▶ CaptureRouter ─▶ reminder, saved thing, money, or question
                                       │
 Notifications ─▶ NotificationClassifier ─▶ drops secrets, keeps who/when/how much
                                       │
                                       ▼
                     Room (+ DataStore) ──Flow──▶ AttentionEngine ──▶ TodayDigest ──▶ Compose UI
                          ▲                                               │
                          │                                               ▼
 Alarm / action / boot receivers ◀── ReminderRepository ──▶ AlarmManager  BriefComposer ──▶ briefs
```

- `parser/`: `HinglishNormalizer`, `ReminderTextParser` and `CaptureRouter`. Pure Kotlin.
- `attention/`: `AttentionEngine` sorts everything into the three questions. Pure Kotlin.
- `notice/`: the classifier, which is pure Kotlin, and the listener service.
- `data/`: Room entities, DAOs, migrations and repositories.
- `scheduler/`: alarms, notifications, the daily rhythm and receivers.
- `presentation/`: MVI contract, `NookiViewModel` and the Compose screens.
- `widget/`, `capture/`: the home-screen widget and quick settings tile.

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
./gradlew :app:testDebugUnitTest   # logic, privacy, migrations and reminder lifecycles
./gradlew :app:testDebugUnitTest -Pscreenshots --tests "com.example.ScreenshotTest"   # renders every screen to PNG
./gradlew :app:lintDebug           # report: app/build/reports/lint-results-debug.html
```

## Roadmap

- [ ] 12 testers for 14 days, then publish measured delivery numbers
- [ ] Full Hindi interface
- [ ] Guided setup for OEM battery restrictions ([dontkillmyapp.com](https://dontkillmyapp.com))
- [ ] Back up to a file you control, since nothing goes to the cloud
- [ ] Move to a real application id before publishing on Google Play

---

<sub>Built by <a href="https://github.com/Rishicreates20">Rishi</a>. The model, icon and code in
this repository are original.</sub>
