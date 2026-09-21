# Antigravity (agy) Resident vs One-Shot 50-Turn A/B Benchmark Report

## 1. 概要 (Overview)

本ドキュメントは、Elite Intel AI (VEGA) において導入された常駐プロセス方式 (`AgyResidentProcessManager`) と、従来のワンショット起動方式 (`sendOneShotOutcome`) の性能・安定性を実測比較した技術レポートです。

50種類の固定日本語会話プロンプトを用い、プロセス起動・破棄ライフサイクルの排除によるレイテンシ改善効果およびスキーマ遵守の安定性を定量評価しました。

---

## 2. 測定環境・条件 (Benchmark Setup)

* **実行環境**: Windows (MIKEGAME)
* **実行CLI**: Google Antigravity CLI (`agy.exe`)
* **テストスイート**: `AgyResidentBenchmarkTest.java` (`@Tag("benchmark")`)
* **測定ターン数**: 各方式 50 ターン（計 100 ターン実測）
* **プロンプト**: 50種類の独立した固定日本語会話入力（同一順序・同一内容）
* **プロンプト生成**: `wrapPrompt()` による共通ヘッダおよび `[Commander]` 入力ラッピング
* **構造化出力スキーマ**: `AgyResidentProcessManager.buildFixedSpeakSchema()`（固定 Speak JSON Schema）を両方式に同一適用
* **推論 Effort 設定**: `--effort low` を両方式に統一適用
* **CLI オプション比較**:
  - **Resident 方式**: `--input-format stream-json --output-format stream-json --sandbox --disable-slash-commands --effort low --json-schema <path> --print-timeout 15s`（同一プロセスで入出力を継続ストリーミング）
  - **One-shot 方式**: `--sandbox --disable-slash-commands --output-format json --effort low --json-schema <path> --print-timeout 15s`（毎ターン独立した一時ディレクトリおよびプロセスを生成・破棄）

---

## 3. ルーティング検証 (Routing Smoke Test)

A/B ベンチマークに先行して、`TurnRoutingLlmGateway` の独立 Smoke Test を実施し、ルーティング判定が正常に機能することを確認しました。

* **Case A (`speak` ツールのみの通常会話リクエスト)**:
  - 判定: `chatGateway` (`agy`) へ到達（`submitCalls=1`, `toolGateway=0`）
* **Case B (非 speak 内部ツール `docking_request` を含むリクエスト)**:
  - 判定: `toolGateway` (LM Studio/Cloud) へ到達（`submitCalls=1`, `chatGateway=1` のまま維持）
* **結果**: **PASS** (所要時間: 0.013s)

---

## 4. A/B ベンチマーク実測結果 (Benchmark Results)

### 4.1 統計サマリ比較

| 指標 (Metric) | Resident Warm (Turn 2..50) | Resident Overall (Turn 1..50) | One-shot Overall (Turn 1..50) | 差 / 改善率 |
| :--- | :--- | :--- | :--- | :--- |
| **Mean (平均レイテンシ)** | **2278.1 ms** | **2371.9 ms** | **7356.3 ms** | **約 3.10〜3.23 倍高速 (約 5.0 秒短縮)** |
| **Median (中央値)** | **2106 ms** | **2157 ms** | **6688 ms** | **約 3.10〜3.17 倍高速 (約 4.5 秒短縮)** |
| **P95 (95パーセンタイル)** | **3750 ms** | **4103 ms** | **11230 ms** | **約 2.7〜3.0 倍高速** |
| **Min (最小レイテンシ)** | **1370 ms** | **1370 ms** | **5759 ms** | **約 4.4 秒短縮** |
| **Max (最大レイテンシ)** | **6164 ms** | **6965 ms** (Cold) | **11968 ms** | **約 5.0〜5.8 秒短縮** |
| **プロセス起動回数** | **1 回 (常駐維持)** | 1 回 | **50 回 (毎回起動)** | 再起動 0 回 |
| **通信成功率 (Success)** | **50 / 50 (100.0%)** | 50 / 50 (100.0%) | **50 / 50 (100.0%)** | 同等 |
| **Strict Schema 検証ロジック合格率 (*1)** | **50 / 50 (100.0%)** | 50 / 50 (100.0%) | **50 / 50 (100.0%)** | 同等 |
| **Timeout / Error** | **0 件** | 0 件 | **0 件** | 同等 |

> **(*1) Strict Schema 検証ロジックの適用範囲について**:  
> テストコード内の `verifyStrictSchema()` が検証しているのは、「`tool_calls` 配列の存在」「先頭 tool_call の `name == 'speak'`」「`arguments` フィールドの存在」（またはトップレベルの `speak` オブジェクト）という出力構造（エンベロープ）の適合性までです。引数内部の型、必須プロパティ、プロパティ値制約など、JSON Schema 定義全体の網羅的なスキーマバリデーションを行うものではない点に留意してください。

### 4.2 Resident モード詳細

* **Cold 起動レイテンシ (Turn 1)**: `6965 ms`（CLI プロセス初回起動および初期化）
* **Warm 起動レイテンシ (Turn 2..50)**:
  - 平均 `2278.1 ms`、中央値 `2106 ms`、P95 `3750 ms`
  - 最小 `1370 ms`、最大 `6164 ms`
* **プロセス安定性**:
  - 初回 PID: `26080`、最終 PID: `26080`
  - 50 ターンを通じてプロセスの再起動・クラッシュはゼロ回（完全常駐維持）
  - タイムアウト・エラー・内部ツール誤検知はすべてゼロ件

### 4.3 One-shot モード詳細

* **プロセス起動検証**:
  - `ProcessStarter` 起動カウンタ: `50 / 50`（毎ターン ProcessBuilder.start() が呼び出されたことを確認）
* **レイテンシ推移**:
  - 平均 `7356.3 ms`、中央値 `6688 ms`、P95 `11230 ms`
  - 最小 `5759 ms`、最大 `11968 ms`
* **プロセス安定性**:
  - 成功数: 50 / 50、Strict Schema 検証ロジック合格数: 50 / 50 (100.0%)
  - タイムアウト・エラーはゼロ件

### 4.4 生レイテンシ配列 (Elapsed Milliseconds)

```json
{
  "resident_latencies_ms": [
    6965, 2812, 1900, 1620, 2584, 1826, 1772, 1543, 2253, 2055,
    2106, 1698, 1418, 2262, 1799, 1419, 1545, 1623, 1370, 4103,
    2605, 2024, 2886, 1721, 1924, 2962, 1774, 1674, 2561, 2338,
    2007, 3241, 1900, 1879, 2316, 3750, 2537, 3277, 1702, 3017,
    2157, 2313, 1702, 2158, 2307, 1853, 2393, 2515, 6164, 2264
  ],
  "oneshot_latencies_ms": [
    6340, 6503, 9527, 7607, 6266, 6095, 7349, 6931, 9457, 6539,
    5844, 7578, 6181, 7313, 6124, 6040, 6773, 11230, 7960, 5790,
    5759, 6178, 7484, 6254, 6602, 6368, 6339, 9075, 7804, 11017,
    5917, 6688, 6009, 8580, 5951, 6347, 6434, 7817, 5832, 5818,
    10864, 6191, 11968, 6292, 11350, 7553, 9607, 7650, 7003, 7618
  ]
}
```

---

## 5. One-shot における `--effort low` 追加前後の比較

事前検証において、One-shot 側に `--effort low` が付与されていない状態（既定推論設定）と、追加後の状態を比較検証しました。

| 指標 | `--effort` 未指定 (前回 One-shot) | `--effort low` 追加後 (今回 One-shot) | 変化 / 改善量 |
| :--- | :--- | :--- | :--- |
| **平均レイテンシ** | 19774.9 ms | **7356.3 ms** | **-12418.6 ms (約 62.8% 短縮)** |
| **中央値** | 19744 ms | **6688 ms** | **-13056 ms** |
| **P95** | 20149 ms | **11230 ms** | **-8919 ms** |
| **成功数** | 34 / 50 (68.0%) | **50 / 50 (100.0%)** | **+16 件 (100% 達成)** |
| **Strict Schema 検証ロジック合格数** | 1 / 50 (2.0%) | **50 / 50 (100.0%)** | **+49 件 (100% 達成)** |
| **Timeouts / Errors** | 16 / 50 | **0 / 50** | **完全解消 (0 件)** |

* **考察**:
  - `--effort` 未指定時はモデルの思考時間（Thinking）が長引いて 15 秒 CLI print-timeout や 20 秒 Watchdog 期限に抵触し、タイムアウトやスキーマ不完全が発生していました。
  - `--effort low` を明示的に指定したことで、推論が 15 秒以内に安定して収束し、100% の speak エンベロープ適合率とゼロタイムアウトが達成されました。

---

## 6. 技術的考察と評価 (Evaluation & Analysis)

1. **実測レイテンシ差（約 5 秒 / 約 3.1〜3.2 倍の高速化）**:
   - 同一の JSON Schema および `--effort low` 条件下において、Resident Warm（平均 2278.1 ms）と One-shot（平均 7356.3 ms）の間に **約 5.0 秒の平均レイテンシ差** が実測されました。
2. **最小レイテンシ（Min）に見る起動コストの底上げ効果**:
   - Resident の最小レイテンシは **`1370 ms`**、One-shot の最小レイテンシは **`5759 ms`** でした。
   - One-shot では、推論処理が即時終了する短い会話入力であっても、プロセス生成・Node/CLI ランタイム初期化・一時ディレクトリ・環境構築・プロセス終了処理に最低約 5.7 秒を要しますが、Resident 方式では 1.3〜1.5 秒で応答が完了します。
3. **通信プロトコル差異に関する留意事項**:
   - Resident 方式は `--input-format stream-json --output-format stream-json` を使用し、One-shot 方式は `--output-format json` を使用しています。
   - したがって、「約 5 秒の差すべてが純粋な OS プロセス起動コストである」と断定するのではなく、「通信プロトコル差も含めた両方式の実装条件における実測レイテンシ差が約 5 秒である」と評価するのが正確です。
4. **定常安定性**:
   - Resident 方式は Turn 1 の Cold 起動（6965 ms）を除き、Turn 2〜50（Warm）において平均 2.28 秒・P95 3.75 秒で極めて安定して推移し、同一 PID（26080）を完全に維持しました。

---

## 7. 将来の再測定手順 (Reproducibility)

本ベンチマークは、デフォルトの test タスク (`./gradlew test`) から `@Tag("benchmark")`（`excludeTags 'benchmark'`）により除外されており（Excluded from default test task）、日常の開発テスト実行に影響を与えません（※CI 自体に専用の分岐設定があるわけではなく、Gradle のデフォルト test タスクのタグ除外設定によるものです）。将来のモデルアップデートや CLI 仕様変更時に同一条件で再測定を行う場合は、以下のコマンドを実行します。

```powershell
# ベンチマーク専用タスクの実行 (約8分〜10分)
./gradlew benchmarkTest

# またはクラス指定での実行
./gradlew benchmarkTest --tests "*AgyResidentBenchmarkTest*"
```
