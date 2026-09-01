# Round 17 — the template beats the documentation

Every number independently re-measured: `mvn clean test` re-run from clean, the engine run against a
21-event hold-out it never saw, and the audit log scored by
[`round-16/oracle/score.py`](../round-16/oracle/score.py) — which was itself validated at 8/8 against a
reference implementation before it scored anybody.

| cell | starting point | doc tokens | `mvn` (failed) | output | weighted | **hold-out** |
|---|---|---|---|---|---|---|
| fx20 | v1 docs, empty dir | 2,955 | 7 (2) | 13,495 | 6.89 | *no log produced* |
| L1 | v1 docs, empty dir | 2,955 | 8 (3) | 21,289 | 10.38 | correct, scored 0 on format |
| A1 | v2 docs, empty dir | 3,905 | 10 (3) | 13,223 | 7.78 | 7/8 |
| A2 | v3 published set, empty dir | **12,443** | 8 (2) | 12,247 | 9.92 | 7/8 |
| **T1** | **working template** | **662** | 11 (3) | 16,465 | **6.03** | **8/8** |

**T1 is the first perfect score in the series, on the smallest doc payload and the lowest weighted
cost.** It carried 1/19th of A2's documentation and scored higher.

## What it actually got right

D2 at cycle 14 (the tenth stuffing order), D1 at 18 (the execution completing the wash pair), D3 at 19
and 20, **and nothing anywhere else** — precision, not just recall. It is also the only cell to pass N7:
R1's 20,000 notional raised, R2's 5,000 suppressed. A1 suppressed both; A2 raised correctly but
miscounted.

## Why the template wins, in its own words

> *"I would have wasted 30% of time on framework mechanics."*

It lists what the example code taught without being read as instruction: `@OnEventHandler` for state
versus `@OnTrigger` for derived logic, returning false to arrest propagation, parents-are-fields,
transient state, the audit processor registered before `init()`. Asked directly whether it would rather
have had an empty directory and good docs, it said **no**.

**Note the honest shape of this result: T1 used MORE build cycles (11 vs 8) and more output tokens than
A2.** The template did not make authoring cheaper in raw effort. It made it *correct* — and correctness
was the thing the documentation never bought. Every doc iteration from v1 to v3 moved cost around and
left the score at 7/8; the template moved the score.

## The template's own worst moment confirms the fix

T1's third-largest cost was the bootstrap trap — *"move Main temporarily, run process-classes, restore
Main"* — which it hit despite the README describing it. That is now the fourth author to hit this after
reading a warning about it, counting two agents and me.

It is fixed structurally in the template's pom as of `d76eebf`, after T1 launched: the generated source
is deleted at `initialize`, pass one compiles everything except `${generated.dependents}`, generation
runs, pass two compiles the rest. Verified green from a tree with no generated source, and still green
after adding a constructor argument to a node with nothing deleted by hand. **T1 therefore measures the
template as it was, and a repeat should be cheaper than 11 build cycles.**

## What T1 says the template still needs

Ranked as it gave them, all concrete:

1. **A multi-event example.** One event type and two nodes left it inferring how a node handles two
   event types.
2. **A node that returns `false`.** `ThresholdAlert` always returns true, so the single most important
   idea in the framework — propagation arrest — is described in the README but not demonstrated in code.
3. **State tracked across events** — the live-order pattern, which cost it 30% of its effort.
4. **A test that filters by node name** rather than `anyMatch(contains(...))`.
5. **The scenario-file format** shipped with the template rather than only in the task.

Item 2 is the sharpest: the template demonstrates every mechanic except the one the whole framework
turns on. The audit log shows the arrest — cycle 4 runs only `sensorState` — but no *node* in the
template ever refuses to propagate on a business condition.

## What this changes

The working conclusion after four doc iterations and one template: **documentation moved cost, code
moved correctness.** Prose is the right instrument for what cannot be demonstrated; everything that can
be shown in a project that builds should be.
