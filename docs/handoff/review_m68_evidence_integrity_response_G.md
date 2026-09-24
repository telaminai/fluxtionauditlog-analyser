# Review — the M68 evidence-integrity review (reviewer G)

Reviewing `docs/handoff/review_evidence_integrity_2026_09_24.md` (branch
`review/m68-evidence-integrity-2026-09-24`, commit `ed06170c`) against the specification it reviews,
`docs/specs/spec-evidence-integrity.md` at `7ecb0c38`, the published Format 1 contract, the committed
recovery packet and the baseline source at `main`.

**Conflict of interest, declared first.** I wrote the specification under review. I am therefore the least
independent party available to assess its review, and an assessment that simply agreed with the review
would be worth nothing either way. So no finding below is accepted on the reviewer's authority: every one
is checked against source, the published contract, or the committed artefacts, and the check is named.

**Verdict: the review is sound and should be actioned. All five findings CONFIRMED.** Two are narrowed on
detail. One further defect the review does not reach changes what the first correction should be, and it
is the reason M68.1 is now cheaper than either document assumes.

---

## The finding the review does not reach

**G-A · The graph declares node authorship and audit capability per node, and the analyser does not read
the authorship fact.** This is the actual root cause of the false warning, and it makes both EI-1's
diagnosis and my own acceptance 1 slightly wrong.

The packet's preserved graph carries `fluxtion.framework` and `fluxtion.auditCapable` on every node:

| node | `fluxtion.framework` | `fluxtion.auditCapable` | `fluxtion.kind` |
|---|---|---|---|
| `checked` | false | true | EVENT_HANDLER |
| `child` | false | true | EVENT_HANDLER |
| `rootNode` | false | true | EVENT_HANDLER |
| `serviceRegistry` | true | true | NODE |
| `clock`, `context`, `eventLogger`, `nodeNameLookup`, `callbackDispatcher`, `subscriptionManager` | true | false | NODE / EVENT_HANDLER |

The producer states plainly that `checked` is an authored node that can write audit output, and the
graph-level `fluxtion.authoredNodeCount` is 3, matching those three nodes exactly.

The analyser never reads it. `Scaffolding.isScaffolding` decides framework membership from a hardcoded
package-prefix list and a hardcoded type-name set applied to `fluxtion.class`; the file contains no
reference to `fluxtion.framework` at all. `checked`'s class is
`com.telamin.fluxtion.runtime.output.SinkPublisher` — a framework class the developer used as an authored
node and named. The prefix matches, so the heuristic calls it scaffolding, overriding an explicit
declaration that says the opposite. `CoverageScope.of` then builds its scope by iterating only that
derived authored set, so `checked` enters neither `loggable` nor `excluded`, and
`CoverageService.assess` computes `outOfTopology` by removing those two sets from the logged ids. A
declared, authored, audit-capable node therefore falls out as "absent from the topology" and fires
"the graphml is probably from a different build, which makes every other figure here suspect".

Three consequences.

1. **The codebase already has the rule this violates, one class away.** `NodeLogging` line 104 is headed
   *"Ask the graph first"* and reads `node.fact("fluxtion.auditCapable")` in preference to inference
   (M45). `CoverageScope` line 172 likewise drops a node as silent-by-construction when *the graph
   declared it cannot log*. `GraphVocabulary.trustedForNodeFacts()` is the existing gate for exactly this
   class of fact. Scaffolding is the one place that infers where the graph declares.
2. **The correct denominator is 3, not 2.** EI-1's required acceptance keeps `declared=2`, `covered=2`.
   With the declared authorship honoured, the authored and audit-capable population is
   `{checked, child, rootNode}`, so the honest figures are `declared=3`, `covered=3`, `ratio=1.0`. That
   is a denominator change for a *declared* reason, not the "sweep framework nodes in and change what
   coverage means" that acceptance 1 was written to forbid. My acceptance 1 ("the coverage denominator
   unchanged") and EI-1's restatement of it both preserve a population the graph contradicts.
3. **M68.1 needs no packet logs.** A minimal fixture is a graph declaring one `framework=false`,
   `auditCapable=true` node whose class sits under a framework package, plus a log in which that node
   writes. That is constructible from the committed graph alone and does not depend on the missing inputs
   EI-2 identifies. EI-2 therefore does not gate M68.1.

**Required correction, superseding EI-1's:** read `fluxtion.framework` as the authorship fact when node
facts are trusted, falling back to the class heuristic only when the key is absent. Keep EI-1's separate
correction as well — membership for the out-of-topology warning is compared against the full declared
node set, because a framework node that logs (`eventLogger`, say) must not warn either.

**Smaller point for the graph vocabulary.** `GraphVocabulary.trustedForNodeFacts()`'s documentation
enumerates the trusted node facts as `auditCapable`, `kind`, `class`, `callbackKinds` and
`topologicalRank`, and omits `framework`, which emitted graphs carry at `metaVersion` 1.0. The
aggregation argument recorded there applies to every node fact equally, so this reads as an omission in
the doc rather than a restriction. Confirm and add it when G-A is implemented.

**Basis:** node facts extracted from the committed graph; `Scaffolding`, `CoverageScope`,
`CoverageService`, `NodeLogging` and `GraphVocabulary` read at `main`. Not reproduced through a running
analyser.

---

## Per-finding disposition

### EI-1 · CONFIRMED, and strengthened beyond a specification gap

The contradiction the review names is real and I had already conceded it: D-E1 line 31 requires a
denominator from the complete declared set, while acceptance 1 and the tracker preserve the authored
coverage population. Those are different populations and the spec asserts both.

Every source claim checks out. `CoverageService.assess` removes `scope.loggable()` and
`scope.excluded().keySet()` from the logged ids and warns on the remainder.
`GraphPairing.declaredNodeIds` does already return every declared id. `GraphPairing.KEEP_ABOVE` is 0.5,
so retention is a greater-than-half overlap policy, and with no logged ids `GraphPairing.of` allows the
graph with the words *"this log records no node output, so it cannot say whether the graph applies"*.
The review is right that none of that establishes build provenance. One correction to its citation: the
service lives at `analyser/topology/CoverageService.java`, not the `coverage/` path the review gives.

**Strengthening.** The review treats the build-identity boundary as a clause to add. It is already
required by an accepted specification. `spec-tool-agreement.md` states that opening a graph whose
same-named sibling has a different fingerprint is *"announced, not forbidden"*, and then: *"State facts
only. The analyser does not declare which copy is correct. Fit against the log is the evidence, and the
reader decides."* The shipped warning declares a build-provenance conclusion — "probably from a
different build" — from a name-membership test. That is a conformance defect against tool agreement
today, not merely an M68 requirement. It raises the priority of M68.1 and settles how the fix is
justified.

### EI-2 · CONFIRMED, narrowed twice

The inventory is as the review states. The packet holds the graph, the coverage response, the screenshot,
the exported PDF, the scenario checker and the summaries. There is no `exchange/run1.log` or `run2.log`,
and `SUBJECT-REPORT.md` line 17 names `exchange/run1.log` as the input to the failing call. `run1.txt` is
scenario-checker output — `STATE` lines and a `PASS` verdict — not an audit log. A reviewer elsewhere
cannot push the original log through coverage, reproduce the framing verdict, or regenerate the export.

**First narrowing.** The review hedges with "if original inputs cannot be published". That hedge is very
likely moot. The committed graph already carries `com.example.myapp` placeholder names and
`fluxtion.toolchainVersion` 1.0.74, so the log from the same starter project should pass the public-repo
sweep as it stands. Attempt publication before falling back to a constructed case.

**Second narrowing.** Given G-A's constructible fixture, EI-2 blocks M68.2 and M68.3 but not M68.1. The
review's own summary makes M68.1 wait on "its replay inputs in EI-2"; it need not.

The review's demand that "same inputs" be defined for the chart comparison is correct and matters: a
series point outside a pinned window does not falsify a no-data chart, and my acceptance 8 as written
does not say which inputs must match.

### EI-3 · CONFIRMED as an outright error in my acceptance 9

The published contract settles it. Format 1 §1: *"non-blank text anywhere is a record"* — a file holding
content and no separators is one legal record. §1a: *"An unterminated final record that does not look
like a marker is an ordinary record in a static read and a pending record in a live read."* My acceptance
9 would flag both as broken framing. The review is right, and this was the one finding I conceded before
reading it.

The review is also right, and understates its own case, on the header-like-text hazard. §1a already
forbids precisely the inference D-E9 invites: *"audit records carry a producer's own `toString` output,
which may contain any text at all, including a line that reads exactly like this key... Recognise the
marker by what the record contains in full, never by what it mentions."* Splitting nine header-looking
blocks into nine records is the same defect the termination rule was written to stop, and that rule was
paid for with a shipped fabricated verdict. D-E9 must diagnose *suspected* collapsed framing with its
basis, and must never report a record count it inferred from repeated text.

The producer entry is as the review says: `SUBJECT-REPORT.md` line 72 names `FluxtionMain.java`'s log
sink. Closing a Mongoose delivery path alone would not have prevented this file.

### EI-4 · CONFIRMED, narrowed on one detail

`ActionExecutor.doOpen` does return immediately when `logs` holds a non-empty list, before `graphml` is
read at all. So `open {logs: [...], graphml: ...}` silently discards the graph — acceptance 3's case,
confirmed by control flow. The ordinary single-log path opens the log first and attempts the graph after,
so a graph failure leaves the log open and the request neither applied nor undone. `PAIRING_PENDING`
confirms the review's asynchronous point: the graph verdict can be pending when the reply is sent.

**Narrowing.** The review says the spotlight can be cleared "even before verb validation". More precisely
it is before *parameter* validation, and only for verbs on `SpotlightTarget.VIEW_CHANGING_VERBS`; an
unknown verb clears nothing. The contract problem is unchanged: `open {follow: "yes"}` is refused after
the spotlight has already gone out, so a refused request has altered pre-request state. My D-E3 does not
say whether that is permitted, and it must.

### EI-5 · CONFIRMED

My D-E6 line 62 — "Detection already exists in `context`; what is missing is the action" — overstates
what exists. `FileObservation` records size, modification time and filesystem file key, and its own basis
string reads *"size, modification time and file identity; unchanged metadata does not prove identical
bytes"*. Its states are `unchanged-metadata` and `changed-on-disk`. That detects change, not replacement,
and explicitly disclaims byte identity.

The concrete gap acceptance 6 must name, which neither document states: `MainFrame.pollFollow` appends
when `appendFrom` returns zero or more and reloads only when it returns a negative, meaning shrink or
rotation. A same-length in-place replacement produces no append and no reload, so there is no
announcement on any surface. That is the case the acceptance has to carry, alongside the review's
truncation, ordinary append and missing-file-key cases.

---

## Owner decisions

- **Q1.** Accept the review's recommendation, and note it is closer to settled than "open".
  `spec-tool-agreement.md` already commits to announced-not-forbidden and to stating facts without
  declaring which artefact is correct. Choosing to block would be a compatibility change against an
  accepted spec, so annotate is the default and blocking needs a reason on the record.
- **Q2.** Accept. The review is right that D-E5 and acceptance 5 already choose refusal while Q2 still
  offers mapping — my spec pre-empts its own open question. Make both clauses conditional until you
  decide.
- **Q3.** Genuinely yours; it is a cost decision, not a correctness one. The review's split — an
  executable content check as a standing gate, visual inspection only when rendering changes — is the
  cheaper half of D-E8 and keeps the part that catches a regression.

## What must change before implementation, and what is optional

**Required before M68.1:** G-A's declared-authorship correction, with acceptance figures `declared=3`,
`covered=3`, `ratio=1.0`; EI-1's separate membership-against-the-full-graph correction; the
build-identity clause; and the removal of "the coverage denominator unchanged" from acceptance 1.

**Required before M68.2 and M68.3:** EI-2's fixture definition, including the attempt to publish the
original log and the definition of "same inputs"; EI-3's observable condition, diagnostic strength, and
the preservation of the ordinary-tail and static/follow rules.

**Required before M68.4 and M68.5:** EI-4's enumeration of the covered verb combinations and its
statement of what a refusal preserves; EI-5's definition of identity, of append versus replacement, and
of the observation boundary.

**Optional:** linking the packet from the spec, naming tests beside each slice, and the
`GraphVocabulary` documentation addition under G-A.

Nothing here needs another client trial, a redesign, a new verb, or a producer rewrite to settle. I agree
with the review on that, and it is worth saying because the triggering incident was a client-facing one
and the reflex is to reach for a bigger response than the defect deserves.

## Checks I ran, and their limits

Source and artefact inspection, at `main` and against the committed packet: `CoverageService`,
`CoverageScope`, `Scaffolding`, `GraphPairing`, `NodeLogging`, `GraphVocabulary`, `ActionExecutor`
(`render` and `doOpen`), `FileObservation`, `MainFrame.pollFollow`; `docs/site/format-spec.md` §1 and
§1a; `spec-tool-agreement.md`; the packet's graph node facts, `coverage-response.json`, `run1.txt` and
`SUBJECT-REPORT.md`.

Reproduced behaviour, Java 21, on the reviewed commit:

```sh
JAVA_HOME=/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.8/Contents/Home \
  mvn -q -Dtest=CoverageServiceTest,CoverageScopeTest,GraphPairingTest,FormatConformanceTest,FollowAppendTest test
```

| Suite | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| CoverageServiceTest | 2 | 0 | 0 | 0 |
| CoverageScopeTest | 10 | 0 | 0 | 0 |
| GraphPairingTest | 8 | 0 | 0 | 0 |
| FormatConformanceTest | 28 | 0 | 0 | 0 |
| FollowAppendTest | 4 | 0 | 0 | 0 |

Those counts match the review's table for the same five suites, so its verification record is accurate
as far as I checked it. They establish the current baseline only. I did not re-run the client scenario,
open a UI session, drive an export, probe a malformed input, or exercise a filesystem race. I did not run
the full suite; the review did, and reports 1,876 tests with 62 skips.

## Remaining uncertainties

- Whether `fluxtion.framework` is emitted by every graph source the analyser accepts, or only by the
  current toolchain at `metaVersion` 1.0. The fallback to the class heuristic is needed either way, but
  the answer decides how prominent the fallback is and whether older graphs keep the false warning.
- Whether any surface other than coverage relies on `Scaffolding`'s heuristic in a way that G-A would
  change. The subgraph filter and the hide-scaffolding checkbox both use `authoredNodes`, so honouring
  the declared fact will move what those show for a graph like this one. That is almost certainly the
  correct behaviour, and it is a visible change that needs its own acceptance line.
- Whether the original `exchange/run1.log` still exists anywhere recoverable. EI-2's correction is much
  weaker if it does not, and I did not search outside the committed packet.
- `serviceRegistry` is declared `framework=true, auditCapable=true`. It is out of the authored coverage
  population by design, but it can log, so it is a live candidate for the out-of-topology warning that
  EI-1's membership fix must cover. I did not check whether it logs in practice.

Reviewer: G. No specification, tracker, source or preserved evidence was changed by this review.
