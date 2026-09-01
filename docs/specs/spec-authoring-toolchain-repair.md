# Spec — repairing the authoring toolchain, from four measured rounds

**Status:** PROPOSED 2026-09-01. **Tracker:** [tracker.md](tracker.md) ▸ M46.
**Evidence:** `docs/experience/runs/round-07` … `round-10` — 23 fresh-context agent runs, two model tiers,
predictions committed before each round, every finding scored against the artefact rather than the
agent's report.

## Why this exists, in one paragraph

Across four rounds the framework's *core claims held perfectly*: M5 (a designated node dispatches last)
and M6 (no value mixes generations) were **never violated by any agent in any run**, on either model
tier, with or without documentation. 22 of 23 runs produced a correct working graph. **Every defect below
is a communication failure, not a correctness failure** — a green build that ran the wrong graph, a
diagnostic with its structure stripped, a verdict computed before the data it describes. That is the
right shape of problem to have left, and it is why fixing this list is worth doing as one programme.

## D-R1 — the organising principle: rank by whether the failure is SILENT

Every item is ranked by one question: **does the toolchain tell you?** A loud failure costs a build
cycle. A silent one costs the correctness of everything downstream, and in three measured cases produced
a confident, evidenced, wrong report.

| tier | meaning |
|---|---|
| **S** | silent — green build, wrong result, no signal | fix first |
| **W** | wrong or missing information that misleads | fix second |
| **N** | noisy or awkward but visible | fix last |

## Upstream — `fluxtion-compiler` / `fluxtion-maven-plugin`

| id | tier | defect | evidence |
|---|---|---|---|
| **U1** | **S** | **Structured diagnostics never reach the console.** `code`/`rule`/`why`/`suggestedFix` go to the sidecar; the default path emits a raw `DiagnosticException` in ~60–80 lines of Maven stack trace naming no code and no fix. Round 08 measured the *good* version only because it passed `-Dfluxtion.diagnostics.sidecar=true`. | armB-1 D6, armB-2 #1 — both no-docs Opus runs, independently |
| **U2** | **S** | **`target/classes` lags the generated source by one build.** `compile` precedes `process-classes`, so a run straight after a graph change executes the *previous* graph and writes an audit log describing a version that no longer exists. | armA-2 A, armB-2 #2 — independently, by different routes (one changed a trace level and got the old one back) |
| **U3** | **S** | **The `scan` goal silently no-ops when no `FluxtionGraphBuilder` is found.** A project containing zero Fluxtion code builds green. This let an agent ship a hand-orchestrated plain-Java program and report all six requirements satisfied. | haikuB-1 — verified: no processor, no graphml, zero `com.telamin` imports, green build |
| **U4** | **S** | Node inside a **collection constructor argument** is default-constructed, discarding builder-supplied values. Already filed as **UP-FLX-44**. | round 08 armA-3 |
| **U5** | **W** | **Bootstrap deadlock.** Generation targets `src/main/java`, but `compile` runs first — so a hand-written class importing the generated processor can never compile on a clean checkout. Four agents hit it. | armA-1, armB-1, armB-2, haikuA-1 |
| **U6** | **W** | `setAuditLogProcessor` / `setAuditLogLevel` are **dispatches**, so they inject `EventLogControlEvent` records into the user's own log — breaking any count-based claim before the first domain event. | four agents |
| **U7** | **W** | `init()` / `start()` emit audit records; `tearDown()` emits one **into a closed writer** and throws `UncheckedIOException`. | armA-1 A, armB-1 D3 |
| **U8** | **N** | `addEventAudit()` (no-arg) turns tracing **off**; the `LogLevel` overload turns it **on**. The parameter is named for the level, not the switch it flips. | armB-1, haikuA-1 |

**U1 is the highest-value item in this document.** The diagnostics programme is real and good — round 08
measured three one-step repairs from `suggestedFix` alone. It is simply not reaching the surface where
authors read.

## Analyser — ours

| id | tier | defect | evidence |
|---|---|---|---|
| **A1** | **S** | **The first `coverage`/pairing verdict after an `open` is computed against pre-call state**, and is wrong. Observed as *"declares 0 of the 5"*, *"5 of 8"*, and *"no log is open"* — the last in the same response whose `opened.log` names the log just opened. An identical second call returns the truth. `coverage` simultaneously reported `ratio 1.0`. | **all four Opus agents**; armA-1 reproduced on three instances including one under an isolated `user.home` |
| **A2** | **S** | **The REST endpoint hangs permanently.** One route is a modal on a load path (`jstack`: `JOptionPane.showConfirmDialog` ← `maybeOfferProject` ← `onLoaded`); another follows mixed `coverage`/`topology` calls. Process alive, every later verb returns nothing. | armA-2 C (jstack), armB-1 D5 (twice) |
| **A3** | **W** | `open` publishes the **visible** node count under a key called `nodes` — `12` for a 22-node graph — next to a pairing verdict, where it reads as the graph's size and corroborates a wrong verdict. | armA-1, armA-2 |
| **A4** | **W** | State leaks across instances via the shared user home: a "fresh" `--rest` instance reports a sibling run's log as open before anything is opened. | armA-1 E, armB-1 D4 |
| **A5** | **N** | `topology` reports `rowCount: 0, position: "no records"` while records are open and `context` reports them correctly. | armB-1 |

**A1 and A2 are the priority.** A1 is worse than a wrong number: `read-audit-log/SKILL.md` instructs the
reader to *check `graphPairing` before concluding anything*, so following our own guidance on a first
open makes you discard a correct graph. A2 kills the agent API silently.

## Documentation — ours

| id | tier | gap |
|---|---|---|
| **X1** | **W** | How to get the audit log **into a file** is undocumented — `LogRecordListener`, that `LogRecord.toString()` is the YAML document, and that the caller supplies the `---` separators. Two agents recovered it from `javap`. |
| **X2** | **W** | `addEventAudit(LogLevel, boolean, boolean)` is undocumented; without `printEventToString` there is no `eventToString`, and M3/M6-style evidence becomes unquotable. |
| **X3** | **W** | U6 and U7 above (the audit "setters" polluting the log; lifecycle records) belong in the doc set as well as upstream. |
| **X4** | **N** | `CLAUDE.md` and all three skills describe a project that does not exist in a fresh template — `run-server.sh`, `export-audit.sh`, `data/input.txt`, `RootNode`/`RiskCheck` as "shipped examples". Reported by **every agent in every round**. |

## Harness — ours, and they invalidate measurements if left

| id | defect |
|---|---|
| **H1** | Parallel agents shared one analyser process and one `user.home`; one `pkill`ed another's JVM mid-sequence. Runs must be isolated. |
| **H2** | My round-09 specification was self-contradictory: **M3 and M4 pull in opposite directions.** All four Opus agents found it and all four invented the same repair. A behaviours spec must be checked for internal consistency before it is used as an oracle. |

## Acceptance

- [ ] U1: a failing build prints the code, rule and suggested fix **on the console**, with no flag.
- [ ] U3: a module with no `FluxtionGraphBuilder` says so; it does not report success.
- [ ] U2: the plugin warns when `target/classes` predates the generated source.
- [ ] A1: a pairing verdict is never emitted before the log it describes has loaded; the same call twice
      returns the same answer. Regression test drives `open {log, graphml}` on a **virgin** instance.
- [ ] A2: no modal is reachable from any REST-driven path — asserted by a test that opens a log **inside a
      project directory** over the socket, which is the case `verify-session-transitions.py` never covers.
- [ ] X1–X3 land in `docs/experience/current/CLAUDE.md`.
- [ ] H1: each experiment run gets its own `user.home` and its own analyser port.

## What is deliberately NOT here

**Nothing about making the framework catch design errors.** Across four rounds no diagnostic could have
caught an idiom error, and the one real design defect was caught by *reading the generated source*. That
is the framework working as intended, and the repair for it is documentation and inspection habits, not
more compiler checks.
