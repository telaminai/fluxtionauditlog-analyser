# Independent review probes at 14a72acc

Constructed counterexamples, not a replay of a participant session. These probes do not change product source,
use a compilation key, or call a language model. Run from the repository root with JDK 21 and the packaged subject.
The observed outputs beside each probe are failures of the reviewed contract, not successful acceptance results.

```sh
mvn -q package -DskipTests
java -Djava.awt.headless=true --class-path target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar docs/handoff/evidence/m44-4-single-state-2026-09-24/review-probe/ReviewProbe.java
java -Djava.awt.headless=true --class-path target/fluxtion-auditlog-analyser-0.0.0-SNAPSHOT.jar docs/handoff/evidence/m44-4-single-state-2026-09-24/review-probe/CoverageRaceProbe.java
python3 docs/handoff/evidence/m44-4-single-state-2026-09-24/review-probe/ReportCoverageProbe.py
```

The Python probe opens the actual application in an isolated home and uses its action socket; it inspects the
exported PDF's text. It does not press buttons or establish a visual acceptance. Run it alone, on a display.
The Java probes call the real packaged classes. The coverage race uses latches, not a timing assumption: the old
store is captured before the EDT switches the log, and the executor then captures the new session generation.

| Probe | Observations |
|---|---|
| `CoverageRaceProbe.java` | An old log's foreign id is accepted as a qualification of the new log. |
| `ReportCoverageProbe.py` | The coverage action refuses the retained foreign pair, but its report exports a numeric coverage ratio. |
| `ReviewProbe.java` | Mutable published qualification; notification reordering; refused topology call changing selection; rolled freshness bypass; BOM and pending-bound framing omissions; unparseable address for a saved quote name. |

`mutation-results.tsv` is a compact extraction from this reviewer's full fast-gate JSON: all 101 controls caught,
370.8 seconds, every baseline and restored run green, every source and compiled-class restore byte-identical.
It records the named failing assertions without local classpaths or private workspace locations. These successful
controls did not detect the new counterexamples. The 45 controls from `tools/mutation_controls_session.py` are a
subset of those 101; the older separate M68.1 witness harness was not run in this review.
