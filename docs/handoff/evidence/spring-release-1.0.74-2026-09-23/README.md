# Spring-side release evidence

G14 predictions and exact task are frozen before the client in compiler evidence commit
`abaff023`. The task's business text is identical to the September 19 task; environment
changes are recorded in `freeze.json`. Public release/deployment and public provisioning checks pass; G14 remains open after an incomplete retry.
See [the release record](../../release_spring_side_1_0_74_2026_09_23.md) for scope and failures.
The public standalone attempt 1 (old version) and attempt 2 (1.0.74 pass) are separate
result files; hosted and keyless-bundle results retain their own conditions.

## Documentation integration gate

Replayed the nine reviewed documentation/evidence commits onto analyser main `fda01845`
without conflicts. No application source changed. Initial `JAVA_HOME=/path/to/jdk21 mvn -q test`
ran 1,876 tests, one failure, zero errors and 62 skips. The sole failure is an unchanged
main fixture, `mongoose-audit-production-2026-09-23/spike-output.txt`, whose intentional
trailing spaces were not listed in the evidence exemption.

Correction announced before editing, documented here afterwards: explicitly exempt that one
captured producer output and include it in the existing byte-sensitive preservation assertion.
The initial run supplies the observed two-line red control for the missing exemption.
The fixture bytes stay unchanged. No separate mutation of the preserved fixture is claimed. The existing initial red
output is retained in the operator archive. This is evidence-test maintenance, not an analyser
feature change. The corrected full `mvn -q test` gate passes: 1,876 tests, zero failures/errors, 62 skips.
Strict docs passes. The fixture is byte-identical to main `fda01845`; only the test
exemption and its existing preservation list change. No separate fixture mutation is claimed.
