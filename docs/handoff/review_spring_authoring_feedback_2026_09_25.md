# Review: guided Spring authoring feedback, 2026-09-25

**Verdict: actionable friction, with several proposed diagnoses narrowed.** The strongest
common failure is the second structural edit: stale generated code prevents regeneration,
a rename leaves ownership debt, and the analyser can retain a previous Java snapshot.
The default malformed-input fallback also merits an early fix: it manufactures business data.

**Updated during review:** feedback grew from twenty to twenty-four items; the addition
to item 19 and items 21–24 are assessed below. No new client trial was run.

The [proposed specification](../specs/spec-spring-authoring-edit-loop.md) sets the contracts,
acceptances and wrong-result controls. Nothing is implemented or declared closed by this review.

**Revision 4, 2026-09-26:** the independent review at `075107ef` required corrections to
this report and the proposal's premises. The text below incorporates those corrections;
historical verification sections retain their original dates/counts, not v4 verification.
The response at the end distinguishes fresh source inspection from unrerun testimony.

## Evidence boundary

I read the owner-provided running project's `FLUXTION-FEEDBACK.md` and `SESSION-NOTES.md`,
then inspected relevant project files and product source. I did not drive that session,
re-run its application, call a provider, inspect credentials, or alter its workspace.
It was a **guided** session, not an independent uncoached trial. Successful scenario results
and original failures below are participant testimony unless explicitly marked otherwise.

At inspection on 2026-09-25 08:51 UTC:

| Local document | Bytes | SHA-256 |
|---|---:|---|
| FLUXTION-FEEDBACK.md | 25,222 | `2d4d88f23dce78dd95c466d7574ae6545a690390ddfae3e38b3892b845774680` |
| SESSION-NOTES.md | 9,848 | `95946fedb7b31bc06cd04d0a9d19a1889e3060077a5e1de3f6cdb9f43e64777c` |

These hashes identify what was read; they are not public links or a claim that the documents
are committed here. Raw feedback, profiles, logs and private paths are deliberately not copied
into this public repository. The active project's current files cannot prove their original
archive bytes. Preserve relevant sanitised before/after fixtures before implementation trials.

Baselines inspected: analyser `710dc2ca` (1.20.1), starter 1.0.74 release source, and playground
release source `5d6a38a`. The latter identifies inspected code, not the session's unrecorded
website deployment. Session-reported pins: BOM/starter 1.0.74, runtime 1.0.16, plugin 1.3.0,
Mongoose 1.0.29, plugins 1.0.44, GraalVM 25.0.2. Exact analyser binary and ZIP are unknown.
No live-service or later-release behaviour is inferred from those source reads.

## All twenty-four observations, disposition and owner

**READ** means code/files inspected, not reproduced behaviour. **REPORTED** means the
participant's account; a plausible diagnosis has not been promoted to a reproduced result.
Specification letters refer to the linked proposal.

| Feedback | Assessment | Routing / required work |
|---|---|---|
| 1 — compile ordering | **READ + REPORTED.** Current POM runs scan at `process-classes`, followed by compile-generated; ordinary compile still precedes it. Constructor/rename failures are reported, not rerun. | **A / UP-FLX-21**, already open. Exclude generated code only during profile model compilation, then scan and compile-generated. The supplier uses reflection; stage the scan output, not deletion of the prior source. |
| 2 — rename debt | **REPORTED**, with the session notes explicitly retaining the old ownership debt. Conflict refusal is desirable; repeated opt-out is not repair. | **D**, compiler/starter ownership migration, including the next default regenerate and new-node creation. |
| 3 — stale Java | **READ** identifies two panes and a service cache: the Source tab already rereads on selected-processor refresh, the embedded Topology pane fills only on first use, and a pane reread leaves the service model stale. Repeated session symptoms remain **REPORTED**. | **C**, analyser ordinary-navigation freshness. Keep source/run identity separate. |
| 4 — conflict advice | Error text is **REPORTED**. The proposed universal field-name rule is too strong; existing bindings may differ from bean ids. | **A**, actionable diagnostics with only established declarations suggested; also UP-FLX-32's wiring guidance. |
| 5 — foreign recovery offer | **READ + REPORTED.** The current key is the profile's real path, so replacement at the same path aliases the old capture. The participant's actual deletion history was not observed. | **E**, recreated-path fixture first, durable profile identity and capture-time disclosure, then pending-I/O/switch controls; no automatic restore. |
| 6 — fabricated zero event | **READ**: blank/short rows produce zero events; non-numeric rows throw and are dropped by core without audit rejection. The session later uses a replacement mapper. | **B**, remove fabrication together with approved D1's processor-audited rejection event, not the stdout-only unknown handler. |
| 7 — cumulative capture | **REPORTED**; persistence is not inherently wrong. Moving capture aside was a manual session workaround. | **H / MA-2 / OD-5**, explicit export scope and authoritative run boundary; no implicit deletion. |
| 8 — script permissions | **READ**: TemplateArchive deliberately applies a fixed executable list and omits setup/validate/generate; the template marks them executable. This is an analyser installer defect, independently of direct ZIP modes. | **G**, analyser install test/control removing generate.sh from the list. Separate direct-browser ZIP check; D6 approves documenting absent Windows entry points for this pass. |
| 9 — CLI help/link | **READ**: starter special-cases help only as the first argument. `link` encodes the XML into a browser URL; it does not migrate ownership. No CLI was executed here. | **G**, per-command help and accurate link documentation, no project mutation. |
| 10 — incomplete/drifting guides | **READ**: truncated RUNBOOK sentence exists in project and template emitter. Local starter jar has no contract document entry. Version-table confusion is **REPORTED**. | **G**, immutable matching contract, complete workflow and conflict-specific repair guidance. |
| 11 — read grants | **READ**: DesignFiles explicitly makes project a relative base, not permission. Current profile has roots added by the session; it does not prove original grants for every file. | **E / M68.5**, separate resolution from authorisation; proposed role grants require approval. |
| 12 — overlapping timestamps | **REPORTED**; same-millisecond chart readability is already an open requirement. | **H**, existing record-order x-axis item; retain original record identity, no producer sleeps. |
| 13 — missing stdout audit | **REPORTED**, consistent with the already-recorded listener replacement defect. An absent stdout match does not establish no execution. | **H / MA-5**, released-version fan-out and restoration acceptance; do not duplicate the analyser's diagnostic work. |
| 14 — authoring patterns | Tips/results are **REPORTED**; some are explicitly untested. Public vendor-composition guidance already exists, so “nowhere” is too broad. | **F/G**, improve generated entry points and executable patterns, not new advice to bypass name validation. |
| 15 — validate versus generation/cost | **READ**: RUNBOOK explicitly promises XML-only validation. Which failures reached/charged a provider is unknown. | **F**, separate model preflight and evidence-based request/billing receipt, preserving inert validation. |
| 16 — canonical deduped id | **REPORTED**, not independently established in this pass. Changing naming precedence could break consumers. | **F**, expose aliases/selected id; D4 retains current precedence, checked with a reversed-order fixture. |
| 17 — large context | **REPORTED**; existing read projection does not solve context overhead. No byte/token measurements made here. | **H**, opt-in context sections with same-state equivalence and verdict qualifications; preserve default compatibility. |
| 18 — descriptor copy drift | **READ**: hosting guide links the config and also calls its copied YAML effective. Historical drift is **REPORTED**. | **G**, link the actual file, label any example historical; do not duplicate mutable authority. |
| 19 — vacuous starter test | **READ**: supplied test only asserts a non-null processor; POM already declares mongoose-test-support. The update correctly points to existing infrastructure. | **G**, use the actual hosted wiring with the harness plus an independent behavioural table and wrong-result control; keyless on the shipped processor. |
| 20 — vendor sources | **READ**: MavenSourceResolver already searches local source jars. Current vendor POM has no source-archive attachment configuration; that alone does not prove none was installed. | **C/G**, source availability/discovery/refresh and supplier instructions, not a new resolver by assumption. |
| 21 — mapper extension/discovery | **READ**: core 1.0.29 exposes a generic per-feed mapper; plugins 1.0.44 has TypeSerialiser. Its documented discriminator/configuration differs from its implementation. | **G1**, teach composition with executable pinned examples and visible rejection; not just an extra link to the current recipe. |
| 22 — replay contradictions | **READ**: current runbook disclaims a supplied replay command/recorder but its descriptor comment promises deterministic replay. Connector capabilities do not resolve that conflict. | **G2 / H**, a configuration-specific capability table and actual replay acceptance; keep audit inspection distinct from input reconstruction. |
| 23 — runtime loading versus AOT | **READ**: plugins 1.0.44 Spring loader has compile and interpreter branches and is preview-marked. No route was run. | **G3**, compare build-time AOT, load-time compile and load-time interpretation, with measured prerequisites and narrowly tested equivalence. |
| 24 — plugin-site version | **READ + metadata fetched**: site label comes from plugin_version 1.0.37; project pin is 1.0.44. Repsy metadata checked for v4 reports release 1.0.45; that is the deployment route, not Maven Central. | **G4**, versioned documentation and generated labels, not a one-off “latest” number replacement. |

## Corrections that matter before implementation

1. **The dependency premise is corrected.** The emitted supplier loads the processor by name
   at runtime, not an import. Exclude the generated package only in the generate profile's
   initial compile; compile nodes/supplier, scan, then compile-generated. Default keyless
   compilation retains the committed processor. Scan's direct generated-source write needs
   staging; reconciliation already has its own snapshot/rollback. A customer's static import
   is a separate support choice, not this template's dependency.
2. **`link` is not rename.** It opens the authored design in the browser. A new ownership
   operation requires its own explicit mapping and safety contract.
3. **Source freshness has a concrete cache boundary.**
   [SourcePanel](../../src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/SourcePanel.java)
   `navigate` can skip an existing nonempty FQN, but `showSelectedProcessor` already rereads
   changed content in the main Source tab after configuration/inference changes. Topology has
   a separate embedded SourcePanel that fills only on first use. Both existing pane read/parse
   paths run on the EDT.
   [SourceService](../../src/main/java/telamin/fluxtion/audit/analyser/analyser/source/SourceService.java)
   retains `selectedModel` even when a pane rereads, leaving node→class navigation stale after
   a rename. §C therefore tests both panes, service-model replacement and off-EDT work separately.
   Its existing fresh spotlight snapshot is reusable machinery, not proof ordinary panes use it.
4. **Recovery is path-scoped, which is insufficient for replacement projects.**
   [SessionRecoveryController](../../src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/SessionRecoveryController.java)
   loads by `SessionResumeStore.key(profile)`;
   [SessionRecovery](../../src/main/java/telamin/fluxtion/audit/analyser/analyser/session/node/SessionRecovery.java)
   rejects a mismatched key and stale generation, but the key is a real path. A new profile
   at that path inherits the key. Test capture/delete/recreate first, bind a durable profile
   identity, disclose capturedAt and qualify/withhold mismatches. An external log remains a
   legitimate input; directory ancestry is not the defect. The reviewer's local birth-time
   inference was not independently reproduced here.
5. **Neither XML declarations nor runbooks grant read access.**
   [DesignFiles](../../src/main/java/telamin/fluxtion/audit/analyser/analyser/design/DesignFiles.java)
   explicitly enforces this. A convenience improvement must preserve that boundary.
6. **Validation success and billing are distinct.** A deeper preflight must say it may load
   user code. Provider invocation, acceptance and billing each need their own evidence.
7. **Vendor source support exists.**
   [MavenSourceResolver](../../src/main/java/telamin/fluxtion/audit/analyser/analyser/source/MavenSourceResolver.java)
   searches configured local repositories, caches hits and misses and discovers archives once
   per resolver. Source archives must actually exist. No source/binary equivalence follows
   merely from finding a same-named class.

## What to preserve, and what is not established

The participant reports successful keyless first runs/body edits, vendor composition, accurate
scenario outputs, safe reconciliation refusals and useful linked reports. Preserve these as
positive acceptance scenarios. This review did not independently verify their counts, ordering,
coverage ratios or report pixels. It does not attribute any defect to the event runtime.

Needed before claiming reproductions: exact analyser build, original archive/route, preserved
pre-edit XML/Java/ownership state, failed-attempt receipts, recovery capture history and source
archive availability. The running session may overwrite current receipts; ask for relevant
sanitised snapshots rather than treating the latest success as proof of all earlier attempts.

No implementation, new client session, mutation trial, paid call, merge or deployment occurred.
The proposed tests/controls are future acceptance, not claims that regression coverage exists.
Repository consistency checks for this docs-only change are recorded below after execution.

## Historical v1 verification of this documentation change

- **RUN:** `JAVA_HOME=<Corretto 21.0.8> mvn -q test` — **1,996 total / 0 failures /
  0 errors / 98 skipped**, 266 XML reports, no orphan reports relative to `src/test/java`.
  Thus 1,898 executed, not 1,996 passes. No real-display acceptance is claimed.
- **RUN:** `mkdocs build --strict` — passed. This checks the published site; the specification
  is outside that site. `SpecLinksResolveTest` passed in the full suite, and an additional
  local check resolved all nine file links in the new review/spec packet.
- **RUN:** `git diff --check` and the CLAUDE.md rule-one sweep — clean. New documents were also
  scanned before staging so untracked files could not escape the tracked-file sweep.
- **READ:** session feedback/notes, current project POM/scripts/mapper/test/profile/runbook,
  starter jar entries, versioned starter/template source and the analyser paths cited above.
- **NOT RUN:** product scenarios, UI checks, mutations, provider calls, billing queries,
  hosted archive download/extraction or a new client session. No product code or site
  screenshot changed. The checklist in the spec is future work, not present verification.

The first test attempt was unsuccessful: **1,996 / 1 / 29 / 98**. I started it before writing
this companion review, so its link check correctly caught the then-missing file; sandbox
socket restrictions caused the 29 errors. After completing the packet, I reran the full
suite with permission for local socket tests; the counts above are that completed green
run. No test assertion was weakened or product source changed to obtain it.


## Mid-cycle update: feedback 21–24 and expanded 19

Re-read at **2026-09-25 09:07 UTC**: `FLUXTION-FEEDBACK.md` now has **30,284 bytes**, SHA-256
`cddd724011bc79eab5bf11386a84960650481413ea8c8ecb115e7c54b9b708c3`.
`SESSION-NOTES.md` is unchanged from the hash above. The original fingerprint is retained to
make the evidence boundary of the first review explicit. The new feedback itself says items
21–24 came from documentation reading, not a new runtime trial.

Additional inspections, **not executed examples**:

- Core 1.0.29 `EventFeedConfig` declares `Function<IN, ?> valueMapper` and passes it to the
  event source. This supports the generic extension-point claim, not every suggested
  composition/error-handling behaviour.
- Plugins 1.0.44 (`117ce80c`) `MongooseTestHarness` supplies lifecycle/await helpers and an
  adapter for an already-created server. The project's test dependency is present; the
  existing test does not exercise the harness. §G now requires the real hosted route.
- The same release's Spring loader selects `compileAot` or `interpret`, then initialises
  and registers the processor. It is preview-marked. This initial read did not establish
  dispatch equivalence or provider requirements. The v4 inspection now establishes the
  expected hosted compile/key versus local interpretation boundary, still not a provider run.
- **Additional mismatch found while checking item 21:** release source
  [TypeSerialiser](https://github.com/telaminai/mongoose-plugins/blob/117ce80ceec49afb5564eb75f34d1b3b3c7149f0/library/lib-jsonserialiser/src/main/java/com/telamin/mongoose/plugin/lib/json/TypeSerialiser.java)
  reads a `type` key through class lookup, whereas the release's documentation and fetched
  [public JSONL page](https://telaminai.github.io/mongoose-plugins/libraries/jsonserialiser/)
  teach `@type` plus `typeMap`. The implementation has no such configuration field and
  returns null for some parsing/class-lookup errors. This is a source/document discrepancy,
  not a reproduced feed failure. §G1 gates any recommendation on running the real example
  and testing visible rejection; it does not assume the mapping is safe for arbitrary inputs.
- The fetched [plugin overview](https://telaminai.github.io/mongoose-plugins/) shows 1.0.37
  as latest. This was read from live HTML on 2026-09-25; it may change. §G4 separates
  documentation version, published-artifact version and consumer pin.

These additions strengthen documentation and cheap example checks without moving the first
priorities: compile ordering, fabricated values, source freshness and explicit ownership
migration. Replay guidance is corrected now as a requirement; a new replay implementation
is not assumed to exist or added to the analyser's responsibility.

**Revision checks RUN:** JDK 21 `mvn -q test` again produced **1,996 total / 0 failures /
0 errors / 98 skipped**, 266 source-mapped reports and no orphans. `mkdocs build --strict`,
`git diff --check`, all nine local packet file links and the exact tracked-file rule-one sweep
passed. No display or mutation checks were run for this documentation-only revision. Public
pages were fetched and read; plugin/core examples were inspected at the reported versions,
not executed. The updated feedback hash was unchanged when checks finished.


## Owner additions: design-first journey, console and vendor integration

**READ, not a UI run:** the inspected playground release's bundle model and generic profile
emitter include only `src/main/java`. The current participant profile has later-added designer
and target roots. Analyser `openDesign` and `openGraphml` do not require a log; GraphML opening
explicitly states that there is nothing to compare against when no log is open. XML is exposed
in Source/Design rather than being automatically embedded in the GraphML canvas. §I1 now
requires a no-log real-frame journey and the explicit design directory in the generated profile.

**READ:** plugins 1.0.44 writes server-registry records before HTTP binding and can leave them
after crashes. It has process metadata but no project identity. Revised §I2 requires a
bounded pid/start-metadata probe and separately disclosed home/processor-class inference,
subject to D5; it does not claim the registry can verify a project. No live registry, token or running server was accessed.

**READ from a fresh public clone:** vendor collection head `3a89391` is source-only by its
own publication contract. `QuoteView` and the demo's concrete PriceEvent are different
contracts, and the supplied CSV adapter is callback-based rather than a valueMapper Function.
§I3–I4 now follow D7's approved shared-interface demo change and require compatible
mapper/wiring, source jars, binary resolution and
M67's staged tour, preserving the deliberately incorrect risk A and its independent oracle.
No vendor source or binary was changed and no guided tour was run by this inspection.

These additions extend the specification. They do not claim the requested UI or vendor/demo
changes are implemented. The canonical M67 tracker entries remain open.


**Revision 3 documentation gate RUN:** JDK 21 `mvn -q test`: **1,996 / 0 / 0 / 98**
(total/failures/errors/skips), 266 reports. Strict MkDocs, diff whitespace and the tracked-file
public-data sweep pass. No display acceptance is claimed for the newly proposed journey.
The owner authorised reusable demo implementation on branches, leaving publication for review;
that authorisation is not a claim that implementation has started or passed these acceptances.


## Independent review response — v4 (2026-09-26)

The [independent review at 075107ef](https://github.com/telaminai/fluxtionauditlog-analyser/blob/075107ef/docs/handoff/review_spec_spring_authoring_edit_loop_2026_09_25_claude.md)
was read in full and left unedited. I independently inspected the
analyser at this subject baseline, private starter/compiler 1.0.74 and the plugins' builder
pin, template emitter `5d6a38a`, core 1.0.29, plugins 1.0.44 and vendor `3a89391`.
Private repository behaviour is described without file locations or excerpts. **READ** below
means inspected code, never an executed reproduction. None of the proposed product controls
was run. The participant project and private recovery store were not accessed in this pass.

| Finding | Independent check and revision | Status of evidence |
|---|---|---|
| R1 | Reflective supplier, model-before-scan requirement, scan source write and reconciliation rollback verified. §A specifies profile-only exclusion, final compile and staging. | READ; the new pipeline was not run. |
| R2 | Installer's fixed executable list omits the three authoring scripts. §G separates installer and direct-browser tests, preserves mode refusal and adds D6. | READ; no install/extraction run or Windows entry point invented. |
| R3 | Real-path recovery key and input-only hash check verified. §E starts with replacement at the same path, identity/capturedAt disclosure and an identity mutation. | READ; reviewer's local timestamps and inferred deletion were not independently checked. |
| R4 | Main pane content reread, embedded first-use gate, cached service model and EDT reads verified. §C and this report now name all four. | READ; proposed per-pane/model/EDT controls not run. |
| R5 | Serializer typed-object/raw-map/null/exception/batch branches and core null/exception drop paths inspected. §B/G1 distinguish fabrication from non-numeric loss and require D1's surface. | READ; no feed or serializer execution. |
| R6 | Registry fields, basename naming, pre-bind write, pid/start metadata and bearer login inspected. §I2 separates process verification from project inference, adds D5 and collision/reuse/auth/frame cases. | READ; no live registry, console, token or probe used. |
| R7 | Exact-type-first interpreter dispatch and vendor size/CSV/notifier/build behaviour inspected. §I3 adds D7, units, source-link/build work and the shared-FQN guard, retaining risk A/oracle/fixtures. | READ; AOT parity unverified. Remote branch listing separately checked. |
| R8 | Hosted compile fallback with no public local generator, local interpretation and swapped reload registrations inspected. §G3 states expected prerequisites and the upstream handoff names the swap. | READ; no provider request or command execution. |
| R9 | Plugin version variable and deployment configuration inspected; Repsy metadata returned release 1.0.45 and Central's aggregate coordinate metadata returned 404. §G4/I3 name the correct route. | READ + RUN metadata fetches only; not a binary-resolution/build acceptance. |
| R10 | Admin Replay code handles visual record playback, not application execution. §G2 adds that fifth capability and corrects the emitted comment's meaning. | READ; no browser replay run. |
| R11 | Parameter-FQN member keys and type:FQN keys inspected. §D/D2 require cross-class migration and an explicit resulting ownership state. | READ at v4 inspection; subsequent D2 approval preserves states under plan → review → apply. |
| R12 | Installer and recovery identity now have analyser tracker entries; feedback 8 has split installer/browser ownership in delivery and upstream tables. | Documentation diff inspected; all pre-existing status marks retained. |

What I got wrong: I treated a possible custom supplier dependency as the shipped template's
premise, routed permissions to the wrong producer, called path keys profile identity, and
reduced source freshness to one navigation skip. The serializer summary was also wrong.
These were incorrect or incomplete source conclusions, not failures proved by client trials.
V4 changes the governing text rather than relying on this response to qualify it.

Further precision from the source checks: registry startedAt records service registry
publication at admin-service start, truncated to whole seconds and fixed across refreshes,
not JVM start time. A pid-reuse test must not require those times to be equal.
Core's null mapper route emits FINE but does not populate the exception ring. The local
MkDocs build is runnable on a branch; verification of the published site and its release
metadata is a distinct check. Repsy's existing releases do not publish the vendor catalogue.

Unverified: the participant's actual capture/recreation history; generated AOT equivalence
to the interpreted type-selection rule; execution of all proposed regressions; the new
pipeline and real provider prerequisites in operation; public-download install/build/tour
acceptance; and live console/login behaviour. These need the fixtures, published artifacts
or separate provider authorisation named in the specification. D1–D8 were open at the v4
source review and are now approved as recorded below. No slice
was implemented, no risk fixture/oracle changed and no status was advanced.

### V4 documentation checks

- **RUN:** JDK 21 `mvn -q -Dtest=SpecLinksResolveTest test` — **3 total / 0 failures /
  0 errors / 0 skips**, counted from that suite's Surefire XML. A pinned review link and
  heading anchors changed, so the scoped link gate was run; no full suite was run for v4.
- **RUN:** `mkdocs build --strict` — passed. It checks the site, not these spec/handoff files.
- **RUN:** packet-related local file/anchor check — 50 references resolved across the four
  edited documents. The ad hoc check initially failed on a missing Python dependency, then
  on using collapsed-hyphen rather than GitHub anchor rules; both were checker issues,
  corrected without changing existing links. This is additional inspection, not a new
  committed regression test.
- **RUN:** `git diff --check`, the exact CLAUDE.md tracked-file sweep and the same terms over
  added lines — clean. Added-line scans for private local paths, email addresses and the
  requested excluded wording — no matches.
- **RUN:** scope/status comparison — only the four authorised documents differ; the rest
  of the tracker is byte-identical. Existing marks stay unchanged; the installer adds one
  new unchecked item. Commit email verified as the owner's personal address.
- **NOT RUN:** implementation, product tests, display tests, mutations, participant/session
  activity, provider calls, public-archive build or live console checks. No full-suite result
  from an earlier revision is presented as v4 evidence.


## Owner decision addendum — 2026-09-26

The owner approved all eight recommendations after the v4 source review. The specification's
D1–D8 table is the canonical record: audited rejection events including blank rows; explicit
plan/review/apply rename preserving ownership states; explicit role-scoped grants; unchanged
canonical-id precedence with disclosure; qualified console inference for a verified process;
documented Windows absence for this pass; shared-interface demo handlers; and documentation
of actual serializer behaviour with tested examples.

Affected requirements, tracker text and upstream routing now reflect those choices. No
implementation status mark advanced. Windows entry points remain a separately tracked deferred
follow-up; live provider authorisation and published-artifact acceptance are still required.
This records policy approval only, not implementation or a rerun of the source/product checks.

Decision-record checks: strict MkDocs, diff whitespace, tracked-file and added-line public-data
sweeps, and the private-path/address/wording scans passed. Existing status marks and Markdown
link targets are unchanged; no Maven tests or product trials were rerun for this policy record.


## V4 re-review response — C1–C3 (2026-09-26)

Read the whole independent report `1bae2480`, left unedited. It reviews `449265ec`, before
the owner decision record `13c44071`; its statement that D1–D8 are unresolved describes that
older subject, not current policy. All earlier R1–R12 corrections remain in force.

| Finding | Source/policy check and governing correction |
|---|---|
| C1 | **READ:** PathForm explicitly supports external workspace-relative roots. TemplateArchive installs the profile without validating its roots. §I1 now requires containment for every template-supplied source root at installation, with traversal/symlink controls and a positive user-authored external-root case. It does not prohibit external roots at ordinary profile load. Tracker routing matches. |
| C2 | **READ:** slice 7's old independence claim hid the blank-row policy choice. Approved D1 now resolves that dependency. §B and slice 7 explicitly deliver fabrication removal with audited rejections for blank/short rows; no unaccounted interim fallback is approved. Upstream routing agrees. |
| C3 | **READ:** option (b) was removed when the owner approved shared-interface handlers under D7. §I3 nevertheless makes the conversion boundary explicit: application or separate integration module, never a reusable vendor jar that depends on demo classes. No new event-policy decision is taken. |

Optional O3 is incorporated: plugin release source sets startedAt once at admin-service start,
truncated to seconds; registry refresh does not change it. The spec and this report now say
that precisely. O1 (explicit bearer-token probe policy) and O2 (local-host evidence beyond the
pid) remain optional implementation clarifications; no credential access or console probe was
performed here. They are not claimed resolved by this text revision.

This pass inspected installer/path-form source and the plugin start/refresh paths; it ran no
product scenario, client session, mutation or provider call, and did not touch the participant.
The reviewer's new checks are future acceptance, not tests implemented or reproduced here.

**RUN for this correction:** strict MkDocs, diff whitespace, tracked-file and added-line
public-data sweeps, and private-path/address/wording scans passed. All 50 packet-related local
links/anchors resolve; link targets and existing tracker status marks are unchanged. No Maven
suite was rerun for unchanged links. Commit identity was verified as the personal address.


## C1 boundary clarification — 2026-09-26

Read re-review `43a117ba` in full; C2 and C3 remain resolved and D1–D8 remain approved.
**READ:** TemplateArchive extracts regular files/directories into staging, refuses a linked
destination and moves the sole project root atomically. ProjectProfile supplies the profile's
project-relative base; PathForm and SettingsShare support user-authored workspace, home and
absolute forms. No installer or product scenario was run.

§I1 now validates template profile roots before the move against the canonical staged root,
canonicalising both sides. Template workspace, home and absolute forms cannot widen the boundary.
Missing internal roots use the canonical nearest existing directory ancestor plus a normalised
remainder without parent traversal. The unreachable archive-internal-symlink fixture is
removed; extraction's link-free result is asserted instead. Actual installer controls cover
profile traversal separately from ZIP-entry traversal, and retain a positive external-root
case for ordinary user profiles. The companion feedback-6 wording now says “together with”
to match the approved single rejection/fabrication-removal slice.

The earlier specification was still imprecise about staging versus installed paths and
proposed a fixture the extractor cannot produce. This corrects those premises without adding
a validation hook, implementation, owner decision or completion claim. Optional console
points O1/O2 remain unchanged.

**RUN:** strict MkDocs, diff whitespace and rule-one sweeps of tracked files and added lines
passed. Link targets are unchanged, so no Maven link-test or full-suite rerun was needed.
Only the specification and this response changed; tracker status marks remain untouched.


## C1 final response — descendant paths (2026-09-26)

Read review `23f47a1c` in full. **READ:** SettingsShare resolves source roots against the
profile base with lexical normalisation; its loaded `workspaceRoot` is not an input to that
resolution. TemplateArchive moves the archive's sole staged root to the user-chosen destination.
My earlier claim that staged containment alone survives that move was false.

**RUN:** a scratch JDK 21 check using that resolution expression and a real `ATOMIC_MOVE`
reproduced the re-entry case: containment before the move was true, after the move false.
The new leading-parent predicate refused it and accepted an ordinary descendant. This was a
path-mechanics check, not a run of the installer or an implementation acceptance.

§I1 now refuses a template source root whose normalised relative form begins with a `..`
component, before resolving it. Both existing and missing roots retain the canonical checks.
The survival claim is conditional on both rules, and the later-filesystem-change caveat stays.
A negative install fixture must refuse the re-entry root even though staged canonical
containment passes; its mutation removes only the leading-parent guard. Nonblank template
workspace anchors, including `.`, remain deliberately refused as an extra restriction.

Both optional clarifications are taken: regular-file components such as `pom.xml/sub` are
invalid; `mavenRepo` locations are explicitly outside the source-root containment rule, retaining
existing source-jar access rules and the legitimate `~/.m2` default. No general source-root
grant is implied. C2, C3 and approved D1–D8 are unchanged; no tracker status mark is edited.

**Documentation checks:** diff whitespace, strict MkDocs, tracked-file and added-line rule-one
sweeps, and added-line local-path/address scans passed. Markdown link targets are unchanged,
so SpecLinksResolveTest was not run. No product code, participant files, client session,
provider call or release was involved. Commit identity was verified as the personal address.


## C1 whole-project-root response — 2026-09-26

Read review `657d881e` in full. **RUN:** a scratch JDK 21 path-mechanics check confirmed that
`Path.of("src/..").normalize()`, `.` and `./` have empty textual forms, resolve to the project
itself and pass the leading-parent check. A normalised `src/main/java` descendant stays nonempty.
This was not an installer run or implementation acceptance.

§I1 now refuses an empty normalised relative root before resolution, independently of the
accepted leading-parent rule. Canonical containment alone is explicitly insufficient. Negative
install fixtures cover the whole-project spellings, and removing only the empty-root guard
must fail their refusal assertions with the other guards intact. This enforces the existing
no-whole-project-grant policy; C2, C3 and D1–D8 are unchanged.

The optional Windows clarification is adopted: refuse any root component, not only an absolute
path. Drive-relative and rooted Windows forms get Windows-only install fixtures, explicitly
not runnable in this macOS/Linux branch check. No Windows execution or installer acceptance
is claimed here. Existing workspace-anchor strictness and Maven-repository scope are unchanged.

**Documentation checks:** strict MkDocs, diff whitespace, tracked-file and added-line rule-one
sweeps, and added-line local-path/address scans passed. Link targets are unchanged, so
SpecLinksResolveTest was not run. Only §I1 and this appended response changed; no tracker mark
moved. Personal commit identity was verified. No code, participant project, client session,
key, merge or release was involved.
