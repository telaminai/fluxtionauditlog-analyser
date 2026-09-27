# Evidence bundles — combined product and delivery proposal

**Status: DISCUSSION DRAFT r3, 2026-09-27. Not approved or implemented.** r3 challenges the capture and replay
conclusions with source evidence, and narrows archive reuse. Reasons and unresolved choices are in the
[discussion log](discussion-log.md), L-16–L-22. D-0 is unchanged.

This consolidates all five [source versions](#15-source-versions-and-reconciliation). They remain
unchanged in `versions/` as the record of the discussion. This is the working document to argue over,
not a claim that their authors agreed. Recommendations are marked as such; outstanding choices have
stable decision IDs. Agreement between AI reviewers does not establish a fact.

**Owner direction already given:** deliver something useful in the next couple of days, combining
portable investigations **and a bounded replay → fix → comparison → response journey**. The browser
viewer is optional. An inspection-only release can be an intermediate checkpoint, but is not completion
of that requested delivery. Two days is a planning target, not grounds for waiving an acceptance check.

## 1. The proposition

**Trust the evidence, not the author.**

An investigation should end with evidence someone else can open, check and challenge, not a
description of evidence. A bundle packages the recorded run, its execution topology, the analysis
that made sense of it and the author's explanation. Where the required inputs and conditions exist,
it also carries a reproducible experiment and the means to assess a proposed fix.

```text
intent → application/build → execution → investigation → incident bundle A
                                                           │
                                                ticket / support / PR
                                                           │
                                  recipient inspects, replays and changes the build
                                                           │
                                     comparison + response bundle B → sender checks
```

The notebook becomes portable: design/code are the authoring material, the processor runs the logic,
the analyser is the investigation canvas, and the bundle preserves the experiment and discussion.
An LLM may propose the design, investigate it and explain the result. It does not award itself trust.
The reader can skip the explanation and inspect the supporting records.

The defensible claim depends on the evidence available:

- A basic unsigned bundle establishes that its included bytes match its manifest; its author
  **declares** the association between inputs, build and outputs.
- A recorded local run can establish what that runner observed for the supplied inputs, conditions
  and build, within the runner's documented boundary.
- An assertion or independent oracle establishes a particular expected result, not general correctness.
- A future signature authenticates a statement under a key. It does not turn the statement into truth.

Consequently, “these exact binaries produced these results” is a goal for run provenance, not a
consequence of putting JAR hashes and an audit log in the same ZIP.

## 2. Why this is valuable, and what has been demonstrated

The immediate use case is support and engineering across a team or supplier boundary. A ticket
arrives with the incident, not just a screenshot. The recipient can follow the author's walk, locate
the relevant observation, investigate against their own source/build, and return a checkable response.
Neither party must exchange a whole source repository or keep the original investigator on a call.

The longer-term model includes supplier experiment packs shipped alongside components: normal cases,
failures, reconnects and upgrades, replayed by the customer within its own composition. A sequence
`incident A → supplier response B → customer verification C` preserves which experiment answered which.
A digest link is not, by itself, authenticated custody or a trusted timestamp.

The prototype account reports the following, **not independently re-run during this consolidation**:

| Observation | Product consequence |
|---|---|
| Cold open and open from a dirty session converged to 3 charts, 7 focuses, 1 report, 21 records and the same focus | Acceptance must compare restored state, not merely report that files opened. |
| A content cut reduced 6,543 records to 21; a time window retained mostly irrelevant records of the same event type | Excerpting needs content/record selection and explicit omissions, not only time/dimension filters. |
| Copying the profile broke its path anchors; the workaround pointed outside the bundle | A portable profile must be constructed deliberately and tested without the sender's checkout. |
| Four nominally identical input sequences gave different numeric results with a moving simulated feed | Distinguish re-running, replaying controlled inputs and reproducing an asserted result. |
| Images occupied 1.1 MB of a 1.4 MB bundle | Include referenced images only; vector rendering is a useful later optimisation, not a prerequisite. |
| Capture was a checklist and opening allowed auto-save into the experiment | Capture needs transactional publication; the received evidence needs an immutable original and visibly separate working state. |

The product's commercial hypothesis is an exchange loop: a useful ticket attachment introduces
another recipient, and replying creates a reason to adopt authoring tools. This is not an established
network effect. Measure cold-open success, time to the supporting record, reproduction rate, time to
verify a response and repeat use by teams outside the authoring group.

## 3. First-delivery scope

Start with one synthetic incident, one processor, one supported text-log reader, one input codec and
one explicitly supported local replay runner. The format may carry other optional artefacts, but the
first implementation does not claim to execute every application or interpret every log source.

| Required for the first complete delivery | Optional or later |
|---|---|
| One attachable `.fexp` file, manifest, digests, offline verification | Signing/organisation trust, registry, transport service |
| Capture, portable open, captured/current verdicts, no sender paths | Source distribution, automatic checkout/download, arbitrary reader support |
| Saved attributed walk, record/chart/topology inspection, explicit flags and applied view state | General macros, source/menu walks, native Walk panel if a client provides the first controls |
| Original unchanged; fresh working copy; edits cannot masquerade as the received snapshot | Full native overlay editor or in-place bundle rewriting |
| Original inputs, initial conditions, local replay on original and changed builds, assertions | General deterministic replay service, live systems or provider-dependent acceptance |
| Linked response and scoped comparison including unchanged controls | General-purpose two-window diff UI, global correctness certification |
| Synthetic/demo data, member allow-list, clear `redaction: none` | Customer-data exchange and field-redaction engine |
| Desktop analyser as the inspection surface | Offline browser viewer and SVG: optional, per owner direction |

Inspection-only bundles are valid artefacts and remain useful. A bundle containing inputs but no
verified executable route is labelled accordingly. It cannot count as the replay acceptance pair.
No implementation may silently move the required replay or comparison into “phase 2”.

## 4. Delivery architecture: recommended sequence, still open to review

### External versus internal

An **external tool** is a separate client that captures files and sequences existing analyser calls.
An **internal tool** uses analyser services directly and participates in its session model. These are
implementation choices, not different evidence standards. Internal does not require a large new UI;
a headless Java command can share the same core as a menu or MCP operation.

| Concern | External client | Internal analyser feature |
|---|---|---|
| First implementation | Potentially fast: ZIP/JSON and existing verbs; can target an already released analyser. | Requires a product build and relevant gates, but can reuse serializers/readers without exposing another API. |
| Snapshot fidelity | Sees only exposed fields, echoes and persisted files; several calls can observe different states. | Can capture one immutable DTO at the session/EDT boundary, then perform I/O off-thread. It still needs race tests. |
| Charts, flags and view | Must prove each field is exportable and not stale; reconstructing from summaries is unsafe. | Can copy full model state, including unsaved definitions and flags, through the production serializers. |
| Lifecycle | Orchestrates asynchronous calls and must handle partial completion, cancellation and superseding user actions. | Session nodes can own one operation and its effects/facts, rather than a client approximating completion. |
| Human experience | CLI plus analyser window; another tool to install/version and a visible split in errors/status. | Capture/open/walk fit the app; pending, integrity and working-copy state can reach every surface consistently. |
| Agent experience | Useful immediately if the API is sufficient; client versions must track supported analyser versions. | A shared native operation serves human and agent, with schemas and tests updated together. |
| Parsing and trust | Tempting to add a second YAML reader, series calculator or verdict; prohibit those shortcuts. | Reuses the existing semantic authority, but must not create a second controller beside the session. |
| Maintenance and portability | Can iterate independently, but API drift and duplicate state become lasting costs if it grows into a second application. | One distribution and regression boundary; larger initial change surface. |
| Replay | Calls an explicitly installed application runner. | Still uses an external application runner; being internal does not authorise server-mutating analyser verbs. |
| Two-day risk | Interface gaps may erase its apparent time advantage. “No product change” is unproved. | Session/UI integration may exceed the time box. A CLI-first shared core avoids making a full UI the first dependency. |

**Recommendation D-1: shared internal evidence core, thin client orchestration where it saves time.**
Keep capture fidelity, canonical parsing/calculation and session verdicts inside their existing
analyser boundary. Expose the smallest capture/verify/open primitive needed by a CLI or client; reuse
it later from native menus/MCP. A small `fexp` client can sequence the experiment and drive existing
views; it must not become a second analyser. The replay runner remains application-side.

An external-only first delivery remains an alternative if EB-0 proves complete, coherent exports
already exist. This qualifies draft A and draft B's r2 speed recommendation rather than assuming it:
ZIP creation is easy outside the application; trustworthy capture is the harder boundary. The initial
proposal to prefer client-led delivery is revised here after explicitly comparing that boundary.

The first work block must establish whether the available interfaces expose complete, coherent
capture state, raw-record export and flags. If they do not, choose and review the smallest analyser
adapter that reuses the production services, or switch to native capture. Do not work around a missing
interface by reconstructing a chart from a partial echo or reading a stale on-disk profile.

**EB-0, inspected in r2 and rechecked/qualified in r3 (L-16).** These answer part of the question above. They are
READ at `main` `d82f1487`, not socket reproductions, so re-check them at the implementation head.

| Capture need | Available to a client today? | Evidence |
|---|---|---|
| Flags | **Yes.** `context.flags` lists every flagged row's `recordIndex` and `kind`, plus `note` and `fix` when present. The index is into the live store, so an excerpt must remap it through `record-map.json`. | `MainFrame` context builder, the `flags` loop |
| Raw record text | **Yes, by index.** `read` returns `text` from the same `rawText(row)` the YAML exporter writes. It is anchored by record index, byte offset or time, at 25 records per call. A whole-log or explicit-index excerpt needs no second parser; this is record text, not an exact original-file byte export. The exporter adds document separators and newlines. | `ReadService` (`MAX_COUNT = 25`, `m.put("text", rawText.apply(row))`); `RecordExporter.toYaml` |
| The records the current filter shows | **No.** No verb lists the filtered record indices; `aggregate` returns counts and where the counted records begin and end. A filtered-view excerpt needs a native adapter, or the person must choose explicit indices. | the verb list in `VerbSchemas` |
| Complete chart definitions | **No.** `context.savedGraphs` carries `name`, `open`, `input`, `series`, expression *strings* and `style` only. It lacks expression labels, guides, bands, markers, external series, notes, explanation, window and rationale. | `SessionFacts.savedGraphs` |
| The on-disk profile as a substitute | **Not safely.** Profile writes are coalesced (debounced), so the file can lag the live charts. | `ProjectSession` class javadoc, *Auto-persist, debounced*; `requestSave` |
| The pairing verdict | **Yes.** `context.graphPairing`. | `MainFrame.context`, reading the session pairing (READ again for r3) |

**r3 correction to D-1/D-1a (L-16, L-17).** These are individual facts, not a coherent capture interface.
`context` is assembled on the EDT; a subsequent `read` has its own snapshot/raw-text accessors and no expected-capture
revision parameter. A person can change a chart or switch logs between calls, even with Follow off and no load pending
at either observation. Adding chart fields does not close A-3. An external-only route is therefore **not established**.

For the chart-definition fact alone, extending each existing `context.savedGraphs` entry is the smaller candidate:
keep its summary fields and add a complete, versioned definition through the production serializer. This changes
output assembly without a new input option; the existing `charts` projection includes it. A separate `graph` read-only
option needs schema/prompt/routing changes and must bypass that verb's log guard, tab selection and reopen side effects.
L-17 inventories the existing contract tests; these are review surfaces, not measured implementation times.

Keep D-1a's fact export separate from a capture-validity contract. EB-0 must prove a stable snapshot/retained source,
or add a session-owned capture operation with immutable state, source lifetime protection and revision-bound reads.
Before/after polls or file hashes alone do not catch an intervening change that returns to the same visible state.
The extra adapter work is a delivery dependency, not grounds to weaken §6.1.

| Responsibility | Initial proposed owner | Durable home / invariant |
|---|---|---|
| Manifest, archive, hashing, verification, parent relationship | Shared library/headless command; client invokes it | One contract and golden fixtures; do not maintain competing verifiers. |
| Log parsing, record selection/export, pairing, series and coverage | Existing analyser services | One reader and one verdict model; tool replies retain scope/qualifications. |
| Capture transaction and complete state | Existing supported snapshot/export route if proved; otherwise narrow native adapter | Session owns operation state; immutable capture DTO, effects and completion facts. |
| Open and walk sequencing | Client using existing verbs in a dedicated or explicitly selected instance | Native sequencing, if added, belongs in the generated session processor. |
| Re-execution of application inputs | Explicit, locally trusted application-side runner | No server-mutating verbs or received-code execution in the analyser. |
| Comparison | Client assembles canonical analyser results and runner assertions | It does not invent a second verdict about log/graph identity or “fixed”. |

Proposed user/agent commands are `fexp capture`, `verify`, `open`, `walk`, `compare` and a bounded
`replay` wrapper for the trusted runner. Human and assistant use the same commands and receive the
same explanations. Command spelling is proposed, not an existing API. Capture accepts explicit input,
conditions, oracle, walk and parent files; it does not discover them by reading arbitrary repositories.

Later native actions would include capture/open/response menus, a headless verifier, a bounded
`bundle` verb and a Walk panel. They require updates to schemas, manifest, prompts, help, menu inventory,
tests and human surfaces together. A new verb is a reviewed decision, not a relaxed contract test.

Rule 9 still applies: a native adapter executes requested effects and reports facts; surfaces render
the published snapshot. The external client may sequence calls, but may not call intermediate replies
a completed bundle open. Await explicit completion with deadlines, then verify the final state.

## 5. Format, identity and trust

### 5.1 Container and layout

Proposed extension: `.fexp`, an ordinary ZIP with no encryption or executable semantics in v1.
`manifest.json` is required. Other paths below are conventions selected to make existing profile
resolution work. Members declare roles; directory names alone do not establish their meaning.

```text
manifest.json
.analyser/project.bundle.fluxtion-settings
audit/excerpt.yaml
audit/record-map.json
topology/processor.graphml
analysis/view.json
analysis/flags.json
analysis/walk.json
testimony/investigation.md
reports/investigation.pdf
inputs/events.dat
inputs/conditions.json
inputs/oracle.json
execution/receipt.json
execution/assertions.json
comparison/result.json              # response only
charts/incident.png                 # optional
index.html                          # optional browser tier
```

At least one audit member is required for inspection. GraphML is required for the topology walk in
the acceptance journey, but an ordinary log-only bundle may explicitly say topology is not carried.
Input/receipt/comparison members are required only for the capabilities the bundle declares.
An optional report may be absent; a required walk target may not silently disappear.

### 5.2 Proposed manifest contract

Use UTF-8 JSON, reject duplicate keys, and validate references before applying anything. Required
top-level fields are `format: "fluxtion-evidence-bundle"`, `formatVersion: 1`, `capturedAt`,
`createdWith`, `members`, `capture`, `capabilities`, `limitations`, and `semanticCorrectness: "NOT ASSERTED"`.
Optional fields include self-declared `creator`, `title`, `respondsTo` and source coordinates.

Each member has a unique `id` and `path`, `role`, `kind`, `mediaType`, `bytes`, SHA-256 and `required`.
Every file except the manifest is listed exactly once, including testimony, receipts and optional HTML.
Listed-but-missing, unlisted, duplicate or mismatching members fail verification. A member's role is
separate from its hash status: testimony bytes can match while the testimony remains unverified.

`capture` records source identities or a reason they are unavailable, scan bound, selected/indexed
counts, selection/filter, pending-tail disposition, declared provenance, captured verdicts and their
bases. It references the audit, map, view, flags, profile and topology members that are present.
Record count, file byte count and completeness are different facts. No absolute private path is needed.

`capabilities` explicitly declares inspection, walk and replay-input presence with member references
or absence reasons. Local readiness is assessed separately. A manifest cannot establish that a local
runner, build, decoder or dependency is present just by saying `replay: present`.

**Recommended identity rule D-2:** `sha256:<digest of the exact manifest bytes>`, computed externally,
not embedded in the same manifest. Member hashes bind contents without a recursive hash. A stable
writer emits deterministic JSON, but a verifier hashes original bytes without reformatting. Repacking
unchanged members/manifest preserves identity; changing the timestamp or manifest spelling creates a
new capture identity. `respondsTo` contains the parent's identity. Compare data-member hashes separately
when two captures contain the same evidence but have different capture times.

Drafts A/B instead canonicalise JSON while blanking/removing an ID field; these are incompatible
algorithms, so the implementation must choose one before writing bundles. This draft recommends exact
bytes for simplicity, not because a canonical scheme is inherently wrong. Golden cross-reader fixtures
must pin the chosen rule.

**r3, L-20:** keep D-2 open. Exact bytes deliberately distinguish a pretty-printed manifest from the received one.
If canonical identity is chosen instead, name the canonicalisation standard and numeric/string constraints;
“sort the keys” is not a cross-language contract. Duplicate-key rejection is required under either choice.

Version 1 readers refuse unknown versions or unknown required capabilities/roles. Unknown optional
members may be integrity-checked and listed without interpretation. Preserve opaque unknown metadata
when making a derivative, but never execute or import unknown configuration into the recipient's machine.
No claim of forward compatibility is made for a version whose required semantics are unknown.

### 5.3 Four questions, four answers

| Question | What the receiver sees |
|---|---|
| Integrity | “Member hashes match” or exactly which member fails; not “the application is verified”. |
| Authorship / provenance | Unsigned, self-declared, locally observed, or a separately verified signature/receipt with its basis. |
| Runtime/structural evidence | Captured and current pairing, source identity, completeness, scope and producer findings, kept separate. |
| Expected behaviour | Named assertions with PASS/FAIL/NOT RUN/INCOMPATIBLE and their evidence. General semantic correctness remains NOT ASSERTED. |

Hashes do not establish who wrote the manifest, whether a graph was generated from a particular JAR,
or whether a report was correctly derived. Someone can replace both data and manifest and produce a
different internally consistent bundle. A separately known original ID exposes that substitution;
cryptographic authenticity requires a trust model beyond the v1 recommendation.

**Recommendation D-3: unsigned first.** Optional Ed25519 from draft A is a valid later addition if a
reviewed extension specifies the signed bytes, key identity, trust source, revocation/rotation and
failure behaviour. “Signature valid under supplied key; signer not vouched for” differs from trusted
organisational identity. Do not silently accept an unsupported signature and display a green signature
badge. Signature files must have an explicit envelope rule so they do not create a digest cycle.

**r3, L-21:** D-3 remains an owner choice. Unsigned verification detects a changed member against an unchanged
manifest; it cannot detect replacement of both without a separately trusted original identity. A valid signature under a key supplied in the
same bundle proves signature/key consistency, not that the ticket's claimed author signed it. If that author identity
is a first-delivery acceptance requirement, explicit trusted-key distribution and its tests must enter the scope.

An integrator, component supplier, customer CI and Telamin tooling may eventually attest different
facts. No party becomes the sole authority on correctness. Runtime-emitted receipts can improve build
provenance; a capturer-authored receipt is useful but labelled declared.

## 6. Capture and portable state

### 6.1 A coherent transaction

First delivery requires a stable supported text log, Follow explicitly paused, no pending load or
evidence refresh, and no changed/unverified file identity. "No pending load" matters beyond tidiness: tracker item
**M44.6** (an owner decision, still open) is a real case where a superseded load's tail installs its table over a newer
log. Capture and open must not run while a load is pending, and a client-led open must await completion before
reading state. An unclaimed stream end may remain UNKNOWN;
incomplete evidence is not automatically corrupt evidence. Record its qualifications.

Capture definitions, store identity, session snapshot, filter, flags, view, scan bound and relevant
graph content identity coherently. A native capture obtains an immutable DTO on the EDT and copies/
hashes off it, with lifetime protection for the store. A client route must demonstrate equivalent
coherence; two polls alone do not rule out an intervening change that returns to the same apparent state.
If this cannot be established through released interfaces, the snapshot adapter is a prerequisite.

Write to private staging, verify copied bytes and references, recheck capture validity, then publish
atomically to the chosen destination. Same-length rewrites, a competing open/filter/chart change,
I/O failure or cancellation cannot leave a complete-looking mixed bundle or overwrite an existing one.
Source fingerprints taken at opening are not a substitute for checking the bytes actually captured.

Derived reports must be rendered from the staged evidence in an isolated analyser context, not by
switching the author's live session away and back. The output records the analyser/renderer versions.
The tool's file access is limited to explicit sources, bundle members and selected destinations; an
analyser path shown in context is information, not permission to crawl the host filesystem.

### 6.2 Excerpts and chart scope

Use the analyser's canonical record selection/export service. Support whole indexed population,
the current supported filter and an explicit ordered set of source record indices. The last permits
content-derived cuts without adding a second parsing grammar. A text selector must operate on the
reader's records, not split YAML on guessed delimiter strings.

Record map entries contain original source index, bundled index and raw-record digest; use index
plus digest, since identical record payloads can occur more than once. Raw records remain intact;
framing separators may be added by the existing exporter. Reopen the excerpt and compare selected
record identities/values, not merely the total count. Equal counts can hide wrong records or payload drift.

Persist selection/filter, source population at the captured bound, selected/dropped counts, time
range when known, pending-tail treatment and limitations. Never turn the source's stream-end marker
into a claim that the excerpt is the complete execution. Keep source diagnostics and excerpt diagnostics
distinct; do not require pairing to be identical when the population deliberately changed.

Default recommendation for the first synthetic incident: retain the whole small audit log. Excerpt
acceptance is still required for the portable format. Replay input/state is separate and must contain
the history needed by the replay even when the explanatory output excerpt is tiny.

Charts and report tables name their population, expressions and resolution policy. Include needed
warm-up/history and external inputs, or refuse the dependent capture. Check point values, counts and
range, not only that a chart definition exists. A smaller excerpt must not silently change a running
sum or last-known value. If a supplied PDF belongs to another scope, preserve it as an attributed
attachment with that scope; do not present it as newly derived from this excerpt.

### 6.3 Profile, flags and view

Use `.analyser/project.bundle.fluxtion-settings` at the bundle root. This avoids draft A's extra
`analyser/` level and its `../log` references. Do not invent log/GraphML keys in the project profile:
the manifest identifies those members, and the open operation loads them explicitly.

Build from complete supported profile/state data using a strict allow-list, preserving chart style,
open/closed definitions, formulas, guides, bands, markers, notes and report/focus definitions. A graph
echo is not presumed complete; an on-disk profile is not presumed to contain unsaved live changes.
Rewrite permitted external-data references to included members. Refuse missing required dependencies.

Strip source roots, Maven repositories, workspace anchors, credentials, API/server tokens, exchange
grants, machine settings, recovery state and deleted-report bins. Do not carry arbitrary runbooks or
saved analyses. Explicitly selected source coordinates are inert metadata; missing source reads “source
not included”, with no implicit grant or download. Ordinary user-profile anchoring remains unchanged.

Flags and the applied focus travel separately because definitions alone do not preserve them. Flags are
readable today through `context.flags` (§4, EB-0); their `recordIndex` is a live-store index and must be remapped to
the bundled index. Store flag text as attributed testimony with stable record references. Restore it before resolving a report
FINDING section; an unresolved finding is shown as unavailable, not silently omitted. The first walk
step carries the initial view/filter/focus/record. Do not use the recipient's recovery store as transport.

## 7. Verify, open and working copies

`verify` runs headless and offline. Proposed exit codes: 0 for a structurally valid bundle whose
members match; 1 for missing/extra/mismatched members; 2 for unreadable, malformed or unsafe input;
3 for unsupported format/required capability. A zero integrity exit does not mean replay or assertions
passed. Report these separately, including unsigned status and semantic correctness NOT ASSERTED.

Treat every archive as untrusted. Reject absolute/rooted/drive paths, backslashes, traversal, duplicate
normalised or case-colliding paths, symlinks/special entries, unsupported encryption/compression and
undeclared files. Enforce limits while streaming: initially 4,096 entries, 64 MiB/member and 512 MiB
expanded total. These are exactly `template/TemplateArchive`'s `MAX_ENTRIES`, `MAX_ENTRY_BYTES` and
`MAX_EXPANDED_BYTES` (READ). **r3, L-19:** reuse those bounds and audited extraction mechanics, not
`TemplateArchive.install` unchanged: it requires one top-level project directory, while this format has a root
manifest and multiple roots. Its extractor is private and applies template-specific executable permissions. A shared
extraction primitive needs a reviewed split, separate layout/permission policies and tests for both consumers. Nested executable/archive content is outside the initial member allow-list. Hashing a
hostile file does not authorise executing it.

Extraction may write regular files only into private staging, never outside it; no project is opened
until validation succeeds. This is more precise than the original “refuse before any write”: bounded
staging writes are allowed, destination/session changes are not. No `--force` normal open of a failed
bundle in v1. A future forensic inspection mode must stay separate from verified operation.

**Recommendation D-4:** keep the archive and verified evidence immutable; extract a fresh working
copy for each open. Profile/view edits happen in disposable working state. Reopening uses the verified
original, not a cached copy someone edited. If the working evidence itself is changed, detect it and
withdraw the original identity claim. A response is a new capture with a parent link, never a rewrite.

For a client-led delivery, use a dedicated analyser instance/home, or explicitly selected disposable
session, so opening a bundle does not replace the user's unrelated live project. Verify everything
before the project switch, then open project → graph/log → restore flags/view, await completion and
compare the result. Direct file-opening calls use checked absolute extracted paths; saved analyses,
if generated locally, follow their existing project-relative rules. Do not run bundle-supplied analyses.

Any load failure must remain labelled incomplete; stale completions cannot be mistaken for the
requested bundle. The archive's identity and capture-time assessments remain historical. Current
pairing/identity/findings come from the analyser's session and may differ; show both with their scope.

The initial client summary and analyser provenance identify the bundle and working-copy nature.
If current interfaces cannot display a material qualification alongside the inspected result, a small
native surface is required. Terminal text alone is not evidence that a chart is visible or qualified.

## 8. The saved walk

A walk is the author's argument, labelled testimony. It restores the view needed for each step and
points at checkable artefacts; it requires no new model interpretation to play. Next/Previous/Pause
may initially be client controls while the analyser shows real targets and captions.

Use typed steps with IDs, attributed text, desired view and targets: bundled record index plus digest,
GraphML member plus node ID, or chart ID plus declared point/series/note. A step may restore a named
focus, filter or chart window from validated captured state. Translate this bounded format into existing
verbs; do not replay arbitrary action JSON from an attachment. `graph` and `topology` can mutate state,
so approving a verb name alone is not a safe walk policy: validate the allowed fields and meanings.

No project switch, delete, export, source-root grant, free path, shell command or server control is a
walk step. Resolve targets before application. If a client sequence fails after a partial view change,
stop, report the partial state and restore the last complete step before continuing. Do not silently
skip a failed step and call the tour complete. A missing record/node or clipped chart is unavailable;
never substitute a nearby one.

Test actual painted/visible bounds and the real Next/Previous interaction, not just `spotlight.ok`.
The receiver may leave the walk and inspect freely, then explicitly resume. The walk's caption is not
an analyser-established causal verdict. “This record isolates the mapper output” needs a stronger
basis than “this record proves the supplier caused the incident”.

## 9. Replay, oracle and comparison

Distinguish three operations everywhere:

1. **Playback:** navigate recorded audit observations; no application execution.
2. **Re-run:** execute a scenario again with uncontrolled conditions; exact values may differ.
3. **Bounded replay:** feed recorded inputs at a declared boundary into a selected build, with stated
   initial conditions and controls. Reproduction is an observed result of that run, not its assumed name.

First delivery requires a bounded replay demonstration, not a general replay platform. The runner
is explicitly installed/selected locally, outside the analyser's action surface; bundle contents never
select executable code. Opening or walking cannot launch it. A wrapper can pass validated input data
to that runner, but never execute a recipe/script contained in the bundle.

**r3 feasibility result (READ, L-18): replay primitives exist; a ready bundle experiment runner is not established.**
The public framework reference and replay guide were read together with `YamlReplayRunner` at Fluxtion `3de39f55`.
That runner accepts an already-created processor and decoded typed replay records, sets the processor clock before
`onEvent`, and preserves input order. It does not select a build, reset an existing instance or capture a receipt.
Its optional lifecycle calls happen before `runReplay` installs the clock, and its time bounds are exclusive.
Those details must be covered by the chosen runner's lifecycle/clock and selection policy.

Mongoose's `ReplayRecord` route supplies timestamped dispatch, but `EventToQueuePublisher.publishReplay` bypasses
`dataMapper` (`publish` calls it). Feeding already-mapped events through that route cannot demonstrate a feed-mapper
fix. The admin console's replay engine navigates recorded observations; it is playback. The template's generated
hosting guidance explicitly supplies no generic feed reset or recorded-session replay command.

The initial adapter therefore belongs to the application/tooling owner: select a locally approved build, start fresh
isolated state, consume a finite input stream **before the component under test**, establish lifecycle and event clock
policy, check consumed counts and emit the receipt below. Reuse existing dispatch/clock primitives at their actual
boundary; do not substitute a second implementation of the faulty mapper. Pin released dependencies and codec before
claiming a keyless route. This review ran no replay and established no released executable combination: EB-0's measured
original-fails / corrected-passes experiment remains a delivery gate. It cannot be replaced with playback or a moving feed.

For a faulty feed mapper, capture **before the mapper**, not after it. Include finite input order,
initial state/reset, configuration, clock policy, seed/external responses where relevant, and a safe
treatment of side effects. The first runner uses isolated local processes and no live external system.
Unsupported nondeterminism, missing state or incompatible codec yields INCOMPATIBLE/NOT RUN.
Same inputs plus a data-driven clock alone do not guarantee the same output.

Define the oracle before measuring the first run. Prefer an independent expected mapping with fields
and units, plus a malformed-row rejection control that should not change. Record the oracle and runner
versions/digests. For this experiment require:

- Original build repeats the incident assertion failure under the declared conditions.
- Corrected build passes the same incident assertion.
- Unchanged control assertions pass on both builds.
- Inputs/starting conditions/oracle match; allowed changes are the declared build/component and
  necessary configuration. Anything else is a named comparison qualification or refusal.

A receipt records supplied and actually observed loaded build identities separately, conditions/input/
oracle digests, consumed input count, output digests, start/finish, exit/timeout and assertion results.
Unknown loaded identity stays unknown. Imported receipts are declared by their producer, not magically
authenticated by the analyser. Preserve failures and their diagnostics, without credentials.

Comparison has two levels:

| Descriptive comparison | Controlled assertion comparison |
|---|---|
| Parent link, changed member/build/config hashes, node/edge changes, scoped event counts, series summaries, producer findings and flags | Same input/state/oracle with explicitly permitted changes; named assertions and unchanged controls |
| May show bundles with different inputs, labelled different and not a controlled A/B | Refuses to claim equivalence/pass where required identities or evidence are missing |
| Says changed/unchanged/only in A/only in B | Says PASS/FAIL/NOT RUN/INCOMPATIBLE, never global `fixed:true` |

Use analyser services for record/series results and its graph reader for topology; client code assembles
the comparison. Match execution outcomes by input sequence/correlation, not merely wall-clock time or
excerpt row. Missing/duplicate correlation, zero inputs or zero assertions cannot pass. Any ignored
nondeterministic fields are predeclared and shown, with originals retained. Changed graph structure is
disclosed; input compatibility, not graph equality, decides whether replay is applicable.

The response preserves its parent's exact ID and the original input/state/oracle identities, and
adds new build/output/graph, comparison, narrative and walk. If the parent is not present, report the
relationship as declared, not locally checked. No automatic parent download. A recording supplies a
regression fixture; it becomes a test only when an independent expectation is executed against it.

## 10. Security, privacy and optional browser view

v1 acceptance uses synthetic data. `redaction: none` must be explicit. Selecting fewer records is not
redacting their contents; stripping roots does not sanitise event text, screenshots or report prose.
Before real cross-organisation exchange, add a reviewed selection/redaction process and declare how
transformations affect replay, identity and completeness. Encryption, permissions and access control
are later product work; ZIP plus hashes provides neither confidentiality nor access control.

No network service is needed to capture, verify or view a local bundle. Do not upload customer content
as a side effect. Unknown metadata is preserved inertly, not imported as settings. All text, including
captions and filenames, is untrusted display content; no automatic code, commands or HTML interpretation.

**Browser tier is optional.** A self-contained `index.html` can later show manifest, chart, records,
findings, topology, walk and omissions without installation. Inline the displayed data/assets; do not
depend on sibling-file fetch, CDNs or a local server. Keep a restrictive network policy, escape script
terminators and render untrusted records as text. The full artefacts remain canonical.

First implementation may use captured PNGs with record tables. SVG from a shared drawing surface is
preferable when available and tested, but does not automatically prove equal rendering or safe output.
The drafts reference PR #53 and a view-model spike #55. At `main` `d82f1487` **neither is merged**: #53 (the
`Surface`/SVG drawing layer) is an open proposal, retargeted to `main`, whose full CI has not run since; #55 is a draft
spike, green on all 12 checks and marked not for merge. The proposal is therefore right not to make either a delivery
dependency. Native chart data and a browser chart engine
must not become two independent calculation implementations.

The page names its viewer version, shown subset and limitations: no general full-log query, source
navigation or replay. It must not embed its enclosing manifest digest if its own bytes are a hashed
member, creating a cycle; use a capture ID/member digests and an external verifier. Double-click offline
behaviour and hostile-content fixtures must be tested in the named supported browsers before shipping.

## 11. Two-day plan and demonstration

The architecture choice is a first-work-block decision. Do not spend a day polishing packaging while
the replay route or complete-state capture is still assumed.

| Slice | Target | Exit evidence |
|---|---|---|
| EB-0 | First work block: verify runner/input boundary/reset/oracle; test profile, export, full chart/flags/view access and coherent capture; choose client or narrow native adapter | Measured original replay; a portable-state probe; recorded gaps/owners. Identify the “two bugs” from the demo discussion by issue and failing check. |
| EB-1 | Day 1: settle manifest rule; capture/verify, archive guards, portable profile, immutable original and working copy | Member mutation refused; cold open from another path/home; source project unchanged. |
| EB-2 | Day 1/2: walk, restored flags/view, reader-visible scope; avoid a new UI where the client suffices | Real record/chart/topology targets visible at 1200×800; missing target honestly unavailable. |
| EB-3 | Day 2: original and changed build replay, oracle comparison, response capture | Old failure, new pass, stable controls; sender checks the returned bundle. |
| EB-4 | Day 2: regression witnesses, full relevant gates, independent recipient rehearsal and documentation | Reviewer opens A/B; all mandatory checks pass, measured timings and remaining limits recorded. |
| EB-X | Stretch: browser viewer / optional signing only after explicit contract review | Separate acceptance; no substitution for EB-0…4. |

Proposed demonstration, approximately 5½–6 minutes, **not yet measured**:

| Step | Target | Claim allowed |
|---|---|---|
| Ticket arrives with A | 30 s | Bytes match, provenance/limits visible. |
| Open and follow the walk | 90 s | The record and chart expose the incident and implicated component boundary. |
| Select corrected mapper/build in the Spring design | 90 s | Changed configuration and generated wiring are identified. |
| Replay the original inputs | 60 s | The named incident assertion changes from fail to pass; controls stay green. |
| Capture B and attach to PR/ticket | 60 s | Parent link, changed build, result and walk are independently inspectable. |

Use fictional DEMO data. The rejected-row alarm and faulty feed mapper are candidates; select one
coherent incident/oracle, not two unrelated runs merely labelled before/after. Existing risk-A fixtures
may be controls only if their boundary and expected behaviour fit. Do not alter their oracle to fit
the result. Prebuilt committed generated processors are acceptable if labelled; live generation is a
separate authorised provider-dependent step and never implied by switching JARs.

If the time box fails, report what works and the actual blocker. Do not describe input carriage as
replay, a signature as correctness, tool success as a visible spotlight, or an inspection milestone as
the completed owner-requested workflow.

## 12. Acceptance and validation

These are proposed checks, not existing passes. Each closure needs a cheap regression at the real
boundary and, where applicable, a wrong-result witness. Record predictions before trials and misses
afterwards. Targeted development controls use green → named assertion failure → byte-identical restore
→ green. A crash, skip or wrong assertion is not a successful witness.

| ID | Check | Example discriminating control |
|---|---|---|
| A-1 Integrity/identity | One byte changed, member missing/extra/duplicate, unsupported version and duplicate JSON key are reported; repacking preserves identity | Skip one member's hash or reference validation. |
| A-2 Extraction | Traversal, rooted/drive paths, links, collisions and excessive expansion never write outside staging or install a project | Remove descendant check against a bounded scratch fixture. |
| A-3 Coherent capture | Same-length rewrite, competing view/project change, close, timeout or disk failure cannot publish a mixed/partial bundle | Drop capture identity/revision recheck. |
| A-4 Portable fidelity | Different home/path, sender checkout absent: same supported charts/styles/data, focuses, flags, reports and initial view; no machine/secret families | Omit chart style or flags, or restore the sender's anchor. |
| A-5 Excerpt scope | Separated records, duplicate times and hostile delimiter/content payloads round-trip by identity/value; counts, omissions and completeness truthful | Use an incorrect cut that preserves count, or reuse whole-source completeness. |
| A-6 Working copy | Original archive/evidence unchanged after chart/report edits; reopening starts from original; changed working evidence loses original claim | Reuse a modified cached copy or save into verified members. |
| A-7 Walk/display | Actual forward/back interaction lights correct records/chart/topology at default size; dirty starting view does not leak; invalid step stops honestly | Detach target/banner, omit view restore or allow arbitrary step fields. |
| A-8 Replay | Original incident reproduced, corrected assertion passes, independent control holds, actual input boundary/count/reset checked | Supply post-mapper input or drop an event. |
| A-9 Comparison | Wrong parent/input/state/oracle, zero assertions, missing correlation or failed runner cannot produce controlled pass | Bypass the input identity check. |
| A-10 Response | Sender opens B and traces each result to A/B records, changed build and limitations; absent parent remains declared | Replace scoped result with unconditional success or hide absent parent. |
| A-11 Trust wording | Integrity, declared receipt, unsigned/authenticated state and semantic NOT ASSERTED remain distinct; testimony never becomes a computed finding | Promote a prose claim or declared build association to verified execution. |
| A-12 Optional extensions | If shipped, wrong signing key/changed signature and hostile offline HTML fail their own checks | Skip signature validation or interpret record text as markup. |

Archive hostile-content checks include control characters and `</script>`; record bytes remain intact
while rendered text is escaped. Point/value agreement tests are separate from image inspection. A
successful action reply does not prove the recipient saw a legible chart or attached spotlight.

Gates follow the chosen implementation: client unit tests and its real analyser end-to-end checks;
JDK 21 headless and sequential display tests for native changes; existing full CI for affected code;
strict docs, link, whitespace and public-data checks. New frame classes enter both CI lists. A Python
tool still needs wrong-result witnesses and real display acceptance; being outside product code is
not exemption from regression protection.

Report exact candidate versions, OS, test totals/failures/errors/skips, report/orphan counts, mutation
results and any retry. No compiler key, hosted provider, LLM session or participant project is required
for the synthetic acceptance. Inspect every public screenshot. Independent review and release remain
separate from writing this proposal.

## 13. Roadmap and commercial options

After the complete bounded journey, promote the proven contract into native menus/verifier/`bundle`
verb and session-owned opening/walk state. Reuse the format and reader, not a second independently
specified implementation. Keep human/assistant surfaces aligned.

Then add capabilities based on actual exchanges: more replay boundaries, runtime-emitted receipts,
source coordinates with explicit local mapping, field redaction and privacy checks, richer comparison,
signed identities and rotation, optional offline browser/SVG viewer, vendor scenario packs and CI.
The existing M12 diagnose → fix → prove direction is complementary: replay reference, assertion
fixture, comparison and evidence-linked PR become bundle members/relationships, not a competing loop.

Possible packaging, **not approved pricing policy**:

- Open format and low-friction/free receiving and verification encourage exchange.
- Paid authoring/team capabilities: capture, investigation, walks, response comparison and collaboration.
- Enterprise assurance: organisational signing, policy/redaction, approvals, CI, retention, search and
  private experiment catalogues.
- An optional registry could carry identities, timestamps and response relationships while contents stay
  local. Participation, metadata disclosure and trust are explicit; hashes are not assumed non-sensitive.

The value is in producing and checking useful experiments, not making ZIP contents opaque. Repeated
external use and measured support outcomes are evidence of demand; a successful demo alone is not.

## 14. Open decisions and factual checks

Only D-0 is owner direction; all other rows are recommendations to contest before implementation.

| ID | Recommendation / open choice | What settles it |
|---|---|---|
| D-0 | Portable investigation plus bounded replay/comparison required; browser optional | Already stated by owner. Scope changes require an explicit owner decision. |
| D-1 | Internal evidence core with a thin client; external-only is an alternative if complete exports are proved | EB-0 proves complete state/export/fidelity and races; compare integration cost with native CLI-first delivery. |
| D-1a | *r3:* extend existing `context.savedGraphs` for complete definitions; preserve summary fields. This closes a representation gap only. Coherent capture and any filtered-index export remain separate requirements. | L-17 favours the context extension on inspected contract surface. EB-0/A-3 must prove a capture-validity contract before choosing the overall architecture; L-16. |
| D-2 | `.fexp`; integer format v1; exact manifest-byte identity outside manifest; timestamp included | Choose one algorithm and pin cross-reader fixtures. Canonical blanked-ID alternatives remain valid proposals, not simultaneous rules. |
| D-3 | Unsigned first; optional signatures only with a separate reviewed envelope/trust rule | Owner weighs value versus delivery time; reviewer checks signer language and digest-cycle avoidance. |
| D-4 | Fresh disposable working copy; immutable received evidence; no cached edited instance | Show source/destination hashes across edits and reopen; native overlay may later improve UX. |
| D-5 | Complete allow-listed profile plus explicit view/flags; root `.analyser/` layout | Fidelity probe; no missing log/profile keys, unsafe grants or stale saved-state assumptions. |
| D-6 | Typed bounded walk translated to existing verbs; client controls initially | Visible playback and failure handling; full arbitrary saved analyses are not an equivalent safety boundary. |
| D-7 | Keyless application-side runner at a named input boundary; comparison assertions mandatory | Inspect/read/run actual route. Missing runner is a blocker, not a reason to relabel a re-run. |
| D-8 | Synthetic data, whole small incident log by default, canonical excerpting tested | Independence of audit excerpts and replay history; production sharing waits for privacy policy. |
| D-9 | Browser/SVG and general native UI not critical path | Verify available #53/#55 work before counting reuse or claiming unavailable capability. |
| D-10 | No commercial/signing authority commitment | Owner decides after external use; all business models in §13 remain hypotheses. |

Facts to recheck at the implementation head: complete chart/profile/flags API; current `context`
verdict shape; no-log/project-open semantics; raw export grammar/refusals; source/graph digest freshness;
existing UI bugs; replay codec/runner/reset; shared renderer availability. **r2:** the chart/profile/flags API, raw
export and renderer availability are answered by READ evidence in §4 (EB-0) and §10. They still need re-checking at
the implementation head, but they are no longer unknowns. **r3:** L-18 establishes dispatch/clock primitives and the missing mapper/reset/build-selection integration by READ;
a released runner/codec and measured reset/reproduction are still unverified. Capture coherence remains unproved
(L-16); L-19 narrows archive reuse. Earlier draft line numbers
are review pointers, not proof that a later version behaves the same.

During this consolidation, `ProjectProfile.baseDirFor` and `AnalysisSpec` were inspected: project-role
profiles anchor on the parent of `.analyser`; saved analyses gate path parameters and resolve selected
open/source paths against the project. This does **not** prove end-to-end bundle fidelity, safe arbitrary
analyses or complete export APIs. Other input-document measurements remain REPORTED unless reproduced.

Review contributions should name a stable D-/A-/EB- identifier, the concrete failure/cost, evidence
labelled READ/RAN/REPORTED, proposed replacement and delivery impact. Record misses, rejected options
and their reasons. No silent majority vote between models, and no changes to settled owner scope.

## 15. Source versions and reconciliation

All five inputs contributed; none is promoted wholesale into an approved specification.

| Input | Preserved contribution | Reconciled rather than copied verbatim |
|---|---|---|
| [Product positioning](versions/evidence-bundle-positioning.md) | Notebook, support/supplier exchange, original/response chains, four trust questions, multi-party receipts/signing, experiment packs, business options | Association is not proven execution; signatures do not certify correctness; network effects and pricing remain hypotheses. |
| [Prototype proposal](versions/evidence-bundles.md) | Cold-load evidence, portable paths, content excerpts, caveats, immutable investigations, saved walk, offline viewer | Reported runs remain reported; replay is not automatically exact; a fixture needs an oracle; viewer optional per owner. |
| [Tool-first draft A](versions/evidence-bundle-spec.md) | Small client tool, inspectable archive, verification, walk, response/compare commands and rapid delivery | No second audit parser; no unsafe force-open or arbitrary action walks; complete-state interfaces unproven; replay cannot be deferred out of owner scope. |
| [Analyser-native draft B](versions/evidence-bundle-spec-analyser-native.md) | Correct root profile placement, existing analysis/session boundaries, flags gap, archive/CLI rules, source fact map, native roadmap | Retain its client-first alternative but test its speed premise; do not omit walk/replay/comparison; changed working copies cannot retain original verification; mapped claims need checks. |
| [Codex proposal](versions/evidence-bundle-proposal-codex.md) | Required replay scope, independent oracle, captured/current scope, atomic capture, input boundary, bounded walks, acceptance witnesses | A full new native DTO/verb/panel is not assumed necessary on day one; retain its behavioural guarantees and test the lighter implementation first. |

The source drafts disagree on input replay availability, SVG support, native versus external delivery,
identity canonicalisation, signatures, mutable copies and whether a report is evidence. This proposal
keeps those disagreements visible in the relevant sections and decision register. Reports are treated
as attributed argument containers; separately emitted measured tables/results may be evidence with
their own derivation and scope. Hash validation applies to both categories.

**Revision record:**
- r1 consolidates the five archived versions, preserves the owner's first-delivery scope, and proposes one staged
  route with explicit alternatives. No bundle implementation, replay, provider trial or independent acceptance was
  performed as part of this document consolidation.
- r2 (Claude, analyser session) answers four of EB-0's checks by reading the code: flags, raw record text, the
  filtered-view gap, and incomplete chart state. It adds D-1a, identifies the archive limits as `TemplateArchive`'s,
  links "no pending load" to M44.6, and states #53's and #55's status. Reasons for each change are in the
  [discussion log](discussion-log.md). Nothing was run.

- r3 (Codex, also an author of r1) distinguishes per-call facts from coherent capture (L-16), inventories the
  chart-export contract surface (L-17), and checks actual replay boundaries and missing integration (L-18). It narrows
  archive reuse (L-19) and adds arguments without deciding identity or signing policy (L-20, L-21). L-22 records checks
  and limits. Earlier discussion entries and all five source drafts are preserved; no implementation or replay trial.
