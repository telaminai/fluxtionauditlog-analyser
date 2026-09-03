# Un-reviewed changes on `main` — pending review

A running ledger of changes **committed directly to `main` without** the usual brief → report → review
cycle. These are small, ad-hoc fixes made by a session working primarily in **another repo** (a downstream
consumer of the analyser) that hit an analyser-side bug and fixed it in passing, rather than a delegated
work block.

**For the reviewing session:** on your next pull, review each `☐` entry below — read the commit, sanity
the change against the codebase and the repo rules (CLAUDE.md), run `mvn test`, and **verify anything the
entry says was not verified** (Swing UI changes are not unit-tested — build and run the jar). Then tick it
`☑ reviewed <date>` with a one-line verdict, and file any follow-up as a normal review. Fully-reviewed
entries move to `completed/` when this file is next tidied.

Every entry must carry: commit SHA, what & why, files, what was verified, and **what the reviewer must
still check**.


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

_No further entries awaiting review._

---

_Reviewed entries are retired to [`completed/unreviewed-changes-2026-08.md`](completed/unreviewed-changes-2026-08.md)._
