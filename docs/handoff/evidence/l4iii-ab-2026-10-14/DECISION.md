| rung | n T/C | diag cost $ median T / C (C spread) | (a) | diag >100k median T / C | (b) | total $ median T / C (C spread) | (c) | PASS T/C | ORACLE T/C | CHART T/C | (d) | rung passes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2 | 3/3 | 0.0688 / 0.1266 (0.0616) | False | 7 / 13 | False | 0.2098 / 0.1819 (0.0377) | False | 3/3 | 3/3 | 3/3 | True | False |
| 3 | 3/3 | 0.0171 / 0.0147 (0.0191) | False | 0 / 0 | True | 0.0573 / 0.0513 (0.0301) | False | 3/3 | 3/3 | 3/3 | True | False |
| 4 | 3/3 | 0.0343 / 0.0678 (0.0227) | True | 2 / 8 | True | 0.1036 / 0.2124 (0.0880) | True | 3/3 | 3/3 | 3/3 | True | True |
| 5 | 3/3 | 0.0177 / 0.0194 (0.0237) | False | 0 / 0 | True | 0.0530 / 0.0557 (0.0282) | True | 3/3 | 3/3 | 3/3 | True | False |

**Decision: NO EFFECT SHOWN.** UP-PG-08 starter half proceeds: False. Out-of-sample check (recomputed within 2% of billed in every run): True.

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

{"decision": "NO EFFECT SHOWN", "starterHalf": false, "outOfSample": true, "rungs": {"2": {"n": [3, 3], "nCost": [3, 3], "diagCost": {"t": 0.06881241, "c": 0.12656291, "cSpread": 0.061622960000000004}, "diagOver100k": {"t": 7, "c": 13}, "total": {"t": 0.20984976500000002, "c": 0.18185992, "cSpread": 0.03772747000000001}, "quality": {"PASS": [3, 3], "ORACLE": [3, 3], "CHART": [3, 3]}, "a": false, "b": false, "c": false, "d": true, "cost": false, "pass": false, "totalAboveBySpread": false}, "3": {"n": [3, 3], "nCost": [3, 3], "diagCost": {"t": 0.01706173, "c": 0.0146849, "cSpread": 0.019086149999999996}, "diagOver100k": {"t": 0, "c": 0}, "total": {"t": 0.05731407, "c": 0.051254299999999996, "cSpread": 0.03010636}, "quality": {"PASS": [3, 3], "ORACLE": [3, 3], "CHART": [3, 3]}, "a": false, "b": true, "c": false, "d": true, "cost": false, "pass": false, "totalAboveBySpread": false}, "4": {"n": [3, 3], "nCost": [3, 3], "diagCost": {"t": 0.03434468, "c": 0.06779091, "cSpread": 0.02266825}, "diagOver100k": {"t": 2, "c": 8}, "total": {"t": 0.10359432000000002, "c": 0.21242481000000002, "cSpread": 0.08803965}, "quality": {"PASS": [3, 3], "ORACLE": [3, 3], "CHART": [3, 3]}, "a": true, "b": true, "c": true, "d": true, "cost": true, "pass": true, "totalAboveBySpread": false}, "5": {"n": [3, 3], "nCost": [3, 3], "diagCost": {"t": 0.01774661, "c": 0.01943941, "cSpread": 0.023705970000000003}, "diagOver100k": {"t": 0, "c": 0}, "total": {"t": 0.053039085, "c": 0.05570585000000002, "cSpread": 0.028188910000000005}, "quality": {"PASS": [3, 3], "ORACLE": [3, 3], "CHART": [3, 3]}, "a": false, "b": true, "c": true, "d": true, "cost": false, "pass": false, "totalAboveBySpread": false}}}
