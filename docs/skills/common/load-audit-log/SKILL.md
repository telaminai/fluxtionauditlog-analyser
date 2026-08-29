---
name: load-audit-log
description: Open this project's audit log in the analyser and confirm the graph matches it, before drawing any conclusion from what you see.
x-analyser-min-version: 1.12.0
---

# Load this project's audit log

Use this before answering any question about what the running system did.

## Steps

1. **The log path is written into this skill when the bundle is generated.** It is not discoverable
   from the analyser, and asking it is the wrong move:

   ```
   TODO(bundle): the generator substitutes this project's actual audit-log and GraphML paths here.
   ```

   **Do not use `analyser_context` to find an unopened log** — an earlier version of this skill said to,
   and it was false (review F3, 2026-08-29). `context.log` describes the log **already open**; on a fresh
   project it is absent, and the profile stores no log path at all (`ProjectProfile` has no such
   category). An agent following the old instruction on a fresh bundle would find nothing and conclude the
   project was broken.

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
