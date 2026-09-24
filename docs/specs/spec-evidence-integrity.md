# Spec — evidence integrity: the instrument never says more than it established

**Status:** PROPOSED v3 2026-09-24, corrected after three review rounds · **Milestone:** M68 · **Tracker:** [tracker.md](tracker.md) ▸ M68.
**Sibling of** [`spec-tool-agreement.md`](spec-tool-agreement.md): that spec says tools describing one application
must not contradict each other; this one says a single tool must not state a verdict it has not established.
**Distinct from** [the Mongoose audit format proposal](../proposals/mongoose-audit-format/README.md), which is about
how a producer writes and delivers its audit file. They meet at exactly one point, D-E9.

**Review record, and what independence these rounds actually had.** None of the three rounds was a fully
independent review, and earlier versions of this spec and its tracker entry wrongly called them independent.

| Round | Branch, commit | Who, and their conflict |
|---|---|---|
| Round 1 | `review/m68-evidence-integrity-2026-09-24`, `ed06170c`, corrected `2f321705` | Raised EI-1 to EI-5. Also prepared the client evidence the spec cites. |
| Round 2 | `review/m68-evidence-integrity-response-G`, `7b8d9f51` | Reviewer G, who **wrote this spec**. Confirmed all five against source and added D-E10. |
| Round 3 | `review/m68-v2-2026-09-24`, `d1e58bc9` | The round-1 reviewer again, reviewing v2. Narrowed a conformance claim it had itself endorsed. |
| Round 4 | `review/m68-v3-tracker-2026-09-24`, `c583edf3` | The same reviewer again, on v3 and the sweep. Cleared M68.1 to start, and caught a false closure the spec author had just introduced. |

Each round checked source, the published contract and the committed artefacts, and each found errors the
previous one missed. That is the value they have. Agreement between them is **not** evidence, and this table
exists so no reader mistakes three passes by two conflicted parties for independent confirmation.

Round 3 raised R1 to R5, all folded in below. It also reproduced two defects in shipped code, which changes
this milestone's character: part of M68 is correcting false verdicts the product emits today.

Round 4 cleared **M68.1 to start** and left five conditions on the wider handoff, all folded in: the freshness
policy this spec invented could not establish its own claim (V3-1); the disposition table is deferred rather than
closed (V3-4); the M45.4 safety argument was a measurement of one graph and its own report's rebuttal had to travel
with it (V3-5); three archived obligations still lacked a live home (V3-3); and **the SG-2 closure written while
correcting this spec was itself over-read** (V3-2), which is the same defect the milestone exists to fix, committed
in the act of specifying the fix. That last one is the clearest evidence in this file that D-E1 is worth having:
the author of the rule broke it within hours, by trusting a claim copied out of a CI run instead of reading the run.

## Why now

Three releases have carried evidence work, and on 2026-09-24 a held-out client was still told something untrue.
In the recovery packet the coverage response calls a node absent from the topology and suggests a different build,
while the preserved GraphML declares that node and the window status agrees all three logged nodes are declared.
The subject reported what the instrument told it. That is the exact failure this product exists to prevent, met by
the first outsider to look, and it is now holding a release gate rather than a review paragraph.

**The client wrote the false verdict down as fact.** Its report states that the sink node appears in the log but is
not declared in the loaded graph. The graph declares it explicitly, as an authored node that can write audit
output. The same paragraph carries one true producer defect, a missing record separator, and one false analyser
verdict, and the reader had no way to tell them apart. An assistant downstream cannot avoid repeating a confident
falsehood it was handed. That is the whole argument for this milestone.

The same packet records a chart that says there is no data under the current filter while the series response
yields a point, and a requested topology illustration that is simply absent from an export that reported success.

These are not new findings. They have been open as DX-02 to DX-05, WS-1 and WS-2 since 2026-09-19 and have been
first in the delivery order for five releases. The framing defect was recorded earlier still: revision 7 of the
audit format proposal states that a writer built to an earlier revision of its spec emits no separators and that
the analyser reads the result as one record. The analyser half of that was funded and shipped as format 1.1 §1a.
The producer half was not, and that is what the client then met.

## D-E1 · The governing rule

**Every verdict names what established it, and anything not established says so rather than defaulting to a
confident answer.** A verdict with no evidence behind it is reported as unknown, never as a negative. This is the
same rule the stream-end marker already applies to completeness, where a file that makes no claim reads as unknown
and never as complete; D-E1 generalises it from the log's shape to every statement the instrument makes.

Two consequences that decide most of the cases below. Absence of a record is not absence of execution, and absence
of a declaration is not absence of the thing declared.

### Three questions, three bases, never one set

v1 required the denominator to come from the complete declared set, which conflicted with its own acceptance. v2
split membership from population, and round 3 found that still conflated two more things. There are **three**
independent states, and no surface may derive one from another.

| Question | Basis | When it cannot be answered |
|---|---|---|
| **Membership** — is this id in the graph? | Every declared node vertex, and nothing else | Only when there is no graph |
| **Coverage ratio** — what share of eligible nodes reported? | The authored, audit-eligible population, exclusions disclosed | Empty eligible population: **no ratio**, which is not no membership |
| **Retention** — should this graph stay open against this log? | An overlap policy, deliberately permissive | Never reported as evidence of anything |

**The case that proves they are separate.** Restrict the graph to a single declared framework node and log that
node. The eligible coverage population is empty, so there is no ratio, while membership is fully established at
one of one. A blanket "no comparison was established" would suppress a result the instrument actually has. A
foreign id must stay reportable in the same situation.

**Retention is a policy, and policy must not leak into a claim.** With no logged ids the pairing check returns
that the graph applies, because it cannot judge; fed that, the coverage policy returns its fullest verdict and
states that the graph describes the log. Both were reproduced in round 3. Changing the human-readable reason does
not close it: the downstream conclusion is where the false claim is made, so the gate is what has to change.

**Membership agreement is not build identity.** Matching names do not establish that a graph and a log came from
one build, and no logged ids supplies no pairing evidence at all.
## D-E2 · One verdict, every surface, at a stated scope

A verdict reads identically in `context`, the human panel, the status bar and an exported report **for the same
graph revision, the same log revision and the same observation scope**. Where a surface cannot show the whole
verdict it shows the part it can and names what it omitted. Divergence beyond that is a defect of the same
severity as a wrong verdict, because a reader who sees two of them cannot tell which to believe.

**Scope is part of the verdict, not a footnote.** Two surfaces observe different amounts of the same log today:
the pairing shown on open samples the first five hundred records, while coverage scans the whole log or the
requested filter. A constructed log whose only foreign id sits after record five hundred therefore produces an
all-matched sample and a whole-log out-of-topology finding. **Both are true at their stated scopes.** The defect
is that neither states its scope, so the reader sees a contradiction.

So every verdict discloses the scope and basis it was computed over; a sampled verdict is labelled as sampled and
is qualified or replaced by a later full comparison, which says that it did so. v1's rule, read literally, would
have forced a valid sample to claim it had inspected the whole file. That is the same defect in the opposite
direction, and it is forbidden. Equally, the threshold at which a graph is retained is not changed merely to make
two surfaces produce the same sentence: retained and every-observed-id-declared are different statements.
## D-E3 · A request is honoured whole or refused whole

A call that carries several parameters either applies all of them or refuses, naming what it would have ignored.
No reply reports success while discarding part of what it was asked. This is already the canvas-write rule; it
becomes general.

**Scope, because "general" was not testable.** M68.4 covers, by name: `open` with a rolled set and a graph, `open`
with a log and a graph, and the topology record parameter. Every other verb combination is audited during M68.4,
and each is either brought under the rule or listed here with its reason. **An empty exception list is not a claim
that there are no exceptions** — until the audit is done and its result written here, the list is simply unwritten,
and a per-item partial success found elsewhere is an unresolved finding rather than a permitted case.

**The disposition table is M68.4's first deliverable, before any behaviour changes.** Round 4 named existing
contracts the general rule would prohibit, and at least one is deliberately tested: a report keeps its valid
sections beside a rejected one, asserted by name in `ReportVerbTest`. Graph per-item warnings, and a saved analysis
that retains earlier effects when a later step fails, are the same shape. **An audit during the slice is not
permission to pick different semantics case by case while implementing.** Each of these gets a row, and any change
to a tested behaviour needs a recorded decision:

report siblings beside a rejected section · graph series, markers and bands · source-root mixed additions and
removals · `open` precedence and format combinations · multi-field topology calls · unknown or misspelled
parameters, including one reported after execution · saved-analysis steps · a parameter silently ignored · a
parameter honoured with a warning · an early success followed by a later failure · a parameter whose effect depends
on state another established in the same call.

**What a refusal preserves.** A refused request leaves pre-request view and session state as it was. Today a
view-changing verb puts the spotlight out before its parameters are validated, so a refused call has already
destroyed context the caller was relying on. That ordering is a defect under this rule. An unknown verb does not
reach it.

**Accepted is not applied.** A reply that accepts an asynchronous load says so, and the verdict that depends on
the load is reported when the load lands, never guessed at the first echo. Name the state after an asynchronous
failure and after a supersession — a close, or a new open while a load is pending — so a late partial result
cannot stand as though it succeeded.
## D-E4 · A declared parameter acts or refuses

A parameter published in the manifest never silently does nothing. Where state is required before it can act, it
either establishes that state or refuses and says what is missing. The echo and the visible result always agree.

## D-E5 · Names and addresses share one grammar

Anything the product lets a user or an assistant name must be addressable by every surface that can point at it.
Where a grammar cannot express a name, creation refuses at the point of naming rather than producing an object
that later cannot be referenced. Already-saved names stay reachable; a compatibility path is chosen explicitly and
recorded.

**Conditional on Q2.** The refusal clause above is one of the two options Q2 still offers. v1 chose refusal in the
decision while leaving the choice open in the question, which pre-empted the owner. Until Q2 is answered, read
this decision as: creation refuses **or** the name is accepted and mapped to a stable address, and whichever is
chosen is recorded here with its migration path for saved names.

## D-E6 · An input that changed underneath is announced, not served stale

Identity, not path, decides whether the thing open is the thing on disk. When identity changes the reader is told
at the next observation, before it is served anything further, and no view continues to present superseded content
as current.

**What exists, stated honestly.** v1 said detection already exists and only the action is missing. That overstated
it. What exists is a metadata observation — size, modification time and filesystem file key — whose own reported
basis says that unchanged metadata does not prove identical bytes. It detects change. It does not distinguish
replacement from ordinary append, and on some filesystems there is no file key at all.

**The policy, supplied rather than deferred.** Round 3 was right that listing cases and saying the decision must
define them is a checklist, not a definition. The default policy is:

- **Append** — the file key is unchanged, the length has grown, and **every byte up to the previous end is
  verified unchanged**. v3 proposed a boundary re-read plus a bounded prefix sample; round 4 showed that cannot
  establish the claim. Preserve the key, rewrite a byte in an old middle record outside both samples, then append a
  complete record: both samples match, the length grows, and already-indexed rows now describe different content.
  A bounded match is therefore **not** append. Either the whole old prefix is compared, and the cost is stated, or
  the state is unverified.
- **Replacement** — the file key changed, or the length shrank, or the verified prefix comparison fails.
  Announced before anything further is served.
- **Unverified — the catch-all, not a fourth case.** Anything that is not established append and not established
  replacement is unverified: unchanged metadata, an unavailable file key, a prefix comparison that was not
  performed or could not complete, an I/O failure, a missing or unreadable file, and a change observed during
  verification. The decision table has **no hole**: same key, same length, changed modification time and a matching
  prefix is unverified, because a same-length in-place rewrite produces exactly that. **Unverified is never
  reported as proven unchanged.**

**What this costs in each store, measured rather than assumed.** The heap store already reads the entire file as
text on every poll and compares character lengths, returning immediately when they are equal, so comparing the
complete old prefix there costs **nothing extra** — the bytes are already in hand, and the append-only assumption
its own comment relies on is currently unverified. The mapped store reads row bytes from the channel on demand, so
retaining its old index is not retaining an immutable snapshot after an in-place rewrite, and it needs its own
answer. Verification happens **before** anything is published, including on a poll that adds no rows. A missing
file key must not be compared as an equal established identity; it is currently stringified, which would make two
absent keys look alike.

The observation boundary is the next poll or the next request that reads the file, because a desktop reader cannot
know about a write before it observes one. On replacement, operations are suspended until reopen. A prior snapshot
may be read while explicitly labelled superseded **only where its bytes are actually retained**; where the store
reads through to the file, reads are suspended instead.

**The case that currently produces nothing.** The follow poll appends when the store reports zero or more new
records and reloads only on a shrink or rotation, and the append path returns zero for equal-length content before
updating the store. A same-length in-place replacement is therefore neither appended nor reloaded, and nothing is
announced on any surface. That case is acceptance, not an aside.
## D-E7 · A pointer that cannot resolve says why

The root a project resolves against is recorded rather than inferred, and a pointer that fails reports the root it
tried. A panel of unresolved pointers must be diagnosable from what is on screen.

## D-E8 · Rendered evidence is checked, not assumed

An export reporting success establishes that a file was produced, not that its contents are right. Every requested
section either renders or carries an explicit statement of why it could not, and a rendered artefact whose content
contradicts the data it was built from is a failure of the export, not a cosmetic issue. Acceptance for this
decision is by inspection of the artefact, not by exit status.

**"The same inputs" is defined, because otherwise the contradiction test is unsound.** A chart's no-data result is
contradicted by a series response only when both are taken over the same selected log revision, the same
expression, the same record and filter scope, and the same chart window. A point outside a deliberately pinned
window is not a contradiction, and must not be reported as one.

## D-E9 · A producer learns its output is unreadable when it writes it

**This is the seam with the Mongoose audit format work and is owned jointly.** In the recovery packet an
application wrote nine record blocks and no separators; the analyser correctly read one record and the client
correctly reported that rather than inventing a chart. Both halves behaved well and the evidence was still
useless, because nothing told the writer at the moment of writing.

**The diagnostic already exists, and it already overclaims. This slice corrects it; it does not create it.**
Round 3 reproduced the defect: the producer diagnostic scans each indexed frame for a second raw occurrence of the
record-header key within a bounded span, counts those occurrences, and reports that the frame contains that many
records. A legal **one-record** file whose ordinary quoted value contains that key as literal text was reported as
"record 1 alone contains 2 records run together", while the reader correctly returned one record. That verdict is
computed on open, exposed to an assistant and shown on the human surface. It is the exact mistake §1a already
forbids for the marker: a record is recognised by what it contains in full, never by what it mentions.

**The observable predicate.** v1's "record blocks and no separators" is wrong on the published contract: §1 makes
non-blank text anywhere a record, so a file with content and no separator is one legal record, and §1a makes an
unterminated final record ordinary in a static read and pending in a live read. The conservative predicate is:
within one raw frame, at least two **physical lines whose trimmed content is exactly the record-header key**,
excluding comments and text known to lie inside a quoted scalar under the active encoding. The verdict names its
basis — the inspected span and the candidate locations — and is phrased as suspicion: repeated header-looking
lines inside one frame, separators may be missing, and this does not establish multiple events.

**Never an inferred record count.** Actual record counts and framing are unchanged by the diagnostic. An unquoted
multiline payload may remain genuinely ambiguous; the suspicion qualifier and the raw frame are retained.

**Bounded means not assessed, not clean.** The scan's bound is stated, and a frame beyond it is reported as not
assessed. Under follow, pending raw content is not yet in the index, so an index-only scan misses the whole case:
the pending-frame observation is supplied explicitly, without accepting the pending record as a record.

**Which producer.** The writer at fault in this incident is the starter template's own main class log sink, owned
by the compiler and the playground, not a Mongoose export. The audit format proposal's scope is Mongoose, the
playground, its plugin repo and this one. Closing only a Mongoose delivery path would not have prevented this
file. The producer's part is a write-time check in each writer, and needs an entry against the starter as well as
against the audit format work. Neither half alone closes it.
## D-E10 · A declared fact outranks an inference, and an inference says it is one

Where a producer declares a fact about its own output, the instrument reads the declaration. Inference is a
fallback for when the fact is absent, invalid or untrusted, and a verdict resting on inference never presents
itself as declared.

**This is the root cause of the incident.** The preserved graph declares the sink node as not-framework and
audit-capable, and its graph-level authored count agrees with the three nodes so marked. The analyser classifies
from a hardcoded package prefix applied to the class name instead, and that node's class is a framework class the
developer used as a node and named. The heuristic overrode an explicit declaration that said the opposite, the
node fell out of the coverage scope, and a provenance conclusion was manufactured from its apparent absence.

**This was already specified, authorised and parked. See the reconciliation section below.** It is M45.4, whose
named authority arrived on 2026-09-01 and whose analyser half was verified the same day against the real session
graph on released 1.0.65. D-E10 is not a new requirement. It is the one that was not implemented.

**The authority basis, which the version gate does not supply.** The consumption rule for this vocabulary requires
supported vocabulary **and a named authority for the key**, and records that emission alone was explicitly
insufficient because the key had been emitted incorrectly before. The node-fact trust gate is only a presence and
major-version check: it takes no key argument, validates no value and checks no producer authority. So D-E10 rests
on M45.4's recorded authority and verification, not on that gate.

**What the fact is, precisely.** It is **NODE-scoped**. Applied to event or exported-service vertices it would
understate coverage, which is the safe direction and still wrong. Adopt it for nodes and leave the existing kind
filter alone. M45.4's measurement found the declared-only set empty **on that one measured session graph**. That is evidence,
not a theorem: it does not establish that a declaration can never shrink another graph's population, and
acceptance 1 deliberately requires a declared framework node with an ordinary class name to leave the population.
The graph-level authored count is an independent cross-check rather than a substitute for per-node handling.

**The same adoption report carries a qualification that must travel with it.** Its *ATTACK 2* section states that
the registration windows are still there and that the reason given for their unreachability was wrong, narrowing
the assurance to the tested shapes (`../handoff/completed/response_compiler_1.0.65.txt`). A report cannot be cited
for its optimistic half while its own rebuttal disappears. Whether that window survives in the current toolchain is
not established here. M68.1 may consume declared provenance under the trust policy above; it must not treat that as
proof that a producer cannot be wrong, and what M45.4 verified was the **comparison** between declaration and
heuristic, not an implemented analyser consumer.

**The fallback, specified rather than delegated.** Where the key is absent, holds an invalid value, or the major
version is unsupported, the package heuristic is used, its basis is disclosed as inferred, and the existing kind
exclusions still apply. It is never presented as authoritative authored provenance. Per-node facts remain usable
in aggregated graphs, since aggregation merges edge facts and does not touch nodes.

**The fallback does not excuse a false membership verdict.** D-E1's membership test operates against every
declared node vertex independently of this classification, including on a legacy graph. Legacy coverage may still
exclude a framework-classed node heuristically, which is a disclosed fallback limitation. Saying its id is absent
from the graph is not.

**One caller needs its own decision.** The exported-service entry fallback calls the classifier directly with a
node-only signature, so it cannot apply a graph-level trust gate by itself. Changing the authored-set derivation
while leaving direct callers on the old classifier requires an explicit decision about which facts those callers
may consume. Its event and service policy is preserved, including when no declared fact exists.
## Conformance note, narrowed by round 3

The shipped warning states that a graph is "probably from a different build, which makes every other figure here
suspect". **That warning is false on this fixture for a reason that needs no spec interpretation at all: the graph
contains the id.** That is the whole justification for fixing it.

v2 additionally claimed the wording already breached the tool-agreement spec globally. Round 3 narrowed that, and
it is right. The state-facts-only clause sits in the context of discovering and opening same-named graph copies
whose fingerprints differ, and the announce-not-forbid rule concerns a deliberate graph open rather than every
retention decision on a later log open. The accompanying explanatory text and the existing pairing tests still use
different-build language for a negative overlap result. So extending factual wording to every membership and
coverage surface is a **sound new decision of this milestone**, not a rule that was already fully specified. The
earlier, broader claim was over-read, by both the reviewer who made it and the spec that adopted it.
## Fixtures

**M68.1 needs no original logs.** Its boundaries are exercised by the committed graph from the recovery packet
plus a **labelled constructed log**, which is not called a replay of the session. Both reviews agree, so M68.1 is
not gated on the packet's missing inputs.

**M68.2 and M68.3 do need the originals.** The committed packet holds the graph, the coverage response, the
screenshot, the exported PDF, the scenario checker and the summaries. It does not hold the audit logs the subject
report names as its inputs; the two text files with similar names are scenario-checker output. The originals still
exist locally, under a temporary directory recorded in the corrected review, and have **not** been revalidated for
publication. Preserve them after inspection under the public-repo rule; the placeholder names in the graph do not
establish that every field of a transcript is safe to publish. If a field cannot be published, say so and use a
labelled constructed case instead, and do not fill the gap with authenticated startup logs.

Every slice names its regression check beside it as it closes, and preserves the observed failure rather than
replacing the packet with corrected output.

## Surfaces that change, and what each must assert

Correcting the classification moves more than the two surfaces v2 named. Each row is acceptance, and none may be
satisfied by asserting that every count becomes three: the visible authored view, the eligible coverage population
and the raw graph are three different quantities that happen to be confused today.

| Surface | Must assert |
|---|---|
| Hide scaffolding, authored subgraph | The declared authored node stays visible |
| Coverage and report ledger | All three eligible rows present, the sink node covered, every exclusion carrying a basis |
| Visible and hidden counts, topology echo | Counts reconcile with the displayed set including focus; the total graph stays at eighteen |
| Graph-open echo, discovery count | Raw graph size, authored view size and eligible coverage size labelled separately |
| Audit readiness | Stays enabled, computed over the full graph; hiding the auditor must not turn readiness off |
| Exported-service entry fallback | Its event and service policy preserved, including with no declared fact |

**Every exclusion disclosed needs a destination.** The present ledger covers only the supplied authored population
and its capability and kind exclusions; framework nodes disappear before it. Name the summary or ledger
representation that carries framework exclusions, and do not expand the scored population in order to disclose
them.

## Acceptance

1. **Coverage and membership, from declared facts.** The committed recovery graph with a labelled constructed log
   covering its three declared authored nodes reports three declared, three covered, and a ratio of one, with no
   out-of-topology warning and no claim about which build the graph came from. A separately constructed log in
   which a **declared framework** node writes produces no out-of-topology warning and does not enter the authored
   coverage population. Declared-false overrides a framework package prefix; declared-true overrides an ordinary
   class name; an absent key, an invalid value and an unsupported major version each fall back to the heuristic
   with an inferred basis disclosed. Per-node facts still work in an aggregated graph. Every row of the surface
   table above is asserted.
2. **The three states are separate, tested on the public path.** A graph restricted to one declared framework node
   with a log in which it writes reports membership established at one of one **and** no ratio, not "no comparison
   established". A foreign id still warns when the eligible population is empty. With no logged ids, no pairing is
   established, the graph may still be retained by policy, and **no downstream verdict claims the graph describes
   the log** — asserted through the coverage, context and report paths, not only the pure comparison. Genuine
   mismatches warn, naming the ids and the artefacts compared, without declaring which artefact is correct. The
   existing foreign-graph negative control is preserved.
3. **One verdict at a stated scope.** A constructed log of more than five hundred records whose only foreign id
   lies in the last record: the sampled pairing and the whole-log coverage each disclose their scope, the sampled
   verdict is labelled sampled, and the later full comparison qualifies or replaces it and says so. A filtered
   scope and a retained partial match are asserted the same way. The retention threshold is unchanged.
4. A combined request naming a rolled set and a graph either loads both, with pairing reported, or refuses naming
   what it would have dropped. Tested on load completion, not on the first asynchronous echo. A refused request
   leaves the spotlight and session state as they were. Each enumerated disposition in D-E3 has a case, and the
   audit's exception list is written down before the slice closes.
5. From a freshly loaded log with nothing selected, the topology record parameter selects the named record or
   refuses with what is missing. Echo and visible record agree.
6. A chart name that no spotlight grammar can address is handled by whichever branch of Q2 the owner chooses, with
   existing saved names still reachable and the compatibility choice recorded here.
7. Replacing an open log file with a new file of the same path, while following, produces an announcement at the
   next observation before any further content is served, and never presents the superseded content as current.
   The cases are: same-length replacement, truncation, ordinary append, an unavailable file identity, an in-place
   rewrite with metadata restored, **a middle-record rewrite combined with a genuine append**, **same key and same
   length with a changed modification time**, a missing or unreadable file, and a change observed during
   verification. Each resolves to append, replacement or unverified per D-E6, and unverified is never reported as
   proven unchanged. Asserted on both store implementations, since only one of them holds the old bytes.
8. A project whose settings live below its root resolves every shipped pointer, and a pointer that fails names the
   root that was tried. **Partly overtaken by a release:** 1.19.1 (2026-09-24) anchored named project profiles at
   the project root rather than the settings directory, so the anchoring half of this acceptance has a landed fix
   with its own regression. What remains for M68.5 is the **diagnostic** half — a pointer that fails naming the root
   it tried, diagnosable from what is on screen — which that fix explicitly did not close.
9. Every requested report section renders or states why not; a chart that reports no data is contradicted by no
   successful series response over the same inputs as defined in D-E8; the artefact's content is inspected, not
   inferred from exit status.
10. **Framing, without over-claiming.** The existing producer diagnostic is corrected, not duplicated, and its
    named tests are extended. A legal single-record file whose quoted value contains the header key as literal
    text is **not** reported as containing two records — this is the reproduced false verdict, and it is the
    wrong-result witness. The preserved malformed input is diagnosed as suspected collapsed framing, naming the
    inspected span and the candidate locations. An ordinary unterminated export tail stays visible on a static
    open. A pending live record, including a separator arriving in pieces, stays pending, and the pending frame is
    observable without being accepted as a record. A frame beyond the scan bound is reported as not assessed
    rather than clean. An ambiguous unquoted payload keeps its suspicion qualifier. Reported with the same
    prominence to a person and to an assistant, at open and under follow, on the reader backends affected.

**Regression closure, per the repository rule.** Each acceptance closes with a cheap static check, and each
runtime boundary with an executable check and a wrong-result witness. Acceptance 1 and 2 carry **separate**
mutations, each of which must fail the suite alone: ignoring declared authorship; restoring authored-only
membership; deriving no-ratio from no-membership; and allowing the retention policy to reach a downstream claim.
A single mutation that fails for more than one reason does not show that each correction is load-bearing.
## M45.4 reconciliation — this fix was specified, authorised, verified and then lost

**The most important finding in three review rounds is not a defect in the code.** D-E10 is M45.4, an item that
has existed since the M45 milestone, that waited on a named authority from upstream, that got one on 2026-09-01,
and whose analyser half was verified the same day against the real session graph on released 1.0.65 and explicitly
unparked. Its own text names the failure mode that later reached a client: a declared fact replaces a
package-prefix guess, which misreads an author's class in a framework-shaped package and a framework class outside
one. The measurement, the safe-direction analysis and the node-scoped implementation lesson were all recorded then.

It was never implemented. Three weeks later a held-out client was told a declared node was absent from a graph
that declares it.

**How it was lost is a tracker defect, and it is fixable.** The live M45 block is a stub that says shipped detail
was archived on 2026-09-03 and that open slices only follow. Four open slices follow. **M45.4 is not among them**:
it was swept into the completed tracker with the shipped detail, because the entry carries a ☑ on the sentence
recording that *our half* was verified and unparked, while the item as a whole is still marked partially complete
and its consumption was never built. A reader of the delivery order therefore cannot see it, and nobody looking
for work reads the completed tracker.

That is the same defect class this spec is about, applied to the tracker: a ☑ on one clause made the whole item
read as finished while the claim inside it was still open. The archive sweep had no check that an item marked
partial stays in the live file, so the instrument that records what is left to do stopped listing something that
was left to do.

**Therefore:** M68.1 completes M45.4 rather than duplicating it, cites its authority and verification as D-E10's
basis, and the tracker is corrected so the outstanding item appears where the delivery order is read.

## Slices

- **M68.1** the coverage, pairing and scope verdicts (acceptance 1, 2, 3), completing M45.4 — first, because it is
  what a held-out client was told, what blocks G14, and what was already authorised three weeks ago. Not gated on
  the packet's missing inputs.
  **Structural follow-up: [M44.4](spec-session-processor.md)** (§13, the single-state model). Four review rounds
  of M68.1 each closed a synchronisation defect between copies of one verdict. M44.4 gives the verdict one owner,
  the session processor, and every surface reads its snapshot, so D-E2 holds by construction and not by vigilance.
- **M68.2** report and chart rendering (acceptance 9) — second, same reason.
- **M68.3** the framing diagnostic's correction (acceptance 10), with the producer half filed against the starter
  and the audit format work. Correcting a shipped false verdict, not adding a diagnostic.
- **M68.4** whole-or-refused requests and the declared parameter (acceptance 4, 5), including the action-surface
  audit whose result is written into D-E3.
- **M68.5** identity change under follow, and the project root (acceptance 7, 8).
- **M68.6** the naming grammar (acceptance 6), last because it needs the Q2 decision.
## What this does not cover

The producer's encoding, persistence and delivery, which are the audit format proposal's and the starter's.
Agreement between separate tools, which is the tool agreement spec's. New rendering primitives, new verbs, or
anything that adds a surface rather than making an existing one honest. No new analyser execution
responsibilities, and no further client trial: these are contract and acceptance corrections.

## Open for the owner

- **Q1** whether a genuine mismatch warning should block an operation or only annotate it. *Both reviews
  recommend annotate, and note it is closer to settled than open: the tool-agreement spec already commits to
  announced-not-forbidden for a deliberately opened graph, so blocking would be a compatibility change against an
  accepted spec and needs its reason on the record.*
- **Q2** the compatibility choice for names that no grammar can address: refuse at creation, or accept and map.
  *D-E5 and acceptance 5 are conditional until this is answered. Recommended: refuse for the first slice, and
  keep saved names reachable through an explicit compatible address.*
- **Q3** whether D-E8's inspection requirement becomes a standing release gate or applies only to this milestone.
  *Recommended split: the executable content check becomes a standing export regression gate, and visual
  inspection applies when rendering changes. This is a cost decision, not a correctness one.*
