# Release 1.27.0 — evidence bundles, first delivery (2026-09-28)

**What ships:** evidence bundles (PR #63, merged `77d268be`). An investigation as one verifiable `.fexp`: the log,
its graph, the saved charts and named focuses, reports and walks, hidden columns, and the author's notes.
- **Capture** is one operation on the running analyser: `report {bundle: {path, notes?, from?, to?}}`. It is
  decided by the `evidenceCapture` session node (rule 9).
  - It refuses, by name: no log open, a load pending, an unestablished identity, a changed file (outside Follow),
    not one plain file, an empty window, or a capture already writing.
  - It takes the settings in force, not the file on disk. It pauses and restores Follow.
  - It writes off the event thread. If another log is opened, or the log is closed, meanwhile, it refuses and
    deletes what it wrote.
- **Excerpts:** an optional time window. The excerpt is re-read and matched record by record against the source,
  walks and reports are re-based onto it, and anything outside the window is left out and named. A log still
  growing under Follow is captured as the records read so far.
- **Nothing that describes the machine leaves.** A path-valued key refuses the capture; a path inside prose is
  redacted and named.
- **The recipient** runs `--verify` / `--unpack`. They stream in bounded memory, refuse any changed, missing,
  unlisted, duplicated, oversized or escaping member by name, and never modify the received file. A bundle is
  **unsigned** and **not a replay**, and every output says so.
- **Shared exchange directories are safe:** working folders are owned under an OS lock, and a folder is reaped
  only when its owner is provably dead.
- **Removed before release:** the capture and open skills, and `--pack` / `--bundle-profile`. The docs site's
  *Evidence bundles* section is the procedure.

## Review history

All the reports are in `main` (spec §13.8).
1. **v1 review:** F1 (`--verify` died on a zip bomb) and F2 ("holds no paths" was false) fixed (`79851205`,
   `f908f08f`).
2. **Convergence** (owner: "the `.fexp` is the product; few skills, or none"): capture moved into the analyser,
   with an optional excerpt (owner decision) and the skills deleted.
3. **Convergence review:** the empty-window refusal moved into the node; the age-gated cleanup was rejected in
   favour of lock ownership (`a8c37972`).
4. **Reaper review:** the reaper released its own lock through a POSIX descriptor close; fixed by real-path
   ownership (`0edc4916`). EB.F9, the moved-generation rule, is now provoked from the frame on every build
   (`534089a6`).
5. **Narrow review of EB.F6 and EB.F11** (verdict: merge now). The "records read" count was proven equal to what
   the session published (`a3c622fa`). Two advisories went to issues.

Owner decisions:
- EB.F6: bundle what has been read so far;
- EB.F1: accept a pinned window.

## Gates

| gate | result |
|---|---|
| CI on the PR head `a3c622fa` | all green: build, ui-frame (Xvfb), loop-bench, mutation-selftest, mutation-gate, four mutation shards, static |
| CI on `main` after the merge and the docs commit | green |
| `mvn -o clean test` on the merged tree, fresh Surefire XML | 2795 / 0 / 0 / 169 (skips = frame suites) |
| display gate | 32 suites, 167 / 0 / 0 / 5: the two keyboard-focus skips, and 3 in `TableDragCancellationFrameTest`, whose native Robot input this Mac drops. CI runs them. |
| mutation controls | every `eb-` / `rf` / `cv-` / `mousetrace-` control and every control anchored in `MainFrame`, 130 / 130 on the merged tree |
| the driver, `tools/evidence-bundle-demo.py` | 43 / 0 |
| `mkdocs build --strict`, `git diff --check`, rule-1 sweep | clean |
| author identity | the merge and every commit carry the personal address; the employer-domain count is unchanged at 226 |

## What to watch in a demo

- **Give the recipient's window room.** At the default 1200×800 a chart step reports *"no room — widen the window"*
  (EB.F1, accepted). The demo pins 1440×900.
- **A bundle is the whole log unless you pass a window,** so a real incident's log can make a large file. Use
  `from` / `to`.
- **A log growing under Follow** is captured as the records read so far. The capture says so, and so does
  `--verify`.
- **Two analysers sharing an exchange directory** are safe, but that case has been tested on one machine only
  (issue #69).

## Open, as issues

- #64: withheld chart definitions are captured silently.
- #65: the "changed-on-disk under Follow is growth" premise.
- #66: restoring Follow after a capture.
- #67: an excerpt of a log that is not time ordered.
- #68: the walk save's frame-side check (rule 9).
- #69: small follow-ups.
