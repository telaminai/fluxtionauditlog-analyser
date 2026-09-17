---
name: guided-start
description: Give someone a short guided tour of the analyser by driving its UI while they watch. Use when a person asks to be shown what the tool does, or says they are new to it.
x-analyser-min-version: 1.12.0
---

# Guided start — show, do not tell

You are giving a person a tour of a running analyser by **driving its window while they watch it change**.

## The one rule that matters

**You point; the screen proves.** Never state a figure the person cannot see in front of them. Drive the
view first, then say what to look at.

That is not politeness. This product exists because a record produced by execution can be checked
independently of anyone's account of it — so a tutorial where *you* are the source of the claims quietly
demonstrates the opposite. A tour where they read every number off the screen themselves demonstrates the
thing itself.

In practice:

- Before saying "as you can see…", call `analyser_context {}` and confirm the view really is showing it.
- If a step does not work, **say so plainly and move on.** A tour that glosses a failure is worth less
  than one that finds a real limitation, and this audience is being asked to trust the instrument.
- Do not read the whole log and summarise it. That is testimony. Put it on their screen.

## Point before you speak

"It is on screen" is not the same as "they know where to look" — the window is two thousand pixels wide
and they are new to it. So each beat has the same three moves:

1. **Spotlight the thing you are about to talk about:** `analyser_spotlight {"target": "…", "caption": "…"}`.
   The window dims, that one thing is cut out, and your caption points at it.
2. **Say one sentence.** The caption is a few words saying *why to look here*; the sentence belongs in
   your chat, where it is clearly yours. The spotlight shows **where** — never let it stand in for **what**.
3. **Take the screenshot that proves the spotlight is on it:** `analyser_screenshot {…}`. `context` also
   reports what is lit. If it is lit in the wrong place, say so and light it again.

A spotlight goes out by itself on any click, on Escape, and on any verb that changes the view (`open`,
`filter`, `goto`, `graph`, `topology`) — so light it **after** the view is how you want it, not before.
A target that is not on screen is revealed first; one that does not exist is refused, never lit on nothing.

**If `analyser_spotlight` is not among your tools** the analyser is older than the verb. Carry on without
it — every beat below still works — and tell them where to look in words.

## Before you start: whose data?

Call `analyser_context {}` first and branch on what is already open.

**A log is already open** → use **their** data. Coverage against their graph, a threshold on their series.
It lands far harder than a demo, and it is the same three beats with different inputs. **Never open
anything else without asking** — opening a project or a different log closes what they have, and a tour
that destroys someone's work in progress is a support ticket, not an introduction.

**Nothing is open** → use the demo set that ships with the analyser, at
`~/.fluxtion-analyser/demo/`:

| File | What it is for |
|---|---|
| `demo-quote-audit.yaml` | 10 records, and **one declared node that never logged** — beat 2 |
| `demo-quote-audit-traced.yaml` | the **traced** log: every node ran, so coverage is 1.0 — use it for the contrast, not for beat 2 |
| `demo-quote-processor.graphml` | the declared graph both were generated from |
| `demo-quote-series.yaml` | 726 records, for the chart in beat 3 |

**If that directory is missing, stop and ask.** The demo unpacks only when someone uses a demo action on
the analyser's **Start page** — you cannot install it yourself, and there is no verb for it. One click,
then carry on.

Say which data you are using, and if it is the demo, **say that it is demo data.**

## Beat 1 — what ran, and in what order

```
analyser_open {"log": "~/.fluxtion-analyser/demo/demo-quote-audit.yaml",
               "graphml": "~/.fluxtion-analyser/demo/demo-quote-processor.graphml"}
analyser_context {}          → confirm graphPairing.applies before you claim anything
analyser_topology {}         → put the graph on screen
```

**Read the pairing from `context`, not from the echo `open` returns.** If a previous session's log is
still open when you call `open`, the echo describes the graph against *that* log. `context` is computed
after, and is the one to trust.

**Spotlight it, then speak:** select a record and light its node list —
`analyser_goto {"recordIndex": 3}` then `analyser_spotlight {"target": "detail", "caption": "read this list top to bottom"}`
(or one node in it: `detail:node:<instanceId>`, and the same node on the graph: `topology:node:<instanceId>`).

Ask them to look at one record's node list. The point to make, once:

> The order you are looking at is **dispatch order**, and it was derived by the compiler before the
> program ran — not reconstructed afterwards from timestamps. A node listed after another ran after it,
> in the same cycle, on the same event. That is why this can be read as cause.

Check `graphPairing` first. If the graph does not apply to this log, say so and skip to beat 3 — a
mismatched pair makes beat 2 meaningless, and pressing on anyway would be exactly the overclaim this tool
refuses to make.

## Beat 2 — what never ran

This is the beat that is unlike a log viewer, so give it room.

```
analyser_coverage {}
```

On the demo above this reports **6 declared, 5 covered, 1 uncovered**. Let them read it off the screen.

**Spotlight it, then speak:** light the node that never logged — the echo's `neverLogged` names it —
with `analyser_spotlight {"target": "topology:node:<that instanceId>", "caption": "declared, and silent in this run"}`.
`topology:verdict` lights the Topology tab's own verdict line instead — where the analyser states how the graph
fits the log — if the sentence you want is the pairing one.

Then the point:

> That is a declared node with **no audit output in this run**. It needs the declared graph *and* the
> record — neither file can produce it alone, and no volume of log lines will, because a log has no list
> of what was supposed to happen.

**Then read them the analyser's own note, because it is the more impressive half:** it says this is
*"never logged"*, not proven *"never ran"* — a node with no `auditLog` call is silent by design. The
instrument is declining to make the stronger claim its own number would support. That refusal is the
product; a tool willing to overstate here would be worth nothing to anyone who has to defend the answer.

**The contrast, if they want it.** Re-open `demo-quote-audit-traced.yaml`: coverage is **1.0**, because
that log has invocation tracing on, so every node's participation is recorded whether or not it logged
anything. Same graph, same processor — a different thing provable. That *is* beat 2's point in one move,
and it is why the tracing switch is a build-time decision worth making deliberately.

If coverage refuses — an inferred graph cannot support it — **show them the refusal and explain it**. It
is a better demonstration than the number would have been: the instrument declining to compute something
it cannot stand behind is the whole argument, live.

## Beat 3 — answer a real question

Pick something the data actually supports; look at what keys exist rather than assuming.

There is no verb that lists available keys, so read a record first rather than guessing:
`analyser_read {"recordIndex": 0, "count": 2}`. On the demo series log, `riskMonitor.liveOrders` works.

```
analyser_open   {"log": "~/.fluxtion-analyser/demo/demo-quote-series.yaml",
                 "graphml": "~/.fluxtion-analyser/demo/demo-quote-processor.graphml"}
analyser_series {"expr": "riskMonitor.liveOrders", "crossings": {"above": 1}}
                             → 160 points; each crossing carries recordIndex and byteOffset
analyser_goto   {"recordIndex": 15}
analyser_flag   {"recordIndexes": [15], "note": "why it crossed"}
```

**`flag` takes `recordIndexes` — an ARRAY.** The singular is refused; guessing it wastes a turn in front
of the person you are showing.

**Spotlight it, then speak:** `goto` changed the view, so light the record *after* it —
`analyser_spotlight {"target": "records:row:15", "caption": "the crossing — flagged so you can come back"}`.
If you drew a chart, `graph` lights the plot and `graph:note:<n>` one numbered note on it.

They end with a bookmarked record they can reopen. That is the loop: a question, an anchor in the
evidence, and something durable.

## Then stop

Three things shown on screen beats ten described. Ask what they want to look at in their own system —
that is the question the tour exists to provoke.

## If you are tempted to

- **…summarise instead of showing.** That is the one failure that matters here.
- **…open something without asking, when they already had work open.** Don't.
- **…claim a capability you could not get on screen.** Say it exists and that you could not demonstrate
  it, or leave it out.
