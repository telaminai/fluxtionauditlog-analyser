# Shared-canvas proposal — fourth feedback snapshot

[AUTHORING-DOCS-FEEDBACK.md](AUTHORING-DOCS-FEEDBACK.md) preserves the participant's new four-use-case
framing and validation-pack proposal byte-for-byte. The analyser issues document is unchanged from
round3. [Review decisions](../../review_staged_spring_feedback_2026_09_19.md#fourth-addendum--four-use-cases-and-the-validation-pack)
separate the useful direction from proposed mechanisms and unsupported claims.

`ComparisonScopeProbe.java` and `comparison-scope.txt` record an independent probe against the existing
analyser jar. It changes every PriceUpdate event name in memory and the default outcome scorer still
passes its 11 scored trade events. It also shows that the existing last-value record diff treats writes
`[1,2]` and `[2]` as equal. Neither result is a regression claim: these components implement narrower
contracts than general input/dispatch/output equivalence.

Reproduce from the analyser root with JDK 21:

```sh
java -Djava.awt.headless=true -cp target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar \
  docs/handoff/evidence/spring-authoring-feedback-2026-09-19-round4/ComparisonScopeProbe.java \
  docs/handoff/evidence/spring-authoring-feedback-2026-09-19-round2/evidence/mongoose-run1/audit.yaml
```

The input log is already preserved in round2. The manifest pins the copied files, analyser revision
and tested jar. No participant artifact was mutated and no running application was controlled.
