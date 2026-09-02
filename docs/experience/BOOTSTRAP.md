# How to bootstrap an authoring model — what the measurements say

Distilled from rounds 13–21 (`docs/experience/runs/`). Every claim below is tied to a measured run,
scored on hold-out scenarios the authoring agent never saw. Where something is unproven it says so.

## The one-line answer

**Ship a project that already runs, with the silent-failure semantics demonstrated in the code at the
point of use. Reserve prose for what cannot be demonstrated. Make the rest impossible in the build.**

## The evidence, shortest form

| what the agent was given | doc tokens | `mvn` runs | hold-out |
|---|---|---|---|
| hand-written guidance, empty directory | 2,955 | 7 | *no log produced* |
| **more** hand-written guidance | 3,905 | 10 | 7/8 |
| the full published reference set | **12,443** | 8 | 7/8 |
| **a working template** | **662** | 11 | **8/8** |
| a working template + one annotation demonstrated | 1,144 | 6 | 8/8 |
| **the same, table removed** | **742** | **5** | **8/8** |

Four documentation versions moved cost around and left the score at 7/8. **A template moved the score.**
Adding one demonstrated annotation took a *different* task from 6/8 to 8/8. Removing the table again
cost nothing.

## What to put in the starting project, in order of measured value

**1. It must already build, test and run.** Not a skeleton to fill in — a green project whose output
you can read before changing anything. This is the single largest effect measured.

**2. A build in which the structural failures cannot happen.** Generation runs *after* compilation, so
anything importing generated output cannot compile in one pass, and a processor left from an older node
shape breaks the build with errors pointing at code the author never wrote. Both were top-three costs.
The fix is a two-pass compile plus deleting stale generated source every build — after which neither
failure exists. **Prose about them does not work: the bootstrap trap caught four consecutive authors who
had read a warning about it**, including this document's author.

**3. The silent-failure semantics, demonstrated in code, at the point of use.** One annotated field with
a comment beside it beat both a warm session and a 12,443-token reference set:

```java
private final SensorState sensorState;                    // trigger: a new reading
@NoTriggerReference private final LimitStore limitStore;  // data only: read, never triggers
```

Self-reported framework difficulty on one fixed task: **60%** cold with a plain template, **50%** for an
agent warm from having just built a similar engine, **25%** cold with the annotation shown. *The artefact
beat session context* — and unlike session context it survives to the next cold start.

**4. Tests that assert on the framework's own log**, including the traps. A test helper that reads "the
last record" silently reads the `TearDown` cycle, which contains no nodes and therefore passes any
did-not-run assertion for the wrong reason. Two of this project's own template tests were green that way.

**5. A short annotation table — optional.** Removing it entirely cost nothing (5 runs, 8/8 — the
cheapest cell measured). Keep one because it is under 900 tokens and an omission costs a silent bug,
but do not expect it to teach: the agent that had no table still used the annotation, and named the
code comment as where it learned it.

## What does not work

- **More documentation.** Three versions, 2,955 → 12,443 tokens, no correctness gain.
- **Warning about a failure instead of removing it.** *"I read this but did not execute it."*
- **Self-authored tests as the correctness gate.** One engine passed **29 of its own tests** and got
  **0 of 5** behavioural checks right on unseen input. Another shipped a green build whose materiality
  gate suppressed everything. Correctness must be scored on input the author never saw.
- **Trusting self-reported difficulty.** The cheapest cell measured reported the framework as *harder*
  than a slower one. Those percentages track how the work felt, not what it cost.

## The failure class that matters

Every expensive bug in nine rounds was **silent**: green build, passing tests, plausible output.
A detector re-firing on cycles where its input never moved. A gate suppressing every alert.
`addEventAudit` omitted, producing an empty log with no warning. A missing builder class, producing no
generated code with no warning. A monolith narrating a graph it did not have.

Loud failures are cheap — one cycle, one fix. **Design the bootstrap around the silent ones**: put them
in code, or remove them in the build, and let the compiler keep the loud ones.

## Still unproven

- Whether the complete 29-row annotation table pays on a **harder** task than the ones measured.
- Whether any written artefact recovers the remaining advantage of a warm session.
- Whether these results hold above ~10 nodes; every scored run here is small. Two attempts at ~50 nodes
  produced no working engine in either arm, which measured the task rather than the approach.
