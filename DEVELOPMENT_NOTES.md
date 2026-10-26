# DEVELOPMENT_NOTES — Liquid Launcher 開発記録

新しい会話の Claude (または開発者) が、このファイルとソースコードだけで現状を把握して作業を引き継げるようにまとめた開発記録です。
**コードを変更したら、このファイルの該当箇所 (特に「修正履歴」「既知の不具合」「今後の予定」) も必ず更新してください。**

最終更新: 2026-10-24 (パフォーマンス最適化とクリアアイコン修正を含む版)

---

## 1. プロジェクト概要

- **目的**: iOS 27 のホーム画面 (Liquid Glass デザイン) を基準にした Android 用ホームアプリ (ランチャー)。
- **ユーザーの要望 (原文の要点)**:
  1. 「iOS 27 を基準とした Android 向けランチャーを作成。設定等も全て盛り込む」
  2. 「描画品質・使用感を維持したまま」、Liquid Glass のクリアを透明かつ高解像度に、静止時/非表示時の描画停止、
     操作中は端末の最高リフレッシュレート、クリアアイコン消失の修正、CPU/GPU/メモリ/電池の最適化。
     **品質・アニメーション・操作感・既存機能を犠牲にする軽量化は禁止。**
- **アプリ名**: Liquid Launcher (設定アプリ側の表示名: Launcher Settings / ランチャー設定)
- **パッケージ**: `com.aa.launcher` (debug ビルドは `com.aa.launcher.debug`)
- **リポジトリ**: `Plpp555/-aa`、作業ブランチ `claude/android-launcher-ios27-91mgc6`、PR #1 (オープン中)
- **言語**: UI は英語 (既定) と日本語。コードのコメントは日本語。ユーザーとのやり取りも日本語。

## 2. 開発方針 (必ず守ること)

1. **品質を下げる軽量化はしない**。描画品質・アニメーション・操作感・既存機能はそのまま。最適化は「同じ見た目で無駄を省く」方向だけ。
2. **静止中は何も計算・再描画しない**。動いているもの (スクロール・アニメーション・時計の秒表示など) だけが処理を使う。
3. **見えていない画面は止める** (非表示ページ・他のアプリ使用中)。
4. **操作中・アニメーション中は端末の最高リフレッシュレート**。60fps などの固定上限は設けない。
5. 既定値の見た目は変えない (例: Liquid Glass の既定 0.3 は従来と同じ見た目になるようにカーブを合わせてある)。
6. 変更したら **CI でビルド** → **エミュレータのスモークテストでスクリーンショット確認**。クラッシュがないことを確認してから完了とする。
7. 文字列は必ず英語 (`values/strings.xml`) と日本語 (`values-ja/strings.xml`) の両方に追加し、`python3 tools/check_strings.py` で確認。
8. **Android 6.0 (API 23) 以降で動くこと**。新しい API は `SDK_INT` で分岐し、古い端末では同等の代替か機能を出さない (6.10 参照)。描画方式・最適化は全バージョン共通。
9. コミットメッセージは英語。末尾に Co-Authored-By / Claude-Session の行 (システムの指示に従う)。モデル名をリポジトリ内に書かない。

## 3. 作業の始め方 (新しいセッション向け)

### 3.1 ビルド環境の制約
- このクラウド環境では `dl.google.com` / `maven.google.com` (Google Maven) にアクセスできないことがある。
  その場合 **ローカルでは AGP ビルド不可**。ビルドはすべて GitHub Actions で行う。
  (ユーザーが環境のネットワーク許可に `dl.google.com` と `maven.google.com` を追加すればローカルでも可能)
- Maven Central と GitHub (github.com / api.github.com) には接続できる。
- ローカルで構文だけ確認したい場合: GitHub Releases から `kotlin-compiler-2.2.21.zip` を取得し、
  `kotlinc $(find app/src/main/java -name '*.kt')` を実行して `syntax|expecting|unexpected` を grep する
  (クラスパスがないので未解決参照エラーは無視する)。

### 3.2 CI (GitHub Actions)
| ワークフロー | トリガー | 内容 |
| --- | --- | --- |
| `.github/workflows/android.yml` (Android CI) | すべての push / PR / 手動 | `assembleDebug assembleRelease`、APK を Artifact `liquid-launcher-apk` にアップロード、その後 `lintDebug` (NewApi のみ) |
| `.github/workflows/emulator-smoke.yml` (Emulator smoke test) | PR / 手動 | API 34 エミュレータ (pixel_6, 1080x2400) で `smoke.sh` と `smoke2.sh`、API 23 (Android 6.0) で `smoke_legacy.sh` を別ジョブで実行 |

- 実行状況は `curl -s https://api.github.com/repos/Plpp555/-aa/actions/runs?branch=<ブランチ>` で取得できる (公開リポジトリなので認証不要)。
- 待機は `until ...; do sleep 30; done` を `run_in_background` で実行するのが良い (フォアグラウンドの sleep は不可)。
- ジョブログは GitHub MCP の `get_job_logs(job_id, return_content=true, tail_lines=3000)` で取得。
  大きいログは自動的にファイルへ保存されるので、`python3 tools/extract_smoke_log.py <ファイル> <出力dir>` で
  スクリーンショット (JPEG) と画面テキストを取り出し、`montage a.jpg b.jpg -tile 4x1 -geometry +4+4 sheet.jpg` で並べて Read ツールで見る。
- Artifact の直接ダウンロードや注釈 (annotation) 経由の画像取得はできない (前者はプロキシで遮断、後者は 4KB で切り詰め)。

### 3.3 スモークテスト (`.github/scripts/`)
- `smoke_lib.sh`: 共通関数。`shot` (スクショを base64 でログに出力)、`find_text`/`tap_text`/`long_press_text` (uiautomator のダンプで文字列を探してタップ)、
  `drag_to` (`input motionevent` で 0.9 秒押す → 10 段階で移動 → 目的地で 1.2 秒止まって離す)、
  `hold_then_drag` (`input motionevent` で 1.2 秒押したまま → 動かす → 離す前に "Done" の有無で `HOLD_DRAG ...: drag started / no drag` を出力)、`setup_app` (インストール、ホーム設定、Pixel Launcher 無効化、初回起動)、`print_logs`。
- `smoke.sh`: 初回案内 → ホーム → 今日の表示 → App ライブラリ → Spotlight → 長押しメニュー → 編集モード → コントロールセンター → 設定 → 色合いアイコン。
- `smoke_wide.sh` (API 34、Pixel Tablet の 2 ジョブ。うち 1 つは `FOLD_SIM=1` で `wm size 2208x1840`・`wm density 420` にして Pixel Fold の内側の画面を再現。CI の SDK には pixel_fold の定義がない): ホーム → 今日の表示 → App ライブラリ → コントロールセンター → Spotlight →
  ドラッグ (フォルダ作成) → 長押しメニュー → クリアアイコン。タブレットは 1 ページ、Fold (開いた画面) は 2 ページ表示になる。
  座標は画面サイズの割合と画面密度から決める。スクショは幅 640。
- `smoke_legacy.sh` (API 23): ホーム → 今日の表示 → App ライブラリ → Spotlight → 長押しメニュー → 編集モード → コントロールセンター →
  アイコンのスタイル (クリア/色合い/ダーク) → 壁紙設定。API 23 にない `input motionevent` / `cmd` は使わない (Launcher3 を無効化してホームにする)。
- `smoke2.sh`: 揺れオフ → ドラッグでフォルダ作成 → 並べ替え → 長押しメニュー後のドラッグ (Photos) → ウィジェット追加 → カスタマイズ (クリア) → ダーク → 壁紙 → フォルダ → 日本語。
- 注意:
  - 以前は「`input motionevent` はイベントごとに downTime が変わるため長押し→ドラッグに使えない」としていたが、
    dd32fda 以降は `input motionevent` でも長押し→ドラッグが成功している。以前うまくいかなかったのは 6.4 の不具合
    (メニュー表示で祖先の pointerInput が外れる) が原因だった可能性が高い。
  - 編集モードの揺れ (無限アニメーション) 中は uiautomator のダンプが待たされるので、smoke2 では最初に揺れをオフにしている。
  - エミュレータ標準の Pixel Launcher が ANR ダイアログを出すため `pm disable-user` で無効化している (このとき SystemUI のクラッシュがログに出るが本アプリとは無関係)。
  - 設定画面をルート指定で開くときは `--activity-clear-task` を付ける。

## 4. 技術スタック・バージョン

- AGP 8.13.0 / Gradle 8.14.3 (wrapper) / Kotlin 2.2.21 (Compose Compiler プラグイン・Serialization プラグイン)
- Compose BOM 2025.08.00 (Compose 1.9 系)、Material3、material-icons-extended 1.7.8
- androidx.core-ktx 1.16.0、activity-compose 1.10.1、lifecycle 2.9.2、datastore 1.1.7、kotlinx-serialization-json 1.9.0
- compileSdk 36 / targetSdk 36 / **minSdk 23 (Android 6.0)**、JDK 17。androidx.exifinterface 1.3.7 (Android 8.1 以前の写真の向き)
- lint は `NewApi` だけを検査し、エラーでビルド失敗 (`lint { checkOnly += "NewApi" }`)。minSdk より新しい API は必ず `Build.VERSION.SDK_INT` で分岐するか `@RequiresApi` を付ける
- release ビルドは R8 (minify + shrinkResources)。
- **署名**: debug / release とも `app/signing/liquid-launcher.p12` (パスワード `liquidlauncher`、別名 `liquid`) で署名する (`signingConfigs.shared`)。
  CI の既定の debug 鍵は実行ごとに作り直されるので、以前は署名が毎回変わり、上書き更新できなかった (「パッケージが競合」)。
  鍵は公開リポジトリにあるため誰でも同じ署名を作れる。ストア配布時は非公開の鍵に置き換えること。
- **versionCode** = `GITHUB_RUN_NUMBER` (ローカルビルドは 1)、versionName = `1.0.<実行番号>`。CI のビルドは常に新しい版として更新できる
- `app/src/main/baseline-prof.txt`: 自アプリのコードをインストール時に事前コンパイル (profileinstaller は Compose の依存で入る)

## 5. ディレクトリ構成とファイルの役割

```
app/src/main/java/com/aa/launcher/
├── LauncherApp.kt            Application。各リポジトリの生成、アプリ一覧→レイアウト同期、壁紙更新、アイコンキャッシュ破棄、onTrimMemory
├── MainActivity.kt           HOME アクティビティ。Compose ルート、WidgetHostManager、FrameRateController、ウィンドウ設定 (壁紙/ステータスバー)
├── core/
│   ├── AppModels.kt          ComponentKey (アプリ ID)、AppEntry、AppCategory と自動分類、LauncherJson
│   ├── AppRepository.kt      LauncherApps でアプリ一覧 (仕事用プロファイル含む)、起動、App 情報、削除、ショートカット、packageChanges/shortcutChanges
│   ├── LayoutModels.kt       HomeItem (AppItem/FolderItem/WidgetItem)、HomePage、HomeLayout、WidgetKind/Size、GridPacker
│   ├── LayoutOps.kt          レイアウト変更の純粋関数 (移動・フォルダ化・削除・normalize)
│   ├── LayoutRepository.kt   レイアウトの保存 (AtomicFile, home_layout.json)、初期レイアウト、インストール/削除の同期、DefaultApps
│   ├── IconShapes.kt         スーパー楕円などの形状 (android.graphics.Path / Compose Shape)
│   ├── IconRenderer.kt       アイコン描画 (スタイル別・グリフ抽出)
│   ├── IconRepository.kt     描画済みアイコンの LruCache (HARDWARE ビットマップ)、rememberAppIcon、LocalIconVersion
│   ├── IconPackManager.kt    ADW/Nova 互換アイコンパック (appfilter.xml)
│   ├── Wallpapers.kt         BlurUtils、内蔵壁紙 8 種 (コード生成)、WallpaperRepository、Backdrop (ガラス用背景)
│   ├── FrameRateController.kt 操作中だけ最高リフレッシュレートを要求
│   ├── MediaRepository.kt    再生中メディア (通知リスナー経由の MediaSession)
│   ├── SystemActions.kt      システム画面/パネル、通知センター展開、画面ロック、既定ホーム、Web 検索、Flashlight、DoNotDisturb、SystemSettingsAccess、AppLock (生体認証)
│   ├── UsageTracker.kt       起動回数 (Siri からの提案)、最近の検索 (SharedPreferences "usage")
│   ├── SearchText.kt         検索用正規化 (NFKC・かな同一視)、一致スコア、App ライブラリの見出し
│   ├── Calculator.kt         Spotlight の計算機
│   ├── BackupManager.kt      設定+レイアウトの JSON 書き出し/読み込み
│   └── WidgetHostManager.kt  AppWidgetHost、バインド/設定フロー、RoundedWidgetHostView (角丸・長押し検出)
├── service/
│   ├── BadgeNotificationListener.kt  通知数→バッジ (NotificationState)、メディア連携のきっかけ
│   └── LauncherAccessibilityService.kt 画面ロック・通知・履歴をジェスチャーから実行
├── settings/
│   ├── LauncherSettings.kt   全設定のデータクラスと enum (ThemeMode, IconStyle, GestureAction, ControlTile ほか)
│   ├── SettingsRepository.kt DataStore (launcher_settings.json)。メモリ上の StateFlow を即時更新し、保存は 300ms デバウンス
│   ├── SettingsActivity.kt   設定アプリ。Route (画面) と SettingsNav、スタック遷移、認証、FrameRateController
│   └── ui/SettingsComponents.kt / SettingsScreens.kt  iOS 風の行・グループ・スイッチ等と全設定画面
└── ui/
    ├── theme/Theme.kt        LauncherTheme、LocalLauncherSettings、LocalIsDark、IosColors、アニメ速度ヘルパー
    ├── glass/Glass.kt        Liquid Glass (Modifier.liquidGlass / Node 実装)、GlassParams、glassFill、blurCompat
    ├── common/Common.kt      AppIconImage、rememberIconSpec、バッジ、MinusBadge、ActionSheet、GlassSearchField 等
    ├── common/Controls.kt    IosSwitch、IosSlider、IosSegmented、HueBrush
    ├── home/                 ホーム画面
    │   ├── LauncherScreen.kt ルート (壁紙・ページャー・ドック・オーバーレイ・ドラッグ追跡・ページ効果)
    │   ├── LauncherViewModel.kt UI 状態、起動、編集、ドラッグ&ドロップの当たり判定、ジェスチャー動作
    │   ├── HomeState.kt      PageModel、OpenFolder、MenuTarget、DragState、DropTarget 等
    │   ├── HomeGrid.kt       HomeGridPage、Dock、PageIndicator、背景/縦スワイプのジェスチャー
    │   ├── HomeItems.kt      ItemCell (操作可能セル)、ItemVisual、FolderIcon、揺れ
    │   ├── ItemGestures.kt   launcherItemGestures (タップ/長押し/ドラッグ開始)
    │   ├── FolderOverlay.kt / ContextMenu.kt / EditOverlays.kt (カスタマイズ・ページ編集)
    │   └── LauncherLocals.kt LocalLauncherActions、LocalWidgetHost、LocalContentActive
    ├── library/AppLibrary.kt App ライブラリ (カテゴリ・一覧・索引)
    ├── today/                今日の表示、内蔵ウィジェット、ウィジェットギャラリー、メモ編集
    ├── spotlight/Spotlight.kt 検索 (App・設定・連絡先・計算機・Web・Play ストア)
    ├── controlcenter/ControlCenter.kt コントロールセンター
    └── onboarding/Onboarding.kt 初回案内
app/src/main/res/  strings (values, values-ja)、themes、アダプティブアイコン (ic_launcher, ic_settings)、accessibility_service.xml
tools/             check_strings.py (文字列整合性)、extract_smoke_log.py (スモークテストのログ展開)
docs/screenshots.jpg  README 用スクリーンショット
```

## 6. 重要な技術仕様

### 6.1 識別子とデータ保存
- **アプリ ID**: `"<package>/<activityClass>#<userSerial>"` (`ComponentKey.toString()` / `parse()`)。仕事用プロファイルは serial で区別。
- **バッジのキー**: `"<package>#<userSerial>"` (`ComponentKey.packageKey`)。
- **設定**: `files/datastore/launcher_settings.json` (DataStore + kotlinx.serialization)。項目追加時は必ずデフォルト値を付ける (古い JSON 互換)。
  `ignoreUnknownKeys / coerceInputValues` で未知の値は既定値になる。
- **レイアウト**: `files/home_layout.json` (AtomicFile、250ms デバウンス保存)。
- **使用履歴・最近の検索**: SharedPreferences `usage`。
- **写真壁紙**: `files/wallpaper_custom.jpg` (画面サイズに切り抜いて保存)。

### 6.2 レイアウトモデル (core/LayoutModels.kt, LayoutOps.kt)
- `HomeLayout(pages, dock, today, knownApps)`。ページは `HomePage(id, items, hidden)`。
- **配置は iOS と同じ「左上から詰める」方式** (`GridPacker.pack`)。アイテムの順序だけを保存し、座標は保存しない。
  ウィジェットは spanX×spanY (SMALL 2x2 / MEDIUM 4x2 / LARGE 4x4、列数で頭打ち)、偶数列の場合 2 列幅は偶数列にそろえる。
- `LayoutOps.normalize` (更新のたびに実行): 空フォルダ削除、ドック容量超過分をページへ、はみ出しを次ページ先頭へ、空ページ削除 (最低 1 ページ)。
- `knownApps`: 一度見たアプリ。「ホーム画面から取り除く」したアプリが再追加されないため。新規アプリは設定「新しい App をホームに追加」に従う。
- インストール一覧が空 (読み込み前) のときは同期しない (全消去事故防止)。

### 6.3 ドラッグ & ドロップ (LauncherViewModel / LauncherScreen)
- アイテムの `launcherItemGestures` が長押し/移動を検出して `vm.startDrag` を呼び、以降の追跡は **ルートの `dragTracking` (PointerEventPass.Initial)** が行う
  (アイテムが再配置・削除されてもドラッグが途切れない)。
- 開始時に **元の位置を初期ターゲットにする** (`initialTarget`) — これがないと隣のアイコンが指の下に詰めてきてフォルダ化できない (修正済み)。
- プレビュー: `previewPageItems/previewDock/previewFolder` がドラッグ中アイテム (ゴースト, alpha 0) をターゲット位置に差し込む。
  当たり判定はプレビュー上の配置で行う。フォルダ化候補中もプレビューは固定 (ターゲットを動かさない)。
- タイミング: 並べ替えターゲット確定 110ms、フォルダ化 (アイコン中心 ±26% に滞在) 420ms、画面端でのページ移動 600ms 後、以後 900ms ごと。
- ドラッグ中はページャーの末尾 (App ライブラリの手前) に空ページ `EXTRA_PAGE_ID` を追加。そこへ落とすと新規ページ。
- フォルダからのドラッグ: フォルダ枠内は並べ替え、枠外へ出るとフォルダを閉じてホームへ。
- フォルダを作った・フォルダに入れたときは **開かない** (実機フィードバックにより変更。以前は iOS と同じく開いていた)。名前は対象アプリのカテゴリ名。

### 6.4 ジェスチャー
- `launcherItemGestures`: タップ / 長押し (非編集 420ms・編集 220ms) / 移動。長押し後のイベントは Initial パスで消費し、子要素 (ウィジェット内ボタン) のクリックを防ぐ。
- 背景ジェスチャー (`homeBackgroundGestures`): `vm.lastItemDownUptime` でアイテム上の押下を除外。長押し=編集、タップ=編集終了、ダブルタップ=設定の動作。
- 縦スワイプ (`homeVerticalSwipe`): 下 / 右上から下 (y<18% かつ x>60%) / 上。ホームページ表示中・オーバーレイなし・ドラッグ中でないときだけ有効。
  **修飾子は常に付けたまま**、有効かどうかは指を置いた時点で `enabled()` を評価する (無効ならイベントを消費せず見送る)。
- **重要な規則: アイコンの祖先に付く `pointerInput` を状態によって付け外ししないこと。**
  Compose は祖先の pointerInput ノードが外れると、その下の子孫の進行中ジェスチャーもキャンセルする (HitPathTracker)。
  以前は `homeVerticalSwipe` を `if (!enabled) this else pointerInput(...)` で付け外ししていたため、
  長押しメニューが出る (= `hasOverlay` が true になり修飾子が外れる) とアイコンのジェスチャーが取り消され、
  メニュー表示後に指を動かしてもドラッグに移行しなかった (メニューが出たのと同じフレームで指が動いた場合だけ移行できた)。
  ドラッグ開始後のページャーの `userScrollEnabled = false` も同じ理由で子孫を取り消すが、その時点で追跡はルートの `dragTracking` (祖先) に移っているので問題ない。
- AppWidget は AndroidView がタッチを消費するため、`RoundedWidgetHostView.dispatchTouchEvent` で長押しを検出してメニューを出す。
  編集モードでは上に透明 Box を重ねてドラッグできるようにしている。

### 6.4.1 大画面 (折りたたみを開いた画面・タブレット)
- **折りたたみを開いた画面だけ 2 ページ表示、タブレットは iPadOS と同じく 1 ページ** (ユーザーの指定)。
- 判定 `isTwoPane()`: ウィンドウ (Configuration) の幅 600dp 以上かつ高さ 480dp 以上で、
  「折りたたみ端末 (`DeviceInfo.isFoldable` = ヒンジ角度センサーあり、Android 11+)」または「縦横比 1.3 以下 (開いた内側の画面はほぼ正方形)」。
  タブレット (16:10 など) は対象外。MainActivity は configChanges で再生成されないので、開閉するとその場で切り替わる。
- 見開き表示 (ユーザーの指定): **今日の表示 → 1+2 ページ → 3+4 ページ → … → App ライブラリ**。ページャーの 1 画面 = `Spread`。
  `buildSpreads()` がホームのページを 2 つずつ `Spread.Two(left, right)` にまとめ、今日の表示と App ライブラリは `Spread.Single` (見開き全体に 1 つ)。
  ホームのページ数が奇数なら最後の見開きの右は空のページ (`right = null`、背景の長押しで編集モード)。ドラッグ中は新規ページ (EXTRA) がその位置に入り、置くと新しいページになる。
  ページャーは全幅のページ送り (`PageSize.Fill`)。各ページは半分の幅でグリッド (列数は設定どおり) を計算するので、アイコンやウィジェットは閉じた画面と同じ大きさ。
  配置データは共通 (開閉しても並びが変わらない)。開閉で見開きの構成が変わったら、見ていたページ (`ShownPage`) を含む画面へ移る。ページ効果は見開きではスライドのみ。
  - ドラッグ: `DragState.currentPageId` (左) と `secondPageId` (右) のうち、指に近いほうのグリッドをドロップ先にする。画面端では見開き単位で送る。
  - ドック/インジケータ: 1 ページ分の幅で中央 (左右のページの間)。今日の表示・App ライブラリではフェード。ドットは見開きごと。
  - 以前の「半分幅のページを 1 ページずつ送る」方式 (8a6364f〜d4f79d1) は、右スワイプで今日の表示へ戻れない不具合があった (エミュレータで 2 回とも失敗)。
- タブレット (1 ページ): ホームのグリッドとドックは従来どおり画面幅で計算。大きさを直すのは次の 2 ページだけ:
  - 今日の表示: 最大幅 `MaxPanelWidth` (560dp) で中央。
  - App ライブラリ: `LibraryColumns` で列数を幅に合わせる (フォルダは約 190dp 以下、スマホは 2 列のまま)。検索欄と一覧は最大 640dp。
- その他の最大幅 (スマホの幅では効かない): カスタマイズ・ウィジェット追加・メモ = 560dp、Spotlight 640dp、ページを編集 720dp、
  ActionSheet 520dp、フォルダ 380dp。コントロールセンターは 440dp で右上に寄せる (iPadOS と同じ)。

### 6.5 Liquid Glass (ui/glass/Glass.kt)
- `@Composable fun Modifier.liquidGlass(shape, tint, strength, rim)` → `graphicsLayer()` + `LiquidGlassElement` + `graphicsLayer()`。
  - **ガラスと中身を別レイヤー**にしており、中身 (時計の文字等) の更新でガラスを描き直さず、スクロール中のガラスの位置更新で中身を描き直さない。
  - `LiquidGlassNode` (DrawModifierNode + GlobalPositionAwareModifierNode): 位置 (`positionInRoot`) が変わったときだけ `invalidateDraw`。
    パス・ブラシはサイズ/パラメータが変わるまでキャッシュ。**静止中は何も計算しない。**
- 描画内容: 背景 (壁紙を画面座標に合わせて写す) → フィル → 上部ハイライト → 内側の光 (厚み) → 縁 (リム)。
- 背景は `Backdrop(light, heavy)` (core/Wallpapers.kt):
  - `light`: 画面の 1/2 解像度・ボックスブラー半径 3 (×3 回)。クリア寄りでも粗く (ガビガビに) ならない。
  - `heavy`: 画面の 1/4 解像度・半径 15。従来 (1/6・半径 10) と同等の効きでより滑らか。
  - どちらも HARDWARE ビットマップ。`heavyMix = (opacity / 0.25)` で連続的に混ぜる (既定 0.3 以上は heavy のみ = 従来どおり)。
- フィルの不透明度: `lerp(minA, maxA, opacity^0.745) * strength` (minA: ライト 0.04 / ダーク 0.06、maxA: 0.82 / 0.78)。
  **既定 0.3 で従来値 (0.358) と一致**し、0 ではほぼ透明 (背景がはっきり見える)。
- `strength >= 1.4` は文字を載せるパネル (メニュー・シート・アラート等) とみなし、常に heavy 背景 + 不透明度の下限 (ライト 0.40 / ダーク 0.45) で可読性を保つ。
- 「透明度を下げる」: 背景なしの不透明色。「コントラストを上げる」: 太く濃い縁。
- システム壁紙が読めない (Android 13+ で多い) ときは `backdrop = null` で、半透明フィルのみ (位置更新も不要)。

### 6.6 壁紙 (core/Wallpapers.kt)
- ソース: BUILTIN (コード生成のグラデーション 8 種、画面の 1/2 解像度で生成) / CUSTOM (写真、画面サイズ) / SYSTEM (ウィンドウ越しに表示)。
- `WallpaperState(image, backdrop, averageColor, isDark)`。image が null ならシステム壁紙を表示 (`FLAG_SHOW_WALLPAPER`)。
  image がある場合はウィンドウ背景を null にして重ね塗りを省く (MainActivity.applyWindow)。
- 「ホーム画面で壁紙をぼかす」は `backdrop.heavy` を全面に描く。ダークモードでの減光は黒 22% を重ねる。

### 6.7 アイコン (core/IconRenderer.kt, IconRepository.kt)
- スタイル: AUTOMATIC (ライト=DEFAULT/ダーク=DARK) / LIGHT(=DEFAULT) / DARK / CLEAR (ライト/ダーク/自動) / TINTED。`rememberIconSpec()` で解決。
- 形状: SQUIRCLE (スーパー楕円 n=5) / ROUNDED_SQUARE / CIRCLE / TEARDROP / SQUARE / SYSTEM (マスクなし)。
- アダプティブアイコンはレイヤー 108dp の中央 72dp を表示 (bounds を size/4 外側へ広げて描く)。
- TINTED/CLEAR (と DARK の一部) は「アルファに形と陰影を持つ単色マスク」を作って塗る (`mask`):
  1. モノクロレイヤー (Android 13+) があり、空でなければそれを使う
  2. 前景レイヤーの覆い率が 2〜75% なら `layerMask(前景)`: **輝度を白の濃さにする** (絵柄の陰影・線が残る。iOS のクリアと同じ見え方)。
     - 全体に暗い (平均輝度 < 0.45) と、98 パーセンタイル輝度で最大 2 倍に持ち上げ、暗部に最大 0.3 の下限を付ける
     - 暗い記号 (平均輝度 < 0.25 かつ中間調 < 25%) は反転して記号を白くする (絵柄は反転しない)
  3. 前景が空 (絵柄が背景レイヤー側) か全面なら、合成した全体で `opaqueMask`: 縁の中央値を背景色として色差を求め、
     「背景色の画素 ≥ 30% かつ中間の色差 ≤ 20%」(単色の背景に記号) なら色差で記号を抜き出す。それ以外 (写真・絵柄) は輝度で描く (反転なし)
  4. レガシーアイコンは全面なら `opaqueMask`、透明背景なら `layerMask`
  5. どれもほぼ空なら形を 55% で使う (`ensureVisible`、**絶対に消えない**)
  - DARK は前景が暗い/空ならマスクを明るい色で描く。白い記号は背景色で着色。それ以外は前景をそのまま。
  - **前景レイヤーの「形」を単色で塗りつぶさないこと**。絵柄のアイコン (ゲームなど) が白い塊になって見分けられなくなる
    (75c4880〜dd32fda の不具合。ユーザー報告: エンドフィールドのアイコン)。
- クリアスタイルではアイコンの下にガラス (`liquidGlass`) を敷く (AppIconImage)。
- キャッシュ: LruCache (最大 maxMemory/8、8〜64MB)。キー `"<appId>|style|shape|tint|pack|sizePx"`。描画後 **HARDWARE ビットマップ**へ変換。
  - パッケージ更新時は `invalidatePackage` でそのパッケージだけ破棄、ショートカット変更はショートカットアイコンだけ破棄、アイコンパック変更時は全破棄。
  - `LocalIconVersion` を画面ルートで 1 回だけ購読して配り、`rememberAppIcon` は読み込み完了まで前の画像を表示 (ちらつかない)。
  - `onTrimMemory` で MODERATE 以上は半分、COMPLETE で全破棄。

### 6.8 パフォーマンス設計 (今回の最適化の要点)
- **静止中**: Liquid Glass は位置が変わらない限り再描画しない。Compose はアニメーションがなければフレームを要求しない。
- **ページャー**: 各ページを独立レイヤー (`pageEffect` の SLIDE でも `graphicsLayer()`) にして、スクロール中はページの再記録なしに移動だけ。
  `beyondViewportPageCount = 1` (スワイプの滑らかさのため隣のページは構成しておく)。
- **見えないページ**: `LocalContentActive` (ページ位置との差 < 1) が false のページでは、時計 (`rememberNow`) とバッテリー受信を止める。
- **他のアプリ使用中**: Compose のフレームクロックは ON_STOP で停止。加えて `rememberNow` は `repeatOnLifecycle(STARTED)`、
  バッテリーは `LifecycleStartEffect` で受信解除、`onStop` で編集モード終了 (揺れ停止)、ドラッグ取消、ウィジェットホスト停止、FrameRate 解除、設定の保存。
- **時計**: 秒表示なしは 1 分ごと (分の境界にそろえる)。秒表示ありは 1 秒ごと。
- **リフレッシュレート (`FrameRateController`)**: 解像度が同じ表示モードのうち最高レートを `preferredDisplayModeId`/`preferredRefreshRate` で要求
  (Android 15+ は `View.requestedFrameRate = REQUESTED_FRAME_RATE_CATEGORY_HIGH` も)。
  タッチ開始で引き上げ、指を離して 1.5 秒後に解除。編集モードの揺れ・ドラッグ中は `hold`。ホームボタン (onNewIntent) で `pulse`。
  解除時は 0 (システム既定) に戻すので、静止中は可変リフレッシュレートが効き電池を消費しない。
  ウィンドウ属性の更新は relayout を伴うため、同じ解像度で **複数のリフレッシュレートを選べる端末だけ** で行う
  (60Hz 固定の端末やエミュレータではタッチのたびの無駄な relayout をしない)。
- **設定の保存**: メモリ上の値を即時反映、ファイル書き込みは 300ms デバウンス (スライダー操作で毎回書かない)。onStop で flush。
- **配置情報**: `HomeGridPage` のジオメトリは変化時だけ保存 (スクロール中の毎フレーム割り当てを回避)。
- **メモリ**: アイコン・壁紙・背景は HARDWARE ビットマップ (Java ヒープを消費しない)。
- **起動/初回描画**: baseline profile。

### 6.9 設定アプリ (settings/)
- `Route` (Root, Wallpaper, Appearance, HomeScreen, AppIcons, IconPack, Dock, Folders, Widgets, Search, Gestures, GestureChoice(type), GestureApp(type),
  Notifications, Privacy, Animations, ControlCenter, Accessibility, Backup, About)。
- 外部から開くキー (`EXTRA_ROUTE`): wallpaper, appearance, home, icons, dock, folders, today, search, gestures, notifications, privacy, animations,
  controlcenter, accessibility, backup, about。**ルート指定で開くときは FLAG_ACTIVITY_CLEAR_TASK** を付ける (既存タスクがあると指定が無視されるため)。
- 「設定に認証を要求」がオンなら起動時に AppLock 認証。

### 6.10 Android のバージョン対応 (minSdk 23)
描画方式 (Liquid Glass、CPU のぼかしで作る背景、アイコン描画) と最適化は全バージョン共通。新しい API だけ分岐している:

| 機能 | 新しい API (必要なバージョン) | それより前 |
| --- | --- | --- |
| アイコン | AdaptiveIconDrawable (8.0) / モノクロ (13) | すべてレガシーアイコンとして描画 (`drawLegacy`) |
| ビットマップ | HARDWARE (8.0) | 通常のビットマップのまま (見た目は同じ、メモリはヒープ) |
| App ショートカット | LauncherApps.ShortcutQuery (7.1) | メニューに出さない |
| ユーザー一覧 | LauncherApps.getProfiles (8.0) | UserManager.getUserProfiles (`Context.userProfiles()`) |
| App のカテゴリ | ApplicationInfo.category (8.0) | パッケージ名のキーワードで推定 |
| 既定のホーム | RoleManager (10) | HOME の解決結果で判定、ホーム設定画面を開く |
| 認証 | BiometricPrompt + 画面ロック (10) | KeyguardManager の画面ロック確認画面 (`confirmDeviceCredential`) |
| 画面ロック (ジェスチャー) | GLOBAL_ACTION_LOCK_SCREEN (9) | 非対応のトースト |
| 設定パネル | Settings.Panel (10) | 通常の設定画面 |
| 写真の読み込み | ImageDecoder (9) | BitmapFactory + EXIF (androidx.exifinterface) で回転 |
| システム壁紙の設定 | ホーム/ロック別 (7.0) | 両方に 1 枚 (ボタンは「両方」だけ表示) |
| 壁紙の色 | WallpaperColors (8.1) | 既定の灰色 |
| システム壁紙の変更検知 | addOnColorsChangedListener (8.1) | `ACTION_WALLPAPER_CHANGED` ブロードキャスト |
| 通知バッジの非表示設定 | Ranking.canShowBadge (8.0) | すべて表示 |
| テーマ | Theme.DeviceDefault.DayNight (10) | values / values-night の `Base.Theme.Launcher` (Light / 通常) で同等 |
| アプリアイコン | adaptive-icon (8.0)、ベクターのグラデーション (7.0) | `mipmap/` の layer-list、`drawable/` の単色背景 (7.0 以降は `drawable-v24/` のグラデーション) |
| 背後のぼかし | Modifier.blur (12) | ぼかしなし (従来から 10/11 も同じ) |
| リフレッシュレート | View.requestedFrameRate (15) | preferredDisplayModeId (6.0〜) のみ |

- Java 8 のコレクション API (`putIfAbsent`・`getOrDefault`・`computeIfAbsent`・`removeIf`・`List.sort`・`stream` など) は Android 7.0 以降。
  Kotlin の拡張 (`getOrPut`・`getOrElse`・`removeAll`・`sortWith` など) を使う (lint の NewApi で検出される)。

### 6.11 権限・サービス
- 権限: REQUEST_DELETE_PACKAGES, SET_WALLPAPER, EXPAND_STATUS_BAR, VIBRATE, USE_BIOMETRIC, READ_CONTACTS (実行時), ACCESS_NOTIFICATION_POLICY,
  SET_ALARM, WRITE_SETTINGS (特別アクセス), READ_EXTERNAL_STORAGE (≤API 32, システム壁紙の読み取り用)。
- `<queries>`: ランチャー App、https 閲覧、カメラ、計算機、カレンダー、各アイコンパックの intent。
- 通知リスナー (バッジ・メディア)、ユーザー補助サービス (画面ロック・通知・履歴)。いずれもユーザーが手動で有効化する。

## 7. 実装済み機能

- ホーム: 左上詰めグリッド (列 3〜7 × 行 4〜9)、ドック (3〜6 個、ガラス/不透明/透明、ラベル)、検索ボタン/ページドット、ページ効果 (スライド/奥行き/キューブ/フェード)
- 編集モード: 揺れ、−ボタン (取り除く/削除)、ドラッグ並べ替え・ページ移動・ドック出し入れ、フォルダ作成/追加、編集メニュー (ウィジェット追加/カスタマイズ/ページ編集)、ページの表示/非表示/削除
- 長押しメニュー: App ショートカット、ホーム編集、共有、認証ロック、非表示、App 情報、取り除く/削除、フォルダ名称変更/削除、ウィジェット編集/サイズ変更/削除
- フォルダ: 背景ぼかし、3/4 列 × 3 行のページ、名前編集、フォルダ内並べ替え、外へドラッグ、編集モードの「−」で「App を削除 / ホーム画面から取り除く」のシート (`folderAppRemoveSheet`)
- App ライブラリ: 提案・最近追加・カテゴリ (11 種、ApplicationInfo.category + パッケージ名ヒューリスティック)・非表示フォルダ (認証)、一覧 (セクション索引)、検索
- 今日の表示: 日付、ウィジェット (並べ替え・削除・追加)
- ウィジェット: 時計・アナログ時計・カレンダー・バッテリー・Siri からの提案・メモ・検索 (小/中/大)、Android AppWidget (バインド・設定・再設定・角丸・長押し)
- Spotlight: App (かな同一視・頭文字)、設定項目 (ランチャー/システム)、連絡先 (権限)、計算機、Web (7 エンジン)、Play ストア、提案、最近の検索
- コントロールセンター: 通信 (パネル起動)、再生中 (通知アクセス要)、明るさ/音量スライダー、フラッシュライト、おやすみモード、画面回転ロック、タイマー、アラーム、計算機、カメラ、画面ロック、位置情報、ダークモード、設定 (表示タイル選択可)
- アイコン: 5 スタイル + クリア変種 + 色合い (色相/明るさ/壁紙色)、大きいアイコン、サイズ、6 形状、アイコンパック、ラベル表示/サイズ、影、作業プロファイルバッジ、通知バッジ (数字/ドット/オフ、色)
- 壁紙: 内蔵 8 種、写真、システム、ダークで減光、ホームでぼかし、システム壁紙 (ホーム/ロック) に設定
- 外観: 自動/ライト/ダーク、Liquid Glass スライダー (クリア↔色合い)、アクセント色、ステータスバー表示
- アクセシビリティ: 透明度を下げる、コントラストを上げる、文字を太く、視差効果を減らす
- ジェスチャー: 下/右上から下/上/ダブルタップ/ホームボタンに 13 種の動作 (App 起動含む)
- プライバシー: App ロック・非表示 (生体認証/画面ロック)、非表示フォルダ・設定画面の認証
- アニメーション: 速度、App を開く演出 (ズーム/リビール/フェード/システム/なし)、揺れ、触覚
- バックアップ: 設定+レイアウトの書き出し/復元 (AppWidget は除外)、設定/提案データのリセット
- 初回案内、既定ホームアプリ設定 (RoleManager)、英語/日本語

## 8. 未実装・制限事項 (Android の制約を含む)

- Wi-Fi/Bluetooth/モバイル通信/機内モードを直接切り替えることはできない (Android の制限) → システムパネルを開く。
- Android 13 以降はシステム壁紙の画像を読めないことが多く、その場合はガラスの背景ぼかしなし。
- App ショートカットはデフォルトのホームアプリのときのみ取得可能。
- カレンダーウィジェットは予定を読まない (READ_CALENDAR 未使用、「今日の予定はありません」固定表示)。天気ウィジェットなし。
- App ライブラリの漢字ラベルは読みが分からないため「#」セクションにまとめる。
- iOS のような屈折 (レンズ歪み) 表現はなく、ぼかし+ハイライト+縁で表現している。
- ウィジェットの自由なサイズ変更 (S/M/L 以外) は未対応。ドックのページ化は未対応。
- 写真壁紙の位置調整 (ピンチ/移動) は未対応 (中央で切り抜き)。
- 署名は公開リポジトリ内の固定鍵 (配布用としては安全でない。4 章参照)。
- Android のバージョンによる違い (6.10 参照): 8.0 未満はアダプティブアイコンがなく、7.1 未満は App ショートカットなし、
  9 未満はジェスチャーからの画面ロック不可、6.0 はホーム/ロック画面の壁紙を別々に設定できない、12 未満は背後のコンテンツのぼかし (Modifier.blur) なし。

## 9. 既知の不具合・注意点

- 実機での検証は未実施 (エミュレータのみ)。特に以下はエミュレータで未確認:
  通知バッジ、メディア操作、App ロック/非表示の認証フロー、AppWidget の追加、バックアップ/復元、アイコンパック、リフレッシュレート切替 (エミュレータは 60Hz 固定)。
- フォルダやメニューの拡大/縮小アニメーション中、ガラスの背景位置は拡大率を考慮しない (一瞬ずれる程度)。
- メニュー・シート等のガラスは壁紙だけを写す (背後のアイコンは写らない。背後のコンテンツ自体は Modifier.blur でぼかしている)。
- `FrameRateController` は端末のシステム設定 (例: 60Hz 固定) を超えることはできない。
- 署名鍵を固定する前 (75a7b93 以前) のビルドから更新するときは、署名が違うので一度アンインストールが必要 (以後は上書き更新できる)。
- API 23 エミュレータでは、起動後最初の右スワイプ (アイコン上から開始) だけ反映されないことが毎回あった。同じ操作は 2 回目以降成功し、
  API 34 では初回案内直後でも成功するため、エミュレータの入力注入の問題と判断 (実機で再現するかは未確認)。
- API 23 エミュレータで一度だけホーム (MainActivity) のスクリーンショットが真っ黒になった (同じコードの再実行では正常。swiftshader の一時的な不調)。
- 大画面はエミュレータ (Pixel Tablet と、その画面サイズを Pixel Fold の内側に変えたもの) でのみ確認。折りたたみのヒンジ位置 (FoldingFeature) は考慮していない (画面中央で分割)。
  ヒンジセンサーを公開せず、開いた画面の縦横比が 1.3 を超える折りたたみ端末は 1 ページ表示になる。
- クリア/色合いアイコンの見え方 (絵柄のアイコンなど) は実機の様々なアプリでは未確認。新しい種類で見分けにくいものがあれば 6.7 の判定を調整する。
- スモークテストで SystemUI のクラッシュが出ることがある (Pixel Launcher 無効化の副作用、本アプリとは無関係)。
- スモークテストで `input draganddrop` は使わない。長押し時間 (400ms) ちょうどで指を動かし始め、アプリの長押し判定 (400ms) と競合して結果が揺れる
  (75c4880 ではメニューが出たまま失敗、50b85a0 では長押し前にスクロール扱いになりフォルダ化されなかった)。8c3dcb2 で `drag_to` を motionevent 方式に変更。
  並べ替えの目的地はアイコン中心から 100px ずらす (止まるとフォルダ化範囲 = セルの ±26% に入るため)。

## 10. 修正履歴

| コミット | 内容 |
| --- | --- |
| a0baa7c | Gradle プロジェクトと Android CI の雛形 |
| 3632a7c | ランチャー本体 (ホーム・編集・フォルダ・ライブラリ・今日の表示・ウィジェット・Spotlight・コントロールセンター・アイコン・設定アプリ・英日) |
| 6267a59 | 日本語 README |
| 0a3126c | コンパイルエラー修正 (RowScope の weight、ネストしたスコープからの maxHeight) |
| afe7a7f〜e20f4f9 | エミュレータ・スモークテスト追加と改良 (draganddrop、シナリオ分割、ANR 対策) / ドラッグ端の幅を密度に合わせる / 設定ルートの戻るボタン非表示 |
| 4b633f7 | 設定の見出しを大文字化しない (日本語で「APP」になる問題) |
| a50d8fe | ドラッグ開始時に元の位置を空けたままにする (フォルダ化できない不具合の修正)、フォルダ化候補中のプレビュー固定、設定のルート指定を CLEAR_TASK で確実に |
| 249563a | README にスクリーンショット、CI 整理 (スモークは PR/手動、checkout/setup-java v5) |
| 75c4880 | **パフォーマンス最適化とクリアアイコン修正** (下記) |
| 6c8fe9f | DEVELOPMENT_NOTES.md・CLAUDE.md・tools/ を追加 |
| 278da56 | スモークテストに `hold_then_drag` (長押しでメニューが出てから指を動かす、人の操作の間合い) を追加 |
| dd32fda | **長押しメニュー表示後にドラッグへ移行できない不具合の修正** (`homeVerticalSwipe` を付け外ししない)、FrameRateController の無駄な relayout 削減 |
| 75a7b93 | dd32fda の検証結果を記録 |
| 50b85a0 | **絵柄のアイコンがクリア/色合いで白い塊になる不具合の修正** (前景は輝度で描く)、**署名鍵の固定** (上書き更新できない問題の修正)、versionCode を CI の実行番号に。エミュレータでクリア/ダーク/色合いの全アイコン表示を確認 |
| 8c3dcb2 | スモークテストの `drag_to` を motionevent 方式に (draganddrop のタイミング競合をなくす)。フォルダ作成・並べ替え・長押し後ドラッグ・クリアアイコンすべて成功を確認 |
| 6e4440e | 8c3dcb2 の検証結果を記録 |
| 6f74508 | **Android 6.0 (API 23) 対応** (minSdk 29 → 23)。新しい API の分岐と代替、旧バージョン用のテーマ・アプリアイコン、CI に lint (NewApi) と API 23 エミュレータのテスト |
| 07567a4 | lint の指摘を修正 (`HashMap.putIfAbsent` は API 24 → Kotlin の `getOrPut`)、lint 失敗時にレポートをログへ出す |
| 3d0b38a | lint の残り: システム壁紙の変更検知 (`addOnColorsChangedListener` は 8.1 以降、それより前は `ACTION_WALLPAPER_CHANGED`)。API 23 のスモークで右スワイプの切り分けを追加 |
| f28442c〜a615c26 | スモークテストのみ: API 23 で uiautomator 後に待つ・長押し対象を Gmail に、初回案内直後の右スワイプ確認 (API 34/23)、同じスワイプの再試行 |
| b0844ec | Android 6.0 の検証結果を記録 |
| 8a6364f | **実機フィードバック**: フォルダ化で開かない、フォルダ内の「−」で削除シート、バッテリー (小) のリングを中央に描く (枠が正方形でない端末でずれていた)、**折りたたみ/タブレットの 2 ページ表示**と大画面での最大幅、スモークテストに `smoke_wide.sh` |
| 0aab088 | タブレットは iPadOS と同じく 1 ページに戻す (2 ページ表示は折りたたみを開いた画面だけ: ヒンジセンサーか縦横比 1.3 以下)。App ライブラリは幅に応じて列を増やす。スモークテストに折りたたみ (タブレットの画面サイズを変えて再現) |
| 389d5a2 | スモークテスト: 折りたたみはタブレットの画面サイズを Fold の内側に変えて再現 (CI に pixel_fold の定義がない)、コントロールセンターのスワイプ位置 |
| d4f79d1 | 2 ページ表示でドックが今日の表示/App ライブラリに重なる問題を修正 (見えているホームの下に付いていく) |
| e1d4380 | **折りたたみは見開き表示に変更** (今日の表示 → 1+2 → 3+4 → … → App ライブラリ、奇数なら右は空のページ)。ドックは中央に戻す |

### 実機フィードバック対応の検証結果 (2026-10-26, e1d4380)
- Android CI 成功 (lint NewApi 0 件)。全スモークテスト成功、クラッシュなし。
- スマホ (API 34): フォルダ化しても開かない (`FOLDER_AUTO_OPEN: stayed closed`)、フォルダ内の「−」で「App を削除 / ホーム画面から取り除く」のシート、長押し後ドラッグ。
- タブレット (Pixel Tablet): 1 ページ表示、今日の表示は中央の列、App ライブラリは 6 列 (フォルダはスマホ程度)、フォルダ作成、コントロールセンターは右上。
- 折りたたみ (Pixel Tablet を 2208x1840/420dpi にして再現): 見開き (ホーム 1 + 空のページ)、右スワイプで今日の表示 (見開き全体)、
  App ライブラリ (4 列)、ドラッグでフォルダ作成、ドックは中央。バッテリーのリングと中のマーク・% が中央に揃っている。

### Android 6.0 対応の検証結果 (2026-10-25, a615c26)
- Android CI 成功 (lint NewApi のエラー 0 件)。
- API 23 エミュレータ: ホーム・今日の表示 (バッテリー含む)・App ライブラリ・Spotlight・長押しメニュー (ショートカットなし)・編集モード・
  コントロールセンター・アイコン (クリア/色合い/ダーク、レガシー経路)・壁紙設定 (「両方」ボタンのみ) が正常、クラッシュなし。
- API 34 エミュレータ: smoke / smoke2 とも従来どおり成功 (フォルダ作成・長押し後ドラッグ含む)。

### dd32fda の検証結果 (2026-10-24)
- Android CI 成功 (APK は Artifact `liquid-launcher-apk`)。
- Emulator smoke test: smoke2 で `HOLD_DRAG 16b_hold_then_drag: drag started` (1.2 秒押してメニューが出た後に動かしてドラッグ開始)、
  draganddrop でのフォルダ作成 (Entertainment)・並べ替え・ウィジェット追加・クリア/ダーク・日本語もすべて成功。smoke.sh も成功 (Spotlight・コントロールセンターの縦スワイプ含む)。
  クラッシュバッファは SystemUI (Pixel Launcher 無効化の副作用) のみ。
- 修正前 (278da56) の比較実行は、SystemUI のクラッシュでホームに戻れず途中のステップが進まなかったため、修正前の失敗は直接は確認できていない。

### 75c4880 の詳細 (2026-10-24)
- Liquid Glass を Modifier.Node 化 (composed を廃止)、キャッシュ、ガラス/中身のレイヤー分離。
- 背景を 2 段階化 (light: 1/2 解像度・弱ぼかし / heavy: 1/4 解像度・強ぼかし)。クリア側は背景がはっきり、かつ高解像度で見える。既定 0.3 の見た目は従来どおり。
- 時計・バッテリーをライフサイクルとページ可視状態に連動。時計は分境界で更新。onStop で編集モード終了。
- ページャーの各ページを独立レイヤー化。不透明壁紙のときウィンドウ背景を null に。
- FrameRateController 追加 (MainActivity/SettingsActivity)。
- アイコンのグリフ抽出を色差ベースに変更 (クリア/色合い/ダークで記号が消える不具合の修正)。
- アイコン・壁紙・背景を HARDWARE ビットマップ化、パッケージ単位のアイコン破棄、ショートカット変更でアプリアイコンを作り直さない、onTrimMemory。
- アイコンバージョンをルートで 1 回だけ購読 (LocalIconVersion)。
- 設定保存のデバウンス (300ms) と onStop での flush。
- baseline profile 追加。HomeGridPage のジオメトリ保存を変化時のみに。

## 11. 今後の開発予定 (候補)

優先度高:
- 実機での検証 (リフレッシュレート切替、通知バッジ、メディア、AppWidget、生体認証)。
- カレンダーウィジェットの予定表示 (READ_CALENDAR、任意権限)。
- 天気ウィジェット (位置情報 + 公開 API、要検討)。

優先度中:
- 写真壁紙の位置/拡大調整、ロック画面と別の壁紙。
- Liquid Glass の屈折表現 (RuntimeShader, API 33+) — ただし既存の見た目と性能を損なわないこと。
- フォルダ/メニューのアニメーション中のガラス背景の拡大率対応。
- App ライブラリから直接ホームへドラッグ。
- スマートスタック (ウィジェットの重ね) 。
- Macrobenchmark による本物の baseline profile 生成。

優先度低:
- release 用の署名設定、Play ストア向けの整備。
- 他言語対応。

## 12. よくある作業の手順

- **設定項目を追加**: `LauncherSettings` にデフォルト値付きで追加 → `SettingsScreens.kt` の該当画面に行を追加 → 文字列を英日に追加 → `tools/check_strings.py`。
- **内蔵ウィジェットを追加**: `WidgetKind` に追加 (ラベル/説明の文字列) → `ui/today/Widgets.kt` の `WidgetContent` に分岐と実装。
  定期更新が必要なら `rememberNow` を使う (表示中だけ動く)。ブロードキャストは `LifecycleStartEffect` + `LocalContentActive` で。
- **ガラス素材を使う**: `Modifier.liquidGlass(shape, tint, strength)`。文字を載せるパネルは `strength >= 1.4`。
- **アイコン描画を変える**: `IconRenderer` を変更したら、スタイルごとに消えるアイコンがないかスモークテストで確認 (smoke2 のクリアアイコンのスクショ)。
- **検証**: push → Android CI 成功を確認 → (PR があれば自動で) Emulator smoke test → ログからスクショを取り出して確認。
