# Round 01 — 2026-08-30

## Environment (a run whose conditions are unknown proves nothing)

| | |
|---|---|
| Bundle | `fluxtion-spring-mongoose-keyfree.zip`, `fluxtion-web@m19/p3-artifacts` |
| Integrity | SHA-256 verified against the artefact's own `SHA256SUMS` |
| Doc set under test | **the bundle's own** — this round is the baseline, before any edit here |
| Fluxtion API key | **PRESENT** — so the regenerate path was exercised and the **keyless claim was not** |
| Analyser reachable | **NO** — no MCP tools in the agent's session |
| Agent | fresh context, general-purpose; session-free, **not** knowledge-free |

## Task

> add a node that flags a price above a threshold, run the project, and show me evidence from the audit
> log that it fired

## Outcome: TASK SUCCEEDED — and that is the least informative part

Node added, design IR edited, processor regenerated, server run, audit evidence produced. The mechanism
works end to end. Per the process rules a pass is a **non-event**; what follows is the output.

## Findings, ranked

**R1-A · The bundle ships two example nodes that teach contradictory things.** `RootNode` extends
`EventLogNode`; `RiskCheck` does not. Nothing states that extending it is what supplies `auditLog`.
Copying the wrong shipped example yields a node that runs and **leaves no evidence** — silent failure, and
precisely the class of defect the audit log exists to prevent. This is the same undocumented contract that
made M40.2b's own tracker premise wrong; it has now cost a second author from the opposite direction.

**R1-B · The documented lifecycle cannot be executed as written.** README, `AGENTS.md` and the
`run-mongoose-server` skill all present `run-server.sh` / `export-audit.sh` / `stop-server.sh` as a
sequence in one block. `run-server.sh` ends in `exec java … -jar` and blocks forever. Taken literally,
**step 2 is unreachable.** Nothing says to background it.

**R1-C · Registry publish-before-ready, and the recovery advice is the thing that fails.** `run-server.sh`
writes `~/.mongoose/servers/<name>` *before* the server is up and does not remove it when boot fails — so
a failed boot overwrites a **live** server's entry with a dead pid. The skill's rule *"the published
registry entry is the authority"* is then exactly backwards, and `stop-server.sh` exits **0** while the
real server holds the port. Recovery required `lsof`/`kill`, host tools the scripts are written to avoid
needing. NOTE: this is a *different writer* from mongoose-plugins' `ServerRegistryFile`, which publishes
on service start and removes on stop correctly — so one file may have two writers.

**R1-D · `load-audit-log` has no fallback without the analyser.** Its entire procedure is MCP calls. With
no analyser reachable the agent read the YAML with `grep`, and the pairing check the skill demands
*before drawing any conclusion* could not be performed at all.

**R1-E · Dispatch order is invisible to a reader.** The agent saw its node sorted between the two shipped
ones in `nodeLogs` and recorded *"nothing explains what determines that order… I do not know whether it is
meaningful."* It is meaningful — it is dispatch order, the property the whole product rests on. A
competent fresh author could not discover that from a real export.

**R1-F · Audit level is unrecoverable from the export.** `info` and `warn` land in the same flat map with
no level marker, so severity cannot be recovered by a reader.

**R1-G · Smaller, all COULD-NOT-FIND:** whether a scalar `constructor-arg` is legal on a graph node;
what `@OnTrigger`'s boolean return means; whether a bean must also be listed in `nodeBeans`; why the
generated processor exists in two places and which is authoritative; how to tell the server is ready.

## What the agent never opened

Not measurable this round — the baseline doc set was the bundle's own and no usage was instrumented.
**Process gap: later rounds must record this**, or the pruning rule has nothing to act on.

## Side effect worth recording

The agent killed a pre-existing server (pid 9319) to free port 8181 — a direct consequence of R1-C.
Runs must be isolated from anything the owner is running.


## Changes made for round 02 (in `current/`)

| Finding | Change | Root cause or symptom? |
|---|---|---|
| R1-A | `CLAUDE.md` rule 1 states the `EventLogNode` contract and **names the two shipped examples as deliberately different** | root |
| R1-B | `run-mongoose-server` leads with "the first command BLOCKS" and backgrounds it | root |
| R1-C | same skill: **trust the port, not the registry, when they disagree**, with the publish-before-ready reason | symptom — the script defect remains and is an upstream fix |
| R1-D | `read-audit-log` now leads with the **no-analyser** route; MCP is the second half | root |
| R1-E | dispatch order stated in **both** `CLAUDE.md` (rule 3) and the read skill | root |
| R1-F | severity absence stated where a reader meets it | root |
| R1-G | scalar `constructor-arg`, `@OnTrigger` return, `nodeBeans` requirement, the two generated copies — all in `CLAUDE.md` | root |

Also **split** the old `run-mongoose-server` skill: the key preflight moved into a separate `regenerate`
skill, so running never mentions a key and regenerating always does.

**Size:** three skills + one bootstrap doc. Round 02 must record what goes unused so this can shrink.
