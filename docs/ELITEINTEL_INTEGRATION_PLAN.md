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
| 6 | ~~Collection state~~ Aleoida MATCH candidates on the HUD | Reordered in execution (§13): showing a result turned out to be the natural next step after Phase 5's boundary proof, ahead of collection-state tracking. Renumbered here rather than left silently mismatched with what commit `a99127939` actually built, per §0's ground rule |
| 7 | Collection state | Scanned/collected state tracked and reflected in the HUD (the original Phase 6) |
| 8 | All species | Every C-CORE genus wired through the same boundary as Phase 5 |
| 9 | Navigation integration | Distance/jump-count context (per `EDpjKinsaku`'s `DESTINATION_ETA_SPEC`) surfaced in EliteIntel |
| 10 | Exobiology value/ranking | `EDpjKinsaku`'s value/ranking model surfaced as prioritized recommendations |
| 11 | AI conversation surface | Text input to VEGA (done, see below); VOICEVOX as a `TtsProvider` option (not started); C-CORE result injection into AI chat (done, §14) |

## 3. Phase 4 decisions (provisional, 2026-09-12)

Direction set for the four open questions in §7, not yet implemented:

1. **Transport:** ~~subprocess + stdio JSON (tentative first choice)~~ **Done on both sides**,
   `EDpjKinsaku` commit `3e87288` (`edpj bio evaluate` reads `{"genus", "body"}` JSON on stdin, writes
   the aggregated `RuleEvaluation` list as JSON on stdout) and EliteIntel commit `890531a1a`
   (`elite.intel.bio.ccore.CCoreAdapter`, a one-shot `ProcessBuilder` call — see §11). Not wired into
   `ScanEventSubscriber`/the HUD yet; that is Phase 5.
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
| 4 | Adapter done on both sides | `edpj bio evaluate` (EDpjKinsaku `3e87288`) + `CCoreAdapter` (EliteIntel `890531a1a`, see §11) |
| 5 | Aleoida vertical slice done | `SAASignalsFoundSubscriber` → `CCoreAdapter` → `LocationDto.speciesEvaluations` (EliteIntel `ae4270496`, see §12), scoped to Aleoida only |
| 6 | Aleoida MATCH candidates on the HUD | `ExobiologyObjectiveSource` extended, not a new card (EliteIntel `a99127939`, see §13); NO_MATCH/INSUFFICIENT_DATA intentionally not shown |
| 7–10 | Not started | No collection-state tracking, no other genus wired, no navigation/value-ranking integration |
| 11 (C-CORE → AI chat) | Not started | No C-CORE result has been injected into a VEGA prompt/response yet |
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

## 11. C-CORE Adapter (2026-09-12)

Read-only investigation into how EliteIntel's runtime can actually launch `edpj bio evaluate`, followed
by the adapter itself (EliteIntel commit `890531a1a`) — scoped to the adapter alone, not wired into
`ScanEventSubscriber` or the HUD (that is Phase 5).

### Investigation findings

- **`edpj.exe` (the pip-installed console script) is not reliably invokable.** `pip show edpj` reports
  it editable-installed from this checkout, but the generated `edpj.exe` lives in
  `AppData\Roaming\Python\Python311\Scripts\`, a directory that is **not on PATH** (PATH only carries
  `AppData\Local\Programs\Python\Python311\Scripts`). `where edpj` fails; confirmed, not assumed.
- **`python -m app.cli bio evaluate` works from any working directory** — tested from inside the
  EliteIntel checkout itself, nowhere near EDpjKinsaku. Because `edpj` is pip-installed in *editable*
  mode, `app.cli` is importable via site-packages regardless of CWD. This means the adapter needs no
  knowledge of where EDpjKinsaku lives on disk — only a working Python interpreter.
- **Cold latency measured at ~450-480ms** across three runs, dominated by Python startup plus
  `app/cli/__main__.py` eagerly importing every subcommand module (`api`, `backfill`, `calibration`,
  `collector`, `state`, `bio`) even though only `bio` is used. Sets the floor for timeout design; not
  optimized in this pass.
- **EliteIntel's existing subprocess conventions** (`Updater.java`, `NativeHudOverlay.java`): explicit
  UTF-8 on all streams, stderr separated via `redirectError(Redirect.PIPE)`, bounded
  `process.waitFor(timeout, TimeUnit)`, `process.destroy()`/`destroyForcibly()` as the backstop. Both
  existing uses are fire-and-forget or persistent line-protocol children; neither is the one-shot
  write-stdin-then-read-stdout-then-wait shape this adapter needed, so `CCoreAdapter` follows the
  conventions but not the exact call shape of either.
- **Existing path-configuration pattern**: `PlayerSession.setJournalPath()`/`getJournalPath()` via
  `DirectorySetting` + a DB-backed DAO, "never throws, falls back to a default" read semantics. Noted as
  the template for a future Python-path setting, deliberately not built yet (see decisions below).

### Decisions

- **Python path:** stays a hardcoded constant (`CCoreAdapter.PYTHON_COMMAND = "python"`, resolved via
  PATH), not a Settings/DB-backed value. Adding that is deferred until the adapter itself is proven, to
  keep this change scoped to the CLI boundary alone — it can be added later following the
  `PlayerSession` pattern above with no change to the adapter's public shape.
- **Scope:** the adapter only — `BodyContext`/`RuleEvaluation`/`RuleStatus` (field-for-field mirrors of
  EDpjKinsaku's dataclasses, including JSON field names via `@SerializedName`) and
  `CCoreAdapter.evaluate(genus, body)`. No call site in the real scan pipeline yet.

### Implementation (`elite.intel.bio.ccore`)

`CCoreAdapter.evaluate(String genus, BodyContext body)`: serializes the request with the shared
`GsonFactory` Gson instance, starts `python -m app.cli bio evaluate` via `ProcessBuilder`, writes the
request to stdin, drains stdout/stderr concurrently on separate threads (`StreamCollector` — necessary
because reading either stream sequentially risks a deadlock if the child fills that pipe's OS buffer
first), waits up to 5 seconds, and returns the parsed `List<RuleEvaluation>`. Throws
`CCoreAdapterException` (unchecked, deliberately silent on how a caller should present a failure — this
class has no caller yet to design that for) on: process failed to start, timeout, non-zero exit
(including EDpjKinsaku's `KeyError` for a genus not yet converted), or unparseable stdout.

**Verified:** `:app:compileJava`/`:app:compileTestJava` succeed. `CCoreAdapterJsonTest` (5 tests, no
process involved — request field names, null-field omission, response parsing, malformed-JSON and
empty-body error paths) and `CCoreAdapterIntegrationTest` (4 tests, the real subprocess — Aleoida Arcus'
boundary body comes back `MATCH`, genus matching is case-insensitive, an empty body comes back
`INSUFFICIENT_DATA` for all four Fumerola species, an unconverted genus like `"Tussock"` fails with a
clean error naming the genus rather than an empty list) all pass. Full suite: 3116 tests, 9 failures,
all the same pre-existing `JukeboxPlayerTest`/`TagScannerTest` failures already confirmed unrelated.

## 12. Phase 5: Aleoida vertical slice wired (2026-09-12)

The first real call from the scan pipeline into `CCoreAdapter` (EliteIntel commit `ae4270496`).
Deliberately narrow: one genus, one new storage field, no HUD/AI consumer yet.

### Where and how

- **Trigger:** `SAASignalsFoundSubscriber.onSAASignalsFound()`, immediately after
  `location.setGenus(...)` inside the per-body `locationManager.updateBody(...)` lock. By the time a
  `SAASignalsFound` (DSS) event arrives, an earlier `Scan` event has normally already populated the
  body's physical fields via `ScanEventSubscriber` — this is why genus arrival, not the scan itself, is
  the natural trigger.
- **Genus name mismatch found and bridged:** `GenusDto.genusSymbol` holds Frontier's journal stem
  (`"Aleoids"`), not the English display name C-CORE's `evaluate_genus()` dispatches on (`"Aleoida"`) —
  confirmed by reading `BioForms.java`'s own `genus("Aleoids", "Aleoida", ...)` registration, not
  assumed. Bridged with the already-existing `BioForms.englishGenusName()`, adding no new lookup table.
- **Scope guard:** `SAASignalsFoundSubscriber.CCORE_GENUS_SLICE = "Aleoida"` — only a body whose
  detected genuses include Aleoida (via the bridge above) ever reaches `CCoreAdapter`. C-CORE has six
  genera converted; this phase proves the connection with one before widening it (Phase 7).
- **Storage:** no existing field held anything like this — checked `LocationDto`, `GenusDto`, and
  `BioSampleDto` directly rather than assuming one existed. `BioSampleDto` is what has actually been
  scanned, a different concept from a body-conditions candidate list, so reusing it would have
  conflated the two. Added `LocationDto.speciesEvaluations` (`List<RuleEvaluation>`), mirroring the
  Phase 3 `surfacePressure` pattern — one new field, no new subsystem.
- **Failure handling:** `CCoreAdapterException` is caught inside
  `evaluateCCoreSliceIfPresent()` and logged, never propagated — the signal/announcement processing
  around this call must keep working whether or not C-CORE (or Python) is available on the machine.

### Not done in this phase (by design)

HUD display, AI/chat consumption, VOICEVOX, cross-checking against completed `BioSampleDto` samples,
and widening past Aleoida to the other five converted genera are all left for later phases.

**Verified:** `:app:compileJava`/`:app:compileTestJava` succeed. `SAASignalsFoundCCoreSliceTest` (2
tests) passes via `subscriberTest` — the dedicated Gradle task for this package
(`elite.intel.junit.gameapi.journal.subscribers.*`), since the default `test` task excludes it for
timing reasons (an existing, unrelated project convention, not something this change introduced):
Aleoida Arcus' exact boundary body comes back `MATCH` end-to-end through the real CLI and lands on
`LocationDto.speciesEvaluations`; a Tussock-only body never triggers a C-CORE call at all. Default
`test` task: 3116 tests, 9 failures, all the same pre-existing `JukeboxPlayerTest`/`TagScannerTest`
failures already confirmed unrelated.

## 13. Phase 6: Aleoida MATCH candidates on the HUD (2026-09-13)

First surfacing of a C-CORE result to the commander (EliteIntel commit `a99127939`). Renumbered ahead
of the original Phase 6 ("Collection state") — see §2 — because showing a result was the natural next
step once Phase 5 proved the boundary, not because collection-state tracking stopped mattering.

### Design

- **Extends the existing card, does not add a new one.** `HudObjectiveSource` implementations compete
  for one shared display slot (`HudObjective.priority`); `ExobiologyObjectiveSource` already owns the
  exobiology slot (genus list + sample progress, `PRIORITY_AMBIENT`). A second exobiology-flavoured card
  would only have fought that one for the same slot, so this phase extends `card()` instead.
- **Aleoida only**, matching Phase 5's slice. A body's genus row gets extra rows — one per species in
  `LocationDto.speciesEvaluations` with `status == MATCH` — only when that genus resolves (via the same
  `BioForms.englishGenusName()` bridge Phase 5 uses) to `"Aleoida"`.
- **Only `MATCH` reaches the HUD.** `NO_MATCH` and `INSUFFICIENT_DATA` are C-CORE's internal reasoning,
  not something the commander asked to see; they stay on `LocationDto.speciesEvaluations` for any later
  consumer (e.g. Phase 11's AI chat injection) but are filtered out of the card by `matchedSpecies()`.
- **A checkmark, not translated text.** The match row's value is a bare `✓`, not an i18n key — `MATCH`
  is C-CORE's vocabulary, not HUD copy, and a symbol needs no translation in any language.
- **Zero matches ⇒ unchanged card.** A body with no `MATCH` species (including one with no
  `speciesEvaluations` at all, e.g. before Phase 5 ran or when C-CORE was unreachable) renders exactly
  as it did before this phase — the existing genus/sample-progress row only.
- **Row budget is now a shared running total**, not a fixed per-genus slice: the existing six-row card
  budget (`MAX_GENUS_ROWS`) is spent across genus rows and species-match rows together, since Aleoida
  alone can contribute up to five extra rows. The existing "+N more" overflow row already existed for
  when genuses do not fit; it now also fires when match rows crowd out later genuses.

### Not done in this phase (by design)

Widening past Aleoida, showing `NO_MATCH`/`INSUFFICIENT_DATA` anywhere, AI/chat consumption, VOICEVOX,
and collection-state tracking (the original Phase 6/now Phase 7) are all left for later phases.

**Verified:** `:app:compileJava`/`:app:compileTestJava` succeed. `ExobiologyObjectiveCardTest`: 16
tests (12 existing + 4 new — match rows appear in order immediately after the genus row; zero matches
leaves the card byte-for-byte unchanged; species evaluations on the body but for a non-sliced genus
never leak into that genus's row; Aleoida's five matches plus two more genuses correctly overflow
rather than exceed the row budget), all pass. Default `test` task: 3120 tests, 9 failures, all the same
pre-existing `JukeboxPlayerTest`/`TagScannerTest` failures already confirmed unrelated. `subscriberTest`
(Phase 5's async wiring): unaffected, still passing.

## 14. Phase 7: C-CORE exobiology fact source for AI chat (2026-09-13)

First consumption of a C-CORE result by VEGA's AI chat, via the existing `MemoryFactSource` extension
mechanism (read-only investigation confirmed this mechanism already exists and is auto-wired — no new
architecture was introduced). Reverses this plan's earlier "HUD next" direction (§2's original ordering
of Phase 6 before Phase 11): once the investigation showed AI chat already has a formal fact-injection
path, wiring C-CORE into it was the smaller, more natural next step than further HUD work.

### Design

- **New file:** `elite.intel.ai.brain.vega.memory.facts.sources.ExobiologyCandidateFactSource`,
  `@RegisterMemoryFactSource`-annotated exactly like every other fact source — no registry/dispatcher
  changes needed, auto-discovery picks it up.
- **Reads only already-persisted state.** `factsFor()` reads `LocationDto.speciesEvaluations` — the
  field Phase 5's `SAASignalsFoundSubscriber` already populates by calling `CCoreAdapter` at scan time.
  This source **never** constructs a `CCoreAdapter` or shells out to the C-CORE CLI itself; the chat path
  and the scan-time evaluation path stay fully decoupled, matching the explicit constraint that a chat
  turn must not trigger a fresh C-CORE process call.
- **`MATCH` only**, same filter `ExobiologyObjectiveSource` (Phase 6) already applies to the HUD — both
  are independent readers of the same `LocationDto` field, neither depends on the other.
- **Relevance reuses existing alias vocabulary.** `isRelevant()` calls the shared
  `LocalizedFactRelevance.matches()` helper with the existing `query_exobiology_samples` and
  `query_biome_analysis` keys from `ai_action_aliases.properties` (already covering phrases like "what
  organisms are on this planet" / "what life is here" in every supported language) — no new alias
  vocabulary invented.
- **Situational gate matches `CurrentBodyFactSource`'s `AT_BODY` set exactly** (ship landed/gliding/in
  orbit/in a ring, in an SRV, or on foot on a planet), checked before the `LocationManager` read so a
  not-at-body turn never touches the DB.
- **Genus-agnostic by design.** The fact source has no genus knowledge of its own — it reports whatever
  C-CORE already decided and stored, whichever genus that happens to be (Aleoida only, for now, per
  Phase 5's scope). Widening C-CORE's genus coverage (Phase 8) widens this fact source automatically,
  with no changes needed here.

### Not done in this phase (by design)

Re-invoking the C-CORE CLI from the chat path, changing `CCoreAdapter`/`SAASignalsFoundSubscriber`/the
HUD/the CLI, and inventing new relevance vocabulary were all explicitly out of scope and not touched.

**Verified:** `:app:compileJava`/`:app:compileTestJava` succeed. New `ExobiologyCandidateFactSourceTest`
(9 tests: single match, multiple matches preserving order, `NO_MATCH`-only ⇒ empty, `INSUFFICIENT_DATA`-
only ⇒ empty, empty list ⇒ empty, null list ⇒ empty, relevant for an exobiology-samples-style query,
relevant for a biome-analysis-style query, not relevant for an unrelated query) all pass. Default `test`
task: 3129 tests, 9 failures, the same pre-existing `JukeboxPlayerTest`/`TagScannerTest` failures already
confirmed unrelated — no regression from this change.

Example fact line this source contributes to VEGA's `<facts>` block when Aleoida Arcus and Aleoida
Gravis both evaluate to `MATCH` on the current body:

```
C-CORE exobiology candidates: Aleoida Arcus, Aleoida Gravis
```
