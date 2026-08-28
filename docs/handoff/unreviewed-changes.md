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

_Reviewed entries are archived verbatim in
[`completed/unreviewed-changes-2026-08.md`](completed/unreviewed-changes-2026-08.md)._

## ☑ reviewed 2026-08-28 (the second session — the review F2 came from) · `bc36c53` (entry written as `9d5f1a2` before the push rebase) · docs: F2 fixed by stating the starter-relative link rule; F1 left for the author

**Verdict.** Accepted — the right option, for the reason given: a snapshot whose parity cannot be checked (F1)
must not acquire intentional differences, or drift becomes indistinguishable from intent. The section is where a
reader following the dead link will find it (the README's review table sits above it), and the `mkdocs --strict`
blind spot it records is real — `docs/specs/` is outside the built site; ten stale links between specs were
repaired by hand on 2026-08-27 for the same reason. Re-ran the snapshot link check after the pull: the two
`../../CLAUDE.md` links are the only unresolved ones and are now explained. Item 1 (browse vs compare) is put
to the owner in the session log rather than decided here; item 2 (F1) stays open for whoever holds the starter.


**What.** One section added to `docs/specs/mongoose-bootstrap-artefacts/README.md` naming that links
inside `specs/` are STARTER-relative and do not resolve in the snapshot. Responds to F2 of
`review_mongoose_bootstrap_review_resolution.txt`.

**Why this option and not the other.** That review offered two fixes — adapt the paths, or state the
rule. They are not equal. Adapting would make every future refresh re-apply the same edits, and would put
intentional differences into a copy **whose parity with the source already cannot be checked** (that is
the same review's F1). Divergence indistinguishable from drift is worse than a link one sentence
explains, so the snapshot stays byte-faithful to the starter and the README carries the explanation.

Also recorded there, because it explains how two dead links passed every gate: `mkdocs build --strict`
cannot catch this class at all — `docs/specs/` is not part of the built site, so the link checker never
sees that directory. They were found by reading.

**Files.** `docs/specs/mongoose-bootstrap-artefacts/README.md` (+1 section).

**Verified.** Both links confirmed dead independently before fixing — `../../CLAUDE.md` from `specs/`
resolves to nothing; the file is at `ai/CLAUDE.md`. mkdocs strict still passes; four-term sweep clean;
1069 green.

**What the reviewer must still check.**
1. **Whether the owner prefers the other option.** If the snapshot is meant to be browsed standalone,
   adapt the paths instead and delete this section. Both are defensible; only the author knows whether
   this copy exists to be read or only to be compared.
2. **F1 is NOT fixed, and I cannot fix it.** The README's own update rule requires the source revision
   and date to be recorded; none is anywhere in the directory. That needs the starter, which is not on
   this machine. Until it is recorded, "the copy matches the source" is unverifiable by anyone —
   including the author, later.
