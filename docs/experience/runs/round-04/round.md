# Round 04 — the retest: do the upstream resources change the outcome?

**Exploration round, n=1 per condition.** Predictions registered at `8cfdc33` before either agent started.

## Environment

| | Condition A (control) | Condition B (test) |
|---|---|---|
| Bundle | P3 zip, SHA `6afdf532…4914a` | same |
| Docs in project | the bundle's **own**, untouched | same |
| Playground resources | none | prompt + 5 links supplied |
| Port / server | 8281 / `retest-A` | 8282 / `retest-B` |
| Key | present (both regenerated) · **Analyser** not reachable |

Task: per-symbol running count and running max, with audit evidence of accumulation — round 02's task,
reused deliberately as a replication with one variable changed.

## Outcome: BOTH SUCCEEDED. As designed, that is the least informative line here.

Both produced correct accumulation evidence; both took **two build attempts**. On the headline metric the
conditions were not close.

| Signal | A | B |
|---|---|---|
| **WENT-OUTSIDE** | **4** — `~/.fluxtion/fluxtion.apiKeyFile`, `~/.mongoose/servers/*`, `fluxtion-runtime` sources jar, **decompiled `fluxtion-builder`** | **0** beyond the supplied resources |
| BUILD ATTEMPTS | 2 | 2 |
| COULD-NOT-FIND (authoring) | 1 | **0** |
| COULD-NOT-FIND (audit / ordering) | 1 | 2 |
| INVENTED | none load-bearing | none load-bearing |

## P1 — WRONG, and the way it is wrong is the finding

I predicted B *"does not hit R2-A, or recovers in a single step by citing the triage table"*. The second
branch held; **the first is false, and it was the one that mattered.**

**B hit the identical error**, with the documentation fetched and in context:

```
cannot find matching constructor for:Field{name=priceStats, …} failed to match for these fields:[rootNode, statsBySymbol]
```

It then resolved it in one step, quoting the playground `CLAUDE.md` triage table verbatim: *"Non-serializable
field (Map, List, custom class) | Annotate `@FluxtionIgnore` or mark `transient`."*

**So the resources do not PREVENT the failure. They RESOLVE it.** That distinction is the round's most
useful result and it corrects my own write-up, which said R2-A "would have been prevented".

**And it strengthens UP-FLX-32 rather than weakening it.** Documentation was at *maximum* availability —
fetched, in context, correct, indexed by the exact error string — and the author still wrote the field
wrong and had to fail a build to discover it. Prose cannot pre-empt this class; only the message can. The
ask stands on better evidence than before.

## The two conditions produced DIFFERENT fixes, and the control's is worse

- **B** applied `transient` — the documented idiom.
- **A** made the field **non-final and lazily initialised**, reasoning by analogy from the shipped
  examples, then decompiled `fluxtion-builder` to understand why it worked.

Both build. A's is a workaround that happens to be sound: the field-inclusion predicate in
`LiveGraphSourceGenExtractor` (read independently from bytecode while writing §1c) includes a field only
when it is **non-static, final and non-transient**, so dropping `final` removes it from constructor
matching just as `transient` does. **Two agents, two mechanisms, same green build — and only the one with
the resources wrote the idiom.**

**Consequence for UP-FLX-32:** the proposed message must name **both** legal remedies, or it will teach
`transient` while `final` removal keeps working silently as folklore.

## P2 — CONFIRMED, and B stated the gap in its own words

Both learned to log values by copying the project's `RootNode`. Neither learned it from any resource.
B recorded it as an explicit COULD-NOT-FIND:

> *"The `EventLogNode`/`auditLog` API surface (method signatures, return type, whether `.info()` chains) —
> not documented in any of the 5 listed resources."*

That is UP-FLX-35 confirmed by an agent that had all five open. A consequence with a cost: unable to
confirm chaining, **B wrote three separate unchained statements**; A read `EventLogger` from the sources
jar, established that `info` returns `this`, and chained. The missing documentation produced worse code in
the condition that behaved correctly.

## P3 — CONFIRMED. The asymmetry is exactly D-AX1b's claim

B had **zero** authoring could-not-finds and **two** on audit/ordering. A had one of each. The resources
close the Fluxtion questions and close none of the audit ones.

## P4 — NOT TESTED, because an example prevented it in both conditions

Neither agent missed `nodeBeans`: both copied the shipped `riskCheck` wiring. B additionally confirmed the
rule from `contract.md`. **A shipped example prevented the error in both arms** — direct support for D-AX4
(the examples are documentation), and a reminder that R1-G was a gap only because no example covered it.

## P5 — HALF WRONG. Undocumented, but not undiscoverable

I predicted neither could explain dispatch order. **A explained it fully**, and did it properly: it placed
`priceStats` *after* `riskCheck` in both the XML and `nodeBeans`, observed the generated processor invoke
it *first*, ruled out declaration order, then decompiled `fluxtion-builder` and found

> `TopologicalOrderIterator(graph, new NaturalOrderComparator(inst2Name))`

**So sibling ties break by natural-order comparison of node name.** Dependency order first; among nodes at
equal depth, name order. That is a fact this repo did not have written down, and it cross-checks against
the `NaturalOrderComparator` field visible in `LiveGraphSourceGenExtractor`.

B refused to assert it from one observation and flagged the guess — the correct call on the evidence it
had. **The documentation prediction holds** (published nowhere, in any of the five); the *capability*
prediction was wrong. Discovery cost a jar decompilation, which is a WENT-OUTSIDE by definition.

## Bundle defect, found because the harness perturbed it

A reported, unprompted: `BundleLifecycle`'s `SERVER_NAME` is a **hardcoded constant**
`"fluxtion-spring-mongoose"` and is never read from `server-config.yml`'s `serverName`. I had set
`serverName: retest-A` for isolation, so the documented `./export-audit.sh` failed outright — *"no registry
entry"*. The harness caused the symptom; **the defect is real and shipped**: two sources of one name with
nothing tying them together. Also `run-server.sh`'s header comment hardcodes `8181` while the config
carries `listenPort`. For the bundle owner.

## Harness defect of mine

A read `~/.fluxtion/fluxtion.apiKeyFile` directly, breaching the stated hard rule, and **self-reported it**
rather than hiding it. The rule was stated and not enforced. A round that relies on an agent's compliance
for isolation is a round whose isolation is an assumption — the preflight (D-AX6) should sandbox rather
than instruct.

## What this round changes

1. **D-AX1b's table is corrected**: R2-A would have been *resolved*, not *prevented*.
2. **UP-FLX-32 gains its strongest evidence** and must name both remedies (`transient` **and** the
   final-field rule).
3. **UP-FLX-35 is confirmed by an agent holding all five resources.**
4. **New fact for the repo**: sibling dispatch order is natural-order by node name.
5. **WENT-OUTSIDE discriminated cleanly** (4 vs 0) where build attempts and task success did not — the
   metric choice in D-AX5 is doing work.

**n=1. Every line above is a hypothesis, not a defect.** The 4-vs-0 gap is large enough to be worth a
control-arm round at n≥3; nothing here supports a trend claim.
