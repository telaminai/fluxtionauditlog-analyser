# Assistant profiles

A **profile** is how the assistant works on this machine: *Safe analysis*, *Full Claude Code*, *In-app assistant* or
*Off*. You choose one in **Set up AI…**, or switch with the mode chip in the assistant panel's header (**Safe ▾**,
**◆ Full ▾**, **In-app ▾**, **Off ▾**), and it is stored for this user on this machine only. While nothing is set up
the chip reads **Set up AI…**.

## Set up AI

**Set up AI…** is the one place to choose how AI works with the analyser. It is the last item of the mode chip's quick switch,
first in the **AI** menu (which opens it at *Your own AI client* when one is set up), at the top of *Settings ▸
Assistant*, and on the start page's AI line. On a first run the start page asks the same question inline, as part of
the page: **How do you want AI to work with the analyser?** It is offered once — to a new installation and to one
upgraded with nothing set up alike; after that the page shows the one AI line, which keeps the same door.

- **No AI**: everything works without AI — records, topology, charts, reports, walks and the tour.
- **Your own AI client connects**: Claude Code, Codex or another AI app that can connect to the analyser, in your
  terminal, drives the analyser (see [Your own client, step by step](#your-own-client-step-by-step) below).
- **Claude inside the analyser**: the same Claude Code, run by the analyser in this panel.
- **Both**: Claude in the panel, and your terminal client too.

For **Claude inside**, the window first checks this machine by running `claude --version` and
`claude auth status --json`. No question and no evidence is sent; the checks run in an empty private folder, and no
e-mail or organisation from the answer is kept. Each check says what failed and has **Check again**. An API key is
reported as *uses an API key* and never as *signed in*: Claude Code checks a key only when it starts. You then choose
**Safe analysis** (recommended) or **Full Claude Code**. Full asks for the same confirmation as the Settings switch,
with *Cancel* as the default. The in-app assistant with your own API key is behind **Other options** on the first
screen; it is no longer one of the first choices.

### Your own client, step by step

For **Your own AI client** (and the second half of **Both**, after Claude inside) one screen takes four steps, each
saying what it checked:

1. **Which client?** Claude Code, Codex, or another MCP client (Claude Desktop included: copy the configuration).
2. **Let your client reach this window** — the *External MCP/REST access* switch. Turning it on asks first, with
   **Cancel** as the default: only programs on this computer that have this run's key can connect, the endpoint listens
   on 127.0.0.1 only, and a Full Claude Code session in the panel can use it too. The AI menu's checkbox, *Settings ▸
   Assistant* and the *Connect an AI client* dialog ask the same question; turning access off never asks. Beside the
   switch, the *Where data goes* picture shows the path it opens: your client's arrow is solid only while access is on.
   **Check connection**, under the switch, starts the bridge your client will start and asks it what is open here: *Bridge
   works now* proves this window can be reached through it (not that your client has registered it). With access off,
   it asks the same question first, and confirming turns access on and runs the check in one step.
3. **Register the analyser with your client.** The exact command is shown. **Register for me…** runs it only after a
   confirmation that shows the full command (*Cancel* the default); **I'll run it myself** copies it; **Check
   registration** asks your client. Nothing runs before you press one of them, and the analyser never reads or edits your
   client's settings file.
4. **Can your client see the analyser?** Start a new session of your client and ask it *Use analyser_context and tell me
   what is open.* When a call with this window's key arrives, the step says when, and the name the client gives itself
   — its own claim, shown as such. If another analyser window holds the connection, or access is off, it says so.

**Finish** is enabled once access is on (the last check is never required). **More options…** opens the *Connect an
AI client* dialog for the rest (project-scope command, remove, Claude Desktop's note); it is the only way to that
dialog. If you **Cancel** after this window turned
access on, access stays on and the closing line offers **Turn it off**. Choosing **No AI** while access is on offers a
ticked box, *Also turn off External MCP/REST access*.

Nothing is sent from the window. If a question is waiting — you asked from a record, the start page or the panel
before anything was set up — the last button reads **Finish, then send my question**: it closes the window and puts the
question in the composer of the assistant you chose, so pressing **Send** is the only step left. Otherwise a short summary suggests one first step. **Cancel**
applies nothing further; a question waiting in the start box stays there. *Not now* on the start page's block hides it
and leaves one line, *AI: not set up — Set up AI…*. Both the block and the line are remembered on this machine only.

## Choose how the assistant works

The **mode chip** in the assistant's header (it names the profile in force, e.g. *Safe ▾*) is a
quick switch — a short menu, not a set of cards:

> ✓ **Safe analysis** · **◆ Full Claude Code…** · **In-app assistant** · **Off** · ─── · **Set up AI…**

The profile in force is ticked, and said in words to a screen reader (*Safe analysis, in force*). Each row's tooltip
says what it sets up and where your words go. With nothing being answered, a switch that Claude Code must restart for
(Safe analysis → Full Claude Code) restarts it at once; while an answer runs, the panel asks *Settings changed — restart
Claude Code now?* with **Restart** / **Later**, as for a change in Settings.

| Profile | What it sets up |
|---|---|
| **Safe analysis** (the default) | Claude Code that **reads your evidence; it changes what is open or writes a file only after you approve each call**: Read, Grep and Glob in the project folder and the analyser's own views. No shell, no edits, no web, none of your own skills or servers — safe with logs you did not write. **Not local-only:** what you ask, and the evidence Claude reads through the analyser, is sent to the model provider (Anthropic, on your Claude plan or API key), as in any Claude Code session. |
| **◆ Full Claude Code** | **Your own Claude Code** — your settings, skills, plugins, hooks and MCP servers, and your own tools under your rules, in the **mode** *Settings ▸ Assistant* holds (`default`, `acceptEdits`, `auto`, `plan`). Choosing it opens the same confirmation as the Settings switch (the warning, *Cancel* the default) and nothing changes until you confirm; while it is showing, the menu's rows are disabled. When Claude Code asks for approval, the analyser asks you ([Approvals](../inapp/approvals.md)). Like Safe analysis, it sends what you ask, and the evidence Claude reads, to the model provider. |
| **In-app assistant** | The analyser's own assistant: a **provider**, a **model** and an **API key**. **Not local-only:** what you ask, and the evidence the assistant reads through the analyser, is sent to the model provider you chose, on your API key. |
| **Off** | **No assistant in the analyser.** No question is sent anywhere from the analyser; every view, chart, report and walk still works. A stored key is kept, unused. An external MCP or REST client you connected is **not** affected. |

- **A switch applies at once** — on this machine only, never in a project, a shared settings file or a bundle — and
  puts the tab in that profile's mode. It writes the same settings *Settings ▸ Assistant* holds; *Settings ▸ Assistant*
  still has every option (the models, Full's mode, the spend limit, the provider and key), grouped the same way.
- **A switch that cannot work as it stands opens [Set up AI](#set-up-ai) at the screen that finishes it**, and changes
  nothing: *Safe analysis* or *Full Claude Code* while Claude Code was not found on this machine opens *Claude inside*,
  which checks for it; *In-app assistant* with no API key stored opens *Other options*, at the key field.
- **With nothing set up, a Send, a Start or an Ask opens Set up AI**, every time, and the question waits for it. A
  question asked on the [start page](../user-guide/start-page.md#which-assistant-answers) — or from a record, a chart or the topology
  with *Ask the assistant* — is placed in the composer of the assistant you set up when you press **Finish**, ready
  for **Send** — you do not ask again, and nothing is sent until you press Send. **Cancel** and the start page's
  question stays in the box. A machine that was already set up (Claude Code allowed, or an API key) counts as set up
  and is never asked.
- **A change while an answer is in progress is refused**, by name — *"Claude is still answering — Stop or wait, then
  change how the assistant works."* — in the status bar, as a mode switch is.
- **External MCP/REST access is not part of the switch.** The switch chooses the assistant *in* the analyser, never the
  external loop: that is Set up AI's *Your own AI client* screen, the **AI** menu's checkbox, or *Settings ▸ Assistant ▸
  External MCP/REST access*.

(Earlier releases had four cards in the panel that opened by themselves on the first question; *Set up AI* and this
switch replaced them.)

## Where profiles are stored

Set up AI, the quick switch and *Settings ▸ Assistant* write the user's own `~/.fluxtion-analyser/config`, on this machine. A profile is
never read from a project, a shared settings file or an evidence bundle, so a cloned repository or a received bundle
cannot turn an assistant on. No switch travels: a shared settings file's *Assistant limits* category carries only the
in-app assistant's two action limits, and *External MCP/REST access* and the in-app assistant's view switch are set on
each machine ([Projects, profiles and sharing](../user-guide/projects.md#what-is-kept-where)). What an administrator can and cannot enforce today is on
[Managed policy (planned)](managed-policy.md).

*Settings ▸ Assistant* groups its switches by profile: *Safe analysis*, *Full Claude Code*, *In-app
assistant*, and *Every assistant* for what all of them share (the bridge, files and projects).

## Set up the in-app assistant

Set up AI's **Other options** sets the provider, model and key in one step (the quick switch's **In-app assistant**
opens it there when no key is stored). Or open
**Settings ▸ LLM** (provider, model, key and base URL; *Settings ▸ Assistant ▸ In-app assistant* holds its action
switch and limits):

- **Provider / model** — Anthropic (Claude) or OpenAI, and a model id.
- **API key** — stored locally in cleartext (single-user tool), and sent to the configured provider
  endpoint to authenticate requests. It is never included in a shared settings file.

For OpenAI, leave **Base URL** blank to use its standard API endpoint and enter an OpenAI API model ID
in **Model**. `OpenAI HTTP 404` can mean the model ID or a custom endpoint is wrong. `OpenAI HTTP 429`
means a rate, credit or usage limit was reached; check your API account's
[billing and limits guidance](https://help.openai.com/en/articles/5955604-troubleshooting-api-rate-limits-and-429-errors).
The analyser does not show the provider's raw error body because it may contain private data.
