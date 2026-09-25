# archive/agy-runtime — 未運用（実行時 agy AI Provider）

**Status:** 未運用（archived, 2026-09-25）

## これは何か

v2-P3 で実装・検証した「EliteIntel の実行時 AI Provider として `agy`（Antigravity CLI）を使う」構成の記録です。
2026-09-25 に、アプリケーションの AI 会話は **LLM（LM Studio + Gemma 4 E4B）へ一本化**し、実行時に `agy` を使わないことに決定しました（`docs/ELITEINTEL_INTEGRATION_PLAN.md` §R.6）。
実装やテスト結果に有用な情報が含まれるため、削除せずにここへ退避しています。

- **開発作業には引き続き `agy` を使います**（`docs/ELITEINTEL_INTEGRATION_PLAN.md` §R.13）。ここに退避したのは「アプリが実行時に `agy` を呼ぶ」部分だけです。

## 参照ルール

- **実装時にこのフォルダを参照するのは、ユーザーから明示的に求められた場合のみとします。**
- `agy`・Claude Code などの開発エージェントは、指示で明示されない限り、このフォルダを読まない・参照しない・変更しません。
- このフォルダの内容は現行仕様ではありません。現行仕様と矛盾する場合は `docs/ELITEINTEL_INTEGRATION_PLAN.md` が優先します。
- `src/` 配下は Gradle のソースセット外にあり、ビルド・テストの対象になりません。

## 内容

| パス | 中身 | 移動元 |
|---|---|---|
| `docs/ELITEINTEL_PLAN_R6_AGY_RUNTIME.md` | 旧 §R.6〜§R.6.2・§R.6.4（v2-P3 目的・実行境界・G-1〜G-5・stdin 化・resident agy）と v2-P3 実装台帳 | `docs/ELITEINTEL_INTEGRATION_PLAN.md`（`665b18071`） |
| `docs/RECOVERED_R6_R8_RUNTIME_AGY.md` | Recovered record の §2・§3・§6〜§9（hybrid routing、runtime safety boundary、provider contract、E2E contract、implementation gate） | `docs/RECOVERED_R5_R8_AGY_SAFETY.md`（`665b18071`） |
| `docs/AGY_RESIDENT_BENCHMARK_REPORT.md` | resident vs one-shot 50 ターン A/B ベンチマーク結果 | `docs/`（`git mv`） |
| `docs/PHASE11_AI_PROVIDER_CLI_SPEC.md` | Phase 11 AI Provider CLI 仕様（廃止） | `docs/`（`git mv`） |
| `src/main/java/elite/intel/ai/brain/vega/llm/` | `AgyCliTransport`、`AgyCliProviderAdapter`、`AgyResidentProcessManager`、`TurnRoutingLlmGateway` | G-9 で `app/src/main/java/...` から `git mv` 予定 |
| `src/test/java/elite/intel/ai/brain/vega/llm/` | `AgyCliTransportTest`、`AgyCliProviderAdapterTest`、`AgyResidentBenchmarkTest`、`AgyResidentProductionVerificationTest`、`TurnRoutingLlmGatewayTest` | G-9 で `app/src/test/java/...` から `git mv` 予定 |
| `build/benchmarkTest.gradle` | `app/build.gradle` の `benchmarkTest` タスク原文 | G-9 で保存予定 |

G-9 では、上記のほか `ProviderEnum.AGY("LLM"),` の 1 行を本筋から削除します（原文はこの README に記録: `AGY("LLM"),` — `app/src/main/java/elite/intel/ai/ProviderEnum.java` の列挙の先頭にあった）。
