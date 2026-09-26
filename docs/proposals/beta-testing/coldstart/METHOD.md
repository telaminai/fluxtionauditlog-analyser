# The release checks that run today — method note

`HARNESS.md` beside this file is **retired** and says so. What replaced it for releases is a lighter,
fully automated virgin-LLM check whose runner and evidence live in **git-ignored** folders under
`.local-evidence/coldstart-v2-2026-09-20/`. The runner is reconstructible from any run folder; the
method around it was not written down anywhere. This note is that method.

It describes what is actually done. It does not propose changes, and it is not a protocol to conform
to — where this note and a run folder disagree, the run folder is the evidence.

## What this instrument measures

**The analyser, through a held-out model, over MCP.** Not onboarding routing — that was `HARNESS.md`'s
question and it left with the battery. A run asks whether a model that has never seen this project can
get true answers out of the analyser using only its tools, and whether the analyser ever tells it
something the analyser has not established.

The project the model is given is a **fixture**: acquired, set up and regenerated with a key by the
operator, then stripped of `.git`, `target` and `.fluxtion`. The model performs no acquisition and no
generation. That is deliberate — it is why these runs never need a compilation key — and it is the
line between this instrument and G14, which is exactly the amputated half. See
[../g14/PROTOCOL.md](../g14/PROTOCOL.md).

## Launching an analyser instance

Undocumented until now. One instance per run, isolated by home directory:

```sh
java -Duser.home=<base>/tmp/home -jar <base>/tmp/jar/analyser.jar --rest [log]
```

`<base>` is a per-run directory under `/private/tmp` (e.g. `/private/tmp/fx-f121-s`). The REST endpoint
it writes to `<base>/tmp/home/.fluxtion-analyser/rest-endpoint` carries the port, and `runner.py` reads
the port from there rather than being told it. A second instance started with a different `user.home`
does not see the first. The trailing log argument is omitted for a no-log task.

The subject reaches this instance over **MCP stdio**, not over REST: `runner.py` starts a *second* JVM
with `--mcp` and the same `-Duser.home`. The REST instance is what the operator can inspect while the
run is live; the MCP process is what the model talks to.

## The five steps, in order

1. **Measure the answer key on a throwaway instance.** Start a scratch instance on the same jar, drive
   the task's calls by hand (the REST helper in a run's `helpers/` does this), and record what the tool
   actually returns. Predictions are written from measurement, never from reading the source. Where a
   control jar is in play, measure both and record that they agree — `v121-freshlook` states
   "both jars behave the same over MCP for this task".
2. **Seal the predictions.** `predictions.md` names the jars by sha256, the fixture and its provenance,
   the instance count, the cap, and the ground truth. Then:
   ```sh
   shasum -a 256 predictions.md > seal.txt && date -u +%Y-%m-%dT%H:%M:%SZ >> seal.txt
   ```
   Nothing in `predictions.md` may change afterwards. A prediction edited after a result is not a
   prediction, and the seal is what makes that checkable by someone who was not there.
3. **Launch.** One `runner.py` invocation per cell:
   ```sh
   python3 runner.py <run-name> <model> <base> <input-file>
   ```
   It refuses to rerun a sealed trial (`assert not (archive/'meta.json').exists()`). It writes
   `isolation.sb`, `config.json` (including the jar sha256), `raw.jsonl`, `events.jsonl` with observer
   timestamps from `tools/coldstart_clock.py`, `stderr.log` and `meta.json`.
4. **Score.** `SCORE.md`, written afterwards, against the sealed predictions. The transcript is the
   evidence; a model's own summary of what it did is not.
5. **Check the token.** The trial token is read from disk into the child environment and never printed.
   Confirm after the run that it was the trial token that was spent.

## What the sandbox actually guarantees

`runner.py` writes `isolation.sb` per run. Worth stating plainly, because the guarantees are not obvious
from reading it:

- The subject **cannot read the source tree**. `/Users/greg` is denied wholesale, with narrow
  allowlisted exceptions for the claude binary, its support directory, the JDK and the keychain.
- The subject **cannot see the compilation key**: `(deny file-read-metadata (subpath "/Users/greg/.fluxtion"))`.
  Not merely unreadable — invisible.
- The subject **cannot reach another analyser instance**: every `localhost:*` is denied except the one
  port this run owns.
- The subject has **no built-in tools at all** — `--tools ''` with `--allowedTools mcp__fluxtion-analyser`.
  It cannot read a file, run a command or fetch a URL. Everything it learns, it learns from the analyser.
- Environment is scrubbed of `CLAUDE*`, `ANTHROPIC*`, `FLUXTION*`, `RAPIDAPI*`, `CODEX_*` and the Maven
  and Java option variables before the trial token is set.

General outbound network is *not* denied; only localhost is constrained. For this instrument that is
harmless, because the subject has no tool that can make a request.

## Known drift from the retired HARNESS.md

Recorded so a reviewer is not left to infer whether a difference was a decision or an omission.

| `HARNESS.md` | Today | Deliberate? |
|---|---|---|
| Journal with a `from:` field — "the one thing this measures" | No journal | Yes — measures the instrument, not routing |
| `score_journal.py` | `SCORE.md` + per-run `helpers/` scorer | Yes |
| Operator answers environment questions, never names a trap | No operator; `claude -p`, no interaction | Yes — strictly stronger |
| No source-tree access, by operator discipline | Same, enforced by `sandbox-exec` | Yes — strictly stronger |
| Two capability levels, **three runs each** | **One run per cell** | **No — see below** |
| (nothing) | Predictions sealed by hash before launch | Addition |
| (nothing) | Ground truth measured on throwaway instances | Addition |
| (nothing) | Released control jar beside the candidate | Addition |

The replication gap is the one worth arguing about. `a2-haiku-2026-09-21-r1/r2/r3` honoured the rule;
`v1210-rc-2026-09-26` and `v121-freshlook-2026-09-26` are n = 1 per cell. By the retired protocol's own
standard — "One session is a hypothesis, per H3" — a single run is a hypothesis, and a release verdict
resting on one Haiku run cannot distinguish a real regression from variance. Sealed predictions and a
control jar make these *better-designed* hypotheses than the old battery produced; they do not make them
replicated. Either restore three runs for any cell that gates a release, or state in `SCORE.md` that the
cell is indicative and say what would be checked before it blocked one.

## What survived the retirement

The battery went; the tooling did not. `tools/check_coldstart_corpus.py` and `tools/coldstart_clock.py`
are still load-bearing — the corpus checker runs in `.github/workflows/starter-static.yml`, and
fluxtion-web's `docs/starter-verification-tiers.md` names it as the finished-project command for its
static tier. Do not delete them as part of any cold-start cleanup.
