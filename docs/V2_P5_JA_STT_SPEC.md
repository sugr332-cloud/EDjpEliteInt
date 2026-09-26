# v2-P5 日本語音声認識（STT）仕様

**Status:** Draft（2026-09-26）
**正本との関係:** `docs/ELITEINTEL_INTEGRATION_PLAN.md` §R.7 v2-P5・R.7（STT/TTS 分離）の下位仕様。矛盾する場合は計画書を優先する。

## 0. 背景（2026-09-26 実機）

- 同梱 STT（`ParakeetSTTImpl` + `distribution/parakeet/`）は英語専用で、日本語の発話を英語の文字列として出す（実機: 日本語で話しかけて `STT: [what the fuck]`）。計画書 §R.7 の既知事項どおり。
- 開発者のローカルに作業中の stash `wip-feature-ja-stt-reazonspeech`（`stash@{0}`、ベース `665b1807`）がある。READ-ONLY 調査（2026-09-26）の結果:
  - `ParakeetSTTImpl.java` / `AppPaths.java`: 日本語時に sherpa-onnx の ReazonSpeech Zipformer モデルを読む分岐。ベース以降 main で変更なし → そのまま取り込める
  - `VegaLlmGatewayFactory.java`: 実行時 agy と LM Studio の切り替え。main では v2-P3b（G-8）で単一プロバイダに統一済み → **取り込まない（破棄）**
  - `CommanderPrompt.java`（待機・雑談は speak）、`ai_action_aliases_ja.properties`（発進・脚の別名）: STT とは別件 → P5-2 で扱う
- native ライブラリ（`distribution/native/sherpa-onnx`）は Parakeet と共用で配置済み。

## 1. 採用するモデル

- sherpa-onnx の `sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01`（オフライン Zipformer transducer、ReazonSpeech 35k 時間で学習）
- 取得元: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01.tar.bz2
- 使うファイル（stash の実装どおり）: `encoder-epoch-99-avg-1.int8.onnx`（約 148 MB）、`decoder-epoch-99-avg-1.onnx`、`joiner-epoch-99-avg-1.int8.onnx`、`tokens.txt`
- 置き場所: `distribution/reazonspeech/`
- **リポジトリにはコミットしない**（`.gitignore` に追加）。サイズ（LFS 容量）とモデルのライセンス（R.7「モデルのライセンスは同梱前に確認する」）を確認するまでは、各自がダウンロードして置く。同梱するかは別途決める

## 2. 動作

- 応答言語（STT の言語設定）が日本語のとき ReazonSpeech を使い、それ以外は従来どおり Parakeet（英語）を使う
- 日本語のときの前処理・後処理は stash の実装どおり（先頭無音トリムをしない代わりに発話エネルギーで無音を捨てる、小文字化しない、最小文字数 2、言語コード `ja`）
- **モデルが無いとき:** 日本語設定で `distribution/reazonspeech/` のファイルが揃っていない場合、英語モデルに黙って切り替えない（日本語の発話が英語の文字列になり誤動作の元になるため）。STT を起動せず、システムメッセージに「日本語音声認識モデルが見つかりません（distribution/reazonspeech/）。文字入力は使えます」と出す。アプリ本体・文字入力は止めない
- STT の出力は従来どおり未検証のユーザー入力として扱う（§R.7）

## 3. 実装台帳

| ID | 内容 | 変更許可ファイル | TEST GATE | 状態 |
|---|---|---|---|---|
| P5-1 | 日本語 STT（ReazonSpeech）の取り込み。stash@{0} から `ParakeetSTTImpl.java` と `AppPaths.java` の 2 ファイルだけを `git checkout stash@{0} -- <file>` で取り出す（stash 自体は変更しない。pop / apply / drop しない）。取り出した内容に §2「モデルが無いとき」の処理を追加する。`.gitignore` に `distribution/reazonspeech/` を追加 | `app/src/main/java/elite/intel/ai/ears/parakeet/ParakeetSTTImpl.java`、`app/src/main/java/elite/intel/util/AppPaths.java`、`.gitignore`、STT の文言を足す場合は `gui.properties` / `gui_ja.properties`（新キーのみ）と `i18n-parity-baseline.txt`（MISSING 行のみ）、テスト（既存の STT テストがあればそれ、無ければ新規 1 つ: モデル有無の判定・言語による分岐・日本語の後処理）。`VegaLlmGatewayFactory.java` は取り込まない。これ以外は PLAN CHANGE REQUEST | 新規／既存テストと全体テスト（既知の 10 件以外の失敗 0 件）。実機: モデルを置いた状態で日本語の発話（例「着陸装置を下ろして」「Sol 周辺の交易候補」）が日本語の文字列で `STT:` に出ること。モデルを外した状態でアプリが起動し、システムメッセージが出て文字入力が使えること | 未着手 |
| P5-2 | stash の STT 以外の変更の取り込み: `CommanderPrompt.java` の待機・雑談の指示 1 行、`ai_action_aliases_ja.properties` の発進・脚の別名。main の現状に手で合わせる | P5-1 後に確定 | 確定時に記載 | 未着手 |
