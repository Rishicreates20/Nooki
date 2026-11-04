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

# Real notifications, sent from the shell.
adb shell cmd notification post -S bigtext -t "Missed call" call1 "Sharma uncle"
adb shell cmd notification post -S bigtext -t "Your electricity bill" bill1 "Rs. 1240 is due on 28 Dec 2026"
adb shell cmd notification post -S bigtext -t "HDFC Bank" otp1 "Your OTP is 918273. Do not share it."
adb shell cmd notification post -S bigtext -t "Bank" bal1 "Rs.2000 debited from a/c XX4411. Avl Bal Rs.777123"
sleep 4
shot 05_noticed_shade_closed

adb shell am force-stop $PKG
adb shell am start -W -n $ACTIVITY >/dev/null
sleep 6
shot 06_today_after_notifications

# Look at the real database files for what was and was not kept.
adb exec-out run-as $PKG sh -c 'cat databases/nooki_desk_pet.db databases/nooki_desk_pet.db-wal 2>/dev/null' > "$OUT/db.bin" || true
if [ -s "$OUT/db.bin" ]; then
  LC_ALL=C grep -aq 'Sharma uncle' "$OUT/db.bin" && pass "missed call was noticed" || fail "missed call not in the database"
  LC_ALL=C grep -aq 'Electricity bill' "$OUT/db.bin" && pass "bill was noticed" || fail "bill not in the database"
  LC_ALL=C grep -aq '918273' "$OUT/db.bin" && fail "OTP reached the database" || pass "OTP left no trace"
  LC_ALL=C grep -aq '777123' "$OUT/db.bin" && fail "bank balance reached the database" || pass "bank balance left no trace"
else
  fail "could not read the database"
fi
rm -f "$OUT/db.bin"

# A real alarm: tell the app through its own share path, then watch it ring.
adb shell am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'test ring in 1 minute'" -n $ACTIVITY >/dev/null
sleep 4
shot 07_capture_from_share
adb shell input keyevent KEYCODE_ENTER
sleep 3
shot 08_after_save

adb logcat -d -t 4000 > "$OUT/logcat.txt"
if grep -E "FATAL EXCEPTION|AndroidRuntime: *Process: $PKG" "$OUT/logcat.txt" >/dev/null; then
  fail "the app crashed"
  grep -A25 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -60
else
  pass "no crashes"
fi

# Reboot-style recovery: the system broadcast must reach the receiver and not crash it.
adb shell am broadcast -a android.intent.action.MY_PACKAGE_REPLACED -n $PKG/com.example.scheduler.receiver.BootCompletedReceiver || true
sleep 3
adb logcat -d -t 400 | grep -E "FATAL EXCEPTION" && fail "boot recovery crashed" || pass "boot recovery ran"

exit $FAILED
