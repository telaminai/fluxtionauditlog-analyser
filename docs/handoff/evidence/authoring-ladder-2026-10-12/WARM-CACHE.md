# Warm cache disclosure (2026-10-12, before any affected run)

Repsy (`repo.repsy.io/mvn/fluxtion/fluxtion-public`) returned 502 Bad Gateway from about 22:00. Polls every 2 minutes
were still 502 at 23:10.

- **lad-r3-s5-1:** its setup.sh failed on `fluxtion-starter-core:1.0.76` (502). It reported itself blocked after
  writing the full solution in source. This is VOID (infrastructure), not a model failure.
- **lad-r3-h55-2:** stopped by the operator while Repsy was down. VOID.

Both are preserved as `*-VOID-repsy502` and re-run.

**Owner decision:** run the remaining cells with a warm cache. Each run's empty Maven repository is seeded from a
frozen copy of a cache filled TODAY by an earlier run that started empty:

| Seed | Source run | Used for | Tree sha256 |
|---|---|---|---|
| `seed-spring` | lad-r2-h55-3 | rungs 2 and 4 | `1c96097473eb4bb8cd7db2b738e85421232b633348dd31d8787e6925cb0218ae` |
| `seed-bundle` | lad-r3-h55-1 | rungs 3 and 5 | `c060f4a484c54013f1db5fd9affd1ab4049852395f8443357ec5cdab78d31880` |

**Provenance check:** every artefact directory records its source in `_remote.repositories`.
- seed-spring: 506 files from central and 10 from repsy-fluxtion-public.
- seed-bundle: 641 from central and 16 from repsy-fluxtion-public.
- Neither seed holds a locally installed artefact, a jar without provenance, or a SNAPSHOT.

The operator's ~/.m2 was NOT used, because it holds test builds, SNAPSHOTs and local installs.

**Sufficiency check:** with Repsy still down, setup.sh succeeded on every rung fixture (2, 3, 4 and 5) from its seed,
and made no request to Repsy.

**What changes:** these runs do not exercise the cold download a user's setup performs. The bytes are those the public
endpoint served earlier today. The model, task, fixture, oracle and scoring are unchanged, and so are the predictions.
Each seeded run carries `warm-cache.json`. The five cells completed before the outage (rung 2 x4, lad-r3-h55-1) ran
cold.
