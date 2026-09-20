# Project starter journey

Status: **owner requirements recorded 2026-09-20; implementation pending**. This is a cross-repo
contract for playground generation and analyser discovery. The proposed authoring page and a simpler
headless configuration API remain design proposals. Companion: [template picker](spec-template-from-analyser.md),
[onboarding](spec-onboarding-example.md) and [tracker](tracker.md).

**Review revision, 2026-09-20:** the [independent review](../handoff/review_project_starter_journey_2026_09_20.md)
is CONDITIONAL; the [author response](../handoff/response_project_starter_journey_2026_09_20.md) answers
F1–F16. The owner has now selected default-on support for old links too, and one explicit restore offer
for both reopen and relaunch. This revision requires re-review, not implementation acceptance.

The [re-review](../handoff/rereview_project_starter_journey_2026_09_20.md) accepts the F-series dispositions
and identifies G1–G3. Those three spec corrections are incorporated below and recorded in the response;
the [second re-review](../handoff/rereview2_project_starter_journey_2026_09_20.md) closes G1–G3 and
retains the F-series closures. Its new H1–H3 findings are corrected below; the verdict remains
CONDITIONAL pending independent confirmation of those corrections. The
[consolidated review](../handoff/review_all_specs_2026_09_20.md) also raises H4–H6: canonical comment
ownership/parity, independent report checks, and hosted ordering/reset/replay. These are addressed below
and in the response; no independent closure or implementation is claimed.
The [latest re-review](../handoff/rereview_all_specs_2026_09_20.md) closes H1–H6 and leaves J1/J2 as
conditions plus J3 as a minor clarification. Author corrections below use a public artifact for comment
provenance, version-stamp the Mongoose observations and clarify consumed-cache delivery. Confirmation of
J1–J3 is pending; the reviewer's verdict is unchanged.
The [final re-review](../handoff/rereview_final_project_starter_journey_2026_09_20.md) closes J1–J3 and
marks the combined handoff READY, with K1's endpoint naming correction and K2's digest clarification.
Both author corrections are incorporated below; implementation and publication gates remain outstanding.

**Additional review scope, 2026-09-20:** the participant's learning-channel feedback adds the proposed
requirements in “Learning at the point of use” and acceptance 10–12. These need review alongside G1–G3;
they are not covered by the earlier conditional verdict.

## Start before a project exists

The website, a local LLM and the analyser are three entry points to the same catalogue and generator.
A local LLM can acquire a project before an analyser is installed or running. Once downloaded, the
project's bootstrap entry directs the human/LLM to its own runbooks. The analyser joins as a shared
canvas for design, producer results and runtime evidence; it does not build, deploy or run the application.

The full catalogue is visible, with `onboarding` entries marked **Recommended starting points** as
specified by template-picker D-1. Project type and guided walkthrough are distinct: a walkthrough is
an optional runbook exercise using a project, not a runtime mode.

## Default-on analyser support

The website's new-project configuration has an **Analyser support** option, enabled by default and
explicitly switchable off before download. The same effective choice must be expressible by a headless
client and survive shared links and exported/imported starter configurations.

**Owner decision after F5:** `analyserSupport` is a boolean defaulting to true when absent, including
old shared links and imported starter JSON. Explicit false stays false through validation, share/export,
import and generation. New shares/exports serialize the effective value. Old inputs intentionally gain
support files; their old ZIP contents are not promised byte-identical. This is a documented additive
packaging change, not permission to change compilation mode, hosting, credentials or audit instrumentation.
Do not use the existing `version` field as a schema version: it is the generated Maven project version.
An old `analyserBundle` still generates its existing example. Explicit false combined with that inherently
support-enabled bundle is a validation conflict: explain how to choose an ordinary project instead;
never silently ignore the opt-out or strip required bundle files.

Enabled support delivers:

- A portable `.analyser/project.fluxtion-settings` with project-relative source roots, declared
  processor information where available and runbook pointers applicable to the generated project.
  Distinguish a declared generated type whose output is not present, a runtime/interpreted processor
  without a fixed generated type, and an unspecified processor. Never fabricate an FQCN. A shared
  additive metadata schema for those states must be pinned before producer/consumer implementation.
- One obvious bootstrap entry, linked from README and supported agent entry files, explaining the
  project shape, prerequisites, available runbooks and how to connect an analyser.
- Runbooks composed for the actual choices: Spring validation/reconciliation/generation only where
  applicable; embedded execution for a bare project; Mongoose deployment, start, stop, feed, audit and
  evidence-export procedures for a hosted project. Deployment instructions must name a supported target
  and configuration; no invented remote environment, credentials or service endpoint.
- Paths to expected evidence outputs, clearly distinguished from files that already exist. A new
  skeleton must not advertise a generated graph or successful run as already available.

The generator owns these files alongside the matching scripts. Shared reference material is reused;
project names, paths, processor identities and enabled capabilities are rendered from the same spec.
The analyser stores runbook pointers, never executes their contents. MCP setup must target a real chosen
analyser instance; the download cannot assume the staging machine's jar, home or port.

For new downloads the playground owns bootstrap content. The analyser's optional reference-guide writer
remains a fallback for old/profile-less projects, retaining its existing `ALREADY_EXISTS` guard. It never
overwrites or merges into generated `CLAUDE.md`. An opted-out download must not silently acquire support
again through discovery: any later project/profile/reference-guide creation remains an explicit offer.

Disabled support omits analyser-specific profile/bootstrap integration. Ordinary build, authoring and
hosting instructions remain: a usable project must not lose its essential runbook because the user
does not want the analyser. The option is distinct from audit instrumentation and from the existing
`analyserBundle` example mode. It does not implicitly switch compilation mode, commit a generated
processor, add a server or claim keyless generation. Any instrumentation requirement is explained
against the user's explicit project choices.

### Mongoose procedures follow capabilities (F6)

Factor profile/bootstrap/runbook emitters out of the special bundle path. Do not automatically add its
registry helper, web admin or performance plugins to ordinary Mongoose projects. Document the actual
foreground launcher, feed setup and graceful Ctrl-C/shutdown-hook path for an ordinary host. Local
deployment means installing the built project and configuration on a named local target; remote/service
manager deployment needs a separate supported recipe. Test termination and output closure, not a guessed
process-name kill. If launch-script generation is disabled, render the actual supported alternative.

Registry-backed `stop-server`/`export-audit` recipes are emitted only with their helper, registry publisher
and supported capture configuration. Resolve the required published plugin coordinates from the owning
version pins and verify their capability. Generalising that machinery to other hosts is separately scoped
and must name its version floor. Other capture backends get their tested file/stream/export procedure.
With no compatible capture, state the limitation and link explicit enablement instructions; do not invent
an export script or silently enable capture. Every host gets an audit-state explanation, but only available
operations are promised. The runbook requirement does not require a script with a particular filename.

**Ordering, reset and replay (H6).** Every hosted runbook states the selected feeds' ordering domains and
the order the scenario relies on. Preserve a required common input order through a supported single-feed
route or an explicitly defined upstream ordering mechanism; do not promise deterministic interleaving of
independent feeds. Name the effective configuration and limitations, including time/external inputs.

Describe each supported clean/restart/reset step in a table: the exact command, affected paths/state,
what is retained, and resulting feed position and processor initial state. Distinguish build cleanup,
process restart, feed rewind and evidence deletion. If no reset/rewind operation is supported, say so;
do not invent one or claim restart resets application fields. Preserve evidence before any documented
destructive cleanup and use disposable directories to verify these procedures.

**Source observation, not a released-version guarantee:** the cache and capture facts below were read
from `com.telamin:mongoose:1.0.30-SNAPSHOT` (local source `17a03b4`), whereas the inspected playground pin
is `1.0.29`. Verify effective defaults and behaviour against the generated project's resolved version
before emitting runbook claims; this recital is not the acceptance oracle.

Distinguish pre-start cached delivery from recorded-session re-execution. In that inspected Mongoose
`InMemoryEventSource`, `cacheEventLog` defaults false; enabling it supports cached input dispatch at
`startComplete`, using the publisher's cache read pointer. That dispatch sends the pending cached prefix
to subscribers present then and advances the publisher-wide pointer. A later subscriber does not receive
that consumed prefix; another dispatch with no new cached entries sends nothing. This is not a re-readable
buffer, durable session replay or reset of application state. Describe the selected connector's actual behaviour rather than generalising
this option to every feed. If a separate replay recorder/plugin is selected, name its prerequisites,
captured input scope, initial-state requirements and supported replay command, or state that full-session
replay is unavailable. Do not silently enable a recorder to satisfy the runbook.

For the supported audit-capture service, state both service enablement and per-processor recording state.
The inspected `AuditCaptureConfig` defaults to `enabled=false`, `backend=chronicle`, `rollSize=64m`,
`retainHours=24`, `directory=./audit` and empty `autoStart`; installing the service alone does not start
recording every processor. Render effective settings for the pinned project version, including directory
resolution, roll/retention and start/stop/export operations. Warn that retention can remove evidence;
do not promise a previous run remains available. Distinguish capture storage from exported YAML/JSONL.

## Learning at the point of use

Participant evidence from one session ranks generated stubs, contract tables and actionable errors above
unvisited prose. It also reports two decisive owner interventions and three unread authoring documents
containing applicable hazards. Treat this as a routing hypothesis to test, not proof that twelve facts
cover every project or that one retrieval mechanism beats training in general.

**Playground owns the entry and routing.** Emit one short project bootstrap linked by README and agent
entry files. With analyser support enabled, declare that entry and applicable task runbooks in the
profile so existing `context.runbooks` exposes them. Without support, retain the same essential guidance
through README. Aim for roughly 250 lines or less in the entry as an editorial target, not a correctness
gate; link task references rather than copying every reference into it. Select routes from actual project
choices: Spring contract/reconciliation, audit setup, embedded execution, Mongoose/plugin capabilities,
vendor consumption and evidence/report presentation. Describe when to follow each route and expected
outputs, not only its title. Do not require an analyser connection to learn how to start the project.

**Compiler/starter and playground own lessons in emitted code.** At generated decision points, add brief
comments explaining the construct and linking the applicable contract. Comments must be **choice-neutral**:
never state which alternative is currently in force or embed advice specific to the initial selection.
For example: “This reference's propagation mode is declared in the XML; see the contract.” Reconciliation
owns annotations, not explanatory prose, and a preserved comment must remain accurate after a declaration
changes. Put choice-specific explanations in the owning contract. The compiler/starter contract owns the
canonical wording. **Public distribution contract (J1):** publish a versioned UTF-8 comment resource at
`META-INF/fluxtion/starter/comment-contract.json` inside the released
`com.telamin.fluxtion:fluxtion-starter-core` jar with classifier `all`. Java emission reads that resource;
the playground vendors the resource extracted from the same published artifact and pins its released
version via `starterCore`, recording the coordinate, artifact SHA-256 and resource SHA-256. The artifact
digest is enforced only for an immutable released coordinate; the extracted resource digest is the
comment-content parity oracle, not whole-jar equality across repacks or local development builds. Do not pin
public guidance to a closed-repository commit or require private credentials to verify it. CI must compare
the vendored bytes with the resource in the pinned public artifact; a missing artifact/resource or digest
mismatch fails verification. Resolve the authoritative release from
`https://repo.repsy.io/mvn/fluxtion/fluxtion-public`, the repository declared by the generated project's
POM for `setup.sh`; a GitHub mirror may be used for convenience only after validation against that
Maven coordinate at this endpoint, never as the publication or parity authority.
Local development may use an explicitly identified local artifact, but that
does not pass the public publication gate. Packaging and publishing this resource remain implementation
work; its presence in any existing release is not asserted. Keep parity fixtures for the same construct
and artifact version rather than independently maintained wording. In the referenced guidance, explain
callback propagation choices, DATA versus TRIGGER and audit requirements without implying that DATA
makes Java objects immutable, every false return prevents all downstream execution, or extending a base
class alone guarantees useful audit output. Do not change defaults, re-parent existing classes or rewrite
developer bodies as a documentation fix. Retain ownership/hash/no-op reconciliation guarantees; any
change to generated stub text must preserve cross-language hash equivalence and old-record compatibility.
Existing Java body/construct hashing and browser ownership hashing skip comment tokens; retain their
regressions. Hash equivalence checks content ownership, not whether a comment tells the truth.

**The diagnostic owner supplies rule, why and fix.** New or revised authoring diagnostics must identify
the violated rule, explain its consequence and provide an applicable next action with the owning
construct/location when available. State uncertainty or an unavailable prerequisite rather than invent
a fix. Extend the existing diagnostic builders and codes; this requirement does not authorize a new
envelope or a blanket rewrite of all errors. Select concrete messages from reproduced failures before
implementation; regression fixtures must assert actionable content, not merely a matching code.

**One maintained source per rule, compatible references.** Generated comments and runbooks point to
the owning contract and identify the tool/template version or source revision they describe. Bundled
reference snapshots, if used, are generated from that source and record their revision; they are not
independently maintained copies. A reference URL being live is not proof it describes the downloaded
tool version. Verify referenced examples against the pinned toolchain and report unsupported combinations.
Extend the playground's existing minimum-analyser-version checks and `skills.provenance` revision/hash
machinery where applicable; do not treat skill provenance as coverage for unrelated references.

**Fallback exception (H2):** the analyser's explicit reference-guide writer for legacy/profile-less
projects emits version-neutral discovery pointers only. It is exempt from identifying the project's tool
version or claiming compatibility; its block must state that compatibility is unverified and direct the
reader to check the project's dependencies against the reference. No new `ReferenceSet.Resource` field
is required in this slice. Retain the no-overwrite guard. This narrow exception does not exempt generated
project runbooks, comments or bundled reference snapshots from the version rule above.

**No guide verb is approved by this revision.** Use existing runbook pointers and external client file/web
reading first. The analyser continues to store pointers rather than document contents and never executes
instructions. Serving references through MCP could be reviewed as a separate documentation-provider
capability with ownership, access, version and freshness contracts; adding a transport alone does not
remove contradictory sources. The journey must work with the documented existing surface.

A small maintained example set should pair source/XML, scenario input, expected results, audit output
and the command that compares them, naming build/dependency identity and validation scope. Extend existing
fixtures instead of creating a second generator or a mandatory new public corpus. Examples demonstrate
tested behaviour, not universal proof. The project runbook/harness executes; the analyser presents evidence.

**Participant follow-up, implementation intake:** the nearest shipped example is also an instruction.
Each template's runnable harness must demonstrate its intended host/feed/sink shape; a hosted template
must not teach application orchestration through a neighbouring hardcoded-event/sleep harness. Keep
special-purpose regression fixtures clearly labelled and outside the ordinary project entry path.
Record which existing source/example the client copied, not only which prose it opened. Do not forbid
an embedded main for a template whose intended shape is an embedded application.

## Headless creation: present capability and proposed improvement

Source inspected on 2026-09-20: the existing scaffold endpoint supports either
`GET /start/scaffold?template=<id>` with artifact/group/basePackage overrides, or
`GET /start/scaffold?s=<token>` carrying a compressed, validated starter spec. The latter is the same
configuration mechanism as the website's shared link / Copy curl. Arbitrary configuration is not
available as arbitrary query parameters, and no plain-JSON POST contract was found in that route.

A documented JSON request is a candidate improvement for local LLMs: publish the accepted schema,
validate it using the existing validation path and return a ZIP from the same generator. This is not
approval for a second generator or a new service. Until chosen and implemented, documentation must
describe the actual template/token API and its limits.

**Required headless extension (F4):** the existing `?template=` route accepts
`analyserSupport=true|false`, applied before normal validation/generation. An absent query parameter
means **no override**: preserve the template's decoded value, including explicit false. Default true
applies only when decoding a spec whose support field is absent, not when applying query overrides. Reject
malformed/repeated values with a named 400. A false override on the special bundle returns the validation
conflict above. The token route embeds the same field; reject combining a token with a template-only
override rather than inventing precedence. The token route is technically usable headlessly, but clients
should not need to reproduce compression for ordinary template downloads. A plain-JSON API remains optional.

Own empty-directory instructions in playground `web/static/CLAUDE.md`, linked from
`web/static/fluxtion-golden-path.md`: catalogue URL, template identifier, download command, safe destination/
extraction, then the downloaded bootstrap. Document opt-out and errors with the extension; until deployed,
examples must not claim the endpoint accepts the new parameter.

## Proposed analyser “Author a new project” page

The owner suggested a more explanatory catalogue surface. It should help people choose using purpose,
host, prerequisites, guidance availability and declared evidence support, then continue through the
existing safe download/open flow. It reads the same catalogue and links to the website configurator
for richer choices; it must not become a second implementation of the full starter form. Exact layout
and navigation remain to be designed. Proposed host: a section of the existing `StartPanel`, using the same
catalogue model and selection/download controller as the File-menu picker, not a new window or duplicate
configurator. The agreed full-catalogue picker change does not depend on this optional section.

## Re-entry and reported missing context

Opening a project must load its persisted source roots, processor configuration, runbook pointers and
saved analysis definitions, and show any missing/unavailable paths explicitly. Reopening does not imply
rerunning setup scripts or recovering an LLM client's conversation history.

Loaded logs, topology, design/result views and cursor state have a separate session lifecycle. Current
project boundaries close these; saved charts need their input log before they can render. The previously
accepted direction for feedback 28 is an explicit **restore last session** offer, with project identity,
freshness and partial-refusal checks, without carrying another project's state across the boundary.

### Saved definitions and session memory (F7–F9)

Add `context.savedGraphs`, sourced from `AppConfig.savedGraphs` above the no-filter early return. Name
each saved definition and distinguish it from a currently open tab. Keep `context.graphs` with its existing
open-tab meaning. With no loaded log a saved definition is waiting for input, not missing or proven
compatible; validate bindings on load. The Project panel **states** these facts in a **Saved charts**
row and remains reveal-only; it gains no open/restore control or mutation callback. The landing
(`StartPanel`) displays the same facts and **offers** explicit open/restore actions through the existing
action/session machinery. Keep `ProjectPanel.Navigator` unchanged. `ProjectPanelIsRevealOnlyTest` gates
both the no-mutation boundary (D-L3) and context-backed panel facts (D-L1). Update
`docs/site/user-guide/projects.md`, `project-panel.md` and `assistant.md` in the same implementation
commit. Swing reads these facts rather than constructing a second model.

Store versioned last-session candidates in user-local state keyed by canonical project-profile location,
with a separate no-project bucket. They are excluded from portable profiles, settings-share exports and
generated ZIPs. Capture the outgoing session before clearing it. Retain log-set membership/order,
topology/design/result paths, relevant view bindings and observed input identities. A moved/missing
project does not inherit a candidate by basename. Restoration rereads/checks inputs and reports changed,
missing or refused parts; a path/mtime alone cannot establish build/run identity.

**Owner decision after F8:** both project reopen and quit/relaunch use the same explicit **Restore last
session** offer. Replace the implicit remembered-log/GraphML opens in `Main.main` / `reopenLastGraphml`
in that slice, rather than layering an offer over them. Legacy global `logFile`/`graphmlFile` values have
no proven project association: do not silently attach them to the active project. If retained, present them
as unassigned legacy locations for explicit selection. A command-line log remains an explicit open request;
it does not authorize restoring unrelated remembered topology. Failed project activation must not load
another project's candidate. Accept/decline and partial failure have matching human/MCP state and echoes.

**Owner decision after implementation review JI-1:** keep full content verification at every file size;
optimise the reader rather than introducing a threshold or accepting metadata as identity. Native
readers compute SHA-256 from the bytes consumed by indexing (the heap reader's losslessly decoded UTF-8
buffer is equivalent). Opaque plugin readers keep independent checks until they expose an equivalent
verified read. Capture/restore comparisons still check current bytes. Performance acceptance: on the
reviewer's large warm-cache fixture, target median overhead below 15% relative to the same index scan;
record raw timings and digests, and do not generalise them to other hardware or plugins.

Implementation review JI-2–JI-4 acceptance: all apply-time verification facts return to the graph's
whole-set rule before log publication; a recheck only narrows a plan. UI accept/dismiss carries the
rendered offer generation. A superseded asynchronous success or failure completes its own recovery
with a retryable outcome; it cannot finish a newer restore. No duplicate Swing recovery policy.

Owner report 2026-09-20: closing/reopening the locally provisioned sample looks as though everything
has gone and initialization is unclear. Read-only inspection of the staged profile found two
runbooks, two source roots and five saved chart definitions; that proves those declarations are present
on disk, not that the UI reloaded them. Preserve the live session while diagnosing. Capture profile and
context before close, after close and after reopen on a disposable copy, and distinguish lost settings,
unbound views and absent restore support before assigning a root cause.

Initial probe: copied only the staged profile to `/tmp/spring-reopen-probe/project/.analyser/`, then
called `ProjectSession.open`, `close`, `open` with a fresh `AppConfig`. Both roots, both runbook pointers,
all five saved charts and the selected processor were equal after reopening; the profile stayed
byte-identical. This rules out loss in that isolated profile round-trip, not in the full Swing/session
transition. No live project, analyser view or settings were changed. Existing project/profile/verb and
spec-link tests also passed (46 tests, zero failures/errors/skips).

**Real-app reproduction completed:** an isolated instance of the staged jar, using a copied project,
retains project/source/runbook declarations and one report after close/reopen, but has no log, topology,
design or producer result loaded. Five saved chart definitions reappear when the log is explicitly
reopened. Inspected screenshots show a generic demo start page despite the active project. The launcher
performs extra explicit evidence-open calls after loading the project; ordinary reopening does not.
No persisted-definition loss was reproduced. The action socket drove the real Swing application; menu
clicks and quit/relaunch remain untested. [Evidence packet](../handoff/evidence/spring-authoring-feedback-2026-09-20-reopen/README.md).

**Project landing requirement:** when a project is active with no loaded evidence, identify the project,
show its bootstrap/runbooks and saved definitions, and explain which inputs are closed or missing.
The landing offers explicit open/restore actions with freshness and failure information; the Project
panel only states context and retains its existing reveal/navigation affordances. The generic demo welcome
must not be the only prominent next step. Opening a saved project must not require remembering the
staging launcher's hidden sequence, nor silently run build/start scripts.

Widen the single decision site `MainFrame.syncRecordsCard`: no project/no log → generic start; active
project/no log → project landing; loaded records → records view. The landing may name open topology or
design without a log. Do not add a competing visibility rule. Session decisions remain in the session
model and the Swing adapter renders their facts.

## Delivery order and catalogue coverage

1. Playground: define/default `analyserSupport` and its API override, with captured legacy-link fixtures.
2. Factor profile/bootstrap and capability-specific runbooks out of the bundle, preserving its behaviour.
   Give every output path exactly one emitter: for a bundle spec, its bundle emitter owns shared profile/
   bootstrap paths and the general emitter skips them. Select ownership before emission, never by list
   order. At the end of `generate()`, assert that all emitted project paths are unique and fail naming any
   duplicate before ZIP assembly, even when contents match. No silent last-wins deduplication. Generate
   all catalogue entries in tests and compare disclosures to actual ZIP contents.
3. Publish the headless extension and agent entry instructions. The catalogue already carries
   `agentBootstrap`; consume it instead of inventing a replacement. Missing means not declared, an empty
   array means explicitly none, and paths are declarations to verify against the effective download.
   Missing `keyNeed` means **build key: not declared**, never inferred from AOT. Build and regeneration
   requirements remain distinct. Recommendations are advice, not a readiness/certification claim.
4. Analyser: saved definitions in context, then project landing, then project-scoped session storage and
   the unified restore offer including launch migration. These slices can proceed independently of the
   playground once their shared metadata contracts are pinned.
5. Ship the full-catalogue picker after support-enabled new downloads contain their promised profile and
   bootstrap. Keep a witnessed, clearly described discovery fallback for legacy/profile-less downloads.
   Test opt-out separately; no silent recreation of declined support.
6. Add the optional authoring section and witness empty-directory and re-entry acceptance.

Vendor integration requires a **new playground catalogue entry**, not a connector relabel. Coordinate
it with the existing M67 component-tour track to avoid a duplicate sample. Use the existing generator,
a declared dependency, host-event contract, scenario and vendor-specific runbook. Publish only after its
artifact resolves; keyless local fixtures support development. Fix feedback 29's dependency-shadowing
defect before recommending the authoring route. The template id/coordinate belong to producer delivery,
not an analyser allowlist.
Dependency identity (feedback 30/33) remains a compiler/starter prerequisite for any claim about verified
vendor binaries: record effective classpath artifact bytes and order, bind build/run receipts, and compare
against a trusted expected manifest. Comment-contract hashes do not cover application dependencies.
Until that gate exists, the vendor journey must state its identity limitation and cannot call a component
certified or verified merely because its filename/coordinate resolves. Missing audit scaffolding (6) is
separately tracked as behaviour work, not closed by generated comments.

## Acceptance

1. Website-created skeletons default to support enabled; explicitly disabled configurations remain off
   through share/import/download and the headless override. Test a captured old token and JSON file with
   no field (now on), explicit true/false, malformed/repeated parameters and special-bundle conflicts.
   Verify additive support files without unintended runtime/build changes on old inputs.
   A template with explicit false and no query override stays false; a true override enables it.
   A template with absent support decodes to true; a false override disables it. Test the resulting ZIPs,
   not just the parsed flag.
2. Website and headless generation of the same effective spec produce equivalent project contents.
   The four requested starting journeys have applicable runbooks: worked example, Mongoose + Spring,
   bare Fluxtion, and vendor integration. Vendor documentation separates consuming from publishing a library.
3. Profile paths and bootstrap links resolve after extraction to a different directory. Instructions
   match actual scripts and selected hosting/plugins. Missing future outputs are identified honestly.
4. A fresh local LLM starts in an empty directory with catalogue/bootstrap access, without repository
   source or observer coaching, downloads a project and follows its runbook to measured first results.
   No analyser is required for download, build or application execution.
5. The human and MCP context expose the same persisted project declarations after close/reopen.
   Test with and without a loaded log, changed/missing evidence files, and a switch through another
   project. Saved definitions must survive; restore decisions must not mix project state.
6. Witness the selection/download/open and close/reopen UI paths. Headless profile tests alone do not
   establish that the complete user journey works. Include quit/relaunch with an active project, switching
   A→B before relaunch, failed/moved project activation, no-project sessions, legacy remembered global paths,
   changed/missing evidence and explicit command-line logs. Assert no automatic restore before acceptance
   and no cross-project candidate selection. No application script runs during restoration.
   Include a saved chart pinned to a disjoint prior-run window and a separate active dimension/text filter:
   expose both restrictions to the person and client, distinguish no visible points from no source data,
   and offer explicit clearing without silently rewriting an intentional saved window (feedback 41).
7. Without a loaded log, assert saved-chart facts in context, the read-only Project-panel row and the
   landing separately from open tabs. Exercise open/restore from the landing and retain
   `ProjectPanelIsRevealOnlyTest` unchanged as the panel boundary gate. Check all three `syncRecordsCard`
   states and failed-load behaviour.
8. Generate ordinary Mongoose, hosted Spring, bundle, embedded and support-disabled fixtures. Each runbook
   names only supported/emitted operations; test graceful foreground stop independently of registry-backed
   stop, and audit export only where configured. No owner credentials or paid calls are needed for fixtures.
   For hosted fixtures, check the runbook's ordering domain, cross-feed limitation, reset/state table and
   selected connector's replay scope against effective configuration. Exercise disposable restart/reset
   scenarios and cached versus uncached pre-start delivery where supported; observe positions/state rather
   than assuming they reset. Check a late subscriber and a second dispatch without new cached entries
   against the resolved connector's contract. Derive expected defaults from that pinned artifact, not the
   snapshot observations above. Check disabled capture and enabled capture with/without processor auto-start,
   directory resolution and configured roll/retention/export settings. Assert that runbooks distinguish
   recording from installation, cached delivery from session replay, and unsupported operations from
   available ones. Any selected replay plugin needs its own bounded fixture; no real user evidence is deleted.
9. Assert unique emitted paths for bundle and ordinary support-enabled projects. Inject duplicate profile
   and agent-guide paths and require `generate()` to fail before ZIP creation. Verify the bundle retains
   its contract-specific profile/bootstrap content after general emitters are factored out.
10. For the generated fixture matrix, check that README reaches the bootstrap and applicable task guides,
    and support-enabled profile runbook paths reach those same files after relocation. Disabled support
    retains essential build/authoring/hosting guidance. Verify references against the pinned versions;
    unavailable or incompatible references fail the documentation check rather than silently falling back.
    Separately exercise the explicit legacy/profile-less fallback writer: its output is discovery-only,
    discloses unverified compatibility, asserts no matched tool version and preserves an existing guide.
11. Compile representative commented stubs and exercise reconciliation after implementation and after a
    no-op run. Preserve developer bodies, record compatibility and browser/Java ownership-hash agreement.
    Change a commented reference DATA→TRIGGER and back; require correct annotation changes, preserved
    developer code, compilation, and comments still accurate in both states. Assert choice-neutral wording
    against the canonical comment fixture in both emitters; ignoring comments in a hash is not this check.
    Verify the comment resource from the `starterCore` artifact resolved at the authoritative Repsy
    endpoint above against the vendored coordinate/version and digests without compiler-repository access.
    Use the resource digest for content parity and the artifact digest for immutable release integrity.
    Require missing resources and
    altered vendored bytes to fail. Compare emitted guidance for the same construct and artifact version directly, allowing only
    indentation/line-ending differences. Mutate one emitter's wording and require the parity assertion to
    fail even though ownership hashes remain equal; neither emitter may independently redefine the text.
    Exercise the selected failing diagnostic cases and verify rule, consequence and actionable correction
    against the actual fixture. Comments alone cannot close a behaviour defect.
12. Freeze predictions before the fresh-client journey in acceptance 4. Record which entry/runbook links
    are followed, applicable hazards found before the first affected edit, time to first checked result,
    corrective iterations, unused guidance and every owner intervention. Include a held-out task, such as
    adding an observer/report to an already verified processor. For that task require two separate results:
    (a) report correctness against independently derived per-book/per-symbol expectations from the feed,
    with named assertions for arithmetic, grouping, accepted-order count and threshold below/equal/above,
    checking both structured audit summaries and rendered report output, both trigger paths and empty state;
    (b) existing application observations compared separately as non-regression. A generated report or
    unchanged old outputs cannot satisfy (a). Inject isolated mark-selection, multiplier, threshold,
    grouping and order-count errors; each must fail a named report assertion. Preserve expectations and
    actual values, not only a pass count. Other held-out tasks need analogous independent checks of their
    new behaviour. An assisted run is recorded as assisted, not passed as unassisted discovery.
    Publish the task, versions and bounded results; one successful session is not cross-model validation.
    **Adoption threshold (H3):** require the same routing friction in two independent sessions on different
    applicable tasks before treating it as a recurring finding. Trial a correction with before/after
    evidence on those tasks, then require an unassisted held-out task to pass its frozen checks before
    adopting the correction. A failed or assisted held-out run leaves adoption open. A single-session
    observation remains a hypothesis; independently reproduced broken links/incorrect instructions can be
    fixed as factual defects without claiming recurring usability evidence. Removing guidance as unused
    requires non-use in both applicable task sessions and the held-out run, plus a recorded check that no
    unique prerequisite or hazard is lost (retain or reroute such content). Archive removed material and
    measurements. Stop and hand off after a clean held-out run or report non-convergence; do not add/delete
    indefinitely. This applies the onboarding recurrence rule without making non-use proof of irrelevance.
    Record source/example imitation and whether it followed the template's intended runtime shape.
    The now-discussed EOD scenario is a regression task, not held out. Select a different unexposed task
    before the participant starts; preserve an access manifest proving the client cannot read prior
    transcripts, retrospectives, solutions, oracles or mutant-killing answers (including staged desk files).
    Start from a clean generated download, not the staged project. Keep the assessor's independent
    expectations outside the participant's accessible workspace; record any isolation breach as contamination.


### Cold-start instrument intake (2026-09-20)

The [preserved proposal and intake review](../handoff/evidence/coldstart-proposal-2026-09-20/REVIEW.md)
provide pre-action attribution, example-imitation records and pristine-download baseline provenance for
acceptance 4/12. A journal is testimony; transcript/tool observations and operator-owned snapshots check
its chronology. Static fingerprints are review leads, never automatic defect or pass counts. Record search,
prior knowledge and operator intervention separately and adjudicate whether routing actually failed.
The reconstructed v1 session is historical context, not a matched quantitative control. Choose one fresh
session per complete journey and keep the operator prompts, injected-error answers and prior solutions
outside the subject's enforced access boundary. Validate the scorer inputs and report unknown/missing
observations before using its output for acceptance. This intake does not claim a trial has run or authorize
paid key use; the existing recurrence and held-out thresholds still govern adoption.


### Processor declaration wire contract — implementation pin, version 1

The profile uses `processorDeclaration.version=1`, `processorDeclaration.count=N`, then zero-based
`processorDeclaration.i.name`, `.kind` and optional `.fqcn`. Kinds are `declared` (requires a qualified
class name), `runtime` (no fixed generated type) and `unspecified` (not yet chosen). The latter two forbid
an FQCN. Names are unique within the declaration list. A missing family is legacy/undeclared, not evidence
that the project has no processor. Unknown versions, kinds and malformed declarations refuse profile
import before replacing active project state. This metadata travels with the event-processor share category;
import merges declarations by name and project activation replaces the category. Older readers preserve
the unknown property family. New readers expose it as `context.processorDeclarations` and on the Project
panel/landing; source availability and observed execution remain separate facts. No fabricated generated
name is added for an interpreted/in-process processor.
