# Test index — which test defends which claim

A performance band says a number has not moved. It says nothing about whether the number describes a
program that computes the right answer, and this round produced three separate cases where it did not:
a profile that discarded a filter and measured 1.7x faster for it, a C++ target that emitted no dirty
flags at all, and a hand-written C++ chain published as though the generator produced it.

So the bands in `control-bands.tsv` are only half the gate. This is the other half: for each claim, the
test that makes it false if it stops being true. A claim with no row here is unguarded, and should be
read as an observation rather than a property.

## Cross-language correctness

| claim | defended by | repo |
|---|---|---|
| A hand-written graph computes identically in Java and C++, step by step | `CppJavaAuditParityTest` | compiler |
| **A DSL graph** computes identically in Java and C++, step by step | `CppDslAuditParityTest` | compiler |
| The C++ target refuses a DSL graph it cannot model rather than emitting a wrong one | `CppDslRefusalTest` | compiler |

Both parity tests compare a binary audit log entry for entry, decoded by the same Java reader, with a
**data-driven clock** so timestamps are a function of the input and a difference is a defect rather
than jitter. The DSL test's prices are `5, -3, 7, -1, 4` — the filter rejects two of five, deliberately:
a run whose data never exercises the filter cannot tell a working filter from an ignored one.

## DSL semantics under configuration

| claim | defended by | repo |
|---|---|---|
| No performance profile changes the answer | `DslProfileCorrectnessTest.*AgreesOnTheAnswer` (4 profiles x 5 dispatch modes) | compiler |
| A filter's decision survives every profile | `DslProfileCorrectnessTest.filterHonouredUnder*` | compiler |
| A window publishes on close only, under every profile | `DslProfileCorrectnessTest.tumblingWindowPublishesOnCloseOnly*` | compiler |
| flatMap delivers every element and signals batch end | `DslProfileCorrectnessTest.flatMapDeliversAllElementsAndSignalsEnd*` | compiler |
| A config that removes a capability the graph needs is refused at BUILD time, naming the flag | `DslProfileCorrectnessTest.*RefusedAtBuildTimeNamingTheFlag` | compiler |
| `setSupportSubscriptions(false)` is NOT refused, because it is safe | `DslProfileCorrectnessTest.disablingSubscriptionsIsNotRefusedBecauseItIsSafe` | compiler |

The last row matters as much as the others: a check that refuses too much is a check that will be
disabled.

## C++ atomic capabilities

| claim | defended by | repo |
|---|---|---|
| Every runtime annotation is emitted, or recorded as absent | `CppAnnotationCoverageTest` | compiler |
| `@OnParentUpdate` fires per PARENT VARIABLE, so same-type parents stay distinct | `CppParentUpdateTest` (compiled + run) | compiler |
| `@AfterEvent` / `@AfterTrigger` run, and run in the right place | `CppAtomicCapabilityTest` | compiler |
| `propagate=false` updates the node without triggering children | `CppAtomicCapabilityTest` | compiler |
| `@NoTriggerReference` does not fire the child | `CppAtomicCapabilityTest` | compiler |
| `@PushReference` orders the target AFTER the pusher | `CppAtomicCapabilityTest` (sequence stamp) | compiler |
| The emitted surface compiles and behaves under every profile | `CppCapabilityGridTest` | compiler |

These are **behavioural**, not textual, and the distinction is not academic: an earlier pass reported
`@TriggerEventOverride`, `@NoTriggerReference` and `@PushReference` as supported because `.calc()`
appeared in the emitted source. None of those three changes *whether* a method is emitted — only *when*
it fires and in *what order*.

## Audit record

| claim | defended by | repo |
|---|---|---|
| The binary record round-trips every tag, including strings | `BinaryLogFileRoundTripTest`, both parity tests | core / compiler |
| `logTime` is the process time the clock already read | `LogRecordLogTimeTest` | core |
| Method tracing produces a publishable record | `BinaryRecordHotPathTest` | core |

String-valued entries were **silently dropped from every binary record** until 2026-09-09 — they wrote
into a byte buffer the reader does not parse, so `auditLog.info("mapFunction", auditInfo)` produced
nothing at all. Neither log looked wrong on its own; only comparing two implementations showed it.

## Time

| claim | defended by | repo |
|---|---|---|
| A window rolls at the size it was given | the windowing suites — `PrimitiveStreamBuilderTest`, `GroupBySlidingWindowTest`, `EventStreamBuildTest` | compiler |
| The default clock agrees with the unit `atMillis` names | the same suites — 30 of them failed when it did not | compiler |

There is no test named "the clock is in milliseconds", and there does not need to be: the windowing
suites are that test. When the default strategy moved to nanoseconds they all failed, and **not one of
the failures mentioned a clock** — which is the argument for keeping them fast and green rather than
adding a narrower assertion that would have caught it more legibly.

## What is NOT defended

- **Windowed graphs in C++.** Not emitted, so not measured and not compared. Aggregates today are a
  single `int32_t` inside the node struct — stack-resident, no allocation, which is part of why the C++
  arm is cheap. Windows need buffers whose lifetime spans events, and the stack-versus-heap choice
  there is a design decision with a real cost. No figure in this kit predicts it.
- **`mkdocs build --strict` on the core site.** Never run; mkdocs is not installed here.
- **Any C++ figure recorded before 2026-09-09** — those arms were measured while the target emitted no
  dirty flags at all, so conditional propagation was absent rather than cheap.
