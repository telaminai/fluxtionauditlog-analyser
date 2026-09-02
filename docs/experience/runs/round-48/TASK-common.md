# Build: a risk and capital engine from a component catalogue

Your firm has bought components from five vendors. You have their **jars in `lib/`, plus a shared
`contracts.jar` holding the event types and the published interfaces. You do not have any vendor's
source and you must not decompile them** — no `javap -c`, no CFR/Procyon/Fernflower, no reading
class-file bytes. Reading the published API with `javap` (no `-c`) is expected.

## Each jar offers more than one component

A jar is a **catalogue**, not a single library. Each component it publishes is declared in the jar's
manifest as a named entry, stating what that component provides, what it requires, and what it
consumes. Read the manifests first:

```
unzip -p lib/<jar> META-INF/MANIFEST.MF
```

**Choosing the wrong component is not a build error.** A smaller component compiles and runs, and
simply produces fewer figures. Match what you declare against what the business asks for below.

## What the business needs

1. **These published figures must be correct after every event**, and all of them must be present:
   `mid`, `depth`, `vol`, `ewma`, `adjusted`, `spread`, `book`, `score`, `notional`, `exposure`,
   `var`, `charge`, `buffer`, `fee`.
2. **A breach alert is published when, and only when, the exposure limit is breached.** Alerts go to
   the desk and to the regulator. **A false alert is a reportable incident** — publishing one for a
   breach that did not happen is more serious than any arithmetic error on this list.
3. **The breach count, the breach streak and the alert count must be exactly right.** They are
   reported. Too high is as serious as too low.
4. **The operator must be able to change how the fee is calculated while the engine is running**,
   without a restart, effective from the next event. The vendor supports `default` and `premium`.
5. **An event carrying a configuration key that no component owns must cost nothing.**
6. **Every run must produce an audit trail.** See *Evidence*.

## What arrives

```
TICK,symbol,bid,ask
TRADE,symbol,qty,price
RATE,ccy,rate
CONFIG,key,value
STRATEGY,default|premium        <- the operator changing the fee calculation, mid-run
```
One per line, `#` comments ignored.

## Where the alerts go

The components publish alerts themselves. Send them to a **second output file**, one per line, in
publication order:

```
java -cp <classpath> <your.Main> <scenario-file> <audit-output> <alert-output>
```

## How you assemble the components is entirely your decision.

Nothing here prescribes a design. The requirements above are the whole specification, and you will be
judged on whether an engine you have not seen satisfies them.

## Deliverables

1. `Main` as above. **Do not hardcode a scenario.**
2. `mvn -o test` green, with tests you believe would catch a regression in any requirement.
3. In your final message: your `Main`'s FQN, the exact build and run commands, how many `mvn` runs you
   needed, and **which components you selected from each jar, and why**.
