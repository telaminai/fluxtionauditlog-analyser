# Round 29 — the edge detector was used and the build never compiled

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `c57f1a0`. **1 of 5.**

| | round 28 | round 29 |
|---|---|---|
| helpers shipped | none | `EdgeDetector`, `Decisions` |
| `mvn` runs | 1 | 12 |
| compiled | yes | **no** |
| score | 8/18 | **0/18** |

**Z1 ✗, Z2 ✗, Z3 ✗, Z4 ✗** — it scored lower, not higher. **Z5 ✓**: it used `EdgeDetector` and named it.

## What actually stopped it

Not the transition tracking. Its own §5: twelve `mvn` runs, all failing on

> *"Event class visibility — Java requires public classes in separate files (28 files needed for 28
> event types)."*

It put the events in one `Events.java`, hit the one-public-class-per-file rule, and spent the budget
splitting them. **The mechanical cost of 28 files consumed the run before any logic was written.**

Round 28 hit the same rule but at 12 rules had room to recover; at 24 it did not.

## The helper did what it was built for

> *"EdgeDetector eliminated 13 separate previous-state Maps per rule… removed manual boolean logic per
> rule… consistent keying across all rules. **Remained hard:** framework constraints — the public-class
> file rule, field parent/non-parent declaration, trigger vs data-parent — dominated the implementation
> time, not the EDGE/CONDITION logic."*

So the diagnosis from round 28 was right *and* incomplete. Transition tracking was **a** repeated cost;
at this size a larger one sits in front of it, and it is pure boilerplate: one file per event type, one
field-parent decision per node.

**That is exactly what `scaffold.sh` generates on the Spring route** — declare the beans, get the files.
The Java route has no equivalent, and this round is the first time the difference has been decisive.

## Correction to round 28's conclusion

Round 28 said *"the graph was not the limit; the per-rule state was."* That was one run's evidence and it
was wrong as a general claim. With the per-rule state solved, the limit moved to file-level boilerplate.
**The honest version: at 28 event types the binding constraint is mechanical volume, and it moves each
time the previous one is removed.**
