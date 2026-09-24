# Review — M68 evidence integrity

Reviewed `docs/specs/spec-evidence-integrity.md` and its tracker entry at
`7ecb0c38659f6fac206cbfc7cc66bc367536294c`, 2026-09-24.

**Verdict: CONDITIONAL.** The governing principle, separation from producer work, and delivery order are sound.
Five requirements need clarification before the affected slices are handed off. These are contract and
acceptance gaps, not an argument for another client trial or a redesign. M68.1 can start once EI-1's
declared-authorship and membership corrections are specified. It does not need the original logs: a labelled
constructed log against the committed graph can exercise both boundaries. EI-2's original replay inputs
remain needed for M68.2 and M68.3; the later findings need not hold up the first fix.

**Correction after reviewer G, 2026-09-24:** [response at `7b8d9f51`](https://github.com/telaminai/fluxtionauditlog-analyser/blob/7b8d9f51/docs/handoff/review_m68_evidence_integrity_response_G.md).
My first review preserved `declared=2, covered=2`. That was wrong. I checked the graph and source again:
the graph declares three authored, audit-capable nodes, including `checked`, which the package heuristic
incorrectly hides. EI-1 below now requires 3/3 and keeps the separate full-graph membership correction.
The original review remains in commit `ed06170c`; this correction does not present its missed fact as an
original finding. G's five confirmations do not constitute implementation closure.

This review used a separate worktree on `review/m68-evidence-integrity-2026-09-24`. No spec, tracker, source,
or preserved evidence was changed. The primary checkout was not touched.

**Independence:** this session prepared the recovery evidence cited by the proposal. This is a fresh review of
the proposed contract against the recorded revision, not an independent repetition of that client experiment.
Read-based findings below are identified as such. No new client, UI session, generation, or paid operation ran.

## EI-1 · High — distinguish graph membership from the coverage population and build identity

**Where:** D-E1, lines 30–32; acceptance 1–2; tracker M68.1.

The governing rule requires a denominator from the complete declared set, while acceptance 1 and the tracker
explicitly preserve the authored coverage denominator. Those are different populations. In the preserved
`coverage-response.json`, the denominator is two, two non-node declarations are excluded, and `checked` is
incorrectly called absent. Preserving that reported denominator also preserves a defect: the graph declares
`checked`, `child` and `rootNode` as `fluxtion.framework=false` and `fluxtion.auditCapable=true`, and its
`fluxtion.authoredNodeCount` is three. `checked` uses the framework class `SinkPublisher` as an authored node.

The source confirms two corrections. `Scaffolding.isScaffolding` ignores the declared authorship fact and
classifies `checked` from its framework package prefix. `CoverageService.assess`, lines 85–87, subtracts the scored and
excluded authored sets from logged IDs. `GraphPairing.declaredNodeIds` already returns every declared ID.
`CoverageScope` deliberately excludes events, service interfaces and demonstrably silent nodes from its
authored population. The membership question needs the complete graph; the coverage ratio does not.

There is a second boundary to make explicit here: matching node names does not establish that two files came
from the same build. `GraphPairing` uses a greater-than-one-half overlap policy and allows a graph when there
are no logged IDs because it cannot judge. Those are compatibility/retention decisions, not build provenance.
A shared verdict must not turn that policy boolean into a proven relationship.

**Required correction:** name the two sets and their bases separately. Prefer valid declared authorship
when `GraphVocabulary.trustedForNodeFacts()` permits it, consistent with `NodeLogging`'s treatment of audit
capability. Define the fallback for absent, invalid or untrusted facts without calling inference a declaration.
Derive missing IDs from the full declared graph independently of that classification. Preserve the meaning
of authored, eligible coverage, not the current erroneous count, and disclose exclusions. State that
membership agreement is not proof of build identity, and that no logged IDs supplies no pairing evidence.

The accepted tool-agreement spec already requires factual wording without declaring which artefact is
correct (TA-2 / D-I3a). Remove the unsupported build-provenance inference from the warning as a current
conformance correction, not merely a future M68 enhancement.

**Acceptance to add:** the committed graph with a labelled constructed log covering its three declared
authored nodes produces `declared=3`, `covered=3`, `ratio=1.0`, with no false `checked` warning. A separately
constructed log from a declared framework node, such as `serviceRegistry`, must not create an out-of-topology
warning or add that framework node to authored coverage. A foreign ID still warns; changing scaffolding
visibility changes neither fact; no-log and
zero-eligible-node cases disclose that no meaningful comparison/ratio was established. Preserve the existing
foreign-graph negative control. Use separate mutations for ignoring declared authorship and for restoring
authored-only membership. Also check that hiding scaffolding and deriving an authored subgraph keep `checked`
visible, since those surfaces share the classification.

**Basis:** source and committed response inspection; existing coverage and pairing tests re-run. The recovery
scenario itself was not re-run during this review.

## EI-2 · Medium — the named packet is not yet a replayable acceptance fixture

**Where:** acceptance 1, 8–9; M68.1–M68.3.

The committed recovery directory contains the GraphML, coverage response, screenshot, exported PDF, scenario
checker and result summaries. It does not contain `exchange/run1.log` or `run2.log`, which the subject report
names as its inputs. `run1.txt` and `run2.txt` are scenario-check outputs, not those audit files. The narrative
also abbreviates graph/report calls; it is not an executable replay of the export with its full view state.

A reviewer on another machine can inspect the reported failures but cannot feed this packet's original log
through `CoverageService`, reproduce its framing, or regenerate its chart/report from the committed files.
The PDF demonstrates an omission; it does not carry all the inputs needed to test the correction.
This does not prevent M68.1's constructed regression described above.

**Required correction:** preserve public-safe raw input and exact graph/report requests, with the relevant
filter, window/pin, series and focus state and file fingerprints. If original inputs cannot be published,
say so and use a labelled constructed regression case. Do not call that case a replay of the session.
Do not copy authenticated startup logs to fill this gap.

On the response pass I checked that both original `exchange/run1.log` and `run2.log`, plus
`exchange/transport.jsonl`, still exist under `/private/tmp/spring-g14-canvas-recovery-2026-09-24/`.
Their contents were not revalidated for publication on this pass. Prefer preserving those originals after
inspection; the placeholder graph alone cannot establish that every transcript field is safe to publish.

Define “same inputs” for the chart/series comparison to include the selected log revision, expression,
record/filter scope and chart window. A series point outside a deliberately pinned window is not by itself
proof that the chart's no-data result is false.

**Acceptance to add:** a fresh checkout can replay the named fixture without private temporary directories,
keys or source from another repository. Include a deliberately missing image and a false no-data result as
wrong-result witnesses. The regression must inspect generated content, alongside the visual acceptance.

**Basis:** committed packet inventory and report read. No new chart/export experiment was run.

## EI-3 · High — zero separators is not enough to establish broken framing

**Where:** D-E9; acceptance 9; M68.3.

The example is nine apparent records collapsed into one frame. The acceptance instead says “a file with record
blocks and no separators” without defining how a block is recognised or which verdict is justified.
Format 1 §1 allows an ordinary single-record file with no separators. §1a explicitly distinguishes an ordinary
EOF record in a static read from a pending record in follow mode. A missing separator alone cannot establish
that a writer failed, nor can a quiet live tail establish completion.

This boundary has already caused a release regression. The existing conformance and follow tests preserve
ordinary export tails and wait for a complete separator under follow. M68 must not undo those rules.
Text resembling a header inside a producer's value also cannot automatically establish another event.

**Required correction:** define the observable condition and the strength of the diagnostic. Where repeated
record-looking content is ambiguous, report suspected collapsed framing with its basis, not a proved event
count or a fabricated split into records. Preserve the raw frame and the static/follow distinction.

**Acceptance to add:** the preserved malformed input is diagnosed; a valid one-record file is not called
malformed; ordinary unterminated export tails remain visible on static open; a pending live record and a
separator arriving in pieces remain pending; header-like payload text is not promoted to proof of more events.
Exercise the actual reader backends affected, with a mutation disabling the diagnostic.

The joint owner also needs a concrete producer entry. The recovery report identifies the standalone
`FluxtionMain.java` log sink, not a Mongoose export. Link the task covering that writer and its runbook/template,
in addition to the broader audit-format proposal. Closing only a Mongoose delivery path would not fix this
particular experiment. The analyser can diagnose on reading; it cannot promise feedback at write time unless
the producer has its own check.

**Basis:** published Format 1 §1/§1a, source and evidence read; `FormatConformanceTest` and `FollowAppendTest`
re-run, including the ordinary EOF and pending-tail cases. No new malformed-input probe was run.

## EI-4 · Medium — define the scope and state guarantee of “refused whole”

**Where:** D-E3, lines 40–44; acceptance 3–4; M68.4.

The rule explicitly becomes general, but acceptance only covers a rolled set plus a graph and one topology
parameter. Existing behaviour includes both silent omission and intentional partial success with warnings.
For example, `ActionExecutor.doOpen` returns immediately for `logs`, dropping a simultaneous graph request;
its ordinary log-plus-graph path starts loading the log before attempting the graph. `doGraph` also has
per-item warning paths. These are different compatibility changes, not one branch-condition correction.

Returning an error after opening a log does not make the request all-or-nothing. Before parameter validation
for a view-changing verb, `ActionExecutor.render`, lines 118–122, can clear a spotlight; an unknown verb does
not meet that condition. The proposal does not say whether this is allowed
on a refused request, or whether “whole” requires preserving all pre-request view/session state.

**Required correction:** enumerate the verbs/combinations covered now, or explicitly commit to auditing the
whole action surface. Define what a refusal preserves and when an asynchronous operation is accepted versus
successfully applied. Name how load failure and supersession prevent a late partial result. This need not
dictate an implementation; it must make the observable contract testable.

**Acceptance to add:** valid log plus invalid graph, invalid log plus valid graph, and a close/new-open while
the load is pending. Check final session and canvas state as well as the reply. Also test an invalid later
parameter after a valid earlier one on a covered synchronous verb. A fail-after-first-effect mutation must
fail. If explicitly reported per-item partial success remains supported anywhere, list the exception rather
than contradicting the universal rule.

**Basis:** production control flow read, not a reproduced UI transaction failure.

## EI-5 · Medium — existing freshness detection is not a content-identity guarantee

**Where:** D-E6, lines 60–62; acceptance 6; M68.5.

“Detection already exists; what is missing is the action” overstates what is available for this contract.
`FileObservation` records size, modification time and filesystem file key. Its reply deliberately says
`unchanged-metadata`, with the qualification that unchanged metadata does not prove identical bytes.
Metadata may be unchanged after an in-place rewrite, and some filesystems do not provide a file key.

`MainFrame.pollFollow` captures metadata, appends, and reloads when the store reports shrink/rotation. Thus the
work is not simply turning an existing proven identity verdict into an announcement. Legitimate append also
changes metadata and content, and must remain distinct from replacement. “Before any further content” needs
an observation boundary rather than implying that a desktop reader can know about a write before observing it.
Concretely, `HeapLogStore.appendFrom` returns zero on equal-length content before updating the store; the
follow poll has no replacement announcement on that path. A same-length in-place replacement is therefore
an explicit acceptance case. This conclusion comes from source inspection, not a new filesystem trial.

**Required correction:** define identity and the guarantee: what establishes replacement, what remains
unknown, how ordinary append differs, and when an observed change invalidates or labels the loaded snapshot.
Retain the distinction between an unchanged metadata observation and verified unchanged content. Specify
whether operations are suspended pending reopen or may inspect an explicitly labelled prior snapshot.

**Acceptance to add:** same-length replacement, truncation, ordinary append, missing/unavailable identity,
and an in-place rewrite with restored metadata. The last must either be detected by the chosen mechanism or
remain explicitly unverified; it must not be called proven unchanged. Exercise context, visible state and
export at the chosen observation boundary. This does not require hashing the entire file on every request.

**Basis:** `FileObservation` and the follow poll read; no filesystem-race or performance trial run.

## Owner decisions and smaller corrections

- **Q1 recommendation:** retain the existing distinction between warning on an explicitly opened graph and
  the policy for automatically retained graph state. Do not forbid opening mismatched artefacts for an
  investigation; qualify or refuse conclusions that require agreement. D-I3a already records an
  announce-not-forbid policy for deliberate graph opens, so a different choice is a compatibility change.
- **Q2 recommendation:** refuse newly unaddressable names for the first slice and preserve old names through
  an explicit compatible address. This remains the owner's choice. D-E5 and acceptance 5 currently choose
  refusal while Q2 still offers mapping; make the operative clauses conditional until that decision is made.
- **Q3 recommendation:** make executable output-content checks a standing export regression gate, with
  visual inspection when rendering changes. A full manual inspection on every unrelated release is a
  separate cost decision. Either choice must retain the repository's existing rule: a finding closes only
  with a committed cheap regression check and, at a runtime boundary, its wrong-result witness.
- Link the recovery packet directly from the spec and name tests/fixtures beside each slice as it closes.
  Preserve observed failures and unknowns rather than silently replacing the packet with corrected output.
- No new analyser application-execution responsibilities, guide verb, producer rewrite or LLM battery is
  needed to close these specification gaps.

## Verification and limits

Ran on the reviewed commit with Java 21:

```sh
JAVA_HOME=/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.8/Contents/Home \
  mvn -q -Dtest=CoverageServiceTest,CoverageScopeTest,GraphPairingTest,FormatConformanceTest,FollowAppendTest,ReportRendererTest,SpecLinksResolveTest test
```

| Suite | Tests | Failures/errors/skips |
|---|---:|---|
| CoverageServiceTest | 2 | 0/0/0 |
| CoverageScopeTest | 10 | 0/0/0 |
| GraphPairingTest | 8 | 0/0/0 |
| FormatConformanceTest | 28 | 0/0/0 |
| FollowAppendTest | 4 | 0/0/0 |
| ReportRendererTest | 10 | 0/0/0 |
| SpecLinksResolveTest | 3 | 0/0/0 |
| **Total** | **65** | **0/0/0** |

An initial attempt to use `./mvnw` failed because this repository has no wrapper; the command above is the
actual successful gate. These existing tests establish the current baseline, not that the proposed behaviour
is implemented. No implementation or mutation was added. Display suites, new export render,
client acceptance, and cross-repository producer checks were not run for this review.

Before publishing this review, the repository's pre-commit full-suite gate was also run:
`JAVA_HOME=/Users/greg/Library/Java/JavaVirtualMachines/corretto-21.0.8/Contents/Home mvn -q test`.
Result: **1,876 tests, 0 failures, 0 errors, 62 skips**. The initial sandboxed run had 29
local-socket permission errors; the rerun with loopback access passed. The skips remain reported;
this is not a display-suite acceptance claim.

`git diff --check` passed; the untracked review was separately checked for trailing whitespace. The
public-content sweep over tracked and untracked files found no matches outside the two rule documents.
Publication branch: `review/m68-evidence-integrity-2026-09-24`. Only this review document is included;
the reviewed spec, tracker, source and evidence remain unchanged.

The response correction checked G's full report, extracted the graph's node and graph facts, and re-read
`Scaffolding`, `GraphVocabulary`, `NodeLogging`, the action precheck and the heap follow equal-length branch.
No production code changed and no new runtime claim is made. The test counts above are the previous runs
on the same production tree; they were not rerun for this prose correction.
