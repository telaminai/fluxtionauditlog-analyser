# Independent review: Spring authoring edit-loop specification (PROPOSED v3)

Subject: `docs/specs/spec-spring-authoring-edit-loop.md` at `63065b91`, branch
`docs/spring-session-friction-2026-09-25`, with its companion feedback review and the branch's tracker
and upstream-asks changes. Reviewed 2026-09-25 in an isolated worktree. I did not implement anything,
edit the subject documents, merge, publish, run a client session, or use a compilation key.

## Verdict: changes required, narrowly

The specification's posture is right throughout: it does not weaken ownership refusal, read
authorisation, or source/run qualification. It also keeps participant testimony separate from
inspected fact. Most slices are implementable as written.

Seven factual premises are wrong or incomplete, though, and each one would send an implementer to
the wrong component or produce an acceptance that cannot detect the defect it targets:

- **R1, compile pipeline (§A):** it asserts a supplier-import dependency that the template does not have.
- **R2, script permissions (§G):** it routes feedback 8 to the template producer, but the permission
  loss is produced by the analyser's own template installer. The proposed archive-mode control
  cannot catch it.
- **R3, recovery (§E):** it treats the foreign restore offer as undiagnosable. Local evidence now
  points at a specific, cheaply reproducible gap: a path-only recovery key.
- **R4, source freshness (§C):** it misses the Topology tab's separate source pane and a model cache
  that survives a reread.
- **R5, mapper and feed (§B/§G1):** it misdescribes `TypeSerialiser` and assumes a rejection surface
  that core does not provide.
- **R6, console (§I2):** it requires server identity matching that the registry cannot supply.
- **R7, vendor contract (§I3):** its shared market-event contract cannot work with Fluxtion's
  exact-type-first dispatch while the demo keeps a concrete handler.

Once R1–R7 are corrected in the text, the specification is ready to implement slice by slice, and
several slices can start before that (see the last section).

## Evidence boundary

- **Participant feedback, read-only:** `FLUXTION-FEEDBACK.md` is 30,284 bytes, SHA-256 `cddd7240…c3`.
  `SESSION-NOTES.md` is 9,848 bytes, SHA-256 `95946fed…7c`. Both were read at 10:26 UTC, and both
  match the companion review's mid-cycle hashes, so there has been no change since then. The
  feedback has 24 numbered items. Its owner additions (design-first tour, console, vendor jars) come
  from the specification, not the feedback file.
- **Public sources, cited by path and line:**
  - analyser `63065b91`
  - `telaminai/mongoose` tag v1.0.29 (`68a790b`)
  - `telaminai/mongoose-plugins` tag v1.0.44 (`117ce80`); v1.0.45 was also diffed
  - `telaminai/fluxtion-vendor-jars` `3a89391`
- **Private sources, behaviour stated without locations or excerpts:**
  - starter and compiler, `fluxtion-compiler` tag v1.0.74
  - builder at the plugins' pinned v1.0.67
  - template emitter, `fluxtion-web` `5d6a38a`

  Exact locations are held in local review notes.
- **The analyser's local recovery store:** listing its one snapshot's key, capture time and input
  path, read-only. No file content is reproduced here beyond those facts, and no private path is
  published.
- **Not done:** no product scenario, UI check, mutation, provider call, archive download, console
  probe or live-session interaction.

## Required corrections

### R1 — §A: the supplier does not import the processor, and the real gap is the scan's write

**Premise.** §A (lines 63–67) and priority 1 of the brief assume that "a supplier imports" the
generated processor, so excluding the processor would break the supplier. In the 1.0.74 template,
the supplier resolves the processor by class name through reflection, and no node refers to the
processor. There is no compile-time cycle.

**The pipeline that works.**
1. In the `generate-fluxtion` profile only, exclude the generated package from the ordinary compile.
2. Compile the nodes and the supplier.
3. Run the scan. It genuinely needs compiled classes: it builds a class loader from the compile
   classpath, including `target/classes`, and instantiates the Spring beans.
4. Compile the generated processor (the existing `compile-generated` execution).

The keyless default build without the profile must keep compiling the committed processor.

**Where the transactional risk actually sits.** `regenerate` already snapshots, writes per-file
atomically, and rolls back on I/O failure. The plugin's scan writes the processor directly into the
source directory, with no staging. The §A staging requirement should name the scan's output as the
protected artifact.

**Corrections.**
- Replace the supplier-import premise with the verified dependency graph.
- Keep "a customer supplier that statically imports the processor" only as an explicit contract
  choice, either supported with a test or documented as unsupported. It is not a present blocker.
- Name the scan output write as the step that must be staged.

**Failure scenario if left as written.** An implementer designs a two-stage consumer compile to
solve a dependency the template does not have, while the stale-source break and the unstaged scan
write remain.

### R2 — §G feedback 8: the permission loss is the analyser installer, not the template ZIP

**Evidence.**
- The template's ZIP writer marks `setup.sh`, `validate.sh`, `generate.sh` and
  `check-fluxtion-key.sh` executable, like the hosting scripts.
- The analyser's `TemplateArchive` deliberately ignores archive modes and applies a fixed allowlist
  (`src/main/java/.../template/TemplateArchive.java:34-35, 213-230`): `mvnw`, `run-server.sh`,
  `export-audit.sh`, `stop-server.sh`, `check-fluxtion-key.sh`.
- The participant project's modes match that list exactly: `mvnw` and the four hosting scripts are
  0755, while `setup.sh`, `validate.sh` and `generate.sh` are 0644.
- `TemplateArchiveTest:44-49` asserts that an archive's 0755 claim on a non-listed file is not
  applied. That is correct security design, and it is also why the spec's control, "removing the
  authoring scripts' ZIP mode must fail the archive check" (line 245), cannot detect this defect on
  the analyser route.

**Corrections.**
- Route feedback 8 to a new analyser tracker item.
- Add the three authoring scripts to the allowlist, or derive executables from a validated template
  command manifest. Keep refusing arbitrary archive modes.
- **Control:** remove `generate.sh` from the list, and require an install test asserting it is
  executable to fail.
- Keep the ZIP-writer check for direct browser downloads as a separate route.
- Also state that no Windows entry points exist for setup, validate or generate (only `.sh` is
  emitted). "Verify the supplied Windows entry points" (line 245) would find none, so the spec
  should either supply them or document their absence. This is decision D6.

### R3 — §E: the offer is explained by a path-only recovery key; reproduce that first

**Evidence.**
- `SessionResumeStore.key` is the profile's real path (`session/resume/SessionResumeStore.java:32-34`).
  Capture is skipped if `toRealPath` fails (`SessionRecoveryController.java:38`), so a saved key
  proves that path existed at capture time.
- The local store holds exactly one snapshot. It is keyed to the participant project's canonical
  profile path, `capturedAt` 2026-09-24T22:28:33Z. Its only input is another agent session's
  temporary fixture log.
- The participant project directory, and its profile, were created at 2026-09-25T07:19:23Z, about
  9 hours 50 minutes later.
- So a previous project existed at the same path, captured that log, and was replaced. The new
  project, at the identical path, inherits the old offer.
- `check()` compares only the input's hash, so the offer would also report the log as `unchanged`,
  which reads as endorsement.

The birth time comes from the file system. Deletion and re-extraction is the inference that fits
both facts, not an observed event.

**Corrections.**
- Replace "reproduce the capture/switch sequence" with a first fixture: capture under profile P at
  path X; delete the project; create a new profile at X; activate it. Today this fixture should show
  the stale offer.
- Bind snapshots to a profile identity, not just a path. For example, use a creation nonce written
  into the profile, or file key plus creation time.
- Disclose `capturedAt` and the capturing profile identity in the offer.
- Treat an identity mismatch as "captured by a different project at this path". The offer should be
  withheld or qualified, never silently offered.
- Keep the spec's existing cases (pending I/O switch, same display name, no-project) as further
  controls.
- **Mutation:** drop the identity comparison; the recreated-path assertion must fail.

This is independently shippable and needs no owner decision.

### R4 — §C: two source panes, and a model cache that survives a reread

The companion review's mechanism (same-FQN `navigate` skips `render`) is real
(`ui/SourcePanel.java:415-431`), but it is incomplete, and partly contradicted.

**What the main pane already does, and where the gaps are.**
- The main pane already rereads by content on configuration change and on processor inference after
  a log load: `showSelectedProcessor` → `rerenderIfChanged` (`SourcePanel.java:241-266`), called from
  `MainFrame.java:4591, 4649-4654`. That compares text, not modification time, so a
  same-size/same-mtime replacement is caught there.
- The **Topology tab embeds a second `SourcePanel`** (`ui/TopologyPanel.java:1479`), and the
  feedback names the Topology tab's pane. It calls `showSelectedProcessor` only when nothing is open
  (`TopologyPanel.java:1458`), and no reload path refreshes it. That matches "even after explicitly
  reopening the new log and GraphML".
- A reread re-parses the pane's own model, but `SourceService.selectedModel` is never invalidated by
  it (`source/SourceService.java:62-67`). So `fqnForInstance` keeps resolving node ids through the
  old model. After a rename it can send navigation to the removed class, and the pane then keeps
  that class's former text because its FQN is unchanged and its text is non-empty. That matches the
  second symptom, the `RootNode` heading.
- Both existing reread paths read and parse on the EDT (`Pane.render`, `SourcePanel.java:676-692`).

**Corrections.**
- Name both pane instances and the service-level model cache.
- Acceptance C: run in the embedded Topology pane as well as the Source tab. Include node-id →
  class navigation after a rename.
- Controls, one assertion each:
  - skip the embedded pane's refresh;
  - keep the stale service model;
  - reread on the EDT (the blocked-read test).

### R5 — §B and §G1: correct the `TypeSerialiser` description, and name the missing rejection surface

**`TypeSerialiser` at v1.0.44** (`library/lib-jsonserialiser/.../TypeSerialiser.java`):
- With a `type` key, it returns a **typed object**: `Class.forName`, then Jackson `readValue`
  (:55-57). The key is ignored because unknown properties are allowed (:24). It does not return "a
  map without that key" (spec line 284).
- Without `type`, it returns the raw `Map`, with all keys kept (:59).
- It returns null on JSON or class-lookup failure (:61-63).
- A non-string `type`, or a class-initialisation error, is not caught.
- Multi-line input yields a batch that may contain nulls (:39-44).
- The `@type`/`typeMap` documentation mismatch is confirmed (`docs/libraries/jsonserialiser.md:7,27-29,39-40`).
  The module's own `README.md` correctly documents `type` as a fully qualified class name.
- Input-selected `Class.forName` initialises arbitrary classpath classes, so the spec's caution
  against untrusted feeds is warranted.

**Core feed behaviour at v1.0.29** (`dispatch/EventToQueuePublisher.java`):
- A mapper returning **null** is dropped with a FINE log, before the publish counter (:89-94).
- A mapper **exception** is logged SEVERE and reported to a 100-entry error ring, then dropped
  (:197-210).
- There is **no rejection counter**.
- An identity-mapped raw String reaches `DataFlow.onEvent`.

**The template today.**
- Blank or short rows fabricate a zero event.
- Non-numeric rows throw, so core drops them silently apart from a log line.
- The unknown-event handler prints to stdout only; it is not an audit record.

So the bundle has two distinct defects, fabrication and silent loss. Only the first is in the spec's
framing.

**Corrections.**
- Fix line 284's description.
- Record the per-class current behaviour.
- State that "accounted for on a named observable surface" (line 107) needs either a typed rejection
  event that the processor handles and audits, or a core change (a rejection counter or report). The
  second is an upstream dependency, not a template edit. This is decision D1.
- Add "non-numeric row is silently dropped" as its own acceptance case, with a control.

### R6 — §I2: the registry can prove the process, not the project

**The registry record at v1.0.44** (`service/svc-admin-web/.../ServerRegistryFile.java:410-443`)
holds exactly these fields:
- `name`
- `home` (the JVM's `user.dir`)
- `url`
- `token` (the bearer token, or empty)
- `authMode`
- `environment`
- `pid`
- `startedAt`
- `processors[{group,name,className,graphml}]`

There is no descriptor path, instance id or project identity. The file name defaults to the basename
of the working directory, so two projects with the same folder name overwrite each other (:398-405).

**What the server exposes.**
- `GET /api/server` returns `pid@host` and the JVM start time (`MonitoringSampler.java:334-345`).
- `/healthz` is unauthenticated and returns only `OK`.

**Authentication.**
- It defaults to `NONE` on 127.0.0.1:8181.
- In `BEARER` mode the browser route is `POST /api/session/login`, which sets an HttpOnly cookie.
  No URL token is accepted.
- The analyser cannot log a browser in, so a bearer-mode offer can only open the login page.

**What can be established.** A registry entry can be tied to a live process by matching its `pid`
to `/api/server`'s pid, after a bounded probe of `url`. "This project's server" can only be
inferred: `home` equals the project root, and a `processors[].className` equals the profile's
selected processor. Both are defeated by launching from another directory or by a basename
collision.

**Corrections.**
- Replace "establish matching server identity using the supported server metadata" (line 433) with
  that two-level rule: process identity is verified; project association is inferred and disclosed
  as such.
- Or file an upstream ask for a project or descriptor identity in the registry, and hold the
  analyser affordance until it exists. This is decision D5.
- Add to Acceptance I2:
  - same-basename collision;
  - `home` ≠ project root;
  - pid reuse;
  - `NONE` versus `BEARER`;
  - a real-frame check that the offer is visible and then withdrawn.
- The browser-opener seam should assert that the opened URL is the registry `url`, never containing
  the token.

### R7 — §I3: the shared-interface plan conflicts with dispatch and with the vendor code as it is

**Dispatch.** The interpreted processor (builder source) dispatches on `event.getClass()` first.
It falls back to an assignable-type search only when no exact handler matched, and stops at the
most specific type. Whether generated AOT code does the same was not verified, because the
generator is hosted.

So if the demo keeps a `PriceEvent` handler, vendor handlers typed to a new market-event interface
never receive `PriceEvent`. The plan works only if every handler of that event is typed to the
interface. That is a demo code change, which conflicts with "XML only" in §I4 step 3; §I4 already
hedges this, but §I3 line 464 does not.

**Vendor code at `3a89391`.**
- `component-api` has three single-method interfaces with no units: `QuoteView{symbol, long size}`,
  `RiskInput{exposure, dailyVolatility}` and `NotificationService`. There is no price field.
- `LimitCheck` measures **quote size** against a per-symbol map, and throws on an unknown symbol or
  a negative value (`LimitCheck.java:18-23`). On a live feed, core retries and then drops that event,
  with an error report.
- `CsvFeedAdapter(Consumer<QuoteView>)` throws on blank, header, short or non-numeric lines
  (`CsvFeedAdapter.java:7-13`).
- `Notifier` is an `@ExportService` with no subscription. Nothing bridges a `LimitBreach` to it.
- There is no Maven build: `tools/build.py` makes deterministic jars and POMs with `.sha256`
  sidecars, but no source jars.
- The generated POMs put `fluxtion-runtime` at compile scope.
- `catalogue.json`'s `source` links point at `tree/main/…`, but the repository's only branch is
  `feat/spring-side-work-block`, so every source link is dead today.
- Risk A and risk B are separate artifacts sharing one FQN. Their oracle (`checks/check_risk.py`,
  `fixtures/risk.csv`, `tools/verify.py:27-54`: A has 5 mismatches, B has 0) is independent and
  should be kept as is.

**Corrections.**
- Replace "handlers typed to that interface" with an explicit choice (decision D7):
  - (a) retype the demo's handlers to the shared interface and declare it a demo change; or
  - (b) keep concrete types, and have the vendor mapper emit the demo's event.
- Define the new contract's fields with units (price, traded volume), separately from
  `QuoteView.size`.
- Require unknown-symbol and header/blank handling that neither throws nor fabricates.
- Add a notifier bridge, or state that it is out of scope.
- Fix the dead source links.
- Add a Maven build or a source-jar step before "include sources in the local Maven layout" (line 481).
- Treat the A/B shared FQN as a load-one-at-a-time rule, with a test that loading both is refused or
  detected.

## Further required corrections (smaller)

- **R8 — §G3 provider boundary.** In the plugins' pinned builder, the Spring loader's compile branch
  falls back to the hosted generator when no local source generator is registered. The public
  builder jar registers none, and the hosted path requires a key. The interpreter branch is local.
  State this as the inspected expectation, still to be observed.

  Separately, `SpringEventHandlerLoader.java:69-70` wires the two reload commands the wrong way
  round (interpret ↔ compile). Add it to the upstream asks.
- **R9 — §G4 facts that can now be established.**
  - The plugin site's "latest" comes from `mkdocs.yml:91` `plugin_version: 1.0.37`, unchanged at the
    v1.0.44 tag.
  - mongoose-plugins artifacts are not on Maven Central; they deploy to Repsy, whose latest release
    is 1.0.45 (2026-09-24).
  - The G4 and I3 publication checks must name that repository as the public binary route.
- **R10 — §G2 needs a fifth capability.** The admin console's "Replay" view is visual playback of
  captured audit records, with GraphML lighting (`svc-admin-web/.../index.html:805`,
  `replay/replay-engine.js:2-4`). It is not re-execution. The template's descriptor comment,
  "deterministic replay via the admin console", describes this and is false as a re-execution claim.

  Add "visual playback of captured records" to the capability table, so the console's label is not
  read as input replay.
- **R11 — §D names the cross-class keys.** Ownership member keys embed parameter-type FQNs
  (`name(fqnParamTypes)`), and whole types are keyed `type:<FQN>`. Renaming a class therefore
  changes keys in other classes; for example, the constructor key of a class that takes the renamed
  type.

  "Affected owned members" (line 149) must explicitly include those foreign signature keys. It must
  also state which state a migrated construct gets (`owned` or `adopted`), since today `adopted` is
  assigned only automatically.
- **R12 — Tracker and upstream routing.**
  - The delivery table (line 50) and the upstream table ("Delivered scripts…") route feedback 8
    wholly to the producer. Add an analyser tracker line for the installer allowlist (R2) and the
    recovery identity key (R3).
  - Retitle the tracker's "Recovery provenance investigation" once R3's fixture confirms it.

## Owner decisions (not routine implementation choices)

| # | Decision | Section |
|---|---|---|
| D1 | Rejection surface: a typed rejection event audited by the processor, or a core rejection counter/report (upstream). Also the blank-line policy. | §B, R5 |
| D2 | The rename/ownership migration contract, including cross-class key migration and the resulting ownership state. | §D, R11 |
| D3 | Role-scoped project grants, or keep per-root authorisation. | §E |
| D4 | Canonical-id policy for equality-shared nodes. The inspected builder keeps the first instance and gives it the later bean id. | §F |
| D5 | Accept an inferred project association for the console offer (disclosed), or wait for an upstream registry identity field. | §I2, R6 |
| D6 | Supply Windows entry points for setup, validate and generate, or document their absence. | §G, R2 |
| D7 | The demo's event contract: retype its handlers to a shared interface, or keep concrete types and have a vendor mapper emit them. | §I3, R7 |
| D8 | Document `TypeSerialiser` as it is, or change the plugin to match its documentation (a versioned discriminator change). | §G1 |

Routine choices the implementer can make: exact staging mechanics for the scan write, and the form of
the profile identity nonce.

## Acceptance quality

- **Checks that need a published artifact or an authorised provider run:**
  - the §A public-download acceptance;
  - the §G published-archive extraction;
  - the §G3 compile route (hosted provider, per R8);
  - the §G4 built site;
  - the §I3 publication check;
  - the §H feedback-13 released-version run.

  The spec already says no key use is authorised. It should also label these as not runnable on a
  branch fixture.
- **Controls that would fail in the wrong place:**
  - §G's ZIP-mode control, per R2.
  - The §C controls, unless they are made per-pane (R4).
  - §B's "named observable surface", while no such surface exists (R5).
- **Missing negative cases:**
  - §E: a project recreated at the same path (R3).
  - §I2: a basename collision, and `home` ≠ project (R6).
  - §I3: an unknown symbol, a header line, and loading both risk jars (R7).
  - §B: a silently dropped non-numeric row (R5).
  - §I1: a profile whose declared design root escapes the project after `realpath`. The emitted
    design root becomes a read grant through `DesignFiles(effectiveSourceRoots())`
    (`ui/MainFrame.java:1032-1035`), so it must resolve inside the project.
- **Missing real-display acceptance:**
  - §I2 names no real-frame check for the offer's visibility and withdrawal.
  - §C and §I1 already require one.

## Confirmed as written

These statements were checked against source and hold:

- **§A:** The scan runs at `process-classes`, after the ordinary compile, and it needs compiled
  classes.
- **§B:** Short rows fabricate zero-valued events.
- **§D:**
  - `link` is a browser URL; it deflates the XML, refuses it over 32 KiB, and writes no receipt.
  - `--help` is handled only as the first argument.
  - `--no-reconcile` skips new-type shells.
  - The bean id is only one of several sources for an owned field name.
- **§F:**
  - `validate` parses XML only.
  - FLX-1001 and FLX-1005 are raised by the local builder while it builds the model, so a local
    preflight is plausible. That is unverified.
  - Receipts record the stage and route, but no request count or cost.
- **§G:**
  - The truncated "For graph changes, " sentence is real.
  - The hosting guide copies the descriptor and calls it effective.
  - The replay comment is present.
- **§H:** `ChronicleAuditCaptureService` replaces the processor's listener and installs a no-op on
  stop (core v1.0.29 `internal/ChronicleAuditCaptureService.java:232-242`), consistent with MA-5.
- **§I1:**
  - The template profile has only `src/main/java`; the design path is computed but not written.
  - The profile schema has no design key, so "do not invent an ignored key" is right.
  - GraphML is machine-tier state.
- **Harness:** `MongooseTestHarness.wrap` plus `MongooseServer.bootServer(Reader)` can host the real
  descriptor (§G starter test).
- **Existing items:**
  - UP-FLX-21, MA-2, MA-5, OD-5, M68.5, M67.1–M67.6, D-T8, D-T9, D-X8, D-X9 and the record-order
    item all exist. The branch extends them without changing their status marks.
  - All 24 feedback items and the three owner additions are routed.

## Slices that may start independently

1. **Analyser installer allowlist** (R2). A one-line fix with a control.
2. **Analyser recovery identity** (R3). Same-path fixture first, then identity binding and disclosure.
3. **Analyser source freshness** (§C with R4). Both panes, the service model, off-EDT reads.
4. **Analyser context projection** (§H feedback 17).
5. **Template compile profile** (§A with R1). Exclude the generated package in the profile, with the
   constructor and rename fixtures. Staging of the scan write can follow.
6. **Template text fixes** (§G). The truncated sentence, linking the descriptor instead of copying
   it, and the replay comment (with R10). Starter CLI per-command help.
7. **Template mapper** (§B). Remove the fabricated zero now. The rejection surface waits for D1.
8. **Template profile design root** (§I1), plus the no-log real-frame journey.

Blocked on decisions or redesign: §D (D2), §I2 (D5), the §I3 shared contract (D7), §G1's
recommendation (D8), and §F's preflight until slice 5 lands.

## Verification of this review

- **RUN:** read-only source inspection at the commits named above.
  - Plugin, core and vendor clones are local scratch copies.
  - The vendor build was run offline in scratch. The regenerated `catalogue.json` was byte-identical
    to the committed one, so its digests reproduce. Nothing was installed.
- **RUN:** the rule-one public-data sweep on tracked files and on this file, and `git diff --check`.
  Both are clean.
- **NOT RUN:** no Maven test suite. No test would answer these questions; the conclusions rest on
  source reads, stated as such. No mutation gate, display test, provider call, console probe,
  archive download, or change to the participant workspace.
