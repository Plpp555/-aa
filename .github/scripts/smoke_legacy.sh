#!/usr/bin/env bash
# Android 6.0 (API 23) のエミュレータでの確認。API 23 にないコマンド (input motionevent / cmd ...) は使わない。
# アダプティブアイコン・HARDWARE ビットマップ・App ショートカットなどがない環境で、
# ホーム・今日の表示・App ライブラリ・Spotlight・長押しメニュー・編集モード・コントロールセンター・設定・
# アイコンのスタイル (クリア/色合い/ダーク) が動くことを確かめる。
set -u
source "$(dirname "$0")/smoke_lib.sh"

setup_app
adb shell getprop ro.build.version.sdk
shot L01_onboarding 2
tap_text "Get Started"
shot L02_home 4
dump_texts home

adb shell input swipe 150 1300 900 1300 250
shot L03_today 3
dump_texts today

adb shell input keyevent KEYCODE_HOME
sleep 2
for i in 1 2 3 4 5 6; do adb shell input swipe 900 1300 150 1300 200; sleep 1; done
shot L04_library 3
dump_texts library

adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell input swipe 400 700 400 1600 300
sleep 2
adb shell input text "set"
shot L05_spotlight 3
dump_texts spotlight
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_HOME
sleep 2

long_press_text "Settings"
shot L06_context_menu 2
dump_texts menu
tap_text "Edit Home Screen"
shot L07_edit_mode 2
tap_text "Done"
sleep 1

adb shell input swipe 980 220 980 1400 300
shot L08_control_center 3
dump_texts control_center
adb shell input keyevent KEYCODE_BACK
sleep 1

# アイコンのスタイル (Android 6.0 ではすべてレガシーアイコンの描画経路)
for style in Clear Tinted Dark; do
  adb shell am start -W -n "$PKG/com.aa.launcher.settings.SettingsActivity" --activity-clear-task --es route icons
  sleep 2
  tap_text "$style"
  sleep 1
  adb shell input keyevent KEYCODE_HOME
  shot "L09_home_$style" 4
done

adb shell am start -W -n "$PKG/com.aa.launcher.settings.SettingsActivity" --activity-clear-task --es route wallpaper
shot L10_wallpaper_settings 3
dump_texts wallpaper
adb shell input keyevent KEYCODE_HOME

print_logs
