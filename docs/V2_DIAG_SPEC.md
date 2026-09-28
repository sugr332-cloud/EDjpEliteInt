# マップの言い回し・診断ログ・ゲーム状況 Unknown（J-20 / D-1 / D-2）

**Status:** J-20 DONE / D-1・D-2 レビュー済み・マージ待ち（2026-09-28）
**正本との関係:** `docs/ELITEINTEL_INTEGRATION_PLAN.md` §R.13 の統制手順に従う。全体テストの判定は「失敗 0 件」。

## J-20: 「マップ開いて」をギャラクシーマップにする

- 背景（2026-09-27 実機）: 「マップ開いて」で LLM がシステムマップを選んだ。日本語の言い回しに「マップ」だけのものが、ギャラクシーマップ（`display_open_galaxy_map`）にもシステムマップ（`display_open_system_map`）にも無い。
- `ai_action_aliases_ja.properties` の `display_open_galaxy_map` に「マップ」「マップ開いて」「マップを開いて」「マップを表示」を追加する。システムマップ側は変えない（「システムマップ」「星系マップ」で開く）。
- 埋め込みルーティングテスト（`trainingphrases/`、`embeddingTest`）を追加: 「マップ開いて」「マップを開いて」「マップ」で `display_open_galaxy_map` が offered に入り、かつ `display_open_system_map` より上位であること。「システムマップを開いて」では `display_open_system_map` が上位のままであること。

## D-1: VEGA の診断記録をログファイルにも残す

- 背景: `STT:`、`intake:`、`reduce:`、`llm: response`、`exec:` などの VEGA の診断記録（`VegaDiagnostics`）は、アプリ画面のシステムログ欄にだけ出て、`logs/elite-intel.log` に残らない。後から PowerShell で検索できない。
- 画面に出している同じ内容を、専用の Logger（例: `elite.intel.vega.diag`）で INFO としてログファイルにも書く。画面の表示は変えない。
- `log4j2.xml` に、その Logger を info で出力する設定を追加する（`elite.intel` 全体は error のまま）。
- 実装時の確定（PLAN CHECK）: `info()` は INFO、`debug()` は DEBUG で書き、Logger `elite.intel.vega.diag` は debug で出力する（画面の詳細表示の設定に関係なく、ファイルには全行残す）。
- ログファイルは `.gitignore` 済み（`/logs`）。発話内容が記録されるが、ローカルのファイルのみ。

## D-2: ゲーム状況が Unknown になる件

- 背景（2026-09-26 実機）: ゲーム中にもかかわらず `Game situation: Unknown` と表示された。`Status.getSituation(flags, flags2, location)` は、Status.json のフラグにどの状態も立っていないとき（本船フラグも無い）に `UNKNOWN` を返す。フラグが 0（Status.json が読めていない、古い、別フォルダを見ている等）の可能性がある。
- **まず調査と診断ログのみ**（直し方は結果を見て決める）:
  - READ-ONLY 調査: Status.json をどこから・いつ読んでいるか（パスの決め方、監視方法、読み込み失敗時の扱い）、`getSituation` に渡るフラグの出どころ、`UNKNOWN` になり得る経路。
  - 診断ログの追加: 状況が `UNKNOWN` に変わったとき（変化したときだけ、毎回ではない）に、INFO で `flags`、`flags2`、読んでいる Status.json のパスと最終更新時刻、最後に読めた時刻を 1 行出す。
- 実装時の確定（PLAN CHECK）:
  - `StatusEventSubscriber`: `Game situation changed to UNKNOWN — flags=0x.. flags2=0x.. path=.. modified=.. lastRead=..` を、UNKNOWN に入ったときだけ 1 行（起動直後の最初のイベントも含む）。`lastRead` はそのイベントを受け取った時刻（Status.json は読んだ直後にイベントとして流れるため）。
  - `AuxiliaryFilesMonitor`: 起動時に監視フォルダ・Status.json の有無・最終更新時刻を 1 行。Status.json の内容が 30 秒（`STATUS_STALE_THRESHOLD_MS`）変わらないとき `Status.json has not been read for ..ms` を 1 回（変化したら再び出せる状態に戻る）。ゲーム側は変化があるときだけ書くので、ドッキング中の放置などでも出る。単独では異常を意味しない。
  - `log4j2.xml`: 上記 2 クラスを info。これにより `AuxiliaryFilesMonitor` の既存 info 行（`Published update for file:` など、ファイル更新ごと）もログに出る。
- 実機で再現したら、そのログを基に原因を特定し、別サブフェーズ（D-3）で直す。

## 実装台帳

| ID | 内容 | 変更許可ファイル | TEST GATE | 状態 |
|---|---|---|---|---|
| J-20 | 上記 J-20 | `ai_action_aliases_ja.properties`（`display_open_galaxy_map` の行のみ）、新規ルーティングテスト 1 つ | 新規ルーティングテスト（`embeddingTest`）、`AliasPhraseTest`、`AliasVocabularyTest`、`AliasPhraseCollisionTest`、`AiActionLocalizationsTest`、全体テスト（失敗 0 件） | DONE（`1aaeb8a`） |
| D-1 | 上記 D-1 | PLAN CHECK で確定（`VegaDiagnostics` と `log4j2.xml`、テスト） | 新規テスト（画面向けの出力と同じ内容がログにも渡ること）、全体テスト（失敗 0 件）。実機: `logs/elite-intel.log` に `STT:` や `llm: response` の行が出ること | レビュー済み・マージ待ち（`v2/d-1` @ `645acf3`） |
| D-2 | 上記 D-2（調査＋診断ログ） | PLAN CHECK で確定（Status の読み込み箇所、`log4j2.xml`、テスト） | 新規テスト（UNKNOWN への変化時だけ 1 回出ること）、全体テスト（失敗 0 件） | レビュー済み・マージ待ち（`v2/d-2` @ `1c4be91`） |
