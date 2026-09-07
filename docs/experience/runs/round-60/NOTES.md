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

## 1. The mode is decided by the build, not by the source and not by the profile

This is the finding that reframes everything else. `base1` measured **1.60**. Its profile pair was kept
and fed back to two further builds — same instrumented image, same classes, same flags, nothing else
changed:

| build | profile | generated | hand |
|---|---|---|---|
| `base1` | freshly collected | **1.60** | 1.57 |
| `base2` | freshly collected | **1.67** | 1.57 |
| `base3` | freshly collected | **1.68** | 1.59 |
| `rp_base1` | **`base1`'s own profile, reused** | 6.66 | 1.59 |
| `rp_base1b` | **`base1`'s own profile, reused** | 5.66 | 1.56 |
| `rp_a1` / `rp_a1b` | `aud_a1`'s profile, reused | 5.68 / 5.70 | 1.56 / 1.57 |

**A profile that produced 1.60 produces 5.66 on a rebuild.** Two builds from one profile also differ in
SHA while measuring the same, so `native-image` is not byte-reproducible; the nondeterminism reaches
the inlining decision that decides whether the processor dissolves.

That kills the two explanations round 59 offered in turn — that the node graph sits at a size
threshold, and that the profile collection varies. Neither survives: the input is identical and the
output is not.

### 1.1 It is baked into the binary once built

Not run-to-run luck, and not address luck. Each of the 13 surviving images from round 59 was
re-measured three times, interleaved: `v6` gives 5.34 / 5.46 / 5.43 and `v7` gives 1.43 / 1.44 / 1.44,
every time. Padding the environment from 16 B to 16 KB (shifting the stack) and varying
`-XX:MaxHeapSize` from 128m to 1g (shifting the heap) leave `v6` at 5.34–5.49 throughout.

### 1.2 Two different failure signatures, told apart by the hand-rolled arm

The hand-rolled arm contains no processor and no auditor, so it says which decision was lost:

| | generated | hand-rolled | what failed |
|---|---|---|---|
| directive missing (`nf_base`) | 5.59 | **1.56** | the dispatch chain — arm-specific |
| a bad build, directive present (`aud_a1..a4`, `rp_*`) | 5.58–6.66 | **1.56** | the dispatch chain, again |
| a bad build, round 59's images (`v6`, `v11`) | 5.41 | **5.41** | **both arms** — `onTick` too |

The published mechanism covers the first row and is confirmed. The third row is the one round 59
mis-explained: it loses the hand-rolled arm as well, and hand-rolled is a single object with primitive
fields. **Round 59 §25.2 is therefore withdrawn** — "hand-rolled is one object so it always lands" is a
good story that this measurement kills.

Unified reading, and no more than the evidence carries: the priority inliner's decisions vary between
builds, and *which* call site it declines to inline varies with them. `PriorityForceInline` removes the
decision for the class you name; it does not remove it anywhere else.

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

These were run *during* the slow run described in §2.3, with `base1`'s profile fixed as the input — so
a knob that rescues dissolution would show as 1.6 against a floor of 5.6. None did.

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
| build parallelism | `-H:NumberOfThreads=1`, twice, same profile | 5.61 / 5.51 — **and the two images still differ in SHA**, so the build is not deterministic even single-threaded |
| build parallelism | `-H:NumberOfThreads=4`, twice | 5.47 / 5.60, different sizes |
| builder heap (sized from *available* RAM, so it drifts) | read back from each build log | 9.28–9.65 GB, and it does not sort: `base3` at 9.31 GB is fast, `rp_base1` at 9.32 GB is slow |

The kit's inputs were verified unmodified for the whole session (`find -newermt` over `target/classes`
and `target/res` finds nothing).

### 2.3 One thing that is not explained

The outcomes are not independent. `base1..3` were fast; every one of the fifteen builds after them was
slow, across four separate batches, with the identical command and unmodified inputs. An i.i.d. coin
does not do that in either direction. Something about the machine's state persists across builds and
changes slowly, and none of the three suspects above is it.

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

- The 1.57 claim stands, qualified, and the qualification is stronger than "85%": the landing rate is
  itself not a stable property. Round 59 measured 11 of 13; this round measured 3 fast and then **15
  slow in a row** for the identical configuration and unmodified inputs.
- **Verify the build you ship** stops being advice and becomes the method. Rebuild until it lands, and
  keep the binary that did — the mode is fixed once built (§1.1).
- Do not spend time on the escape-analysis and inliner knobs (§2), the wider force-inline patterns
  (§2.1), the build's thread count or its heap (§2.2). Thirteen configurations, none of them a lever.
  A day each is what those tables are worth.
- Add the third profile-provenance clause (§3).
