# Review — `spec-mongoose-audit-production.md` at `2718495e` (reviewer: claude)

**Verdict: CHANGES REQUIRED before implementation.** The measurement the spec rests on is **correct** — I
reproduced it on both published jars. The argument built on it is not: `complete` is a true statement, the
harm is a missing *empty-log* signal in the reader, and **MA-1 does not remove the case it is meant to
gate** — a correctly audited processor at a quiet log level also writes an empty log. So "MA-1 precedes MA-2
as a requirement" does not follow, and the real fix is small and on the analyser side. Several claims about
the runtime also fail when driven rather than read.

Reviewed in my own worktree at `2718495e` (`origin/main` has since moved to `3866d2a1`). Nothing was
edited, implemented, merged, pushed or released.

---

## F1 · Central · the measurement is right; the conclusion drawn from it is not

**Reproduced exactly (RUN).** The spec's four-line file, against the published jars downloaded by tag and
checked by digest (1.18.0 `5a8c2a4f…`, 3,914,217 bytes; 1.19.0 `826daf64…`, 3,945,846 bytes), reads
`{state=complete, recordsRead=0, declaredRecords=0}` on **both**. The spec's quoted output is accurate.

**The neighbouring cases are what matter (RUN, both jars, identical results):**

| Case | state | analyser warning |
|---|---|---|
| A — marker declaring 0, nothing else (the spec's case) | complete | **none** |
| B — today's empty export (no marker, no records) | unknown | **none** |
| C — 5 records with empty `nodeLogs`, marked | complete | **`NO_NODE_LOGS`** |
| D — 5 records with empty `nodeLogs`, unmarked | unknown | `NO_NODE_LOGS` |
| E — 5 healthy records, marked (control) | complete | none |

**1a · `complete` is true, not false.** Spec line 41-42 calls it "a confident false one". Under §1a
`complete` is a *container* claim — every marker's count matches its segment and a marker is last — and
for case A it is literally true: nothing was written and nothing was lost. The brief's own phrase,
"true-but-useless", is the accurate one; the spec's text should say that.

**1b · The harm is real, and it lives in the reader.** `NO_NODE_LOGS` fires when records exist and are
empty (case C), but **nothing fires on an empty log at all**, marked or unmarked (A and B), because
`ProducerDiagnostics` returns before any check when the index is empty. So today's empty export is already
silent; the marker only changes the label from `unknown` to `complete`. What makes A look healthy is the
absence of a warning, not the verdict.

**1c · MA-1 does not remove the empty case (RUN + read).** `EventLogManager.processingComplete()`
(runtime 1.0.15, line 295-297) publishes only when `logRecord.terminateRecord()` is true, i.e. when some
node logged at the current level. Driven: with a manager installed and the level at WARN, an event whose
handler calls only `info`/`debug` produced **zero records**. So a correctly audited processor — on either
path, AOT included — that logs nothing at its configured level writes an empty log, and an MA-2 marker on
it declares 0 and reads `complete`. The ordering gate does not close the hole it exists for.

**Required correction.** Put the fix where the harm is: the analyser reports an **empty log** as its own
finding whenever `recordsRead == 0` — marked or not, which also fixes case B today — and qualifies
`complete` on an empty file ("complete, and empty: the writer declared and wrote nothing"). With that
shipped, MA-2 is safe in either order, and MA-1 stands on its own merit (the handler's log lines) rather
than as a gate. Rewrite "The measurement that orders this work" and the ordering diagram accordingly.

**My answer to the brief's harder question:** `complete` is defensible as the container verdict and should
stay; the spec's central argument (that it is false) is wrong. The concern behind it is right and cheap to
address in the reader.

## F2 · High · the coverage "denominator of four" does not exist (read; MA-1.4 unmeetable)

The analyser takes coverage's denominator from the **paired graph** (`CoverageScope`: "from the graph
alone"); with no graph, `LogArrival` records `noGraph` / `nothingToJudge` (`LogArrival.java:73`) and makes
no coverage claim. The wrapper path produces no graph, so there is **no denominator** — not four.

- D-MA1b line 111, "the analyser's coverage denominator becomes four", is wrong; it becomes absent.
- MA-1.4 ("non-empty coverage denominator … the four registered nodes") cannot be met as written, and its
  second clause, "`NO_NODE_LOGS` does not fire", passes vacuously on an empty log (case B).
- The four-node registration itself is **confirmed** (`DefaultEventProcessor.java:292-296`). The spec's
  auditor list (line 56-57) omits a third auditor, `serviceRegistry` (line 100). Minor, but the section is
  titled "read not inferred".

**Required:** say coverage is unavailable on the wrapper path unless it can emit a graph, and rewrite
MA-1.4 as "records carry the handler's node entries, and the analyser reports no coverage claim rather
than a partial one".

## F3 · High · D-MA1: right conclusion, wrong reason — a Mongoose-side route exists, and driving it shows why not to take it (RUN)

The spec says the fix belongs in `fluxtion-runtime` because `DefaultEventProcessor` owns its auditors and
"Mongoose only chooses that class". But Mongoose **subclasses** it — `ConfigAwareEventProcessor extends
DefaultEventProcessor`, which the spec cites — and `onEvent`/`onEventInternal` (lines 188, 203) and the
lifecycle methods (114-164) are public and not final.

I drove that route against `fluxtion-runtime-1.0.15`: a public subclass with a `public final
EventLogManager eventLogger` field, `nodeRegistered(handler, "allEventHandler")`, and a bracketed
`onEventInternal`. Results:

- **It works for events:** records carry the handler's line (`- allEventHandler: { seen: tick }`), and
  `getAuditorById("eventLogger")` resolves (it uses `getClass().getField`, which finds a public field on a
  public subclass).
- **Hazard 1 — the clock:** `eventReceived` throws NPE unless the manager's `clock` is injected; a generated
  processor does this for you.
- **Hazard 2 — corrupt records:** after `init()`, the published record was malformed — a node entry opened
  *before* the `eventLogRecord:` header, with the header spliced inside the braces. Lifecycle events go
  through the private `auditEvent` (lines 116-166), so the subclass cannot see them without overriding every
  lifecycle method, and exported-service calls go through private `beforeServiceCall`/`afterServiceCall`
  (299, 308).
- **Hazard 3 — the level:** see F4.

**Required:** keep the runtime home, and give the reason that holds: a Mongoose subclass can install the
manager only by re-implementing the runtime's private dispatch bracket (seven-plus call sites, the clock,
the control-event route), which drifts with every runtime change and already produced corrupt YAML on the
first attempt. "There is no field" is true but is not the argument.

## F4 · High · installing the manager is not enough: the level control event must be routed (RUN)

Driven on the same subclass, after registration:

| How the level was changed | Effect on an `info` call |
|---|---|
| `logLevel(WARN)` (the builder method) | none — it only affects loggers registered later |
| `EventLogControlEvent(WARN)` sent through `onEvent` — **what the admin endpoint does** | **none** |
| `calculationLogConfig(EventLogControlEvent(WARN))` called directly | suppressed (0 records) |
| `calculationLogConfig(EventLogControlEvent(DEBUG))` called directly | `info` and `debug` both recorded |

In a generated processor the control event is dispatched to the manager; on the wrapper nothing routes it,
so after a naive MA-1 **the endpoint is still a 200 that changes nothing** — the exact symptom in the spec's
table. MA-1 must include routing `EventLogControlEvent` to `calculationLogConfig`, and MA-1.3 is the right
acceptance precisely because it would catch this. Make MA-1.3 concrete and endpoint-driven: `POST` WARN →
`info`/`debug` produce 0 records; `POST` DEBUG → both appear; asserted on the Chronicle bytes.

## F5 · Medium · OD-1: deciding with UP-FLX-51 is right, but the table omits the consequence

`UP-FLX-51`'s fix defaults the record to `NONE` (`upstream-asks.md:1745`). An always-on manager with a `NONE`
default is **installed but silent until a level is set** — the silent no-op the spec exists to remove, in a
new form — unless Mongoose raises the level when capture is enabled. Mongoose already sends an
`EventLogControlEvent` to install its sink (it is the first record of the template's own export), so the
fix is plausible, but the table must state it. Two further points:

- The owner is asked to choose always-on on the strength of an **unmeasured** post-fix cost (the spec's own
  "Not established", line 214). Measure first, or mark the decision provisional.
- The same `NONE` path is where AFMT-3 lives (F7). Defaulting to `NONE` before AFMT-3 is diagnosed risks
  shipping a default onto an undiagnosed corruption path.

This is not dodging the cost question, but as framed it decides it on a promise.

## F6 · Medium · D-MA2: parity cannot be declared by the writer under §1a (read)

A marker may carry only `streamEnd`, `streamEndRecords` and `logTime`; **any other key makes it an ordinary
record** (§1a recognition rule). A per-node count therefore cannot travel in the marker without a format
change — the stream-end spec's own open question 2 already records that a second count is a format change.
So D-MA2's parity is an **acceptance-time** comparison (the handler's ground truth against the reader's
parse), not something the writer declares or a runtime reader can check. Say so in D-MA2, or the
implementer will try to put it in the marker.

## F7 · Medium · the per-node `NONE` corruption is recorded, and it is in scope

The spec (line 187-189) says its description could not be found and cites `tracker.md:131`. At `2718495e`,
`tracker.md:131` is AF-5's text, not a `NONE` reference. The description exists:

- `docs/specs/tracker.md:494` — **AFMT-3**: after `EventLogControlEvent(sourceId, null, NONE)` the next
  record loses its header, keys and newlines for every node; runtime 1.0.16; cause undiagnosed; repro
  `…/audit-format-review-2026-09-21/rev5/LevelTest3.java`.
- `docs/proposals/mongoose-audit-format/README.md:102-106` and `:267-271` — the same defect, and the
  release-2 gate: "diagnose the per-node `NONE` corruption first".

It is a dependency of this spec, not adjacent to it: MA-1.3 drives levels through the same control event,
and OD-1's `NONE` default touches the same path.

## F8 · Acceptances

- **MA-2.1 passes on an empty log** — case A satisfies "complete, `declaredRecords` equal to
  `recordsRead`". Add `recordsRead > 0` with node entries, tied to D-MA2.
- **MA-2.3** needs defined kill points, or "never `complete`" is false for the one case where `complete`
  is correct: killed before any record → `unknown`; mid-record → `unknown`; mid-marker →
  `unterminated_marker`; **after the marker's separator is flushed → `complete`, correctly**. Scope "never"
  to "before the marker's terminating separator is flushed".
- **MA-1.3** — right, and F4 shows why; make it concrete and endpoint-driven as above.
- **MA-1.4** — unmeetable as written (F2).
- **MA-1.2, MA-1.5** — fine. MA-1.2 is testable; my experiment resolves `getAuditorById` on a public
  subclass.

## F9 · Scope

- **MA-3** — agree it belongs in `mongoose-plugins#38`. Keep a one-line pointer here and move the
  acceptance there; it shares no code or ordering with MA-1/MA-2.
- **AFMT-3** — bring in as a dependency (F7).

---

## What I ran versus what I only read

**Ran:**
- the five-case probe (F1) against both published analyser jars, downloaded by tag, digests checked;
- two experiments against `fluxtion-runtime-1.0.15`: a Mongoose-style subclass installing an
  `EventLogManager` (records, clock, lifecycle corruption, accessors), and the four level routes (F4);
- a check of the developer template's audit layout on today's A2 run: Chronicle backend, `.cq4` queue,
  text YAML records, exported to text for the analyser.

**Read, not run:**
- `DefaultEventProcessor` and `EventLogManager` sources (runtime 1.0.15 sources jar);
- `CoverageScope`, `LogArrival`;
- `ConfigAwareEventProcessor` on Mongoose `develop` `17a03b4`;
- `UP-FLX-51`; AFMT-3 in the tracker and the audit-format proposal.

**Not done:**
- I did not boot a Mongoose server with a `customHandler`; the zero-record emission is the spec's
  measurement, not mine.
- I did not run AFMT-3's repro or measure `EventLogManager` cost.

The experiment sources are left beside this report, uncommitted, in `.review-tmp/exp/`
(`MongooseSideRoute.java`, `Route2.java`) and `.review-tmp/cases/` for reproduction.
