#!/usr/bin/env bash
# 大画面での確認。同じスクリプトを 2 つの端末で動かす:
# - Pixel Tablet (横長): iPadOS と同じく 1 ページ表示。今日の表示は最大幅、App ライブラリは列が増えてフォルダはスマホ程度の大きさ
# - Pixel Fold (開いた画面、ほぼ正方形): ホームを 2 ページ並べて表示し、2 ページ表示でもドラッグでフォルダを作れる
# 座標は画面サイズの割合と画面密度から決める。
set -u
source "$(dirname "$0")/smoke_lib.sh"
SHOT_WIDTH=640

setup_app
DENS=$(adb shell wm density | tr -d '\r' | grep -o '[0-9]*' | tail -1)
DENS=${DENS:-320}
# ラベルの中心からアイコンの中心まで (約 34dp)
OFF=$((DENS * 34 / 160))
# 画面サイズ (向きによって変わるので座標は割合で決める)
SIZE=$(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)
set -- ${SIZE:-2560 1600}
W=$1; H=$2
# wm size は自然な向き (縦) で返ることがあるので、実際の向きはスクリーンショットの縦横で判断する
adb exec-out screencap -p > "$OUT/orient.png"
read -r SW SH < <(identify -format '%w %h' "$OUT/orient.png")
W=${SW:-$W}; H=${SH:-$H}
echo "screen ${W}x${H} density ${DENS}"
tap_text "Get Started"
shot W01_home 4
dump_texts home

adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell input swipe $((W * 12 / 100)) $((H * 45 / 100)) $((W * 70 / 100)) $((H * 45 / 100)) 300
shot W02_today 3
dump_texts today

adb shell input keyevent KEYCODE_HOME
sleep 2
for i in 1 2 3 4 5; do adb shell input swipe $((W * 86 / 100)) $((H * 45 / 100)) $((W * 16 / 100)) $((H * 45 / 100)) 250; sleep 1; done
shot W03_library 3
dump_texts library

adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell input swipe $((W * 94 / 100)) 30 $((W * 94 / 100)) $((H * 55 / 100)) 300
shot W04_control_center 3
adb shell input keyevent KEYCODE_BACK
sleep 1

adb shell input swipe $((W / 2)) $((H * 20 / 100)) $((W / 2)) $((H * 70 / 100)) 300
sleep 2
adb shell input text "set"
shot W05_spotlight 3
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_HOME
sleep 2

# 2 ページ表示でのドラッグ (フォルダ作成)
A=$(find_text "YouTube"); B=$(find_text "YT Music")
echo "YouTube=$A YTMusic=$B"
if [ -n "$A" ] && [ -n "$B" ]; then
  set -- $A $B
  drag_to "$1" "$(($2 - OFF))" "$3" "$(($4 - OFF))"
  shot W06_folder_created 2
  dump_texts folder
  tap_text "Done"
fi

long_press_text "Settings"
shot W07_context_menu 2
adb shell input keyevent KEYCODE_BACK
sleep 1

adb shell am start -W -n "$PKG/com.aa.launcher.settings.SettingsActivity" --activity-clear-task --es route icons
sleep 2
tap_text "Clear"
adb shell input keyevent KEYCODE_HOME
shot W08_home_clear 4

print_logs
