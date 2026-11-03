# Proposal: deliver the browser run canvas through the analyser's JBang install

**Status:** proposal for independent review. Nothing has been built. Raised 2026-11-03.

**In one line:** the playground's local browser canvas currently needs a repo checkout, Node 22, `pnpm` and a
hand-edited MCP config. The analyser already has a one-line JBang install, a headless `--mcp` stdio bridge,
and built-in MCP registration for the assistants. Serve the canvas from that bridge, and open the analyser
window only when the assistant needs it to explain something.

**Naming.** In this repo "canvas" already means the *shared evidence canvas*
([`spec-shared-evidence-canvas.md`](../specs/spec-shared-evidence-canvas.md), `CanvasHandoff`). This
document calls the playground feature the **browser run canvas**: a browser page where a person can inspect
a project snapshot, run its `Main`, send inputs and stop it. It is a presentation surface. It is not the
shared evidence canvas, and it does not replace it.

## 1. The problem

The browser run canvas is a local prototype in the playground repo (`fluxtion-web`, `web/scripts/canvas.mjs`).
For a first user to try it today, they must:

1. clone the web repo, install dependencies in `web/` (`pnpm install`), and fetch the browser library jars;
2. install Node 22 or later;
3. optionally run `pnpm dev` to reach the `/connect-ai` setup page;
4. paste a JSON entry containing absolute paths into their assistant's MCP configuration;
5. restart the assistant.

The prototype's own docs list a named-host installation, a distributable package and a clean-machine trial
as unmet release gates. Each step above is a place for a first user to fail.

Meanwhile the analyser is already installed with JBang
(`jbang-catalog.json`: alias `analyser`, Java 21+). The repo records native installers as spec'd and
withdrawn: "JBang is the install" (tracker ▸ Decisions, M41).

## 2. What the analyser already provides (checked on `origin/main`, 1.40.2)

| Need | Already in the analyser |
|---|---|
| One-line install including the JVM | JBang alias in `jbang-catalog.json` |
| A headless MCP server | `analyser --mcp` → `McpBridge`: hand-rolled JSON-RPC over stdio. It sets `java.awt.headless`, touches no Swing/AWT class (enforced by `McpBridgeHeadlessTest`), and writes only MCP messages to stdout. |
| Registering the server with assistants | M42: `ClaudeMcpClient` (runs `claude mcp add --scope … --transport stdio …`), `CodexMcpClient`, `GenericMcpConfiguration`, and a connection probe |
| Driving the window from an assistant | The bridge forwards `analyser_*` verbs to the running app's REST `/action` |

What it does **not** have:

- any `playground_*` tools;
- a way to serve the canvas page;
- a way to start the window from the bridge. Today the app must already be running, with external access
  switched on.

## 3. Proposed user experience

```
jbang app install analyser@telaminai        # once: JDK, jar, `analyser` on PATH
analyser --setup-assistant                   # once: registers the MCP server (wraps M42)
```

(Command and catalog names are illustrative.) After that:

1. The person asks their assistant to show a project.
2. The assistant launches `analyser --mcp`. No window appears.
3. The assistant calls `playground_present` with explicit relative paths. The bridge snapshots the files and
   returns a single-use local link: `http://127.0.0.1:<auto>/canvas?...`.
4. The person opens the link. Source, graph and README display. Nothing compiles or runs.
5. On request, the assistant calls `playground_run`. Java compiles and runs inside the browser tab. Inputs,
   results and Stop behave as in the prototype.
6. When the assistant needs to explain a run (open its audit log, spotlight a record, flag a finding), the
   bridge starts or attaches to the analyser window. The `analyser_*` verbs then work as they do today.

Network access is assumed. People already need it for the cloud compiler, and JBang needs it on first install.

## 4. Design

### 4.1 One MCP server, two tool families

`McpBridge` publishes `playground_*` alongside the existing `analyser_*` verbs. The person registers one
server once. The `playground_*` tools are handled **inside the bridge process** and do not require the app
window. The `analyser_*` tools continue to forward to `/action`.

### 4.2 The bridge serves the canvas

- **Static build.** Ship a production build of the canvas page and its worker (`runtime-worker.js` in the
  fix commit) inside the jar. Serve them from a loopback HTTP server with an automatically allocated port.
  Do not run a Vite dev server.
- **Port the session protocol to Java.** This means pairing and the single-use invitation; the HttpOnly
  session cookie; snapshot revision (SHA-256 over the sorted path and digest list); command IDs with
  idempotency; poll, ack and report; expiry; and the caps. That logic is `web/scripts/canvas/core.mjs`
  plus the HTTP layer in `canvas.mjs`.
- **Use the existing tests as the acceptance spec.** `core.node-test.mjs` and `review-regressions.mjs`
  become the cases the Java port must pass. Ideally they run against both implementations until the Node
  one is retired.
- **Keep the prototype's boundaries.** One selected project root. Only explicitly named files. No shell,
  no editing, no Git. Loopback only, with Host and Origin checks. Run only code the person has approved:
  this is not a sandbox for hostile Java.

### 4.3 Window on demand

When an `analyser_*` verb arrives and no app is listening, the bridge launches the app (same jar, GUI mode)
and waits for `/action` to become ready. It then forwards the call, or returns a clear error saying how to
start the app. Questions to settle:

- **Consent.** Opening a window is visible to the person. Does the first launch need confirmation?
- **External access.** Today, external MCP/REST access is a Settings toggle. Should a window launched by the
  bridge enable it for that session only?
- **Single instance.** If the person already has the analyser open, attach to it. Never open a second one.
- **Handover.** A run shown in the browser canvas should be openable in the analyser through `analyser_open`
  on that run's export, with no new pairing step.

### 4.4 Versions

The canvas page, its runtime library manifest and the bridge ship in one jar, so they always match. The
JBang alias should resolve a pinned release, as it does today, rather than a moving "latest" between the
browser assets and the bridge.

## 5. Risks and points to get right

1. **Slow first start.** JBang's first launch downloads a JDK. MCP hosts time out slow server starts (Claude
   Code's limit is configurable but short by default). The install or setup step should fetch everything, so
   the first `--mcp` launch is fast.
2. **stdout must stay clean.** The bridge already enforces this. The new HTTP server and canvas code must log
   only to stderr.
3. **One bridge per assistant session.** Each session starts its own bridge. Ports must be allocated
   automatically. The prototype originally pinned 5173, and a second bridge died with only
   `Connection closed`.
4. **Third-party hosts.** Opening the page currently contacts `cjrtnc.leaningtech.com` (Java runtime) and
   `api.iconify.design` (icons). Bundle the icons, and document the runtime host, the cloud compiler and
   the JBang and Maven hosts for corporate proxies.
5. **Jar size and release coupling.** Canvas fixes would ship on the analyser's release cycle, and the jar
   grows by the built page and worker.
6. **Two implementations during migration.** The Node prototype and the Java port must not drift. Run the
   shared regression cases against both, or retire the Node bridge as soon as the port passes them.

## 6. Repo rules this work must satisfy

- **Rule 6, read the live protocol first.** `McpBridge` already answers two MCP eras. New tools follow the
  same dual-era handling, checked against the live MCP specification rather than the prototype's SDK
  behaviour.
- **Rule 9, one dispatch model.** The canvas session state (previews, runs, jobs, expiry) lives in the
  headless bridge process, not the app. A reviewer should decide whether rule 9 applies there. If so, that
  state belongs in a generated processor with effects and facts. If not, record why. Anything the window
  shows about a browser run must come through the session snapshot, never a hand-placed refresh.
- **Rule 8, regression closure.** Each prototype defect in §8 that the port could reintroduce needs a static
  or executable check in this repo's suite, not just a passing trial.
- **Rule 1, public repo.** Canvas fixtures must use demo content only. Screenshots of the browser canvas need
  the same generated, isolated capture as other docs images.

## 7. What this does not change

- The browser canvas stays a presentation surface. Authoring, builds, tests and Git stay with the person's
  assistant and local tools.
- The shared evidence canvas, the `CanvasHandoff` rules and the `analyser_*` verbs are unchanged.
- Hosted generation still goes through the cloud compiler and its account rules. Running a supplied,
  pre-generated example uses no credits.

## 8. Evidence behind this proposal

Two reviews of the prototype on 2026-11-01 and 2026-11-03, both outside this repo. They used the official MCP
SDK client over stdio with real Chromium and the real CheerpJ runtime. No assistant host was registered,
because the reviewing session's config change was blocked.

- **Prototype at `fee7c0e`.** The approved order/limit demo worked end to end. Seven defects were found,
  including the fixed port (above), a stale snapshot returned for a repeated `commandId`, single-use links
  with no reissue, a run left `active` after its tab closed, expired previews still counting towards the
  cap, output truncated at 60,000 characters, and "Running" shown after a program crashed.
- **Fix commit `6262920`.** The canvas moved Java into disposable workers. All eight steps of the supplied
  end-to-end script passed: present without execution, focus, run to readiness, four inputs with the right
  boundary results, cached retry with no extra event, negative input refused, Stop, and a fresh JVM
  restoring the £100 limit. The seven earlier defects were **not** retested in that pass.

Full report: the reviewer's local note `fluxtion-playground-mcp-review-2026-11-01.md` (not in this repo).
Those findings belong to the web repo. They are summarised here only because they shape the requirements
in §4 and §5.

## 9. Suggested steps

1. **Decide:** accept or reject "the analyser is the canvas host" (this document).
2. **Spike:** serve a static canvas build from `McpBridge`, with `playground_present` and `playground_status`
   only. Measure the jar size increase and the cold `--mcp` start time.
3. **Port:** move the session protocol to Java against the shared regression cases. Add `run`, `input`,
   `stop`, `result`, `focus` and `close`.
4. **Window on demand:** settle the questions in §4.3, then implement launch or attach.
5. **First-user trial:** on a clean machine, JBang install, one setup command, then the order/limit demo,
   observed rather than scripted. This is the gate the prototype has not yet met.

## Open questions for the reviewer

- Should `--setup-assistant` be a new CLI flag, or should first launch of the window offer the existing M42
  registration?
- Should the window-on-demand launch need explicit consent each time, once, or never?
- Does the canvas page belong in this repo's build, or should it be built in the web repo and consumed as a
  versioned artifact?
- Is retiring the Node bridge an explicit goal, or do both remain supported?
