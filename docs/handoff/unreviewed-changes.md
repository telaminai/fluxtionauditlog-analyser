# Un-reviewed changes on `main` — pending review

A running ledger of changes **committed directly to `main` without** the usual brief → report → review
cycle. These are small, ad-hoc fixes made by a session working primarily in **another repo** (a downstream
consumer of the analyser) that hit an analyser-side bug and fixed it in passing, rather than a delegated
work block.

**For the reviewing session:** on your next pull, review each `☐` entry below — read the commit, sanity
the change against the codebase and the repo rules (CLAUDE.md), run `mvn test`, and **verify anything the
entry says was not verified** (Swing UI changes are not unit-tested — build and run the jar). Then tick it
`☑ reviewed <date>` with a one-line verdict, and file any follow-up as a normal review. Fully-reviewed
entries move to `completed/` when this file is next tidied.

Every entry must carry: commit SHA, what & why, files, what was verified, and **what the reviewer must
still check**.

## ☐ 2026-09-16 · Response to the 1.13.1 review: B1, B2, F3, F4 fixed; F5 corrected · based on `26c10d45`

Response: [handoff_analyser_1.13.1_response_2026-09-16.md](handoff_analyser_1.13.1_response_2026-09-16.md).
Review: [review_analyser_1.13.1_2026-09-16.md](review_analyser_1.13.1_2026-09-16.md).

**What & why.** B1 — the verdict retires when a load starts (`setBusy(true)`) and when a graph is opened during
one; `context.graphPairing` says `pending` + `loading` while `loadInFlight`; a failed load restores the session's
still-true verdict (`setBusy(false)`). B2 — `ReadService.traceOnly` decides per instance over every contribution:
`tracedOnly` needs a wire marker and no entry anywhere; `traceLikeOnly` (legacy only) needs a `method` entry and
nothing but `thread`/`method`; schema updated. F3 — `repairLoadedGraph` builds the session driver on the first log
arrival instead of returning a null verdict. F4 — `SourcePanel.rerenderIfChanged` always re-renders a pane showing
a miss, so the placeholder names the roots now searched; `processorPaneText()`/`nodePaneText()` for tests. F5 —
handoff §4 rewritten to the client-side boundary; the compiler spec on branch
`docs/diagnostics-entry-point-rendering` already sits there.

**Verified.** `PairingDuringLoadFrameTest` — the reviewer's real-frame two-call sequence on a fresh window with no
project: echo pending, context pending with no `applies`, final B/B verdict. **Skipped under Maven** (the pom
forces headless for every run) and run with `-Djava.awt.headless=false -DargLine=…` on a machine with a display.
Mutation controls, all red: B1 (context reports the old verdict, no invalidation), F3 (no lazy driver → no verdict
within 20 s), F4 (content-only re-render → node placeholder names the old root). `ReadServiceTest` carries both B2
counterexamples in the legacy grammar and the marker+value case under the declared grammar, with a positive bare-marker
control and a business-`method` control. Full `mvn -o clean verify`, strict docs, rule-1 sweep before commit.

**Reviewer must still check.** The frame test cannot run on CI; it is a developer-machine test and its skip is
silent there. Whether `traceLikeOnly` should require `thread` as well as `method` (it requires `method` only,
matching `AuditTrace`). The skill wordings from the review's other dispositions — "every final field" narrowed to
eligible instance fields; the Mongoose capture roll is a roll, not a one-day retention; fresh capture location over
deletion — are NOT changed here: each is a canonical-skill edit plus an index re-pin, an owner's call.

## ☑ reviewed 2026-09-16 · Session-report items: pending pairing echo, `tracedOnly`, three skills · based on `c443c865`

**Verdict: NOT READY for 1.13.1.** Independent [review](review_analyser_1.13.1_2026-09-16.md): B1 stale
pairing in context and B2 false `tracedOnly` assertions block; F3 records the pre-existing fresh-session
gap and F5 corrects the compiler attribution. The author account below is retained as reviewed history,
not as acceptance of those claims.

**Source.** An agent's session report on the 1.13.0 template bundle (off-repo, the owner's copy). Its analyser
items were triaged: one defect, two gaps, one policy kept (screenshot never overwrites — `ExportGuard` is
deliberate), and the compiler item is already in 1.0.67 (FLX-1009) but the remote path throws the LEGACY
message (`DiagnosticException(diagnostic, legacyMessage)`; the http client rethrows `resp.getError()`), so an
agent never sees it — a compiler/generator-http item, recorded in the report to the owner, not fixed here.

**What & why.** (1) `open {log, graphml}` echoed a pairing judged against the PREVIOUS log or none, because
`openLog` hands the load to `Background.run` and returns; `openGraphml` judged synchronously. Now the log echo
says `loading: true`; `ActionExecutor.doOpen` replaces the verdict keys with `PAIRING_PENDING` when the log
opened in the same call is loading; `MainFrame.openGraphml` echoes pending itself while `loadInFlight`, so a
graph opened in the NEXT call is covered too. `onLoaded → repairLoadedGraph` already re-judges. (2) `read …
fields` adds `tracedOnly` (`ReadService.traceOnlyNodes`: legacy text grammar with only `thread`/`method`
keys, or a bare binary trace marker); the verb schema says so. (3) Skills: `spring/add-a-node` (description
broadened to body-only logging; constructor-mapping rule before regenerate; a heading for the logging
section), `mongoose/run-mongoose-server` (cumulative export; confirm a clean stop; edit input only between
stop and start), `common/load-audit-log` (the echo cannot judge; context is the authority).

**Index and test.** `m19-skills/2/index.json` is re-pinned IN THE FOLLOWING COMMIT (its `revision` must name
the commit containing the bytes, so it cannot be the same commit); the commit with the bytes is red on exactly
`aPUBLISHEDindexMustPinARevisionContainingEverySelectedByte` and green again one commit later — pushed
together, CI sees the tip. `publishedM19IndexPinsTheAcceptedMongooseSubsetAndItsExactBytes` now hashes the
bytes AT v1's revision (`git show`) instead of the worktree: it was only ever true because neither v1 file had
changed since v1, and v1 stays byte-pinned exactly as the README says. Bundles keep `canonical@48b0e0a7` until
the playground vendors the new revision.

**Verified.** `OpenEchoPendingPairingTest` (rewrite when the adapter says loading; control: a synchronous
adapter keeps its verdict; a graph opened alone is never rewritten), `ReadServiceTest.tracedOnlyNames…` (with a
control record where the same node logs a value), `CanonicalSkillsTest` string pins intact. Full
`mvn -o clean verify`, strict docs, rule-1 sweep before each commit.

**Reviewer must still check.** The live two-call race on a real frame (`open {log}` then `open {graphml}`
within the load): the second echo must say pending and the status bar must show the new log's pairing after
it lands. Whether `tracedOnly` should also appear in the raw-text (`fields` omitted) shape — left out because
the raw text already shows `thread`/`method`.

## ☑ reviewed 2026-09-16 · Source panel: stale "No source to show" after a project switch · based on `7960462d` (v1.13.0)

**Verdict: original processor switch and live project hint accepted; node-pane follow-up F4 remains.**
Independent [review](review_analyser_1.13.1_2026-09-16.md) reproduced the missing→missing node placeholder
retaining old roots. Source re-read overhead on large/remote files was not measured.

**What & why.** Reported on the deployed 1.13.0: the processor pane said *No source to show … Source root
searched: &lt;an older checkout's root&gt;* while the nodes pane resolved and `context` already reported the new
project's root and `processors[0].source=found`. Cause: `SourcePanel.navigate` skips an unchanged class name, so
after a project switch that selects the SAME processor the pane was never re-read; and the placeholder listed
roots without saying which project they came from, while the offer to load the log's own project had gone by as
a status-line note (M35.7: socket-driven opens never show the dialog). Fix: `showSelectedProcessor()` re-reads
either pane whose file content changed with the roots; `navigate` retries a miss on every navigation (history is
still pushed only for a new name); the placeholder text is a pure static (`nothingToShowText`) that appends a
frame-supplied hint — the active project and root, and any pending project offer with both remedies.

**Files.** `ui/SourcePanel.java`, `ui/MainFrame.java` (`sourceLookupHint()`), `CHANGELOG.md`,
`src/test/java/…/ui/SourcePanelRootChangeTest.java`.

**Verified.** Four new tests (headless Swing construction, as `DetailPanelExactClickTest` does): same name +
new roots → re-read; new roots without the file → stale source dropped; placeholder ordering roots → origin →
remedy; blank hint adds nothing. Mutation control: with the re-read and the miss-retry removed the two switch
tests fail (2/4 red), so they pin the behaviour. `mvn -o clean verify`, strict docs build and the rule-1 sweep
run before commit (see the commit).

**Reviewer must still check.** The live surface: open a log over the socket while another project is active,
then load the log's project — the processor pane must show the file without a click, and before loading, the
placeholder must name the pending project with `File ▸ Open project…` and `open {project: …}`. Also whether
re-reading the node pane on every `onConfigChanged()` (a file read per pane) is noticeable on large files.

## ☑ 2026-09-15 · round-11 response, based on `06d7cb28` · INDEPENDENTLY REVIEWED 2026-09-15, accepted with one correction

Response: [handoff_analyser_round11_response_2026-09-15.txt](handoff_analyser_round11_response_2026-09-15.txt).
Independent review: `review_analyser_round11_response_2026-09-15.txt` (core-baseline `design/handoff/`, off-repo):
R11-1, R11-2 and R11-3 accepted as closed CLASSES (118 key-character clicks per arm across six arms, span
integrity over every offset, mutation controls for each new test). One correction required and folded into the
commit: **F1** — the shared formatter quoted legacy text values (`C:\temp\x` rendered `"C:\\temp\\x"` in the
logical view); values are now quoted only when the parsed entry says the reader quoted them, pinned by a legacy
red-then-green test. F2 (three vacuous assertions) reworded. The broader review's B1 (guided-start fallback said
"never ran"; it now says "never logged" and why) and B3 (the CHANGELOG folded to one Added/Changed/Fixed, stale
fixture count and builder version corrected) are in the same commit. B2 (`verify-m43.py` tautology) is deferred
to after the release. Not checked by either review: a rendered PDF, a native popup, the fresh-model guided tour.

The tokenizer now emits complete key positions with its entries; the detail panel consumes those
positions rather than inferring identity from a displayed word. Logical view, step status and both
report-evidence paths share the same name/value formatter. The six-node sparse M61 workload label
and format-spec wording are corrected. The package-generated reduced POM now matches released pins.

Author checks: three regressions failed before production edits; clean verify **1,385/1,385**;
docs strict build; tool smoke **24/24**; packaged session acceptance **22/22**; packaged regression
probes **4/4**; live template download/open **6/6**. Parser semantic parity held over **18 fixtures,
1,584 record/grammar pairs**, comparing the pre-fix parser with the packaged candidate.

Reviewer must attack the new source-position mapping (CRLF, skipped comments, continuations,
repeated nodes/records, escaped keys), the conservative multiline fallback, and the shared formatter
through actual UI/report consumers. The broad review also leaves the fresh-model guided tour and
modal-dialog/theme checks unverified; a green script is not their sign-off.


---

---

---

## ☑ reviewed 2026-09-03 · `180a1e7`, `4c1c9d8` · review response + M48.11

**Review:** [`review_response_180a1e7_4c1c9d8.txt`](review_response_180a1e7_4c1c9d8.txt) — verified by
execution, not by reading: the reviewer's original probe binaries were replayed unchanged against the
fixed scorer and flipped to the correct verdicts; round-48 XML held byte-identical through all four
resolver fixes; M48.11 reproduced independently (PASS 12/12, exit 0).

**Verdict: accepted, with residue.** Follow-up review `review_analyser_response_4c1c9d8_followup.txt`
then found six further defects, all since fixed (G9/G10, the `--json` cycle path, bean-id collisions,
and the derived fixture whose provenance had become record metadata).

## ☑ reviewed 2026-09-03 · `6f45fe4^..4224196` + spec response · resolver, gates and builder spec

**Review:** [`review_spec_builder_component_resolution.txt`](review_spec_builder_component_resolution.txt)
checked the production spec against the target builder module and accepted its architecture with two
Java-8/CheerpJ blockers and three contract corrections. **Response:**
[`review_response_spec_builder_component_resolution.txt`](review_response_spec_builder_component_resolution.txt)
made Java 8 mandatory, separated the desktop Java parser from the browser surface, pinned SpEL and
Stitch byte semantics, and additionally corrected remote transport to the frozen-DTO/side-band rule.

**What & why.** Closing the residue from four review rounds. Three parts:

- **Resolver restructured, not patched.** A review found a cyclic 2-component candidate beating a
  valid 3-component one on minimality — the valid answer was then discarded downstream as
  UNSATISFIABLE. **Constructibility is now a validity constraint inside `solve()`**, and `solve()`
  returns a typed `Resolution` carrying selection, emission order and id allocation. Renderers consume
  it; none re-validates. The same review found `com.alpha.Node` and `com.beta.Node` in one jar both
  emitting `bundleNode` — ids are now allocated once from full class identity and escalate only as far
  as needed, so single-entry-point catalogues keep their committed output.
- **New gates.** `tools/test_tools.py` (24 checks) **wired into CI**; `TrailingWhitespaceTest`
  hardened — `.txt` scanned, audit logs detected structurally rather than by any mention of
  `eventLogRecord:`, a failure if `git ls-files` returns nothing, and every byte-sensitive fixture
  pinned with a missing file treated as failure rather than a skip.
- **Documentation** reconciled: the fingerprint three-way distinction, orientation §6, volatile line
  counts removed. A new canonical production spec places catalogue generation, typed resolution and a
  dependency-free Spring document parser/writer in the existing `fluxtion-builder` jar. It keeps the
  starter as a downstream editor and makes `#{bean.field}` a typed exposed-field reference rather than
  an opaque scalar.

**Verified.** `mvn -q test` green · `python3 tools/test_tools.py` 24/24 · **round-48 XML byte-identical
after every resolver change** · sweep clean.

**Review residue carried as delivery gates, not present claims.** The prototype id-escalation ladder's
fourth level still produces ugly names and has no direct test; `Fluxtion-Consumes` is still unused in
solving; the Java/JavaScript conformance corpus, dependency-tree proof, CheerpJ smoke and Stitch
byte-parity migration are specified but unbuilt; M49's evidence package remains unbuilt.

_No further entries awaiting review; the entries below are historical._

---

_Reviewed entries are retired to [`completed/unreviewed-changes-2026-08.md`](completed/unreviewed-changes-2026-08.md)._
