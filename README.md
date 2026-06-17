# NoteScope

**日本語** | [English](README.en.md)

音符ブロックを**見るだけ**で、設定されている音階をアクションバーに表示する Paper プラグインです。
いちいち鳴らさなくても「この音符ブロックは何の音か」が分かります。

```text
♪ F#3 (ファ#)   調律 0/24   楽器: ハープ
```

- **音名**（`F#3`）… 科学的音名。音符ブロックは F#3〜F#5 の 2 オクターブ
- **ドレミ**（`ファ#`）… 日本語の音階名
- **調律 0/24** … 調律段階。設置直後＝0（F#3）、右クリックごとに +1、24（F#5）で 1 周
- **楽器: ハープ** … 真下のブロックで決まる楽器

---

## 背景・目的

音符ブロックは右クリックするたびに音階が 1 段ずつ変わりますが、**今どの音に設定されているかは鳴らしてみるまで分かりません**。
曲を組むときに「あと何回右クリックすればいいか」を数えたり、設置済みのブロックを耳で確認したりするのは地味に面倒です。

NoteScope はプレイヤーの視線を判定し、音符ブロックを見ている間だけアクションバーに音階を表示します。
クライアント MOD ではなくサーバー側プラグインなので、**プレイヤーは何もインストール不要**で、バニラクライアントのまま機能します。

---

## 動作要件

| 項目 | バージョン |
| --- | --- |
| サーバー | Paper **26.1.2**（build 69 で確認） |
| Java | **25**（25.0.x で確認） |
| ビルド | JDK 25 + Maven（`brew install openjdk@25 maven`） |
| 依存プラグイン | **なし** |
| クライアント | **バニラのままで可**（MOD 不要） |

> この jar 1個だけで動作します。追加ライブラリ・別プラグインは不要です（`paper-api` は `provided` スコープ＝サーバーが実行時に提供）。

---

## 使い方

1. サーバーの `plugins/` に jar を置いて再起動（→ [サーバーへの配置](#サーバーへの配置)）。
2. ゲーム内で**音符ブロックに視線を合わせる**だけ。アクションバーに音階が表示されます。
3. 表示が不要なときは `/notescope off`、再び有効化は `/notescope on`。

### 表示の見かた

| 表示 | 意味 |
| --- | --- |
| `F#3` | 科学的音名（音名 + シャープ + オクターブ）。音符ブロックは **F#3〜F#5** |
| `(ファ#)` | ドレミ表記（`ド レ ミ ファ ソ ラ シ` + シャープ） |
| `調律 0/24` | 調律段階。**0 = 設置直後（F#3）**、右クリックごとに +1、**24 = F#5** |
| `楽器: ハープ` | 真下のブロックで決まる楽器（土・空気ならハープ、石ならバスドラム…等） |

> シャープのみ表記します（バニラの音符ブロックがシャープ基準のため）。たとえば `F#3` は `G♭3` と同じ音です。

---

## コマンド仕様

| コマンド | 説明 | 実行者 | 権限 |
| --- | --- | --- | --- |
| `/notescope` | 表示の ON/OFF をトグル | プレイヤーのみ | `notescope.use` |
| `/notescope on` | 表示を ON | プレイヤーのみ | `notescope.use` |
| `/notescope off` | 表示を OFF | プレイヤーのみ | `notescope.use` |
| `/notescope status` | 現在の ON/OFF を確認 | プレイヤーのみ | `notescope.use` |

- 表示の ON/OFF は**プレイヤーごと**に切り替わります（他人には影響しません）。
- 既定は全員 **ON**。ON/OFF の状態はサーバー稼働中のみ保持され、**サーバー再起動で ON に戻ります**。
- `/notescope` のタブ補完で `on` / `off` / `status` が候補に出ます。

---

## 権限

| 権限ノード | 既定 | 説明 |
| --- | --- | --- |
| `notescope.use` | `true`（全員） | 音階表示の受信と `/notescope` の使用を許可 |

既定では全プレイヤーに表示されます（LuckPerms 等の追加設定は不要）。
特定プレイヤー／グループで**無効**にしたい場合のみ、対象に該当ノードを `false` に設定します。

```bash
# 例: あるグループで音階表示を無効化
lp group default permission set notescope.use false
# 例: あるユーザーで音階表示を無効化
lp user <name> permission set notescope.use false
```

---

## 仕組み（技術メモ）

- **視線判定**: 一定間隔（4 tick ≒ 0.2 秒）で全オンラインプレイヤーをループし、目線から `rayTraceBlocks(6.0, NEVER)` でレイを飛ばします。最初に当たったブロックが音符ブロック（`BlockData instanceof NoteBlock`）なら表示します。
- **負荷について**: コストは「頻度 × オンライン人数 × 1 回のレイトレース」で決まります。頻度は秒間 5 回、レイは最大 6 ブロック分のボクセル走査（`FluidCollisionMode.NEVER` で流体判定も省略）と軽く、プレイヤー周囲 6 ブロックは常にロード済みなので追加のチャンク読み込みも発生しません。線形に増えるのはオンライン人数だけで、数十人規模なら 1 tick（50ms）の処理予算にはほぼ影響しません（数百人規模なら間隔を伸ばす等で調整可能）。なおレイトレースはワールド状態を読むためメインスレッド（`runTaskTimer`）で実行します。
- **音階の取得**: `NoteBlock#getNote()` で得た `org.bukkit.Note` から、`getTone()`（音名）・`isSharped()`（シャープ）・`getId()`（0〜24 の調律段階）を読み取り、科学的音名・ドレミ・調律段階に整形します。オクターブ番号は C で繰り上がるため、`getId()` から `3 + (id + 6) / 12` で算出します。
- **楽器の取得**: `NoteBlock#getInstrument()` を日本語名に変換します（未知の楽器は enum 名をフォールバック表示）。
- **表示**: Adventure の `Player#sendActionBar(Component)` でアクションバーへ送信します。チャットを汚さず、見るのをやめれば数秒で自然に消えます。
- **ブロックの書き換えは一切行いません**（読み取り専用）。

> 視線判定は Bukkit のグローバルスケジューラ（`runTaskTimer`）で動くため、対象は **Paper**（非 Folia）です。

---

## ビルド

JDK 25 と Maven が必要です（未導入なら `brew install openjdk@25 maven`）。
付属の `deploy.sh` でビルドできます（**Docker 不要**）。

```bash
./deploy.sh
```

生成物: `target/NoteScope-1.0.0.jar`

`deploy.sh` は内部で JDK 25 を指定して `mvn clean package` を実行します。
別の場所の JDK を使う場合は `JAVA_HOME=/path/to/jdk25 ./deploy.sh` で上書きできます。直接ビルドするなら:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean package
```

---

## サーバーへの配置

サーバーの `plugins/` に jar を置いてサーバーを再起動します。jar の入手方法は次の 2 通りです。

### A. リリース版を使う（ビルド不要・推奨）

[Releases](https://github.com/astail/minecraft-onpu/releases) から最新の `NoteScope-<version>.jar` をダウンロードします。JDK や Maven は不要です。

```bash
# 最新リリースの jar をダウンロード（gh CLI を使う場合）
gh release download --repo astail/minecraft-onpu --pattern '*.jar'
```

### B. 自分でビルドする

[ビルド](#ビルド) の手順で `target/NoteScope-1.0.0.jar` を生成します。

### 配置

入手した jar をサーバーの `plugins/` に置いてサーバーを再起動します。

```bash
# バインドマウントしている場合（ホスト側 plugins ディレクトリへコピー）
cp target/NoteScope-1.0.0.jar /path/to/data/plugins/
docker restart <コンテナ名>

# 名前付きボリューム等の場合（コンテナへ直接コピー）
docker cp target/NoteScope-1.0.0.jar <コンテナ名>:/data/plugins/
docker restart <コンテナ名>
```

起動ログに以下が出れば成功です。

```text
[NoteScope] NoteScope を有効化しました。音符ブロックを見るとアクションバーに音階が表示されます。
```

---

## プロジェクト構成

```text
.
├── pom.xml
├── deploy.sh
├── README.md
└── src/main/
    ├── java/io/github/astail/notescope/
    │   ├── NoteScopePlugin.java    # 本体（コマンド登録・視線タスク起動）
    │   ├── NoteLookTask.java       # 視線判定 → アクションバー表示
    │   ├── NoteFormatter.java      # Note/Instrument → 表示文字列の整形
    │   └── NoteScopeCommand.java   # /notescope（ON/OFF トグル）
    └── resources/plugin.yml
```

> パッケージ名（`io.github.astail.notescope`）/ `NoteScope` / コマンド名は任意でリネーム可能です（pom.xml・各 `package`・`plugin.yml` を揃えて変更）。

---

## 注意点

- **表示が出ない場合**: 音符ブロックに視線がちゃんと合っているか（最大 6 ブロック）、`/notescope status` が ON か、`notescope.use` 権限があるかを確認してください。
- **アクションバー以外への表示**（ボスバー・常時 HUD 等）はサーバー側プラグインの範囲では制約があります。アクションバーは「見ている間だけ出て、すぐ消える」用途に最適です。
- **音階のオクターブ表記**は F#3〜F#5 を採用しています（Minecraft 公式 Wiki の表記に準拠）。資料によっては F#4〜F#6 等と記す場合があります。
- `paper-api` の build 番号はサーバー更新に追従可能です（例: `26.1.2.build.70-stable`）。
