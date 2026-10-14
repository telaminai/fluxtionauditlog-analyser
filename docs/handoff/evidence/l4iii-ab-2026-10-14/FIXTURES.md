# Fixtures: M72 L4 (iii) acceptance A/B (2026-10-14)

Written before the first run. Every digest below is also in `MANIFEST-AB.sha256`.

## Downloads (both arms)

All three were fetched at `2026-10-14T00:14:11Z` from the public endpoint. The scaffold URLs and tokens are the
earlier ladders'.

| Download | Bytes | sha256 |
|---|---|---|
| Rung 2 Spring starter | 37154 | `4d82884a078d7ed991a0ee60bf3e3dd1db88d9d2dabd38b564aa9b454db42e1e` |
| Rung 4 Spring starter | 34098 | `b55d359ae07427fe0849296b89ca27270f84d37c5d700d4093a44e5d1a6fe9ca` |
| Analyser bundle | 118138 | `1335a17884a45f3767e72a6b442a708c824d919c82ee7155529965a7dbf643a7` |

**Versions:** unchanged from the release ladder. starter-core and BOM are 1.0.79, runtime is 1.1.1, and every
regeneration stamps hosted generator 1.0.79.

**Since the release downloads (2026-10-13 20:40Z),** only these changed: the scripts (`setup.sh`, `validate.sh`,
`generate.sh`, `check-fluxtion-key.sh`, and in the bundle `run-and-export.sh`, `run-server.sh`, `export-audit.sh`
and `stop-server.sh`), plus the bundle's `CLAUDE.md` and `AGENTS.md`. Java sources, poms, design XML and skills are
byte-identical.

**fluxtion-web #17 is live in both arms.**
- The bundle's `run-and-export.sh` carries #17's verdict ("… did not process its feed cleanly"). The as-shipped
  run printed `events: marketProcessor processed 5 of 5 feed rows`, then `done: exports written; server stopped
  cleanly`, and named no log on success.
- The served bundle matches `web/src/lib/starter/bundle.ts` at fluxtion-web `d01b95f3`. Its added line is in the
  served script, and its removed text is not. That file is identical at `fc3ee0ee` (the merge of PR #17,
  2026-10-14T00:10:52Z), which is fluxtion-web `main`.
- The #17 marker is absent at `8de05be9` (PR #16), so the deployment is `d01b95f3`/`fc3ee0ee` or later.

## Control fixtures (`r<n>/fixture`)

These are today's downloads plus each rung's own modification only, as in the release ladder: the r2 `childTypo`
edit (its XML is byte-identical to the earlier ladders' r2 XML), and r5's `supplier-repo/` and `data/fx.csv`. r3 and
r4 are as downloaded.

| Rung | Control tree sha256 | Treatment tree sha256 |
|---|---|---|
| 2 | `860d4dd1de1336f91dd537ba31b2eab4ed50f3b1bd502e3b6c1b1707ff88e26c` | `33c8e3be87fb75a369f2f39824f5c394fea336e853dd8941f736e811035d19cc` |
| 3 | `3888cbb19a54b077d3239b1fbd9f7ef3b65436bd9c08c58c0049bd541cd8e636` | `735ee5bb629fb06d092ec2e3a52e29f4864697a50305bb0766d1a5b66736dba8` |
| 4 | `fcf6cfc8d2992000d3cdb18c317ec54fd0663dfa472c812c27fdcb7bdaa5a9b8` | `5c7cfa2ae9898ffb800f3fa1f655bd4ba14330691ec6154520163976440ad1aa` |
| 5 | `c7dfcfc39ce04326f38d3ef9b40a054466245ea7dd3dbde66d15fac5cb0b258e` | `e10a9843c8f38efdcfccbfb07f34031b3bd9cf0aa5eb2b459261d7cc413678d0` |

**What both arms already vendor:** the bundles vendor `load-audit-log` **without** the fresh-context section, and
`add-a-node` at `e78e50a6`, without #139's nodeBeans clause. Both are the same in both arms.

## Treatment fixtures (`r<n>/fixture-t`): the control plus only the fresh-context guidance

The canonical source is analyser `docs/skills/common/load-audit-log/SKILL.md` on `main` (`e3786f68`), sha256
`c5b7d90867adfee693cfdf049bd3588d8618eb252981db08135d6c85fce59c62`. That equals the m19-skills/2 `index.json`
entry. `harness/build_treatment.py` builds both arms' differences and checks them; its output is
`checks/treatment-patch.json`, with `ok: true`.

**Bundle rungs (3, 5).** The vendored skill (`03394266…`) gains exactly two hunks of canonical text, byte-identical:
1. the step-1 routing paragraph ("If this conversation designed or changed the graph … A single check stays here.");
2. the `## Diagnose in a fresh context` section (2,201 characters, sha256 `0d47a95f…`).

Checks:
- **Patched file:** `9d3901bbdc6b…`, the same for r3 and r5; it is `treatment/bundle-load-audit-log-SKILL.md`.
- **Against vendored:** it differs only by insertions, 37 lines, whose text is exactly the two hunks
  (`treatment/bundle-vs-vendored.diff`).
- **Against canonical:** it differs in exactly 2 hunks, both kept from the vendored file
  (`treatment/bundle-vs-canonical.diff`):
  - the bundle's substituted export command and paths, where canonical has `TODO(bundle): …`;
  - the vendored 5-line "Do not use `analyser_context` …" paragraph, where canonical has 2 lines.

**Deviation from the spec's literal wording.** The spec says the treatment "replaces
`.claude/skills/load-audit-log/SKILL.md` with this revision's". A wholesale replacement would have put an
unsubstituted `TODO(bundle)` placeholder into the treatment's skill and shortened a second paragraph, and neither is
the lever. The coordinator agreed before sealing (2026-10-14) to patch only the routing paragraph and the section, so
that the arms differ by the lever alone.

**Starter rungs (2, 4), as the spec states:**
1. `runbooks/analyser.md`: the section, from `## Diagnose in a fresh context` up to the next heading, is appended
   byte-identical (`sectionByteIdentical: true`). The sha256 goes from `0805f60ded8d…` (control) to `e298fd890765…`,
   the same for r2 and r4.
2. `RUNBOOK.md` step 5: after "Then run ./run.sh." it inserts "If checking the change needs more than one analyser
   read after opening the log, follow *Diagnose in a fresh context* in runbooks/analyser.md." The sha256 goes
   from `6d180982faa4…` to `637a5502f231…`, the same for r2 and r4.

The full before/after digests are in `checks/treatment-patch.json`, and the diff is
`treatment/starter-vs-control.diff`. `diff -rq fixture fixture-t` lists only these files for every rung.

## Tasks, oracles and scorer

`TASK-R2…R5`, the four oracles, `score_ladder.py` and `probe_levers.py` are the release ladder's. `r5_oracle.py`
resolves `FIXTURE` to `r5/fixture`, the control. I3 compares only `src/main/java` and `supplier-repo/`, which are
identical in the two arms. `BASELINE` resolves to `r5/baseline/`: today's as-shipped bundle run, whose business keys
equal the release baseline's.

## Ground truth and controls (on today's control fixtures, before sealing)

The release ladder's ground truths were ported (`harness/gt_port.sh`) and regenerated with the shipped
`./generate.sh`. All four stamp generator 1.0.79.

| Rung | Ground truth VALID, ORACLE | No-op control ORACLE |
|---|---|---|
| 2 | PASS, PASS | unsolved: VALID fail, ORACLE fail |
| 3 | PASS, PASS | as shipped: fail |
| 4 | PASS, PASS | unsolved: fail at step 1 |
| 5 | PASS, PASS | unintegrated: fail (I1) |

The verdicts are in `checks/gt-r*.json` and `checks/ctl-*.json`.

## Warm caches

Both seeds are the release ladder's (cold-filled and provenance-checked on 2026-10-13 and frozen at 20:49:17Z).
They are unchanged:
- `seed-spring`: `b3f23c40aeb9e04662661b00f8f9c54827c91fec7fc0cbeded8903703b41c3de`;
- `seed-bundle`: `a8ffd4903f591ca73b25430881c8c3846a4a8f0f7516fcd4bb1354286ae44e9c`.

They still suffice, because the dependencies are unchanged. `./setup.sh -o` succeeds on all 8 fixtures, and r2's
validate fails, as intended, on the typo (`checks/sufficiency.txt`).
