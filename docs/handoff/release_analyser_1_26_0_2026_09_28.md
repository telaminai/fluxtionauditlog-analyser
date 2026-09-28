# Release 1.26.0 — M69 spotlight walks (2026-09-28)

**What ships:** M69 spotlight walks (PR #57, merged `5cdd12ec`). A named sequence of spotlight steps, stored like a
report and replayed with ◀ ▶ on the overlay:
- the `walk` verb, the seventeenth;
- the right-click save menu;
- the Reports tab's Spotlight walks list and `context.walks`.
Also in the same `[Unreleased]` block: the topology digest fix (M69 S0).

## Review history

1. **Spec review** (Codex, r3 → r4, `80decad7`).
2. **Implementation review**, PR57 R1–R9: all fixed, each with a regression that was red before its fix and a
   control (`docs/handoff/evidence/m69-review-response-2026-09-28/RESPONSE.md`).
3. **Fix review**: F1, F4, F5 and F6 fixed; F3 withdrawn; W-A4 shown in bytes
   (`docs/handoff/review_m69_fixes_2026_09_28_claude.md`).
4. **Targeted review with authority to merge.** It fixed a stale bulk-diff baseline found in F1's fix
   (`f130b1e1`) and a changelog overstatement (`f1d0086a`), then merged locally with `--no-ff` under the personal
   identity.

## Gates at `f1d0086a`, the merged head

| gate | result |
|---|---|
| `mvn -o clean test`, fresh Surefire XML | 2698 / 0 / 0 / 148, 359 reports, 0 orphans |
| display gate, sequential | 148 / 0 / 0 / 2 (the two focus-bound skips, disclosed) |
| M69 mutation controls | 101 of 101 caught (four parallel shards) |
| preflight | 29 frame suites, 350 anchors |
| `mkdocs build --strict`, `git diff --check`, rule-1 sweep | clean |
| CI on the PR head and on `main` after the merge | green |
| author identity | every commit in the range, and the merge, carry the personal address; the employer-domain count is unchanged at 226 |

## What to watch in a demo

- **The unassessed-log caveat.** With Follow off, every record or chart step's reason line says *current* means
  unchanged since saving. It is true, but it is repeated (M69.F3).
- **A project switch ends a playing walk** with "the spotlight went out…", not a reason that names the switch.
- **The pre-existing `MainFrame.onLoaded` NullPointerException** in `LogFindingsOnEverySurfaceFrameTest`. It
  reproduces on `main` before this merge, and it is not a walk defect.
- **No docs-site capture of a walk yet** (W-A11, M69.F1).
