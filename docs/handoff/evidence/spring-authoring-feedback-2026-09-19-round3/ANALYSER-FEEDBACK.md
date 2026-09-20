# Analyser + starter feedback from a Spring authoring session

**Date:** 2026-09-19
**Session:** an MCP client (Claude Code) driving the Fluxtion Audit Log Analyser and the Spring
authoring workflow against the demo project at `/private/tmp/fluxtion-spring-demo/project`.
**Task performed:** add a `Trade` event, a `positionNode` and a `markToMarketNode` to the Spring XML,
run `./validate.sh`, `./generate.sh`, `./run-scenario.sh`, then spotlight the XML changes in the analyser.
**Audience:** the LLM working on the analyser / starter codebase
(`/Users/greg/IdeaProjects/telamin/fluxtionauditlog-analyser`).

Everything under "Observed" was seen in this session — tool echoes, files on disk, or screenshots.
Anything that is a guess about cause is marked as such. I have not read the analyser source; the
"Wanted" sections describe behaviour, not implementation.

## Summary

| # | Area | Issue | Severity |
|---|---|---|---|
| 1 | Analyser · open/context | Graph and log stay stale after the files are regenerated in place; context mixes live and cached values | High |
| 2 | Analyser · spotlight | Targets scrolled out of view are lit in the wrong place and reported `ok` | High |
| 3 | Analyser · spotlight | Negative-height bounds returned as a successful light | Medium |
| 4 | Analyser · spotlight | `add: true` silently dropped existing spotlights; no `wentOut`; renumbered | Medium |
| 5 | Analyser · spotlight | No changeset target — line numbers must be computed by hand | Suggestion |
| 6 | Starter · generation | Stubs for newly added nodes do not extend `EventLogNode`, so they never appear in the audit log | High |
| 7 | Starter · generation | Stub quality: `arg0`/`arg1` parameter names, column-0 methods, annotation placement | Low |
| 8 | Starter · scripts | `validate.sh` / `generate.sh` print a ~1.5 KB playground URL on every run | Low |
| 9 | Analyser · producer results | Source-tab header presents an old build receipt as current after a newer run exists on disk | Medium |
| 10 | Analyser · context | `source.nodeTypes` lists only `child` although `rootNode` also logs and resolves | Low (unconfirmed) |

Suggested order: 1, 6, 2–4 (one fix), 9, then the rest.

## Evidence files

All under `/private/tmp/fluxtion-spring-demo/project/`:

- `evidence/xml-changes-spotlight.png` — first spotlight attempt; callouts 1 and 2 are over the header text, not the bean lines (issue 2), callout 5 is the negative-height one (issue 3).
- `evidence/xml-changes-spotlight-beans.png` — the two bean lines lit correctly once they were the only targets.
- `evidence/xml-changes-spotlight-all.png` — after `add: true`; only the three new targets are lit, the beans are gone (issue 4).
- `src/main/fluxtion/designer/application-context.xml` — the edited design (revision `12734c35…cb99f`; previous `08491c21…1a4f2`).
- `src/main/resources/com/example/myapp/generated/MyProcessor.graphml` — regenerated, 21 nodes.
- `target/fluxtion-run.json`, `target/fluxtion-reconciliation.json`, `target/classes/fluxtion-diagnostics.json` — the 19:39Z run; all stages ok, no conflicts, no diagnostics.

---

## 1. Graph and log stay stale after regeneration (High)

### Prompt

```text
Fix: the analyser keeps showing a stale processor graph (and log) after the file
on disk is regenerated.

REPRO (observed against the spring demo project, /private/tmp/fluxtion-spring-demo/project)
1. Open the project. It pairs evidence/latest/audit.yaml with
   src/main/resources/com/example/myapp/generated/MyProcessor.graphml
   (context.graphPairing.graphSource = "OPENED").
2. Edit the Spring XML to add two nodes and an event, run ./generate.sh, then
   ./run-scenario.sh. Both rewrite files in place at the same paths:
   - MyProcessor.graphml now has 21 nodes, including Trade, positionNode,
     markToMarketNode.
   - audit.yaml is a new run (new logTimes, 4479 bytes; the old one was 4170).
3. Call analyser_context. Nothing was reopened.

OBSERVED
- topology.totalNodes is still 18, and graphPairing still says "the graph declares
  2 of the 3 node(s) this log writes". The in-memory graph is the pre-regeneration
  one. The new nodes do not exist in Graph/Topology.
- The log is HALF refreshed, which is worse than fully stale: log.sizeBytes reports
  the new file (4479) while records/from/to still describe the old run
  (from=1789844571273; the new run is ~1789846795xxx). Context is mixing a live
  stat() with a cached parse and presenting them as one fact.
- Nothing in context, the status bar or the Project panel says either file changed
  underneath the session. The design XML, by contrast, IS re-read (design.beans
  lists the new beans, inputs.relationship flipped to "input-stale"), so the three
  artefacts of one project disagree about what "now" is.

WANTED
1. Detect that an opened graphml / log has changed on disk since it was loaded
   (content hash or mtime+size captured at load; check on context, on window focus,
   and/or a file watcher — your call, but context must never lie).
2. Surface it, do not silently swap it. This codebase deliberately refuses to
   auto-select artefacts (see the M35 graphml discovery note: "auto-selecting would
   be the convenience that reintroduces the defect"). So:
   - context.graphPairing and context.log each gain a freshness field, e.g.
     loaded {hash, mtime, size} vs onDisk {…} and a relationship of
     current | changed-on-disk | missing, plus the one call that reloads it.
   - Every field in context.log must come from ONE snapshot. Either all loaded
     values, or loaded values plus a clearly separate onDisk block. Never a live
     size next to cached record counts.
   - The status bar / Project panel shows "changed on disk — Reload" for the graph
     and the log, attributed like the existing design staleness line.
3. Make reload a first-class, cheap action: analyser_open {graphml: <same path>}
   and {log: <same path>} must re-read from disk, never short-circuit because the
   path is unchanged. Consider an explicit open {reload: "graph"|"log"|"all"}. The
   echo should say what changed (node count before/after, nodes added/removed,
   record count before/after).
4. Reloading the graph keeps profile state (named graphs, focuses, source roots)
   and re-resolves it against the new graph, saying why anything no longer
   resolves. This is the same rule close already follows. Log-derived state
   (filter, cursor, flags) follows the existing log-reopen rules.
5. After a graph reload, graphPairing's verdict is recomputed against the loaded
   log, and source.nodeTypes is re-resolved (new node classes should appear).

ACCEPTANCE
- Test: load graph A, overwrite the file with graph B at the same path. Context
  reports changed-on-disk with both fingerprints. Reload yields B's node set. The
  echo lists added/removed nodes.
- Test: same for the log. Assert no context.log field reflects the new file until
  reload, and all do after.
- Test: reopening the same path after an on-disk change does not serve the cached
  parse.
- Test: unchanged files report "current" and cost no reparse.
- Run the repro above end to end. After reload, Topology shows positionNode and
  markToMarketNode, and the pairing verdict is recomputed.

Read the existing open/close/context handlers and the design-freshness
(inputs.relationship) code first. That is the pattern to extend, not a new
mechanism. Do not add auto-reload.
```

### Raw evidence

`analyser_context` after regeneration (excerpt):

```json
"log": {"records": 12, "sizeBytes": 4479, "from": 1789844571273, "to": 1789844571477},
"graphPairing": {"graphSource": "OPENED", "loggedNodes": 3, "declaredByGraph": 2,
                 "verdict": "the graph declares 2 of the 3 node(s) this log writes"},
"topology": {"totalNodes": 18},
"design": {"revision": "12734c35…", "beans": ["rootNode","child","positionNode","markToMarketNode","fluxtionSpringConfig"],
           "inputs": {"relationship": "input-stale"}}
```

On disk at the same moment: `MyProcessor.graphml` written 20:39:52 local with 21 `<node>` elements
(including `Trade`, `positionNode`, `markToMarketNode`); `audit.yaml` written 20:39:55, 4479 bytes,
12 records with logTimes around `1789846795xxx`. The first context call of the session reported
`sizeBytes: 4170` for the same path.

---

## 2–4. Spotlight: wrong geometry reported as success

These three are one defect family: the echo says `ok` and returns bounds without checking that the
target is actually visible at those bounds after the reveal. I caught each one only by taking a
screenshot.

### Prompt

```text
Fix: analyser_spotlight reports success for targets it did not light where it said.

REPRO A — scrolled-out targets lit in the wrong place
Source tab showing application-context.xml (47 lines; the XML pane shows ~25 at the
window size used). One call with five targets:
  source:design:bean:positionNode      (line 18)
  source:design:bean:markToMarketNode  (line 19)
  source:design:line:31
  source:design:line:38
  source:design:line:45
Echo: ok:true, five entries with bounds. Targets 1 and 2 came back at y=472 and y=489.
Screenshot (evidence/xml-changes-spotlight.png): the pane had scrolled to show lines
~25-47, so lines 18-19 were above the viewport. Cut-outs 1 and 2 were drawn over the
Source tab's HEADER text ("Last build: ok …", "Inputs read at …"), with their callouts
pointing at it. The tool description says a set is all-or-nothing and things that
cannot be on screen together are refused. This set was not refused.

REPRO B — negative bounds
Same call: target 5 (source:design:line:45) returned
  "bounds": {"width":437,"height":-33,"x":1230,"y":931}
A negative height is a row clipped by the viewport, returned as a successful light.

REPRO C — add:true silently drops what was lit
1. Light two targets: source:design:line:18 and :19. Verified correct by screenshot.
2. Call again with add:true and three targets (lines 31, 38, 45).
Echo: "lit" lists only the three new targets, numbered 1-3. No wentOut field. The
screenshot confirms the two bean spotlights are gone — the reveal scrolled them off.
The tool description promises: "A standing spotlight the new reveal takes off screen
goes out, and the echo's wentOut names it."

WANTED
1. After the reveal/scroll settles, validate every target's rectangle against the
   visible rect of its own scroll container (not the window). A target that is not
   fully visible — outside the viewport, zero/negative size, or intersecting a
   different component — is NOT lit.
2. Apply the documented all-or-nothing rule to "cannot fit in one viewport": refuse
   the set, name which targets conflict, and suggest the split (e.g. "lines 18-19
   and 31-45 span 28 lines; the pane shows 25"). Never draw a cut-out over a
   component the target does not belong to.
3. Never return negative or zero width/height with ok:true.
4. add:true: compute wentOut from the same visibility check and include it in the
   echo; keep numbering stable for spotlights that stay (new ones continue the
   sequence) so the sentences already said in chat stay true. If everything already
   lit would go out, say so in the echo — the caller may prefer to refuse.
5. Bounds are computed AFTER layout/scroll completes (if this is a Swing
   invokeLater / scrollRectToVisible timing issue — a guess — wait for it).
6. Consider whether source:design:bean:<id> and source:design:line:<n> share one
   geometry path. In this session the line form worked where the bean form had
   failed, but the conditions differed (2 targets vs 5), so that is not established.

ACCEPTANCE
- Test: a set spanning more lines than the viewport is refused with the named
  conflict; nothing is drawn.
- Test: every lit target's bounds lie inside its container's visible rect; no
  non-positive dimensions ever appear in an ok echo.
- Test: add:true that scrolls a standing spotlight away reports it in wentOut, and
  surviving spotlights keep their numbers.
- Re-run repro A at the same window size (1680x950) and at 1802x1085, where all 47
  lines fit: the first is refused or split, the second lights all five correctly.
```

---

## 5. Suggestion: a changeset target for the design source

Lighting "what changed in the XML" needed a shell `grep -n` to find line numbers, then five
hand-written targets. The analyser already holds both revisions' hashes (`design.revision` and the
receipt's `xmlHash`) and re-reads the working copy.

```text
Add a spotlight target (and a read-only source verb) for design changes:
- source:design:changed — lights each changed hunk between the working copy and the
  last validated/built revision, one numbered callout per hunk, captions optional.
- analyser_source {changed: true} — returns the hunks as {fromLine, toLine, kind:
  added|removed|modified, beans: [...]} so the caller can caption them.
Needs the previous revision's TEXT, not only its hash: keep the XML content captured
at validate/build intake. If the previous text is unavailable, refuse and say why —
do not guess. Hunks that cannot share a viewport follow the rule from issue 2:
refuse and offer the split, or light them as a stepped sequence the person advances.
```

---

## 6. Starter: new node stubs do not extend `EventLogNode` (High)

### Prompt

```text
Fix: nodes added to an existing Spring authoring project are generated without audit
logging, so they are invisible in the audit log and in the analyser.

REPRO
In the demo project, add to application-context.xml:
  <bean id="positionNode" class="com.example.myapp.node.PositionNode"/>
  <bean id="markToMarketNode" class="com.example.myapp.node.MarkToMarketNode">
      <constructor-arg ref="positionNode"/>
      <constructor-arg ref="rootNode"/>
  </bean>
plus nodeBeans entries, eventTypes com.example.myapp.event.Trade and an
EventHandlerBinding Trade -> positionNode. logLevel is INFO. Run ./generate.sh.

OBSERVED
Generated stubs:
  public class PositionNode { @OnEventHandler public boolean onTrade(Trade event) { return true; } }
  public class MarkToMarketNode { …ctor… @OnTrigger public boolean onMarkToMarketNode() { return true; } }
Neither extends com.telamin.fluxtion.runtime.audit.EventLogNode. The project's
original RootNode and Child (from the same starter) both do, and RootNode's stub
even calls auditLog.info(...). With logLevel INFO the new nodes still show up in the
record as bare "{thread, method}" entries, but carry no values, and nothing tells the
author why. I had to hand-edit both classes to extend EventLogNode and run
generate.sh a second time before they logged anything.

WANTED
- When FluxtionSpringConfig.logLevel is set, a node stub created by regenerate
  matches what the initial starter output does: extends EventLogNode, and each
  generated handler/trigger body logs a minimal line (as RootNode's does).
- Same rule for initial generation and for reconcile-adds: one template, not two.
- If a class already exists and cannot be re-parented (it extends something else),
  do not rewrite it: emit a reconciliation note / diagnostic naming the node and
  saying it will not audit, with the one-line fix (implement EventLogSource).
- Record the parent in the authoring record the same way other owned members are,
  so a later hand-change is respected.

ACCEPTANCE
- Test: add a node to an existing spring-aot project with logLevel INFO; the
  generated class extends EventLogNode and a run's audit record contains a keyed
  entry from that node.
- Test: logLevel absent -> plain class, as today.
- Test: pre-existing class with another superclass -> unchanged source, one
  diagnostic, build still succeeds.
```

---

## 7. Starter: stub quality (Low)

```text
Tidy the source the starter emits for added members:
- Constructor parameters are named arg0, arg1. Use the referenced bean ids
  (positionNode, rootNode) — the fields already are.
- Generated methods are emitted at column 0 inside an indented class, and
  @Generated("fluxtion-starter") lands between the wiring annotation and the method
  at a different indent:
      @com.telamin…OnEventHandler
  @javax.annotation.processing.Generated("fluxtion-starter")
  public boolean onTrade(…) {
  Indent to the enclosing member level, and put @Generated first.
- Child.java has a dangling @Generated above blank lines and then a field
  (`@Generated … private final RootNode parent;` separated by three empty lines).
  Whitespace left behind by removed members should be collapsed.
- Event shells under the base package are generated as an empty `record Trade() {}`.
  Documented, and fine — but add a `// TODO: declare the event's fields` so it is
  not mistaken for finished output.
Stub body hashes must stay computed on the normalised body so re-indenting does not
flip ownership state for existing projects — add a test that a project generated by
the previous version reconciles with zero changes.
```

---

## 8. Starter scripts: playground URL noise (Low)

`./validate.sh` prints, and `./generate.sh` prints again, a `https://fluxtion-playground.dev/start#xml=…`
URL of about 1.5 KB of base64 on every run. In an LLM-driven loop this is the bulk of the output and
buries the one meaningful line (`XML: valid; 4 nodes, 3 edges`).

```text
Print the playground URL only with --playground-url (or write it to
target/fluxtion-playground-url.txt and print that path). Default output for validate
is the verdict line plus any findings. While there: generate.sh's build step prints
the builder's sample run (two full eventLogRecords) — put that behind --verbose too.
```

---

## 9. Source-tab header presents an old receipt as current (Medium)

### Prompt

```text
Fix: after a newer producer run exists on disk, the Source tab header and
context.design still present the previously loaded receipt without saying it is
superseded.

OBSERVED (context.design after the 19:39Z generate run)
- inputs.receipt.at = 2026-09-19T19:02:45Z (validate), build.at = 19:02:51Z,
  inputChecks.checkedAt = 19:09:02Z.
- target/fluxtion-run.json on disk: validate/regenerate/preflight/build all at
  19:39Z, outcome ok, with a different xmlHash (12734c35…) and source/record hashes.
- The header reads "Last build: ok · compiler ran: true · build Java source: match".
  "match" is the 19:09 check of the 19:02 build against source hashes that have
  since changed (three new classes). It does correctly show "XML input: input-stale"
  and "reopen diagnostics after a source or build change".

Explicit intake of producer results is the design and should stay. The defect is that
a receipt known to be older than the run file on disk is still summarised with
present-tense verdicts.

WANTED
- Track the loaded producer result's file fingerprint like issue 1. When
  target/fluxtion-run.json (or the loaded sidecar) has changed on disk, the header
  and context say "superseded on disk — reopen diagnostics", and the derived
  verdicts (Last build, buildSourceHash) are shown as "as of <time>", not as current.
- inputChecks should be recomputed when context is read (hashing is cheap), or carry
  an explicit "stale since" once the source tree's fingerprint moves. A "match" that
  is no longer true must not be displayed.
- open {discover: "diagnostics"} already lists candidates; mention it in the
  superseded message as the one call to make.

ACCEPTANCE
- Test: load a run receipt, rewrite fluxtion-run.json, read context: receipt is
  marked superseded, no present-tense "match".
- Test: change a Java source after an input check: buildSourceHash is no longer
  reported as match.
```

---

## 10. `source.nodeTypes` lists only `child` (Low, unconfirmed)

In both context reads, `source.nodeTypes` contained only
`child → com.example.myapp.node.Child`, although `rootNode` also writes to the log and
`RootNode.java` sits in the same source root. I did not investigate — it may be populated lazily
from selection (`child` was the first logged row) rather than from the graph. If it is meant to be
the full resolved map, `rootNode` is missing; if it is lazy, the field name or a note should say so.
After issue 1's reload it should also pick up `positionNode` and `markToMarketNode`.

---

## What worked well (keep)

- `analyser_context` is a very good first call: project, log, pairing verdict, runbook pointers and
  design freshness in one read made the session self-orienting.
- The runbook pointers (`RUNBOOK.md`, `LOCAL-DEMO.md`) resolved and were sufficient to run the
  whole authoring loop without guessing.
- Reconciliation did exactly what the runbook promised: implemented bodies kept, `Child` and
  `RootNode` reported unchanged, no conflicts, and the acceptance scenario still passed.
- `design.inputs.relationship` flipping to `input-stale` on the XML edit is the right behaviour —
  issues 1 and 9 ask for the same treatment for the graph, the log and the run receipt.
- The `auditLoggingNote` ("did not log is never by itself did not run") pre-empted a wrong
  conclusion about the third logged node.
- Screenshots painted by the app made the spotlight defects catchable at all.

---

# Addendum — later in the same session (charts, markers, Mongoose)

Companion document: `AUTHORING-DOCS-FEEDBACK.md` (same directory) covers the authoring *docs* and the
Mongoose re-host. The items below are further analyser observations. Numbering continues from the table
at the top.

| # | Area | Issue | Severity |
|---|---|---|---|
| 11 | Analyser · graph | No group-by: one series per entity needs a node code change | High |
| 12 | Analyser · graph markers | Buy / sell marker counts do not match the data; marker `when` semantics undocumented | Medium |
| 13 | Analyser · graph | Step series stop at their last sample instead of carrying to the window end | Medium |
| 14 | Analyser · graph | Explanation box and pinned-note strip are drawn over the data; caption is clipped | Low |
| 15 | Analyser · graph | Formula language is undiscoverable from the tool surface | Medium |
| 16 | Fluxtion runtime | `EventLogConfig.toString` embeds a lambda identity hash — the only thing that stops two runs being byte-comparable | Low, high leverage |

Evidence: `evidence/position-by-instrument.png` (explanation box over the NVDA line),
`evidence/position-by-instrument-2.png` (clean), `evidence/pnl-trade-vs-price-spotlight.png` (markers, notes,
four spotlights — all four landed correctly; spotlight across the records table and a graph worked well).

## 11. No group-by on a logged key (High)

```text
Add a way to split one logged key into a series per value of another key in the same node log.

OBSERVED
positionNode logs {symbol: AAPL, quantity: …, position: 150}. Series are addressed as
"instanceId.key", so positionNode.position is ONE line mixing every instrument. To plot position by
instrument I had to change node code to also log position_AAPL / position_MSFT / position_NVDA (and
pnl_<SYMBOL> in markToMarketNode), rebuild and re-run. That is three instruments. A real book has
hundreds, and the author cannot know at design time which split someone will want.

WANTED
- graph / series accept a split, e.g. {series: ["positionNode.position"], by: "positionNode.symbol"}
  -> one series per distinct symbol, labelled by value, each using only the records where the by-key
  had that value. Same-record (STRICT) pairing of value and by-key.
- A cap with an explicit truncated flag and the distinct-value count, like crossings.
- by-key values are text: this must not require the key to be numeric.
- Works in analyser_series too (stats per group) and in markers (payload already reads text keys).

ACCEPTANCE
- On evidence/mongoose-run1/audit.yaml, {series: positionNode.position, by: positionNode.symbol}
  yields three series equal to positionNode.position_AAPL / _MSFT / _NVDA point for point.
```

## 12. Marker counts do not match the data (Medium)

Graph "PnL by instrument" on `evidence/trades/audit.yaml`, markers declared as:

```json
{"label": "buy",  "when": "positionNode.quantity > 0", "glyph": "triangleUp",   "y": "series:markToMarketNode.totalPnl"}
{"label": "sell", "when": "positionNode.quantity < 0", "glyph": "triangleDown", "y": "series:markToMarketNode.totalPnl"}
{"label": "price update", "when": "rootNode.price", "glyph": "diamond", "y": "axis"}
```

Legend showed **buy (11), sell (10), price update (8)**. The log has 11 `Trade` records — **5 buys and 6
sells** — and 8 `PriceUpdate` records. So the bare-key marker is right (8) and the two condition markers are
not: 11 + 10 = 21 fires from 11 trades.

Not investigated. A guess, marked as a guess: the condition is evaluated with carry-forward (LOCF) on
records where `positionNode` did not log, so a past quantity keeps the condition true on later
`PriceUpdate` records. The `graph` tool documents `resolve: LOCF | STRICT` for `exprs` but not for
`markers.when`.

```text
WANTED
- State the resolve semantics of markers.when in the tool description, and make the default for a
  condition STRICT: fire only on records where every referenced key was logged. A marker means "this
  happened here"; carry-forward turns it into "this was still true here".
- Accept resolve per marker, as exprs do.
- Clicking a legend count (or a verb) lists the recordIndexes that fired, so a doubted count can be
  checked in one step. I doubted the correct count (8) because I had miscounted my own scenario; the
  analyser gave me no quick way to settle it, and analyser_aggregate did, later.

ACCEPTANCE
- On evidence/trades/audit.yaml the three markers above report 5, 6 and 8.
```

**Correction to something I told the user:** I first said the scenario had "12 trades and 7 prices" and that
the 8 price markers looked wrong. It has 11 and 8; the price markers were right. The provenance string set on
that log carried my wrong counts.

## 13. Step series end at their last sample (Medium)

In "Position by instrument" the MSFT line stops at its last trade, part-way across the plot, while AAPL runs
to the right edge. MSFT's position is still 100 at the end of the window; the chart reads as if the position
ceased to exist. For `style: step` the last value should be carried to the window end (or to the last record
in scope), drawn in a lighter stroke if you want to distinguish "held" from "observed". `series` with
`resolve: LOCF` already has the concept.

## 14. Annotations drawn over the data (Low)

- The `explanation` box is placed bottom-left inside the plot area and covered the NVDA series (the lowest
  line). I removed the explanation to make the chart readable, which defeats its purpose ("survives an
  exported PNG").
- The pinned-note text strip at the bottom overlaps the low end of the series and truncates each note with
  "…" at this width (1802 px). The note text is the finding; losing its tail loses the point.
- The rationale caption under the plot is clipped on the right.

Wanted: put explanation and note text outside the plot rectangle (below it, wrapping), or place the box in
the emptiest quadrant.

## 15. Formula language is undiscoverable (Medium)

I guessed `positionNode.quantity > 0` and it parsed. From the tool surface I cannot tell: which operators and
functions exist, how text keys compare (`positionNode.symbol == "AAPL"`?), what a condition returns on a
record where a key is absent, or whether conditionals exist (which would have been a workaround for #11).
The `unresolved` echo is good for keys; there is nothing equivalent for formulas.

Wanted: a read-only verb or a documented block in the `graph` / `series` descriptions giving the grammar,
the functions, text comparison and absent-key behaviour; and formula errors echoed with position, the way
unresolved keys are.

## 16. `EventLogConfig.toString` breaks run-to-run comparison (Low, high leverage)

Two boots of the Mongoose-hosted processor over the same `data/market.csv` gave 36-record audit logs that
are identical after normalising time fields and thread names — except 2 `EventLogControlEvent` records:

```
eventToString: EventLogConfig{level=null, logRecordProcessor=com.example.myapp.MongooseMain$$Lambda/0x0000007001001800@28dada3f, …}
eventToString: EventLogConfig{level=null, logRecordProcessor=com.example.myapp.MongooseMain$$Lambda/0x0000007001001800@5acc4af3, …}
```

Print the listener's class name without the identity hash (or nothing). Then "diff two audit logs" is a
one-line regression test, and the analyser could offer it as a verb: open two logs of the same processor,
normalise clocks, report the first differing record and node. That is the replay-and-diff step the
authoring loop is missing.

## Also confirmed since the first write-up

- **#1 is avoidable, and only by luck.** Reopening the same graphml and log paths with `analyser_open` did
  re-read them from disk (the echo reported 21 graph nodes), so the manual workaround works. The defect is
  that nothing tells you to do it.
- **Spotlight across panes works.** Four targets — two records rows and two graph notes — lit correctly in
  one call and the second row was selected, filling the detail pane with the exact values being described.
  The geometry problems in #2–#4 look specific to the source pane's scrolling.
- **`analyser_aggregate` and `analyser_series` settled two arguments with myself** (event counts; PnL
  endpoints) in one call each. They are the most trustworthy part of the surface.

## Second addendum — refreshing the analyser after the Mongoose re-host

Evidence: `evidence/analyser-current-state.png` (unpaced Mongoose run, chart collapsed),
`evidence/analyser-current-state-paced.png`, `evidence/analyser-mongoose-pnl-spotlight.png` (paced run).

| # | Area | Issue | Severity |
|---|---|---|---|
| 17 | Analyser · graph | Time is the only x-axis; a real-time host puts a whole run in one millisecond and the chart collapses to a vertical line | High |
| 18 | Analyser · graph notes | Notes given by `recordIndex` are stored as absolute time, so they silently vanish when the graph is pointed at another run of the same scenario | Medium |
| 19 | Analyser · graph markers | More evidence for #12: identical business records, different marker counts | Medium |
| 20 | Analyser · open | `open {design, diagnostics}` in one call echoes the design block computed BEFORE the diagnostics were loaded | Low |
| 21 | Analyser · graph | Legend covers the top-right of the plot, where a rising series ends | Low |

**17.** Mongoose dispatched all 19 events within ~5 ms, 14 of them stamped `…147`. With millisecond `logTime`
as the x-axis the PnL chart is one vertical line (`analyser-current-state.png`). I worked around it by
appending the CSV rows to the live feed every 30 ms (`./run-server.sh --drain --paced`), which is a fine demo
of the tailing feed but is pacing the *system* to suit the *chart*. Wanted: an x-axis mode by record sequence
(`x: "record"`), equal spacing per record, time shown on hover. Dispatch order is already total and already
what the analyser treats as causality, so it is the more honest axis for a deterministic processor anyway.
This matters more than it looks: the faster and more production-like the host, the less usable the charts.

**18.** I pinned two notes with `recordIndex: 26 / 29` on the unpaced Mongoose log, then opened the paced run
and refreshed the graph. Same records, same indexes, same byte offsets — and the notes were gone, with
nothing in the echo. They had been resolved to the first log's timestamps, which fall outside the new
window. Wanted: keep the anchor the caller gave (`recordIndex` stays a record anchor and re-resolves on
refresh), and have the echo list notes that no longer resolve or fall outside the pinned window, the way
`unresolved` lists keys.

**19.** The same graph definition gave **buy 11 / sell 10** on `evidence/trades/audit.yaml` and
**buy 9 / sell 10** on the Mongoose logs. The 19 business records in those logs are identical after
normalising time and thread (verified by diff); truth is 5 buys, 6 sells. The Mongoose logs differ only in
their non-business records (11 `ExportFunctionAuditEvent`, extra control events). So the count depends on
what other records surround the trades — consistent with the carry-forward guess in #12, and proof the
markers are not a function of the trades alone.

**20.** Calling `analyser_open` with both `design` and `diagnostics` returned a `design` block still showing
the 19:02Z receipt and `input-stale`, next to a `diagnostics` block showing the 20:22Z receipt and
`input-current`. A following `context` call is consistent. Compute the echo after all parts of the call
have applied.

**21.** In `analyser-current-state-paced.png` the final rise of `totalPnl` to 3160 runs up behind the legend
box. Place the legend outside the plot rectangle, or in the emptiest corner.

Also worth keeping: **#9 has a clean workaround** — `open {diagnostics: target/fluxtion-validation.json}`
replaced the 19:02Z receipt with the 20:22Z one and the header went to `input-current` with matching
hashes. The problem remains that nothing prompts for it.

## Third addendum — the principal desk build, and authoring a report

Context: `desk/ITERATIONS.md` (8 cycles, 2 failures, neither a logic bug) and the report
`evidence/desk-validation-report-v2.pdf` (first attempt: `evidence/desk-validation-report.pdf`).

| # | Area | Issue | Severity |
|---|---|---|---|
| 22 | Starter · generation | Every generated handler returns `true`, including signal handlers on a node that has dependents | **High** |
| 23 | Starter · contract | `parentUpdateCallback` is documented without a type; the error does not say what form is expected | Medium |
| 24 | Analyser · report | A `topology` section is silently omitted from the PDF — no picture, no warning | High |
| 25 | Analyser · report | A finding is always headed "WHAT IS WRONG" / "LIKELY CAUSE / SUGGESTED FIX" | Medium |
| 26 | Analyser · report | `rowWhen` cannot compare text; `series` sections are a stub in the PDF; a table cannot pick non-contiguous records | Medium |
| 27 | Analyser · report / graph | Title truncated at ~50 characters; "WRITTEN AGAINST" truncated; a band over a boolean logged as text never draws | Low |
| 28 | Analyser · project | Reopening the project closes log, graph, design and drops the session's charts, with nothing offering to restore them | Medium |

**22.** `OrderGateway` owns the `haltTrading` / `resumeTrading` signals and `PositionKeeper` triggers on it.
The generated `onSignalHaltTrading` returns `true`, so as generated a halt signal marks the gateway dirty,
`PositionKeeper`'s parent callback fires and **the previous execution is booked a second time**. I caught it
by reading the stub. Mutant `signal-propagates` then showed it for real: position 800 -> 1600 and a phantom
-800 hedge order sent to the exchange, on a *halt*. Green build, no diagnostic. Wanted: generated handlers
on a node with dependents return `false` (or carry `// TODO: return true only when state others depend on
changed`), and the runbook's verify step says so. This is the most dangerous default I met.

**23.** `contract.md` lists `parentUpdateCallback` as "optional" with no type. I passed a method name; it is a
boolean. The diagnostic was `Invalid Spring declaration: onExecution` — my value, not the expected form.
`--summary-detail` printing `'callback': False` is what gave it away. Wanted: types in the contract table,
and `fix:` text that names the accepted values. Related and good: the generated callback
`@OnParentUpdate(value="hedgeFills") onParentHedgeFills(HedgeFillHandler)` was exactly what two-parent
nodes need, and `DATA` -> `@NoTriggerReference` is enforced by reconciliation ("Construct annotations were
edited") — that killed a mutant at build time. Both deserve a worked example in the docs.

**24.** Both report builds included `{kind: "topology", focus: "Desk: everything that reaches the hedger"}`;
the focus had just been saved with `saveFocusAs`. The echo counted the section, `warnings[]` was empty, and
the PDF contains no picture and no placeholder. My first narrative said "the graph above" about nothing.
`series` sections at least print "(a recorded gap)". Wanted: render it, or warn and print a placeholder.

**25.** My seven findings are "this is the moment that proves the behaviour" — all expected. The PDF heads
each "WHAT IS WRONG - RECORD #42". For a validation report that reads as seven defects, so I had to put a
disclaimer in the notes. Wanted: a finding `kind`/tone (defect | observation | evidence) chosen at flag
time, driving the heading. The narrative banner ("THE AUTHOR'S ACCOUNT, NOT LOG EVIDENCE") is excellent and
should stay exactly as it is.

**26.** `rowWhen: orderGateway.decision == "REJECT"` -> "'"REJECT"' is not a duration". No text comparison,
so a table cannot highlight by status. A table's `call` is a contiguous `read`, so "every record where the
hedger sent an order" cannot be one table; I used marker tables instead (which are very good — payload and
record per marker). Wanted: text equality in formulas, and a table over a filter or a marker's records.

**28.** The user reopened the project to look at the new graph and lost every view. `context` afterwards had
no log, no graph, no design and no `graphs`. It is documented as a session boundary, but from the person's
side it is data loss. Wanted: the project remembers its last session (log, graphml, design, diagnostics,
graphs with their pins) and offers "restore last session" on open; never automatic, per the M35 rule.
