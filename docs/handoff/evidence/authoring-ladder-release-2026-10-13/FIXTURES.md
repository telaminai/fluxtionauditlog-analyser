# Fixtures, downloads and seeds: authoring ladder on the fully released stack (2026-10-13)

Written before the first run. Every digest below is also in `MANIFEST-RELEASE.sha256`.

## Downloads

All three were fetched at `2026-10-13T20:40:55Z`, after the live playground began serving starter-core 1.0.79. The
poll in `checks/live-poll.log` first saw it at 20:40:32Z, both through the marker script and in the served
bundle's `starterVersion`. The scaffold URLs and tokens are the baseline's (see
`../authoring-ladder-baseline-2026-10-13/FIXTURES.md`).

| Download | Bytes | sha256 |
|---|---|---|
| Rung 2 Spring starter (`scaffold?s=<r2 token>`) | 36916 | `389e55920ca223496e2384761caf2b4b7d062ff62e5066eda8c6376e8957758d` |
| Rung 4 Spring starter (`scaffold?s=<r4 token>`) | 33860 | `8f9da37a124295186f2bf97c0ea3614c08de881e1ecdbb7bb98daa46116dfeb1` |
| Analyser bundle (`scaffold?template=analyser-bundle`) | 114644 | `167c592e312e56e469fcf3317c6426061de4bbdd6cb0a0dd7169e7ca578d508d` |
| Analyser 1.34.0 (GitHub release `v1.34.0`, published 2026-10-13T20:13:32Z) | — | `3f1ce0ced839cae4c88fe5390ba441c2acce29ae3fd8a95388c52b083ff8988f`, which equals `SHA256SUMS.sha256` |

### Versions inside the downloads

| | Spring starter (r2, r4) | Analyser bundle (r3, r5, r6) | Baseline (2026-10-13 morning) |
|---|---|---|---|
| starter-core / BOM | 1.0.79 / 1.0.79 | 1.0.79 / 1.0.79 | 1.0.77 / 1.0.77 |
| runtime | 1.1.1 | 1.1.1 | 1.1.1 |
| Shipped processor stamp | — | analyser 1.0.79, target generator 1.0.79 (annotated) | 1.0.77 |
| Hosted generator, as stamped by today's keyed regenerations | 1.0.79 (r2, r4 ground truth) | 1.0.79 (r3, r5, r6 ground truth) | 1.0.78 |
| Bundle skills | — | `canonical@e78e50a6`; m19-bundle/4; targets analyser ≥ 1.33.0 | `canonical@7057074d` |
| Script output | capped: full output to `logs/<step>.log`, printed summary | capped, the same | uncapped |

## Per-rung fixture

| Rung | Pristine source | Modification (re-applied from the baseline unless new) | Fixture tree sha256 |
|---|---|---|---|
| 2 | Rung 2 Spring starter | The one-token `childTypo` edit to the `pause` SignalBinding. The resulting XML is byte-identical to the baseline's rung-2 fixture XML. | `309cf297ce138dd6a857f1c378e1cb779b81969c7aedae121bfa2f6b192fbc73` |
| 3 | Analyser bundle | None. | `4101b318c22b0725284e0f341b3a2ea735549c3b3e4d4c6ea43f0d45306ce625` |
| 4 | Rung 4 Spring starter | None. | `c953d273fbfb524e15869087a6885db574358588791278986840a1651dbe8a74` |
| 5 | Analyser bundle | `supplier-repo/` and `data/fx.csv`, byte-identical to the baseline's. | `94c1dc8895727f3013754ee63d947953ef81bb6a1a8ddeb1f43df4d3bdd01544` |
| 6 (new) | Analyser bundle | (a) `data/input.txt` gains one row the mapper rejects, `TSLA,n/a,300` (NON_NUMERIC), after GOOG. (b) `CsvRejections.onCsvRejection` returns `true` instead of `false`. See `r6/fixture-modifications.diff`. | `35494e9eb261b938b2d6e8417459f049f6c3171ecb549385f6f3d7437a27a139` |

**Why rung 6 needs (b):** as shipped, the handler returns `false` ("nothing downstream reacts to a rejected row"),
so `CsvRejections` never propagates. A child declared TRIGGER on it would then behave exactly like one declared
DATA, and "not triggered by the DATA parent's updates" could not be observed in the audit log. That was found by
control C2 before sealing: with (a) alone, C2 failed only the XML check. With (b), the DATA declaration is visible
in behaviour, as C2 below shows.

**Tasks and oracles:**
- `TASK-R2…R5`, `R2Oracle.java`, `R4Oracle.java` (corrected r2), `r3_oracle.py` and `r5_oracle.py` are
  byte-identical to the baseline's. `r5_oracle.py` resolves `FIXTURE` and `BASELINE` relative to itself, so it
  uses this harness's `r5/fixture` and the 1.0.79 bundle's as-shipped run, `r5/baseline/`. That run's business
  keys, as `price_business()` extracts them, equal the baseline's for all five PriceEvent records.
- `score_ladder.py` differs from the baseline's only in routing rung 6 to its exported-log oracle, as for rungs 3
  and 5.
- `probe_levers.py` adds three supplementary counts that do not affect PASS: `withdrawCalls`, `subagentCalls` and
  `serviceRegisteredWrites`.

## Rung 6 (new)

**Task (`r6/TASK-R6.txt`):** add `com.example.myapp.node.TurnoverTracker`. It has:
- two trigger parents, RootNode and RiskCheck;
- a DATA-only reference to CsvRejections: it reads the rejected-row count, and a rejected row must not run it;
- a new output `turnover`, written by the server to `data/turnover.txt`;
- all of it declared in the Spring XML design.

The subject then regenerates, runs, exports, charts the notional values and proves the result from the audit log.

None of the names (TurnoverTracker, turnover, CsvRejections as DATA) is in the add-a-node skill's example
(`myNode`, `parentA`, `lookup`, `out`) or in the owner's cycle-5 probe combination (`alertNode`, `riskCheck` as
DATA, `alerts`).

**Oracle (`r6/r6_oracle.py`):** every check is made from evidence, none from prose. PASS = X1 and X2 and X3.

- **X1 (XML-first):**
  - the design XML declares a bean of class `TurnoverTracker` whose constructor refs include rootNode, riskCheck
    and csvRejections;
  - the bean is in `nodeBeans`;
  - a `ReferenceBinding` makes csvRejections `DATA`, and none makes rootNode or riskCheck `DATA`;
  - a `SinkBinding` named `turnover` lists the node.
- **X2 (dispatch, from `logs/*.yaml`):**
  - in the last record of each of the five accepted symbols, the node's entry comes after both rootNode's and
    riskCheck's;
  - every `CsvRejection` record contains csvRejections and not the node.
- **X3 (output):** `data/turnover.txt`'s line count is a multiple of 5, because the sink appends one block per
  run. Its last five lines are exactly the expected records, in order: symbol, notional within 0.01, and
  rejected-so-far:
  - AAPL 234360 0
  - MSFT 328080 0
  - GOOG 233625 0
  - AMZN 114060.8 1
  - NVDA 1901760 1

## Ground truth and controls (all on the 1.0.79 fixtures, before sealing)

The ground truths are the baseline's, ported (`groundtruth/gt-r<n>.solution.diff`), with authoring records
re-versioned to 1.0.79. The r6 ground truth was authored XML-first:
1. the bean, nodeBeans, three ReferenceBindings (two TRIGGER and one DATA) and a SinkBinding, plus a
   `server-config.yml` `turnover` FileMessageSink;
2. `./generate.sh` wrote the class shell (the constructor, a `@NoTriggerReference` field for the DATA parent, the
   `sinkTurnover` SinkPublisher and an `@OnTrigger` method);
3. only the method body was filled in.

Every generation used plain `./generate.sh`, and every one stamped generator 1.0.79.

| Rung | Ground truth (VALID, ORACLE) | Controls: each FAILs ORACLE |
|---|---|---|
| 2 | PASS, PASS | The unsolved fixture fails VALID and ORACLE. The typo-only repair fails ORACLE at step 1. |
| 3 | PASS, PASS | The as-shipped bundle fails F1 and F2. A limit of 3,000,000 fails F1 and F2. |
| 4 | PASS, PASS | The unsolved fixture fails at step 1. No eviction fails at output 4. |
| 5 | PASS, PASS (plain generate) | The unintegrated fixture fails I1. An added Java file fails I3. |
| 6 | PASS, PASS (X1, X2, X3) | **C0**, the unsolved fixture: X1, X2 and X3 all fail. **C1**, the sink hand-wired outside the XML: no SinkBinding, and an `@ServiceRegistered MessageSink` callback in TurnoverTracker, which needed `./generate.sh --no-reconcile` because plain generate refused to drop the owned `sinkTurnover` field with SPRING_RECONCILE_CONFLICT. Its runtime output is correct (X2 and X3 pass), and **X1 fails** on `sinkBinding`. **C2**, the DATA reference declared TRIGGER: X1 fails on `dataRef`; **X2 fails**, because the node runs in the CsvRejection record; and **X3 fails**, because a sixth line, `GOOG,233625.0,1`, is published on the rejection. |

The verdicts are in `groundtruth/`, and the C1 and C2 edits are in `groundtruth/ctl-r6c*.vs-gt.diff`.

## Warm caches (cold-filled today, then frozen)

Each cache was filled into an empty `maven.repo.local` with an empty user `settings.xml`, so only Central and public
Repsy were used, never `~/.m2`.

| Seed | Filled by | Tree sha256 | Provenance |
|---|---|---|---|
| `seed-spring` | the r2 and r4 ground truths (setup, validate, generate, run.sh, oracle build) and their controls; started 20:43:18Z | `b3f23c40aeb9e04662661b00f8f9c54827c91fec7fc0cbeded8903703b41c3de` | 506 files from central and 10 from repsy-fluxtion-public; clean |
| `seed-bundle` | the as-shipped bundle run (r5 baseline), the r3 and r6 ground truths and the r3, r5 and r6 controls; started 20:41:51Z | `a8ffd4903f591ca73b25430881c8c3846a4a8f0f7516fcd4bb1354286ae44e9c` | 641 files from central and 16 from repsy-fluxtion-public; clean |

- **Frozen** read-only at 2026-10-13T20:49:17Z.
- **Excluded from the bundle seed:** the r5 ground truth ran in a separate copy, so the seed holds no `com/acme`.
- **Also in the bundle seed:** `fluxtion-bom` 1.0.67, pulled in transitively. It came from a remote and has
  provenance recorded.
- **Offline sufficiency** (`checks/sufficiency.txt`): `./setup.sh -o` succeeds on all five fixtures from their
  seed, and r2's validate fails, as intended, on the typo.
