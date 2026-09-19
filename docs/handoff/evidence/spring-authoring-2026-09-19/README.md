# Spring authoring fresh-session evidence, 19 September 2026

Captured from a real application and a separate analyser process with an isolated
profile. The participant received no prior conversation, implementation review or
expected-result table. These are the first attempt's final outputs, including its
explicit limitations; they have not been rewritten to incorporate later fixes.

- `participant-prompt.txt`: exact prompt (SHA-256 recorded in the observer report).
- `participant-actions.json`: the participant's saved 37 analyser requests/replies.
- `observer-actions.json`: separate reproductions after the first attempt, in a
  fresh isolated analyser. These are not participant actions.
- `run-c` and `run-d`: actual state getters, sink callbacks and audit output from two
  fresh JVMs. Earlier runs A/B had missing audit document separators and remain in
  the local experiment archive, not the final analyser dataset.
- `generated/source`: captured participant-produced host/nodes/events, kept under
  the repository's generated-evidence convention so their original whitespace is
  preserved. The complete generated Java is
  retained in the isolated archive and was read by the observer; its original hash
  is in `generated-source-sha256.txt`. It is omitted from this public packet because
  its generated header is unsuitable for public repository policy. No runtime
  output or node/business source was rewritten to remove that header.
- `MyProcessor.graphml`: the actual generated graph used by the analyser.
- `participant-report.pdf` and `participant-chart.png`: unchanged analyser exports.
  Their references to `/tmp/spring-authoring-acceptance/run1` name the original
  experiment; use the corresponding files here for the portable evidence.
- `SpringCoverageProbe.java`, `coverage-probe.txt`: independent reproduction of the
  false absent-node warning. The saved probe result is from the original location.

Verify the measured scenario with `python3 docs/handoff/evidence/spring-authoring-2026-09-19/verify.py`.
After `mvn package`, reproduce the coverage defect with Java 21:

```bash
java --class-path target/classes docs/handoff/evidence/spring-authoring-2026-09-19/SpringCoverageProbe.java docs/handoff/evidence/spring-authoring-2026-09-19
```

On the observed analyser this intentionally exits 1: `checked` is in the graph,
but the authored-node subset excludes the framework sink. Coverage and graph
pairing must use the complete graph for membership, while keeping their appropriate
coverage denominator. A future fix should make this probe exit 0, retain warnings
for genuinely absent nodes, and update the action, UI and exported report together.

The PDF was rendered and all five pages inspected. Its duplicated record-5 panel,
clipped marker captions and small chart remain visible as observed presentation
limitations. None changes the measured state or callback counts. This packet is
acceptance evidence, not a polished public tutorial or proof of arbitrary inputs.
