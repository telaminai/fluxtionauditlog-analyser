# Review: guided Spring authoring feedback, 2026-09-25

**Verdict: actionable friction, with several proposed diagnoses narrowed.** The strongest
common failure is the second structural edit: stale generated code prevents regeneration,
a rename leaves ownership debt, and the analyser can retain a previous Java snapshot.
The default malformed-input fallback also merits an early fix: it manufactures business data.

**Updated during review:** feedback grew from twenty to twenty-four items; the addition
to item 19 and items 21–24 are assessed below. No new client trial was run.

The [proposed specification](../specs/spec-spring-authoring-edit-loop.md) sets the contracts,
acceptances and wrong-result controls. Nothing is implemented or declared closed by this review.

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
| 1 — compile ordering | **READ + REPORTED.** Current POM runs scan at `process-classes`, followed by compile-generated; ordinary compile still precedes it. Constructor/rename failures are reported, not rerun. | **A / UP-FLX-21**, already open. Solve model/dispatcher/consumer dependencies; removing a file is not a durable pipeline. |
| 2 — rename debt | **REPORTED**, with the session notes explicitly retaining the old ownership debt. Conflict refusal is desirable; repeated opt-out is not repair. | **D**, compiler/starter ownership migration, including the next default regenerate and new-node creation. |
| 3 — stale Java | **READ** identifies a sufficient mechanism: same-FQN navigation skips rendering and selected-model parsing is separately cached. Repeated session symptoms remain **REPORTED**. | **C**, analyser ordinary-navigation freshness. Keep source/run identity separate. |
| 4 — conflict advice | Error text is **REPORTED**. The proposed universal field-name rule is too strong; existing bindings may differ from bean ids. | **A**, actionable diagnostics with only established declarations suggested; also UP-FLX-32's wiring guidance. |
| 5 — foreign recovery offer | **REPORTED, cause unresolved.** Existing controller/store/node already partition and validate project keys. A path outside the project can be a legitimate chosen input. | **E**, reproduce capture/switch order; disclose provenance; existing journey recovery, not a new auto-restore policy. |
| 6 — fabricated zero event | **READ** in the current original mapper and template source: fewer than three fields produce a zero-valued PriceEvent. The session uses a replacement mapper later. | **B**, early template correction under D-T9/D-T8; assert no fabricated decision plus visible rejection. |
| 7 — cumulative capture | **REPORTED**; persistence is not inherently wrong. Moving capture aside was a manual session workaround. | **H / MA-2 / OD-5**, explicit export scope and authoritative run boundary; no implicit deletion. |
| 8 — script permissions | **READ**: three local scripts are mode 0644. Inspected template objects mark them executable. This does not locate the loss in the acquisition/extraction path. | **G**, real ZIP metadata/extraction check on each route; do not close on the in-memory flag test. |
| 9 — CLI help/link | **READ**: starter special-cases help only as the first argument. `link` encodes the XML into a browser URL; it does not migrate ownership. No CLI was executed here. | **G**, per-command help and accurate link documentation, no project mutation. |
| 10 — incomplete/drifting guides | **READ**: truncated RUNBOOK sentence exists in project and template emitter. Local starter jar has no contract document entry. Version-table confusion is **REPORTED**. | **G**, immutable matching contract, complete workflow and conflict-specific repair guidance. |
| 11 — read grants | **READ**: DesignFiles explicitly makes project a relative base, not permission. Current profile has roots added by the session; it does not prove original grants for every file. | **E / M68.5**, separate resolution from authorisation; proposed role grants require approval. |
| 12 — overlapping timestamps | **REPORTED**; same-millisecond chart readability is already an open requirement. | **H**, existing record-order x-axis item; retain original record identity, no producer sleeps. |
| 13 — missing stdout audit | **REPORTED**, consistent with the already-recorded listener replacement defect. An absent stdout match does not establish no execution. | **H / MA-5**, released-version fan-out and restoration acceptance; do not duplicate the analyser's diagnostic work. |
| 14 — authoring patterns | Tips/results are **REPORTED**; some are explicitly untested. Public vendor-composition guidance already exists, so “nowhere” is too broad. | **F/G**, improve generated entry points and executable patterns, not new advice to bypass name validation. |
| 15 — validate versus generation/cost | **READ**: RUNBOOK explicitly promises XML-only validation. Which failures reached/charged a provider is unknown. | **F**, separate model preflight and evidence-based request/billing receipt, preserving inert validation. |
| 16 — canonical deduped id | **REPORTED**, not independently established in this pass. Changing naming precedence could break consumers. | **F**, expose aliases/selected id; owner decision on compatibility after a reversed-order fixture. |
| 17 — large context | **REPORTED**; existing read projection does not solve context overhead. No byte/token measurements made here. | **H**, opt-in context sections with same-state equivalence and verdict qualifications; preserve default compatibility. |
| 18 — descriptor copy drift | **READ**: hosting guide links the config and also calls its copied YAML effective. Historical drift is **REPORTED**. | **G**, link the actual file, label any example historical; do not duplicate mutable authority. |
| 19 — vacuous starter test | **READ**: supplied test only asserts a non-null processor; POM already declares mongoose-test-support. The update correctly points to existing infrastructure. | **G**, use the actual hosted wiring with the harness plus an independent behavioural table and wrong-result control; keyless on the shipped processor. |
| 20 — vendor sources | **READ**: MavenSourceResolver already searches local source jars. Current vendor POM has no source-archive attachment configuration; that alone does not prove none was installed. | **C/G**, source availability/discovery/refresh and supplier instructions, not a new resolver by assumption. |
| 21 — mapper extension/discovery | **READ**: core 1.0.29 exposes a generic per-feed mapper; plugins 1.0.44 has TypeSerialiser. Its documented discriminator/configuration differs from its implementation. | **G1**, teach composition with executable pinned examples and visible rejection; not just an extra link to the current recipe. |
| 22 — replay contradictions | **READ**: current runbook disclaims a supplied replay command/recorder but its descriptor comment promises deterministic replay. Connector capabilities do not resolve that conflict. | **G2 / H**, a configuration-specific capability table and actual replay acceptance; keep audit inspection distinct from input reconstruction. |
| 23 — runtime loading versus AOT | **READ**: plugins 1.0.44 Spring loader has compile and interpreter branches and is preview-marked. No route was run. | **G3**, compare build-time AOT, load-time compile and load-time interpretation, with measured prerequisites and narrowly tested equivalence. |
| 24 — plugin-site version | **READ / live page fetched**: overview advertises 1.0.37; current project POM pins 1.0.44. Latest release not independently established here. | **G4**, versioned documentation and generated labels, not a one-off “latest” number replacement. |

## Corrections that matter before implementation

1. **“Small compile-order fix” is not established.** Generation needs the edited model, while
   suppliers can import the generated processor. A fix must handle both, and failure must not
   leave the last usable processor deleted. UP-FLX-21 gets this stronger acceptance.
2. **`link` is not rename.** It opens the authored design in the browser. A new ownership
   operation requires its own explicit mapping and safety contract.
3. **Source freshness has a concrete cache boundary.**
   [SourcePanel](../../src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/SourcePanel.java)
   `navigate` renders only a new FQN or empty pane; `refresh` recolours. Its `Pane.render`
   does read source, but is bypassed for an existing nonempty pane.
   [SourceService](../../src/main/java/telamin/fluxtion/audit/analyser/analyser/source/SourceService.java)
   caches `selectedModel` and already has a separate fresh snapshot route for spotlights.
   Reopening a log is not proof that an ordinary source pane reloaded.
4. **Recovery is already project-scoped.**
   [SessionRecoveryController](../../src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/SessionRecoveryController.java)
   loads by `SessionResumeStore.key(profile)`;
   [SessionRecovery](../../src/main/java/telamin/fluxtion/audit/analyser/analyser/session/node/SessionRecovery.java)
   rejects a mismatched key and stale generation. Capture timing, profile identity or a legitimate
   external input may explain the report. Do not invent a diagnosis from the path alone.
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

## Verification of this documentation change

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
  and registers the processor. It is preview-marked. Neither dispatch equivalence nor key
  requirements were established by this read; §G3 requires evidence before those claims.
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
after crashes. §I2 therefore requires project/server identity and an actual bounded read-only
probe before offering a browser link. No live registry, token or running server was accessed.

**READ from a fresh public clone:** vendor collection head `3a89391` is source-only by its
own publication contract. `QuoteView` and the demo's concrete PriceEvent are different
contracts, and the supplied CSV adapter is callback-based rather than a valueMapper Function.
§I3–I4 specify compatible shared types, mapper/wiring, source jars, binary resolution and
M67's staged tour, preserving the deliberately incorrect risk A and its independent oracle.
No vendor source or binary was changed and no guided tour was run by this inspection.

These additions extend the specification. They do not claim the requested UI or vendor/demo
changes are implemented. The canonical M67 tracker entries remain open.


**Revision 3 documentation gate RUN:** JDK 21 `mvn -q test`: **1,996 / 0 / 0 / 98**
(total/failures/errors/skips), 266 reports. Strict MkDocs, diff whitespace and the tracked-file
public-data sweep pass. No display acceptance is claimed for the newly proposed journey.
The owner authorised reusable demo implementation on branches, leaving publication for review;
that authorisation is not a claim that implementation has started or passed these acceptances.
