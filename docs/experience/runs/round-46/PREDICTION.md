# PREDICTION — round 46, documented vendors, and the plain arm on a stronger model

**Written and committed BEFORE either arm is launched.** No result seen.

## What round 45 showed, and what it did not

Fluxtion got **every business outcome right**: 3 alerts for 3 breaches, all four stateful counters
exact. The plain-Java arm published **0 alerts for 3 real breaches**, left 8 of 20 nodes never
invoked, and passed all 8 of its own tests.

But neither arm was measured cleanly:

- **The plain arm's arrest logic was correct.** It gated the alert chain on the detector's return and
  kept `streak` outside the gate — the right design. It failed on *bookkeeping*: it maintained ten
  hand-written per-event dispatch lists and its `TICK` list omitted the whole
  marketdata→pricing→liquidity chain, so `exposure` stayed `0.000000` and the detector never fired.
- **Fluxtion was hobbled by the arm and by me.** It declared **34 beans where 5 would do**, hard-coded
  a config whitelist in `Main`, and lost 3 builds — one to the package trap my own toolkit note had
  spelled out.
- **Both arms burned most of their turns reverse-engineering undocumented jars** — 52 and 68 Bash
  calls, almost all `javap` and `mvn`. That is not a property of either approach.

## The two changes

**1. The vendors now ship documentation** (178 lines, `lib/docs/`). Entry point, what each library
consumes, what it publishes, and the non-obvious semantics stated plainly — that anything below
`limitDetector` must not run when it reports `false`, and that `streak` and `ewma` are stateful and
must be advanced exactly once per change. **Both arms get the same semantics.** Only the
"For Fluxtion users" bean snippets are stripped from the plain arm's copies, since they document a
route it is not taking.

This is the largest confound removed so far. A real vendor documents its entry point; mine did not,
so both arms were paying to rediscover it.

**2. The plain arm moves to a stronger model. Fluxtion stays on Haiku 4.5.**

The question is no longer *can a small model wire this up* — round 45 answered that. It is **whether
the plain-Java approach is correct-able at all** on this problem shape, given a better author.

> **CORRECTION, made before any result arrived.** I first wrote that cost comparison was *void* this
> round because the arms run on different models. That was wrong, and the owner is right to reject it.
>
> **Token counts across models are not comparable. The priced cost of a correct result is — and that
> is the commercial question.** The claim a buyer cares about is not "fewer tokens", it is *"this
> approach reaches a correct answer on a cheaper model."* A cross-model comparison is the only
> instrument that can measure that, and refusing to run it would be measuring the wrong thing
> carefully.
>
> So this round reports **cost per correct result**, computed as follows. Let **R** be the price ratio
> of the stronger model to Haiku 4.5 for the same token mix. With `F` = Fluxtion's tokens on Haiku and
> `V` = the plain arm's tokens on the stronger model, the plain route costs more whenever
> **`V × R > F`**, i.e. for any `R > F / V`. Because `F` and `V` have been within ~20% of each other
> all series, the break-even lands near `R ≈ 1` — so **on any realistic price ratio the cheaper-model
> route wins on cost, provided it is correct.** The whole commercial argument therefore rests on
> correctness, not on token efficiency.
>
> Stated in the form that actually sells: **if Fluxtion is right on the cheap model and hand-wiring
> needs the expensive one, the saving is the price ratio. If hand-wiring is wrong even on the
> expensive model, there is no price at which it is a substitute** — and rounds 44 and 45 both ended
> that way.
>
> I will report raw tokens per arm, name the model each ran on, and give the ratio rather than
> asserting prices I cannot verify from here.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| U1 | **The documented entry point removes most of the exploration.** Both arms use far fewer `javap` calls than round 45's 52 and 68. | high |
| U2 | **Fluxtion declares 5 beans, not 34**, because the documentation says the composite is the supported unit. | medium-high |
| U3 | **Fluxtion has zero failed builds.** Both round-45 causes are gone and the docs name the entry point. | medium |
| U4 | **The stronger plain-Java arm publishes exactly 3 alerts and gets every counter right.** The docs state the arrest and the statefulness explicitly; round 45's arm already had the design right and lost to bookkeeping, which a stronger model should not repeat. | medium |
| U5 | **If it still fails, it fails on completeness, not on semantics** — a node or a path left out of a hand-maintained list, as in round 45 — rather than on misunderstanding the detector. | medium-high |
| U6 | **Neither arm's own tests catch its own defect**, if it has one. Round 45: both arms green, one catastrophically wrong. | medium-high |

## Falsifiers

- **If the plain arm is now fully correct**, the round-45 result was a model-capability artifact, not
  a property of the approach, and the honest conclusion is that hand-wiring is viable given a
  competent author and documented vendors. I will say so plainly — it is the most likely way this
  series ends up overstating the framework.
- **If Fluxtion still declares 34 beans**, U2 is wrong and the composite idiom is not discoverable
  even when documented, which is a docs problem worth filing upstream.

## Standing caveats

n=1 per arm. Different models per arm, deliberately. The vendor documentation is written by me, and I
also wrote the libraries — I have tried to state semantics rather than design, but a reader should
discount accordingly.
