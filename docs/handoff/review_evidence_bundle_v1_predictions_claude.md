# Predictions — evidence bundle v1 review, before running anything

Written 2026-09-28 against `dff81924..e0e67b6d`, after reading only the commit list, the file sizes and the
review brief. Nothing has been built or run. Scored at the end of the review.

| # | prediction | why |
|---|---|---|
| P1 | **A hostile zip gets past `--unpack` in at least one of: duplicate `manifest.json`, duplicate member in the manifest, or case-only-differing names.** | The brief says the first two have no test, and the author disclosed that. Untested branches in a 267-line format class that must resist adversarial input is the highest-probability defect in the range. |
| P2 | **`--unpack` writes something before verification completes**, or streams members to disk while checking them. | The natural way to write an unpacker is entry-by-entry. Verifying first needs a deliberate two-pass design, and the brief asks the question, which suggests it is worth asking. |
| P3 | **No size limit anywhere** — a zip bomb or a declared-huge member is accepted. | v1 scope pressure; nothing in the commit titles suggests a quota. Low severity for a hand-carried file, real for anything that arrives over a network later. |
| P4 | **The path-shape check on profile values produces a false positive on ordinary prose.** A report narrative containing something like `C:\Users` or `/tmp/x` or a bare `~name` will refuse a legitimate capture. | The brief hints at it. A regex over free text written by a human or an LLM is a classifier, and classifiers on prose have false positives. This will bite a real user before any attacker sees it. |
| P5 | **`project.unsavedEdits` is hand-placed state, not a projection.** | It is read from `ProjectSession.isDirty()` in `MainFrame.context`, which is the adapter computing a fact in the adapter. Rule 9 says the session owns decisions. This is the same shape as the `context` identity branches I flagged on PR #58. |
| P6 | **The driver contains at least one vacuous check** — always true, or asserting on a field that is present rather than on its value. | 20 checks written by the author of the thing under test, in a 456-line script, with no adversary. I have written vacuous checks three times this week; the base rate is high. |
| P7 | **RESULTS.md overstates at least one claim** — most likely "byte-identical", "none unrun", or a timing. | Not dishonesty; the usual failure is a claim measured once under favourable conditions and then stated flatly. |
| P8 | **The capture transaction's moved-generation path is never exercised**, so the deletion on refusal is unproven. | The brief says it is code only. Untested cleanup paths are where "nothing was written" claims quietly fail. |
| P9 | **The "continuing walk" rule misses at least one case** — a rename, or a definition changed between plays. | M69's own R6 round found exactly this class: a rule stated over name plus generation, with the definition able to move underneath it. |
| P10 | **The skills and the driver disagree somewhere**, because the driver re-implements the skill rather than executing it. | A python driver cannot follow prose; it can only encode one reading of it. The gap between the two is where a real agent will fall over. |

**What would show my priors wrong:** if the format class verifies fully before writing anything, refuses
duplicates by construction rather than by check, and the driver's checks assert values rather than presence,
then the work is stronger than the brief's own disclosure suggests and my P1/P2/P6 are simply wrong.
