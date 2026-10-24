#!/usr/bin/env python3
"""文字列リソースの整合性チェック。

コード (Kotlin / Manifest / res/xml) から参照されている R.string / @string のキーが
values/strings.xml (英語・既定) と values-ja/strings.xml (日本語) の両方に存在するか確認する。

使い方: python3 tools/check_strings.py
"""
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main"
used = set()
for f in (root / "java").rglob("*.kt"):
    used.update(re.findall(r"R\.string\.([A-Za-z0-9_]+)", f.read_text(encoding="utf-8")))
for f in [root / "AndroidManifest.xml", *(root / "res" / "xml").glob("*.xml")]:
    used.update(re.findall(r"@string/([A-Za-z0-9_]+)", f.read_text(encoding="utf-8")))


def keys(path):
    return {e.get("name"): e for e in ET.parse(path).getroot().iter("string")}


en = keys(root / "res" / "values" / "strings.xml")
ja = keys(root / "res" / "values-ja" / "strings.xml")
non_translatable = {k for k, e in en.items() if e.get("translatable") == "false"}

missing_en = sorted(used - en.keys())
missing_ja = sorted(used - ja.keys() - non_translatable)
unused = sorted(en.keys() - used)
print(f"used={len(used)} en={len(en)} ja={len(ja)}")
print("missing in values/:", missing_en or "none")
print("missing in values-ja/:", missing_ja or "none")
print("unused keys:", unused or "none")
sys.exit(1 if missing_en or missing_ja else 0)
