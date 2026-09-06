# Round 59 — verifying two M50 claims that had never been executed

**Date** 2026-09-06 · **Machine** macOS aarch64 (Darwin 25.6.0)
**Runtime** Oracle GraalVM 25.0.4+7.1 LTS (`graal-25.3.4.1` build), `native-image` 25.0.4
**Purpose** close the two gaps the M50 review briefs admitted: W10 had never run against a real
Fluxtion processor, and W12's native-image claim was inferred rather than demonstrated.

Round 58 measured ~700 runs but its *conclusions* rested in two places on things nobody had executed.
This round executes them. It also found a defect in the harness itself, and a conclusion round 58's
own data supports but its write-up never drew.

---

## 0. The workspace had to be rebuilt, and that is a finding

Round 58's `scripts/` expects `classes/` and `gvm/` beside it. **Neither survives** — the same defect
already tracked as "round 57 lacks its jar workspace" (tracker M48.15). Worse, `src/base/BaseProcessor.java`
is a **hand-written stand-in** whose own javadoc says "what the generator emits", and `MarketTick`
does not exist in the repo at all.

So round 58 never benchmarked a generated processor in the arms that used `BaseProcessor` — it
benchmarked a faithful *model* of one. That is legitimate and was labelled, but it means "run the bench
against a real processor" required generating one, which this round does.

## 1. Three traps hit while getting a real processor generated

Recorded because each would silently produce a wrong answer, and two produced one here.

**1.1 A stale installed jar shadows the branch.** `mvn test` does not `install`. The
`fluxtion-generator-core-1.0.67-SNAPSHOT.jar` in `~/.m2` was built 2026-09-02 — before the W12 change —
and carries the OLD template. Generation used it. A developer verifying W12 this way generates
pre-W12 code and concludes the change does not work.

**1.2 Generation silently falls back to a REMOTE service.** With Velocity absent from the classpath the
`ServiceLoader` finds no local `SourceGenerator` and logs, at DEBUG only:

```
no local SourceGenerator found on classpath, falling back to remote
using remote /generate-source endpoint
Sending DTO+config to remote source endpoint at https://fluxtion-source-gen…/generate-source
```

**The build succeeds and emits a processor built from the PUBLISHED template, not the local one.** Any
local compiler change is silently inert. Nothing at INFO or WARN says so. This is the single most
dangerous behaviour found in this round: a compiler developer can change a template, watch a green
build, and measure the old output.

*It also has a use*: it gives a free control arm — the published generator's output — used in §3.

**1.3 Classpath order.** Module `target/classes` appended AFTER dependency jars is shadowed by them.

## 2. W10 — the bench, run against a real generated processor, and a defect in the bench

`tools/bench/dispatch-bench.py` had **never** been run against anything but a fake program. The first
real run failed on its own gate:

```
[FAIL] every arm produced a RESULT line — arm 'hand' produced no RESULT line
```

**A genuine defect in the harness.** It built `java -cp CP Bench -Darm=hand …`, putting the `-D` flags
AFTER the main class, where the JVM passes them to `main(String[])` as program arguments.
`System.getProperty("arm")` returned the default, so **every arm would have measured the same code**.

That is precisely the silent wrong-answer this harness exists to prevent, and it was in the harness.
It failed loudly rather than reporting "no measurable difference between the arms" — which is the
error round 58 published in draft — because the fake program in the tests happens to read `sys.argv`
while a JVM does not. Fixed; two regression tests added (`test_jvm_property_flags_precede_the_main_class`,
`test_native_mode_appends_flags_after_the_binary`). Suite 13/13.

### The measured result

Real W12-branch-generated processor, 10 nodes, void triggers, `setSupportDirtyFiltering(false)`,
auditors present (clock, nodeNameLookup, serviceRegistry). One binary, arms by `-Darm=`, 9 interleaved
rounds, warm 5M, 200M iterations, EpsilonGC. **All gates passed. All three arms emit identical checks**
(`buf=11551.2267 upd=205000000 brch=102500000`).

| arm | median ns | min | max | sd | events/sec |
|---|---|---|---|---|---|
| `generated` — default clock | **29.08** | 28.94 | 31.78 | 0.86 | 34M |
| `generatedStreamClock` — supplied `ClockStrategy` | **5.03** | 4.90 | 5.20 | 0.10 | 199M |
| `hand` — hand-rolled flat | **2.19** | 2.12 | 2.24 | 0.03 | 458M |

Paired, same round: stream clock **−24.02 ns (−82.6%)**, faster in 9/9, ranges disjoint.

**Cross-validation:** `generatedStreamClock` at 5.03 ns sits beside round 58's recorded JIT base case of
4.80 ns. Different machine state, same shape, same order. The harness reproduces the historical figure.

## 3. W12 — the native-image claim, demonstrated

W12's justification was that a hand-written `reflect-config.json` stops being necessary. That had never
been run. Three images, same graph, `--no-fallback`, `-O1`:

| image | reflect-config | `getNodeById("mid")` | `getAuditorById("clock")` | `getLastAuditLogRecord()` | exit |
|---|---|---|---|---|---|
| published generator (3 reflective sites) | **none** | PASS | **FAIL** `NoSuchFieldException: clock` | PASS | 3 |
| published generator | **supplied** | PASS | PASS | PASS | 0 |
| **W12 branch (0 reflective sites)** | **none** | PASS | **PASS** | PASS | 0 |

**The claim holds.** The middle row closes the causal chain: the failure is caused by the reflective
lookup and is cured either by config the user must write, or by W12 removing the reflection.

**But it is one site of three, and the brief should say so.**

- `getNodeById` passes either way — `"mid"` is in `nodeNameLookup`, so the reflective fallback is never
  reached. It would only differ for an id that is an auditor.
- `getLastAuditLogRecord` passes either way for a worse reason: it catches `Throwable` and returns `""`.
  Under the published generator it silently returns empty instead of the record. W12 does not change
  that — `EventLogManager.NODE_NAME` is `"eventLogger"`, which is not a field here, so both throw
  internally and both swallow it.
- **`getAuditorById` is the one that observably breaks**, and it is the documented way to reach an
  auditor.

Also worth stating: **dispatch itself never needed the config.** Both images ran events correctly. The
config was only ever needed for the introspection API.

## 4. The conclusion round 58's own data supports and its write-up never drew

`generated` 29.08 vs `generatedStreamClock` 5.03 is a 24 ns difference from one cause:

```java
// Clock.java
@Initialise public void init() { wallClock = System::currentTimeMillis; }
@Override public void eventReceived(Object event) {
    processTime = getWallClockTime();     // System.currentTimeMillis() on EVERY event
    eventTime   = processTime;
}
```

**This is not new data.** `results/all-results.csv` from round 58 has it on every runtime measured:

| runtime | `fluxtionDefault` | `fluxtionStreamClock` | delta |
|---|---|---|---|
| graal25.3-graaljit | 19.01 | 7.71 | −11.3 |
| oracle25-graaljit | 19.01 | 8.03 | −11.0 |
| corretto21-c2 | 18.45 | 8.58 | −9.9 |
| oracle25-native | 20.87 | 13.98 | −6.9 |
| oracle25-native-pgo | 19.94 | 9.58 | −10.4 |

**The measurement was made and the conclusion was never carried forward.** `spec-generated-dispatch-performance.md`
§3.3 discusses the `ClockStrategy` *field typing* at +0.25 ns and calls the clock **"third-order; do it
if the field is being touched anyway"** — a verdict about the field indirection that reads as though it
covers the clock generally. The same round's raw data shows the clock **read** is the largest single
line item in the whole CSV.

Both statements are true about different things. The spec never separates them, and W8 was deferred on
the "third-order" wording.

**Consequence for W9.** The performance-configuration page listed five decisions and did not include
*supply a `ClockStrategy`*, which on this evidence is the largest application-level lever in the default
configuration on every runtime measured. Corrected in that document.

**Caveat.** `System.currentTimeMillis()` cost is platform-dependent; 24 ns here is macOS/aarch64. Round
58's ~11 ns on the same class of machine is the more conservative figure. The direction and the
dominance are consistent across seven runtimes; the magnitude is not a portable constant.

## 5. What is still not done

- **No native-image throughput arm.** §2 is JIT only. The native floor (1.42 ns) is NOT re-measured here
  and nothing in this round should be quoted against it.
- **The clock finding is not re-measured under native-image** in this round; §4's native rows are round
  58's.
- **W2/W3 are not measured at all.** Still true: no number should be attached to that branch.

---

## 6. The native arm — and what it says about the 1.42 ns floor

Same three arms, one native binary, Oracle GraalVM 25.0.4 `native-image`, **no PGO**, epsilon GC,
`--no-fallback`, no reflection config. Processor generated by the **local** compiler
(`-Dfluxtion.sourceGeneratorId=local`, `Selected SourceGenerator by id 'local'`, 0 reflective sites).
**All gates passed; all three arms emit identical checks.**

| arm | median ns | min | max | sd | events/sec |
|---|---|---|---|---|---|
| `generated` — default clock | 16.07 | 15.91 | 16.10 | 0.07 | 62M |
| `generatedStreamClock` | **8.92** | 8.88 | 9.01 | 0.04 | 112M |
| `hand` | 3.14 | 3.06 | 3.18 | 0.04 | 318M |

Paired: stream clock **−7.09 ns (−44.1%)**, 9/9, disjoint. The clock finding holds on native too, at
roughly half the JIT proportion.

**Epsilon GC survived 200M events on every arm** — the zero-steady-state-allocation property holds for
a real generated processor, which is a result in its own right and was previously only shown for the
hand-written model.

### 6.1 A prediction of mine that was wrong

I expected the 3-arm binary to be the reason these are far from 1.42 ns, since round 58 measured
identical source at 4.86 vs 1.58 ns in multi-arm vs single-purpose binaries. So I built single-purpose
binaries. **The opposite happened for the generated arm** (5 reps each, medians):

| arm | 3-arm binary | single-purpose binary |
|---|---|---|
| `generatedStreamClock` | 8.92 | **11.07** (slower) |
| `hand` | 3.14 | **2.41** (faster) |

The multi-arm penalty round 58 found applies to the hand-rolled arm and **reverses** for the generated
one. Recorded rather than explained: I do not know why, and it is not required for anything below.
*(Different binaries — T1 forbids the harness from reporting this as a comparison, and it is not
presented as one.)*

### 6.2 What the 1.42 ns floor actually measured

The floor arm is `V1_bare.java`:

```java
BaseProcessor p = new BaseProcessor();
for (long i = 0; i < it; i++) p.handleEvent(set(e, i));
```

`BaseProcessor` is the **hand-written stand-in** from §0 — its own javadoc says "what the generator
emits". It calls `handleEvent` directly and has no auditors, no `processEvent` re-entrancy wrapper and
no callback dispatcher. **The 1.42 ns figure is a measurement of that class, not of generated code.**

The spec is not silent about this: §1 states the base case is "no auditors, no re-entrancy wrapper".
But **no auditors and no re-entrancy wrapper is a configuration the generator cannot currently
produce** — `clock`, `nodeNameLookup` and `serviceRegistry` are emitted into every processor, and
`processEvent` wraps every event. That configuration is the *target* of W1, W2 and W4, which is why
those work items exist.

Lined up, with the caveat that round 58 ran on a different machine state and absolute cross-run
comparison is weak:

| what was measured | native, no PGO |
|---|---|
| round 58 `V1_bare` — **hand-written `BaseProcessor` model** | **1.42** |
| round 58 `fluxtionStreamClock` — **real generated processor**, ladder | 13.98 |
| this round `generatedStreamClock` — real generated, 3-arm binary | 8.92 |
| this round `generatedStreamClock` — real generated, single-purpose | 11.07 |
| this round `hand` — `HandBase`, single-purpose | 2.41 |

Round 58's own ladder measured the real generated processor at **13.98 ns** and its floor table reports
**1.42 ns** in a column headed "Fluxtion". Both numbers are in the same round's data. The difference
between them is not noise, not shape, and not the harness — it is that one measured generated code and
the other measured a model of it.

**What is safe to say from this round:**

- The generated processor, base case with a stream clock, no PGO: **~9–11 ns native, ~5 ns JIT** on
  this machine, with gates passed and output verified.
- Generated dispatch **is** cheap. The gap to the hand-rolled arm is the auditors, the entry wrapper
  and the clock — exactly what W1/W2/W4 target — not the node dispatch the generator emits.
- **1.42 ns is a target, not a current measurement of generated code**, and "0.95× hand-written /
  703M events per second" compares two hand-written classes to each other.

**This is an owner call, not a defect finding.** The modelling is legitimate and labelled inside round
58. The risk is only in how the number travels: the floor table's column heading is "Fluxtion", and
that is the form the figure has been quoted in.
