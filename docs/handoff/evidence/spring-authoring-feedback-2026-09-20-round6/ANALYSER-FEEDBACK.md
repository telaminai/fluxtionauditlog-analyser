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

Suggested order for these ten: 1, 6, 2–4 (one fix), 9, then the rest.

## Master index — all 40 issues, plus proposal P1, in the order I would fix them

This file grew over one long session: the ten issues above, then addenda (11–40) further down, plus proposal P1. **Read
to the end.** Each issue has evidence and a "wanted" where it is described. Companion documents, same
directory: `AUTHORING-DOCS-FEEDBACK.md` (docs, the Mongoose re-host, and *"The analyser as a shared canvas"* —
read that section first, it reframes several items below as new uses rather than defects),
`desk/ITERATIONS.md` (the principal-desk build) and `vendor/PREDICTIONS.md` (vendor jar integration).

**A. Breaks a promise the product makes — fix first**

| # | Component | Issue |
|---|---|---|
| 29 | Starter | A `nodeBeans` class found only in a dependency jar is overwritten by an empty generated shell; build stays green |
| 30 | Starter | Dependency jars are not in the run receipt; a tampered jar passes every freshness check |
| 6 | Starter | Nodes added to an existing project do not extend `EventLogNode`, so they never log |

**B. The canvas says something untrue — the LLM then repeats it with confidence**

| # | Component | Issue |
|---|---|---|
| 1 | Analyser | Graph and log stay stale after regeneration; `context` mixes live and cached values |
| 2–4 | Analyser | Spotlight lit in the wrong place / negative bounds / `add:true` drops targets — all reported `ok` |
| 12, 19 | Analyser | Marker counts do not match the data, and change between logs with identical business records |
| 24 | Analyser | A `topology` report section is silently omitted from the PDF |
| 9, 20 | Analyser | An old build receipt shown as current; `open {design, diagnostics}` echoes a pre-load design block |
| 34 | Analyser | Supertype dispatch is not drawn: the route that actually runs is missing from the graph |
| 41 | Analyser | A pinned chart window survives a log change and renders an empty plot with no explanation |
| 37 | Analyser | `showAll: true` leaves the topology focus active; `pop: "all"` works |
| 40 | Analyser | Topology spotlight callout boxes overlap neighbouring nodes with six targets lit |
| 39 | Analyser | Spotlight returns off-screen negative bounds despite a correctly rendered target |
| 38 | Analyser | Restart restores only part of the session without naming omitted design/diagnostics |

**C. Needed for the new kinds of work (authoring, maintenance, scenario conversation)**

| # | Component | Issue |
|---|---|---|
| 16 | Runtime | `EventLogConfig.toString` lambda hash — the only thing stopping a two-log diff |
| 11 | Analyser | No group-by on a logged key; per-entity series need node code changes |
| 17 | Analyser | Time is the only x-axis; a real-time host collapses a run into one millisecond |
| 25 | Analyser | Every finding is headed "WHAT IS WRONG"; validation needs an evidence tone |
| 26 | Analyser | `rowWhen` cannot compare text; `series` is a PDF stub; tables are one contiguous read |
| 18 | Analyser | Notes anchored by record index vanish when the graph is pointed at another run |
| 28 | Analyser | Reopening the project drops log, graph, design and charts; no "restore last session" |
| 36 | Analyser | Named focuses lack MCP deletion and rename |
| 5 | Analyser | No changeset spotlight for the design XML |
| 42 | Analyser | `series` merges and cannot be reduced; `rightAxis` does not separate scales |
| 15 | Analyser | Formula language undiscoverable from the tool surface |

**D. Authoring friction**

| # | Component | Issue |
|---|---|---|
| 23 | Contract | `parentUpdateCallback` documented without a type; error does not say what is expected |
| 22 | Starter | Generated handlers return `true` on nodes with dependents — a one-line `// TODO` in the stub would do (see the correction under #22) |
| 31 | Compiler | Non-constructible reachable node fails late, as `javac` errors in generated source |
| 32 | Compiler | Index-suffixed node names; bean id lost; `NamedNode` names are global |
| 35 | Docs | No "redistributable component" page; interface-typed handlers (the best integration pattern) undocumented |
| 33 | Scripts | Run scripts use a cached classpath the build does not |
| 43 | Analyser | A graph name containing `:` is accepted at creation, rejected as a spotlight target |
| 7, 8 | Starter | Stub quality (`arg0`, column-0 methods); playground URL noise on every run |
| 13, 14, 21, 27 | Analyser | Step series stop at last sample; annotations and legend drawn over data; truncated titles |
| 10 | Analyser | `source.nodeTypes` lists only `child` (unconfirmed) |

**Feature requests** (not defects, listed after the addenda): **#40** — let a saved focus carry its spotlight
captions, so a walk-through survives alongside the view it explains.

Also in `AUTHORING-DOCS-FEEDBACK.md`: no headless template generator, no path from a bare project to a hosted
one, five contradictions between the docs and the starter's output, and the dead `setAuditLogProcessor` in
the hosted template's supplier.

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
| 22 | Starter · generation | Every generated handler returns `true`, including signal handlers on a node that has dependents | Low (was High — see correction) |
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
changed`), and the runbook's verify step says so.

*Correction, after discussing it with the project owner:* I over-ranked this. The rule is documented
(`skill.md`: handlers return `boolean`, "true = propagate"), I worked it out from the stub before anything
went wrong, and across many models run through the same exercise most read it or work it out quickly. I
ranked it by how vivid the mutant's consequence was, not by how likely an author is to fall in. It is a
cheap nicety — a `// TODO` in the stub — not a priority. The mutant remains a good *demonstration* for the
docs of what wrong propagation looks like in an audit log.

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

## Fourth addendum — integrating a vendor jar (`vendor/PREDICTIONS.md`)

Predictions were written before the attempt and scored after: 15 of 17 confirmed, 1 half right, and the
coin-flip (supertype dispatch) worked. **The core claim holds**: one bean reference to a class in a jar
pulled in 3 internal nodes, 2 event types, an exported service, audit logging and correct dispatch order,
with no integration code; the vendor's VaR matched an independent calculation on 11 of 11 rows and the
existing desk validation (784 + 33) was unchanged throughout. The two issues below break the promise a
"certified component" makes and should be fixed before that phrase is used with a customer.

| # | Area | Issue | Severity |
|---|---|---|---|
| 29 | Starter · regenerate | A `nodeBeans` class that exists only in a dependency jar is treated as not-yet-written: the starter writes an EMPTY class of the same FQCN into `src/main/java`, which shadows the jar, and records ownership of it | **Critical** |
| 30 | Starter · run receipt | Dependency jars are not hashed. A tampered jar under the same file name leaves `xmlHash`, `sourceHash` and `recordHash` identical and every freshness check green | **Critical for certification** |
| 31 | Compiler · diagnostics | A reachable node that is not constructible from the generated package (non-public class or wiring constructor) fails late, as `javac` errors in generated source | Medium |
| 32 | Compiler · naming | Discovered nodes get index-suffixed names (`riskEngine_13`); the Spring bean id of a referenced-but-not-listed bean is lost; `NamedNode` names are global, so two vendors can collide | Medium |
| 33 | Scripts | `run*.sh` use the cached `.fluxtion/classpath`; the build sees a new dependency (Maven reads the pom) but the run does not → `NoClassDefFoundError` at first event | Low |
| 34 | Analyser · topology | Supertype dispatch is not drawn: the graph shows `Quote -> QuoteFeed` but not the `MarketPrice -> acmeQuoteFeed` route that actually runs | Medium |
| 35 | Docs | Nothing documents writing a redistributable component, or that handlers typed on an interface receive any implementing event — the best integration pattern found all session | Medium |

**29.** With `<bean id="acmeRisk" class="com.acmerisk.RiskEngine"/>` in `nodeBeans`, `generate.sh` wrote
`src/main/java/com/acmerisk/RiskEngine.java` containing `public class RiskEngine { }` and added
`com.acmerisk.RiskEngine` to `ownership` in `fluxtion-authoring.json`. `target/classes` precedes the jar.
Result: `validate ok · regenerate ok · preflight ok · build ok`, zero diagnostics, and `MyProcessor` holds
`new RiskEngine()` — the empty one; the vendor's calculator, feeds and handlers are gone. With a Spring
property on the bean the build does fail, but the message blames the vendor ("Bean property 'varLimit' is
not writable") for a setter the vendor class has. Workaround: keep the vendor bean out of `nodeBeans` and
reference it from a host node. Wanted: resolve `nodeBeans` classes against the dependency classpath before
writing any skeleton — a class found in a jar is `foreign: never generated, never owned`, reported in the
reconciliation output; and a source file whose FQCN also exists in a dependency is a build error.

**30.** Rebuilt the vendor jar with the VaR multiplier zeroed, same file name. Receipt inputs before and
after: identical. `acme` appears nowhere in `fluxtion-run.json`. The tampered build ran, reported VaR 0.00
on every row and published no alerts; only an independent calculation caught it. Wanted: per-artefact
coordinates + sha256 in the run receipt and in the analyser's freshness chain; optionally an allow-list in
the authoring record ("certified against acme-risk 1.0.1 sha256:c7ea…") that fails the build on mismatch.
Then the analyser can also say which vendor build produced a given log.

**35.** Vendor v1.1.0 typed its handler on an interface it owns (`onQuote(com.acmerisk.api.Quote)`); the
host's `MarketPrice` implements it; the compiler merged both handlers into one `handleEvent(MarketPrice)`
and kept an `instanceof Quote` branch for other implementors. One market data row now drives desk and
vendor with no adapter. Recommend documenting this as THE integration pattern — vendor publishes
interfaces, customer events implement them — alongside a "writing a redistributable component" page:
public nodes and wiring constructors (#31), `NamedNode` with a vendor prefix (#32), `transient`
collections, getter + setter for configurable properties, public event types.

Not tried: bridging host *node state* (positions) into the vendor; Maven-coordinate resolution; a
`fluxtion-runtime` version mismatch between vendor and host; two vendors with colliding node names.

## Note — diagnose #1, #20 and #28 from the analyser's own session audit log

Added after reading `analyser/session/generated/SessionProcessor.java` and its nodes. The analyser's session
logic is itself a Fluxtion graph (`OperationGate`, `ActiveProject`, `OpenLog`, `OpenGraph`, `Pairing`,
`CoverageClaim`, `SessionBoundary`, `EffectQueue`, …) with its own bounded audit log (`SessionAuditSink`,
exportable as a snapshot). Three issues in this file live exactly in that graph's domain:

- **#1 stale graph / log after regeneration** — is there any event that tells `OpenGraph` / `OpenLog` the file
  changed underneath them? If not, the fix is a new observed-change event and a node reaction, and the
  half-refreshed `context.log` (live `sizeBytes`, cached record count) is a read that bypasses the node state.
- **#20 `open {design, diagnostics}` echoes a pre-load design block** — an ordering question between two
  effects in one request; the session log will show which outcome the echo was built from.
- **#28 project reopen drops every view** — **reframed.** `SessionBoundary` documents why a project change
  closes the log and graph, and the argument is sound. Keep the rule. The ask is an effect *after* the
  boundary: remember what was closed and offer "restore last session". Never automatic, per M35.

Suggestion: expose the session audit snapshot through the MCP surface (e.g. `analyser_session {export}`), so
an LLM client that files a defect can attach the analyser's own account of what it decided. Every report in
this file would have been better with one; I could only describe the symptom from outside.

## Source-check addendum — topology focus and callout placement (37 and 40, 2026-09-20)

Numbering correction: the earlier intake called Show all 36 and overlap 37. A concurrent participant
addition used 36–39. Canonical numbers now follow that participant addition: Show all is 37 and
overlap is 40. Earlier archived snapshots retain their original numbers; this note maps them.

Added by the authoring assistant from the owner's latest participant feedback. The observations below
are the participant's; the separate source checks are by the authoring assistant. No live session was
changed and neither interaction was independently rerun for this intake.

**37 — `showAll` does not exit the focus.** The described operation promises to exit every focus context
and clear selection/cycle shading. The participant called topology with `showAll: true` and
`scaffolding: false` together; afterwards context still reported depth 1 and 16 nodes. `pop: "all"`
worked as a workaround. The participant suspected an interaction between the combined parameters;
that suspicion is not an observed cause.

Source check: `ActionExecutor.doTopology` processes scaffolding, then maps `showAll` to
`TopologyPanel.clearView()`. That method calls only `clearHighlights()`, which does not pop the focus
stack. The toolbar's private `showAll()` separately calls `focusStack.popToFull()` before clearing
highlights and refreshing breadcrumbs. This establishes a source-level discrepancy even without
the scaffolding parameter; the exact live request has not been rerun by the authoring assistant.

Wanted: MCP and toolbar Show all honour the same full-focus-exit contract, while preserving the requested
scaffolding visibility. Acceptance: enter one and multiple focus levels; call showAll alone and with
scaffolding true/false; verify depth zero, cleared selection and cycle shading, full visibility subject
to scaffolding, refreshed breadcrumbs and matching echo/context. Cover an already-unfocused call too.

**40 — topology callout boxes overlap other nodes.** With six targets lit, the participant reports
callout 3 covering MarketSession and HedgeFillHandler, and callout 2 covering OrderGateway. This extends
the annotation-over-data family (#14) to the topology. The participant's proposed placement rule is to
keep caption boxes outside occupied node rectangles, including nodes that are not spotlight targets.
The existing `evidence/eod-feed-path.png` was inspected during intake: it has six lit targets and caption
overlap with topology content. Exact covered-node identities are participant-reported; hidden labels
alone cannot independently establish their identity.

Source check: `SpotlightGeometry` scores overlap with cut-out targets and already placed captions;
these are not the full set of visible topology node rectangles. Wanted: provide all occupied node bounds
in the same coordinate space and seek placements clear of nodes and other captions. Define an explicit
fallback when the viewport cannot fit every caption; do not promise zero overlap at every size.
Acceptance: the six-target example plus non-target nodes, zoom/pan/resize, different caption lengths,
and a crowded viewport. Check pixel output and bounds; an `ok` echo alone cannot verify placement.

## Fifth addendum — saved focuses, and the end-of-day reporter build

| # | Area | Issue | Severity |
|---|---|---|---|
| 36 | Analyser · focus | A named focus can be saved but not deleted or renamed through the MCP surface | Medium |
| 37 | Analyser · topology | `showAll` did not exit the focus context when combined with `scaffolding` in the same call | Low |
| 38 | Analyser · session | A restart restores log, graph, charts and reports — but NOT the design or the loaded diagnostics | Medium |
| 39 | Analyser · spotlight | Bounds echoed as negative/off-screen while the target rendered correctly | Low (new variant of #3) |

**36.** `analyser_topology {saveFocusAs}` is replace-by-name; there is no delete. Focuses live in
`.analyser/project.fluxtion-settings` as `focus.N.*` with a `focus.count`, but the analyser holds them in
memory and auto-saves, so editing the file has no effect on the live session — `open {project}` on the
already-active project correctly declines ("a reload would only read back what is live"). The working
sequence was: edit the file, `open {close: "project"}`, `open {project}` — and the close does NOT rewrite
the profile first, so the edit survives. That is three calls plus restoring log, graph, design and
diagnostics, to remove one list entry. Wanted: `saveFocusAs` gains a companion (`deleteFocus`, `renameFocus`),
or a focus can be removed by saving an empty context under its name.

**37.** `topology {showAll: true, scaffolding: false}` returned `focus: true, contextDepth: 1` and the same
16 visible nodes. Its own description says showAll "exits every focus context and clears selection and cycle
shading". `pop: "all"` worked. Possibly the two parameters interact; worth a test for showAll combined with
each other parameter.

**38.** After the analyser was restarted, `context` came back with the log, graph, five named graphs and the
report — and `design` with no `file`, and no diagnostics. The user noticed ("I can't see the spring design
file"), not the tool. If the session restore covers four of six artefacts it should cover all six, or name
the two it did not restore.

**39.** `spotlight {target: "source:design:bean:eodReporter"}` echoed `bounds {y: 1262, height: -109}` in a
1205px window — by the rule in #3 that is a clipped target, but the screenshot showed it lit correctly on
the bean. So negative bounds are not a reliable signal of anything. Either way the caller cannot trust the
geometry it is handed, which is the point of #2-#4.

### Also confirmed while building the end-of-day reporter

- **#6 recurred in a fresh context.** The generated `EndOfDayReporter` stub did not extend `EventLogNode`,
  so `auditLog` did not resolve; hand-edited again. Second occurrence this session, on a node whose whole
  purpose is to write to the audit log.
- **The DATA-reference design held.** Seven `mode=DATA` bindings, and the audit record for both report
  triggers shows `1 node(s) ran`. Had they been triggers the report would have been rebuilt and published
  about 60 times in one day.
- **The influence-set focus is a genuinely new use of the topology.** "Show me everything that can affect
  this output, and prove nothing else can" — 16 nodes in, 15 provably out. That reading deserves to be a
  first-class question in the UI, not something assembled from select + scope + routeBound + focus.


## Proposal P1 — a saved walkthrough associated with a named focus

Added from the participant's proposal supplied by the owner, 2026-09-20. This is a proposal for design
review, not an implemented feature or permission to persist ordinary transient spotlights automatically.
The participant twice recalled a saved focus and then resent the same six-target caption sequence;
the node set survived but its explanation did not. A named walkthrough could retain that explanation.

Participant request: save an ordered sequence of node targets and captions with a focus; recall/step
through it; retain visible author/time attribution; record what it was written against and warn on a
changed run. Keep it lightweight: it explains the view, while existing reports present record evidence.

Author assessment and constraints:

- Saving must be explicit. Existing spotlights stay transient by default; changing the standing
  spotlight spec requires a reviewed extension, not silent autosave on every focus save.
- Preserve ordered steps and resolve semantic node targets at recall; do not save screen rectangles.
  Captions remain attributed commentary, not facts certified by the analyser. Author identity is a
  declaration unless authenticated separately. A tour must be editable, removable and shareable with
  clear persistence rules; define how focus rename/delete affects it.
- Structural commentary needs graph/model identity and relevant code/build provenance when available.
  A claim such as “the only memory of a price” cannot be established by topology alone, and may become
  false while node names and edges stay unchanged. “Structural” is declared scope, not automatic proof.
- Observational captions additionally need their original run/record or time-window anchor. Prefer a
  reference to an existing report/finding rather than duplicating its evidence model. On mismatch or
  unavailable identity, do not paint the old number as a current observation: show an explicit historical
  or unresolved state, or withhold that step pending confirmation. The policy must be visible per step.
- Reuse report attribution and mismatch presentation, but not an assumed exact identity guarantee.
  Current `LogFingerprint` compares record count and first/last log times, with additional name/provenance
  warnings. Different contents can pass that comparison. A tour identity contract must state its strength
  and report unknown rather than silently accepting missing identity.
- Renamed, absent or ambiguous nodes remain visible as unresolved steps with reasons; never silently
  drop them, renumber the surviving steps or remap by a guessed name. Stable names help targeting but
  do not prove unchanged behaviour. Preserve context/focus warnings on partial resolution.
- Start with an explicitly saved structural explanation and links to existing run evidence. Sequential
  playback is a useful follow-up, not a reason to build another reporting subsystem. Fix focus management,
  restore and spotlight geometry/placement before calling this a dependable walkthrough feature.

Acceptance for a future design: same graph/new run; changed graph/same names; changed code/same graph;
missing/ambiguous targets; same-count/same-time logs with different contents; absent provenance; mixed
structural/observational steps; rename/delete collisions; restart and export/import. Human UI and MCP
must report the same resolved/unresolved/history status and attribution. Application execution remains
outside the analyser.

### Qualifications to the latest participant findings

- **36:** deletion exists in the topology toolbar's Focuses → Delete menu. The gap is MCP deletion and
  rename, not absence of any delete UI. The participant's file-edit/close/reopen sequence happened to
  work; it is not a safe general workaround. `ProjectSession.close()` flushes pending changes, so a dirty
  live configuration can overwrite an external edit. Use supported controls instead of recommending
  direct edits to the active profile. Prefer explicit delete/rename operations over treating an empty
  focus as deletion; define duplicate-name handling and references from reports/tours.
- **38:** the restart observation is new participant evidence, consistent with the source-read global
  log/GraphML restoration path. The earlier independent probe tested close/reopen, not quit/relaunch.
  Do not rewrite that evidence as if it witnessed this run.
- **39:** a negative height is invalid rectangle geometry even when the drawn target is correct. It
  disproves using the echo as a visibility oracle, not the need to fix it. Test reported and painted bounds
  from the same settled layout/revision; retain the earlier mispainted-target tests too.
- **6 recurrence:** recorded as a second participant observation (EndOfDayReporter); consistent with the
  audit-scaffolding gap. Lack of an EventLogNode base or values does not by itself prove no execution.
  Design an explicit audit policy for new owned nodes without re-parenting implemented or foreign classes.
- The influence-set claim is limited by the completeness/semantics of the supplied graph. “Nothing else
  can affect it” is not proven merely by excluding nodes, especially with known missing dispatch metadata
  (34) and external state/service dependencies.

### P1 participant clarification — optional captions, not another focus-saving route

The participant already saved and recalled focuses repeatedly. Persistence across close/reopen was
observed when namedFocuses returned from 0 to 3. The missing state was specifically the six captions:
the same payload had to be resent twice after recalling the saved node set.

The requested extension is optional ordered captions on the existing focus. A focus without captions
behaves exactly as today. The original six include two described as structural and four quoting one
log's values; retaining that mixed explanation is the use case, not only a structural-tour feature.
Save its written-against context and preserve visible attribution. On recall against a different or
unverified run, disclose that each affected observation belongs to the original run rather than
displaying it as current. Exact identity-strength and stale-step presentation remain design questions;
the report fingerprint limitations noted above still apply. This clarification is not a request for
a new focus-saving MCP route, and does not require proving the already-observed focus persistence again.

---

# Feature request #40 — let a saved focus carry its captions

**Type:** feature request, not a defect. Everything below works as documented; this asks for one addition.
**Rank it against your cross-model data, not this session.** My evidence is that I retyped a payload twice
in one afternoon, which is a weak basis for a priority. If other models never annotate a focus, this is
worth nothing; if they all do, it is worth more than several defects above it.

## What already exists, verified

`analyser_topology {saveFocusAs, rationale}` / `{focus: "<name>"}`. I exercised all four behaviours against
the running analyser:

| Behaviour | Result |
|---|---|
| Save an applied focus | 4 saved this session (hedger, vendor, EOD reporter, EOD influence set) |
| Recall by name | `focus: "EOD report: everything that can affect it"` restored all 28 nodes after a full project close/reopen |
| Refuse on the unfocused graph | `nothing to save — the full graph is not a focus; apply one first`; profile untouched, no partial entry written |
| Replace-by-name | Re-saved under the same name: still 3 focuses, `focus.2.node.count=28`, no duplicate |

Persistence across a session boundary is also proven: a hand-edited profile survived `open {close: "project"}`
and reloaded as `namedFocuses: 0 -> 3`. **No new MCP route is needed for any of this.**

## The gap

A saved focus stores the node set and a one-line rationale. It does not store the spotlight captions that
explain it. In this session:

- the **node set** survived a project reload — one call brought all 28 nodes back;
- the **six captions explaining that node set** did not, and I re-sent an identical 6-target
  `analyser_spotlight` payload by hand, twice.

The focus's `rationale` is one sentence for a 16-node graph ("the complete influence set of the end-of-day
report"). The captions are the same idea at working resolution: *which* six of those nodes to look at, in
what order, and why each matters. Saving one without the other keeps the view and discards the reading of it.

## What to add

Make captions an optional part of the focus — a focus with none behaves exactly as today.

1. **Save the spotlight set with the focus**, one name, one recall. Ordered, so it can be stepped through
   rather than shown all at once: a numbered walk is what a support engineer wants when they open a focus
   somebody else built.
2. **Stamp it `writtenAgainst`**, exactly as reports already do, and say so on recall when the loaded log
   differs. This is the load-bearing part — see the risk below.
3. **Keep the attribution visible.** The on-screen `assistant · 1` badge should persist into the saved form,
   with author and date, so a caption never reads as something the analyser established.

## The risk, and why the stamp matters

Your tool description says a callout "is YOUR words (testimony), not evidence, and is never saved". That
reads as a deliberate decision and it is the right one. Saving changes the epistemics: a caption the next
reader finds already on screen carries more apparent authority than one they watched being written.

Two kinds of caption were written today, and only one is safe to persist blind:

- **Structural** — *"priceBook keeps the only memory of a price"*. True of the graph, true for every run.
- **Observational** — *"AAPL 900 and MSFT 600, both over their 500 threshold"*. True of record 49 of one log.

The second is the danger, and it is worse than the equivalent problem for pinned chart notes (#18). A note
anchored by record index silently *vanished* when its graph was pointed at another run. A caption anchored to
a graph node would **not** vanish — it would stay on screen, well-formatted, and wrong. The `writtenAgainst`
stamp turns that from a silent falsehood into a visible caveat, which is the whole difference.

## What not to build

Do not let this grow into a second reporting mechanism. The line worth holding:

- a **tour** explains *structure*, is cheap, and stays valid across runs;
- a **report** presents *evidence* from one run, anchored to records, re-rendered live.

If tours start carrying findings you will have two systems making similar claims with different guarantees,
which is how the "WHAT IS WRONG" framing problem (#25) arose in the first place.

## Dependency

Saved captions reference nodes by instance id, so they inherit **#32**: auto-named nodes (`varCalculator_14`)
change between builds and would silently drop out of a saved tour. `NamedNode` fixes it for components that
opt in; a tour over auto-named nodes needs either a rename-tolerant anchor or an explicit "3 of 6 captions no
longer resolve" on recall.

## Related

**#36** (a focus can be saved but not deleted or renamed) and this are the same shape: focuses are the one
piece of durable, shareable view state in the tool, and their lifecycle is write-only. Deleting one today
takes a profile edit plus a close/reopen; annotating one is not possible at all.

## Sixth addendum — charts: a silent empty plot, and a write-only series list

Found while explaining the hedging orders on a chart. The first one cost the project owner real time —
they hit it before I did and diagnosed it themselves.

| # | Area | Issue | Severity |
|---|---|---|---|
| 41 | Analyser · graph | A pinned window survives a log change and renders an **empty chart with no explanation** | **High** |
| 42 | Analyser · graph | `series` merges rather than replaces, a series cannot be removed, and `rightAxis` does not separate the scales | Medium |
| 43 | Analyser · spotlight | A graph name containing `:` is accepted at creation but rejected as a spotlight target | Low |

**41.** Two charts had been pinned to `1789851500050–1789851503200` while a previous run was loaded. The
log now open spans `1789893911697–1789893911744` — about twelve hours later, **zero overlap**. Both charts
rendered blank. Nothing said why: no "pinned window lies outside this log", nothing in `context`, nothing in
the `graph` echo, which still reported `refreshed: "scheduled"` as though it had work to do. An empty plot
reads as *no data*, which sends the reader to the log and the series names — the wrong places.

A second cause was stacked underneath and equally silent: an active record filter
(`dimensions: [HedgeFill, MarketPrice]`, 20 of 76 records visible) further starved the extract. Neither
condition is visible from the chart.

This is worse than #18. A note anchored by record index **vanishes** when its graph is pointed at another
run — visibly absent, so you go looking. A pinned window **persists** and silently suppresses everything.
Wanted: the `writtenAgainst` treatment already specified for reports — stamp the pin with the log it was
set against, and when the loaded log falls outside it say so on the plot and in the echo rather than
drawing an empty frame. Related: `from: null, to: null` does clear a pin, but both parameters are typed
`integer`, so that is undocumented and was found by guessing.

**42.** The chart carried `positionKeeper.position`, whose EURUSD value of 1.25m flattened four equity
series into the zero line. Two documented routes both failed:

- `graph {series: [the four hedger keys]}` — the echo resolved all four, but the legend still showed
  `positionKeeper.position` afterwards and the axis stayed at ±1,322,500. `series` **adds**; nothing in its
  description says so, and unlike `markers`, `guides`, `bands` and `external` — each explicitly documented
  as "REPLACES the set" — it has no stated semantics at all.
- `graph {rightAxis: ["positionKeeper.position"]}` — after this **both** axes displayed the same
  ±1,322,500 range, so the left scale was never freed. Whatever it did, it did not separate the scales.

With no removal verb the only fix was building a new tab. That is the same write-only lifecycle as #36
(a focus can be saved but not deleted): chart definitions are durable project state that can be added to
and never reduced. Wanted: state whether `series` replaces or merges, add removal, and make `rightAxis`
actually rescale.

**43.** `spotlight {target: "graph:Hedging: exposure and working:note:1"}` was rejected —
*"a chart's name cannot contain ':'"* — but `graph {name: "Hedging: exposure and working"}` had **created**
that chart happily some hours earlier. Creation permits what targeting forbids, and the failure surfaces
much later, in a different verb. Wanted: reject or escape the separator at creation. The unnamed
`graph:note:<n>` form (current tab) is a usable workaround and worth documenting as such.

### Confirmed working, and worth protecting

The records-row viewport refusal is **correct and valuable**. Asking for `records:row:43` and
`records:row:75` together returned:

> `'records:row:43' and 'records:row:75' cannot be on screen at the same time — bringing the second into
> view hid the first. Light them one after the other`

That is exactly the behaviour #2 asks for — refuse the set, name the conflicting targets, suggest the
split — and it turned a would-be silent mis-light into a two-step walk. It shows the rule is already
implemented for the records pane; #2 is about extending the same check to the source and graph panes,
not inventing it.
