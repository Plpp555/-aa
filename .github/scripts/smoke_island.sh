#!/usr/bin/env bash
# Dynamic Island 版 (FLAVOR=island): 島の位置 (カメラ穴を中心に)・充電・再生中の音楽・タップ (ぽよん / ポップアップ)。
# タブレット・折りたたみでは島が出ないこと (ログの "disabled") を確認して終わる。
set -u
source "$(dirname "$0")/smoke_lib.sh"
SHOT_WIDTH=640
API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')

# 島の位置 (アプリが debug ビルドでログに出す "placed ... rect=l,t,r,b")
island_center() {
  local line
  line=$(adb logcat -d -s LiquidIsland:D | grep "placed" | tail -1)
  echo "ISLAND_LOG $line" >&2
  echo "$line" | sed -n 's/.*rect=\([0-9-]*\),\([0-9-]*\),\([0-9-]*\),\([0-9-]*\).*/\1 \2 \3 \4/p' |
    awk '{ print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

# タップした直後 (ぽよんの途中) を撮る
tap_and_shot() {
  local name=$1 x=$2 y=$3
  adb shell "input tap $x $y; screencap -p /sdcard/poyon.png"
  adb pull /sdcard/poyon.png "$OUT/$name.png" > /dev/null 2>&1
  convert "$OUT/$name.png" -crop "x${TOP_H:-320}+0+0" +repage -quality 70 "$OUT/$name.jpg"
  echo "SHOT_BEGIN $name"
  base64 -w 0 "$OUT/$name.jpg"
  echo
  echo "SHOT_END $name"
}

# Android 10 以降のエミュレータにある開発者向けの切り欠き (パンチホール) を有効にする
if [ "$API" -ge 29 ]; then
  adb shell cmd overlay list | grep -i cutout || true
  adb shell cmd overlay enable com.android.internal.display.cutout.emulation.hole || true
  sleep 3
fi
# 充電していない状態から始める
adb shell dumpsys battery unplug || true
adb shell dumpsys battery set status 3 || true
adb shell dumpsys battery set level 64 || true

setup_app
tap_text "Get Started"
shot i01_home 4
shot_top i01_top 0
adb logcat -d -s LiquidIsland:D | tail -20

if adb logcat -d -s LiquidIsland:D | grep -q "disabled"; then
  echo "ISLAND: disabled on this device (expected on tablets and foldables)"
  print_logs
  exit 0
fi

read -r cx cy < <(island_center)
echo "ISLAND_CENTER $cx $cy"
if [ -z "${cx:-}" ]; then
  echo "ISLAND: no placement log"
  print_logs
  exit 0
fi

# 何も表示していないとき: タップで「ぽよん」
tap_and_shot i02_idle_poyon "$cx" "$cy"
shot_top i02_idle_after 1

# 充電中: 稲妻と % を表示、タップで「ぽよん」
adb shell dumpsys battery set ac 1
adb shell dumpsys battery set status 2
adb shell dumpsys battery set level 76
shot_top i03_charging 2
tap_and_shot i03_charging_poyon "$cx" "$cy"
shot_top i03_charging_after 1

# 再生中の音楽 (debug ビルドの擬似メディア): アートワークと波形、タップで Liquid Glass のポップアップ。
# 実機で表示されなかった状況を再現する: 一時停止中のセッションが先にあり (別のアプリが一覧の先頭)、
# そのあと曲が再生を始める (セッションの一覧は変わらず、再生状態だけが変わる)
adb shell am broadcast -n "$PKG/com.aa.launcher.debugtools.DebugMediaReceiver" --es cmd prepare
sleep 3
shot_top i04_paused_sessions 0
adb shell am broadcast -n "$PKG/com.aa.launcher.debugtools.DebugMediaReceiver" --es cmd play
shot_top i04_music 3
if adb logcat -d -s LiquidIsland:D | grep -q "mode=MUSIC"; then echo "ISLAND_MUSIC: shown"; else echo "ISLAND_MUSIC: not shown"; fi
shot i04_music_full 0
adb shell input tap "$cx" "$cy"
shot i05_popup 2
dump_texts popup
# 一時停止 (ポップアップは開いたまま、島は充電の表示に戻る)
tap_text "Pause"
shot i06_popup_paused 2
dump_texts popup_paused
# 戻るキーで閉じる
adb shell input keyevent KEYCODE_BACK
shot i07_closed 2
# 再生を再開してから、外側のタップで閉じる
adb shell am broadcast -n "$PKG/com.aa.launcher.debugtools.DebugMediaReceiver" --es cmd play
sleep 2
adb shell input tap "$cx" "$cy"
sleep 2
adb shell input tap "$cx" $((cy + 1200))
shot i08_closed_outside 2

# 再生を止め、充電もやめると何も表示しない島に戻る
adb shell am broadcast -n "$PKG/com.aa.launcher.debugtools.DebugMediaReceiver" --es cmd stop
adb shell dumpsys battery reset || true
adb shell dumpsys battery unplug || true
adb shell dumpsys battery set status 3 || true
shot_top i09_idle 3
adb logcat -d -s LiquidIsland:D | tail -20

# 切り欠きの形を変える (中央の縦長のノッチ)。島がその位置に移るか
if [ "$API" -ge 29 ]; then
  adb shell cmd overlay disable com.android.internal.display.cutout.emulation.hole || true
  adb shell cmd overlay enable com.android.internal.display.cutout.emulation.tall || true
  sleep 4
  shot_top i10_tall_notch 2
  adb logcat -d -s LiquidIsland:D | grep placed | tail -2
fi

print_logs
