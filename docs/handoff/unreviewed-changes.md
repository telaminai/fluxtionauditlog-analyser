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

## ☑ reviewed 2026-08-30 (the analyser reviewer) · `7b2e854^..7a1811f` (15 commits) · the authoring-experience block — measurement, diagnostics, reference set, guided start

**Full report:** [`handoff_30_aug_2026_2_report.txt`](handoff_30_aug_2026_2_report.txt). Read that rather
than this entry; it is a delegated work block, not an ad-hoc fix, and it records five errors of mine that
should weight how you read the rest.

**What.** Nine measured LLM sessions against the released P3 bundle (rounds 04-05, predictions committed
before each run) → five upstream asks filed as telaminai/fluxtion#19-23 → an agreed reference set with a
link bench → skills selected by template (`m19-skills/2`, common + specialisations) → the guided-start
install prompt and tour skill (M19.19). Two specs: `spec-authoring-experience.md` (M19.14a),
`spec-guided-start.md`.

**Why.** The measurement found that most Fluxtion authoring material is already published and the bundle
did not reference it; what is genuinely undocumented is the audit log — this product's own subject.

**Files.** Only three touch `src/main`: `TemplateClient` (loopback origin override), new
`config/ReferenceSet.java`, and a one-line UI banner. Everything else is docs, skills, specs, tests.

**Verified.** 1140 green; four-term sweep clean; the reference-set bench passes 4/4; every jar-level claim
in the report is reproducible with one command against `~/.m2`.

**REVIEW CLOSED — ACCEPTED WITH DECLARED RESIDUALS.** See
[`review_handoff_30_aug_2026_2.txt`](review_handoff_30_aug_2026_2.txt) and the point-by-point
[`response_review_30_aug_2026_2.txt`](response_review_30_aug_2026_2.txt). Fixes are in `f23b5c9`,
`2a4960a`, `b83459e`, `de9d17b` and `8d5591d`. The reviewer accepted the final corrective pass; the four
honestly unverified items remain below and the playground consumer work is tracked as M19.21.

**F1 corrected a claim that had reached two public issues** — the audit model conflated registration,
invocation tracing and value logging, and overstated what an untraced record proves. The reviewer also ran
the real cold JBang install and found an undocumented trust prompt that self-cancels.

**Closed by the reviewer's own verification:** the Maven suite, `mkdocs build --strict`, the reference-link
bench, diff/sweep, the three jar-level claims, and the Swing origin banner in a built jar.

**Residuals for a next reviewer** (from the response, not re-listing what was closed):
- the guided-start prompt run end to end by a fresh external client — the held-out evidence D-G5 asks for;
- `AGENTS.md` auto-loading under Codex;
- the human confirmation flow for `ReferenceSet`, still deliberately unwired;
- **real playground consumption of `m19-skills/2`** — F3 is only partly closed (C1) and v2 is marked DRAFT
  until a generator selects, substitutes and passes the fail-on-`TODO` gate.

---

## ☑ reviewed 2026-08-30 (the independent session) · `c6c7235` (the ledger's `1707087` rotted in a rebase — the fourth SHA this has happened to; push reports promptly) · correct the M19 tutorial and integrate generated screenshots

**Verdict.** Correct, and the honesty is the best part: the graph step is explicitly downgraded to
illustrative (DEMO asset, labelled as such IN the page) rather than pretending the bundle has a numeric
key. All four reviewer checks done. **(a)** project → GraphML → log as three actions is right against
M35/M40 semantics, and shot 1's own status bar shows the session-boundary wording. **(b)** the
fixed-snapshot/Follow distinction is stated and Follow is visibly greyed in the no-log shot. **(c)** all
seven images read at full resolution by this review: the four real-bundle shots are internally consistent
(23 records, 5 PriceEvent, fits-this-log 2/2, canonical skills provenance, the honest key-file wording)
and every visible path is neutral `/tmp/fluxtion-tutorial` / `com.example.myapp`; the three reused
isolated-DEMO shots fit the journey and two of them were read by this reviewer when they were made.
**(d)** the numeric-log finding REPRODUCES from shot 3 alone — `price=195.3` sits inside the
`receivedEvent` toString, not as a top-level key, so it is text, not graphable (the ONBOARDING rule);
the producer-side fix stays open cross-repo as recorded. One judgement noted: the generated source shot
carries the framework's `@author` attribution of the owner's own public name — accepted, it is not a
venue/account name. Gates re-run: 1144 green, mkdocs strict, sweep clean.

**What.** Rewrites the playground tutorial against the released catalogue/lifecycle and actual analyser
session semantics; integrates four real-bundle screenshots from `9d322ed` plus three existing screenshots
generated by `tools/capture-docs.py` under its isolated home; amends the spec after the export-beat reality
changed the old import/Follow story; marks the independently-landed cross-links complete; and records the
remaining producer and capture findings in `review_m19_tutorial_and_screenshots.txt`.

**Why.** The draft told readers to open a project and log but not the GraphML, implied a fixed exported
YAML could demonstrate Follow, implied two complete logs could be viewed together, and did not cover the
assistant evidence loop. Reading the real evidence log found a deeper issue: it has five typed business
cycles and coverage 1.0 but no numeric node-log value, so the promised graph step is not executable.

**Files.** Tutorial, M19 spec/tracker, changelog and the new handoff review. The four
real-bundle screenshot binaries arrived independently in `9d322ed`; this commit places them in the
corrected journey and reuses three visually inspected isolated-DEMO assets for graph and AI setup.

**Verified.** `mkdocs build --strict`, focused `SpecLinksResolveTest`, Python compile of the mandated
capture harness, diff check and the tracked-file four-term sweep pass. All seven referenced images were
opened at original resolution and read before use. The Browser integration reported no connected browser,
so no live playground/terminal/IDE image was fabricated.

**What the reviewer must still check.** Challenge the explicit project → GraphML → log order against the
desktop UI and the fixed-export/Follow distinction. Confirm the four real-bundle captures and three
isolated-DEMO figures are appropriate in one journey. On the producer side, reproduce the numeric-log
finding and add an actual numeric node-log field before restoring the graph instruction. Four original
spec captures remain: playground Download, terminal lifecycle, an Explain answer and IDE edit.

---

## ☑ reviewed 2026-08-30 (the independent session) · `546b2e7` (ledger's `7db161c` also rotted) · make tutorial screenshot staging fail closed

**Verdict.** Correct and stronger than the guard it replaced: the exact-path pin subsumes the old
inside-$HOME refusal and sits BEFORE the `rm -rf` (guard line ~41, destruction line 49, verified in the
live script). The fixed `/tmp/fluxtion-tutorial` policy is right — it is the one path capture-docs.py
reads, it is neutral by construction, and the exact string compare fails closed on any variant including
a trailing slash. The stop-failure path leaves its evidence: output redirects to `$DEST/stop.log` before
the fatal exit, and the error names that file. One nit, not blocking: the script's HEADER comment
(constraint 1) still describes the replaced mechanism — "refuses a destination inside $HOME" — rather
than the exact-path pin the code now enforces; align the prose next touch. A full staging run was not
repeated here either (needs a Fluxtion key and the neighbouring web checkout, as the entry says).

**What.** Constrains the staging script's recursively removed destination to the exact neutral path read
by `capture-docs.py`, and makes a failed `stop-server.sh` fatal instead of hiding it with `|| true`.

**Why.** `fadfa5b` correctly supplied the missing exact-template staging harness, but its arbitrary first
argument flowed into `rm -rf`; `/`, `/tmp` or another broad path was not refused. Masking clean-stop
failure could also leave the Mongoose process and registry entry behind while allowing a screenshot run
to appear valid.

**Files.** `tools/stage-tutorial-bundle.sh`; disposition recorded in
`review_m19_tutorial_and_screenshots.txt`.

**Verified.** `bash -n`, Python compile of `capture-docs.py`, strict docs, focused link test, diff check
and privacy sweep pass. The destructive guard is exercised without deleting anything by passing a
non-canonical destination and requiring a refusal before the web checkout check.

**What the reviewer must still check.** Challenge the fixed `/tmp/fluxtion-tutorial` policy and confirm
the clean-stop failure path leaves enough evidence in `stop.log`. A full staging run was not repeated;
it deliberately needs a Fluxtion key for the one generation step and executes the neighbouring web
template generator, outside this analyser-only change.

---

Eleven earlier reviewed entries are archived verbatim in
[`completed/unreviewed-changes-2026-08.md`](completed/unreviewed-changes-2026-08.md), together with
their review detail files.
