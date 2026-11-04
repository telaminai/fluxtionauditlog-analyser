# Set up with your AI assistant

!!! note "Supported automatic setup: macOS and the Claude Code terminal"
    Automatic setup is available for macOS with Claude Code in an interactive terminal.
    Automatic setup currently supports that host and OS only. Linux and Windows use the
    [manual install](../install.md). The manual route stays fully supported, for example for teams that do not let an
    assistant install software.

**Timing target:** from a Mac with nothing installed to your first audited run in under ten minutes.
One setup question, one reconnect and a browser sign-in (optional for setup; needed for processor generation, free).
The earlier production Manual-mode preview trial passed, but did not establish this timing target. Browser approval, downloads and the task affect elapsed time.

Ask your assistant to set the analyser up, answer **one question**, and it installs the analyser, opens it and
then checks its connection. You do not need to install Java or JBang first. Your host and operating system
may also ask for permission; setup asks again for changes outside the list you approved.

## Claude Code: the plugin

In Claude Code:

```text
/plugin marketplace add telaminai/claude-plugins
/plugin install fluxtion-analyser@telamin
```

After marketplace installation, if Claude Code asks, run `/reload-plugins --force` or start a new session
before setup. See [plugin reloads](https://code.claude.com/docs/en/plugins/cli-reference#reloads-that-change-mcp-tools).

Then say:

> **Set up the Fluxtion Analyser and connect it to this assistant.**

The plugin declares its own MCP server; setup does not add a user-scope registration. If an older
`fluxtion-analyser` entry exists, setup offers to remove just that entry with your consent and a private backup.
In the macOS Claude Code 2.1.316 interactive trial, explicit reconnect worked in the same session;
`/reload-plugins` alone did not reconnect the failed server.

## Claude Code without the plugin: the setup instructions

Give Claude Code this prompt:

> **Set up the Fluxtion Analyser and connect it to this assistant. Follow
> https://fluxtion-playground.dev/skills/analyser-setup/SKILL.md and ask me before changing anything.**

That route registers the connection in Claude Code's user configuration, and needs one restart
(`/exit`, then `claude --continue`) before the assistant can use the analyser's tools.

## What happens

In **Manual mode**, Claude Code asks before commands that are not already allowed. A remembered
`analyser-setup plan *` rule covers matching plans in that project; `analyser-setup apply *` is a separate
approval. Neither approves the other, and other commands may still prompt. The production trial needed
four setup command approvals plus one native MCP approval, with none on the sign-in rerun. Counts depend
on existing rules and the commands used. Your permission mode determines the prompts; setup does not change it.
See [Claude Code permissions](https://code.claude.com/docs/en/permissions).

1. **One question.** Before it changes anything, the assistant lists every change and where it goes (JBang, the
   analyser, its settings folder, the optional sign-in), and asks:
   *yes / yes, but without the Fluxtion sign-in / no*. **No** changes nothing.
2. **Install.** If JBang is missing, it is installed. When a usable Java 21 is not selected, JBang downloads one Java 21 (approximately 190 MB compressed / 335 MB on disk),
   used by JBang, setup and the analyser, and by your builds if you have no JDK 21+. An explicit
   JBang Java-version override can change the download. The analyser
   **1.40.3** is installed and its download is checked against a pinned checksum. You see progress as each step
   finishes. Installation may also add PATH, the j! alias and JAVA_HOME to shell startup files; the
   question discloses this because it can affect future Java builds. The Java-free production trial downloaded approximately 420 MB in total and used about 580 MB on disk, including the analyser. Sizes vary by platform and version.
3. **Open.** The analyser window opens with external access turned on. While the app is closed, setup records
   Claude Code as your AI client if no choice is saved. It preserves an existing choice. On the URL route, if the app is already
   open without a choice, use **AI ▸ Set up AI…**, pick **Your own AI client connects** (not **Claude inside**),
   then **Claude Code**. On the plugin route, the connection comes from the plugin: do not use that registration
   flow just to change the start-screen label. The label can still reflect a previous saved choice.
4. **Sign in (optional for setup; needed for processor generation, free).** Your browser opens on the Fluxtion compiler's sign-in page with a short code, which the
   assistant also shows you. Approve it there. A build credential, readable only by you, is saved in
   `~/.fluxtion/credentials` by default. Signing in charges nothing; free-plan limits apply. Skipping or
   declining sign-in leaves the analyser setup in place.
5. **Connect.** With the plugin, the assistant asks you to run
   `/mcp reconnect plugin:fluxtion-analyser:fluxtion-analyser` in the same interactive session. A sign-in-only
   rerun keeps an already confirmed connection. The URL route uses the restart described above.
6. **Confirm.** The assistant calls `analyser_context` itself and requires `ok: true`. Setup is complete only
   after this native call, not the setup program's diagnostic check.

Asking again later only adds what is missing, for example the sign-in, and never registers a second copy. If an
older `fluxtion-analyser` entry is in your Claude Code configuration, the plugin route offers to remove that entry
and keeps a backup.

## What the assistant reports

| Status | Meaning |
|---|---|
| Installed | the pinned analyser version is in place and checksum-verified |
| Open | the analyser is running |
| External access on | local clients can use its action API |
| Registered | URL route only: Claude Code has a user-scope server configured |
| Plugin declared | plugin server declaration checked; current session not inspected |
| Connected | the host bridge answered; the assistant's own check is still pending |
| Confirmed | the assistant itself called `analyser_context` and got `ok: true` |
| Signed in | a compiler build credential is saved; any validation limitation is reported (the analyser's Compiler account view uses a separate store) |
| Not signed in | no usable sign-in for the selected compiler; skipped, declined or refused |

"Registered" or "Plugin declared" is not "Confirmed": only a successful call from the assistant counts.

## Next: your first audited run

Ask for a project, for example:

> **Create a small Fluxtion project from the AOT starter, build and run it, and show me its audit in the analyser.**

This generation step needs compiler sign-in and uses its plan allowance. The assistant downloads
an audited [starter template](templates-and-examples.md), builds and runs it, then opens its audit beside the graph.

The starter writes `logs/audit-<processor>.yaml` as separate YAML documents for records published after Init;
Init remains on stdout. Open the untouched export beside its generated GraphML.

**No Java on your machine?** If no JDK 21+ is available, the assistant selects the same Java 21 that
JBang downloaded, separately for each build or run command. This Java selection does not change your
shell profile. To build outside the assistant, select this JDK with JAVA_HOME and PATH, or install a JDK 21 or newer.

## Next day: the analyser is closed

The plugin connects only to a **running** analyser. If you start Claude Code while it is closed, `/mcp` shows the
plugin's server as failed. Then:

1. Say **set up the Fluxtion Analyser** and review its plan. It normally opens the existing app and may refresh
   the saved Java path for the bridge. Any extra changes follow the same consent rules. Alternatively, open
   the installed launcher at `${JBANG_DIR:-$HOME/.jbang}/bin/analyser`.
2. Run `/mcp reconnect plugin:fluxtion-analyser:fluxtion-analyser`.
3. Ask the assistant to call its native `analyser_context` and require `ok: true`.

To run `analyser` from a terminal by name, ensure the actual JBang `bin` directory is on your `PATH`.
Installation may have added it; setup does not assume that it did.

Day-two recovery was exercised in an isolated macOS Claude Code 2.1.317 interactive session:
setup reopened the app and a native check succeeded after reconnect. Marketplace installation and production acceptance also passed in the 2026-11-04 macOS trial. The authoring bundle subsequently exposed a separate legacy preflight failure; a successful initial AOT trial does not certify graph-editing workflows.

## Already connected the analyser another way?

An earlier external Claude Code connection may have left a user-scope `fluxtion-analyser` entry. The plugin
provides its own connection, so setup checks for that entry and offers removal with your consent and a private
backup. If you decline, stop and choose which connection to keep; leaving both loaded can expose duplicate tools.

With the plugin installed, you do not need the analyser's own *Set up AI… ▸ Your own AI client connects ▸ Claude Code* to
connect Claude Code. If you use it anyway and it adds a `fluxtion-analyser` entry again, rerun plugin setup
to review removal with a backup. Alternatively, after confirming it is the extra user-scope entry, remove it yourself:

```bash
claude mcp remove --scope user fluxtion-analyser
```

If the old connection remains loaded in the current session, disable that user-scope connection in `/mcp`;
keep the plugin server, reconnect it and ask for its native `analyser_context` check.

**Tool names differ with the plugin.** They start `mcp__plugin_fluxtion-analyser_fluxtion-analyser__` instead of
`mcp__fluxtion-analyser__`. If your Claude Code settings allow the analyser's tools by name, or a script calls
them by name, add the plugin's prefix, for example:

```json
{ "permissions": { "allow": ["mcp__plugin_fluxtion-analyser_fluxtion-analyser__*"] } }
```

This allows every tool on that plugin server; setup does not add the permission rule for you. See
[Claude Code MCP permissions](https://code.claude.com/docs/en/permissions#mcp).

## Teams: pre-install the plugin

Administrators can pre-install the plugin for everyone through Claude Code's managed settings: add the `telaminai/claude-plugins` marketplace
under `extraKnownMarketplaces` and set `"fluxtion-analyser@telamin": true` under `enabledPlugins`. This is not
yet tested with this plugin, and requires the marketplace to be published. These keys are documented in
[Claude Code's organisation plugin controls](https://code.claude.com/docs/en/plugins/org#pre-install-and-require-plugins).
The marketplace key is its name, `telamin`:

```json
{
  "extraKnownMarketplaces": {
    "telamin": {"source": {"source": "github", "repo": "telaminai/claude-plugins"}}
  },
  "enabledPlugins": {"fluxtion-analyser@telamin": true}
}
```

See [Managed policy](../admin/managed-policy.md) for the analyser's own policy controls.

## What is not covered yet

- Linux and Windows: use the [manual install](../install.md).
- Assistants other than Claude Code: automatic setup is not supported; use the manual installation and
  [client connection instructions](../connect-an-llm.md).
- Managed pre-install remains untested. Marketplace installation and the production Manual-mode trial passed; local follow-up fixes need their own acceptance.
- Claude Code's desktop app and non-interactive runs (`claude -p`): plugin connections take effect in the next
  session.

## Privacy and undo

- Everything setup changes is listed in the question first, and each run keeps a private receipt in
  `~/.fluxtion-analyser/setup-runs/` saying what it did and how to undo it.
- The setup helper downloads tooling and the app, optionally signs in to the compiler, reads account usage and
  checks the local analyser connection. It does not upload project source files. Your assistant's own provider
  and permissions govern what it reads and sends.
- Later code generation sends a graph model and generation configuration, not Java source files. The model can
  include configured values, including private fields; do not put secrets in it. See
  [What is stored where, what leaves](../admin/data.md).


### Also use Claude inside the Analyser

Open **Set up AI** from the onboard **Assistant panel** (or its header dropdown), then choose
**Both** to keep your external client and add onboard Claude. Close an already-open setup window
before using this route. With an external client selected, the top **AI > Set up AI** menu can jump
straight to the external-client screen instead of the choice screen. Keep the plugin connection;
do not create another user-scope registration to complete the onboard setup.

The legacy **Fluxtion API key** display and `context.fluxtionKey` inspect only the old key file.
They do not inspect Maven's saved build sign-in in `~/.fluxtion/credentials`; an absent legacy key
is not evidence that a signed-in Maven build cannot authenticate.

## Upgrading from Analyser 1.40.2

Upgrade to Analyser 1.40.3 before creating projects from the updated starter catalogue. Version 1.40.2
does not understand the new build-sign-in declaration and may offer a failing legacy check as the
first step for an AOT starter.
