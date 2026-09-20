# Author response — project starter journey review

2026-09-20. Responds to [the independent design review](review_project_starter_journey_2026_09_20.md).
Changes are to specifications/tracker only, in the current working tree; the reviewer's file is untouched.
The CONDITIONAL verdict remains theirs to revise. Nothing here claims a product fix, release approval,
new publication, or witnessed launch restoration.

## Owner answers obtained

1. **Old links gain support too.** `analyserSupport` absent means true; false is preserved explicitly.
   Old outputs intentionally gain integration files while build/runtime choices remain unchanged.
2. **One explicit restore offer.** Quit/relaunch and project close/reopen both use the same project-scoped
   offer. The current implicit startup opens must be replaced, not left alongside it.

The remaining review questions are addressed within existing owner direction: a headless override makes
the already-required opt-out usable; session memory is user-local, never portable; recommendations disclose
unknowns; hosted runbooks describe supported capabilities rather than silently adding plugins. StartPanel
is the proposed authoring host. A real vendor starter is scoped alongside the existing M67 track.

## Finding dispositions

These are author dispositions of design requirements, not independent closure or completed implementation.

| Finding | Response / change | Remaining acceptance |
|---|---|---|
| F1 | Accepted. Picker spec names profile adoption versus unselected discovery. Release expansion after default-on generated support, with explicit legacy and opt-out cases. | Generate the catalogue and witness both branches; do not accept a silent fallback as a complete supported download. |
| F2 | Accepted. Tracker now says producer landed, analyser consumer open. Picker must render `agentBootstrap` present/empty/absent and verify the effective ZIP. | Producer consistency tests and human disclosure. |
| F3 | Accepted. Require declared key facts or “not declared” on every entry, including recommended ones. No inference from AOT; build versus regeneration stays distinct. | Catalogue/default ZIP checks and UI cases for missing/unrecognised values. |
| F4 | Gap accepted; wording narrowed. The token route is technically callable without a browser; its source says clients should not need to reproduce compression, not that it is impossible. Specify `analyserSupport=true|false` on the template route with validation/conflict rules. | API tests and documentation; plain-JSON POST remains optional. |
| F5 | Owner resolved: old links also gain support. No tri-state migration needed. Default true at decode; explicit false retained. Do not reuse Maven project `version`. Special bundle + false is a clear validation conflict. | Captured legacy-token/JSON fixtures, explicit false round-trip and no unintended runtime changes. |
| F6 | Accepted capability gap; do not generalise all bundle machinery by implication. Ordinary Mongoose already emits a shutdown hook for foreground Ctrl-C. Specify that tested route, and conditional registry/capture recipes only with their prerequisites. | Test ordinary graceful stop and configured audit routes; any later helper generalisation must state its plugin floor. No invented export script or forced instrumentation. |
| F7 | Accepted. Add saved-definition facts from config above the no-filter return, separate from live-tab `graphs`. Name Project-panel/landing row and existing public docs in the same change. | Empty-log human/MCP parity and binding status tests. |
| F8 | Accepted startup policy gap. Owner selects an offer for both paths. Replace `Main.main`/`reopenLastGraphml` implicit restoration in the same slice; handle legacy unassigned paths and explicit CLI input. | Relaunch, A→B, failed activation and stale/missing-input tests. Cross-project harm remains source-derived risk, not a reproduced incident in this response. |
| F9 | Accepted. Versioned user-local session candidates keyed by canonical profile location, separate no-project bucket, excluded from portable/share outputs. | Identity, relocation, missing files, partial refusal and unchanged portable-profile checks. |
| F10 | Accepted. Generator owns new project bootstrap. Existing analyser fallback remains explicit and keeps its no-overwrite guard. | Existing generated guides preserved; opt-out not silently undone. |
| F11 | Accepted. Distinguish declared/not-generated, runtime without fixed generated type and unspecified processor; never invent a class name. | Pin additive producer/consumer metadata schema before implementing this slice; cover interpreted and multi-processor hosts. |
| F12 | Accepted. Widen `syncRecordsCard` into generic-start / active-project landing / records states, with one decision site. | Three-state and failed-load tests plus visual verification. |
| F13 | Accepted. Acceptance explicitly names quit/relaunch and project-switch cases, not just close/reopen. | New witnessed run still owed; previous packet remains unchanged. |
| F14 | Accepted as design proposal. Use existing StartPanel and shared selection/download controller; no extra window or configurator. | Review layout and navigation before implementation. |
| F15 | Accepted. Scope a new vendor catalogue entry with M67 coordination, supported artifact and tested runbook; connector is not an alias for it. | Dependency-shadow fix and resolvable artifact gate before recommendation/publication. |
| F16 | Accepted. Playground `web/static/CLAUDE.md` owns the empty-directory recipe, linked from golden path. | Fresh-client discovery using deployed documented API, without repository source. |

## Verification and limits

Read independently: `Main.main` startup opens; `MainFrame.syncRecordsCard`, early context return and
live-tab graph reporting; `AppConfig`/profile separation from prior investigation; playground main's
catalogue bootstrap field; special bundle helper gating and ordinary Mongoose shutdown hook; existing
template/token endpoint and schema. These support the contract corrections. The two reviewer overstatements
above do not remove the corresponding work.

No UI, network publication, compiler service or application was run for this response. Existing close/reopen
evidence remains the earlier isolated real-app probe; it did not exercise startup restoration.
`SpecLinksResolveTest`: 3 tests, zero failures/errors/skips. `git diff --check` and a sweep of the changed
spec/response text pass. Detailed metadata schema and authoring layout remain
explicit design work; do not claim the whole multi-repo implementation is ready solely because the two
owner policy choices are now settled.

## Response to re-review G1–G3 — 2026-09-20

Read [the re-review](rereview_project_starter_journey_2026_09_20.md) and the cited builtin-template parser,
ZIP assembler and panel structural test. All three findings are accepted and corrected in the journey
spec. The reviewer's file and verdict are unchanged; these are specification fixes, not product fixes.

| Finding | Correction | Required implementation evidence |
|---|---|---|
| G1 | Missing query parameter means no override. Default true belongs to spec decoding only; a template's explicit false survives an omitted override. | Template false + no override remains off; explicit true enables it. Absent spec field defaults on; explicit false query disables it. Check ZIP contents. |
| G2 | Each emitted path has one owner. For bundle specs, the bundle owns shared profile/bootstrap paths and general emitters skip them. `generate()` must reject duplicate paths before ZIP assembly, even with equal contents. | Bundle/ordinary uniqueness, injected duplicate profile and guide failures, preserved bundle-specific contents. |
| G3 | The Project panel states saved-chart facts and remains reveal-only. StartPanel's landing offers open/restore via the existing action/session machinery. No Navigator expansion. | Context-backed panel facts, landing actions and unchanged `ProjectPanelIsRevealOnlyTest` boundary gate. |

No new owner decisions, UI run, network fetch, template generation or application execution were needed
for these text corrections. Source inspection confirms the current code paths cited by the reviewer;
runtime implementation and the new regression cases are still owed.

Checks after G1–G3 corrections: `SpecLinksResolveTest` and `ProjectPanelIsRevealOnlyTest` pass
(5 tests, zero failures/errors/skips); `git diff --check` and the changed-text anonymisation check pass.

## Additional review scope — learning routes, 2026-09-20

After the G-series response, the owner supplied participant feedback ranking stubs, field tables,
diagnostics and refusals above unvisited prose. The journey spec now proposes “Learning at the point
of use” and acceptance 10–12. Review these as new requirements, not previously accepted G-series fixes.

Attack project-specific bootstrap/runbook routing, ownership of generated comments and diagnostics,
version-compatible references, stub-hash compatibility, and the unassisted/held-out acceptance method.
The suggested 250-line entry is a target, not a gate. No new MCP guide verb or analyser content store is
approved; existing pointers and external reading remain sufficient for this design. Error-message scope
is selected reproduced failures, not an unbounded rewrite. The participant's twelve-fact list is task
evidence, not an authoritative framework contract. No product code or fresh-client run is claimed.

## Response to second re-review H1–H3 — 2026-09-20

Read [the second re-review](rereview2_project_starter_journey_2026_09_20.md). Its F/G closures stand;
the review file is unchanged. Accept all three new findings and correct the specification, not product
code. The reviewer's CONDITIONAL verdict is theirs to revise.

| Finding | Specification correction | Required implementation evidence |
|---|---|---|
| H1 | Generated comments are choice-neutral and cannot describe the initial selection as current state. Compiler/starter contract owns canonical wording; playground consumes a pinned copy with parity fixtures. Existing comment-insensitive hashes remain, but are not a semantic check. | Acceptance 11 changes DATA→TRIGGER→DATA on a commented reference, checks annotations, preserved code, compilation and truthful comments, alongside no-op and ownership checks. |
| H2 | Explicit exception for the analyser fallback: version-neutral discovery pointers, compatibility unverified, no claim of a matched project version. No new resource field. Generator-produced guidance retains the version requirement and reuses existing provenance machinery where applicable. | Acceptance 10 separately checks fallback disclosure and no-overwrite behaviour; generated-project checks cannot silently use this exemption. |
| H3 | Two independent sessions on different applicable tasks establish recurring friction; trial before/after and an unassisted held-out pass precede adoption. Guidance removal requires non-use across those sessions and held-out run, plus a unique-hazard/prerequisite check and an archive. | Record task applicability, frozen checks, interventions and outcomes. Failed/assisted held-out runs leave adoption open; single observations stay hypotheses. Reproducible factual defects can be corrected without claiming usability recurrence. |

Read in source for this response: `ReferenceSet.Resource` has no version field and keeps the
pointers-only boundary; playground bundle generation already uses minimum-analyser-version checks and
skill provenance; ONBOARDING states recurrence across distinct tasks and the held-out/archival process.
H1's runtime reproduction and comment-hash checks are the reviewer's evidence, not independently rerun
here. No UI, template generation, key use or fresh-client experiment was performed for these corrections.

Checks after H1–H3 corrections: `SpecLinksResolveTest` and `ProjectPanelIsRevealOnlyTest` pass
(5 tests, zero failures/errors/skips). `git diff --check` and the changed-document policy sweep pass.

## Response to consolidated review H1–H6 — 2026-09-20

Read [the consolidated review](review_all_specs_2026_09_20.md) in full. Its quoted learning section and
acceptance text precede the H1–H3 corrections above. Do not erase the historical findings or infer a
new reviewer verdict from the current edits: all author corrections still require confirmation.

H1–H3 are answered in the preceding table. H4 was also addressed while answering the second re-review's
ownership comment: the compiler/starter contract owns canonical text, playground consumes a pinned copy,
and acceptance 11 compares both to the canonical fixture. This response makes the direct emitted-text
comparison explicit and requires a one-emitter mutation to fail despite unchanged ownership hashes.

| Finding | Author disposition / specification correction | Required implementation evidence |
|---|---|---|
| H4 | Accepted; one canonical owner, revision-pinned reuse and direct emitted-guidance parity, independent of hashes. | Compare same construct/revision allowing only indentation/line-ending differences; change one emitter's wording and see the test fail. |
| H5 | Accept the need for named checks. The earlier phrase “independently check its new outputs” did not intend mere output existence, but left the check underspecified. Acceptance 12 now separates independent report correctness from old-behaviour non-regression explicitly. | Feed-derived per-book/symbol expectations, rendered and structured outputs, arithmetic/grouping/count/boundary cases and isolated mutations that fail named assertions; old outputs checked separately. |
| H6 | Accepted. Hosted runbooks now specify ordering domains, reset effects and feed position, connector-specific cached delivery versus full replay, and effective capture/recording/retention/export settings. | Acceptance 8 adds disposable state/position and cache tests, capture disabled/enabled/auto-start cases and configuration-to-runbook checks; no unsupported reset or blanket recorder activation. |

Independently read for H6: Mongoose `InMemoryEventSource.start/startComplete`, publisher
`dispatchCachedEventLog`, and `AuditCaptureConfig`. They confirm opt-in caching, the advancing cache
read pointer and the distinction between capture installation and per-processor recording. No Mongoose
server, replay plugin, fresh-client experiment or template generator was run for these spec corrections.
The dependency/schema/version-floor and vendor-publication prerequisites listed by the reviewer remain
open implementation prerequisites; addressing H findings does not manufacture those artifacts.

Checks after the consolidated corrections: `SpecLinksResolveTest` and `ProjectPanelIsRevealOnlyTest`
pass (5 tests, zero failures/errors/skips); `git diff --check` and the changed-document policy sweep pass.

## Response to re-review J1–J3 — 2026-09-20

Read [the latest re-review](rereview_all_specs_2026_09_20.md). Its F/G/H closures stand and its verdict
is unchanged. These are author corrections pending confirmation, not product fixes or a publication claim.

| Finding | Correction | Required implementation evidence |
|---|---|---|
| J1 | Choose the public released `com.telamin.fluxtion:fluxtion-starter-core` artifact, classifier `all`, carrying `META-INF/fluxtion/starter/comment-contract.json`. Java reads the resource; playground vendors it by published `starterCore` version with artifact/resource digests. No closed compiler commit pin or private-repository dependency. | Acceptance 11 verifies public artifact/resource provenance, rejects missing/changed bytes, then compares actual emitted guidance and exercises the existing one-emitter mutation. Local artifact development is explicitly insufficient for publication acceptance. |
| J2 | Stamp the read source as `com.telamin:mongoose:1.0.30-SNAPSHOT` at `17a03b4`, distinct from the inspected playground `1.0.29` pin. | Acceptance 8 derives default expectations from the project's resolved artifact, not the snapshot recital. No compatibility with the released pin was inferred or run here. |
| J3 | State that cached-prefix dispatch advances a publisher-wide pointer; late subscribers do not receive that consumed prefix and a second dispatch without new cached entries sends nothing. | Acceptance 8 includes late registration and repeat dispatch against the selected connector's resolved-version contract. This does not claim that later newly cached entries can never be dispatched. |

Read locally: Mongoose POM and publisher pointer advancement, playground's version pins, and the staged
starter coordinate/classifier in the existing launcher. The comment resource is a proposed release
contract, not an assertion that it already exists or has been published. No private source revision needs
to be distributed to implement this choice. No runtime cache probe, publication fetch or template build
was performed for these corrections.

Checks after J1–J3 corrections: `SpecLinksResolveTest` and `ProjectPanelIsRevealOnlyTest` pass
(5 tests, zero failures/errors/skips); `git diff --check` and the changed-document policy sweep pass.

## Final review — K1/K2 wording corrections, 2026-09-20

The [final re-review](rereview_final_project_starter_journey_2026_09_20.md) closes J1–J3 and records a
READY combined handoff, subject to naming the comment artifact's authoritative endpoint. Its file is
unchanged. Both requested wording corrections are applied:

- K1: name `https://repo.repsy.io/mvn/fluxtion/fluxtion-public`, matching the generated POM used by
  `setup.sh`. A convenience mirror must be verified against that endpoint's Maven coordinate; it cannot
  satisfy the publication/parity gate independently. Acceptance 11 names this authority explicitly.
- K2: enforce the artifact digest only for an immutable released coordinate. The extracted resource
  digest is the content-parity oracle; whole-jar equality across repacks/local builds is not that check.

Read the playground generator's `REPSY` constant and the staged project's POM/setup command to confirm
the endpoint/coordinate relationship. No artifact was fetched, publication performed or mirror verified.
The resource still has to be implemented and published before its public parity gate can pass. Local
development remains allowed, with no release claim. This does not reopen the settled F/G/H/J design.

Checks after K1/K2: both focused tests pass (5 cases, zero failures/errors/skips), `git diff --check`
passes, and the changed-document policy sweep passes.
