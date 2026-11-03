# Option C review — 3 November 2026

Reviewed proposal `f4e96ba2ffafc5c4ff3d7d88aef6b8bfcb9091cd` against Analyser `origin/main` / remote main `10841b2ad2f7b8ed99c29ac046e5b74e93c3a01f` (1.40.2). The branch contains seven documentation commits and changes only the 329-line proposal. Source references below are relative to `src/main/java/telamin/fluxtion/audit/analyser/analyser/` unless stated otherwise; they refer to that main SHA.

**Verdict: ready to become a spec after the named changes.** The assistant-owned build/run loop and Analyser-owned context, evidence and presentation are coherent and consistent with the September decision. The main correction is to unify setup without merging the built-in and external permission routes. Browser authentication and immutable presentation identity need explicit contracts before implementation. No running implementation of this new canvas was supplied, so the findings below are design gaps, not demonstrated production vulnerabilities.

## Required findings

### R1 — Major: shared registration does not mean shared permissions or session

**Proposal:** §13, lines 316–322.

**Checked:** `assistant/localagent/ClaudeCodeProvider.java:110` sets an explicit session ID/resume; line 123 supplies `--strict-mcp-config` in Safe analysis, while lines 127–128 always supply a private bridge configuration. Lines 198–210 bind that bridge to a per-instance endpoint file. `session/LocalAgentPosture.java:80` deliberately separates the hosted server name from the user's registration; line 283 excludes the user's registration in Full mode. `mcp/McpBridge.java:333` uses the hosted generation credential and tool-use ID, whereas external calls use a different token. `net/ActionServer.java:164` supports the hosted connection with external access off. The proposal's own premise that `propose` is hosted-only is correct (`McpBridge.java:281`).

**Consequence/inference:** replacing the private configuration with user registration either fails to expose the required tools or removes window binding and permission protections. Sharing a server entry cannot make two Claude processes one conversation.

**Change:** one setup experience, shared schemas/implementation, two explicit authorisation routes. Preserve Safe/Full behaviour, per-instance binding and hosted-only `propose`. Do not promise shared chat history. Any external `propose` design needs a separate permission and generation-binding contract.

### R2 — Major: broad setup needs ownership-aware undo and partial-failure rules

**Proposal:** §4, lines 113–130.

**Checked:** `mcp/McpRegistrar.java:78` says registration checks are explicit because `claude mcp get` can launch a configured bridge. Lines 101–129 execute command vectors and may stop partway through them. `mcp/GenericMcpConfiguration.java:19` only renders JSON; it does not merge or edit Desktop configuration. `docs/site/connect-an-llm.md:95` documents this distinction. `mcp/CodexMcpClient.java:135` implements replacement as remove followed by add, with a possible partial failure.

**Consequence/inference:** “remove them all” can delete a pre-existing or subsequently edited registration unless the installer tracks what it owns. Blind Desktop JSON replacement can lose unrelated servers. A successful CLI command is not evidence of a working host connection.

**Change:** record each successful owned edit, scope/profile, exact entry and prior access setting. Undo only unchanged owned entries; retain user edits and unrelated settings. Recheck conflicts before writes, fail closed on indeterminate status, preserve partial successes and retry only failed items. Discovery before consent must not run health checks. Desktop needs a reviewed merge writer or the existing copy-only fallback. Show precise commands/locations and security effects on the consent screen.

### R3 — Major: opening a URL is not browser authentication

**Proposal:** §6.3, lines 199–203.

**Checked:** `net/ActionServer.java:266` refuses every request carrying Origin; preserve that existing contract. The new browser server has no implementation. Cookies are not isolated by port: [RFC 6265 §8.5](https://www.rfc-editor.org/rfc/rfc6265.html#section-8.5).

**Consequence/inference:** the server cannot distinguish an OS-opened browser request from an uninvited navigation merely because the Analyser normally opens the page. A cookie issued to any initial visitor supplies no pairing boundary. A constant custom header is not a secret.

**Change:** require an unguessable, single-use, expiring invitation exchanged for a presentation-scoped session, with reissue/revocation. Define Host/Origin validation, CSRF protection, cookie policy and browser-session expiry; account for cookies crossing localhost ports. No invitation/token in logs, referrers or exported content. Distinguish unauthenticated static/bootstrap requests from authenticated data requests; ordinary navigation cannot attach a custom header. Do not expose the action token or create a general browser-to-`/action` proxy.

### R4 — Major: define authorised reads and inert rendering

**Proposal:** §6.3, lines 186–207.

**Checked:** `design/DesignFiles.java:9` explicitly says the project is not an implicit read grant. Lines 29–62 resolve against authorised roots; lines 94–100 recheck on each bounded read. The proposal says files are under a project root, but does not say who authorises that root or how presented content is rendered.

**Consequence/inference:** accepting an assistant-supplied root as permission can expand read access. Rendering supplied HTML, SVG, Markdown links or terminal control sequences as active content can escape the intended display-only role.

**Change:** preserve source-root authorisation; specify supported inert file types, canonical containment, regular-file checks, bounded reads and handling of symlink races. Render source/output as text and graphs through a bounded parser/renderer, with no supplied scripts, external resources or class loading. Use a restrictive browser CSP. Treat displayed content as data, never as commands or runbook instructions.

### R5 — Major: bind each run to a frozen revision and define stale results

**Proposal:** §§6.1, 6.3–6.4 and §8.5.

**Checked:** `session/SessionSnapshot.java:8` publishes immutable state; lines 15–17 identify log/graph generations. `session/SessionDriver.java:34` routes results through new event cycles and lines 48–52 explain operation IDs. The proposed digest is a good starting point but the run only names an output/result/log path.

**Consequence/inference:** a delayed run can otherwise appear beside newer source, and an overwritten log path can silently change what an old presentation refers to. Browser-close callbacks are not reliable evidence that a tab is still open.

**Change:** bind presentation ID, revision, run ID and application instance epoch; hash and display the same captured bytes. Qualify separately assistant-reported execution and log contents read by the Analyser. Reject late results after replacement/close, bind paging cursors to immutable output, and declare retention/resource limits. Represent browser presence as connected/disconnected/unknown using a lease, not an infallible “open” bit. Put these decisions in the generated session processor, not HTTP callbacks.

### R6 — Major: installation, registration and ability to build are different capabilities

**Proposal:** §§2–4 and §13.

**Checked:** the prototype branch `b36ee80dbaf8eee7181ce21457a2d069708cdaf2` documents the two zero-install JBang commands in `docs/site/start-with-claude.md:17` and `:23`. Its `AiSetupWizardFrameTest.java:225` exercises Build with Claude without enabling external REST; the test also checks that enabling/typing does not start a session. Safe analysis on main exposes read-only built-ins (`ClaudeCodeProvider.java:116`). Registering this display-only bridge does not itself supply editing, shell or Maven tools.

**Consequence/inference:** “Claude Desktop covered” must not imply that this MCP server grants a full local build toolchain. The new external setup must not silently change the prototype's internal-only Build route.

**Change:** document a host capability matrix. The authoring journey requires a host with separately authorised local file/build/run tools. Other MCP hosts can still use investigation/presentation. Keep the internal Build route operational without external registration. Specify the single-command installation journey, stable launcher, retries and actual host-call completion check; the original proposal currently describes a launch and consent screen, not the complete install contract.

### R7 — Minor: scope the no-execution/no-credential statements

**Proposal:** §2 line 73 and §6.2 lines 178–179.

**Checked:** `account/CredentialStores.java:32` selects keychain/secret-service/protected-file storage for the shipped compiler-account feature. The built-in provider launches Claude, and the MCP bridge is itself a headless Java process. `config/Runbooks.java:9` and `ui/MainFrame.java:709` correctly expose pointers, not contents.

**Change:** say the new presentation feature neither executes project code nor uses/exposes compiler credentials. Do not claim the whole Analyser holds no credentials or launches nothing. Explain that the assistant follows runbook pointers using its own file tools. “No second JVM” means no additional project-runtime JVM owned by the Analyser, not elimination of its existing bridge process.

### R8 — Minor: make display-only a product boundary, not a universal MCP limitation

**Proposal:** §6.3 lines 186 and 191–194.

**Checked:** [MCP 2026-07-28 transports](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports) disallow server-initiated JSON-RPC requests in the modern era, but explicitly note that older revisions allowed them. This bridge supports both eras. Notifications also exist.

**Change:** state that this feature deliberately provides no browser-to-assistant work queue or Run/Generate controls. Replace “no logic and no state” with “no authoritative application/session state”; rendering, navigation and connection state still exist in the browser.

## Recommendations for every §12 question

1. **Launch:** keep the existing rule for v1. A deliberate setup/launch command may open the app; a routine MCP tool call must not auto-launch it. Endpoint absence does not establish that no app exists: access can be disabled or another window can own it (`docs/site/connect-an-llm.md:131–149`). Auto-launch would require a separate instance-discovery contract.
2. **One route or two:** one setup UX and shared capabilities, two permission routes. Keep hosted-only `propose`, private binding and separate conversations. See R1.
3. **Canvas or window:** show presentations in the Analyser window first. Permit an optional browser mirror using the same snapshot when useful for screen sharing. Neither surface runs project code; a browser is not a prerequisite for adoption.
4. **Tools:** stay with `analyser_*`. Prefer `open` to put/close a presentation, `context` to read status and existing navigation idioms where they fit. `docs/specs/decisions.md:28–45` already rejects a redundant canvas verb. A new verb needs a demonstrated distinct concept; five parallel `canvas_*` tools are unnecessary.
5. **Pairing:** yes, an invitation or equally strong explicit capability exchange is needed for the browser mirror. Reissue must work without creating leaked presentations or forcing another install.

## Source and documentation audit

- **Bridge:** confirmed headless entry, stdio-only protocol output, modern and handshake revisions, forwarding to `/action`, separate hosted/external credentials (`McpBridge.java:53–59, 156–182, 329–348`).
- **M42/M77:** Claude Code and Codex use their CLIs; Generic renders a portable record; Desktop editing is new work. Setup completion and a real client's first call are already distinct in the connect guide.
- **September decision:** proposal §1.3 agrees with `docs/specs/decisions.md:5–14`. The superseded November 2 wording permits execution only as historical text; keep it clearly superseded.
- **Runbooks:** pointers and declared descriptions only. Existing `context` does not send the file's instructions.
- **Rule 9:** correct architectural direction. R5 and the spec's acceptance cases must turn it into a complete transition/effect/fact contract.
- **Zero-install prototype:** command forms match the prototype docs. This review inspected its source/test; it did not run its GUI or assert a new cold-install pass.
- **Archive:** GitHub annotated tag resolves to `848f330a7f1896a2f3e640d4977b9c2e8555beea`; PR #39 is closed, unmerged. Archived `docs/proposals/canvas-install-and-connection.md` contains the November 2 decisions and historical experiment claims. Referenced scripts exist under **`web/scripts/canvas/`** from the repo root. Historical test/size/licence observations were checked as recorded claims, not independently rerun or legally assessed.
- **Official client locations:** Claude Code user/local entries are in `~/.claude.json`, project entries in `.mcp.json`; retain CLI-based edits and actual profile scope ([Claude MCP docs](https://code.claude.com/docs/en/mcp), [settings](https://code.claude.com/docs/en/settings)). Codex defaults to `~/.codex/config.toml`, with trusted-project `.codex/config.toml`; shared configuration is not shared conversation state ([OpenAI MCP docs](https://learn.chatgpt.com/docs/extend/mcp)). Desktop's documented files are `~/Library/Application Support/Claude/claude_desktop_config.json`, `%APPDATA%\Claude\claude_desktop_config.json`, and `~/.config/Claude/claude_desktop_config.json` on the respective platforms ([local-server guide](https://modelcontextprotocol.io/docs/develop/connect-local-servers)). Do not overwrite a whole configuration using an example from a guide.
- **JBang:** zero-install bootstraps are documented; putting its commands on PATH is a separate setup operation. App installation supports an explicit name and Java version ([installation](https://www.jbang.dev/documentation/jbang/latest/installation.html), [app install](https://www.jbang.dev/documentation/jbang/latest/cli/jbang-app-install.html)).

## Checks performed and limits

`git status --short` was clean; `git diff --stat origin/main HEAD` showed one proposal, 329 insertions. `git ls-remote` confirmed the main and prototype SHAs above. GitHub API checked the archive tag, its target, PR state and referenced file paths. Rule-1 tracked-text sweep, using the four terms read from CLAUDE.md and exactly its two exclusions, returned **zero matches for every one of the seven commits**; HEAD has 5,691 tracked files. No screenshots changed. Live protocol/client references were opened, along with the framework reference and golden path. No credentials or production settings were read or changed.

No Maven, GUI or cold-install execution was needed to judge the original documentation-only proposal, and none is claimed as review evidence. Implementation must add static guards, executable boundaries and named wrong-result witnesses under rule 8. In particular: cross-window permissions, setup partial failure/undo, uninvited browser access, hostile rendered content, stale source/run pairing, final output paging and close/reopen races.

## Owner follow-up

After this review, the owner authorised specification edits, requested a single-command/LLM-guided setup journey and public setup skill, then requested separate branches, tracker entries and review handoffs. These are follow-up design/prototype changes, not changes to the reviewed SHA or evidence that the new canvas has shipped. The new spec must carry the disposition of R1–R8 and its own implementation acceptance gates.
