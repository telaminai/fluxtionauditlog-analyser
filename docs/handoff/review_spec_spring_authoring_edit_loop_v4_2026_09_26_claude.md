# Re-review: Spring authoring edit-loop specification, PROPOSED v4

**Subject:** `449265ec` on `docs/spring-session-friction-2026-09-25`. The diff reviewed is `63065b91..449265ec`
across four files:
- the spec;
- the companion feedback review;
- `docs/specs/tracker.md`;
- `docs/proposals/upstream-asks.md`.

**Previous review:** `075107ef`, `docs/handoff/review_spec_spring_authoring_edit_loop_2026_09_25_claude.md`.

This review is documentation-only and proportionate. I did not implement anything or edit the
subject documents. I did not access the participant project or its recovery store, and I used no
client session, key, merge or release.

## Verdict: changes required, three small text corrections

All twelve findings (R1–R12) are resolved **in the governing text**, not merely acknowledged in the
response section. I checked each resolution against source.

Three new, small problems remain:
- **C1:** the §I1 design-root escape case (which my first review proposed) cannot be implemented as
  worded.
- **C2:** one of the eight "independent" slices quietly depends on D1.
- **C3:** D7 option (b) leaves the adapter's home ambiguous, in a way that contradicts the
  no-demo-classes rule.

Each is a sentence or two to fix. Slices 1–6 may start now; C1–C3 affect only slices 7 and 8 and
the D7 wording.

## R1–R12 disposition

**Evidence tags:**
- **READ:** source inspected, by me for this re-review unless marked *(prior)*. *(prior)* means
  verified during the `075107ef` review at the same public or private revisions.
- **RUN:** executed here.

Line numbers refer to `docs/specs/spec-spring-authoring-edit-loop.md` at `449265ec` unless another
file is named.

| # | Resolved? | Where in the governing text | Evidence and notes |
|---|---|---|---|
| R1 | **Yes** | §A lines 71–92; controls lines 104–108 | The supplier is reflective, not an import (READ *(prior)*, private template; behaviour only). The four-step pipeline, the profile-only exclusion and the preserved default keyless compile (line 80) are stated. The scan's direct `src/` write is named as the output to stage (lines 84–88); reconciliation rollback is correctly not credited to the scan. The added controls (disabled staging, and profile leak into the default build) are good. The upstream-asks UP-FLX-21 paragraph matches. |
| R2 | **Yes** | §G lines 309–323; delivery table line 57; tracker "Template installer authoring executables"; upstream table row "Direct-browser ZIP scripts…" | READ: `TemplateArchive.java:34-35, 213-230` (fixed list, archive modes ignored) and `TemplateArchiveTest:44-49`. The control is the installer list, not ZIP metadata (line 314). Arbitrary modes stay refused (line 312). The browser ZIP route is separate (lines 316–320), and Windows absence is D6 (lines 321–323). |
| R3 | **Yes** | §E lines 228–254; tracker "Recovery profile identity" | READ: `SessionResumeStore.java:32-34` (real-path key), `check()` (input hash only). The first fixture recreates a profile at the same path (lines 237–238). Identity binding, `capturedAt`, mismatch withholding and the identity mutation are explicit (lines 243–247), and legacy snapshots with no identity never match (line 241). Lines 233–235 correctly present the participant history as inferred, not reproduced, and the tracker says the same. |
| R4 | **Yes** | §C lines 150–161; Acceptance C lines 177–186; tracker "Java snapshot freshness" | READ: both `SourcePanel` instances (`MainFrame.java:74`, `TopologyPanel.java:1479`); the first-use gate (`TopologyPanel.java:1458`); the content reread (`SourcePanel.java:241-266`). `setProcessors` resets only the combo box (`SourcePanel.java:220-228`). `SourceService.selectedModel` survives a pane reread (`SourceService.java:62-67`), and reads happen on the EDT. "Catch even a same-size/same-mtime replacement" (line 153) is accurate, because the comparison is by content. Three independent controls are paired with their own assertions (lines 183–186). |
| R5 | **Yes** | §B lines 120–146; §G1 lines 359–378; upstream row "No fabrication or unaccounted…" | READ *(prior)*, mongoose-plugins v1.0.44 and mongoose v1.0.29. The `TypeSerialiser` wording is now correct: a typed object when `type` is present; the raw map with keys intact when it is absent; null on parse or class-lookup failure; non-string types and initialisation errors uncaught; batches that may contain nulls. The core null path (FINE log, never reaching the error ring) and the exception path (SEVERE log plus the 100-entry ring) are distinguished (lines 125–128). Fabrication and non-numeric loss get separate assertions and controls (lines 142–145), and full accounting is gated on D1 (lines 131–135). |
| R6 | **Yes** | §I2 lines 534–581; tracker; upstream "Conditional D5 ask" | READ: `WebAdminService.java:238-242` sets `registryStartedAt` once, at admin-service start, truncated to seconds, before the bind. Refreshes (`:705-711`, `ServerRegistryFile.publish`) do not change it. So a live JVM with the registry pid whose start time is later than `startedAt` + 1 s is a different incarnation: the pid was reused. The spec does not equate the two times, and it allows the documented precision (lines 551–556), so the check is implementable without overstating identity. Process verification is separated from inferred association under D5 (lines 543–549). All the requested cases are present (lines 571–581): collision, differing `home`, missing processor, NONE/BEARER, login-only offer, real-frame visible-then-withdrawn, and a token-free URL. |
| R7 | **Yes, with C3** | §I3 lines 586–658 | READ *(prior)*: vendor `3a89391`. The interpreted dispatch was read in the private builder source; behaviour only. Exact-type-first dispatch with interface fallback is stated, AOT parity stays unverified, and a real parity run is labelled not branch-runnable (lines 593–598). D7 does not choose a contract (lines 602–613). Every item is covered: fields and units (lines 607–611), `LimitCheck` quote size and unknown-symbol throw (lines 622–627), CSV throws (line 617), no notifier bridge (lines 629–631), dead `tree/main` links (lines 587–590), no Maven or source-jar build and the compile-scope runtime (lines 633–636), and the shared-FQN guard with its mutation (lines 642–644). Risk A, its oracle and its fixtures are preserved (lines 639–641). |
| R8 | **Yes** | §G3 lines 419–424; upstream "V4 corrections" | Hosted compile versus local interpretation is labelled "source evidence, not an observed provider run". The swapped reload commands have a named owner (the Mongoose Spring-loader plugin) and a swap mutation. |
| R9 | **Yes** | §G4 lines 438–453 | `mkdocs.yml` `plugin_version: 1.0.37` is distinguished from the 1.0.44 pin and the Repsy release 1.0.45. The Central path `com/telamin/mongoose-plugins` is the right coordinate for the plugins' groupId (`com.telamin`, pom.xml:10, READ). Line 444 avoids overgeneralising from that single 404. |
| R10 | **Yes** | §G2 lines 385–391 | The fifth capability is added. The console's Replay is visual playback, and the template comment is named false as a re-execution claim. |
| R11 | **Yes** | §D lines 198–206; Acceptance D lines 216–224; D2 row | Parameter-FQN keys in other classes and `type:<FQN>` are named. The resulting `owned`/`adopted` state is left to D2, with the preview disclosure required. |
| R12 | **Yes** | tracker lines added under the 2026-09-25 intake; upstream tables | Installer and recovery identity have analyser entries. Feedback 8 is split between the installer and the browser ZIP route. RUN: `git diff 63065b91..449265ec -- docs/specs/tracker.md` advances no status mark: one ☐ item is retitled ("Recovery provenance investigation" → "Recovery profile identity") and stays ☐; the other changes are new ☐ lines and prose. |

## Required corrections

### C1 — §I1 escape case: the analyser cannot tell a design root apart, and profiles legitimately reach outside

**Where:** §I1 lines 527–530. The line reads: "a declared design root resolves outside the project
after `realpath` … Reject it before it becomes an effective source-root read grant." I proposed this
case in `075107ef`; it was underspecified there.

**Why it cannot be implemented as worded:**
- The template profile has no typed design key; this spec correctly forbids inventing one (line 511).
- So the design directory is just another `sourceRoot` entry.
- Profiles legitimately declare roots outside the project, such as monorepo neighbours written as
  `../shared-lib/...`, which `config/PathForm.java:13,34` supports.

**Failure scenario.** An implementer rejects every profile root that escapes the project at load
time, breaking the supported monorepo form. Or they cannot find "the design root" to reject, and
the negative case silently tests nothing.

**Fix.** Place the check where the root is known to be template-supplied: in `TemplateArchive`
installation (READ: it installs the archive's `.analyser/project.fluxtion-settings` at lines 78–91,
without validating its roots). Require every root in an **installed template's** profile to resolve
inside the installed project after `realpath`, including through symlinks, or refuse the install.
State that user-authored external roots remain legitimate and fall under §E's grant policy.

The control is to remove the install-time check; a constructed archive with a `../` or symlinked
root must then fail its install assertion.

### C2 — Slice 7 depends on D1's blank-line policy

**Where:** slice 7 (line 725) and §B lines 131–135. The spec says fabrication removal "can start
independently", but D1 explicitly includes "blank-line policy" (line 131, and the D1 row at
line 705).

**The hidden choice.** Removing the zero fallback forces a choice for blank and short rows, and
every option either decides D1 or causes silent loss:
- return the raw line: stdout only, unaccounted;
- return null: dropped silently at FINE;
- throw: dropped with a SEVERE log.

**Fix.** Either:
- name the interim behaviour explicitly for slice 7: for example, blank and short rows follow the
  existing non-numeric route (logged, unaccounted), disclosed as **interim and not D1's surface**,
  with that interim behaviour named in the release note and template docs; or
- move slice 7 behind D1.

Either way, the slice must not quietly choose the blank-line policy.

### C3 — D7 option (b): say where the adapter lives

**Where:** lines 602–606. Option (b) has "the vendor mapper emit the demo's event through an
integration adapter/factory". The same bullet forbids "casts to demo classes inside a supposedly
reusable vendor component".

**The contradiction.** A reusable vendor mapper cannot construct the demo's `PriceEvent` without a
compile dependency on the demo.

**Fix.** State that under (b), the adapter or factory lives in the application, or in a separate
integration module that depends on both. The vendor jar keeps emitting its own type.

**Failure scenario.** An implementer puts the conversion in the vendor jar, reintroducing a
demo-class dependency into the collection.

## Optional improvements

- **O1, §I2, bearer mode:** say whether the analyser reads the registry's bearer token, from the
  user's own mode-600 file, for the `/api/server` probe. `/api/*` is gated in BEARER mode. Line 566
  ("any authorised probe credentials stay in request headers") implies yes, but doesn't say it. The
  alternative is to treat bearer servers as "authentication required" without probing.
- **O2, §I2, host check:** the pid comes back as `pid@host`. Assert that the host part is local
  before accepting a pid match.
- **O3, companion review:** "registry publication" describes `startedAt` loosely. "Admin-service
  start, truncated to whole seconds, fixed across refreshes" is the precise meaning, and it is what
  makes the 1 s tolerance correct.

## Owner decisions

D1–D8 (lines 701–714) remain genuine, unresolved owner decisions, and none is quietly chosen. The one
exception is C2's hidden D1 dependency.

## Other checks requested

- **Startable slices:**
  - 1–6 and 8 do not depend on unresolved decisions. For 8, apply C1's placement.
  - Slice 5's closure is correctly tied to the protected-output failure tests.
  - Slice 7 needs C2.
- **Published and provider acceptance:** separated from branch fixtures in every section (§A:101,
  §B:145, §G:318–320, §G1:372, §G3:432, §G4:451–453, §H:475, §I1:529, §I2:580, §I3:597/654,
  §I4:678). Local checks remain runnable, so nothing local is blocked unnecessarily.
- **Read-authorisation boundary:** preserved in §E and §I1, subject to C1.
- **Cross-document contradictions:** none found beyond C3. The tracker, upstream asks and companion
  review agree with the spec on R1–R12.
- **Private compiler and web behaviour:** described without locations or excerpts.
  - RUN: grep of the added lines for private file and class names (the template emitter's TypeScript
    files, the starter's reconciler and workflow classes, the builder's generator classes): **0 hits**.
  - RUN: rule-one terms and local paths in the added lines: **0 hits**.

## Evidence limits

- **RUN:** `mvn -o -Dtest=SpecLinksResolveTest test` at `449265ec` (JDK 21): **3 total / 0 failures /
  0 errors / 0 skips**, which matches the author's report. This is a documentation check, not product
  evidence.
- **Not run:**
  - `mkdocs build --strict`: the edited files are outside the published site;
  - the full suite, mutations, and display or product checks;
  - any proposed acceptance.
- **Not re-checked since `075107ef`:** the private starter/template facts. Their v4 wording matches
  what was verified then. The vendor and plugins facts were likewise reused from `075107ef`, except
  the `startedAt` assignment and the groupId, which I re-read here.
- **Still unverified**, as the spec itself states:
  - the participant's recreation history;
  - AOT dispatch parity;
  - the proposed pipeline, installer, recovery, pane and console behaviours in operation;
  - real provider prerequisites;
  - published-artifact acceptance.

## Addendum, 2026-09-26: decisions commit `13c44071`

Checked `449265ec..13c44071` (the same four files). This is a documentation-only check: the
decision text and the C1–C3 status, with no source re-inspection.

**D1–D8 are recorded consistently.** The decision table in the spec's final section lists all
eight as owner-approved on 2026-09-26, and states that approval is not delivery. A search of the
spec, tracker, upstream asks and companion review found no remaining phrasing that presents a
decision as open. The patterns searched were "D5 chooses", "past D5", "upstream core rejection
counter" as a dependency, and "owner chooses". The slice list now names only technical and
evidential dependencies, and D6 defers Windows entry points explicitly (§G, lines 331–334).

**Status of this review's corrections:**
- **C2, resolved.** D1 rejects blank rows through the audited typed rejection event. Slice 7 now
  requires that path before claiming full rejection accounting.
- **C3, no longer applies.** D7 chose option (a): demo handlers are retyped to the shared
  interface, and vendor mappers emit it (§I3, lines 616–627). No adapter needs placing, and "no
  casts to demo classes" stays consistent.
- **C1, still open.** The §I1 negative case at lines 540–542 is unchanged. The analyser cannot tell
  which `sourceRoot` is the design root, and profiles legitimately declare roots outside the
  project (`config/PathForm.java:13,34`). Move the check to `TemplateArchive` installation:
  - every root in an installed template's profile must resolve inside the installed project after
    `realpath`, including through symlinks, or the install is refused;
  - roots in user-written profiles are left to §E and D3;
  - **control:** remove the install-time check, and a constructed archive with a `../` or
    symlinked root must fail its install assertion.

**Verdict: ready for implementation once C1 is corrected.** Slices 1–7 may start now. Slice 8's
negative case needs C1's placement first.

The added lines are clean of public-data terms and local paths (RUN).
