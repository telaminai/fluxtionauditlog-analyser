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

## ☐ `f645cce^..3c39c6c` (32 commits) · 2026-09-03 · authoring modes, the catalogue resolver, and the runtime benchmark

**Review brief:** [`prompt_review_authoring_modes.md`](prompt_review_authoring_modes.md) — the
authoring programme is new; a reviewer who last saw M40/M42/M43 has not met any of it.

**What & why.** One long session covering two separable bodies of work, both committed straight to
`main` with no brief → report → review cycle. **They are independent and can be reviewed separately.**

**A · runtime benchmark (rounds 54).** The first runtime measurement in this project — every prior round
measured tokens. Confirms zero-allocation dispatch (500M events under EpsilonGC, 0 collections),
decomposes Fluxtion's cost against hand-written Java, and files **UP-FLX-45** (the wall clock is 55% of
dispatch by default; replay mode avoids it). Deliverables: `docs/experience/runs/round-54/NOTES.md`,
`BLOG-NUMBERS.md`, and a published artifact.

**B · authoring modes (rounds 55–57 + specs).** Names four authoring modes; shows the bean-file wiring is
a **constraint solve, not a model task** (`tools/bean-resolver.py` reproduces the measured-optimal
selection and wiring, builds green, produces byte-identical alerts, at zero token cost); shows selection
is memoisable via a `Fluxtion-Convention` manifest field plus a site profile; and adds
`tools/fluxtion-harness.py`, which derives the mode from the catalogue rather than asking.

**C · analyser code — the only `src/` change.** `ExpectationScorer` + `ScoreCommand`
(`analyser.score`): headless comparison of two audit logs on business outcomes, built on the shipped
`YamlAuditReader`/`RecordParser`. Motivated by **five scoring defects in this project's experiment
history, three in one session, every one erring toward agreeing with its author**. 11 tests, one per
guard, each reproducing a defect that actually occurred.

**Files.** `src/main/java/.../analyser/score/` (2 new), `src/test/java/.../analyser/score/` (1 new),
`CHANGELOG.md`, `tools/bean-resolver.py`, `tools/fluxtion-harness.py`, `docs/specs/spec-authoring-modes.md`,
`spec-authoring-mode-selector.md`, `spec-authoring-session-walkthrough.md`,
`spec-minimal-authoring-instructions.md`, `spec-component-catalogue.md` (V-A/V-B),
`docs/proposals/upstream-asks.md` (UP-FLX-45, UP-FLX-46), `docs/proposals/assessment-playground-ai-prompts.md`,
`docs/experience/runs/round-5[3-7]/`.

**Verified.** Full `mvn -q -o test` green at every commit. Rule-1 sweep clean throughout. Resolver output
verified end to end: `mvn process-classes` green, 222 dirty flags in the generated processor, alerts
byte-identical to `round-48/expected.alerts`. Harness verified on four scenarios. Scorer: 11 tests
including an end-to-end run through the shipped reader on a committed conformance fixture. UP-FLX-46
reproduced in 12 lines and **lodged upstream** as
[telaminai/fluxtion#31](https://github.com/telaminai/fluxtion/issues/31).

**What the reviewer MUST still check.**

1. **The specs are unreviewed and argue for a strategy.** `spec-authoring-mode-selector.md` claims the
   analyser is *required* for verification on a capacity argument (a 10k-event log is 5.8× Haiku's
   context). If that framing is wrong, say so — it is the most consequential claim written here.
2. **`ExpectationScorer`'s figure extraction is heuristic.** It reads two shapes — the natural
   `instanceId.key` form and a tagged `stage`/`value` convention — and picks by whether the tag keys are
   present. That rule is asserted, not derived from the format spec; check it against
   `src/test/resources/conformance/` and the published format spec.
3. **Round-49's `expected.txt` is not in the analyser's record format** (18 blocks, zero `---`
   separators) — recorded in `round-49/FORMAT-NOTE.md`, **not repaired**. Confirm that deferral is right.
4. **Nothing in `docs/site/` was touched**, so no MkDocs build was run. Confirm none of this needs to
   reach the published docs yet.
5. **No Swing/UI change**, so rule 4's build-and-run gate was not exercised for this range.
6. **The resolver and harness are Python in `tools/`, untested by `mvn test`.** They have no test suite;
   their evidence is the end-to-end verification above. Decide whether that is sufficient for tools that
   now carry an argument about product strategy.
---


---

_Reviewed entries are retired to [`completed/unreviewed-changes-2026-08.md`](completed/unreviewed-changes-2026-08.md)._
