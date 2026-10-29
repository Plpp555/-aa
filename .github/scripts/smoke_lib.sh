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
  convert "$OUT/$1.png" -resize "${SHOT_WIDTH:-300}x" -quality 50 "$OUT/$1.jpg"
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

# TARGET (テキストか content-desc) のうち、ANCHOR に一番近いものの中心座標 (例: フォルダ内の YouTube の「−」)
find_text_near() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1
  python3 - "$1" "$2" "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
target, anchor, path = sys.argv[1], sys.argv[2], sys.argv[3]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2
nodes = list(root.iter("node"))
anchors = [center(n) for n in nodes if anchor in (n.get("text"), n.get("content-desc"))]
targets = [center(n) for n in nodes if target in (n.get("text"), n.get("content-desc"))]
if anchors and targets:
    ax, ay = anchors[0]
    x, y = min(targets, key=lambda p: (p[0] - ax) ** 2 + (p[1] - ay) ** 2)
    print(x, y)
PY
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


# 長押ししてからドラッグして離す。押したまま 0.9 秒待って (メニューが出る) から 10 段階で移動し、
# 目的地で 1.2 秒止まってから離す (フォルダ化は 420ms の滞在が必要)。
# input draganddrop は長押し時間 (400ms) ちょうどで動き始め、アプリの長押し判定と競合して結果が揺れるので使わない。
drag_to() {
  local x1=$1 y1=$2 x2=$3 y2=$4 i
  adb shell input motionevent DOWN "$x1" "$y1"
  sleep 0.9
  for i in 1 2 3 4 5 6 7 8 9 10; do
    adb shell input motionevent MOVE "$((x1 + (x2 - x1) * i / 10))" "$((y1 + (y2 - y1) * i / 10))"
  done
  sleep 1.2
  adb shell input motionevent UP "$x2" "$y2"
}

# 人の操作と同じ間合いの長押し → ドラッグ: 押したまま待ち (メニューが出る)、少し後で指を動かす。
# 指を離す前に画面を記録し、編集モード ("Done") になっていればドラッグに移行できている。最後は元の位置で離す。
# (input draganddrop は長押し時間ちょうどで動き始めるので、メニュー表示後に動かす操作は確認できない)
hold_then_drag() {
  local x=$1 y=$2 name=$3
  adb shell input motionevent DOWN "$x" "$y"
  sleep 1.2
  adb shell input motionevent MOVE "$((x + 8))" "$((y + 4))"
  sleep 0.3
  adb shell input motionevent MOVE "$((x + 30))" "$((y + 25))"
  adb shell input motionevent MOVE "$((x + 45))" "$((y + 40))"
  sleep 0.8
  shot "$name" 0
  dump_texts "$name"
  if grep -q 'text="Done"' "$OUT/ui.xml"; then echo "HOLD_DRAG $name: drag started"; else echo "HOLD_DRAG $name: no drag"; fi
  adb shell input motionevent MOVE "$x" "$y"
  sleep 0.6
  adb shell input motionevent UP "$x" "$y"
}

# 写真壁紙の編集画面: 横長のテスト画像 (LEFT / CENTER / RIGHT) を選んだ写真として置いて編集画面を開き、
# 右へドラッグして (写真の左側が見えるように) 位置を変え、「Set」で反映してホーム画面を撮る。
# 編集画面は debug ビルドだけ外部から起動できる (app/src/debug/AndroidManifest.xml)。
crop_photo_test() {
  local name=$1 sw sh
  convert -size 3000x2000 gradient:'#ff5f6d'-'#2193b0' -fill white -pointsize 240 \
    -gravity west -annotate +120+0 'LEFT' -gravity east -annotate +120+0 'RIGHT' -gravity center -annotate +0+0 'CENTER' \
    "$OUT/photo.jpg"
  adb push "$OUT/photo.jpg" /data/local/tmp/photo.jpg > /dev/null
  adb shell chmod 644 /data/local/tmp/photo.jpg
  adb shell "run-as $PKG sh -c 'mkdir -p files && cat /data/local/tmp/photo.jpg > files/wallpaper_pending.jpg'"
  adb shell am start -W -n "$PKG/com.aa.launcher.settings.WallpaperCropActivity" --ez pending true
  shot "${name}_editor" 3
  dump_texts "${name}_editor"
  adb exec-out screencap -p > "$OUT/crop_size.png"
  read -r sw sh < <(identify -format '%w %h' "$OUT/crop_size.png")
  adb shell input swipe $((sw / 3)) $((sh / 2)) $((sw * 2 / 3)) $((sh / 2)) 600
  shot "${name}_editor_moved" 2
  tap_text "Set"
  sleep 2
  adb shell input keyevent KEYCODE_HOME
  shot "${name}_home" 4
}

# 初回起動の準備: インストール、ホームアプリ設定、通知アクセス、初回案内を閉じる
setup_app() {
  adb install -r -g app/build/outputs/apk/debug/app-debug.apk
  adb shell cmd package set-home-activity "$PKG/com.aa.launcher.MainActivity" || true
  # 標準ランチャーが起動直後に ANR ダイアログを出して操作を妨げるので無効化する
  adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher > /dev/null 2>&1 || true
  # Android 6.0 のイメージは Launcher3 (cmd package set-home-activity がないので、無効化してホームの選択画面を出さない)
  adb shell pm disable-user --user 0 com.android.launcher3 > /dev/null 2>&1 || true
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
