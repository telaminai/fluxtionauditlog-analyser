# Un-reviewed changes — REVIEWED entries archived 2026-09 (from `../unreviewed-changes.md`)

_Moved verbatim on 2026-09-16. Relative links resolve from this directory: the referenced responses and reviews
live beside this file._

## ☑ 2026-09-15 · round-11 response, based on `06d7cb28` · INDEPENDENTLY REVIEWED 2026-09-15, accepted with one correction

Response: [handoff_analyser_round11_response_2026-09-15.txt](handoff_analyser_round11_response_2026-09-15.txt).
Independent review: `review_analyser_round11_response_2026-09-15.txt` (core-baseline `design/handoff/`, off-repo):
R11-1, R11-2 and R11-3 accepted as closed CLASSES (118 key-character clicks per arm across six arms, span
integrity over every offset, mutation controls for each new test). One correction required and folded into the
commit: **F1** — the shared formatter quoted legacy text values (`C:\temp\x` rendered `"C:\\temp\\x"` in the
logical view); values are now quoted only when the parsed entry says the reader quoted them, pinned by a legacy
red-then-green test. F2 (three vacuous assertions) reworded. The broader review's B1 (guided-start fallback said
"never ran"; it now says "never logged" and why) and B3 (the CHANGELOG folded to one Added/Changed/Fixed, stale
fixture count and builder version corrected) are in the same commit. B2 (`verify-m43.py` tautology) is deferred
to after the release. Not checked by either review: a rendered PDF, a native popup, the fresh-model guided tour.

The tokenizer now emits complete key positions with its entries; the detail panel consumes those
positions rather than inferring identity from a displayed word. Logical view, step status and both
report-evidence paths share the same name/value formatter. The six-node sparse M61 workload label
and format-spec wording are corrected. The package-generated reduced POM now matches released pins.

Author checks: three regressions failed before production edits; clean verify **1,385/1,385**;
docs strict build; tool smoke **24/24**; packaged session acceptance **22/22**; packaged regression
probes **4/4**; live template download/open **6/6**. Parser semantic parity held over **18 fixtures,
1,584 record/grammar pairs**, comparing the pre-fix parser with the packaged candidate.

Reviewer must attack the new source-position mapping (CRLF, skipped comments, continuations,
repeated nodes/records, escaped keys), the conservative multiline fallback, and the shared formatter
through actual UI/report consumers. The broad review also leaves the fresh-model guided tour and
modal-dialog/theme checks unverified; a green script is not their sign-off.


---

---

---

## ☑ reviewed 2026-09-03 · `180a1e7`, `4c1c9d8` · review response + M48.11

**Review:** [`review_response_180a1e7_4c1c9d8.txt`](review_response_180a1e7_4c1c9d8.txt) — verified by
execution, not by reading: the reviewer's original probe binaries were replayed unchanged against the
fixed scorer and flipped to the correct verdicts; round-48 XML held byte-identical through all four
resolver fixes; M48.11 reproduced independently (PASS 12/12, exit 0).

**Verdict: accepted, with residue.** Follow-up review `review_analyser_response_4c1c9d8_followup.txt`
then found six further defects, all since fixed (G9/G10, the `--json` cycle path, bean-id collisions,
and the derived fixture whose provenance had become record metadata).

## ☑ reviewed 2026-09-03 · `6f45fe4^..4224196` + spec response · resolver, gates and builder spec

**Review:** [`review_spec_builder_component_resolution.txt`](review_spec_builder_component_resolution.txt)
checked the production spec against the target builder module and accepted its architecture with two
Java-8/CheerpJ blockers and three contract corrections. **Response:**
[`review_response_spec_builder_component_resolution.txt`](review_response_spec_builder_component_resolution.txt)
made Java 8 mandatory, separated the desktop Java parser from the browser surface, pinned SpEL and
Stitch byte semantics, and additionally corrected remote transport to the frozen-DTO/side-band rule.

**What & why.** Closing the residue from four review rounds. Three parts:

- **Resolver restructured, not patched.** A review found a cyclic 2-component candidate beating a
  valid 3-component one on minimality — the valid answer was then discarded downstream as
  UNSATISFIABLE. **Constructibility is now a validity constraint inside `solve()`**, and `solve()`
  returns a typed `Resolution` carrying selection, emission order and id allocation. Renderers consume
  it; none re-validates. The same review found `com.alpha.Node` and `com.beta.Node` in one jar both
  emitting `bundleNode` — ids are now allocated once from full class identity and escalate only as far
  as needed, so single-entry-point catalogues keep their committed output.
- **New gates.** `tools/test_tools.py` (24 checks) **wired into CI**; `TrailingWhitespaceTest`
  hardened — `.txt` scanned, audit logs detected structurally rather than by any mention of
  `eventLogRecord:`, a failure if `git ls-files` returns nothing, and every byte-sensitive fixture
  pinned with a missing file treated as failure rather than a skip.
- **Documentation** reconciled: the fingerprint three-way distinction, orientation §6, volatile line
  counts removed. A new canonical production spec places catalogue generation, typed resolution and a
  dependency-free Spring document parser/writer in the existing `fluxtion-builder` jar. It keeps the
  starter as a downstream editor and makes `#{bean.field}` a typed exposed-field reference rather than
  an opaque scalar.

**Verified.** `mvn -q test` green · `python3 tools/test_tools.py` 24/24 · **round-48 XML byte-identical
after every resolver change** · sweep clean.

**Review residue carried as delivery gates, not present claims.** The prototype id-escalation ladder's
fourth level still produces ugly names and has no direct test; `Fluxtion-Consumes` is still unused in
solving; the Java/JavaScript conformance corpus, dependency-tree proof, CheerpJ smoke and Stitch
byte-parity migration are specified but unbuilt; M49's evidence package remains unbuilt.

_No further entries awaiting review; the entries below are historical._

---

_Earlier reviewed entries: [`unreviewed-changes-2026-08.md`](unreviewed-changes-2026-08.md)._
