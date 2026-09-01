
## The scenario file — your `Main` is run against inputs you have not seen

`Main` takes exactly two arguments and must work with **any** valid scenario file:

```
java -cp <classes> com.acme.surveillance.Main <scenario-file> <output-log-path>
```

It reads events from the scenario file, feeds them through the processor in file order, and writes the
audit log to the second path. **Do not hardcode a scenario in `Main`** — supply your own scenario file
in the project for your own testing, but `Main` must read whichever file it is given.

Scenario format: one event per line, comma-separated, `#` starts a comment, blank lines ignored.

```
ORDER,orderId,trader,instrument,BUY|SELL,quantity,limitPrice,timestampMs
EXEC,orderId,quantity,price,timestampMs
CANCEL,orderId,timestampMs
QUOTE,instrument,bid,ask,timestampMs
INSTRUMENT,instrument,sector,lotSize
ROSTER,trader,desk,true|false
CLOSE,timestampMs
```

**Your engine will be scored by running it against a scenario written by someone else, and comparing
the audit log it produces against expected results you will not see.** Build for the format, not for
your own examples.
