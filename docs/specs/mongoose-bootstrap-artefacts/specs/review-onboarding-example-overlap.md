# Review — overlap with `spec-onboarding-example.md` (M19)

**Verdict:** The new local Mongoose validation should be an M19 conformance exercise, not a parallel
onboarding design. Its staged AI/Mongoose/analyser workflow is compatible with M19 once the constraints
below are made explicit. **Status:** findings recorded 2026-08-28; independent review requested.

## What aligns

| M19 contract | Local validation interpretation |
|---|---|
| A downloaded full Maven project is edited in the user's IDE with their own LLM. | `CLAUDE.md`/`AGENTS.md` direct the IDE agent through a small event/node/test loop; the LLM does not execute business decisions at runtime. |
| The user runs Mongoose, then opens a live audit log in the analyser. | V2/V3 require a local server, a produced artifact, visible analyser evidence and a clean stop. |
| The bundle has a layered agent bootstrap: thin project adapter, local version-pinned guidance snapshot, canonical links. | V0.5 tracks the same layering. The currently detailed root `CLAUDE.md` is a temporary validation record, not the final bundle form. |
| The analyser support loop is Follow, source navigation, graph and Explain; coding remains in the IDE. | V3 requires Follow/click-to-source/a bounded visible answer. V4 MCP is deliberately later and supplements rather than replaces Explain or IDE editing. |
| The bundle contract should be a real cross-repo conformance point. | The `mongoose-local` skill proposal is a development helper that discovers and runs the project contract; it does not put server-control verbs in the analyser or create a second onboarding path. |

## Gaps the downloaded starter currently exposes

These are observations from the copied source/configuration, not claims that the user journey already
works.

1. **No analyser-readable audit artifact:** `config/server-config.yml` has
   `performanceMonitoring.auditCapture.enabled: false`. M19 requires an INFO text-file audit log at a
   predictable `./logs/audit-<name>.yaml` path. Chronicle output is not an acceptable substitute while
   the analyser has no compatible reader.
2. **Input type is unproven:** the fixture is CSV-like text but the only handler accepts `PriceUpdate`.
   The supplier announces unknown events. A value mapper/parser plus normal and malformed-input tests
   are a prerequisite to claiming a live typed flow.
3. **The example has no project profile:** no `.analyser/project.fluxtion-settings` supplies relative
   source roots and the generated EventProcessor FQN, so source navigation is not zero-setup.
4. **The prompt stack is incomplete:** the root `CLAUDE.md` is now present and `AGENTS.md` was added,
   but the version-pinned local orientation/golden-path snapshot under `docs/ai/` has not yet been
   captured. URLs alone are not the bundle contract for an offline/sandboxed agent.
5. **Fresh-JDK claim needs a runnable artifact:** `run-server.sh` builds only when its JAR is absent;
   that Maven generation path requires a subscribed Fluxtion key. The current starter's generated
   processor is ignored. A bundle advertised as “JDK only” therefore must include the generated source
   and/or runnable artifact required by its documented command, or its README must honestly state the
   key prerequisite.
6. **Named output is not yet demonstrated:** YAML declares `data/output.txt`, but the current root node
   only audits its handler input. V1 needs a deterministic output assertion before V2 calls the sink
   path evidence.

## Agreed boundaries

- The analyser is an investigation and evidence surface. It does not autonomously start/stop this
  Mongoose server. A local skill/scripts can operate a developer-owned local process, subject to the
  explicit single-instance and cleanup contract.
- The first audit target is the M19 text/YAML file and project profile. Any later MCP connection shares
  the already-open analyser workspace and is not proof of client approval without a real tool result.
- The proposed shared skill does not belong in every starter until the discovery/start/stop/audit handoff
  contract has been exercised on two projects including a failure case.

## Required amendments now reflected in the local spec/tracker

1. V0.5: add the agent entry point and capture the versioned local guidance snapshot.
2. V3: target the predictable text/YAML audit log, add `.analyser/project.fluxtion-settings`, and prove
   import/auto-detection plus click-to-source against the actual artifact.
3. V4: record MCP as optional and compare it with V3 evidence; retain M19's Explain/IDE edit loop as the
   primary support journey.
4. Before a distribution claim, resolve the no-key/packaged-artifact discrepancy and include generated
   source as M19 requires.

## Independent reviewer checklist

- Does this keep M19 as the one user-facing onboarding contract, with the local project as its bench?
- Is an INFO text/YAML audit file at `./logs/audit-<name>.yaml` still the correct first analyser format
  for the installed Mongoose stack?
- Does the proposed skill stay on the developer/IDE side of the boundary, without expanding analyser
  authority over the server?
- Is the bundle's “fresh machine with only a JDK” promise compatible with the current `run-server.sh`,
  API-key requirement and ignored generated source? If not, which artifact should the download include?
