# Spec — validate the AI-authored Mongoose → Audit Log Analyser loop

**Status:** DRAFT — owner review required before implementation beyond the starter baseline.
**Created:** 2026-08-28. **Tracker:** [tracker.md](tracker.md). **AI entry point:**
[../../CLAUDE.md](../../CLAUDE.md).

## 1. Outcome

Prove, with local repeatable evidence, that a developer and LLM can design a small deterministic
Fluxtion process, run it in Mongoose, inspect the resulting audit data in the Fluxtion Audit Log
Analyser, and—when expressly requested—let an AI client assist within that already-open analyser
workspace.

The deliverable is a trustworthy developer workflow and its evidence, not an LLM-driven production
runtime. Fluxtion-generated Java remains the process that executes; Mongoose remains the local host;
the analyser remains the evidence/investigation tool; the LLM is a design and investigation collaborator.

## 2. Scope and boundaries

### In scope

- A minimal but real `PriceUpdate` vertical slice in this generated starter project.
- A local AOT build, a local Mongoose deployment, its admin-console observation, and a clean stop.
- A supported, documented audit-capture configuration that produces a known local artifact.
- Opening that artifact in the existing Fluxtion Audit Log Analyser and answering one predeclared
  question using its visible evidence.
- An optional explicit Claude/Codex/generic MCP registration through the analyser UI, followed by an
  MCP query whose result is compared with the same visible analyser state.
- A proposed user-local reusable skill contract for Mongoose starter projects, validated before it is
  generalized.

### Out of scope

- A production deployment, remote Mongoose host, remote audit storage, credential distribution or CI.
- Letting an LLM make runtime business decisions or auto-approve actions.
- A custom Mongoose plugin, custom MCP server, or an analyser code change before a repeated, measured
  need establishes that the existing script/skill/UI path cannot do the job.
- Claiming that a bridge connection proves an AI client imported, authorised or used its registration.

## 3. Starting facts and first risk

The starter currently declares a `FileEventSource` reading CSV-looking lines from `data/input.txt`,
while the only graph handler is `RootNode.onPriceUpdate(PriceUpdate)`. `MyProcessorSupplier` prints
unknown event types. Therefore the first validation is whether the source already maps a line to
`PriceUpdate`; it is not valid to infer that from the filenames or comments.

If it does not, the first application change is a small, pure CSV-to-`PriceUpdate` mapper and a test
that proves valid input reaches the handler and malformed input has an explicit outcome. The exact YAML
or API wiring must come from the installed Mongoose version's documentation and a working local build,
not a guessed property name.

## 4. Required end-to-end path

```text
domain intent + input fixture
        |
        v
small event types and local Fluxtion nodes --[AOT build + focused test]-->
generated deterministic processor
        |
        v
local Mongoose server --[named output + audit capture]--> known audit artifact
        |
        v
open Audit Log Analyser workspace --[human-visible question]--> evidence/report
        |
        +-- optional, explicit MCP client registration --> same workspace evidence via tool call
```

Every arrow needs an observable acceptance result. An agent narrative, a green graph-construction test,
or a successful MCP bridge check alone is not end-to-end evidence.

## 5. Requirements and evidence

| ID | Requirement | Minimum evidence |
|---|---|---|
| VAL-01 | The LLM has a repository-local orientation, design constraints and run loop. | `CLAUDE.md` directs the agent to this spec/tracker, actual project files, safe commands and the first known mismatch. |
| VAL-02 | CSV input becomes a typed `PriceUpdate` deterministically. | Focused test covers normal and invalid input; a local server run shows the typed handler/audit output rather than the unknown-event message. |
| VAL-03 | The graph has an observable deterministic business result. | A named sink/audit assertion identifies input, expected result and produced output. |
| VAL-04 | The generated processor deploys locally in Mongoose. | `check-fluxtion-key.sh`, test/package, `run-server.sh`, admin console at `127.0.0.1:8181`, and a documented clean-stop observation. |
| VAL-05 | Mongoose produces a durable, analyser-compatible audit artifact. | Exact configuration, artifact path/format, fixture/run identity and a restart-safe opening procedure are recorded. |
| VAL-06 | The analyser answers a predeclared question from the captured artifact. | The question, UI-visible filter/record/graph/report result and exported artifact (if any) are recorded together. |
| VAL-07 | An optional MCP client sees the same already-open analyser workspace without another analyser process. | Explicit setup/check, tool call, and comparison against the human-visible evidence; client import/approval is separately observed. |
| VAL-08 | Reusable automation is safe and portable across starter projects. | A user-local `mongoose-local` skill contract discovers project facts, protects secrets, runs one server, reports/cleans up, and fails clearly for absent audit mapping. |

## 6. Delivery order and decision gates

### Gate V0 — orient and agree the experiment

Review this specification, the tracker and `CLAUDE.md`. Choose one small question that the completed
slice will answer (for example, *which symbol updates were received and what result did the node
produce?*). Do not enable audit capture or introduce automation until the question and evidence path are
agreed.

### Gate V1 — typed, tested vertical slice

Establish the mapper/event/handler/output behaviour with a JUnit test. The test must exercise the
observable result, not only call `Supplier.get()`. Keep the value mapping pure and document the malformed
input policy. A code review should be able to read the event fields, node dependencies and test without
reconstructing a hidden dispatcher.

### Gate V2 — local Mongoose deployment

Run the normal key preflight, test and package loop, then start the server once from the repository
root. Confirm the configured admin console and the expected named output. Record the command, port,
fixture and clean-stop method. If a generated build fails, use the generator's directive and rerun; do
not hand-edit generated output.

### Gate V3 — audit artifact and analyser investigation

Consult the Mongoose version's installed documentation to make the smallest audit-capture configuration
change that produces a durable local artifact. Record its location and retention/cleanup rules. Open it
in the analyser, answer the predeclared question with visible evidence, and save a report only if it adds
traceable value. If the artifact is not supported by the analyser, stop and record the incompatibility;
do not fake a conversion or silently substitute a different data source.

### Gate V4 — optional AI sharing through MCP

Only after V3 works, open the analyser workspace and use **Connect an AI client** to configure
Claude/Codex/generic MCP explicitly. Check the local bridge, approve/import it in the client, then ask
one bounded question. Compare its tool result with the V3 UI evidence. Registration commands, tokens,
client configuration files and screenshots must not expose credentials.

### Gate V5 — extract the shared local skill

Generalize only operations proved by V1–V4. The project supplies its versioned topology and fixtures;
the user-local skill discovers its project contract and orchestrates preflight/build/status/stop/artifact
handoff. Run the skill against at least this project twice, including a clear failure path, before calling
it a common starter capability.

## 7. Local skill contract (design target)

The proposed `mongoose-local` skill is a local developer tool, not a service bundled in every generated
project. It must:

- discover `pom.xml`, `run-server.sh`, server YAML, local admin URL and declared output/audit paths;
- invoke the project's own preflight/build/test/run commands without printing the API key;
- refuse or warn before starting a duplicate local server;
- report the actual admin endpoint, PID/process identity if available, outputs and audit artifact path;
- provide a clean stop path; and
- state precisely when the project has no typed mapper, no enabled audit artifact, or no
  analyser-compatible evidence.

It must not edit domain code, create a cloud/server registration, copy AI configuration into the project,
or claim an MCP connection worked solely because a command was generated.

## 8. Security, evidence and operational rules

- Keep Fluxtion API keys in the existing user-local key file/environment only. Do not write them to a
  shell transcript, issue tracker, report, screenshot, YAML or client command.
- Treat every audit fixture and report as potentially sensitive. Use synthetic/local data for this
  exercise and name every exported artifact deliberately.
- One local server per working project/port; stop it after each acceptance run. Do not overwrite an
  artifact until the run it supports has been recorded.
- For each milestone, capture: source revision or file list, exact command(s), input fixture, expected
  result, actual result, artifact path, human reviewer and outstanding uncertainty.
- Work handed to another session uses a committed brief under `docs/handoff/` and a paired report when
  done; keep them live until independent review is written, then archive the complete correspondence.

## 9. Open decisions

| ID | Decision needed | Owner / gate |
|---|---|---|
| D-01 | Which supported Mongoose audit-capture settings and output format work with the installed 1.0.28/1.0.38 stack and the analyser? | V3 — establish from local docs/run. |
| D-02 | What is the smallest domain result worth observing after a `PriceUpdate`? | V0/V1 owner decision. |
| D-03 | Which AI client is the first MCP acceptance client: Claude or Codex? | V4 owner decision; generic remains documented. |
| D-04 | Where should the shared user-local `mongoose-local` skill live and how does it discover a starter? | V5, after two local validations. |

## 10. Definition of complete

This validation is complete only when VAL-01 through VAL-11 have recorded evidence, V3's visible
analyser result and V4's optional tool result agree for the same input, any required shared skill has
been exercised twice, and the final independent review accepts or explicitly amends the record. A
working server or a connected MCP entry by itself is not completion.

## 11. M19 onboarding-example alignment addendum

`spec-onboarding-example.md` in the Audit Log Analyser repository owns the user-facing M19 contract.
This specification is its local conformance exercise, not a replacement. The review found these shared
requirements that must be added to the validation evidence:

| ID | Additional requirement | Acceptance evidence |
|---|---|---|
| VAL-09 | The downloaded project is self-describing to an IDE agent and the analyser. | A project-specific `CLAUDE.md`, `AGENTS.md`, and a version-pinned local framework-orientation/golden-path snapshot under `docs/ai/`; `.analyser/project.fluxtion-settings` uses bundle-relative source roots and the generated EventProcessor FQN. |
| VAL-10 | The audit handoff uses the current analyser-readable onboarding format. | Mongoose writes an INFO text/YAML audit file at the documented `./logs/audit-<name>.yaml` path; Chronicle output is not presented as a substitute while no analyser reader exists. |
| VAL-11 | The analyser walkthrough is an observable support loop. | The produced log opens with Follow; a node/record can navigate to bundled source; a bounded question is answered from a visible filter/record/graph/report. |

MCP registration remains a later optional comparison: it must query the same already-open workspace and
agree with VAL-11 evidence, but it does not displace M19's in-app Explain/copy-prompt or the IDE-agent
edit → rerun → Follow loop. Before this project is offered as an onboarding bundle, general framework
rules in `CLAUDE.md` must be moved into a version-labelled local snapshot; the root file then remains the
thin project-specific adapter M19 specifies.
