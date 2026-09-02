# Round 41 — the unannounced change: stored dispatch goes stale, derived dispatch cannot

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `f579531` before either arm launched.
One variable against round 40: **one sentence removed from the release note.** Same jars, same
scoring scenario, same starting solutions (taken fresh from round 39, because round 40's arms know
about the multiplier and are contaminated).

## Result

| | Fluxtion | plain Java |
|---|---|---|
| **score** | **41/41** | **30/41** (realigned) |
| new failures from the unannounced change | **0** | **the two `spreadMult` events, plus knock-on** |
| carried over from round 39 | 0 | 2 events |
| **its own build** | green | **green** |
| **its own tests** | pass | **4 passed, 0 failed** |
| lines changed for the upgrade | **4 XML** | 14 Java + 2 test |
| turns | 94 | 83 |
| output tokens | 11,091 | 4,004 |

## What happened

`PxRate` gained `onConfig` in 2.0. The release note did not mention it. It was never hidden —
`javap` shows it in plain sight — it simply was not announced.

**The plain-Java arm dropped both `CONFIG,spreadMult` events entirely**, emitting no records for
either, and then carried an un-multiplied `pricing.spread` forward into every later event. Its own
report explains exactly why, and the sentence is worth keeping:

> *"**No breaking changes**: All existing constructors and methods unchanged; 1.0 integrations
> compile without modification."*

That is **true and irrelevant**. Nothing was removed; a method was *added*. It ran `javap` against
`Skew`, `Spread` and `Adjusted` — the classes the note pointed at — and not against `PxRate`. Its
stored `handleConfig` routing had no reason to be revisited, so it was not.

**The Fluxtion arm scored 41/41 without knowing the change existed.** It happened to run `javap` on
`PxRate` and recorded *"still has onRate and onConfig methods"*, then concluded in its own summary:
*"No changes to existing classes (Adjusted, PxRate, Spread) — their signatures remain identical."*
**It was wrong about that, and it did not matter.** The generator re-read the jar, saw a handler for
`Config`, and routed the event. Nobody had to notice.

That is the whole finding, and it is narrower and sharper than "Fluxtion is better":

> **A stored dispatch table is a fact about the jars, recorded once. When the jars change, it is
> stale and nothing says so. A derived dispatch table cannot be stale, because it is not stored.**

## The cost result inverts, and that is the honest headline

**The plain-Java arm was CHEAPER this round** — 83 turns against 94, 4,004 output tokens against
11,091. It was cheaper *because it did less work*: it missed the change. Fluxtion additionally
burned turns on a self-inflicted fat-jar detour (23 lines of `pom.xml`, three attempts, one failed
build) that had nothing to do with the upgrade; the upgrade itself was 4 lines of XML.

**Being wrong is cheap.** Any cost comparison that does not condition on correctness is measuring
the wrong thing, and this round is the clean demonstration. It also retires the cost framing from
rounds 39–40: at n=1, with a 2× threshold, and with a result that inverts when the arm fails, token
cost is not the metric this series should lead with. **Correctness and silent-failure exposure are.**

## Predictions scored: 4 of 5 (one moot)

| # | Predicted | Actual | |
|---|---|---|---|
| R1 | Fluxtion picks it up with no consumer action | yes — and did not know it was new | ✓ |
| R2 | the plain-Java arm misses it; builds green, tests pass | **exactly that** | ✓ |
| R3 | if it catches it, it will be by re-running `javap` over the whole jar | it did not catch it | moot |
| R4 | Fluxtion ≤5 lines, plain Java ~15–20 | 4 vs 14 — plain Java came in **under** | ✗ (narrowly) |
| R5 | neither build nor either arm's own tests fail | held; Fluxtion's one failed `mvn` was the fat-jar detour, not the drop-in | ✓ |

R4 missing low is the **third consecutive under-prediction of the plain-Java arm** in this series.

## What this does NOT show, including a confound found during the round

- **n=1**, one cell per arm.
- **I removed the sentence.** A real supplier's release note is incomplete for reasons of its own,
  not because someone deleted a line to make a point. What generalises is the *mechanism* — stored
  versus derived dispatch — not how often suppliers under-document.
- **The task text specifies Fluxtion's execution model**, and this is the significant one. `TASK.md`
  requires that a stage recompute *"when, and only when, something it depends on has changed"*, that
  every class run *"exactly once"*, and that a `false` return *"stops that path"*. That is dirty
  propagation, topological ordering and conditional suppression — stated as requirements. The
  plain-Java arm's dirty flags and `computedStages` set are it **implementing my specification**,
  not independently arriving at the framework's design.

  Worse: **for this fixture none of it is needed for correct values.** Every stage is a pure function
  of its parents, so recomputing all 20 nodes in topological order after every event would produce
  identical numbers with no dirty tracking at all. Only `C2` (which stages ran) and `C5` (exactly
  once) would fail — and those two criteria score adherence to the model I specified rather than
  correctness of the answer.

  So rounds 39–41 measure **"reimplement this execution model"**, not **"solve this problem"**, and
  that plausibly accounts for much of the line-count gap. Raised by the owner, not found by me.

## Two follow-ups this leaves

1. **The open brief** — five jars, "produce the correct value for every stage after each event",
   scored on **values only**, no constraint on what runs or how often. If the plain-Java arm scores
   full marks in ~80 simple lines, the framework's advantage on *this problem shape* is far smaller
   than rounds 39–41 imply.
2. **A fixture where selective dispatch is load-bearing** — stages with side effects, or a detector
   that must not fire — so the model earns its place instead of being assumed into the spec.

Also pending: **one bean per subsystem** (`docs/experience/runs/round-42/`). The consumer should
declare five beans, not twenty; each subsystem publishes one root class that constructs its own
subtree and takes only its external dependencies. The jars are built and the published faces are
right; whether the generator interleaves the subtrees across subsystem boundaries is **not yet
verified**, and per rule 6 it will be checked against the generated dispatch rather than assumed.
