# PR #105: verification after integrating main

Tree: `bb87ac5c` plus main `c66f744d`; the only merge conflict was the changelog.
The Java changes and mutation controls merged unchanged. Main includes the issue #84 fixes,
the broken-value reader correction, and the released fluxtion runtime 1.1.0 stack.
JDK 21, DEMO data, an isolated worktree. No regeneration or provider calls.

Every row is **RAN**. Counts are total / failures / errors / skips. The headless suite
ran on October 1; the final targeted display and mutation checks ran on October 5,
after the session resumed. Display operations ran serially under
`lockf -k /tmp/fluxtion-analyser-display.lock`.

| Check | Command | Result | Raw evidence |
|---|---|---|---|
| Full headless suite | `mvn -o -q test` | **3184 / 0 / 0 / 249**, 420 source-mapped reports, no orphans | [counts](headless-counts.json), [output](headless.log) |
| Native mouse suite | `mvn -o -q test -Dtest=TableDragCancellationFrameTest,LogFindingsOnEverySurfaceFrameTest -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false -Dsurefire.failIfNoSpecifiedTests=false` | **4 / 0 / 0 / 0** | [report](TableDragCancellationFrameTest.txt), [output](display.log) |
| Log-findings display suite | Same command | **14 / 0 / 0 / 0** | [report](LogFindingsOnEverySurfaceFrameTest.txt) |
| Registry/anchor preflight | `python3 tools/verify_project_chart_review.py --mode preflight --output <output>` | **43 frame suites, 633 anchors** | [result](preflight.json) |
| Harness regressions | `python3 tools/test_project_chart_review.py` | **5 / 0 / 0 / 0** | Passed locally |
| Documentation | `mkdocs build --strict` | Exit 0 | [output](mkdocs.log) |

Targeted command: `python3 tools/verify_project_chart_review.py --mode mutations --engine fast --output <output>`,
with one `--case` for each of the 13 names in [the assertion checklist](intended-assertions.json).
Requested **13**, ran **13**, caught **13**, in 195.7 seconds. The shared baseline was
**34 / 0 / 0 / 0**. Each mutated run failed at its intended assertion; none was accepted
on a focus/setup assertion. Every source and class restoration was byte-identical, and every
restored run was green with zero skips. [Raw results](controls.json), [output](controls.log).
No full local mutation gate was run.

The earlier failed attempts remain in [native acquisition evidence](../native-acquisition/README.md).
This integration check does not erase those attempts or substitute for exact-head CI.
Private machine paths in the raw evidence are replaced with placeholders.
