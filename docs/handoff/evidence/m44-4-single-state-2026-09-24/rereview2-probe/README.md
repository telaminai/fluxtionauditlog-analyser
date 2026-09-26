# Focused N1/N2 re-review at 85a3f598

Constructed cases, no client or model session. Run from the repository root with JDK 21 and the
subject jar packaged. `REVIEW_SCRATCH` below is a scratch directory chosen by the reviewer.

```sh
javac -cp target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar -d "$REVIEW_SCRATCH/classes" FocusedProbe.java LiteralKeyProbe.java SeriesParity.java
java -Djava.awt.headless=true -cp "$REVIEW_SCRATCH/classes:target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar" telamin.fluxtion.audit.analyser.analyser.ui.FocusedProbe
python3 PdfProbe.py "$REVIEW_SCRATCH/pdf"
python3 LiteralKeyPdfProbe.py "$REVIEW_SCRATCH/literal-pdf"
```

Prefix each probe filename above with this packet's directory when running from the repository root.
Run the two PDF scripts sequentially on the display. Each uses an isolated home and an action socket,
and closes its app. The exported path in captured replies is replaced with `<artifact-directory>`.
PDFs and rendered pages are otherwise unchanged.

For the old/new comparison, extract `graph/SeriesScan.java` and `ui/ReportSeriesPicture.java` from
commit `93046a48` into scratch using `git show`. Both paths start with
`src/main/java/telamin/fluxtion/audit/analyser/analyser/`. Compile them against the subject jar into a
separate `old-classes` directory. Run the probes once with that directory before the jar on the
classpath, and once without it. `LiteralKeyProbe` takes its output PNG filename as its sole argument;
it reflects the old/new adapter signatures without copying either implementation. `SeriesParity`
prints 15 result maps; the preserved old/new files compare byte-identically.

| Files | Evidence |
|---|---|
| `FocusedProbe.java`, `direct-output.txt` | 26 assertions: original N1 refusal, three combined-action variations, copy isolation, original N2 counts |
| `SeriesParity.java`, `series-old.txt`, `series-new.txt` | 15 valid verb calls unchanged; this compares compiled old/current parser source, not two whole-app builds |
| `PdfProbe.py`, `pdf-output.txt`, `pdf/` | Original N2 counterexamples exported while the view is filtered to a different time; both pages inspected |
| `LiteralKeyProbe.java`, `key-old.*`, `key-new.*` | Literal key `v+1` draws 7 with the old adapter, 101 with the new one |
| `LiteralKeyPdfProbe.py`, `literal-pdf-output.txt`, `literal-pdf/` | Same new regression through the actual report action; PDF inspected |
| `headless-counts.json`, `display-counts.json` | Counts captured before later runs overwrite Surefire reports |
| `mutations-summary.json` | Three requested controls; named failures and restoration evidence; command/classpath/output fields omitted |
| `coverage-output.txt` | 89 built-jar checks pass |

PNG pages were rendered using `pdftoppm -scale-to 1600 -png` (original counterexamples) and
`-scale-to 1300` (literal-key export). The isolated-home artifacts contain only constructed data.
