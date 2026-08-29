---
name: run-mongoose-server
description: Start, check and stop the local Mongoose server hosting this project's processor, and find the audit log it writes.
x-analyser-min-version: 1.12.0
---

# Run the local Mongoose server

## Before anything: the preflight

A Mongoose/Fluxtion project needs a Fluxtion API key **only to regenerate** the processor. The generated
processor ships with the project, so it builds and runs without one; changing the graph does not.

```
./scripts/check-fluxtion-key.sh      # if this project ships it
```

The key is looked up in this order — the first that answers wins, and knowing WHICH one answered is
usually the thing that resolves confusion:

```
FLUXTION_API_KEY                              environment
~/.fluxtion/fluxtion.apiKeyFile               apiKey=…      [preferred]
-Dfluxtion.apiKey=…                           build property
```

If a build stops at `process-classes` with no obvious cause, this is why.

## Steps

1. Start the server using **this project's own script**, not an invented command:

   ```
   ./scripts/run-server.sh              # the starter's entry point
   ```

   TODO(bundle): if this project names it differently, correct this line when the bundle is generated.

2. Find the audit log. It is declared in the server's YAML deployment descriptor — read that rather than
   assuming a path, because the descriptor is what the server obeyed.

3. Open it in the analyser with the processor's GraphML (see the `load-audit-log` skill), declaring which
   system it came from.

4. Stop the server through the same script or its admin endpoint, so the audit sink closes cleanly.

## Do not start a second instance

If a server is already running for this project, find it before starting another — two instances writing
under one deployment produce two partial logs and no error. The same applies to the analyser: if its
status bar reads **MCP elsewhere**, another analyser window owns the endpoint and your questions are
being answered about a different log.
