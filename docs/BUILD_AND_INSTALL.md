# Build Nooki and install it on your phone

Requirements: Android Studio (Ladybug or newer, bundles JDK 21) or JDK 17+ plus the Android SDK
(platform 36). Phone: Android 7.0+; enable **Developer options > USB debugging**.

```bash
# 0. One-time: create local.properties if Android Studio hasn't already
echo "sdk.dir=$HOME/Android/Sdk" > local.properties        # macOS: $HOME/Library/Android/sdk

# 1. Lint (report: app/build/reports/lint-results-debug.html)
./gradlew :app:lintDebug

# 2a. Debug APK (signed with the standard debug key, no setup needed)
./gradlew :app:assembleDebug
#     -> app/build/outputs/apk/debug/app-debug.apk

# 2b. Optimized release APK (R8 + resource shrinking)
cp keystore.properties.example keystore.properties          # then edit passwords
keytool -genkeypair -v -keystore nooki-release.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
./gradlew :app:assembleRelease
#     -> app/build/outputs/apk/release/app-release.apk
#     (without a keystore it is debug-signed and Gradle prints a warning)

# Optional: per-ABI APKs (much smaller; modern phones use arm64-v8a)
./gradlew :app:assembleRelease -PsplitAbi
#     -> app/build/outputs/apk/release/app-arm64-v8a-release.apk (+ universal)

# 3. Install
adb devices                                                  # phone must show "device"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.aistudio.nookipet.r8k2m/com.example.MainActivity
```

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The installed copy is signed with a different key (e.g. AI Studio build vs local). `adb uninstall com.aistudio.nookipet.r8k2m`, then install again. This wipes app data. |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | You installed a split APK for the wrong CPU. Check `adb shell getprop ro.product.cpu.abi` and install that APK, or the universal one. |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | Bump `versionCode` or use `adb install -r -d`. |
| Play Protect "unsafe app" warning | Expected for sideloaded apps. Tap **More details > Install anyway**. |
| `adb devices` shows `unauthorized` | Unlock the phone and accept the RSA prompt; `adb kill-server && adb start-server` if needed. |
| Black/empty pet stage | Filament needs OpenGL ES 3.0 (every phone since ~2014; some old emulators lack it). Use a real device or an emulator with "Hardware GLES 3.0". |
| Reminders late or missing | Allow **Alarms & reminders** (Android 12+), notifications (Android 13+), and set battery to **Unrestricted** for Nooki. OEM-specific steps: https://dontkillmyapp.com |
