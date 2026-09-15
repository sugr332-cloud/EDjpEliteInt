# Phase 11 — 日本語音声入出力 フェーズ追記

**Status:** Planned / not implemented  
**Date:** 2026-09-13  
**Base:** `docs/ELITEINTEL_INTEGRATION_PLAN.md` / Phase 11

> **改訂注記（2026-09-15）:** Roadmap v2（`ELITEINTEL_INTEGRATION_PLAN.md` §R）では本書の内容は **v2-P5** の完了判定とする。E2E 試験で使う AI CLI Provider は既定で `agy` とする。

## 1. 位置付け

Phase 11 の完了条件に、AI Provider CLI 化だけでなく **日本語音声入出力の実機E2E検証**を追加する。

既存の Phase 11 で確認済みの「テキスト入力 → VEGA → LlmGateway」の到達確認だけでは、音声入出力を含む会話機能の完了とはしない。

## 2. Phase 11 の追加完了条件

### 日本語音声入力（STT）

- [ ] 日本語音声を実機から入力できる
- [ ] STT の認識結果が `UserInputEvent` に到達する
- [ ] 日本語のゲーム内用語・固有名詞を含む代表的な発話を確認する
- [ ] 音声入力がテキスト入力と同一の VEGA 会話経路へ入る
- [ ] 日本語STTモデルが未対応の場合、その制約を明記し、対応済みとは判定しない

### 日本語LLM応答

- [ ] 日本語音声入力から `LlmGateway` まで到達する
- [ ] Gemini CLI または Claude CLI Provider が実際に応答を返す
- [ ] 日本語の応答を取得できる
- [ ] CLI異常終了・タイムアウト時に既存のエラー処理へ戻る

### 日本語音声出力（TTS）

- [ ] 日本語LLM応答が `TtsProvider` へ渡る
- [ ] 日本語対応TTS Providerを選択できる
- [ ] 実機で日本語音声が再生される
- [ ] TTS失敗時もテキスト応答は失われない
- [ ] 日本語非対応Provider選択時のフォールバックが確認できる

## 3. 日本語音声E2E試験

Phase 11 の最終実機試験として、以下を一連で確認する。

```text
日本語発話
  ↓
STT
  ↓
UserInputEvent
  ↓
VEGA / ThoughtDispatcher
  ↓
LlmGateway
  ↓
Gemini CLI / Claude CLI
  ↓
日本語LLM応答
  ↓
TtsProvider
  ↓
日本語音声再生
```

### 試験記録

最低1ケースについて、以下を記録する。

- OS / 実機
- STT Provider / モデル
- AI CLI Provider
- TTS Provider
- 発話内容
- STT認識結果
- LLM応答
- 音声再生結果
- PASS / FAIL
- FAILの場合の原因

## 4. Phase 11 完了判定

**以下の4領域をすべてPASSするまで Phase 11 完了とはしない。**

1. AI CLI Provider
2. 日本語STT
3. 日本語LLM応答
4. 日本語TTS

特に、コード上のProvider定義やフォールバック実装だけでは実機PASSにしない。実際の日本語発話から音声再生まで確認した結果を根拠として記録する。

## 5. 既存フェーズとの関係

- C-CORE CLI (`CCoreAdapter → ProcessBuilder → bio_entry.exe`) の完了判定とは分離する。
- Phase 10 の日本語応答/TTSフォールバック実装が存在する場合、それは実装済みとして扱うが、Phase 11 の実機E2E確認とは別である。
- VOICEVOX を採用する場合でも、Phase 11 の最終判定は特定製品の導入そのものではなく「日本語TTSが実機で正常再生されること」を基準とする。
