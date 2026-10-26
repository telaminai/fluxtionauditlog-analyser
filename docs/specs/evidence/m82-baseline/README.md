# M82 baseline digest (2026-10-25): unguided stranger authoring from the playground

Sanitised summary of one robot-stranger trial, for [spec-authoring-scorecard.md](../../spec-authoring-scorecard.md) §7.
Counts and findings only: no transcripts, no machine paths, no key material. Raw per-run archives stay with the operator.

## Method
- Entry E2: the public playground's start page. No analyser, no MCP. Task: a running average trade price per symbol, an
  alert when a price is more than 2% from it, run on samples, show evidence, explain one event (the task of this trial,
  not the later fixed task).
- Rig: adapted from the analyser's G14 runner: sandbox, scrubbed environment, a cold Maven repository and settings per
  run, project-only settings, strict MCP config, a tool allow-list, stream capture, an observer clock, a key-leak scan
  and a redaction pass. The CLI login used the real home with skills, projects, plugins, history and settings denied, so
  this is not the spec's §4 isolation.
- Runs: Sonnet 5.5 x5 with a key (s1-s5), Haiku 5.5 x1 with a key (h1), Sonnet x1 with no key (k1). Medium effort,
  40-minute cap. The key was used implicitly and never read; every archive scans clean after redaction.

## Per-run counts

Endpoints: E1 buildable project, E2 compiles, E3 runs correctly, E4 explains one record, E5 dispatch claims correct.

| Run | Min | Acts | $ | Dead ends | Version reached | E1 | E2 | E3 | E4 | E5 | End |
|---|---|---|---|---|---|---|---|---|---|---|---|
| s1 | 3.0 | 29 | 0.38 | 3 | 1.0.9 (Central) | yes | yes | no | no | yes | stopped to ask consent |
| s2 | 2.8 | 17 | 0.25 | 1 | 1.0.80 (Repsy) | yes | yes | yes | partial | yes | finished |
| s3 | 3.1 | 29 | 0.31 | 3 | 1.0.9 | yes | yes | no | no | yes | stopped to ask consent |
| s4 | 2.2 | 22 | 0.26 | 2 | 1.0.9 | yes | yes | no | no | n/a | stopped to ask consent |
| s5 | 3.4 | 29 | 0.42 | 3 | 1.0.9, then 0.9.27 | yes | yes | no | no | yes | stopped to ask consent |
| h1 | 3.8 | 38 | 0.04 | 3 | 1.0.9, then 0.9.17 | yes | yes | yes (old stack) | partial | yes | obsolete version |
| k1 | 5.9 | 48 | 0.65 | 4 | 1.0.80, 1.0.9, then a legacy artifact | yes | yes | yes (legacy) | yes | yes | legacy stack |

Funnel on the current release (Sonnet, key, n=5): E1 5/5, E2 5/5, **E3 1/5**, E4 0/5 (1 partial). The dead ends are the
trial's own count, excluding 1-3 acts per run lost to the rig's sandbox.

## Top stopping points
1. Maven Central serves an outdated `fluxtion-builder` (1.0.9) and no BOM; against today's hosted generator it fails with
   a `ClassNotFoundException` that reads as a classpath bug. 6 of 7 runs went to Central.
2. Conflicting version numbers across README, quickstart header and examples (two do not exist). The one success found
   the README's repository line.
3. The remote build is undisclosed: 4 of 5 Sonnet runs stopped to ask consent on discovering that building posts the graph
   to a hosted service.
4. No route from the playground into a project an agent can follow; `llms.txt` exists but nothing links to it. 0 of 7 runs
   found it, the starter, the contract, the analyser or the MCP.
5. The audit hint (FLX-1008, no event audit configured) is dismissed and does not say how to turn audit on.

## Observed vs inferred
- Observed: the messages, repository listings, page links, the keyless run's 401, every count above.
- Inferred: that the keyed 1.0.9 calls carried a key; that human strangers would hit stops 1 and 2 at a similar rate.
- Limits: n=5 detects only large effects; one grader; endpoints graded by hand, not by the spec's tooling.
