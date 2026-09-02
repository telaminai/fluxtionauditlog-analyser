# Round 23 — the diagnostic bootstrap made it worse

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `053b867` before launch. **1 of 5.**

Same spec, same model, same probes as round 22. Only the bootstrap changed: `trace.sh` plus a four-step
procedure separating wiring failures from logic failures from output-path failures.

| round | cell | bootstrap | `mvn` runs | own tests | `trace.sh` runs | **score** |
|---|---|---|---|---|---|---|
| 22 | A | plain template | 12 | **21** | 0 | **5/8** |
| 22 | B | plain template | 6 | 3 | 0 | 0/8 |
| 23 | C | **+ trace.sh** | 15 | 0 | **0** | **0/8** — never built |
| 23 | D | **+ trace.sh** | **22** | **3 smoke tests** | **9** | **3/8** |

**More build cycles, fewer tests, lower score.**

## Predictions scored

| # | Predicted | Actual | |
|---|---|---|---|
| Y1 | neither cell emits zero decisions | **C emitted zero** | ✗ |
| Y2 | at least one cell ≥7/8 | best was 3/8 | ✗ |
| Y3 | a cell credits `trace.sh` with a named bug | D named three | ✓ |
| Y4 | 8–14 build cycles | **22 and 15** | ✗ |
| Y5 | the differential diff becomes meaningful | C emits nothing, so no | ✗ |

## The mechanism, and it is the opposite of the intent

**The tool displaced the tests.** Round 22's cell A wrote **21 rule-level tests**, ran no traces, and
scored 5/8. Round 23's cell D wrote **three smoke tests** — `graphBuildsAndInitializes`,
`stockTrackerAcceptsReceipt`, `orderTrackerAcceptsOrder` — ran `trace.sh` nine times, and scored 3/8.

Giving an author a manual inspection tool made it stop writing automated checks. Manual inspection only
covers the scenarios you think to run, and D never thought to run a GOLD-tier release:

```
ev 4 Order  ['orderTracker','creditChecker','allocatabilityChecker','releaseabilityChecker']
```

`releaseDecider` is absent from the ORDER path — D wired it to trigger on `PaymentTracker` only, so an
order that becomes releasable through GOLD credit rather than payment never releases. **Four of its five
failures are that one wiring bug**, and it is precisely what step 2 of the procedure exists to catch:
*a node that never appears is a wiring problem.* The tool was used nine times and never pointed at it,
because it was never pointed at the right scenario.

## Cell C: the builder gutted, and 15 cycles blaming the plugin

```java
public void buildGraph(EventProcessorConfig config) {
    new GraphRoot();          // constructed and DISCARDED — never addNode'd
    config.addEventAudit();
}
public void configureGeneration(FluxtionCompilerConfig config) { }   // no class name, no package
```

The builder runs, registers nothing, and is told neither what to emit nor where. C concluded the fault
was *"Fluxtion Maven plugin code generation infrastructure"* and spent 15 build cycles there. It ran
`trace.sh` zero times — it could not, having never produced a processor.

**Second occurrence**: round 22's T3 deleted the builder outright and hand-wrote a processor. The
builder is the single point where classes become a graph, and when it is wrong the symptom is silence.
Third distinct silent-on-absence failure in this framework, with `addEventAudit` and the missing builder
class.

## The falsifier I wrote fired

> *"If a cell again ships an engine emitting nothing, then a script that must be run is still a thing
> that can be skipped, and the next move would have to be a failing test in the template rather than a
> tool."*

That is what happened, and the escalation is now specified rather than guessed:

1. **A test that ships in the template and fails when the graph is empty or generation unconfigured.**
   It runs under `mvn test` whether or not anyone chooses to use a tool. Both C and round-22's T3 would
   have been stopped at the first build.
2. **Do not offer a manual tool as an alternative to tests.** The tool must be framed as what you use
   *when a test fails*, never as the way you check your work — this round is evidence that authors
   substitute rather than add.

## Honest summary

I added tooling on the strength of a good argument and it cost 10 build cycles and 2 points. The
argument — that Fluxtion uniquely lets orchestration be checked independently of logic — is still
sound, and D's report confirms the tool found three real bugs. But **the intervention's net effect was
negative**, and the cause is a behavioural one I did not anticipate: a manual instrument crowds out
automated ones, and manual coverage is whatever the author happened to think of.
