# PR #57 review probes — 2026-09-28

These are reviewer probes, not additional product implementation. The original subject is
`42309ed2445ea246dceebc1a3d0f744f8836310e`. The representation correction is `1a6ddf63`.
No participant project, key, provider or client-model session was used.

## Reproduction

Use JDK 21, compile the repository tests, and obtain the test classpath from the
`java.class.path` property of a fresh Surefire XML report. Compile both Java files here with:

```sh
"$JAVA_HOME/bin/javac" -cp "$TEST_CP" -d "$PROBE_CLASSES" ReviewProbe.java ReviewFrameProbe.java
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "$PROBE_CLASSES:$TEST_CP" \
  telamin.fluxtion.audit.analyser.analyser.ui.ReviewProbe
"$JAVA_HOME/bin/java" -Djava.awt.headless=false -cp "$PROBE_CLASSES:$TEST_CP" \
  telamin.fluxtion.audit.analyser.analyser.ui.ReviewFrameProbe
```

Run from the repository root, qualifying the Java file arguments with this directory.
Use a fresh temporary directory for `PROBE_CLASSES`. Run the frame probe by itself on a display.
The frame fixture isolates its home/settings and opens only the repository's demo graph/log.
It uses the real frame and action executor, but is not a native mouse/keyboard trial.
Its waits evaluate conditions on the EDT. It also inherits the fixture's initial 300 ms settling delay.

`ReviewProbe` uses the existing authoring/presenter test fixtures and the production resolver.
The chart drawn fact and run hashes in its append scenario are supplied facts: this is a
reproduction of re-binding unchanged steps, not an end-to-end chart paint or file-rewrite test.
`ReviewFrameProbe` posts a `LogIdentityObserved` fact into the real session, then waits for
re-resolution. It does not physically replace the file or verify the observation trigger.

## Captures

- `headless-probe.txt`: original-head outcomes; all four defects reproduce.
- `frame-probe.txt`: original-head outcomes on a real frame; five component-integration failures reproduce.
- `headless-probe-after.txt`: the same probe after `1a6ddf63`; only the representation verdict changes.
- `representation-regression-before.txt`: the new test on the original implementation, **1 / 1 / 0 / 0**
  (total / failures / errors / skips), failing at the representation assertion.
- `controls-summary.json`: a projection of the fast engine results, retaining control names, source hashes,
  failure class/method, restoration evidence and restored exits. Commands and machine paths are omitted.
  Original-head controls: **62/62**, **235.3 s**. Fix controls: **3/3**, **8.0 s**; the latter include two
  existing controls again. Every mutated run had a named assertion failure; every restoration was green.

The initial authoring probe used an unsupported `graph:DEMO:plot` address and failed its setup assertion.
It was corrected to `graph:DEMO` before recording product outcomes. That setup failure is not a finding.
The representation test was run red before editing production source. The subsequent registered control
removes only the representation guard; it restores source and compiled classes byte-identically.
No session processor regeneration was necessary for this resolver-only correction.
