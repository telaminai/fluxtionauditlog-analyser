# Tool agreement — the tools that describe an application must not contradict it, or each other

**Status:** proposed 2026-09-21 · **Builds:** the analyser work items TA-1…TA-8 · **Also records:** the
upstream asks the same evidence raised, so they are owned rather than lost.

## Why this spec exists

On 2026-09-21 a fresh LLM session with no coaching took the unmodified Spring/Mongoose starter
(`fluxtion-starter-core` 1.0.72) through three rounds of real work: a notional risk limit, trade
recording with mark-to-market by book, and an end-of-day report built with the starter's stub
generator. It predicted every result before measuring it.

**The runtime matched every prediction.** DATA versus TRIGGER, the at-limit boundary, the EOD publisher
not firing on an intraday re-mark — all exact, all provable from the audit log. Every wrong prediction
in the session was the agent's.

**The tooling around the runtime did not always tell the truth.** The analyser said a graph was "probably
from a different build" when it was not. The jar shipped a graph from the previous build next to the
running code. `validate` passed with every declared class missing. Report labels called confirmed-correct
behaviour "WHAT IS WRONG". None of these is a runtime defect, and every one is a tool describing the
application incorrectly.

That is the one failure this product cannot have. The headline is *trust the evidence, not the author*,
and positioning's launch gates name it directly: slice B, **truthful echoes** — "a headline about evidence
is measured against the analyser's own output". This spec turns one session's findings into a countable
list, fixes the analyser's share, and routes the rest.

One further reason to count rather than narrate: the session's own closing summary said "when my
expectation and the tool disagreed, the tool was right", a few lines after listing the analyser warning
that was wrong. An agent that has become impressed starts attributing disagreements to itself. A table
does not.

## Evidence

Copied into the repository so this spec cites files that cannot move. The copies are byte-identical to
the source.

| File | SHA-256 | What it is |
|---|---|---|
| [`session-report.md`](../handoff/evidence/unguided-session-2026-09-21/session-report.md) | `dc8f6798…5029787` | the session's own report, verbatim; source `~/tmp/fluxtion-spring-mongoose-1/docs/session-report-2026-09-21.md` |
| [`fixtures/MarketProcessor.src-round3.graphml`](../handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml) | `d56fc497…e324a03d8` | the generated graph for the running build: 23 nodes, `sourceFingerprint` `f6ae6f84…` |
| [`fixtures/MarketProcessor.target-stale.graphml`](../handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.target-stale.graphml) | `b057121f…782b77f766` | the copy in `target/classes` and the jar: 20 nodes, `sourceFingerprint` `4ecd6134…`, missing `MarketCloseEvent`, `eodReport`, `eodReportPublisher` |

Citations below are **report § / appendix row**. Every analyser item's acceptance uses these fixtures;
no new LLM session is needed to build or verify any of it.

## Baseline — known disagreements on 2026-09-21

This table is the direction check. Re-count it each release. The spec succeeds when every
**analyser**-owned row is closed by a committed test, and every other row has an owner and a link.

| # | What a tool says | What is true | Owner | Evidence |
|---|---|---|---|---|
| D1 | pairing: "declares 2 of 3 node(s)… probably from a different build" | the graphml declares `output`; it is a framework `SinkPublisher` | **analyser** → TA-1 | §5.6, A1 |
| D2 | the three pairing call sites compute "declared" three different ways | one question should have one answer | **analyser** → TA-1 | code, below |
| D3 | the jar's graph has fingerprint `4ecd6134…` | the running processor includes `eodReportPublisher` (R1 proves it ran); only `f6ae6f84…` declares it | build → upstream; **analyser** detects → TA-2 | §5.5, §3.6 R1, fixtures |
| D4 | report finding: "WHAT IS WRONG / LIKELY CAUSE" | the flag confirms correct behaviour | **analyser** → TA-3 | §5.6, A9 |
| D5 | windowed `delta` answer omits the flip at record 27 | the flip is present in the whole-log answer | **analyser** → TA-4 | A8 |
| D6 | follow shows the log as current | the newest record is held back until the next one arrives | **analyser** → TA-6 | §3.2 step 5, A7 |
| D7 | `validate`: "XML: valid; 5 nodes, 4 edges" | all three declared classes were missing | starter | §3.6 G1–G2 |
| D8 | the build accepts the round-1 `RiskCheck` | the reconciler refuses it three ways, for rules the contract does not state | starter / contract | §3.6 |
| D9 | the authoring contract: the starter generates stubs | nothing in the project can run it | starter | App. B |
| D10 | generated stubs are the recommended shape | they lack `EventLogNode`, so they cannot audit — the template's own convention | starter | §3.6 G4 |
| D11 | `SinkBinding.valueType = java.lang.String` | the generated processor declares `java.lang.Object` | compiler | §5.8 |
| D12 | `/ws/audit-tail` accepts connections | it delivers no records (starts at `toEnd()`) | Mongoose plugins 1.0.43 | §3.2, §5.2 |
| D13 | `/api/audit/files` reports record counts and times | they are frozen at startup while the queue grows | Mongoose plugins 1.0.43 | §5.3 |

---

## Analyser work items

### TA-1 · P0 · One declared set for pairing, and it includes framework nodes that log

**Root cause, verified against the code and the fixture.** The fixture's `output` node carries a label with
`id:output` and `fluxtion.kind = EVENT_HANDLER`, class `SinkPublisher`, so `GraphMlParser` reads it
correctly. The mismatch comes from what each caller passes as *declared*:

| Call site | Declared set |
|---|---|
| `ui/MainFrame.pairingAgainst` | `topologyPanel.authoredNodeIds()` — authored only |
| `topology/GraphmlDiscovery` (line ~139) | `Scaffolding.authoredNodes(topology)` — authored only |
| `session/node/Pairing` (line ~64) | `openGraph.declaredNodeIds()` — all declared |

A framework `SinkPublisher` writes `- output: { registeredMessageSink: output}` into the log, so it is
*logged*, but the two UI-side paths never count it as *declared*. The result is a false "different build"
verdict on the right graph. The session-processor path may give a different answer to the same inputs.
`GraphPairing`'s own javadoc warns against exactly this: "a second scorer would be a second answer to one
question, and they would drift."

**Required.**
- One function computes the declared set for pairing, and all three call sites use it.
- For pairing, *declared* means every node the graph declares. Hiding scaffolding is a **view** choice;
  pairing is a **fact** about which logged ids the graph accounts for. The view may keep hiding
  scaffolding.

**Acceptance.**
1. `fixtures/MarketProcessor.src-round3.graphml` paired with a log whose records write `rootNode`,
   `riskCheck` and `output` gives `matched == logged`, `applies == true`, and no "different build" text.
2. A test drives all three call sites with the same graph and log and asserts identical `GraphPairing`
   values.
3. **Negative control:** the M35.1 foreign-graph case (market-maker log, supermarket graph) still fails.
   The fix narrows a false positive; it must not weaken the check.
4. The tests use the committed fixture, not a hand-built graph.

### TA-2 · P0 · Say so when two copies of the same graph disagree

**Evidence.** One project held two `MarketProcessor.graphml` files: the source copy (23 nodes, `f6ae6f84…`)
and the `target/classes` copy, which is also what the jar ships (20 nodes, `4ecd6134…`). Resources are
copied before generation runs, so the shipped copy trails the build (§5.5). Fixing the build order is
upstream (D3). **Detecting** it is the analyser's job, because the analyser is where someone opens a graph
and trusts it.

**Required.**
- `open {discover: "graphml"}` groups candidates that share a file name or processor class. When copies in
  a group differ in `fluxtion.sourceFingerprint` or node set, it says so. For each copy, it reports the
  node count, fingerprint, modification time, and fit against the open log.
- Opening a graph that has a same-named sibling with a different fingerprint is **announced, not
  forbidden** (D-I3a).
- State facts only. The analyser does not declare which copy is correct. Fit against the log is the
  evidence, and the reader decides.

**Acceptance.**
1. Both fixtures under one temporary root: discovery reports the disagreement with both fingerprints and
   both node counts.
2. With a log in which `eodReportPublisher` writes, the source copy ranks first. The stale copy's pairing
   names the logged-but-not-declared id.
3. With no log open, the disagreement is still reported. It is a fact about the files, not about a run.

### TA-3 · P0 · A finding can be a confirmation

**Evidence.** Report finding sections are always labelled "WHAT IS WRONG" / "LIKELY CAUSE / SUGGESTED FIX",
including when the flag records correct behaviour (A9). This is a truthfulness defect, not cosmetics: the
tool asserts a fault that does not exist. It also pushes agents to phrase confirmations as faults.

**Required.** `flag` accepts `kind: fault | confirmation`, defaulting to `fault` for backward
compatibility. A confirmation renders as **Observation / Assessment** in the records table, the topology
callout, the report, and the PDF.

**Acceptance.** Existing reports and flags render unchanged. A confirmation flag renders the neutral labels
on all four surfaces. The kind survives save and restore.

### TA-4 · P0 · Window edges must not change the answer silently

**Evidence.** `series {expr: "delta(…)", filter: {from, to}}` omitted the flip at record 27. The first
in-window record has no predecessor, while the whole-log query found the flip (A8). The answer changed
with the window, and nothing said so.

**Required, in order of preference.** Rolling functions look back past `from` for the history they need.
Where that is not possible, the result must state that edge rows have no history. A silent omission is not
acceptable.

**Acceptance.** The A8 reproduction returns the flip at 27 with the window applied, or returns it absent
together with an explicit edge note naming the affected rows.

### TA-5 · P1 · Live Mongoose evidence in without reverse-engineering

**Context — do not reopen the decision.** `spec-agent-brokered-dev-loop.md` deleted in-app log discovery
(item 18.2) on purpose, handed fetching to the agent, and names an M31 live-store source
(`supportsFollow`) as the fix for Follow. The session shows what that route costs today. The agent found
`/api/audit/file/{id}/export` by disassembling the plugin jar, hit D12 and D13, wrote its own polling
follower, and then found the unterminated last record by reading raw bytes (§3.2, §5.1–5.4). That took
most of an hour before the first measurement.

**Required.**
- **Documentation, owned here.** The analyser's runbook and skill are vendored into starters. They gain
  an "Audit evidence" section: the export endpoint, D12 and D13 with workarounds, and the supported way
  to follow a live run.
- **A decision record.** Either ship the live-store follow source, or declare export-follow the supported
  path and ship one follower instead of every agent writing its own.

**Acceptance.** A fresh agent given the starter reaches a followed, live log in the analyser without
disassembling a jar or writing a follower. Verify this with one **spot-check** session scored from tool
events, not with the retired battery.

### TA-6 · P1 · A trailing unterminated record is shown as pending, never as complete

**Evidence.** The Mongoose export separates records with `\n---\n` and leaves the last one open, so follow
holds the newest record back and the view is always one behind (§3.2 step 5, A7).

**Required.** Follow the binary reader's rule (`spec-binary-audit-reader.md`): a record that may be partial
is never presented as complete. The status shows **"1 trailing record pending"**. Optionally, accept the
record after a quiet period if it parses completely. If that option ships, the accepted record is marked
as accepted-on-quiet.

**Acceptance.** A fixture log without a trailing separator shows the pending hint. If the option ships, the
record appears after the quiet interval, carrying its mark.

### TA-7 · P1 · The assistant can turn Follow on

**Evidence.** No verb starts Follow; the person had to click it (A6, §5.7).

**Required.** `open {follow: true}` or a `follow` verb. The echo states that follow is on. The person can
still stop it.

**Acceptance.** The agent path starts follow on a growing fixture file. The echo and the toolbar agree.

### TA-8 · P2 · Ergonomics raised by the same session

| Item | Required | Route |
|---|---|---|
| A2 | formulas accept boolean and string literals (`== true`, `== "true"`) | add goldens to `spec-formula-golden-fixtures.md` |
| A3 | marker series are spotlight targets, or the error says markers are not targetable | `spec-spotlight.md` |
| A4 | a spotlight on a hidden Project section reveals it, as other targets are revealed | `spec-spotlight.md` |
| A5 | `open {design}` outside the roots: the error names the one `source_root` call that fixes it | here; the starter-profile half is upstream |
| A10 | PDF flag glyph renders, or falls back to text | here |
| A11 | chart explanation box can be positioned, or is drawn below the plot | here |
| A12 | chart notes avoid collisions; legend sits outside the plot | here |

---

## Not the analyser's to build — recorded so they are owned

Carry these into [`docs/proposals/upstream-asks.md`](../proposals/upstream-asks.md) with an owner each.

- **Build order (D3):** generation must run before resources are copied, or the build must copy the
  regenerated graph. Stale generated source must not break a constructor change (§5.5, §6.5). This was
  found independently on 2026-09-20 in the path-auditor experiment as well: two sessions, one defect.
- **`validate` (D7):** check the Java against the design, or rename it to say it checks the XML only.
- **Reconciler versus build (D8):** document the three conventions — reference field named after its
  bean, sink as a field rather than a bean, no literal constructor arguments — in the authoring contract,
  or make the build enforce them. Today a project can be valid to the build and permanently outside what
  the authoring tool will touch.
- **Stub generation (D9, D10):** publish a released `fluxtion-starter-core` matching the pinned
  coordinate, ship a project script (`./author.sh validate|regenerate|link`), and make generated stubs
  match the template (`EventLogNode`, comment-contract comments, imports, formatting). D10 was filed on
  2026-09-19 as analyser feedback issue #6 and has now been found again independently.
  **Sequencing:** fix the stubs **before** telling agents to always use the generator. Otherwise the
  instruction sends every agent into T-EVENTLOG. The session avoided that trap only because it
  hand-wrote round 2.
- **Ownership record:** stubs generated in a copy and brought into a project leave no
  `fluxtion-authoring.json` behind (§8). The next reconcile cannot know who owns those members.
- **Compiler (D11):** carry `SinkBinding.valueType` into the generated field type.
- **Mongoose plugins 1.0.43 (D12, D13):** `/ws/audit-tail` must deliver records or be removed;
  `/api/audit/files` must report live counts and times; the export must close its final record.
- **Template:** `RootNode` logs `event.symbol()`, not the whole event. Remove `ServerSmokeTest` from the
  README or add the test. Add a one-feed, multi-event-type example. Add `final` versus
  `transient`/`@FluxtionIgnore` for working state to the contract. The starter's `.analyser` profile
  includes `src/main/fluxtion` as a source root.

## What this spec deliberately does not do

- It runs **no new LLM sessions**. Every analyser item is built and verified against the committed
  fixtures.
- It does **not** reopen item 18.2. TA-5 fixes the documentation and asks for a decision; it does not
  bring back in-app discovery.
- It attributes **nothing to the runtime**. Every runtime prediction in the session matched. The defects
  are all in the tools that describe the runtime.

## Closing rule

The rule from the cold-start work applies: **a finding is closed only when the check that would catch it
next time exists.** Every TA item's acceptance names a committed test on these fixtures. Re-count the
baseline table each release. The count of places where two tools disagree about the same artefact is the
measure of whether this is heading in the right direction.
