# Decisions (resolved)

Moved verbatim from the live tracker on 2026-10-01. Cited as *tracker ▸ Decisions*; this file is that section.

- **The LLM operates the application toolchain through documentation and runbooks; the analyser
  renders evidence** _(owner, 2026-09-19, staged-feedback review)_. Bootstrap the LLM's understanding
  of Fluxtion, the analyser, audit logs, Mongoose and Mongoose plugins, then point it to project/task
  runbooks. Application processing, generation, hosting, replay and domain validation execute in their
  owning tools/application/test harness, driven externally by the LLM or developer. The analyser must
  not acquire application execution or orchestration logic, interpret runbooks as commands, or become
  another implementation of domain behaviour. It retains log queries, reductions, scoped comparisons,
  provenance/freshness checks and shared presentation of externally produced results. A validation
  pack is portable documentation, test assets and evidence, not an analyser-run workflow. This narrows
  the fourth feedback addendum's invariant/validation proposals; it authorizes no new execution verb.
- **This block ships as 1.14.0, together — NO 1.13.3, NO cherry-pick** _(owner, 2026-09-17, after the re-review)_.
  The `open {analysis}` regression released in 1.13.0–1.13.2 (M46.10) is therefore **fixed in 1.14.0, not patched**:
  the second reader recommended cutting 1.13.3 from the fix commit first (A4); the owner declined, and A4 is closed
  as decided, not open. One consequence, stated: the commit that fixes it also REMOVES the `nodes` reply key
  (M46 A3), so that removal is a 1.14.0 change and is announced as one — deliberate, no alias.
- **The merge of `fix/m46-agent-api-closure` does NOT wait for M64.9; the 1.14.0 RELEASE does** _(owner, same day)_.
  M64.8 (the runbook that points) and M64.9 (`coverage` → `topology:verdict`) are ONE skill edit and ONE re-pin,
  on main, after the merge and before that release — so the first release of `spotlight` teaches the intended
  target name and the skills index moves once.
- **The built-jar harnesses are on the pre-release checklist** _(owner, same day)_:
  `tools/capture-conversations.py` and the three `tools/verify-*.py`, in `docs/admin/release-process.md` §4.0, with
  the reason they are there (four reviews, three releases, one path nobody drove). `.mcp.json` is git-ignored.

- **REVERSED the same day — `handoff` is NOT a verb; the shared canvas is written through `open`** _(owner,
  2026-09-17, after the second-reader review's A6)_. `open {posture}`, `open {record}` and
  `open {close: "handoff"}`, mirroring `open {close: "project"}`; reading stays `context.handoff`. **Why the
  earlier decision (below) was wrong:** it argued the canvas write is "not a lifecycle act", so it did not belong
  on `open`. But `open` does not mean "a lifecycle act" — it means **put this in force**, which is what setting a
  posture or placing the selector's record does, and it already carries the one idiom for taking something out of
  force. The verb-ness of M48.7 was two lines of wiring; everything of substance (`CanvasHandoff`'s typed,
  attributed, fail-closed state, `context.handoff`, the Project-panel row) is unchanged. Unreleased, so this was
  the cheapest it would ever be; after a release it would have been a compatibility decision. The surface is
  FIFTEEN: the fourteen, plus `spotlight` (M64) — the one concept no existing verb named. **(True when written; the
  surface became SIXTEEN in 1.16.0, when M66 added `source`. This decision is about `handoff` and stands as recorded.)** One consequence is
  stated rather than hidden: `open`'s other forms name what they ignored, but a canvas write is REFUSED when
  combined with anything else, because shared state may not be half applied.
  _Superseded, kept for the record:_ ~~`handoff` is the fifteenth verb (owner, 2026-09-17: "keep the 15th verb,
  handoff is fine") — the canvas's posture and record are not a lifecycle act, and `context` must stay read-only
  because M42's loopback probe depends on it.~~ The `context`-stays-read-only half still holds. The rule the four
  guard tests state also still holds, and is the rule this reversal applies: **the surface does not grow for a
  concept an existing verb already names**; the NEXT verb still has to clear that bar.
- **A close supersedes a pending open of the same kind** _(owner, 2026-09-17; M44.3b)_. Close log, close all and
  Reset take the id of an outstanding log open, so its late result is refused; closing the graph alone does not,
  and a close with nothing outstanding changes nothing in the gate. It is D-A3 — the last deliberate request
  wins — applied to one more request, not a new rule.
- **Component resolution and Spring manipulation live in the single existing `fluxtion-builder` jar**
  _(owner, 2026-09-03)_. The builder owns catalogue generation, the typed resolution result and a small
  parser/canonical writer for the supported Fluxtion Spring authoring subset. It adds no transitive
  Spring dependency. The Maven plugin is an invocation surface, not a second implementation; the
  starter remains the browser editor and consumes the shared document contract and conformance corpus.
  Builder main remains Java 8 compatible; the JDK parser is not a browser surface, and a CheerpJ smoke
  test guards against breaking the jar that the playground loads. `fluxtion-runtime` is untouched.
  Canonical production spec:
  [`spec-builder-component-resolution.md`](../proposals/upstream-specs/spec-builder-component-resolution.md).

- **The commercial model, and where the analyser sits in it** _(owner, 2026-09-01)_. Recorded because it
  bears on **D-S1.2**, the open licence choice that blocks a release, and because the reasoning would
  otherwise be lost in conversation. **Owner's model:** the compiler is priced (hosted, or **hosted
  on-prem for enterprises**, which removes generation friction while keeping enforcement); the analyser is
  **per seat per month**; **redistribution/deployment licences are required for the generated event
  processor and for Mongoose**; and auditors are a pricing dimension.
  - **The deciding argument is redistribution, and it settles the shape.** *"One generation deployed in a
    million drones."* Generation is a one-time act; the artefact replicates without limit. Pricing only
    generation sells a million-unit fleet for the price of one build, so value capture has to sit at
    deployment. That is enforced by contract, procurement and audit rights rather than by code — the
    generated processor is committed Java in the customer's tree, with no wire to gate — which is a normal
    enterprise model but a **different competence** from the rest of the stack, and should be resourced
    deliberately rather than discovered at the first renewal.
  - **Consequence the owner's own model implies: the compiler gate is then a tax on the funnel.** If the
    revenue is at deployment, the drone customer pays regardless of what generation cost, while a public
    repo or a thirty-node experiment never deploys anything and so collects nothing — the gate only
    suppresses the reading and trying that feed the deployments. The narrow ask that survives is
    **generation free for public repositories**, on evidence grounds rather than price sensitivity: an
    open Fluxtion project is the only place a prospect can read a real graph, its annotations and its
    generated dispatch before spending. **This repository is the case in point** — anyone can clone,
    build, test and fix the analyser, and nobody without a key can add a node to the one part that
    demonstrates the thesis.
  - **Withdrawn after owner pushback: "free at entry is required".** That was reasoning from the
    open-source-tooling era. Developers now pay per seat and per token for tooling as a matter of course,
    so entry friction is a far weaker objection than it was, and on-prem hosting removes it for the buyer
    who matters. The public-repo carve-out above is argued from *evidence access*, not from price.
  - **The one part still argued against: charging per auditor.** Two reasons, the first specific to a
    known framework constraint. **(1)** Invocation tracing is fixed at generation time (UP-FLX-37 /
    `fluxtion#25`) — so a customer who wants more audit detail mid-incident must regenerate and redeploy.
    Metering that means metering the thing they most need at the moment they can least obtain it, and the
    incident where the product could not help is the story that travels. **(2)** Auditors are how the
    audit log exists, and the audit log is what creates demand for analyser seats — the razor, not the
    blade. Most graphs need exactly one auditor, so the revenue is small while the incentive it creates
    ("use fewer auditors") points against *audit everything, always*, which is the strongest claim in the
    product. **Node count is a cleaner complexity proxy** and tracks the measured value curve (≈0 glitch
    sites under 10 nodes, ≈0.1/node above 50) without discouraging anything.
  - **Per-seat for the analyser is right and needs no metering** — its value grows with graph size and
    incident count, which seats already track.
  - **One trade to state publicly rather than discover:** an on-prem compiler loses the "the generator
    improves without every project upgrading a plugin" property that hosted generation provides.

- **A processor with no source keeps offering "Add source", whatever the cause** _(owner, 2026-08-30;
  re-affirming 2026-08-27)_. The live v4 bundle raised a case the original decision may not have had in
  view — a declared processor class, no generated source shipped, and `src/main/java` already configured,
  so adding a root cannot help. **The owner's answer is to keep the button as it is.** The Project panel's
  *wording* still distinguishes the two causes, which is where the correction belongs: one remedy button
  that is occasionally unhelpful beats a row whose control changes shape depending on why something is
  missing. `ProjectModelTest` records the target as deliberate so a later reader does not "fix" it.
- **API key at rest:** stored **cleartext** in `~/.fluxtion-analyser/config`.
- **Display time zone:** **UTC** for all date/time rendering.
- **EventProcessor:** **infer when possible** by scoring candidate processors' `instanceId→field`
  sets against the log's observed instanceIds; fall back to configured/default
  `DemoMarketMakerStrategy` (user can override). Implemented in M4 (needs source parsing).
- **Server control is not an assistant capability** (spec-closed-loop §B.5) — server verbs never
  appear on the action socket; any future agent-initiated server action requires per-action human
  approval. Keeps the FAQ's security guarantee simple and true.
- **Agent fixes arrive as evidence-linked PRs on a branch, never direct edits** (spec-closed-loop
  §A.4) — the M12 guardrail, embedded verbatim in every generated brief.
- **O5 RESOLVED (2026-08-15) — the analyser and `svc-admin-web` complement, and the overlap is
  deliberate.** The analyser is a **dev tool _and_ a production-support tool**; web-admin views **one
  live server** whose log may be rolled or deleted, and suits dev + MCP-driven poking. A low-latency
  production system may have **no admin-web and no MCP at all** — logs are transported to a shared store,
  and **offline analysis across many files is where the analyser shines**. So the topology view and event
  step-through are **replicated into the analyser** (**M21**), because the good view has to exist where
  the logs actually land. Consequence: the analyser needs the **GraphML**, sourced from a file first and
  the server only when one happens to be there.
- **Distribution is the shaded fatjar + JBang; no native bundles** _(2026-08-27)_ — `jbang app install analyser@…`
  is the one-command install (JBang supplies the JDK), `~/.jbang/bin/analyser` is a stable launcher path for MCP
  configs, `--rest` enables the transport without a config edit. A `jpackage`/Homebrew milestone (M41) was spec'd
  and withdrawn the same day: it would have added a Dock icon, a four-runner release matrix and a code-signing
  bill, and solved nothing a user has asked for. Reopen only for a real user who cannot run JBang.
- **The MCP server identity is `fluxtion-analyser`; its executable need not share that name** _(2026-08-27)_ — a
  client registration names the server independently and launches a resolved absolute command. The current JBang
  launcher remains `analyser`; M42 uses it rather than making an install-name migration a prerequisite. A compatible
  `fluxtion-analyser` JBang alias is welcome only after it has been proven to coexist and upgrade cleanly.
- **Rendering stays Swing/Java2D — no embedded browser.** Reusing the JS replay engine via JCEF/JavaFX
  WebView would cost a ~100MB native per-platform dependency and destroy the single shaded fatjar that
  `jbang analyser@…` depends on. FlatLaf remains the only runtime dependency; a hand-rolled layered
  layout is the work, with pure-Java ELK as the fallback (spec-graph-replay §3).
