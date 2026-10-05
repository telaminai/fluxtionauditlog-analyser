# PR #88 targeted review and corrections

The original head `db725e60` required corrections. These are implemented here, following the owner's
choice to include cross-process ownership and cleanup. This is the reviewer's implementation report,
not an independent approval of the resulting code. Merge remains subject to current-head CI and review
of this correction delta. No merge or release was performed.

## Required corrections found

1. **Deletion of in-use evidence — fixed.** READ: `EvidenceBundle.unpackAndReap` previously deleted
   every other working copy before the new project was accepted. Open A, start B, or open B in another
   analyser → A's files disappear while still being used. A superseded pending open could delete the
   accepted copy. `reap` also admitted the root and arbitrary descendants.
   RAN: `WorkingCopyOwnershipTest` exercises a second JVM, two windows' independent references,
   unknown and foreign ownership, root/nested/link refusal; `EvidenceBundleTest#pendingExtractionsCannotReapEachOther`
   checks two pending opens. `BundleImportOffTheEdtFrameTest` checks an actual bundle window and a
   manually opened working-copy profile. Named mutation witnesses are in the accompanying receipt.
   Fix: `WorkingCopyOwnership.java:52–148`, `WorkingCopyScope`, and `MainFrame.java:733,1483,7316,7343`.
   Shared leases protect readers; a parent coordination lock prevents acquisition racing deletion;
   cleanup holds an exclusive per-copy lock through deletion and only accepts same-host marked direct children.
   Resource release follows settled session paths; in-flight log reads retain their own lease.

2. **A cancelled or displaced import still changed settings — fixed.** RAN on the old implementation:
   pause the read, cancel, or switch A→B or A→B→A → hidden columns become `DEMO-borrowed` anyway.
   The raw `invokeAndWait` lost the action guard; no project lifetime was checked.
   Fix: `MainFrame.java:8228,8275` captures the basis and previews/applies in the guarded EDT transaction;
   `ActiveProject.java:41,55,66–71` owns and checks the project lifetime. The existing generated processor
   still delivers the same ProfileApplied/SettingsRestored facts: no new handlers, edges or generated code.
   A worker is required for the blocking read. Thirteen correction tests were added overall; the three
   original wrong-result regressions and their mutants now fail/pass as required.

3. **Cleanup tests touched the real working-copy directory — fixed.** READ: the two original cleanup
   fixtures used the default home and one reaped the entire directory. `EvidenceBundleTest` now supplies
   an isolated home before every test and restores it afterwards. No original destructive test was run
   against the owner's home. All new data and child-process fixtures are isolated DEMO inputs.

4. **The PR description described withdrawn reuse — corrected in the PR update.** READ: it still
   claimed identity-based reuse and cited tests no longer present. Fresh extraction is retained, because
   the active project can change its working profile. Ownership is permission to use files, not evidence
   that their contents still match the bundle. Issue #109 remains open.

## Scope and limitations

- Closing a project releases unused resources; deletion waits for the next bundle open or explicit cleanup.
  A failed open's released copy may therefore remain until that cleanup.
- Older unmarked copies, foreign-host markers, unsupported locking and other uncertain ownership are kept.
  No claim that the earlier accumulated legacy directories are automatically removed.
- Copies outside the standard root are not automatically cleaned. Another program replacing local files
  is outside this cooperating-process protocol. Network filesystems and Windows were not exercised.
- Manual cleanup runs off the EDT. The dialog itself was read, not operated in a native-input trial.
- The initial import red run preceded the written predictions; the document says so. Ownership predictions
  preceded ownership trials but were not a separate pre-implementation commit.

## Verification

RAN: full headless **3201 / 0 / 0 / 257**, **422 reports, no orphans**; focused display **41 / 0 / 0 / 0**
across three sequential classes; **14/14 targeted controls caught** at named assertions, each byte-restored
and green afterwards; preflight **44 suites / 647 anchors**; Python harness **5 / 0 / 0 / 0**; strict docs,
whitespace and public-data sweep clean. Failed/environmental attempts are recorded, not omitted.

Commands, per-class counts, prediction scoring and compact control receipts:
[evidence](evidence/pr88-targeted-2026-10-05/RESULTS.md).

Not run: full local mutation gate, all registered display suites, native visual inspection, Windows or
network-filesystem trials, regeneration or paid/model sessions. CI must cover the full gate on the new head.
