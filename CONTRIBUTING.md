# 開発への参加

Issue や Pull Request を歓迎します。

## 不具合の報告・要望
[Issues](https://github.com/Plpp555/liquid-OS-Android-launcher/issues) のテンプレートに沿って書いてください。
不具合の場合は、端末名・Android のバージョン・Liquid Launcher のバージョン (設定アプリの一番下) と、再現手順やスクリーンショットがあると助かります。

## Pull Request
1. `main` から作業用のブランチを作る
2. 変更する前に [DEVELOPMENT_NOTES.md](DEVELOPMENT_NOTES.md) の「開発方針」を読む。特に:
   - 描画品質・アニメーション・操作感・既存機能を犠牲にする軽量化はしない
   - Android 6.0 (minSdk 23) 以降で動くこと。新しい API は `Build.VERSION.SDK_INT` で分岐する (CI の lint `NewApi` で検査)
   - UI の文字列は英語 (`values/strings.xml`) と日本語 (`values-ja/strings.xml`) の両方に追加し、`python3 tools/check_strings.py` で確認する
3. Pull Request を作ると、GitHub Actions がビルド・lint と、エミュレータでの動作確認 (スマホ・Android 6.0・タブレット・折りたたみ) を行います
4. 変更点を [CHANGELOG.md](CHANGELOG.md) の先頭 (次のバージョン) に書き足す

## ライセンス
送っていただいたコードは、このリポジトリと同じ [MIT License](LICENSE) で公開されます。
