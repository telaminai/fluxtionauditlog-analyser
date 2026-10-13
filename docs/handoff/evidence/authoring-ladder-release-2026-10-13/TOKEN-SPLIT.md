# Token split: release ladder (2026-10-13)

The per-run tables in `results/<run>/` are `buckets.json`, `categories.json` and `phases.json`. They come from analyser
main's `tools/token_split.py split` (`fc867643…`, at the v1.34.0 tag) on each run's transcript. The transcripts are
not committed. The summary below is `python3 tools/token_split.py summary results`, reproduced as printed. Every
run's recomputed cost is within 1% of billed (0.991–1.005), inside L0's 2% out-of-sample check.

| run | toolResultBytes = score.json | cost recomputed / billed | >100k requests | >100k cost share |
|---|---|---|---|---|
| rel-r2-h55-1 | 92007 = 92007 yes | 0.998 | 10/35 | 0.6402 |
| rel-r2-h55-2 | 104887 = 104887 yes | 1.0014 | 12/39 | 0.7107 |
| rel-r2-h55-3 | 131694 = 131694 yes | 0.9911 | 20/45 | 0.8162 |
| rel-r3-h55-1 | 99120 = 99120 yes | 0.9991 | 6/31 | 0.5064 |
| rel-r3-h55-2 | 159877 = 159877 yes | 0.9949 | 14/39 | 0.7436 |
| rel-r3-h55-3 | 122139 = 122139 yes | 0.9963 | 11/54 | 0.5981 |
| rel-r4-h55-1 | 118420 = 118420 yes | 0.9908 | 17/41 | 0.7599 |
| rel-r4-h55-2 | 129837 = 129837 yes | 0.9977 | 35/59 | 0.8644 |
| rel-r4-h55-3 | 122203 = 122203 yes | 0.9972 | 22/49 | 0.8201 |
| rel-r5-h55-1 | 115116 = 115116 yes | 0.9996 | 18/50 | 0.7544 |
| rel-r5-h55-2 | 100203 = 100203 yes | 1.0 | 0/29 | 0.0 |
| rel-r5-h55-3 | 92378 = 92378 yes | 1.0054 | 3/36 | 0.3568 |
| rel-r6-h55-1 | 85923 = 85923 yes | 1.0 | 0/30 | 0.0 |
| rel-r6-h55-2 | 70990 = 70990 yes | 1.0 | 0/25 | 0.0 |
| rel-r6-h55-3 | 105682 = 105682 yes | 1.0003 | 7/36 | 0.5239 |

**claude-haiku-5-5, 15 runs — tool-result bytes by kind** (share of 1,650,476 bytes; share = bytes assigned by kind priority; single-kind = calls that read only that kind, the lower bound; upper = every call that names the kind)

| kind | bytes | share | single-kind share (lower) | upper | calls | carried share | tier-weighted carried share |
|---|---|---|---|---|---|---|---|
| auditLog | 138,724 | 8.4% | 4.4% | 8.4% | 67 | 4.3% | 5.5% |
| generated | 30,093 | 1.8% | 0.0% | 1.9% | 8 | 2.4% | 2.2% |
| server | 71,505 | 4.3% | 1.5% | 6.9% | 39 | 3.0% | 3.4% |
| build | 208,398 | 12.6% | 6.0% | 15.1% | 103 | 12.8% | 15.0% |
| design | 287,556 | 17.4% | 0.4% | 19.7% | 31 | 23.0% | 20.8% |
| library | 137,160 | 8.3% | 4.1% | 12.9% | 40 | 10.4% | 12.2% |
| docs | 322,833 | 19.6% | 5.8% | 33.9% | 49 | 22.9% | 18.4% |
| source | 232,063 | 14.1% | 12.1% | 48.1% | 55 | 16.4% | 15.1% |
| analyser | 179,959 | 10.9% | 10.9% | 10.9% | 207 | 2.9% | 4.9% |
| other | 42,185 | 2.6% | 2.6% | 23.2% | 187 | 2.0% | 2.6% |
| unclassified | 0 | 0.0% | 0.0% | 0.0% | 0 | 0.0% | 0.0% |

Raw audit-log text (auditLog without auditCompare): 138,167 bytes, 8.4% of tool bytes; per run median 8.6%, max 15.9%. Build/server results that also carry audit-record keys: 54,994 bytes, 3.3% (whole calls, an upper bound on echoed audit text).

Cost: billed $2.2999, recomputed $2.2929 (0.9970); requests over 100k $1.5830 = **69.0%** of recomputed cost (input side alone $1.2942 = 56.4%); at or under 100k $0.6882 = 30.0%; CLI side calls $0.0217 = 0.9%.

| phase | cost $ | cost share | requests | over 100k | tool calls | analyser calls | tool bytes | bytes share |
|---|---|---|---|---|---|---|---|---|
| design | 0.9820 | 42.8% | 376 | 47 | 504 | 30 | 1,326,013 | 80.3% |
| run | 0.3015 | 13.1% | 77 | 25 | 104 | 17 | 181,182 | 11.0% |
| diagnosis | 0.9877 | 43.1% | 145 | 103 | 178 | 160 | 143,281 | 8.7% |
| (CLI side calls) | 0.0217 | 0.9% | | | | | | |


## What fills the context past 100k (part A)

**Post-hoc analysis**, added after the runs at the owner's request. It does not affect any score. The tables are
rendered by `harness/past100k_md.py` from:
- `part-a/past100k.jsonl` (`harness/past100k.py`);
- `part-a/service-failures.jsonl` (`harness/service_failures.py`);
- the committed `results/<run>/*.json`.

The per-call rows (request index and phase of each named read) are in `part-a/past100k.jsonl`. Every estimate
marked INFERRED is a model of what a run *would* have cost, not a measurement.

### Where each run first crosses 100k

Prompt = input + cache-read + cache-write tokens of one request. Carried bytes = tool-result bytes returned before the crossing request, so inside its prompt, by L0 kind (kB).

| run | requests | >100k | base prompt | first >100k request | its phase | its prompt | carried kB total | design | docs | build | analyser | source | library | auditLog | generated | server | other |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| rel-r2-h55-1 | 35 | 10 | 20451 | 25 | run | 100051 | 85.6 | 36.0 | 12.5 | 13.4 | 3.5 | 0.0 | 12.0 | 5.1 | 0.2 | 0.6 | 2.4 |
| rel-r2-h55-2 | 39 | 12 | 20451 | 27 | diagnosis | 100048 | 92.2 | 23.0 | 8.2 | 12.2 | 0.0 | 28.2 | 7.6 | 9.0 | 0.0 | 0.1 | 4.0 |
| rel-r2-h55-3 | 45 | 20 | 20451 | 25 | design | 112682 | 108.0 | 25.4 | 12.8 | 11.4 | 0.0 | 29.0 | 18.1 | 4.5 | 0.0 | 3.6 | 3.1 |
| rel-r3-h55-1 | 31 | 6 | 23115 | 25 | diagnosis | 103003 | 97.2 | 11.6 | 40.0 | 5.6 | 9.7 | 16.7 | 0.0 | 10.1 | 0.0 | 1.2 | 2.3 |
| rel-r3-h55-2 | 39 | 14 | 23115 | 25 | run | 100666 | 137.6 | 6.1 | 43.3 | 24.2 | 0.0 | 34.2 | 0.0 | 7.2 | 0.0 | 21.5 | 1.1 |
| rel-r3-h55-3 | 54 | 11 | 23115 | 43 | run | 101047 | 105.5 | 6.2 | 31.7 | 12.0 | 0.0 | 25.2 | 1.1 | 6.4 | 11.1 | 9.4 | 2.4 |
| rel-r4-h55-1 | 41 | 17 | 20386 | 24 | design | 103588 | 99.1 | 30.1 | 12.9 | 22.4 | 0.0 | 5.8 | 21.6 | 3.5 | 0.0 | 0.0 | 2.8 |
| rel-r4-h55-2 | 59 | 35 | 20386 | 24 | design | 102437 | 96.3 | 20.8 | 11.4 | 20.4 | 0.0 | 19.2 | 21.1 | 0.0 | 0.0 | 0.0 | 3.3 |
| rel-r4-h55-3 | 49 | 22 | 20386 | 27 | design | 109502 | 101.6 | 13.0 | 8.2 | 25.2 | 0.0 | 2.1 | 35.3 | 3.7 | 10.0 | 0.7 | 3.5 |
| rel-r5-h55-1 | 50 | 18 | 23121 | 32 | diagnosis | 102005 | 99.5 | 46.3 | 9.9 | 12.8 | 0.0 | 0.5 | 9.5 | 11.0 | 2.0 | 6.2 | 1.4 |
| rel-r5-h55-2 | 29 | 0 | 23121 | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| rel-r5-h55-3 | 36 | 3 | 23121 | 33 | diagnosis | 100730 | 88.0 | 8.5 | 28.5 | 11.8 | 8.6 | 10.8 | 0.0 | 9.9 | 0.0 | 8.2 | 1.8 |
| rel-r6-h55-1 | 30 | 0 | 23220 | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| rel-r6-h55-2 | 25 | 0 | 23220 | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| rel-r6-h55-3 | 36 | 7 | 23220 | 29 | diagnosis | 100750 | 102.1 | 13.0 | 40.2 | 14.5 | 7.0 | 14.5 | 0.0 | 8.2 | 0.0 | 2.5 | 2.2 |

### Over-100k requests by phase, and the lever each points to (INFERRED)

| run | design | run | diagnosis | design tool kB | lever the split points to (INFERRED) |
|---|---|---|---|---|---|
| rel-r2-h55-1 | 0 | 1 | 9 | 78.0 | fresh context / fewer diagnosis turns (most over-100k requests are in diagnosis) |
| rel-r2-h55-2 | 0 | 0 | 12 | 84.8 | fresh context / fewer diagnosis turns (most over-100k requests are in diagnosis) |
| rel-r2-h55-3 | 6 | 5 | 9 | 119.1 | fresh context / fewer diagnosis turns (most over-100k requests are in diagnosis) |
| rel-r3-h55-1 | 0 | 0 | 6 | 75.1 | fresh context / fewer diagnosis turns (most over-100k requests are in diagnosis) |
| rel-r3-h55-2 | 0 | 5 | 9 | 108.9 | fresh context / fewer diagnosis turns (most over-100k requests are in diagnosis) |
| rel-r3-h55-3 | 0 | 1 | 10 | 100.5 | fresh context / fewer diagnosis turns (most over-100k requests are in diagnosis) |
| rel-r4-h55-1 | 5 | 5 | 7 | 105.5 | fresh context / fewer diagnosis turns (most over-100k requests are in diagnosis) |
| rel-r4-h55-2 | 22 | 4 | 9 | 121.0 | fewer orientation reads / smaller base (crossed during design) |
| rel-r4-h55-3 | 14 | 4 | 4 | 116.2 | fewer orientation reads / smaller base (crossed during design) |
| rel-r5-h55-1 | 0 | 0 | 18 | 87.3 | fresh context / fewer diagnosis turns (most over-100k requests are in diagnosis) |
| rel-r5-h55-2 | 0 | 0 | 0 | 61.0 | none needed: stayed under 100k |
| rel-r5-h55-3 | 0 | 0 | 3 | 69.2 | fresh context / fewer diagnosis turns (most over-100k requests are in diagnosis) |
| rel-r6-h55-1 | 0 | 0 | 0 | 64.8 | none needed: stayed under 100k |
| rel-r6-h55-2 | 0 | 0 | 0 | 48.4 | none needed: stayed under 100k |
| rel-r6-h55-3 | 0 | 0 | 7 | 86.3 | fresh context / fewer diagnosis turns (most over-100k requests are in diagnosis) |

### Named file reads: bytes, request, phase, and over-100k requests each would have avoided if absent (INFERRED)

A read is a Bash call whose read verb (cat, head, tail, sed, grep, awk, nl, less, more) names the file, or a Read/Grep of it. A compound call reads other files too, so its bytes are an upper bound for the file. "Avoided" counts later over-100k requests whose prompt minus those bytes / 4 would be at or under 100,000. The 4 bytes per token, and the assumption that nothing else would have been read instead, are INFERRED.

| run | serverLog: reads / kB / avoided | runbookMd: reads / kB / avoided | projectMd: reads / kB / avoided | runMongooseSkill: reads / kB / avoided | addANodeSkill: reads / kB / avoided | loadAuditLogSkill: reads / kB / avoided | claudeMd: reads / kB / avoided | readmeMd: reads / kB / avoided |
|---|---|---|---|---|---|---|---|---|
| rel-r2-h55-1 | — | 1 / 12.5* / 4 | 1 / 12.5* / 4 | — | — | — | — | 1 / 12.5* / 4 |
| rel-r2-h55-2 | — | 1 / 8.2* / 3 | 1 / 8.2* / 3 | — | — | — | — | 1 / 11.0* / 3 |
| rel-r2-h55-3 | — | 1 / 12.8* / 0 | 1 / 12.8* / 0 | — | — | — | — | 1 / 12.8* / 0 |
| rel-r3-h55-1 | — | 1 / 12.0* / 1 | 1 / 12.0* / 1 | 2 / 14.5* / 1 | 2 / 13.4* / 1 | — | — | — |
| rel-r3-h55-2 | 5 / 26.1* / 1 | 2 / 18.4* / 1 | 2 / 18.4* / 1 | 2 / 12.3* / 1 | 1 / 26.8* / 2 | 1 / 4.2 / 0 | — | 1 / 6.4* / 1 |
| rel-r3-h55-3 | 3 / 7.5* / 1 | 1 / 9.7* / 1 | 1 / 9.7* / 1 | — | 2 / 14.7* / 1 | 1 / 5.1* / 0 | — | — |
| rel-r4-h55-1 | — | 1 / 19.0* / 2 | 1 / 8.2* / 0 | — | — | — | — | 1 / 19.0* / 2 |
| rel-r4-h55-2 | — | 1 / 8.2* / 0 | 1 / 8.2* / 0 | — | — | — | — | 1 / 13.0* / 1 |
| rel-r4-h55-3 | — | 1 / 8.2* / 0 | 1 / 8.2* / 0 | — | — | — | — | 1 / 13.0* / 0 |
| rel-r5-h55-1 | — | 1 / 9.9* / 1 | 1 / 9.9* / 1 | — | 1 / 22.5* / 5 | — | — | — |
| rel-r5-h55-2 | — | 1 / 7.9* / 0 | 1 / 5.5* / 0 | — | 1 / 27.6* / 0 | 1 / 5.2* / 0 | — | — |
| rel-r5-h55-3 | — | 1 / 7.9* / 1 | 1 / 5.5* / 1 | — | 1 / 17.9* / 3 | 1 / 5.1* / 1 | — | — |
| rel-r6-h55-1 | — | 1 / 9.0* / 0 | 1 / 9.0* / 0 | — | 2 / 14.7* / 0 | — | — | — |
| rel-r6-h55-2 | — | 1 / 12.1* / 0 | 1 / 12.1* / 0 | — | — | — | — | — |
| rel-r6-h55-3 | — | 1 / 12.1* / 4 | 1 / 12.1* / 4 | — | 2 / 25.3* / 7 | — | — | — |
| **total** | 8 / 33.5 / 2 | 16 / 167.8 / 18 | 16 / 152.3 / 16 | 4 / 26.9 / 2 | 12 / 162.9 / 19 | 4 / 19.5 / 1 | 0 / 0.0 / 0 | 7 / 87.7 / 11 |

`*` = at least one compound call.

### Event-processing failures during authoring (un-injected service)

| run | failure | un-injected service | diagnosis window (calls) | requests | tool kB | server.log reads / kB | over-100k in window | window cost $ | phase |
|---|---|---|---|---|---|---|---|---|---|
| rel-r3-h55-2 | yes | True | 27–34 (8) | 8 | 29.8 | 4 / 22.5 | 3 | 0.0386 | run |
| rel-r3-h55-3 | yes | True | 37–48 (12) | 11 | 13.6 | 2 / 6.8 | 0 | 0.0162 | design |

2 of 15 runs hit an event-processing failure; the other 13 show none.

### Reading these tables

**The base.**
- Every request re-pays a base of **20.4k** tokens (Spring starter) or **23.1–23.2k** (bundle): the system prompt,
  the tool definitions and the project's preloaded `CLAUDE.md`, before any tool result.
- No run read `CLAUDE.md` with a tool, because the CLI preloads it into that base.
- A run therefore crosses 100k after it has carried about **77–80k** tokens of tool results and turns. Measured as
  bytes before the crossing, that is 85–138 kB.

**Where runs cross.**
- 12 of the 15 runs cross 100k; 3 never do (rel-r5-h55-2, rel-r6-h55-1, rel-r6-h55-2).
- Of the 12: 5 cross in diagnosis, 3 in the run phase, and 4 in design.
- In 10 of the 12, most over-100k requests fall in diagnosis.
- Over all 15 runs, diagnosis holds 103 of the 175 over-100k requests (the phase table above), and run holds 25.
- **Lever:** a fresh context for diagnosis (L4 (iii)) is the lever these runs point to (INFERRED). It removes the
  carried design history from every diagnosis request at the price of re-paying the base once.

**Design crossings.**
- Rung 4's runs (r4-h55-2 and -3) cross early in design, at 22 and 14 over-100k design requests. That is where they
  hit a reconcile loop: SPRING_RECONCILE_CONFLICT, then `withdraw`, then regenerate.
- Their design tool bytes are 116–121 kB. **Lever:** fewer orientation and design reads, or a shorter loop (INFERRED).

**Orientation reads.**
- In every run, RUNBOOK.md and PROJECT.md are read together in the first one or two calls, with README.md in 7
  of the 15 runs (compound calls, 5.5–19 kB).
- `add-a-node/SKILL.md` is read in 8 of the 9 bundle runs (13–28 kB with whatever else the call read). The ninth,
  rel-r6-h55-2, loaded it through the `Skill` tool.
- Skills loaded through the `Skill` tool are not counted as reads here (9 calls in 7 runs: `load-audit-log` in 5,
  `add-a-node`, `point-at-the-fault`, and the CLI-bundled `dataviz` in 2).
- `run-mongoose-server/SKILL.md` is read in 2 runs (12–15 kB).
- Removing any one of these alone would, by the bytes/4 model, have kept at most 0–7 of a run's over-100k requests
  under the line (INFERRED).
- The file-group columns overlap, because one compound call can read several files, so they are **not additive**.

**`logs/server.log`.**
- It is read only in rel-r3-h55-2 (5 reads, 26.1 kB) and rel-r3-h55-3 (3 reads, 7.5 kB). Each read is a grep or
  sed slice, not a whole-file read; the file is 30–32 kB.
- By the bytes/4 model, each run's reads would have avoided 1 over-100k request (INFERRED).
- **Cause:** see the next section.

### The server.log reads diagnose an un-injected service, not a guidance change

In **2 of the 15 runs**, rel-r3-h55-2 and rel-r3-h55-3, both on rung 3, the author's own change left the
`TradeLimits` service un-injected:
- rel-r3-h55-2 gives `NullPointerException … "this.tradeLimits" is null`;
- rel-r3-h55-3 gives `IllegalStateException … TradeLimits service is not registered`.

In both, the server booted, exported and stopped cleanly. `run-and-export.sh`'s summary reported success. Every
event then failed inside the new node, so the export held only lifecycle and control records. The authors noticed
the missing business records and grepped `logs/server.log` to find out why.

**What diagnosing the failure cost**, measured from the call after the failed run to the call before the fixing
rerun:

| run | calls | requests | tool bytes | server.log reads, bytes | over-100k requests | cost |
|---|---|---|---|---|---|---|
| rel-r3-h55-2 | 8 | 8 | 29.8 kB | 4, 22.5 kB | 3 | $0.039 |
| rel-r3-h55-3 | 12 | 11 | 13.6 kB | 2, 6.8 kB | 0 | $0.016 |

In rel-r3-h55-2, three greps make up 20.9 kB of its 22.5 kB of server.log reads, and a `grep -c` after a `cp` makes
up the remaining 1.6 kB.

**Not a regression.** The pointer "server output in logs/server.log" was already printed before fluxtion-web #11;
the baseline runs printed it and nobody read the log. No other run hit an event-processing failure, and both of
these runs PASS. The cost is that of **an event-processing failure the run summary did not report**. A summary that
said "N events failed in a node" would have pointed straight at it (INFERRED). The owner reports that a fix is being
built.
