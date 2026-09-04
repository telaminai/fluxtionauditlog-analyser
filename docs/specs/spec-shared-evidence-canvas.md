# Thesis — the shared evidence canvas

**Status:** PROPOSED FOR INDEPENDENT REVIEW, 2026-09-04

**Scope:** the direction shared by the Fluxtion builder/compiler, runtime and audit-log analyser.
This is a framing document: it does not make an unimplemented capability true. It states the product
thesis, assigns authority, and defines the tests future work must pass to remain part of that thesis.

**Builds on:** [`spec-authoring-modes.md`](spec-authoring-modes.md),
[`spec-builder-component-resolution.md`](spec-builder-component-resolution.md),
[`spec-trust-structure.md`](spec-trust-structure.md),
[`completed/spec-portable-context.md`](completed/spec-portable-context.md), and
[`spec-agent-brokered-dev-loop.md`](spec-agent-brokered-dev-loop.md).

## The thesis

The direction is strong because the analyser and compiler are becoming parts of a larger,
**evidence-carrying build loop** — not because the analyser is a second verifier.

Fluxtion should not merely help a model write an application, and the analyser should not merely show
the application's logs.

Together they should create a **shared evidence canvas**: a persistent workspace in which a human and
an LLM can see the same project context, manipulate the same investigation state, exchange findings,
and connect every consequential statement to the compiled model or recorded run that supports it.

The compiler externalises the application's mechanical decisions into deterministic artefacts. The
analyser externalises the collaboration into visible, durable, evidence-linked state. The model is no
longer the hidden author of an answer and the human is no longer reconstructing its context from a chat
transcript. They work on the same objects.

The product proposition is therefore:

> **Turn probabilistic AI from a narrator into a participant in a shared, persistent and checkable
> engineering workspace. Give mechanics to deterministic tools, judgement to humans and models, and
> make the evidence and context of every decision available to both.**

This is broader than verification and narrower than autonomy. The analyser is not a semantic oracle,
the compiler does not know the business goal, and a model proposal is not made true by writing it into
a project. The value is that these different kinds of knowledge stop arriving as indistinguishable
sentences in one transient conversation.

## One loop, two planes

This is the canonical product loop:

```text
human goal
    ↓  unmeasured interpretation
formal requirements
    ↓
builder-emitted catalogue + declared policy
    ↓
typed deterministic resolution
    ↓
Spring document
    ↓
compiler-owned graph and execution model
    ↓
generated processor + model fingerprint
    ↓
runtime audit evidence
    ↓
analyser queries, reductions and explicit expectation checks
    ↓
human/model correction
```

Its authority is deliberately asymmetric. The compiler decides and records the mechanical facts it
owns. The runtime records what the configured audit path observed. The analyser makes that evidence
queryable, reduces it, and checks it against explicit expectations; it does not independently decide
whether an application is correct. The human and model interpret the resulting evidence together and
propose the next change.

```text
                          COLLABORATIVE CONTEXT
               goal · policy · questions · views · findings
                   human  ⇄  shared canvas  ⇄  LLM
                                      │
                                      │ evidence references
                                      ▼
                             EXECUTABLE EVIDENCE
  catalogue → resolution → declaration → compiler model → processor → audit log
                                      │
                                      └──── correction → new model/run
```

The two planes have different truth rules.

### The executable-evidence plane

This plane contains facts a tool can decide or record:

- component capabilities and construction facts emitted at build;
- the resolver's selected components, exact bindings, constructibility, order and stable identities;
- the Spring document that serialises that result;
- compiler-owned graph relationships, propagation, dispatch, provenance and model identity;
- the generated processor;
- what a configured runtime audit path recorded during a run;
- comparisons against an explicit expected result.

These facts are decided once and carried forward. A renderer must not rediscover them. Where an
identity crosses an artefact boundary, the fingerprint or other explicit linkage crosses with it.

### The collaborative-context plane

This plane contains what the participants need in order to continue the work:

- the current goal and formal requirements;
- human-declared policy, environment and provenance;
- unresolved questions and genuine ambiguities;
- model hypotheses and suggested corrections;
- selected records, filters, topology scope and named graphs;
- findings, rationales, reports and evidence anchors;
- accepted decisions and the reasons they were accepted;
- pointers to vocabulary, runbooks, source, catalogues, generated artefacts and prior investigations.

The analyser is the meeting point because it already owns both human and agent projections of this
state. The project-context slice of its `context` payload is rendered for the person in the Project
panel, while filters, graphs, topology, flags and reports appear in their owning panels; its action model
lets an agent manipulate those same objects. Its project profile persists portable context, and its
exchange directory carries bounded artefacts between tools.

The target is not two integrations that happen to touch the same data. It is **one model with two
participants**. A graph created by an LLM must be the graph the human sees. A filter changed by the
human must be the filter the LLM receives from `context`. A finding must have one text and one set of
anchors wherever it is rendered. A project decision must survive the client or model session that
made it.

## The conversation must survive without becoming the authority

A chat transcript is useful history and poor project state. It is long, model-specific, hard to diff,
full of abandoned hypotheses, and gives a confident mistake the same shape as an accepted decision.
The durable asset is not every turn. It is the **promoted state of the conversation**.

The shared canvas therefore has three persistence layers:

| Layer | Purpose | Current or target |
|---|---|---|
| **Project profile** | typed, portable context and pointers: roots, processor, graphs, environments, vocabulary/runbooks and saved analyses; target additions include conventions, catalogue and the promoted-context pointer | substantially shipped, with the named additions proposed; grows only through reviewed key families |
| **Project-owned context document** | goals, open questions, decisions, hypotheses and evidence references that must survive a conversation | **proposed**; version-controlled content pointed to by the profile, not arbitrary prose embedded in it |
| **Exchange artefacts** | reports, screenshots and intermediate handoffs shared during an investigation | shipped exchange boundary; promotion into durable project context remains explicit |

The context document is not a hidden prompt and not an executable instruction file. It is a
human-readable, version-controlled project artefact. Its entries distinguish at least:

- **DECLARATION** — policy or intent supplied by a responsible person;
- **DECISION** — an accepted choice, its owner and rationale;
- **QUESTION** — unresolved, with the evidence needed to settle it;
- **HYPOTHESIS** — attributed testimony from a human or model, not established fact;
- **FINDING** — a claim with resolvable evidence anchors and scope;
- **RESULT** — the outcome of an explicit comparison or test.

The profile stores a project-relative pointer, not the document body. When the analyser exposes the
document to a model, it is a bounded, explicitly delimited **project-authored context** payload, never a
system instruction and never machine-derived fact merely because the analyser carried it. Prefer a
parsed projection of the typed entries; if the document cannot be parsed safely, return its pointer and
the refusal rather than silently treating arbitrary prose as structured state. This extends the shipped
vocabulary boundary without weakening the runbook rule: nothing in the document is executed.

Whether these are headings in one Markdown file, a small document family, or a typed project model is
an implementation decision for review. The requirement is that a fresh human or model can recover the
current objective, settled decisions, open questions and supporting artefacts without receiving the
prior chat transcript.

Raw transcripts may be retained as optional history. They do not override the promoted state and are
never the only place a decision lives.

## Who writes, and what a write means

“Both can write on the canvas” does not mean both have equal authority or that an agent receives an
unbounded file editor through the analyser.

| Participant | May contribute | Authority |
|---|---|---|
| **Compiler/build tools** | catalogue facts, resolution result, graph model, diagnostics, fingerprints, generated artefacts | authoritative for the fact they compute |
| **Runtime** | audit records at the configured level | evidence of what was recorded, not proof of completeness or origin |
| **Human** | goals, policy, provenance, accepted decisions, approvals and corrections | authoritative for declared intent and acceptance |
| **LLM** | hypotheses, proposed selections, graphs, flags, reports, questions and candidate corrections | testimony/proposal until tested or accepted |
| **Analyser** | typed projections, reductions, comparisons, refusals and evidence-linked presentation | authoritative only for what its inputs and declared rules establish |

Every durable write must be:

1. **Visible** — the other participant can see it through the same underlying model.
2. **Typed** — fact, evidence, declaration, hypothesis and decision are not rendered as synonyms.
3. **Attributed** — machine-derived, runtime-recorded, human-declared and model-proposed are distinct.
4. **Scoped** — to a project, model fingerprint, log fingerprint, filter and/or record anchors as
   appropriate.
5. **Diffable and reversible** — project state changes survive as reviewable artefacts, not hidden
   conversational memory.
6. **Fail-closed** — a stale or unresolved anchor is named; it is never silently rebound to something
   convenient.
7. **Bounded** — the analyser returns a useful projection and retrieval handles, not the entire log,
   repository and conversation in every prompt.

Model writes default to reversible curation or explicit proposals. Promotion to a human declaration,
accepted decision, source change or deployment remains a distinct act. Existing boundaries continue
to apply: the analyser executes no runbook, exposes no server-mutating verb, and does not silently edit
application source.

## The division of labour

### Builder/compiler: turn mechanics into artefacts

The build side should progressively remove mechanically decidable work from both participants:

- emit component catalogues rather than asking an integrator to inspect jars;
- resolve selection, wiring, cycles, construction order and identity before rendering;
- compile graph relationships and dispatch rather than asking an author to maintain call order;
- resolve auditor execution as one phase-aware plan rather than replaying registration history;
- emit diagnostics and the effective model fingerprint with every relevant artefact;
- preserve released wire payloads and carry new facts through negotiated side-band metadata.

The result is not “AI-generated software”. It is a deterministic build whose remaining human/model
input is explicit intent or policy.

### Analyser: make context and evidence jointly operable

The analyser should:

- consume compiler facts instead of inferring them from appearance or adjacency;
- expose the same project, selection, filter, graph, finding and report state to UI and agent clients;
- persist the context needed to resume or hand over an investigation;
- let either participant curate evidence through typed, bounded and reversible operations;
- distinguish evidence from testimony and an explicit comparison from general correctness;
- make every long-lived finding identify the model and run it concerns;
- refuse questions its graph, audit level, provenance or expectation cannot establish;
- hand candidate corrections to the repository workflow with their evidence attached;
- show whether a later build and run actually closed the recorded divergence.

The analyser is therefore not just downstream of the compiler. It is the collaboration surface that
makes compiler facts useful to people and models over time.

### Human and LLM: spend judgement only where mechanics stop

The human owns goals, policy, acceptance and consequential authority. The LLM helps translate goals,
identify ambiguity, propose explanations and corrections, and navigate more evidence than a person can
read directly.

That role is a target, not a measured conclusion. Goal-to-formal-requirement translation has not been
measured in this programme, and free-form component authoring modes 2 and 3 remain unmeasured. What is
measured is narrower: in one component-composition fixture, deterministic resolution replaced the
model's mechanical assembly work.

## The end-to-end contract

A complete loop should leave a reviewable chain:

1. **Orient** — a fresh participant reads the project context and can state the goal, declared policy,
   selected processor, open questions and available evidence.
2. **Declare** — formal requirements and unresolved choices are recorded separately.
3. **Resolve** — catalogue facts and policy produce either one typed plan or a named refusal.
4. **Compile** — the declaration becomes an authoritative model, generated processor and fingerprint.
5. **Run** — the audit artefact states which model produced it and what audit capability was active.
6. **Inspect** — human and LLM see and manipulate the same bounded views.
7. **Record** — findings contain rationale, scope and stable evidence anchors; hypotheses remain labelled.
8. **Decide** — a person accepts, rejects or reframes the proposed correction; the decision survives
   the conversation.
9. **Correct** — work is handed to the repository with the evidence and acceptance condition attached.
10. **Re-run** — a new model/run either satisfies the recorded expectation or leaves the divergence open.

The current programme has pinned several joints but not this whole chain. Catching a mutation proves
divergence detection; it does not prove correction. A context file proves persistence; it does not prove
that a cold participant understands it. Both need end-to-end tests.

## What is already real

| Capability | Status and boundary |
|---|---|
| project profile as portable context | shipped; typed facts, analyses and safe pointers, not a free-form transcript |
| project-context model shared by agent and Project panel | shipped and parity-gated; other `context` state is visible through its owning human panel rather than all appearing in Project |
| model/human manipulation of shared views | shipped through filter, graph, topology, goto, flag and report surfaces; persistence differs by object |
| bounded log retrieval and computation | shipped through read, aggregate, series and coverage; claims remain capability-scoped |
| exchange artefacts | shipped behind an explicit directory/permission boundary |
| compiler relationships, provenance, dispatch facts and model fingerprint | substantially shipped into GraphML/generated descriptors |
| deterministic component resolver | measured prototype on one fixture family; production builder implementation proposed |
| explicit expectation scoring | shipped with ten guards and reviewer-found false-pass regressions |
| generated-model identity in the audit log | unpinned |
| project-owned promoted conversation context | proposed in this thesis |
| detect → correct → re-run closure | unpinned |
| goal interpretation and free-form authoring | unmeasured |

This table is deliberately asymmetric. The thesis combines shipped parts, measured prototypes and open
work; it must not be presented as a description of the current product.

## What makes this distinctive

A normal coding assistant owns a conversation and borrows project files. A normal observability tool
owns telemetry and treats collaboration as comments outside the system. A normal compiler emits code
and forgets why the graph was declared.

The shared evidence canvas joins those three without asking one component to impersonate the others:

- deterministic artefacts carry mechanics;
- the analyser supplies a bounded common view over structure and execution;
- project context carries intent, decisions and unresolved work across sessions;
- humans and models collaborate through visible objects rather than private narration;
- correction is judged against a new recorded run, not against the model's claim that it fixed the
  problem.

The differentiator is not merely a frictionless loop. Friction can be copied. The differentiator is a
loop whose **joints are explicit, attributable and testable**, while still being fast enough that people
use it continuously.

## Priorities implied by the thesis

1. **Specify and cold-test the promoted project context.** Decide its file/model shape, attribution,
   acceptance semantics and how it appears identically to the human and agent.
2. **Productise catalogue generation and typed resolution in `fluxtion-builder`.** This closes the
   build-to-component-metadata joint and removes mechanical assembly from the conversation.
3. **Carry model identity into the audit-log header.** A finding must bind the declared model to the run,
   not merely fingerprint the log after it arrives.
4. **Make correction a first-class canvas transition.** Link finding → decision → change/handoff → new
   fingerprint/run → expectation result.
5. **Resolve remaining compiler policy once.** Auditor phases and other runtime policies become
   authoritative plans consumed identically by generated and interpreted processors.
6. **Build one-command evidence packages.** A reviewer must be able to reproduce the fixture, model,
   run, analyser result and mutation probes without reconstructing the session.
7. **Measure the judgement layer.** Goal interpretation and modes 2/3 determine whether the proposed
   human/LLM division survives beyond component composition.

## Acceptance tests for the thesis

This thesis is useful only if it produces tests stronger than its prose:

- **Cold handoff:** give a fresh human or model the project, but no transcript. It identifies the
  current goal, accepted decisions, open questions, model fingerprint, relevant run and next evidence
  needed without inventing any of them.
- **Two-surface parity:** every canvas fact visible to an agent has a named human surface over the same
  model, and every human change appears in the next `context` response.
- **Attribution:** a model hypothesis cannot render as a compiler fact, runtime evidence or human
  decision; promotion changes status explicitly and records who accepted it.
- **Anchor integrity:** changing the loaded log, filter or model makes stale findings announce the
  mismatch rather than silently reattach.
- **Bounded retrieval:** a large run can be investigated without placing the run or the project history
  into the model context; responses say what was omitted or truncated.
- **Deterministic build:** input permutation does not change catalogue, resolution, document, graph or
  fingerprint bytes where the contract promises stability.
- **Correction closure:** a recorded divergence, accepted correction and new run form one chain, and the
  final status comes from the explicit expectation check rather than narrative.
- **Permission boundary:** no canvas operation executes a runbook, mutates a server or edits application
  source without leaving the analyser and entering the existing human-approved repository/tool path.

## Non-goals and limits

- Not a claim that the analyser verifies application correctness. It verifies explicit expectations and
  reports what supplied evidence supports.
- Not a complete or authenticated chain of custody. Log provenance is declared today, not proved.
- Not a transcript database or a requirement to preserve every model token.
- Not a general-purpose wiki, issue tracker, IDE or workflow engine.
- Not equal authority for human, model, compiler and runtime contributions.
- Not autonomous source editing, deployment or server control by the analyser.
- Not evidence that goal interpretation or free-form authoring has been solved.
- Not evidence that the one measured composition fixture generalises to an ecosystem.

## Questions the independent reviewer should settle

1. Is the “shared evidence canvas” one coherent product thesis, or does it improperly combine an
   observability instrument, project context and an authoring workflow?
2. Does a project-owned context document violate the shipped rule that the profile is not per-user notes,
   or does versioned, typed, team-owned decision state fit the existing portable-context model?
3. What is the minimum durable schema needed for cold handoff? Are the six entry kinds above sufficient,
   excessive, or missing ownership/expiry/conflict semantics?
4. Can an LLM write durable context directly through a bounded analyser action, or must its writes remain
   session curation/reports until a human promotes them? Which rule is safe and usable?
5. Is “one model with two participants” true of the current UI/MCP implementation, or are there hidden
   duplicate authorities and one-way surfaces that make it aspirational?
6. Does the priority order follow from evidence, or does it promote context work ahead of the more
   consequential model-to-audit fingerprint joint without justification?
7. Which claim in this document is persuasion rather than something an acceptance command can settle?
