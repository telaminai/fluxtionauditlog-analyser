# Build: a risk and capital engine from five bought-in libraries

Your firm has bought five libraries from five vendors. You have their **jars in `lib/`. You do not
have their source and you must not decompile them** — no `javap -c`, no CFR/Procyon/Fernflower, no
reading class-file bytes. Reading the published API with `javap` (no `-c`) is expected.

Between them these libraries compute the figures the business needs. Your job is to make them work
together as one engine.

## What the business needs

1. **The published figures must be correct after every event.** They feed the firm's P&L and its
   regulatory return.
2. **The operator must be able to change how the fee is calculated while the engine is running**,
   without a restart. The new calculation applies from the next event onwards. Today there are two:
   `default` charges **1%** of exposure, `premium` charges **5%**.
3. **The breach count must always be exactly right.** It is reported to the regulator, and a count
   that is too high is as serious as one that is too low.
4. **An event carrying a configuration key that none of the vendors owns must cost nothing** — the
   engine must do no work for it.
5. **Every run must produce an audit trail** of what the engine actually did. See *Evidence*.

## What arrives

```
TICK,symbol,bid,ask
TRADE,symbol,qty,price
RATE,ccy,rate
CONFIG,key,value
STRATEGY,default|premium        <- the operator changing the fee calculation, mid-run
```
One per line, `#` comments ignored.

## How you wire the libraries together is entirely your decision.

Nothing here prescribes a design. The requirements above are the whole specification, and you will be
judged only on whether an engine you did not test against satisfies them.

## Running it

```
java -cp <classpath> <your.Main> <scenario-file> <output-file>
```

`sample-scenario.txt` is provided so you can exercise the engine. **Its expected output is not
provided**, and you will be scored on a different scenario you will never see.

## Deliverables

1. `Main` as above. **Do not hardcode a scenario.**
2. `mvn -o test` green, with tests you believe would catch a regression in any of the five
   requirements.
3. In your final message: your `Main`'s FQN, the exact build and run commands, how many `mvn` runs you
   needed, and how you decided the way the libraries fit together.
