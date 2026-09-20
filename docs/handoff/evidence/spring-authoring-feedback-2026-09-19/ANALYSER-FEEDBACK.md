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
