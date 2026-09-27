# Evidence bundles — portable investigations and replayable responses

**Status: DISCUSSION DRAFT v0.1, 2026-09-27. Implementation and acceptance not started.**
Target: a usable first delivery in the next two working days, subject to the gates below, not a
calendar-based release promise. This is the consolidated working proposal/specification for review
by multiple authors and reviewers; it is not final, accepted or an instruction to implement. The
[prototype account](../proposals/evidence-bundle/evidence-bundles.md) and
[product positioning](../proposals/evidence-bundle/evidence-bundle-positioning.md) are background,
not additional requirements. Differences are resolved here provisionally and remain open to challenge
in the decision register (§12); do not implement conflicting instructions from the three documents.

**Owner scope, 2026-09-27:** both portable investigations **and** replay/fix/comparison are required
for the first delivery. The browser viewer is optional. This draft proposes deferring signatures,
a registry and a general replay platform. Detailed contracts below are proposed for review, not
previously approved decisions.

## 1. Product and first delivery

**Trust the evidence, not the author.** An investigation ends with evidence someone else can open.
The receiver can follow the author's argument, inspect its records, run a supported incident against
a local build, and return a new bundle with the result. The original is unchanged.

The first delivery is a bounded experimental feature: one processor, one recorded input format,
one explicitly supported application-side replay runner, a synthetic incident, and ordinary text
audit logs. Its file format supports future members without claiming their capabilities today.
No customer production data or participant project is needed for acceptance.

| Required in this delivery | Deferred or optional |
|---|---|
| One portable `.fexp` ZIP, versioned manifest, member hashes, validation | Signatures, trusted organisational identities, timestamp authority |
| Capture and cold open in the analyser, without the author's paths | Arbitrary source inclusion, automatic repository checkout or dependency downloads |
| Read-only original, editable private working state, new response identity | In-place bundle editing, live Follow inside a bundle |
| A saved, attributed walk through records, a chart and topology | General action macros, source/menu spotlights, an AI session during playback |
| Original input stream and starting conditions; local runner re-executes it | Running received code automatically; generic producer/runtime adapters |
| Original and changed build results, an explicit oracle and comparison | Inferring expected behaviour from the observed answer; general semantic equivalence |
| Explicit excerpt omissions and captured/current verdicts | Field redaction engine, customer-data sharing, access-control service |
| Desktop inspection and a return bundle the sender can open | Self-contained browser viewer: stretch only; it cannot replace desktop acceptance |

This is both a collaboration feature and a bounded experiment, not a certification service. A
failed, unsupported or incomplete replay is still a useful result and must not be dressed as a pass.

## 2. Starting point and evidence limits

Source inspection at proposal branch `2d19b9d7` establishes these integration points; it is not a
run of the proposed feature and does not establish the contents of a later release:

| Existing mechanism | Consequence for this work |
|---|---|
| `ProjectProfile.baseDirFor`, `SettingsShare.preview` and `resolveAgainstBase` resolve profile paths from their location | Copying a profile is insufficient. Make bundle member references independent of the sender's checkout. Do not change ordinary profile resolution. |
| `SessionDriver`, `SessionSnapshot`, session nodes/generated processor | New bundle lifecycle decisions belong here under CLAUDE.md rule 9. UI and tools render one published state. |
| `RecordExporter` preserves raw text and refuses YAML export when a reader's grammar cannot round-trip | Reuse that refusal. v1 supports the text reader only; binary, plugin and rolled stores are explicitly unsupported for capture. |
| `GraphSpec` carries style, open state, expressions, annotations and external data references | Preserve supported fields. Missing external inputs cannot silently produce a different chart. |
| `ChartPanel.toImage` uses the existing Java2D paint path | PNG is the bounded implementation choice. An SVG surface abstraction is described in the background but was not found in this checkout; do not make delivery depend on it. |
| Existing `spotlight`, topology focus and record navigation | Reuse their resolution and refusal semantics. A walk is data, not a sequence of unrestricted tool calls. |
| Existing archive staging/limits in `TemplateArchive`, path checks in `ExportGuard` | Reuse reviewed primitives where applicable; a template installer is not already a bundle loader. |

The prototype's two cold-load results and upstream replay capability are **reported evidence**.
The exact runner, released dependencies, reset semantics and input codec must be verified in EB-0.
Read the framework reference and the actual runner before asserting execution or clock semantics.
Audit playback in the analyser is not input re-execution. This spec does not claim that the current
analyser already implements either a bundle loader or an application replay service.

## 3. The acceptance journey

Use a fictional feed mapper whose faulty build changes a valid input's meaning; retain a malformed
row as an unchanged rejection control. Choose the exact defect and oracle before collecting output.
The capture boundary must be **before the mapper**: replaying already-mapped events would bypass the
component whose replacement this demonstration is supposed to test.

1. Author captures incident A with the original inputs, audit output, GraphML, chart, walk and caveats.
2. Receiver opens A in a clean analyser home on a different absolute path with the sender's checkout
   unavailable. Next/Previous shows the record, chart and topology without a model session.
3. Receiver invokes the trusted local runner explicitly, using A's inputs and initial conditions
   against the original build. A predeclared assertion fails in the expected way.
4. Receiver changes the mapper selection/configuration and uses the corrected build. The same
   inputs, initial conditions and assertion now pass; the rejection control still behaves as declared.
5. Receiver captures response B, linked to A, including the new output, build/configuration identities,
   comparison and walk. Sender opens B and inspects the differences; A's hash is unchanged.

The demo may use two committed generated processors and locally built JARs. If generation was not
performed live, say so. Live XML regeneration is an optional demonstration requiring its own authorised
provider run; it is not implied by swapping prebuilt artefacts. No release gate here spends a key.

Measure the five steps separately. The proposed 30/90/90/60/60-second timings are demonstration
targets, not measured results. A credible completion matters more than fitting a predetermined time.

## 4. Format v1 and identity

`.fexp` is a ZIP containing `manifest.json` and declared regular-file members. No executable member
is run on open. Suggested member names below are conventions; the manifest's IDs and roles resolve
references, so a consumer never infers a role from a filename.

```text
manifest.json
audit/excerpt.yaml
audit/record-map.json
topology/processor.graphml
analysis/state.json
analysis/walk.json
analysis/claims.json
replay/inputs.dat
replay/conditions.json
execution/receipt.json
comparison/result.json             # response only
charts/incident.png                # optional convenience image
reports/investigation.pdf          # optional, separately scoped
index.html                        # optional viewer
```

### Manifest contract

Use UTF-8 JSON. Reject duplicate JSON keys. `format` is `fluxtion-evidence-bundle`, `version` is
integer `1`. Required fields are `capturedAt` (UTC timestamp), `createdWith` (analyser version and
build revision), `members`, `capture`, `capabilities`, `claims`, and `limitations` (possibly empty).
`creator` is optional, explicitly self-declared. Never infer an organisational identity from a path.

Each member has unique `id`, `path`, `role`, `mediaType`, `byteLength`, lowercase SHA-256 `sha256`
and `required` boolean. Optional members not supported by a reader are listed as unavailable;
unknown required roles or a newer manifest version refuse open without changing the current session.
Every file other than the manifest is inventoried and hashed, including walks, receipts and the
optional HTML. No unlisted files or duplicate IDs/paths are accepted. The manifest itself is not a
member, avoiding a digest cycle.

**Bundle identity is SHA-256 of the exact stored manifest bytes**, not of a ZIP's timestamps or
compression, and not of parsed/reformatted JSON. A writer emits stable ordered JSON; a reader hashes
the original bytes. Repacking unchanged members preserves identity; rewriting the manifest creates
a different identity. No canonical-JSON signing algorithm is needed in v1. Unknown optional fields
are preserved when copied to a response; the received manifest is never rewritten.

`capture` names member IDs for audit, record map, topology, analysis state and walk; the original
source digest (or `unavailable` with a reason), selected count, indexed population at capture,
pending-tail disposition, selection rule and captured session verdicts with their scope/basis.
Do not include private absolute filenames. Original on-disk byte count and indexed-record count
are different quantities and must be named separately.

`capabilities` declares `inspection`, `walk` and `replay` as `present` or `absent`, with required
member IDs and a reason when absent. A receiver reports replay readiness separately: `not assessed`,
`ready` or `incompatible`, naming checks and missing dependencies. A manifest's `present` is not a
verified claim that a local build can execute it. Inspection-only bundles are valid, but the acceptance
pair A/B must carry replay evidence. Neither the format nor the demo silently treats audit as input.

A response adds `respondsTo` with A's exact manifest digest and `comparison` naming its result
member. Bundle identity, original input digest and result membership are checked mechanically;
absence of A means the relationship is declared but not locally verified. Opening a response never
downloads its parent.

### Verification language

Keep these independent on the bundle panel, in `context`, and in any exported comparison:

| Question | Permitted claim in v1 |
|---|---|
| Do included bytes match this manifest? | `Member hashes match` / named mismatch. This does not establish authenticity. |
| Who captured or ran this? | Self-declared identity; `Unsigned`. No trusted signer is implemented. |
| Which software produced the output? | Receipt's declared build/configuration digests; local matching bytes verified separately. No runtime attestation is implied. |
| Does the graph describe the captured log? | Session's pairing/coverage assessment at its stated scope, separate from the capture-time assessment. |
| Did a comparison run? | Passed/failed/not run/incompatible, with inputs, scope, oracle, runner version and result references. |
| Is the software correct generally? | Not established by this experiment. |

An attacker able to rewrite both files and manifest can create a different internally consistent
bundle. Unsigned hashes detect changes relative to an already-known identity, not forged authorship.
An original reference on a ticket is useful independently of any future signing service.

## 5. Capture, excerpts and portable analysis

Capture is a transaction, not a copy of live settings. v1 requires a paused, stable text log; Follow
must be turned off explicitly before capture. Refuse while a load/evidence refresh is pending, or the
session reports a changed/unverified source. A missing stream-end claim is still allowed and remains
unknown; lack of completeness is not a fabricated file-identity verdict.

Capture the store reference, session snapshot, filter copy, chart/focus/report definitions, walk and
scan bound in one EDT operation. Copy/hash off the EDT with a read lease or equivalent lifetime
protection. Verify the source and captured state still match at completion, including same-length
rewrites and competing graph/filter/project changes. Publish only after all members and references
validate. On failure, discard staging and leave the destination and live project untouched. A source
changing during capture refuses rather than exporting a mixed run. No final-looking partial archive.

v1 selects whole records from an explicit list of zero-based source record indices, or the existing
filter evaluated once at the captured bound. This permits content-based selection without inventing
a new query language. Never cut raw text by time alone or slice partial records. Record map entries
carry `sourceIndex`, `bundleIndex` and raw-record digest; walks refer to the bundled index and digest.
Indices remain ordered and unique. Record export may add framing separators, not change payloads.

Declare the selection expression or explicit index set, selected/indexed counts, source time range
when known, and what is omitted. Omitted records cannot support an absence-of-execution claim.
Preserve producer completeness/framing findings as captured facts; do not copy a whole-file stream-end
claim onto an excerpt and call the excerpt complete. Receiver diagnostics concern the excerpt and are
shown separately. A response and its charts must say which population they concern.

The **full replay input stream and initial state required by the incident** are independent members.
The small explanatory audit excerpt does not imply that upstream input history is dispensable.

Analysis state is a versioned, allow-listed DTO, **not** an export of every profile key. It carries
supported chart definitions (including style/open state/annotations), named focuses, selected record,
view filter and chosen report definitions. Bundle assets use member IDs, never host paths, home
expansion, workspace anchors or network URLs. External series/markers must be explicitly included and
rewritten to member references, or the dependent chart must be excluded with a visible reason. The
required demo chart cannot be excluded. Existing profile path semantics remain unchanged.

Do not include credentials, recovery data, deleted-report bins, server tokens, machine settings,
runbooks, exchange-directory grants or arbitrary source roots. Optional source coordinates are inert
repository/revision/class metadata, not automatic fetches or read grants. v1 can state `Source not
included`; source navigation is not needed to inspect the recorded experiment.

**Chart scope is explicit.** v1 recomputes selected charts against the bundled audit records, using
the recorded chart calls and resolution policy. A source chart requiring omitted history, external
inputs or rolling-window warm-up must either include the needed records or be refused as unsupported
for capture. It must not be presented as unchanged. Compare resulting point values/counts and range
against the source-scoped extraction before publishing. Reports similarly render from the bundled
population; unrelated existing PDFs may be included only as attributed attachments with their own
scope, never as freshly verified derivatives.

## 6. Open, read-only inspection and the walk

Validate archive structure, hashes, schema, mandatory capabilities and all member references before
changing the session. Reject absolute/rooted/drive paths, backslashes, traversal, duplicate normalised
paths, case-colliding paths, symlinks/special entries, encrypted entries and unsupported compression.
Initial limits: 4,096 entries, 64 MiB per member, 512 MiB expanded total; enforce while streaming, not
only from ZIP metadata. Refuse larger bundles with a reason. They are not silently truncated.

Extract into a private staging directory using canonical descendant checks and regular-file creation;
validate before installation. Failure leaves the current log, project and graph unchanged. A later
completion from an earlier open cannot install over a newer request. A missing source repository is
allowed; a missing required log/topology/member is not. A pairing mismatch is shown honestly, not
converted into archive corruption or a matching verdict.

Open into an isolated bundle workspace. The original archive and extracted evidence members remain
unchanged; editable filters, chart experiments and notes live in a separate working overlay. Disable
Follow and project auto-save into the received bundle. Closing discards that overlay unless the user
captures a new bundle. Restoring the prior project is an explicit ordinary open, not an implicit
recovery-store rewrite. Bundle opening grants access only to its validated members.

The desktop has a Bundle panel showing identity, unsigned status, included/omitted data, captured and
current assessments, replay readiness and response relationship. It renders the same model as
`context.bundle`. A new Walk panel supplies Next/Previous and attributed captions; playback needs no
LLM. The Project panel remains reveal-only.

Walk v1 is an ordered array with stable step IDs, author caption, desired view and typed targets:
record (bundled index + digest), topology (node ID + graph member), or chart (chart ID + time/value
anchor). A step may name a saved topology focus and chart window; no arbitrary action JSON, paths,
shell commands, menu actions, provider calls or source access. Resolve all prerequisites before
changing the view. Missing/ambiguous targets mark the step unavailable with a reason; never substitute
the nearest record/node or claim it was lit. Successful steps restore view then use existing spotlight
geometry. User inspection may interrupt; resuming reapplies the selected step explicitly.

## 7. Replay and response comparison — required, bounded

The analyser **does not acquire server-mutating verbs or execute bundled recipes**. Opening, viewing
or stepping a bundle never starts an application. Replay is an explicit invocation of a separately
installed, locally trusted application-side runner. The analyser imports its result; runner discovery
must not execute a path supplied by the bundle. Recipe text is testimony, displayed inertly.

EB-0 must identify one existing public, keyless route and pin its version/command and input codec in
the implementation handoff. If no route satisfies the checks below, implement the narrow adapter in
its owning application/tooling repository or report that dependency as a blocker. Do not substitute
visual audit playback or quietly drop replay from the first-delivery requirement.

The runner takes an explicitly selected local build, the original input member, conditions and oracle.
Conditions name input boundary/codec, initial state or reset procedure, event order, clock policy,
seed where relevant, configuration, dependencies and treatment of external effects. v1 uses a fresh
isolated process, finite inputs and no live external side effects. Missing state, incompatible codec,
unavailable dependency or unsupported nondeterminism is `incompatible`, not a green empty run.

Its receipt records runner/version, supplied and actual loaded build/configuration digests where
observable (otherwise declared/not established), input/initial-state/oracle digests, consumed input
count, started/finished timestamps, exit status and output member digests. Include failures/timeouts
and stderr as scoped diagnostic artefacts without secrets. A receipt from another author remains an
unsigned assertion; the receiver's independent local run is distinct evidence.

Define the oracle before the first measured run. For the mapper demo, use an independent expected
mapping with declared fields/units plus an unchanged malformed-row rejection assertion. The comparison
checks identical inputs, conditions and oracle; explicitly permitted changes are build/component and
their necessary configuration. Other differences make results `not comparable`, with reasons. Match
outputs by recorded input sequence/correlation identity, not log timestamp or excerpt row number.
Missing/duplicate output correlations refuse the affected comparison. Every result names the member
and record establishing it. Empty inputs/zero assertions cannot pass.

Allow only a documented list of nondeterministic output fields to be excluded from comparison;
show that list and preserve the originals. Do not erase real output differences to obtain equality.
The expected outcome is **original build fails the incident assertion; corrected build passes; the
control assertion passes on both**. Repeating the original build with the same conditions is a
separate reproducibility check. A failed original replay means reproduction was not established.

B keeps A's manifest digest, original input/conditions/oracle identities, new build/configuration,
new log/graph and assertions with PASS/FAIL/NOT RUN. The comparison is a report of this experiment,
not a global `fixed:true` bit. A different graph is permitted and disclosed; input compatibility is
checked explicitly rather than assuming changed topology itself makes replay invalid.

## 8. Implementation boundaries and proposed surfaces

This section proposes the smallest consistent product surface; review it before implementation.
Add one `bundle` verb with mutually exclusive `capture`, `open`, `inspect` and `compare` operations.
It reads/writes through existing file-grant boundaries and refuses mixed operations/unknown keys before
effects. `compare` compares captured result evidence; it does not launch replay. Add a bounded `walk`
operation within this verb for storing/stepping the typed walk, with the same validation. Update the
pinned verb inventory, manifest, prompt, help and human surfaces together; do not smuggle a new verb
through a test relaxation. No generic script executor.

Project menu actions: Open evidence bundle…, Capture evidence bundle…, Capture response bundle…;
Bundle/Walk panels provide inspection and navigation. Update the menu inventory/documentation guards
with their exact labels. Capturing a response requires a parent and comparison result, not merely a
caption saying it is fixed. Reuse the existing exchange guard for assistant writes; a human chooser
grants only its chosen destination. The owning operation publishes completion or refusal only when
the archive is atomically installed and validated.

Session nodes own bundle identity, pending/open state, capture generation and walk-step completion.
Filesystem/render adapters report facts and execute requested effects. MainFrame, MCP, reports and
panels render the published snapshot. Do not add a parallel bundle controller deciding session state.
Generated processor changes must be regenerated through the authorised repository workflow; if that
requires a provider/key, obtain the separate authorisation before that implementation step. This
documentation task performs no generation and grants no provider access.

## 9. Delivery plan and cut line

The sequence below is a two-day target for a narrow path, not a claim that every unknown is solved.
If EB-0 fails, report it early; do not spend the second day polishing a replay that cannot run.

| Slice | Deliverable and dependencies | Acceptance before moving on |
|---|---|---|
| EB-0 — first work block | Pin runner/codec, inspect execution contract, choose synthetic incident/oracle, reproduce on original build. Identify the two bugs mentioned in the demo discussion by issue and failing check; no unidentified bug is declared fixed. | An actual keyless replay, input at the mapper boundary, repeatable original result. Record missing dependencies and measured durations. |
| EB-1 — day-one core | Manifest/parser/validator, bounded archive, capture transaction, portable analysis DTO, session integration; depends on reviewed format. | A round-trip on another path/home, mutation refusal, original/host project unchanged; supported chart matches its captured scope. |
| EB-2 — day-one journey | Bundle panel, readonly workspace/overlay, typed walk and tool/human surfaces. Depends on EB-1. | A cold recipient steps the record/chart/topology and sees qualifications at 1200×800, with no model or sender checkout. |
| EB-3 — day-two closed loop | Bounded runner result import, assertion comparison, response capture and parent relationship. Depends on EB-0 and EB-1. | Original failure, corrected pass, unchanged control; sender independently opens B and rechecks results. |
| EB-4 — day-two hardening/review | Negative fixtures, targeted controls, docs/screenshots, full headless/display CI, timed rehearsal. | All mandatory checks below pass; independent reviewer opens A and B. |
| EB-X — stretch only | Static browser viewer of the same artefacts. | Does not delay or replace EB-0…4; test separately if shipped. |

**Cut line:** no signatures/registry, general diff engine, field-redaction UI, source distribution,
arbitrary reader support, arbitrary runtime replay, live capture or new SVG renderer. Do not cut
identity, read-only originals, atomic capture, missing-input refusal, the independent oracle, the
saved walk or the replay/response acceptance to meet the date. If needed, ship later or ask the owner
to change scope explicitly. An inspection-only prototype is not completion of this delivery.

## 10. Acceptance and regression witnesses

Names below are proposed checks, not tests already present. Each behaviour needs a regression at its
production boundary. Before fixing/implementing, record predicted outcomes. For the listed controls,
show green → named assertion failure (not error/skip) → byte-identical source/class restore → green.
Use the fast targeted harness while developing; CI runs the complete gate. Never restore mutations
with a command that discards unrelated work.

| ID / proposed check | Concrete acceptance | Wrong-result witness |
|---|---|---|
| A1 `BundleIntegrityTest` | Alter one record/chart/member after capture; validation names it and leaves current session intact. Changing a manifest creates a new identity, not authenticated authorship. | Skip digest comparison: modified bytes must be wrongly accepted and the named refusal assertion fail. |
| A2 `BundleArchiveTest` | Traversal, rooted paths, aliases/case collisions, symlink, duplicate entry, excessive expansion and missing member refuse before install. | Remove descendant check using a bounded scratch fixture; assert no outside write and refusal. |
| A3 `BundleCaptureTest` | Rewrite same-length source or switch graph/filter/project during capture; no published mixed archive. Include close during copy and an I/O failure. | Drop capture-generation/fingerprint check; stale-capture refusal assertion fails. |
| A4 `BundleRoundTripTest` | Two isolated homes, source checkout absent, moved archive: same supported chart style/points/window, record anchor and focus; no private paths or secret/profile families exported. | Restore ordinary profile anchoring or omit style: the corresponding equality fails. |
| A5 `BundleExcerptTest` | Select separated records including identical timestamps; exact record map, counts and omissions; no inherited whole-file completeness; missing warm-up refuses required chart. | Omit record mapping/scope check; named identity or chart agreement assertion fails. |
| A6 `BundleReadOnlyTest` | Open A, change chart/filter/walk working state, close; archive and evidence hashes unchanged, host project unchanged. Capture B has a new identity. | Route an edit to received evidence instead of overlay; original-hash assertion fails. |
| A7 `BundleWalkFrameTest` | Real Next/Previous clicks from dirty session show actual record/chart/topology targets at default window; missing target says unavailable, with no guessed highlight. | Detach walk/spotlight surface or bypass target validation; on-screen/refusal assertion fails. |
| A8 `ReplayIncidentTest` (runner owner) | Same original input/conditions/oracle: old fails, new passes, rejection control passes twice; repeat old result. No provider/network dependency. | Replay mapped output instead of raw input, or drop an input; boundary/count assertion fails. |
| A9 `BundleComparisonTest` | Different input/state/oracle, zero assertions, missing correlation or failed runner never reports comparable/pass. A/B member references resolve. | Bypass one input-digest comparison; named not-comparable assertion fails. |
| A10 `BundleResponseFrameTest` | Sender opens B, follows comparison to A's and B's records, sees build change/limits and absent-source label. Missing parent stays declared, not verified. | Substitute global success for scoped outcome or suppress missing-parent label; named assertion fails. |

Also check unsupported reader/manifest version, unknown required role, no automatic script execution,
and malicious caption/member text displayed inertly. The same opened state must appear in UI and
`context.bundle`, including pending/refused operations. Frame tests go in both CI lists and run
sequentially. Inspect captured chart/report images, not only tool exit status; state any focus skip
and require CI's supported display gate to execute its cases.

Release evidence records head/version, JDK/OS, mapped Surefire totals/failures/errors/skips and orphan
reports, display counts, targeted controls, full CI collector result, strict MkDocs, whitespace and
public-data sweeps. Use an isolated home and synthetic data; inspect every screenshot. Existing gate
counts are discovered at the candidate, not copied from another release. Docs and tracker distinguish
implemented, locally verified, independently accepted and released. No capability is marked shipped
by this spec or by a successful rehearsal alone.

## 11. Optional browser tier and later commercial work

If included, `index.html` is self-contained: no CDN, fetch of sibling files, network requests or
automatic external navigation. Inline only explicitly selected data, label its omissions, escape
HTML/script terminators and treat all captions as text. Use captured images plus record tables and
walk anchors in v1; no new chart computation in JavaScript. The page identifies the snapshot it
describes; since it is itself a hashed member, use a capture ID/data digests rather than embedding
its enclosing manifest digest and creating a hash cycle. It cannot independently authenticate itself.

Test offline file opening in the named supported browsers, content escaping, readable chart/records
and a step-through without a model. If not tested, omit the page and label browser viewing unavailable.

Later stages add redaction with transformed-input compatibility checks; producer run receipts binding
actual loaded code; signatures and organisational key trust; a richer comparison UI; browser vector
rendering; more runners/readers; CI/vendor experiment packs. No hidden execution, network content
upload or pricing enforcement enters through this first format.

Commercial hypothesis: a ticket recipient becomes a user because they can inspect and return a
checkable experiment. Measure cold-open success, time to locate the supporting record, reproduction
rate, time to produce/verify a response, and repeat use by external teams. Free receiving/open format,
paid authoring/team/assurance features remain packaging options, not policy approved by this spec.

## 12. Decision register and review handoff

Review the first-delivery contract and its limits, not the full long-term positioning as a promise.
Agreement between several AI reviewers is not evidence. Each challenge should name the paragraph,
concrete failure or cost, evidence marked READ/RAN/REPORTED, proposed replacement and acceptance impact.
Keep rejected alternatives and their reasons here briefly, rather than growing parallel specifications.
The owner resolves policy; source and executable checks resolve factual disputes. Draft changes do not
turn themselves into accepted decisions. If delivery scope must change, ask the owner explicitly.

| ID | Current proposed choice | What would settle it / alternative to examine | State |
|---|---|---|---|
| D-EB1 | Portable investigation **and** bounded replay/comparison; browser optional | Owner's explicit scope in this conversation. The time budget is a target, not permission to omit replay. | Owner direction recorded |
| D-EB2 | One text-log reader, processor, finite input codec and local runner first | EB-0 source/run evidence; challenge any limitation unnecessary for a two-day implementation. | Proposed |
| D-EB3 | Exact manifest-byte digest and member hashes; unsigned v1 | Prove response linkage and tamper checks without a hash cycle. Compare cost of canonical JSON/signing now; do not label hashes provenance attestation. | Proposed |
| D-EB4 | Read-only received evidence plus private overlay; session owns lifecycle | Check reuse of current session/profile model and failure rollback. A simpler design is welcome if it preserves originals and has one state owner. | Proposed |
| D-EB5 | Typed saved walk, including record/chart/topology | Run the cold-recipient acceptance; narrower targets are acceptable only if the required story remains inspectable. | Proposed |
| D-EB6 | New bounded `bundle` verb and menu actions | Review against pinned verb conventions; extending existing verbs or an initial tool wrapper is an alternative if human/tool parity and whole-or-refused behaviour remain explicit. | Proposed |
| D-EB7 | Explicit trusted application runner; analyser imports its results | Verify the actual upstream route. Preserve the standing no-server-mutation boundary; challenge adapter cost and receipt claims. | Proposed |
| D-EB8 | Recompute scoped charts, require input history separately; no arbitrary redaction yet | Test stateful/rolling inputs and point equality. Decide whether larger retained evidence is simpler than excerpt capture in the first implementation. | Proposed |
| D-EB9 | PNG-first; no mandatory SVG/browser work or signing service | Inspect available rendering work outside this checkout before estimating reuse. A tested existing implementation may change the cost. | Proposed |
| D-EB10 | Synthetic data first; no pricing/registry commitment | Owner decides customer-data/policy/commercial expansion after the complete journey works. | Deferred |

Check in particular the snapshot transaction, portable path boundary, independent oracle and
original/response identity chain. The acceptance checks, fields and APIs above may change during this
review; preserve stable EB/A/D identifiers where possible so findings remain traceable.

Still to establish in implementation: the exact runner/released codec and its reset/clock behaviour;
availability of complete pre-mapper inputs; the named bugs and whether they still affect the candidate;
generation prerequisites for session changes; measured capture/replay performance. None is silently
assumed from a prototype claim. If they prevent the bounded journey, name the blocker and its owner.

Documentation preparation checked the listed analyser source points and reconciled both inputs; it
did not run a bundle prototype, provider, replay, model session or production experiment. No claim of
exact reproducibility or current SVG support is imported from background prose without verification.
