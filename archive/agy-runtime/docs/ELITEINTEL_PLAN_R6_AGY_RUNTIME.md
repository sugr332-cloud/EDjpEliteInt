# 未運用: 実行時 agy（Antigravity CLI）AI Provider — 計画書からの退避分

**Status:** 未運用（archived, 2026-09-25）
**出典:** `docs/ELITEINTEL_INTEGRATION_PLAN.md` の旧 §R.6（R.6〜R.6.2、R.6.4）および §R.14「v2-P3（AI Provider CLI化 / agy対応）実装台帳」を、`main` = `665b18071` 時点の本文から**文言を変えずに**移した（R.6 冒頭と R.6.2 冒頭の 2026-09-25 改訂注記のみ移設時に付加）。
**現行方針:** 実行時の AI 会話は LLM（LM Studio + Gemma 4 E4B）に一本化した。`docs/ELITEINTEL_INTEGRATION_PLAN.md` §R.6 を正とする。
**参照ルール:** 実装時にこの文書を参照するのは、ユーザーから明示的に求められた場合のみとする（`archive/agy-runtime/README.md`）。

---

### R.6 v2-P3 AI Provider CLI 化 / `agy` 対応【完了 → 2026-09-25 実行時経路は廃止対象】

> **改訂注記（2026-09-25）:** AI 会話は LLM へ一本化し、実行時に `agy` を使わないことに決定した。§R.6〜§R.6.4 は
> 実装・検証の**実行記録**として残すが、現行の設計方針ではない。現行方針は **現行 docs の §R.6** を正とする。
> §R.6 の安全境界のうち「ツール実行は EliteIntel の決定的コードだけが行う」「LLM から DB・シェル・OS・ゲームへ直接経路を作らない」は、
> LLM 一本化後もそのまま有効（現行 docs の §R.6、§R.12）。

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

#### R.6.1 実装計画（サブフェーズ）

##### 実機検証で確立した安全要件（確定事項、2026-09-19 追記）

以下は実機検証で確認済みの事実である。§R.6「`agy` の実行境界（安全要件）」を具体化する。

- `agy` には全ツールを無効化する単一フラグ（`--no-tools` 相当）は存在しない。
- 実行のたびに空の一時ディレクトリを作成し、その中に隔離用の `settings.json`（`permissions.deny` に
  `command(*)` / `write_file(*)` / `read_file(*)` を指定）を配置し、環境変数 `USERPROFILE` をその一時
  ディレクトリへ向けることで、ホスト環境の設定（`toolPermission: always-proceed` 等）に関わらずエージェント
  機能（シェル実行・ファイル読み書き）を無効化できることを実機で確認済み。
- `--dangerously-skip-permissions` は使用しない（§R.13.10 の禁止と一致）。
- `--json-schema` と `--output-format json` を併用することで、tool-call 形式の JSON 出力が得られることを
  実機で確認済み。ただし `arguments` フィールドは `object` 型ではなく `string` 型（JSON 文字列として
  シリアライズしたもの）で定義する必要がある。`object` 型で定義した場合、検証したモデル側でスキーマ
  バリデーションエラーが発生した。
- 検証に使用した一時ディレクトリと隔離用 `settings.json` は、検証終了後に完全に削除する。実装（G-1）でも
  このライフサイクル（作成 → 使用 → 完全削除）を踏襲する。

##### サブフェーズ分割

§R.14 の実装台帳（Track J）にならい、v2-P3（Track G）の実装単位をサブフェーズへ分割する。各サブフェーズは
§R.13 の統制手順（1 サブフェーズ＝1 作業ブランチ＝1 コミット、PLAN CHECK → 承認 → 実装 → TEST GATE →
DIFF GATE → END REPORT）に従う。

| ID | 内容 | 変更許可ファイル | TEST GATE |
|---|---|---|---|
| G-1 | `AgyCliTransport`（`LlmTransport` 実装）: プロセス実行、隔離ライフサイクル管理（一時ディレクトリ＋隔離 `settings.json`＋`USERPROFILE` 差し替え）、二重タイムアウト（既定／絶対上限）、プロセスツリー終了、`CCoreAdapter`（`app/src/main/java/elite/intel/bio/ccore/CCoreAdapter.java`）／`StreamCollector`（`app/src/main/java/elite/intel/bio/ccore/StreamCollector.java`）パターンの再利用 | 新規 `app/src/main/java/elite/intel/ai/brain/vega/llm/AgyCliTransport.java`、新規 `app/src/test/java/elite/intel/ai/brain/vega/llm/AgyCliTransportTest.java` | `./gradlew --no-daemon :app:test --tests '*AgyCliTransportTest'` |
| G-2 | `AgyCliProviderAdapter`（`LlmProviderAdapter` 実装、`app/src/main/java/elite/intel/ai/brain/vega/llm/LlmProviderAdapter.java` を実装）: プロンプト構築、JSON Schema 生成（`arguments` は string 型）、tool-call 解析、要約ターン（`parseText`）処理 | 新規 `app/src/main/java/elite/intel/ai/brain/vega/llm/AgyCliProviderAdapter.java`、新規 `app/src/test/java/elite/intel/ai/brain/vega/llm/AgyCliProviderAdapterTest.java` | `./gradlew --no-daemon :app:test --tests '*AgyCliProviderAdapterTest'` |
| G-3 | `ProviderEnum.AGY` の追加と `VegaLlmGatewayFactory` への統合。既存の `SystemSession.useLocalCommandLlm()`（ローカル LM Studio 優先、`VegaLlmGatewayFactory.create()` 内で最初に判定）と `LlmProviderResolver.detectCloudProvider()`（クラウド API キー設定）をそのまま優先し、いずれも未設定の場合にのみ `agy` を既定として選択する設計とする | `app/src/main/java/elite/intel/ai/ProviderEnum.java`、`app/src/main/java/elite/intel/ai/brain/vega/llm/VegaLlmGatewayFactory.java`、既存 `app/src/test/java/elite/intel/ai/brain/vega/llm/VegaLlmGatewayFactoryTest.java` | `./gradlew --no-daemon :app:test --tests '*VegaLlmGatewayFactoryTest'` |
| G-4 | 固定 fixture によるテスト整備: 正常系、非ゼロ終了コード、タイムアウト、不正出力、`agy` 未インストール、隔離ディレクトリのクリーンアップ検証。新規の fixture 用リソースファイルが必要になった場合の配置場所は G-1/G-2 の PLAN CHECK 時に確定する | G-1/G-2 で作成した `app/src/test/java/elite/intel/ai/brain/vega/llm/AgyCliTransportTest.java`、`app/src/test/java/elite/intel/ai/brain/vega/llm/AgyCliProviderAdapterTest.java`（新規ファイルは作らずケースを追加する） | `./gradlew --no-daemon :app:test --tests '*AgyCliTransportTest' --tests '*AgyCliProviderAdapterTest'` |

##### 完了条件

§R.6 の完了条件をそのまま引き継ぐ。

- `ProviderEnum` と `VegaLlmGatewayFactory` から `agy` を選択でき、既定になっている。
- 固定 fixture（stdin / stdout / stderr / exit code / timeout / 不正出力 / 未インストール）でテストが成功する。
- 日本語のテキスト指示が `agy` 経由で tool-call に変換され、既存アクションが実行される（v2-P4 の最初の実機確認）。
- AI 会話が HTTP API の直接呼び出しに依存しない。

#### R.6.2 ターン種別によるプロバイダー振り分け（G-5）

> **改訂注記（2026-09-25）:** 本節の二重ルーティング（雑談・要約ターン → `agy`）は 現行 docs の §R.6 の LLM 一本化で廃止する。
> G-8 以降、tool-calling ターンと雑談・要約ターンはどちらも同じ LLM Gateway へ到達する。以下は実行記録。

##### 背景（実機確認で判明した事実、確定事項）

G-1〜G-4 の実装後に実機確認を行ったところ、以下が判明した。

- `VegaLlmGatewayFactory.create()`（`app/src/main/java/elite/intel/ai/brain/vega/llm/VegaLlmGatewayFactory.java`）は `SystemSession.useLocalCommandLlm()` を最初に判定し、`true` の場合は無条件に LM Studio 用の Gateway（`LOCAL_GATEWAY`）を返す。`agy` へは、`useLocalCommandLlm()` が `false` かつクラウド API キーが未設定・未検出（`LlmProviderResolver.detectCloudProvider() == ProviderEnum.UNKNOWN`）の場合にのみ到達する。
- `useLocalCommandLlm` は `game_session` テーブルのカラムであり、`app/src/main/resources/db-migration/00030__schema.sql` で `boolean default true` として定義されている。この既定値は、`agy` 統合より前の 2026-04 のコミット「Fixing on-boarding. Default to LMStudio tulu3.1:8b-supernova etc」に由来する既存の設計判断であり、G-1〜G-4 では変更していない。
- `AiServicesSettingsPanel`（`app/src/main/java/elite/intel/ui/screen/settings/AiServicesSettingsPanel.java`）の UI は `settings.ai.localSetup`（LM Studio）／`settings.ai.cloudSetup`（クラウド）の2択セグメントコントロールのみを提供し、`agy` に相当する第3の選択肢は存在しない。
- 以上の結果、初期状態（DB 既定値のまま、設定変更なし）では `VegaLlmGatewayFactory.create()` は常に LM Studio 用 Gateway を返し、`agy` に一度も到達しない。
- **この既存の仕組み（`useLocalCommandLlm` の既定値・優先順位・UI の2択構成）自体は、今回変更しない。**

##### 新しい設計方針（確定事項）

VEGA への応答要求を、以下の2種類に区別する。

- **tool-calling ターン**: `request.tools()` に `SpeakFunction`（`speak`）以外のゲーム内アクションツールが含まれている（`TurnRoutingLlmGateway.isToolCallingTurn` が `true`）。ゲーム内アクションの実行判断が必要な場面。
- **雑談・要約ターン**: `request.tools()` が空、または `SpeakFunction` のみを含む（`TurnRoutingLlmGateway.isToolCallingTurn` が `false`）。自由な会話や要約のみを行う場面。

> **判定ロジックの確定経緯（2026-09-19、コミット `201b0ad2`）:**  
> 当初は「`request.tools().isEmpty()`」での判定を予定していたが、`ComposedPrompt.tools()` が COMMANDER ターンにおいて常にシステム関数 `SpeakFunction` をリストに結合（union）するため、`SemanticActionReducer` がゲームツールをゼロ件に絞り込んだ「こんにちは」等の純粋な雑談であっても `request.tools()` には必ず `speak` が含まれることが判明した。その結果、単純な `isEmpty()` 判定では全ターンが tool-calling ターンと判定されてしまう不具合が生じたため、現行の実装では「`SpeakFunction` 以外のツール（非 speak ツール）が存在するか否か（`request.tools().stream().anyMatch(tool -> !SpeakFunction.ID.equals(tool.name()))`）」を厳格な判定条件として採用している。

それぞれ次のプロバイダーを使用する。

- tool-calling ターン: 既存の LLM Provider（`SystemSession.useLocalCommandLlm()` およびクラウド API キー設定に基づく、LM Studio／クラウド API）をそのまま使用する。
- 雑談・要約ターン: `agy`（`AgyCliProviderAdapter` + `AgyCliTransport`）を使用する。

LM Studio／クラウド API が未設定・未導入の環境では、tool-calling ターンは現状どおり接続エラーとなる。これは §R.7.1「ローカル LLM 導入」（次フェーズ）で解消する。

##### G-5 実装計画

§R.14 の実装台帳（Track J）にならい、Track G の実装単位として以下を追加する。

| ID | 内容 | 変更許可ファイル | TEST GATE |
|---|---|---|---|
| G-5 | 非 speak ツールの有無に基づいて、tool-calling ターンと雑談・要約ターンで異なる `LlmProviderAdapter` / `LlmTransport` のペアを選択するルーティング層を実装する。`VegaLlmGatewayFactory.create()` が、既存の選択ロジック（LM Studio／クラウド、`agy` フォールバックなし）で構築した Gateway と、`agy` 専用の Gateway の2組を保持し、リクエストごとに使い分ける新規クラス `TurnRoutingLlmGateway`（`LlmGateway` 実装）を返すよう変更する。既存の `VegaLlmGateway`、`AgyCliTransport`、`AgyCliProviderAdapter` のインターフェース契約（`LlmGateway` / `LlmProviderAdapter` / `LlmTransport`）は変更しない。 | 新規 `app/src/main/java/elite/intel/ai/brain/vega/llm/TurnRoutingLlmGateway.java`、新規 `app/src/test/java/elite/intel/ai/brain/vega/llm/TurnRoutingLlmGatewayTest.java`、既存 `app/src/main/java/elite/intel/ai/brain/vega/llm/VegaLlmGatewayFactory.java`、既存 `app/src/test/java/elite/intel/ai/brain/vega/llm/VegaLlmGatewayFactoryTest.java` | `./gradlew --no-daemon :app:test --tests '*TurnRoutingLlmGatewayTest' --tests '*VegaLlmGatewayFactoryTest'` |

##### 完了条件

- `request.tools()` に非 speak ツールが含まれるリクエストは既存の LLM Provider（LM Studio／クラウド）へ、空または `speak` のみのリクエストは `agy` へ到達することが、固定 fixture で確認できる。
- 既存の `VegaLlmGateway`、`AgyCliTransport`、`AgyCliProviderAdapter` のインターフェース契約に変更がない。


#### R.6.4 stdin 化と Resident 常駐プロセスの本番導入（PR #10、PR #11）

##### 1. プロンプト入力の stdin 化（PR #10、確定事項）

- **背景と課題**: 初期実装（G-1）では `--print <prompt>` CLI 引数を用いてプロンプトを渡していたが、会話履歴やツール指示が蓄積した際に Windows OS のコマンドライン長制限（`CreateProcess` 32,767 文字制限）に達するリスク、および `CommandLineToArgvW` による引数パース時のダブルクォート・改行・マルチバイト文字（日本語）のトークン破損・エスケープ破損リスクが存在した。
- **設計決定**: CLI 引数から `--print` を完全撤廃し、プロンプトを標準入力（`process.getOutputStream()`、UTF-8）へパイプ送信する方式に完全移行した。
- **該当実装**: `AgyCliTransport.java`（L133-138、L189-204）

##### 2. Resident agy 常駐プロセス管理（PR #11、確定事項）

- **背景と課題**: ターンごとに `agy` プロセスを起動・終了するワンショット方式では、プロセス初期化に伴うレイテンシ（毎ターン 3〜6 秒）が累積し、会話の即応性が損なわれていた。また、プロセス毎の初期化に伴いスキーマ制約の解釈が不安定になるリスクがあった。
- **本番アーキテクチャ**: `AgyResidentProcessManager` を導入し、常駐プロセスとの入出力ストリームを維持する常駐実行を **VEGA の本番既定経路** とした。
  - **実行モード**: `agy` を `--effort low`、`--json-schema`（固定 SpeakFunction スキーマ、`arguments: string`）、`--print-timeout 15s` で起動し、`stream-json` 形式で双方向通信を行う。
  - **二重タイムアウト境界**: agy CLI 内部の 15s タイムアウトと、Java 側の 20s watchdog（`cliTimeout + 5s`）の二重構造。タイムアウト時にプロセスが生存していればプロセス状態を `READY` に戻し、`TRANSIENT` failure を返却して次ターンでの再利用を可能とする。
  - **透過的自動復旧（Auto-Recovery）**: 外部からの強制終了（taskkill 等）やパイプ切断（Broken pipe）を検知した場合、次回リクエスト時に透過的に新規プロセスを起動して復旧する（`test4`、`test5` で実証済み）。
  - **ライフサイクル伝搬**: `AgyCliTransport` が `AutoCloseable` を実装し、`VegaRuntimeGraph.close()` → `VegaLlmGateway.close()` → `AgyCliTransport.close()` → `AgyResidentProcessManager.close()` を経由して、OS プロセスツリー（子孫プロセス含む）を強制終了し隔離一時ディレクトリを完全削除する（`test6`、`test7` で実証済み）。
  - **ワンショット実行の扱い**: 従来のワンショット実行（`AgyCliTransport.sendOneShotOutcome`）は本番経路から切り離され、モック/フェイクプロセスを注入するユニットテスト用シーム（`ProcessStarter`）としてのみ保持される。
- **該当実装**: `AgyResidentProcessManager.java`（新規）、`AgyCliTransport.java`、`VegaLlmGateway.java`、`AgyResidentProductionVerificationTest.java`（実機統合テスト、`@Tag("local-integration")` により CI 除外）


---

#### v2-P3（AI Provider CLI化 / agy対応）実装台帳

v2-P3 の実装は、G-1〜G-7、stdin 化（PR #10）、および resident agy 常駐化（PR #11）を経て完了した。

| ID | 内容 | 変更許可ファイル | TEST GATE / 検証 | 状態 |
|---|---|---|---|---|
| G-1 | `AgyCliTransport` の one-shot プロセス実行基礎実装。`--print` 引数およびモック用 `ProcessStarter` の導入。 | `app/src/main/java/elite/intel/ai/brain/vega/llm/AgyCliTransport.java` 等 | 単体テスト | DONE |
| G-2 | `SpeakFunction` の引数型契約整合。`arguments` を `string` 型で統一し、CLI/スキーマとの不整合を解消。 | `app/src/main/java/elite/intel/ai/brain/vega/SpeakFunction.java` 等 | 単体テスト | DONE |
| G-3 | `AgyCliProviderAdapter` のレスポンス契約整合。空応答や不正形式に対する例外ハンドリングおよびフォールバック定義。 | `app/src/main/java/elite/intel/ai/brain/vega/llm/AgyCliProviderAdapter.java` 等 | 単体テスト | DONE |
| G-4 | `SemanticActionReducer` の日本語正規化および境界値強化。空文字・句読点・特殊文字の誤認識防止。 | `app/src/main/java/elite/intel/ai/brain/vega/SemanticActionReducer.java` 等 | 単体テスト | DONE |
| G-5 | `TurnRoutingLlmGateway` による二重ルーティング層。非 speak ツールを含む場合は既存プロバイダー（LM Studio/クラウド）、非 speak ツールを含まない場合（雑談/要約/speakのみ）は `agy` へ振り分け。 | `TurnRoutingLlmGateway.java`、`VegaLlmGatewayFactory.java` 等 | 単体・結合テスト | DONE |
| G-6 | エイリアス未定義ツールの除外による意味類似度の誤検出防止。自然言語エイリアスを持たない内部ツールの誤選定を遮断。 | `SemanticActionReducer.java` 等 | 単体テスト | DONE |
| G-7 | 短文入力に対する意味類似度フロア引き上げ（`SHORT_INPUT_SEM_FLOOR`）。偶発的な短フレーズ一致を抑制。 | `SemanticActionReducer.java` 等 | 単体テスト | DONE |
| PR #10 | プロンプト入力の stdin 化。Windows CLI コマンドライン長制限（32,767文字）およびエスケープ破損リスクを解消。 | `AgyCliTransport.java` | 単体テスト | DONE |
| PR #11 | `AgyResidentProcessManager` による resident agy 常駐化。`stream-json` 双方向通信、二重タイムアウト（15s/20s）、透過的自動復旧、およびライフサイクル管理の実装。 | `AgyResidentProcessManager.java`、`AgyCliTransport.java`、`VegaLlmGateway.java`、`AgyResidentProductionVerificationTest.java` | 実機実測テスト（Test 1〜7 ALL PASS、Strict Schema 100%） | DONE |
