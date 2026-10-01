# Issue #84 third-review evidence — 2026-10-01

Read [the report](../../review_issue84_bundle_provenance_2026_10_01.md) first. Application code is unchanged; the Java files here are standalone reviewer probes, intentionally outside Maven's production/test source trees.

## Reproduce

Use your own released-tag worktree, JDK 21 on JAVA_HOME/PATH, and no competing build/mutation process in that worktree. Build `mvn -o -q package -DskipTests` first. Set `EVIDENCE` to this directory from the review checkout. From v1.30.0 run:

```sh
lockf -k /tmp/fluxtion-analyser-display.lock python3 "$EVIDENCE/run_probes.py" bundle
lockf -k /tmp/fluxtion-analyser-display.lock python3 "$EVIDENCE/run_controls.py" --bundle-only
```

From v1.30.1 run:

```sh
lockf -k /tmp/fluxtion-analyser-display.lock python3 "$EVIDENCE/run_probes.py" walk
lockf -k /tmp/fluxtion-analyser-display.lock python3 tools/verify_project_chart_review.py --mode mutations --engine fast --output target/issue84-walk-control.json --case walk-can-point-at-java
```

The final walk source has twelve tests: the recorded ten-test run plus the two later encoding/Back probes. The native keyboard refinement was also run separately. This is deliberate red evidence, so run_probes exits 1 for wrong results. Own probes use test helpers followed by the shaded tag jar, and synthetic homes. The serial FastEngine uses its normal source/class byte-copy restoration; no checkout restoration or generated-source edit was used. The explicit monitor hold is on the actual Maven resolver/read, not a replacement presenter.

## Contents and preservation

- `findings.json`: R1–R10 descriptions, suggested checks; issue URLs are added after filing.
- `counts.json`: raw Surefire totals and per-test outcomes, with source mapping/orphan audit; native walk counts were recorded before the final headless run replaced those reports, then confirmed by an additional 27/0/0/0 native run whose report rows are retained.
- `logs/`: stdout/stderr including intentional assertion failures and unsuccessful attempts. Empty logs mean a successful quiet Maven selector; consult counts.json.
- `controls-bundle.json`, `control-walk.json`: requested names, assertion messages, source/class byte restoration, restored run results. Registered requested names all caught; two planted D-L3 bypasses actually remained green.
- `gate-run.json`: directly read job results for run 36734309006, not locally re-run evidence.
- `preflight.json`: forty frame suites / 551 anchors.
- Three `.png` files: actual native Swing renders, inspected by eye. The changed-line and after-navigation images are native lower-pane crops to omit filesystem headers, not edited mocks.

Machine paths were normalised, and trailing whitespace in log lines was trimmed for the repository whitespace gate: `<REVIEW_V1300>`, `<REVIEW_V1301>`, `<PRIMARY_CLONE>`, `<USER_HOME>`, `<PYTHON_RUNTIME>`, `<REVIEW_TEMP>` identify local harness locations; `/DEMO/tmp/` replaces the OS temporary directory prefix for generated DEMO fixtures. No assertion message meaning, counts, verdict or data value was changed. Public data sweeps cover tracked files, new files and public issue/comment text; images were inspected separately.

### Unsuccessful attempts kept

1. Initial headless sandbox: 3026 / 0 / 36 / 218, socket-permission errors. Identical permitted retry and final run: 3026 / 0 / 0 / 218.
2. Bundle native suite first run: 18 / 0 / 1 / 0, DirectoryNotEmpty cleanup error. No edit; retry 18 / 0 / 0 / 0.
3. First standalone bundle launcher picked incompatible cached JUnit launcher: OutputDirectoryCreator missing, zero tests. Runner now matches the engine version.
4. First Java probe compile had an ambiguous Frame import: eleven javac errors, zero tests. Import corrected in the reviewer probe.
5. First Java changed-line assertion compared lowercase `current` with uppercase `CURRENT`, falsely passing: 10 / 3 / 0 / 0 preserved. Corrected assertion produced 10 / 4 / 0 / 0. The false pass is not acceptance evidence.
6. First control runner imported caught from the wrong module: zero tests. Then it passed Class#method to a scorer expecting only the method name; raw assertion failures were incorrectly labelled survived. The first JSON is retained. Corrected scoring and final result are in controls-bundle.json.
7. An intermediate restored bundle-anchor run hit the same cleanup IOException. Byte restoration completed, but that rerun was not green. Unchanged final retry completed all seven restored-green runs. Read the raw error; it is not a stale assertion anchor.
8. An initial preflight command omitted required --output and exited 2 before testing. The corrected preflight completed. GitHub/network retries affected reads only, not test outcomes.

No credential was inspected, no paid provider was used, and no PR #87 worktree/tests were accessed. Its posted review/status is context only.
