# Recorded baselines — the numbers a later run must reproduce

Every row is a **measured** figure with the configuration that produced it. A new measurement that
disagrees is either a regression or a changed input; the point of the table is that it cannot be
neither. `validate-controls.sh` enforces the bands in `control-bands.tsv` against this table.

**Method for every row:** interleaved arms, **minimum of ≥5 reps**, idle machine, harness invariants
asserted (records published non-zero, checksum matched, `-D` placement verified). Native: per-arm PGO
with the profile SHA recorded, and every build input verified in the build log rather than assumed.

| | |
|---|---|
| Machine | Apple M4 |
| JIT | OpenJDK 25.0.2 (build 25.0.2+10-69) |
| Native | Oracle GraalVM 25.0.4+7.1 · `native-image 25.0.4` |
| Harness | **h5** — stamps a runtime digest as well as its own version; h4 — adds a no-op record arm; h3 fixed the escaping processor |

## After the id-path fix (round 63 §20) — harness h4

`EventLogger` resolves each node and key name to an id **once per node**, not once per event.

| arm, 30-node converging, every node logs | JIT ns | native ns |
|---|---:|---:|
| fair baseline, no auditor | 19.42 | 17.64 |
| binary record, String path (before) | 74.35 | 167.82 |
| **binary record, id path (now)** | **54.6–61.1** | **82.2–115.8** |
| text record | 403.0 | 698.6 |

The native binary range spans the value-store choice: **81.3 with `VarHandle`, 115.8 with the byte
loop** that core must ship for Java 8. JIT is the other way round — 54.6 byte loop, 63.2 `VarHandle`.

> **Bands are only valid for the harness version they were recorded under.** h1/h2 numbers are not
> comparable to h3: h2 let the processor escape its loop method, which costs **4.3× on native** and
> nothing on JIT, with nothing in the build log to show for it.

## The 30-node, 5-event-type, converging-tail graph — harness h3

| Variant | Nodes | Audit | Record | Profile | JIT ns | JIT M/s | native ns | native M/s |
|---|---|---|---|---|---:|---:|---:|---:|
| **`c-dispatch`** no audit | light | none | — | `LOWEST_LATENCY` | **12.34** | 81.0 | **2.17** | **461.0** |
| `c-audit-sparse` one node logs | light | on | binary | `LOW_LATENCY_AUDIT` | 47.6 † | 21.0 | 70.9 † | 14.1 |
| `c-audit-sparse` one node logs | light | on | text | `LOW_LATENCY_AUDIT` | 147.2 † | 6.8 | — | — |
| **`c-audit-dense`** every node logs | light | on | binary | `LOW_LATENCY_AUDIT` | **81.51** | 12.3 | **119.36** | 8.4 |
| `c-audit-text` every node logs | light | on | text | `LOW_LATENCY_AUDIT` | 403.0 † | 2.5 | 698.6 † | 1.4 |
| `c-heavy` every node logs | **heavy** | on | binary | `LOW_LATENCY_AUDIT` | 485.8 † | 2.1 | 517.7 † | 1.9 |

† measured under harness h2 (processor escaping). **Re-measure under h3 before calibrating anything
against these rows.** The two bold rows are h3 and are what `control-bands.tsv` gates on.

## Derived, from the h3 rows — with the auditor as the ONLY variable

The audit cost is only meaningful against a baseline that differs by the auditor alone. A
`LOWEST_LATENCY` baseline has **172 fewer `isDirty_` references** than an audited processor, so its
delta is audit *plus* conditional propagation. The fair baseline uses the same profile and installs no
auditor; both generated sources are diffed feature-by-feature before the numbers are taken.

| arm | JIT ns | native ns |
|---|---:|---:|
| `LOWEST_LATENCY`, no auditors | 11.84 | **2.13** |
| `LOW_LATENCY_AUDIT`, **no auditor** (the fair baseline) | 29.01 | 25.47 |
| `LOW_LATENCY_AUDIT` + auditor + binary record | 82.02 | 119.34 |
| **profile cost — guards, not audit** | **17.18** | **23.33** |
| **true audit cost** | **53.00** | **93.88** |
| per audit entry (11.75 per event) | 4.51 | 7.99 |

**Anything that stops the processor dissolving costs native far more than JIT.** Three instances of one
mechanism, measured:

| | native | JIT |
|---|---:|---:|
| processor escapes its loop method | 4.3× | 1.0× |
| dirty filtering on | **11.9×** | 2.5× |
| audit record built per event | +93.9 ns | +53.0 ns |

**The guards skip nothing on this graph** — the auditor shows guards-on and guards-off invoking
identical nodes (13/10/11), because each event reaches its chain by topology. So the 17/23 ns is the
pure cost of guards with zero benefit, and a guard breaks even only when
`P(skip) × cost(node) > ~1.4 ns` (JIT). Light nodes: never. Heavy nodes (~34 ns): at ~4% skip rate.

## Current shippable configuration — core, Java 8, no generation

`LOW_LATENCY_AUDIT` (guards off) + `BinaryEventLogger` + `BinaryLogRecord` with `long[]` slots.
30 nodes, 5 event types, every node on the path logging, 11.75 entries/record, harness h4.

| | JIT | native |
|---|---:|---:|
| baseline, no auditor | 19.72 · 50.7 M/s | 17.77 · 56.3 M/s |
| **audited** | **53.42 · 18.7 M/s** | **66.12 · 15.1 M/s** |
| audit cost | 33.70 | 48.34 |

Verified per run: `recPerEvent=1.000`, 188 bytes/record (11.75 × 16), matching graph checksum, and the
harness version and runtime digest stamped on the result line.

**Measured repeatability**, three batches of six, minimum per batch:

| | spread of minima | CV |
|---|---:|---:|
| native audited | **0.053 ns** | **0.05%** |
| native baseline | 0.106 ns | 0.31% |
| JIT audited | 6.673 ns | 5.49% |

So native figures are quoted to three decimals and **JIT figures are quoted as approximate** — at 5.49%
they are not repeatable to the precision this table would otherwise imply. `measure.sh` enforces 2%
native / 6% JIT and refuses anything looser.

## Known inputs that change the answer — isolate one at a time

Hold every other column in `binaries.tsv` equal; the difference is then attributable.

| Input | Effect | § in round-63 notes |
|---|---|---|
| **harness: processor escapes the loop method** | **4.3× on native, 0 on JIT** | 16 |
| `-H:-SpawnIsolates` | −24 ns audited; ~−0.7 ns baseline | 13.2 |
| record type binary vs text | 3.2× sparse, **5.1× dense** | 12.6 |
| audit density | the variable behind most of the spread | 12.6 |
| node weight | dilutes the record-format gap 4.83× → 1.56× | 14.1 |
| GC epsilon vs serial | 0–14 ns | 9.5 |
| PGO profile SHA | decides which regime a build lands in | round 60 |
| `-march=native` | small regression — do not add it | 11.4 |
| `--initialize-at-build-time` | 9.6 ns worse | 13.2 |
| widened `PriorityForceInline` | 4.4 ns worse | 13.2 |
| build lottery, same config rebuilt | **±8 ns audited · ±0.002 ns dispatch** | 15.2, 16 |

> **The lottery is wider than most single-flag effects on the audited graph.** Any audited difference
> below ~8 ns needs several builds per configuration before it means anything. One build per arm is how
> "method-level inlining works" was nearly published — the control that killed it was a second build of
> the *same* configuration reading 119.1 against 126.9.

> **`LOW_LATENCY_AUDIT` once disabled the audit log entirely.** Before trusting any audited number,
> confirm `recPerEvent > 0` and count `auditor.nodeRegistered` in the generated source.
