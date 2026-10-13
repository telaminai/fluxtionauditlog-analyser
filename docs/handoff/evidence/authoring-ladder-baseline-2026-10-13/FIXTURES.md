# Fixtures, downloads and seeds: authoring-ladder baseline (2026-10-13)

Written before the first run. Every digest below is also in `MANIFEST-BASELINE.sha256`.

## What was downloaded

All downloads were fetched at `2026-10-13T12:00:22Z` from the public endpoint with an explicit User-Agent (the CDN
refuses the Python default).

| Download | URL | Bytes | sha256 |
|---|---|---|---|
| Rung 2 Spring starter | `https://fluxtion-playground.dev/start/scaffold?s=<token>`, where the token is the same as the first ladder's (sha256 `6b97b575…`), and encodes `r2/request-spec.json` | 29296 | `5037a15146ff7760530d4ec68f4f1e80479bbd87cd9120be311838f7325f6831` |
| Rung 4 Spring starter | `…/start/scaffold?s=<token>`, where the token is the same as the first ladder's (sha256 `77f5595d…`), and encodes `r4/request-spec.json` | 26241 | `275541f4e4e134fe8fd5a8a8428cb9b2ca620529c492bfd4ca98e2756aaac65a` |
| Analyser bundle (rungs 3 and 5) | `https://fluxtion-playground.dev/start/scaffold?template=analyser-bundle` (the runner's `SCAFFOLD_URL`) | 99279 | `4db393edc28952949b42fe54d855b7a508ef4e07ac426ba1b6b0f195f427914c` |
| Analyser 1.33.0 | GitHub release `v1.33.0` asset `fluxtion-auditlog-analyser-1.33.0.jar` (published 2026-10-13T08:53:42Z) | 4701496 | `f70da960a9f54be0b7ff11d966034d3505da2752e38a67b8a42505fc231055c9` |

The analyser jar's digest equals the `SHA256SUMS.sha256` entry for `fluxtion-auditlog-analyser-1.33.0.jar` in the
same release (the release also lists `fluxtion-auditlog-analyser.jar` and `target/fluxtion-auditlog-analyser-1.33.0.jar`
with the same digest).

The request specs leave `versions` empty, so the scaffold returns today's defaults.

### Versions inside the downloads

All three templates have the same versions.

| | Spring starter (r2, r4) | Analyser bundle (r3, r5) | First ladder (2026-10-12) |
|---|---|---|---|
| `fluxtion-starter-core` (`starterVersion`) | 1.0.77 | 1.0.77 | 1.0.76 |
| `fluxtion-bom` | 1.0.77 | 1.0.77 | 1.0.76 |
| `fluxtion-runtime` (`fluxtion.version`) | 1.1.1 | 1.1.1 | 1.1.0 |
| `fluxtion-maven-plugin` | 1.3.0 | 1.3.0 | 1.3.0 |
| Bundle contract | — | m19-bundle/4, skills `canonical@7057074d`, targets analyser ≥ 1.33.0 | m19-bundle/4, skills `canonical@94e49ea7`, targets analyser ≥ 1.30.1 |
| Hosted generator, as stamped by a regeneration today | 1.0.78 | 1.0.78 | — |

The bundle's committed `MarketProcessor.java` still says `target generator version : 1.0.77`. Every ground-truth
regeneration today stamped `target generator version : 1.0.78` (4 of 4). Its audit traces carry `annotation:`
(for example `- rootNode: { thread: main, method: onPriceUpdate, annotation: OnEventHandler, … }`).

### What changed in the templates since the first ladder

This is the published guidance, so each run gets it. It is the change being measured.

- **Spring starter:** `RUNBOOK.md` step 5 changed from "Read the generated processor and changed node sources" to
  "Confirm the change from the Spring XML and the compiler's own messages … Do not read the generated processor, its
  GraphML or the builder". README.md and PROJECT.md also changed, and the version pins moved. The design XML is
  byte-identical to the first ladder's pristine download.
- **Analyser bundle:** it adds `run-and-export.sh`, which in one command starts the server, waits, settles,
  exports every processor and stops. It adds `.claude/skills/add-a-node/audit-compare.py`. A new CLAUDE.md section,
  "What to read when you change the graph", says not to read generated code, GraphML, the builder, script source, or
  build and server logs. Export is now one YAML per processor: `logs/audit-marketProcessor.yaml`, which replaces
  `logs/audit-audit-analyser-bundle.yaml`. `config/server-config.yml` gains a `performanceMonitoring` block before
  `services:` and has reworded feed comments. `BundleLifecycle.java`, `export-audit.*`, skills and docs also changed.

## Per-rung fixture: download plus the rung's own setup only

Each old fixture was diffed against its pristine source to find the rung-specific modification, and only that
modification was re-applied to today's download.

| Rung | Pristine source | Rung-specific modification (old fixture vs its pristine) | Re-applied to the new download | Fixture tree sha256 |
|---|---|---|---|---|
| 2 | Rung 2 Spring starter | One token in `application-context.xml`: the `pause` SignalBinding's node bean `child` became `childTypo`. This is the injected SPRING_SIGNAL_UNKNOWN_NODE defect. | The same one-token edit. The resulting XML is byte-identical to the first ladder's rung-2 fixture XML. | `48e40e0698d4e18cd4483330a54e342de5f0aa570acd3c85c82e39e4f8fbb882` |
| 3 | Analyser bundle | None: the fixture was the bundle as downloaded. | None. | `7563106c65fb9a9d58607cc8eddba0d1fc0fe3119d7a25b6a79e8fa1ca81f0b3` |
| 4 | Rung 4 Spring starter | None: the fixture was the download as fetched. | None. | `8ac86244269466856660764a6c3689561bf580d01434be816c0a74de76dca3a3` |
| 5 | Analyser bundle | Added `supplier-repo/` (the DEMO `com.acme.fx:acme-fx:1.0.0` Maven repository with SUPPLIER.md) and `data/fx.csv`. | Both copied byte-identical from the first ladder's rung-5 fixture (`diff -r` empty). The pom repository and dependency, the server feed and the XML bean are what the TASK asks the subject to make. The fixture does not pre-apply them, as before. | `b61fa77bfd3c739b2eabff3461eb269b18f2761839dcee50f22e904da74a6a60` |

The tree digest is computed by `harness/treesha.py`: sha256 over the sorted lines `<relative path>\0<file sha256>\n`.

## Tasks and oracles

- **Tasks:** `TASK-R2…R5.txt` are byte-identical to the first ladder's.
- **Oracles:** `R2Oracle.java`, `R4Oracle.java` (the corrected r2 disclosed in the first ladder, `6b7d6c91…`) and
  `r3_oracle.py` are byte-identical.
- **`r5_oracle.py`** changes two path constants and nothing else:
  - `FIXTURE` is now this harness's `r5/fixture`, the rebuilt fixture that I3 compares against.
  - `BASELINE` is now the new bundle's own as-shipped run, `r5/baseline/logs/audit-marketProcessor.yaml`. The old
    file name no longer exists in the template. The new baseline's business keys, as `price_business()` extracts
    them, are equal to the old baseline's for all five PriceEvent records.
- **`score_ladder.py`** changes only `L` (the harness root), which was a literal `/private/tmp/fx-ladder`.
- **The rung-3 and rung-5 oracles** glob `logs/*.yaml`, so the renamed export file is found unchanged.

No task or oracle was made impossible by the new template.

## Ground-truth validation (before sealing)

Each rung's operator ground truth from the first ladder (`r<n>/gt`) was ported onto the rebuilt fixture:
- the hand-authored files: the design XML, the node, event and service sources, `config/server-config.yml` and
  (for r5) `pom.xml`;
- for r2, r3 and r4, the generator-written `fluxtion-authoring.json` ownership record, with only its
  `starterVersion` changed from 1.0.76 to 1.0.77. Without it the reconciler correctly refuses the ported Java
  (SPRING_RECONCILE_CONFLICT on ownership).

Each project was then regenerated with the shipped `./generate.sh`. The key is used only by that script's Maven
plugin and builder path. The verdicts are under `groundtruth/`, and the ported solutions are
`groundtruth/gt-r<n>.solution.diff`.

| Rung | Ground truth (VALID, ORACLE) | Controls (each must FAIL the oracle) |
|---|---|---|
| 2 | PASS, PASS (`{10,1}`, `{40,1}`) | The unsolved fixture fails VALID and fails ORACLE at the build (`childTypo`). The typo-only repair passes VALID and fails ORACLE at step 1 (0 outputs). |
| 3 | PASS, PASS (F1 `largeTradeFlag.large`, F2 `alertCounter`) | The as-shipped bundle fails F1 and F2. A server limit of 3,000,000 instead of 300,000 fails F1 and F2. |
| 4 | PASS, PASS (all 7 outputs exact) | The unsolved fixture fails at step 1. No eviction fails at output 4 (26 vs 30). |
| 5 | PASS, PASS (I1, I2, I3) with **plain `./generate.sh`, no `--no-reconcile`** | The unintegrated fixture fails I1. An added Java file fails I3. |

**Port note (r3):** the first ladder's `server-config.yml` hunk applies at a shifted offset, because of the new
`performanceMonitoring` block, and lands outside `services:`. The server then refuses the YAML. The tradeLimits
entry was therefore placed under `services:` explicitly. The first ladder's ground truth also moved the admin port
to 8191, but the ported one keeps 8181, as shipped, because nothing else held 8181.

**Rung 5:** the starter refused the supplier class with SPRING_RECONCILE_CONFLICT on 1.0.76. Plain
`./generate.sh` now succeeds on the released 1.0.77 starter with the 1.0.78 hosted generator, and the oracle passes.

## Warm caches (cold-filled today, then frozen)

There are two caches. Each was filled cold into an empty `maven.repo.local` with an empty user `settings.xml`, so
only the templates' own repositories were used: Central and public Repsy. Neither was filled from `~/.m2`.

| Seed | Filled by | Fill started | Tree sha256 | Provenance (`harness/provenance.py`) |
|---|---|---|---|---|
| `seed-spring` | r2 and r4: ground-truth setup, validate, generate, oracle build and run.sh, plus the r2/r4 controls | 2026-10-13T12:03:09Z | `e78185a1ce19c391b1d3bba4cbb84edeff01391b786a039790b5c062f0b63df9` | 506 files from central and 10 from repsy-fluxtion-public. No problems. |
| `seed-bundle` | The as-shipped bundle run (the r5 baseline), the r3 ground truth and its controls, and the unintegrated r5 control | 2026-10-13T12:05:48Z | `95ec98c6e6c9f48ea5baf3999057698e5951db9046fb15e14800545fb64aa8a8` | 641 files from central and 16 from repsy-fluxtion-public. No problems. |

**Frozen:** both seeds were frozen read-only at 2026-10-13T12:09:37Z.

**Clean** means:
- every jar and pom is listed in its directory's `_remote.repositories` with a named remote, and that remote is
  only `central` or `repsy-fluxtion-public`;
- there is no locally installed artefact and no SNAPSHOT.

**Excluded from `seed-bundle`:** the supplier artefact. The r5 ground truth ran in a separate copy, so `com/acme` is
not in the seed, and a subject must resolve it from `supplier-repo/`.

**Sufficiency check:** `./setup.sh` succeeds **offline** (`-o`) on all four rebuilt fixtures from a copy of their
seed. On r2, `validate.sh` then fails, as intended, on the injected typo.

**Contents:** the seeds hold `fluxtion-starter-core` 1.0.77, `fluxtion-bom` 1.0.77 and `fluxtion-runtime` 1.1.1.

**What the seeds do not cover:** a seeded run does not exercise the cold download a user's setup performs. The
first ladder's runs were mixed: five ran cold and eleven ran warm.
