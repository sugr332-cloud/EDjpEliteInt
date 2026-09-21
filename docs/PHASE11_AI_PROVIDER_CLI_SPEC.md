# Phase 11 — AI Provider CLI Specification

**Status:** Completed (v2-P3 integrated)  
**Date:** 2026-09-13 (Last Updated: 2026-09-21)  
**Repo:** `sugr332-cloud/EliteIntel`

> **改訂注記（2026-09-15 / 2026-09-21）:** Roadmap v2（`ELITEINTEL_INTEGRATION_PLAN.md` §R）により、この仕様は **v2-P3** として扱う。
> AI 会話の既定 CLI Provider は **`agy`（Antigravity CLI）** とし、本文中の Gemini CLI / Claude CLI は同じ Provider 契約に載せる任意の実装とする。
> 2026-09-21 に v2-P3（G-1〜G-7、PR #10 stdin 化、PR #11 resident agy 常駐化）が main へ統合完了した。最新の本番構成・二重ルーティング契約（非 speak ツール判定）については `docs/ELITEINTEL_INTEGRATION_PLAN.md` §R.6 を正とする。

## 1. Purpose

Phase 11 の AI 会話機能について、LLM をどの方式で実行するかを明文化する。

EliteIntel の AI 会話は、既存の `LlmGateway` を入口として維持し、UI や VEGA のイベント経路から CLI 実行方式を直接意識させない。

## 2. AI Provider の実行方式

### Required

AI 会話で使用する LLM は、**HTTP API を直接呼び出さず、ローカル CLI をプロセスとして起動する**。

対象 CLI:

- Gemini CLI
- Claude CLI

想定経路:

```text
AiTabPanel / UserInputEvent
        ↓
VegaSubsystemGate
        ↓
ThoughtDispatcher
        ↓
LlmGateway
        ↓
AI CLI Provider
   ┌────┴────┐
Gemini CLI  Claude CLI
   └────┬────┘
        ↓
 stdin / stdout
        ↓
LLM response
```

## 3. 禁止事項

Phase 11 の AI Provider では以下を採用しない。

- Gemini / Claude 等の HTTP API の直接呼び出し
- AI 会話専用の別チャットシステムの新設
- UI から直接 CLI を起動する実装
- C-CORE と AI Provider の責務統合

API キーを前提とする既存 Provider 実装が存在する場合、それは本仕様の最終形ではなく、CLI Provider へ置き換える対象とする。

## 4. Provider 層の責務

CLI Provider は少なくとも以下を担当する。

1. CLI 実行ファイルの解決
2. CLI プロセスの起動
3. stdin へのプロンプト入力
4. stdout からの応答取得
5. stderr の収集
6. 終了コードの確認
7. タイムアウト処理
8. プロセス異常終了の処理
9. CLI 出力から LLM 応答を抽出する処理
10. `LlmGateway` が扱える Provider 契約への変換

UI、VEGA、`ThoughtDispatcher` は CLI の存在を直接知ってはならない。

## 5. Provider 選択

Gemini CLI / Claude CLI の選択方法は Provider 層に閉じ込める。

選択方式、実行ファイルのパス、追加引数、タイムアウト値などは、実装着手時に現在の EliteIntel 設定方式を確認したうえで決定する。

この段階では具体的な環境変数名や固定パスを仕様として決めない。

## 6. C-CORE との責務分離

C-CORE の CLI 実行は AI Provider とは別機能である。

```text
C-CORE
EliteIntel → CCoreAdapter → ProcessBuilder → bio_entry.exe
```

これはゲーム内の生物種評価を行うための既存 CLI 境界であり、Phase 11 の LLM CLI 実行とは統合しない。

```text
AI conversation
EliteIntel → LlmGateway → Gemini CLI / Claude CLI
```

両者はプロセス実行という共通点を持つが、入力データ、出力データ、失敗時処理、責務を分離する。

## 7. 日本語音声入出力のテスト仕様

Phase 11 では、テキスト経路だけでなく **日本語の音声入出力を実機で確認することを完了条件に含める**。

### 7.1 音声入力（STT）

日本語音声入力について、以下を確認する。

- [ ] 日本語音声を入力できる
- [ ] STT が日本語発話を `UserInputEvent` に到達させられる
- [ ] 日本語の固有名詞・ゲーム内用語を含む発話で致命的な認識崩れがない
- [ ] 認識結果が AI 会話の通常テキスト入力と同一経路に入る
- [ ] STT が日本語モデル未対応の場合、その制約を明示したうえで代替経路を確認する

**重要:** 日本語STTモデル未対応を「日本語音声入力対応済み」とみなさない。実際に日本語音声を入力し、認識結果が確認できた場合のみ PASS とする。

### 7.2 日本語LLM応答

- [ ] 日本語音声入力を受けた会話が LlmGateway / CLI Provider まで到達する
- [ ] LLM が日本語で応答する
- [ ] CLI Provider の stdout から応答を正常に取得できる
- [ ] エラー時に UI がフリーズせず、既存のエラー表示・ログ経路へ戻る

### 7.3 音声出力（TTS）

- [ ] LLM の日本語応答が TTS へ渡る
- [ ] 日本語を発話可能な TTS Provider が選択される
- [ ] 実機で日本語音声が再生される
- [ ] TTS 非対応 Provider を選択した場合、無音・クラッシュではなく既定のフォールバック動作になる
- [ ] 日本語音声再生失敗時もテキスト応答自体は失われない

VOICEVOX を採用する場合、VOICEVOX 固有の導入・起動条件は TtsProvider 実装仕様で別途定義する。本仕様では「日本語音声出力を実機で確認できること」を完了条件とする。

### 7.4 音声入出力のE2E試験

最低1回、以下を一連の実機テストとして実施する。

```text
日本語で発話
   ↓
STT
   ↓
UserInputEvent
   ↓
VEGA / ThoughtDispatcher
   ↓
LlmGateway
   ↓
Gemini CLI または Claude CLI
   ↓
日本語LLM応答
   ↓
TtsProvider
   ↓
日本語音声再生
```

テスト記録には少なくとも以下を残す。

- 使用OS / 実機
- 使用STT Provider / モデル
- 使用AI CLI Provider（Gemini CLI / Claude CLI）
- 使用TTS Provider
- 発話内容
- STT認識結果
- LLM応答結果
- TTS再生結果
- PASS / FAIL と失敗時の原因

## 8. 現在の実装との差分
 
2026-09-13 の read-only 調査時点では CLI Provider は未実装であったが、**2026-09-21 の v2-P3 完了（PR #9〜#11）により、`agy` CLI Provider（常駐プロセス管理 `AgyResidentProcessManager`、双方向 stdin/stdout `stream-json` 通信、二重タイムアウト制御、自動復旧機構）が本番経路として統合完了した。**
 
また、G-5 により `TurnRoutingLlmGateway` が導入され、非 speak ツールを要求するターンは既存の LLM Provider（LM Studio / クラウド）、雑談・要約・speak のみのターンは `agy` CLI Provider へ振り分けるハイブリッドルーティングが確立されている。
 
C-CORE の CLI (`CCoreAdapter` → `ProcessBuilder` → `bio_entry.exe`) も別途実装済みである。
 
日本語音声入出力（STT / TTS 実機連携）については後続のサブフェーズにて実機検証を継続する。

## 9. Phase 11 CLI Provider 完了条件

以下をすべて満たした場合に AI Provider CLI 化を完了とする。

- [ ] Gemini CLI を Provider 経由で起動できる
- [ ] Claude CLI を同一 Provider 契約で起動できる
- [ ] stdin / stdout の受け渡しができる
- [ ] stderr を取得できる
- [ ] 非ゼロ終了コードをエラーとして扱える
- [ ] タイムアウト時にプロセスを適切に終了できる
- [ ] Provider 選択を `LlmGateway` 配下で行える
- [ ] 既存の `AiTabPanel → UserInputEvent → VEGA → ThoughtDispatcher → LlmGateway → AiResponseLogEvent` 経路を維持できる
- [ ] AI 会話について HTTP API 直接呼び出しに依存しない
- [ ] CLI Provider のテストを追加する
- [ ] 日本語STTの実機E2E試験がPASS
- [ ] 日本語LLM応答の実機E2E試験がPASS
- [ ] 日本語TTS再生の実機E2E試験がPASS
- [ ] STT → LLM CLI → TTS の日本語E2E試験がPASS

## 10. 実装順序

この仕様は設計固定用であり、このコミットでは実装変更を行わない。

次回の Phase 11 実装では、まず現在の `LlmGateway` / Provider 抽象と設定経路を read-only で再確認し、その既存構造を壊さない最小変更として CLI Provider を追加する。

その後、音声入出力について単体・結合テストを追加し、最後に実機で日本語 STT → CLI LLM → TTS のE2E確認を行う。実機未確認の場合は Phase 11 完了とは記録しない。
