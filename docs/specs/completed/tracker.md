## Polish round (brief 2026-08-17) — ☑ SHIPPED 2026-08-25 (H1–H6 complete)
_Merged from `feat/polish-round`. Brief `docs/handoff/completed/handoff_17_aug_2026_1.txt`, report
`docs/handoff/completed/handoff_17_aug_2026_1_report.txt`, review `docs/handoff/completed/review_feat_polish_round.txt`._
- [H1] ☑ **Tracker archaeology** — found already done by the per-merge tidies and verified section by
  section: no block was left all-☑. Only the "Shipped — archived" summary line was stale (stopped at
  M28); it now lists M29–M35.
- [H2] ☑ **Spec archiving** — verified: `spec-project-profiles.md` and `spec-graph-replay.md` were
  already in `completed/`; the remaining mentions are textual and one link already points there.
- [H3] ☑ **Screenshots capture ONE WINDOW, not a screen region** — `screencapture -l <CGWindowID>`,
  the id found via a JXA `CGWindowListCopyWindowInfo` lookup matched on the OWNER PID the app
  publishes in its own rest-endpoint file (owner name and window title both proved unusable: a
  `java -jar` window's owner is the main class, and titles need Screen Recording permission). 14 of 15
  shots are window captures and the mode is printed per shot, so a silent fall back to a region
  capture shows up in the log. The MENU shot stays a region capture — a Swing popup is its own window
  and `-l` takes one id — with the residual risk stated at the call site.
- [H4] ☑ **"All routes" is bounded at a sink, and says so** — `TopologyFocus.routes` +
  `FocusStack.routesInWorld` share one rule (`ROUTE_HOP_BOUND=3`, `DEGENERATE_SHARE=0.5`,
  `BOUND_MIN_GRAPH=40`), a `≤3 hops` toolbar checkbox, the status line naming what the unbounded
  answer would have been, and `scopeBounded`/`scopeNote` in the echo. **Review P1 fixed before merge:**
  the bound was reachable only from the checkbox, so `topology {scope:"routes"}` narrowed an agent's
  answer while the echo told it to untick a control it cannot reach — the M35.7 / N2 shape again. Now
  `topology {routeBound: false}` lifts it, `routeBound` is echoed as state, and the checkbox remains the
  single source of truth so screen and socket cannot disagree. The frozen `scope` enum is untouched.
- [H5] ☑ **Exported pictures fit their labels** — `TopologyCanvas.fitNodeWidthToLabels()` widens the box
  to the longest label before an offscreen render, so a PDF never shows `Category…` with no hover to
  recover it. Screen unchanged: the export uses its own canvas instance.
- [H6] ☑ **The whole-graph report view is readable at scale** — the check FAILED (a 309-node estate
  rendered as a grey band at 8% zoom, the lit path reduced to specks) and was fixed rather than
  restated: above 60 visible nodes the second picture is the cycle's nodes and their immediate
  neighbours, headed "(neighbourhood)", with a caption counting what was left out. Closed by fix, not
  by verification.
- **Review P2 fixed before merge:** `lastRoutes` was written only by `scopedIds()`, which `applyView`
  skips when nothing is selected, so the echo went on reporting a bound for a selection that no longer
  existed. The status line was already safe; the agent-facing surface was not.
- Recorded, not fixed — **P4** H6 guards on `!touched.isEmpty()` where the operative condition is
  `touched ∩ all` non-empty; **P5** `fitNodeWidthToLabels` measures name and id only, and its return
  value has no caller. Neither is reachable in a shipped configuration.

## M35 · Log + graph lifecycle — ☑ SHIPPED 2026-08-25 (the pairing is evidence, not decoration)
_Merged from `feat/m35-lifecycle`. Reviewed by the second session, which found five more
half-cleared states on the log's SMALLER axes and fixed them on the branch — the branch
hunted the log↔graph axis and stopped there. Brief, report and review:
`docs/handoff/completed/`. **Second half, merged 2026-08-25 the same day:** M35.8 `open {project}` (feat/m35-project), M35.10/.11 project-relative committed profiles (feat/m35-relative-roots), M35.9 the `OpenRequest` (feat/m35-open-request) — each reviewed by the other session; their entries follow the original section below._

### M35 second half — .8, .9, .10, .11 (moved from the live tracker 2026-08-25)
- [M35.10] ☑ **Relative profile roots resolve against `.analyser/`, not the project root** _(found driving
  M35.8, report_feat_m35_project O1; DONE 2026-08-25, merged to main)_ — `ProjectProfile.load`
  handed `SettingsShare.preview` the profile's own directory as the base, so a bundle's
  `sourceRoot.0=src/main/java` landed at `<bundle>/.analyser/src/main/java`. The writer never emits
  relative roots, so nothing shipped was hit — but the **M19.2 bundle contract** is built on exactly
  this. Fix: `ProjectProfile.baseDirFor(file)` — the project root for the canonical profile, the file's
  own directory for a loose `.fluxtion-settings`; used by `load` and the Import dialog. Spec-onboarding
  §Contract notes corrected.
- [M35.11] ☑ **Auto-persist rewrites a committed profile's relative roots as absolute** _(found driving
  M35.8, report O2; DONE 2026-08-25, merged to main)_ — open a project, do nothing, switch
  away: the flush wrote the in-memory config back and the writer knew only `~`-relative and absolute
  forms — plus `share.exportedAt` and `Properties.store`'s date comment, two more diffs per write. Fix:
  `SettingsShare.export(c, categories, projectRoot)` writes paths under the project project-relative
  (checked before the `~` rule — a project inside home must not come out `~/…`), omits the timestamp and
  strips the date line; `ProjectProfile.save` returns false and touches nothing when the file already
  holds that text. Round-trip is byte-identical (tests). One-off share exports unchanged.
- [M35.9] ☑ **An `OpenRequest` for load-time side effects** — **DONE 2026-08-25, merged to main** (review F1: a follow rotation had rebuilt the request as human — fixed by the reviewer; `reload(original, provenance)` keeps who asked) (report `docs/handoff/completed/report_feat_m35_open_request.txt`): `OpenRequest
  {fromActionSocket, provenance}` is built where the open starts (verb adapter → `socket(provenance)`;
  chooser/drag/recent/S3 → `HUMAN`; follow rotation → `reload(provenance)`), carried through the async
  load and read once in `onLoaded`. `openFromActionSocket` and `pendingProvenance` are gone; provenance
  arrives WITH the open (`AppControl.openLog(path, format, provenance)`, defaults keep old implementors
  working). Threading it found the FIFTH and SIXTH instances: `open {logs}` never set the flag (its
  time-order modal fired on agents) and `open {log}` on a rolled-set member hit the "open the whole
  set?" confirm — now `context.rolledSetOffer`. R1 (two loads crossing a field) is gone with the field.
  _Original entry:_ **TRIGGER FIRED 2026-08-25, schedule it.** The condition recorded below was "when a fourth appears, or when R1 bites". A fourth
  appeared, and it was not hypothetical: the M35 review's F5 fix (suppressing the time-order modal
  on socket-driven opens) NEVER WORKED, because `maybeOfferProject` consumes `openFromActionSocket`
  59 lines before the time-order gate tests it — and the gate's own comment asserted the opposite
  ordering, which is why it passed review. Fixed tactically on `feat/m35-project` by capturing the
  flag once into a local; this record is the structural fix and is no longer optional._Original
  rationale:_ _(review R1 + R3, 2026-08-25)_ — the
  same shape has now appeared **three times**: the project offer (M35.7), `provenance` (§E) and the
  time-order dialog (review F5) are each "a load-time side effect whose audience differs by who asked
  for the load", and each is currently a field on MainFrame consumed during `onLoaded`. A record
  threaded through the load — `OpenRequest {path, fromSocket, provenance}` — replaces all three and
  removes **R1** with them: today `openFromActionSocket` is a single field, so two loads in flight (a
  verb open racing a drag-drop) could cross it. Rare and serialised in practice, which is why the
  review noted rather than fixed it. _Do this when a fourth appears, or when R1 bites — not before:
  it is a refactor of code that has just been reviewed._
- [M35.8] ☑ **`open {project: path}`** — the lifecycle surface's missing half. **DONE 2026-08-25, merged to main** (report `docs/handoff/completed/report_feat_m35_project.txt`): the verb applies, the echo names every replaced category with before/after counts, what it closed WITH PATHS, the previous project and the one call back; `open {close: "project"}` added so "your own settings" is reachable in one call too (report D1); `context.project` names the settings in force (D5). E7/E8/E10 driven over REST, E9's offer half proven, its keep half stays human (report §6). Brief was `docs/handoff/completed/handoff_25_aug_2026_2.txt`. _(Deferred out of M35
  deliberately: this is the largest single mutation any verb would perform — it replaces source
  roots, event processors, graphs and hidden columns in one call — so it needs its own decision about
  confirmation rather than being smuggled in beside a lifecycle fix. Not started on
  `feat/m35-lifecycle`; build it on its own branch after M35 merges. Previously floated as "M20.6",
  which had no home since M20 is archived; it belongs here because the project IS the session
  boundary M35.5 established.)_ Three arguments accumulated during M35, none of them speculative:
    1. **M35.7's `projectOffer` is currently unactionable.** An agent is told a project is available
       and has no way to accept it — an offer with no accept button.
    2. **M35.5 is read-verified only** (report D10). There is no socket route to a project switch, so
       E7–E10 cannot be driven; this verb turns four eyeball items into a scripted check.
    3. **The surface is asymmetric.** An agent can open and close a log, and open and close a graph,
       but cannot touch the thing that owns both.
  **THE CONFIRMATION DECISION, settled 2026-08-25 so this is workable cold** — the entry above
  deferred M35.8 *for* this question and then left it unanswered, which is the worst kind of handoff.
  The answer follows the rule the rest of the surface already uses rather than inventing a new one:

  · **The verb APPLIES; it does not ask.** A modal cannot be answered at the socket — that is M35.7's
    whole finding, one dialog over — so an "are you sure?" would either hang the call or be silently
    defaulted, and silently defaulting a mutation this size is worse than performing it openly.
  · **What makes it safe is the ECHO, not a prompt.** `open {project}` must name **everything it
    replaced**, with before/after counts: source roots, event processors, named graphs, hidden
    columns, and whether a log and graph were closed (M35.5 — a project switch is a session
    boundary). The close verb's `kept` sentence is the precedent; this is its mirror.
  · **It is reversible and must say so.** The echo names the previously-active project (or "your own
    settings"), so the agent can put it back in one call. A mutation you can undo from the answer you
    were given is a different risk from one you cannot.
  · **The MCP client's own per-call approval is the human gate.** Declaring the verb destructive —
    as `open`, `source_root`, `screenshot` and `report` already are — puts the prompt where a human
    can actually see it, instead of behind a Swing dialog nobody is looking at.
  · **The auto-detect path is unaffected**: `applyProjectResult(result, endsSession=false)` still
    keeps the log when a project is adopted *because* that log was opened.

  Design note: the M20 project-tier snapshot/restore machinery already exists (ProjectProfile,
  ConfigStore's project tier) — this verb is a route to it, not a new mechanism, which is why the
  slice is small once the decision above is made.
## M35 · Log + graph lifecycle — ◧ **.1-.7 COMPLETE on `feat/m35-lifecycle`; M35.8 deferred by design** (owner, 2026-08-22; the pairing is evidence, not decoration)
_Today a log and a GraphML are opened independently and **neither can be closed**. `TopologyPanel.load()`
sets the graph; nothing clears it, and the File menu has "Close project" but no "Close log". So opening a
second log leaves the FIRST log's topology on screen, and every figure derived from it — coverage,
"did not run" shading, step-through — is then about a graph that does not belong to the records.
**This is the M33 defect class** (a confident answer computed from mismatched inputs), and `coverage`
already knows how to say it: `loggedButNotInTopology` warns that "the graphml is probably from a
different build, which makes every other figure here suspect". The warning exists; the lifecycle that
would prevent needing it does not._
_Sharpened by the M18 alternative ([spec-agent-brokered-dev-loop.md](../spec-agent-brokered-dev-loop.md)):
one analyser + many servers + many processors makes every one of these a per-minute operation rather
than a per-session one._
- [M35.1] ☑ **Close / reset** *(on `feat/m35-lifecycle`)* — close the log, close the graph, or reset both. The absent capability;
  everything else here depends on it. Clearing must reach the derived state too (topology shading,
  step cursor, coverage, focus contexts) — a half-cleared app is worse than an uncleared one.
- [M35.2] ☑ **Opening a log clears the previous graph unless it still applies** *(on `feat/m35-lifecycle`)* — offer-never-act:
  keep it and say so when the instanceIds still match, otherwise close it and say why. The scoring
  already exists (Decisions ▸ EventProcessor inference).
- [M35.3] ☑ **Switch graph without reopening the log** *(on `feat/m35-lifecycle`)* — multi-processor servers emit one GraphML per
  processor; analysing a second processor against the same log must not mean starting over.
- [M35.4] ☑ **Scan source roots for GraphML** *(on `feat/m35-lifecycle`)* — discover candidates under the configured roots and
  offer them. **Never auto-select silently**: N candidates → name them and their match scores; a
  wrong graph auto-picked is precisely the confidently-wrong reading M35 exists to prevent.
- [M35.5] ☑ **New/switched project closes log + graph** *(on `feat/m35-lifecycle`)* — the profile is the session boundary; today
  "Close project" leaves both loaded.
- [§E] ☑ **`provenance` — which SYSTEM this log came from** *(on `feat/m35-lifecycle`, 2026-08-25)*
  — spec-agent-brokered-dev-loop §E, delivery-order item 2, landed here at the owner's call because
  it is the same subject as M35.6 (which graph) one level out (which system), and small enough to
  review in one pass. `LogFingerprint.provenance` added additively; `open {log, provenance}`; rides
  every surface `describe()` already reached. Two servers on the same build produce identical content
  under identical names, so provenance is checked FIRST and gets its own banner heading, **SAME
  CONTENT — A DIFFERENT SYSTEM**. Absent means absent — never inferred. Share-disclosure row moved in
  the same commit with a contract test, per review §1.9.
- [M35.7] ☑ **No modal in the load path** *(on `feat/m35-lifecycle`)* _(found by M35.2, 2026-08-22)_ — `onLoaded` assigns
  `store` and then calls `maybeOfferProject()`, which shows a MODAL dialog; everything after it waits
  for a human, and on the agent path there is nobody. Worse, the new log is already live behind it,
  so it is answerable against the OLD graph — the M35 defect happening inside the code meant to
  prevent it (report §8 D4 has the REST transcript). M35.2 sidestepped it by re-pairing first; the
  hazard is untouched. The offer must be non-blocking, or suppressed when the open came from the
  action socket rather than a human.
- [M35.6] ☑ **State the pairing before anything is derived from it** *(on `feat/m35-lifecycle`)* — status bar and `context` name
  which graph is paired with which log, and whether they match. Coverage's warning arrives only if
  someone runs coverage; the mismatch should be visible before that.

# Fluxtion Audit Log Analyser — Work Tracker (completed)

Archived, fully-shipped milestones and refinement rounds. The **live** tracker with in-progress
and future work is **[../tracker.md](../tracker.md)**. Status keys: ☑ done · ◧ partial · ⊘ dropped.

---

## M0 · Project setup — ☑ DONE
- [S0.1] ☑ **Maven/JDK21 skeleton + Swing entry point** · `Main` sets system L&F and shows
  `MainFrame`; zero runtime deps; JUnit5 test scope; runnable jar via maven-jar-plugin
  (`Main-Class: telamin.fluxtion.audit.analyser.Main`), `mvn package` verified.
- [S0.2] ☑ **AppConfig + ConfigStore** (`~/.fluxtion-analyser/config`) · cleartext properties;
  round-trip + cleartext-key + missing-file-defaults tested (3 tests).
- [S0.3] ☑ **Background executor + EDT marshalling** · `core/Background.run(work, onSuccess, onError)`
  on a daemon pool, results delivered on the EDT.

## M1 · Parser & index (foundation) — ☑ DONE (24 tests green)
- [P1.1] ☑ **RecordFramer** streaming `---` boundaries → `[offset,length]` slices · CRLF/LF,
  blank lines, no‑trailing‑separator, offset round‑trip all tested; sample → 21 records.
- [P1.2] ☑ **HeaderParser** + scalar fields · `eventToString` never re‑split on inner `:`;
  `eventTime -1`→null; missing header tolerated.
- [P1.3] ☑ **NodeLogTokenizer** (§4.2) depth‑aware split · `MutableOrder(a=1, b=2)`,
  `[demoRfqOrders]`, `NaN`, space‑separated `venueStatus`, duplicate instanceIds, unbalanced
  braces (no throw) all covered.
- [P1.4] ☑ **Derived dimension** (§6) · method‑sig→callback (+declaringType) else event; verified
  on `orderVenueConnected`/`ScheduledTriggerNode`/`LifecycleEvent`.
- [P1.5] ☑ **LogIndex (columnar) + Dictionary** · parallel primitive arrays; interns
  dimension/logger/thread; min/max logTime; per‑dimension counts (ExportFunctionAuditEvent grouped
  by callback).
- [P1.6] ☑ **LogStore + HeapLogStore** · index built in one streaming pass; `record(row)`
  re‑slices + parses on demand; `nodeLogs()` lazy + memoised.
- [P1.7] ☑ **Parser test corpus** · `sample.yml` on the test classpath + golden assertions across
  4 test classes (framer/tokenizer/parser/store).

**Classes delivered:** `model/{EventKind,KV,NodeLog,LogRecord}` ·
`parse/{RecordFramer,RawRecord,HeaderParser,RecordHeader,NodeLogTokenizer,EventDimension,RecordParser,LogStore,HeapLogStore}`
· `index/{Dictionary,LogIndex}`.

## M2 · Table + detail viewer — ☑ DONE (GUI compiles; model path tested headless)
- [U2.1] ☑ **Virtual `JTable` model over LogIndex** · 8 columns, fully index-backed (event/
  eventToString/groupingId added to the index so scrolling never parses); sortable; multi-select.
- [U2.2] ☑ **Date/number renderers** · times rendered `yyyy-MM-dd HH:mm:ss.SSS` **UTC**;
  `eventTime -1`→blank.
- [U2.3] ☑ **nodeLogs preview cell** · shows `N nodes` over a relative-size **bar** (count ÷ max, from
  the index; round 5). Full instanceIds are in the detail viewer.
- [U2.4] ☑ **DetailPanel + YamlHighlighter** · read-only colourised record; multi-select concatenates
  with `---`. (Right-click menu lands in M4.)
- [U2.5] ☑ **Row anomaly cues** · parse-error / breach / NaN tinted, from index-resident flags
  (computed cheaply during the index pass, no node-log parse).

**Classes delivered:** `core/Background` · `config/{AppConfig,ConfigStore}` ·
`ui/{TimeFormat,LogTableModel,YamlHighlighter,DetailPanel,LogTablePanel,MainFrame}` · `telamin.fluxtion.audit.analyser.Main`
· index extended (event/eventToString/groupingId/nodeLogsCount/flags). Tests: 31 green.

## M3 · Filters & summary — ☑ DONE (40 tests green)
- [F3.1] ☑ **FilterState + wiring** · one observable filter (time range + dimensions + text + group
  mode) drives table (`RowFilter`), summary and event checklist; listeners propagate live; 6 tests.
- [F3.2] ☑ **EventFilterPanel** checklist w/ counts + group‑by toggle (callback/event ↔ raw event) ·
  All/None; syncs when cross‑filtered from the summary (ActionListener avoids feedback loops).
- [F3.3] ☑ **Text filter** (eventToString/thread), debounced 250 ms, case‑insensitive.
- [F3.4] ☑ **TimeRangeSlider** custom two‑thumb draggable picker · spans index min/max, live UTC
  labels, updates the filter on drag, syncs back on external change. (Numeric entry + density
  histogram deferred — nice‑to‑have.)
- [F3.5] ☑ **SummaryPanel** grouped by dimension · count/first/last(UTC)/span/rate; row click
  cross‑filters the whole app; rebuilds on filter change; 3 tests.

**Classes delivered:** `filter/FilterState` · `summary/{SummaryRow,SummaryBuilder}` ·
`ui/{EventFilterPanel,TimeRangeSlider,SummaryPanel}` + `MainFrame` filter bar & right‑hand
tabs + `LogTablePanel` row‑filter/sorter. Tests: **40 green**.

## M4 · Source resolution & viewer — ☑ DONE (7 tests green)
- [C4.1] ☑ **EventProcessorModel** — field/import scan → `instanceId→FQN`; generics/array stripped;
  same-package fallback; `coverage()` for inference. 3 tests.
- [C4.2] ☑ **SourceRootResolver** FQN→File over roots; graceful miss.
- [C4.3] ☑ **Discovery + inference** — `SourceService.discover` scans the strategy package;
  `infer` scores candidates by instanceId coverage (decision) with fallback to the default;
  overridable in Settings. 4 tests.
- [C4.4] ☑ **SourcePanel + JavaHighlighter** — read-only colourised source; processor picker;
  **right-click a node instanceId in the detail viewer → opens its declaring class** and switches to
  the Source tab.
- Extra: **ConfigPanel** (Settings) for source roots (add / remove / **drag-drop folders**),
  EventProcessor FQN, and LLM settings — all persisted (improvements.md #2/#3).

## M5 · LLM — ☑ DONE (7 tests green)
- [L5.1] ☑ **Json** mini codec (reader+writer, path navigation) — 4 tests.
- [L5.2] ☑ **PromptBuilder** — bundled system prompt + record context + budgeted source snippets;
  same builder feeds API and copy modes. 3 tests.
- [L5.3] ☑ **LlmClient** (Anthropic + OpenAI) over `java.net.http`; provider factory + default models;
  non-fatal errors.
- [L5.4] ☑ **Conversation + LlmPanel** — two-way chat, **Reset**, context attached to first turn.
- [L5.5] ☑ **No-key copy mode** — Copy prompt (and Send with no key) produces the full prompt to the
  clipboard.
- [L5.6] ☑ **Bundled `llm/system-prompt.md`** explaining Fluxtion audit semantics.
- [L5.7] ⊘ streaming (deferred).

## M6 · Graphing — ☑ DONE (5 tests green)
- [G6.1] ☑ **SeriesExtractor** — `GraphKey(instanceId,key)` → points; last-occurrence per record;
  booleans → ±1; **NaN/non-finite omitted** (round 5); streams over the store. `discover()` finds
  numeric+boolean keys. 5 tests.
- [G6.2] ☑ **Series key picker** — combo from discovered keys; add / **remove individual** / clear;
  multiple graph panels (`GraphTabs`) for comparisons (round 5).
- [G6.3] ☑ **ChartPanel** — custom multi-series chart, auto-scale, legend, UTC axis (no charting dep);
  **stairs/line/points** styles, **zoom/pan** (precise-wheel; +/−/Fit buttons) and hover tooltip
  (rounds 2/4/5).
- [G6.4] ☑ **CSV export** of active series. (Graph↔table deep-link still deferred — nice-to-have.)

**Improvements.md folded in:** log-file **drag-and-drop** onto the window (#1); source roots + EP +
all paths **persisted** to config, drag-drop folders in Settings (#2/#3); table columns — added
**callback**, blanked `event` for `ExportFunctionAuditEvent`, short `Class.method` in place of the
full signature, `eventToString` optional (#4). Search now also covers **nodeLogs** and combines
with the other filters.

## Refinements (round 2) — ☑ DONE (66 tests green)
- **LLM: show the prompt** — Copy prompt (and no-key Send) now opens a resizable, selectable dialog
  with the full prompt + a Copy button (still auto-copied to the clipboard).
- **LLM: richer context** — the prompt now includes a **Node types** section (each nodeLogs
  `instanceId → declared field type`, resolved from the loaded EventProcessor) and the
  **EventProcessor source** itself, in addition to the node-class snippets. 1 new test.
- **Source roots: detection + guidance** — `ConfigPanel.detectSourceRoots` accepts a **project dir**
  and finds its `src/main/java` (incl. sub-modules), or takes an already-correct source root;
  non-source folders are flagged red in the list and warned on add. Clearer help text. 4 new tests.
- **Graph: zoom/pan** — mouse-wheel zoom (Shift = X-only, Ctrl = Y-only), drag to pan, double-click
  to fit, hover tooltip (time + value); view bounds decoupled from data bounds.
- **Graph: booleans** — `KV.graphValue()` maps `true`/`false` → `+1.0`/`-1.0` (symmetric around zero
  so flips are obvious); extractor and key discovery include boolean keys. 1 new test.

## Refinements (round 3) — ☑ DONE (70 tests green)
- **Click-to-source navigation** (`SourceNavigation`, 4 new tests):
  - selecting a record scrolls the Source tab's EventProcessor to the **dispatch method** (the
    record's callback);
  - **click a node-log line** in the detail viewer → opens that node's class scrolled to the method
    it ran (the line's first key), resolved instanceId → EP field → type → file;
  - **Ctrl/⌘-click** in the source viewer → `receiver.method()` opens the node's method, a field opens
    its type, a Type opens its source (using the shown file's own fields/imports).
- **App icon + splash** — `AppImages` draws the icon (all sizes, via `setIconImages`) and the
  `SplashScreen` banner at runtime; no image files, shown briefly at launch.

## Refinements (round 4) — ☑ DONE (70 tests green)
- **Graph wheel-zoom fixed for trackpad/Magic Mouse** — uses `getPreciseWheelRotation()` with a
  clamped per-event factor (consistent direction, no runaway from momentum); added **+ / − / Fit**
  zoom buttons as a reliable alternative.
- **Plot styles** — default **Stairs** (step; values hold until next update), plus **Line** and
  **Points**, chosen from a style dropdown.
- **Node-log viewer Wrap toggle** — checkbox; off → horizontal scroll for long lines.
- **EDT + thread exception reporting** — `ExceptionHandling.install()` sets a default uncaught handler
  and pushes a wrapping `EventQueue` so EDT-dispatch errors are printed to the console too.
- **Richer Java colouring** — distinct hues for keywords, types, methods, constants, numbers,
  annotations, strings and comments.
- **Source Back navigation** — history stack with a **Back** button and **Alt+Left** / **Cmd|Ctrl+[**
  key bindings.

## Refinements (round 5) — ☑ DONE (71 tests green)
- **Range slider: pan the window** — grabbing the middle of the selected span drags the whole range,
  preserving its width (start+end move together); cursor hints (resize on thumbs, move on the span).
- **S3 connector** — `io/S3Source` streams `s3://bucket/key` via the local `aws` CLI
  (`aws s3 cp … - --quiet`, stderr drained to avoid deadlock); **File → Open from S3…**, AWS
  profile/region in Settings, recent-files reopen s3 URIs. Zero new deps (reuses the user's aws auth).
- **Custom app icon + Dock icon** — `AppImages` drawing used for the window icon *and* `Taskbar`
  (replaces the Java "Duke" on the macOS Dock / taskbar).
- **Graph: remove individual series** — a "Shown" combo + Remove (plus Clear).
- **Graph: multiple panels** — `GraphTabs` (New graph / Close graph) for side-by-side comparisons,
  each bound to the shared filter; `FilterState.removeListener` for clean teardown.
- **Table column show/hide + persistence** — View→Columns checkboxes; hidden by default:
  eventTime, groupingId, eventToString, endTime. Stored in config.
- **endTime parsing fix** — end-of-nodeLogs detection is now indentation-independent, so `endTime`
  (the only scalar after nodeLogs) parses even when scalars are indented > 2 spaces. Regression test.
- **nodeLogs count bar** — the nodeLogs cell draws a translucent bar sized by count ÷ max, so
  heavy-fan-out cycles stand out at a glance.
- **NaN in graphs = no data point** — non-finite values are omitted entirely (not a gap, not a parse
  failure). Test updated.
- **Back-to-EventProcessor scrolls to the dispatch method** for the current record's callback.

## Refinements (round 6) — ☑ DONE (74 tests green; uber-jar bundles FlatLaf)
- **Repaint bug fix** — removed the custom `EventQueue` push (interfered with the macOS paint/validate
  pipeline → uncommitted splitter drags, partial text wrap); EDT exceptions still print by default.
  Also `setContinuousLayout(true)` on the splitters.
- **Range → focus/context** — selecting a range shows the **final** record in detail; the whole range
  feeds the LLM (last = "record to explain", earlier = preceding context).
- **Professional L&F: FlatLaf** (MIT, ~1 MB, the one runtime UI dep) with **Light/Dark/IntelliJ/Darcula**
  themes (Theme menu, persisted); bundled into the runnable uber-jar via shade.
- **Theme-aware rendering** — YAML + Java highlighters and the chart pick light/dark palettes and
  re-render on theme change, so text/code stay readable on any theme.
- **Custom Dock/taskbar icon** via `Taskbar` (no Java "Duke").
- **Graph**: precise-wheel zoom (trackpad/Magic-Mouse safe) + +/−/Fit buttons; **stairs/line/points**;
  booleans → ±1; **NaN omitted** (no point); **remove individual series**; **multiple graph tabs**;
  **PNG export**; **saved graphs persist** in the profile and reopen on load; **searchable multi-select
  Pick…** dialog to add several series.
- **Records**: **CSV/YAML export** of the filtered set; **bookmarks/flags** ('F') + flagged-only filter.
- **Search box**: history with **inline autocomplete** + dropdown; **Settings → History** clears
  search history / saved graphs / recent files.
- **LLM prompt**: adds **source roots + resolved file paths** (for agentic exploration) alongside the
  embedded snippets and node-type map; **Copy prompt** now prints the full prompt into the transcript
  bracketed by clear "COPIED — NOT sent" markers (and to the clipboard).
- **S3**: open `s3://…` logs via the local `aws` CLI (round 5) — profile/region in Settings.

_Note: the "zero runtime dependencies" goal now has one deliberate exception — FlatLaf (UI only),
added at the user's request for a professional themed look._

## M7 · Large‑file mode — ☑ DONE (80 tests green)
- [M7.1] ☑ **MappedLogStore + ByteRecordFramer** — streaming byte-offset framer (64-bit offsets,
  scales past 2 GB) builds the index without loading the file; records read on demand from a
  `FileChannel` + LRU cache. Verified byte-for-byte against `HeapLogStore` on the sample (incl. CRLF).
- [M7.2] ☑ **`LogStores.open` threshold auto-select** — ≤ `memoryThresholdMb` (default 500) → heap,
  else memory-mapped (0 → always mapped); used by local and S3 opens; previous store closed on reload.
- [M7.3] ◧ **Background load** keeps the UI responsive with a "Loading…" status; explicit
  progress‑percent + cancel deferred (indexing a multi‑GB file runs off‑EDT and can't be cancelled yet).
- **S3 → temp file** — `S3Source.fetchToFile` streams `s3://…` to a local temp file via the `aws` CLI
  (never into heap), which then goes through `LogStores.open`, so **large S3 logs get the mmap path**
  too. Temp file deleted on exit. 2 tests.

## M9 · UX pass (discoverability) — ☑ DONE (82 tests green)
- [U9.1] ☑ **Toolbar** — Open, Open S3, Flag, Flagged-only (toggle), Explain, Export CSV, all with
  tooltips (`JToolBar` above the filter bar).
- [U9.2] ☑ **Explain button** on the detail panel (and toolbar) → jumps to the LLM tab and primes a
  default question with the selection as context.
- [U9.3] ☑ **Event-types moved to a left rail** (WEST); right tabs are now pure outputs
  (Summary / Source / Graph / LLM).
- [U9.4] ☑ **Feedback** — detail bar shows "N records selected — last shown here; all sent to the LLM";
  slider tooltip + duration label; indeterminate **progress bar** in the status bar during loads;
  empty-state hints in the table and detail panel.
- [U9.5] ☑ **Clickable legend** removes a series (dropped the "Shown" combo; kept Add / Pick… / Clear).
- [U9.6] ☑ **Record diff** — `DiffBuilder` (2 tests) + `RecordDiffDialog`: Records → "Diff selected two
  records" shows a side-by-side table with changed / only-A / only-B rows highlighted.
- Extra: **time slider polished** — density histogram behind the track (in-range brighter), duration
  label, centre grip (pan affordance), double-click to reset, theme-aware colours.
- Extra: **empty state** — the records table paints an "open a log" hint when nothing is loaded.

## Refinements (round 12) — ☑ DONE (144 tests green)
- **Graph action wasn't plotting valid keys (bug fix).** The `graph` action resolved series names by
  enumerating keys via `SeriesExtractor.discover`, which is **front-truncated** (first ~20k records only),
  and then `addKeys(resolved)` added **only** the discovery-resolved keys — so a valid `instanceId.key`
  whose node first fires later (or fires rarely, e.g. a locked-book flag) was reported "unresolved" and
  **never added**, even though `extract` scans the whole log and could plot it. Fixes:
  - **Add every well-formed key/formula regardless of discovery** (`GraphKey.fromDisplay` splits
    `instanceId.key` on the first dot) — extraction is whole-log, so a real-but-rare key now plots.
  - **`Expr.parse(String)`** (no known-set) resolves refs by the same split, so derived series parse and
    extract **without** the front-truncated discovery gate (removed from `GraphPanel` re-extract, the f(x)
    field, and CSV export).
  - **`SeriesExtractor.resolveExisting`** — a *targeted, whole-log, early-exit* check used only for accurate
    action feedback; the echo now carries `resolved`/`unresolved`, per-expr `unseenRefs`, a sample of real
    `availableKeys`, and a note that **graphable keys are top-level numeric/boolean nodeLog values**
    (a price nested in `MutableOrder(price=..)` is not itself a key) — the likely cause of the agent's
    unresolved names. _New tests: `ExprTest` (fromDisplay + no-known-set parse), `SeriesExtractorExprTest.resolveExistingFindsAKeyThatOnlyFiresLate`._

## Refinements (round 11) — ☑ DONE (125 tests green)
- **Graph click → nearest record** — clicking the plot selects the record whose `logTime` is closest to
  the clicked time and scrolls the table to it (`ChartPanel.setOnPlotClick` → `GraphTabs.setTimeClickHandler`
  → `MainFrame.gotoNearestRecordByTime`). Reports if the nearest record is filtered out of the view.
- **Main-screen layout** — the filter controls are now one **titled "Filter" group**: **Search** (top-left),
  **showing N of M** (centred above the slider), **Window** combo (top-right), the slider, then the pan bar
  — search no longer floats disconnected, and the count sits over the time bar.
- **Slider selection labels** — the from/to are drawn **centred under the thumb ends** (the blue drag bar),
  **rounded to the window granularity** (seconds ≤3 min, minutes ≤3 h, day+minutes ≤3 d, else date) — so
  they read as the *selected window*, not absolute log bounds.
- **Theme-switch NPE fix** — `updateComponentTreeUI` briefly sets a combo's editor to null during
  `BasicComboBoxUI.uninstallUI`, firing our `"editor"` property listener while `getEditor()` was null →
  NPE that aborted the whole tree update (and cascaded into a FlatLaf `arrowButton` paint NPE).
  `HistoryComboBox.editor()`/`getText`/`setText`/`complete` are now null-safe on the teardown path.
- **`doGraph` series resolution** (reviewer #1/#2) — resolve requested `instanceId.key` against the
  **whole log** (a neutral filter), not the filtered view, so a key outside the active window still
  resolves; and echo `discoveryLimited` when the ~20k-record discovery cap was hit, so
  unresolved-because-unscanned is distinguishable from unresolved-because-typo (no silent caps).
- **Graph smoothness — decoupled extraction + decimated paint** (replaces the earlier throttle, which
  still froze because each redraw re-parsed the whole log). Root cause was two O(N) costs on the
  time-drag hot path: `extract` re-parsed **every record's node-logs** on each redraw (time bound baked
  into the extraction filter), and `drawSeries` was **O(points) on the EDT** (a `drawLine`+`fillOval` per
  point). Fixes:
  - **Extract once, across all time** (`FilterState.testExceptTime` + `SeriesExtractor.extract(…, acrossAllTime=true)`);
    a **time-only** filter change now just **windows the cached series** via `ChartPanel.setViewWindow`
    (no parsing). Re-extraction happens only on a **structural** change (dimensions/text/keys),
    **debounced** (`extractDebounce`, 200 ms) and **generation-guarded** (stale results dropped).
  - **Decimated drawing** — series denser than ~3×plot-width downsample to one representative point per
    pixel column and render with the **same STEP/LINE/POINTS logic** (a faint min/max envelope behind
    preserves spikes), so paint is **O(plot width)** not O(points) *without* changing how a style looks
    (an earlier envelope-only version was a regression — stairs/line/points all looked like a line).
  Net: dragging the time slider/window re-parses nothing and repaints in O(plot width) — no EDT freeze.
  `SeriesExtractorTest.acrossAllTimeIgnoresTheTimeWindow…` locks the decoupling. 126 tests green.
  - _Known residual (deferred): `ChartPanel.setViewWindow` rescans every point to autoscale Y on each drag
    tick — O(points), no parse/paint. Invisible until a **single** series covers ~every record of a
    multi-million-record log, where it'd show as drag lag. Fix if it bites: a coarse time-bucketed min/max
    Y index built once at extraction (query O(buckets) per tick), or fix Y during the drag and refit on
    settle. Not built — proportionate to the rare trigger._

## Refinements (round 7) — ☑ DONE (82 tests green)
- **Dark-theme contrast** — slider drag-bar/label text brightened for dark themes; anomaly/flag row
  tints in the records table are now theme-aware (light tints on light themes, dark tints on dark so the
  light foreground text stays readable): ERR / BREACH / NaN / FLAG each have a light+dark variant chosen
  via `ThemeManager.isDark()` in `LogTablePanel.prepareRenderer`.
- **Time-window granularity** — the slider now separates an *outer visible window* from the *selection*.
  A **Window** combo (All / 1 week / 1 day / 6h / 1h / 30m / 15m / 5m / 1m) shrinks the visible span and a
  **pan scrollbar** below the slider moves that window across the full log range, so a ~40-minute window of
  interest isn't a sliver of a ~50-hour log. `TimeRangeSlider` gained `absMin/absMax` + `setWindowMillis`,
  `setWindowStartFraction`, `isWindowed`, `windowSpanFraction`, `windowStartFraction`; the density histogram
  is now positioned by **time** (window-aware) not array index; `publish()` nulls the bound only at the
  absolute edges; double-click resets window **and** selection.
- **Right-click on the records table** — Diff (enabled only with exactly 2 rows) / Explain / Flag, mirroring
  the Records menu; right-clicking an unselected row selects it first.
- **Settings dialog redesign** — replaced the blocky `GridLayout(0,2)` tabs with a shared two-column
  `GridBagLayout` form (right-aligned labels, fields that grow to fill, muted help notes, top-pinned rows);
  EP tab no longer a cramped `FlowLayout`; History buttons left-aligned + uniform width; right-aligned
  button bar with **OK as default** (Enter) and **Esc to cancel**; consistent 14/16px tab padding.

## Refinements (round 8) — ☑ DONE (85 tests green)
- **Search bug fix (theme change)** — `updateComponentTreeUI` (fired on every theme switch) recreates the
  editable combo's editor (new `JTextField` + `Document`), which stranded the autocomplete `DocumentFilter`
  and the debounce listener on the discarded document and left `getText()` reading a stale editor — so
  search silently died after any theme change. `HistoryComboBox` now resolves the editor dynamically and
  re-installs the filter + change bridge on every editor swap (`propertyChange("editor")`).
- **Memory threshold in Settings** — the heap-vs-mmap cutoff (`memoryThresholdMb`) is now a spinner in
  Settings → *Performance & S3* (was hand-edit-only); closes the M7 loop. Applies to the next file opened.
- **Anomaly navigation (H8.4)** — ⚠ Next / ⚠ Prev toolbar + F3 / Shift+F3 select the next/previous
  anomaly row (parse-error / breach / NaN), wrapping, over the current filtered/sorted view.
- **Window follows selection** — dragging a selection thumb to the visible-window edge now auto-pans the
  window (Swing timer) so you can extend the selection past the current window; the pan bar stays in sync
  via a `setWindowChangeListener` callback.
- **Follow / tail mode (H8.7)** — File → "Follow (tail)" / toolbar toggle polls the open **local** file
  (~1 s) and appends newly-*completed* records live (auto-scroll to newest), preserving flags/filter/
  selection. Built on `RecordFramer.frame(…, requireTerminator)` (a record isn't indexed until its
  closing `---` arrives) + `HeapLogStore.appendFrom` (append-only re-read; returns count / 0 / −1 for
  shrink-rotation → reload) + `LogTableModel.rowsAppended` + `TimeRangeSlider.extendAbsMax`. Heap-loaded
  local files only (not S3 / not mmap). 3 new `FollowAppendTest` cases.

## Refinements (round 9) — ☑ DONE (88 tests green)
- **LLM whole-log access (agentic retrieval)** — the prompt is now seeded with the audit log's file path,
  shape (human size, record count, UTC time span) and framing description, plus each selected record's
  **byte offset + length + logTime**, so a file-reading model can grep/seek the log in **both directions**
  from an anchor (read-behind for causes, read-ahead for consequences) and count occurrences — the curated
  selection becomes a starting point, not the whole evidence base. `PromptBuilder.recordContext` gained a
  `LogFileInfo` overload (path/localPath/size/count/min/max); `LogStore.localFile()` exposes the real local
  file (the fetched **temp file** for S3, so grep targets a readable path); the system prompt tells the
  model to use it. Inert text for non-agentic targets — no toggle, same graceful degradation as source
  roots. Byte offsets were already in the index, so this is a thin seam. 4 new `PromptBuilderTest` cases.

## M8 · Polish, help, extras
- [H8.1] ☑ **HelpPanel (HTML)** — logs, columns, dimension, filtering/search, summary, graphing,
  source, LLM, config &amp; perf · bundled `/help/help.html`, opened via Help &rarr; User guide.
- [H8.2] ☑ **Export** rows→CSV/YAML (`RecordExporter`, filtered; YAML re-parses) + series→CSV + graph→PNG.
- [H8.3] ☑ **Bookmarks/flags** — 'F' toggles a flag on selected rows (tinted); Records → "Show flagged
  only" filter + Clear all flags.
- [H8.4] ☑ **Anomaly navigation** — ⚠ Next / ⚠ Prev toolbar buttons + F3 / Shift+F3 jump to the next /
  previous parse-error / breach / NaN row (wraps; respects filter/sort). Round 8.
- [H8.5] ☑ **Record diff** side‑by‑side (M9/U9.6; also on the table right-click, round 7).
- [H8.6] ◧ **Column show/hide** ☑ (round 5), **recent files** ☑ (config), **window persist** ☑ (M0);
  **copy‑row‑as‑YAML** still ☐.
- [H8.7] ☑ _(stretch)_ **Follow/tail mode** — round 8.

## M10 · Assistant actions — two‑way curation loop — ☑ DONE (all slices, 125 tests green)
_Design: **[spec-assistant-actions.md](spec-assistant-actions.md)**. Completes the LLM loop: the app
seeds file access (round 9); this adds the return path so the assistant can **compute over the index**
(query verbs) and **build curation** in the UI (render verbs). App curates, model retrieves + reasons.
Two peer‑review rounds folded in: `text` filter = honest raw‑scan (`scan:index|raw`); agent loop
auto‑resends **results and errors** with a **round cap (3)** + per‑reply action cap (20); reject **any**
`Origin` header + REST token‑bucket; snapshot guard captures **columns AND dictionary copies** vs
follow‑mode appends; `flag` verb in v1 (note = small new store); worked examples split index‑path
(`onMultilevelMarketData`) vs raw‑path (`failedValidation` is a nodeLogs key, not a dimension)._
- [A10.1] ☑ **Action schema + `ActionDispatcher`** · `ActionResult` + `ActionFilter` over `llm.Json`
  (dispatcher parses the request map directly — no separate `ActionRequest` type); validates
  version/token/verb; routes to handlers; structured `ok:false` (never throws). _(Per‑reply cap lands
  with the loop in A10.3, where a "reply" exists.)_
  _Done: `ActionDispatcherTest` (7) — version mismatch, missing/wrong/correct token, unknown verb, missing action, render‑not‑enabled, malformed JSON._
- [A10.2] ☑ **`AggregateService` + `LogIndex.snapshot()` (query, read‑only)** · count / rate_per_min /
  nan_count / breach_count; groupBy dimension / thread / hour / minute / **day**; optional filter that
  reports `scan:index|raw` (dimension/flag = O(index); `text` = streaming raw pass); result echoes its
  population; defined rate denominator; **snapshot guard captures columns + `Dictionary.copyValues()`**
  so off‑EDT reads don't tear vs follow‑mode `appendFrom` (`add` synchronized).
  _Done: `AggregateServiceTest` (9) + `LogIndexSnapshotTest` (2, incl. concurrent new‑dimension append)._
- [A10.3] ☑ **In‑process executor + bounded agent loop** · `LlmPanel.runRound` parses **exact‑tag**
  ```analyser-action``` blocks (`ActionParser`; `-example` + other fences ignored), runs them via an
  in‑process `ActionDispatcher` (fresh snapshot per round), **auto‑resends query AND error results** with
  a `maxActionRounds`=3 cap + **per‑reply action cap (20)** + **Cancel** + "↻ round n/m" markers; results
  shown as a **visible, marked** transcript turn. No open port. `PromptBuilder.inProcessActionManifest`
  seeds the verbs (no URL) on the first turn. Config `assistantActionsInProcess` (default **on**) +
  `maxActionRounds`/`maxActionsPerReply` persisted. _(Settings UI toggle is A10.6.)_
  _Done: `ActionParseTest` (5) — none/one/many/malformed; example‑fence + json‑fence ignored._
- [A10.4] ☑ **Render verbs** (`filter`, `graph`, `goto`, `flag`) via `ui/ActionExecutor` (implements the
  UI‑free `llm/RenderExecutor` seam; dispatcher delegates render verbs to it). Bridges to `FilterState`
  (echoes **full** resulting state; missing=unchanged/null=cleared via `containsKey`), `GraphTabs`
  (**named** graphs: `graphForAction`/`renameNamed`; series resolved against `SeriesExtractor.discover`,
  echoing **resolved‑vs‑unresolved**; style by name), table select+scroll (`selectModelRow`), and the flag
  store (+ **note** map + hover tooltip). Offsets **floor‑resolve + clamp** to the containing record and
  echo the resolution; `recordIndex(es)` accepted too. All UI mutation **EDT‑marshalled** (`invokeAndWait`).
  Loop resend condition refined: resend on a **query result or any error**, never a render success
  (reviewer #1); manifest attaches on the first **enabled** turn (#3); manifest now advertises all verbs.
  _Done: `GotoResolveTest` (4) — exact/mid‑record floor, out‑of‑range clamp, recordIndex clamp._
- [A10.5] ☑ **localhost REST transport** · `net/ActionServer` (JDK `HttpServer`, `127.0.0.1`, ephemeral
  port) with `POST /action` + `GET /manifest` (**advertises verbs + caps**); same dispatcher (+ render
  executor); **`X‑Analyser‑Token` header guard + reject any `Origin` + token‑bucket rate limit (10 req/s →
  429)**. **Opt‑in** (default **off**); when on, MainFrame starts it, shows URL+token in the status bar/
  console, and the copy‑prompt seeds `restActionManifest(url, token)`. Per‑run UUID token.
  _Done: `ActionServerTest` (6) — loopback bind, `/manifest` shape, missing/wrong token 401, any‑Origin 403, 429._
- [A10.6] ☑ **Config + Settings** · `assistantActionsInProcess` (on) / `assistantActionsRest` (off) /
  `maxActionRounds` (3) / `maxActionsPerReply` (20), persisted; a new **Assistant** tab in Settings with the
  two toggles + the two spinners + an explainer; REST start/stop applied on save. _(Live port/token shown
  in the status bar rather than the dialog.)_
- [A10.7] ☐ _(deferred)_ **`aggregate` over `nodeLogs` numeric keys** (needs per‑record node‑log parse);
  **index‑time text flags** for O(index) anomaly counts (would make the `failedValidation` headline case
  index‑fast); flag‑note **persistence** across reload.

### Named graphs (landed ahead of the render slice)
- [A10.8] ☑ **Graphs are named** — tab title is the name; **Rename…** button + double‑click to rename;
  `config/GraphSpec` (name + series) persists names in the profile (round‑trip tested); `GraphTabs`
  gains `addGraph(name)` / `graphNamed` / `graphForAction(name,newTab)` / `renameSelected` so the future
  `graph` action can build‑then‑refine a graph by name. The named series definition is the **promotion
  contract** to monitoring (M11). _Done: `ConfigStoreTest.savedGraphsRoundTripWithNames`._

## M14 · Graph artifacts — pinned range + derived series — ☑ DONE (M14.1–M14.5; v2.0 windowed transforms deferred; 141 tests green)
_Design: **[spec-graph-artifacts.md](spec-graph-artifacts.md)**. Turn LLM-built graphs from views into
artifacts (pinned window + formula-defined series) that survive filter changes, persist, and promote to
Grafana (M11). Both slot into existing seams — `GraphSpec`, the `graph` verb, `SeriesExtractor`._
- [M14.1] ☑ **Pinned range** · `GraphSpec` gains nullable `from`/`to` (null = follow; set = pin);
  `GraphPanel` holds a logical `graphName` + pin state and windows via `ChartPanel.setViewWindow`
  (`applyWindow`: pinned → fixed window, ignores filter time changes; following → filter window). A **📌
  toggle** pins to the current filter window — or the effective data bounds when the filter is unbounded —
  and the tab shows a **📌** prefix + tooltip; the `graph` verb's `from`/`to` now **pin** and are echoed;
  `ConfigStore` persists `graph.i.from/to` (backward compatible). _Done: `ConfigStoreTest.savedGraphsRoundTripPinnedWindow`;
  pinned-ignores-filter verified by compile + manual._
- [M14.2] ☑ **Expr engine** `graph/Expr` · sealed AST (Num/Ref/Neg/Bin/Call) + recursive-descent parser
  (refs `instanceId.key` resolved to `GraphKey` at parse time, numeric literals, `+−×÷`, parens,
  `abs`/`min`/`max`) + evaluator. **Hermetic** (no eval/SDK — zero-dep), serializable, portable to Grafana.
  Lexer takes **ASCII and Unicode** operators (`−`/`×`/`÷`); unknown ref error **names the ref + suggests
  the nearest** (edit distance); div-by-zero / missing ref → NaN (omitted point); backtick escape for odd
  keys; `refs()` lists referenced keys for the extractor. _Done: `ExprTest` (9)._
- [M14.3] ☑ **Derived extraction** · `SeriesExtractor.extractExpr(store, filter, Expr, label, acrossAllTime,
  Resolve)` in **row order** with **LOCF** + `STRICT`; carry rule implemented: **finite updates, explicit
  NaN/non-numeric clears** (no fabricated continuity), absent leaves unchanged, point only on a record
  touching ≥1 ref; non-finite result omitted; `Series` gained a **display label** (+ nullable key for
  derived). _Done: `SeriesExtractorExprTest` (4) — LOCF carry + NaN-clears (omits the NaN record, resumes
  after), strict co-occurrence, missing-ref omits all, single-ref exactness._
- [M14.4] ☑ **Action wiring** · `graph` verb `exprs:[{label,expr,resolve?}]` parsed against discovered keys,
  echoed **per-expr ok/error** (parse error names the ref); `GraphPanel` holds derived series (re-extract
  parses off-EDT + generation-guarded), legend/CSV show the **label**, legend-click removes raw or derived
  by label; `GraphSpec.ExprSpec` + `ConfigStore` persist formulas (backward compatible); manifest documents
  exprs + pinning. _Done: `ConfigStoreTest.savedGraphsRoundTripDerivedFormulas`._
- [M14.5] ☑ **UI** · 📌 pin/unpin (M14.1) + a second graph toolbar row with a plain **`f(x)` field** (+ label
  + locf/strict), which discovers keys off-EDT, parses with the **same inline error** the LLM path returns,
  and adds the derived series (Enter or "Add f(x)"). Agent-first: the UI authors simple formulas, the LLM
  the gnarly ones. _Verified by compile + manual pass._
- [M14.6] ☐ _(v2.0)_ closed-set windowed transforms `delta`/`dt`/`rate`/`rolling(x,w,agg)` as pure
  `Series→Series`, **top-level wrap only** (O(n) each). _(v2.1: series-level arithmetic + alignment — separate.)_

## Refinements (round 12) — ☑ DONE (152 tests green)
- **Package move** — the code moved from `com.acme.analyser` to `telamin.fluxtion.audit.analyser.*`
  (`Main` at `telamin.fluxtion.audit.analyser.Main`, app packages under `…analyser.analyser.*`); maven
  coordinates are now `com.telamin:fluxtion-audiitlog-analyser`; the default strategy FQN seeds moved to
  `com.acme.…`. README/specs updated; older tracker entries keep the pre-move names.
- **Settings dialog tidy-up** — the dialog no longer opens over-wide: HTML help notes are
  **width-constrained** (`ConfigPanel.mutedNote` wraps at ~500 px; unconstrained HTML labels report a
  one-line preferred width that blew up `pack()`). Assistant-tab spinner rows get proper GridBag weights
  (label column fixed, value column takes the slack) so fields are no longer squeezed/cut off.
- **Event processor tab redesign** — the single editable combo is now a **list of FQNs** (like source
  roots): Add… / **Edit…** (also double-click an entry) / Remove / **Set active**; the active processor is
  bold with an "(active)" suffix. Save writes the full list + active selection.
- **Maven repos (new Settings tab + fallback resolver)** — a list of local Maven repositories (default
  `~/.m2/repository`, multiples supported) searched for **`*-sources.jar`** when an FQN has no `.java`
  under the source roots, with a **"Don't search local Maven repositories"** opt-out.
  `source/MavenSourceResolver`: lazy one-time jar walk (warmed off-EDT on configure), group-path-prefix
  candidate ordering, per-FQN hit/miss cache; `SourceService.sourceForFqn` = roots first, then repos —
  so the detail/source viewers and LLM snippets resolve library nodes. 4 tests + config round-trip tests.
- **History tab: Clear all** — one button runs the three individual clears (each still available).
- **First-run Settings popup** — no `~/.fluxtion-analyser/config` at launch → a welcome note + the
  Settings dialog open automatically (after the splash); saving creates the file so it shows once.
  `ConfigStore.exists()`.
- **Formula management UI (M14 follow-on)** — each graph shows its derived formulas in a combo with
  **Edit** (loads expr/label/policy back into the fields; Add f(x) applies, handling renames) and
  **Remove** (legend-click removal stays). The **f(x) field autocompletes** the token being typed from
  the union of discovered `instanceId.key`s and existing formula labels (backticked when the label
  isn't a plain token).
- **Formulas referencing formulas** — `GraphPanel.expandFormulaRefs` textually expands other formulas'
  labels (bare or backticked) into parenthesised sub-expressions before `Expr.parse`; longest-label-first
  (no partial shadowing), a real key wins over a same-named label, chains resolve across passes, cycles
  are stopped by a pass cap and surface as the standard unknown-key error. 6 tests.
- **Detail viewer → graph** — right-click an attribute on a node-log line ("Add `instanceId.key` to
  graph") with a submenu: **current graph** (default), any **named graph**, or **New graph…**;
  `GraphTabs.addSeriesTo/graphNames/selectedGraphName`; the right rail switches to the Graph tab to show
  the result. Key hit-detection reuses `SourceNavigation.lineAt/parseNodeLogLine` plus a token scan.
- **Main layout rebalance** — the records table no longer dominates: vertical split 440→330 px
  (resize weight 0.6→0.45), horizontal split 840→630 px (0.72→0.55), giving the detail panel and the
  right-hand tabs breathing room.

## M15 · Settings export / import — shareable analysis setups — ☑ SHIPPED
_Design: **[spec-settings-share.md](spec-settings-share.md)**. File → Export/Import settings: a
versioned properties file carrying a **whitelisted** subset of the config (roots, maven repos, EP list,
named graphs incl. formulas/pins, view, assistant caps; LLM provider opt-in) — never the API key, AWS
details, recent files or search history. `~`-relative paths for portability; additive list merge,
graphs replace-by-name, import summary dialog; whitelist enforced on import too._
- [S15.1] ☑ **`config/SettingsShare` core** — export/preview/apply, whitelist, `~` paths, version gate,
  merge rules; 11 unit tests (`SettingsShareTest`). Reuses `ConfigStore`'s list/graph serialization
  (helpers widened to package-private). `ImportPlan` carries a per-category summary for the dialog.
- [S15.2] ☑ **UI** — `ExportSettingsDialog` (category checkboxes + Copy / Save-and-reveal / Email) and
  `ImportSettingsDialog` (per-category summary + deselect); File-menu items; refresh via
  `onConfigChanged` + `graphTabs.restore` + `setVisibleColumns`. Slack-webhook transport deferred
  (offline ethos) — clipboard/file/email cover messaging.
- [S15.3] ☑ **Docs** — help section, README, changelog entry. Site page (`sharing-setups.md`) lands
  with the M17 docs site.

## M16 · Release & distribution — GitHub Actions, fatjar, JBang — ☑ SHIPPED (bar one repo toggle)
_Design: **[../admin/release-process.md](../../admin/release-process.md)**. Trunk-based on master;
releases are one workflow-dispatch with a version number: tests → changelog stamp ([Unreleased] →
version) → tag → fatjar build (`versions:set` from input; pom stays a placeholder) → GitHub release
with notes + versioned & **stable-name** jars + sha256. `CHANGELOG.md` (Keep-a-Changelog, seeded) is
the one manual habit and is **bundled into the jar** for Help → Release notes + a what's-new-on-upgrade
note. JBang catalog alias points at `releases/latest/download/` so it never needs updating._
- [R16.1] ◐ **Pre-flight** — ☑ artifactId typo fixed (`audiitlog`→`auditlog`; jar is now
  `fluxtion-auditlog-analyser-*.jar`), ☑ pom version placeholder `0.0.0-SNAPSHOT`. ☐ repo Actions
  **write permissions** — a GitHub settings toggle, done in the UI before the first release.
- [R16.2] ☑ **`ci.yml`** — `mvn -B verify` gate on push/PR to master.
- [R16.3] ☑ **`release.yml`** — dispatch-driven: verify → stamp+commit changelog → tag → fatjar
  (`versions:set` from input) → GitHub release with versioned + stable-name jars + sha256. Guards a
  bad version input, an existing tag, and an empty changelog section.
- [R16.4] ☑ **Bundled notes + version** — CHANGELOG shipped as `/release-notes/CHANGELOG.md`,
  `Implementation-Version` in the (shaded) manifest, `ReleaseNotes` helper (3 tests), Help → Release
  notes panel, Help → About shows the version, and a `lastRunVersion` what's-new-on-upgrade dialog
  (suppressed for dev/`-SNAPSHOT` builds and fresh installs).
- [R16.5] ☑ **`jbang-catalog.json`** (alias → `releases/latest/download/…`) + README run-it-now
  (JBang + curl) snippets.


## M17 · Documentation site — ☑ DONE (live at https://telaminai.github.io/fluxtionauditlog-analyser/)
_Shipped on **MkDocs Material** (migrated mid-build from the original Jekyll/Just-the-Docs design — the
`github-pages` gem pins 2018-era Jekyll 3.9 and cannot run on Ruby ≥ 4; Material matches
mongoose-plugins, so the org has one docs toolchain and one look). Design: **[../../admin/docs-site.md](../../admin/docs-site.md)**._
- [D17.1] ☑ **Skeleton + deploy** — root `mkdocs.yml` (org theme block), `docs-requirements.txt`,
  `pages.yml` (`mkdocs build --strict` + `deploy-pages` via OIDC); Pages enabled (Source = GitHub
  Actions); repo made **public** on a fresh single-commit history (tree *and* history anonymised —
  DEMO/acme placeholders across site, specs, fixtures, javadoc; 186 tests green).
- [D17.2] ☑ **User guide** — index (hub), records-and-filtering, graphs, assistant (round-trip
  fault-finding workflow), source-navigation, sharing-setups; getting-started quick start; 9
  anonymised screenshots (light + dark).
- [D17.3] ☑ **Log format + producing + FAQ** — annotated real record, field reference, downloadable
  22-record sample; producing-a-log page; FAQ with action-socket security answer, troubleshooting
  table, keyboard shortcuts. `CHANGELOG.md` injected as the release-notes page at build; the release
  workflow now **dispatches the docs deploy** (a `GITHUB_TOKEN` push never fires `on:push`).
- [D17.4] ☑ **Landing page** — hero value sentence + "Why it matters" section; theme-aware inline-SVG
  **audit-loop diagram** (no blank lines — python-markdown ends raw HTML blocks at one); nav
  consolidated to 6 tabs (Getting started and The-audit-log groups); Run-it-now links the sample log.
- [D17.5] ☑ **Distribution proven** — v1.0.0 released; JBang catalog + trust prompt + `--fresh`
  documented; catalog passes `--enable-native-access=ALL-UNNAMED` (warning-free launch, verified).

## Refinements (round 13) — ☑ DONE (UI polish backlog, gathered live)
- [R13.1] ☑ Chart gridlines — more contrast in both themes. · [R13.2] ☑ Search box vertical padding.
- [R13.3] ☑ Source view Wrap checkbox, default no-wrap (shared `WrapTextPane`).
- [R13.4] ☑ Record detail **Copy** button. · [R13.5] ☑ Clicking event/`eventToString` navigates the
  Source view to the dispatch method (`showDispatchFor`).
- [R13.6] ☑ **Add to graph** offered anywhere on a node line (not only on a key token).
- [R13.7] ☑ Summary rows: right-click → Filter to / Add / Remove (left-click no longer filters).
- [R13.8] ☑ Event-types panel: Select all/none; split Event types + Callbacks; right-click Only
  this / Add / Remove; filtering unchanged (single-grouping OR).
- [R13.9] ☑ Records ▸ Copy selected as YAML. · [R13.10] ☑ Search row: Clear history; field fills width.
- [R13.11] ☑ "LLM" tab renamed **Analyser assistant**.
- [R13.12] ☑ Diff viewer export — CSV / JSON / PDF (`DiffExport` + dependency-free `TextPdf`; 4 tests).
- [R13.13] ☑ Correctness pass — Select-none clears the view (`FilterState`: empty = none, null = all);
  PDF diff lines clipped to page; review sweep fixed filter-echo all-vs-none and a before-heavy `read`
  anchor drop.
- [R13.14] ☑ Toolbar icons — `ToolIcons` 16px vector glyphs, theme/enabled-aware, no image assets.

## Assistant vocabulary — follow-ups (external review) — ☑ DONE
- [AV.1] ☑ **`read` verb** — N records around an offset/index over the socket (rate-limited;
  `ReadService`, 6 tests; snapshot carries byte offsets + `rowForOffset`); sandboxed/remote agents can
  seek the log without filesystem access.
- [AV.2] ☑ **Graph provenance** — `graph` takes optional `rationale`, stored on `GraphSpec.note`,
  persisted, shared, shown as an italic caption under the plot.
- [AV.3] ☑ **Per-verb JSON schemas in `/manifest`** (`VerbSchemas`, draft-07-style; single source of
  truth, also feeds the MCP bridge).
- [AV.4] ☑ **`goto {reveal:true}`** — minimally relaxes the filter to show a hidden record; without it
  the echo names the hiding constraint.

---

# Archived 2026-08-17 (post-M28 prune; newest first)

## M13 · MCP transport — ◧ shipped portion: M13.1–13.4 (2026-08-15); M13.5 remains live
_**Reviewed, approved and merged to main** (report + review: **[handoff_15_aug_2026_1_report.txt](../../handoff/completed/handoff_15_aug_2026_1_report.txt)**).
Stays live only because **M13.5** (resources/prompts, in-app HTTP-MCP) is open; move the milestone to
`completed/` once that lands or is dropped. Review decisions: hand-roll **kept** over an MCP SDK (the era
change was absorbed in ~60 lines — evidence *for* it); `structuredContent` parked with M13.5; move
`ReleaseNotes` to a neutral package as a later non-blocking refactor._
_Design: **[spec-assistant-actions-mcp.md](../spec-assistant-actions-mcp.md)**. MCP as the preferred door for
MCP-native clients (Claude Code/Desktop) — one MCP tool per verb over the **same `ActionDispatcher` /
`RenderExecutor`**. Hand-rolled minimal JSON-RPC (no MCP SDK → keeps the near-zero-dep ethos). Not a
replacement: in-process drives the app's own chat; REST stays the universal zero-config door. AV.3's
`VerbSchemas` already provides the shared tool schemas._
- [M13.1] ☑ **`RestEndpointFile`** — app publishes its live REST url+token+**pid** to `~/.fluxtion-analyser/rest-endpoint`
  (mode 600) on REST start, deletes on stop/exit; bridge does a **pid liveness check** before trusting it
  (clean "not running" vs connection-refused) — so a static MCP client config finds the per-run endpoint.
  Publishing is an **opt-in `ActionServer` collaborator** (the path is injected, not baked in) so the
  server started inside unit tests can't clobber a running app's endpoint; exit-time cleanup is
  ownership-checked (pid) so a second instance's endpoint survives. 9 tests.
- [M13.2] ☑ **`McpTools` adapter + `McpBridge` handshake/list** — `analyser.jar --mcp` (**headless-safe**: sets
  `java.awt.headless`, touches no Swing — enforced against the compiled bytecode, not by review):
  hand-rolled **newline-delimited** JSON-RPC stdio returning one tool per verb (six today, incl. `read`)
  **adapted from the shipped `VerbSchemas`** (AV.3) — the same single source of truth as REST
  `/manifest`; no parallel schema holder. **Shipped dual-era** (spec §2.1, v1.2): MCP's current revision
  `2026-07-28` **deleted the `initialize` handshake** this milestone was specced against, so the bridge
  answers both the legacy handshake *and* modern per-request `_meta` versioning + the now-mandatory
  `server/discover`, with `-32022` for unsupported versions. 26 tests.
- [M13.3] ☑ **`tools/call` → REST forward** — map a tool call to `{action, params}`, POST `/action` with the
  token, wrap `ok:true`→text result / `ok:false`→`isError:true` (same actionable feedback). Reuses slice 4.
  The endpoint is re-read **per call**, so a long-lived MCP client survives an analyser restart (new port
  + token) without being restarted itself. Tool vs transport failure kept distinct: a dispatcher rejection
  or a `429` is `isError:true` (retryable, actionable); only an absent/dead endpoint is a JSON-RPC error,
  carrying the "enable it in Settings ▸ Assistant" hint. 12 tests, incl. a live `ActionServer` round-trip.
- [M13.4] ☑ **Docs** — "Connect an MCP client" on the site's assistant page (tabbed config snippets for
  **Claude Code** `.mcp.json` / **Claude Desktop** `claude_desktop_config.json` / **Codex**
  `~/.codex/config.toml`, all three verified against current vendor docs, not written from memory), plus
  how the per-run endpoint is discovered, a troubleshooting list, the capability boundary, and a README
  highlight linking in. `mkdocs build --strict` green locally.

## M21 · Topology view + event step-through — ◧ shipped portion: M21.1–21.6 + M21.10; M21.7–21.9 remain live
_**Reviewed and approved** ([review_feat_m21-topology.txt](../../handoff/completed/review_feat_m21-topology.txt)),
merged from `feat/m21-topology`. **O5 is formally resolved as complement** by §1 of the design spec —
recorded here per the review's F5, so M18.2–18.4 are no longer positioning-blocked._
_Design: **[spec-graph-replay.md](spec-graph-replay.md)**. Render the processor **GraphML** and **step
through events on it** — nodes that fired lit in dispatch order, with their logged values. Resolves O5:
web-admin sees one live server whose log may vanish; the analyser works on **many archived logs with no
server at all**, which is production support. So the view belongs where the logs land. Wired into the
index, filter, table, value graphs and click-to-source — the cross-view coupling a per-server web tab
structurally cannot have. **Swing/Java2D, no embedded browser** (tracker ▸ Decisions)._
- [M21.1] ☑ **GraphML parse + model** — `topology/GraphMlParser` + `ProcessorTopology` (nodes with
  `instanceId`/class/`Kind`, directed edges, parents/children, roots) and `Match` — the **pair-check**
  against the log's `instanceId` set, distinguishing *a node that never fired* from *a topology from a
  different build*. Lifted from `fluxtion-visualiser`'s Java `GraphMlTopologyParser` (our code): same
  document shape and label conventions, IntelliJ logger dropped, model widened for rendering. Lenient
  like the log parser; XXE refused (a `.graphml` can arrive from a shared store). 24 tests, plus
  validation against three real emitted graphs (69/16/**300** nodes). _Resolution via source roots is
  M21.3's UI work — the parser takes text or a Path today._
- [M21.2] ☑ **Layered layout** — `LayeredLayout` (Sugiyama): break cycles → longest-path layering →
  dummy bend points → median sweeps + adjacent-exchange crossing reduction → median coordinate
  assignment → routed polylines, emitting `TopologyLayout` (plain geometry, no Swing). Deterministic by
  construction. 22 tests on **invariants** (every edge points downward, no overlap, same graph lays out
  identically) rather than coordinates. **Two bugs only the real 300-node graph exposed** — an
  intransitive comparator that made TimSort throw, and bend points consuming a full node's width
  (135661px canvas). Both fixed and regression-tested; layout went 2000ms → **13ms** at 300 nodes.
  Real graphs: 69 nodes → 8 layers, 5924×888. ELK not needed.
- [M21.3] ☑ **Topology panel** — `TopologyCanvas` (Java2D: pan, zoom-at-cursor, fit, hover, select,
  tooltips, kind-coloured boxes, arrowheads, viewport culling, label LOD by rendered pixel width) +
  `TopologyPanel` (toolbar, open `.graphml`, orientation toggle, status line incl. the M21.1 pair-check).
  Added as a **Topology** tab. Verified by **rendering offscreen to PNG and inspecting it** — which
  caught three defects a green test suite did not: the layout sheared into a diagonal (systemic drift in
  the coordinate pass, fixed in `LayeredLayout`), arrowheads were painted underneath node boxes (edges now
  stop at the border), and labels were hidden by a zoom-based LOD threshold.
- [M21.4] ☑ **Step-through** — selecting a record lights the nodes that fired, **numbered in dispatch
  order** (green ring + ordinal badge), fades the ones that didn't, and ◀ ▶ walks the cycle node by node
  with that node's logged key/values on the status line. Bidirectional. **Driven by the table's existing
  selection** — one `topologyPanel.showRecord(focus)` in `onRowsSelected`, no second cursor, per the
  binding reuse constraint. Flags instanceIds absent from the loaded topology (build mismatch) inline.
- [M21.5] ☑ **Cross-view wiring** — right-click a node: **open source** (the same `openNodeSource` the
  detail viewer uses), **graph a key** (the *same* `DetailPanel.GraphTargets` instance, now shared by both
  panels rather than duplicated), **filter records to this node** (routed through the existing search
  field so the box shows what is filtered and can be cleared normally), copy instance id; double-click →
  source. Offered keys come from `KV.graphValue()`, so "graphable" means what it means everywhere else.
  6 headless tests on the menu-population rules (panel constructed, never shown).
  _Node → flag not done: flags are per-record and the index has no instanceId lookup, so "flag every
  record where node X fired" needs index work — filter-to-node covers the same intent for now._
- [M21.6] ☑ **Docs — the Topology tab** _(review F1)_ — `user-guide/topology.md`: reading the graph,
  opening a `.graphml`, the **build-mismatch warning**, stepping a cycle, node right-click actions, and
  why the offline case is the one this tab is for. Nav entry + cross-links from graphs / records /
  user-guide index. Screenshot is a **real render** of the canvas over `sample.yml` against a new
  anonymised `demo-marketmaker.graphml` fixture — and a test pins that fixture to the sample log, so the
  page can't quietly start depicting a mismatch. `mkdocs build --strict` green. **Release gate cleared.**
- [M21.10] ☑ **Intra-record step-through** _(brief: [handoff_16_aug_2026_1.txt](../../handoff/completed/handoff_16_aug_2026_1.txt))_ —
  one cursor walking record → nodeLog row → next record, the topology following it.
  - [S1] ☑ **`StepCursor`** — pure two-depth model over the filtered record sequence: next/prev with
    entry-as-a-stop, backwards roll-over to the previous record's *last* row, per-cycle accumulation,
    entry-point resolution, and **regime-aware labels** ("row 3/8 (logged nodes)" vs "invocation 3/16")
    so a row count is never read as "the nodes that ran". Repeated rows are separate steps, never
    deduped. 16 tests, driven by both real fixtures. No Swing.
  - [S2] ☑ **Cursor overlay** — current position is a **halo drawn outside the box**, the trail a
    weaker one, the entry a dashed one; the node keeps its own execution border and fill underneath.
    Recolouring the border (the obvious implementation) would hide what the log establishes in order to
    show where you are standing — two different questions, both needed at once. Verified offscreen.
  - [S3] ☑ **Wiring** — cursor walks the **filtered** view (`RecordSource` over the table's visible
    rows, so stepping honours the shared filter); `[` / `]` keys (F3 left alone — one key meaning two
    kinds of "next" is worse than a second pair); rolling into another record re-shades the canvas and
    moves the table selection; the row under the cursor is highlighted in the detail viewer's
    `nodeLogs` text **by occurrence**, so a node logging twice highlights the right line. A guard flag
    stops the table ⇄ cursor sync looping.
  - [S4] ☑ **Docs** — `topology.md` "Step through a cycle" rewritten to the two-depth walk (entry as a
    stop, `[`/`]`, halo-over-shading, filtered sequence, detail sync) with the regime readout and the
    logs-twice rule called out; spec-graph-replay §4 records the finalised granularity. `--strict` green.
- _M21.1–2 carry the risk and are pure logic — front-loaded deliberately, testable before a pixel exists._

## M20 · Project profiles — ☑ SHIPPED (2026-08-17)
_Brief: `docs/handoff/completed/handoff_16_aug_2026_2.txt` · Spec: `spec-project-profiles.md` (O1–O4 resolved)._
- [M20.5] ☐ **Project artifact pointers — offer, never act** (owner-requested 2026-08-17, revisits O3
  with the surprise removed). The profile MAY carry optional `graphml=<relative>` and
  `logDir=<relative>` / `logGlob=` entries; on open/switch the analyser **asks** — "Open this
  project's topology (and latest log)?" — the same ask-don't-act gate as auto-detect. Missing files
  ignored silently (never-fail rule); a stale graphml is caught by the existing build-mismatch check,
  which is what makes pointing at build output safe. Rationale for the original exclusion stands for
  *unasked* reopening; this is the offered middle path. Bundle synergy: M19's bundle names exactly
  these artifacts — with M20.5 the bundle profile carries them natively instead of via README prose.
- [B-M20-3] ☑ **FIXED (shipped 2026-08-17): graph persistence ignores the
  active project tier — ALL new graphs write to GLOBAL.** With maker-fxoc ACTIVE, four named graphs
  (one UI-created, three verb-created) sit in `~/.fluxtion-analyser/config` (`graph.0..3`) while the
  project file says `graph.count=0`. Not verb-specific: the graph-save path routes to the global store
  regardless of the active project — the tier split (B-M20-1's mirror: that bug leaked project→global
  on snapshot; this writes new project-work→global). Consequence: on next launch the active profile's
  empty graph set REPLACES the in-memory graphs, so the user's graphs silently vanish from view (they
  resurrect on switching to no-project — maximally confusing). Fix at the tier-routing seam, not per
  entry point; regression tests for BOTH paths (UI save and dispatcher-created) asserting the active
  profile file gains the graph and global does not. _Fix shipped: GraphPanel/GraphTabs change
  notifications → sync + global save (snapshot-shielded) + debounced project save; ProjectSession
  preSave hook re-syncs live tabs before EVERY profile write (stale flush impossible); quit sequence
  flushes the project and uses the guarded sync (the old raw clear could wipe saved graphs when no log
  was open). 3 regression tests incl. the dispatcher-path one; suite 520 green._
- [M20.4] ☑ **Docs — "Working across projects"**, plus the harness work the screenshots needed.
  New user-guide page (nav + a cross-reference from Sharing setups, which now explains merge-vs-open).
  Says *why* a committed profile is safe: the key is not filtered out, it was **never in the project
  tier**, so no setting or mistake can put it there.
  - `screenshot` gained a **`menu:<Name>`** scope — raised as a judgement call (product surface added
    for docs) and **kept by owner decision**, documented in the user guide as a capability in its own
    right: an assistant can show a user where a control is instead of describing it. It: it opens a top-level menu via the selection manager
    and leaves it open so a native capture includes the popup (`menu:close` restores). The painted
    fallback can never show a menu — a Swing popup is a separate layer, not part of the content pane's
    paint. `setPopupMenuVisible` alone highlights the title without laying the popup out, which looks
    right on screen and is empty in the capture.
  - **`screenshot` now raises the window, and so does the harness.** `screencapture -R` photographs a
    REGION OF THE SCREEN, not a window: a browser sitting over the analyser was captured into a docs
    image complete with its URL bar and personal bookmarks. Caught by reading the image before
    committing, which is the only control that can catch it — rule 1 exists because a text sweep cannot
    see inside a PNG. **Nothing leaked was committed.**
  - Capture geometry is now **fixed** (1680×1050). Without it every run produced differently-sized
    images and the whole asset set churned for no visual change; documentation images being
    reproducible is the reason they are generated rather than taken by hand.
  - The harness **backs off on HTTP 429**. The added captures pushed it past the socket's rate limit and
    an unhandled 429 aborted the run mid-way.

- [M20.3] ☑ **Auto-detect a project beside an opened log** — the M19 zero-setup hook. Open a bundle's
  audit log and the profile committed at its repo root offers to configure roots, event processor and
  graphs. `config/ProjectAutoDetect` holds the policy (7 tests) because *when not to ask* is the hard
  part: no profile above the log, the profile is already the active project, this log was declined
  earlier this session, or the log has no local path at all (an `s3://` object streams to a temp
  directory, and a temp directory is not a project). Declines are per session and per log — a later
  launch is a fair time to ask again, and declining one file says nothing about another.
  Deliberately a **question**, not an action: loading a project replaces your roots and graphs, which
  is not something to do to someone because they opened a file. Nested repositories resolve to the
  nearest profile.
  _Verification note worth keeping:_ I tried to confirm the dialog appeared by checking whether the
  REST socket went unresponsive, reasoning that a modal dialog blocks the EDT. **It does not** —
  `JOptionPane` runs a nested event loop, so the EDT keeps pumping and the socket answers normally.
  The heuristic could never have worked; the owner seeing the dialog, plus a one-line diagnostic
  showing the computed offer, is what actually confirmed it.

- [M20.2] ☑ **Open / New / Save-as / Close project, recent projects, and auto-persist.**
  `config/ProjectSession` owns the lifecycle and is headless (13 tests); the File menu is a thin caller.
  Project items are their own group after the log/graph openers — the items above open a *file to look
  at*, these change *which project's settings are in force*, and appending them to the end would file
  "switch my whole working set" next to "Exit".
  - **Auto-persist rides `onConfigChanged()` and nowhere else.** That funnel is what `source_root` and
    `open {processor}` already go through, so verb-driven edits persist with no second code path —
    the brief's NOTE (b). Verified against a running app: three scripted `source_root` calls landed in
    the project profile.
  - Debounced at 800ms, and the *semantics* are tested rather than the timer: fifteen edits, one write.
    A profile is often a committed file and a legible diff is what gets it reviewed. Leaving or closing
    a project **flushes first** — a debounce window is exactly when the last edit would be lost.
  - Import gains the explicit choice: **Merge (share)** stays additive, **Open as project (replace)**
    swaps the project tier and makes the file active. Conflating the two is what made switching pile one
    setup on the last.
  - _Bug found by driving the app, not by a test:_ one `AppConfig` holds both tiers in memory, so
    `saveConfigQuietly()` wrote the open project's roots into the **global** file. Delete that project
    directory afterwards and the user is left with a stale project's settings as their own, with their
    pre-project configuration gone — the thing the spec promises survives. `ConfigStore.save` now takes
    the global tier to persist, and **`ProjectSession` owns startup activation** so the snapshot is taken
    *before* the profile overwrites it. The first fix was incomplete: it was correct code in the wrong
    order, and only re-driving the app showed it.

- [M20.1] ☑ **Tier the config; load/save a profile with REPLACE semantics.** `config/ProjectProfile` is
  pure and headless; 13 tests written as the spec's two-project acceptance story, because an additive
  implementation would pass a shallower one.
  - **The project tier is FIVE categories, not the seven-category M15 whitelist.** The brief's shorthand
    ("the M15 shareable whitelist") is one category too broad in two places: `ASSISTANT` caps and `LLM`
    provider/model are *shareable* with a colleague but are not *project* facts, and the spec's own tier
    table lists them under global. Shareable and project-scoped are different questions.
  - **Decision recorded (the brief asked for one): `graphmlFile` and `recentGraphml` stay GLOBAL.**
    `graphmlFile` is the topology *currently open* — session state of exactly the same kind as the loaded
    log, and open question O3 deferred coupling log state to a profile precisely to avoid that surprise;
    deciding differently for the graph than for the log would reopen half a workspace on every switch.
    `recentGraphml` is a recent-files list, which the spec's global tier names explicitly. **The boundary
    is unchanged** — nothing here required widening it.
  - Replace is **total over the tier**, not over the categories a file happens to contain: a profile with
    no graphs must leave you with no graphs, or A's graphs leak into B and the pile-up M20 exists to fix
    comes back. The scalars (`selectedEventProcessor`, `searchMavenRepos`, `hiddenColumnsSet`) reset with
    their categories — a stale selected processor names a class that need not exist in the new project.
  - One deliberate exception: a profile naming **no Maven repo** keeps the default rather than emptying
    the list. An empty list silently disables source lookup for every dependency, and "I did not say" is
    not "never search".
  - Startup is global → profile. A moved repository clears the pointer, reports it once in the status
    bar, and the app opens exactly as it did before projects existed.

## M25 · Post-1.1.0 drift fixes — ☑ SHIPPED (2026-08-16)
_Found reviewing v1.1.0 main after the release. Three were pre-existing and mine; one is fallout from
review B1._
- [M25.1] ☑ **The manifest lied about its own verb set.** `ActionServer.handleManifest` hardcoded
  `List.of("aggregate","read","filter","graph","goto","flag")` and never grew, so `/manifest` published
  `verbs` naming six and `schemas` describing thirteen — an internally inconsistent document, and the
  `verbs` field is the one a foreign agent reads. `PromptBuilder.restActionManifest` repeated a
  five-verb list in prose, which is the *only* verb list a copy-prompt session ever sees.
  Both now derive from `VerbSchemas`. `ManifestVerbContractTest` pins all three published lists
  (manifest, copy-prompt, assistant guide) and refuses a literal list in the manifest stanza.
  _Why it rotted: `VerbSchemasTest` and `McpToolsTest` pin the schema set and the MCP tool set, so the
  two places that tell a **foreign** agent what it may call were the two nothing guarded._
- [M25.2] ☑ **`analyser_coverage` was undocumented**, and the assistant guide's destructive paragraph
  still claimed exports "can overwrite a file the app knows nothing about" — untrue since B1, and in
  direct contradiction of the FAQ answer the same commit corrected.
- [M25.3] ☑ **`tools/capture-docs.py` was broken by the export guard** — it set no export directory,
  wrote an absolute `/tmp` path, and reused one filename for every capture, so it failed all three of
  the guard's rules at once. Now points the app at a throwaway export directory, asks for a unique name
  per capture, and copies into `docs/site/assets` itself.
  **The guard was not weakened.** Confinement exists because a verb-driven write is one no human
  approved; regeneration is the script's problem and the script solves it on its own side of the socket.
  Verified end to end: all ten assets regenerate, and the output is pixel-identical to what shipped
  (82 differing pixels of 11.6M — PNG encoding noise), so no image churn was committed.

## M24 · Coverage for a graph — ☑ SHIPPED (2026-08-16, owner-requested)
- [M24.1] ☑ **`coverage` verb.** Which declared nodes never wrote audit output in a run. Came out of the
  POC's 309-node round: the harness emitted chiller readings at `i % 12` against a **24**-wide estate, so
  only 2 of 24 chillers were ever reachable and **54 of 275 nodes never ran** — through a clean build and
  a green suite. Nothing in the tool could have told you.
  `NodeCoverage` is pure (5 tests) and keeps three outcomes apart that a naive implementation collapses:
  covered, never-logged, and **silent by design** (a node with no `auditLog` call can never appear, and
  listing it would be the noise that trains people to ignore the report). The reverse direction —
  instanceIds in the log that the topology does not contain — is reported separately as a **build
  mismatch**, because if that is true no other figure on screen can be trusted.
  Read-only, scans off the EDT, and honest in the result: absence is only conclusive under
  `addEventAudit(TRACE)`, and the payload says so.
  _Measured on the POC: 299 declared, 217 covered, 82 uncovered, ratio 0.726._

## M23 · Explaining what you found — ☑ SHIPPED (2026-08-16, owner-requested)
_M23.1–23.6 explain a **trend**; M23.7–23.9 explain a **single cycle**. The owner's framing: "the graph
plot shows trends, this is a particular issue diagnosis."_

- [M24.2] ☑ **Export guard (review B1).** `screenshot`/`report` are opt-in (*Allow file exports*,
  Settings ▸ Assistant, default off) and confined to one export directory via pure `llm/ExportGuard`
  (relative paths land inside it; escapes and overwrites refused; 7 tests). FAQ + assistant.md rewritten
  to the new truth; `FaqSecurityContractTest` asserts every destructiveHint verb is named in the FAQ's
  security answer, so the promise can't silently drift again. Implemented by the reviewing session.
- [M23.7] ☑ **A finding callout on the topology** (owner). A record's diagnosis is painted bottom-right
  over the graph — note in ink, suggested fix in green, an amber bar down the left edge so it reads as
  commentary rather than more log output. On the canvas, not in a side panel, for the same reason the
  chart's explanation is: this picture gets screenshotted into a ticket, and a diagnosis that lives only
  in the app is gone the moment the image leaves it. Clear of the legend (top-left), the HUD and the index
  overlay (bottom-left). Both themes verified against a running app.
  _Design decision worth keeping: the callout has **no text of its own**. It renders the record's
  `Finding` — the flag's note and fix. One write site, three readers (table note column, callout,
  exported report). The `topology` verb therefore gets `callout` as a **visibility** switch only; adding
  an `explanation` field there would have been a second place to write the same sentence, which is how
  this codebase has produced disagreeing halves three times already._
- [M23.8] ☑ **Export a finding as a PDF** (owner). One document: coloured header, provenance strip
  (record / time / event / log / processor), the explanation, the suggested fix, a picture of the
  topology as currently focused, an optional plot, then the event record and the full node log in
  monospace. Everything is taken from **what is on screen** rather than recomputed — a report assembled
  from a parallel query is a document that can disagree with the app it came from.
  - `report/PdfDoc` — a small dependency-free writer: pages, coloured text and rects, Flate/DeviceRGB
    image XObjects, standard-14 fonts only (nothing embedded, opens anywhere). Exposes **top-left**
    coordinates and flips into PDF's bottom-left space internally, so no call site does that arithmetic.
  - `diff/TextPdf` was **reimplemented on top of it**. Two writers emitting the same format is a standing
    invitation for one to acquire a bug the other lacks. `DiffExportTest` unchanged and green.
  - Node logs paginate rather than truncate — a log cut off at the page break is exactly where the
    interesting line tends to be. Every page carries the record anchor and `n / m`, because printed pages
    get separated from each other.
  - Pictures shrink to fit the space left on the page (down to 260pt, below which a screenshot is
    unreadable and a page break is better). A first draft always pushed the image to a new page and
    produced a third-full page 1 — caught by looking at the output, not by a test.
  - 17 tests (`FindingReportTest`) covering the value type's merge semantics, the coordinate flip, PDF
    string escaping, image embedding, wrapping, pagination and footers.
  - _Two defects found by **looking at the exported PDF**, both invisible to the tests that existed:_
    (a) em dashes rendered as `?` — the standard-14 fonts are single-byte and my fallback replaced
    everything above U+00FF with a question mark, which reads as a corrupted file rather than a
    typographic limit. Common punctuation is now transliterated, and the fonts declare
    `/WinAnsiEncoding` so bytes 0x80–0xFF are not read from a 1980s glyph table.
    (b) a 3-line node log widowed across a page break, leaving one line at the foot of one page and two
    on an otherwise blank next page — a section heading was placed with room for less than it needed.
    Both now have regression tests; the widow test **sweeps** the variable that moves the cursor rather
    than guessing one value, because the first version of it passed with the fix deliberately disabled.
- [M23.10] ☑ **The report renders its own graph views, and two of them** (owner). Screenshotting the
  live panel meant the document inherited whatever zoom, pan and toolbar the user had left on screen —
  and the only way to make it look right was to change what they were looking at. `renderCycleViews`
  builds a **detached** `TopologyCanvas`, sizes it for the page, fits and paints it, then discards it:
  the export is side-effect free and framed for the paper rather than the window.
  Two views, because they answer different questions and the second is the one people forget to ask:
  **the trace** (only the nodes the event reached) and **the whole processor with that cycle lit**. What
  stayed grey in the second is what the event did *not* reach — which is the entire evidence for "the
  stock check never fired". A trace alone cannot show an absence.
  `fitToView(maxScale)` was added rather than a second fit method: on screen the 1:1 ceiling is right (a
  four-node graph blown up to fill a window looks broken); in a fixed report frame nothing else can use
  the space, so it magnifies to 2.2×.
  _Evidence was restructured from three nullable image fields to a `List<Picture>` (heading + caption +
  image) — captions matter here, because the same picture of three lit nodes means "this is all that
  ran" or "this is a filtered slice" depending on which view it is, and those support opposite
  conclusions._
- [M23.11] ☑ **The plot is marked with the record under diagnosis** (owner). A chart pasted beside a
  finding shows a trend but says nothing about *which* point of it the finding is about, leaving the
  reader to join a header timestamp to an axis by eye. `ChartPanel.setRecordMarker` draws a dashed rule
  and a `record #N` label. Deliberately **not** a `ChartNotes.Note`: a note is authored and saved with
  the graph, this is a transient pointer at whatever is being looked at, so it is held in its own field
  and can never leak into a saved graph's annotations. Cleared in a `finally`.
  The meta strip also gained **ANALYSED** — when the report was produced, which on an archived log is
  months from when the event happened. A report carrying only the second reads as if it were live.
- [M23.9] ☑ **`flag` carries a `fix`; a human can write one too.** `flag {note, fix}` — supplying one
  keeps the other (`Finding.merge`), so an agent adding a suggested fix cannot wipe the note that says
  what the fix is for. Records ▸ *Write a finding for this record…* is the human path into the same
  store, and *Export finding to PDF…* the human path out. Verified live: a fix-only re-flag preserved the
  note.
  _Also corrected: `screenshot` was never marked `destructiveHint` even though it writes a
  caller-supplied path unconditionally and can overwrite a file the app knows nothing about. It and
  `report` now are._

## M23 · Charts that explain themselves — ☑ SHIPPED (2026-08-16, owner-requested)
- [M23.1] ☑ **Second vertical scale** (`rightAxis`). One shared range is right until two series differ in
  magnitude, and then it is actively misleading — a revenue line at 2,000 beside a stock level at 20
  renders the stock as a flat smear. Both facts on screen, neither readable, and it *looks* like an
  answer. Two axes only: past two, a height needs a legend to interpret and the chart has stopped being a
  picture. `AxisAssignment` is pure with a `suggestFor` heuristic that deliberately refuses to split a
  bid from an ask (comparable series must stay comparable). 8 tests.
- [M23.2] ☑ **Explanation block and pinned notes** (`explanation`, `notes`, `clearNotes`). Drawn **on**
  the plot rather than beside it, so they survive an exported PNG — a rationale that lives only in the app
  is lost exactly when the picture is shared. Notes are numbered on the chart and listed beneath, anchored
  by `at` (epoch millis) **or** `recordIndex`, because a caller that just found something with `read` has
  the index to hand. Colliding notes stack by pixel column instead of overprinting. `ChartNotes` is pure;
  7 tests.
- [M23.4] ☑ **Right-click a chart to pin a note** (owner) — reading a chart is when you notice the thing
  worth writing down, and an annotation you must leave the chart to add is one you mostly do not add.
  Right-click inside the plot gives *Add note here* (the time comes from the cursor), *Add/Edit
  explanation*, and *Clear notes* — which keeps the explanation, because they are different statements.
  The note picks up the series whose line passes nearest the click, in **both** axes: matching on y alone
  labels the note with whatever series happens to cross that height at some other time entirely.
  Annotations persist with the graph (`GraphSpec` + `ConfigStore`), because a note that does not survive
  a restart is one nobody bothers to write. A pre-M23 config still loads, with empty annotations.
- [M23.5] ☑ **Fixed: clicking a node reset the topology zoom** (owner). Self-inflicted in M22: opening
  the source pane re-fits the canvas (correct — its width changed), but `showSourcePane(true)` re-fit
  **unconditionally**, so with Sync on every node click ran `openSource → showSourcePane(true) → fitToView`
  and threw away the zoom. Now it re-fits only when the pane actually appeared or disappeared.
- [M23.6] ☑ **Fixed: clicking a node in a focused view emptied it** (owner). Two causes. The mouse path
  reset the scope to NODE on a new selection, which under focus collapses the view to a single box; a
  focused width is a width the user *chose*, so it now survives a new selection (unfocused still starts
  the cycle fresh — nothing is hidden, so it is harmless). And the saved zoom/pan was being kept across a
  **re-layout**: a different node set is a different coordinate space, so the old view addressed
  coordinates that no longer existed and left the user staring at empty space. The view is now preserved
  only while the visible node set is unchanged.
  _Also aligned `selectNode` (verb) with `onNodeClicked` (mouse) — they had different scope rules, which
  is how a scripted session and a hand-driven one stop agreeing._
- [M23.3] ☑ **All of it over REST/MCP** — the `analyser_graph` verb carries the new fields, so the MCP
  bridge publishes them with no extra work. Verified by driving a live analyser and capturing the result.
  _One bug caught in review: epoch millis do not fit in an `int`, and my first `anchorMillis` parsed `at`
  with `intOrNull` — silently wrapping to a negative and pinning notes somewhere in 1969._

## M22 · Topology view usability — ◧ shipped portion: 36 of 41 (+2 superseded), 2026-08-16
_Open: **22.3** PNG export · **22.6** alternative layouts · **22.11** re-dispatch cause (needs
`UP-FLX-10` upstream, see `docs/proposals/upstream-asks.md`) · **22.19** partial (chips deliberately not
built). Superseded: 22.5, 22.9._
_Owner-specified batch (2026-08-16), ported from what fluxtion-visualiser already does well. The
topology renders correctly but does not yet let you **explore** — these are the affordances that turn a
picture into a tool. Ordered by value/effort; 22.1 and 22.2 are the ones that change daily use._
- [M22.1] ☑ **Hide framework scaffolding** — shipped: a toolbar checkbox (off = hidden, the default).
  **10 of 20 nodes in the demo graph
  are scaffolding** (`context`, `clock`, `nodeNameLookup`, `callbackDispatcher`, `subscriptionManager`,
  `serviceRegistry`, `eventLogger`, `ClockStrategyEvent`, `EventLogControlEvent`, `ServiceListener`), so
  the user's actual graph is a third of what is drawn. Detection must handle **both** label shapes seen in
  real graphml — a package-qualified `class:` (`com.telamin.fluxtion.runtime.…`) and a bare simple name —
  plus EVENT nodes whose `class:` is absent entirely, which need matching by id.
- [M22.2] ☑ **Selection-driven focus** — shipped. `TopologyFocus` (pure, 15 tests) holds the cycle
  *node → +neighbours → +all routes → whole graph → node*; clicking the same node widens one step,
  Cmd/Ctrl-click (Cmd on macOS — plain Ctrl-click is the popup trigger there) adds to the selection, **F**
  or the Focus button hides everything outside the scope. Unfocused, the scope is **dimmed rather than
  hidden**: a node you cannot see reads as a node that is not there, which is the one confusion this view
  exists to prevent.
  Three things that had to be right, each a place the obvious implementation is wrong:
    - **classification is pinned to the full graph** (`setClassificationTopology`). `classifyCycle`
      reasons from parents and reachability, so filtering the drawn graph would change what the view
      claims about the same log — ticking a checkbox could turn RAN_SILENTLY into MAY_HAVE_RUN;
    - **every graph question uses the full topology** — entry-point resolution, build-mismatch matching,
      "not in this topology", parent/child counts. What is drawn is a display choice; what the graph says
      is not;
    - **filters keep the zoom/pan and selection** (`setTopology(view, keepView)`); re-fitting on each
      toggle discards where the user had navigated to, which defeats the exploring.
- [M22.8] ☑ **Shape carries the kind** — event = stadium, exported service = hexagon, everything that
  computes = rounded rect. Fill alone failed in greyscale, on a projector and for colour-blind readers,
  and the three roles are the first thing you need to read. _(Answers "how do you render exported
  services": they are the hexagon — the only one in the demo graph is the framework's `ServiceListener`,
  which 22.1 hides, so a user-authored `@ExportService` in the fixture would show it better — see 22.10.)_
- [M22.9] ⊘ **Source without losing the graph** — SUPERSEDED by M22.13, which answers the layout question
  it left open (the source view splits Processor/Node rather than the topology tab embedding a viewer).
  _Original note:_ **Source without losing the graph** — double-click currently opens the Source tab, which is a
  *sibling* of Topology in the same tabbed pane, so navigating to source hides the thing you navigated
  from. Options: split the side pane (graph above, source below) when navigating from the topology; or
  give the Topology tab its own embedded source view. Owner-raised; needs a layout decision before code.
- [M22.10] ☑ **A user-authored exported service in the demo fixture** — shipped. `QuotePublisher`
  implements `@ExportService QuoteControl` (`suspendQuoting`/`resumeQuoting`, both `void` per
  `claude.txt`), so the compiler emits a **separate `QuoteControl` EXPORTSERVICE node** with an edge into
  `quotePublisher` — an exported service is its own entry-point node, not a marking on the implementing
  node. Two defects came out of using real artefacts: the generator was copying a **stale** graphml from
  `target/generated-resources` (the plugin writes back into the source tree), and `EntryPointResolver`
  knew only the fully-qualified signature spelling, so every exported call resolved to no entry point.
- [M22.4] ☑ **Text-size and separation sliders** — shipped, plus a **Show all** reset (clears the
  selection and focus so nothing is dimmed; clicking empty canvas does the same, but only if you know it
  does). `Config.withSpacing()` scales the **gaps only** — growing the boxes would also move the
  label-visibility threshold, which is keyed to box pixels. Label size is deliberately **independent of
  zoom**, which settles M22.5: labels that scale with zoom read well zoomed in and become unreadable
  zoomed out, exactly when you most need to know what you are looking at. The spacing slider reports on
  drag-settle so a 300-node layout does not re-run per pixel. _(Not yet persisted in `AppConfig`.)_
- [M22.5] ⊘ **Text scales with zoom** — DECIDED AGAINST, see M22.4: label size is a slider, independent
  of zoom. _Original note:_ **Text scales with zoom** _(design question, not just work)_ — today the font is a fixed
  screen size and labels disappear below a pixel threshold, so zooming out shrinks boxes around
  constant-size text. Growing text with zoom reads better while zoomed in but re-introduces unreadable
  labels when out. Likely answer: scale within a clamped band, keeping the existing threshold.
- [M22.7] ☑ **Split Open Recent** — shipped: *Open recent audit log* and *Open recent GraphML*
  (`AppConfig.recentGraphml`). One list would mean scrolling past logs to find a graph, and picking the
  wrong kind silently does nothing useful. A `onTopologyLoaded` listener records the graph from **every**
  entry point — the toolbar chooser, the recent list and a drop — rather than each caller remembering to.

### Added 2026-08-16 (owner, from the playground visualiser reference)
_Reference implementations reviewed: `mongoose-plugins/service/svc-admin-web/src/main/resources/web`
(`visualiser/scaffold-filter.js`, `replay/replay-engine.js`, `replay/eventlog-parser.js`) and the
playground's `audit/step-through` screen. Findings: our `Scaffolding` is already a **superset** of
`scaffold-filter.js` (which matches by class name only, so framework EVENT nodes stay visible); our
`StepCursor` is a superset of `replay-engine.js` **except** it has no play/pause autoplay; and **neither
reference handles re-dispatch at all** — their replay engines are record→step only, so M22.11 is new
design, not a port._

- [M22.12] ☑ **Node-log panel: Logical and Text views** — shipped. Logical gives each node a block with its
  values on their own lines, in dispatch order; Text is the raw YAML, kept one click away because it is the
  **evidence** for anything Logical re-arranges. Framework keys (`thread`, `method`) are **muted, not
  hidden** — they are the marker that says the record is traced, so dropping them would hide the regime.
  Layout is separated from colouring (`LogicalLogView.Layout`) because the block offsets drive
  click-to-source and the step cursor's highlight: an off-by-one opens the wrong file rather than merely
  looking wrong. 9 tests.
- [M22.13] ☑ **Source view: three modes — Processor · Node · Split** — shipped. `SourcePanel` now holds two
  independent panes, each parsing what it shows into its own `EventProcessorModel` so Ctrl-click navigation
  works from either half. Navigating to a node while in Processor mode **promotes the view to Split**
  rather than replacing what you navigated from — the whole point being that the call site (and the guard
  above it that decides whether the node runs) and the method body need to sit still at the same time.
- [M22.14] ☑ **Source view fills the viewport with wrap off** — `getScrollableTracksViewportWidth()`
  returned the wrap flag, so the pane sized to its longest line and the rest of the viewport showed the
  scroll pane's background. Now tracks the viewport whenever the text is narrower (and the same
  vertically); long lines still scroll horizontally.
- [M22.15] ☑ **"No source to show" instead of a 5%-tall empty editor** — the empty state now fills the
  panel and names the roots actually searched plus `File ▸ Settings… ▸ Source roots ▸ Add…`. An empty
  editor says "nothing here" when the truth is usually "looking in the wrong place".
- [M22.16] ☑ **Window-span selector moved to the left** of the time-range bar (owner).
- [M22.17] ☑ **Collapsible event-types panel behind a vertical nav bar** — shipped. `NavRail` draws
  bottom-to-top labels as an `Icon`, so hover/pressed/selected stay FlatLaf's to render. Collapsed state
  persists (`AppConfig.eventFilterCollapsed`) — a window that forgets its layout teaches people not to
  adjust it. **Deviation:** Columns is *added* to the rail as a popup rather than *moved* off the menu bar;
  removing a menu that people already know costs more than the duplication saves.
- [M22.18] ☑ **Panel surfaces must read as surfaces** — shipped. `UiTheme.surface()` / `surfaceEdge()` /
  `applySurface()` give the source, record-detail and topology panels one content surface with a hairline
  edge. The canvas's old light value (`0xF6F8FA`) sat within a shade of FlatLaf's panel grey and lost its
  own boundary. `applySurface` paints the **viewport** as well as the view: the viewport is what shows
  through around the margins and during a resize, which is what made a short document look like a strip.
- [M22.47] ☑ **The app is scriptable end to end** (owner) — four new verbs take the set from six to ten:
  **`topology`** (select · scope · focus · scaffolding · step · record · source pane · orientation · fit ·
  showAll, echoing the full cursor state), **`open`** (log / graphml / **processor**), **`source_root`**
  (add / remove), **`screenshot`**. The MCP bridge picked all four up with no work, as
  `spec-assistant-actions-mcp` promised — the verb set is enumerated from `VerbSchemas`.
  Design points worth keeping:
    - the filesystem-reaching verbs live behind their own `AppControl` interface, separate from
      `RenderExecutor`. Render verbs rearrange what is loaded and are reversible; `open` replaces the log
      (losing in-session flags) and `source_root` writes config. They are marked
      **`destructiveHint: true`** to MCP for that reason — calling them reversible because "no file is
      deleted" would be true and useless;
    - **`screenshot` has the app paint itself** rather than asking the OS. A macOS screen grab needs the
      Screen Recording permission, which a headless caller cannot grant; painting has no such gate and is
      deterministic. It cannot draw the native title bar, so the echo carries `windowBounds` for a caller
      that *does* hold the permission to capture the same window with `screencapture -R`.
  `tools/drive-analyser.sh` + `tools/README.md` document the whole loop.
- [M22.48] ☑ **Four bugs the scripted run exposed** — none would have been found by reading the code:
    - opening the source pane left the graph **clipped**: the canvas kept a frame sized for the old width.
      It now re-fits;
    - both source panes were **blank** before anything was opened, which reads as broken rather than
      empty. They now say what they are waiting for;
    - `source: true` showed an empty pane next to a selected node. Asking for the source view is asking
      to see source, so it now opens the selection;
    - **adding a source root did not re-run processor inference**, so source navigation kept reporting
      "no source mapping" with the source sitting right there. Adding a root *is* the statement "the code
      is here", so it re-infers. That in turn exposed the need for `open {processor}`: inference only
      considers candidates in the package of the currently-selected processor, so a differently-packaged
      one is invisible to it.
- [M22.46] ☑ **Docs and in-app help brought up to date** (owner). `user-guide/topology.md` gains the
  exploration model (scaffolding, scope cycle, focus, index), the new open/persistence behaviour, record
  skip + autoplay, the edge-highlight rule and the source split; its screenshot was **three months of
  features stale** — it still showed the scaffolding nodes that are now hidden by default. The bundled
  `help/help.html` had **no topology section at all**, which was the bigger gap: it is the largest feature
  in the app and the in-app guide did not mention it. Screenshots are regenerated by a script against the
  demo fixture, so they can be refreshed rather than re-photographed. `mkdocs build --strict` green.
- [M22.44] ☑ **Topology zoom, pan and orientation persist** (owner) — `topologyZoom` / `topologyPanX` /
  `topologyPanY` / `topologyOrientation`. Applied **after** a load rather than before, because loading
  fits the graph and would overwrite them, and used **once**: a later load of a different graph fits
  normally rather than jumping to where an unrelated graph happened to be scrolled. Saved on zoom, on
  pan-*end* and on Fit — never mid-drag, which would rewrite the config file hundreds of times per
  gesture. Zoom `0` is the "never saved" marker.
- [M22.45] ☑ **Settings ▸ History clears the new settings too** (owner asked whether it did — it did not).
  *Clear recent files* now also clears the recent-GraphML list and both "last opened" paths — one idea to
  the user ("forget what I have had open"), and leaving the topology quietly remembered would look like a
  bug. A new **Reset topology view** button covers zoom/pan/orientation/spacing/label size, and *Clear
  all* includes both. Tested that the reset restores **every** display default: a reset that leaves one
  behind looks broken.
- [M22.43] ☑ **Dark-theme node fills lifted for contrast** (owner: "getting lost when not dimmed"). M22.28
  fixed the *light* theme's invisible plain node but left dark only a few steps off its canvas, so an
  undimmed node barely read as filled. All five kinds are raised, and **the border moved with them** — at
  `0x30363D` it was darker than the new fills, so lifting the fills alone would have traded one vanishing
  element for another. Both states re-checked by render: undimmed nodes read against the canvas, dimmed
  ones still recede.
- [M22.41] ☑ **Fixed: arrow-key stepping stopped working** (owner). A `JSplitPane`'s look-and-feel binds
  Up/Down/Left/Right to move its divider in the **ancestor** input map, and key lookup walks *up* from the
  focused component — so the split added in M22.37 sat between the canvas and the panel and was consulted
  first. Adding a source pane silently broke stepping, and nothing threw. The four strokes are now
  shadowed on the split with a name no `ActionMap` defines, which makes `processKeyBinding` return false
  and keep walking rather than consume the key; drag and F6/F8 still move the divider.
  Pinned by a test that **mirrors Swing's own lookup** — walks from the canvas up and asserts the first
  ancestor that both binds the stroke and has an action is the panel. Asserting the panel's own map (as
  the existing tests did) passes happily while an ancestor eats the key.
- [M22.42] ☑ **The topology showing at shutdown reopens on start** (owner). `AppConfig.graphmlFile`
  alongside `logFile` — the graph is half of the same working state, and having to find it again every
  launch is what stops people leaving it open. Silent when the file has moved: a startup dialog about a
  file you have not thought about in a week is noise, and the tab already says nothing is loaded.
- [M22.39] ☑ **Topology toolbar is controls only** (owner: "too busy and confused"). The two readouts —
  step position and selection scope — moved to the status line, and *Open .graphml…* moved to the File
  menu. A toolbar is for controls: readouts wedged between a slider and a play button are hard to find
  and make the toolbar's width jump as their text changes. The status line is now composed from four
  independently-owned parts (what is happening · step position · selection scope · what is hidden) rather
  than each caller overwriting one string.
- [M22.40] ☑ **File menu: added *Open GraphML…*, renamed *Open from S3…* → *Open log from S3…*** (owner).
  Opening a topology is the same kind of act as opening a log and belongs beside it; the S3 rename says
  which of the two it opens, now that there are two.
- [M22.35] ☑ **Fixed: Show all sometimes left nodes dimmed** (owner). `showRecord` re-shaded
  unconditionally, and the table re-fires its selection for reasons the user never caused — a re-filter, a
  repaint, regaining focus — so the clear was silently undone. Now a repeat notification for the **same
  record** (by identity) neither resets the cleared flag nor re-applies the shading; only a genuinely
  different record does. This is why it was intermittent rather than broken.
- [M22.36] ☑ **Repeated clicks always cycle the scope** (owner: "sometimes misinterpreted as a
  double-click"). Java increments `clickCount` for successive clicks, so click 2 of a fast cycle arrived
  as a double-click and opened source. Double-click activation is **removed from the canvas** — the two
  gestures cannot coexist when one of them *is* repeated clicking. Source navigation is now **Enter** on
  the selected node, the node context menu, or a double-click in the index overlay, all of which are
  unambiguous.
- [M22.37] ☑ **Source opens beside the graph, draggable** (owner; completes what M22.13 began). The
  Topology tab holds its own `SourcePanel` in a horizontal split, sharing the app's `SourceService` so the
  processor selection and roots are the configured ones rather than a second set. Navigating no longer
  switches to the sibling Source tab, which hid the thing you navigated from.
- [M22.38] ☑ **Nested classes resolve to their enclosing file.** Exposed by M22.37: the demo's own nodes
  are `Nodes.QuotePublisher`, and the resolver looked for `Nodes/QuotePublisher.java`. Fluxtion's examples
  group nodes inside a holder, so source navigation failed on exactly the shape the framework teaches.
  Trailing capitalised segments are now dropped in turn, stopping at the package — a lower-case segment
  cannot enclose a class. 8 tests, including that a real file still wins over the fallback.
- [M22.34] ☑ **Show all / background click returns the plain, fully-lit graph** (owner). Clearing the
  selection was not enough: **selection dimming and execution dimming look identical on screen**, so a
  graph with a record selected stayed half-faded and there was no way to see the whole thing plainly. Both
  are now dropped together — selection, focus, emphasis *and* the cycle shading. Nothing is lost: stepping
  (↓/↑), the record buttons, *Whole cycle* and selecting a row all restore the shading, and `stepBy`
  restores it **before** moving so the first keypress after a clear does not silently do half of what the
  second does.
  A background press is also how a **pan** starts, so the clear is held until release and dropped if the
  mouse moves — otherwise dragging the canvas would wipe the shading every time.
- [M22.29] ☑ **Only edges that carried dispatch light up while stepping** (owner). Highlighting every
  edge touching the current node is right with no cycle on screen and **wrong** the moment one is on:
  stepping into `quotePublisher` on an order cycle lit its `QuoteControl` edge, asserting that an operator
  called the service — the one thing that definitely did not happen. An edge is now hot only when **both
  ends ran** (LOGGED or RAN_SILENTLY). MAY_HAVE_RUN is excluded on purpose: it means the log does not say,
  and a highlighted arrow is an assertion.
- [M22.30] ☑ **Bend points clear the boxes beside them** (owner: "the box clips the arrow… they look like
  dependants, not siblings"). `dummyWidth` 8 → 28. A long edge's bend sat close enough to the real node
  next to it that the line appeared to touch the box, and a line touching a box reads as an edge into it.
  _(The layering itself was correct: `riskMonitor` is one layer above `quotePublisher` because
  `quotePublisher` also waits on `spreadCalculator`. Longest-path layering, not a sort-order bug.)_
- [M22.31] ☑ **Index click scrolls the node into view** (owner) — `canvas.centreOn`, pan only. Picking a
  name is a request to go there, not to change how much of the graph you can see.
- [M22.32] ☑ **Topology display prefs persist and are never exported** (owner) — `topologySpacing` /
  `topologyTextSize` in the config file. `SettingsShare`'s whitelist is opt-in in both directions, so
  leaving them out of every `Category` *is* the mechanism; a test asserts they never appear in a share
  file. Same reasoning as the theme: a fact about this screen and these eyes.
- [M22.33] ☑ **One right-click menu on the records table, not two.** M22.22 added a full popup while an
  older three-item `installTableContextMenu` (Diff / Explain / Flag) was still installed — two listeners
  on the same table, and the narrow one is what the owner was seeing. Removed, and *Explain selected with
  LLM* folded into the shared `addRecordActions` so both entry points carry it.
- [M22.24] ☑ **Selection is marked positively, not by dimming alone** (owner: "too subtle"). The clicked
  nodes get a heavy accent ring and an accent-tinted fill; nodes their scope *reaches* get a lighter ring;
  everything else fades harder (0.22 → 0.16). "What did I select" should be answerable by looking at the
  selection, not by comparing the whole graph against itself.
- [M22.25] ☑ **"N scaffolding node(s) hidden" on the status line**, not just beside its checkbox — it is a
  statement about what you are looking at, and half the graph being absent is the most misleading thing
  this view can do quietly. All eight status writes now go through one setter so no caller can drop it.
- [M22.26] ☑ **Collapsible index overlay**, bottom-left of the canvas (owner, from the playground
  reference): sections for Nodes / Events / Services, click to select, double-click to open source.
  Hunting for a box does not scale — zoomed out the labels are gone, zoomed in most of the graph is off
  screen; a list is immune to both. Built from the **full** graph, so it is how you reach a node the
  filters have hidden. Sections start collapsed (expanded, three lists cover half the canvas) and an empty
  section is omitted rather than shown — most graphs export no services.
- [M22.27] ☑ **Node tooltips show the class javadoc** (owner asked whether they did — they did not).
  `Javadoc.forType` is a deliberate text scan, not a parse: the analyser reads generated processors and
  classes whose dependencies are absent, so anything needing a resolvable compilation unit would fail on
  exactly the files most worth reading. Cached per class — a tooltip fires on every hover. 11 tests,
  including that it does not steal a comment belonging to something else.
- [M22.28] ☑ **Node boxes smaller and the plain-node fill visible** (owner). 160×48 → 132×40: the boxes
  carried more weight than the edges, which are what the graph exists to show. And the `NODE` fill was
  `0xFFFFFF` on a `0xFCFDFF` canvas — invisible, **caused by M22.18** moving the canvas to a near-white
  surface; dark was one hex step away too. Both are now a distinct slate.
- [M22.23] ☑ **The side-split divider stays put when you change tab** (owner). A `JTabbedPane` reports the
  **selected** tab's preferred size as its own, and the tabs differ widely (the topology canvas asks for
  640×420, a chart more), so the split re-laid out to suit whichever tab was showing and the divider
  walked about. Fixed by pinning each tab's minimum size, fixing the divider size, and restoring the
  divider location after a tab change.
- [M22.22] ☑ **Records: right-click on the table; Columns off the menu bar** (owner). The record actions
  (flag, show-flagged-only, clear, copy as YAML, diff, export CSV/YAML) plus the column chooser are now on
  the table's context menu, and **Columns is no longer a top-level menu** — the nav rail and the
  right-click both reach it, and you are at the table when you notice a column is missing. The popup is
  rebuilt per click so enabled states match the selection (Diff needs exactly two); right-clicking outside
  the selection selects the row under the cursor first, as elsewhere. One shared `addRecordActions` builds
  both entry points so they cannot drift, and the *Show flagged only* checkboxes are kept in step.
- [M22.21] ☑ **Control clusters tint away from content** — the time-range bar is a *control*, not a
  document, and shared the panel background with everything around it. `UiTheme.controlSurface()` shifts
  the **theme's own** `Panel.background` (lighter in dark themes, darker in light) rather than naming a
  colour, so it holds for Light/Dark/IntelliJ/Darcula and anything added later. Theme switching re-applies
  it: `updateComponentTreeUI` preserves an explicitly-set colour, so a stale tint would otherwise survive.
- [M22.20] ☑ **Code type** — Swing's logical `Monospaced` is a per-platform alias that lands on Courier on
  some machines, so `UiTheme.mono()` picks the best installed family (JetBrains Mono → SF Mono → Menlo →
  …) and `applyReadingRhythm()` opens line spacing to 0.18. Swing sets lines at the font's own leading,
  which is most of why dense key/value output reads as a block next to the same content on a web page.

## M27 · Topology focus as a filter context — ☑ SHIPPED 2026-08-17 (merged; review approved)
_Design: **[spec-topology-focus.md](spec-topology-focus.md)**. Owner correction to M22's focus model:
focus is a **filter operation**, not a view toggle — the focused subgraph becomes "the whole graph" for
every subsequent operation (contexts NEST); node clicks cycle scope within the context; canvas click
clears dimming only and never exits the filter; exit is explicit and stack-shaped (Esc pops, Show all
pops-to-full). Execution shading stays computed on the full graph with out-of-context propagation
indicated at the boundary, never cropped silently. Plus **named focuses** — save/recall/share a context
by name (project-tier, instanceId-based, mismatch-surfaced) — the large-graph payoff. Same seam as
in-flight H4; coordinate._
- [M27.1] ☑ **FocusContext + context stack** in `topology/TopologyFocus` — pure, headless: nesting,
  context-relative scope cycling, boundary detection, pop semantics.
- [M27.2] ☑ **UI rewiring** — canvas-click = clear-dim only, Esc = pop, Show all = pop-to-full,
  breadcrumb in the status line, boundary indication. (Behaviour change to a shipped gesture.)
- [M27.3] ☑ **Named focus** — save/picker/persist (project tier; keep PROJECT_SCOPED at five pinned
  categories — fold deliberately, say so) + SettingsShare + verb alignment: `topology {focus: name,
  pop}` recalls/exits, `{saveFocusAs, rationale}` saves — **agents may create named focuses** (owner
  decision; AV.2 graph precedent: rationale-captioned, replace-by-name, not destructive-hinted, no
  FAQ change). Changelog must call out the gesture change.
- [M27.4] ☑ **Docs** — exploration section rewritten around the filter-context model; harness
  screenshots.

## M26 · Agent-efficiency verbs — ☑ SHIPPED 2026-08-17 (merged; review approved) (the analyser computes, the agent concludes)
_Design: **[spec-agent-efficiency.md](spec-agent-efficiency.md)**. The analyser is an un-metered local
JVM holding the whole log behind the index; the agent is token-metered — so any question answerable by
an index/series scan should be a **verb**, not a paged raw read. Every item came from a real friction
in a production-log MCP session (finding "spread > 0.004" took five hand-anchored reads and manual
arithmetic; one scan should answer it). All read-only — no change to the FAQ security answer, and
`FaqSecurityContractTest` must not need touching._
- [M26.1] ☑ **`series` scan** — stats + threshold crossings over any key or formula (STRICT/LOCF),
  filter-scoped, edge-events with a hard cap and an explicit `truncated` flag, off the EDT. Reuses
  `SeriesExtractor`/`Expr`; auto-publishes over MCP via `VerbSchemas`.
- [M26.2] ☑ **Time anchors** — `read {at}` / `goto {at}` resolve epoch millis to the record
  at-or-before (index binary search); kills records-per-minute estimation arithmetic.
- [M26.3] ☑ **`read.fields` projection** — compact `{recordIndex, logTime, values{}}` rows for named
  `instanceId.key`s (wildcards ok; last-occurrence semantics); raw text stays the default.
- [M26.4] ☑ **Echo hardening** — `graph` warns on a `rightAxis`/note series not in the graph; verbs
  name ignored parameters in their echoes. Docs + changelog.

## M28 · Expression conditionals + rolling windows — ☑ SHIPPED 2026-08-17 (merged; review approved — F1 rate() bias fixed on branch, F2 resolved by disclosure/D-W2b) (formulas that judge and remember)
_Design: **[spec-expr-conditionals-windows.md](spec-expr-conditionals-windows.md)**. Owner ask:
`if(x−y > 10, f(x))` conditional plotting and rolling-window memory formulas. Two bounded vocabulary
additions to `Expr` — NOT a scripting engine — landing in graphs and the `series` verb at once because
they share the engine. NaN-is-no-point carries the design: a false condition or unfilled window simply
plots nothing. Windows need the one real refactor: per-scan stateful evaluators (`newEvaluator()` +
`EvalContext{logTime, values}`) — the AST stays immutable. Three window-semantics decisions (what
enters a window / NaN handling / STRICT-LOCF interaction) are proposed in the spec for review._
- [M28.1] ☑ **Conditionals** — comparisons +
  `if(cond, then[, else])` + `and/or/not`; two-arg `if` defaults else to NaN = "plot only when".
  Stateless, no signature changes; ships alone.
- [M28.2] ☑ **Evaluator refactor (W0)** — per-scan compiled MIRROR (position-keyed state, not
  value-equal-node-keyed: `delta(x)+delta(x)` must be `2*delta(x)`); `Expr.eval(Map)` DELETED so the
  compiler enforces migration; all three call sites (extractExpr STRICT/LOCF, SeriesScan) migrated.
- [M28.3] ☑ **Count windows** —
  `lag/delta/mean/sum/rollingMin/rollingMax(x, N)` (min/max overload REJECTED in review: breaks
  `min(4, 2)`); D-W1/2/3 semantics pinned by test, incl. gate-output vs gate-input and
  `delta(x)+delta(x) == 2*delta(x)`.
- [M28.4] ☑ **Time windows** — duration literals
  (`"5m"`), `rate`, the D-W3 arrival-rate steer; docs (incl. the gate-output/gate-input table) +
  changelog.
- [M28.5] ☑ **Guide lines (P1)** — labelled threshold
  rules on either scale; share-surface checklist honoured (spec round-trip, share ride-along,
  restore-not-an-edit, disclosure row updated).
- [M28.6] ☑ **Condition bands (P2)** — condition
  persists, intervals recompute with the series' own extraction pass; same checklist.


## M29 · External series — ◧ shipped portion: M29.1–.4, 2026-08-18 (merged; review approved; M29.5 optional embed stays live) (plot what the outside world did)
_Design: **[spec-external-series.md](spec-external-series.md)**. Owner ask: an agent filters and parses a
foreign log (FIX to begin with) into a CSV, hands the analyser the file location, and the analyser plots it
beside the audit-derived series. **The analyser never learns a foreign format** — the agent adapts, the tool
stays hermetic. The work is honesty, posed as five decisions: the clock domain is **declared, never
inferred** (D-F1); foreign series are **permanently second-class** — no recordIndex, stamped external in
every export (D-F2); **no foreign refs in formulas** (D-F3); reads are **confined and their diagnostics
sanitised** — exchange dir + chooser-as-grant, the widened "Allow assistant file exchange" opt-in (D-F4);
saved graphs store portable paths and **degrade out loud** (D-F5). Review closed three contract gaps:
out-of-order rows sort with a running-max reorder echo, duplicate timestamps both kept, 5M-row cap
refused loudly._
- [M29.1] ☑ **Loader + CSV contract** — explicit time/zone/value columns, no sniffing; sort-on-load with
  reorder echo; duplicates kept; 5M-row cap; bounded sanitised parse diagnostics. Headless and pure.
- [M29.2] ☑ **UI** — *File ▸ Add series from CSV…*, legend marking, offset display, D-F2 painted export stamp.
- [M29.3] ☑ **`graph {external}` verb** — M26.4-style echo (rows/skipped/reordered, range, offset);
  ExportGuard.resolveRead confinement; FAQ read rule contract-test pinned.
- [M29.4] ☑ **Persistence + sharing** — portable paths, honest degradation, export-side disclosure;
  docs + changelog.

## M30 · Rolled log sets — ☑ SHIPPED 2026-08-18 (merged; review approved — F1 violation-cap promise fixed post-review) (one session, many files)
_Design: **[spec-rolled-logs.md](spec-rolled-logs.md)**. Owner ask: open a set of same-rooted rolled
files (date-time or index suffixes) as ONE log, with time validation catching sets that are not
correctly ordered. Principle: **names discover; content orders; violations are reported, never
repaired** — file order comes from each file's first `logTime`, disordered records surface as a
`TimeOrderReport` (UI banner, `open` echo, `context`), never silently re-sorted. `recordIndex` stays
the global gap-free anchor; byte offsets become (file, offset) pairs. Opening a set is offered, never
assumed; memory scales per SET total (D-R6 as corrected in spec review). Monotonicity checking also
landed for single files — A2 finally checked. Post-merge review found the violation cap not keeping
its javadoc promise; fixed with `unexaminedFiles` counted, summarised and in `isClean()`._
- [M30.1] ☑ **`RollSetResolver`** (pure) — suffix grammars, head/tail time probe, content ordering;
  both logrotate-convention fixtures pass without configuration.
- [M30.2] ☑ **Composite store** — per-file backends under one global index, per-record file id,
  (file, offset) anchors through read/goto/crossings/context/copy-prompt.
- [M30.3] ☑ **Validation surfaced** — banner, verb echoes, single-file monotonicity check, D-R4
  caveats on time-anchored features.
- [M30.4] ☑ **Offer + `open {logs}`** — offer-never-act UI, verb + schema, docs + changelog.

## M31 · Log-source plugins — ◧ shipped portion: 31.1–.3 + author guide, 2026-08-18 (merged; review approved; out-of-tree example reader is cross-repo, stays live) (other containers, same records)
_Design: **[spec-log-source-plugins.md](spec-log-source-plugins.md)**. Owner ask: parquet / Chronicle /
DB audit sources as **plugins, not a requirement**. The core understands ONE thing — the Fluxtion audit
record — and containers adapt: a reader SPI (identity, `canOpen` by content, canonical record text in
container order, MANDATORY timeBase, capability flags) with the CORE building index/store above it.
Plugins are jars the user explicitly installs (per-jar classloaders, parent-first for the SPI + JDK,
child-first else — review P1's textbook shape; the arbitrary-code warning named in FAQ + Settings and
contract-test pinned; a plugin can only be a READER, never verbs). The registry — not the SPI — routes
the built-in YAML reader to the existing optimised stores, so the seam costs the fast path nothing.
Capabilities degrade loudly (byteAnchors=false → read/goto refuse offset anchoring naming the
capability and the alternative). The separate `analyser-reader-spi` artifact is deferred (owner
decision on multi-module build)._
- [M31.1] ☑ **The SPI + text parser behind it** — suite green unchanged; in-tree toy reader proves the
  seam headlessly.
- [M31.2] ☑ **Registry + isolation + Settings ▸ Plugins** — trust boundary in FAQ, contract-test pinned.
- [M31.3] ☑ **Capability wiring + `open` integration** — loud degradation, `format` override, refusal
  names installed plugins.
- [M31.4] ◧ **Plugin-author guide** — docs-site half shipped; the out-of-tree example reader lives in
  the playground repo (cross-repo remnant, stays in the live tracker).

## M32 · Marker series — ◧ shipped portion: core 32.1–.5, 2026-08-18 (merged; review approved; Flags rug + PDF table + external-CSV source stay live) (events on a value chart)
_Design: **[spec-marker-series.md](spec-marker-series.md)**. Owner ask: buys/sells on a price plot
with the client order id and a distinctive point style, plus point-snapped mouseover. Markers are
`(time, y, payload, recordIndex)` drawn as glyphs; payloads are DISPLAY CARGO — hover, click→goto,
exports — never computable. Density degrades to a count glyph with ×N badge, never silence and never
soup. Persisted as SOURCE not points; fifth artifact on the Graphs share category. Post-review fixes
on the same branch: D12 (a marker riding a series rides its SCALE — `riddenSeries` resolved against
the axis at paint time) and R7 (markers got their OWN palette after the first real capture showed
marker colour (1+5)%6 IS series colour 0). Docs shots captured and embedded: markers + the guardrails
composite (guide + band + markers + on-plot explanation and pinned note)._
- [M32.1] ☑ **Point-snapped mouseover, all series** — severed in review, shipped first; pure SnapSearch,
  own-scale distance, decimated columns answer min/max.
- [M32.2] ☑ **Model + extraction** (pure) — key-triple + condition sources on the existing record walk,
  series-pinned `y` with the dangling-pin loud-degrade rule, density aggregation as data.
- [M32.3] ☑ **Rendering** — glyphs, count badges, payload on the M32.1 tooltip, click→goto, the axis
  lane. (Flags rug deferred — live remnant.)
- [M32.4] ☑ **Verb** — `graph {markers}`, REPLACE + warnings contract, M26.4 echoes.
- [M32.5] ☑ **Persistence + share + docs** — D-M4 checklist, capture-harness screenshots (shipped
  post-review), docs + changelog. (PDF markers table and the external-CSV marker source deferred —
  live remnants.)

- [M32.9] ☑ **Marker series now appear in the legend** — glyph-aware rows drawn by the chart's OWN
  painter (`ChartPanel.paintGlyph`, made public/static so key and plot cannot drift), in the marker
  palette, labelled with the series' event count. Rows come from the LAST EXTRACTION rather than the
  specs, so a dangling pin shows `(0)` and its note as the tooltip instead of a phantom row. Right-click
  removes the marker spec. Count is TOTAL points, not drawn glyphs — drawn glyphs collapse with zoom
  under D-M3, and a legend number that changed on zoom while the data did not would be its own small
  lie; the ×N badges already carry density on the plot. `MarkerLegendTest` pins the pure parts; the
  overlay itself stays an eyeball item (rule 4). *Shipped after v1.5.0.*
  _(Original finding, kept for the record:_ *Measured 2026-08-18 against v1.5.0, and visible in the shipped docs
  image `docs/site/assets/graph-markers-dark.png`: the chart draws orange ▲ and pink ✕ glyphs and the
  legend lists only `quotePublisher.spread`, so nothing on screen says what either glyph means.*
  `GraphPanel.rebuildLegendLabels()` adds a row for each of `activeKeys`, `activeExprs` and
  `externalSpecs` — markers are never offered to it. The spec says legend rows show **glyph + label
  with a count** (spec §C), and that half was not built.
  Two things it needs beyond a fourth loop: `legendRow(String, int)` paints a square swatch from
  `ChartPanel.paletteColor(idx)`, so it needs a **glyph-aware** variant drawing the marker's own shape
  from the **marker palette** (separate since the R7 fix); and the count is per the D-M3 aggregation,
  not `points().size()`, if it is to agree with what the eye sees.
  *Consequence while open:* the bands docs image only reads correctly because its caption does the
  legend's job in prose — which is the tell that the legend is doing none.)_
- [M32.10] ☑ **Docs images regenerated against the legend fix** — all 14 shots retaken natively
  (`done — 14 captures, all native`, exit 0), so `graph-markers-dark.png` now shows the key it was
  missing: `▲ order live (166)` and `✕ risk breach (160)` beside `quotePublisher.spread`, in the
  marker palette, glyphs matching the plot. Every visible string re-read per rule 1. This is also the
  first run where the harness's success signal means anything — the pre-fix version reported ✓ for
  shots it never took.

## M32 · Marker series — ☑ COMPLETE 2026-08-20 (core v1.5.0/v1.6.0; rug, PDF table and external-CSV source merged with M33)
_32.1–.5 core shipped, reviewed and merged (incl. post-review D12 right-axis scale + the dedicated
marker palette) — full record in **[completed/tracker.md](tracker.md)**.
Design: **[completed/spec-marker-series.md](spec-marker-series.md)**._
- [M32.6] ☑ **Flags rug** — flagged records as a built-in axis-lane rug
  (D-M5's second half): MarkerExtractor.flagRug is pure (tested — filter honoured, finding note as
  payload, click→record, no flags → no rug), the MainFrame seam supplies row→note and every flag
  mutation site refreshes the rug from the CACHED extraction (flags are not data; no re-extract).
  One marker seam (GraphPanel.pushMarkers) feeds chart and legend, so the rug's legend row appears
  exactly when its ticks do, with the unflag-to-remove tooltip.
- [M32.7] ☑ **PDF markers table** — a report's chart section now carries
  its markers as DATA under the picture: label · glyph · time · payload · record, rendered through
  M33.2's table (no second layout), capped at 200 rows with the cap NAMED (D-M3 in table form). The
  rug rides too, since it is a marker series. The M23 single-record sugar keeps its frozen shape —
  its charts still paint glyphs in the image; the table is the investigation form's.
- [M32.8] ☑ **External-CSV marker source** — ExternalCsvLoader.loadMarkers
  (same declared-clock/sort/refuse rules; payload column as display cargo; value column optional →
  axis lane; recordIndex always -1 — an external row is not a record and click-through is refused by
  contract). MarkerSpec gains the ext* definition fields (persisted + shared portable, path resolved
  like external series); the verb takes markers[{external:{…}}] behind the SAME read confinement as
  graph{external}; the D-F2 stamp covers marker sources; loads are cached per definition AND
  mtime-checked (review R5 — a file changed under a cached read must not show evidence that no
  longer exists; a filter
  change cannot change what a CSV contains). The y-pin (`series:<label>`) is NOT supported for
  external markers in v1 — value column or axis lane; recorded here rather than half-built.

## M33 · Investigation reports — ◧ shipped portion: M33.1–.4, 2026-08-20 (merged; reviewed twice + owner-eyeballed; M33.5 gated, stays live) (the account, not just the evidence)
_Design: **[spec-investigation-reports.md](spec-investigation-reports.md)**. Owner ask: a general
reporting mechanism — an explanation for a set of results, or an investigation, rendered and kept in
memory or written to disk. Every surface here produces evidence; nothing produces the ACCOUNT of it,
and a real investigation is never one record. The principle is the whole design: **a report is an
ordered list of REFERENCES with connective prose, never a free-form document** — which is what keeps
it a forensic instrument rather than a word processor with a database attached. Sections are typed
(finding · record · chart · topology · series · table · narrative) and only `narrative` stores its own
content. Seven decisions posed for review: a report **includes** findings and never authors them, so
`flag` stays the one write site (D-I1); narrative renders **visibly** as narrative, M29 D-F2 applied one
level up (D-I2); evidence persists as its REFERENCE and re-renders live, so a report is a re-runnable
claim that degrades out loud (D-I3); a **table is a query plus a column spec** — rows derived,
presentation declared, CSV per section, and an agent-authored table is narrative not evidence (D-I7);
table **formatting is a declared rule that is shown** — a highlighted row prints the `Expr` condition
that selected it, reusing M28 rather than inventing a rule language, because an unexplained red row
is a judgement wearing evidence styling; per-cell painting rejected as D-M1's rendering DSL one
artefact later (D-I8);
own share category rather than a sixth passenger on Graphs (D-I4); writes ride ExportGuard unchanged
(D-I5); and **M12.1's fix-brief becomes a report with a fixed section list** rather than a second
document model that will drift (D-I6).
**Review amendments folded (v2):** the dangerous failure ARRIVES RESOLVED — `recordIndex 42`
resolves against any log with 43 records — so D-I3 alone could not enforce the spec's own
principle. **D-I3a** captures the authoring context (log fingerprint + `FilterState`) and applies
one rule: compare, announce, offer. Same log moved on = re-verification; different log =
misapplication, and the page says which. `rowWhen` evaluates STRICTLY against its own row, no
carry — a highlight a reader cannot verify from the visible row is a colour, not a rule._
- [M33.1] ☑ **Model + reference resolution** (headless) — ReportSpec with
  D-I1 STRUCTURAL (a finding section has no text field; supplied text is dropped and the verb names
  the rule), LogFingerprint + FilterSnapshot (D-I3a as data, announce lines composed once),
  ReportResolver with the fingerprint verdict positioned before the sections. 15 tests before any
  rendering.
- [M33.2] ☑ **Rendering** *( deviation: ReportRenderer is a SIBLING of
  FindingReport — the shipped sugar keeps its exact shape, both share PdfDoc)* — narrative standing
  label, announce-first banners, unresolved sections render their reason in place, D-I7/D-I8 table
  (declared widths/formats/emphasis, monospace tabular figures, printed highlight rule, header
  repeated across page breaks). `PdfDoc` unchanged.
- [M33.3] ☑ **Verb + echo** — `report {name, sections}` REPLACES by name;
  sugar untouched; M26.4 echo (skipped sections NAMED, D-I1 called out, unresolved + rowWhen
  warnings, writtenAgainst in the echo); rowWhen STRICT per row pinned where LOCF would differ;
  table rows via `read {fields}` (25-record cap note rides); CSV through RecordExporter (raw
  values). Gaps stated, not hidden: aggregate/coverage/series table sources + topology/series PDF
  images say so in place.
- [M33.4] ☑ **Persistence + share + Reports panel** — the F1 checklist
  asserted by test: ConfigStore round-trip (incl. null-dims = ALL), project snapshot/restore/clear,
  SettingsShare ride-along under the OWN category (D-I4 — export without the category writes no
  report keys; the import summary counts narrative), replace-by-name apply, disclosure row in
  sharing-setups.md same commit (contract-test enforced). Reports tab: sections clickable through
  to record/graph/focus, fingerprint banner first, the filter offer is a BUTTON (offer-never-act).
  **Post-review, owner-driven:** Q1 decided — same content under a different name announces SOFTLY
  ("SAME CONTENT — A DIFFERENT FILE"), the strong banner reserved for content differences; the
  Reports tab gained **Export PDF…** (human parity with `report {path}`, chooser-as-consent); and
  the owner's live eyeball pass surfaced two silent failures, both made loud — the chart's
  empty-state now distinguishes "nothing configured" from "configured but filtered to nothing"
  (a SHIPPED surface, fixed under Fixed in the changelog), and a report's open-record click on a
  filtered-out record now OFFERS the goto-reveal relaxation instead of doing nothing.
- [M33.5] ☐ **Fold M12.1's fix-brief onto the model** (D-I6) — after the closed-loop precondition
  (journal ↔ audit-log pairing) resolves, not before.

## M37 · Project panel — what is in force, stated in one place — ☑ **SHIPPED 2026-08-27** (.1–.6; review `docs/handoff/completed/review_feat_m37_loaded_panel.txt` MERGE, F1 fixed by the reviewer, F2 taken)
_Design: **[spec-loaded-panel.md](spec-loaded-panel.md)**. The owner's ask: a tab on the west rail that
shows the loaded graphml(s), the event processors (Java classes), the audit logs, and the project's name
and file location — "currently it is not clear what is loaded in the current project"._
_The framing that shaped it: the app already knows all of this and says it to **agents** (`context`), while
the human gets five scattered fragments (title, status line, Topology header, two dialogs). So the panel
is `context` rendered for the human — one model, two readers — and it goes **before M20.5**, whose
project pointers are invisible without it. The 2026-08-26 graph-loss defect is the motivating case:
saved graphs fell 6 → 1 and no surface showed the count._

- Review (`docs/handoff/completed/review_spec_loaded_panel.txt`, 2026-08-27): ACCEPT. Four corrections fold into
  **M37.1**, not the panel slices — C1 the mock's project path is wrong (`.analyser/project.fluxtion-settings`,
  `ProjectProfile.CANONICAL_RELATIVE`, not `.fluxtion-analyser/project.properties`); C2 an S3 log's
  `localFile()` is a TEMP copy, so the row must show the origin the user named; C3 "default shown once"
  already exists as `config.lastRunVersion`, which `maybeShowWhatsNew` skips writing on a dev build;
  C4 support screenshot this app and the panel is the most path-dense surface in it, so abbreviate and
  put full paths behind *Copy path*. Confirmed NOT a problem: rebuilding `context` per lifecycle event
  is cheap (it loops only over selected and flagged rows).
- [M37.1] ☑ **`context` parity** — add the facts the panel needs that `context` lacks (project file
  location, source-root tiers, rolled-set members, per-processor source resolution); parity test scaffold.
- [M37.2] ☑ **The panel** — `NavRail` toggle "Loaded" beside *Event types*, persisted, default shown once;
  five sections (Project · Audit log · Graph · Processors · Source roots), every empty state a sentence.
- [M37.3] ☑ **Provenance + reveal actions** — per-row where-from (`OpenRequest` provenance, the tiers);
  actions are copy-path / show-in-folder / go-to only — a test proves nothing on the panel mutates.
  The pairing verdict is a row (applies · declared/inferred · opened beats supplied).
- [M37.4] ☑ **Lifecycle wiring** — re-renders on the M35 events, no polling; the open→graph→project→close
  sequence test.
- [M37.5] ☑ **Docs page + generated shot**; CHANGELOG; spec → SHIPPED.
- [M37.6] ☑ **Reports section** _(owner-requested 2026-08-27, after seeing the panel)_ — where files leave
  (the assistant exchange directory, machine-tier, or "File exchange off" with where to turn it on — the
  state that refused `screenshot` twice that day with nothing on screen saying so) and the project's saved
  reports by title. `context` gains `exports` and `reports`; M38.5's published destination is a further row
  on this section (D-C6).

**Owner calls, decided 2026-08-26/27:** name **Project** (was "Loaded" until seen live); **stacked** with Event
types; the start page does **not** show the PROJECT section inline. Review F2 taken: sentences wrap, paths
elide, default column 340px. Brief `docs/handoff/completed/handoff_26_aug_2026_1.txt`, report
`docs/handoff/completed/report_feat_m37_loaded_panel.txt`.

## M38 · Portable context — the project as a shared workspace — ☑ **SHIPPED 2026-08-27** — .1–.7 merged to main; .1–.6 reviewed end to end (`docs/handoff/completed/review_feat_m38_closing.txt`), .7 merged with its tests, review welcome (owner-requested; report `docs/handoff/completed/report_feat_m38_portable_context.txt`)
_Design: **[spec-portable-context.md](spec-portable-context.md)**. Owner's framing: portable context for a
human and an AI to work in a shared space — code, metadata, artifacts, logs, display, analysis. **Depends
on M37**: without the Loaded panel these facts are readable by agents and invisible to humans, which is
the asymmetry M37 exists to end._
- **D-C1 is the load-bearing decision — tiers by whether the stored thing EXECUTES.** Facts (vocabulary,
  environments, pointers, baselines) and analyses (sequences of ANALYSER verbs) travel in a shared
  profile; runbooks never travel as payload. Tier 2 is safe only because **server verbs never appear on
  the analyser's action socket** — relaxing that standing decision would make every shared profile
  executable, and the spec says so out loud.
- [M38.1] ☑ **Tier model + runbook POINTERS** _(2026-08-27)_ — the profile records `ops/deploy.md`, never the commands:
  execution stays with the agent / UP-MNG-02, the trust boundary becomes "you cloned this repo" rather
  than "you opened a file someone sent you". Write-time validation, import refusal of contents, a
  Loaded-panel row. Security first, before anything wants to bend it.
- [M38.2] ☑ **Vocabulary** _(2026-08-27: `vocabulary=` pointer in the profile; `context.vocabulary` with the file's text; first block of the Explain prompt; `VOCABULARY` share category default-on; panel row)_ — what `live` means in THIS system. The cheapest large win in the milestone:
  an LLM on an unseen processor and a first-week support engineer need the identical thing, and neither
  has it. Inert, shareable, no execution.
- [M38.3] ☑ **Environments + the §E provenance each stamps** _(2026-08-27: `environment.N.*` + `environment.default`; matched by logDir then default, only when nobody declared; `context.provenanceSource`; `ENVIRONMENTS` share category default-on; panel rows; review F1: the report header qualifies a matched provenance)_ — correctness, not convenience: two
  environments on one build emit indistinguishable logs, and an answer right about UAT read as
  production has no symptom the analyser can detect. Pairs with UP-MNG-03 (server wins where both exist).
- [M38.4] ☑ **Repeatable analyses** _(2026-08-27: `analysis.N.*` in the profile; gate = analyser verbs only, no project switch; `context.analyses` is the offer; recall via `open {analysis, bind}` and File ▸ Run analysis; stops at first failure; `ANALYSES` share category default-on; panel section)_ — a named sequence of analyser verbs with its rationale and bound
  parameters; an offer, never automatic.
- [M38.5] ☑ **Report destinations** _(2026-08-27: `destination.N.*`; gate refuses credential shapes; `context.reportDestinations`; Reports-section rows; `DESTINATIONS` share category — default OFF after review F1: a webhook URL is a credential in path form, known hosts refused by name; the category table completed on the docs page)_ (a place, never a credential — the `LLM`-category precedent), share
  categories completed, docs + CHANGELOG.
- **Owner decisions, 2026-08-27:** vocabulary is a **pointed-at markdown file** (same rule as a runbook —
  one rule for pointed-at content, not two); environments **travel by default**, label naming the cargo;
  prior findings are **links only**; **baselines become M39** — "what does normal look like here" is the
  question support cannot answer about an unfamiliar system and deserves its own design.
- [M38.7] ☑ **Rewrite what you own, preserve what you do not understand (D-C10)** _(owner decision 2026-08-27; the
  mixed-version hazard found live in .5: an older analyser dropped a newer one's keys on save. `KnownKeys` registry;
  both writers carry over unknown key families and rewrite owned ones wholesale; loader unchanged — ignore, never reject)_
- [M38.6] ☑ **Path anchors (D-C9)** _(2026-08-27: `workspaceRoot` anchor, validated; roots/repos under it written `../…`; `context.source.rootTiers[].form` + the Project panel's stored-form badge with a WARN for absolute/~ under a project; pointers unchanged)_ _(owner question 2026-08-27: "relative or absolute?")_ — three forms
  already exist and are chosen automatically (project-relative → `~` → absolute). Keep the rule, add the
  missing **anchor**: an optional project-declared `workspaceRoot` so a SIBLING checkout
  (`../shared-lib`) can be expressed — today it is written `~/…`, portable for you and silently wrong
  for a colleague. Pointers (runbook, vocabulary) stay project-relative with no `..`; source roots may
  use the wider set. The Project panel shows the stored FORM per row, so "this profile is not portable"
  is visible before it is shared rather than after it fails.

## M36 · Start page — ☑ **.1–.5 SHIPPED** (.1–.4 2026-08-25, .5 docs page 2026-08-25; archived 2026-08-27 — the rule-1 upstream ask stays live)
_Design: **[spec-start-page.md](spec-start-page.md)**. The owner's four sections — what it does, how
it helps, where it fits in the cycle, who you are — placed where they cost a returning user nothing._
_The framing that shaped it: **the analyser already HAS a start page**, and it reads "No log loaded —
File ▸ Open, drag a file in, or File ▸ Open from S3". Honest and useless. Meanwhile "what is this
for" lives in HelpPanel, a static page nobody opens before they have a problem. So this is not a new
surface; it is the empty state finally earning its keep, and the test of the design is that opening
a log from the command line means never seeing it._
- [M36.1] ☑ **The state, not a screen** (D-S1) — occupies the main area whenever no log is open;
  a log replaces it, closing one brings it back, `Help ▸ Start page` recalls it. No splash, no modal,
  no dismissal to remember.
- [M36.2] ☑ **Every section ends in an ACTION** (D-S2) — each of the four sections links into the
  BUNDLED DEMO LOG, so every button works with no configuration, no server and no API key. A start
  page whose buttons need setup first is one that lies on first contact.
- [M36.3] ☑ **Three audience lanes, phrased as the user's own sentence** (D-S3) — "I am writing the
  graph", "something is wrong in production", "I want the numbers out". **Never a question the app
  asks**: people recognise their situation faster than they classify themselves, and nothing is
  remembered or personalised.
- [M36.4] ☑ **No feature list** (D-S4) — a page that enumerates capabilities is stale the release
  after it is written, and it is the first thing a new user reads, so its errors are the ones they
  carry. Three problems and three lanes; anything version-specific belongs in the release notes.
- **O-S1 RESOLVED — bundle a demo SET, not a log.** `DemoAssets` ships the walkthrough log, a traced
  log, a series log and the GraphML in the jar and unpacks them to `~/.fluxtion-analyser/demo`. One
  log was the wrong answer: three of the four sections ask a question one log cannot answer (coverage
  needs a TRACED run; a chart needs a series; step-through needs the graph).
- **O-S2 RESOLVED — the whole LEFT COLUMN, tabs kept.** "Records pane only" was tried first and
  failed its own acceptance: the page was clipped and a scrollbar appeared, because the detail pane
  below it has nothing to say with no log and was still holding half the height. The right-hand tabs
  stay, so the product's structure is still visible.
- **O-S3 HELD, and it bit.** `DemoAssetsTest` asserts the shipped demo carries no real names — and
  caught a vendor-domain copyright header in generated source on its way into the jar. That term is
  now the FOURTH in rule 1's sweep, and the four `examples/` files carrying it are clean.
- Closes **review_feat_m35_project N2** ("the first-run modal blocks an agent-driven start
  entirely"), together with M19.7. M19.7 suppressed the modal for `--rest`; M36 removed it outright,
  because the same three objections apply to the human at a fresh install. The `--rest` stdout note
  M19.7 added stays — see `MainFrame.showFirstRunSettingsIfNeeded`.
- [M36.5] ☑ **The start page is documented, with a generated screenshot** _(2026-08-25)_ —
  `getting-started.md` is rebuilt around it: the Quick start is now "run it, click Open the demo log",
  and the sentence promising that a first run opens Settings is gone (it had been false since M36).
  `capture-docs.py` takes the shot by CLOSING the log — the page is a state, so the only honest way to
  photograph it is to be in that state. Doing so exposed that `launch()` opens the log from the command
  line while `seed()` does not, so the first run of the new step photographed an empty analyser for five
  light-theme shots; the harness now reopens the log and waits for it.

### Rule 1 — owner decisions, resolved (raised M36, sharpened by the polish round; the open upstream ask is in the live tracker)
- ☑ **The sweep is extended to four terms, and its exemption is written down** _(2026-08-25)_ — run
  literally the sweep can never be empty: `CLAUDE.md` states the rule and `ONBOARDING.md` restates it,
  so both must spell the terms. Two sessions had been reporting "sweep clean" under different unwritten
  exemptions. Rule 1 now says the exemption out loud and gives the form that needs no remembering —
  `git ls-files | xargs grep -ril …` minus those two paths — which must print nothing. The mechanical
  cost is real and is stated with it: a swept term may not be spelled anywhere else, **including in
  prose about the rule**, so five documents were reworded to describe the fourth term rather than
  print it, and `DemoAssetsTest` now parses all four from the sweep line at runtime instead of
  concatenating one locally.
- ☑ **The four `examples/fixture-generator` files are clean** _(2026-08-25)_ — the compiler-emitted
  block carried a personal address on a vendor domain inside an "all rights reserved / confidential,
  delete this file" notice, on files published in a public repo, where the notice was both a leak and
  untrue. Header removed from all four. This also closes the live hazard the polish round exposed:
  `tools/capture-docs.py` adds that directory as a source root and `source-navigation.png` renders one
  of those files, so the docs screenshots were one scroll position away from publishing it.

## M40 · Audit readiness — will this processor log at all? — ☑ **COMPLETE 2026-08-27** (.1/.2a/.2b/.3; post-merge review `docs/handoff/completed/review_main_m40_2b_3.txt` GOOD, F1 fixed; .2c optional, stays live)
_Brief `docs/handoff/completed/handoff_27_aug_2026_1.txt`, report `docs/handoff/completed/report_feat_audit_readiness.txt`.
The owner's redirect: the authoring side belongs to the LLM writing the processor; the ANALYSER side can
diagnose the graph. Every other producer check needs a log to examine, and the worst case produces no log._
- [M40.1] ☑ **The verdict, from the graph** — `AuditReadiness.of(topology)` → ENABLED / NOT_ENABLED /
  UNKNOWN. The compiler installs `EventLogManager` as a node when `addEventAudit()` was called; absent,
  the processor writes nothing. **Measured** (rule 6): same program, one call removed → 613 bytes became
  0. UNKNOWN with no graph — with no evidence the answer is "unknown", never "probably fine". Surfaced
  in `context.topology.auditLogging`, verified live with a graph and NO log open. 7 tests, the real demo
  fixture as the positive control.
- [M40.2a] ☑ **The denominator counts only what could log** _(2026-08-27)_ — measured first: the shipped
  demo reported `declared 10 · covered 5 · ratio 0.5`, and all five "uncovered" were things that can
  never write audit output (three event classes, an exported service, and the deliberately-silent
  `spreadCalculator`). `CoverageScope` excludes by KIND — from the graph alone, no source needed — and
  every exclusion is reported with its reason. Demo now `declared 6 · covered 5 · ratio 0.833`, the
  remainder being the one real case. An UNKNOWN kind is KEPT: dropping what we cannot classify would
  flatter the score, which is this defect pointing the other way.
- [M40.2b] ☑ **Which NODES can log** _(2026-08-27)_ — `NodeLogging` over the node's own source.
  **The premise recorded here was WRONG and rule 6 caught it**: "does not extend `EventLogNode`" would
  have excluded `RiskMonitor`, which extends `SingleNamedNode` and logs on line 108 of the demo — a
  false exclusion, the direction that flatters the score. Read from the runtime jar: the contract is the
  `EventLogSource` interface, `EventLogNode` is a convenience base, and nine further framework classes
  reach it transitively (that list is measured, and a test asserts each is recognised). Exclusion needs
  PROOF — source in hand and no supertype at all; missing source or an unrecognised supertype stays
  counted. Demo 0.833 → 1.000 with `spreadCalculator` named as **unobservable**, not as fine.
- [M40.2c] ☐ _(live — see tracker.md ▸ M40)_ **Follow the supertype chain** — a node extending a project-local base that itself extends
  `EventLogNode` currently lands in UNKNOWN and stays counted. Correct but conservative; resolving one
  more hop needs the file's imports (`EventProcessorModel.resolveSimpleType`).
- [M40.3] ☑ **The audit LEVEL** _(2026-08-27)_ — **the gate was answered NO, and the slice moved.** The
  graph does not distinguish INFO from TRACE: the compiler's GraphML carries id, class and style per node
  and no level string at all. And a build-time `addEventAudit(LogLevel.INFO)` would be the wrong fact
  anyway, because `DataFlow.setAuditLogLevel` resets it at runtime — the M40.1 harness does exactly that.
  So the level is read from the artefact in hand: every record header carries it. `AuditLevel` states the
  levels present and names what they would have discarded, surfaced on `coverage` only when there IS a
  gap the level could explain. Two facts, no verdict — the log genuinely cannot tell "the threshold
  excluded them" from "nothing called `debug()`", so it does not pretend to.

## M42 · Connect an AI client — ☑ COMPLETE 2026-08-28
_Design: **[spec-mcp-client-install.md](spec-mcp-client-install.md)**. Acceptance record:
`docs/handoff/completed/report_m42_client_install.txt`; final review:
`docs/handoff/completed/review_m42_milestone.txt`._

M41 installed the application; M42 is the separate, client-specific last mile: a Start-page/Assistant
setup flow registers the existing `--mcp` bridge with **Codex** and **Claude Code**, and supplies a
generic MCP record (including the Claude Desktop fallback). It does not add another protocol server,
start a duplicate GUI, copy a per-run token, or edit unknown foreign configuration files silently. The
analyser proves its own side with a loopback invocation of the exact bridge command and read-only
`analyser_context`; it refuses `OTHER_INSTANCE` when another analyser owns the last-writer-wins endpoint,
and a green check never pretends it has observed a foreign client or model.

- [M42.1] ☑ **Launch command + loopback probe** — resolved absolute launcher, argument-vector process handling,
  redaction, `OTHER_INSTANCE` protection, and a real bridge → REST → `context` test under isolated home. The M19
  isolated-home bench launches the packaged `--mcp` child and proves modern discovery, `analyser_context`
  discovery and its read-only call back into that exact analyser (2026-08-27). The direct-JAR fallback now
  refuses debug/instrumentation JVM options rather than enrolling a bridge that could suspend or bind a
  debugger port when a client launches it (final review F1, 2026-08-28).
- [M42.2] ☑ **Human surface + readiness** — non-modal Start-page card; persistent Assistant setup; explicit local
  transport enablement; distinct app/bridge/client state. The packaged-launch bridge check and its wrapped,
  copyable command field were manually reviewed in the running UI (2026-08-27).
- [M42.3] ☑ **Codex registration** — current CLI integration, confirmed add/replace/remove and a copy fallback.
  Codex discovered the `fluxtion-analyser` registration and its 14 tools (2026-08-27); the product owner's
  final read-only `analyser_context` acceptance is recorded in `report_m42_client_install.txt` (2026-08-28).
- [M42.4] ☑ **Claude Code registration** — current user-scoped CLI integration; project `.mcp.json` is deliberate,
  copy/diff-only, never a default side effect. Its shared confirmation disclosure uses a readable desktop-width
  command field (2026-08-27); the product owner's final acceptance is recorded in
  `report_m42_client_install.txt` (2026-08-28).
- [M42.5] ☑ **Claude Desktop route** — the live MCPB contract was verified at its first-party sources (named in
  `report_m42_client_install.txt`); the documented generic-config fallback is retained because the per-machine
  JBang/Java bridge has no portable bundled entry point (2026-08-27).
- [M42.6] ☑ **Generic configuration + docs** — exact argument-vector JSON can be copied or saved only to a
  user-chosen file (with overwrite confirmation); connection, Assistant, Start-page and FAQ guidance cover the
  in-app path, including the Working-with-AI connection and loop pages. Isolated native setup and confirmation
  captures are published and visually inspected; the install and MCP guides explicitly distinguish the
  `analyser` JBang executable, `fluxtion-analyser` client registration, and bridge protocol name. The final
  generic capture shows the complete naming disclosure (final review F4, 2026-08-28).
