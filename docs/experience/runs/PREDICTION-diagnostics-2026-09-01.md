# PREDICTION — do the 1.0.65 compiler diagnostics help an LLM author a Fluxtion app?

**Written before either loop was run.** Committed first so the scoring cannot be generous afterwards.
**Date:** 2026-09-01 · **Compiler:** builder 1.0.65 (released), backend deployed
**Bootstrap resources:** UNCHANGED — `docs/claude.txt` and the golden path as published today. UC1–UC4
and `life-of-an-event.md` (fluxtion#29) are deliberately **not** in the resources for this run.

## Why unchanged resources

The diagnostics' claim is that they teach **unaided** — `rule`, `why`, `suggestedFix` with imports. A run
against today's resources measures exactly that, and it is the only chance to measure it. Adding the
documents first makes "the diagnostic worked" and "the doc told them" permanently inseparable. Docs go in
after, and the delta is their measured value.

## The contamination, stated up front

**I am not a blind subject.** This session has spent days learning these idioms — `@OnBatchEnd`, the
`final`-means-constructor-mapped rule, `@NoTriggerReference`, the two-phase model. An error rate measured
on me is worthless.

So this run measures a **different and still-answerable** question: *given code of the shape the published
canon would produce, do the diagnostics fire, are they actionable, and do they name the right repair?*
That is a property of the diagnostics, not of my ignorance.

**The method:** author the app deliberately in canon-guided shape — write what the three published
sources would lead an author to write — rather than in the shape I now know is right. I am simulating the
uncontaminated author's **code**, not their mind, and the difference matters when reading the result.

**What this run therefore CANNOT answer:** whether a fresh LLM would be rescued by the diagnostics. That
needs a session that has not read this one.

## Loop 1 — `tools/bench/loop-bench.py --stub --launch`

| # | Prediction | Confidence |
|---|---|---|
| P1 | It passes all steps against the stub. It was green when merged 2026-08-25 and nothing since touched the REST verbs it drives. | high |
| **P2** | **It shows ZERO signal about compiler diagnostics, because it never invokes the compiler.** It plays an agent driving the *analyser* over REST — export a log and a GraphML, open them, ask `context`/`coverage`/`topology`. The new diagnostics cannot appear anywhere in it. | **very high** |

P2 is the one worth recording. If it holds, the honest conclusion is that loop-bench is the right harness
for the *dev loop* and the wrong one for this question, and running it proves the loop still closes rather
than telling us anything about diagnostics.

## Loop 2 — author a small Fluxtion app, canon-guided

A ~10-node warehouse graph, deliberately planted with one instance of each error class — two that
diagnostics should catch, two that they structurally cannot.

| # | Planted | Prediction | Confidence |
|---|---|---|---|
| P3 | a `final` field holding derived local state (a counter map) | **FLX-1009 fires** and names the field | high |
| P4 | a constructor that does not accept every mapped `final` field | **FLX-1001 fires** | high |
| P5 | audit-capable nodes with no `addEventAudit` | **FLX-1008 fires** as a WARN | high |
| P6 | a plain field reference intended as data-only | **NOTHING fires.** It silently becomes a trigger parent. Compiles, runs, wrong. | high |
| P7 | one `@OnTrigger` written as several per-parent handlers | **NOTHING fires.** Defensible structure, worse than the idiom. | high |

And on the quality of what does fire:

| # | Prediction | Confidence |
|---|---|---|
| P8 | Each firing diagnostic's `suggestedFix` names a repair specific enough to act on **without consulting another document**, including the annotation import. | medium-high |
| P9 | The `documentationUrl` on every emitted diagnostic **resolves** (200), now that the constant points at telaminai.github.io. | high |
| P10 | Total diagnostics fired across the build: **3 codes** (1001 or 1009, plus 1008). Not more — because the remaining planted errors have no code that could see them. | medium |

## The headline prediction, and the one I most want falsified

> **P11 — the diagnostics will close structural rejection almost entirely and move idiom errors not at
> all.** Every planted error that compiles and runs will survive the build green.

If P11 is wrong — if `suggestedFix` teaches something that stops an idiom error the compiler never
rejected — that is the interesting result, and it means the diagnostics are worth more than the tracker's
UC4 thesis credits them with.

**Prior evidence for P11, and it is why the prediction is confident.** UC4 records that over one real
twelve-node graph, *every* mistake worth recording was a case where the structure was defensible and the
framework had a better construct — **none would have been caught by a diagnostic**. This session is a
second instance of the same graph size and produced the same split. P11 predicts a third.

## Scoring

Each prediction gets ✓ / ✗ / UNTESTED in `ASSESSMENT-diagnostics-2026-09-01.md`, with the evidence
inline. Quantitative misses get a note on direction, because every quantitative miss in this project's
history has been in the pessimistic direction and that is now a fact about the estimator rather than
about any round.
