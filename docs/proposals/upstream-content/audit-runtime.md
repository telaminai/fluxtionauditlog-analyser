# Proposed upstream content — getting the audit log OUT of a running processor

**What this is.** Drafted content for the **static authoring resources**, not for this repo. Companion to
[`audit-authoring.md`](audit-authoring.md), which covers how a node *participates* in the log and stops at
`addEventAudit`. This covers the half after that: everything between "my nodes are written" and "I have a
log in my hand".

**Separate from the compiler-diagnostics work** (owner, 2026-08-31). None of this is a build failure —
every wiring below compiles and runs. That is the point: the failures here are green builds that produce
the wrong evidence, so a diagnostic cannot reach them and a document has to.

**Where it goes**, in priority order:

| File | Why |
|---|---|
| `fluxtion-playground.dev/fluxtion-golden-path.md` | it already shows the correct wiring in a code block and never says that the order is what makes it correct |
| `telaminai/fluxtion` `docs/claude.txt` | the framework canon; its one audit snippet shows the *other* order, for a different situation, unlabelled |
| `fluxtion-playground.dev/CLAUDE.md` | carries the same snippet as `claude.txt` |
| `fluxtion-playground.dev/spring-authoring/contract.md` | one line under `logLevel` — the Spring author's entry point to audit |

## Evidence the gap is real — retrieved 2026-08-31

Live documents, so the retrieval date matters: they can change under this claim.

| Source | Shows the sink call? | Says the order against `init()` matters? |
|---|---|---|
| `https://fluxtion-playground.dev/fluxtion-golden-path.md` | yes — `setAuditLogProcessor(System.out::println); flow.init();` | **no** |
| `https://raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt` | yes — inside a `captureRun()` test helper, on an already-built shared flow | **no** |
| `https://fluxtion-playground.dev/CLAUDE.md` | same snippet as above | **no** |
| all three | — | **`setAuditLogLevel`, `LogRecordListener`, `setLogSink`: zero occurrences in any of them** |

Searched for `before init` / `after init` / `init()` near audit across all three: **no matches**.

**The sharp point.** The two canonical snippets show **opposite orders**, each correct for its own
situation and neither labelled:

- the **golden path** sets the sink *before* `init()` — correct for first wiring;
- the **`captureRun()` helper** sets it *after*, on a flow that is already initialised and being reused —
  correct there, because records are already flowing and the call replaces the listener.

An author copying the second pattern into a first wiring gets a working build, a running processor, and
audit records on stdout. There is nothing in any source that would tell them why.

---

## Draft — *Getting the log out*

> Everything below was measured against a generated processor on `fluxtion-runtime` 1.0.13 by running
> each wiring and counting the records captured and the characters written to stdout.

### 1 · The default sink is `System.out`

`EventLogManager`'s no-arg constructor — **the one the generated processor declares** — defaults its sink
to `System.out::println`. So a processor with audit compiled in and no sink attached does not lose its
audit log. It **prints it to the console**, one `eventLogRecord` block per event, for the life of the
process.

That is why the wiring order below matters rather than being a style question.

### 2 · Six wirings, measured

Same processor, same single event, each run captured:

| wiring | records captured | stdout | verdict |
|---|---|---|---|
| `setAuditLogProcessor(sink); init();` | **3** | clean | **do this** |
| `eventLogger.setLogSink(sink); eventLogger.logLevel(l); init();` | 2 | clean | works; no advantage, and misses a record |
| `init(); setAuditLogProcessor(sink);` | 2 | **leaks records** | the trap |
| `setAuditLogProcessor(sink); setAuditLogLevel(l); init();` | 4 | **prints a config line** | see §4 |
| `init(); setAuditLogLevel(l);` | 4 | **prints a config line** | see §4 |
| `init(); eventLogger.logLevel(NONE);` | — | clean | **silent no-op**, see §3 |

The extra record in row 1 is `init()`'s own lifecycle event. Attach the sink after `init()` and that
record — and anything else `init()` audits — has already gone to `System.out`.

### 3 · The level is stamped when a node is registered, not when you set it

`EventLogManager.nodeRegistered` builds each node's `EventLogger` and **stamps it with the level in force
at that moment**. Registration happens during `init()`.

So `eventLogger.logLevel(...)` **after** `init()` changes the field and none of the loggers already built
from it. The call returns normally and does nothing — measured as row 6 above, where setting `NONE` after
`init()` failed to suppress anything.

Set the level before `init()`, or use `setAuditLogLevel(...)`, which rebuilds the loggers — and see §4.

### 4 · These are not setters. They are events.

```java
default void setAuditLogProcessor(LogRecordListener p) { onEvent(new EventLogControlEvent(p)); }
default void setAuditLogLevel(LogLevel level)          { onEvent(new EventLogControlEvent(level)); }
```

`setAuditLogProcessor`, `setAuditLogLevel`, `setAuditLogRecordEncoder` and `setAuditTimeFormatter` are all
**dispatches into the graph**. Two consequences worth knowing before you need them:

- **their position relative to `init()` is significant**, which is the whole of §2 — a setter's would not be;
- **none of them can be called from inside a node.** A node that configured the audit log during a
  dispatch would be re-entering the processor it is running in.

And one to budget for: **handling the level control event prints `updating event log config: …` to
`System.out`**, unconditionally, every time. Harmless in a test; not harmless in a desktop application or
a service whose stdout is a log stream. If the compiled level is already what you want, not calling
`setAuditLogLevel` at all is a legitimate choice.

### 5 · The shortest correct wiring

```java
MyProcessor flow = new MyProcessor();
flow.setAuditLogProcessor(myListener);   // BEFORE init: init() audits, and the default sink is stdout
flow.init();
flow.onEvent(...);
```

If you need a level other than the compiled one and cannot afford the stdout line, set it on the auditor
before `init()` instead:

```java
flow.eventLogger.logLevel(LogLevel.DEBUG);   // silent, and before registration stamps the loggers
```

### 6 · Triage row, in the existing house style

| Symptom | Cause | Fix |
|---|---|---|
| audit records appear on the console although a sink was attached | the sink was attached after `init()`; `EventLogManager`'s default sink is `System.out::println` and `init()` audits | attach it before `init()` |
| `logLevel(...)` appears to have no effect | loggers are stamped with the level at `nodeRegistered`, during `init()` | set it before `init()`, or use `setAuditLogLevel` |
| `updating event log config:` on stdout | the level control event prints unconditionally | set the level before `init()` via the auditor, or leave the compiled level alone |

---

## A note for whoever takes this

The one-line change with the best ratio is in the **golden path**: it already has the right code. It needs
a clause saying *why* — that `init()` audits, and the default sink is the console. That is the sentence
that would have saved this repo a defect, and it is cheaper than everything else on this page.
