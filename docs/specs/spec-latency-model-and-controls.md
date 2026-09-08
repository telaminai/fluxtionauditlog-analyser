# Spec — control binaries, a latency model, and drift detection

**Status:** PROPOSED · **Owner:** analyser (`tools/bench/latency-kit`) · cross-repo evidence
**Evidence:** `docs/experience/runs/round-63/NOTES.md` §§8–16

## 1. Should we build this? — an honest read

**Yes for the controls. Yes but narrowed for the model.**

Round 63 produced **five silent harness faults**, each of which yielded a plausible number and a wrong
conclusion: a system property swallowed because `-D` sat after the main class; two different arms
compared as one; un-interleaved runs on a machine with P and E cores; a build classpath missing the
generated inlining directive; and a processor allowed to escape its loop method, worth **4.3×** on
native. It also shipped a `LOW_LATENCY_AUDIT` profile that silently **disabled the audit log**, and
reported the missing work as a speed-up.

**A control binary would have caught every one of them.** That is the strongest argument for this work
and it is not speculative — it is six for six on faults that actually happened.

The model needs its scope cut, and §4 says why.

## 2. Control binaries

### 2.1 Normative

- The kit MUST ship a small set of **control binaries**, each a fixed graph with a fixed configuration,
  whose expected result is recorded in `RECORDED-BASELINES.md`.
- A control MUST fail loudly when its own result falls outside its recorded band — not print a number
  and leave the comparison to a human.
- Each control MUST carry, in its own output: **harness version, graph id, profile, record type, GC,
  PGO state, target machine, inlining directive, isolate mode**. A result without its inputs is not
  comparable and MUST NOT be recorded.
- The set MUST include, at minimum:

| id | graph | audit | purpose |
|---|---|---|---|
| `c-dispatch` | 30-node converging tail, light nodes | none | the dispatch floor — 2.26 ns native, 12.42 JIT |
| `c-audit-sparse` | same | one node logs, binary record | audit machinery at low density |
| `c-audit-dense` | same | every node logs, binary record | the realistic audited shape |
| `c-audit-text` | same | every node logs, text record | the format comparison |
| `c-heavy` | same, heavy nodes | every node logs, binary | the compute-dominated regime |

### 2.2 What a user does with them

A user runs the controls on their platform **before** measuring their own graph. The controls tell them
whether their machine, JDK and toolchain reproduce the recorded band. If `c-dispatch` reads 9.8 ns
instead of 2.26, their build is not landing the runtime shape and **no other number they take is
comparable to ours** — which is exactly the situation this round was in for five sections.

## 3. Drift detection

### 3.1 Normative

- `index-binaries.sh` MUST record one row per binary with every input that decides the result, plus a
  **tag** identifying the source it was built from (`git describe`) and the harness version.
- A drift check MUST compare two indexed runs **row by row on equal configuration**, and report only
  rows where the configuration matches and the result moved. Comparing rows with different inputs is
  the mistake this whole spec exists to prevent.
- The index MUST include the **profile SHA**. A native result is a function of its PGO profile, and two
  runs sharing every flag but not the profile are not the same configuration.

### 3.2 The tag is what makes single-variable isolation possible

The owner's framing — Gaussian elimination — is exactly right and is why the column set has to be
complete. Hold every column equal but one, and the difference is attributable to that one. Miss a
column and you get round 63: same profile, same flags, same graph, same machine, 3.41 ns against 9.7,
and nothing in the record to explain it, because the harness was an unversioned input.

**Every input found to matter becomes a column.** The current list, each with a measured effect:

| Column | Measured effect | §  |
|---|---|---|
| harness version (processor escapes?) | **4.3× on native, 0 on JIT** | 16 |
| `-H:-SpawnIsolates` | −24 ns audited, −0.7 ns baseline | 13.2 |
| record type (binary/text) | 3.2× sparse, **5.1× dense** | 12.6 |
| audit density | the variable behind most of the spread | 12.6 |
| node weight | dilutes the record-format gap 4.83× → 1.56× | 14.1 |
| GC (epsilon/serial) | 0–14 ns | 9.5 |
| PGO profile SHA | decides which regime a build lands in | round 60 |
| `-march=native` | small regression | 11.4 |
| build lottery (same config, rebuilt) | **±8 ns audited, ±0.002 ns dispatch** | 15.2, 16 |

## 4. The model — narrow it, and say what it cannot do

### 4.1 What is achievable

A **calibrated additive cost model** over named terms, predicting a **band**, not a number:

```
ns/event  ≈  dispatch(nodes, eventTypes)
           +  auditEntries × perEntry(recordType)
           +  recordFixed(recordType)
           +  nodeWork
           ±  buildLottery(regime)
```

Round 63 already measured most of the terms: dispatch 2.26 ns native / 12.42 JIT for 30 light nodes;
per-entry **3.4 ns binary, 26.2 ns text**; node work ~405 ns for a 24-iteration Horner loop; lottery
±8 ns on the audited graph.

### 4.2 What it cannot do, and MUST say so

- **It cannot predict across regimes.** Three findings in this round failed to survive the move from
  the dispatch regime to the audit regime — receiver provability (§9.7), the inlining directive
  (§10.6), and the round-60 flag sweep (§13.2). A model fitted on one regime and applied to another
  will be confidently wrong.
- **It cannot beat the build lottery.** ±8 ns on the audited graph is wider than most single-flag
  effects. **The model MUST emit a band, and the band MUST be at least the lottery width for that
  regime.** A point prediction would be false precision.
- **It cannot model a platform it has not been calibrated on.** Hence §2: the controls are what tell a
  user whether our coefficients apply to their machine at all.

### 4.3 Normative

- The model MUST emit `{low, high}`, never a single number.
- It MUST name which term dominates its prediction, so a user can see what to change.
- It MUST refuse to predict when the requested configuration is outside its calibrated range — an
  extrapolation MUST be reported as such rather than returned as a number.
- Every coefficient MUST cite the run notes section it came from, so a stale coefficient is traceable.

## 5. Sequencing

| # | Step | Depends on |
|---|---|---|
| 1 | harness versioning (`HarnessVersion`, stamped on every RESULT) | — **done** |
| 2 | `index-binaries.sh` with tag + profile SHA | — **done** |
| 3 | `RECORDED-BASELINES.md` — the expected bands | — **done** |
| 4 | re-measure every band with harness h3 (processor local) | 1 |
| 5 | control binaries with pass/fail against the bands | 3, 4 |
| 6 | drift check comparing two indexed runs on equal configuration | 2, 5 |
| 7 | the cost model, emitting bands, coefficients cited | 4 |

**Step 4 is a prerequisite and is not optional**: every band currently in `RECORDED-BASELINES.md` was
measured with harness h1/h2 and the processor escaping. They are the wrong numbers to calibrate against
and are marked as such in that file until re-run.

## 6. Open questions

- **Where do the controls live?** They are most useful shipped with the analyser so a user can validate
  a platform before trusting any figure, but they need the compiler to generate their graphs — the same
  dependency-order problem as `spec-binary-audit-reader.md` §1.
- **How many builds per control?** The lottery is ±8 ns on audited graphs and ±0.002 ns on dispatch. A
  fixed rep count is wrong for both; the count should be derived from the observed spread, which means
  the control has to build several times before it can state a band.
- **Does the band travel between machines?** Nothing here has been run on a second machine, so the
  coefficients are Apple-M4-specific until shown otherwise. This is the assumption most likely to be
  wrong, and §2 is what would expose it.
