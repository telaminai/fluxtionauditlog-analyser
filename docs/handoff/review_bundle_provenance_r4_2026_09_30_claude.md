# Review R4: `feat/bundle-provenance` at `f5dfc9c8` (2026-09-30, Claude)

Adversarial review, read-only, on a detached worktree at `f5dfc9c8` (origin/main..f5dfc9c8, 12 commits). Maven was
run serially. Every scratch reproduction below was run in Java, then removed; none is committed.

**Verdict: not clean.** A session can still claim to be evidence when it is not: finding 1 comes from the one rule this
round relies on, and finding 3 is older than the branch. No case was found of a session failing to claim when it IS
evidence, but the merge deleted the only test pinning that direction (finding 4).

## Findings, most severe first

### 1. A stale verified plan labels an ordinary open of the bundle's own working-copy profile

CONFIRMED in the session processor; PLAUSIBLE in the product.

**Where:** `session/node/OpenBundle.java:58-59` (the plan is held) and `:74-78` (the match).

**Cause:** a plan is dropped only by a failed `ProfileLoaded`, by `ProfileApplied`, or by `SettingsRestored`.
`OpenBundle` has no `EffectFailed` handler. The javadoc calls a leftover plan harmless because "the next profile to
apply is somebody else's". That is false when the next profile is the same working copy.

**Sequence:**
1. Open a bundle. Verification is accepted and the plan is held.
2. The transition dies before `ProfileApplied`, by either route:
   - the working-copy `LoadProfileEffect` throws (an unchecked exception from `preSave` in `flush`, or from the
     sender-authored profile), which becomes `EffectFailed`;
   - the apply hits a `ProtocolViolation`. Here `project.open` has already succeeded and called `addRecent`, so the
     working copy is now in **Recent projects**.
3. Open that same profile by an ordinary route (`EXPLICIT_SWITCH`): the open dialog, or the recent entry.
4. The session publishes the full `BundleProvenance`: identity, source `.fexp`, working copy and limits. That open
   verified nothing.

**Why the existing tests miss it:** `abandonedBundleDoesNotLeakIntoTheNextProject` does not hold a plan at all. Its
plan arrives after the next request, so the gate refuses it. `anAbortedBundleNeverLabelsTheNextProject` holds one, but
opens a *different* profile afterwards.

**Reproduction** (in `...analyser.session`; both runs fail at the last assertion, every precondition passes):

```java
static SessionEvents.BundlePlan plan() {
    return new SessionEvents.BundlePlan("/bundle/project.fluxtion-settings", "/bundle/graph.graphml",
            "/bundle/log.yaml", "sha256:DEMO-identity", "/bundle", "DEMO limits", "", "/demo/evidence.fexp");
}

@Test void aStalePlanLabelsAnOrdinaryOpenOfTheWorkingCopy() {
    var adapter = new FakeSessionAdapter().withProfile(plan().profilePath());
    var driver = new SessionDriver(adapter);
    long id = driver.nextOpId();
    driver.submit(new SessionEvents.OpenProjectRequested(id, "/demo/evidence.fexp", TransitionKind.OPEN_BUNDLE, "start"));
    adapter.loadThrows = true;   // or: adapter.applyViolates = true, wrapped in assertThrows(ProtocolViolation)
    driver.submit(new SessionEvents.ProfileLoaded(id, plan().profilePath(), true, null, 0, null, plan()));
    assertFalse(driver.snapshot().bundle().fromBundle());   // precondition holds
    driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(), plan().profilePath(),
            TransitionKind.EXPLICIT_SWITCH, "open-dialog"));
    assertFalse(driver.snapshot().bundle().fromBundle());   // FAILS: BundleProvenance[identity=sha256:DEMO-identity, ...]
}
```

**Suggested fix:** bind the plan to its operation. Keep the plan's opId, and require `ProfileApplied.opId` to match as
well as the path, or drop the plan when its operation ends by any route. The match on path alone is necessary but not
sufficient. Add both routes above as regressions and a control that removes the opId check.

### 2. Redaction regressed outside U+00C0–U+024F, and the Latin range was only half applied

CONFIRMED, in Java.

**Where:** `bundle/BundleProfile.java:98` (`WHOLE_PATH`), `:102-108` (`EMBEDDED_PATH`), javadoc `:51` and `:64`.

**Method:** the export loop's own find, trailing-dot trim and replace, run over the patterns of three versions:
`origin/main`, the branch's previous version `ec768a6f` (the blanket `UNICODE_CHARACTER_CLASS`), and head.

| Input | Main and head | `ec768a6f` |
|---|---|---|
| `seen in /home/de\u0301mo/logs/x.yaml` (NFD, as macOS often stores names) | `‹path removed›́mo/logs/x.yaml` | redacted whole |
| `seen in /home/nguyễn/logs/x.yaml` (Latin Extended Additional) | `‹path removed›ễn/logs/x.yaml` | redacted whole |
| `seen in /home/дмитрий/logs/x.yaml` | `/home/дмитрий‹path removed›`: username kept, redaction reported | redacted whole |
| `seen in /Users/王/logs/x.yaml` | `/Users/王‹path removed›` | redacted whole |
| `seen in /home/дмитрий` | unchanged, nothing reported | redacted |
| `seen in C:\Users\josé\logs\x.yaml` | `‹path removed›é\logs\x.yaml` | redacted whole |
| `seen in \\srv\josé\share` | `‹path removed›é\share` | redacted whole |
| whole value `~дмитрий/x.yaml` | NOT refused, exported | refused |
| whole value `~de\u0301mo/x.yaml` (NFD) | NOT refused, exported | refused |
| whole value `~nguyễn/x` | NOT refused, exported | refused |

- **Half applied:** the range was added to the POSIX, tilde and `file:` alternatives. The drive and UNC alternatives
  (`:107-108`) still use `[\w.$-]`, which is the R3 pattern again.
- **Reported redactions that still carry the path:** these are the case the javadoc itself calls worse than none.
- **Stale javadoc:** `:64` still says "Both patterns are Unicode-aware, and must stay that way", which the code no
  longer is. `:51` presents Cyrillic and CJK as prose, but a Cyrillic or CJK *username* is left in place while a
  redaction is reported.
- **What head fixed:** no prose was eaten in these probes. `café/menu/items`, `Été/Hiver/Printemps` and `×/sec/min`
  now survive, where main or `ec768a6f` ate them.
- **Leaking in all three versions:** `log:/Users/demo/logs/x.yaml` and `位置:/Users/demo/logs/x.yaml` (an ASCII colon
  directly before the path) export whole with nothing reported. The lookbehind excludes `:` to protect URLs.

**Suggested direction:** keep the lookbehinds narrow, which fixes R3's adjacency case, but let the *segment* classes
accept any letter or mark (`\p{L}\p{M}`) while stopping at the prose that follows. The table shows that treating
non-Latin letters as prose leaks the part of the path they belong to. Apply the same classes to the drive, UNC and
`WHOLE_PATH` tilde alternatives. Normalise to NFC before matching, and redact against the original. Pin every row
above in both directions.

### 3. A third arm with the same shape: `CreateProfileEffect`

PLAUSIBLE in the product, CONFIRMED at component level. Older than the branch (M20.2, 2026-08-17), but it defeats the
branch's claim.

**Where:** `config/ProjectSession.java:220-225`, `ui/MainFrame.java:6672`.

**Cause:** `create()` runs `clearProjectScoped(config)` before `ProjectProfile.save`. When the save throws (a read-only
directory, or a parent that is a file):
- the settings are already wiped, and the old project stays active;
- the session receives `EffectFailed`, so it keeps naming the old project and, with a bundle in force, keeps claiming
  the bundle;
- the next `requestSave` / `flush` writes the blank settings into the old project's file: the bundle's working-copy
  profile, or a person's own committed project.

**Reproduction:**
- Real `ProjectSession`: open A (`sourceRoots=[/bundle/src]`), then `create(<file-as-parent>/new.fluxtion-settings)`
  throws. `activeFile()` is still A and `sourceRoots` is `[]`. After `requestSave(); flush()`, A on disk has `[]`.
- Processor: with a bundle in force, a `CREATE` whose effect throws leaves `fromBundle() == true`.

All three fail at the named assertion.

**Checked, not reproduced:** `ProjectProfile.load` also clears before `share.apply`, but a malformed `\u` escape is
rejected in `preview`, before the clear. No input was found that fails after it.

**Suggested fix:** in `create`, build and save the new profile before touching `config`, or restore `config` on
failure. Pin it, with a control.

### 4. The merge deleted the only test for "keeps the claim when it should"

CONFIRMED (coverage).

**What was lost:** `BundleProvenanceTest#aFailedCloseLeavesTheBundleInForce`, together with
`FakeSessionAdapter.restoreThrows`.

**Why the reason given is only half right:** the merge message says the test "asserted the WRONG behaviour", because
its fake threw before marking the project closed. That is right for a *render* failure. But `ProjectSession.close()`
itself can throw before clearing anything (`flush` → `preSave` → `syncOpenGraphsIntoConfig`). The project is then
still in force, and keeping the claim is correct. That is what the deleted test pinned.

**Head today:** still correct. A scratch adapter that throws on `RestoreSettingsEffect` keeps `fromBundle() == true`,
and the test passes. Nothing pins it any more.

**Suggested fix:** restore it beside `aCloseWhoseRenderFailsStillEndsTheClaim`: the real half throws and the claim
stays; the render throws and the claim ends.

### 5. Nit: the render helper's javadoc overstates its premise

**Where:** `ui/MainFrame.java:6860`.

It says the settings are "swapped or closed ... before either effect arm runs". For the restore arm, `project.close()`
runs *inside* the arm (`:6718`) and the helper follows it. The order still makes the reported fact true; the sentence
should say so.

## Verified clean

- **Merge unions** (the side-added lines of every file changed on both sides, checked against head):
  - `SessionSnapshot` carries both `assistant` and `bundle`, each filled from its own node.
  - `ContextSections` has both `"assistant"` and `"bundles"`.
  - `onSessionSnapshot` dropped nothing from either side.
  - `ci.yml` frame list: 33 + 1 + 6 = 40, the same in both lists.
  - CHANGELOG: `[1.29.0]` is identical to main's; all 7 branch lines are under `[Unreleased]`, none misfiled.
  - `tools/mutation_controls_session.py`: the anchors that changed are the redesigned patterns.
- **Generated artefacts:** both `SessionProcessor.java` copies are byte-identical, and the graphml
  `sourceFingerprint` equals the one in the source. `openBundle` (3 handlers) and `assistantLoop` (29) are both
  dispatched. No test from either side is missing apart from the one in finding 4.
- **`MainFrame.beforeProjectRender`:** its only product value is the no-op, and nothing in `src/main` or `tools/`
  assigns it. It is package-private. Both tests that set it reset it in `finally`, and no JUnit parallel execution is
  configured.

## Runs

- `mvn -o test`: **2974 tests, 0 failures, 0 errors, 205 skipped**, 401 reports.
- CI frame list with `-Djava.awt.headless=false`: **203 tests, 0 failures, 0 errors, 8 skipped**, 40 reports.
  - The 8 skips are native-input and focus tests that skip on the review machine.
  - All six `BundleProvenanceFrameTest` tests ran and passed.
- `verify_project_chart_review.py --mode preflight`: 40 frame suites, **542 anchors**, exit 0.
- The full mutation gate was not run.

## The question

**Can a session still claim to be evidence when it is not?** Yes: finding 1, and finding 3 with a bundle in force.
**Can it fail to claim when it is?** Not found at head, but untested since the merge (finding 4).
