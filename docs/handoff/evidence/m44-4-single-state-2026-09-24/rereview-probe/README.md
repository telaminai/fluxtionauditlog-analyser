# Independent re-review probes at aaca6166

Constructed cases, not a client-session replay. No key or model was used. The product source was not edited to run these probes.

Run from the repository root with JDK 21 on PATH and the subject jar packaged. Use a scratch directory of your choosing:

```sh
mvn -q package -DskipTests
javac -cp target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar -d "$REVIEW_SCRATCH/classes" docs/handoff/evidence/m44-4-single-state-2026-09-24/rereview-probe/RereviewProbe.java
java -Djava.awt.headless=true -cp "target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar:$REVIEW_SCRATCH/classes" telamin.fluxtion.audit.analyser.analyser.ui.RereviewProbe
python3 docs/handoff/evidence/m44-4-single-state-2026-09-24/rereview-probe/PdfProbe.py "$REVIEW_SCRATCH/pdf"
```

The PDF probe opens the built app with an isolated user home and a local action socket. Run it alone on the display. It makes a three-record fixture, saves a one-node focus, asks the series verb for the middle point, and exports the same series call alongside the focus. It does not click a menu or use an assistant model.

| Artifact | What it establishes |
|---|---|
| `RereviewProbe.java`, `output.txt` | A refused showAll/save clears an existing focus; explicit STRICT and time-filter series calls differ between action and report extraction. Additional raw address inputs are recorded, including invalid/unnormalised inputs that do not establish a production-surface defect. |
| `PdfProbe.py`, `pdf-output.txt` | The real action returns one filtered point; the real report export succeeds. The output-directory prefix in the recorded reply is replaced with `<artifact-directory>`; no other result is changed. |
| `constructed-series.yaml`, `focus-and-series.pdf`, `pdf-text.txt` | Actual constructed input and exported two-page artifact. Both pages were rendered with Poppler and visually inspected: one-node focus on page 1, three-point chart on page 2. The PDF itself is unedited. |
| `original-output.txt` | The preserved earlier ReviewProbe exits 1 at the expected immutable-snapshot exception; this is not counted as a passing test. |
| `guarded-output.txt` | The preserved fix-probe/ReviewProbeGuarded completes, retaining the snapshot, ordered delivery, atomic original save refusal, rolled identity, BOM suspicion and pending NOT ASSESSED outcomes. |
| `report-coverage-output.txt` | The preserved earlier ReportCoverageProbe prints the action refusal and the PDF refusal, without a ratio. |
| `headless-counts.json`, `display-counts.json` | Own Surefire totals, captured before subsequent runs overwrite reports. Headless: 300 mapped reports, no orphans. Display: 19 frame suites, zero skips. |
| `mutations-summary.json` | All 120 controls, including each named assertion failure, green baseline/restored result and byte-identical source/class restoration. The record omits machine classpaths/build logs and elides scratch paths in assertion messages. |
| `coverage-output.txt`, `transitions-output.txt`, `python-output.txt` | Own built-jar and Python verification outputs. Machine scratch paths, if present, are labelled. |

The full suite and all registered controls pass despite the two new counterexamples. These probes describe defects; they are not implementation fixes or new green regression tests. The implementor must add the regressions with controls when fixing them.
