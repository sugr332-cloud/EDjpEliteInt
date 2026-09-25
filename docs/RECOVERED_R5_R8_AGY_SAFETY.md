# Recovered R.5–R.8 decisions and agy safety boundary

**Date:** 2026-09-15
**Status:** Normative recovery record
**Repository:** `sugr332-cloud/EDjpEliteIntel`

> The local uncommitted R.5–R.8 edits were lost before they could be committed. This document records the decisions that had already been established and the safety requirements agreed immediately before the loss. It is normative where it conflicts with older wording in the roadmap/specification.

> **改訂注記（2026-09-25 / LLM 一本化）:** アプリケーションの AI 会話は LLM（LM Studio + Gemma 4 E4B）へ一本化し、実行時に `agy` を使わないことに決定した（`ELITEINTEL_INTEGRATION_PLAN.md` §R.6 が正本）。開発作業には引き続き `agy` を使う。
> 実行時 `agy` に関する §2・§3・§6〜§9 は、未運用として `archive/agy-runtime/docs/RECOVERED_R6_R8_RUNTIME_AGY.md` へ文言を変えずに移した（参照はユーザーから求められた場合のみ）。本書に残るのは次の節である。
>
> | 節 | 扱い |
> |---|---|
> | §1 R.5 commodity matching | **有効**（変更なし） |
> | §2, §3 | archive へ移動。LLM に対して有効な安全境界（未知ツール拒否・引数検証・LLM 出力から DB/DAO/FS/シェル/OS キーボード/ゲームプロセスへ直接経路を作らない）は `ELITEINTEL_INTEGRATION_PLAN.md` §R.6「維持する安全境界」に引き継いだ |
> | §4 development-time agy boundary | **有効**（開発時の `agy` 利用は本決定の対象外） |
> | §5 STT/TTS | **有効**。ただし「AI response generation remains the responsibility of `agy`」は「LLM（Gemma 4 E4B）」と読み替える |
> | §6〜§9 | archive へ移動。Offline Assistant は LM Studio + Gemma 4 E4B（§R.6）。AI Provider 契約は §R.8、E2E 安全契約は §R.6 を正とする |

## 1. R.5 — Commodity Japanese matching

J-5 commodity matching must use the existing multilingual commodity database architecture.

- Extend the existing multilingual commodity DB column structure with Japanese according to the existing schema/migration convention.
- Extend `FuzzySearch.fuzzyCommodityMatch` with the Japanese locale branch.
- Japanese commodity names resolve deterministically to the canonical English commodity name.
- Do **not** create a new generic dictionary framework.
- Do **not** use LLM free translation as the commodity lookup mechanism.
- An unknown Japanese commodity name must not be searched or saved as an assumed commodity.
- Existing English and other-language matching must remain unchanged.

This supersedes the older wording that described J-5 as a separate static/generic commodity dictionary.

## 2. / 3. （archive へ移動）

実行時 `agy` の hybrid routing と runtime safety boundary は `archive/agy-runtime/docs/RECOVERED_R6_R8_RUNTIME_AGY.md` へ移した（未運用）。現行は `ELITEINTEL_INTEGRATION_PLAN.md` §R.6。

## 4. Development-time agy safety boundary

The coding-agent use of `agy` is a separate threat model from runtime `agy`.

The protocol in `P2_J14_J16_IMPLEMENTATION_SPEC.md` must not depend solely on `agy` self-reporting compliance.

### 4.1 Worktree isolation

Coding-agent work must occur in a dedicated worktree/branch rather than directly in the user's primary working tree or `main` checkout.

Required flow:

```text
main
  ↓
dedicated worktree / branch
  ↓
agy coding agent
  ↓
changes
  ↓
human diff review
  ↓
explicit approval
  ↓
commit / merge
```

### 4.2 Fixed allowlist

The allowed files must be supplied by the launcher/session configuration and checked externally.

`agy` must not be allowed to define its own allowed-file list.

The enforcement layer must reject writes outside the fixed allowlist.

### 4.3 Human approval

`READY FOR APPROVAL: YES` is an informational report only. It is not an approval mechanism.

A human must explicitly approve the diff before the change is committed/merged.

Self-approval by `agy`, an automatic approval step, or treating the START REPORT as approval is prohibited.

### 4.4 Change/session bounds

The development wrapper should enforce bounded damage with explicit limits for:

- maximum session duration;
- maximum changed-file count;
- maximum changed-line count where practical;
- maximum command/tool-call budget where practical.

Exceeding a bound is a STOP condition, not an instruction for `agy` to continue.

### 4.5 STOP conditions

The external enforcement layer must stop the session when any of the following occurs:

- a write outside the fixed allowlist is attempted;
- a forbidden path is touched;
- the worktree/branch boundary is violated;
- a command/tool outside the permitted command set is attempted;
- the phase/specification and actual repository state disagree;
- a required test fails unexpectedly;
- a session/change limit is exceeded;
- `agy` attempts to modify the plan/spec itself without explicit authorization.

START/END reports remain useful audit information, but are not a security control.

## 5. R.7 — STT / TTS separation and AivisSpeech

STT and TTS are independent Provider contracts. They are not `agy` internal features.

### TTS

The selected TTS engine is **AivisSpeech Engine**.

- AivisSpeech is a separate local TTS engine.
- AI response generation remains the responsibility of `agy`.
- AivisSpeech only receives the text selected for speech and synthesizes audio.
- AivisSpeech availability/failure must not make text AI conversation unavailable.
- Voice-model selection and its model license are separate concerns from the engine itself and must be specified before packaging a model.

### STT

Japanese STT remains a separate implementation task.

- The bundled `ParakeetSTTImpl` is not treated as Japanese STT.
- A Japanese STT backend must be selected and verified in v2-P5.
- STT output is untrusted user input and must pass through the same validation/command boundary as typed input.

## 6.〜9. （archive へ移動）

Offline/Ollama、provider contract split、end-to-end safety contract、implementation gate（いずれも実行時 `agy` 前提）は `archive/agy-runtime/docs/RECOVERED_R6_R8_RUNTIME_AGY.md` へ移した（未運用）。現行は `ELITEINTEL_INTEGRATION_PLAN.md` §R.6・§R.8。
