# Ceiling measurement — the diagnostics half, run against a real build

**Run 2026-08-31**, against the two rows deferred in
[`baseline-2026-08-31`](../baseline-2026-08-31/RESULTS.md) when the sidecar had no production caller.
It has one now (`DiagnosticSidecar`, `-Dfluxtion.diagnostics.sidecar=<path>|true`), so the rows are
runnable. Two fresh agents, ~5 minutes each, same questions as the baseline's authoring pair.

**This is a ceiling, not a default.** The sidecar is opt-in and the console still shows the legacy prose
by design, so an author who has not set the property sees exactly what the baseline agents saw. What is
measured here is what a repair loop that *parses the sidecar* can do.

## The fixture is real output, not a hand-written message

The baseline used a hand-written error string. This one did not: a node with a final state map and a
final node reference was added to this repo's own graph, `mvn -Pregen process-classes` was run through
the real `fluxtion-maven-plugin`, and **the console text and the `fluxtion-diagnostics.json` handed to
the agents are what that build actually produced** (renamed to the baseline's class and field names so
the two runs are comparable). The distinction matters: a fixture the product never emits proves nothing,
which is the failure this project keeps finding on both sides.

## Scorecard — both pinned predictions confirmed

| row | baseline | predicted | measured |
|---|---|---|---|
| predicted build attempts | **2–3**, both agents | **1** | **1**, both agents ✓ |
| names `final` as the mapping trigger | **0 of 2** | **2 of 2** | **2 of 2** ✓ |

**The secondary falsifier also passes**, and it is the one that decides whether the result means
anything. The prediction was pinned with: *if agents answer correctly while citing prior knowledge
rather than the artefact, the file has not been read and the result proves nothing.* Both attributed the
rule to the JSON, unprompted:

> "(b) diagnostics json — essentially all of it. I am paraphrasing, not recalling. Without the json my
> rule would have been vaguer and probably wrong on the precedence point."

> "(b) entirely… prior training knowledge is consistent with this but I did not need it and would not
> have produced the precedence detail or the 'initializer still runs' detail from memory."

So the file supplied the rule rather than confirming one the model already had. That is the difference
between a document and a diagnostic, measured.

## What exceeded the prediction

Both agents produced the **complete** four-route rule — constructor parameter, explicit
`@ConstructorArg`/`@AssignToField` opt-in *and its precedence*, setter-wiring for a non-final field, and
exclusion — and both stated the **initializer-survives** semantics correctly:

> "Excluding a field stops Fluxtion supplying its *value*. A field with its own initializer is
> unaffected… only a value the builder put there before generation is lost."

That sentence is the correction this repo measured and sent upstream after the closure review turned an
open question into an assertion. It is now in the shipping message and doing work: one agent said
explicitly it *would not have known* the initializer survives without it.

Both also took the one-step route because the diagnostic **certified** it — *"the compiler checked the
remaining mapped fields against the available constructors, so this exclusion is a complete repair."*
That is F1 from the FLX-1009 review, and it is what turned 2–3 attempts into 1.

## The one residual, and it is small and fixable

Both agents named the same single risk of a second build, and it is not about the graph model:

> "if `com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore` is the wrong package, the
> compile fails on the import."

Both said they would use `transient` first *specifically to avoid guessing an import*. So the remaining
retry risk in the best case is a package name, and **one token per annotation in `suggestedFix` — the
fully-qualified name — removes it.** Raised upstream.

## Limits

- n=2, one model family, one sitting. Same instrument as the baseline, which is the point; not
  comparable to the earlier task-driven rounds.
- Ceiling, not default: measured with the sidecar enabled. The default path is unchanged from baseline
  and will stay so until something surfaces the structure without an opt-in.
- The agents were handed the sidecar. A real loop must also *find* it, which is a harness question
  nobody has measured.
