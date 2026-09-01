# Hold-out expectations — round 18, written before any engine exists

| # | check |
|---|---|
| P1 | 21 business records |
| P2 | E1 trips at cycles 9 and 14 only (third consecutive over-limit reading; 80 at cycle 10 resets) |
| P3 | E2 trips at cycle 19 only (V3 serviced at 100, reads at 300000; V2 never serviced so never trips) |
| P4 | E3 trips at cycles 15 and 16 only (V2 is inactive) |
| P5 | the repeated `ROSTER,V1,true` (cycle 4) and repeated `LIMIT,temp,90` (cycle 21) evaluate no detector |
| P6 | gate raises at 9 (107), 15 (120), 19 (150) and suppresses at 14 (97), 16 (80) |
| P7 | no detector trips in any other cycle |
| P8 | a reference-data cycle runs fewer nodes than a telemetry cycle |
