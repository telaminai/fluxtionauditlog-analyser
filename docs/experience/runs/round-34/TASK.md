# Integrate two supplied components

You are given two components from different vendors. **You did not write them and you must not modify
them.** Read `COMPONENTS.md` for what they are.

Your job is to build an engine that feeds them market data and reports what they compute.

## Events

- `TICK,symbol,bid,ask`

## What it must produce

**A results file**, one line per tick, giving the four stage values after that tick has been fully
processed, to two decimal places:

```
<eventNumber>,<mid>,<notional>,<adjusted>,<score>
```

**And an evaluation file**, one line per event, naming the stages that evaluated **in the order they
evaluated**, each appearing once:

```
<eventNumber>,<stageName>|<stageName>|...
```

Use the stage names exactly as the components report them: `pricing.mid`, `risk.notional`,
`pricing.adjusted`, `risk.score`.

## What must be true

**G1 — every stage evaluates exactly once per tick.**

**G2 — no stage reads a stale value.** When a stage evaluates, everything it reads must already reflect
this tick. The components are mutually dependent at their internal stages, so this constrains the order.

**G3 — the reported values are internally consistent.** The four numbers on one line must all derive
from the same tick.

## Running it

```
java -cp <classpath> <your.Main> <scenario-file> <results-file> <evaluation-file>
```
One event per line, `#` starts a comment. **Do not hardcode a scenario.**

## Deliverables

1. The engine. Report your main class's fully-qualified name.
2. `Main` as above, writing both files.
3. **JUnit tests**; `mvn clean test` green.
