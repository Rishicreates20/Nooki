#!/usr/bin/env bash
# Runs the real debug APK on an emulator: opens every tab, posts real notifications, and checks that
# a missed call is noticed while an OTP leaves no trace on disk. Screens land in emulator-shots/.
set -uo pipefail
PKG=com.aistudio.nookipet.r8k2m
ACTIVITY=$PKG/com.example.MainActivity
LISTENER=$PKG/com.example.notice.NookiNotificationListener
OUT=emulator-shots
mkdir -p "$OUT"
FAILED=0

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
fail() { echo "CHECK FAILED: $1"; FAILED=1; }
pass() { echo "CHECK PASSED: $1"; }

adb install -r -t app/build/outputs/apk/debug/app-debug.apk || exit 1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb logcat -c

SIZE="$(adb shell wm size | tail -1 | awk '{print $NF}' | tr -d '\r')"
W=${SIZE%x*}; H=${SIZE#*x}
echo "Screen $W x $H"

# First run, before anything is set up.
adb shell am start -W -n $ACTIVITY >/dev/null
sleep 6
shot 01_first_open

# The notification reader, as a person would turn it on in Settings.
adb shell cmd notification allow_listener $LISTENER
sleep 2

# Fill the app with the sample day.
adb shell am force-stop $PKG
adb shell am start -W -n $ACTIVITY --ez nooki.sample_day true >/dev/null
sleep 8
shot 02_today

# Bottom tabs: Today, Remember, Nooki, evenly spread above the gesture bar.
TAB_Y=$((H - 130))
adb shell input tap $((W / 2)) $TAB_Y
sleep 3
shot 03_remember
adb shell input tap $((W * 5 / 6)) $TAB_Y
sleep 8
shot 04_nooki
adb shell input tap $((W / 6)) $TAB_Y
sleep 2

# Real notifications, sent from the shell. The whole command is one quoted string because adb
# joins its arguments with spaces, which would otherwise split "Missed call" into two words.
post() { adb shell "cmd notification post -S bigtext -t '$1' '$2' '$3'"; }
post "Missed call" call1 "Sharma uncle"
post "Your electricity bill" bill1 "Rs. 1240 is due on 28 Dec 2026"
post "HDFC Bank" otp1 "Your OTP is 918273. Do not share it."
post "Bank" bal1 "Rs.2000 debited from a/c XX4411. Avl Bal Rs.777123"
sleep 5
adb shell dumpsys notification --noredact | grep -E "android.title=|android.text=" | tail -12
shot 05_after_notifications

# Look at the real database files for what was and was not kept. Sample data must be found first,
# or "no trace" would prove nothing.
sleep 2
adb exec-out "run-as $PKG cat databases/nooki_desk_pet.db" > "$OUT/db.bin"
adb exec-out "run-as $PKG cat databases/nooki_desk_pet.db-wal" >> "$OUT/db.bin"
ls -la "$OUT/db.bin"
if LC_ALL=C grep -aq 'Passport is in the blue bag' "$OUT/db.bin"; then
  pass "the database could be read"
  LC_ALL=C grep -aq 'Sharma uncle' "$OUT/db.bin" && pass "missed call was noticed" || fail "missed call not in the database"
  LC_ALL=C grep -aqi 'Electricity bill' "$OUT/db.bin" && pass "bill was noticed" || fail "bill not in the database"
  LC_ALL=C grep -aq '918273' "$OUT/db.bin" && fail "OTP reached the database" || pass "OTP left no trace"
  LC_ALL=C grep -aq '777123' "$OUT/db.bin" && fail "bank balance reached the database" || pass "bank balance left no trace"
else
  fail "could not read the database, so nothing about it was proven"
fi
rm -f "$OUT/db.bin"

adb shell am force-stop $PKG
adb shell am start -W -n $ACTIVITY >/dev/null
sleep 6
shot 06_today_after_notifications

# A real reminder, saved through the share path, must ring.
share() { adb shell "am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT '$1' -n $ACTIVITY" >/dev/null; }
share "Ring test in 1 minute"
sleep 4
shot 07_capture_from_share
adb shell input keyevent KEYCODE_ENTER
sleep 3
shot 08_after_save
FOUND=0
for _ in $(seq 1 26); do
  if adb shell dumpsys notification --noredact | grep -q "Ring test"; then FOUND=1; break; fi
  sleep 5
done
[ "$FOUND" = 1 ] && pass "a saved reminder rang" || fail "the reminder did not ring"
adb shell cmd statusbar expand-notifications
sleep 3
shot 09_notification
adb shell cmd statusbar collapse

adb logcat -d -t 4000 > "$OUT/logcat.txt"
if grep -E "FATAL EXCEPTION|AndroidRuntime: *Process: $PKG" "$OUT/logcat.txt" >/dev/null; then
  fail "the app crashed"
  grep -A25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
else
  pass "no crashes"
fi

# Reboot for real, with a reminder waiting: it has to come back and ring.
share "Reboot test in 3 minutes"
sleep 4
adb shell input keyevent KEYCODE_ENTER
sleep 3
adb reboot
adb wait-for-device
for _ in $(seq 1 60); do
  [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break
  sleep 5
done
pass "phone rebooted"
FOUND=0
for _ in $(seq 1 60); do
  if adb shell dumpsys notification --noredact 2>/dev/null | grep -q "Reboot test"; then FOUND=1; break; fi
  sleep 5
done
[ "$FOUND" = 1 ] && pass "the reminder survived a reboot and rang" || fail "the reminder did not survive a reboot"
shot 10_after_reboot
adb logcat -d -t 1500 > "$OUT/logcat_after_reboot.txt"
grep -q "FATAL EXCEPTION" "$OUT/logcat_after_reboot.txt" && fail "crash after reboot" || pass "no crash after reboot"

exit $FAILED
