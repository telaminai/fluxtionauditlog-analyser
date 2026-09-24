# Handoff — M68.1, the coverage, pairing and scope verdicts

**For a session starting cold.** Everything below was verified in the session of 2026-09-24 against source at
`main`, the committed evidence packet, the published format contract and the actual CI log. Where something was
read rather than reproduced, it says so. **Read this before the spec**, because it tells you which parts of the
spec are settled and which were wrong twice.

**Spec:** [`../specs/spec-evidence-integrity.md`](../specs/spec-evidence-integrity.md) at v3, D-E1, D-E2 and D-E10,
acceptance 1 to 3. **Tracker:** [`../specs/tracker.md`](../specs/tracker.md) ▸ M68, and ▸ M45 for the restored item.
**Cleared to start** by review round 4. The four later slices are not cleared and are not your problem.

## The one-paragraph version

The analyser tells a user that a node is absent from a graph that declares it, and then concludes the graph is
probably from a different build. Both statements are false. The cause is that node authorship is decided by a
hardcoded package-prefix guess while the graph declares the fact per node, and that graph membership is tested
against the guess's output instead of against the declared nodes. Fix both, keep three verdicts separate that are
currently entangled, and make every verdict say what it looked at.

## What to build, in dependency order

1. **Read the declared authorship fact before the heuristic.** `Scaffolding.isScaffolding` classifies from
   `FRAMEWORK_PACKAGES` and `FRAMEWORK_TYPES` applied to the class name, and the file contains no reference to
   `fluxtion.framework`. The graph declares it. Precedent to copy, one class away: `NodeLogging` line 104 is headed
   *Ask the graph first* and reads `node.fact("fluxtion.auditCapable")` in preference to inference.
   **The fact is NODE-scoped.** Applied to event or exported-service vertices it understates coverage. Adopt it for
   nodes and leave the existing kind filter alone.
2. **Test membership against every declared node.** `CoverageService.assess` computes `outOfTopology` by removing
   `scope.loggable()` and `scope.excluded().keySet()` from the logged ids — both authored-derived. Use the full
   declared set; `GraphPairing.declaredNodeIds` already returns it. This must hold **independently of the
   classification**, including on a legacy graph that carries no declared fact.
3. **Separate the three states.** Membership, the coverage ratio and retention have different bases and none may be
   derived from another. A zero eligible population means no ratio, and membership can still be fully established.
4. **Close the downstream gate.** With no logged ids, `GraphPairing.of` returns that the graph applies, and feeding
   that to `CoveragePolicy.decide` with TRACE and an installed auditor returns `FULL` and claims the graph describes
   the log. Reproduced in round 3. **Changing the reason string does not fix this** — the claim is made downstream.
5. **Disclose observation scope on every verdict.** `MainFrame.pairingAgainst` samples the first 500 records;
   `CoverageService.assess` scans the whole log or the requested filter. A log whose only foreign id sits after
   record 500 yields an all-matched sample and a whole-log mismatch. Both are true at their scopes. Label the
   sampled verdict as sampled, and have the later full comparison qualify or replace it and say that it did.
6. **Remove the build-provenance conclusion** from the warning wording. State the disagreement and the artefacts;
   do not say which is correct.

## Numbers, and the trap in them

For the committed packet graph the honest figures are **3 declared, 3 covered, ratio 1.0**. The shipped response
says 2 and 2. Do **not** reach 3 by sweeping framework nodes into the population — that changes what coverage
means, and it is what two earlier spec revisions got wrong in opposite directions. You reach 3 because the graph
declares `checked`, `child` and `rootNode` as `framework=false, auditCapable=true`, and its
`fluxtion.authoredNodeCount` is 3. `checked`'s class is `com.telamin.fluxtion.runtime.output.SinkPublisher`, a
framework class the developer used as a node and named, which is exactly what the prefix guess cannot see.

`serviceRegistry` is declared `framework=true, auditCapable=true`. It stays out of the authored population and must
not produce an out-of-topology warning when it logs. It is the test that separates the two corrections.

**Do not assert that every count becomes 3.** The visible authored view, the eligible coverage population and the
raw graph are three different quantities that are confused today. The raw graph stays at 18.

## Surfaces that must be asserted

| Surface | Production path | Assertion |
|---|---|---|
| Hide scaffolding, authored subgraph | `TopologyFocus.visible`, `Scaffolding.authoredNodes` | `checked` stays visible |
| Coverage and report ledger | `CoverageService.ledger`, `MainFrame.coverageInput` | three eligible rows, `checked` covered, every exclusion carries a basis |
| Visible and hidden counts, topology echo | `TopologyPanel.viewNote`, `cursorState` | counts reconcile with the displayed set including focus; total stays 18 |
| Graph-open echo, discovery count | `MainFrame.openGraphml`, `GraphmlDiscovery.candidate` | raw, authored-view and eligible-coverage sizes labelled separately |
| Audit readiness | `TopologyPanel.auditReadiness` passes **fullTopology** to `AuditReadiness.of` | stays `ENABLED` over the full graph; hiding the auditor must not turn it off |
| Exported-service entry fallback | `EntryPointResolver.addSoleExportedService` calls `Scaffolding.isScaffolding(node)` directly | event and service policy preserved, including with no declared fact |

The last caller has a **node-only signature**, so it cannot apply a graph-level trust gate by itself. Changing
`authoredNodes(topology)` while leaving direct callers on the old classifier needs an explicit decision about which
facts those callers may consume. The risk there was read, not reproduced.

Framework nodes currently disappear before the ledger, so *every exclusion disclosed* needs a destination. Name a
summary or ledger representation for them. Do **not** expand the scored population in order to disclose them.

## Fixtures

**You do not need the original session logs.** The fixture is the committed graph at
`evidence/spring-g14-recovery-2026-09-24/MyProcessor.graphml` plus a **labelled constructed log**, which
is never described as a replay of the session. Both reviewers agree, and this is what unblocked the slice.

The originals do exist, under a temporary directory named in the round-3 review, and are **not** revalidated for
publication. They are needed for M68.2 and M68.3, not here. The placeholder names in the graph do not establish
that every transcript field is publishable — inspect before preserving, under hard rule 1.

## Regression closure, and why one mutation is not enough

Four separate mutations, each of which must fail the suite **on its own**:

1. ignore declared authorship;
2. restore authored-only membership;
3. derive no-ratio from no-membership;
4. let the retention policy reach a downstream claim.

A single mutation that fails for more than one reason does not show that each correction is load-bearing. Preserve
the existing foreign-graph negative control.

## The history, so you do not rediscover it

**This fix was specified, authorised, verified and then lost.** It is M45.4. Its named authority arrived on
2026-09-01; the analyser half was verified the same day against the real session graph on released 1.0.65 and
explicitly unparked. Its own text names the failure mode that later reached a client. On 2026-09-03 it was swept
into the completed tracker with the shipped detail, because a ☑ on one clause — *our half verified* — made an item
still marked ◧ read as finished. It left the delivery order and was never built. Three weeks later a held-out
client was told a declared node was absent from a graph that declares it.

Two consequences for you. The archived M45.4 entry is your **authority and measurement basis** — read it. And its
empty declared-only result was a measurement of **one** graph, not a theorem: the same adoption report's *ATTACK 2*
section says the registration windows remain and that the reason given for their unreachability was wrong. Consume
declared provenance under the trust policy; do not treat it as proof a producer cannot be wrong.

**The sweep rule this produced:** an item marked ◧ never leaves the live tracker, whatever ☑ marks appear inside its
text.

## Reviews to read, and what independence they had

None of the four rounds was independent, and earlier versions of the spec wrongly said they were.

| Round | Branch, commit |
|---|---|
| 1 | `review/m68-evidence-integrity-2026-09-24`, `ed06170c`, corrected `2f321705` |
| 2 | `review/m68-evidence-integrity-response-G`, `7b8d9f51` — written by the spec's author |
| 3 | `review/m68-v2-2026-09-24`, `d1e58bc9` — round 1's reviewer again |
| 4 | `review/m68-v3-tracker-2026-09-24`, `c583edf3` — the same reviewer again |

Their value is that each found errors the previous missed. Their agreement is not evidence. **An independent
reviewer for the implementation would be worth more than a fifth pass by either party.**

## A worked example of the failure mode, committed by the spec's own author

While correcting this spec I reconciled SG-2 and wrote that the CI evidence was *exactly what this item required*.
I had taken that from a claim copied into the tracker. Round 4 read the run. It has three jobs, two provisioning
and one customer-download, no generation job, `generationAttempted: false` for the hosted template, and the
build-run-export-stop evidence comes from the keyless bundle. Acquisition, setup and validation are closed; hosted
generation and run are not. The item was wrong in both directions inside four days.

Keep that in view while you implement, because it is the same mistake in a different costume: a verdict adopted
from a summary instead of the thing it summarises.

## Gates before you commit

- `mvn test` green. The baseline is **1,877 tests, 0 failures, 0 errors, 62 skips**, measured on `main` after
  1.19.1 was released on 2026-09-24. It was 1,876 before that release, so check the count rather than trusting this
  line. The skips are expected; one needs a runtime jar environment variable.
- The public-content sweep in the exact form given in `CLAUDE.md` hard rule 1, which must print nothing.
- `git config user.email` must print the personal address.
- `SpecLinksResolveTest` if you touch anything under `docs/specs/`.
- `CHANGELOG.md` gets a line under `## [Unreleased]` in the same commit, because this is user-visible: a warning
  disappears and coverage figures change.
- Swing is not unit-tested here. Verify the UI surfaces by building and running the jar.

## What is explicitly not in this slice

Report and chart rendering; the framing diagnostic, which already exists and already overclaims; whole-or-refused
requests; freshness under follow; the naming grammar. The three owner questions are all still open, and none of
them gates this slice.
