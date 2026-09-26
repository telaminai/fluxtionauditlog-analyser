# M68.7 review fixes — predictions, recorded before any change or trial (2026-09-26)

Review: `docs/handoff/review_m68_7_identity_mark_2026_09_26_codex.md` on `review/m68-7-identity-mark-2026-09-26`
(34f4d800). The subject was `844462fe`. The reviewer's report and preserved evidence are not edited.

## R1: the acceptance must prove the mark reaches the screen
**Cause, as the review states it:** the frame fixture never showed the frame, and `identityNote()` reads the label's
own visible flag. An unattached label (no parent, never painted) therefore passed. The test checked the label's
state, not its reachability on screen.

**Correction planned:**
- **The trigger.** `IdentityMarkFrameTest` shows and lays out the real frame (1200×800). It triggers the observation
  through the real `ActionDispatcher` record-reading route (`read`), replacing the reflective observer. It asserts
  the read is refused on the rewritten mapped file.
- **The chart banner.** With the chart selected, the test asserts, for the banner:
  - `isShowing()`;
  - it descends from the `GraphTabs` chart area;
  - its visible rectangle is non-empty;
  - its bounds lie within the chart area's bounds.
- **The detail pane.** The same, for the detail banner.
- **Later charts and reopening.** A chart opened after the verdict, once selected, is still marked on screen.
  Reopening clears the marks, so neither banner is showing.
- **Waits.** Every Swing read, including the reopen wait, runs on the EDT, with an explicit condition and a deadline.

## Predictions
1. **The attachment control is caught before the fix.** With the current test, removing `north.add(identityBanner,
   BorderLayout.SOUTH)` from `GraphTabs` survives (reproduced from the review). With the new test, the same mutation
   fails at a named visibility assertion ("…banner must be showing on the chart area…"). Predicted **caught**.
2. **A detail-pane attachment control is caught too.** The same removal in `DetailPanel` fails at the detail
   visibility assertion.
3. **The retained controls hold.** The four M68.7 controls and the two M68.5 controls (`set13-a-table-banner`,
   `set13-a-rendered-from-snapshot`) are still caught. `m68-7-chart-text-bypasses-rule` needs its anchor moved,
   because O1 changes the chart text; its target and meaning do not change.
4. **The verdict through the dispatcher** is UNVERIFIED, as the review observed, and the read is refused.
5. **O1:** a short, self-contained verdict leads each note ("Charts not verified against the file on disk — reopen
   the log …"), then the reason; the full note stays in the tooltip. The frame test asserts that the text actually
   drawn at 1200×800 contains "not verified". The label stays one line, so its height is bounded.
6. **O2:** each banner recomputes `UiTheme.warnForeground()` in `updateUI()`. A headless regression switches Light →
   Dark, updates the component tree and asserts the dark colour (`ffe87a5a`). It then restores the previous look and
   feel. Predicted: it fails before the fix and passes after.
7. **O3:** the evidence and docs stop saying "a record read" of the reflective trigger (it no longer exists), and
   name standalone chart-image export beside the report/PDF as excluded surfaces.
8. **The G14 report policy** is recorded as an owner decision, not settled here. The report implementation is not
   expanded, and G14 is not declared unblocked.
9. **Suites.**
   - Headless: 2397 + new tests, 0 failures.
   - Display gate: 22 suites, 0 failures. The PersonAtTheScreen focus case may skip locally, as before.
   - Preflight: 22 frame suites, 165 + 2 = 167 anchors.

## Risks
- The graph tab must actually be the selected side tab when the assertions run. The `graph` verb selects it, but
  the test selects it explicitly too.
- The frame's size may differ on screen, for example from window-manager insets. The bounds assertion is relative
  to the chart area, not to absolute pixels.
