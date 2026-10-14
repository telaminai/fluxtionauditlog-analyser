# M72 L4 (iii) acceptance A/B: design and predictions, sealed before the first run (2026-10-14)

This is the acceptance in `docs/specs/spec-authoring-token-lowering.md` ▸ *L4 (iii) — as designed* (analyser `main`
`e3786f68`), run as the spec states. The spec governs, and `FIXTURES.md` records the one disclosed deviation (the
treatment patches the vendored skill rather than replacing it).

## Design

- **Arms and order.** Haiku 5.5, rungs 2–5, n = 3 per arm per rung: 24 runs. The arms alternate run by run in one
  window, and the first arm alternates by rung (r2 C first, r3 T first, r4 C first, r5 T first), so the order is
  r2-c-1, r2-t-1, r2-c-2, … (`harness/ladder_ab.py`).
- **Same in both arms:**
  - the runner, `tools/g14_runner.py` `a9511a4f…`;
  - the read-only Claude Code 2.1.296 binary (`c9b53416…`);
  - analyser 1.34.0 (`3f1ce0ce…`), as the operator `--rest` instance and the subject's MCP bridge;
  - the frozen seeds;
  - the cap, 2400 s;
  - the **default subagent grant**: no `--no-subagent`, and meta.json records `subagentOffered`.
- **Control** = today's released fixtures, which include fluxtion-web #17. **Treatment** = the same plus the
  fresh-context section (FIXTURES.md).
- **Scoring:**
  - PASS = ORACLE + VALID + CHART + S6′ (`score_ladder.py` and the four oracles);
  - `measure_reads.py` and `content_reads.py` are reported but not judged;
  - `tools/token_split.py split` per run, then `summary`.
- **Metrics** (spec), all from the token_split tables:
  - **diagnosis-phase cost**: every request in L0's diagnosis phase, in every context, so the subagent's are
    included;
  - **diagnosis-phase requests over 100k**;
  - **total run cost**, as billed, which counts every subagent;
  - reported but not judged: delegations, briefs, and per-context first prompts.
- **Decision rule** (spec, applied literally by `harness/decide.py`).
  - **A rung passes** when all four hold:
    - (a) the treatment's median diagnosis cost is below the control's by more than the control's min–max spread;
    - (b) the treatment's median diagnosis over-100k count is at most half the control's;
    - (c) the treatment's median total cost is not above the control's;
    - (d) the treatment's PASS, ORACLE and CHART are not below the control's.
  - **REJECT** if (d) fails on any rung.
  - **KEEP** if (a), (b) and (c) all hold on both bundle rungs (3 and 5) and on at least 3 of the 4 rungs, and no
    rung's median total exceeds the control's by more than the control's spread.
  - **NO EFFECT SHOWN** otherwise. The section is then deleted.
  - **UP-PG-08's starter half** proceeds only if rungs 2 and 4 both pass.
- **Reporting rules:**
  - each run is scored as assigned, whether or not it delegated;
  - a run with `delegation.warnings` is excluded from the cost metrics only;
  - out-of-sample check: recomputed cost within 2% of billed.
- **VOID:** an external failure is VOID and re-run in the same slot, and kept.
- **Spend cap:** the driver stops before any cell once $8.00 has been billed.

## Reference: the release ladder's Haiku runs on the same stack before #17

These are not a control; they show only what the control's numbers usually look like.

| Rung | Diagnosis cost: median (spread) | Diagnosis >100k | Total cost: median (spread) |
|---|---|---|---|
| r2 | $0.091 ($0.034) | 9, 12, 9 | $0.150 ($0.127) |
| r3 | $0.091 ($0.035) | 6, 9, 10 | $0.167 ($0.093) |
| r4 | $0.064 ($0.044) | 7, 9, 4 | $0.260 ($0.168) |
| r5 | $0.038 ($0.128) | 18, 0, 3 | $0.077 ($0.150) |

With spreads this wide, criterion (a) needs large per-rung effects at n = 3.

## Predictions

1. **Delegation happens, but not often.**
   - At least 1 of the 6 bundle treatment runs (r3, r5) delegates.
   - At most 2 of the 6 starter treatment runs (r2, r4) delegate.
   - At most 1 of the 12 control runs delegates.
2. **A delegating run follows the brief's form:**
   - foreground (`backgrounded` = 0) in at least half of the delegating runs;
   - a brief of at most 2,000 characters in every delegation.
3. **A reply carries qualifications:** at least half of the replies name at least one of `log.identity`,
   `graphPairing`, `timeOrder`, `producer`, `inFlight` or `dispatchOrder`, or say "not stated".
4. **Decision:** **NO EFFECT SHOWN**. Criterion (a) fails on at least 2 rungs, because of n = 3, the spread, and
   delegation being rare.
5. **No REJECT:** PASS, ORACLE and CHART are not lower in the treatment on any rung.
6. **Quality:** PASS is at least 10/12 in each arm.
7. **Total spend** is at most $5.00 over the 24 runs, and no cell reaches the cap.
8. **Subagents offered:** `subagentOffered` is true in 24 of 24.
9. **S6′** holds in 24 of 24.
