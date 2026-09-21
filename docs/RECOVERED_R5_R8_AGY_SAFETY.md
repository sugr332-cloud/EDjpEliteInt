# Recovered R.5–R.8 decisions and agy safety boundary

**Date:** 2026-09-15
**Status:** Normative recovery record
**Repository:** `sugr332-cloud/EDjpEliteIntel`

> The local uncommitted R.5–R.8 edits were lost before they could be committed. This document records the decisions that had already been established and the safety requirements agreed immediately before the loss. It is normative where it conflicts with older wording in the roadmap/specification.

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

## 2. R.6 — AI conversation is agy-only

> **改訂注記（2026-09-20 / G-5 二重ルーティング改訂）:**  
> 本節は 2026-09-15 時点で「すべての AI 会話を `agy` で実行する」方針として記録されたが、その後の G-5（PR #9）の実装および本番検証を経て、**非 speak ツールを含むターンは既存 LLM Provider（LM Studio / クラウド API）、非 speak ツールを含まないターン（雑談・要約・speak のみ）は `agy`** へ振り分ける二重ルーティング（`TurnRoutingLlmGateway`）に改訂された。  
> 現在の本番構成、ルーティング判定境界、および常駐プロセス（resident agy）の仕様については `docs/ELITEINTEL_INTEGRATION_PLAN.md` §R.6.2 および §R.6.4 を参照すること。

The AI conversation execution path is `agy` (Antigravity CLI) as an external process.

```text
Japanese text / STT
      ↓
UserInputEvent
      ↓
ThoughtDispatcher
      ↓
VegaLlmGateway
      ↓
AgyCliProviderAdapter
      ↓
AgyCliTransport / ProcessBuilder
      ↓
agy
```

`agy` is responsible for AI conversation generation:

- natural-language response generation
- tool-call generation
- response generation after deterministic tool execution

`agy` does **not** execute EliteIntel tools itself.

Tool calls follow this boundary:

```text
agy JSON
   ↓
JSON/schema validation
   ↓
tool allowlist
   ↓
argument validation
   ↓
existing IntelCommand / CommandRegistry
   ↓
deterministic execution
   ↓
structured result
   ↓
agy
   ↓
Japanese response
```

### AI provider selection

- `AgyCliProvider` is the only AI conversation provider in the selected architecture.
- Gemini / Claude / OpenAI API providers are not selectable AI conversation providers.
- There is no automatic fallback from `agy` to another LLM provider when `agy` fails.
- Existing legacy provider code may remain in the repository only where it is required for historical/build compatibility; it must not become an alternative AI conversation path.
- STT and TTS are separate concerns and are not implemented as `agy` features.

### Runtime failure behavior

`agy` startup failure, non-zero exit, timeout, malformed output, and unavailable executable must become explicit AI transport failures. The UI must remain responsive. Deterministic commands that do not require AI remain available.

## 3. R.6 — runtime agy safety boundary

The runtime must not rely on `agy` voluntarily following the safety rules. The boundary is enforced by EliteIntel around the process and tool protocol.

### 3.1 Tool-call enforcement

Unknown tool IDs are rejected. Tool arguments are schema-validated and range/format validated before an existing command is invoked.

`agy` output cannot directly invoke a database, DAO, filesystem, shell, OS keyboard, or game process.

### 3.2 Process isolation

The implementation must treat `agy` as a process tree, not only as one Java child process.

- Timeout handling must terminate the complete process tree/group, including descendants started by `agy`.
- `Process.destroyForcibly()` on the direct child alone is not considered sufficient.
- The actual OS mechanism (for example, Windows Job Objects or an equivalent process-group/cgroup mechanism on Linux) must be selected and verified during implementation rather than assumed.

### 3.3 Timeout bounds

The `agy` timeout must have:

- a configurable normal/default timeout;
- an absolute maximum timeout that cannot be exceeded by configuration.

The actual values are implementation/real-device validation decisions and must not be invented in this document.

### 3.4 Network boundary

The runtime must explicitly define what network access `agy` requires for normal operation and prevent unrelated arbitrary network access where the selected execution environment supports that restriction.

The requirement is not to assume that all network access can simply be disabled: the implementation must distinguish required `agy` communication from arbitrary tool/shell networking.

### 3.5 Working directory and secrets

- Run `agy` from an empty temporary working directory.
- Do not use the repository checkout or a directory containing EliteIntel DB files as the `agy` working directory.
- Do not pass DB paths, DB connection strings, credentials, tokens, API keys, or other secrets through prompts, environment variables, or CLI arguments.
- Do not blindly persist raw `agy` stdout/stderr to a file.

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

## 6. R.7 — Offline Assistant / Ollama

The relationship between `agy` and the v2-P10 Offline Assistant/Ollama CLI provider remains **undecided**.

No automatic Ollama fallback, provider-selection policy, or implementation is introduced by this recovery record.

## 7. R.8 — provider contract split

The provider contracts are separated conceptually:

```text
AI Provider
  └─ AgyCliProvider

STT Provider
  └─ independent STT implementation(s)

TTS Provider
  └─ AivisSpeech
```

AI/STT/TTS provider changes must not require changes to Game Context, C-CORE, Journal state, HUD rendering, or Action Registry contracts.

Gemini/Claude providers are excluded from the AI conversation provider choices. They are not an alternative runtime path for the selected architecture.

## 8. End-to-end safety contract

The intended Japanese conversation path is:

```text
Japanese text / Japanese STT
        ↓
EliteIntel input validation
        ↓
agy
        ↓
validated tool-call JSON OR final response
        ↓
if tool-call:
    allowlist + schema validation
        ↓
    deterministic EliteIntel command
        ↓
    structured result
        ↓
    agy
        ↓
Japanese final response
        ↓
AivisSpeech (optional TTS)
```

At no point may `agy` directly access EliteIntel's database, DAO layer, repository files, shell, OS keyboard, or game-control implementation.

## 9. Implementation gate

Before v2-P3 implementation is considered started, the following must be demonstrated rather than merely documented:

1. `agy` non-interactive invocation works as required.
2. Tool-call JSON contract is machine-validated.
3. Unknown/invalid tool calls cannot reach command execution.
4. The complete `agy` process tree can be terminated on timeout.
5. Default and absolute timeout bounds are enforced.
6. The runtime network boundary is documented and tested for the chosen platform.
7. Runtime audit logging records at minimum session ID, timestamps, process start/exit, timeout/failure state, tool ID, validated arguments/result, and parse/validation outcome without storing secrets.
8. Coding-agent writes are constrained by an externally supplied file allowlist.
9. Coding-agent work is isolated from `main` in a dedicated worktree/branch.
10. Human approval is required before commit/merge.
11. No automatic LLM-provider fallback exists.

If any item cannot be technically enforced or verified, v2-P3 must STOP rather than treating textual instructions as sufficient protection.
