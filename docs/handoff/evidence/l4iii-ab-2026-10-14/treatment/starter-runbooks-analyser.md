# Shared evidence canvas

Open .analyser/project.fluxtion-settings in your analyser. Use its Connect AI client action to configure this project's local LLM for that actual instance; no fixed port or machine-specific .mcp.json ships in this download.

Read context.runbooks, then open the relevant source/design and producer reports. The Spring XML is at src/main/fluxtion/designer/application-context.xml. Generated graphs, diagnostics and audit logs are expected outputs until a successful producer run creates them. Never treat a path as proof they exist or are current.

Load matching graph/log evidence and compare measured values with predictions. Use reports for record-anchored evidence and spotlights for attributed explanation. A screenshot checks placement; an action echo alone cannot establish what the human sees.

When reopening the project, inspect loaded versus saved state and explicitly restore available evidence. Missing or changed inputs must be resolved before reusing old conclusions. The analyser does not build, deploy or run your application.

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

