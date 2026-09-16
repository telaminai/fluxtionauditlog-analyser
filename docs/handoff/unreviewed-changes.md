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

## ☐ 2026-09-16 · Source panel `StackOverflowError` on a generated processor (JBang 1.13.0) · based on `96880f2b`

**What & why.** `JavaHighlighter.STRING` was `"(\\.|[^"\\])*"|'(\\.|[^'\\])*'` — a group with an alternation
under `*`, which `java.util.regex` matches by recursing per character (the report's stack: `Loop → GroupHead →
Branch → CharProperty → BranchConn → GroupTail`, repeated). The bundle's generated `MarketProcessor.java` holds ONE
apostrophe, in a javadoc, with 4,115 characters after it and no closing quote: the char-literal alternative scanned
all of them and overflowed the EDT stack. Reproduced on the real file through the highlighter (a probe, not a
test). Fix: `applyLiterals` scans string/char literals by hand, stops at a newline (a Java literal cannot cross one),
and colours nothing for an unterminated literal. The other regexes with a group under a repetition
(`SourceNavigation` modifier prefixes, dotted identifiers in `GraphPanel`/`TemplateClient`, `PathForm.ANCHOR`)
iterate per token, not per character, and were left alone.

**Verified.** `JavaHighlighterLongLiteralTest`: a 50,000-character literal, a literal with escaped quotes, and the
trigger shape (an apostrophe in a comment followed by 3,000 lines) — all three threw `StackOverflowError` against the
regex and pass against the scanner; the third also asserts the body after the apostrophe is not painted as a
literal. The real generated file renders (23,310 chars). Full verify, strict docs, sweep before commit.

**Reviewer must still check.** The highlighter's colouring of literals against the previous regex on ordinary
sources (a quote inside a line comment is coloured string then overridden by the comment pass, as before). Whether
the unit test's 3,000-line file is enough to overflow on every JDK's default EDT stack — it did here.

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

_Reviewed entries are retired to [`completed/unreviewed-changes-2026-09.md`](completed/unreviewed-changes-2026-09.md) and, earlier, [`completed/unreviewed-changes-2026-08.md`](completed/unreviewed-changes-2026-08.md)._
