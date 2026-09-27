# Independent review and fix results

Reviewed `0eda3575`; the owner then authorised fixes on the PR branch. Predictions and historical
counterexamples were committed in `d19d234b` before changing production code.

## Review probes

`Pr33ReviewProbe.java` is a historical counterexample probe for **0eda3575**. It intentionally asserts
the defects, rather than being a passing acceptance test of the fix. Compile it against that tree's
`target/classes`, `target/test-classes` and Maven test dependency classpath. Run its main class in
package `telamin.fluxtion.audit.analyser.analyser.ui` with `headless <isolated-scratch>` or
`display <isolated-scratch>`. The display form needs a real display and `-Djava.awt.headless=false`.
The frame harness sets an isolated home; supply an isolated `-Duser.home` for the headless form too.
Neither form uses a provider, key or model session.

The two committed output files are from the reviewed head. They establish:

- nested outside links were admitted by both guards, and the real screenshot action wrote outside;
- direct/chained outside links were refused; internal, project-root and case-insensitive aliases worked;
- a subsequent request rechecked a swapped directory, but an already-returned lexical path followed it;
- global-tier saves, empty-bin clearing, all-category exports and import exclusion worked;
- two profiles in one root had separate bins, and no-project reports used the empty key;
- the real dialog actions worked when invoked through their Swing buttons;
- string `true` listed rather than restored, and a restore with sections silently ignored the sections.

The first native Robot attempt did not open Delete. This was not counted as a successful mouse-driven
test. The successful attempt used `doClick` on the actual widget, including real modal dialogs. Image
inspection then exposed the clipped toolbar. The permanent frame regression requires fully visible
rectangles **before** invoking buttons, and reintroducing FlowLayout fails that assertion.

## Permanent regressions and controls

Added 11 tests: three ExportGuard tests, one project-exchange alias test, two ReportBin persistence/share
tests, one schema test, and four real-frame tests. Existing frame-suite lists are unchanged: the new
display tests are in the already-registered ReportRecoverableDeleteFrameTest.

The seven new controls plus the five retained controls all passed green → named assertion failure →
byte-identical restore → green, in **40.9 seconds** of control execution. The whole source tree's
SHA-256 mapping also matched before/after. Every control's class-snapshot restore flag was true.
See `controls-output.txt` and `controls-summary.json`; the latter omits local commands/classpaths but
retains source hashes, per-suite counts, test names and restore results. Only these 12 controls ran;
the full mutation gate was not run locally.

## Gates and misses

Counts are total / failures / errors / skips.

- Original full run: **2487 / 0 / 1 / 120**, 334 reports, no orphans. The error was a null pane-text
  read in SourcePanelFreshnessTest's existing polling loop. The class retry was **23 / 0 / 0 / 0**;
  the full retry was **2487 / 0 / 0 / 120**.
- Original display: **120 / 0 / 0 / 0**, 24 suites.
- Initial five controls: **5 caught**, 16.5 seconds, exact restores.
- First targeted development run: **43 / 2 / 8 / 0**. An immutable map was incorrectly passed to the
  schema builder that adds a description; that was fixed. Canonical paths also changed the expected
  spelling of temporary paths on macOS. The rerun passed.
- Corrected report frame class: **5 / 0 / 0 / 0**.
- First corrected full run: **2498 / 2 / 0 / 124**. The two remaining failures were external-series
  tests expecting an alias spelling rather than the canonical path. Only their path expectations
  changed; later-file, series ordering, row counts and plotted data assertions remain.
- Final `mvn -q clean package`: **2498 / 0 / 0 / 124**, 334 source-mapped reports, **no orphans**.
- First corrected display: **124 / 1 / 0 / 0**. NamedGraphAndMenuSpotlightFrameTest's second-menu
  assertion saw no spotlight instead of the AI menu. Isolated retry: **7 / 0 / 0 / 0**.
- Final complete display retry: **124 / 0 / 0 / 0**, 24 suites.
- Preflight: **24 frame suites, 206 anchors**. Python harness tests: **5 passed**.
- Strict MkDocs, whitespace checks and the tracked/additions public-data sweep passed.

The original CI run `36278791716` was read directly: build **2487 / 0 / 0 / 120**, display
**120 / 0 / 0 / 0**, and collector **199 controls caught exactly once across four shards**. All jobs
succeeded. Those results belong to the original head. New-head CI is not inferred from them.

The report records the final display retry and the refreshed docs image. No mutation was restored
with checkout; every restore used the gate's byte snapshot, checked independently with SHA-256.

## Screenshot

Refreshed only `docs/site/assets/reports-dark.png` from the corrected built jar, using the existing
`capture-docs.py` report fixture and native window-capture functions under a fresh isolated home.
The demo inputs were staged under neutral names. The image was opened and read: all four report
actions are visible; no private names or paths appear. It is 3360 × 2100 pixels. No other screenshot was changed.

Final focused docs tests (SpecLinksResolveTest and MenuDocumentationTest): 5 / 0 / 0 / 0. Strict site build and diff check passed after the screenshot refresh.
