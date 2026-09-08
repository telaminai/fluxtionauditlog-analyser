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
| Harness | **h3** — processor constructed inside the loop method, never escaping |

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

## Derived, from the h3 rows

| | JIT | native |
|---|---:|---:|
| dispatch | 12.34 ns | **2.17 ns** — native **5.7× faster** |
| audit cost (dense, binary, 11.75 entries) | **69.2 ns** | **117.2 ns** — native **1.69× slower** |
| per audit entry | ~5.9 ns | ~10.0 ns |

**Native wins the graph and loses the record.** Which one dominates decides the toolchain, and that is
set by audit density and node weight, not by auditing as such.

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
