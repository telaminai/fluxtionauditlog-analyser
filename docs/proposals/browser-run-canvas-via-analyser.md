# Proposal: the Analyser serves the AI canvas and brokers runs (option C)

**Status:** proposal for independent review. Nothing has been built. Raised 2026-11-03.

**Decided already (owner, 2026-11-02):** developers connect an AI assistant to Fluxtion **through the Analyser**
(option C). The Node canvas spike is retired. This document is the Analyser-side detail for that decision. It
carries the decision record in §1, because the web-repo document that first recorded it
(`docs/proposals/canvas-install-and-connection.md` on fluxtion-web branch `fix/canvas-worker-isolation`) is
retiring with the spike.

**Naming.** In this repo "canvas" already means the *shared evidence canvas*
([`spec-shared-evidence-canvas.md`](../specs/spec-shared-evidence-canvas.md), `CanvasHandoff`). This document
calls the new feature the **AI canvas**: a local surface where a person and their assistant see a project
snapshot, run it, send inputs, regenerate it, and stop it. It renders results. It is not the shared evidence
canvas and does not replace it.

## 1. The decision and what it changes

### 1.1 Options considered (fluxtion-web, 2026-11-02)

The options were compared after the first end-to-end test of the Node canvas with Claude Code. That test worked:
an assistant authored a change, a generation was approved in the browser, and the run was checked against the
audit trail. The install did not work well. Users needed Node 22, had to download and unzip a file, hand-edit MCP
config with absolute paths, and open a single-use five-minute link. Each step failed at least once in the test.
The owner's requirement from it: **a user must not need Node.**

| Option | Summary | Outcome |
|---|---|---|
| A. Hosted relay | Claude → HTTPS MCP on the playground site → canvas tab. Zero install, but project source passes through Telamin and it is an operated service. | Dropped |
| B. Native binary | The Node bridge compiled per platform (Bun, 57 MB). Per-platform signing; Bun's LGPL WebKit needs legal review. | Dropped |
| **C. The Analyser** | JBang install (JDK provisioned), already an MCP server with built-in registration. Runs and regenerates locally, with the Analyser's evidence views. | **Developers** |
| D. Assistant inside the playground | The person's own Anthropic API key in the page; tools are page functions. Nothing to install. | **Built first, for evaluators** (web repo, not this proposal) |

### 1.2 Owner decisions, 2026-11-02 (quoted from the retiring web document)

1. **Direction:** C for developers; D built first, for evaluation. A and B are dropped.
2. **PR #39** (the Node canvas) was to ship as a preview. **Superseded on 2026-11-03:** the owner retired the whole
   Node spike instead.
3. **The Analyser decision of 2026-09-19 is revised.** Draft entry for `docs/specs/decisions.md`, to be recorded
   with the spec:
   > **2026-11-02 (owner).** Revises the 2026-09-19 decision. The Analyser may serve a loopback browser canvas and
   > broker a project's run and generation for an AI assistant; it still never mutates a server. The
   > browser-facing server is a second security contract, separate from the action socket, whose Origin refusal
   > (F2) is unchanged. State follows rule 9.

### 1.3 What the revision overturns

The 2026-09-19 decision ([`decisions.md`](../specs/decisions.md), first entry) says application processing,
generation and hosting execute in their owning tools, and that the Analyser *"must not acquire application
execution or orchestration logic … it authorizes no new execution verb."* Option C deliberately adds brokered run
and generation. Every new capability below must stay inside the revised wording: **broker** a run or generation the
person approved. Do not interpret runbooks, implement domain behaviour, or mutate a server.

## 2. Why C, and what friction remains

**Why it suits developers.** It is one command they can trust (JBang, which they may already use). Registration
already exists. Their real project folder is used. Generation uses the same device credential as
`mvn fluxtion:login`. The results land in views the Analyser already has: topology and step-through, audit logs,
spotlight, Design and reports. Fluxtion authors are Java developers, so a JDK is not an extra burden.

**Friction that remains, and the mitigation for each:**

| Friction | Mitigation |
|---|---|
| First JBang run downloads a JDK and the jar, so it is slow | Make install or setup fetch everything, so the first `--mcp` launch is fast (§7.1) |
| A desktop app, not a web page | Evaluators use D; C is for developers |
| Does not reach claude.ai | Accepted; Claude Code, Codex and Claude Desktop are covered by M42 |
| Registration is still a step | One setup command or the existing AI menu wraps it (§3) |
| Development-Use licence | Fine for authoring; production use already needs an agreement |

## 3. Proposed user experience

```
jbang app install analyser@telaminai/fluxtionauditlog-analyser   # once: JDK if absent, jar, `analyser` on PATH
analyser --setup-assistant                                       # once: wraps the M42 registration
```

(The setup flag name is illustrative; the AI menu already offers the same registration.) Then:

1. In Claude Code, inside the project folder, the person asks to see or change the project.
2. The assistant launches `analyser --mcp`. No window appears. **The project root defaults to the folder the
   assistant runs in** (Claude Code passes `CLAUDE_PROJECT_DIR`), so no paths are hand-edited. The spike proved this
   default (commit `e0af7a2`).
3. The assistant presents a snapshot of explicitly named files. The canvas opens in the browser directly
   (`--open-browser` in the spike), so the person does not have to open a five-minute link.
4. When asked, the assistant runs `Main`, sends inputs and stops it. When generation is needed, the person approves
   each one. Generated files are written by the Analyser, never retyped by the model.
5. When the assistant needs to explain a run, the Analyser window opens or attaches, and the existing `analyser_*`
   verbs (open the export, spotlight, flag) take over.

Skills ship as a small plugin, so the assistant has Fluxtion authoring guidance. The web test showed an assistant
without it improvises.

## 4. What the Analyser already provides (checked on `origin/main`, 1.40.2)

| Need | Already in the Analyser |
|---|---|
| One-line install including the JVM | JBang alias in `jbang-catalog.json` |
| A headless, dual-era MCP server | `analyser --mcp` → `McpBridge`. It answers both MCP eras (`2026-07-28` and the handshake revisions), sets `java.awt.headless`, touches no Swing/AWT class (`McpBridgeHeadlessTest`), and writes only MCP messages to stdout. |
| Registration with assistants | M42: `ClaudeMcpClient`, `CodexMcpClient`, `GenericMcpConfiguration`, connection probe |
| Driving the window | Bridge forwards `analyser_*` verbs to the running app's REST `/action` |
| Evidence views for a run | Topology, step-through, audit records, spotlight, Design, reports |

What it does **not** have:

- tools to present, run, send input to, stop or generate a project;
- a browser-facing server;
- a device-credential client;
- a way for the bridge to start the window.

## 5. Design

### 5.1 One MCP server, two tool families

`McpBridge` publishes the canvas tools (`playground_present`, `_status`, `_focus`, `_run`, `_input`, `_stop`,
`_result`, `_close`, `_generate`) beside the existing `analyser_*` verbs. One registration covers both. Canvas
tools are handled in the bridge process and do not need the window. `analyser_*` keeps forwarding to `/action`.

### 5.2 Where the Java runs: decide this first

The owner's wording allows either: *"with the Java executing in the browser or through the compiler"*. Option C's
description also promised a real local JVM. These are different products:

| | In the browser (CheerpJ worker, as the spike) | Local child JVM |
|---|---|---|
| First run | About 35 MB of libraries plus the CheerpJ runtime | None beyond the jar |
| Stop | Terminate the worker (proven in the spike) | `Process.destroy`, then force kill |
| Isolation | Browser process, but it can use browser services | **None.** Native process with the person's file and network access |
| Java limits | CheerpJ compatibility | Full JVM |
| Matches "reviewed code only" | Yes | Yes, but the consequence of running unreviewed code is larger |

**Recommendation:** a local child JVM for developers, launched from the person's own project with an explicit
approval per run, its working directory set to the project, and stdout and stderr captured. Keep the browser page
for inputs and output. Record the choice in the spec. Neither option is a sandbox, so say so in the UI.

### 5.3 Generation

- Use the builder with the same device credential as `mvn fluxtion:login`.
- The person approves each generation; credits belong to the person.
- The credential never reaches the browser or the model's context.
- Generated files are written by the Analyser into the project and presented as a new snapshot revision.
- The spike's generation-source rule (leave out the code that *uses* the processor; commit `8fffa95`) carries over.

### 5.4 The browser-facing server: a second security contract

Separate from the action socket, whose Origin refusal (F2) is unchanged.

- **Network:** loopback only, port allocated automatically, Host and Origin checks, and a custom header on every
  call.
- **Pairing:** a single-use invitation, then an HttpOnly session cookie scoped to the preview. A valid cookie must
  not be re-paired.
- **Files:** one selected project root, only explicitly named files, no symlinks, size and count caps.
- **Snapshots:** revision = SHA-256 over the sorted path and digest list. A local edit needs a new snapshot. The
  idempotency key must cover file **contents**, not just arguments. That was review bug 2.
- **Commands:** IDs are idempotent; conflicting reuse is refused; Stop has reserved capacity.
- **Output:** application-reported, labelled as such; paged by cursor to the end. That was review bug 6.

### 5.5 State and rule 9

The decision says *"State follows rule 9."* Previews, runs, jobs, approvals and expiry are session state. They go
through the generated session processor as nodes, with effects and facts, and the canvas and window render the
snapshot. Nothing is hand-dispatched. This is the largest design task, and the reason the port is not a
transliteration of the spike's `core.mjs`.

### 5.6 Window on demand

When an `analyser_*` verb arrives and no app is listening, the bridge starts the app (same jar, GUI mode). It
waits for `/action`, then forwards the call. If it cannot, it returns a clear error. Settle:

- **Consent.** Does the first launch need confirmation?
- **External access.** Should a launch from the bridge enable external access for that session only?
- **Single instance.** Attach to an open window; never open a second one.
- **Handover.** Opening a run's export should need no new pairing step.

## 6. The retired Node spike as reference

The spike is retired, but it is a working reference and a test suite. **Archive it before deleting the branch**,
and record the archive's location here.

Port, adapting to §5:
- the session protocol (`scripts/canvas/core.mjs`, the HTTP layer in `canvas.mjs`);
- the device sign-in and gated proxy (`scripts/canvas/account.mjs`);
- `playground_generate` with per-generation approval and follow-the-tab;
- the generation source selection (`src/lib/canvas/generation.ts`);
- the standalone canvas page build (`scripts/canvas/standalone/`), if the browser page is kept;
- the static asset server with SHA-256-checked runtime downloads (`distribution-http.mjs`), if Java runs in the
  browser.

Tests to turn into this repo's acceptance cases:
- `core.node-test.mjs`;
- `review-regressions.mjs`;
- the isolation, recovery and browser smokes;
- `native-host-smoke.mjs`, which runs the real Claude Code host.

Not carried over: the downloadable ZIP, the public `/connect-ai` page and its sidebar link, and the Node path in
MCP settings.

## 7. Risks and points to get right

1. **Slow first start.** MCP hosts time out slow server starts; Claude Code's limit is configurable but short by
   default. Prefetch during install or setup.
2. **stdout stays clean.** Already enforced for the bridge; the new HTTP server, child JVM and builder must log to
   stderr only.
3. **One bridge per assistant session**, so ports must be allocated automatically. The spike originally pinned 5173,
   and a second bridge died with only `Connection closed`.
4. **Third-party hosts.** Document every host for corporate proxies: the compiler, the JBang and Maven hosts, and,
   if the browser runtime is kept, `cjrtnc.leaningtech.com`. Bundle icons rather than calling `api.iconify.design`.
5. **Local JVM execution** (if chosen) is native execution. The approval wording, the UI and the docs must say so.
6. **Scope creep against the revised decision.** Brokered run and generation only: no runbook interpretation, no
   domain logic, no server mutation.

## 8. Repo rules this work must satisfy

- **Rule 6.** Read the live MCP specification (currently `2026-07-28`) before adding tools. `McpBridge` already
  answers both eras; keep it that way.
- **Rule 9.** See §5.5.
- **Rule 8, regression closure.** Each defect found in the spike reviews (§9) that the port could reintroduce needs
  a static or executable check in this repo's suite, not just a passing trial.
- **Rule 1, public repo.** Fixtures use demo content only. Canvas screenshots need the same generated, isolated
  capture as other docs images.
- **Decision record.** Add the §1.2 entry to `docs/specs/decisions.md` with the spec.

## 9. Evidence behind this proposal

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
- **Fix commit `6262920`.** All eight steps of the supplied end-to-end script passed. That covers present without
  execution, focus, run, four inputs with correct boundary results, cached retry, negative input refused, Stop,
  and a fresh JVM restoring the original limit. The seven defects were not retested.

**Reported by the spike's author, not verified by this reviewer.** Native Claude Code 2.1.316 completed the MCP
workflow twice, including from the extracted ZIP. A full generation loop on the owner's account also passed.

## 10. Suggested steps

1. **Archive and retire the spike** (§6). Record the archive here.
2. **Decide where the Java runs** (§5.2), and write the spec. Include the rule 9 design (§5.5) and the
   `decisions.md` entry.
3. **Spike in the Analyser:** `playground_present` and `_status` served from `--mcp`, with the project root
   defaulting to the assistant's folder. Measure cold-start time and jar growth.
4. **Port run, input, stop and result**, against the archived regression cases.
5. **Port generation:** device credential, approval and written files.
6. **Window on demand** (§5.6) and the skills plugin.
7. **First-user trial** on a clean machine: JBang install, one setup step, then the demo, observed rather than
   scripted.

## Open questions for the reviewer

- Local child JVM or browser runtime (§5.2)? The recommendation is the local JVM, but the owner's wording leaves
  both open.
- `--setup-assistant` as a new CLI flag, or only the existing AI menu?
- Consent model for opening the window (§5.6)?
- Is the browser page still needed if Java runs locally, or can the Analyser window host the inputs and output?
