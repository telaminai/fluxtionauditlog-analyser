---
name: regenerate
description: Regenerate the processor after changing the graph — the only step that needs a Fluxtion API key.
x-analyser-min-version: 1.12.0
---

# Regenerate after a graph change

Running the project needs **no key**. Changing the graph and regenerating **does**.

## Check the key the way the BUILD resolves it

The build reads, in this order:

```
-Dfluxtion.apiKey=…                system property   [WINS when set]
~/.fluxtion/fluxtion.apiKeyFile    apiKey=…          [used when the property is absent]
```

**`FLUXTION_API_KEY` is not read by the build.** A preflight script that checks that variable can report
success on a value the build never receives, so check the file:

```bash
grep -s '^apiKey=' ~/.fluxtion/fluxtion.apiKeyFile >/dev/null \
  && echo "key file present" || echo "NO key file — regeneration will fail"
```

## Regenerate

```bash
./mvnw -Pgenerate-fluxtion package
```

If the build stops at `process-classes`, the key is why.

## Confirm your change is in the graph

Both generated copies are rewritten. Read one — this is the fastest way to know a node was actually wired
rather than silently dropped for not being in `nodeBeans`:

```bash
grep -n "YourNodeName" src/main/java/com/example/myapp/generated/MarketProcessor.java
grep -n "YourNodeName" src/main/resources/com/example/myapp/generated/MarketProcessor.graphml
```

Absent from both means the bean is not in `fluxtionSpringConfig.nodeBeans`.
