# M72 L4 (iii) acceptance A/B: results (2026-10-14)

## Decision: **NO EFFECT SHOWN**

This is the spec's decision rule (`docs/specs/spec-authoring-token-lowering.md`, *L4 (iii) — as designed*,
Acceptance), applied literally by `harness/decide.py` to the committed `results/`. The full output is in
`DECISION.md`.
- **Not REJECT:** PASS, ORACLE and CHART are 3/3 in both arms on every rung, so the treatment never falls below the
  control.
- **Not KEEP:** the cost criteria pass on rung 4 only. KEEP needs both bundle rungs (3 and 5) and at least 3 of the
  4 rungs.
- **Rule 3 applies:** the section is to be deleted (P2: a measurement retires it).
- **But KEEP was impossible on this control data: the bundle half had no power.** Criterion (a) needs the
  treatment's median diagnosis cost to sit below the control's by more than the control's min–max spread. On both
  bundle rungs the control's spread exceeds its own median:
  - rung 3: spread $0.0191, median $0.0147;
  - rung 5: spread $0.0237, median $0.0194.

  Even a treatment diagnosis cost of zero would fail (a) on both, and KEEP requires (a) on both. The deletion
  follows from the rule as sealed, not from evidence that the section is harmful or useless on the bundle rungs.
  (Added 2026-10-14 after review of PR #141; see `POST-SEAL-NOTES.md`.)
- **UP-PG-08's starter half does not proceed** (rule 4): rung 4 passes, rung 2 does not.

The runs were sealed before the first run at `3ca0bed4` (2026-10-14T00:25:18Z, pushed), ran from 00:25Z to 02:08Z,
and cost **$2.93** billed for 24 scored runs. The VOID cell launched no subject and cost nothing. The $8 cap was
never approached.

## Per rung (medians; the treatment arm is listed first)

| rung | n T/C | diag cost $ median T / C (C spread) | (a) | diag >100k median T / C | (b) | total $ median T / C (C spread) | (c) | PASS T/C | ORACLE T/C | CHART T/C | (d) | rung passes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2 | 3/3 | 0.0688 / 0.1266 (0.0616) | False | 7 / 13 | False | 0.2098 / 0.1819 (0.0377) | False | 3/3 | 3/3 | 3/3 | True | False |
| 3 | 3/3 | 0.0171 / 0.0147 (0.0191) | False | 0 / 0 | True | 0.0573 / 0.0513 (0.0301) | False | 3/3 | 3/3 | 3/3 | True | False |
| 4 | 3/3 | 0.0343 / 0.0678 (0.0227) | True | 2 / 8 | True | 0.1036 / 0.2124 (0.0880) | True | 3/3 | 3/3 | 3/3 | True | True |
| 5 | 3/3 | 0.0177 / 0.0194 (0.0237) | False | 0 / 0 | True | 0.0530 / 0.0557 (0.0282) | True | 3/3 | 3/3 | 3/3 | True | False |

**What failed on each rung:**
- **Rung 2:** (a) failed: the treatment's diagnosis cost is lower ($0.069 against $0.127), but by $0.058, under the
  control's spread of $0.062. (b) failed: 7 over-100k requests against 13 is more than half. (c) failed: the
  treatment's median total cost is higher ($0.210 against $0.182).
- **Rung 3:** (a) failed, and could not have passed: the treatment's median diagnosis cost is $0.017 against the
  control's $0.015, and the control's spread ($0.019) exceeds its median. (c) failed: the treatment's total is $0.057
  against $0.051.
- **Rung 4:** passes all four criteria: diagnosis $0.034 against $0.068 (spread $0.023), over-100k 2 against 8,
  and total $0.104 against $0.212.
- **Rung 5:** (a) failed, and could not have passed: $0.018 against $0.019, with a control spread of $0.024, above
  its median. (b) and (c) passed.

## Per run

The columns come from `DECISION.md`. Delegation facts come from `results/<run>/delegation.json` (`harness/early_t.py`)
and the token_split `phases.json` `delegation` block.

| run | arm | PASS | ORACLE | CHART | total $ | diag $ | diag >100k | delegations | backgrounded | main requests while subagent ran | subagent $ | subagent first prompt | brief chars | reply bytes | warnings | recomputed/billed |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ab-r2-c-1 | c | True | True | True | 0.1815 | 0.1348 | 16 | 0 | 0 | 0 | - | - | - | - | - | 0.9968 |
| ab-r2-c-2 | c | True | True | True | 0.1819 | 0.1266 | 13 | 0 | 0 | 0 | - | - | - | - | - | 1.0005 |
| ab-r2-c-3 | c | True | True | True | 0.2192 | 0.0732 | 7 | 0 | 0 | 0 | - | - | - | - | - | 0.9989 |
| ab-r2-t-1 | t | True | True | True | 0.2098 | 0.1535 | 14 | 1 | 0 | 0 | 0.00556731 | 13671 | 2094 | 3998 | - | 0.9982 |
| ab-r2-t-2 | t | True | True | True | 0.3332 | 0.0688 | 6 | 2 | 0 | 0 | 0.00838971 | 13796,13835 | 4776 | 6322 | - | 0.9925 |
| ab-r2-t-3 | t | True | True | True | 0.1068 | 0.0537 | 7 | 0 | 0 | 0 | - | - | - | - | - | 0.9972 |
| ab-r3-c-1 | c | True | True | True | 0.0513 | 0.0122 | 0 | 0 | 0 | 0 | - | - | - | - | - | 1.0 |
| ab-r3-c-2 | c | True | True | True | 0.0462 | 0.0147 | 0 | 0 | 0 | 0 | - | - | - | - | - | 1.0 |
| ab-r3-c-3 | c | True | True | True | 0.0763 | 0.0313 | 3 | 0 | 0 | 0 | - | - | - | - | - | 0.9939 |
| ab-r3-t-1 | t | True | True | True | 0.0480 | 0.0120 | 0 | 0 | 0 | 0 | - | - | - | - | - | 1.0 |
| ab-r3-t-2 | t | True | True | True | 0.0573 | 0.0171 | 0 | 1 | 0 | 0 | 0.00889385 | 16693 | 2596 | 2762 | - | 1.001 |
| ab-r3-t-3 | t | True | True | True | 0.1134 | 0.0577 | 6 | 0 | 0 | 0 | - | - | - | - | - | 0.9961 |
| ab-r4-c-1 | c | True | True | True | 0.2124 | 0.0678 | 8 | 0 | 0 | 0 | - | - | - | - | - | 0.9933 |
| ab-r4-c-2 | c | True | True | True | 0.1253 | 0.0804 | 10 | 0 | 0 | 0 | - | - | - | - | - | 0.9855 |
| ab-r4-c-3 | c | True | True | True | 0.2133 | 0.0578 | 6 | 0 | 0 | 0 | - | - | - | - | - | 1.0035 |
| ab-r4-t-1 | t | True | True | True | 0.2418 | 0.0449 | 2 | 2 | 0 | 0 | 0.02310087 | 13665,13748 | 4052 | 8220 | - | 0.9849 |
| ab-r4-t-2 | t | True | True | True | 0.0606 | 0.0098 | 0 | 0 | 0 | 0 | - | - | - | - | - | 1.0 |
| ab-r4-t-3 | t | True | True | True | 0.1036 | 0.0343 | 4 | 0 | 0 | 0 | - | - | - | - | - | 0.9872 |
| ab-r5-c-1 | c | True | True | True | 0.0491 | 0.0194 | 0 | 0 | 0 | 0 | - | - | - | - | - | 1.0 |
| ab-r5-c-2 | c | True | True | True | 0.0773 | 0.0396 | 3 | 0 | 0 | 0 | - | - | - | - | - | 1.0064 |
| ab-r5-c-3 | c | True | True | True | 0.0557 | 0.0159 | 0 | 0 | 0 | 0 | - | - | - | - | - | 1.0 |
| ab-r5-t-1 | t | True | True | True | 0.0530 | 0.0213 | 0 | 1 | 0 | 0 | 0.01163839 | 16473 | 1905 | 3061 | - | 1.0006 |
| ab-r5-t-2 | t | True | True | True | 0.0454 | 0.0149 | 0 | 0 | 0 | 0 | - | - | - | - | - | 1.0 |
| ab-r5-t-3 | t | True | True | True | 0.0630 | 0.0177 | 0 | 1 | 0 | 0 | 0.01043074 | 16571 | 2194 | 2710 | - | 1.0006 |


## Did the treatment do what the section says?

**Delegation.**
- **6 of 12 treatment runs delegated**, 8 delegations in all:
  - bundle rungs: r3 1 of 3, r5 2 of 3;
  - starter rungs: r2 2 of 3, r4 1 of 3.
- **No control run delegated** (0 of 12), so the section is what brings delegation about.
- The subagent tool was offered in all 24 runs (`subagentOffered`).

**Form of each delegation.**
- Every delegation was `subagent_type: "general-purpose"` with `run_in_background: false`, and token_split records
  `backgrounded` 0. **All 8 were foreground.**
- **The author waited every time:** 0 main-context requests and 0 analyser calls while a subagent ran.
- **Brief size:** 4 of the 8 briefs exceed the section's 2,000-character bound (2,036, 2,125, 2,300 and 2,419
  prompt characters). All carry the brief's fields; ab-r2-t-1's lacks `logs:`, which suits a Spring rung with no
  export file.
- **Subagent first prompts:** 13.7–13.8k tokens on the starter rungs and 16.5–16.7k on the bundle rungs. The section
  assumed a base of about 22k.
- **Subagent cost:** $0.006–0.023 per run, and no subagent request went over 100k.

**The replies.**
- All 8 replies cite records.
- 3 replies (ab-r2-t-2 ×2, ab-r3-t-2) name all six qualification keys, or say "not stated" for them.
- The other 5 name only the pairing, sometimes with `producer` or `dispatchOrder`.

**Main-context analyser calls around the delegations**, per run. These are distinct calls in event order (corrected
2026-10-14: per-delegation sums double-counted the runs that delegated twice).

| run | delegations | analyser calls before the first delegation | after the first reply | after the last reply |
|---|---|---|---|---|
| ab-r2-t-1 | 1 | 5 | 7 | 7 |
| ab-r2-t-2 | 2 | 12 | 4 | 3 |
| ab-r3-t-2 | 1 | 0 | 1 | 1 |
| ab-r4-t-1 | 2 | 0 | 0 | 0 |
| ab-r5-t-1 | 1 | 2 | 1 | 1 |
| ab-r5-t-3 | 1 | 0 | 0 | 0 |

- **ab-r2-t-1.** After its reply the author made 7 calls: `open`, `graph`, 3 × `flag`, `spotlight` and
  `screenshot`. These are the canvas artefacts the brief's `question` was meant to hand to the subagent. The run
  had 14 over-100k diagnosis requests.
- **ab-r2-t-2.** It had already diagnosed in the main context before delegating: 12 analyser calls, counted
  distinct by call id (the review counted 11). After its first reply it made 4 more, and it had 6 over-100k
  diagnosis requests.
- **The other 4 delegating runs** made 0–1 analyser calls after their replies.

**No evidence that delegation lowered cost, and the comparison is confounded by which runs chose to delegate
(self-selection).**
- The treatment runs that delegated had a higher median diagnosis cost and total cost than those that did not:
  $0.033 and $0.136, against $0.025 and $0.082. The rung mix is balanced, with 3 delegating runs on each side of
  the starter/bundle split.
- **Self-selection.** The two most expensive delegators are fix-and-rerun loops: each edited and regenerated between
  its two delegations.
  - ab-r2-t-2, $0.333: 2 edits and 1 generate.
  - ab-r4-t-1, $0.242: 8 edits and 3 generates.

  A run that meets a defect is both more expensive and more likely to delegate twice, so the comparison cannot
  isolate what delegation itself costs.
- **Rung 4.** Its pass comes mostly from its two non-delegating runs: ab-r4-t-2 cost $0.061 and ab-r4-t-3 $0.104.
- **At n = 3** the arm-level differences sit within run-to-run noise. The rule judges the arm as assigned, as the
  spec requires.

**Observation, not a claim: the control's bundle rungs left little headroom for this lever.**

This control's bundle-rung costs are far below the release ladder's (`../authoring-ladder-release-2026-10-13/`).
Between the two, the seeds, CLI, analyser jar, tasks, oracles and scorer were held fixed. The bundle scripts and
CLAUDE.md changed (fluxtion-web #17).

| Rung 3 / rung 5, median | Release ladder (2026-10-13) | This control (2026-10-14) |
|---|---|---|
| Total cost | $0.167 / $0.077 | $0.051 / $0.056 |
| Diagnosis cost | $0.091 / $0.038 | $0.015 / $0.019 |
| Diagnosis over-100k requests, per run | 6, 9, 10 / 18, 0, 3 | 0, 0, 3 / 0, 3, 0 |

This is consistent with #17 having removed much of the diagnosis cost the lever targeted on the bundle rungs.
Confounds:
- a different session window;
- n = 3;
- the other bundle changes (scripts and CLAUDE.md, not #17 alone);
- the release ladder's own rung-3 un-injected-service failures and its rung-5 run (rel-r5-h55-1) that failed CHART
  after a `withdraw` and `--no-reconcile` loop, which inflated the release medians.

## Arm totals (from the committed tables)

| | Control | Treatment |
|---|---|---|
| Billed total / median per run | $1.489 / $0.101 | $1.436 / $0.083 |
| Diagnosis-phase cost, median | $0.049 | $0.028 |
| Diagnosis over-100k requests, total | 66 | 39 |
| All over-100k requests, total | 101 | 74 |
| Median turns / wall | 48.5 / 208 s | 45 / 210 s |
| CLI side calls, outside the phases (token_split) | $0.019 | $0.050 |
| PASS | 12/12 | 12/12 |

The full split is in `TOKEN-SPLIT.md`.

## Sealed predictions against outcomes (PREDICTIONS-AB.md)

| # | Prediction | Outcome |
|---|---|---|
| 1 | ≥ 1/6 bundle treatment runs delegate; ≤ 2/6 starter treatment runs; ≤ 1/12 control runs | holds; **wrong** (3/6); holds (0/12). Bundle is 3/6. |
| 2 | Foreground in ≥ half of delegating runs; every brief ≤ 2,000 chars | holds (8/8 foreground); **wrong** (4 of 8 exceed) |
| 3 | ≥ half of replies name a qualification key or "not stated" | holds (8/8 name at least the pairing; 3/8 name all) |
| 4 | NO EFFECT SHOWN, with (a) failing on ≥ 2 rungs | holds ((a) fails on 3 rungs) |
| 5 | No REJECT | holds |
| 6 | PASS ≥ 10/12 in each arm | holds (12/12 and 12/12) |
| 7 | Total ≤ $5.00; no cell at the cap | holds ($2.93; none capped) |
| 8 | subagentOffered 24/24 | holds |
| 9 | S6′ 24/24 | holds |

## Disclosures

- **VOID: ab-r5-c-3, first attempt.** The disk filled (`No space left on device`) while the runner was copying the
  CLI into the run base. The runner exited with 1, no subject was launched, and nothing was billed.
  - It is kept as `results/ab-r5-c-3-VOID-enospc/`.
  - The re-run happened in the same slot at 02:04Z, after freeing space: I removed the byte-identical read-only CLI
    copies from 50 `cli/` directories of my own finished run bases, each checked against digest `c9b53416…` before
    removal. The VOID cell's own partial copy (44 MB, truncated by the full disk) failed the digest check and was
    left in place. No evidence was removed.
  - All 23 earlier cells were checked intact afterwards: their tables parse, and every transcript ends in its result
    event.
  - **Order:** ab-r5-c-3 is the last cell in the sealed order, so its re-run took its own slot, and the interleaved
    order is intact. (Corrected 2026-10-14: an earlier version said it was out of order.)
- **Key scan.** ab-r5-c-3's re-run reads "not clean": 0 key values, 0 token occurrences, and 4 key-*path* pattern
  hits from one `test -f ~/.fluxtion/fluxtion.apiKeyFile`. `~` was the isolated home, with no `.fluxtion` in it.
  This is a path-reference false positive, and S6′ holds.
- **The treatment patches the vendored skill rather than replacing it** (FIXTURES.md). This deviation was agreed
  before sealing by the **coordinating session** that commissioned the A/B, not by the owner; see
  `POST-SEAL-NOTES.md`.
- **Post-seal, reporting only:** `harness/early_t.py`'s "waited" measure first counted analyser calls up to the end
  of the run, not up to the reply. I fixed it to count by event position before any result was reported other than
  the early message. `harness/collect_ab.py` was written after the runs. The scorer, oracles, token_split and
  `decide.py` are as sealed.
- **Small samples.** n = 3 per arm per rung, and the per-rung spreads are wide (see FIXTURES and PREDICTIONS).
- **Prestaged G14 rig, never an acceptance.** `acceptanceEligible` is false in every meta.json.
