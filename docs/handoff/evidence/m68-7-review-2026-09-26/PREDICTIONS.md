# Independent M68.7 review predictions

Recorded before trials, subject 844462fe, 2026-09-26.

1. Full headless suite: 2397 tests, no failures/errors, 109 skips, 323 reports.
2. Requested neighbouring display tests pass without skips. Registered display gate has 22 suites; the focus-only case may skip. Preserve either outcome.
3. Six requested controls are caught at named failures, restore bytes, and return green. Preflight finds 165 anchors.
4. Same-length mapped rewrite publishes UNVERIFIED; atomic replacement publishes REPLACEMENT. All live banners follow the same snapshot and clear on reopen/close.
5. Suspected visual gap: a long identity reason in a plain JLabel may clip the chart-specific explanation at default pane width. Inspect actual bounds/text, not just JLabel visibility.
6. Report images may omit the mark. Determine whether export refusal prevents the proposed G14 bypass before calling this an observable defect.
7. A temporary control removing the banner from its parent while retaining its text and visible flag may survive the new tests. If so, record the limit: they assert component state rather than on-screen reachability.

No API key, provider, client trial, or full mutation gate will be used.
