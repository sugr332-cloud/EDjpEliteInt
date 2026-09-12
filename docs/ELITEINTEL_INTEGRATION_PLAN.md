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
| 2 | Japanese localization | `Language.JA` exists and the P0 priority set (core UI, AI chat, HUD, navigation, announcements, log, setup warnings) is translated |
| 3 | BodyContext Adapter | A component that reads EliteIntel's current system/body/journal state into a shape `EDpjKinsaku` can consume |
| 4 | C-CORE integration boundary | Defined interface between EliteIntel and `EDpjKinsaku`'s prediction engine (in-process call, HTTP, or CLI — undecided) |
| 5 | Aleoida vertical slice | One genus (Aleoida) working end-to-end in a real game session as proof of the boundary |
| 6 | Collection state | Scanned/collected state tracked and reflected in the HUD |
| 7 | All species | Every C-CORE genus wired through the same boundary as Phase 5 |
| 8 | Navigation integration | Distance/jump-count context (per `EDpjKinsaku`'s `DESTINATION_ETA_SPEC`) surfaced in EliteIntel |
| 9 | Current Body HUD | A dedicated HUD panel/overlay showing the current body's exobiology prediction |
| 10 | Exobiology value/ranking | `EDpjKinsaku`'s value/ranking model surfaced as prioritized recommendations |
| 11 | AI conversation surface | Text input to VEGA (done, see below); VOICEVOX as a `TtsProvider` option (not started) |

## 3. Phase 4 decisions (provisional, 2026-09-12)

Direction set for the four open questions in §7, not yet implemented:

1. **Transport:** ~~subprocess + stdio JSON (tentative first choice)~~ **Done**, `EDpjKinsaku` commit
   `3e87288`: `edpj bio evaluate` reads `{"genus", "body"}` JSON on stdin and writes the aggregated
   `RuleEvaluation` list as JSON on stdout (verified with a real `... | python -m app.cli bio evaluate`
   pipe). This lives entirely in `EDpjKinsaku` — nothing on the EliteIntel side calls it yet.
2. **Genus dispatch:** ~~add `evaluate_genus(name, body)` on the `EDpjKinsaku` side~~ **Done**, same
   commit: `evaluate_genus(name, body)` in `app/bio/c_core.py`, dispatching by lowercased genus name to
   the six converted evaluators, raising `KeyError` (not a silent empty list) for the other 13.
3. **`gravity` unit/scale parity:** **Investigated, no conversion needed** — see §9. `LocationDto.gravity`
   (Earth-g, EliteIntel's own recomputed value) and C-CORE's `BodyContext.gravity`/`min_gravity`/
   `max_gravity` (also Earth-g) are the same unit and scale. The raw journal `SurfaceGravity` (m/s²)
   must never be the value passed — which EliteIntel's existing code already avoids, for a different
   documented reason (accuracy, not units). A single live-game spot-check during the Phase 5 Aleoida
   slice is still worth doing as final confirmation, but nothing here blocks starting Phase 4 design.
4. **`pressure`:** ~~wire `ScanEvent.getSurfacePressure()` into `LocationDto` in Phase 3.~~ **Done**,
   commit `4e0882259`: added `LocationDto.surfacePressure` (mirroring the existing zero-guard pattern
   used by `surfaceTemperature`/`gravity`) and one assignment in
   `ScanEventSubscriber.onScanEvent()`. Verified via `:app:compileJava`/`:app:compileTestJava` and 24
   passing tests across the new `LocationDtoPressureTest` plus five pre-existing `LocationDto`/`ScanEvent`
   test classes (no effect on other consumers).

## 4. Phase 2 audit (read-only, 2026-09-12)

Scope for this pass, by explicit decision: gap audit only, not a translation effort. Findings:

- `elite.intel.i18n.Language` has no `JA` constant (`EN, RU, UK, DE, FR, ES, PT, PTBZ, IT`).
- No `gui_ja.properties` (or any `*_ja.properties`) exists anywhere under
  `app/src/main/resources/i18n/` — every other supported language has one.
- `Language.isGameLocalized()` would correctly exclude `JA` if added: Elite Dangerous ships no
  Japanese client, so Japanese would be treated like Italian/Ukrainian today (English client, English
  in-game noun names, translated app UI).
- Material name aliasing spans "all nine" languages via dedicated DB columns
  (`db-migration/01003__schema.sql`, `01017__schema.sql`, read by `MaterialNameDao`) — a tenth
  (`name_ja`) would be needed for Japanese voice input to match material names the way it does for the
  other nine.

Full localization (translating `gui.properties` and the other per-language resource families such as
`ed_events_*`/`ai_action_aliases_*`, plus the DB alias column) is deliberately **not** undertaken now.
It is a separate, large future task, tracked here rather than started.

**Update (2026-09-12):** the audit above was superseded by real implementation, not just planning.
`Language.JA` now exists (commit `11907a780`), all 14 `switch(language)` call sites this required are
handled (see §10), and the P0 priority set — core UI labels, Vega/AI chat tab, HUD quick-status badges,
navigation HUD card labels, announcements, log messages, setup/first-run warnings, about 140 keys — is
translated in `gui_ja.properties`. The remaining ~640 `gui.properties` keys and the three other bundle
families (`responses`/`ed_events`/`ai_action_aliases`, 831 keys total) are still not translated;
`_ja.properties` stub files exist for them only so `BundleKeyParityTest`/`BundleQuotingTest` have a
file to load, and every resulting gap is declared honestly in
`app/src/test/resources/i18n-parity-baseline.txt` rather than silently missing. The `name_ja` material
DB column is still not added.

## 5. Verified status (as of 2026-09-12)

Only entries backed by an actual command run or a real file are listed as done. Everything else is
"not started," regardless of what any prior conversation said.

| Phase | Status | Evidence |
|---|---|---|
| 0 | Done | `sugr332-cloud/EliteIntel` fork exists; `./gradlew :app:compileJava` → BUILD SUCCESSFUL (JDK 21.0.12 Temurin) |
| 1 | Done | AI input pipeline traced: `UserInputEvent` (`app/src/main/java/elite/intel/gameapi/UserInputEvent.java`) → `VegaSubsystemGate.onUserInput()` → `ThoughtDispatcher` → LLM → `AiResponseLogEvent` → `AiTabController` → `AiTabPanel` |
| 2 | Audited, not implemented | See §4 |
| 3 | Partially started | `pressure` field wired (commit `4e0882259`); no `BodyContext` adapter class exists yet — that is the rest of Phase 3 |
| 4 | CLI boundary done on the `EDpjKinsaku` side | `edpj bio evaluate` (EDpjKinsaku commit `3e87288`); no EliteIntel code calls it yet — that is the rest of Phase 4 |
| 5–10 | Not started | No HUD panel, no live call from EliteIntel into the CLI exist in this repo |
| 11 (text input) | Done | See §6 below |
| 11 (VOICEVOX) | Not started | `TtsProvider` enum only has `KOKORO` / `GOOGLE` / `EDGE` |

`EDpjKinsaku` C-CORE progress (verified via `git log`, branch `bio-c-core-validation`, last relevant
commit `3e87288`, 2026-09-12): 6 of 19 genera converted (31 species / 65 rulesets), `bioscan-count`
20/116/254/0 PASS, `pytest` 707 passed (699 baseline + 8 for `evaluate_genus`/the new CLI). The CLI
boundary (`edpj bio evaluate`) now exists and is independently verified; nothing in EliteIntel calls
it yet.

## 6. What "Phase 11 text input" actually is

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

## 7. Phase 4 investigation (read-only, 2026-09-12)

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

These four questions are the ones resolved (three provisionally, one still genuinely open) in §3.

## 8. Division of responsibility with EDpjKinsaku

`EDpjKinsaku` (C-CORE) owns species/ruleset prediction logic and stays genus-by-genus, independently
testable via `pytest` and `bioscan-count`, with no dependency on EliteIntel. `EliteIntel` owns game
state capture, the HUD/UI, and the AI conversation surface. Phase 4 (§2) is where a boundary between
them gets defined — until then, no EliteIntel code should reach into `EDpjKinsaku` internals or vice
versa.

## 9. Gravity parity investigation (read-only, 2026-09-12)

No code changed to produce this section. Question: can `LocationDto.gravity` be passed straight into
C-CORE's `BodyContext.gravity` (and, through it, `NormalizedRule.min_gravity`/`max_gravity`), or does
it need converting first?

### The two candidate values, and why they differ

| Source | Value | Unit |
|---|---|---|
| Journal `Scan.SurfaceGravity` | e.g. `9.81` in `ScanEventTest`'s fixture | **m/s²** (Frontier's journal convention) |
| `LocationDto.gravity` | computed by `GravityCalculator.calculateSurfaceGravity(massEM, radiusMetres)` | **Earth gravities (g)** — Earth itself = `1.0` |

`ScanEventSubscriber` already discards the raw journal value and stores only the computed one
(`ScanEventSubscriber.java:113-115`: "DO NOT use event.getSurfaceGravity() as it is not accurate").
That comment is about accuracy, not units, but it has the side effect of already avoiding the
unit mismatch this investigation was checking for.

### What C-CORE expects

`app/bio/c_core.py` defines `min_gravity`/`max_gravity` on every genus converted so far (63 bounded
rules across the 6 genera). Every single bound in the file, across every genus, falls in **0.04 to
0.65**:

```
grep -oE 'm(in|ax)_gravity=[0-9.]+' app/bio/c_core.py | sort -t= -k2 -n | (head -3; echo ...; tail -3)
```

Values in that range cannot be m/s² (a body at 0.04-0.65 m/s² would be barely more massive than a
speck of dust) but are exactly the range real Elite Dangerous exobiology occupies in Earth-g (organics
only spawn on low-gravity bodies). `app/bio/body_context.py::body_context_from_parameters()` passes
EDSM's own cached `gravity` column straight through with **no conversion** into `BodyContext.gravity`,
which only makes sense if EDSM's `gravity` field is already Earth-g — matching EliteIntel's own EDSM
DTO (`elite.intel.gameapi.search.edsm.dto.data.BodyData.gravity`), which the codebase treats as
directly comparable to `LocationDto.gravity` (both consumed as Earth-g elsewhere in EliteIntel).

### Anchor data points (existing, real, already trusted by each codebase's own tests)

| Body | Mass / Radius | `LocationDto.gravity` (computed) | Plausible for C-CORE's 0.04-0.65 range? |
|---|---|---|---|
| Earth (exact, sanity check) | 1.0 EM / 6,371,000 m | `1.00` g | No — real Earth has no organics rule to satisfy, correctly outside range |
| Colonia 4 (real, named class III gas giant — `GravityCalculatorTest`) | — | `199.31` g | No — gas giants correctly fall far outside every organics range |
| Synthetic rocky body (`GravityCalculatorTest`, EDSM-style input) | 0.513865 EM / 4,977,078.5 m | `0.84` g | No — above every genus's `max_gravity` (highest is 0.65), i.e. correctly excluded as "too heavy for any organics" |

No currently-available fixture happens to land inside 0.04-0.65 g, so this pass could not show a
`MATCH`-eligible body end-to-end — but that is a fixture-coverage gap, not evidence against parity: all
three anchors behave exactly as physically expected once interpreted as Earth-g, and none would make
sense interpreted as m/s².

### Conclusion

**そのまま渡せる (pass as-is)** — `LocationDto.gravity` and C-CORE's gravity fields are the same unit
and scale. No conversion function is needed in the future `BodyContext` adapter for this field.
Recommended, not required: capture one live `Scan` event from a body already known (via EDSM or
BioScan) to sit inside a genus's gravity range, as a final end-to-end confirmation during the Phase 5
Aleoida slice — this pass used existing anchors rather than a fresh live capture.

## 10. Language.JA implementation (2026-09-12)

`Language` is referenced by 14 `switch(language)` call sites across the codebase (found by re-grepping
every switch on the enum, not just ones using a parameter literally named `language` — three used
`lang`, one switched on `SystemSession.getInstance().getLanguage()` inline). Java requires an
exhaustive switch over an enum to handle every constant, so all 14 needed a `case JA` before the code
would compile. Eleven are locale/text-lookup plumbing (`MultiLingualTextProvider`, `EventsTextProvider`,
`ResponseTextProvider`, `AiActionAliasTextProvider`, `StringUtls`, `PhraseCorrectionSuggestionDialog`,
`NumberWords`, `LocalizedNumbers`, plus the language-name/locale switches) — mechanical, no functional
compromise. Three needed a real decision:

- **`ParakeetSTTImpl.toLangCode()`:** the bundled STT model's vocabulary
  (`distribution/parakeet/tokens.txt`) contains no Japanese characters at all — checked directly, not
  assumed. Japanese speech cannot be transcribed by this model regardless of what language hint is
  passed. Returns `"en"` (documented as "the least-wrong of the codes this method already returns," not
  a working substitute) — voice input stays unavailable for Japanese until a Japanese-capable STT model
  is bundled, which is out of scope here.
- **`InputNormalizerLocalizations` / `AiActionLocalizations`:** since STT produces no real Japanese
  text, both route `JA` through the English rules/aliases rather than building unused Japanese
  voice-command infrastructure for a language STT cannot reach. `FighterAttackTargetPhrasingTest` (a
  safety-critical test guarding the one fighter order that cannot be taken back) needed a matching `JA`
  entry using the same English stems for this reason.
- **`GoogleVoiceProvider` / `EdgeVoiceProvider`:** unlike STT, both cloud TTS providers genuinely
  support Japanese today, so this is completing existing provider capability, not building VOICEVOX.
  Real voice codes: Google `ja-JP` / `ja-JP-Standard-A` (female) / `ja-JP-Standard-C` (male); Edge
  `ja-JP` / `ja-JP-NanamiNeural` (female) / `ja-JP-KeitaNeural` (male). Not added to
  `CHIRP3_HD_LANGUAGES` — that roster's Japanese coverage was not verified against the live API, so
  Japanese stays on the same guaranteed-to-exist Standard tier `pt-PT` uses.

**Translated (P0, commit `11907a780`):** `gui_ja.properties` — core UI labels (tab/button/language
names), the Vega/AI chat tab, HUD quick-status badges, navigation HUD card labels, announcement
toggles, system log messages, and the setup/first-run warnings. About 140 keys. Confirmed in a real
game session by the commander: tabs/buttons/surface UI read correctly in Japanese.

**Translated (P1, commit `5c9716ec2`, 2026-09-12):** the bindings tab, the actions tab (built-in +
custom commands), the input monitor tab, the remaining overlay HUD cards, and the jukebox tab. About
407 more keys.

**Not translated (tracked, not hidden):** the remaining `gui.properties` keys not covered by P0/P1
(mostly `settings.*`/`speech.*`/`player.*`/`automation.*`/`overlay.settings.*`), and the
`responses`/`ed_events`/`ai_action_aliases` bundle families (831 keys — mission/rank/event narration
text and voice-command aliases). `BundleKeyParityTest` requires every declared `Language` to have a
bundle file for every family (a missing file fails the test outright, not just a gap), so minimal stub
`_ja.properties` files exist for the three untranslated families purely to satisfy that. Every gap this
produces is declared line-by-line in `app/src/test/resources/i18n-parity-baseline.txt`, per that file's
own existing mechanism (previously used for exactly one deliberate exclusion; now also carries this
dated, explained backlog — down to ~1,111 lines after P1, from 1,485 after P0).

**Fixed in passing:** `ai.chatInput.send` and `language.japanese` were missing from all 8 other
translated languages (`BundleKeyParityTest` caught this too) — `ai.chatInput.send` dates back to the
Phase 11 text-input commit `875e3f597`. Both are now translated in all 8, not baselined.

**`DisplayNumeralsTest` exemption:** its number-spellout round-trip test is skipped for `JA`. It finds
a spelled figure by scanning for space-delimited alphabetic word boundaries; Japanese text has no
spaces between words, so the scan cannot locate a spelled figure even though ICU spells it correctly
in isolation (confirmed: `NumberWords.of(100, Language.JA)` correctly produces "百"). Revisit once
Japanese TTS narration is a real path (VOICEVOX, Phase 11) rather than an unused one.

**Verified:** `:app:compileJava`/`:app:compileTestJava` succeed; full suite 3107 tests, 9 failures, all
confirmed pre-existing via `git stash` (identical failures with this change removed) and unrelated
(`JukeboxPlayerTest`/`TagScannerTest`, environment-specific audio file issues); a real launch (a second
instance alongside one already running, only its own new PID touched) reached full startup —
`SetupCheck`/`KeyBindCheck`/`DeviceService` all completing — with no new exception.
