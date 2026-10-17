# Your first diagnosis session

One session, start to finish, with Claude Code as the client. Codex and other MCP clients work the same way; only the
way you check the registration differs (in Codex, `codex mcp get fluxtion-analyser`).

**Before you start:** the analyser is open, *External MCP/REST access* is on, and your client is registered
([Connect your client](../connect-an-llm.md)). The status bar's light reads **MCP ready**.

## 1. Check the client sees the tools

Start your client anywhere: the analyser's tools are not tied to a project directory. In Claude Code, `/mcp` should show
`fluxtion-analyser` connected with 18 tools ([the full listing](../connect-an-llm.md#check-it-is-working)).

- **The server is missing.** The client never started the bridge. Re-check the registration from
  **AI ▸ Connect an AI client…**, then restart or reload the client if it needs that.
- **Connected, but a call says *analyser not running*.** The bridge is fine and the analyser is the missing half:
  open it and turn on *External MCP/REST access*. [Troubleshooting the connection](troubleshooting.md) has the rest.

## 2. Open the log

Open it yourself (**Audit log ▸ Open log…**, or drag it onto the window), or ask the client to:

```text
> Open /path/to/audit.yaml in the analyser.
```

Opening is asynchronous: the call returns when the load starts. A client that wants to know what the load found (the
record count, the time-order report, whether a project or a rolled set is on offer) calls `analyser_context` once the
load has landed.

## 3. Ask

Say what you want, and name the server once, so the agent reaches for these tools rather than, say, grepping your
filesystem:

```text
> I have a Fluxtion audit log open in the analyser. Use the fluxtion-analyser
  tools to work out why the hedge stopped quoting.
```

That is the whole handshake. There is no path to give it and no token to paste: it is already pointed at the log you
have open.

## 4. Watch it work

The agent reads, aggregates and renders. An abridged and illustrative run, on the
[sample audit log](../assets/sample-audit-log.yaml):

```text
⏺ analyser_aggregate(metric: "count", groupBy: "dimension")
  ⎿ total 21 records
     orderUpdate 10 · ScheduledTriggerNode 3 · orderVenueConnected 2
     onMultilevelMarketData 2 · registerService 2 · LifecycleEvent 1
     marketDataVenueConnected 1

⏺ analyser_read(recordIndex: 1, count: 3)
  ⎿ hedgeConnectionMonitor: { orderVenueConnected: …, status: CLOSED, hedgeQuantity: NaN }
     hedgePositionMonitor:  { hedgePositionBreach: false, hedgeStatus: CLOSED }

⏺ analyser_graph(name: "hedge quantity",
                series: ["hedgeConnectionMonitor.hedgeQuantity"],
                rationale: "hedgeQuantity goes NaN when the venue drops")
  ⎿ {"ok":true,"applied":{"name":"hedge quantity",
      "resolved":["hedgeConnectionMonitor.hedgeQuantity"],"unresolved":[]}}

⏺ analyser_flag(recordIndexes: [1, 2], note: "hedge went NaN here")
  ⎿ {"ok":true,"applied":{"flagged":2}}

  The hedge monitor reports status CLOSED with hedgeQuantity NaN from the venue-connect
  event onward, so the quote calculator had nothing to size against. I've plotted
  hedgeQuantity as "hedge quantity" and flagged the two records where it flips.
```

**Watch the analyser while this runs.** A *hedge quantity* chart appears, captioned with the agent's `rationale`, and
two rows pick up flags. The findings land in the window, as things you can click, zoom and trace to source.

Note what the agent did not need: no file path, no token, no pasted records. It read the log through `analyser_read`.
The [sample conversations](../sample-conversations.md) are seven sessions like this one, with every tool call and
echo recorded from a real run.

## 5. Check it

The chart and the flags are the claim; check them before you repeat it.

- **Click a plotted point.** It resolves to its record.
- **Open a flagged record.** Its detail shows the event and what each node computed.
- **Click a node log line.** With source roots set, it opens the method that wrote it.
- **Say *"show me"*.** The agent lights what it means on screen, numbered, so *"1 feeds 2, and 2 never logged"* finds
  its place ([Ask it to show you](../user-guide/assistant.md#ask-it-to-show-you)).

## 6. Ask the next question

Good next questions, each answered on the canvas:

- *"Which nodes never logged in this run? Highlight the one that matters on the graph."* (`coverage`, then
  `spotlight`)
- *"When did live orders first go above 1? Point at the record."* (`series` finds the crossing, `goto` selects it)
- *"Step me through record 40 on the topology."* (`topology`)
- *"Compare the cycle before the breach with the one after."* (`read` on both)

When you have the answer, [hand it on](hand-off.md).
