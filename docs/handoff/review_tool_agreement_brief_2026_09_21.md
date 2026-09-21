# Review — tool-agreement specification and implementation brief

Date: 2026-09-21. Reviewed `docs/specs/spec-tool-agreement.md`, its complete session report and
all committed GraphML fixtures. Spec/source baseline: `332701da`; rechecked at `c7fc4f0a`, whose
changes are the separate VI-1 documentation PR. This is a review, not an implementation or closure
report. No implementation, priority change or tracker housekeeping was performed for this review.

**Verdict: CONDITIONAL.** The objective and TA-1 root-cause analysis are sound. The whole brief is
not ready to execute unchanged: it reverses two fixture fingerprints, lacks inputs for the exact
reproductions it promises, and leaves the authority for supertype routes unspecified. TA-1 and
TA-3 can start; the findings below should be resolved before treating every remaining acceptance
as a complete implementation contract.

## Findings

### TB-1 — P1: the fixture fingerprints are reversed

Locations: `docs/specs/spec-tool-agreement.md:42`, `:43`, `:58`, `:108`.

Parsed the committed files as XML and independently hashed their bytes. File SHA-256 values and
node counts agree with the evidence table, but the embedded source fingerprints do not:

| Fixture | Nodes | Actual `fluxtion.sourceFingerprint` |
|---|---:|---|
| `MarketProcessor.src-round3.graphml` | 23 | `4ecd613398d340719081dfe71143287e96bb3ae7284819687853a0679914c9d9` |
| `MarketProcessor.target-stale.graphml` | 20 | `f6ae6f84383bd1c7f063f61db2cea505a26a4291226f02ff1124f654513dc576` |

The table, D3 and TA-2 assign these the other way around. An author copying the prose into test
expectations would reject truthful output. Correct those descriptions, not the evidence files.
The source copy still contains the additional nodes; this finding does not reverse which graph
is stale. Reproduce by reading each graph's `data` element keyed `fluxtion.sourceFingerprint`.

### TB-2 — P1: the exact A8 reproduction is not in the evidence packet

Locations: `docs/specs/spec-tool-agreement.md:46`, `:149`, `:157`, `:302`;
`docs/handoff/evidence/unguided-session-2026-09-21/session-report.md:313`.

The packet has the report and **three GraphML files**, not four GraphML files. It contains no
audit log. A8 records `delta(…)` and `{from, to}`, without the actual expression, bounds or
record values. Consequently the specified “A8 reproduction” at record 27 cannot be replayed
from this copy. Graph fixtures also cannot supply a growing-file or saved-flag reproduction.

Preserve the actual A8 inputs if available. Otherwise explicitly permit minimal additional
log/report fixtures, label them as constructed regression cases rather than a replay of A8,
and freeze the expected boundary semantics. Keep the requirement to use the committed graphs
for graph tests. Add mutation witnesses per behavior; do not require irrelevant graph data
in a formula or PDF test simply to satisfy the universal wording.

### TB-3 — P1: TA-9 needs an authoritative hierarchy input

Locations: `docs/specs/spec-tool-agreement.md:211`, `:216`;
`fixtures/desk-quote-supertype.graphml` under the cited evidence directory.

The committed graph supplies the FQNs for `MarketPrice` and `Quote` and their separate handler
edges. It does not encode `MarketPrice implements Quote`: there are no superclass/interface
metadata keys. A MarketPrice record plus that graph cannot distinguish a matching supertype
from an unrelated event class. The session's written observation is evidence of the defect,
but is not machine-readable authority for drawing a new route in arbitrary projects.

Name the hierarchy source, ownership and freshness rules: producer metadata or an explicitly
authorized source/bytecode representation, with a fixture containing the relationship. Do not
execute application classes or infer an edge from nodes appearing in the same audit cycle.
Exercise the shared union used by coverage and shading, with negatives for unrelated event
types, unavailable hierarchy and ambiguous simple names. The unavailable case must remain
explicitly unknown. This respects the existing authoritative-dispatch tracker item rather
than replacing it with an analyser guess.

### TB-4 — P1 if enabled: quiet time cannot establish record completeness

Locations: `docs/specs/spec-tool-agreement.md:180`, `:185`, `:190`.

“Never as complete” conflicts with the optional acceptance of a parseable trailing record after
a quiet interval. A valid YAML prefix can still receive additional fields after a pause. The
label `accepted-on-quiet` alone does not define whether counts, calculations and reports may
treat it as complete, nor how those results change when more bytes arrive.

For this delivery, choose pending-only behavior and omit the optional path. If provisional
display is wanted later, specify its separate semantics and add an append-after-quiet test.
This is a contract inconsistency identified by reasoning; I did not run a timed follower probe.

### TB-5 — P2: TA-5's delivery boundary exceeds “documentation plus a decision”

Locations: `docs/specs/spec-tool-agreement.md:169`, `:173`, `:176`, `:292`.

The brief scopes TA-5 as documentation and a recorded decision, but its acceptance requires a
shipped live-store source or follower and a starter containing the updated runbook. It is not
possible to close that acceptance merely by writing analyser documentation. The spec also
prohibits all new client sessions while TA-5 explicitly requires one; the brief's exception
should be reflected in the spec.

Separate the documentation/decision deliverable from implementation, vendoring and the final
spot-check. Name the selected route and its owner before starting dependent work; retain any
unshipped dependency as open. Run the one permitted client check only after that route and the
starter documentation are actually available. Do not reopen in-app discovery or app execution.

### TB-6 — P2: the progress measure needs an explicit denominator and split ownership

Locations: `docs/specs/spec-tool-agreement.md:49`, `:54`, `:58`, `:240`;
`docs/specs/tracker.md:12`.

The tracker still describes D1–D13, while the spec counts D1–D20. D1–D13 have no status column;
D14–D20 do. There are **12 solely analyser-owned rows** (D1, D2, D4–D6, D14–D20), plus the
analyser's detection responsibility within shared D3. Detecting D3 must not close the upstream
stale-resource build defect. TA-5, TA-7 and TA-8 also have required work without their own D rows.

Record all 20 baseline rows with status and regression evidence, split D3's detection/build
dispositions, and report the analyser's 13 responsibilities separately from the upstream count.
Track TA completion alongside that count: a zero row count is not by itself completion of all
work in this brief. Close a compound row only when all its referenced disagreements are closed.

### TB-7 — P2: upstream asks need reconciliation with the shipped starter

Locations: `docs/specs/spec-tool-agreement.md:266`, `:273`;
tracker sections “Spring getting-started guide” and SG-2;
`docs/handoff/report_sg1_release_2026_09_21.md`.

The blanket request to publish a matching starter and introduce `author.sh` is stale for the
standalone template: the existing release record establishes public 1.0.73 provisioning and
the shipped setup/validate/generate route. The hosted template's missing authoring files remain
SG-2, and new-node audit scaffolding remains feedback 6/D10. Copying the ask verbatim would
reopen completed publication work and invent another command surface.

File scoped upstream asks against the affected template/version and reuse SG-2/D10. Keep
“fix stubs before recommending the generator” intact. D7 also needs an accurate distinction:
the reported output already says “XML: valid”. Missing Java classes do not falsify that XML
result. Preserve the agreed XML-only validation tier and route the unmet expectation through
capability disclosure or the separately owned model/build tier.

## What holds and what can start

- TA-1's three caller sets match the current source: `MainFrame.java:3936` and
  `GraphmlDiscovery.java:139` use authored nodes; `session/node/Pairing.java:64` uses declared
  nodes. Use the shared fact-level set and retain the M35.1 foreign-graph negative control.
  No need to re-investigate the parser as the root cause.
- TA-3's backward-compatible kind and persistence requirement is implementable; verify all
  named surfaces, including PDF output, with constructed flag/report inputs.
- TA-2 can proceed once TB-1 is corrected, with both conflicting-copy and agreeing-copy cases.
- Use the existing `open` surface for TA-7 unless a new verb is separately approved; the repo
  currently pins the verb surface. Unsupported readers must not echo Follow as active.
- Predictions before fixes, committed regressions and mutation witnesses are appropriate.
- Preserve the existing ownership of D14–D20. Raising the staged-feedback item's priority is
  still an owner decision, not something this review approves.
- Archive only wholly completed tracker sections during implementation, as requested. This
  review has not moved them or touched the SG-1 evidence directory.

## Verification boundary

Read: entire spec and session report; tracker ownership; cited binary-reader rule; existing
SG-1 release record. Verified with commands: packet inventory, all evidence SHA-256 values,
GraphML node counts and fingerprints, absence of hierarchy metadata, and the three pairing
call sites. These are read-only checks. No new client session, application execution, UI
probe, mutation or runtime test was run for this review. The VI-1 PR's tests are not claimed
as tool-agreement acceptance evidence.

The subsequent reviewer message about repeated/interleaved comment-contract text in 1.0.73
stubs was outside the reviewed D1–D20 snapshot. After this review was written, the owner supplied
the scratch workspace: the packet was preserved and D21 was added explicitly as an upstream
intake in the VI-1 review reply. Source inspection confirms duplicated/interleaved comments;
generation was not independently re-run. Findings above refer to the original reviewed snapshot;
that later intake is not a claim to have resolved the review findings.
