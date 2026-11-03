# Proposal: the Analyser as context and display for an AI assistant (option C)

**Status:** proposal for independent review. Nothing has been built. Raised 2026-11-03.

**Decided already.**
- **Owner, 2026-11-02:** developers connect an AI assistant to Fluxtion **through the Analyser** (option C). The
  Node canvas spike is retired.
- **Owner, 2026-11-03:**
  - **The assistant does the work.** It authors, builds, generates and runs the project locally with its own tools
    (Maven, the Fluxtion compiler, `java`).
  - **The Analyser is the context and the display.** It gives the assistant the project's context and evidence, and
    exposes a canvas that shows what it is told to show.
  - **No second JVM.** The Analyser is already a running Java process, and it runs no project code.
  - **The canvas is display-only.**
  - **Registration covers as much as possible**, with the person's consent.

**Naming.** In this repo "canvas" already means the *shared evidence canvas*
([`spec-shared-evidence-canvas.md`](../specs/spec-shared-evidence-canvas.md), `CanvasHandoff`). This document
calls the new browser page the **AI canvas**: a display, driven by the Analyser, of what the assistant presents.
It is not the shared evidence canvas and does not replace it.

## 1. The decisions and what they change

### 1.1 Options considered (fluxtion-web, 2026-11-02)

The options were compared after the first end-to-end test of the Node canvas with Claude Code. The loop worked,
but the install did not: users needed Node 22, a ZIP download, hand-edited MCP config with absolute paths, and a
single-use five-minute link, and each step failed at least once. The owner's requirement: **a user must not need
Node.**

| Option | Summary | Outcome |
|---|---|---|
| A. Hosted relay | Claude → HTTPS MCP on the playground site → canvas tab. Project source passes through Telamin; an operated service. | Dropped |
| B. Native binary | The Node bridge compiled per platform (Bun, 57 MB). Per-platform signing; Bun's LGPL WebKit needs legal review. | Dropped |
| **C. The Analyser** | JBang install (JDK provisioned), already an MCP server with built-in registration and evidence views. | **Developers** |
| D. Assistant inside the playground | The person's own Anthropic API key in the page. Nothing to install. | **Built first, for evaluators** (web repo, not this proposal) |

### 1.2 Owner decisions, 2026-11-02 (quoted from the retired web document)

1. **Direction:** C for developers; D built first, for evaluation. A and B are dropped.
2. **PR #39** (the Node canvas) was to ship as a preview. **Superseded on 2026-11-03:** the owner retired the
   whole spike (§7).
3. **The Analyser decision of 2026-09-19 is revised.** Draft entry for `docs/specs/decisions.md`:
   > **2026-11-02 (owner).** Revises the 2026-09-19 decision. The Analyser may serve a loopback browser canvas and
   > broker a project's run and generation for an AI assistant; it still never mutates a server. The
   > browser-facing server is a second security contract, separate from the action socket, whose Origin refusal
   > (F2) is unchanged. State follows rule 9.

### 1.3 How 2026-11-03 narrows that revision

The 2026-11-02 draft let the Analyser **broker runs and generation**. The 2026-11-03 decision takes that back: the
assistant runs and generates with its own tools. What remains is narrower, and much closer to the
2026-09-19 decision ([`decisions.md`](../specs/decisions.md), first entry): *"the LLM operates the application
toolchain … the analyser renders evidence."*

Proposed entry, replacing the 2026-11-02 draft:

> **2026-11-03 (owner).** Narrows the 2026-11-02 revision of the 2026-09-19 decision. The assistant authors, builds,
> generates and runs the project with its own tools. The Analyser runs no project code and performs no generation.
> It may serve a loopback, display-only browser canvas showing what an assistant presents to it: a project
> snapshot, a graph, run output and results. The browser-facing server is a second security contract, separate
> from the action socket, whose Origin refusal (F2) is unchanged. State follows rule 9.

The only new capability is **serving a display**. No execution verb is added.

## 2. Why C, and what friction remains

**Why it suits developers.**
- One trusted command (JBang), and registration already exists.
- Their real project folder and their real build: Maven, the Fluxtion compiler and their own JDK.
- The assistant's runs produce audit logs that land in views the Analyser already has: topology and step-through,
  audit records, spotlight, Design and reports.
- The Analyser runs nothing, so there is no execution sandbox question, no browser Java runtime and no credential
  in the Analyser.

| Friction that remains | Mitigation |
|---|---|
| First JBang run downloads a JDK and the jar | Install or setup prefetches everything (§8.1) |
| A desktop app, not a web page | Evaluators use D; C is for developers |
| Does not reach claude.ai | Accepted; Claude Code, Codex and Claude Desktop are covered |
| Registration is a step | Done at first start, as broadly as the person allows (§4) |
| The canvas cannot start work | The person asks the assistant in chat; the canvas shows the result (§6.3) |

## 3. Proposed user experience

**First time**, for someone with no JBang or Java, using the one-line zero-install launch already on branch
`prototype/builtin-claude-onboarding`:

```
curl -fsSL https://sh.jbang.dev | bash -s - --java 21 <analyser jar URL>        # macOS / Linux
iex "& { $(iwr -useb https://ps.jbang.dev) } --java 21 <analyser jar URL>"     # Windows PowerShell
```

People who already have JBang can use `jbang app install analyser@telaminai/fluxtionauditlog-analyser`.

On first start, the Analyser offers **one consent screen** that registers everything it can (§4). Then, from the
person's own assistant in their project folder:

1. **Context.** The assistant reads the project's context through the Analyser: profile, processors, graph,
   previous runs, evidence, and the project's **runbooks** (existing `analyser_*` verbs). The runbooks, not the
   Analyser, say how this project is built, generated and run.
2. **Work.** The assistant edits the code, generates the processor and builds, with its own tools.
3. **Run.** The assistant runs the application locally, with its own tools. The run writes an audit log.
4. **Present.** The assistant tells the Analyser what to show: the changed files, the graph, the run's output and
   its results. The Analyser opens the AI canvas in the browser and displays them.
5. **Explain.** The assistant opens the run's audit log in the Analyser and points at the proving records with the
   existing verbs (open, spotlight, flag).
6. **Iterate.** The person asks for changes in the chat. The assistant repeats from step 2 and presents a new
   snapshot. The canvas never picks up edits by itself.

## 4. Registration: as much as possible, with consent

M42 registers the bridge with **one** client, when the person chooses one. The new behaviour is a single consent
screen at first start (and in **Set up AI**) that lists everything it can register, all pre-selected, each with what
it changes and how to undo it:

| Item | What it does |
|---|---|
| Claude Code | `claude mcp add --scope user …` (existing `ClaudeMcpClient`) |
| Codex | Existing `CodexMcpClient` |
| Claude Desktop | Generic `mcpServers` entry (existing `GenericMcpConfiguration`); write it only where the config location is known |
| Fluxtion skills | A plugin or skills folder for each assistant that supports one, so it knows how to author, build, generate and run Fluxtion projects (the web test showed an assistant without it improvises) |
| External MCP/REST access | Switch on for this machine (today off by default, with its own confirmation) |

Rules:
- **Consent is explicit.** One screen, items visible and untickable, nothing written before **Register**.
- **Detected hosts only.** Never register with a client that is not installed.
- **Never overwrite** an existing entry with the same name; report it instead.
- **Reversible.** Every registration has a listed undo, and **Set up AI** can remove them all.
- **Re-runnable.** After installing a new assistant, run setup again.

The skills matter more in this design: the assistant now does all the building and running, so its guidance is the
main thing that keeps it on the golden path.

## 5. The launch-model conflict, and the recommended resolution

**The conflict.** The connect guide (`docs/site/connect-an-llm.md`, M42) is explicit:

> **Your client does not launch the analyser.** You start the analyser yourself and leave it open … A freshly
> started analyser would have none of it: no log, no charts, no flags.

The first draft of this proposal had the bridge serving the canvas headlessly and opening the window on demand,
which contradicted that rule and split state across two processes.

**Recommended resolution.**
1. **The running Analyser owns everything:** the presented snapshot, the canvas server and the display state. The
   bridge stays a stateless forwarder to `/action`. Rule 9 is satisfied naturally, because canvas state lives in the
   existing session processor (§6.4).
2. **The rule stays for `analyser_*` verbs.** They drive *your* open session.
3. **One narrow exception for canvas tools, if wanted.** When a canvas tool arrives and no Analyser is running, the
   bridge may start it, after consent the first time. The guide's reason for the rule, that a fresh Analyser has
   none of your session, does not apply: a presentation brings its own snapshot. If adopted, the guide is updated
   to say so.

Reviewers decide whether to adopt the exception or keep the rule absolute, so the person opens the Analyser first.

## 6. Design

### 6.1 Tools

The bridge publishes display tools beside the existing `analyser_*` verbs, all forwarded to the running app:

| Tool | Purpose |
|---|---|
| `canvas_present` | Show a snapshot of explicitly named project files |
| `canvas_show_run` | Show a run the assistant performed: its output, results and the path of its audit log |
| `canvas_focus` | Point the canvas at a file, the graph or a result |
| `canvas_status` | Report what the canvas is showing, and whether the person has it open |
| `canvas_close` | Close the presentation |

(The names are illustrative. Whether they become `analyser_*` verbs is a review question, since the verb count is
documented and guarded.)

No run, input, stop or generate tool: the assistant does those with its own tools.

### 6.2 What the Analyser does not do

- It runs no project code: no `Main`, no processor, no second JVM, no class loading of project classes.
- It performs no generation and holds no compiler credential.
- It has **no build knowledge**. How to build, generate and run a project lives in the project's runbooks and in
  the skills. The Analyser makes them available as context: the profile records runbook pointers, never contents
  (M38). It interprets no runbook and implements no domain behaviour (the 2026-09-19 decision, unchanged).

### 6.3 The canvas: display only

The browser page holds **no logic and no state**. It renders a display model the Analyser pushes:
- the snapshot's files and graph;
- a run's output and results, labelled **application-reported**;
- links into the Analyser's own evidence views for that run.

The page has **no controls that start work**. An MCP server cannot call the assistant: calls only go from assistant
to server. So a Run or Regenerate button on the page could not reach the assistant. The person asks in the chat,
and the canvas shows the outcome. Navigation controls (choose a file, show the graph) are fine, because they only
change the view.

The page and its assets ship inside the jar. No Java runs in the browser, so it needs no CheerpJ, no runtime
download and no third-party hosts.

The browser-facing server is the second security contract the decision names, separate from the action socket
(F2 unchanged):
- loopback only, port allocated automatically, Host and Origin checks, and a custom header on every call;
- a session cookie set when the Analyser opens the page. Whether a single-use invitation is still needed, when only
  the Analyser opens the page, is a review question;
- presentations contain only explicitly named files under the project root, no symlinks, with size and count caps;
- snapshot revision = SHA-256 over the sorted path and digest list. A local edit needs a new presentation, and a
  repeated request must be compared by file **contents**, not just arguments (review bug 2);
- run output paged by cursor to the end (review bug 6).

### 6.4 State and rule 9

The presented snapshot, the shown run and the canvas display state are session state: nodes in the generated
session processor, with effects (push display, open page) and facts (presentation received, page opened, page
closed). The canvas and the window both render the published snapshot. Nothing is hand-dispatched.

### 6.5 Starting the Analyser for a canvas tool (only if §5.3 is adopted)

- **Consent.** Confirm the first time, then remember the choice.
- **External access.** Should a launch from the bridge enable external access for that session only?
- **Single instance.** Attach to an open Analyser; never start a second.
- **Readiness.** The bridge waits for `/action`, or returns a clear error saying how to start the Analyser.

## 7. The retired Node spike as reference

Archived 2026-11-03 as fluxtion-web tag `archive/node-canvas-spike-2026-11-03` (commit `848f330`, the head of the
deleted branch `fix/canvas-worker-isolation`; PR #39 closed). Read files with
`git show archive/node-canvas-spike-2026-11-03:<path>`.

**Still useful:**
- the snapshot, revision and idempotency rules (`scripts/canvas/core.mjs`);
- the canvas page's layout, as a starting point for the display-only page;
- `native-host-smoke.mjs`, which drives the real Claude Code host;
- the regression cases in `core.node-test.mjs` and `review-regressions.mjs` that concern presentation.

**Not needed:** the run, input and stop machinery, the CheerpJ worker runtime, the runtime library download, the
device sign-in and generation proxy (`account.mjs`, `playground_generate`), the ZIP distribution, the public
`/connect-ai` page and the Node bridge. Building, generating and running belong to the assistant's tools.

## 8. Risks and points to get right

1. **Slow first start.** MCP hosts time out slow server starts; Claude Code's limit is configurable but short by
   default. Prefetch during install or setup. The bridge stays thin, so its own start is fast.
2. **stdout stays clean.** Already enforced for the bridge.
3. **Presented output is the assistant's claim.** The canvas shows what the assistant sends. Label it as reported,
   and point to the audit log in the Analyser, which the Analyser reads itself, as the evidence.
4. **Runbooks and skills carry the workflow.** With the assistant doing all the work and the Analyser holding no
   build knowledge, missing or stale runbooks and skills are the main way the experience goes wrong. Pin and
   version the skills with the Analyser. Make a project with no runbook visible as such, rather than letting the
   assistant improvise.
5. **One Analyser, several assistants.** Two sessions may present at once. Key presentations so they do not
   collide, or refuse the second with a clear message.
6. **Broad registration must not surprise.** One consent screen, undo for every item, no overwrites (§4).

## 9. Repo rules this work must satisfy

- **Rule 6.** Read the live MCP specification (currently `2026-07-28`) before adding tools. `McpBridge` already
  answers both eras.
- **Rule 9.** §6.4.
- **Rule 8, regression closure.** Each spike defect (§10) that this design could reintroduce needs a static or
  executable check here, not just a passing trial.
- **Rule 1, public repo.** Fixtures use demo content only. Canvas screenshots need the generated, isolated capture
  used for other docs images.
- **Decision record.** Add the §1.3 entry to `docs/specs/decisions.md`.
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

  Bugs 1, 2 and 6 apply to this design. Bugs 3 and 5 depend on whether invitations and preview caps are kept.
  Bugs 4 and 7 concern runs the Analyser no longer performs.
- **Fix commit `6262920`.** All eight steps of the supplied end-to-end script passed. The seven defects were not
  retested.

**Reported by the spike's author, not verified by this reviewer.** Native Claude Code 2.1.316 completed the MCP
workflow twice, and a full generation loop passed on the owner's account.

## 11. Suggested steps

1. ~~Archive and retire the spike~~ **Done 2026-11-03** (§7).
2. **Review this proposal**, settle §5 and §12, and write the spec with the rule 9 design and the §1.3 entry.
3. **Registration and skills first** (§4). Useful on their own, today, for the existing `analyser_*` tools.
4. **Present and display:** `canvas_present`, `canvas_focus`, `canvas_status` and the display-only page, served by
   the running Analyser.
5. **Show a run:** `canvas_show_run`, linking the run's output to its audit log in the Analyser's evidence views.
6. **First-user trial** on a clean machine: zero-install launch, one consent screen, then a change built, run and
   presented by the person's own assistant, observed rather than scripted.

## 12. Open questions for the reviewer

- **Launch model (§5).** Adopt the narrow exception for canvas tools, or keep "your client does not launch the
  analyser" absolute?
- **One route or two (§13).**
- **Canvas or window.** If the Analyser runs nothing, is a browser page still needed, or can the Analyser window show
  the presentation itself? The browser page suits sharing a screen; the window already has the evidence views.
- **Tool names.** Separate `canvas_*` tools, or new `analyser_*` verbs?
- **Pairing.** Is a single-use invitation needed if only the Analyser opens the page?

## 13. One route or two

**Today there are two routes:**
- the built-in Claude Code panel (M74, M77, M78), where the Analyser launches Claude Code as a child and the session
  gets the `propose` verb;
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
