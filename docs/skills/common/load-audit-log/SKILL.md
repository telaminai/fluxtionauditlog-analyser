---
name: load-audit-log
description: Open this project's audit log in the analyser and confirm the graph matches it, before drawing any conclusion from what you see.
x-analyser-min-version: 1.12.0
---

# Load this project's audit log

Use this before answering any question about what the running system did.

## Steps

1. Find the log. This project writes it to the path recorded in the project profile; if you are unsure,
   ask the analyser rather than guessing:

   ```
   analyser_context {}
   ```

   The `log` and `graphPairing` sections say what is already open and which graph is paired with it.

2. Open the log together with the processor's GraphML, so structure and behaviour are read as one thing:

   ```
   analyser_open {"log": "<path>", "graphml": "<path>", "provenance": "<which system this came from>"}
   ```

   Declare `provenance` when you know it. It is how a report written from this log later says which
   system it described — nothing infers it for you.

3. **Check the pairing before concluding anything.** `context.graphPairing` states whether the graph
   applies to this log. If it does not, a node's absence is not evidence of anything, and neither is
   coverage.

## What to do when there is no log yet

Open the **graph alone**. The analyser will still tell you whether this processor is even capable of
writing an audit log:

```
analyser_open {"graphml": "<path>"}
analyser_context {}      →  graphPairing.auditLogging: enabled | not_enabled | unknown
```

`not_enabled` means the build omitted `addEventAudit()` and will write **nothing** — not a sparse log,
none. That is worth knowing before you run anything and conclude the system was quiet.
