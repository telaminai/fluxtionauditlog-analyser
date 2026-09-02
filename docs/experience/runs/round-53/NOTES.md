# Round 53 — idiomatic Java, given a vendor library that ships `refresh()`. **IT PASSES.**

**SCORE 17/17.** This is the result that breaks the run of idiomatic-Java failures, and it deserves to
be stated before any caveat: **given a vendor library with a counter-free `refresh()` on the stateful
components, an idiomatic Java engine got every published figure, every alert and every stateful
counter exactly right.**

Rounds 49 and 52 failed the same task — `risk.var` 88% understated, `capital.buffer` 87% — and round 52
reproduced the error *deliberately*, documenting that the alternative (refreshing) would publish a
false alert and inflate all three counters. **Round 53 removes that dilemma by changing the vendor
library, not the instructions**, and the dilemma was the whole failure.

## What changed

`Risk` and `Capital` gained:

```java
/** Recompute derived figures because an INPUT changed, not because an event arrived.
 *  Advances no counter. */
public void refresh() { compute(false); }
```

That is the entire delta. The agent then used it exactly as intended — `risk.refresh()` then
`capital.refresh()` on a `volFactor` config, in dependency order so Capital reads the corrected `var`.

## I nearly reported this as a FAIL. Twice. Both were defects in my scorer.

This is the fourth scoring defect in this project and the most consequential, because it pointed the
wrong way — it would have manufactured evidence for the conclusion I was already writing.

| | reported | actual cause |
|---|---|---|
| first run | **8/16 FAIL** | wrong scenario — `expected.txt` pairs with `scoring-scenario.txt` (14 lines), not `scenario-vol-after-tick.txt` (5) |
| second run | **8/16 FAIL** | `plain_state` dropped record-groups carrying no values, so an event that legitimately publishes nothing shifted every later comparison by one |
| third run | **13/17** | the empty-group fix used the file-**final** state, back-dating the end state onto every silent event |
| fourth run | **17/17 PASS** | — |

`score-aligned.py` is the corrected scorer: it aligns on **scenario position**, keeps silent events, and
carries the state each event actually had at that moment.

**Sensitivity established by mutation, because a scorer that had just started passing needed
falsifying:**

| mutation | score |
|---|---|
| unmutated | 17/17 |
| one `risk.var` value wrong | 16/17 |
| one `marketdata.vol` wrong (propagates by carry-forward) | 14/17 |
| one alert line missing | 16/17 |
| `capital.breachCount` wrong | 16/17 |

4 of 4 valid mutations caught. (A fifth was written and **did not mutate the file** — its condition
never fired. It is recorded because an unmutated mutant that "passes" is exactly how a permissive
scorer launders itself.)

## What this does and does not change

**It does not rescue rounds 49 and 52.** Those measured a library *without* `refresh()`, and both horns
were wrong. That result stands.

**It does change the claim I was building toward.** "Idiomatic Java cannot get this right" is not
supported. The supported claim is narrower and more interesting: **the vendor's API decides whether the
integrator can be correct at all.** With no counter-free recompute, both available behaviours are
wrong. With one, a competent integrator gets it exactly right.

That is a statement about *library design*, and it is the one Fluxtion's model makes structurally
rather than by convention — a Fluxtion node's recompute is separated from its event handling by the
framework, so the `refresh()` that had to be added by hand here is what `@OnTrigger` already is.

**Cost is not yet compared.** Round 53's token cost against the Fluxtion arm is not scored here, and
the correctness result is the one that mattered. The plain-Java arm remains **n=1** against Fluxtion's
n=4; the ratio should not be published until that is closed.
