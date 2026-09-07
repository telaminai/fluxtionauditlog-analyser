# Round 60 — is there a lever for the dissolution threshold?

**Runtime** Oracle GraalVM 25.0.4+7.1 LTS, `native-image` 25.0.4, macOS/aarch64
**Harness** `tools/bench/latency-kit`, both arms, 100M events after 5M warm, output verified identical
**Question** round 59 left the native+PGO figure bimodal at ~85%. Two things were open: is there a
compiler knob that makes dissolution reliable, and why did dropping the last auditor look like a
regression?

**Short answer to both.** The only lever is the one already documented — `PriorityForceInline`. Six
knobs that promise exactly what is wanted do nothing. And the auditor question dissolves, because the
thing both round 59 samples were measuring is **a nondeterministic build**: the same source, the same
flags and *the same profile file* produce 1.60 ns on one build and 5.66 on the next.

---

## 1. The PGO profile decides it — and my first answer to this was wrong

**Corrected 2026-09-07, after the owner pushed back:** *"I'm still confused why the compiler with
deterministic rules produces different outputs. I feel the input jar is the same, the only difference
is pgo or config."* That is exactly right, and the reason this section originally said otherwise is a
bug in my own harness, not a property of GraalVM.

### 1.1 The harness bug

`cycle4.sh` was supposed to reuse a saved profile when one was supplied. Its guard read

```bash
prof="$D/$name.iprof"
if [ ! -f "$prof" ]; then   # ← this file NEVER exists; the collected pair is $name.g/.h.iprof
```

so every run took the collect branch and **overwrote the profiles I had just copied in.** `base1`'s
profile was written at 07:21; the file the "reuse" build actually consumed was written at 07:28. The
experiment reused nothing. Every conclusion drawn from it is void, and they were the headline ones.

### 1.2 What a correct experiment shows

Profile passed explicitly, no collection anywhere, the profile's SHA verified unchanged across the
build:

| profile | three rebuilds | image size |
|---|---|---|
| the one that produced 1.60 (`base1`) | **1.5987 / 1.6552 / 1.6804** | 10270136, all three |
| the one that produced 5.70 (`aud_a1`) | **5.7102 / 5.6310 / 5.6136** | 10270152, all three |

**The compiler is deterministic in the decision that matters.** Hold the profile and the mode is
reproduced, three times out of three, in both directions, and the image comes out the same size every
time. The images are not byte-identical — the SHAs differ — so layout or ordering is nondeterministic,
but nothing that changes the outcome is.

### 1.3 So what varies is the profile, and it varies a lot

The two profiles above were collected from the *same instrumented image*, running the *same workload*,
minutes apart:

| section | landing profile | missing profile | differ | only in landing | only in missing |
|---|---|---|---|---|---|
| `callCountProfiles` | 7,988 | 7,887 | **1,190** | 162 | 61 |
| `conditionalProfiles` | 6,160 | 6,046 | **1,194** | 158 | 44 |
| `samplingProfiles` | 6 | 8 | 7 | 1 | 3 |

Round 59 said these profiles were "identical on every hot counter". **They are not**, and that claim
came from comparing *section lengths and the four hottest methods*, never the contexts. The four hot
methods do match at 21,000,000 each; over a thousand contexts around them do not.

That is not mysterious either. Collecting a profile means running an instrumented binary, so the
profile is a **measurement**, and measurements of a JVM-shaped startup vary: class initialisation,
deoptimisation, GC and sampling all land differently run to run. It only takes one of those
differences to sit on an inlining decision.

### 1.4 The consequence, which is better than the thing it replaces

**Keep the profile, not just the binary.** A landing profile is a reproducible input: commit it,
rebuild from it, get the result again. A landing binary is one artifact that goes stale the moment the
classes change. `tools/bench/land-native.py` now keeps both, and takes `--profile` to rebuild from a
known-good one with no collection and no lottery.

### 1.5 What survives from the original section

- **The mode is fixed once the image is built.** Each of round 59's 13 images re-measured three times,
  interleaved: `v6` gives 5.34 / 5.46 / 5.43, `v7` gives 1.43 / 1.44 / 1.44. Padding the environment
  from 16 B to 16 KB and varying `-XX:MaxHeapSize` from 128m to 1g move neither.
- **Two failure signatures, told apart by the hand-rolled arm** (it holds no processor, so it says
  which decision was lost):

| | generated | hand-rolled | what failed |
|---|---|---|---|
| directive missing (`nf_base`) | 5.59 | **1.56** | the dispatch chain — arm-specific |
| a missing build, directive present | 5.6 | **1.56** | the dispatch chain, again |
| round 59's `v6`, `v11` | 5.41 | **5.41** | **both arms** — `onTick` too |

- **Round 59 §25.2 stays withdrawn.** "Ten node objects sit at a size threshold, hand-rolled is one
  object so it always lands" is killed by that third row: in those builds the hand-rolled arm does not
  land either, and it has nothing to dissolve.

## 2. Levers: six knobs, none of them work

`native-image --expert-options-all` offers a plausible-looking set. Each was applied to a build with
the directive deliberately removed — the reliably-slow configuration, 5.59 — to see whether any could
recover dissolution on its own. Every row is a full fresh cycle: instrumented image, freshly collected
profile for both arms, final build.

| flag | generated | hand | verdict |
|---|---|---|---|
| *(none — baseline)* | 5.59 | 1.56 | |
| `-H:IPEAMaxForce=8` *(default 2)* | 5.69 | 1.56 | no effect |
| `-H:IPEAVirtualEscapeBoostSingle=64` *(default 12)* | 5.79 | 1.58 | no effect |
| `-H:TuneInlinerExploration=1.0` *(default 0)* | 5.59 | 1.57 | no effect, **+530 KB image** |
| `-H:BaseTargetSpending=600` *(default 120)* | 5.49 | 1.57 | no effect |
| `-H:MaximumInliningSize=1500` + `SmallCompiledLowLevelGraphSize=1650` *(300/330)* | 5.53 | 1.57 | no effect |
| `-H:EscapeAnalysisIterations=6` + `EscapeAnalysisLoopCutoff=80` *(2/20)* | 5.54 | 1.57 | no effect |

All arms produced identical output values; 5.49–5.79 is the measurement's own noise.

**The negative result is the useful one.** These are the options whose names promise precisely what is
wanted — force interprocedural PEA more often, boost the escape cutoff, spend longer exploring
inlining, raise the graph-size ceilings, iterate escape analysis harder — at 4× to 6× their defaults,
and not one moves a processor the priority inliner has decided not to inline.

### 2.1 Nor does forcing more inlining, up to and including all of it

These were run *during* the run of misses described in §2.3. Each is a full fresh cycle with its own
collected profile (the `--profile` reuse path did not exist yet, and the guard that was supposed to
provide it was broken — §1.1), so each row is one sample of a knob against a background that was
producing ~5.6 unaided. None of them rescued it.

| flag | generated | hand |
|---|---|---|
| `PriorityForceInline=…BenchProcessor.*` *(the shipped directive)* | 5.58–5.73 *(4 cycles)* | 1.57 |
| …`,app.Bench.*` — the loop's own class | 5.47 / 5.51 / 5.50 | 1.57 |
| …`,com.benchv.*.*` — the node classes | 5.65 | 1.57 |
| …`,com.telamin.fluxtion.runtime.*.*` — the framework | 5.76 | 1.56 |
| …`,` all four together | 5.76 | 1.57 |
| **`-H:+InlineEverything`** | 5.73 | 1.57 |

**`InlineEverything` not recovering it is the interesting row.** If the loss were only an inlining
decision, that flag should not be able to leave it on the floor. Whatever is bailing is downstream of
inlining, or is not reached by it.

### 2.2 Nor is the nondeterminism something you can pin down

Three candidate sources were checked and none of them is it:

| suspect | test | result |
|---|---|---|
| build parallelism | `-H:NumberOfThreads=1`, twice | 5.61 / 5.51 |
| build parallelism | `-H:NumberOfThreads=4`, twice | 5.47 / 5.60 |
| builder heap (sized from *available* RAM, so it drifts) | read back from each build log | 9.28–9.65 GB, and it does not sort: `base3` at 9.31 GB is fast, `rp_base1` at 9.32 GB is slow |

The kit's inputs were verified unmodified for the whole session (`find -newermt` over `target/classes`
and `target/res` finds nothing). These four builds each had their own freshly collected profile, so by
§1.2 they are four different inputs and prove nothing about determinism — they are listed only as
knobs that did not rescue a run of misses.

**Byte-reproducibility, stated correctly:** two builds from one explicitly supplied profile
(`p7a`/`p7b`, and again `fx_a1..a3`) come out the same size and measure the same, with different
SHA-1s. Ordering or layout is nondeterministic; the optimisation decisions are not.

### 2.3 One thing that is not explained

The outcomes are not independent. `base1..3` landed; every build after them missed, across four
batches, with the identical command and unmodified classes. By §1.2 each of those had its own freshly
collected profile, so the thing that clustered is **profile collection**, not the compiler — but that
only moves the question. Something about the machine's state biased twenty consecutive collections the
same way. The instrumented image, the workload, the iteration counts and the machine were all
unchanged; what differed is that from the second batch onward there was always a background
poll loop running while the profiles were collected. That is a hypothesis with one supporting
coincidence and no test behind it.

**That is recorded as unexplained, not as a mechanism.** It is also the strongest argument on this page
for the rule that follows from it: the build you measured is the only build you know about.

**It is the builds that changed, not the measurements.** Control, run at the end of the slow run: `f1`
gives 1.57, `v7` gives 1.57, `base1` — built fast this morning — gives 1.58, while `v6` still gives
5.50. The machine had drifted about 9% slower over the session (the fast mode reads 1.57 now against
1.43 this morning, which is how the published 1.57 gets reproduced exactly), and 9% is not 3.5×.

## 3. A profile belongs to the image that produced it

Reusing a saved `.iprof` against a **rebuilt** instrumented image is not a shortcut — it is a bad
profile, and a bad profile is worse than none:

| profile | generated | hand |
|---|---|---|
| `v7.iprof` (from a build that measured 1.43), new instrumented image | 8.01 | 3.25 |
| `v7.iprof` again | 7.99 | 3.24 |
| `v6.iprof` (from a build that measured 5.41) | 7.95 | 3.10 |
| `v6.iprof` again | 7.95 | 3.07 |
| *fresh cycle, same flags* | **1.60** | **1.57** |

8.0 is worse than the 6.54 the page records for **no profile at all**, and the image reports
`PGO: user-provided` with no warning. The page's rule was "never reuse a profile across image kinds or
from a different entry point"; it needs a third clause — **never across a rebuild of the instrumented
image**. (Reuse against the *same* instrumented image is legitimate — that is §1's `rp_*` — and it is
how §1 shows the profile is not what carries the mode.)

## 4. The auditor question, and why neither sample meant anything

Round 59 §24 claimed dropping `NodeNameAuditor` helped (3 of 6); the §25 withdrawal claimed it hurt
(0 of 5 against 11 of 13). Today, with the same harness:

- The five surviving "auditor" binaries (`va1`–`va5`, `AutoApp2`, auditor **retained**) measure
  1.51–1.54 generated and 1.50–1.52 hand — **5 of 5 fast**, matching the record for that side.
- A source-level A/B — the generated processor as emitted, against a copy with the auditor field, its
  two per-event calls and its lookup cases removed — gives, over four cycles each: **retained 0 of 4
  fast, dropped 2 of 4**. The dropped side is not even a clean comparison, because its profile came
  from an instrumented image built from the retained source (§3).
- Over the same period the *unmodified* configuration that had measured 1.60/1.67/1.68 an hour earlier
  measured 5.58/5.70/5.71/5.73 — **0 of 4** — with nothing changed.

So the honest position: **the withdrawal stands, but not for the reason it gave.** Neither the 3-of-6
nor the 0-of-5 was measuring the auditor. Both were sampling a build whose outcome varies with nothing
the experiment controlled. §24 and §25 are both superseded by §1 here.

## 5. What this changes on the published page

- The 1.57 claim stands. The qualification is now precise: **it is a property of the profile**, so a
  landing profile is worth keeping and rebuilding from, and the landing *rate* of fresh collections is
  not a stable number (11 of 13 in round 59; 3 and then a long run of misses here).
- **Verify the build you ship** stops being advice and becomes the method. Rebuild until it lands, and
  keep the binary that did — the mode is fixed once built (§1.1).
- Do not spend time on the escape-analysis and inliner knobs (§2), the wider force-inline patterns
  (§2.1), the build's thread count or its heap (§2.2). Thirteen configurations, none of them a lever.
  A day each is what those tables are worth.
- Add the third profile-provenance clause (§3).

## 6. The pragmatic route, and the one thing that broke the run

After 19 consecutive misses on unmodified inputs, the instrumented image was rebuilt — the one input
that had been held constant since `base1`, because every attempt so far had collected its profiles from
the same `mi`. Three cycles from the new one:

| build | generated | hand |
|---|---|---|
| `m2a` | 5.65 | 1.50 |
| `m2b` | **1.66** | 1.53 |
| `m2c` | **1.68** | 1.56 |

The run ended. **Whether the fresh instrumented image caused that is unproven** — one sample, and `m2a`
missed on the same new image — but it is the only input that had not been varied, and it costs one build
to try. That is why `--reinstrument-every` exists rather than being asserted as a fix.

The route itself does not depend on knowing: **a binary reproduces its own mode for ever** (§1.1), so a
build that lands is shippable. `tools/bench/land-native.py` builds, measures, keeps the binary that
landed, and exits non-zero if none did — with the best still kept and named, and every attempt printed
including the discarded ones. Its refusals are one per defect this project has already paid for:
disagreeing arms, the elimination floor, a missing `RESULT` line read as a zero, and silent caps.
