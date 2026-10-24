#!/usr/bin/env bash
# 第 2 弾: ドラッグでのフォルダ作成、ウィジェット追加、カスタマイズ、ダークモード、壁紙、日本語表示。
set -u
source "$(dirname "$0")/smoke_lib.sh"

setup_app
tap_text "Get Started"
sleep 2

# テスト中は編集モードの揺れを止める (アニメーション中は uiautomator が待たされるため)
adb shell am start -W -n "$PKG/com.aa.launcher.settings.SettingsActivity" --es route animations
sleep 2
tap_text "Wiggle in Edit Mode"
shot 13_animations_settings 1
adb shell input keyevent KEYCODE_HOME
sleep 2

# YouTube を YT Music に重ねてフォルダを作る (長押し → そのままドラッグ)
A=$(find_text "YouTube"); B=$(find_text "YT Music")
echo "YouTube=$A YTMusic=$B"
if [ -n "$A" ] && [ -n "$B" ]; then
  set -- $A $B
  drag_to "$1" "$(($2 - 90))" "$3" "$(($4 - 90))" 3000
  shot 14_folder_open 2
  dump_texts folder
  adb shell input keyevent KEYCODE_BACK
  shot 15_home_folder 2
  dump_texts home_after_folder
fi

# 並べ替え: Maps を Calendar の位置へ
A=$(find_text "Maps"); B=$(find_text "Calendar")
if [ -n "$A" ] && [ -n "$B" ]; then
  set -- $A $B
  drag_to "$1" "$(($2 - 90))" "$(($3 - 60))" "$(($4 - 90))" 2000
  sleep 1
  tap_text "Done"
  shot 16_reordered 2
fi

# ウィジェットの追加
long_press_text "Settings"
sleep 1
tap_text "Edit Home Screen"
sleep 1
tap_text "Edit"
shot 17_edit_menu 1
tap_text "Add Widget"
shot 18_widget_picker 3
dump_texts widget_picker
tap_text "Add"
shot 19_widget_added 3

# カスタマイズ (クリアアイコン)
tap_text "Edit"
sleep 1
tap_text "Customize"
shot 20_customize 2
tap_text "Clear"
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 1
tap_text "Done"
shot 21_clear_icons 2

# ダークモード
adb shell am start -W -n "$PKG/com.aa.launcher.settings.SettingsActivity" --es route appearance
sleep 2
tap_text "Dark"
shot 22_appearance_dark 2
adb shell input keyevent KEYCODE_HOME
shot 23_home_dark 3

# 壁紙
adb shell am start -W -n "$PKG/com.aa.launcher.settings.SettingsActivity" --es route wallpaper
shot 24_wallpaper_settings 3
XY=$(find_text "Sunrise")
if [ -n "$XY" ]; then set -- $XY; adb shell input tap "$1" "$(($2 - 250))"; fi
sleep 2
adb shell input keyevent KEYCODE_HOME
shot 25_home_sunrise 3

# フォルダを開く
F=$(find_text "Entertainment")
[ -z "$F" ] && F=$(find_text "Folder")
if [ -n "$F" ]; then set -- $F; adb shell input tap "$1" "$(($2 - 90))"; fi
shot 26_folder_dark 2
adb shell input keyevent KEYCODE_BACK

# 日本語表示
adb shell cmd locale set-app-locales "$PKG" --locales ja-JP || true
sleep 3
adb shell am start -W -n "$PKG/com.aa.launcher.settings.SettingsActivity"
shot 27_settings_ja 3
dump_texts settings_ja
adb shell input keyevent KEYCODE_HOME
shot 28_home_ja 3
adb shell input swipe 400 700 400 1600 300
sleep 1
adb shell input text "keisan"
shot 29_spotlight_ja 2

print_logs
