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

## ☐ `6f45fe4^..HEAD` · 2026-09-03 · review-residue closure, resolver restructure, new gates

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
  counts removed.

**Verified.** `mvn -q test` green · `python3 tools/test_tools.py` 24/24 · **round-48 XML byte-identical
after every resolver change** · sweep clean.

**What the reviewer must still check.** Whether `Resolution` is the right seam or whether resolution
belongs upstream in the build toolchain entirely (see `spec-component-catalogue.md` — the owner has
proposed moving it); the id-escalation ladder's fourth level is a guaranteed-unique fallback that
produces ugly names and has no test; and M49's evidence package remains unbuilt.

_No further entries awaiting review._

---

_Reviewed entries are retired to [`completed/unreviewed-changes-2026-08.md`](completed/unreviewed-changes-2026-08.md)._
