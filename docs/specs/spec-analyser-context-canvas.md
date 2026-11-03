# Analyser context, presentation and assisted setup

**Status:** implementation spec r1, 2026-11-03; not implemented. Owner authorised this specification after the
option-C review, including a low-friction setup skill prototype. Review handoff: [brief](../handoff/handoff_2026_11_03_option_c.txt).
The historical [proposal](../proposals/browser-run-canvas-via-analyser.md) is superseded by this document.

## 1. Product boundary

The assistant authors, generates, builds and runs the application using its own tools. The Analyser supplies
project context, runbook pointers, investigation and presentation of externally produced results. It does not
execute project classes, interpret runbooks as commands, generate processors or become an application runner.
The existing compiler-account UI and built-in Claude provider remain; this feature neither reads their credentials
into a canvas nor uses them for generation. Its existing headless MCP bridge is not a project-runtime JVM.

Developers use option C. The retired Node/CheerpJ canvas remains archived. Evaluator option D is separate web work.

## 2. The adoption journey

**User request:** “Set up the Fluxtion Analyser so you can work with my project.” The assistant retrieves the
published setup skill, checks its release/support information and carries out the supported sequence.

The intended full experience is one bootstrap command, a concise consent screen, then an open Analyser and a real
assistant connection. Existing JBang/Java installations are reused when compatible. No Node, hand-edited JSON,
copied endpoint token, admin account or compiler sign-in is needed for setup and investigation.

### 2.1 Deliver the useful prototype first

The first prototype is a **setup skill using released Analyser 1.40.2**, not a new installer or new Java flags.
It may use more than one assistant tool call: the person makes one request. A documented JBang command provisions
Java and installs the app (including any disclosed JBang shell/PATH setup); the assistant opens it; the existing **Set up AI → Your own AI client** flow handles
consent/registration. The person may need to reload their assistant, then the assistant calls `analyser_context`.

This prototype intentionally retains the existing per-client setup UI. It does not claim to have implemented the
single consolidated consent screen, automatic Desktop config editing or the new presentation view. Its release
can precede the canvas only after an independent onboarding trial and the release gates in §10.

### 2.2 Full one-command setup contract

The subsequent installer invokes a version-pinned, published bootstrap using the documented JBang shell or
PowerShell entry point. The release supplies executable commands, release URL/checksum and supported OS/host
versions; a spec placeholder is never published as a working command. A proposed `--setup-ai` flag, if used, must
be implemented and tested before it appears in a released skill. It is absent in 1.40.2.

The sequence is:

1. Bootstrap JBang and Java 21 in user-owned locations if needed; explain network downloads and JBang's trust
   prompt. Honour existing JBang configuration, including `JBANG_DIR`; no hidden shell-profile/PATH edits.
2. Open the setup UI in the Analyser. Discover installed hosts using file checks only. Show the supported exact
   install/registration actions and destinations, existing conflicts and any unsupported host.
3. One **Install and connect** confirmation covers individually deselectable actions: durable Analyser launcher,
   selected MCP clients, versioned authoring skills and external access. The last item explicitly says it permits
   local external clients to read/change the open Analyser through its existing API; it is not read-only.
4. Install the durable launcher through JBang's supported app mechanism, then register absolute argument vectors
   that remain valid after shell exit/reboot. Reuse the same resolved command for registration and bridge probe.
   Never leave MCP pointing into a temporary download directory or a cache entry that the installer will delete.
   Test transitive executable resolution: JBang app launchers may call `jbang` by name even when their own path is
   absolute. A GUI host without the new shell PATH must still have a verified supported launch route.
5. Leave the Analyser open. Show **Installed**, **Registered**, **Bridge checked**, then **Assistant connected**
   as distinct observations. The final state requires an actual host `analyser_context` call to this window,
   excluding the setup probe. A host reload is a normal, clearly explained handoff, not a setup failure.

Cancellation before confirmation makes no registrations, enables no external access and starts no AI session.
Bootstrap downloads already made are identified as retained caches. Retry after a partial failure resumes only
unfinished approved actions; an interrupted or failed registration is not reported as success. The current 1.40.2 wizard retains already-enabled external access when cancelled; it offers to turn it off
when this wizard enabled it, while preserving pre-existing access;
the prototype must state that rather than promise rollback. A network/auth/
policy failure gets its exact stage and supported next step, not a workaround that weakens host policy.

Installing skills is not permission to execute project code. Actual authoring follows the assistant's normal
workspace, shell and generation-account permissions. No model API key is required by the Analyser setup skill.

### 2.3 The public setup skill

The web prototype owns `web/static/skills/analyser-setup/SKILL.md`; proposed public URL:
`https://fluxtion-playground.dev/skills/analyser-setup/SKILL.md`. It is **not live until released**.
Keep the skill short and provide a human-readable entry/link from the playground when release gates pass.
Do not add an active feature link before that route is deployed.

The skill explains supported platforms, exact pinned commands, existing-install detection, consent, registration,
reload, a real host call, bounded recovery and a receipt. It never asks the LLM to invent a shell installer, edit
unrelated configuration, read endpoint secrets into a prompt or claim the canvas is shipped. A retrieved Markdown
file is guidance, not automatic host installation or an override of the person's permissions.

Publish one maintained copy with a tested Analyser version. A broader authoring-skills package is separate from
this installation skill. Keep a release compatibility table and invalidate stale instructions when CLI semantics
change. Fetching a skill does not itself register an MCP server.

## 3. Registration, ownership and host capabilities

The single screen may preselect detected supported hosts as the owner requested, with each selection visible and
deselectable. Never execute `mcp get` during passive discovery: that command can launch a configured server.
After explicit consent use official CLIs for Claude Code/Codex, not direct edits to their settings files.
An indeterminate lookup is a conflict, not an absent entry. Do not silently replace same-name registrations.

| Host | Setup | Capability boundary |
|---|---|---|
| Claude Code | Existing CLI, user scope; show resolved executable/profile | Own tools perform edits/build/run under its permissions |
| Codex | Existing CLI; preserve actual configuration/profile scope | Own workspace/shell permissions still apply |
| Claude Desktop / generic | Existing copy/save flow first; automated JSON merge only after dedicated tests | Analyser tools provide context/display; local editing/build tools require separate support/consent |
| Built-in Claude | Existing private per-window connection and Safe/Full controls | Safe analysis keeps read-only built-in file tools; hosted Analyser mutations retain their existing approval gates. Full is separately enabled |

Track an installation receipt per action: component/version, client/profile/scope, exact non-secret entry written,
previous access state and result. Undo removes only entries/files still matching what this setup installed.
Pre-existing entries, user modifications and unrelated servers survive. Never restore an entire stale config backup.
Partial success remains visible. Do not delete shared JBang/Java installations as “undo”; remove only the owned
Analyser launcher/skills when appropriate. Unsupported or policy-controlled config gets a supported fallback.

Desktop merge implementation, if added, preserves unknown JSON keys, checks for concurrent modifications and
writes atomically. No guessed Windows/macOS/Linux path or profile; confirm the documented location and supported
client version. CLI registration completion alone never marks a model session connected.

## 4. One setup, two permission routes

Keep shared schemas and implementations, but retain the hosted bridge's per-instance endpoint and generation
credential, tool-use-bound approvals and private server name. Safe analysis retains strict MCP configuration;
Full retains its current permissions and excludes the ordinary external registration to prevent a bypass.
Keep `propose` hosted-only. No shared conversation is promised between the in-app panel and an external terminal.
The prototype's internal Build route continues to work without external registration or enabling external REST.

Routine MCP calls never launch a new Analyser. The explicit setup/launch command can open it. A missing endpoint
may mean external access is off, not that the application is absent. Preserve the current “MCP elsewhere” behaviour
and identify which window serves the caller. Do not silently attach to another project or turn access on.

## 5. Presentation surface and verbs

Start with an Analyser **Presentation** view. An optional browser mirror renders the same published snapshot;
it is a later slice with its own security gate. The view contains source/files, graph, externally reported output
and results, and links into existing evidence views. Rendering/navigation are permitted; Run, Generate, Stop,
shell execution and a browser-to-assistant work queue are not features of this display.

Prefer extensions of existing `open` for setting/closing a presentation and `context.presentation` for status.
Reuse source/spotlight navigation only where their semantics match. Design exact typed schemas before coding;
do not add a parallel `canvas_*` namespace. Any necessary new verb must clear the existing “new concept” rule and
update schema, permissions, docs and verb-count tests together. The existing shared evidence canvas is unchanged.

Every result identifies the actor/source of the claim. “Application-reported” is not execution attestation.
Reading an audit file establishes what that file contains, not that its producer actually executed the supplied
source. Preserve existing graph/log pairing and provenance qualifications instead of implying stronger evidence.

## 6. Frozen content and identity

Accept explicitly named supported files only, under authorised roots. Project membership is not an implicit read
grant. Canonicalise paths, reject traversal/symlinks and non-regular files, revalidate on bounded reads and handle
replacement races. Enforce per-file, aggregate and file-count limits before large allocations.

Freeze the bytes used for both hashing and rendering. Revision is SHA-256 of a versioned, unambiguously encoded,
sorted path/content-digest manifest. Repeating a request ID with different captured content is a conflict; an
identical retry returns the existing result. Local edits require a new presentation; there is no background edit
discovery. An assistant cannot overwrite another presentation just by reusing a display label.

Bind each externally supplied run to presentation ID, revision, run ID and Analyser instance epoch. Record reported
exit/result status and claimed artefact locations distinctly from Analyser-read artefact identities. A mutable
log filename is not stable run identity. Replacing, closing or changing project supersedes pending operations;
late facts cannot resurrect old state or pair an old result with new source. Cursors bind to an immutable output
identity and carry an explicit end marker, with bounded pages and a complete retrieval path.

The initial implementation permits one active presentation per window. A second owner's attempt is refused with
a clear busy/conflict response, not silently substituted. Define resource caps and retention in the implementation
schema, including reaping closed/expired entries and a recoverable path after a cap is reached.

## 7. Browser mirror contract

Loopback binding with an allocated port; exact allowed Host/Origin policy and no wildcard CORS. Preserve the action
socket's blanket Origin refusal. The browser never receives its token, hosted credential or a general action proxy.

Opening the mirror creates an unguessable, expiring, single-use invitation. Exchange it for a scoped session; support
reissue and explicit revocation without leaking presentations. Opening the root URL alone grants no access.
Keep secrets out of logs, referrers, browser history where practical and exports. Define the bootstrap/static routes
separately from protected data routes; browser navigation cannot send a custom request header.

Use a reviewed session/CSRF design with exact-origin checks and per-instance request protection. Do not rely on a
cookie being isolated by port: localhost services share cookie scope. Define HttpOnly/SameSite, expiry, no-store,
referrer policy and authenticated event-stream rules. Browser presence is a renewable connection lease; a lost tab
becomes disconnected/unknown. An `openBrowser` success is not proof that a page rendered.

Render source, output and Markdown as inert text/sanitised structure. Do not execute supplied HTML/SVG/JavaScript,
load project classes, follow external graph resources or evaluate terminal escape sequences. Bound graph parsing
and layout. Bundle assets in the jar, use restrictive CSP, and make no third-party requests. Navigation links are
typed, allowlisted evidence operations, never arbitrary `file:`, `javascript:` or shell links.

## 8. Rule 9 implementation

Session nodes own setup progress, presentation identity, shown run, current view, ownership, invalidation and
connection leases. Effects perform install/registration, bounded file reads, display publication and page opening.
Facts report completion/failure with operation and generation IDs. Registration/file/network adapters decide no
session transitions. The window, browser and `context` render one immutable `SessionSnapshot`.

Use the existing SessionDriver dispatch and effect boundary; do not invent another event loop. Generate the
processor for new nodes. Installation subprocesses are infrastructure effects, not application builds. No Java
domain code or runbook command is ever executed by the new feature.

## 9. Review disposition

| Review finding | Spec treatment |
|---|---|
| R1 shared registration/session | §4 preserves two permission routes |
| R2 undo/partial failures | §§2–3 ownership receipts and explicit outcomes |
| R3 browser bootstrap | §7 invitation and separate browser security contract |
| R4 file/render trust | §§6–7 authorised reads, immutable bytes and inert rendering |
| R5 stale run/source | §§6–8 revision/epoch/run binding and generated-state ownership |
| R6 host capability/install gaps | §§2–3 staged adoption and capability matrix |
| R7 broad credential claims | §1 scopes the feature boundary |
| R8 protocol/no-state wording | §§5, 7–8 describe deliberate display-only policy |

These are design dispositions, not closed implementation findings. Rule-8 tests below must exist before a feature
is called complete. The original review remains pinned to `f4e96ba2`.

## 10. Delivery and acceptance gates

**C0 — Setup skill prototype (web branch, current release).** Publish only after a fresh-host trial: no JBang/Java
initially, supported bootstrap succeeds, app opens, user consents, native assistant registers/reloads/calls context.
Also test existing installation, existing same-name entry, declined consent, interrupted download, registration
failure, rerun and a host without reload support. Record exact OS/client/release versions and pass/fail/blocked.
Do not count an SDK bridge probe as a native assistant trial. The prototype may explicitly support only platforms
actually tested; do not delay useful supported platforms by implying untested ones work.

**C1 — Consolidated setup.** Preserve internal-only Build and Safe/Full guards; test explicit consent, no side
effects on Cancel before confirmation, accurate partial-state disclosure after cancellation, passive detection,
profile scope, conflicts, partial retries and ownership-aware undo. Named
wrong-result witnesses must demonstrate that pre-existing config survives and a real host call, not a probe,
sets Connected. No change to action-socket permissions or automatic model startup.

**C2 — Window presentation.** Demonstrate freeze/hash consistency, revision-conflicting retries, late completion
after close/project switch, another assistant's attempted replacement, final output-page retrieval, modified audit
path and resource-limit recovery. Verify source/graph/result navigation on a real window, including error states.

**C3 — Optional browser mirror.** Add executable refusal tests for uninvited/reused/expired invitations, foreign
Origin/Host, wrong-instance tokens, untrusted markup/graph resources, traversal/symlink replacement, stale leases,
tab loss/reissue and cross-window access. Inspect generated demo screenshots on desktop/mobile. Confirm no
third-party traffic and no project execution routes. Rerun the action socket's F2 refusal checks unchanged.

For each implementation slice: static architecture/schema guards, executable boundary test and named wrong-result
witness, relevant ordinary tests and the repository's CI gates. Track pending tests honestly. This documentation
change implements none of C1–C3 and makes no claim that those tests already pass.

## 11. Source references

- [Standing decisions](decisions.md), especially 2026-09-19 and the existing canvas verb decision.
- [Current client connection guide](../site/connect-an-llm.md).
- [AI setup spec](spec-ai-setup-wizard.md) and [shared evidence canvas](spec-shared-evidence-canvas.md).
- [JBang installation](https://www.jbang.dev/documentation/jbang/latest/installation.html) and
  [app install](https://www.jbang.dev/documentation/jbang/latest/cli/jbang-app-install.html).
- [Claude MCP](https://code.claude.com/docs/en/mcp), [Codex MCP](https://learn.chatgpt.com/docs/extend/mcp) and
  [Desktop configuration](https://modelcontextprotocol.io/docs/develop/connect-local-servers).
- [MCP transports and compatibility](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports),
  [cookie port boundary](https://www.rfc-editor.org/rfc/rfc6265.html#section-8.5).
