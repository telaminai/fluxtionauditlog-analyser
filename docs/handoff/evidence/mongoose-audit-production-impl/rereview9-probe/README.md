# Ninth re-review probes — 2026-09-26

Subject: `8a35a988`, delta from `e5541d5b`. These are constructed review cases, not a producer replay or an implementation change. All Java runs use the jar built by this review with JDK 21. The two R8Review/R9Extra text captures omit the programs' final empty line for the repository whitespace gate; no note text is changed.

- `R8Review.java` is copied byte-for-byte from `ba463890:docs/handoff/evidence/mongoose-audit-production-impl/rereview8-probe/R8Review.java`. Its new output is `R8Review-output.txt`.
- `R9Extra.java` uses those helpers for adjacent markers, mixed views, and null-record headers. The `streamEnd` header variant is an exploratory mixed ordinary record, not a valid end-marker fixture; no finding depends on it.
- `R9Matrix-output.txt` is a new run of the subject's preserved `rereview8-fixes/R9Matrix.java`.
- `R9MatrixCheck.java` copies that matrix replay and adds principal-pattern exclusivity, branch-family and holds-clause counts. It does not replace the committed regression matrix.
- `witness13-output.txt` is a new run of the subject's unchanged witness13.py. No earlier witnesses or full mutation gate ran.
- `negative_guards.py` probes the three rewritten negative guards, drops the holds premise, and deliberately moves a second-marker label one record too far. That last case documents a matrix-only survivor. The appended phrases in the first two cases are guard-sensitivity probes, not restorations of historical code.
- `package-counts.json` sums all Surefire XML suites mapped to source tests immediately after the full clean package, before targeted runs replace those reports. `final-package-counts.json` records a second clean package after all mutations were restored; the totals match.

Run from the repository root with JDK 21 as `JAVA_HOME` and first on `PATH`:

```sh
mvn -q clean package
java -cp target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar docs/handoff/evidence/mongoose-audit-production-impl/rereview9-probe/R8Review.java
java -cp target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar docs/handoff/evidence/mongoose-audit-production-impl/rereview8-fixes/R9Matrix.java
java -cp target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar docs/handoff/evidence/mongoose-audit-production-impl/rereview9-probe/R9MatrixCheck.java
mkdir -p /tmp/rereview9-classes
javac -cp target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar -d /tmp/rereview9-classes docs/handoff/evidence/mongoose-audit-production-impl/rereview9-probe/R8Review.java docs/handoff/evidence/mongoose-audit-production-impl/rereview9-probe/R9Extra.java
java -cp target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar:/tmp/rereview9-classes R9Extra
python3 docs/handoff/evidence/mongoose-audit-production-impl/rereview8-fixes/witness13.py
python3 docs/handoff/evidence/mongoose-audit-production-impl/rereview9-probe/negative_guards.py
```

Run the mutation scripts sequentially in a disposable worktree with clean `src/`. Both restore source from bytes and check restoration. The targeted scripts change Surefire reports; do not derive full-suite counts from their residual reports.
