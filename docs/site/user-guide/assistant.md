# Analyser assistant

The assistant assembles the selected record(s), the node-type map and the relevant source, then asks a
model to explain what happened in the cycle and why.

Its real power is the **round trip**. The assistant doesn't just answer in a chat window — it can
**drive the analyser** and file its findings back as things you can *see and interrogate*: a plotted
graph, a filtered table, jumped-to and flagged records, all over the **same data** you can click into
and trace to source. The conclusion arrives as a chart, not a wall of text you have to trust — so the
**verification loop is built in**. When the assistant says "the quote calculator stopped because the
venue disconnected here", it can plot that series and flag those cycles, and you watch it on screen.

![Scope a time window, select records, then Explain — or copy the seeded prompt for an external agent](../assets/assistant-explain.png)

## Fault-finding in practice

1. **Scope it.** Drag the **Time range** window to the incident, then select the suspicious records in
   the table (Shift/⌘-click for several).
2. **Ask.** Right-click ▸ *Explain selected with LLM*, or use *Explain with LLM* in the record detail.
3. **Watch it work.** The assistant reads the surrounding records, aggregates over the index to spot the
   pattern, and when it finds the cause it **plots the offending series into a graph** (captioned with
   its reasoning), **flags** the culprit records, and **filters** the table down to them — the fault
   rendered in your own instrument, not described in prose.
4. **Verify.** Click the plotted points, jump to the flagged records, trace a nodeLog line to the exact
   source method. Nothing is taken on faith; the evidence is right there to challenge.

## No API key? Copy-prompt mode

No key, or working in a different agent (Claude Code, Claude Desktop)? Hit **Copy prompt**. The copied
prompt is a complete, self-contained brief: the selected records, the node-type map, the relevant
source, **and** the log's file path, shape and per-record **byte offsets** — plus the analyser's
**action protocol** (the localhost REST endpoint, token and verbs). So an external agent can not only
reason about the evidence, it can **drive this analyser back** — seek more records, aggregate, and plot
a graph to show you the fault — exactly as the in-app assistant does.

## Setup

Open **Settings ▸ Assistant / LLM**:

- **Provider / model** — Anthropic (Claude) or OpenAI, and a model id.
- **API key** — stored locally (cleartext, single-user tool). **Never leaves your machine** and is
  never included in a shared settings file.

## Actions the assistant can take

From a reply the assistant runs bounded **actions** (within the round / per-reply caps in Settings) and
feeds the results back. An optional localhost **REST transport** (off by default) lets an external agent
drive the same verbs:

- **aggregate** — counts / rates over the index (the expensive parse is done once and shared).
- **read** — the raw text of N records around an anchor, so an agent can seek the log through the socket
  without its own file access.
- **filter** — narrow every view to the records in question.
- **graph** — plot a series or formula, with an optional `rationale` that **captions the plot** with why
  it was drawn (durable provenance).
- **goto** — select a record; `reveal:true` un-hides one the current filter is hiding.
- **flag** — bookmark the culprit records with a note, so the finding is reviewable later.

`GET /manifest` publishes a JSON schema for every verb, so a foreign agent learns the shapes up front
instead of trial-and-erroring against the structured errors.
