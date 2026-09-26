# M68.7 — results against the sealed predictions (2026-09-26)

Predictions: `PREDICTIONS.md`, committed first (fce34506). JDK 21, macOS.

| # | Prediction | Result |
|---|---|---|
| 1 | Headless text and panel test passes | **Held.** `IdentityMarkSurfacesTest` 3/0/0/0; `LogTablePanelIdentityBannerTest` unchanged, 3/0/0/0 |
| 2 | Frame test on a mapped log: all three surfaces marked, later chart marked, reopen clears; verdict **REPLACEMENT** | **Held, with one miss.** `IdentityMarkFrameTest` 1/0/0/0 on a real display. The session reported **UNVERIFIED**, not REPLACEMENT: an in-place same-length rewrite suspends reads ("the opened file changed in place … reads are suspended until the log is reopened"). The test asserts a changed verdict, so it did not depend on the guess. Observed with a temporary print, then restored from a byte copy (`cmp` identical). |
| 3 | Four controls caught at named assertions, restored byte-identical | **Held.** See the table below. |
| 4 | Suites: 22 registered frame suites, headless green | **Held.** Headless 2397 / 0 / 0 / 109 over 323 reports, no orphans. Display 22 suites, 109 / 0 / 0 / 1 — the one skip is `PersonAtTheScreenFrameTest`'s focus-guarded case, which this machine cannot give focus (CI's Xvfb runs it). Mutation gate 165/165 in 426 s. Preflight 22 frame suites, 165 anchors. |

| Control | Named assertion that went red |
|---|---|
| `m68-7-charts-not-rendered` | "every surface that can show a value states the verdict" → "the charts must state the verdict before G14 can pass on them" |
| `m68-7-detail-not-rendered` | same group → "the detail pane must state the verdict" |
| `m68-7-chart-banner-hidden` | "the chart banner must show its note" |
| `m68-7-chart-text-bypasses-rule` | "the charts say nothing for null" |

**Operational slip, recorded:** the verdict probe's command stopped on a zsh glob error before its restore step ran.
The restore was then run by itself, and `cmp` confirmed it byte-identical, with no probe line left in the file.
