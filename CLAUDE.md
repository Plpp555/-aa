# CLAUDE.md

このリポジトリは iOS 27 スタイルの Android ランチャー「Liquid Launcher」です。

**作業を始める前に必ず `DEVELOPMENT_NOTES.md` を読んでください。** 開発方針、実装済み/未実装機能、技術仕様、
ビルド・検証手順 (GitHub Actions とエミュレータのスモークテスト)、修正履歴、既知の不具合、今後の予定がまとまっています。

要点:
- ユーザーとのやり取りとコードコメントは日本語。UI 文字列は英語 (`values/`) と日本語 (`values-ja/`) の両方に追加し、`python3 tools/check_strings.py` で確認する。
- 描画品質・アニメーション・操作感・既存機能を犠牲にする軽量化は禁止。静止中・非表示中は処理を止め、操作中は最高リフレッシュレート。
- Android 6.0 (minSdk 23) 以降に対応。minSdk より新しい API は `Build.VERSION.SDK_INT` で分岐する (CI の lint `NewApi` で検査、エミュレータは API 34 と 23)。
- ローカルで AGP ビルドができない環境が多いので、push して GitHub Actions (Android CI / Emulator smoke test) で検証する。
- 公開リポジトリ (MIT)。`main` が公開版で、作業はブランチ → Pull Request。公開版の APK は Release ワークフロー (手動実行でバージョン指定、またはタグ `v*.*.*`) が非公開の鍵で署名して Releases に置く。**公開はユーザーが「公開して良いです」と言ったときだけ** (手順は DEVELOPMENT_NOTES.md 12 章)。
- 変更したら `DEVELOPMENT_NOTES.md` の修正履歴・既知の不具合・今後の予定を更新する。
