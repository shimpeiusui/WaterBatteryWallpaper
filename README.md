# WaterBatteryWallpaper（水のバッテリー壁紙）

Android向けのライブ壁紙アプリです。**スマートフォンのバッテリー残量を水位で表現**し、端末を傾けると水面が斜めになります。

## 主な機能

- バッテリー残量（％）に応じて水位が上下します
- 重力センサー（利用できない場合は加速度センサー）で傾きを検知します
- 青緑色のグラデーションと光る水面をアニメーション表示します
- 壁紙が非表示の間はセンサー監視と描画を停止します
- アプリを開いて「Set live wallpaper」を押すと壁紙の設定画面が開きます

## 対応環境

- Android 8.0（API 26）以降
- ライブ壁紙に対応したAndroid端末
- 傾きへの反応には重力センサーまたは加速度センサーが必要です

## APKの入手方法

1. このリポジトリの [Actions](../../actions) を開きます。
2. **Build Android APK** の実行履歴を選びます。
3. ビルドが成功していること（緑のチェック）を確認します。
4. 画面下部の **Artifacts** から `WaterBatteryWallpaper-debug` をダウンロードします。
5. ZIPファイルを展開すると `app-debug.apk` が入っています。

※ デバッグ用APKです。インストール時にAndroidの確認操作が必要な場合があります。

## 使い方

1. APKをAndroid端末にインストールします。
2. **Water Battery Wallpaper** を起動します。
3. **Set live wallpaper** を押します。
4. プレビューを確認し、ホーム画面などに壁紙を設定します。

## 開発・ビルド

- 言語：Java
- Android SDK：35
- Java：17
- Gradle：8.9
- Android Gradle Plugin：8.7.3

ローカルでのビルド例：

```bash
gradle :app:assembleDebug
```

`main` ブランチへの更新時にGitHub ActionsでもAPKを自動ビルドします。

## 現在の開発状況

最初の試作版です。実機での動作確認や、液体表現・傾きへの追従性の調整はこれから行います。APKの生成状況はActionsの最新実行結果を確認してください。
