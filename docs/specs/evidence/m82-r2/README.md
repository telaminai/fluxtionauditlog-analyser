# M82 r2 digest (2026-10-25/26): fixed task, three arms, sealed oracle

Sanitised summary for [spec-authoring-scorecard.md](../../spec-authoring-scorecard.md) §7. Counts and findings only; no
transcripts, paths or key material. The task texts and the sealed oracle are committed alongside (`TASK-A.txt`,
`TASK-BC.txt`, `ORACLE.md`). Their sha256 values, recorded before the first run:

| File | sha256 |
|---|---|
| TASK-A.txt | `418d77b86afbc68281a01e8451bcce9cd3d9b4d24d7ffa53423d5ebb5d338214` |
| TASK-BC.txt | `fcf0057ad70f1e562c250f057681e2b2dd0ec969db84b5670612500258ad8db6` |
| ORACLE.md | `d0d5c23e6ddc7565bcf37b33d61faf22281728d0122aea51c1f8c593c9f8193e` |

(Task files committed without a trailing newline, as sealed. Verify with `shasum -a 256`.)

## Arms and pins (public identifiers only)
- **A**: unguided, from the playground's public URL, no analyser, no preamble.
- **B**: today's live starter bundle (template digest `6d331b04...`) with analyser 1.38.0 and its MCP.
- **C**: the fixed starter bundle (digest `d9535a45...`; web repo branch `fix/starter-analyser-routing` at `3fb07c7`) with an
  analyser built from the branch of the same name at `f92ac394`.
- Models: Sonnet 5.5 and Haiku 5.5, 5 runs each per arm (30 scored runs). Medium effort.
- Rig: sandbox isolation, a cold Maven repository per run, project-scope MCP registration for B and C, the key handled
  implicitly and never read, a key scan and redaction of every archive. The operator pre-opened the project in the analyser.

## Counts per cell (n = 5 each)

| Cell | F1 strict | F2 compiles | F3 runs correctly | Pointer | F5 | MCP reached | Median min | Median acts | Median $ | Consent stops |
|---|---|---|---|---|---|---|---|---|---|---|
| A Sonnet | 0 | 5 | 4 | 0 | 4 | n/a | 3.2 | 25 | 0.33 | 1 |
| A Haiku | 0 | 4 | 4 | 0 | 4 | n/a | 4.5 | 51 | 0.08 | 1 |
| B Sonnet | 5 | 5 | 5 | 5 | 5 | 5 | 4.2 | 39 | 0.64 | 0 |
| B Haiku | 5 | 5 | 5 | 5 | 5 | 5 | 4.8 | 51 | 0.12 | 0 |
| C Sonnet | 5 | 5 | 5 | 5 | 5 | 5 | 3.3 | 33 | 0.47 | 0 |
| C Haiku | 5 | 5 | 5 | 5 | 5 | 5 | 5.3 | 45 | 0.09 | 0 |

Per-run rows were not kept in this digest; the per-cell counts above are what the graded archives yielded. Counts are per
stage as graded, not a nested funnel. Scored runs cost $8.94 in total. VOID runs (not in any denominator): three stopped by
an account 429 (a-h1, b-h1, c-s1, redone) and c-s5 by a port collision between concurrent runs (redone), $0.55 in total;
redone runs ran about an hour after the rest.

Flags and gotos saved: B 9/10, **C 0/10**. Skill tool used: C 0/10 (skills read as files); `load-audit-log` read in 7/10 of C.
Routing misreads of `eventHandlers`: B 1/10, C 0/10. Extra package step needed: B 10/10, C 0/10.

## Top stopping points per arm
- **A (unguided):** (1) Maven Central's outdated builder (`ClassNotFoundException`); (2) a consent stop on the undisclosed
  remote build (1 per model); (3) the analyser not discoverable: all 10 runs took the playground's browser Audit tab for it
  and none left generated source (strict F1 0/10).
- **B (live starter):** (1) a stop-then-restart port race in `run-and-export` (one run, a bind exception); (2) a stale jar,
  needing an extra package step in 10/10; (3) flags were saved in 9/10 (the starter's first-move text does not say to flag the
  proving record; that recommendation came from the C result).
- **C (fixed starter):** (1) no flag and no goto saved (0/10), so no evidence was kept; (2) skills read as files, not loaded
  with the Skill tool; (3) an `analyser_read` call without an anchor errors, and one run grepped generated code for the
  sink field name.

## Observed vs inferred
- **Observed:** every count, median and cost above; the stops; the MCP call logs; zero secrets in any archive (two runs
  checked the existence of the key file only).
- **Inferred:** that the task's "in the analyser" cue explains the jump from r1's 0/5 pointer to 20/20 (the no-cue
  variant N, spec section 5, tests it); that arm A's models mistook the Audit tab for the analyser (from their answers).
- **Limits:** n = 5 per cell; one grader for the pointer and dispatch claims; the operator pre-opened the project in the
  analyser; the redone runs were an hour later than the rest.
