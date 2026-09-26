# M68.7 — predictions, recorded before implementation (2026-09-26)

M68.7: mark the charts and the detail pane with the session's file-identity verdict, rendered from the same
snapshot as the table's banner (M68.5). The charts gate G14; the detail pane does not.

## Design, fixed before code
- One banner on `GraphTabs`, above every chart tab, so a chart opened after the verdict sits under it too.
- One banner on `DetailPanel`.
- Both are set in `MainFrame.onSessionSnapshot`, next to the table's. There is no second verdict source.
- Whether to say anything is the table's rule: `LogTablePanel.identityBannerText` returns non-null only for
  UNVERIFIED and REPLACEMENT. The new texts call it, so the three surfaces cannot disagree about *when*; they differ
  only in *what* they say about their own surface.
- The existing M68.5 control anchors (the table's rule line and its `onSessionSnapshot` line) are left byte-identical.

## Predictions
1. **Headless text and panel test.**
   - UNVERIFIED and REPLACEMENT produce a note containing the reason on both surfaces.
   - null, VERIFIED and REOPENED produce none.
   - Each panel shows its note, then hides it for null.
   - Passes first time once implemented.
2. **Frame test: a mapped log (threshold 0), open chart, record selected, same-length in-place rewrite, then a
   record read.**
   - The session reports a changed identity (UNVERIFIED or REPLACEMENT; I predict **REPLACEMENT**, because the
     content differs at the same length).
   - The table, the chart area and the detail pane all show a note, with the same reason in all three.
   - A chart opened after the verdict is still under the banner.
   - Reopening the log clears all three.
3. **Controls.** Each is caught at a named assertion:
   - removing the chart line from `onSessionSnapshot`;
   - removing the detail line;
   - hiding the chart banner;
   - bypassing the shared rule in the chart text.

   Byte-identical restore, then green.
4. **Suites.**
   - Headless suite: +1 class and more tests, 0 failures.
   - The new frame test is registered in both CI lists, so the display gate grows from 21 to 22 suites with 0 skips.
   - Locally, the known PersonAtTheScreen focus skip is the only expected skip.

## Risks I expect
- The read-identity observation may be posted asynchronously (`invokeLater`), so the frame test must drain the EDT
  after the read before asserting.
- The verdict may be UNVERIFIED rather than REPLACEMENT for a same-length rewrite. The test asserts "a changed
  verdict" and records which one; this prediction is about the observation, not a requirement.
