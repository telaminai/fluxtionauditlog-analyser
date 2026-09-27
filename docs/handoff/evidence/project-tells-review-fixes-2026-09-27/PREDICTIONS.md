# PR #33 review fixes: predictions, recorded before any change (2026-09-27)

From the review of PR #33 at `9885a818`:
- R1: the conflict with main, and the merged tree not compiling. Done first, in merge `815f82c4`.
- R2: D-2's symbolic-link escape.
- R3: no CHANGELOG entry.
- The owner's D-4 decision, 2026-09-27: **recoverable delete.**

## R2: the exchange directory must really be inside the project
**Fix.** After the lexical checks and `isDirectory`, compare the resolved directory's `toRealPath()` with the
project root's real path. If it is not under it, refuse, naming the link, and fall back to the machine setting.

**Predictions.**
1. The reviewer's probe case (a project directory `exports` that is a link to a folder outside the project)
   returns `source=machine`, with a refusal naming the link. Written first as a test, it fails before the fix and
   passes after.
2. A link that stays INSIDE the project (`exports -> reports/shared`) is still accepted, so the check is about
   where the link ends up, not about links as such.
3. The existing 13 boundary tests are unchanged.
4. A control that removes the real-path comparison fails the new escape test at a named assertion.

## D-4: a delete can be undone
**Design.**
- A delete, from the Reports tab or `report {name, delete: true}`, moves the report into a **machine-local**
  "recently deleted" list, keyed by project and capped at 20.
- It is kept in the analyser's own settings, never in the project profile or an export, so a deleted report is
  never committed to a repository or shared.
- `report {restore: "<name>"}` and the tab's **Restore deleted…** bring it back into the project it came from.
  `report {restore: true}` lists what can be restored.
- A restore onto a name that is now taken is refused, not merged.
- The rules live in a pure class (`ReportBin`), so they can be tested without a display.

**Predictions.**
5. Delete then restore round-trips a report unchanged, through a save and reload of the machine settings.
6. A deleted report is restorable only in the project it was deleted from.
7. The list holds at most 20, and the oldest go first.
8. The project profile a delete saves contains no `deletedReport` key (nothing leaks into a committed file).
9. A restore onto a taken name is refused, and nothing changes.
10. The delete confirmation and the verb's reply now say how to restore; the old "cannot be undone" text is gone,
    and its test is updated to the new promise.
11. Controls:
    - delete not recording into the bin fails the round-trip test;
    - the bin not persisted fails the reload test;
    - restore ignoring the project fails the isolation test.

## R3
12. A CHANGELOG `[Unreleased]` entry for each of D-1 to D-4, the recoverable delete included.

## Suites
13. Headless: the merged baseline plus the new tests, 0 failures.
14. Display gate: 23 suites, with the PersonAtTheScreen focus skip environmental, as before.
15. CI green: build, ui-frame with 0 skips, all four mutation shards and the collector.
