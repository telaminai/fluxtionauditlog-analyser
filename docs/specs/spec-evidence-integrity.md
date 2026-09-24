# Spec — evidence integrity: the instrument never says more than it established

**Status:** PROPOSED v2 2026-09-24, corrected after two reviews · **Milestone:** M68 · **Tracker:** [tracker.md](tracker.md) ▸ M68.
**Sibling of** [`spec-tool-agreement.md`](spec-tool-agreement.md): that spec says tools describing one application
must not contradict each other; this one says a single tool must not state a verdict it has not established.
**Distinct from** [the Mongoose audit format proposal](../proposals/mongoose-audit-format/README.md), which is about
how a producer writes and delivers its audit file. They meet at exactly one point, D-E9.

**Review record.** v1 was reviewed twice on 2026-09-24. The first review raised EI-1 to EI-5 and found a
contradiction in D-E1 and an outright error in acceptance 9 (`review/m68-evidence-integrity-2026-09-24`, first
at `ed06170c`, corrected at `2f321705`). Reviewer G confirmed all five against source and added D-E10, the
declared-fact rule, which changes the first correction and the acceptance figures
(`review/m68-evidence-integrity-response-G`, `7b8d9f51`). Both reviews now agree. This revision folds in
everything both required; the per-decision notes below say what changed and why.

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

**Membership and population are different questions with different bases, and must not share one set.**

- **Membership** — is this id part of the graph at all? — is answered against **every declared node**, and against
  nothing else. A logged id is reported as outside the topology only when the declared graph does not contain it.
- **Population** — which nodes should coverage score? — is answered against the **authored, audit-eligible** nodes,
  with every exclusion disclosed. Framework plumbing is not swept into the ratio to make a warning go away.

v1 said "the denominator must come from the complete declared set", which conflicted with acceptance 1 and would
have changed what coverage means. Both reviews rejected it. The two questions are now separated at their source.

**Membership agreement is not build identity.** Matching names do not establish that a graph and a log came from
one build. Graph retention uses an overlap policy and deliberately allows a graph when there are no logged ids,
because it cannot judge. Those are retention decisions. No verdict may present either as a proven relationship,
and no logged ids supplies no pairing evidence at all.

## D-E2 · One verdict, every surface

A verdict reads identically in `context`, the human panel, the status bar and an exported report. Where a surface
cannot show the whole verdict it shows the part it can and names what it omitted. Divergence between surfaces is a
defect of the same severity as a wrong verdict, because a reader who sees two of them cannot tell which to believe.

## D-E3 · A request is honoured whole or refused whole

A call that carries several parameters either applies all of them or refuses, naming what it would have ignored.
No reply reports success while discarding part of what it was asked. This is already the canvas-write rule; it
becomes general.

**Scope, because "general" was not testable.** M68.4 covers, by name: `open` with a rolled set and a graph, `open`
with a log and a graph, and the topology record parameter. Every other verb combination is audited and either
brought under the rule or listed here as a deliberate exception with its reason. A per-item partial success that
is explicitly reported remains permitted only where it appears on that list.

**What a refusal preserves.** A refused request leaves pre-request view and session state as it was. Today a
view-changing verb puts the spotlight out before its parameters are validated, so a refused call has already
destroyed context the caller was relying on. That ordering is a defect under this rule. An unknown verb does not
reach it.

**Accepted is not applied.** A reply that accepts an asynchronous load says so, and the verdict that depends on
the load is reported when the load lands, never guessed at the first echo. A later failure or a supersession must
not leave a partial result standing as though it succeeded.

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

**So this decision must define, not assume:** what establishes replacement; what remains unknown and is labelled
unknown; how legitimate append stays distinct from replacement; and the observation boundary, because a desktop
reader cannot know about a write before it observes one. Whether pending operations are suspended until reopen, or
may read an explicitly labelled prior snapshot, is part of the definition.

**The case that currently produces nothing.** The follow poll appends when the store reports zero or more new
records and reloads only when it reports a shrink or rotation. A same-length in-place replacement returns zero and
is therefore neither appended nor reloaded, and nothing is announced on any surface. That case is acceptance, not
an aside.

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

**The observable condition, and the strength of the diagnostic.** v1 said "a file with record blocks and no
separators", which the published format contract makes wrong: §1 states that non-blank text anywhere is a record,
so a file holding content and no separator is one legal record; §1a states that an unterminated final record which
does not look like a marker is an ordinary record in a static read and a pending record in a live read. A missing
separator alone establishes nothing.

Where repeated record-looking content suggests collapsed framing, the analyser reports **suspected** collapsed
framing and names its basis. It never reports an inferred record count, and it never fabricates a split into
records. §1a already forbids the underlying mistake for the marker: audit records carry a producer's own
`toString` output, which may contain any text, including a line that reads exactly like a key, so a record is
recognised by what it contains in full and never by what it mentions. Promoting header-like payload text to proof
of another event is the same defect, and it was already paid for once with a shipped fabricated verdict. The raw
frame is preserved, and the static and follow distinctions are preserved.

**Which producer.** The writer at fault in this incident is the starter template's own main class log sink, owned
by the compiler and the playground, not a Mongoose export. The audit format proposal's scope is Mongoose, the
playground, its plugin repo and this one. Closing only a Mongoose delivery path would not have prevented this
file. The analyser's part is to state the framing verdict prominently wherever such a file is opened; the
producer's part is a write-time check in each writer, and needs an entry against the starter as well as against
the audit format work. Neither half alone closes it.

## D-E10 · A declared fact outranks an inference, and an inference says it is one

Where a producer declares a fact about its own output, the instrument reads the declaration. Inference is a
fallback for when the fact is absent, invalid or untrusted, and a verdict resting on inference never presents
itself as declared.

**This is the root cause of the incident, and it was missed by v1 and by the first review.** The preserved graph
declares `fluxtion.framework` and `fluxtion.auditCapable` on every node. It declares the sink node as
`framework=false`, `auditCapable=true`, and its graph-level authored count agrees with the three nodes so marked.
The analyser never reads the authorship fact: it classifies from a hardcoded package prefix applied to the class
name, and that node's class is a framework class the developer used as a node and named. The heuristic therefore
overrode an explicit declaration that said the opposite, the node fell out of the coverage scope, and a
provenance conclusion was manufactured from its apparent absence.

The rule already exists one class away. Audit capability is read from the graph in preference to inference, under
a trust gate for node facts, and a node is dropped as silent by construction when the graph declared that it
cannot log. Authorship is the one classification that infers where the graph declares.

**So:** prefer declared authorship when the node-fact trust gate permits it; define the fallback for absent,
invalid or untrusted facts without calling inference a declaration; and confirm the trusted-node-fact
documentation lists the authorship fact, which emitted graphs carry and that documentation omits.

## Conformance note: part of this is owed today, not proposed

The shipped warning states that a graph is "probably from a different build, which makes every other figure here
suspect". The accepted tool-agreement spec already requires the analyser to state facts without declaring which
artefact is correct, and records that a fingerprint difference on an opened graph is announced, not forbidden. The
warning declares a build-provenance conclusion from a name-membership test. That is a conformance defect against
an accepted spec now, not an M68 enhancement, and M68.1 discharges it rather than introducing it.

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

## Acceptance

1. **Coverage and membership, from declared facts.** The committed recovery graph with a labelled constructed log
   covering its three declared authored nodes reports `declared=3`, `covered=3`, `ratio=1.0`, with no
   out-of-topology warning and no claim about which build the graph came from. A separately constructed log in
   which a **declared framework** node writes produces no out-of-topology warning and does not enter the authored
   coverage population. Exclusions stay disclosed. Changing scaffolding visibility changes neither fact, and
   hiding scaffolding or deriving an authored subgraph keeps the declared authored node visible, because those
   surfaces share the classification.
2. **Genuine mismatches still warn**, naming the ids and the artefacts compared, in wording that states the
   disagreement without declaring which artefact is correct. The existing foreign-graph negative control is
   preserved. A log with no node output, and a graph with no eligible nodes, each report that no comparison and
   no ratio was established.
3. A combined request naming a rolled set and a graph either loads both, with pairing reported, or refuses naming
   what it would have dropped. Tested on load completion, not on the first asynchronous echo. A refused request
   leaves the spotlight and session state as they were.
4. From a freshly loaded log with nothing selected, the topology record parameter selects the named record or
   refuses with what is missing. Echo and visible record agree.
5. A chart name that no spotlight grammar can address is handled by whichever branch of Q2 the owner chooses, with
   existing saved names still reachable and the compatibility choice recorded here.
6. Replacing an open log file with a new file of the same path, while following, produces an announcement at the
   next observation before any further content is served, and never presents the superseded content as current.
   The cases are: same-length replacement, truncation, ordinary append, an unavailable file identity, and an
   in-place rewrite with metadata restored. The last is either detected by the chosen mechanism or reported as
   explicitly unverified; it is never reported as proven unchanged.
7. A project whose settings live below its root resolves every shipped pointer, and a pointer that fails names the
   root that was tried.
8. Every requested report section renders or states why not; a chart that reports no data is contradicted by no
   successful series response over the same inputs as defined in D-E8; the artefact's content is inspected, not
   inferred from exit status.
9. **Framing, without over-claiming.** The preserved malformed input is diagnosed as suspected collapsed framing
   with its basis, reported with the same prominence to a person and to an assistant, at open and under follow. A
   valid single-record file is not called malformed. An ordinary unterminated export tail stays visible on a
   static open. A pending live record, including a separator arriving in pieces, stays pending. Header-like text
   inside a payload is never promoted to proof of another event. The reader backends actually affected are
   exercised, and the producer-side half is tracked against both the starter and the audit format work.

**Regression closure, per the repository rule.** Each acceptance above closes with a cheap static check, and each
runtime boundary with an executable check and a wrong-result witness. Acceptance 1 carries two separate mutations:
one that ignores declared authorship, and one that restores authored-only membership. Each must fail the suite on
its own, because a single mutation cannot show that both corrections are load-bearing.

## Slices

- **M68.1** the coverage and pairing verdict (acceptance 1, 2), including D-E10's declared-authorship correction —
  first, because it is what a held-out client was told, what blocks G14, and what the tool-agreement spec already
  requires. Not gated on the packet's missing inputs.
- **M68.2** report and chart rendering (acceptance 8) — second, same reason.
- **M68.3** the framing verdict's analyser half (acceptance 9), with the producer half filed against the starter
  and the audit format work.
- **M68.4** whole-or-refused requests and the declared parameter (acceptance 3, 4).
- **M68.5** identity change under follow, and the project root (acceptance 6, 7).
- **M68.6** the naming grammar (acceptance 5), last because it needs the Q2 decision.

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
