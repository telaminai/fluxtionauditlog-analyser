# Proposal: the Analyser runs projects locally and drives a display-only AI canvas (option C)

**Status:** proposal for independent review. Nothing has been built. Raised 2026-11-03.

**Decided already.**
- **Owner, 2026-11-02:** developers connect an AI assistant to Fluxtion **through the Analyser** (option C). The
  Node canvas spike is retired.
- **Owner, 2026-11-03:**
  - the project runs **locally, inside the Analyser's own JVM**, with no second JVM;
  - the canvas is **display-only** and shows what the Analyser tells it;
  - registration covers **as much as possible**, with the person's consent.

This document is the Analyser-side detail for those decisions. §1 carries the 2026-11-02 record, because the
web-repo document that first held it retired with the spike.

**Naming.** In this repo "canvas" already means the *shared evidence canvas*
([`spec-shared-evidence-canvas.md`](../specs/spec-shared-evidence-canvas.md), `CanvasHandoff`). This document
calls the new browser page the **AI canvas**: a display of a project snapshot and its runs, driven by the Analyser.
It is not the shared evidence canvas and does not replace it.

## 1. The decisions and what they change

### 1.1 Options considered (fluxtion-web, 2026-11-02)

The options were compared after the first end-to-end test of the Node canvas with Claude Code. The loop worked:
an assistant authored a change, a generation was approved in the browser, and the run was checked against the
audit trail. The install did not work well. Users needed Node 22, a ZIP download, hand-edited MCP config with
absolute paths, and a single-use five-minute link, and each step failed at least once. The owner's requirement:
**a user must not need Node.**

| Option | Summary | Outcome |
|---|---|---|
| A. Hosted relay | Claude → HTTPS MCP on the playground site → canvas tab. Project source passes through Telamin; an operated service. | Dropped |
| B. Native binary | The Node bridge compiled per platform (Bun, 57 MB). Per-platform signing; Bun's LGPL WebKit needs legal review. | Dropped |
| **C. The Analyser** | JBang install (JDK provisioned), already an MCP server with built-in registration. Runs and regenerates locally, with the Analyser's evidence views. | **Developers** |
| D. Assistant inside the playground | The person's own Anthropic API key in the page. Nothing to install. | **Built first, for evaluators** (web repo, not this proposal) |

### 1.2 Owner decisions, 2026-11-02 (quoted from the retired web document)

1. **Direction:** C for developers; D built first, for evaluation. A and B are dropped.
2. **PR #39** (the Node canvas) was to ship as a preview. **Superseded on 2026-11-03:** the owner retired the
   whole spike (§7).
3. **The Analyser decision of 2026-09-19 is revised.** Draft entry for `docs/specs/decisions.md`, to be recorded
   with the spec:
   > **2026-11-02 (owner).** Revises the 2026-09-19 decision. The Analyser may serve a loopback browser canvas and
   > broker a project's run and generation for an AI assistant; it still never mutates a server. The
   > browser-facing server is a second security contract, separate from the action socket, whose Origin refusal
   > (F2) is unchanged. State follows rule 9.

The 2026-11-02 wording allowed Java to run "in the browser or through the compiler". The 2026-11-03 decision
settles it: **locally, in the Analyser's own JVM**. The entry above should be amended to say so when it is
recorded.

### 1.3 What the revision overturns

The 2026-09-19 decision ([`decisions.md`](../specs/decisions.md), first entry) says application processing,
generation and hosting execute in their owning tools, and that the Analyser *"must not acquire application
execution or orchestration logic … it authorizes no new execution verb."* Option C deliberately adds brokered,
locally executed runs and generation.

Every new capability must stay inside the revised wording: **broker** a run or generation the person approved.
Do not interpret runbooks, implement domain behaviour, or mutate a server.

## 2. Why C, and what friction remains

**Why it suits developers.**
- One trusted command (JBang), and registration already exists.
- Their real project folder is used.
- Generation uses the same device credential as `mvn fluxtion:login`.
- The run lands in views the Analyser already has: topology and step-through, audit logs, spotlight, Design and
  reports.
- Fluxtion authors are Java developers, so a JDK is no extra burden.
- With a local JVM, there is no 35 MB browser runtime, no CheerpJ limits and no third-party runtime host.

| Friction that remains | Mitigation |
|---|---|
| First JBang run downloads a JDK and the jar | Install or setup prefetches everything (§8.1) |
| A desktop app, not a web page | Evaluators use D; C is for developers |
| Does not reach claude.ai | Accepted; Claude Code, Codex and Claude Desktop are covered |
| Registration is a step | Done during install, as broadly as the person allows (§4) |
| Development-Use licence | Fine for authoring; production use already needs an agreement |

## 3. Proposed user experience

**First time**, for someone with no JBang or Java, using the one-line zero-install launch already on branch
`prototype/builtin-claude-onboarding`:

```
curl -fsSL https://sh.jbang.dev | bash -s - --java 21 <analyser jar URL>        # macOS / Linux
iex "& { $(iwr -useb https://ps.jbang.dev) } --java 21 <analyser jar URL>"     # Windows PowerShell
```

People who already have JBang can use `jbang app install analyser@telaminai/fluxtionauditlog-analyser`.

On first start, the Analyser offers **one consent screen** that registers everything it can (§4). Then, from the
person's own assistant inside their project folder:

1. The person asks to see or change the project.
2. The assistant calls the canvas tools through the registered bridge. The project root defaults to the folder the
   assistant runs in (`CLAUDE_PROJECT_DIR` for Claude Code; the retired spike proved this in commit `e0af7a2`).
3. The Analyser opens the AI canvas in the browser directly, so there is no five-minute link to open. It shows the
   snapshot it was told to show.
4. On request, and after the person approves, the Analyser loads the project's generated processor **in its own
   JVM** and drives it (§6.2). It streams output, results, audit records and status to the canvas, and stops the
   run on request.
5. When generation is needed, the person approves each one. The Analyser writes the generated files; the model never
   retypes them.
6. To explain a run, the assistant uses the existing `analyser_*` verbs on the same session: open the export,
   spotlight, flag.

## 4. Registration: as much as possible, with consent

M42 registers the bridge with **one** client, when the person chooses one. The new behaviour is a single consent
screen at first start (and in **Set up AI**) that lists everything it can register, all pre-selected, each with what
it changes and how to undo it:

| Item | What it does |
|---|---|
| Claude Code | `claude mcp add --scope user …` (existing `ClaudeMcpClient`) |
| Codex | Existing `CodexMcpClient` |
| Claude Desktop | Generic `mcpServers` entry (existing `GenericMcpConfiguration`); write it only where the config location is known |
| Fluxtion skills | A small plugin or skills folder for each assistant that supports one, so it has authoring guidance (the web test showed an assistant without it improvises) |
| External MCP/REST access | Switch on for this machine (today off by default, with its own confirmation) |

Rules:
- **Consent is explicit.** One screen, items visible and untickable, nothing written before **Register**.
- **Detected hosts only.** Never register with a client that is not installed.
- **Never overwrite** an existing entry with the same name; report it instead.
- **Reversible.** Every registration has a listed undo, and **Set up AI** can remove them all.
- **Re-runnable.** After installing a new assistant, run setup again.

## 5. The launch-model conflict, and the recommended resolution

**The conflict.** The current connect guide (`docs/site/connect-an-llm.md`, M42) is explicit:

> **Your client does not launch the analyser.** You start the analyser yourself and leave it open … A freshly
> started analyser would have none of it: no log, no charts, no flags.

The bridge is a thin forwarder; the running app owns the session. The first draft of this proposal moved canvas
state into the headless bridge and opened the window on demand. That contradicts the guide and splits state across
two processes.

**Recommended resolution** (given the 2026-11-03 decisions):

1. **The running Analyser owns everything.** It owns the snapshots, the local JVM runs, generation, the canvas
   server and the run state. The bridge stays what it is: a stateless forwarder to `/action`. Rule 9 is then
   satisfied naturally, because canvas state lives in the existing session processor (§6.4).
2. **The rule stays for `analyser_*` verbs.** They drive *your* open session.
3. **One narrow exception for canvas tools.** When a canvas tool arrives and no Analyser is running, the bridge may
   start it, after consent the first time (§6.5). The guide's reason for the rule, that a fresh Analyser has none of
   your session, does not apply: a canvas run brings its own project snapshot. The guide must then be updated to say
   so.

Reviewers should decide whether to accept this, or keep the rule absolute and require the person to open the
Analyser first.

## 6. Design

### 6.1 Tools

The bridge publishes canvas tools beside the existing `analyser_*` verbs, all forwarded to the running app:

| Tool | Purpose |
|---|---|
| `canvas_present` | Show a snapshot of explicitly named files |
| `canvas_status` | Report what the canvas is showing |
| `canvas_focus` | Point the canvas at a file or the graph |
| `canvas_run` | Load and start the project's processor in the Analyser's JVM |
| `canvas_input` | Send one event to the running processor |
| `canvas_stop` | Stop the run |
| `canvas_result` | Read output, results and status |
| `canvas_close` | Close the snapshot |
| `canvas_generate` | Regenerate the processor, after approval |

(The names are illustrative. Whether they become `analyser_*` verbs is a review question, since the verb count is
documented and guarded.)

### 6.2 Runs inside the Analyser's JVM

**Decision (owner, 2026-11-03):** the Analyser is already a running JVM, so no second JVM is started.

**Drive the processor, not `Main`.** For a Fluxtion project, the Analyser loads the generated processor class and
drives its lifecycle directly: `init`, `start`, `onEvent` for each input, then `stop` and `tearDown`.
- **Inputs are events.** `canvas_input` delivers a typed event to `onEvent`, so no input adapter is needed in the
  application. Graph lab's mailbox file and its markers become unnecessary.
- **Stop is clean.** Stop feeding events, then `stop` and `tearDown`. No thread has to be killed.
- **Audit records come straight in.** The run's audit log processor can feed the Analyser's own log reader, so the
  run appears in the evidence views without an export step.

**Isolation inside one JVM:**
- **A disposable class loader per run.** The project's compiled classes and its dependencies load in a child class
  loader that is dropped after the run, so the next run starts with fresh classes and static state, and two
  snapshots never share classes.
- **Compile in process** with the JDK's compiler API (`javax.tools`), from the snapshot's sources into that run's
  private output folder.
- **Calls run on a worker thread, never the EDT.** Each `onEvent` call has a time limit.

**Costs that come with one JVM** (no sandbox in either design, but these are new):
- **A program cannot be force-stopped.** Java 21 removed `Thread.stop`. A handler stuck in a loop keeps running until
  the Analyser restarts. The worker is marked lost, the run is reported as `stuck`, and no further events are sent.
- **`System.exit` in project code would close the Analyser.** There is no supported way to block it in Java 21
  (the Security Manager is gone). Running only the processor, rather than `Main`, avoids the usual caller, but the
  project's own handler code can still call it.
- **Shared memory.** A leak or a large allocation in project code affects the Analyser itself.
- **Arbitrary `Main` programs are out of scope.** Only the generated processor is driven. Projects that need their
  own `Main` run in the person's own tools, as today.

**Unchanged:**
- One run at a time per snapshot, with a time limit that is set and shown.
- Output is labelled **application-reported**, never a verified result.
- **No sandbox.** Project code runs with the person's file and network access. Approval text, UI and docs must say
  so plainly.

### 6.3 The canvas: display only

The browser page holds **no logic and no state**. It renders a display model the Analyser pushes:
- the snapshot's files and graph;
- the run's status, output and results;
- the approval prompts the Analyser asks it to show.

Any control on the page (Run, Stop, an input field, Approve) only **sends a request**; the Analyser decides. No Java
runs in the browser, so the page needs no CheerpJ, no runtime download and no third-party hosts. The page and its
assets ship inside the jar.

The browser-facing server is the second security contract the 2026-11-02 decision names, separate from the action
socket (F2 unchanged):
- loopback only, port allocated automatically, Host and Origin checks, and a custom header on every call;
- a pairing step, then an HttpOnly session cookie (the spike's single-use invitation is only needed if the page can
  be opened by something other than the Analyser; review this);
- one selected project root, only explicitly named files, no symlinks, size and count caps;
- snapshot revision = SHA-256 over the sorted path and digest list; a local edit needs a new snapshot, and
  idempotency must cover file **contents**, not just arguments (review bug 2);
- output paged by cursor to the end (review bug 6).

### 6.4 State and rule 9

Previews, runs, approvals, generation and canvas display state are session state. They are nodes in the generated
session processor, with effects (compile, load, deliver event, stop, write files, push display) and facts (event
handled, handler timed out, output arrived, approval given). The canvas and the window both render the published snapshot. Nothing is hand-dispatched.

### 6.5 Starting the Analyser for a canvas tool

Settle in the spec:
- **Consent.** Confirm the first time, then remember the choice.
- **External access.** Should a launch from the bridge enable external access for that session only?
- **Single instance.** Attach to an open Analyser; never start a second.
- **Readiness.** The bridge waits for `/action`, or returns a clear error saying how to start the Analyser.

### 6.6 Generation

- Use the builder with the same device credential as `mvn fluxtion:login`.
- The person approves each generation; credits are theirs.
- The credential never reaches the browser or the model's context.
- Generated files are written by the Analyser and presented as a new snapshot revision.
- The spike's generation-source rule (leave out the code that *uses* the processor; commit `8fffa95`) carries over.

## 7. The retired Node spike as reference

Archived 2026-11-03 as fluxtion-web tag `archive/node-canvas-spike-2026-11-03` (commit `848f330`, the head of the
deleted branch `fix/canvas-worker-isolation`; PR #39 closed). Read files with
`git show archive/node-canvas-spike-2026-11-03:<path>`.

**Port, adapting to §6:**
- the snapshot, revision and command-idempotency rules (`scripts/canvas/core.mjs`);
- the device sign-in and gated proxy (`scripts/canvas/account.mjs`);
- `playground_generate` with per-generation approval and follow-the-tab;
- the generation source selection (`src/lib/canvas/generation.ts`);
- the canvas page's layout, as a starting point for the display-only page.

**Turn into this repo's acceptance cases:** `core.node-test.mjs`, `review-regressions.mjs`, and
`native-host-smoke.mjs` (the real Claude Code host).

**Not carried over** (running in the Analyser's JVM and a display-only page make them unnecessary): the CheerpJ worker runtime, the
runtime library download and its SHA-256 manifest, the ZIP distribution, the public `/connect-ai` page, and the Node
bridge.

## 8. Risks and points to get right

1. **Slow first start.** MCP hosts time out slow server starts; Claude Code's limit is configurable but short by
   default. Prefetch during install or setup. The bridge stays thin, so its own start is fast.
2. **stdout stays clean.** Already enforced for the bridge. Project code runs in the app, not the bridge, so its
   output can never reach the bridge's stdout. Capture the project's `System.out` per run without disturbing the
   Analyser's own output.
3. **Project code shares the Analyser's JVM.** A stuck handler, `System.exit` or a leak affects the Analyser
   (§6.2). The approval wording, UI and docs must say so.
4. **One Analyser, several assistants.** Two assistant sessions may drive the same Analyser. Canvas state must be
   keyed so they do not collide, or a second session is refused with a clear message.
5. **Broad registration must not surprise.** One consent screen, undo for every item, no overwrites (§4).
6. **Scope creep against the revised decision.** Brokered run and generation only: no runbook interpretation, no
   domain logic, no server mutation.

## 9. Repo rules this work must satisfy

- **Rule 6.** Read the live MCP specification (currently `2026-07-28`) before adding tools. `McpBridge` already
  answers both eras.
- **Rule 9.** §6.4.
- **Rule 8, regression closure.** Each defect found in the spike reviews (§10) that the port could reintroduce needs
  a static or executable check here, not just a passing trial.
- **Rule 1, public repo.** Fixtures use demo content only. Canvas screenshots need the generated, isolated capture
  used for other docs images.
- **Decision record.** Add the §1.2 entry to `docs/specs/decisions.md`, amended for the local JVM (§1.2).
- **Verb count.** If canvas tools become verbs, update the documented count and its guards.

## 10. Evidence behind this proposal

**Tested by the reviewer (2026-11-01 and 2026-11-03).** These runs used the official MCP SDK client over stdio,
real Chromium and the real CheerpJ runtime. No assistant host was registered, because the reviewing session's
config change was blocked.

- **Spike at `fee7c0e`.** The order/limit demo worked end to end. Seven defects were found:
  1. fixed port 5173;
  2. a stale snapshot returned for a repeated `commandId`;
  3. single-use links with no reissue;
  4. a run left `active` after its tab closed;
  5. expired previews still counting towards the cap of five;
  6. output truncated at 60,000 characters;
  7. "Running" shown after a program crashed.

  Bugs 1, 2, 4 and 6 are relevant to this design. Bugs 3, 5 and 7 mostly disappear, because the Analyser owns
  process state and opens the page itself.
- **Fix commit `6262920`.** All eight steps of the supplied end-to-end script passed. The seven defects were not
  retested.

**Reported by the spike's author, not verified by this reviewer.** Native Claude Code 2.1.316 completed the MCP
workflow twice, and a full generation loop passed on the owner's account.

## 11. Suggested steps

1. ~~Archive and retire the spike~~ **Done 2026-11-03** (§7).
2. **Review this proposal**, settle §5 and §12, and write the spec with the rule 9 design and the `decisions.md`
   entry.
3. **Registration first** (§4). It is useful on its own, today, for the existing `analyser_*` tools.
4. **Present and display:** `canvas_present`, `canvas_status` and the display-only page, served by the running
   Analyser.
5. **Local runs:** compile, load, drive and stop the processor in the Analyser's JVM, against the archived
   regression cases. Include witnesses for a stuck handler and for a class-loader leak.
6. **Generation:** device credential, approval and written files.
7. **First-user trial** on a clean machine: zero-install launch, one consent screen, then the demo, observed rather
   than scripted.

## 12. Open questions for the reviewer

- **Launch model (§5).** Accept the narrow exception for canvas tools, or keep "your client does not launch the
  analyser" absolute?
- **One route or two.** Built-in Claude Code inside the Analyser (M74/M77) and an external assistant through the
  bridge (M42) are two setups today. Can they become one? See §13.
- **Tool names.** Separate `canvas_*` tools, or new `analyser_*` verbs?
- **Typed inputs.** How does the canvas learn which event types a processor accepts, and their fields? (The GraphML
  lists event types; the fields need a source.)
- **Stuck handlers.** Is "report `stuck` and refuse further events until restart" acceptable?
- **Pairing.** Is a single-use invitation needed if only the Analyser opens the page?

## 13. One route or two

**Today there are two routes:**
- the built-in Claude Code panel (M74, M77, M78), where the Analyser launches Claude Code as a child and the
  session gets the `propose` verb;
- your own assistant (M42), registered once, driving your open Analyser through the bridge.

**Recommendation: one setup, one registration and one tool set, with two places to talk.**
- The consent screen in §4 registers the bridge and skills with the person's assistants. The built-in panel then uses
  that **same** registration, rather than a configuration of its own.
- Whether the person types in the Analyser's panel or in their own terminal, it is the same Claude Code, the same
  tools and the same session. The panel is a convenience view, not a separate route.
- The remaining difference is `propose`, which only the built-in session sees today. Either expose it to external
  clients, or keep it as the one panel-only capability and document why.

**Why not drop the panel entirely.** It shipped across M74, M77 and M78, with consent, folder trust and the
data-flow diagram. Removing it is an owner decision with its own cost. Keeping it as a view over one setup removes
the duplication without discarding that work.

This needs a reviewer who knows how the built-in session is configured today. This proposal has not checked whether
the panel can reuse a user-scope registration as it stands.
