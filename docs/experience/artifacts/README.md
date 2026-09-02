# The two engines, saved for inspection

Both solve the identical 15-event-type, 12-rule order-fulfilment spec
([`../runs/round-22/TASK.md`](../runs/round-22/TASK.md)), both written by **Haiku 4.5**, both scoring
**12/12** on the same twelve boundary probes. Generated sources and build output are stripped; what is
here is what the model actually wrote.

| | files | lines | `mvn` runs | output tokens |
|---|---|---|---|---|
| [`spring-fluxtion/`](spring-fluxtion) | 1 XML + 28 java | 86 + 659 | 4 | 14,322 |
| [`vanilla-java/`](vanilla-java) | 5 java | 763 | 6 | 13,343 |

**`spring-fluxtion/generated/`** — what the toolchain emits from the 86-line XML: `AppProcessor.java`
(907 lines of dispatch), `AppProcessor.graphml`, `AppProcessor.png`. Nobody wrote these. The copyright
header is replaced because it carries a vendor domain and this repo is public; nothing else is altered.

## Where to look

**`spring-fluxtion/src/main/fluxtion/designer/application-context.xml`** — the whole graph: a bean per
node, `constructor-arg ref` per parent. This is the part that replaces wiring code.

**`spring-fluxtion/src/main/java/com/acme/app/ReleaseDecider.java`** — 138 lines, and the reason the XML
does not shrink the token count. The declaration says *this node has these five parents*; the body still
has to say what release means, hold `Map<String,Boolean> lastReleasable` for the EDGE transition, and
carry an `@OnEventHandler` per triggering event type.

**`vanilla-java/src/main/java/com/acme/fulfil/Engine.java`** — 228 lines, one `if/else if` chain over
fifteen event types, each branch naming by hand the rules that event can affect, plus five transition
maps.

## Read this before drawing a conclusion from the tie

**The spec these two engines solve does not distinguish them, and that is a defect in the spec.** Three
properties of it let the vanilla engine win by brute force:

1. **No rule depends on another rule's output.** Every rule reads raw state. `isReleasable()` is
   `isAllocatable() && isCreditOk()` — a two-deep tree over stored values, not a chain. A graph where C
   reads B reads A is where dispatch order becomes a correctness property; this spec has none.
2. **Four of the five EDGE rules are triggered by exactly one event type each.** `HAZARD_BLOCK` and
   `OVERWEIGHT` fire on `DISPATCH`, `SLA_BREACH` on `PICKDONE`, `STOCKOUT` on stock events. Those are
   event handlers, not graph nodes. Only `RELEASE` has several inputs.
3. **Recomputing everything is affordable.** `Engine.checkReleaseRuleForAllOrders()` loops every order
   on every stock event. That is O(orders) per event and it is *correct*, because there is no stale
   intermediate to get wrong.

Incremental, ordered, only-what-changed dispatch is what a compiled graph buys. **A task where
recompute-everything is both correct and cheap cannot measure it.** The tie at 12/12 is real and
honestly obtained; it says these engines agree on this problem, not that the approaches are equivalent.

A spec that would distinguish them needs rules reading other rules' outputs several levels deep, where
evaluating in the wrong order yields a stale intermediate — the glitch that topological ordering exists
to prevent — and enough state that recomputing the world per event stops being free.
