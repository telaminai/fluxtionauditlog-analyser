# G14 — the acceptance run from a real download

G14 is the tracker's last release-evidence gate: *"the acceptance run from a real download"*
(`docs/specs/tracker.md`). It is evidence about **what a user receives**, which is why the tracker
refuses branch-built substitutes for it — *"branch evidence, not evidence of what a user receives"* —
and why `docs/starter-verification-tiers.md` in fluxtion-web requires a preregistered,
acquisition-only trial with a fresh client and no coaching.

This folder implements it **on top of the virgin-LLM rig** described in
[../coldstart/METHOD.md](../coldstart/METHOD.md), rather than as a second apparatus.

## Why this is an extension, not a new harness

The release checks already run a held-out model against an isolated released analyser, sandboxed, with
sealed predictions, observer timing and a full transcript. G14 needs all of that. What it does not have
is the front half of the journey: the virgin-LLM fixture is a project the **operator** acquired, set up
and generated with a key, then stripped.

Those amputated steps are G14's entire content.

| | acquisition + keyed generation | canvas / report |
|---|---|---|
| virgin-LLM release check | operator does it, before the run | subject does it |
| **G14** | **subject does it, from the public download** | subject does it |

So `tools/g14_runner.py` is `runner.py` with the fixture removed and four capabilities added. Everything
else — one-shot sealing, `ObserverClock` timestamps, `raw.jsonl`/`events.jsonl`, the per-run isolation
profile, the scrubbed environment, the trial token — is kept identical on purpose, so a reviewer who
knows one harness knows both.

## The four differences, and why each is forced

**1. The subject needs real tools.** It has to download a ZIP, unpack it, run `./setup.sh`,
`./generate.sh` and the launcher. `--tools ''` makes that impossible, so G14 allows the built-in tools
alongside the analyser MCP server. This is the largest reduction in containment and the reason the
remaining controls matter more here than in a cold-start run.

**2. The subject needs the public internet.** Acquisition is the point, and the build resolves from
Maven Central and Repsy and calls the cloud generator. Outbound is therefore open, with localhost still
narrowed to this run's analyser port so a second instance cannot be reached.

**3. The subject needs the compilation key** — the only trial that does. The tracker states it as a
restriction, *"Only G14 may use the owner's compilation key"*, and the virgin-LLM profile enforces the
opposite with `(deny file-read-metadata (subpath "/Users/greg/.fluxtion"))`. G14 narrows rather than
lifts it: the key **file** is readable because `./generate.sh` reads it; the rest of `~/.fluxtion`
stays invisible.

**4. The cap is longer.** The interrupted 1.0.74 attempt ran 869 s and never reached a chart, so a 600 s
cap would end a G14 run before the part that failed last time. Default 1800 s, recorded in `meta.json`.

## The control that replaces the one we gave up

Granting the key re-opens a failure this project has already observed: on the released bundle, **2 of 6
Sonnet sessions ran `cat ~/.fluxtion/fluxtion.apiKeyFile`** and put the key in their transcripts. The
bundle's key-hygiene lines were written for exactly that, and G14 is the one trial where a subject can
still do it.

`g14_runner.py` therefore ends every run with a **key-leak scan**: it reads the key, searches
`raw.jsonl`, `events.jsonl` and `stderr.log` for the secret and for `cat`-shaped reads of the key path,
and writes `keyscan.json` with counts and a verdict. It never prints, logs or stores the value. A
non-zero leak count is a **hard fail of the run** — the run is preserved as evidence of the leak and
the trial is not scored as an acceptance.

Rotate the key if a scan fails. The transcript is written to disk before the scan runs, so a leak is on
disk by the time it is detected; the scan tells you to rotate, it does not prevent the leak.

## Running it

Preflight first. It spends no key and launches no subject — it only checks that the environment can
support a run:

```sh
python3 tools/g14_runner.py --preflight --base /private/tmp/fx-g14-s
```

It verifies the analyser jar and its sha256, the public scaffold URL, the presence (never the content)
of the key file and the trial token, and `sandbox-exec`, `claude`, `java` and `mvn`. It writes
`preflight.json`. Run it on the day of the trial, against the artefact the trial will use.

Then the five steps from METHOD.md apply unchanged — measure, seal, launch, score, check the token —
with two additions specific to G14:

- **Preserve a pristine download.** The operator fetches the public ZIP independently and records its
  digest *before* the subject runs, so the subject's tree can be compared against what was published.
  `--preflight` records the digest it sees; the run records the digest the subject actually obtained.
- **The task prompt must not name a file.** The subject is told what it wants, not how the project is
  laid out. Naming `setup.sh` would hand over the routing the gate is measuring.

```sh
python3 tools/g14_runner.py <run-name> <model> <base> <input-file>
```

## What a pass requires

All four, and the tracker's wording is deliberate — a run that *executes* is not a pass:

1. **Acquisition** — the subject obtained the published download itself, and its digest matches the
   pristine copy the operator preserved.
2. **Generation and run** — from that download, with the key, producing a processor and a run.
3. **Canvas** — a chart or report made from actually logged values in the isolated analyser. This is
   the step the 1.0.74 attempt never reached.
4. **Clean scans** — `keyscan.json` clean, and no claim in the subject's report that the transcript
   does not support.

An assisted run is recorded as assisted, never as passed. An interrupted run is preserved and reported
as interrupted; the supervised recovery of 2026-09-24 is the precedent, and it was explicitly *not*
counted as an acceptance.

## Implemented, not scheduled

This harness is implemented and its controls are tested; **it does not schedule G14**. Two things must
be settled by a person before an attempt, and neither is a code change.

**M68 — one condition of its own answer gates this gate.** The owner answered Q4 on 2026-09-26: M68
ships as an **explicit partial delivery**, because D-E9's producer half belongs to the Mongoose audit
format work and M68 can therefore never be complete in this repository. That answer carries four
conditions, and the fourth is addressed to G14:

> **charts are marked before G14 runs** — the charts must carry the file-identity mark (**M68.7**),
> because G14's pass condition lands on that exact surface: an unmarked chart beside a marked table
> would let G14 pass on content the session already knows is superseded.

So M68 shipping does not by itself release G14. **M68.7 is the tracker's next M68 item and is this
gate's prerequisite.** The detail pane's mark is part of M68.7 too but explicitly does not gate G14,
because a G14 subject reaches its evidence through the verb path and charts. Confirm M68.7's chart
marking has landed before sealing predictions — an acceptance run through an instrument that can still
present superseded content as current produces evidence about the instrument, not about the download.

**Ordering.** The tracker puts G14 fifth, after the new-node stub policy (BETA-B2), SG-2's hosted
acceptance, the jars, and the reconciler follow-ups. M68 removes one blocker, not the queue.

Write a fresh `predictions.md` from `predictions-template.md` for each attempt, against the
then-current published download. The 2026-09-24 sealed predictions are scoped to environment recovery
and cannot be reused.

## For an independent reviewer

What to check, and where the weak points are. These are stated because a reviewer who has to find them
unaided will spend the effort there instead of on the design.

- **The containment trade is the whole argument.** G14 grants built-in tools, outbound network and the
  key. Judge whether `key_leak_scan` plus a narrowed profile is adequate compensation, and whether the
  profile in `isolation_profile()` actually denies what this file claims it denies. The claims to test:
  the source tree unreadable, other analyser instances unreachable, `~/.fluxtion` invisible apart from
  the key file itself.
- **The scan is detective, not preventive.** The transcript reaches disk before the scan runs, so a
  leak is already written when it is found. The verdict tells you to rotate. If that is judged
  insufficient, the alternative is a broker that injects the key without exposing the file, which is
  not implemented here.
- **`keyPathReads` is a regex over transcript text.** It catches the observed shape
  (`cat ~/.fluxtion/fluxtion.apiKeyFile`) and near neighbours. It will not catch an obfuscated read.
  It is a hygiene check, not an exfiltration defence.
- **Tested and untested.** `tools/test_g14_runner.py` covers the scan: both leak shapes, every output
  file, the short-value false-positive guard, the vacuous-when-absent case, and that the verdict never
  contains the secret. `--preflight` has been exercised end to end. **`run_trial` has never been run** —
  it needs the key and a staged analyser instance, so it is reviewed code, not exercised code. Treat
  the first execution as part of the attempt and supervise it.
- **One run is a hypothesis.** METHOD.md records that the current release checks are n = 1 per cell
  against the retired protocol's "three runs each". G14 is a single acceptance by construction, which
  is defensible for a gate about one published artefact — but say so in `SCORE.md` rather than letting
  it read as a replicated result.
- **What this harness cannot establish.** That the download is *good*, only that a fresh client can get
  from it to a verified result. Correctness of the generated graph is the compiler's gates, not this.
