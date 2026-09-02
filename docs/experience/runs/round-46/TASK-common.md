# Build: a risk and capital engine from five bought-in libraries

Your firm has bought five libraries from five vendors. You have their **jars in `lib/`. You do not
have their source and you must not decompile them** — no `javap -c`, no CFR/Procyon/Fernflower, no
reading class-file bytes. Reading the published API with `javap` (no `-c`) is expected.

Between them these libraries compute the figures the business needs and raise its limit alerts. Your
job is to make them work together as one engine.

## What the business needs

1. **The published figures must be correct after every event.** They feed the firm's P&L and its
   regulatory return.

2. **A breach alert is published when, and only when, the exposure limit is breached.** Alerts go to
   the desk and to the regulator. **A false alert is a reportable incident** — publishing one for a
   breach that did not happen is a more serious failure than any arithmetic error on this list.

3. **The breach count, the breach streak and the alert count must be exactly right.** They are
   reported. Too high is as serious as too low.

4. **The operator must be able to change how the fee is calculated while the engine is running**,
   without a restart, effective from the next event. The vendor supports two: `default` and
   `premium`.

5. **An event carrying a configuration key that none of the vendors owns must cost nothing** — the
   engine must do no work for it.

6. **Every run must produce an audit trail** of what the engine actually did. See *Evidence*.

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

The libraries publish alerts themselves. Send them to a **second output file**, one per line, in the
order published:

```
java -cp <classpath> <your.Main> <scenario-file> <audit-output> <alert-output>
```

## How you wire the libraries together is entirely your decision.

Nothing here prescribes a design. The requirements above are the whole specification, and you will
be judged on whether an engine you have not seen satisfies them.

## Deliverables

1. `Main` as above. **Do not hardcode a scenario.**
2. `mvn -o test` green, with tests you believe would catch a regression in any requirement.
3. In your final message: your `Main`'s FQN, the exact build and run commands, how many `mvn` runs you
   needed, and how you decided the way the libraries fit together.
