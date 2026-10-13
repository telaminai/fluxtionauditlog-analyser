# Post-seal notes (added 2026-10-13, after the runs, from the review of PR #131 at `acfe38e3`)

The sealed files (`PREDICTIONS-BASELINE.md`, `FIXTURES.md`, `MANIFEST-BASELINE.sha256` and `.sealed-at`, commit
`b2234e52`) are left **byte-identical**, so the manifest still verifies. These notes correct or qualify statements
in them. None of them changes a fixture, task, oracle, scorer, probe, driver, run or score.

1. **The stack changed as a whole, so no delta is attributable to the guidance alone.**
   - **`FIXTURES.md`, "What changed in the templates":** it says "It is the change being measured." That is too
     strong.
   - **`PREDICTIONS-BASELINE.md`, "What changes":** it names 1.33.0 only as "the operator's `--rest` analyser".
   - **What actually changed between the two ladders, all at once:**
     - the analyser went from 1.32.0 (jar `f125050b…`) to 1.33.0 (jar `f70da960…`), both as the operator instance
       **and as the subject's MCP bridge**, which runs the same jar (`config.json` `mcp.args`);
     - 1.33.0 itself changed the `add-a-node` skill guidance (no generated-code reads; audit-log comparison) and how
       the `annotation` trace key is read (CHANGELOG 1.33.0);
     - starter-core and BOM went from 1.0.76 to 1.0.77, and runtime from 1.1.0 to 1.1.1;
     - the hosted generator now stamps 1.0.78;
     - the templates' guidance and scripts changed (read-less docs, `run-and-export.sh`, per-processor export).
   - **Consequence:** every first-ladder → baseline delta in RESULTS.md is a delta of the whole released stack, and
     cannot be attributed to the template guidance alone, or to any one lever. The lever table in
     PREDICTIONS-BASELINE.md lists *expected* mechanisms, not identified causes.
2. **The runner is not the same as the first ladder's.**
   - **`PREDICTIONS-BASELINE.md`, "What stays the same":** it says "the runner, `tools/g14_runner.py` `b28ca7de…`".
   - **The first ladder's sealed runner** was `39393be5…` (analyser `2eb87ca9`, the same as at `aee55f0f`). This one
     is `b28ca7de…` (`df8dab57`).
   - **The only difference** (`git diff 2eb87ca9 df8dab57 -- tools/g14_runner.py`: 12 insertions, 1 deletion) is the
     stray-sweep path match. `in_base()` replaces the substring test `base in command or _cwd(pid).startswith(base)`,
     so a sibling base sharing a prefix is no longer swept.
   - **Unchanged:** `tools/coldstart_clock.py`, the sandbox profile, the key scan, the tool list and the subject
     command.
3. **`FIXTURES.md` itself is unaffected by these notes** apart from note 1. Its digests, downloads, modifications
   and ground-truth results stand.

The other corrections from the same review (the key-scan mechanism for base-r5-h55-1, the base-r2-h55-3 CHART
cause, the cold-vs-warm wording, the swept-shell command) are in RESULTS.md, which was not sealed.
