# The two engines, saved for inspection

Both solve the identical 15-event-type, 12-rule order-fulfilment spec
([`../runs/round-22/TASK.md`](../runs/round-22/TASK.md)), both written by **Haiku 4.5**, both scoring
**12/12** on the same twelve boundary probes. Generated sources and build output are stripped; what is
here is what the model actually wrote.

| | files | lines | `mvn` runs | output tokens |
|---|---|---|---|---|
| [`spring-fluxtion/`](spring-fluxtion) | 1 XML + 28 java | 86 + 659 | 4 | 14,322 |
| [`vanilla-java/`](vanilla-java) | 5 java | 763 | 6 | 13,343 |

## Where to look

**`spring-fluxtion/src/main/fluxtion/designer/application-context.xml`** — the whole graph: a bean per
node, `constructor-arg ref` per parent. This is the part that replaces wiring code.

**`spring-fluxtion/src/main/java/com/acme/app/ReleaseDecider.java`** — 138 lines, and the reason the XML
does not shrink the token count. The declaration says *this node has these five parents*; the body still
has to say what release means, hold `Map<String,Boolean> lastReleasable` for the EDGE transition, and
carry an `@OnEventHandler` per triggering event type.

**`vanilla-java/src/main/java/com/acme/fulfil/Engine.java`** — 228 lines, and the contrast worth seeing:
one `if/else if` chain over fifteen event types, each branch naming by hand every rule that event can
affect, plus five separate transition maps.
