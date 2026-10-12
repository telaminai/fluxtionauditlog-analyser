# Authoring ladder: predictions, sealed before launch (2026-10-12)

The G14 rig with prestaged fixtures; never an acceptance. Harness: analyser commit `2eb87ca9b1f65c4841bce5d89566a71587697560`, with `--claude-bin`
Claude Code 2.1.296, released analyser 1.32.0 per run, and a 2400 s cap (the frozen task's 40 minutes). Published
guidance only: the fixtures are today's public downloads, with no PR changes. Per rung: claude-haiku-5-5 x3 and
claude-sonnet-5 x1, in the order h55-1, s5-1, h55-2, h55-3. Rungs run in order 2, 3, 4.

## Rungs (fixtures fetched from the public scaffold endpoint today)

- **Rung 2:** the frozen 19 September business task, unchanged.
  - The fixture is `scaffold?s=<token of the frozen request-spec>`, whose design XML is byte-identical to the
    1.0.74 fixture's, plus the same injected `childTypo` defect.
  - The task is the frozen USER TASK, with three disclosed edits: no "inspect the generated processor", no report
    or transcript requirement, and the analyser named as an MCP server.
  - Traps: the typo (SPRING_SIGNAL_UNKNOWN_NODE); a handler declared `propagate=false` and a DATA-mode reference,
    so a price cannot reach Child as shipped; an empty `Checked` record; the pause signal; an exported reset that
    must not publish.
  - Oracle `R2Oracle.java`: sink outputs exactly {10,1} then {40,1} across the 7 steps.
- **Rung 3:** the analyser bundle with a server-provided TradeLimits service at 300000, plus AlertCounter running
  only for large trades.
  - Traps: declaring the service type, naming the binding to match the server's service, the yml service entry,
    and conditional propagation.
  - Oracle `r3_oracle.py` on the exported log: MSFT and NVDA large and the other three not; the counter appears
    only on those two records, with counts 1 and 2.
- **Rung 4:** greenfield on a minimal starter: a per-symbol 3-trade VWAP, a `vwap` sink, rejection of qty <= 0, and
  a `clear` signal.
  - Traps: a new output type, sink and signal bindings, `TradeIntake` exposing the trade, a return value that
    stops a rejected trade, a clear that must not publish, and node-local state (FLX-1009 unless `transient`).
  - Oracle `R4Oracle.java`: (A,10) (A,15) (B,50) (A,22.5) (A,30) (A,60) (B,70).

**Ground truth and controls:** every rung was built by the operator on today's toolchain with the key, and its
oracle passes. Wrong-result controls fail as intended:
- rung 2: the unsolved fixture fails at step 1, and a reset that publishes fails at step 5;
- rung 3: no new nodes, a service not delivered, and a counter that is not conditional each fail;
- rung 4: no eviction fails at output 4, and the unsolved fixture fails at step 1.

**PASS** (`score_ladder.py`) = ORACLE and VALID (`validate.sh` exits 0 on the final design) and CHART (a saved graph
with points and no identity mark) and S6′ (no key or token value in the outputs).

## Predictions

1. Rung 2: Haiku 5.5 PASSes at most 1 of 3, and Sonnet 5 PASSes 1 of 1.
2. Rung 2: every run that reaches validation repairs the typo (VALID on the final design).
3. Rung 3: Haiku 5.5 PASSes at most 1 of 3, and Sonnet 5 PASSes 1 of 1.
4. Rung 3: the most common Haiku 5.5 failure is F1 (the limit never arrives or flags are wrong), not F2.
5. Rung 4: Haiku 5.5 PASSes at most 1 of 3, and Sonnet 5 PASSes 1 of 1.
6. Haiku 5.5's pass rate falls from rung 1 (3/3 in the G14 grid) to at most 1/3 on every rung here.
7. Median Haiku 5.5 cost per run is at most $0.40 on each rung, and Sonnet 5's per-run cost is at least 10x
   Haiku 5.5's median on the same rung.
8. At least one run on some rung reaches the 2400 s cap.
9. S6′ holds in 12 of 12.
