# Review — chart style and external-path fixes, 2026-09-24

**Subject:** `90746e83b05326e2c5d86ea69f6fd72709d3e368`, parent `1e51545d`.
**Verdict:** both local fixes work. Fix 1 also passes the previously missing real-display check.
Fix 2 needs an integration regression test before its finding can be called closed. A separate,
pre-existing import-refresh defect still discards imported chart state; this commit does not establish
end-to-end import correctness or approval to release the wider Close/Delete work.

**Independence:** I reported the earlier dropdown and external-path defects in the review at `abcea7f9`.
This is a re-review by that reviewer, not a third independent opinion. I did not author `90746e83`.
The results below come from new executions against that exact commit, not agreement with its report.
No product source, tracker, spec or earlier evidence was changed. Temporary mutations were restored
byte-for-byte. The primary checkout and the separate chart-lifecycle implementation worktree were left alone.

## Required corrections

### R1 — Medium: the live import adapter overwrites the imported chart before rebuilding it (pre-existing)

**Location:** `MainFrame.java:4718`, through `onConfigChanged:4606` → `saveConfigQuietly:6833` →
`syncOpenGraphsIntoConfig:4707`. **Reproduced**, plus parent-source inspection confirming the same ordering
at `1e51545d`; not introduced by this commit.

Concrete input: an open chart named `Beta chart`, style Line. Import a graph definition of the same name,
style Points, `open=false`. `SettingsShare.preview/apply` correctly installs Points/closed. The actual
`applyImportedConfig` refresh then calls persistence **before** restoring the imported graph. Persistence
snapshots the old tab, whose Line/open state wins over the incoming definition. The chart stays Line/open.
The same ordering can discard other imported definition fields.

Observed output:

```text
PASS import plan applies Points/closed before UI refresh
OBSERVATION after actual import refresh: style=line, open=true
```

The display probe drives the real MainFrame post-dialog merge seam, including the same preview/apply and
private refresh method. It does **not** automate the import chooser/consent dialogs; this is an adapter
integration reproduction, not a claim to have clicked the entire import workflow.

**Required for the import claim:** apply the accepted definition to the views before persistence can
snapshot the old views, or otherwise protect the incoming snapshot during refresh. Add a production-adapter
regression with an existing open chart and a same-name incoming definition; assert incoming style, open
state and another definition field survive. Include a new named chart too, because the current merge
marks a definition with no tab closed. This belongs with the outstanding lifecycle fixes, not a rollback
of the correct `withExternal` change.

### R2 — Medium: the new “settingsShare” test does not exercise SettingsShare (new test gap)

**Location:** `StyleDropdownRequestsASaveTest.java:82–100`, especially `:89`; production call site
`SettingsShare.java:353`. **Reproduced with a mutation.**

The test invokes `GraphSpec.withExternal` directly. Replacing only the SettingsShare call site with the old
13-argument constructor restores the original bug, but **all three tests in this new test class still pass**.
I am not claiming the entire suite was run under that mutation.

My separate probe runs `ProjectProfile.save/load` and `SettingsShare.export/preview/apply`. Under exactly
that mutation it fails:

```text
java.lang.AssertionError: PROFILE_METADATA line-true-series: got step/true
```

**Required for closure under rule 8:** promote a real import/profile round trip into the ordinary suite,
covering external series and external markers, non-default styles and closed charts. The mutation belongs
at `SettingsShare.preview`'s call site. A test solely of the wither remains useful but is not the regression
check for the importer. Preserve the independent closed-state assertion so fixing style alone is insufficient.

## Requested checks and dispositions

### Fix 1: combo events and restore — holds, reproduced

- On JDK 21.0.8, all four shipped FlatLaf 3.5.1 themes (Light, Dark, IntelliJ, Darcula), plus every installed
  JDK look-and-feel on this Mac (Metal, Nimbus, CDE/Motif, Mac OS X), gave **one callback per call**, including
  repeated unchanged selections. No look-and-feel was skipped. The probe exercised 12 calls per theme:
  setter and actual combo model, each with changed and unchanged Stairs/Line/Points selections.
- In each of those eight configurations, binding then restoring two charts with different styles produced
  **zero change-listener calls**. A repeated same-style setter after restoration produced one. This checks
  both sides of the restoring flag: no write-back mid-rebuild, no permanent suppression afterwards.
- The requested mutation, removing only `mutated()` from the dropdown listener at `GraphPanel.java:186`,
  fails `choosingAStyleFromTheDropdownAsksToBeSaved` at the save-count assertion (expected 1, got 0).
  `theVerbPathStillAsksExactlyOnce` also fails. All three tests are green before and after; source SHA-256
  is identical after restoration. See the machine-readable mutation record.
- **Real display:** a Robot clicked the actual Line popup entry on the built subject, under an isolated
  home with a constructed eight-record placeholder fixture. I waited for production autosave without
  invoking a flush or making another edit. The profile acquired `graph.1.style=line`. I closed the project,
  confirmed its log was closed, reopened the project, explicitly reopened the log, and clicked the chart's
  Project-panel Open. It rendered as Line. This does not claim automatic log/session restoration.
- Both before/reopen screenshots were inspected; the dropdown says Line and the plot is a line. The test
  did not substitute the `graph {style}` action for the mouse selection.

This establishes the claim for the supported themes and the installed JDK look-and-feels, not every
third-party delegate or future JDK. Nothing observed supports adding the duplicate setter notification back.

### Fix 2: wither and construction audit — local fix holds, reproduced/inspected

Twelve round trips crossed plain/external-series/external-marker graphs with Line/Points and open/closed.
The probe used the real profile and share entry points, checked that relative external paths became
absolute, and compared **every other GraphSpec component**. All passed. This reaches the importer that
the new unit test does not reach. R1 concerns the subsequent live UI adapter, not these pure entry points.

Production construction sites, inspected over `src/main/java`:

| Site | Result |
|---|---|
| `GraphSpec.java:50` (`withOpen`) | Carries all components; deliberately changes only open. |
| `GraphSpec.java:61` (`withExternal`) | Carries raw stored style and open, plus all other fields; changes only the two lists. |
| `GraphTabs.java:285` (`specs`) | Uses the style-bearing compatibility constructor. Its implicit `open=true` is intentional: these are snapshots of open tabs, not copies of closed definitions. |
| `ConfigStore.java:603` (`readGraphs`) | Reads style and open explicitly; defaults for old files remain deliberate. |
| `SettingsShare.java:353` | Now delegates to the wither; no remaining component-by-component copy here. |

The retained compatibility constructors are still a maintenance hazard, but I found no other production
call site silently losing these two components. A wither reduces that hazard; it does not protect its callers
from reverting to a shorter constructor, which is why R2 matters.

## Optional improvements

1. Put the repeat-selection and restore-suppression probe cases in the ordinary suite, parameterized over
   the four supported themes. Existing tests cover the default theme; the eight-theme probe is preserved
   evidence, not an installed CI gate.
2. Correct the milestone references in the new comments: the live tracker calls M68.3 **framing** and M68.4
   **whole-or-refused requests**, not chart lifecycle/style. Use the chart-rendering M68.2 item or the concrete
   defect name, without implying those broader milestones are complete.
3. Name the wither-only test for what it tests, once the importer has its own test. Likewise, setting a combo's
   index reaches its listener but is not itself a real-display click; the recorded Robot check supplies that
   missing evidence for this revision.

## Gates actually run

| Check | Result |
|---|---|
| `JAVA_HOME=<JDK21> mvn -q test` on clean subject | **1,894 total / 0 failures / 0 errors / 62 skipped** (1,832 executed successfully). |
| Same full command after mutations/restoration and review packet | Same **1,894 / 0 / 0 / 62**. |
| `JAVA_HOME=<JDK21> mvn -q -DskipTests package` | Pass; the resulting jar was used by the display probe. |
| `mkdocs build --strict` | Pass. No site file changed. |
| Existing new test class, mutation green/red/restored | 3 green → 2 named failures → 3 green. |
| Old-constructor call-site mutation | New committed class stays 3 green; real round-trip probe fails; restored class and probe green. |
| Direct callback/restore probe on real display | Eight look-and-feels, none skipped; all checks pass. |
| Profile/share probe | Twelve cases pass, all non-path components compared. |
| Real MainFrame mouse/reload probe | Line persists; R1 reproduced at the import refresh seam. |
| `git diff --check`; tracked public-rule sweep including this packet | Clean. |

The 62 suite skips were not relabelled passes. I did not run the complete CI real-display class list, a
fresh client, a release, the wider Close/Delete acceptance, or other repositories. The only temporarily
changed production files were the two mutation sites, restored byte-identically. No key was used.

## Evidence

[Reproduction instructions and file index](evidence/chart-style-fixes-review-2026-09-24/README.md).
The packet contains runnable Java probes, the mutation runner, exact outputs, hashes and three inspected
screenshots. Any remaining Close/Delete or menu work is separate from this commit's review.
