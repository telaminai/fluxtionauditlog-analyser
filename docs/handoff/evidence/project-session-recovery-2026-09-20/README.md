# Explicit project recovery — adapter witness

Captured 2026-09-20 by `SessionRecoveryFrameTest` using Java 21, a display and isolated user.home.
The subject is the real analyser window and real asynchronous readers. The sample is synthetic repo
content, not the owner's staged project. Both PNGs were visually inspected; they caught an initial
landing alignment/scroll defect, corrected before these final captures. These are frame-painted Swing
images, not simulated layouts and not an OS desktop capture.

- `recovery-offer.*`: after close/reopen, the project declarations and Restore/Dismiss actions are present;
  no log, topology, design or diagnostics are implicitly opened.
- `recovery-finished.*`: after explicit acceptance, the actual loaded evidence and final outcome.
  The intentionally minimal topology does not fit the sample log, and its mismatch remains stated.
  The minimal producer result has no input hashes, so its evidence relationship stays unknown.
- `seen-red.log`: removing the comparison between captured file hashes and the hashes of the loaded view
  makes `fileEditedBeforeCloseCannotAcquireTheOldViewsIdentity` fail at its withheld-view assertion.
  The source was restored in `finally`. The first driver's own result check incorrectly searched for an
  assertion phrase absent from the failure output; the corrected check names the actual failing test line.
- `mutation.py`: portable copy of that driver. Run only in a clean disposable checkout with Java 21 and
  a display; it temporarily edits MainFrame and restores its original bytes even on test failure.

Display command for capture:

```sh
mvn -q -o -Dtest=SessionRecoveryFrameTest -Djava.awt.headless=false \
  -DargLine=-Djava.awt.headless=false -Djourney.evidence=/tmp/journey-recovery-evidence test
```

The suite also covers a changed log with independent design restoration, startup offer/dismissal,
changed bytes before close, and missing rolled-set membership. Restart is witnessed by a new real frame
using the same isolated persisted home after draining the shutdown capture queue; this does not claim an
OS process quit/relaunch or a paid application build. Main's removal of implicit startup paths is source
verified. The accompanying implementation handoff records full gates and remaining delivery scope.

`manifest.json` hashes captured files and the portable driver. It excludes this explanatory README.

The stored JUnit log has trailing whitespace removed to meet repository whitespace rules; all output
text is otherwise unchanged. Original output SHA-256: `9810d1dc44c9bedaedb9be60fd3bf088849285da72ac492f6171f0b92f5ecd49`.
