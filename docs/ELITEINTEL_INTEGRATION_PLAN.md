# EliteIntel Integration Plan

**Status:** Draft — this is a forward-looking plan, not a record of completed work.
**Repo:** `sugr332-cloud/EliteIntel` (fork of `SudoKrondor/EliteIntel`, upstream remote `origin`, fork remote `fork`)
**Related repo:** `sugr332-cloud/EDpjKinsaku` (C-CORE exobiology species evaluation engine)

## 0. Ground rule

A design decided in a conversation (with any AI assistant, in any session) is **not** a fact about
this repository until it has been verified against the actual repository state — a file exists, a
commit exists, a test passes. This document exists because that rule was violated once already: an
earlier session asserted this file existed in `EDpjKinsaku` and described completed phases, when in
fact neither the file nor the phases existed anywhere on GitHub. Before citing a phase in this
document as done, verify it (`git log`, a passing test run, a file that can be `cat`'d) and record
what was actually checked, not what was proposed.

## 1. Scope

Bring the exobiology species-prediction logic already built in `EDpjKinsaku` (C-CORE) into
`EliteIntel`'s live HUD, and — separately — make EliteIntel's existing VEGA AI conversation reachable
by typed text in addition to voice, eventually with a Japanese TTS option (VOICEVOX). These are two
independent tracks that happen to share one repo:

- **Track A (Phases 2–10):** C-CORE integration into the EliteIntel HUD.
- **Track B (Phase 11):** AI conversation input/output surface (text input — done; VOICEVOX — not
  started).

Track B does not block Track A or vice versa.

## 2. Phases

| Phase | Content | Completion criteria |
|---|---|---|
| 0 | Repository / build baseline | Fork exists; builds with `./gradlew :app:compileJava` on a clean checkout |
| 1 | EliteIntel read-only inventory | Existing AI conversation pipeline (input → VEGA → LLM → chat display) documented with real file/class names |
| 2 | Japanese localization | UI/HUD text reviewed for `gui.properties`/i18n gaps relevant to the integration |
| 3 | BodyContext Adapter | A component that reads EliteIntel's current system/body/journal state into a shape `EDpjKinsaku` can consume |
| 4 | C-CORE integration boundary | Defined interface between EliteIntel and `EDpjKinsaku`'s prediction engine (in-process call, HTTP, or CLI — undecided) |
| 5 | Aleoida vertical slice | One genus (Aleoida) working end-to-end in a real game session as proof of the boundary |
| 6 | Collection state | Scanned/collected state tracked and reflected in the HUD |
| 7 | All species | Every C-CORE genus wired through the same boundary as Phase 5 |
| 8 | Navigation integration | Distance/jump-count context (per `EDpjKinsaku`'s `DESTINATION_ETA_SPEC`) surfaced in EliteIntel |
| 9 | Current Body HUD | A dedicated HUD panel/overlay showing the current body's exobiology prediction |
| 10 | Exobiology value/ranking | `EDpjKinsaku`'s value/ranking model surfaced as prioritized recommendations |
| 11 | AI conversation surface | Text input to VEGA (done, see below); VOICEVOX as a `TtsProvider` option (not started) |

## 3. Verified status (as of 2026-09-12)

Only entries backed by an actual command run or a real file are listed as done. Everything else is
"not started," regardless of what any prior conversation said.

| Phase | Status | Evidence |
|---|---|---|
| 0 | Done | `sugr332-cloud/EliteIntel` fork exists; `./gradlew :app:compileJava` → BUILD SUCCESSFUL (JDK 21.0.12 Temurin) |
| 1 | Done | AI input pipeline traced: `UserInputEvent` (`app/src/main/java/elite/intel/gameapi/UserInputEvent.java`) → `VegaSubsystemGate.onUserInput()` → `ThoughtDispatcher` → LLM → `AiResponseLogEvent` → `AiTabController` → `AiTabPanel` |
| 2–10 | Not started | No BodyContext adapter, no C-CORE call boundary, no HUD panel exist in this repo |
| 11 (text input) | Done | See §4 below |
| 11 (VOICEVOX) | Not started | `TtsProvider` enum only has `KOKORO` / `GOOGLE` / `EDGE` |

`EDpjKinsaku` C-CORE progress (verified via `git log`, last relevant commit `f03252c`, 2026-09-12):
6 of 19 genera converted (31 species / 65 rulesets), `bioscan-count` 20/116/254/0 PASS, `pytest` 699
passed. This is the Track A starting point once Phase 4 begins.

## 4. What "Phase 11 text input" actually is

This was investigative verification, not a phase deliverable in the sense of the table above. The
question being answered was: *does EliteIntel's existing voice-only AI conversation also work over a
generic text event, without changing VEGA/LLM/TTS?* It does. Concretely:

- Added a `HudTextField` + send button to `AiTabPanel.buildChatInputRow()`
  (`app/src/main/java/elite/intel/ui/screen/AiTabPanel.java`), gated on the same
  `applyServiceState(running)` as other controls.
- Submitting publishes `GameEventBus.publish(new UserInputEvent(text))` — the exact call
  `DiagnosticsInputTailer.feedPhrase()` already used for file-driven test input.
- No changes to `VegaSubsystemGate`, `ThoughtDispatcher`, `AiTabController`, STT, or TTS.
- Verified: `:app:compileJava` succeeds; `VegaSubsystemGateTest` (2/2) and `ThoughtDispatcherTest`
  (20/20) pass; a real launch reached `buildUi()` with no exception; a real commander session
  confirmed the typed input reached VEGA's LLM gateway (it failed at LM Studio connectivity, which
  is a local environment issue unrelated to this change — the pipeline itself was reached).

Committed as its own commit (`875e3f597`) describing exactly this scope — not folded into a
"Phase 11 complete" claim, since VOICEVOX and the C-CORE-context-injection idea discussed in
conversation are still undesigned.

LM Studio connectivity (the local LLM the pipeline reached and failed to call past) is out of scope
going forward: it served only to confirm the existing conversation path is reachable over text, not
as an AI provider this project intends to build on.

## 5. Phase 4 investigation (read-only, 2026-09-12)

Both sides of the boundary already have more structure than assumed. No code was changed to produce
this section — only reading.

### EliteIntel side: current body/system state already available

- `PlayerSession.getInstance().getLocationData()` → `LocationData<Long,Long>` with
  `getSystemAddress()` / `getInGameId()` — "where the commander is right now."
- `LocationManager.getInstance().getLocation(starSystem, bodyId)` → `LocationDto`
  (`app/src/main/java/elite/intel/gameapi/journal/events/dto/LocationDto.java`), the per-body cache
  written by `ScanEventSubscriber.onScanEvent()` on every `Scan` journal event.
- Field mapping from `LocationDto` to C-CORE's `BodyContext` (see below):

  | `BodyContext` field | EliteIntel source | Note |
  |---|---|---|
  | `atmosphere` | `LocationDto.atmosphere` | set from `ScanEvent.getAtmosphereType()`, not `getAtmosphere()` |
  | `body_type` | `LocationDto.planetClass` | set from `ScanEvent.getPlanetClass()` |
  | `volcanism` | `LocationDto.volcanism` | raw `ScanEvent.getVolcanism()` string |
  | `temperature` | `LocationDto.surfaceTemperature` | |
  | `gravity` | `LocationDto.gravity` | **not** the journal's raw `SurfaceGravity` — recomputed via `GravityCalculator.calculateSurfaceGravity(massEM, radius)` because `ScanEventSubscriber.java:113-115` documents the raw journal value as inaccurate. Unit/scale parity with what the C-CORE rulesets were authored against is unverified — needs a real-data check in Phase 5, not an assumption. |
  | `pressure` | **missing** | `ScanEvent.getSurfacePressure()` exists but is never copied into `LocationDto`. Closing this is a Phase 3 task (one field + one assignment in `ScanEventSubscriber`). Until then every pressure-gated rule can only return `INSUFFICIENT_DATA`. |
  | `regions` | not tracked | leave `None` |

- Genus detection already exists, independent of C-CORE: `LocationDto.genus` (`List<GenusDto>`),
  populated from DSS/SAA signals. It already surfaces today as a HUD objective card
  (`ui/overlay/ExobiologyObjectiveSource.java`) showing sample progress (X/3) and genus-level payout —
  but with no species-level prediction. This is the exact gap C-CORE fills, and the natural place to
  plug an evaluation result in once it exists.

### EDpjKinsaku side: the public interface already exists

`app/bio/c_core.py` already defines the contract this plan was describing conceptually:

```python
@dataclass(frozen=True)
class BodyContext:
    atmosphere: str | None
    gravity: float | None
    temperature: float | None
    pressure: float | None
    body_type: str | None
    volcanism: str | None
    regions: frozenset[str] | None = None

@dataclass(frozen=True)
class RuleEvaluation:
    species_code: str
    species_name: str
    status: RuleStatus  # MATCH / NO_MATCH / INSUFFICIENT_DATA / RULE_DEFINITION_ERROR / RULESET_INCONSISTENCY
    reason: str
```

One function per converted genus — `evaluate_aleoida(body: BodyContext) -> list[RuleEvaluation]`,
plus `evaluate_cactoida` / `evaluate_concha` / `evaluate_fonticulua` / `evaluate_frutexa` /
`evaluate_fumerola` for the other 5 done so far — with `aggregate_species_evaluations()` collapsing
multi-ruleset ORs into one verdict per species. There is no genus-name dispatcher yet
(`evaluate_genus(name, body)`); an adapter needs either its own lookup table or a corresponding small
addition on the EDpjKinsaku side.

`app/bio/body_context.py::body_context_from_parameters()` is a second, independent confirmation of
the field mapping above: it adapts EDpjKinsaku's own cached EDSM data into the same `BodyContext`,
and treats `atmosphere_type` (not `atmosphere`) as the `atmosphere` input — matching the EliteIntel
mapping.

### Open questions before Phase 4 can be implemented

1. Transport (subprocess + stdio JSON / localhost HTTP / JNI / port to Java) — undecided; leaning
   toward subprocess+JSON to keep `EDpjKinsaku`'s existing `pytest` suite as the source of truth.
2. Genus-name dispatch: add `evaluate_genus(name, body)` on the EDpjKinsaku side, or keep a lookup
   table entirely in the adapter?
3. `gravity` unit/scale parity between EliteIntel's computed value and what the rulesets were authored
   against — needs a real-data spot check against a known body, not an assumption.
4. `pressure` is entirely unwired on the EliteIntel side (see table above) and blocks any
   pressure-gated rule from returning anything but `INSUFFICIENT_DATA` until added.

## 6. Division of responsibility with EDpjKinsaku

`EDpjKinsaku` (C-CORE) owns species/ruleset prediction logic and stays genus-by-genus, independently
testable via `pytest` and `bioscan-count`, with no dependency on EliteIntel. `EliteIntel` owns game
state capture, the HUD/UI, and the AI conversation surface. Phase 4 (§2) is where a boundary between
them gets defined — until then, no EliteIntel code should reach into `EDpjKinsaku` internals or vice
versa.
