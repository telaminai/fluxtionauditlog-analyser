```markdown
# Fluxtion Evidence Bundles / Experiment Bundles — Product Proposal

## Core idea

**Trust the evidence, not the author.**

An Evidence Bundle turns an investigation into a portable, replayable package that another developer — or their AI assistant — can inspect, reproduce, explain, challenge and use to test a proposed fix.

The bundle connects:

**intent → compiled application → execution → investigation → evidence → replay → fix → comparison**

The important principle is that the bundle does **not** certify that the software is correct.

It establishes something narrower and more defensible:

> **These exact inputs, software artifacts, configuration and starting conditions produced these exact recorded results.**

The interpretation of those results — whether they are correct, acceptable or compliant — remains a separate assertion supported by tests, invariants, independent models, human review or other assurance processes.

A useful internal rule is:

> **Sign facts of execution, not conclusions.**

---

# Why this matters

AI is making software increasingly cheap to author and modify.

The harder problem is establishing:

- what was actually built;
- what software actually ran;
- what inputs it received;
- what execution occurred;
- what outputs resulted;
- whether a proposed fix changed the behaviour as intended.

Fluxtion already provides important primitives for this:

- compiler-derived deterministic execution;
- event-source replay;
- audit records from the running processor;
- GraphML execution topology;
- Mongoose execution and control;
- plots and calculated series;
- source and topology navigation;
- AI-controlled investigation through the Analyser;
- spotlight views that allow an LLM to show a human the exact evidence supporting its conclusion.

Evidence Bundles package those capabilities into a portable collaboration artifact.

---

# What a bundle contains today

An experiment can currently contain:

- selected audit-log records covering the investigation;
- event-source replay inputs;
- observed outputs;
- plot definitions;
- rendered graphs;
- GraphML describing the execution topology;
- reports combining logs, topology, plots and LLM-generated explanations.

The replay inputs can be run against a local Mongoose server using the existing template workflow.

The bundle therefore preserves both:

1. **what was done to the system**, and
2. **what the system was observed to do**.

This allows an investigation to be exchanged as something executable rather than merely described in a ticket, email or screenshot.

---

# Proposed bundle structure

Conceptually:

```text
experiment/
    manifest.json

    inputs/
        events.*
        starting-state.*

    execution/
        audit-log.*
        observed-outputs.*
        run-receipt.json

    topology/
        graph.graphml

    analysis/
        plots.*
        report.md
        walkthrough.json

    artifacts/
        optional component/build metadata

    signatures/
        manifest.sig
```

The physical format could initially just be a ZIP-compatible archive with a distinct extension such as:

```text
.fexp
```

The format should remain inspectable rather than intentionally opaque.

---

# Provenance manifest

Every bundle should contain a manifest describing exactly what the experiment represents.

Possible fields include:

- experiment ID;
- creation timestamp;
- creator identity;
- source experiment, if this is a response;
- processor/build identity;
- compiler version;
- Fluxtion runtime version;
- Mongoose version;
- Analyser version;
- Spring XML/configuration identity;
- component coordinates and versions;
- component/JAR hashes;
- processor/JAR hash;
- replay-input hash;
- starting-state hash;
- source audit-log identity/hash;
- included audit interval;
- explicitly omitted intervals/data;
- observed-output hashes;
- GraphML hash;
- report hash;
- walkthrough hash;
- analysis/tool versions.

Every important bundle member should have a cryptographic digest, initially SHA-256 or equivalent.

This means replacing any artifact changes the identity of the experiment.

---

# Run receipt

The strongest provenance comes when the running application itself exposes or emits a **run receipt**.

The receipt should bind the observed execution to the actual deployed artifacts.

For example:

```text
processor SHA
component/JAR SHAs
configuration SHA
compiler version
runtime version
initial-state identity
input/event-source identity
audit-log identity
run/start identifier
```

This addresses an important class of failures where the source appears correct but the deployed runtime contains a stale or unexpected dependency.

The experiment should not merely say:

> "This was version 1.3."

It should be able to establish:

> "These exact binary artifacts produced this execution record."

---

# Signing model

The goal is **tamper evidence and provenance**, not certification of correctness.

A signer attests to:

> **These are the inputs, artifacts and results associated with this experiment.**

A signer does **not** necessarily attest to:

> "The results are correct."

For example, the UI could show:

```text
Experiment integrity        VERIFIED
Replay inputs               VERIFIED
Processor artifact          VERIFIED
Vendor artifacts            VERIFIED
Configuration               VERIFIED
Recorded outputs            VERIFIED
Source log                  VERIFIED
Semantic correctness        NOT ASSERTED
```

This distinction should remain explicit throughout the product.

---

# Who signs?

The long-term model should not require Telamin to be the sole authority.

Different parties can sign different assertions.

## Integrator

Can sign:

> These exact binaries, configuration and inputs produced this recorded experiment.

## Vendor

Can sign:

> This response experiment was produced using vendor component version X / artifact digest Y.

## Customer CI

Can sign:

> We independently replayed the original scenario against this replacement and observed these results.

## Telamin tooling

Can mechanically attest:

> This experiment was generated/verified using these Fluxtion/Mongoose/Analyser versions and all referenced artifacts match their recorded hashes.

Telamin may eventually operate an identity or assurance registry, but should avoid implying:

> "Telamin certifies that this application is correct."

---

# Integrity vs correctness

The evidence model should deliberately separate four different questions.

## 1. Provenance

**What exact software and data were involved?**

Established through:

- hashes;
- signatures;
- artifact identity;
- configuration identity;
- run receipts.

## 2. Structural evidence

**What execution structure did the compiler derive?**

Established through:

- compilation;
- dependency analysis;
- execution topology;
- GraphML;
- structural diagnostics.

## 3. Runtime evidence

**What did the deployed processor actually record as happening?**

Established through:

- audit records;
- outputs;
- runtime state;
- plots and calculated values.

## 4. Semantic correctness

**Was the observed behaviour actually correct?**

Established separately through:

- acceptance tests;
- invariants;
- independent calculations;
- reference models;
- regression suites;
- human review;
- regulatory/business rules.

A perfectly signed experiment can faithfully demonstrate the behaviour of an algorithm that is itself wrong.

That is not a failure of the evidence model.

It is an important boundary of what the evidence actually establishes.

---

# Facts vs interpretation

Reports and walkthroughs should distinguish between:

## Recorded facts

Example:

```text
riskEngine.var = 24,320
```

## Calculated facts

Example:

```text
24,320 > configuredLimit(20,000)
```

## LLM interpretation

Example:

```text
The risk-limit breach appears to explain why the order was suppressed.
```

## Remaining uncertainty

Example:

```text
The Acme VaR value was not independently validated in this experiment.
```

The LLM's explanation should never silently become indistinguishable from the underlying evidence.

---

# Guided walkthroughs

The next major extension is a saved, multi-step Analyser walkthrough.

An LLM performing an investigation could construct the walkthrough while it works.

A walkthrough step might:

- open a particular view;
- select a log interval;
- select specific records;
- highlight nodes in the topology;
- show a plot;
- zoom to a period;
- place a spotlight annotation;
- explain the significance of the evidence.

For example:

```text
Step 1:
Show the subscription request.

Step 2:
Highlight the BRL processing path.

Step 3:
Show BRL ticks dropping to zero while MXN remains steady.

Step 4:
Show the re-subscribe event.

Step 5:
Show BRL ticks resuming.
```

The recipient can:

- follow the walkthrough;
- pause at any point;
- inspect the underlying evidence;
- challenge the conclusion;
- continue when ready.

Crucially, once reviewed and saved, walkthrough playback should **not require a fresh LLM interpretation**.

This makes the explanation stable and reviewable.

---

# Investigation → fix → response workflow

A complete workflow could be:

## 1. Receive

Open an Evidence Bundle from another developer, team or vendor.

Review the report or guided walkthrough.

## 2. Reproduce

Replay the attached event inputs against a local Mongoose instance.

Confirm whether the captured behaviour reproduces.

## 3. Investigate

Work with the LLM and Analyser to:

- inspect logs;
- inspect topology;
- inspect source;
- calculate series;
- create plots;
- identify likely causes;
- discuss uncertainties.

## 4. Fix

Change as appropriate:

- Spring XML;
- configuration;
- component code;
- vendor component;
- other application logic.

Compile the changed processor.

## 5. Replay

Run the original captured scenario against the changed system.

Record:

- new audit evidence;
- new outputs;
- new topology where relevant.

## 6. Compare

Compare:

```text
original experiment
        vs
response experiment
```

Highlight:

- intended differences;
- unexpected differences;
- unchanged controls;
- topology changes;
- output changes;
- unresolved uncertainties.

## 7. Respond

Create a linked response bundle containing:

- new artifact identities;
- explanation of the change;
- replay evidence;
- comparison report;
- optional walkthrough;
- signature.

The original experiment remains unchanged.

---

# Experiment chains

Experiments should be immutable by identity and link to one another.

For example:

```text
Customer Incident A
        ↓
Vendor Response B
        ↓
Customer Verification C
```

Each experiment references the cryptographic identity of the preceding experiment.

This creates a chain of custody around software behaviour.

Conceptually this is similar to:

> **Git commits for observed behaviour.**

Git records how source changes.

Evidence Bundles could record:

> **how behaviour changed, with the evidence required to inspect that claim.**

---

# Collaboration across vendor boundaries

This is one of the strongest use cases.

An integrator has an application composed from vendor JARs.

Unexpected behaviour occurs.

Instead of sending:

- screenshots;
- log extracts;
- prose;
- partial reproduction instructions;
- entire source repositories;

the integrator sends the supplier an Evidence Bundle.

The supplier receives:

- exact scenario inputs;
- relevant runtime evidence;
- topology;
- observed outputs;
- investigation report;
- optional guided walkthrough.

The supplier can:

1. inspect the reported behaviour;
2. replay it locally;
3. investigate using its own source;
4. produce a replacement JAR;
5. replay the experiment;
6. create a response bundle.

The integrator then independently reruns the **original experiment inputs** against the replacement component in the actual host application.

This supports collaboration without requiring complete source-repository exchange, assuming the bundle contains sufficient execution context and dependencies.

---

# Relationship to vendor component composition

Evidence Bundles complement Fluxtion's vendor-component model.

Fluxtion can already:

- load separately built vendor JARs;
- discover vendor component subgraphs;
- compose them with host components;
- derive one execution order across the combined system;
- place vendor and host nodes into the same runtime evidence stream.

This allows a bundle to describe behaviour across an organisational boundary.

A supplier does not merely receive:

> "your JAR seems broken."

It can receive:

> "Here is the exact scenario, exact artifact identity, derived topology and observed execution that demonstrates the problem."

---

# Experiment packs as supplier artifacts

In the longer term, vendors could ship components together with reusable experiment packs.

For example:

```text
acme-risk.jar

experiments/
    normal-market.fexp
    risk-limit-breach.fexp
    stale-market-data.fexp
    reconnect.fexp
    component-upgrade.fexp
```

A vendor might state:

> Version 3.4 has been replayed against these published scenarios.

A customer can then replay relevant scenarios inside its own application composition.

This turns software components into something richer than binaries:

> **components accompanied by executable behavioural knowledge.**

---

# The network effect

There are potentially two reinforcing network effects.

## Component network

```text
more conforming components
        ↓
more systems easily composed
        ↓
more Fluxtion users
        ↓
greater incentive for vendors to support Fluxtion
        ↓
more conforming components
```

## Evidence network

```text
more organisations exchange Fluxtion experiments
        ↓
vendors increasingly support the format
        ↓
more reusable experiments and responses exist
        ↓
greater value in using the same evidence model
        ↓
more organisations exchange experiments
```

The technical mechanisms for these effects exist or are emerging.

The network effects themselves are not yet established and should not be claimed prematurely.

---

# Product packaging

Evidence Bundles are currently implemented largely as skills/scripts layered over the base Analyser.

This is not necessarily a weakness.

It demonstrates that the underlying Fluxtion/Analyser platform contains sufficiently powerful primitives that higher-level products can be created relatively cheaply.

The commercial boundary does not need to be based on hiding the scripts.

A possible model is:

## Open / free

**Experiment format**
- documented and inspectable.

**Experiment viewer**
- open received bundles;
- inspect evidence;
- verify hashes/signatures;
- potentially replay basic scenarios.

Low friction for receiving bundles encourages distribution.

## Paid Analyser / Team

**Experiment creation**
- capture;
- select evidence;
- generate reports;
- generate walkthroughs;
- create response experiments;
- comparisons;
- AI-assisted investigation.

## Enterprise / Assurance

- signed run receipts;
- organisational identities;
- artifact provenance;
- redaction policies;
- approval workflows;
- CI integration;
- private experiment repositories;
- vendor collaboration;
- long-term evidence retention;
- experiment chains;
- organisation-wide search;
- policy/invariant checks.

The valuable commercial capability is not that the ZIP format is secret.

It is the ability to **produce trustworthy, provenance-bound experiments from the actual execution environment**.

---

# Hard to counterfeit, easy to exchange

The design principle should be:

> **Make experiments easy to copy and exchange, but impossible to silently modify while continuing to present them as the original experiment.**

This can initially be achieved with:

1. a canonical manifest;
2. SHA-256 hashes for all artifacts;
3. processor/component/configuration hashes;
4. source-log identity;
5. explicit selected intervals and omissions;
6. a run receipt;
7. an Ed25519 or equivalent signature over the manifest;
8. a verifier.

For example:

```bash
fluxtion experiment verify incident-127.fexp
```

might report:

```text
Experiment: incident-127
Creator: Example Bank CI

Bundle integrity            VERIFIED
Replay inputs               VERIFIED
Processor                   VERIFIED
Vendor components           VERIFIED
Configuration               VERIFIED
Audit evidence              VERIFIED
Observed outputs            VERIFIED

Semantic correctness        NOT ASSERTED
```

---

# Possible assurance registry

A later product could maintain identities and experiment relationships without necessarily storing sensitive experiment contents.

For example:

```text
Organisation:
    Acme Risk Ltd

Signing key:
    ABC123...

Experiments:
    exp:a917...
    response:b284...

Relationships:
    response:b284... responds-to exp:a917...
```

The registry could establish:

- organisational signing identities;
- timestamps;
- experiment hashes;
- response relationships;
- component identities;
- verification events.

Sensitive logs and data could remain within customer/vendor environments.

Telamin's role would be closer to:

> **provenance infrastructure / notary**

than:

> **authority declaring software correct**.

---

# Security and privacy

Evidence Bundles may contain sensitive material.

The product therefore needs:

- explicit selection of included records;
- redaction;
- field masking;
- secrets detection;
- declared omissions;
- access control;
- encryption for transport/storage;
- potentially separate public and confidential sections.

An experiment should make it visible when evidence has been deliberately excluded.

---

# The product proposition

The broader Fluxtion proposition becomes:

> **AI helps developers design, investigate and repair applications.**
>
> **Fluxtion compiles their coordination into deterministic event processors.**
>
> **The running application produces execution evidence.**
>
> **The Analyser allows humans and AI to investigate that evidence together.**
>
> **Evidence Bundles make the experiment portable, replayable and reviewable across people, teams and organisational boundaries.**

The LLM can author the software.

The LLM can investigate the result.

The LLM can produce the explanation.

But the LLM does not get to award itself trust.

It must show the evidence.

---

# Product positioning

Primary company philosophy:

> ## Trust the evidence, not the author.

Supporting proposition:

> **Turn intent into evidence you can argue over.**

Evidence Bundle proposition:

> **Software defects shouldn't travel as descriptions. They should travel as reproducible experiments.**

Another possible description:

> **A pull request for runtime behaviour.**

Or:

> **Portable, executable evidence for software behaviour.**

---

# Relationship to the "notebook" concept

The broader Fluxtion/Analyser environment can be described as:

> **The notebook for event-driven applications.**

The analogy is:

| Notebook | Fluxtion |
|---|---|
| Cells | Java / Spring / component authoring |
| Kernel | Generated deterministic Fluxtion processor |
| Canvas | Audit Log Analyser |
| Saved notebook | Evidence Bundle |
| Re-run analysis | Event-source replay |
| Shared explanation | Report / guided walkthrough |

The important distinction from a conventional notebook is that the Fluxtion canvas can represent evidence from the execution of an actual deployed event-driven application.

Evidence Bundles make that notebook portable.

---

# Current status

Already working or substantially demonstrated:

- deterministic Fluxtion processors;
- Mongoose replay/deployment workflow;
- audit-log evidence;
- GraphML topology;
- plots;
- spotlight annotations;
- LLM-controlled Analyser through MCP;
- vendor-style binary component composition;
- selected-record export;
- replay inputs;
- observed outputs;
- generated reports.

Still to build/harden:

- canonical bundle format;
- manifest;
- artifact digests;
- run receipt;
- signatures;
- `experiment verify`;
- guided walkthrough persistence/playback;
- linked response experiments;
- comparison UX;
- redaction/security controls;
- organisation signing identities;
- optional assurance registry.

---

# Key design principles

1. **Trust evidence rather than authorship.**
2. **Sign facts of execution, not conclusions.**
3. **Keep captured facts distinct from interpretation.**
4. **Make omissions explicit.**
5. **Bind evidence to exact binary artifacts and configuration.**
6. **Allow independent replay wherever practical.**
7. **Never imply that deterministic replay alone establishes general correctness.**
8. **Make the format easy to exchange and hard to tamper with silently.**
9. **Allow AI to produce investigations, but keep humans able to inspect the underlying evidence.**
10. **Keep the original experiment immutable; fixes create linked response experiments.**

---

# Central thesis

As AI makes software easier to create, trust increasingly depends not on who wrote the code but on what can be established about the resulting executable behaviour.

Fluxtion's opportunity is to make that behaviour:

**compiled, observable, replayable, portable and contestable.**

Evidence Bundles are the mechanism for exchanging that evidence.

> **Trust the evidence, not the author.**
```