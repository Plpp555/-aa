#!/usr/bin/env bash
# エミュレータ上でランチャーを起動して主要画面のスクリーンショットとクラッシュログを出力する。
set -u
PKG=com.aa.launcher.debug
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=/tmp/smoke
mkdir -p "$OUT"

shot() {
  sleep "${2:-2}"
  adb exec-out screencap -p > "$OUT/$1.png"
  convert "$OUT/$1.png" -resize 300x -quality 45 "$OUT/$1.jpg"
  echo "shot $1 ($(stat -c %s "$OUT/$1.jpg") bytes)"
}

find_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1
  python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
target, path = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
for n in root.iter("node"):
    if n.get("text") == target or n.get("content-desc") == target:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
}

tap_text() {
  local xy
  xy=$(find_text "$1")
  echo "tap '$1' -> ${xy:-not found}"
  [ -n "$xy" ] && adb shell input tap $xy
}

long_press_text() {
  local xy
  xy=$(find_text "$1")
  echo "long press '$1' -> ${xy:-not found}"
  [ -n "$xy" ] && adb shell input swipe $xy $xy 900
}

dump_texts() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1
  echo "TEXTS $1: $(python3 -c "
import xml.etree.ElementTree as ET
try:
    r = ET.parse('$OUT/ui.xml').getroot()
    print(' | '.join(t for t in (n.get('text') for n in r.iter('node')) if t)[:1500])
except Exception as e:
    print('dump failed', e)
")"
}

adb install -r -g "$APK"
adb shell cmd package set-home-activity "$PKG/com.aa.launcher.MainActivity" || true
adb shell cmd notification allow_listener "$PKG/com.aa.launcher.service.BadgeNotificationListener" || true
adb shell wm size
adb logcat -c

adb shell am start -W -n "$PKG/com.aa.launcher.MainActivity"
shot 01_onboarding 12
dump_texts onboarding
tap_text "Get Started"
shot 02_home 4
dump_texts home

adb shell input swipe 900 1300 150 1300 250
shot 03_page2 3

adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell input swipe 150 1300 900 1300 250
shot 04_today 3
dump_texts today

adb shell input keyevent KEYCODE_HOME
sleep 2
for i in 1 2 3 4 5 6; do adb shell input swipe 900 1300 150 1300 200; sleep 1; done
shot 05_library 3
dump_texts library

adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell input swipe 400 700 400 1600 300
sleep 2
adb shell input text "set"
shot 06_spotlight 3
dump_texts spotlight
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_HOME
sleep 2

long_press_text "Settings"
shot 07_context_menu 2
dump_texts menu
tap_text "Edit Home Screen"
shot 08_edit_mode 2
tap_text "Done"
sleep 1

adb shell input swipe 980 220 980 1400 300
shot 09_control_center 3
dump_texts control_center
adb shell input keyevent KEYCODE_BACK
sleep 1

adb shell am start -W -n "$PKG/com.aa.launcher.settings.SettingsActivity"
shot 10_settings 4
dump_texts settings
tap_text "App Icons"
shot 11_settings_icons 3
tap_text "Tinted"
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_HOME
shot 12_home_tinted 4

echo "=== CRASH BUFFER ==="
adb logcat -d -b crash
echo "=== APP LOG ==="
adb logcat -d | grep -E "AndroidRuntime|FATAL EXCEPTION|ANR in|System.err|launcher.debug.*(Exception|Error)" | grep -v AppsFilter | tail -120
echo "=== END ==="
