# Handoff — analyser after 1.13.0, 2026-09-16

_A portable account of the work on `main` since the 1.13.0 release, the triage of an agent's session report on
the template bundle, and that agent's comments on the plotting model. Written to travel between machines: every
claim names a commit, a file or a test, and says whether it was verified or only reported. Nothing here needs
the machine it was written on. Reviewers start with [`REVIEWER-ORIENTATION.md`](REVIEWER-ORIENTATION.md);
implementers with [`../ONBOARDING.md`](../ONBOARDING.md)._

## 1. Where things stand

| Item | State |
|---|---|
| Analyser release | **1.13.0** tagged and stamped on `main` 2026-09-16 (stamp commit `7960462d`). Runtime pin 1.0.15, compiler pin 1.0.67, both released. |
| `main` since the stamp | three commits, all CI-green (build + loop-bench + docs deploy): `c443c865`, `a5f94e57`, `04d56bea` |
| `perf/w10-conformance-bench` | identical to `main` (fast-forwarded with it) |
| Unreleased | the `[Unreleased]` block in `CHANGELOG.md` — one Added, one Changed, two Fixed — is the 1.13.1 candidate |
| Ledger | `unreviewed-changes.md` holds a ☐ entry for each of the two fix commits, with what the reviewer must still check |

**To release 1.13.1:** run the manual `release` workflow on `main` with version `1.13.1`. It copies `[Unreleased]`
verbatim as the notes and dispatches the docs deploy itself.

## 2. Work since 1.13.0, by commit

### `c443c865` — Source panel: a stale "No source to show" survived a project switch

- **Reported on the deployed 1.13.0.** The processor pane said *No source to show … Source root searched: \<an
  older checkout's root\>* while the node pane resolved and `context` already reported the new project's root
  with `processors[0].source = found`.
- **Cause.** `SourcePanel.navigate` skips an unchanged class name, so a project switch that selects the SAME
  processor never re-read the pane. Second defect: the placeholder listed roots without saying which project
  they came from, and the offer to load the log's own project had gone by as a status-line note (M35.7: a
  socket-driven open never shows the dialog).
- **Fix.** `showSelectedProcessor()` re-reads either pane whose file content changed with the roots;
  `navigate` retries a miss on every navigation (history is pushed only for a new name). The placeholder text is
  a pure static (`SourcePanel.nothingToShowText`) that appends a frame-supplied hint (`MainFrame.sourceLookupHint`):
  the active project and its root, and any pending project offer with both remedies (*File ▸ Open project…*,
  `open {project: …}`).
- **Verified.** `SourcePanelRootChangeTest` (4 tests; headless Swing construction). Mutation control: with the
  re-read and the miss-retry removed, the two switch tests fail. Full verify, strict docs, rule-1 sweep.
- **Still to check on a live frame.** Open a log over the socket while another project is active, then load the
  log's project: the processor pane must show the file without a click; before loading, the placeholder must name
  the pending project with both remedies. Cost of re-reading the node pane on every `onConfigChanged()`.

### `a5f94e57` — pending pairing echo; `tracedOnly` in `read`; three skills revised

- **Open echo (defect, confirmed).** `openLog` hands the load to `Background.run` and returns; `openGraphml`
  judged synchronously, so `open {log, graphml}` echoed a verdict about the PREVIOUS log or none. Now the log echo
  says `loading: true`; `ActionExecutor.doOpen` replaces a same-call verdict with `ActionExecutor.PAIRING_PENDING`;
  `MainFrame.openGraphml` echoes pending itself while `loadInFlight` (covers a graph opened in the NEXT call);
  `onLoaded → repairLoadedGraph` already re-judges when the log lands. Test: `OpenEchoPendingPairingTest` (rewrite
  when the adapter says loading; control: a synchronous adapter keeps its verdict; a graph opened alone is never
  rewritten).
- **`read … fields` gap.** A record now carries `tracedOnly: [instanceId…]` (`ReadService.traceOnlyNodes`): legacy
  text grammar with only `thread`/`method` keys, or a bare binary trace marker. The verb schema says so. Test:
  `ReadServiceTest.tracedOnlyNamesTheNodesThatRanButLoggedNoValue` with a control record.
- **Skills** (`docs/skills`, canonical here; bundles vendor them at playground build time):
  `spring/add-a-node` — description broadened to body-only logging; the constructor-mapping rule (derived state
  `transient` or `@FluxtionIgnore`) stated before the regenerate step; a heading for the logging section.
  `mongoose/run-mongoose-server` — the export is cumulative across restarts; confirm a clean stop (the registry
  entry has been seen left behind); edit the input file only between a stop and a start.
  `common/load-audit-log` — the echo cannot judge the pairing; `context.graphPairing` is the authority.
- **Test change.** `CanonicalSkillsTest.publishedM19IndexPinsTheAcceptedMongooseSubsetAndItsExactBytes` now hashes
  the bytes AT v1's revision (`git show`) instead of the worktree. It was only ever true because neither v1 file
  had changed since v1; v1 stays byte-pinned exactly as `docs/skills/README.md` says.
- **Declined from the same report.** `screenshot` with a `replace` flag: `ExportGuard` refuses to overwrite by
  design because nobody is in the loop on that path. Iterations should carry distinct names.

### `04d56bea` — the `m19-skills/2` index re-pinned

`revision → a5f94e57`, three per-path `sha256` values updated, a `$comment` line recording why. Same contract,
same selection. **Why two commits:** the index must name the commit that contains the bytes, so the commit
carrying the bytes is red on exactly `aPUBLISHEDindexMustPinARevisionContainingEverySelectedByte` and green one
commit later; pushed together, CI tests the tip. Anyone revising a published skill repeats this dance. Bundles
generated from `canonical@48b0e0a7` keep their own bytes until the playground vendors the new revision.

## 3. The session report, triaged by owner

An agent drove the 1.13.0 template bundle end to end (runbooks → export → analyse → add logging → add a node and
an event → regenerate → redeploy → admin REST) and wrote a report. Its items, and where they landed:

| Item | Owner | Status |
|---|---|---|
| `open` echo pairing stale (two symptoms) | analyser | **fixed** `a5f94e57` |
| `read` cannot show a traced-only node | analyser | **fixed** `a5f94e57` |
| `add-a-node` excludes body-only logging; constructor rule missing | analyser (skills) | **fixed** `a5f94e57` |
| `run-mongoose-server`: unclean stop, input tailing, cumulative export | analyser (skills) | **fixed** `a5f94e57` |
| `load-audit-log`: echo lag | analyser (skills) | **fixed** `a5f94e57` |
| collapse `ExportFunctionAuditEvent` boot records by default | analyser | **open, product call** — the table already blanks the event column; a boot summary line in `context` is the cheaper answer |
| `screenshot` `replace: true` | analyser | **declined** (see above) |
| constructor-mismatch error should name the fix | compiler | **already in 1.0.67 as FLX-1009 — but unreachable on the remote path**, see §4 |
| triage table: exact symptom text | playground docs | open |
| bundle `CLAUDE.md`: logging pointer, cumulative export note, API entry points | playground template | open |
| `CsvToPriceEvent` short-line fallback prices bad input at zero | playground starter code | open — it produced the stray record the analyser caught |
| `/api` index or OpenAPI; intermittent registry entry left on stop; CSRF accepted in auth NONE; fresh-per-boot capture | Mongoose | open, not verifiable from here |

## 4. Compiler finding: the FLX-1009 diagnostic never reaches a plugin user — CORRECTED per review F5

_The first version of this section blamed the remote path. The independent review (F5) is right that the
constructor check runs **client-side**, while the DTO is built and before any request is sent, so the hosted
generator's version and error prose are irrelevant to this failure. The corrected account:_

Verified in the released sources (tag `v1.0.67`):

- `LiveGraphSourceGenExtractor` builds the FLX-1009 diagnostic (whose `suggestedFix` names `transient` /
  `@FluxtionIgnore`) and throws `new DiagnosticException(diagnostic, legacyMessage)` — the exception **message is
  the legacy text**; the diagnostic rides beside it as a `DiagnosticCarrier`.
- `EventProcessorGenerator` catches that refusal in the plugin's JVM, writes the sidecar only when
  `-Dfluxtion.diagnostics.sidecar` is set (opt-in), and rethrows — before the remote generator is involved.
- `fluxtion-maven-plugin` 1.3.0 logs the exception and wraps the cause in a `MojoExecutionException`; Maven
  prints the cause chain, which is the legacy prose. The plugin never calls `getDiagnostic()`; `logOnce()`'s
  slf4j line has no binding in the plugin's classloader.

So the agent saw the legacy wording whatever the hosted generator runs. **The remedy is at the client-side
boundary:** the spec `docs/specs/compiler-diagnostics/entry-point-rendering.md` on compiler branch
`docs/diagnostics-entry-point-rendering` makes the three static methods the plugin invokes reflectively render
the report, write the sidecar by default and throw a `DiagnosticBuildException` whose message is the rendering,
with the plugin, the in-process API and the "legacy prose stays" decision unchanged. The hosted generator's
error prose and a truthful `/health` version are filed beside it as separate asks.

## 5. The agent's comments on the plotting model

Received 2026-09-16 after the same session; reproduced here with a first-look triage. Only the pointers were
checked, not the behaviour — each item is a reviewer check, not a finding.

> **Where it is the right tool.** The log stays in the analyser and I get pointers — record indexes and byte
> offsets, not text; field projections cost tens of tokens per record. Metadata is verdicts, not raw facts: the
> pairing verdict, the audit-logging status, the dispatch-order guarantee and the runbook list are conclusions the
> analyser computed, and I acted on them directly. Rendering in the tool means the user and I look at the same
> thing; the screenshot verb closes the loop. Provenance is enforced: the graph carries a rationale, the log a
> declared origin, the flags note and fix.
>
> **Where it strained.** (a) Plots draw over the whole log while the table honours the filter — carry-in from
> the old server's records reached the chart despite a time filter; either the graph should respect the filter
> or the echo should say it does not. (b) Markers and formulas carry values by default — a marker condition over a
> per-tick key fired on thirty records, most of them not ticks; markers need a resolve option like series, or
> should default to same-record. (c) The formula language has no strings — quoted text parses as a duration, so a
> per-symbol series from one price key is impossible; I changed the processor to emit per-symbol keys, the right
> long-term shape but a heavy answer to a plotting question; string equality or a group-by key would remove that.
> (d) Open echoes lag state. (e) Screenshots refuse to overwrite. (f) Fifteen ticks in five milliseconds is
> unreadable on a time axis — a record-index x-axis option would suit replayed inputs.
>
> **Overall.** The analyser acts as a domain server rather than a data source: it owns parsing, pairing,
> aggregation, rendering and the human-facing UI, and exposes verbs that return conclusions and anchors. The
> friction points are all at the edges of that model, where a view and a query disagree about scope. They argue
> for making scope explicit in every echo.

First look, with pointers:

| # | Claim | First look |
|---|---|---|
| a | graph ignores the filter | `GraphPanel` listens to `FilterState` and reads `fromMillis`/`toMillis` (around line 757), so the time window is consulted for *something*; whether series extraction is windowed, and what the `graph` echo says about scope, needs a check against a filtered log. If the graph is deliberately whole-log, the echo should say so — the agent's own remedy. |
| b | markers carry by default | series have `resolve` STRICT (default) / LOCF; the formula object lists LOCF first (`VerbSchemas.exprObject`). Check what a marker `condition` resolves against and whether the schema states its default. |
| c | no strings in formulas | `graph/Expr.java` is the parser; confirm quoted text is read as a duration and whether a string-equality predicate or a group-by key is a bounded addition. This is the substantive ask. |
| d | open echo lags | **fixed** `a5f94e57` |
| e | screenshot overwrite | **declined**, `ExportGuard` policy |
| f | record-index x-axis | no such option exists (no hit for an index axis in `VerbSchemas` or `GraphTabs`); a real gap for replayed inputs |

## 6. Deferred, unchanged by this work

- `tools/verify-m43.py` tautological assertion (review B2).
- Core docs site did not redeploy after the 1.0.15 release (GITHUB_TOKEN merges do not trigger workflows) —
  trigger `docs.yml` on `main` manually; no GitHub release-notes object for v1.0.15.
- Compiler `fix/consolidated-open-work`: NOT READY; rebase and fix blockers for 1.0.68. C++ round items.
  Nanosecond timestamps next release. Rust target spec awaiting a decision.
- Analyser: golden-fixture follow-ups, M22 remnants, the un-started polish round (see `docs/specs/tracker.md`).

## 7. Rules that bit this week, for whoever picks this up

- **Check exit codes, not filtered output.** A CI fix (`9edab423`) shipped without compiling because a local check
  filtered the compile error; `7d7f1749` fixed it. Read surefire XML or the exit code.
- **Shallow clones break the revision tests.** `actions/checkout` needs `fetch-depth: 0` (in `ci.yml` and
  `release.yml`); `CanonicalSkillsTest.requireFullHistory()` guards it.
- **Revising a published skill is two commits** (§2, `04d56bea`).
- **Two writers in one checkout caused a mistaken edit once.** Work in one checkout per session.
- The rule-1 sweep, the `[Unreleased]` line, `mvn test` green, `mkdocs build --strict` and the personal commit
  email are unchanged and were run before every commit above.
