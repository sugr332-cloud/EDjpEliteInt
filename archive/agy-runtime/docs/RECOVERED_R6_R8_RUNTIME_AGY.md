# 未運用: Recovered R.6–R.8 runtime agy decisions（退避分）

**Status:** 未運用（archived, 2026-09-25）
**出典:** `docs/RECOVERED_R5_R8_AGY_SAFETY.md`（`main` = `665b18071`）の §2・§3・§6〜§9 を**文言を変えずに**移した。節番号は元のまま。
**現行方針:** 実行時の AI 会話は LLM（LM Studio + Gemma 4 E4B）に一本化した。`docs/ELITEINTEL_INTEGRATION_PLAN.md` §R.6 を正とする。
**参照ルール:** 実装時にこの文書を参照するのは、ユーザーから明示的に求められた場合のみとする（`archive/agy-runtime/README.md`）。

---

## 2. R.6 — AI conversation hybrid routing and agy role

> **改訂注記（2026-09-20 / G-5 二重ルーティング改訂）:**  
> 本節は 2026-09-15 時点で「すべての AI 会話を `agy` で実行する」方針として記録されたが、その後の G-5 実装（`201b0ad2`、PR #9 は G-7）および本番検証を経て、**非 speak ツールを含むターンは既存 LLM Provider（LM Studio / クラウド API）、非 speak ツールを含まないターン（雑談・要約・speak のみ）は `agy`** へ振り分ける二重ルーティング（`TurnRoutingLlmGateway`）に改訂された。  
> 現在の本番構成、ルーティング判定境界、および常駐プロセス（resident agy）の仕様については `docs/ELITEINTEL_INTEGRATION_PLAN.md` §R.6.2 および §R.6.4 を参照すること。

Under the G-5 architecture (`201b0ad2`), AI conversation turns are routed by `TurnRoutingLlmGateway` based on the presence of non-speak tools:

- **Tool-calling turns (requests containing non-speak tools):** Routed to the configured LLM provider (LM Studio / Cloud API).
- **Chat turns (requests with no non-speak tools, i.e. chat, summarization, or speak-only):** Routed to `agy` (Antigravity CLI resident process via `AgyResidentProcessManager`).

```text
Japanese text / STT
      ↓
UserInputEvent
      ↓
ThoughtDispatcher
      ↓
TurnRoutingLlmGateway
      ├─[non-speak tools present]──→ Configured LLM (LM Studio / Cloud)
      │                                    ↓
      │                              Tool-call execution
      │
      └─[speak-only / no tools]─────→ VegaLlmGateway (agy)
                                           ↓
                                     AgyCliProviderAdapter
                                           ↓
                                     AgyCliTransport
                                           ↓
                                     AgyResidentProcessManager
                                           ↓
                                     agy (resident stream-json)
```

In this architecture, `agy` is responsible for natural-language response generation in chat turns:

- natural-language response generation (chat / conversational speak)
- summary / conversational formatting under strict schema

`agy` does **not** execute EliteIntel tools itself. Tool calls and system actions remain strictly bounded by EliteIntel:

```text
LLM tool call (LM Studio / Cloud)
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
```

### AI provider selection and routing

- `TurnRoutingLlmGateway` performs static, turn-based routing based on whether the prompt requires non-speak tools.
- Chat turns (speak-only) are handled exclusively by `AgyCliProvider` backed by `AgyResidentProcessManager`.
- Tool-calling turns (requiring internal game actions) are handled by the configured LLM provider (LM Studio / Cloud API).
- There is no automatic runtime fallback from `agy` to another LLM provider when `agy` fails (failures are explicit and fail-fast).
- STT and TTS are separate concerns and are not implemented as `agy` features.

### Runtime failure behavior

`agy` startup failure, non-zero exit, timeout, malformed output, and process disconnection are handled explicitly by `AgyResidentProcessManager` (reporting `TRANSIENT` or `MALFORMED_RESPONSE` failures and performing transparent auto-recovery on subsequent turns). The UI must remain responsive at all times. Deterministic commands that do not require AI remain available.

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
