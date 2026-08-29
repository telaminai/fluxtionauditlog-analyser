---
name: load-audit-log
description: Open this project's audit log in the analyser and confirm the graph matches it, before drawing any conclusion from what you see.
x-analyser-min-version: 1.12.0
---

# Load this project's audit log

Use this before answering any question about what the running system did.

## Steps

1. **The export command, log path and graph path are written into the generated project.** For a
   Mongoose bundle, run its export command first: Chronicle capture is not itself analyser-readable
   YAML. The unopened path is not discoverable from the analyser, and asking it is the wrong move:

   ```
   TODO(bundle): substitute the bundle's exact export command (where required), concrete YAML-export path and GraphML path here.
   ```

   **Do not use `analyser_context` to find an unopened log** — an earlier version of this skill said to,
   and it was false (review F3, 2026-08-29). `context.log` describes the log **already open**; on a fresh
   project it is absent, and the profile stores no log path at all (`ProjectProfile` has no such
   category). An agent following the old instruction on a fresh bundle would find nothing and conclude the
   project was broken.

2. Make sure this project's profile is active. `analyser_context.project.active` tells you whether it
   is. If it is absent or names a different project, open this project **in a call of its own** first:

   ```
   analyser_open {"project": "<absolute path to this project root>"}
   ```

   Do not combine `project` with `log` or `graphml` in one call. A project switch is deliberately a
   session boundary: the analyser ignores the other open parameters and names them in `ignored`, so
   that a log cannot remain open under a different project's settings.

3. Open the log together with the processor's GraphML, so structure and behaviour are read as one thing:

   ```
   analyser_open {"log": "<path>", "graphml": "<path>", "provenance": "<which system this came from>"}
   ```

   Declare `provenance` when you know it. It is how a report written from this log later says which
   system it described — nothing infers it for you.

4. **Check the pairing before concluding anything.** `context.graphPairing` states whether the graph
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
