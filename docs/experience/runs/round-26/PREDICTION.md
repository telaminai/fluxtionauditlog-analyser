# PREDICTION — round 26: the Spring route on the same spec

**Committed before launch.** Same 15-event spec, same 11 probes, same model. The variable is the
**wiring route**: the graph is declared in Spring XML and the node shells are generated from it,
instead of being hand-written and registered in a Java builder.

## What is different

| | Java route (rounds 22–25) | Spring route |
|---|---|---|
| the graph is | `cfg.addNode(...)` on a root, reachability by constructor reference | a bean per node, `constructor-arg ref` per parent |
| shells at step 2 | hand-written, one class at a time | **`./scaffold.sh` generates every missing class from the bean file** |
| audit switch | `cfg.addEventAudit(LogLevel.INFO)` | `<property name="logLevel" value="INFO"/>` |
| can the graph be empty? | yes, silently — two cells lost 15 cycles each | harder: a bean list is the registration |

Everything else is held: `GraphExistsTest`, `trace.sh`, the staged build order including step 3b.

## Baseline to beat

Round 25 cell G, Java route: **11/11 in 7 `mvn` runs**, with 1 test and 4 traces.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **Q1** | **Fewer build cycles than the Java route's 7.** Step 2 is mechanical rather than authored — the largest single chunk of writing disappears. | medium |
| **Q2** | **Score ≥9/11.** The wiring route does not help with domain logic, which is where round 24's losses were; step 3b is what fixed those and it is unchanged. | medium |
| **Q3** | **No cell loses the graph.** The failure that cost cells C and T3 fifteen cycles each — a builder registering nothing — has no equivalent when the bean list *is* the registration. | medium-high |
| **Q4** | **Fewer output tokens than the Java route.** XML is verbose per node, but the shells are generated rather than written, and shells were the bulk of step 2. | low-medium — this is the one I would least back |
| **Q5** | **At least one new friction appears that the Java route does not have**, and it is about the XML↔class boundary: a bean class name that does not match, a constructor-arg order that does not match the generated field order, or event types declared in `eventTypes` that disagree with the handlers. | medium |

## Falsifiers

- **If it scores below 8/11**, the mechanical wiring gain is outweighed by whatever the XML boundary
  costs, and the Java route stays the recommendation.
- **If build cycles are not lower**, generating the shells did not save what I think it saves, and the
  case for the Spring route rests on the harder-to-lose-the-graph property alone.

## Stated bias

I predicted this route would be *harder* before testing it, on the grounds of unverified FQNs and XML
volume. I then spent an afternoon blaming the framework for a bug in my own scaffold script. Those
priors were wrong; these predictions are deliberately more favourable than my instinct, and I am
recording that so the correction is visible rather than quiet.
