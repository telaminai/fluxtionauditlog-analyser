# G14 connection recovery — result, 2026-09-24

**Connection recovery passed; G14 is not an uninterrupted acceptance pass.** The
fresh recovery client reached released analyser 1.19.0 for 180.79 seconds, with
91 successful supervisor health checks and zero health failures. It created a
chart, named focus and report. All 68 copied project files remained byte-identical.
No generation, application changes, or compilation key was used.

## What was fixed

The first analyser launch had been checked only at startup. It later exited with
code 0 and removed its endpoint file; the cause of that exit is still unknown.
The wrapper failed before HTTP, not in MCP. This trial uses direct REST, not MCP.

`tools/analyser-session-watch.py` now owns the analyser and dependent client,
starts them independently of terminal input, checks process identity and the live
manifest throughout, records exit/health events, and stops the dependent client
on failure. It never silently restarts the analyser. Its explicit `--exchange`
option configures exports in the chosen isolated profile before launch; it does
not change a user's normal profile. The supervisor stops its own analyser when
the dependent command ends (the final exit 143 here is that recorded stop).

Six tests pass with a real loopback server; CI now runs them. Disabling the health
function and removing the production monitor call each fail named assertions in
disposable copies. The first witness packet ran five tests; the sixth, for explicit
export configuration, was added after the client exposed the missing setting.
The first six-test run had a macOS `/var` versus `/private/var` expectation error;
using the resolved path corrected the test. This is not a product failure.

## Predictions scored

Predictions were committed at `2b8e17e5` before the retry.

| Prediction | Result |
|---|---|
| Released analyser and sandbox transport can connect | Held: `transport-preflight.json`, manifest/context and client actions. |
| Loss is detected and dependent client stopped | Held in regression controls: process exit, missing/foreign endpoint, HTTP failure and monitor call-site guard. No loss occurred in this trial. |
| Both independent scenario runs match all expected states | Held: `CheckScenario.java`, `run1.txt`, `run2.txt`; 7 snapshots/run and exactly Checked(10,1), Checked(40,1). Wrong step-one expectation fails (`negative.txt`). No generation. |
| Client creates chart/report and exports without changing the project | Partly held: chart/report created, all 68 files unchanged; export refused because the isolated profile had exports off. Operator setup omission. |
| Analyser stays alive and exports are inspected | Alive throughout. Exports required a separately recorded operator replay with exports enabled; not an unaided client export success. |

The Java oracle uses existing getters on the original compiled processor, with
constants derived from the frozen task. It closes the original report's gap about
state immediately before and after reset. A later post-reset count alone would
not prove that the paused event left state unchanged; the direct snapshot does.

To repeat without a key or generation, compile `CheckScenario.java` against the
preserved application's fat jar and run it twice in separate JVMs. Its SHA256 is
in `result.json`; do not replace it with a newly generated processor.

## Failures preserved; new observations

1. **Input framing:** the original run has nine `eventLogRecord:` blocks and zero
   `---` separator lines. The analyser reads it as one record. The client reported
   this instead of inventing a multi-record chart. The application log writer must
   emit the format's separators; the trial's original files were not rewritten.
2. **Export setup:** the client correctly received export-disabled refusals. After
   it finished, the operator enabled exports in this isolated home, replayed its
   recorded actions, and obtained the PNG and PDF. No further client was run.
3. **Coverage disagreement:** `coverage-response.json` calls `checked` absent from
   the topology and suggests a different build. The preserved `MyProcessor.graphml`
   explicitly declares `<node id="checked">`; the inspected window status says
   the graph declares all three logged nodes. The client's corresponding claim
   that the file lacks the node is therefore false. This is a follow-up to the
   existing truthful-evidence work, not proof of a mismatched build.
4. **Report rendering:** all three PDF pages were rendered and inspected. The
   chart image is narrow and shows "No data under the current filter", despite the
   series response yielding one point. The requested topology illustration is
   absent. Export success does not establish correct rendered evidence. These
   observations belong to the existing chart/report correctness work.
5. **Original ownership edit:** the first client's manual ownership-record edits
   remain recorded. This recovery does not validate that method of resolving a
   conflict or rewrite the original trial as a clean success.

`SUBJECT-REPORT.md` and `subject-report-operator-export.pdf` are **preserved client
claims, not accepted conclusions**. They contain the disputed coverage claim and
the then-true export-disabled limitation; the PDF was exported later by the
operator without rewriting that testimony. Use this README for the disposition.
The screenshot and every PDF page were inspected for public-safe example names.
Raw authenticated startup logs are excluded from this public packet.

## Verification

- `python3 tools/test_analyser_session_watch.py`: 6 passed.
- Two monitor mutations: fail their named assertions, originals unchanged.
- `JAVA_HOME=/path/to/jdk21 mvn -q test`: 1,876 tests, 0 failures, 0 errors, 62 skips.
  First sandboxed run had 29 socket-permission errors; rerun with loopback permission
  passed. Neither is described as a product regression.
- Strict docs, spec links, whitespace and the public-content sweep are checked
  before publishing this packet.

The application remains deployed. Connection supervision is fixed; G14 stays open
for correctly framed, correctly rendered evidence and assessment of the ownership
recovery. No additional battery or paid generation was started.
