# Rationale — what each entry teaches, and where the docs say it

Sources, read 2026-10-17 (CLAUDE.md rule 6):

- **CT** — `claude.txt`, the Fluxtion framework reference (fluxtion repo, `docs/claude.txt`, `main`). Sections are cited by heading.
- **GP** — the golden path (fluxtion-playground.dev, `fluxtion-golden-path.md`). Sections are cited by number.
- **OB** — this repo's `docs/ONBOARDING.md` ▸ *Understand Fluxtion's execution model*, which records what the
  analyser learned from the framework's author and the M21 work. Cited only where it sharpens how a prompt is
  phrased, never as the source of a concept.

"Check, don't assert" marks an entry whose answer the sources do not state outright, so the prompt makes the
assistant check the records and generated code and say when it cannot tell.

Entry 1 of every pack is the same **PLACEHOLDER** teaching example (owner `example`, all-zero commit). It teaches
nothing by itself; its shape (builder, written source and GraphML, audit on) follows GP §2 and CT *Build-time
generation — `FluxtionGraphBuilder`*. See README ▸ *The teaching example each pack expects*.

## 01-basic.md — What is Fluxtion?

| # | Label | Concept | Source |
|---|---|---|---|
| 2 | What is Fluxtion, in plain words? | Events, `@OnEventHandler` entry points, `@OnTrigger` downstream reactions | CT *The mental model in 60 seconds*; CT *The authoring surfaces* (Imperative); CT *House style* |
| 3 | Follow one event through | Events flow in a compile-time-determined dispatch order; a silent node proves nothing | CT *The mental model in 60 seconds* ("flat dispatch tree", "compile-time-determined dispatch order"); CT *You author at the logical level* ("A flat dispatch tree in topological order"). Silence: OB item 1 — check, don't assert |
| 4 | Show me in the records | Audit records are derived from the topology; `auditLog.info(k, v)` is the business-readable part the author writes | CT *You author at the logical level* ("Per-event audit records derived from the topology, not hand-written"); GP §2 (`auditLog.info`, `cfg.addEventAudit(LogLevel.INFO)`) |
| 5 | What I wrote versus what Fluxtion generated | The author writes specification; the compiler emits the processor and fixes the order | CT *You author at the logical level*; CT *Build-time generation*; GP §2 (`setWriteSourceToFile`) |
| 6 | Who calls my methods? | The generated processor dispatches; there is no scheduler | CT *The mental model in 60 seconds* ("no scheduler, no thread pool, no async runtime"); CT *Anti-patterns* (don't bypass `@OnTrigger` with direct calls) |
| 7 | Fluxtion as a DI container | Wiring by constructor reference; roots registered, the rest discovered; source-gen reconstructs fields by constructor calls and field assignments; setter wiring of non-`final` fields; services injected with `@ServiceRegistered` | CT *The mental model in 60 seconds* ("The graph IS the constructor wiring"); CT *Required rules* 3; GP §2 (register only the roots); GP §4b (setter-wiring); CT *Services — typed integration boundaries*. The docs never use the phrase "DI container", so the prompt asks whether the comparison is fair and where it breaks down |
| 8 | How would my own code look? | Plain Java classes, state in fields, upstreams in the constructor; side effects at terminal nodes | CT *House style*; CT *Why this framework fits AI authoring* (local reasoning per node) |

## 02-intermediate.md — Local knowledge, global behaviour

| # | Label | Concept | Source |
|---|---|---|---|
| 2 | A node only knows its parents | Local declaration, global order derived by the compiler (topo-sort) | CT *The mental model in 60 seconds* ("Reason locally per node. Let the framework handle the global topology"); CT *Why this framework fits AI authoring* ("The compiler does global reasoning") |
| 3 | The diamond: how often does D run? | Topological dispatch over a shared descendant | CT *The mental model in 60 seconds* (topo-sort, flat dispatch tree). **Check, don't assert**: neither source states how many times a node with two changed parents runs in one cycle |
| 4 | When only one side changes | Propagation follows the dirty flag of whichever parent changed | CT *Conditional dispatch — `@OnTrigger(dirty)`*; GP §3 (`@OnParentUpdate` to know *which* parent changed). **Check, don't assert**: the sources do not state what D reads from an unchanged parent or how a mixed diamond is guarded; OB item 2 (a child running proves its parent ran, not which way it answered) |
| 5 | The very first event | State lives in user fields; `init()` resets only per-event machinery | CT *Required rules* 2; GP §5 rule 2. **Check, don't assert**: neither source describes first-event propagation |
| 6 | The true or false a node returns | The returned boolean is the dirty / propagation control; `dirty=false` fires on the inverse | CT *Conditional dispatch — `@OnTrigger(dirty)`*; OB item 2 |
| 7 | Guards and change detection | The returned boolean, `@OnTrigger(dirty=false)`, `@OnParentUpdate`, `@NoTriggerReference`; a node needs a trigger annotation to propagate; one `@OnTrigger` per node | CT *Conditional dispatch*; CT *Bridging DSL flows into imperative `@OnTrigger` nodes*; GP §3 (the `@NoTriggerReference` and multiple-`@OnTrigger` rows) |
| 8 | Compared with hand-written callbacks | Direct calls between nodes reintroduce ordering bugs; compiler-determined order does not | CT *Anti-patterns* ("direct calls reintroduce ordering bugs"); CT *You author at the logical level* (bypassing `@OnTrigger` loses dispatch ordering). The callback half is general knowledge; the prompt keeps the Fluxtion half to the docs and records |
| 9 | Compared with an event bus or a scheduler | No scheduler, no thread pool, single-threaded deterministic dispatch | CT *The mental model in 60 seconds*; CT *Required rules* 4; CT *Anti-patterns* (RxJava-style habits) |

## 03-advanced.md — Thinking in Fluxtion

| # | Label | Concept | Source |
|---|---|---|---|
| 2 | The closed-world build step | The compiler statically analyses the graph from registered roots and constructor references; compile-time reachability | CT *The mental model in 60 seconds* ("statically analyses the dependency graph"); CT *You author at the logical level* ("Compile-time reachability that proves which nodes a given event can reach"); GP §2 (roots only, upstream discovered). "Closed world" is this pack's term, not the docs' |
| 3 | Source generation is serialisation | Fields reconstructed by generated code; non-renderable fields need `@FluxtionIgnore`/`transient`; the bystander instance | CT *Required rules* 3; CT *Source-gen annotation triage*; CT *Compile shapes* (bystander); GP §3 (assertions read 0/null; finality rule) |
| 4 | Hidden shared state | State changed outside events breaks audit/replay | CT *Required rules* 5; CT *Anti-patterns* ("Don't mutate state from outside the graph"). **Check, don't assert**: the docs do not discuss static shared state specifically, so the prompt asks which parts are stated and which inferred |
| 5 | Undeclared dependencies | The graph is the constructor wiring; a direct call bypasses dispatch ordering | CT *The mental model in 60 seconds*; CT *You author at the logical level* (bypassing `@OnTrigger`); CT *Anti-patterns*. **Check, don't assert** for the static-lookup case |
| 6 | Side effects and the outside world | Side effects at terminal nodes; no threads; mutation through events or `runInEventCycle`; named sinks | CT *House style*; CT *Required rules* 4 and 5; CT *Emitting output — named sinks* |
| 7 | Determinism and replay | Same input, byte-identical output; replay reproduces exactly; clock fields aside | CT *The mental model in 60 seconds* (properties 1 and 2); CT *You author at the logical level* (byte-equal replay); CT *Browser-runtime constraint* ("byte-identical audit records (modulo clock fields)"; cross-event state needs `reset()`) |
| 8 | Composing components I didn't write | The compiler infers the graph from constructor references and emits topologically ordered dispatch; any Java method can be an operator; services are bound by interface | CT *The mental model in 60 seconds*; CT *The authoring surfaces* (DSL: "every Java method ever written is a candidate operator"); CT *Why this framework fits AI authoring* (open-set operators); CT *Services*. Deliberately kept to what US 11,074,079 B2 publishes and nothing more |
| 9 | Where the model stops | The compiler is the safety boundary; what it guarantees and what it cannot | CT *You author at the logical level* ("The compiler is the safety boundary; you operate inside it"); CT *Required rules*; CT *Anti-patterns* |

## Where the sources were unclear

- **What the audit log records.** CT *The mental model in 60 seconds* says "Every node activation is captured";
  OB item 1 says a node appears in a record only if it writes audit output at the level in force. The prompts
  follow OB (silence proves nothing) and ask the assistant to say when a silent node leaves a question open.
- **The diamond and mixed propagation.** Neither source states how often a node with two changed parents runs in
  one cycle, nor what it observes from an unchanged parent. Entries 02/3 and 02/4 are phrased to be checked.
- **First-event behaviour** is not described in either source. Entry 02/5 is phrased to be checked.
- **"Guards".** Neither source uses the word; entry 02/7 names the mechanisms they do describe.
- **"DI container" and "closed world"** are this pack's framings. The docs support the substance (constructor
  wiring, setter wiring, service injection; static analysis from roots) but not the labels, so the prompts ask
  whether the framing is fair rather than asserting it.
