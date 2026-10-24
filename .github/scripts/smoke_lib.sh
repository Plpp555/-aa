#!/usr/bin/env bash
# スモークテスト用の共通関数。
PKG=com.aa.launcher.debug
OUT=/tmp/smoke
mkdir -p "$OUT"

ADB_BIN=$(command -v adb)
adb() { timeout 60 "$ADB_BIN" "$@"; }

shot() {
  sleep "${2:-2}"
  adb exec-out screencap -p > "$OUT/$1.png"
  convert "$OUT/$1.png" -resize 300x -quality 50 "$OUT/$1.jpg"
  echo "SHOT_BEGIN $1"
  base64 -w 0 "$OUT/$1.jpg"
  echo
  echo "SHOT_END $1"
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

find_text_retry() {
  local xy="" i
  for i in 1 2 3; do
    xy=$(find_text "$1")
    [ -n "$xy" ] && break
    sleep 2
  done
  echo "$xy"
}

tap_text() {
  local xy
  xy=$(find_text_retry "$1")
  echo "tap '$1' -> ${xy:-not found}"
  [ -n "$xy" ] && adb shell input tap $xy
}

long_press_text() {
  local xy
  xy=$(find_text_retry "$1")
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


# 長押ししてからドラッグして離す (input draganddrop は長押し時間だけ待ってから移動する)。
drag_to() {
  local x1=$1 y1=$2 x2=$3 y2=$4 duration=${5:-2500}
  adb shell input draganddrop "$x1" "$y1" "$x2" "$y2" "$duration"
}

# 初回起動の準備: インストール、ホームアプリ設定、通知アクセス、初回案内を閉じる
setup_app() {
  adb install -r -g app/build/outputs/apk/debug/app-debug.apk
  adb shell cmd package set-home-activity "$PKG/com.aa.launcher.MainActivity" || true
  # 標準ランチャーが起動直後に ANR ダイアログを出して操作を妨げるので無効化する
  adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher > /dev/null 2>&1 || true
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
  adb shell cmd notification allow_listener "$PKG/com.aa.launcher.service.BadgeNotificationListener" || true
  adb logcat -c
  adb shell am start -W -n "$PKG/com.aa.launcher.MainActivity"
  sleep 12
  # それでも ANR ダイアログが出ていたら閉じる
  local xy
  xy=$(find_text "Close app")
  if [ -n "$xy" ]; then adb shell input tap $xy; sleep 3; fi
}

print_logs() {
  echo "=== CRASH BUFFER ==="
  adb logcat -d -b crash
  echo "=== APP LOG ==="
  adb logcat -d | grep -E "AndroidRuntime|FATAL EXCEPTION|ANR in|System.err|launcher.debug.*(Exception|Error)" | grep -v -E "AppsFilter|uiautomator|RuntimeInit|boot image|lock profiling|Shutting down VM" | tail -120
  echo "=== END ==="
}
