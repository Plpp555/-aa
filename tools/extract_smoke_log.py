#!/usr/bin/env python3
"""エミュレータ・スモークテストのジョブログからスクリーンショットと画面テキストを取り出す。

GitHub MCP の get_job_logs(return_content=true, tail_lines=3000) の結果は大きすぎて
ファイルに保存されるので、そのパスを渡す (生ログのテキストファイルでもよい)。

使い方: python3 tools/extract_smoke_log.py <保存されたログ> <出力ディレクトリ>
  - SHOT_BEGIN/SHOT_END で囲まれた base64 JPEG を <出力ディレクトリ>/<名前>.jpg に保存
  - TEXTS/tap/long press/クラッシュ関連の行を標準出力に表示
画像は montage (ImageMagick) で並べて Read ツールで確認するとよい。
"""
import base64
import json
import os
import re
import sys

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
raw = open(src, encoding="utf-8").read()
try:
    content = json.loads(raw)["logs_content"]
except Exception:
    content = raw
name = None
for line in content.split("\n"):
    body = re.sub(r"^\S+Z ", "", line)
    if body.startswith("SHOT_BEGIN"):
        name = body.split()[1]
        continue
    if body.startswith("SHOT_END"):
        name = None
        continue
    if name and len(body) > 100:
        open(os.path.join(out, name + ".jpg"), "wb").write(base64.b64decode(body.strip()))
        print("saved", name)
    elif body.startswith(("TEXTS", "tap ", "long press", "=== ")) or "FATAL" in body or "AndroidRuntime" in body:
        print(body[:1500])
