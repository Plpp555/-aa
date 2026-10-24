#!/usr/bin/env bash
# スモークテスト用の共通関数。
PKG=com.aa.launcher.debug
OUT=/tmp/smoke
mkdir -p "$OUT"

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


# 指を押したまま移動して離す (ドラッグ & ドロップ)。最後に dwell 秒だけ止まる。
drag_to() {
  local x1=$1 y1=$2 x2=$3 y2=$4 hold=${5:-0.3} dwell=${6:-1.2} shotname=${7:-}
  adb shell input motionevent DOWN "$x1" "$y1"
  sleep "$hold"
  for i in 1 2 3 4 5 6 7 8; do
    adb shell input motionevent MOVE $((x1 + (x2 - x1) * i / 8)) $((y1 + (y2 - y1) * i / 8))
  done
  sleep "$dwell"
  [ -n "$shotname" ] && shot "$shotname" 0
  adb shell input motionevent UP "$x2" "$y2"
}
