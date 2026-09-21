# SG-1 correction released — 2026-09-21

**SG-1 CLOSED:** starter **1.0.73** is published, the playground pin is deployed, and the public
standalone Spring download passes its actual setup, validation, changed-design generation and run.
Analyser stays at 1.16.0; this repair needs no new analyser binary or hosted generator deployment.

## What changed

The starter now publishes a flattened consumer POM with resolved public dependencies and no
private reactor parent. Compiler Java and generation logic are unchanged. Released 1.0.72 artifacts
were not overwritten. A descriptor regression test fails against the old packaging; the publication
probe now checks the consumer POM as well as the public BOM and executable.

Playground `884ad4e` pins starter 1.0.73 and its public artifact/resource digests, and adds a real
public-download provisioning check. Curated examples keep their independently released BOM pin.
The comment resource bytes are unchanged. Follow-up `e668629` corrects the CI scope as explained below.

## Verified, not inferred

- Full release-profile build: **4,093 tests, zero failures/errors, nine skips**. Compiler CI
  35576829191 passed; publication workflow 35577809248 passed. Public consumer POM has no parent;
  executable version and BOM pin match 1.0.73. Jar check: 2,060 classes, Java 17 floor, keyless validation.
- The strengthened descriptor probe rejects published 1.0.72. The new public-download check also
  rejects it at the shipped `setup.sh` with the original missing-parent error. Both witnesses retained.
- Website: **546/546 tests**, production build, public comment parity and seven Python preflight
  checker tests pass. Negative checks reject wrong tool version, missing classpath and invalid XML.
- Preview download using published artifacts: setup/validate pass with empty caches. Then the
  **public** URL was tested independently with new empty Maven/wrapper caches and empty user/global
  settings: **48 classpath entries, validation valid, all 35 originals unchanged**, 31.821 seconds.
  No private repository, local release install, key or client session was used for provisioning.
- On that public download, add `sentimentCalc` to the `PriceTick` handler binding. Run its unmodified
  `validate.sh`, `generate.sh`, `run.sh`: all exit 0. Reconciliation adds the handler, generated dispatch
  calls it, and the current receipt says `build.compilerRan=true`. The sample audit has PriceTick,
  OrderEvent and NewsEvent (plus Init and audit-listener control records) and shows the added handler on PriceTick. An offline wrong-handler witness
  is rejected. The first offline assertion expected only business events and was corrected to include
  the two actual framework records; the run was not repeated. This tests scaffold execution, not trading/business correctness.
- Generation uses the owner's existing authorised configuration by reference; no credential is copied
  into the project or committed evidence. Generated source identifies client **1.0.73**, deployed
  generator **1.0.72**. Successful generation verifies that pairing; no Railway redeploy is required
  for this packaging-only correction.
- Analyser documentation gate: **1,718 tests, zero failures/errors, 40 display skips**. Guide screenshots
  remain the earlier genuine captures; no application/UI behavior changed. Strict docs and sweep apply.

## New finding kept separate: SG-2

The first expanded CI matrix, 35579191399, failed on the hosted Spring template before setup: its
ZIP has no authoring record or local scripts. The existing hosted generator emits Spring/AOT graph
files without calling the local-authoring emitter. This is a product gap, not a Maven failure, and
was not repaired by changing the publication POM. The initial matrix cancelled its standalone sibling;
the independent public standalone result above is the evidence for SG-1.

Corrected production CI **35579653154 passes** both the existing customer bundle check and the
standalone Spring provisioning check. The deployed correction scopes the preflight to the standalone Spring template which ships these
scripts. SG-2 remains OPEN in both trackers. The guide states that the hosted template uses its own
build/hosting runbooks and does not yet support these local authoring commands. Closure needs an
actual hosted ZIP, correct hosted launcher, ownership record, setup/validate and generation/run checks,
while preserving the existing keyless bundle. Do not substitute helper tests with a bundle flag.

## Reproduction and evidence

In the playground checkout, with Java 21 and a new output directory:

```sh
python3 web/scripts/spring-preflight.py --output /tmp/spring-public-check --expect-version 1.0.73
```

The production-publication workflow runs it without a model or key and preserves download/logs/result.
Generation is a separately provisioned check; the keyless gate does not claim it.

[Old failure](evidence/sg1-release-2026-09-21/public-1.0.72-red.json),
[original setup error](evidence/sg1-release-2026-09-21/public-1.0.72-setup.log),
[public corrected setup](evidence/sg1-release-2026-09-21/public-1.0.73-green.json),
[public CI setup](evidence/sg1-release-2026-09-21/public-ci-1.0.73-green.json),
[changed-design result](evidence/sg1-release-2026-09-21/generation-result.json),
[changed XML](evidence/sg1-release-2026-09-21/changed-design.xml),
[current receipt](evidence/sg1-release-2026-09-21/run-receipt.json),
[sample audit excerpt](evidence/sg1-release-2026-09-21/sample-run.txt),
[hosted gap](evidence/sg1-release-2026-09-21/hosted-template-failure.json).

Published starter SHA-256: `6b508dcc031c8bb96721a656b602487718bf40f6ab7fa3708d8265dce003b25b`.
No cold-start battery or new client trial was run. Customer credential acquisition and business
behavior are not established by this release check.
