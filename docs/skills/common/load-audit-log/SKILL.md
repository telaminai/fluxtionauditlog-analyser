---
name: load-audit-log
description: Open this project's audit log in the analyser and confirm the graph matches it, before drawing any conclusion from what you see.
x-analyser-min-version: 1.12.0
---

# Load this project's audit log

Use this before answering any question about what the running system did.

## Steps

1. **The export command, log paths and graph paths are written into the generated project.** For a
   Mongoose bundle, run its export command first: Chronicle capture is not itself analyser-readable
   YAML. The unopened path is not discoverable from the analyser, and asking it is the wrong move:

   ```
   TODO(bundle): substitute the bundle's exact export command (where required), concrete YAML-export path and GraphML path here.
   ```

   **One log per processor.** A bundle may host several processors, and each is captured and exported
   on its own — `logs/audit-<processor>.yaml` — beside its own GraphML. Open the export of the
   processor your question is about, with **that** processor's graph; a log paired with another
   processor's graph fails the pairing check in step 4, and a node missing from the wrong log is not
   evidence of anything. If the bundle hosts several processors and the export produced fewer files,
   the missing processors were not exported — say so rather than reading one log as the whole system.

   **Do not use `analyser_context` to find an unopened log**: `context.log` describes the log already
   open, and the profile stores no log path.

   If this conversation designed or changed the graph and the question needs more than one analyser
   read after opening (a chart, several records or nodes, before/after, several processors), stop after
   this step and follow *Diagnose in a fresh context* below. A single check stays here.

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

   The log loads in the background, so the `open` echo cannot judge the graph against it: from analyser
   1.13.1 the echo says `pairing: pending`; earlier versions echoed a verdict about the PREVIOUS log, or
   "no log is open". Either way, `analyser_context.graphPairing` is the authority — read it after the
   open, not the echo.

## Diagnose in a fresh context

Once the build has succeeded and the run has written its export, give the diagnosis to a subagent
(`subagent_type: "general-purpose"`, `run_in_background: false`) instead of doing it here: this
conversation still carries everything read while designing, and every further request pays for it again.
Wait for its reply and do nothing in the analyser meanwhile, even if it runs in the background. The
subagent's whole prompt is this brief, filled in from what you already know, at most 2,000 characters:

```
You are the fresh context for diagnosing a Fluxtion run. Do not delegate, edit files or read an export
as text; running audit-compare.py on a before/after pair is allowed. Use the analyser MCP tools.
project: <absolute project root>
logs: <processor>  <logs/audit-<processor>.yaml>  <its GraphML>   (one line per processor)
provenance: <which system and run>
change: <each node or edge declared or changed: bean id, class, from -> to; or none>
predicted: <the record facts you expect if the change works>
question: <what the person asked, with any chart or report to leave on the canvas>
For each log in turn (the analyser holds one open log):
1. If analyser_context.project.active is not this project, analyser_open {"project"} alone first.
   Then analyser_open {"log", "graphml", "provenance"} for this log.
2. Read analyser_context {"sections": ["log", "pairing"]}: note log.identity, graphPairing, timeOrder,
   producer, inFlight and dispatchOrder as stated, or "not stated".
3. Answer this log's part from read {fields}, series and aggregate; cite each fact by recordIndex.
Reply, per processor: the answer; each fact and its recordIndex; its step 2 qualifications; what you
left on the canvas; what the log cannot show.
```

The brief carries pointers and the question, never the evidence: the subagent reads the identity verdict,
pairing, time order, producer findings and in-flight state itself, for each log while that log is open,
because a copy taken now is stale once the file is rewritten. Give the person its reply with those
qualifications, without repeating its reads here. If it reports a defect, fix it here, run again and send
a new brief.

## What to do when there is no log yet

Open the **graph alone**. The analyser will still tell you whether this processor is even capable of
writing an audit log:

```
analyser_open {"graphml": "<path>"}
analyser_context {}      →  graphPairing.auditLogging: enabled | not_enabled | unknown
```

`not_enabled` means the build omitted `addEventAudit()` and will write **nothing** — not a sparse log,
none. That is worth knowing before you run anything and conclude the system was quiet.
