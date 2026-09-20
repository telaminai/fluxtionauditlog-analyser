# Third staged-feedback snapshot — report export

[ANALYSER-FEEDBACK.md](ANALYSER-FEEDBACK.md) is preserved byte-for-byte, now through issue 28.
The preceding packets remain unchanged. [Review addendum](../../review_staged_spring_feedback_2026_09_19.md#third-addendum--report-export-issues-2427)
records the new decisions and verification limits.

`ReportFeedbackProbe.java` was run with JDK 21 against the existing analyser jar and the participant's
`evidence/desk-final/audit.yaml`; `probe.txt` is its output. It creates a resolved report in memory with
the same fallback-content shape as MainFrame, then checks the PDF bytes. It also exercises text
comparison parsing, read-table assembly, working series-statistics tables and quoted/unquoted booleans.
It does not drive the running app or export a new PDF.

Reproduce from the analyser root with JDK 21:

```sh
java -Djava.awt.headless=true --class-path target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar \
  docs/handoff/evidence/spring-authoring-feedback-2026-09-19-round3/ReportFeedbackProbe.java \
  /private/tmp/fluxtion-spring-demo/project/evidence/desk-final/audit.yaml
```

The manifest pins the analyser revision, copied files and inspected originals. Both participant PDFs
were rendered with Poppler and all 17 pages inspected (first: 9 pages; second: 8). The PDFs, current desk
audit and iteration account contain literal exchange names, so this public-repo packet records their
hashes and original locations rather than copying those artifacts. Those originals remain temporary;
this packet is not a complete archive of the desk experiment. No claim of independently verifying the
desk's business model or its mutation runs is made. The preceding authoring-docs report is in round2.
