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

_No further entries awaiting review._

---

_Reviewed entries are retired to [`completed/unreviewed-changes-2026-08.md`](completed/unreviewed-changes-2026-08.md)._
