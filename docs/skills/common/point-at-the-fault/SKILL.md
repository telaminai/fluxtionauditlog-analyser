---
name: point-at-the-fault
description: Check an open audit log for one class of fault and finish by pointing at the evidence on screen. Use when a person asks whether their log shows a particular fault (a limit reached, a value gone NaN, a node that stopped logging), or asks you to write a runbook that checks for one — or asks for a report showing which record proves a fault you found or fixed.
x-analyser-min-version: 1.12.0
---

# Point at the fault — a runbook that ends in "here it is"

A runbook written to find **one class of fault** walks the log, and when it finds the fault it does not
describe where it is. It **lights it**: the record, the node, the series it matched, each with a caption
that quotes what it matched on. *"Here is what I found"* becomes *"here it is"* — and someone new to the
tool learns where that kind of fault shows up at the same moment they learn they have one.

This skill is the **shape**. Copy it for your own fault class; the worked example at the end is one.

## The two rules that make it safe

The person watching cannot tell a demonstration from a diagnosis. These two rules are what let them.

1. **The pointer follows the evidence, not the script.** Light the target your query actually returned —
   the `recordIndex` it gave you, the node that logged the value — and build the caption **from the
   match** (*"liveOrders 2 = limit 2"*), never from what you expected to find. A step that lights the same
   place whatever it found is a demo.
2. **Finding nothing is a result.** Say what you checked, over how many records, and that it did not
   occur — and **light nothing**. The temptation is to point at something anyway so the run looks
   productive. Don't: a spotlight on a healthy record tells the person their system has a fault.

## The shape — four steps

### 1. State the fault as something checkable

Not *"is risk OK?"* but a predicate over logged values: *"`riskMonitor.liveOrders` reached
`riskMonitor.limit`"*. If you cannot write it as a key, a threshold or a formula, you are not ready to
look — ask what would count.

Call `analyser_context {}` first. **Use the log that is open**; never open another without asking — that
closes their work. If nothing is open, say so and stop.

### 2. Find it — let the analyser scan, do not page through records

| The fault is… | One call |
|---|---|
| a value crossing a threshold | `analyser_series {"expr": "<node.key>", "crossings": {"above": <n>}}` — each crossing comes back with its `recordIndex` and `byteOffset` |
| a condition between values | the same, with a formula: `"expr": "a.x - b.y"`, or `if(...)` |
| how often, or when, something happens | `analyser_aggregate {"metric": "count", "groupBy": "hour", "filter": {…}}` |
| a node that never logged | `analyser_coverage {}` — its answer names them, and says *never logged* is not *never ran* |

There is no verb that lists keys. If you do not know the key, read one record first —
`analyser_read {"recordIndex": 0, "count": 2}` — rather than guessing.

### 3. Confirm it on the record itself

A scan tells you *where*. Before you point, read the record and make sure it says what you are about to
claim: `analyser_read {"recordIndex": <n>, "count": 1, "fields": ["<node>.*"]}`. The values that come
back are what your caption will quote. If they do not bear the claim out, **you have not found it** — go
back to step 1, or report that you found nothing.

### 4. Point — after the view is how you want it

Light the evidence as **one call**, so it appears together and numbered:

```
analyser_spotlight {"targets": [
  {"target": "records:row:<n>",            "caption": "<what matched, quoted from the record>"},
  {"target": "topology:node:<instanceId>", "caption": "<the node that logged it>"}]}
```

- A `records:row` is brought on screen for you, even if a filter was hiding it.
- Spotlights are **numbered on screen**; say the numbers in your sentence — *"1 is the record where it
  happened; 2 is the node that reported it."*
- Everything in one call must be on screen **together**. A topology node and a chart note live on
  different tabs — light those one after the other. A refusal tells you which pair.
- `filter`, `goto`, `graph`, `topology` and `open` put a spotlight **out** — so point **last**.
- Then take the shot that proves it: `analyser_screenshot {…}`. `analyser_context {}` also lists what is lit.

**If `analyser_spotlight` is not among your tools** the analyser is older than the verb. Select the record
with `analyser_goto {"recordIndex": <n>}` and say where to look in words. Everything else here still works.

### And make it last

A spotlight is gone at the next click. If the finding is worth keeping, write it where it stays:
`analyser_flag {"recordIndexes": [<n>], "note": "<what>", "fix": "<where to look>"}` — `recordIndexes`
is an **array**. A flag shows in the table, on the graph and in an exported report. Pointing is how you
show someone; flagging is how they find it again.

## What your captions may and may not say

A caption is **your** words — the overlay tags it *assistant* — and it sits on top of the analyser's own
screen. So it says **why to look here**, in a few words taken from the match. It never states a
conclusion the thing under it does not show: if the line you lit reads *"fits this log (5/5)"*, a caption
saying *"coverage is incomplete"* is false, however true it is elsewhere. When the caption and the screen
disagree, the person should believe the screen — do not put them in that position.

## Worked example — *"did live orders reach the risk limit?"*

On the demo series log (`~/.fluxtion-analyser/demo/demo-quote-series.yaml`, 726 records, with
`demo-quote-processor.graphml`) — **say that it is demo data**:

```
analyser_series {"expr": "riskMonitor.liveOrders", "crossings": {"above": 1}}
        → 160 points; ONE crossing: recordIndex 15, value 2
analyser_read   {"recordIndex": 15, "count": 1, "fields": ["riskMonitor.*"]}
        → riskMonitor.liveOrders: 2 · riskMonitor.limit: 2          ← the claim, on the record
analyser_spotlight {"targets": [
  {"target": "records:row:15",            "caption": "liveOrders 2 = limit 2 - first time it is reached"},
  {"target": "topology:node:riskMonitor", "caption": "the node that reports it"}]}
analyser_flag   {"recordIndexes": [15], "note": "live orders reached the risk limit (2 = 2)",
                 "fix": "riskMonitor.limit - is 2 the intended cap?"}
```

And the same runbook **finding nothing**:

```
analyser_series {"expr": "riskMonitor.liveOrders", "crossings": {"above": 99}}
        → 160 points; crossings: none (max 6)
```

> I checked `riskMonitor.liveOrders` across all 160 points it logged in this run; it never went above 99
> — its maximum was 6. Nothing to show you.

No spotlight. That sentence **is** the result.

## Writing it up — the symptom from the record, the cause from the change

A report is read by someone who has **only what you hand them**: not your session, not your context, often
not the source. A record proves what the system **did**. It cannot prove what the code **says**. So a report
that says *"this record proves the fault"* makes two claims, and each needs its own evidence:

1. **The symptom — cite the record.** File and line, or `recordIndex`, and the values quoted from it:
   *"in record N, `a.x` is 12 while `b.y` is 15 for the same event"*. Say what a correct record would
   show and how you know — from the inputs the record itself carries where possible, so the reader can
   redo the arithmetic.
2. **The cause — cite the change.** The cause is a claim about code, so its evidence is **the diff of your
   fix, in the report**, not a description of it — plus the matching record from a run of the fixed build,
   showing the symptom gone.
3. **Label what comes from the source.** Anything the log cannot show — which node reads which, a rule
   that fires only once — mark as *from the source*, so the reader knows what they can check in the log
   and what they are taking from the code.
4. **Make the pack stand alone.** It holds everything you cite: the report, the diff, the logs — and
   **copies of the source files you changed, before and after**, plus any file whose behaviour you cite
   *from the source*, so the reader can check the diff against the code rather than take it from you. If
   one export holds more than one run, say where each run starts and which build produced it.

A reader who can verify the symptom in the log and the cause in the diff does not need to trust you. That
is the point of the report.

## If you are tempted to

- **…point at the usual place because the query came back empty.** That is rule 2. Light nothing.
- **…write the caption before you have the record.** Then it is a script, not a finding. Rule 1.
- **…light six things because six is allowed.** Two that carry the argument beat six that decorate it.
- **…claim the node "never ran" because it is not in the record.** Absence from a sparse log says nothing;
  `analyser_coverage` tells you which regime you are in, and says so itself.
