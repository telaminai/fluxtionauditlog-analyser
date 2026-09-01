## Annotations — the complete set, grouped by how they fail you

**Group A decides what runs. Get one wrong and the build is green, the tests pass, and only the audit
log shows it.** That is where every expensive bug in this project has come from. Groups B and C fail
loudly or are caught by a compiler diagnostic, so they are listed for completeness and not demonstrated.

### A — what runs, and in what order (silent when wrong)

| annotation | on | meaning |
|---|---|---|
| `@OnEventHandler` | method | entry point for one event type. Returning `false` stops the cycle here |
| `@OnTrigger` | method | runs after a *triggering* parent propagated. Returning `false` stops the cycle here |
| `@OnParentUpdate` | method | called per parent that updated — use when you must know **which** |
| `@NoTriggerReference` | field | this parent is **data only**: read it, never be triggered by it |
| `@TriggerEventOverride` | field | this parent is the **only** trigger; every other field behaves as `@NoTriggerReference` |
| `@PushReference` | field | inverts the wave: this node is notified *before* the referenced target, so it can push into it |
| `@AfterTrigger` | method | after-event phase, reverse topological order |
| `@AfterEvent` | method | after-event phase, reverse topological order, for the whole event |
| `@FilterId` | field | a default filter value for the event handlers in this class |
| `@FilterType` | — | filter match strategy for an `@OnEventHandler` |
| `@ExportService` | type use | whether dependents are invoked as part of the exported call chain |
| `@NoPropagateFunction` | method | an exported function that does not propagate |

### B — lifecycle (fails loudly, or not at all)

| annotation | on | meaning |
|---|---|---|
| `@Initialise` | method | bound to `init()`; no arguments |
| `@Start` | method | bound to `start()` |
| `@StartComplete` | method | bound to `startComplete()` |
| `@Stop` | method | bound to `stop()` |
| `@TearDown` | method | bound to `tearDown()` |
| `@OnBatchEnd` | method | bound to `BatchHandler.batchEnd()` — the transaction boundary |
| `@OnBatchPause` | method | bound to `BatchHandler.batchPause()` |

### C — generation and wiring (a diagnostic usually catches these)

| annotation | on | meaning |
|---|---|---|
| `@AssignToField` | field, method, parameter | maps a constructor parameter to a field of this name — the fix for `FLX-1001` with two same-typed parents |
| `@Inject` | field | injection point; the instance is created by a `NodeFactory` |
| `@ConstructorArg` | field | this reference is supplied through the constructor |
| `@FluxtionIgnore` | field | excluded from processor serialisation. **Careful:** correct for derived local state, destructive for builder-supplied configuration, which it silently discards |
| `@SepNode` | field, type | add this reference to the node graph |
| `@ExcludeNode` | field, type | do **not** add this reference to the node graph |
| `@Config` / `@ConfigList` | field | static key/value configuration passed to a `NodeFactory` |
| `@ConfigVariable` / `@ConfigVariableList` | field | configuration read from a variable at construction time |
| `@Disabled` | method, type | conditionally disable processing of a generation annotation |
| `@ClassProcessor` | — | a service that inspects compiled application classes |

Source of truth: `com.telamin.fluxtion.runtime.annotations` and `…annotations.builder`.
