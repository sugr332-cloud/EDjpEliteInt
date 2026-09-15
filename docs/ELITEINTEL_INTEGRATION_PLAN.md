# EliteIntel Integration Plan

**Status:** Draft — this is a forward-looking plan, not a record of completed work.
**Current roadmap:** §R (Roadmap v2, 2026-09-15). §1–§17 below keep the original phase numbering as an execution record.
**Repo (2026-09-15〜):** 統合リポジトリ（本リポジトリ）。EliteIntel のコードと履歴をルートに、C-CORE を `c-core/` に置く（§R.0）。
**Repo (〜2026-09-15, archived):** `sugr332-cloud/EliteIntel` (fork of `SudoKrondor/EliteIntel`, upstream remote `origin`, fork remote `fork`)
**Related repo (〜2026-09-15, archived):** `sugr332-cloud/EDpjKinsaku` (C-CORE exobiology species evaluation engine)

## 0. Ground rule

A design decided in a conversation (with any AI assistant, in any session) is **not** a fact about
this repository until it has been verified against the actual repository state — a file exists, a
commit exists, a test passes. This document exists because that rule was violated once already: an
earlier session asserted this file existed in `EDpjKinsaku` and described completed phases, when in
fact neither the file nor the phases existed anywhere on GitHub. Before citing a phase in this
document as done, verify it (`git log`, a passing test run, a file that can be `cat`'d) and record
what was actually checked, not what was proposed.

## R. Roadmap v2 — 統合版（2026-09-15）

**このセクションが現在の優先順位と Phase 番号の正本である。**
`sugr332-cloud/EDpjKinsaku` の `docs/ELITEINTEL_INTEGRATION_PLAN.md`（`3a5ef93` / `1b320c9`）にあった
「EDpjKinsaku / EliteIntel 実装ロードマップ」はここへ統合した。

### R.0 統合リポジトリ（2026-09-15）

EliteIntel fork と EDpjKinsaku の 2 リポジトリ運用で、計画書・コミット先・ブランチ名（`master` / `main`）の混線が起きたため、
1 つのリポジトリへ一本化した。

| パス | 中身 | 取り込み方式 |
|---|---|---|
| `/` | EliteIntel（Java / Gradle、install4j、`distribution/`） | `sugr332-cloud/EliteIntel` `master` の履歴を保持 |
| `/c-core/` | C-CORE（`bio evaluate` CLI、`bio_entry` ビルド、テスト） | `sugr332-cloud/EDpjKinsaku` `main` `1b320c9` から履歴なしでコピー（`c-core/PROVENANCE.md`） |
| `/docs/` | 本計画書を正本とする | — |

- 既定ブランチは `main`。旧 fork の `master` 履歴をそのまま `main` として引き継ぐ。
- EliteIntel upstream（`SudoKrondor/EliteIntel`）の更新は `upstream` リモートから取り込む。
- EDpjKinsaku の Mining / DB / value・ranking / backtest 系は持ち込んでいない。v2-P9 で必要になった時点で判断する。
- 旧 2 リポジトリはアーカイブし、以後コミットしない。
- 本文中の「EDpjKinsaku」「`edpj bio evaluate`」は、統合後は `c-core/` を指す。

§1 以降（旧 Phase 0〜12、§3〜§17）は **実行記録として残す**。旧番号と v2 番号が衝突するため、
今後のコミットメッセージ・文書では v2 の Phase を必ず `v2-P<n>` と書く（例: `docs(v2-P2): ...`）。
接頭辞のない「Phase n」は旧番号を指す。

### R.1 最優先事項（2026-09-15 決定）

1. **AI 会話は LLM の HTTP API ではなく `agy`（Antigravity CLI）を外部プロセスとして使う。**
2. **日本語化は「表示」から「指示（音声・テキストのコマンド）」まで行う。**

この 2 つは互いにブロックしない並行トラックとして扱う。

- **Track J（日本語化）:** v2-P1 → v2-P2
- **Track G（agy CLI）:** v2-P3

C-CORE の追加・拡張（v2-P9）はこの 2 トラックの後に回す。既に完了した C-CORE 接続（旧 Phase 4〜8/12）は壊さず維持する。

### R.2 基本方針

- 現在存在する EliteIntel の機能を先に日本語化する。日本語化のために既存機能を削除・簡略化しない。
- C-CORE を理由に既存 EliteIntel の UI / Journal / Navigation / Assistant を作り直さない。C-CORE の判定 Source of Truth は維持する。
- AI は判定エンジンではなく、説明・要約・推薦・自然言語対話・許可済みアクションの選択を担当する。
- AI Provider は API に固定せず、**CLI プロセスを第一級の実装方式**とし、`agy` を既定実装とする。
- AI Provider の変更で Game Context や C-CORE の内部構造を変更しない。
- 実装前に read-only 調査を行い、既存コード・仕様・テストを確認する（§0）。
- 各 Phase は小さく実装し、fixture/test を先に追加する。

### R.3 v2 Phase 一覧と現状（2026-09-15、`master` = `459750cd` で確認）

| v2 Phase | 内容 | 状態 | 根拠 / 旧番号との対応 |
|---|---|---|---|
| v2-P0 | 現状固定 / read-only baseline | **完了** | 旧 Phase 0・1（§5）。build 成功、AI 入力経路を実ファイルで追跡済み |
| v2-P1 | 日本語化 inventory（表示〜指示） | **完了（数値確定）** | R.4 の件数は `i18n-parity-baseline.txt` と各 `.properties` から計測 |
| v2-P2 | 既存機能の日本語置換（表示 → 指示） | **進行中** | `Language.JA`（`11907a78`）、P0（`11907a78`）・P1（`5c9716ec`）翻訳済み。残りは R.4 |
| v2-P3 | AI Provider CLI 化 / `agy` 対応 | **未着手** | `ProviderEnum` は API 系のみ。`PHASE11_AI_PROVIDER_CLI_SPEC.md` の CLI 化方針を `agy` 既定に更新（同文書冒頭の改訂注記）。**着手前に R.6「agy の実行境界（安全要件）」を満たすこと** |
| v2-P4 | 日本語自然言語による問い合わせ・指示（テキスト） | 一部実装 | 旧 Phase 10（§17）: 日本語テキスト入力・日本語応答方針は実装済み。`agy` 経由では未確認 |
| v2-P5 | 音声入出力（STT / TTS / VOICEVOX） | 一部実装 | Kokoro→日本語フォールバック（`334b35b1`）。実機 E2E は未実施（`PHASE11_VOICE_IO_PHASE_UPDATE.md`）。**同梱 STT（`ParakeetSTTImpl`）は日本語語彙を持たず日本語音声を認識できない（§10 で確認済み）。日本語 STT は別バックエンドを新規に選定する（CLI 方式を含めて検証、R.7）** |
| v2-P6 | Game Action / Ship Control の安全化 | 既存機能あり | 既存 `GameInputStep` / `GameControllerBus` / `KeyProcessor`。System Map 駅選択は未実装（§17） |
| v2-P7 | HUD / Overlay / VR | 既存機能あり | Aleoida MATCH 表示は実装済み（旧 Phase 6、§13） |
| v2-P8 | Generic Game Data / Trade Assistant | 既存機能あり（艤装検索は欠落） | 既存 Spansh / trade 機能（`FindCommodityCommand` 等）。日本語化は v2-P2 の対象。**艤装（モジュール）をギャラクシー内で検索するコマンドは存在しない**（`ShowModulesPanelCommand` はモジュール画面を開くだけ、`query_local_outfitting` は現在地の艤装を LLM に読ませるだけで、`find_commodity` のような「範囲 ly 内から探す」経路がない）。新規コマンドが必要（R.7） |
| v2-P9 | C-CORE 統合の拡張（全 species / collection state / value / ranking） | 一部完了・後段 | C-CORE 本体は `c-core/`（54 tests passed）。旧 Phase 4・5・6・7(§14)・12(§15) 完了。旧 Phase 7（collection state）・8（all species）・10（value/ranking）未着手。旧 Phase 9 は保留（§16） |
| v2-P10 | Offline Assistant（ローカル CLI provider） | 未着手 | — |
| v2-P11 | Installer / Update / Runtime packaging | 一部 | C-CORE 同梱（旧 Phase 12、§15）のみ。`c-core/` からの `bio_entry` ビルド成果物を `distribution/ccore/windows/` へ置く工程は手動 |

### R.4 v2-P1 inventory: 日本語化の残量（2026-09-15 計測）

| バンドル | 役割 | 基準キー数 | `_ja` 翻訳済み | 未翻訳（baseline の `\|ja\|MISSING`） |
|---|---|---|---|---|
| `gui` | 画面表示（UI・HUD・設定・ログ・警告） | 779 | 523 | 256 |
| `responses` | VEGA の定型応答・読み上げ文 | 286 | 0 | 286 |
| `ed_events` | ゲームイベント通知の文言 | 347 | 0 | 347 |
| `ai_action_aliases` | **指示**（音声/テキストコマンドの別名） | 198 | 0 | 198 |

コード側の未対応:

- 素材名の日本語別名 DB 列 `name_ja` が未追加（§4）。日本語の指示で素材名を照合するには必要。
- `PhoneticInputNormalizer`（`ThoughtDispatcher` の既定正規化）の日本語入力に対する挙動は未確認。

### R.5 v2-P2 既存機能の日本語置換【最優先 / Track J】

#### 目的

v2-P1 で確定した残量を、**表示 → 応答 → 通知 → 指示** の順に日本語へ置き換え、既存機能を日本語環境で一通り操作できる状態にする。

#### カテゴリと順序

1. **J-1 表示:** `gui` 残り 256 キー（Navigation / Mission / Ship / Cargo / Exobiology / Codex / Trade / 設定 / エラー・警告・状態表示 / 音声関連表示 / 既存 Assistant 表示）
2. **J-2 応答:** `responses` 286 キー
3. **J-3 通知:** `ed_events` 347 キー
4. **J-4 指示:** `ai_action_aliases` 198 キー（日本語の言い回しでアクションを指示できるようにする）
5. **J-5 指示の照合:** `name_ja` 列追加と素材名照合、`PhoneticInputNormalizer` の日本語入力確認、**および商品（commodity）の日本語照合辞書**（下記参照）

J-1〜J-4 はそれぞれ内部をさらに画面・機能単位のサブカテゴリに分けてよいが、コミットはサブカテゴリ単位とする。

#### J-5 商品（commodity）辞書の欠落（2026-09-15 判明）

**現状（コード確認済み）:** `FindCommodityCommand`（および `AddMiningTargetCommand` など同じ経路を使うコマンド）は、
LLM への抽出指示（`ActionParameterSpec`）で「商品名を翻訳せず小文字のまま抽出する」ことを明示したうえで、
`FuzzySearch.fuzzyCommodityMatch()` で英語の商品テーブルにあいまい照合している。**日本語の商品名（例:「金」）は
この照合を通らないため見つからない。** J-5 は素材（material）の `name_ja` 列のみを対象としており、商品
（commodity）側には対応する日本語辞書が存在しない。

**方針:** LLM に商品名の自由英訳をさせない。J-5 の `name_ja` と同じ考え方で、商品にも
**日本語名 → canonical 英語名の決定的な辞書**（DB 列 or 静的テーブル）を追加し、`FuzzySearch` 相当の照合を
その辞書経由で行う。辞書に無い日本語名は `UNKNOWN` として扱い、検索・保存のどちらも行わない（曖昧なまま
Spansh 検索や DB 書き込みに渡さない。R.12 の DB 非汚染ルールとも一致する）。

#### ルール

- 既存キーを優先して利用する。同じ意味の日本語を複数箇所へ個別実装しない。
- コード内へ日本語文字列を直接大量に埋め込まない。既存の localization architecture を利用する。
- UI の意味を変更せず、表示言語だけを置き換える。
- Elite Dangerous には日本語クライアントがないため、ゲーム内固有名詞（`Language.isGameLocalized()` の対象外）の扱いは既存言語（IT/UK）と揃える。

#### 作業効率化ルール

日本語化の完了をキー単位の細切れ作業にしない。**カテゴリ単位でまとめて置換・検証する。**

- 1キーごとの個別コミットは行わない。
- 同一カテゴリの翻訳対象をまとめて処理する。
- 翻訳後はカテゴリ単位でキー欠落・重複・placeholder 不整合を検証する。
- 画面表示確認もカテゴリ単位でまとめて実施する。
- 小さな変更であっても、原則としてカテゴリ単位でコミットする。
- 翻訳対象と無関係なリファクタリングを同時に行わない。
- C-CORE、AI Provider、HUD/Overlay 等の後段設計へ寄り道せず、v2-P2 の日本語置換を完了させる（Track G の作業は Track J のコミットに混ぜない）。
- 既存の翻訳済みキーを再調査して同じ作業を繰り返さない。
- 既に検証済みのカテゴリは再検証せず、未完了カテゴリへ進む。
- エラーや曖昧な翻訳が発生した場合のみ、そのキー・カテゴリを個別に追加調査する。

#### 機械的検証（カテゴリ単位の完了条件）

- 翻訳したキーに対応する `|ja|MISSING` 行を `app/src/test/resources/i18n-parity-baseline.txt` から削除している。
- `BundleKeyParityTest` と `BundleQuotingTest` が成功する。
- `:app:compileJava` が成功する。

#### 推奨サイクル

```text
未翻訳カテゴリを抽出
      ↓
カテゴリ単位で翻訳
      ↓
機械的なキー / placeholder 検証（上記）
      ↓
必要な画面だけ表示確認
      ↓
カテゴリ単位でコミット
      ↓
次のカテゴリへ
```

この Phase では、**「翻訳 → 1キー確認 → 1キーコミット」を繰り返さない。**

#### 完了条件

- `i18n-parity-baseline.txt` に `|ja|` の行が残っていない。
- `name_ja` 列が追加され、日本語の素材名で指示できる。
- 商品（commodity）の日本語 → canonical 英語名辞書が追加され、日本語の商品名で `find_commodity` 等が動作する。辞書に無い名前は `UNKNOWN` として検索・保存されない。
- 現在 EliteIntel に存在する主要機能が日本語 UI で利用でき、未翻訳の英語 UI が意図せず残っていない。
- localization test が成功し、既存機能の挙動を変更していない。
- J-1〜J-5 の全カテゴリについて、翻訳・検証・コミットが完了している。

### R.6 v2-P3 AI Provider CLI 化 / `agy` 対応【最優先 / Track G】

#### 目的

AI 会話の LLM 呼び出しを HTTP API から **`agy` の外部プロセス実行**へ置き換え、`agy` を既定の AI Provider にする。

#### 差し込み位置（既存コードで確認済み）

既存の provider 境界は `elite.intel.ai.brain.vega.llm` の次の 2 つである。

- `LlmTransport` — リクエスト本文を送り、生の応答を返す（現状は HTTP）
- `LlmProviderAdapter` — `LlmRequest` を provider 形式へ変換し、`parse`（tool-calling ターン）と `parseText`（圧縮ターン）で応答を解釈する

provider の登録は `VegaLlmGatewayFactory`、種別は `ProviderEnum`。したがって `agy` 対応は以下で行い、
`VegaLlmGateway` / `ThoughtDispatcher` / UI は変更しない。

```text
AiTabPanel / STT → UserInputEvent → VegaSubsystemGate → ThoughtDispatcher
        ↓
VegaLlmGateway
        ↓
AgyCliProviderAdapter（LlmProviderAdapter）
        ↓
AgyCliTransport（LlmTransport, ProcessBuilder）
        ↓
agy process（stdin / stdout / stderr / exit code）
```

#### 着手前の read-only 確認（実機）

- `agy` の非対話実行方式（引数、stdin 入力可否、出力形式、終了コード）
- VEGA の tool-calling ターンを CLI でどう表現するか。`LlmProviderAdapter.parse` は整形済み tool-call 以外を `INVALID_RESPONSE` にするため、**`agy` に JSON の tool-call 形式で出力させる契約**が成立するかを確認する
- 既存の `CCoreAdapter`（`ProcessBuilder`）のプロセス実行・タイムアウト処理のうち再利用できる部分（ただし C-CORE と AI Provider の責務は統合しない）
- **`agy` のエージェント機能（シェル実行・ファイル操作）を無効化または制限して起動できるか。** できない場合は下記「`agy` の実行境界（安全要件）」に従う

#### 必須仕様

- `agy` の executable path・引数・timeout を設定可能にする。
- Prompt / Game Context を stdin または定義済みの CLI 引数で渡す。
- stdout を応答として受け取り、stderr は診断情報として分離する。
- 非ゼロ終了コード、timeout、プロセス起動失敗、不正出力を共通エラーとして `AiTransportResult` の失敗種別へ変換する。
- `agy` が存在しない・失敗した場合も UI がフリーズせず、LLM を使わない決定的なコマンド経路は継続する。
- Provider 固有仕様を Game Context / C-CORE に漏らさない。
- 既存 API adapter（Gemini / Anthropic / OpenAI など）は削除しないが、AI 会話の既定にはしない。

#### `agy` の実行境界（安全要件、2026-09-15 追加）

`agy`（Antigravity CLI）はエージェント型 CLI であり、単なる HTTP API クライアントとは異なる。
CLI 自身がシェル実行やファイル編集を行う機能を持つ可能性があるため、何も制限せずに起動すると、
DB ファイルへ直接アクセスできる経路が生まれてしまう。**このリスクは §R.12（DB 非汚染ルール）の
「LLM から DB への直接経路」の中で最大のものである。** v2-P3 の実装（着手前確認を含む）は次を満たすこと。

- **`agy` は tool-call JSON を生成する生成器としてのみ使う。** 生成された tool-call の実行（DB 書き込み・
  ゲーム操作）は、既存の `IntelCommand` / `CommandRegistry` 経由の決定的コードが行う。`agy` プロセス自身に
  実行させない。
- **`agy` のエージェント機能（シェル実行・ファイル編集など）は、無効化または制限できることを実機で確認して
  から採用を決める。** 「着手前の read-only 確認」（本節の上）にこの確認項目を追加する。
- **無効化・制限できることが確認できない場合、DB にアクセスできる環境では `agy` を実行しない。** 確認が
  取れるまでは v2-P3 の実装を進めない。
- **`agy` の作業ディレクトリは空の一時ディレクトリにする。** リポジトリのチェックアウトや DB ファイルが
  置かれたディレクトリを作業ディレクトリにしない。
- **DB のパス・接続情報・その他の秘密情報を `agy` に渡さない。** プロンプトや環境変数、CLI 引数のいずれ
  にも含めない。`agy` の出力（stdout/stderr）をそのままファイルへ保存する処理も行わない。

#### 完了条件

- `ProviderEnum` と `VegaLlmGatewayFactory` から `agy` を選択でき、既定になっている。
- 固定 fixture（stdin / stdout / stderr / exit code / timeout / 不正出力 / 未インストール）でテストが成功する。
- 日本語のテキスト指示が `agy` 経由で tool-call に変換され、既存アクションが実行される（v2-P4 の最初の実機確認）。
- AI 会話が HTTP API の直接呼び出しに依存しない。

### R.7 v2-P4 以降

- **v2-P4 日本語 Assistant / 自然言語:** deterministic command は LLM 不在でも利用可能。ゲームの事実は Journal / Status / Session から取得し、LLM が事実を捏造して Game Context を上書きすることは禁止。§17 の実機確認 3 項目は `agy` 経由で実施する。
  例: 「今どこにいる？」「貨物の残量は？」「この惑星で採取できる生物は？」「マップを開いて」「ミッションの目的地に行って」
- **v2-P5 STT / TTS / VOICEVOX:** STT / TTS は Provider として分離。VOICEVOX はローカル実行前提、外部 TTS API を必須依存にしない。VOICEVOX 未起動でもテキスト表示を継続。完了判定は `PHASE11_VOICE_IO_PHASE_UPDATE.md` の実機 E2E（AI CLI Provider は `agy`）。**同梱の `ParakeetSTTImpl` を日本語 STT として扱わない**（`tokens.txt` に日本語語彙が無く、原理的に日本語を認識できないことを確認済み、§10）。日本語音声入力には別の STT バックエンドを新規に選定する（ローカルモデルの差し替え・外部 CLI 方式のいずれも候補として検証する）。STT の出力は常に未検証のユーザー入力として扱い（他言語 STT と同様）、そのままコマンドパラメータや DB へ書き込まない。
- **v2-P6 Game Action / Ship Control:** Action Registry・Input Adapter・キーバインド設定・allowlist・dry-run・実行結果 verification。LLM から直接 OS キーボードイベントを発行することは禁止。
- **v2-P7 HUD / Overlay / VR:** Assistant と同じ Game Context を表示し、CLI と HUD で別々の状態取得を行わない。
- **v2-P8 Generic Game Data / Trade Assistant:** commodity / station / where-to-sell / trade route / cargo / 利益計算。**艤装（outfitting）検索コマンドを新設する**（2026-09-15 判明のギャップ、R.3 表参照）。既存は現在地限定の `query_local_outfitting`（LLM に艤装リストを読ませて回答させる）とモジュール画面を開くだけの `ShowModulesPanelCommand` のみで、`find_commodity` のような「範囲 ly 内からモジュールを検索する」コマンドが無い。「アイテム」が商品なのかモジュール／艤装なのかは、まず**ツール選択（LLM が呼ぶコマンド ID）の段階で区別する**——同じ `find_commodity` に両方を混在させない。新設する艤装検索コマンドの実装は `FindCommodityCommand` と同じ形にする: 名前の照合と検索自体は決定的コード（辞書照合 + 既存 `FuzzySearch`/Spansh 相当）が行い、`agy`/LLM は結果の説明・要約のみを担当する（判定・検索ロジックを LLM に持たせない、R.10 の非目標と一致）。
- **v2-P9 C-CORE 拡張:** 全 species、collection state、expected value、ranking、confidence。判定と価値評価を分離。C-CORE ruleset を EliteIntel 側へコピーせず、LLM は C-CORE 結果を説明・要約するだけとする。
- **v2-P10 Offline Assistant:** ローカル CLI provider（例: Ollama CLI）+ VOICEVOX でネットワーク無しでも可能な範囲で日本語 Assistant を使える構成。
- **v2-P11 Installer / Update / Runtime packaging:** Windows runtime package、CLI launcher、`agy` executable 設定、AI Provider / TTS 設定、optional HUD / VR、バージョン情報、更新機構。

### R.8 AI Provider 契約

```text
AIProvider
 ├─ AgyCliProvider      （既定）
 ├─ GeminiCliProvider   （任意）
 ├─ ClaudeCliProvider   （任意）
 └─ OllamaCliProvider   （v2-P10）
```

Provider の差し替えで Game Context schema / C-CORE / Journal state / HUD renderer / Action Registry を変更してはならない。

- 入力: Game Context + user prompt
- 出力: 日本語 response（tool-calling ターンでは tool-call JSON）
- エラー: process unavailable / non-zero exit / timeout / malformed output（Assistant Core が共通処理）

### R.9 テスト方針

- **v2-P0〜P2:** 既存 build/test、`BundleKeyParityTest`・`BundleQuotingTest`、日本語 locale test、HUD / menu / settings の表示確認、既存機能の回帰
- **v2-P3〜P4:** Provider interface test、CLI process test、stdin/stdout fixture、exit code / timeout / malformed output test、`agy` unavailable fallback、日本語 prompt / response test
- **v2-P5:** STT / TTS provider test、VOICEVOX unavailable fallback、実機 E2E
- **v2-P6:** allowlist / dry-run / verification / 禁止操作 test
- **v2-P7〜P8:** Game Context consistency、HUD rendering、Navigation / Mission / Cargo / Trade fixtures
- **v2-P9〜P10:** BodyContext mapping、C-CORE fixture、SpeciesEvaluation preservation、C-CORE result が AI に改変されないこと、offline provider test

### R.10 非目標

- LLM に Exobiology species 判定をさせない。LLM の回答をゲーム事実の Source of Truth にしない。
- C-CORE ruleset を EliteIntel 側へコピーしない。
- LLM から直接 OS キーボードイベントを発行しない。
- 日本語化・`agy` 対応より先に C-CORE を完成させることを要求しない。
- AI/LLM を特定の外部 API に固定しない。
- HUD / VR の実装を CLI の責務へ混在させない。

### R.11 現在の優先作業

1. **Track J:** v2-P2 の J-1（`gui` 残り 256 キー）からカテゴリ単位で着手する。v2-P0/P1 は完了済みなので再調査しない。
2. **Track G:** v2-P3 の着手前 read-only 確認（`agy` の非対話実行方式と tool-call JSON 契約の成立可否）を実機で行う。
3. C-CORE の追加・拡張（v2-P9）はその後。

### R.12 DB 非汚染ルール（2026-09-15 追加）

`agy`（v2-P3）導入を前に、LLM が生成した値が DB へどう届きうるかを洗い出した。結論: **LLM から DB への
直接の書き込み経路を作らない。** DB へ書き込むのは常に EliteIntel 側の決定的コードであり、LLM/`agy` は
値の生成・説明までしか行わない。

#### 現状確認（コード読み取りのみ、2026-09-15）

- **VEGA の会話メモリはセッション内のみ。** `SessionMemoryGateway` / `RecentMemory` は DB／DAO／JDBC を
  一切参照しない（インメモリのみ）。現状、会話履歴が DB を汚染することはない。この状態を維持する
  （§R.10 の非目標「LLM の回答をゲーム事実の Source of Truth にしない」と一致させる）。
- **既に「決定的コードが辞書照合してから保存」になっている経路がある。** 例: `FindCommodityCommand`
  （検索のみで保存はしない）、`AddMiningTargetCommand`（`FuzzySearch.fuzzyCommodityMatch()` で照合した
  結果だけを `PlayerSession.addMiningTarget()` へ渡す）。LLM が抽出した生の文字列をそのまま保存していない。
- **一方で、LLM が抽出した自由文をそのまま DB へ保存するコマンドが存在する。** 例: `SetReminderCommand`
  / `SetTimedReminderCommand` は `key`（"Extract the reminder text the commander dictates, verbatim."）
  を照合なしで `ReminderManager` 経由で保存する。これは自由文メモという機能の性質上意図的だが、
  結果として LLM が生成した任意の文字列が無検証で DB に届く経路になっている。

#### ルール

1. LLM/`agy` から DB への直接アクセス経路（DB パス・接続情報・ORM/DAO への参照渡し）を作らない。書き込みは
   既存の `*Manager`（`ReminderManager` 等）や DAO など、EliteIntel 側の決定的コードのみが行う（§R.6 の
   `agy` 実行境界と同じ原則）。
2. LLM が抽出した値が「既知のエンティティ名」（素材・商品・モジュール名など）を指す場合、**既知の辞書／
   テーブルと照合してから**でなければ検索・保存に使わない。辞書に無ければ `UNKNOWN` として扱い、検索も
   保存もしない（J-5・R.7 v2-P8 の方針と同じ）。
3. LLM が抽出した値を**照合なしの自由文として**保存するコマンド（リマインダーのように仕様上自由文が必要な
   もの）は、個別に許可リスト（allowlist）で管理する。新しいコマンドを自由文保存にする場合は、この
   許可リストに明示的に追加する（暗黙に許可しない）。
4. 会話メモリ（`SessionMemoryGateway` 系）はセッション限定を維持し、DB へ永続化しない。

#### 完了条件

- 現時点で DB に書き込む全コマンド（`IntelCommand` 実装のうち `*Manager`/DAO を呼ぶもの）を棚卸しし、
  「辞書照合後に保存」「自由文保存（許可リスト対象）」「未分類」のいずれかに分類する。
- 「未分類」が残っている間は許可リストを確定させない。全数監査が完了してから許可リストを固定する。
- 許可リストに載っていない自由文保存コマンドが存在しないことをコードレビューで確認する。
- v2-P3（`agy` 導入）は、このルールと R.6 の実行境界の両方を満たすまで、DB にアクセスできる環境では
  実行しない。

## 1. Scope

Bring the exobiology species-prediction logic already built in `EDpjKinsaku` (C-CORE) into
`EliteIntel`'s live HUD, and — separately — make EliteIntel's existing VEGA AI conversation reachable
by typed text in addition to voice, eventually with a Japanese TTS option (VOICEVOX). These are two
independent tracks that happen to share one repo:

- **Track A (Phases 2–10):** C-CORE integration into the EliteIntel HUD.
- **Track B (Phase 11):** AI conversation input/output surface (text input — done; VOICEVOX — not
  started).

Track B does not block Track A or vice versa.

## 2. Phases

| Phase | Content | Completion criteria |
|---|---|---|
| 0 | Repository / build baseline | Fork exists; builds with `./gradlew :app:compileJava` on a clean checkout |
| 1 | EliteIntel read-only inventory | Existing AI conversation pipeline (input → VEGA → LLM → chat display) documented with real file/class names |
| 2 | Japanese localization | `Language.JA` exists and the P0 priority set (core UI, AI chat, HUD, navigation, announcements, log, setup warnings) is translated |
| 3 | BodyContext Adapter | A component that reads EliteIntel's current system/body/journal state into a shape `EDpjKinsaku` can consume |
| 4 | C-CORE integration boundary | Defined interface between EliteIntel and `EDpjKinsaku`'s prediction engine (in-process call, HTTP, or CLI — undecided) |
| 5 | Aleoida vertical slice | One genus (Aleoida) working end-to-end in a real game session as proof of the boundary |
| 6 | ~~Collection state~~ Aleoida MATCH candidates on the HUD | Reordered in execution (§13): showing a result turned out to be the natural next step after Phase 5's boundary proof, ahead of collection-state tracking. Renumbered here rather than left silently mismatched with what commit `a99127939` actually built, per §0's ground rule |
| 7 | Collection state | Scanned/collected state tracked and reflected in the HUD (the original Phase 6) |
| 8 | All species | Every C-CORE genus wired through the same boundary as Phase 5 |
| 9 | ~~Navigation integration~~ **保留（on hold）** | 既存EliteIntelのNavigation機能で要求されたジャンプ数・距離等は既に実装済み。EDpjKinsaku `DESTINATION_ETA_SPEC`は未実装のため統合対象なし。Supercruise ETAは別途新規機能として扱う（§16） |
| 10 | Exobiology value/ranking | `EDpjKinsaku`'s value/ranking model surfaced as prioritized recommendations |
| 11 | AI conversation surface | Text input to VEGA (done, see below); VOICEVOX as a `TtsProvider` option (not started); C-CORE result injection into AI chat (done, §14); Japanese conversation vertical slice - input/LLM-response-language/TTS fallback (done, §17, pending real-machine confirmation) |
| 12 | C-CORE distribution | Not in the original plan - added once Phases 4-7 exposed that they all assumed a system Python EliteIntel's actual commanders do not have. Executed and referred to throughout as "Phase 8" in commits/docs (§15); numbered 12 here only to avoid re-colliding with table row 8 ("All species"), which §15 does not touch |

## 3. Phase 4 decisions (provisional, 2026-09-12)

Direction set for the four open questions in §7, not yet implemented:

1. **Transport:** ~~subprocess + stdio JSON (tentative first choice)~~ **Done on both sides**,
   `EDpjKinsaku` commit `3e87288` (`edpj bio evaluate` reads `{"genus", "body"}` JSON on stdin, writes
   the aggregated `RuleEvaluation` list as JSON on stdout) and EliteIntel commit `890531a1a`
   (`elite.intel.bio.ccore.CCoreAdapter`, a one-shot `ProcessBuilder` call — see §11). Not wired into
   `ScanEventSubscriber`/the HUD yet; that is Phase 5.
2. **Genus dispatch:** ~~add `evaluate_genus(name, body)` on the `EDpjKinsaku` side~~ **Done**, same
   commit: `evaluate_genus(name, body)` in `app/bio/c_core.py`, dispatching by lowercased genus name to
   the six converted evaluators, raising `KeyError` (not a silent empty list) for the other 13.
3. **`gravity` unit/scale parity:** **Investigated, no conversion needed** — see §9. `LocationDto.gravity`
   (Earth-g, EliteIntel's own recomputed value) and C-CORE's `BodyContext.gravity`/`min_gravity`/
   `max_gravity` (also Earth-g) are the same unit and scale. The raw journal `SurfaceGravity` (m/s²)
   must never be the value passed — which EliteIntel's existing code already avoids, for a different
   documented reason (accuracy, not units). A single live-game spot-check during the Phase 5 Aleoida
   slice is still worth doing as final confirmation, but nothing here blocks starting Phase 4 design.
4. **`pressure`:** ~~wire `ScanEvent.getSurfacePressure()` into `LocationDto` in Phase 3.~~ **Done**,
   commit `4e0882259`: added `LocationDto.surfacePressure` (mirroring the existing zero-guard pattern
   used by `surfaceTemperature`/`gravity`) and one assignment in
   `ScanEventSubscriber.onScanEvent()`. Verified via `:app:compileJava`/`:app:compileTestJava` and 24
   passing tests across the new `LocationDtoPressureTest` plus five pre-existing `LocationDto`/`ScanEvent`
   test classes (no effect on other consumers).

## 4. Phase 2 audit (read-only, 2026-09-12)

Scope for this pass, by explicit decision: gap audit only, not a translation effort. Findings:

- `elite.intel.i18n.Language` has no `JA` constant (`EN, RU, UK, DE, FR, ES, PT, PTBZ, IT`).
- No `gui_ja.properties` (or any `*_ja.properties`) exists anywhere under
  `app/src/main/resources/i18n/` — every other supported language has one.
- `Language.isGameLocalized()` would correctly exclude `JA` if added: Elite Dangerous ships no
  Japanese client, so Japanese would be treated like Italian/Ukrainian today (English client, English
  in-game noun names, translated app UI).
- Material name aliasing spans "all nine" languages via dedicated DB columns
  (`db-migration/01003__schema.sql`, `01017__schema.sql`, read by `MaterialNameDao`) — a tenth
  (`name_ja`) would be needed for Japanese voice input to match material names the way it does for the
  other nine.

Full localization (translating `gui.properties` and the other per-language resource families such as
`ed_events_*`/`ai_action_aliases_*`, plus the DB alias column) is deliberately **not** undertaken now.
It is a separate, large future task, tracked here rather than started.

**Update (2026-09-12):** the audit above was superseded by real implementation, not just planning.
`Language.JA` now exists (commit `11907a780`), all 14 `switch(language)` call sites this required are
handled (see §10), and the P0 priority set — core UI labels, Vega/AI chat tab, HUD quick-status badges,
navigation HUD card labels, announcements, log messages, setup/first-run warnings, about 140 keys — is
translated in `gui_ja.properties`. The remaining ~640 `gui.properties` keys and the three other bundle
families (`responses`/`ed_events`/`ai_action_aliases`, 831 keys total) are still not translated;
`_ja.properties` stub files exist for them only so `BundleKeyParityTest`/`BundleQuotingTest` have a
file to load, and every resulting gap is declared honestly in
`app/src/test/resources/i18n-parity-baseline.txt` rather than silently missing. The `name_ja` material
DB column is still not added.

## 5. Verified status (as of 2026-09-12)

Only entries backed by an actual command run or a real file are listed as done. Everything else is
"not started," regardless of what any prior conversation said.

| Phase | Status | Evidence |
|---|---|---|
| 0 | Done | `sugr332-cloud/EliteIntel` fork exists; `./gradlew :app:compileJava` → BUILD SUCCESSFUL (JDK 21.0.12 Temurin) |
| 1 | Done | AI input pipeline traced: `UserInputEvent` (`app/src/main/java/elite/intel/gameapi/UserInputEvent.java`) → `VegaSubsystemGate.onUserInput()` → `ThoughtDispatcher` → LLM → `AiResponseLogEvent` → `AiTabController` → `AiTabPanel` |
| 2 | Audited, not implemented | See §4 |
| 3 | Partially started | `pressure` field wired (commit `4e0882259`); no `BodyContext` adapter class exists yet — that is the rest of Phase 3 |
| 4 | Adapter done on both sides | `edpj bio evaluate` (EDpjKinsaku `3e87288`) + `CCoreAdapter` (EliteIntel `890531a1a`, see §11) |
| 5 | Aleoida vertical slice done | `SAASignalsFoundSubscriber` → `CCoreAdapter` → `LocationDto.speciesEvaluations` (EliteIntel `ae4270496`, see §12), scoped to Aleoida only |
| 6 | Aleoida MATCH candidates on the HUD | `ExobiologyObjectiveSource` extended, not a new card (EliteIntel `a99127939`, see §13); NO_MATCH/INSUFFICIENT_DATA intentionally not shown |
| 7–10 | Not started | No collection-state tracking, no other genus wired, no navigation/value-ranking integration |
| 11 (C-CORE → AI chat) | Not started | No C-CORE result has been injected into a VEGA prompt/response yet |
| 11 (text input) | Done | See §6 below |
| 11 (VOICEVOX) | Not started | `TtsProvider` enum only has `KOKORO` / `GOOGLE` / `EDGE` |

`EDpjKinsaku` C-CORE progress (verified via `git log`, branch `bio-c-core-validation`, last relevant
commit `3e87288`, 2026-09-12): 6 of 19 genera converted (31 species / 65 rulesets), `bioscan-count`
20/116/254/0 PASS, `pytest` 707 passed (699 baseline + 8 for `evaluate_genus`/the new CLI). The CLI
boundary (`edpj bio evaluate`) now exists and is independently verified; nothing in EliteIntel calls
it yet.

## 6. What "Phase 11 text input" actually is

This was investigative verification, not a phase deliverable in the sense of the table above. The
question being answered was: *does EliteIntel's existing voice-only AI conversation also work over a
generic text event, without changing VEGA/LLM/TTS?* It does. Concretely:

- Added a `HudTextField` + send button to `AiTabPanel.buildChatInputRow()`
  (`app/src/main/java/elite/intel/ui/screen/AiTabPanel.java`), gated on the same
  `applyServiceState(running)` as other controls.
- Submitting publishes `GameEventBus.publish(new UserInputEvent(text))` — the exact call
  `DiagnosticsInputTailer.feedPhrase()` already used for file-driven test input.
- No changes to `VegaSubsystemGate`, `ThoughtDispatcher`, `AiTabController`, STT, or TTS.
- Verified: `:app:compileJava` succeeds; `VegaSubsystemGateTest` (2/2) and `ThoughtDispatcherTest`
  (20/20) pass; a real launch reached `buildUi()` with no exception; a real commander session
  confirmed the typed input reached VEGA's LLM gateway (it failed at LM Studio connectivity, which
  is a local environment issue unrelated to this change — the pipeline itself was reached).

Committed as its own commit (`875e3f597`) describing exactly this scope — not folded into a
"Phase 11 complete" claim, since VOICEVOX and the C-CORE-context-injection idea discussed in
conversation are still undesigned.

LM Studio connectivity (the local LLM the pipeline reached and failed to call past) is out of scope
going forward: it served only to confirm the existing conversation path is reachable over text, not
as an AI provider this project intends to build on.

## 7. Phase 4 investigation (read-only, 2026-09-12)

Both sides of the boundary already have more structure than assumed. No code was changed to produce
this section — only reading.

### EliteIntel side: current body/system state already available

- `PlayerSession.getInstance().getLocationData()` → `LocationData<Long,Long>` with
  `getSystemAddress()` / `getInGameId()` — "where the commander is right now."
- `LocationManager.getInstance().getLocation(starSystem, bodyId)` → `LocationDto`
  (`app/src/main/java/elite/intel/gameapi/journal/events/dto/LocationDto.java`), the per-body cache
  written by `ScanEventSubscriber.onScanEvent()` on every `Scan` journal event.
- Field mapping from `LocationDto` to C-CORE's `BodyContext` (see below):

  | `BodyContext` field | EliteIntel source | Note |
  |---|---|---|
  | `atmosphere` | `LocationDto.atmosphere` | set from `ScanEvent.getAtmosphereType()`, not `getAtmosphere()` |
  | `body_type` | `LocationDto.planetClass` | set from `ScanEvent.getPlanetClass()` |
  | `volcanism` | `LocationDto.volcanism` | raw `ScanEvent.getVolcanism()` string |
  | `temperature` | `LocationDto.surfaceTemperature` | |
  | `gravity` | `LocationDto.gravity` | **not** the journal's raw `SurfaceGravity` — recomputed via `GravityCalculator.calculateSurfaceGravity(massEM, radius)` because `ScanEventSubscriber.java:113-115` documents the raw journal value as inaccurate. Unit/scale parity with what the C-CORE rulesets were authored against is unverified — needs a real-data check in Phase 5, not an assumption. |
  | `pressure` | **missing** | `ScanEvent.getSurfacePressure()` exists but is never copied into `LocationDto`. Closing this is a Phase 3 task (one field + one assignment in `ScanEventSubscriber`). Until then every pressure-gated rule can only return `INSUFFICIENT_DATA`. |
  | `regions` | not tracked | leave `None` |

- Genus detection already exists, independent of C-CORE: `LocationDto.genus` (`List<GenusDto>`),
  populated from DSS/SAA signals. It already surfaces today as a HUD objective card
  (`ui/overlay/ExobiologyObjectiveSource.java`) showing sample progress (X/3) and genus-level payout —
  but with no species-level prediction. This is the exact gap C-CORE fills, and the natural place to
  plug an evaluation result in once it exists.

### EDpjKinsaku side: the public interface already exists

`app/bio/c_core.py` already defines the contract this plan was describing conceptually:

```python
@dataclass(frozen=True)
class BodyContext:
    atmosphere: str | None
    gravity: float | None
    temperature: float | None
    pressure: float | None
    body_type: str | None
    volcanism: str | None
    regions: frozenset[str] | None = None

@dataclass(frozen=True)
class RuleEvaluation:
    species_code: str
    species_name: str
    status: RuleStatus  # MATCH / NO_MATCH / INSUFFICIENT_DATA / RULE_DEFINITION_ERROR / RULESET_INCONSISTENCY
    reason: str
```

One function per converted genus — `evaluate_aleoida(body: BodyContext) -> list[RuleEvaluation]`,
plus `evaluate_cactoida` / `evaluate_concha` / `evaluate_fonticulua` / `evaluate_frutexa` /
`evaluate_fumerola` for the other 5 done so far — with `aggregate_species_evaluations()` collapsing
multi-ruleset ORs into one verdict per species. There is no genus-name dispatcher yet
(`evaluate_genus(name, body)`); an adapter needs either its own lookup table or a corresponding small
addition on the EDpjKinsaku side.

`app/bio/body_context.py::body_context_from_parameters()` is a second, independent confirmation of
the field mapping above: it adapts EDpjKinsaku's own cached EDSM data into the same `BodyContext`,
and treats `atmosphere_type` (not `atmosphere`) as the `atmosphere` input — matching the EliteIntel
mapping.

These four questions are the ones resolved (three provisionally, one still genuinely open) in §3.

## 8. Division of responsibility with EDpjKinsaku

`EDpjKinsaku` (C-CORE) owns species/ruleset prediction logic and stays genus-by-genus, independently
testable via `pytest` and `bioscan-count`, with no dependency on EliteIntel. `EliteIntel` owns game
state capture, the HUD/UI, and the AI conversation surface. Phase 4 (§2) is where a boundary between
them gets defined — until then, no EliteIntel code should reach into `EDpjKinsaku` internals or vice
versa.

## 9. Gravity parity investigation (read-only, 2026-09-12)

No code changed to produce this section. Question: can `LocationDto.gravity` be passed straight into
C-CORE's `BodyContext.gravity` (and, through it, `NormalizedRule.min_gravity`/`max_gravity`), or does
it need converting first?

### The two candidate values, and why they differ

| Source | Value | Unit |
|---|---|---|
| Journal `Scan.SurfaceGravity` | e.g. `9.81` in `ScanEventTest`'s fixture | **m/s²** (Frontier's journal convention) |
| `LocationDto.gravity` | computed by `GravityCalculator.calculateSurfaceGravity(massEM, radiusMetres)` | **Earth gravities (g)** — Earth itself = `1.0` |

`ScanEventSubscriber` already discards the raw journal value and stores only the computed one
(`ScanEventSubscriber.java:113-115`: "DO NOT use event.getSurfaceGravity() as it is not accurate").
That comment is about accuracy, not units, but it has the side effect of already avoiding the
unit mismatch this investigation was checking for.

### What C-CORE expects

`app/bio/c_core.py` defines `min_gravity`/`max_gravity` on every genus converted so far (63 bounded
rules across the 6 genera). Every single bound in the file, across every genus, falls in **0.04 to
0.65**:

```
grep -oE 'm(in|ax)_gravity=[0-9.]+' app/bio/c_core.py | sort -t= -k2 -n | (head -3; echo ...; tail -3)
```

Values in that range cannot be m/s² (a body at 0.04-0.65 m/s² would be barely more massive than a
speck of dust) but are exactly the range real Elite Dangerous exobiology occupies in Earth-g (organics
only spawn on low-gravity bodies). `app/bio/body_context.py::body_context_from_parameters()` passes
EDSM's own cached `gravity` column straight through with **no conversion** into `BodyContext.gravity`,
which only makes sense if EDSM's `gravity` field is already Earth-g — matching EliteIntel's own EDSM
DTO (`elite.intel.gameapi.search.edsm.dto.data.BodyData.gravity`), which the codebase treats as
directly comparable to `LocationDto.gravity` (both consumed as Earth-g elsewhere in EliteIntel).

### Anchor data points (existing, real, already trusted by each codebase's own tests)

| Body | Mass / Radius | `LocationDto.gravity` (computed) | Plausible for C-CORE's 0.04-0.65 range? |
|---|---|---|---|
| Earth (exact, sanity check) | 1.0 EM / 6,371,000 m | `1.00` g | No — real Earth has no organics rule to satisfy, correctly outside range |
| Colonia 4 (real, named class III gas giant — `GravityCalculatorTest`) | — | `199.31` g | No — gas giants correctly fall far outside every organics range |
| Synthetic rocky body (`GravityCalculatorTest`, EDSM-style input) | 0.513865 EM / 4,977,078.5 m | `0.84` g | No — above every genus's `max_gravity` (highest is 0.65), i.e. correctly excluded as "too heavy for any organics" |

No currently-available fixture happens to land inside 0.04-0.65 g, so this pass could not show a
`MATCH`-eligible body end-to-end — but that is a fixture-coverage gap, not evidence against parity: all
three anchors behave exactly as physically expected once interpreted as Earth-g, and none would make
sense interpreted as m/s².

### Conclusion

**そのまま渡せる (pass as-is)** — `LocationDto.gravity` and C-CORE's gravity fields are the same unit
and scale. No conversion function is needed in the future `BodyContext` adapter for this field.
Recommended, not required: capture one live `Scan` event from a body already known (via EDSM or
BioScan) to sit inside a genus's gravity range, as a final end-to-end confirmation during the Phase 5
Aleoida slice — this pass used existing anchors rather than a fresh live capture.

## 10. Language.JA implementation (2026-09-12)

`Language` is referenced by 14 `switch(language)` call sites across the codebase (found by re-grepping
every switch on the enum, not just ones using a parameter literally named `language` — three used
`lang`, one switched on `SystemSession.getInstance().getLanguage()` inline). Java requires an
exhaustive switch over an enum to handle every constant, so all 14 needed a `case JA` before the code
would compile. Eleven are locale/text-lookup plumbing (`MultiLingualTextProvider`, `EventsTextProvider`,
`ResponseTextProvider`, `AiActionAliasTextProvider`, `StringUtls`, `PhraseCorrectionSuggestionDialog`,
`NumberWords`, `LocalizedNumbers`, plus the language-name/locale switches) — mechanical, no functional
compromise. Three needed a real decision:

- **`ParakeetSTTImpl.toLangCode()`:** the bundled STT model's vocabulary
  (`distribution/parakeet/tokens.txt`) contains no Japanese characters at all — checked directly, not
  assumed. Japanese speech cannot be transcribed by this model regardless of what language hint is
  passed. Returns `"en"` (documented as "the least-wrong of the codes this method already returns," not
  a working substitute) — voice input stays unavailable for Japanese until a Japanese-capable STT model
  is bundled, which is out of scope here.
- **`InputNormalizerLocalizations` / `AiActionLocalizations`:** since STT produces no real Japanese
  text, both route `JA` through the English rules/aliases rather than building unused Japanese
  voice-command infrastructure for a language STT cannot reach. `FighterAttackTargetPhrasingTest` (a
  safety-critical test guarding the one fighter order that cannot be taken back) needed a matching `JA`
  entry using the same English stems for this reason.
- **`GoogleVoiceProvider` / `EdgeVoiceProvider`:** unlike STT, both cloud TTS providers genuinely
  support Japanese today, so this is completing existing provider capability, not building VOICEVOX.
  Real voice codes: Google `ja-JP` / `ja-JP-Standard-A` (female) / `ja-JP-Standard-C` (male); Edge
  `ja-JP` / `ja-JP-NanamiNeural` (female) / `ja-JP-KeitaNeural` (male). Not added to
  `CHIRP3_HD_LANGUAGES` — that roster's Japanese coverage was not verified against the live API, so
  Japanese stays on the same guaranteed-to-exist Standard tier `pt-PT` uses.

**Translated (P0, commit `11907a780`):** `gui_ja.properties` — core UI labels (tab/button/language
names), the Vega/AI chat tab, HUD quick-status badges, navigation HUD card labels, announcement
toggles, system log messages, and the setup/first-run warnings. About 140 keys. Confirmed in a real
game session by the commander: tabs/buttons/surface UI read correctly in Japanese.

**Translated (P1, commit `5c9716ec2`, 2026-09-12):** the bindings tab, the actions tab (built-in +
custom commands), the input monitor tab, the remaining overlay HUD cards, and the jukebox tab. About
407 more keys.

**Not translated (tracked, not hidden):** the remaining `gui.properties` keys not covered by P0/P1
(mostly `settings.*`/`speech.*`/`player.*`/`automation.*`/`overlay.settings.*`), and the
`responses`/`ed_events`/`ai_action_aliases` bundle families (831 keys — mission/rank/event narration
text and voice-command aliases). `BundleKeyParityTest` requires every declared `Language` to have a
bundle file for every family (a missing file fails the test outright, not just a gap), so minimal stub
`_ja.properties` files exist for the three untranslated families purely to satisfy that. Every gap this
produces is declared line-by-line in `app/src/test/resources/i18n-parity-baseline.txt`, per that file's
own existing mechanism (previously used for exactly one deliberate exclusion; now also carries this
dated, explained backlog — down to ~1,111 lines after P1, from 1,485 after P0).

**Fixed in passing:** `ai.chatInput.send` and `language.japanese` were missing from all 8 other
translated languages (`BundleKeyParityTest` caught this too) — `ai.chatInput.send` dates back to the
Phase 11 text-input commit `875e3f597`. Both are now translated in all 8, not baselined.

**`DisplayNumeralsTest` exemption:** its number-spellout round-trip test is skipped for `JA`. It finds
a spelled figure by scanning for space-delimited alphabetic word boundaries; Japanese text has no
spaces between words, so the scan cannot locate a spelled figure even though ICU spells it correctly
in isolation (confirmed: `NumberWords.of(100, Language.JA)` correctly produces "百"). Revisit once
Japanese TTS narration is a real path (VOICEVOX, Phase 11) rather than an unused one.

**Verified:** `:app:compileJava`/`:app:compileTestJava` succeed; full suite 3107 tests, 9 failures, all
confirmed pre-existing via `git stash` (identical failures with this change removed) and unrelated
(`JukeboxPlayerTest`/`TagScannerTest`, environment-specific audio file issues); a real launch (a second
instance alongside one already running, only its own new PID touched) reached full startup —
`SetupCheck`/`KeyBindCheck`/`DeviceService` all completing — with no new exception.

## 11. C-CORE Adapter (2026-09-12)

Read-only investigation into how EliteIntel's runtime can actually launch `edpj bio evaluate`, followed
by the adapter itself (EliteIntel commit `890531a1a`) — scoped to the adapter alone, not wired into
`ScanEventSubscriber` or the HUD (that is Phase 5).

### Investigation findings

- **`edpj.exe` (the pip-installed console script) is not reliably invokable.** `pip show edpj` reports
  it editable-installed from this checkout, but the generated `edpj.exe` lives in
  `AppData\Roaming\Python\Python311\Scripts\`, a directory that is **not on PATH** (PATH only carries
  `AppData\Local\Programs\Python\Python311\Scripts`). `where edpj` fails; confirmed, not assumed.
- **`python -m app.cli bio evaluate` works from any working directory** — tested from inside the
  EliteIntel checkout itself, nowhere near EDpjKinsaku. Because `edpj` is pip-installed in *editable*
  mode, `app.cli` is importable via site-packages regardless of CWD. This means the adapter needs no
  knowledge of where EDpjKinsaku lives on disk — only a working Python interpreter.
- **Cold latency measured at ~450-480ms** across three runs, dominated by Python startup plus
  `app/cli/__main__.py` eagerly importing every subcommand module (`api`, `backfill`, `calibration`,
  `collector`, `state`, `bio`) even though only `bio` is used. Sets the floor for timeout design; not
  optimized in this pass.
- **EliteIntel's existing subprocess conventions** (`Updater.java`, `NativeHudOverlay.java`): explicit
  UTF-8 on all streams, stderr separated via `redirectError(Redirect.PIPE)`, bounded
  `process.waitFor(timeout, TimeUnit)`, `process.destroy()`/`destroyForcibly()` as the backstop. Both
  existing uses are fire-and-forget or persistent line-protocol children; neither is the one-shot
  write-stdin-then-read-stdout-then-wait shape this adapter needed, so `CCoreAdapter` follows the
  conventions but not the exact call shape of either.
- **Existing path-configuration pattern**: `PlayerSession.setJournalPath()`/`getJournalPath()` via
  `DirectorySetting` + a DB-backed DAO, "never throws, falls back to a default" read semantics. Noted as
  the template for a future Python-path setting, deliberately not built yet (see decisions below).

### Decisions

- **Python path:** stays a hardcoded constant (`CCoreAdapter.PYTHON_COMMAND = "python"`, resolved via
  PATH), not a Settings/DB-backed value. Adding that is deferred until the adapter itself is proven, to
  keep this change scoped to the CLI boundary alone — it can be added later following the
  `PlayerSession` pattern above with no change to the adapter's public shape.
- **Scope:** the adapter only — `BodyContext`/`RuleEvaluation`/`RuleStatus` (field-for-field mirrors of
  EDpjKinsaku's dataclasses, including JSON field names via `@SerializedName`) and
  `CCoreAdapter.evaluate(genus, body)`. No call site in the real scan pipeline yet.

### Implementation (`elite.intel.bio.ccore`)

`CCoreAdapter.evaluate(String genus, BodyContext body)`: serializes the request with the shared
`GsonFactory` Gson instance, starts `python -m app.cli bio evaluate` via `ProcessBuilder`, writes the
request to stdin, drains stdout/stderr concurrently on separate threads (`StreamCollector` — necessary
because reading either stream sequentially risks a deadlock if the child fills that pipe's OS buffer
first), waits up to 5 seconds, and returns the parsed `List<RuleEvaluation>`. Throws
`CCoreAdapterException` (unchecked, deliberately silent on how a caller should present a failure — this
class has no caller yet to design that for) on: process failed to start, timeout, non-zero exit
(including EDpjKinsaku's `KeyError` for a genus not yet converted), or unparseable stdout.

**Verified:** `:app:compileJava`/`:app:compileTestJava` succeed. `CCoreAdapterJsonTest` (5 tests, no
process involved — request field names, null-field omission, response parsing, malformed-JSON and
empty-body error paths) and `CCoreAdapterIntegrationTest` (4 tests, the real subprocess — Aleoida Arcus'
boundary body comes back `MATCH`, genus matching is case-insensitive, an empty body comes back
`INSUFFICIENT_DATA` for all four Fumerola species, an unconverted genus like `"Tussock"` fails with a
clean error naming the genus rather than an empty list) all pass. Full suite: 3116 tests, 9 failures,
all the same pre-existing `JukeboxPlayerTest`/`TagScannerTest` failures already confirmed unrelated.

## 12. Phase 5: Aleoida vertical slice wired (2026-09-12)

The first real call from the scan pipeline into `CCoreAdapter` (EliteIntel commit `ae4270496`).
Deliberately narrow: one genus, one new storage field, no HUD/AI consumer yet.

### Where and how

- **Trigger:** `SAASignalsFoundSubscriber.onSAASignalsFound()`, immediately after
  `location.setGenus(...)` inside the per-body `locationManager.updateBody(...)` lock. By the time a
  `SAASignalsFound` (DSS) event arrives, an earlier `Scan` event has normally already populated the
  body's physical fields via `ScanEventSubscriber` — this is why genus arrival, not the scan itself, is
  the natural trigger.
- **Genus name mismatch found and bridged:** `GenusDto.genusSymbol` holds Frontier's journal stem
  (`"Aleoids"`), not the English display name C-CORE's `evaluate_genus()` dispatches on (`"Aleoida"`) —
  confirmed by reading `BioForms.java`'s own `genus("Aleoids", "Aleoida", ...)` registration, not
  assumed. Bridged with the already-existing `BioForms.englishGenusName()`, adding no new lookup table.
- **Scope guard:** `SAASignalsFoundSubscriber.CCORE_GENUS_SLICE = "Aleoida"` — only a body whose
  detected genuses include Aleoida (via the bridge above) ever reaches `CCoreAdapter`. C-CORE has six
  genera converted; this phase proves the connection with one before widening it (Phase 7).
- **Storage:** no existing field held anything like this — checked `LocationDto`, `GenusDto`, and
  `BioSampleDto` directly rather than assuming one existed. `BioSampleDto` is what has actually been
  scanned, a different concept from a body-conditions candidate list, so reusing it would have
  conflated the two. Added `LocationDto.speciesEvaluations` (`List<RuleEvaluation>`), mirroring the
  Phase 3 `surfacePressure` pattern — one new field, no new subsystem.
- **Failure handling:** `CCoreAdapterException` is caught inside
  `evaluateCCoreSliceIfPresent()` and logged, never propagated — the signal/announcement processing
  around this call must keep working whether or not C-CORE (or Python) is available on the machine.

### Not done in this phase (by design)

HUD display, AI/chat consumption, VOICEVOX, cross-checking against completed `BioSampleDto` samples,
and widening past Aleoida to the other five converted genera are all left for later phases.

**Verified:** `:app:compileJava`/`:app:compileTestJava` succeed. `SAASignalsFoundCCoreSliceTest` (2
tests) passes via `subscriberTest` — the dedicated Gradle task for this package
(`elite.intel.junit.gameapi.journal.subscribers.*`), since the default `test` task excludes it for
timing reasons (an existing, unrelated project convention, not something this change introduced):
Aleoida Arcus' exact boundary body comes back `MATCH` end-to-end through the real CLI and lands on
`LocationDto.speciesEvaluations`; a Tussock-only body never triggers a C-CORE call at all. Default
`test` task: 3116 tests, 9 failures, all the same pre-existing `JukeboxPlayerTest`/`TagScannerTest`
failures already confirmed unrelated.

## 13. Phase 6: Aleoida MATCH candidates on the HUD (2026-09-13)

First surfacing of a C-CORE result to the commander (EliteIntel commit `a99127939`). Renumbered ahead
of the original Phase 6 ("Collection state") — see §2 — because showing a result was the natural next
step once Phase 5 proved the boundary, not because collection-state tracking stopped mattering.

### Design

- **Extends the existing card, does not add a new one.** `HudObjectiveSource` implementations compete
  for one shared display slot (`HudObjective.priority`); `ExobiologyObjectiveSource` already owns the
  exobiology slot (genus list + sample progress, `PRIORITY_AMBIENT`). A second exobiology-flavoured card
  would only have fought that one for the same slot, so this phase extends `card()` instead.
- **Aleoida only**, matching Phase 5's slice. A body's genus row gets extra rows — one per species in
  `LocationDto.speciesEvaluations` with `status == MATCH` — only when that genus resolves (via the same
  `BioForms.englishGenusName()` bridge Phase 5 uses) to `"Aleoida"`.
- **Only `MATCH` reaches the HUD.** `NO_MATCH` and `INSUFFICIENT_DATA` are C-CORE's internal reasoning,
  not something the commander asked to see; they stay on `LocationDto.speciesEvaluations` for any later
  consumer (e.g. Phase 11's AI chat injection) but are filtered out of the card by `matchedSpecies()`.
- **A checkmark, not translated text.** The match row's value is a bare `✓`, not an i18n key — `MATCH`
  is C-CORE's vocabulary, not HUD copy, and a symbol needs no translation in any language.
- **Zero matches ⇒ unchanged card.** A body with no `MATCH` species (including one with no
  `speciesEvaluations` at all, e.g. before Phase 5 ran or when C-CORE was unreachable) renders exactly
  as it did before this phase — the existing genus/sample-progress row only.
- **Row budget is now a shared running total**, not a fixed per-genus slice: the existing six-row card
  budget (`MAX_GENUS_ROWS`) is spent across genus rows and species-match rows together, since Aleoida
  alone can contribute up to five extra rows. The existing "+N more" overflow row already existed for
  when genuses do not fit; it now also fires when match rows crowd out later genuses.

### Not done in this phase (by design)

Widening past Aleoida, showing `NO_MATCH`/`INSUFFICIENT_DATA` anywhere, AI/chat consumption, VOICEVOX,
and collection-state tracking (the original Phase 6/now Phase 7) are all left for later phases.

**Verified:** `:app:compileJava`/`:app:compileTestJava` succeed. `ExobiologyObjectiveCardTest`: 16
tests (12 existing + 4 new — match rows appear in order immediately after the genus row; zero matches
leaves the card byte-for-byte unchanged; species evaluations on the body but for a non-sliced genus
never leak into that genus's row; Aleoida's five matches plus two more genuses correctly overflow
rather than exceed the row budget), all pass. Default `test` task: 3120 tests, 9 failures, all the same
pre-existing `JukeboxPlayerTest`/`TagScannerTest` failures already confirmed unrelated. `subscriberTest`
(Phase 5's async wiring): unaffected, still passing.

## 14. Phase 7: C-CORE exobiology fact source for AI chat (2026-09-13)

First consumption of a C-CORE result by VEGA's AI chat, via the existing `MemoryFactSource` extension
mechanism (read-only investigation confirmed this mechanism already exists and is auto-wired — no new
architecture was introduced). Reverses this plan's earlier "HUD next" direction (§2's original ordering
of Phase 6 before Phase 11): once the investigation showed AI chat already has a formal fact-injection
path, wiring C-CORE into it was the smaller, more natural next step than further HUD work.

### Design

- **New file:** `elite.intel.ai.brain.vega.memory.facts.sources.ExobiologyCandidateFactSource`,
  `@RegisterMemoryFactSource`-annotated exactly like every other fact source — no registry/dispatcher
  changes needed, auto-discovery picks it up.
- **Reads only already-persisted state.** `factsFor()` reads `LocationDto.speciesEvaluations` — the
  field Phase 5's `SAASignalsFoundSubscriber` already populates by calling `CCoreAdapter` at scan time.
  This source **never** constructs a `CCoreAdapter` or shells out to the C-CORE CLI itself; the chat path
  and the scan-time evaluation path stay fully decoupled, matching the explicit constraint that a chat
  turn must not trigger a fresh C-CORE process call.
- **`MATCH` only**, same filter `ExobiologyObjectiveSource` (Phase 6) already applies to the HUD — both
  are independent readers of the same `LocationDto` field, neither depends on the other.
- **Relevance reuses existing alias vocabulary.** `isRelevant()` calls the shared
  `LocalizedFactRelevance.matches()` helper with the existing `query_exobiology_samples` and
  `query_biome_analysis` keys from `ai_action_aliases.properties` (already covering phrases like "what
  organisms are on this planet" / "what life is here" in every supported language) — no new alias
  vocabulary invented.
- **Situational gate matches `CurrentBodyFactSource`'s `AT_BODY` set exactly** (ship landed/gliding/in
  orbit/in a ring, in an SRV, or on foot on a planet), checked before the `LocationManager` read so a
  not-at-body turn never touches the DB.
- **Genus-agnostic by design.** The fact source has no genus knowledge of its own — it reports whatever
  C-CORE already decided and stored, whichever genus that happens to be (Aleoida only, for now, per
  Phase 5's scope). Widening C-CORE's genus coverage (Phase 8) widens this fact source automatically,
  with no changes needed here.

### Not done in this phase (by design)

Re-invoking the C-CORE CLI from the chat path, changing `CCoreAdapter`/`SAASignalsFoundSubscriber`/the
HUD/the CLI, and inventing new relevance vocabulary were all explicitly out of scope and not touched.

**Verified:** `:app:compileJava`/`:app:compileTestJava` succeed. New `ExobiologyCandidateFactSourceTest`
(9 tests: single match, multiple matches preserving order, `NO_MATCH`-only ⇒ empty, `INSUFFICIENT_DATA`-
only ⇒ empty, empty list ⇒ empty, null list ⇒ empty, relevant for an exobiology-samples-style query,
relevant for a biome-analysis-style query, not relevant for an unrelated query) all pass. Default `test`
task: 3129 tests, 9 failures, the same pre-existing `JukeboxPlayerTest`/`TagScannerTest` failures already
confirmed unrelated — no regression from this change.

Example fact line this source contributes to VEGA's `<facts>` block when Aleoida Arcus and Aleoida
Gravis both evaluate to `MATCH` on the current body:

```
C-CORE exobiology candidates: Aleoida Arcus, Aleoida Gravis
```

## 15. Phase 8: C-CORE distribution (2026-09-13)

Phase 4-7 all assumed a working `python -m app.cli bio evaluate` on the machine running EliteIntel.
That is true on the dev machine this was built on (system Python 3.11 with EDpjKinsaku pip-installed
in editable mode) but not on a commander's machine, which has neither Python nor EDpjKinsaku. Phase 8
closes that gap: **8-A** investigated and measured distribution options; **8-B** implemented the
chosen one.

### 8-A: investigation and real-machine PyInstaller build (read-only, no commits)

- **`app/bio/c_core.py` (the actual rule engine) has zero third-party dependencies** - only
  `collections.abc`/`dataclasses`/`enum`. All rulesets are Python code in that one file; there are no
  external ruleset/resource files to bundle. `app/cli/bio.py` (the CLI command) adds only `json`/`sys`/
  `typer`, with no file I/O beyond stdin and no `DATABASE_URL` dependency.
- **The heavy dependencies (sqlalchemy/psycopg/alembic/fastapi/uvicorn/pyzmq) come entirely from
  `app/cli/__main__.py` eagerly importing every sibling CLI submodule** (`backfill`/`collector`/
  `calibration`/`state`), none of which `bio evaluate` itself touches.
- Built and ran real PyInstaller 6.22.2 executables on Windows for both the full CLI (**A**) and a
  narrow `bio_app`-only entry point (**B**):

  | | A: full `app.cli` | B: `bio_app` only |
  |---|---|---|
  | `--onefile` startup (5-run avg) | ~1,300ms | not built (rejected on A's result alone) |
  | `--onedir` startup (5-run avg) | ~460ms | **~125ms** |
  | `--onedir` bundle size | 44MB | **21MB** |
  | sqlalchemy/psycopg/fastapi/uvicorn present | yes | **no** (confirmed absent from `_internal/`) |
  | Aleoida Arcus boundary fixture result | MATCH (correct) | MATCH (correct, identical to A) |

  **`--onefile` is unusable**: its per-run self-extraction cost (~1.3s) eats too much of
  `CCoreAdapter`'s 5s timeout, on every single scan event. **`--onedir` is required.**
- **Pitfall found and fixed**: EDpjKinsaku's editable install (`pip install -e .`) is invisible to
  PyInstaller's static import analysis - a plain `pyinstaller entry.py` reports `app` itself as a
  missing module and the built exe fails with `ModuleNotFoundError: No module named 'app'` at
  startup. Passing `--paths <repo root>` fixes it; this is required for both A and B.
- Existing `distribution/` precedent checked (`AppPaths.java`, `Installer.install4j`,
  `.gitattributes`): EliteIntel already bundles a JRE, ONNX TTS/STT/embedding models, and a native HUD
  overlay so commanders never install anything themselves. A had already broken that precedent (system
  Python); B does not.
- Size was not a deciding factor either way - the TTS model alone is 384MB, the embedding model
  130MB, against 21-44MB for either C-CORE option.
- **Decision: B** (dedicated entry point, `--onedir`). Linux packaging is explicitly deferred to a
  later phase - PyInstaller cannot cross-compile, so it needs its own real build on Linux, not a
  Windows-side guess.

### 8-B: implementation

- **EDpjKinsaku**: new `app/cli/bio_entry.py` (`from app.cli.bio import bio_app; bio_app()` - nothing
  else), a `packaging` optional-dependency group (`pyinstaller>=6.0`) in `pyproject.toml`, and
  `scripts/build_ccore_binary.ps1` (`--onedir`, `--paths <repo root>`). `bio_app` has exactly one
  command, so Typer collapses it away when run standalone - the built binary takes no arguments, only
  the same request JSON on stdin `edpj bio evaluate` always read.
- **EliteIntel**: `AppPaths.getCCoreBinary()` added alongside `getOverlayBinary()`, resolving
  `distribution/ccore/<os>/bio_entry(.exe)` - a per-OS subdirectory rather than overlay's flat
  directory, because a PyInstaller onedir build's `_internal/` dependency folder would collide between
  platforms in one flat directory the way overlay's differently-named per-OS files never do.
  `CCoreAdapter.start()` now launches that binary directly with no arguments instead of
  `python -m app.cli bio evaluate`; `PYTHON_COMMAND` is gone. `.gitattributes` gained a
  `distribution/ccore/** filter=lfs` rule (a onedir build's file types vary too much to hand-pick by
  extension the way overlay's DLL list does).
- The real Windows binary (`bio_entry.exe` + `_internal/`, 21MB) is committed to
  `distribution/ccore/windows/` via git-lfs, built by hand with the script above - there is no
  automated cross-repo copy from EDpjKinsaku into EliteIntel yet (analogous to how `overlay/`'s
  binaries are committed by hand after `buildOverlay`/`buildOverlayWindows`, except overlay's source
  lives inside this repo and C-CORE's does not).

### Not done in this phase (by design)

Linux/macOS binaries, automating the EDpjKinsaku → EliteIntel binary copy, and any change to
`SAASignalsFoundSubscriber`/the HUD/`ExobiologyCandidateFactSource` (none of them call `CCoreAdapter`
differently - only what is inside `CCoreAdapter.start()` changed) were all left alone.

**Verified:** `:app:compileJava`/`:app:compileTestJava` succeed. `CCoreAdapterIntegrationTest` (4
tests, including the Aleoida Arcus boundary-MATCH fixture) and `CCoreAdapterJsonTest` (5 tests) pass
against the real bundled binary. `subscriberTest` (`SAASignalsFoundCCoreSliceTest`, 2 tests) passes
end-to-end through the same binary. Default `test` task: 3129 tests, 9 failures, the same pre-existing
`JukeboxPlayerTest`/`TagScannerTest` failures already confirmed unrelated - no regression. A commander
running the built jar with `distribution/ccore/windows/` alongside it now needs no Python, no pip, and
no EDpjKinsaku checkout for C-CORE species evaluation to work.

## 16. Phase 9: Navigation integration — 保留／on hold (2026-09-13)

Read-only investigation only; no code changed, nothing committed for this phase.

### Why this is on hold, not done or in progress

The table's original Phase 9 description assumed `EDpjKinsaku`'s `DESTINATION_ETA_SPEC` had something
built to bring into EliteIntel. Checked directly against both repos, that assumption does not hold:

- **`EDpjKinsaku`'s `docs/DESTINATION_ETA_SPEC_V0.1.md` and `docs/PHASE_DESTINATION_ETA_V0.1.md` are
  specs only.** The phase doc's own status is "Planned". A repo-wide search for
  `distanceToArrival`/`distance_ls`/`eta_seconds`/`SupercruiseEtaEstimator` found no matching
  implementation anywhere in `app/` - the one incidental hit (`app/bio/body_parameters.py`) is an
  unrelated EDSM body-physical-parameters backfill column for the bio value model, not station
  navigation. Per §0's ground rule: a spec document is not a fact about what exists.
- **EliteIntel already has its own navigation feature, built independently of `EDpjKinsaku`, well
  before this investigation**: `ShipRouteObjectiveSource` (HUD card - destination, next waypoint,
  jump count, scoopable, contextual reminder for a material trader/technology broker/interstellar
  factors/Vista Genomics/refuel errand) and `AnalyzeRouterQuery` (the
  `query_ship_route_remaining_jumps` AI chat query - next waypoint, jumps remaining, and both
  straight-line and total route distance in light-years via `NavigationUtils.calculateGalacticDistance`
  over `NavRoute.json`'s X/Y/Z coordinates). Separately, per-station `distanceToArrival` (light seconds
  from a system's arrival point) is already pervasive across EliteIntel's own EDSM/Spansh search DTOs
  and already reaches AI chat through `AnalyzeMarketsQuery` for market results.
- **The one piece that is genuinely missing anywhere - Supercruise ETA (a time estimate, not a
  distance) - is missing from both repos equally.** It is not something Phase 9 could "surface" from
  `EDpjKinsaku`, because there is nothing there yet to surface; building it would be new-feature work
  against `DESTINATION_ETA_SPEC`'s heuristic model (§4 of that spec), independent of whichever repo
  ends up hosting it.

### Decision

Phase 9 is left on hold rather than marked done or reworked now:
- Not "done", because nothing was integrated in this phase - the existing jump-count/distance features
  predate this investigation and were built for reasons unrelated to `EDpjKinsaku`.
- Not reworked into "build the ETA estimator now", because that is a new feature under
  `DESTINATION_ETA_SPEC`, not the "bring existing EDpjKinsaku output into EliteIntel" task Phase 9 was
  originally scoped as - conflating the two would silently change what Phase 9 means.
- Project priority instead moves to widening C-CORE's genus coverage (6 of 19 done), then exobiology
  value/ranking (table Phase 10), then strengthening AI chat's use of exobiology data - the actual
  cross-repo integration this project exists for. Supercruise ETA, if wanted later, is tracked as an
  independent feature decision, not a resumption of Phase 9.

## 17. Phase 10: Japanese AI conversation (2026-09-13)

### 10-A: investigation (read-only)

Traced the full commander-turn pipeline end to end (`AiTabPanel` → `UserInputEvent` →
`VegaSubsystemGate` → `ThoughtDispatcher.submitCommanderInput()` → `PhoneticInputNormalizer` →
`CommanderThought` → `PromptComposer`/`CommanderPrompt`'s `<language>` rule → `LlmGateway` → `speak.text`
→ `SpeechGateway` → `TtsProvider`) against `Language.JA`. Found: typed Japanese input is untouched by any
translation step; the system prompt already instructs the LLM to write `speak.text` in the commander's
language (`{inputLanguage}`/`{language}`, both resolving to "Japanese" via
`AiResponseLanguagePolicy.resolveEffectiveAiResponseLanguage`); C-CORE facts
(`ExobiologyCandidateFactSource`) are plain English text appended to the system message and are
independent of the response-language rule, so they need no Japanese-specific handling. The one real
defect found was TTS: Kokoro's Japanese speakers are held out and its phonemizer has no Japanese entry,
yet `TtsProvider.canVoice(Language.JA)` returned `true`, so a Japanese commander on the default (Kokoro)
TTS provider would have Japanese text read with English pronunciation rules instead of being voiced at
all - fixed in 10-B. Parakeet STT's Japanese gap (no Japanese vocabulary in the bundled model) was
confirmed as an already-documented, honest limitation, not a hidden defect, and left alone.

### 10-B: implementation (EliteIntel commit `334b35b1b`)

`TtsProvider.canVoice()` now excludes `Language.JA` for `KOKORO`, alongside Cyrillic.
`AiResponseLanguagePolicy.resolveEffectiveAiResponseLanguage()` was refactored to delegate to
`TtsProvider.canVoice()` instead of carrying its own separate copy of "which languages Kokoro cannot
voice" - the duplication is exactly how the Japanese gap went unnoticed while the Cyrillic case stayed
correct. The single `canVoice()` fix propagates automatically through every consumer
(`SystemSession.getTtsProvider()`, `ApiFactory.selectMouth()`, `RadioVoicing`, the AI-services settings
panel) with no changes needed to any of them. `RadioVoicingTest` needed the same Cyrillic→Cyrillic-or-
Japanese update as `TtsProviderLanguageTest`, found only once the fix exposed it as a new test failure -
not anticipated by the 10-A investigation, a reminder that "found every affected file" claims from a
read-only pass should be treated as provisional until the tests actually run.

**Verified:** `:app:compileJava`/`:app:compileTestJava` succeed. `TtsProviderLanguageTest`,
`AiResponseLanguagePolicyTest`, `ApiFactoryTest`, `RadioVoicingTest`, `BundleKeyParityTest`,
`BundleQuotingTest` all pass, all four TTS-related ones extended with explicit Japanese cases rather than
only widening an existing loop. Default `test` task: 3134 tests, 9 failures, the same pre-existing
`JukeboxPlayerTest`/`TagScannerTest` failures already confirmed unrelated.

### Real-machine verification: scope, not yet performed

Everything above was verified through real production singletons (`SystemSession`, the real DB,
`TtsProvider`, `ApiFactory`) exercised by automated tests, not mocks - but no human has yet spoken to a
running EliteIntel in Japanese and listened to the result. That verification is scoped to exactly three
checks, deliberately excluding the separately-tracked System Map station-selection gap (undecided A/B/C
per the investigation above the fold - not part of this vertical slice):

1. **Japanese voice input** - speaking in Japanese, EliteIntel recognises it (text chat input already
   works per 10-A; this checks the STT path specifically, independent of Parakeet's documented Japanese
   transcription gap - the commander may be typing or using a different STT path).
2. **Japanese voice response** - VEGA replies in Japanese, and the TTS output is actually pronounced as
   Japanese (the 10-B fix's real payoff: confirms the Kokoro→Edge fallback is audible, not just
   type-checked).
3. **Game operation from a Japanese utterance** - "マップを開いて" opens the Galaxy Map
   (`display_open_galaxy_map`); "ミッションの目的地に行って" runs `navigate_to_active_mission` and a
   route is actually plotted to the mission's destination system.

Passing all three confirms the vertical slice (voice → AI CLI → Japanese reply → Japanese TTS → game
action) end to end on a real machine. Not in scope here: selecting the mission's specific destination
station within the System Map. A separate read-only investigation (session conversation only, not yet
written up in this document) found `MissionDto` already carries `destinationStation`/
`destinationSettlement`, `Status.isSystemMapOpen()` and the whole `GameInputStep`/`GameControllerBus`/
`KeyProcessor` input pipeline are already reusable, but no code path exists yet to select a station once
the System Map is open, and it is not known from code alone whether the System Map even offers a
text-searchable UI the way the Galaxy Map does - real-machine confirmation of that UI is a separate,
not-yet-scheduled piece of work.
