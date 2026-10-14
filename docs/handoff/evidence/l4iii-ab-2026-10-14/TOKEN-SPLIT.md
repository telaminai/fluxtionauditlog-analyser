# Token split: M72 L4 (iii) A/B (2026-10-14)

`python3 tools/token_split.py summary <control> <treatment>` (analyser main `e3786f68`, `fc867643…`), run over the 24
committed `results/<run>/{buckets,categories,phases}.json` tables. Each arm was linked into its own directory, so the
first block below is the control and the second the treatment. The tool printed both headers as the parent directory's
name; they are relabelled here, and nothing else is edited. The out-of-sample check holds: every run's recomputed cost
is within 2% of billed (0.985–1.006).

### Control arm (12 runs, `results/ab-r?-c-?`)

| run | toolResultBytes = score.json | cost recomputed / billed | >100k requests | >100k cost share |
|---|---|---|---|---|
| ab-r2-c-1 | 108615 = 108615 yes | 0.9968 | 16/44 | 0.7452 |
| ab-r2-c-2 | 110495 = 110495 yes | 1.0005 | 13/46 | 0.6956 |
| ab-r2-c-3 | 113700 = 113700 yes | 0.9989 | 19/48 | 0.7973 |
| ab-r3-c-1 | 93842 = 93842 yes | 1.0 | 0/28 | 0.0 |
| ab-r3-c-2 | 62592 = 62592 yes | 1.0 | 0/29 | 0.0 |
| ab-r3-c-3 | 101445 = 101445 yes | 0.9939 | 3/31 | 0.3203 |
| ab-r4-c-1 | 121773 = 121773 yes | 0.9933 | 20/50 | 0.7459 |
| ab-r4-c-2 | 102611 = 102611 yes | 0.9855 | 10/38 | 0.5837 |
| ab-r4-c-3 | 100132 = 100132 yes | 1.0035 | 17/46 | 0.7619 |
| ab-r5-c-1 | 98239 = 98239 yes | 1.0 | 0/33 | 0.0 |
| ab-r5-c-2 | 82428 = 82428 yes | 1.0064 | 3/42 | 0.3089 |
| ab-r5-c-3 | 83761 = 83761 yes | 1.0 | 0/37 | 0.0 |

**claude-haiku-5-5, 12 runs — tool-result bytes by kind** (share of 1,179,633 bytes; share = bytes assigned by kind priority; single-kind = calls that read only that kind, the lower bound; upper = every call that names the kind)

| kind | bytes | share | single-kind share (lower) | upper | calls | carried share | tier-weighted carried share |
|---|---|---|---|---|---|---|---|
| auditLog | 71,219 | 6.0% | 2.7% | 6.0% | 56 | 3.2% | 4.0% |
| generated | 49,394 | 4.2% | 0.0% | 4.4% | 15 | 4.6% | 5.6% |
| server | 32,001 | 2.7% | 0.4% | 4.1% | 28 | 2.3% | 2.5% |
| build | 149,549 | 12.7% | 4.4% | 15.8% | 73 | 12.4% | 12.1% |
| design | 211,165 | 17.9% | 0.5% | 19.7% | 24 | 22.7% | 19.5% |
| library | 112,532 | 9.5% | 3.9% | 13.2% | 35 | 12.1% | 14.2% |
| docs | 227,205 | 19.3% | 5.0% | 39.1% | 34 | 24.4% | 21.1% |
| source | 149,039 | 12.6% | 11.8% | 50.8% | 45 | 12.8% | 12.0% |
| analyser | 146,524 | 12.4% | 12.4% | 12.4% | 156 | 3.7% | 6.5% |
| other | 31,005 | 2.6% | 2.6% | 21.9% | 146 | 1.9% | 2.5% |
| unclassified | 0 | 0.0% | 0.0% | 0.0% | 0 | 0.0% | 0.0% |

Raw audit-log text (auditLog without auditCompare): 70,067 bytes, 5.9% of tool bytes; per run median 5.3%, max 15.9%. Build/server results that also carry audit-record keys: 13,806 bytes, 1.2% (whole calls, an upper bound on echoed audit text).

Cost: billed $1.4894, recomputed $1.4862 (0.9978); requests over 100k $0.8768 = **59.0%** of recomputed cost (input side alone $0.7153 = 48.1%); at or under 100k $0.5900 = 39.7%; CLI side calls $0.0194 = 1.3%.

| phase | cost $ | cost share | requests | over 100k | tool calls | analyser calls | tool bytes | bytes share |
|---|---|---|---|---|---|---|---|---|
| design | 0.4958 | 33.4% | 277 | 8 | 386 | 10 | 953,029 | 80.8% |
| run | 0.2974 | 20.0% | 69 | 27 | 82 | 21 | 110,905 | 9.4% |
| diagnosis | 0.6736 | 45.3% | 126 | 66 | 144 | 125 | 115,699 | 9.8% |
| (CLI side calls) | 0.0194 | 1.3% | | | | | | |

### Treatment arm (12 runs, `results/ab-r?-t-?`)

| run | toolResultBytes = score.json | cost recomputed / billed | >100k requests | >100k cost share |
|---|---|---|---|---|
| ab-r2-t-1 | 136059 = 136059 yes | 0.9982 | 14/52 | 0.7062 |
| ab-r2-t-2 | 161138 = 161138 yes | 0.9925 | 27/57 | 0.8392 |
| ab-r2-t-3 | 95459 = 95459 yes | 0.9972 | 7/43 | 0.4783 |
| ab-r3-t-1 | 98261 = 98261 yes | 1.0 | 0/29 | 0.0 |
| ab-r3-t-2 | 106898 = 106898 yes | 1.001 | 0/35 | 0.0 |
| ab-r3-t-3 | 125854 = 125854 yes | 0.9961 | 6/38 | 0.5109 |
| ab-r4-t-1 | 170089 = 170089 yes | 0.9849 | 14/70 | 0.6348 |
| ab-r4-t-2 | 83941 = 83941 yes | 1.0 | 0/38 | 0.0 |
| ab-r4-t-3 | 92504 = 92504 yes | 0.9872 | 6/35 | 0.511 |
| ab-r5-t-1 | 126387 = 126387 yes | 1.0006 | 0/37 | 0.0 |
| ab-r5-t-2 | 100845 = 100845 yes | 1.0 | 0/27 | 0.0 |
| ab-r5-t-3 | 141100 = 141100 yes | 1.0006 | 0/47 | 0.0 |

**claude-haiku-5-5, 12 runs — tool-result bytes by kind** (share of 1,438,535 bytes; share = bytes assigned by kind priority; single-kind = calls that read only that kind, the lower bound; upper = every call that names the kind)

| kind | bytes | share | single-kind share (lower) | upper | calls | carried share | tier-weighted carried share |
|---|---|---|---|---|---|---|---|
| auditLog | 124,713 | 8.7% | 3.6% | 8.7% | 56 | 4.7% | 8.0% |
| generated | 17,302 | 1.2% | 0.0% | 1.4% | 9 | 1.5% | 2.3% |
| server | 18,219 | 1.3% | 0.3% | 2.7% | 20 | 0.8% | 1.2% |
| build | 158,877 | 11.0% | 5.3% | 13.2% | 73 | 11.6% | 11.6% |
| design | 198,477 | 13.8% | 0.6% | 15.0% | 25 | 20.5% | 18.2% |
| library | 133,206 | 9.3% | 3.6% | 14.2% | 47 | 11.5% | 12.1% |
| docs | 353,000 | 24.5% | 8.5% | 35.1% | 47 | 31.4% | 27.6% |
| source | 134,282 | 9.3% | 8.1% | 40.6% | 33 | 11.1% | 11.2% |
| analyser | 236,843 | 16.5% | 16.5% | 16.5% | 195 | 4.7% | 4.6% |
| other | 63,372 | 4.4% | 4.4% | 20.8% | 168 | 2.3% | 3.2% |
| unclassified | 244 | 0.0% | 0.0% | 0.0% | 1 | 0.0% | 0.0% |

Raw audit-log text (auditLog without auditCompare): 121,794 bytes, 8.5% of tool bytes; per run median 6.5%, max 17.3%. Build/server results that also carry audit-record keys: 3,866 bytes, 0.3% (whole calls, an upper bound on echoed audit text).

Cost: billed $1.4360, recomputed $1.4275 (0.9941); requests over 100k $0.7375 = **51.7%** of recomputed cost (input side alone $0.5693 = 39.9%); at or under 100k $0.6397 = 44.8%; CLI side calls $0.0503 = 3.5%.

| phase | cost $ | cost share | requests | over 100k | tool calls | analyser calls | tool bytes | bytes share |
|---|---|---|---|---|---|---|---|---|
| design | 0.5396 | 37.8% | 252 | 13 | 359 | 5 | 1,004,945 | 69.9% |
| run | 0.3319 | 23.3% | 106 | 22 | 130 | 37 | 202,473 | 14.1% |
| diagnosis | 0.5056 | 35.4% | 150 | 39 | 185 | 153 | 231,117 | 16.1% |
| (CLI side calls) | 0.0503 | 3.5% | | | | | | |

**Diagnosis contexts** (L4 iii; a run with no `delegation` block did not delegate)

| run | delegations | subagent requests | subagent >100k | subagent first / max prompt | main diagnosis requests | main diagnosis >100k | main max prompt | subagent cost $ | diagnosis cost $ | brief chars | reply chars | backgrounded / main requests meanwhile | warnings |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ab-r2-t-1 | 1 | 7 | 0 | 13671 / 31900 | 14 | 14 | 126898 | 0.00556731 | 0.15349392 | 2094 | 3998 | 0 / 0 | — |
| ab-r2-t-2 | 2 | 11 | 0 | 13835 / 29180 | 6 | 6 | 142532 | 0.00838971 | 0.06881241 | 4776 | 6322 | 0 / 0 | — |
| ab-r2-t-3 | 0 | | | | 9 | 7 | 105379 | | 0.0536661 | | | | |
| ab-r3-t-1 | 0 | | | | 7 | 0 | 97298 | | 0.01195345 | | | | |
| ab-r3-t-2 | 1 | 14 | 0 | 16693 / 37126 | 3 | 0 | 98292 | 0.00889385 | 0.01706173 | 2596 | 2762 | 0 / 0 | — |
| ab-r3-t-3 | 0 | | | | 6 | 6 | 114138 | | 0.05771681 | | | | |
| ab-r4-t-1 | 2 | 28 | 0 | 13748 / 55443 | 2 | 2 | 127008 | 0.02310087 | 0.0448952 | 4052 | 8220 | 0 / 0 | — |
| ab-r4-t-2 | 0 | | | | 7 | 0 | 96875 | | 0.00979187 | | | | |
| ab-r4-t-3 | 0 | | | | 4 | 4 | 107750 | | 0.03434468 | | | | |
| ab-r5-t-1 | 1 | 14 | 0 | 16473 / 49839 | 5 | 0 | 89665 | 0.01163839 | 0.02130474 | 1905 | 3061 | 0 / 0 | — |
| ab-r5-t-2 | 0 | | | | 9 | 0 | 96146 | | 0.01485982 | | | | |
| ab-r5-t-3 | 1 | 15 | 0 | 16571 / 43736 | 3 | 0 | 95829 | 0.01043074 | 0.01774661 | 2194 | 2710 | 0 / 0 | — |

