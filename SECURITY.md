# セキュリティ

## 公式の APK
公式の APK は [Releases](https://github.com/Plpp555/liquid-OS-Android-launcher/releases) にあるものだけです。すべて同じ非公開の鍵で署名しており、署名証明書の SHA-256 は次のとおりです。

```
F5:CA:2C:CB:24:27:10:2C:9E:9D:E5:23:D3:8B:18:A5:E7:51:9D:84:49:AD:5D:A7:20:60:86:F8:A5:75:C6:FE
```

リポジトリ内の `app/signing/` の鍵は開発用 (テスト版の debug ビルド用) で、公開されています。この鍵で署名された `com.aa.launcher` の APK は公式のものではありません。

## 脆弱性の報告
脆弱性を見つけた場合は、公開の Issue ではなく、リポジトリの **Security → Report a vulnerability** (非公開の報告) からお知らせください。
