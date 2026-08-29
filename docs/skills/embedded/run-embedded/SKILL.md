---
name: run-embedded
description: Start and stop this project's processor in-process, and find the audit log it writes, when the runtime is embedded rather than server-hosted.
x-analyser-min-version: 1.12.0
---

# Run the processor in-process

For a project that embeds the Fluxtion runtime rather than deploying to a server. The host is
`com.telamin.fluxtion.runtime.connector.DataFlowConnector`.

## The shape (verified against fluxtion-runtime 1.0.13)

`DataFlowConnector` composes the running system:

- `addDataFlow(DataFlow)` — the compiled processor
- `addFeed(EventFeedAgent<?>)` — where events come from
- `addSink(String, Consumer<T>)` — where output goes
- `registerService(T, Class<S>, String)` — services the graph exports or consumes

The audit log is a sink like any other. `connector.FileMessageSink` takes a path and implements
`Lifecycle` (`init` / `start` / `stop`), so the log file is opened and closed with the run.

## Steps

1. Start it with this project's own entry point.

   TODO(bundle): name the class and command. Do not invent one — an embedded host has no standard main.

2. **Confirm the log is actually being written before doing anything else.** An embedded run with no
   audit sink attached, or a build without `addEventAudit()`, produces an empty file rather than an
   error. Check the file is growing; if it is empty, open the GraphML in the analyser and read
   `graphPairing.auditLogging` — it will say `not_enabled` if the build is the problem.

3. Stop it through the same entry point, so the sink's `stop()` runs and the file is closed cleanly. A
   log truncated by a killed process is still readable, but its last cycle may be partial.

## Why the stop matters more here than on a server

There is no admin endpoint to ask. The process is the deployment, so the only thing that closes the sink
is the process shutting down properly.
