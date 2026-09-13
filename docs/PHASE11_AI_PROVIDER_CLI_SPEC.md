# Phase 11 — AI Provider CLI Specification

**Status:** Planned / not implemented  
**Date:** 2026-09-13  
**Repo:** `sugr332-cloud/EliteIntel`

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

## 7. 現在の実装との差分

2026-09-13 の read-only 調査時点では、EliteIntel の AI 会話経路は `LlmGateway` を経由して既存の LLM Provider を呼び出す構成であり、Gemini CLI / Claude CLI を起動する AI Provider 実装は確認されていない。

したがって、現在の状態は **CLI Provider 未実装** とする。

C-CORE の CLI (`CCoreAdapter` → `ProcessBuilder` → `bio_entry.exe`) は別途実装済みであり、本項の未実装判定には含めない。

## 8. Phase 11 CLI Provider 完了条件

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

## 9. 実装順序

この仕様は設計固定用であり、このコミットでは実装変更を行わない。

次回の Phase 11 実装では、まず現在の `LlmGateway` / Provider 抽象と設定経路を read-only で再確認し、その既存構造を壊さない最小変更として CLI Provider を追加する。
