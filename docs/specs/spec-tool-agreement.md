# Tool agreement — the tools that describe an application must not contradict it, or each other

**Status:** implementation in progress 2026-09-21 · **Builds:** the analyser work items TA-1…TA-9 · **Counts:** the open
category B items of the 2026-09-19/20 feedback (D14–D20), which are built under their existing tracker
items · **Also records:** the upstream asks the same evidence raised, so they are owned rather than lost,
and the subsequent starter comment-emission finding D21. Baseline: 21 rows; D21 is upstream-owned.

> **Revised 2026-09-21 after review** (`docs/handoff/review_tool_agreement_brief_2026_09_21.md`). The first
> version **reversed the two fixture fingerprints**: the source copy is `4ecd6133…` and the stale copy
> `f6ae6f84…` (and `4ecd6134` was a typo). If you copied either value into a test, correct it. The first
> version also called feedback #23, #25 and #34 untracked; all three were already tracked, so TA-3 and TA-9
> now build under the existing items. Also resolved: fixture scope, hierarchy authority, pending-only
> trailing records, TA-5's delivery boundary, the counting rule, and the upstream asks after starter 1.0.73.

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
running code. Report labels called confirmed-correct
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
| [`fixtures/MarketProcessor.src-round3.graphml`](../handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml) | `d56fc497…e324a03d8` | the generated graph for the running build: 23 nodes, `sourceFingerprint` `4ecd6133…` |
| [`fixtures/MarketProcessor.target-stale.graphml`](../handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.target-stale.graphml) | `b057121f…782b77f766` | the copy in `target/classes` and the jar: 20 nodes, `sourceFingerprint` `f6ae6f84…`, missing `MarketCloseEvent`, `eodReport`, `eodReportPublisher` |
| [`fixtures/desk-quote-supertype.graphml`](../handoff/evidence/unguided-session-2026-09-21/fixtures/desk-quote-supertype.graphml) | `2b5b9ecf…78353b6b3781c` | TA-9's fixture: the 2026-09-20 principal-desk baseline graph. `MarketPrice → priceBook` and `Quote → acmeQuoteFeed` are separate event nodes; at runtime one `MarketPrice` (which `implements com.acmerisk.api.Quote`) dispatches to both |

Citations below are **report § / appendix row**. The packet holds **three graphml files and no audit log**.
Graph behaviour (TA-1, TA-2, TA-9) is tested on these fixtures. Behaviour the packet cannot supply —
windowed series, growing files, flags and reports — uses minimal **constructed** fixtures, labelled as
constructed regression cases and never presented as replays of the session.

## Baseline — known disagreements on 2026-09-21

This table is the direction check. Re-count it each release. The spec succeeds when every
**analyser**-owned row is closed by a committed test, and every other row has an owner and a link.

| # | What a tool says | What is true | Owner | Evidence | Status |
|---|---|---|---|---|---|
| D1 | pairing: "declares 2 of 3 node(s)… probably from a different build" | the graphml declares `output`; it is a framework `SinkPublisher` | **analyser** → TA-1 | §5.6, A1 | ☑ `GraphPairingTest.frameworkLoggerIsDeclaredInTheCommittedGraph` |
| D2 | the three pairing call sites compute "declared" three different ways | one question should have one answer | **analyser** → TA-1 | code, below | ☑ `PairingDuringLoadFrameTest.committedGraphPairsIdenticallyThroughFrameDiscoveryAndSession` |
| D3a | the jar ships a graph with fingerprint `f6ae6f84…` (20 nodes) | the running processor includes `eodReportPublisher` (R1 proves it ran); only the source copy `4ecd6133…` (23 nodes) declares it | build → upstream | §5.5, §3.6 R1, fixtures | ☐ |
| D3b | discovery and open say nothing when two copies of one graph disagree | they differ in fingerprint and node set | **analyser** → TA-2 | fixtures | ☑ `GraphmlDiscoveryTest.committedCopiesDisagreeWithoutALogAndRankByLoggedEvidence`; `PairingDuringLoadFrameTest.openingCommittedCopiesAnnouncesDisagreementWithoutRefusing` |
| D4 | report finding: "WHAT IS WRONG / LIKELY CAUSE" | the flag confirms correct behaviour | **analyser** → existing "Reports as evidence, not automatically defects" (25); TA-3 | §5.6, A9 | ☑ `FindingPresentationTest`, `FindingReportTest`, `ReportRendererTest`, `SessionRecoveryFrameTest.confirmationFlagSurvivesExplicitRecoveryOnlyAgainstTheSameLog` |
| D5 | windowed `delta` answer omits the flip at record 27 | the flip is present in the whole-log answer | **analyser** → TA-4 | A8 | ☑ `WindowedHistoryTest` (constructed regression, not session replay) |
| D6 | follow shows the log as current | the newest record is held back until the next one arrives | **analyser** → TA-6 | §3.2 step 5, A7 | ☑ `FollowAppendTest.pendingTailSurvivesQuietAndLaterFieldsUntilACompleteSeparator`; `PairingDuringLoadFrameTest.pendingTrailingRecordIsVisibleInContextAndFollowStatus` |
| D7 | `validate`: "XML: valid; 5 nodes, 4 edges" | true: the XML was valid. Three declared classes were missing, which `validate` does not claim to check | — | §3.6 G1–G2 | ⊘ reclassified — not an untrue echo; see the `validate` scope note below |
| D8 | the build accepts the round-1 `RiskCheck` | the reconciler refuses it three ways, for rules the contract does not state | starter / contract | §3.6 | ☐ |
| D9 | the authoring contract: the starter generates stubs | **standalone:** runnable since starter 1.0.73 (SG-1). **Hosted template:** still ships no local authoring files | playground → SG-2 | App. B; SG-1 release report | standalone ☑ · hosted ☐ |
| D10 | generated stubs are the recommended shape | they lack `EventLogNode`, so they cannot audit — the template's own convention. Feedback #6, tracked ◧ under "Feedback 38/39 and recurring 6"; found again here | starter | §3.6 G4 | ☐ |
| D11 | `SinkBinding.valueType = java.lang.String` | the generated processor declares `java.lang.Object` | compiler | §5.8 | ☐ |
| D12 | `/ws/audit-tail` accepts connections | it delivers no records (starts at `toEnd()`) | Mongoose plugins 1.0.43 | §3.2, §5.2 | ☐ |
| D13 | `/api/audit/files` reports record counts and times | they are frozen at startup while the queue grows | Mongoose plugins 1.0.43 | §5.3 | ☐ |

**How to count.** Twenty-one findings, D1–D21, with D3 split into D3a and D3b. **Analyser
responsibilities: 13** — D1, D2, D3b, D4, D5, D6 and D14–D20. **Current open: analyser 3; upstream 8.** **Upstream: 8 open** — D3a, D8, D9 (hosted),
D10–D13 and D21; D7 is reclassified. Report the two counts separately. Closing D3b (detection) never closes
D3a (the build defect). A compound row closes only when every disagreement it names is closed, and records
its regression test when it does. Track TA items alongside the rows: TA-5, TA-7 and TA-8 carry work with no
row of their own, so a zero analyser count is not completion of this spec.

---

## Analyser work items

### TA-1 · P0 · One declared set for pairing, and it includes framework nodes that log

**Status: ☑ implemented.** At closure: analyser **11** (baseline 13), upstream **8**.
[Tests and mutation witness](../handoff/report_tool_agreement_2026_09_21.md). The table below records the
pre-fix cause; all producers now use `GraphPairing.declaredNodeIds`, including the session input boundary.

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

**Status: ☑ implemented.** [Tests and mutation witness](../handoff/report_tool_agreement_2026_09_21.md#ta-2--completed).
D3b closes detection only; D3a remains upstream-owned and open.

**Evidence.** One project held two `MarketProcessor.graphml` files: the source copy (23 nodes, `4ecd6133…`)
and the `target/classes` copy, which is also what the jar ships (20 nodes, `f6ae6f84…`). Resources are
copied before generation runs, so the shipped copy trails the build (§5.5). Fixing the build order is
upstream (D3a). **Detecting** it (D3b) is the analyser's job, because the analyser is where someone opens a graph
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
4. **Negative control:** two byte-identical copies under one root are reported as agreeing.

### TA-3 · P0 · A finding can be a confirmation

**Status: ☑ implemented.** D4 closed; other work under the existing reports item stays open.
[Tests and mutation witness](../handoff/report_tool_agreement_2026_09_21.md#ta-3--completed).

**Evidence.** Report finding sections are always labelled "WHAT IS WRONG" / "LIKELY CAUSE / SUGGESTED FIX",
including when the flag records correct behaviour (A9). This is a truthfulness defect, not cosmetics: the
tool asserts a fault that does not exist. It also pushes agents to phrase confirmations as faults.

**Built under the existing item** "Reports as evidence, not automatically defects", which already
specifies neutral finding language and an explicit persisted category at the flag write site for feedback
#25. This is not a parallel item: A9 is fresh evidence for it, and the requirement and acceptance below are
offered to that item.

**Required.** `flag` accepts `kind: fault | confirmation`, defaulting to `fault` for backward
compatibility. A confirmation renders as **Observation / Assessment** in the records table, the topology
callout, the report, and the PDF.

**Acceptance**, on constructed flag and report inputs. Existing reports and flags render unchanged. A
confirmation flag renders the neutral labels on all four surfaces, including the PDF. The kind survives
save and restore.

### TA-4 · P0 · Window edges must not change the answer silently

**Status: ☑ implemented.** Rolling history starts at the loaded log’s beginning, respecting non-time
filters; time bounds select output only. [Tests and mutations](../handoff/report_tool_agreement_2026_09_21.md#ta-4--completed).

**Evidence.** `series {expr: "delta(…)", filter: {from, to}}` omitted the flip at record 27. The first
in-window record has no predecessor, while the whole-log query found the flip (A8). The answer changed
with the window, and nothing said so.

**Required, in order of preference.** Rolling functions look back past `from` for the history they need.
Where that is not possible, the result must state that edge rows have no history. A silent omission is not
acceptable.

**Acceptance.** The original A8 inputs (expression, window bounds, record values) were not preserved, so
this is a **constructed regression case, not a replay**: a minimal log whose tracked value changes on the
first record inside the window's lower bound. Freeze the expected semantics before the fix: with look-back,
the windowed answer includes the change; otherwise it carries an edge note naming the row. Mutation
witness: disabling the look-back, or the note, makes the test fail.

### TA-5 · P1 · Live Mongoose evidence in without reverse-engineering

**Status:** TA-5a ☑ documented and pinned; TA-5b ☐ route delivery/starter vendoring; TA-5c ☐ post-shipment spot-check.
[Route decision and canonical runbook](../runbooks/mongoose-audit-evidence.md). Counts unchanged: analyser 8, upstream 8 open.

**Context — do not reopen the decision.** `spec-agent-brokered-dev-loop.md` deleted in-app log discovery
(item 18.2) on purpose, handed fetching to the agent, and names an M31 live-store source
(`supportsFollow`) as the fix for Follow. The session shows what that route costs today. The agent found
`/api/audit/file/{id}/export` by disassembling the plugin jar, hit D12 and D13, wrote its own polling
follower, and then found the unterminated last record by reading raw bytes (§3.2, §5.1–5.4). That took
most of an hour before the first measurement.

**Required — three deliverables, owned separately.**
- **TA-5a · analyser · this spec.** Documentation: the vendored runbook and skill gain an "Audit evidence"
  section — the export endpoint, D12 and D13 with workarounds. A decision record naming the supported follow
  route (the live-store source, or one shipped export follower) **and its owner**.
- **TA-5b · the owner named in TA-5a.** Implement that route and vendor the updated documentation into the
  starters. Anything not shipped stays open; TA-5a does not close it.
- **TA-5c · one spot-check session**, only once TA-5a and TA-5b are available in a starter: a fresh agent
  reaches a followed, live log without disassembling a jar or writing a follower, scored from tool events.
  This is the only new session this spec permits.

**Acceptance.** TA-5a closes on the committed documentation and decision record. TA-5b and TA-5c close on
their own evidence. Nothing here reopens in-app discovery or runs the application from the analyser.

### TA-6 · P1 · A trailing unterminated record is shown as pending, never as complete

**Status: ☑ implemented.** [Regression and mutation evidence](../handoff/report_tool_agreement_2026_09_21.md#ta-6--completed).

**Evidence.** The Mongoose export separates records with `\n---\n` and leaves the last one open, so follow
holds the newest record back and the view is always one behind (§3.2 step 5, A7).

**Required.** Follow the binary reader's rule (`spec-binary-audit-reader.md`): a record that may be partial
is never presented as complete. The status shows **"1 trailing record pending"**. **Pending only in this
delivery:** a quiet interval does not establish completeness, because a valid prefix can still gain fields
after a pause, so no record is accepted on quiet. Any provisional display is a later, separate decision
with its own semantics.

**Acceptance**, on a constructed growing file. Without a trailing separator the record shows as pending and
is excluded from counts; appending the separator after a pause makes it appear. Mutation witness: a
follower that accepts on quiet fails the append-after-pause case.

### TA-7 · P1 · The assistant can turn Follow on

**Status: ☑ implemented.** Standalone `open {follow: true|false}` after the log finishes opening;
mixed operations refuse. [Regression and mutation](../handoff/report_tool_agreement_2026_09_21.md#ta-7--completed).

**Evidence.** No verb starts Follow; the person had to click it (A6, §5.7).

**Required.** Use the existing `open` surface: `open {follow: true}`. The verb surface is pinned, so a new
`follow` verb needs separate approval. The echo states that follow is on. A reader that cannot follow says
so and never echoes Follow as active. The person can still stop it.

**Acceptance**, on a constructed growing file. The agent path starts follow; the echo and the toolbar
agree. Negative: on a reader without follow support, the echo reports that it is not following.

### TA-9 · P1 · Draw the route that actually runs when dispatch goes through a supertype

**Status: ◧ before case implemented; producer relationship contract still blocked.**
`DispatchHierarchyTest` checks the committed graph, unknown disclosure and complete-trace negative control.
[Mutation and limits](../handoff/report_tool_agreement_2026_09_21.md#ta-9--before-case-completed-producer-union-blocked). D20 remains open.

**Built under the existing item** "Authoritative dispatch metadata (34), P1": the compiler/exporter supplies
known concrete dispatch relationships, and the analyser displays the facts and their limits. TA-9 is the
**analyser half** of that item, not a new one. An earlier revision of this spec called #34 untracked; that
was wrong.

**Evidence.** `MarketPrice` implements the vendor's `com.acmerisk.api.Quote`, so one `MarketPrice` event
dispatches down **both** `MarketPrice → priceBook` and `Quote → acmeQuoteFeed`. The graph shows them as
unrelated event types. That polymorphism is **how a vendor component is integrated**, so the missing route is
the integration itself. Anything keyed on the event's own class sees half of what runs.

**Required.** The authority is the **producer**. The compiler already derives this dispatch when it generates
the processor, so the relationship must arrive as producer metadata in the graph, under the existing item's
contract. The analyser consumes it and never infers it: not by executing application classes, not from a
hierarchy it was not given, and not from nodes appearing in the same cycle. With the metadata, a record's
topology shows every route its event takes, and coverage and "not on this path" shading use that same union.
Without it, the route is shown as **unknown**, never as absent.

**Acceptance.** Blocked on the producer contract. The committed `desk-quote-supertype.graphml` does **not**
encode `MarketPrice implements Quote` (it carries no superclass or interface metadata), so it is the *before*
case: with it, the analyser reports the hierarchy as unknown rather than shading `acmeQuoteFeed` as off-path.
Once the producer vocabulary exists, add a fixture carrying the relationship and test that the union route is
drawn, that an unrelated event class draws no extra route, that two event types sharing a simple name in
different packages are not conflated, and that missing metadata stays explicitly unknown.

### TA-8 · P2 · Ergonomics raised by the same session

| Item | Required | Route |
|---|---|---|
| A2 ☑ | formulas accept boolean and string literals (`== true`, `== "true"`) | goldens 11–13, `LiteralFormulaTest`, report/marker regressions; [witness](../handoff/evidence/tool-agreement-2026-09-21/ta8-a2-mutation.json) |
| A3 ☑ | marker series are spotlight targets, or the error says markers are not targetable | `spec-spotlight.md` |
| A4 ☑ | a spotlight on a hidden Project section reveals it, as other targets are revealed | `spec-spotlight.md` |
| A5 ☑ | `open {design}` outside the roots: the error names the one `source_root` call that fixes it | here; the starter-profile half is upstream |
| A10 ☑ | PDF flag glyph renders, or falls back to text | here |
| A11 ☑ | chart explanation box can be positioned, or is drawn below the plot | here |
| A12 ☑ | chart notes avoid collisions; legend sits outside the plot | here |

---

## Carried forward — the same failure, reported on 2026-09-19/20

Category B of the September authoring feedback was titled *"the canvas says something untrue — the LLM then
repeats it with confidence."* This spec covers the same failure, found again by an independent session. Two
lists would drift, so the open category B items are **counted in this baseline** and **built under their
existing tracker items**. Their acceptance lives there; this table does not duplicate it.

| # | What a tool says | Feedback | Where it is built | Status 2026-09-21 |
|---|---|---|---|---|
| D14 | a graph, log or receipt is current when it is stale; a combined action echoes pre-load state | #1, #9, #20 | "Staged feedback — evidence correctness first" | ☐ |
| D15 | spotlight `ok` when lit in the wrong place, with negative bounds, or with `add:true` dropping targets | #2–4, #39 | same item; #39 under "Feedback 38/39 and recurring 6" | ☐ / ◧ |
| D16 | marker counts that do not match the data: carried state evaluated as if it were an event | #12, #19 | "Staged feedback — evidence correctness first" | ☑ `MarkerResolutionTest`, `GraphEchoWarningsTest`, `MarkerLegendTest` |
| D17 | an empty plot, with no explanation, after a pinned window survives a log change | #41 | "Chart feedback 41–43 intake" | ☑ `GraphWindowScopeTest` |
| D18 | left-axis values contaminated by right-axis values under windowing | #42 | "Chart feedback 41–43 intake" | ☑ `ChartAxisWindowTest` |
| D19 | `showAll: true` reports the full graph while a focus is still active | #37 | "Topology feedback 37" | ☑ `TopologyShowAllTest` |
| D20 | the graph shows one route for an event that dispatches down two | #34 | "Authoritative dispatch metadata (34)"; analyser half in TA-9 | ☐ |

**Priority change proposed:** "Staged feedback — evidence correctness first" is filed as P1. It is the same
launch gate as TA-1 to TA-4 (slice B, truthful echoes), so it should be **P0 alongside them**.
**Owner decision, 2026-09-21:** keep its existing **P1** priority for this pass.

Not carried: #29 and #30 (dependency integrity) were deliberately excluded by the 2026-09-21 release
decision. They remain slice A and are not a truthful-echo item.

## Additional upstream finding — reviewer evidence, 2026-09-21

| # | What a tool emits | What is wrong | Owner | Status / evidence |
|---|---|---|---|---|
| D21 | starter 1.0.73 repeats comment-contract text and inserts it between Java modifiers and annotations | the generated explanation is duplicated within the declaration rather than attached once to its member | **starter/compiler**, upstream | ☐ — [preserved reviewer packet](../handoff/evidence/stub-reconcile-1.0.73-2026-09-21/README.md), especially `AlertNode.as-generated.java` |

The saved source directly shows the formatting defect. The reviewer reports that this source compiled;
this intake did not independently rerun generation or compilation. It is not a runtime finding and does
not change the analyser-owned denominator. Before closing D21, test that each canonical comment appears
once at its member boundary, never between modifiers/annotations, after generation and subsequent
reconciliation; retain the cross-emitter comment-contract parity and Java compilation checks.

---

## Not the analyser's to build — recorded so they are owned

Carry these into [`docs/proposals/upstream-asks.md`](../proposals/upstream-asks.md) with an owner each.

- **Build order (D3a):** generation must run before resources are copied, or the build must copy the
  regenerated graph. Stale generated source must not break a constructor change (§5.5, §6.5). This was
  found independently on 2026-09-20 during a separate regeneration experiment as well: two sessions, one defect.
- **`validate` scope (D7, reclassified):** `validate` said "XML: valid" and was right. The unmet expectation
  is that a command named `validate` checks the code. Keep the agreed XML-only validation tier; state that
  scope in its output and help text, and leave code-against-design checking to the separately owned
  model/build tier. This is capability disclosure, not an untrue echo.
- **Reconciler versus build (D8):** document the three conventions — reference field named after its
  bean, sink as a field rather than a bean, no literal constructor arguments — in the authoring contract,
  or make the build enforce them. Today a project can be valid to the build and permanently outside what
  the authoring tool will touch. Feedback **#23** (`parentUpdateCallback` documented without a
  type) is the same failure — the rule exists in the tool but not in the contract — and is already tracked
  under "Additional desk-session intake (22/23/28)".
- **Stub generation (D9, D10), rescoped after starter 1.0.73.** The standalone template has shipped
  setup, validate and generate since 1.0.73 (SG-1, closed), so do not re-request publication or a new
  command surface. Still open: the hosted template's missing authoring files (**SG-2**), and new-node stubs
  that cannot audit (**D10**, feedback #6; public issue #3). **Sequencing:** fix the stub shape **before**
  telling agents to always use the generator, or the instruction sends every agent into T-EVENTLOG. The
  session avoided that trap only because it hand-wrote round 2.
- **Ownership record:** stubs generated in a copy and brought into a project leave no
  `fluxtion-authoring.json` behind (§8). The next reconcile cannot know who owns those members.
- **Comment emission (D21):** starter/compiler owner: eliminate duplicated/interleaved comment-contract
  text in new and reconciled members; preserve the canonical wording and implemented bodies. Regression
  acceptance and the reviewer evidence are recorded above. Do not close this by cleaning up the fixture.
- **Compiler (D11):** carry `SinkBinding.valueType` into the generated field type.
- **Mongoose plugins 1.0.43 (D12, D13):** `/ws/audit-tail` must deliver records or be removed;
  `/api/audit/files` must report live counts and times; the export must close its final record.
- **Template:** `RootNode` logs `event.symbol()`, not the whole event. Remove `ServerSmokeTest` from the
  README or add the test. Add a one-feed, multi-event-type example. Add `final` versus
  `transient`/`@FluxtionIgnore` for working state to the contract. The starter's `.analyser` profile
  includes `src/main/fluxtion` as a source root.

## What this spec deliberately does not do

- It runs **no new LLM sessions except one**: TA-5c's single spot-check, once its route and documentation
  have shipped. Graph behaviour is verified on the committed fixtures; everything else on labelled
  constructed fixtures.
- It does **not** reopen item 18.2. TA-5 fixes the documentation and asks for a decision; it does not
  bring back in-app discovery.
- It attributes **nothing to the runtime**. Every runtime prediction in the session matched. The defects
  are all in the tools that describe the runtime.

## Closing rule

The rule from the cold-start work applies: **a finding is closed only when the check that would catch it
next time exists.** Every TA item's acceptance names a committed test — on the committed fixtures, or on constructed ones
labelled as such — with a mutation witness showing it can fail. Re-count the
baseline table each release. The count of places where two tools disagree about the same artefact is the
measure of whether this is heading in the right direction.
