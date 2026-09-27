# PR #33 review fixes: results against PREDICTIONS.md (9286c0d8)

JDK 21, macOS. RAN means executed here.

| # | Prediction | Result |
|---|---|---|
| R1 | Merge main; the merged tree compiles | **Held.** Merge `815f82c4`. One text conflict (MainFrame's ReportsPanel construction, both sides kept) and one semantic break the text merge hid: main's `ReportsPanelLogFindingsTest` called the old constructor. Its call now passes the two new hooks. |
| 1 | The link-out case falls back to the machine setting, with a refusal naming the link; the test fails first | **Held.** Before the fix: 15 tests / 1 failure (the escape test). After: 15 / 0. |
| 2 | A link that stays inside the project is still accepted | **Held.** It passed before and after. |
| 3 | The 13 existing boundary tests are unchanged | **Held.** |
| 4 | A control removing the real-path check fails at a named assertion | **Held.** `p33-exchange-link-escape` fails at "a link that leaves the project must not be the write directory". |
| 5 | Delete then restore round-trips unchanged through the machine settings | **Held.** `ReportBinTest` |
| 6 | Restore is scoped to the project | **Held.** |
| 7 | Capacity 20, oldest first | **Held.** |
| 8 | No `deletedReport` key in a saved project profile | **Held.** |
| 9 | Restoring onto a taken name is refused, and nothing changes | **Held.** Unit and frame test. |
| 10 | The confirmation and the verb reply say how to restore | **Held.** The old "cannot be undone" assertion now asserts the opposite promise. |
| 11 | Controls: delete skipping the bin; bin not saved; restore ignoring the project | **Held.** `p33-delete-skips-the-bin`, `p33-bin-not-saved`, `p33-restore-ignores-project`. |
| 12 | A CHANGELOG line for each of D-1 to D-4 | Done. |
| 13 | Headless suite green | **Held.** 2487 / 0 / 0 / 120, 334 reports, no orphans. |
| 14 | Display gate: 23 suites | **Missed: 24.** The new `ReportRecoverableDeleteFrameTest` was added and registered in both CI lists. 120 / 0 / 0 / 1; the skip is the known PersonAtTheScreen focus case. |

## Misses and findings the predictions did not foresee
1. **`report {restore: true}` never reached the reports code.** `ActionExecutor` routes the `report` verb to the
   named-report path only when `sections`, `name` or `csv` is present, so a bare `restore` fell through to the
   single-record export and was refused with "'path' is required". The new frame test found it, and the routing
   now includes `restore`. Control `p33-restore-not-routed` fails at "restore: true lists it".
2. **The first full suite run found an unowned key family.** `KnownKeysCoverTheWritersTest` failed because
   ConfigStore writes a `deletedReport` family that `KnownKeys.CONFIG_FAMILIES` did not own, so stale entries
   could never be cleared. It is now registered as a machine-only (CONFIG) family, never a profile family.
3. **Persistence needed a test through the real save.** The first round-trip test called the bin's serialiser
   directly, so a global save that stopped writing the bin would have passed.
   `ReportBinTest#theMachineSettingsSaveKeepsTheBin` goes through `ConfigStore.save` and `load`, and it is the
   target of `p33-bin-not-saved`.

**Controls:** 5 new, all caught at named assertions with byte-identical restores (11.2 s). Preflight reports 24
frame suites and 199 anchors.
