# M82 handover: the authoring scorecard (for another LLM)

Written 2026-10-26 with spec r3 ([spec-authoring-scorecard.md](../spec-authoring-scorecard.md)). Read the spec first; this
file is the state, the open questions, the next steps and the traps. Evidence digests: [m82-baseline](m82-baseline/README.md),
[m82-r1](m82-r1/README.md), [m82-r2](m82-r2/README.md).

## 1. Current state
- **Spec:** DRAFT r3. No implementation: there is no scoring tool yet. All three trials were graded by hand.
- **Three trials done** (robot strangers, Sonnet 5.5 and Haiku 5.5):
  - baseline (unguided, E2): F3 1/5 on the current release; 4/5 stopped to ask consent;
  - r1 (bootstrapped, confounded by a template that already had the task's shape): E4 5/5 from YAML, analyser pointer 0/5,
    strict F1 0/5;
  - r2 (fixed task, three arms, sealed oracle): A strict F1 0/10 and pointer 0/10; B and C 20/20 at every stage and MCP
    reached 20/20. The "in the analyser" cue in the task text likely explains the jump, so B/C measure instruction-following.
- **r3 spec rules adopted:** strict F1; funnel steps F4a (MCP reached) and F5a (flag persisted); F5 scored against a
  scorer-built before-state; VOID runs and their redo rule; the no-cue variant `N`; acceptance tests S82.11-.18 (each with a
  wrong-result witness).
- **Not yet run:** the no-cue variant; arm C after the routing fixes land; any human/stranger run; E1 (Build door).

## 2. Decided versus open
Nothing in the spec is an accepted owner decision. All of OD-82.1-.10 (spec section 9) are open.

| # | Status | Note |
|---|---|---|
| OD-82.1 separate spec | open (de facto followed) | recommendation: separate, linked from M79 section 12 and M76 |
| **OD-82.2 targets** | **OWNER DECISION, not set** | the spec's numbers are discussion only; no run may set or imply a target |
| OD-82.3 fixed/free arm | open | recommend both; only the fixed arm is oracle-scored |
| OD-82.4 virgin vs own client | open | recommend both, never pooled |
| OD-82.5 key route for SC-B5 | open | no operator-supplied key in an SC-B5-scored run |
| OD-82.6 warm/cold Maven for humans | open | the robot rig uses cold per run |
| OD-82.7 SC-B6 field run | open | written consent; counts only |
| OD-82.8 SC-B4 grading | open | two graders, regex proposes |
| OD-82.9 tooling home and run identity | open | extend the M74 runner with a track B |
| OD-82.10 cadence | open | robot per authoring release candidate |

**Decided by r3 as spec text (still a draft, reviewable):** the rules in section 1 above.

## 3. The r2 re-run's recommendations and where they are tracked
- **Starter (fixed bundle):** add "flag the proving record" to the first-move text; tell the model to load skills with the
  Skill tool, or make every skill body work when read as a file; fix the stop-then-restart port race in `run-and-export`;
  name the sink field in the project's CLAUDE.md. Tracked on branch `fix/starter-analyser-routing` in **both** the analyser
  repo (analyser build, `f92ac394` at the r2 run) and the fluxtion-web repo (starter content, `3fb07c7`).
- **Analyser / MCP bridge:** give `analyser_read` a default anchor or an example (a call without an anchor errors).
  Tracked with the analyser's routing branch above.
- **Skills:** `load-audit-log` was read in only 7/10 C runs and never loaded through the Skill tool.
- **Playground / docs (arm A blockers, unchanged):** Maven Central's outdated builder and no BOM; version numbers that
  conflict; the remote build undisclosed; no discoverable route to the starter or the analyser. Owners are listed in spec
  section 7's findings table (upstream asks and the docs/playground PRs); these are prerequisites for any A-arm gain.
- **M82 itself:** the r3 spec changes.
Check the two branches' current heads before you rely on the r2 pins.

## 4. Exact next steps
1. **Build the scoring tool** per spec section 10 (S82.1-.18): extend the M74 runner with a track B, headless tests over
   recorded fixtures under `tools/fixtures/m82-authoring/`. Write each test's wrong-result witness first (mutation turns it
   red). Inputs per run: the final project tree, the generated processor digest, the MCP call log, the analyser end state
   (flags), the stream's end and the rig log.
2. **Seal** before any new run: task text digests (including the `N` text in spec section 5), the oracle (r2's is reusable
   unchanged) and the routing rubric, in `predictions.md`.
3. **Re-run arm C and the no-cue variant `N`** (B and C setups, n = 5 per model) after the routing fixes land on the two
   branches. Report N's F4a and F4 next to the cued arms; do not pool.
4. Record the cycle as `docs/specs/evidence/m82-<cycle>/` per spec section 7 (digests, no transcripts), then update the
   tracker row and the specs index.
5. Do not set OD-82.2 targets; hand the numbers to the owner.

## 5. The rig, at rebuild level
- **Sandbox isolation:** run the CLI under a macOS sandbox profile that denies reads under the home directory except the
  CLI, the JDK, Maven and the keychain, and explicitly denies the CLI's own skills, projects, plugins, history and settings.
  Scrubbed environment; a fresh project directory per run; a 40-minute cap; medium effort; a tool allow-list with
  `dontAsk`; stream-json capture; an observer clock that logs wall time per act.
- **Cold Maven repository per run:** a fresh local repository and settings file per run, so no run benefits from another's
  downloads (this is what exposes the Central-versus-Repsy choice).
- **Project-scope MCP registration (B and C):** register the analyser's MCP in the run's project directory (the analyser's
  own client helper prints the registration command), run with strict MCP config so only that server exists, and confirm
  it reports connected before the run starts. The analyser runs as a GUI with REST on and the project pre-opened.
- **Key handled implicitly, never read:** the Fluxtion key is picked up by the library from its normal location; the agent
  and the observer never read or print it; the rig counts reads of the key path (target 0; existence checks are allowed and
  recorded).
- **Key scan and redaction:** after each run, scan the whole archive for key material and redact; the baseline found only
  public jar constants (a known false positive). Archives are never committed; only the digests are.
- **Seal first:** oracle and task digests written to a sealed file before the first run.
- **Concurrency:** run cells serially, or give each concurrent run its own analyser port (see pitfalls).

## 6. Pitfalls learned
- **Port collisions between concurrent runs.** Concurrent B/C runs fight over the analyser's default port (8181); one r2 run
  was voided by it, and the starter's own restart has a stop-then-restart race on the same port. Separate ports or serialise.
- **Account rate limits void runs.** Three r2 runs were stopped by a 429 with no subject act after it. Detect it from the
  stream, record VOID, redo after the quota resets, and say the redo ran later.
- **The harness blocks report-file writes.** Trial agents could not write their own reports (nor, in the baseline, /tmp or
  heredocs); the operator transcribed the final message. Plan for that, and count the 1-3 lost acts separately.
- **LLM strangers favour Maven Central** (it is the first place they look) and **stop for consent** on discovering the
  remote build; both are product findings, not rig faults. They are also why arm A's outcome says little about humans.
- **The cue confound:** "in the analyser" in the task text names the tool. Do not read B/C's 20/20 as routing.
- **A starter that already has the task's shape** (r1) makes F3 trivially pass; strict F1 exists to catch it.
- **A model's claim is not completion** (ladder run `lad-r3-s5-1`); score from facts.
- **The raw archives hold machine paths and transcripts.** Rule 1 forbids committing them; commit digests only, and run the
  sweep.
