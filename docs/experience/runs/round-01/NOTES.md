# NOTES.md — friction log

Task: add a node that flags a price above a threshold, run the project, show audit-log evidence it fired.

## Authoring the node

- GUESSED: how a node gets a *scalar* constructor argument in the Spring design IR. `application-context.xml`'s own comment documents only `<bean>` = node and `<constructor-arg ref="..."/>` = edge. The only `<constructor-arg value="..."/>` in the project is on `perfMon`, which is an **auditor**, not a `nodeBeans` entry — a different category. I assumed the same scalar form is legal for a graph node (`<constructor-arg value="500.0"/>` for the threshold) because a threshold has to come from somewhere. Nothing in the project says whether scalar args on graph nodes are supported or which types coerce. It worked — the generated code emitted `new PriceThresholdAlert(rootNode, 500.0)` — but I had no way to know that before running the generator.
- COULD NOT FIND: how a node emits evidence into the audit log. Nothing states the rule; I copied it from `RootNode` — extend `EventLogNode`, call `auditLog.info(name, value)`. The project never says that extending `EventLogNode` is what supplies `auditLog`, that it is the only way, or what the `name`/`value` pair becomes in the export. `RiskCheck` (the other shipped node) does **not** extend `EventLogNode`, so the two shipped example nodes disagree and neither explains why. Picking the wrong one silently produces a node that fires and leaves no evidence — which is exactly the failure this task is about.
- COULD NOT FIND: what `@OnTrigger`'s boolean return means. `RootNode` comments its `@OnEventHandler` return as `// propagate to downstream nodes`; `RiskCheck`'s `@OnTrigger` returns a bare `true` with no comment. I assumed the same propagation meaning and returned `breached`. Nothing downstream depends on the alert, so this is still unverified.
- COULD NOT FIND: whether adding a `<bean>` is enough, or whether it must **also** be listed in `fluxtionSpringConfig.nodeBeans`. The XML comment calls `nodeBeans` "explicit graph nodes" without saying what happens to a bean left out. I added it to both, defensively, so I still do not know which one mattered.
- COULD NOT FIND: why the generated processor exists in **two** places — `src/main/java/com/example/myapp/generated/MarketProcessor.java` and `src/main/resources/com/example/myapp/generated/MarketProcessor.java`. Nothing says which is authoritative or that regeneration rewrites both (it does). `AGENTS.md` and `.analyser/project.fluxtion-settings` point at the *resources* copy for the GraphML; `README.md` "What's here" mentions neither. I read the generated source to confirm my node was wired — going outside what the project explains.

## Running it

- THE PROJECT TOLD ME SOMETHING THAT DOES NOT HOLD: `README.md`, `AGENTS.md` and the `run-mongoose-server` skill all present the lifecycle as three sequential commands in one block —
  `./run-server.sh` / `./export-audit.sh` / `./stop-server.sh`. `run-server.sh` ends in `exec java … -jar` and blocks forever. Nothing anywhere says to background the first one. Taken literally the documented sequence can never reach step 2. I had to read the script to find this out.
- THE PROJECT TOLD ME SOMETHING WRONG, and it cost the most time: README says `run-server.sh` "publishes `~/.mongoose/servers/fluxtion-spring-mongoose` **while up**". It publishes the registry entry *before* the server is up, and does **not** remove it when the boot then fails. A server from before this session already held port 8181; my boot attempt died with `JavalinBindException: Port already in use` — but had already overwritten the registry entry with its own (now dead) pid.
- CONSEQUENCE — the project's own recovery advice is the advice that fails here. The skill says "If a server is already running for this project, find it before starting another — the published registry entry is the authority." After a failed boot the registry entry is the *opposite* of the authority: it names a process that never started. `./stop-server.sh` then printed
  `pid 21378 is not running — stale entry from a crash; leaving it for inspection`
  and **exited 0**, while the real server kept running and kept the port. The project offers no way out of this state.
- WENT OUTSIDE THE PROJECT: to recover I used host tools the project explicitly says it avoids needing (`lsof -nP -iTCP:8181 -sTCP:LISTEN` to find the true pid 9319, then `kill -TERM 9319`). The three scripts are careful to require "nothing but the JDK" and to work identically on Windows; the one situation I actually hit is the one they cannot handle, and it needs `lsof`/`kill`.
- GUESSED at readiness. Nothing states how to tell the server is up and has consumed `data/input.txt`. I polled for the registry file plus a listener on 8181 and then slept. Exporting too early would have produced a short log and I would have had no way to distinguish that from a node that did not fire.

## Getting evidence out

- WENT OUTSIDE THE PROJECT: the `load-audit-log` skill's whole procedure is analyser MCP calls (`analyser_open`, `analyser_context`, `context.graphPairing`). Those tools are not available in this session, so I read `logs/audit-fluxtion-spring-mongoose.yaml` directly with `grep`/`awk`. The skill has no fallback for "you have the export but not the analyser", even though the export is a plain YAML file. The pairing check it insists on before drawing any conclusion is therefore one I could not perform.
- SOMETHING WORKED AND I AM NOT SURE WHY: `auditLog.info(...)` and `auditLog.warn(...)` both land in the same flat `nodeLogs` map, keyed by the name I passed, with **no level marker in the export**. `thresholdBreach` (a `warn`) is indistinguishable in the YAML from `priceAboveThreshold` (an `info`) except by my knowing which call produced it. The config sets `logLevel INFO`; I do not know what would have been dropped at a higher level, or how a reader of the export is meant to recover severity.
- SOMETHING WORKED AND I AM NOT SURE WHY: node order inside `nodeLogs` is `rootNode`, `priceThresholdAlert`, `riskCheck` — my new node sorts between the two shipped ones. Nothing explains what determines that order, and the export gives no other ordering signal, so I do not know whether it is meaningful.

## Result

Fired as intended. `logs/audit-fluxtion-spring-mongoose.yaml`, the NVDA cycle:

```
eventLogRecord:
    eventToString: PriceEvent{symbol=NVDA, price=905.6, volume=2100}
    nodeLogs:
        - rootNode: { method: onPriceEvent, receivedEvent: PriceEvent{symbol=NVDA, price=905.6, volume=2100}}
        - priceThresholdAlert: { method: onPriceUpdate, threshold: 500.0, symbol: NVDA, price: 905.6, priceAboveThreshold: true, thresholdBreach: NVDA@905.6}
        - riskCheck: { method: onRiskCheck}
```

The other four rows (AAPL 195.30, MSFT 410.10, GOOG 155.75, AMZN 178.22) each record `priceAboveThreshold: false` and no `thresholdBreach` — so the node ran on every event and flagged only the one above 500.0.
