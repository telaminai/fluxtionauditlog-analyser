# Round 40 — both arms upgrade correctly; the difference is cost, and my design was weaker than I claimed

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `7968c87` before either arm launched.
Design: [`DESIGN.md`](DESIGN.md). Each arm started from **its own round-39 solution, unmodified**,
and was handed `pricing-2.0.jar` plus the supplier's release note.

## Result

| | Fluxtion | plain Java |
|---|---|---|
| score | **41/41** | **37/41** |
| **new failures from the upgrade** | **0** | **0** |
| carried over from round 39 | 0 | 4 |
| files changed | 1 | 2 |
| lines changed | **4 XML, 0 Java** | **18 Java + 1 test** |
| `mvn` runs | 7 (0 failed) | 6 (0 failed) |
| wall-clock | 3m 8s | 5m 31s |
| weighted tokens | **2.63M** | **5.37M** (2.04×) |

**The upgrade introduced no new failures in either arm.** Every point the plain-Java arm lost is the
round-39 propagate-on-value-change defect recurring on the same class of event — a `CONFIG` arriving
before any tick, where the recomputed value coincides with the value already held. Those are carried
over and are **not** a round-40 result, exactly as `PREDICTION.md` said they would have to be
separated.

## Q3 was wrong, and it was the point of the round

> Q3: *"The plain-Java arm misses change A, or catches it only by re-running its discovery over the
> new jar. If its event→handler map is built once from a hard-coded list, `CONFIG,spreadMult`
> silently produces nothing."*

It missed nothing. It added `pxRate.onConfig(config)` to its `handleConfig()` path and got both
multiplier events exactly right — its output at those events is identical to the reference. The
stated falsifier fired, and it fired in the direction that weakens the maintenance claim.

## The design defect, owned

**My release note told both arms about `spreadMult`.** Change A was therefore never invisible; I made
it visible in the handout. What this round measured is **the cost of wiring a known change**, not
*would an integrator notice an unannounced one* — which is the experiment `DESIGN.md` described and
this run did not perform.

That is round 34's defect in a new costume: I drew the answer into the material and then measured
whether it could be copied. It is the second time in this series, and the pattern is specific enough
to name — **when I write both the fixture and the handout, the handout leaks the fixture** unless
something forces the two apart.

A release note that omits the feature is not the fix either, because then no arm has reason to adopt
it and the round measures nothing. The honest version needs the change to be **adopted for one stated
reason while altering dispatch for an unstated one** — e.g. a note that mentions only `Skew`, while
`PxRate` quietly begins accepting `CONFIG`. Then "did your engine route the event" is a question the
handout cannot answer. That is round 41 if it is worth running.

## What the round does support

Narrower than the setup promised, and still worth having: **on a binary-compatible upgrade that
changes which stages run and adds a cross-vendor stage, the integration cost differed by roughly
4.5× in lines and 2× in tokens, at equal correctness on the new behaviour.**

The plain-Java arm's own description of where its 18 lines went is the useful detail:

> *"Marked skew dirty when vol completes calculation… Marked skew dirty when adjusted completes
> calculation… Added skew computation block in recomputeStages with proper dependency checks on both
> vol and adjusted"*

Three separate touches to the propagation logic for one new node, each of which has to be right.
Fluxtion re-derived all of it from one bean and did not need the existing beans reordered. Both arms
called the work easy — Fluxtion's answer to *what was hard* was *"Nothing"*, and the plain-Java arm's
was *"Nothing was particularly difficult"* — so the cost difference here is not difficulty, it is
surface area.

## Predictions scored: 3 of 6

| # | Predicted | Actual | |
|---|---|---|---|
| Q1 | Fluxtion ≤5 lines, no Java | 4 lines XML, 0 Java | ✓ |
| Q2 | change A needs no consumer action under Fluxtion | none needed; it said so unprompted | ✓ |
| Q3 | the plain-Java arm misses change A | **it handled it correctly** | ✗ |
| Q4 | the plain-Java arm changes ≥20 lines | 18 non-test | ✗ (narrowly) |
| Q5 | neither build fails on the drop-in | 0 failures either side | ✓ |
| Q6 | cost gap widens beyond round 39's 1.38× | 2.04× — **at** the n=1 threshold, not over it | not claimed |

Q3 and Q4 both missed in the same direction: **I under-predicted the plain-Java arm again.** That is
the estimator error this project recorded four times before round 39, briefly inverted in round 39
(the first optimistic miss), and now back. The stable reading across all of it is that this model's
baseline competence at plain-Java event orchestration is higher than I keep assuming, and the
framework's advantage shows up in **surface area and silent-failure exposure**, not in whether the
task gets done.

## Standing caveats

- **n=1**, both arms, both rounds. 2.04× is at the threshold this series set, so no cost claim.
- **The plain-Java arm started from a broken base and Fluxtion did not.** That asymmetry favours
  Fluxtion's absolute score and is not a finding; the like-for-like comparison is the *new-failure*
  row, which is 0–0.
- **Supplier cost excluded**, as in round 39 — paid once, amortised, integrator never sees it.
