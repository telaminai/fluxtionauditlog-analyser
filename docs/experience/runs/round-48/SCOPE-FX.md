
## What you write — and nothing else

Your entire integration is the **Spring bean file**. Everything else on this list is fixed boilerplate.

### 1. `src/main/fluxtion/designer/application-context.xml`

Declare the components you selected from the manifests. This is the task.

### 2. `com.acme.app.Main` — about 40 lines, doing exactly five things

1. construct the generated `AppProcessor`, wire the audit log (see *Evidence*), `init()`
2. set `AlertSink.PUBLISH` to collect alerts
3. read the scenario file line by line and call `processor.onEvent(...)` with the matching
   `Events.Tick` / `Trade` / `Rate` / `Config` record
4. on a `STRATEGY,<name>` line, hand the strategy to the running engine:
   ```java
   processor.registerService(new Service<>(FeeStrategies.byName(name), FeeStrategy.class));
   ```
   The component that wants it declares `@ServiceRegistered` and the framework delivers it. There is
   nothing to look up and nothing to wire — you send it, and the node that accepts that type gets it.
5. write the collected audit records to the audit file and the collected alerts to the alert file

## What you must NOT write

- **No node classes.** Every node in this engine is supplied by a vendor. If you are writing a class
  with `@OnTrigger` or `@OnEventHandler`, stop — you have misread the task.
- **No new event types.** The four events are in `contracts.jar`. `STRATEGY` is not an event; it is an
  operator action handled in `Main` as above.
- **No output, report, aggregator or collector class.** The components already record themselves.
- **No reflection.** Every published figure is a `public` field on a vendor node, reachable directly —
  `processor.capital.fee.value`, `processor.risk.streak.streak`. If you find yourself calling
  `Field.get`, you have taken a wrong turn.
- **No fat jar.** Run with an explicit classpath.

If a requirement seems to need code beyond the above, re-read the manifests: you have probably
selected a component that does not provide what you need.
