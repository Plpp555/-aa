#!/usr/bin/env bash
# エミュレータ上でランチャーを起動して主要画面のスクリーンショットとクラッシュログを出力する。
set -u
source "$(dirname "$0")/smoke_lib.sh"
APK=app/build/outputs/apk/debug/app-debug.apk

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
