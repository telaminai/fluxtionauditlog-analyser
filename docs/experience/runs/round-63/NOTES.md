# Round 63 — what does the audit trail cost, and can it be zero-allocation?

**Runtime** Oracle GraalVM 25.0.4+7.1, macOS/aarch64 · **Graph** 10 nodes, **3 of which log** (`tickIn`,
`exposure`, `buffer`) — boundaries and decision points, not every arithmetic step.
**Sink** no-op; this measures the cost of BUILDING a record, not writing it. Allocation measured
directly with `ThreadMXBean.getThreadAllocatedBytes`, not inferred.

**Owner's ask:** *"We should have a low latency audit profile. Audit on, no tracing, no event to string
and anything else not needed. We should be zero gc and the messages should be short id names and not on
every node."*

---

> **⚠ SECTIONS 1–3 BELOW WERE MEASURED ON THE WRONG JDK AND ARE SUPERSEDED BY §5.**
> The benchmark ran on whatever `java` was on `PATH` — **Corretto 21** — while every other measurement
> today used GraalVM 25. `StringBuilder.append(double)` allocates **96 bytes on JDK 21 and 0 on JDK 25**.
> The owner said *"the audit log is zerogc, you must be allocating somewhere"* and was right: the
> allocation was the JDK's, not Fluxtion's. Kept below unedited because the wrong conclusion and how it
> was reached are the useful part.

## 1. The measurement

| mode | ns/event | **bytes/event** |
|---|---:|---:|
| `none` — `LOWEST_LATENCY`, no audit | **5.40** | **0.000** |
| `minimal` — audit, tracing OFF, `printEventToString(false)`, `printThreadName(false)` | 265.3 | **432.003** |
| `traced` — audit + full node-invocation tracing | 418.9 | 432.003 |

**Tracing is not the expensive part.** It costs 154 ns and **zero extra bytes**. Turning it off is worth
having and is not what stands between the current state and a low-latency audit profile.

## 2. The blocker, named: rendering a `double` as decimal text

Same graph, same three logging nodes, same five values per record — logged as `long` instead of
`double`:

| values logged as | ns/event | bytes/event |
|---|---:|---:|
| `double` | 265.3 | **432.003** |
| `long` | **153.9** | **0.003** |

**Double-to-text formatting is the entire allocation and 42% of the time.** `StringBuilder.append(long)`
writes digits in place; `append(double)` runs a decimal-conversion that allocates. Nothing else on the
path allocates — `LogRecord` reuses one `StringBuilder`, `clear()` is `setLength(0)`, `init()` sets
`clearAfterPublish = true`, and the sink is handed the record object rather than a String.

**So the owner's zero-GC requirement is achievable today, and the axis is not the one the ask
assumed.** Short id names would shrink the record; they would not remove the allocation. *Values
rendered as decimal text* are what allocate.

## 3. Where that leaves the profile

**Achievable now, unchanged code:** audit on, tracing off, no event `toString`, no thread name, few
nodes logging, **values logged as integers** → **0.003 bytes/event and ~154 ns/event**.

**Still 28× the no-audit path** (5.40 → 153.9), so it is an "on for a venue that can afford 150 ns"
mode rather than free. Against Mongoose's 250 ns p50 it is +62%; against a 100 ns budget at 10M msgs/s
it does not fit.

**The design that would change the answer** — and it fits the architecture rather than fighting it:
carry values as **raw bits or fixed-point integers** in the record and let the **analyser** render them.
The analyser already owns interpretation; the processor does not need to format decimals on the event
path to produce a log the analyser can read. That is a real piece of work, aimed at a measured cost.

## 4. Method notes

- **The no-op sink was checked for elimination.** A genuinely empty `processLogRecord` can let the
  compiler delete the record construction feeding it, which would read as "free". A counting sink was
  run alongside and confirmed one record per event (100,002 and 100,000 over 100,000 events).
- **The first run was invalid and looked catastrophic.** The sink is **not** carried into generated
  source — the generator emits `new EventLogManager()` and the sink is set at runtime via
  `getAuditorById(EventLogManager.NODE_NAME).setLogSink(...)`. Until that was found, records were being
  written to stdout and the audited modes were measuring console I/O.
- Allocation was measured, not inferred from an Epsilon-GC OOM.


---

## 5. CORRECTED — measured on GraalVM 25.0.4, the JDK everything else today used

**Isolation probe, no Fluxtion involved — just a `StringBuilder`:**

| | JDK 21 (Corretto) | JDK 25 (GraalVM) |
|---|---:|---:|
| `append(double)` | **96.000 bytes/call** | **0.000** |
| `append(long)` | 0.000 | 0.000 |
| `append(int)` | 0.000 | 0.000 |

Four doubles per record × 96 B ≈ the 432 B/event §1 reported. **The allocation was the JDK's.**

**The audit path re-measured on JDK 25:**

| mode | ns/event | bytes/event |
|---|---:|---:|
| `none` — no audit | **5.4** | 0.000 |
| `minimal` — audit, tracing off | **262** | **0.003 — zero** |
| `traced` — audit + node tracing | 446 | **0.003 — zero** |

**The audit log is zero-GC, with `double` values, unmodified, today.** The owner's requirement was
already met and this round briefly claimed otherwise.

### 5.1 What survives, and what does not

**Does not survive:** *"double-to-text formatting is the blocker"*. There is no allocation blocker. §2's
432 B and §3's design proposal — carry raw bits, let the analyser render — were aimed at a JDK artifact.

**Survives, and is the real finding:** **audit costs ~256 ns/event on top of a 5.4 ns graph** — 48×.
That is not allocation-driven; it is unchanged between JDK 21 (265) and JDK 25 (262), where allocation
went from 432 B to zero. It is the cost of building a text record.

**Also survives, weakened:** integer values are still cheaper in *time* — 222 ns against 275 on JDK 25,
about 20%. Worth having, no longer worth redesigning for.

**Tracing costs 184 ns and no allocation** (262 → 446), confirmed on both JDKs.

### 5.2 The profile, as measurable today

Audit on, tracing off, no event `toString`, no thread name, few nodes logging:
**~262 ns/event, zero allocation, ~3.8M events/sec.** With integer values, ~222 ns.

### 5.3 Method failure

**The benchmark did not pin its JDK.** Every native measurement today explicitly invoked GraalVM 25;
the JIT audit runs used bare `java` and silently got Corretto 21. One line of output —
`java -version` — would have caught it, and it is now the first thing the audit bench prints.

That is the second time today a measurement was invalidated by an unstated input (the first was
`--attempts 1` on the node-count sweep). Both were caught by someone questioning the result rather than
by the harness.
