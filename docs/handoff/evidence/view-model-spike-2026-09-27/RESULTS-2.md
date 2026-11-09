# View-model nodes, second element: results (2026-09-27)

Scores [`PREDICTIONS-2.md`](PREDICTIONS-2.md) (`790427ee`, before any code). Every number measured on this branch
with `StatusLineAuditVolumeTest`, the same 1,000-poll Follow loop the first element used.

## The question, answered

**H2. The cost is per CHANGE, not per element.** And more sharply than predicted: the second element adds
**no records at all**.

| | one element | two elements | delta |
|---|---|---|---|
| records written | 5,800 | **5,800** | **0** |
| bytes | 2,548,484 | 2,632,484 | **+84,000 (+3.3%)** |
| renders | 600 | 600 | 0 |
| `ViewRendered` records | 600 | 600 | 0 |

The whole 84 KB is one thing: the new node's **invocation-tracing line**, 70 bytes, in each of the two cycle kinds
it is triggered in (`LogAppended` and `ViewRendered`), 600 times each.

**This settles what the first element could not.** The re-scope ring holds RECORDS. A view element that does not
change writes none, so it cannot shrink the Follow window at all. The 40→25 poll loss is the status line's alone —
the price of an element that restates itself on every appending poll — and is not a tax the next element pays.

## Predictions, scored

| # | prediction | result |
|---|---|---|
| 1 | `-Pregen` succeeds; both copies byte-identical; publishable | **Hit**, with a caveat: see [Finding 3](#finding-3-regeneration-needs-the-network). |
| 2 | The three call sites go, and both cross-calls to `LogTablePanel` | **Hit.** `onSessionSnapshot` has no `setIdentityNote`; `GraphTabs` and `DetailPanel` no longer ask `LogTablePanel` whether to draw. |
| 3a | 0 renders, 0 `ViewRendered`, 0 batch ends | **Hit.** |
| 3b | added records **< 1%** | **Hit, better than predicted: 0%.** |
| 3c | added bytes **< 2%** | **MISS. +3.3%.** |
| 3d | the addition is the invocation line, ~40 B | **MISS on both counts.** It is 70 B, not ~40, and it lands in **two** cycle kinds, not one. I reasoned about the line's size from the results file's "about 40 B" without checking that a node with a longer name in more cycles costs more. |
| 4 | Therefore H2 | **Hit**, and the status line is the codebase's worst case. |
| 5 | A verdict change emits one view, three backends draw | **Hit** (`IdentityBannerViewTest`). |
| 6 | Existing suites pass unchanged | **MISS. Three failed**, and what they were is the most useful finding here — see [Finding 1](#finding-1-moving-a-call-site-invalidates-the-static-check-anchored-to-it). |

**The "not worth it" outcomes, stated in advance:** none met. Backends needed nothing beyond the view; the node
reads only the session; the measured cost is H2.

## Findings

### Finding 1: moving a call site invalidates the static check anchored to it

Two rule-8 regression checks asserted the exact text of the hand-fed calls in `onSessionSnapshot`:

```java
assertTrue(body.contains("graphTabs.setIdentityNote(GraphTabs.identityBannerText(next.logIdentity(), …))"))
```

They failed because the call sites are the thing this element removes. **They were right to fail** — that is a
static check doing its job — but it means every migration of this kind carries a re-anchoring cost that the first
element's results file recorded only for mutation controls ("both controls now point at headless witnesses") and
did not generalise.

Re-anchored, not deleted: the property is unchanged — all three surfaces state the verdict — so the checks now
assert the backend registration, **plus** that `onSessionSnapshot` no longer contains `setIdentityNote` at all. The
second half is new and is what stops the hand-fed path quietly coming back.

**For planning: budget a re-anchor per migrated element, not per migration.**

### Finding 2: a second element exposed a test that was not element-specific

`StatusLineViewTest#theAuditSaysWhatTheLineWasTold` asserted `sink.matching("backends: recorder").size() == 2`.
That string matches **any** view's render answer, so a second element rendering to the same recorder broke it. The
assertion was about the status line and was written against a string that is not.

It now matches `rendered: statusLine`. Nothing was wrong with the production code; the test was over-broad and only
a second element could reveal it. **Expect one of these per shared mechanism** — the first user of a mechanism
cannot write a test that distinguishes itself from a second user who does not exist yet.

### Finding 3: regeneration needs the network

`mvn -o -Pregen process-classes` fails offline: `build-helper-maven-plugin:3.6.0` is not in the local repository
and cannot be fetched. With the network it succeeds first time and both generated copies come out byte-identical.

Not a defect, but it belongs in the authoring cost: **a node cannot be added on a train.** Everything else in this
codebase builds and tests offline.

### Finding 4: the floor cost is real but small, and it is per triggered cycle

A view node that never renders is not free. It is triggered whenever its inputs move, and invocation tracing writes
its line each time — by design, because that is what makes absence from the record mean "did not run". Here: 70 B
× 2 cycle kinds × 600 = 84 KB.

Extrapolated to six non-changing elements: **+0 records, ~+20% bytes.** Bytes are not what the ring holds, so the
Follow window is unaffected; but it is not nothing, and it is the number to watch if view nodes are added liberally
to a graph that already has many.

## What this says about the direction

Worth continuing. The objection the first element raised — that +45% per element would make the idea collapse after
three or four — **does not hold.** The cost tracks how often a view changes; the status line is the worst case in
the codebase and the identity banner, which is typical, costs zero records.

The remaining costs are the two the first results file named (fold the scan's facts, reclassify the render answer),
both of which only affect *changing* views, plus the two authoring costs found here: a re-anchor per migrated
element, and no offline regeneration.

**Next**, on this evidence: the tooltip, Reports tab and time-order dialog — after which `renderLogEvidence`
disappears entirely — then the chart, on top of PR #53's `Surface`.
