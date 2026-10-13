# Authoring ladder rung 5 (integration only): predictions, sealed before launch (2026-10-12)

Same rig, cap and models as rungs 2 to 4 (`PREDICTIONS-LADDER.md`), run straight after them: claude-haiku-5-5 x3 and
claude-sonnet-5 x1, in the order h55-1, s5-1, h55-2, h55-3.

**Fixture:** the published analyser bundle, plus a DEMO supplier library `com.acme.fx:acme-fx:1.0.0` (FxQuote,
CsvToFxQuote, FxRateBook) delivered as a Maven repository in `supplier-repo/` with SUPPLIER.md, plus `data/fx.csv`.

**Task:** `TASK-R5.txt`. Integrate a second feed and the supplier's node by configuration only, with no Java written,
and with price processing unchanged.

**Operator ground truth:** `pom.xml` (repository and dependency), the Spring XML (bean, event type, handler binding)
and the yml feed. Plain `./generate.sh` FAILED with `SPRING_RECONCILE_CONFLICT: Cannot reconcile
com.acme.fx.FxRateBook#<class>` ("Class exists on the resolved classpath; reference the supplier node rather than
generating a shadowing shell"). It succeeded with `./generate.sh --no-reconcile`, a documented regenerate option
("disabled reconciliation is reported as skipped"). This trap is part of the rung and is being filed as a compiler
issue.

**Oracle `r5_oracle.py`:** I1, the FxQuote rate sequence; I2, every price record's business keys equal to the
published bundle's own run; I3, no Java changed outside generated/ and the supplier repository byte-identical. On
the ground truth all three pass. Each control fails exactly its own check: no integration fails I1, a changed
price path fails I2, and an added Java adapter fails I3.

**PASS** = ORACLE and VALID and CHART and S6′, as for the other rungs.

## Predictions

1. Haiku 5.5 PASSes at most 1 of 3, and Sonnet 5 PASSes 1 of 1.
2. Every run that runs plain generate hits SPRING_RECONCILE_CONFLICT.
3. The most common Haiku 5.5 failure is I3 (it writes a Java adapter or wrapper to get past the conflict) or a
   generation that never succeeds, not I1 or I2.
4. No run modifies the supplier repository.
5. S6′ holds in 4 of 4.
