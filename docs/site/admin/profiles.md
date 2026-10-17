# Assistant profiles

A **profile** is how the assistant works on this machine: *Safe analysis*, *Full Claude Code*, *In-app assistant* or
*Off*. You choose one from **Assistant: \<profile\> ▾** in the assistant panel's header, and it is stored for this
user on this machine only. *Not chosen* is the state before anyone has chosen.

## Choose how the assistant works

**Assistant: … ▾** in the assistant's header (it names the profile in force, e.g. *Assistant: Safe analysis ▾*) shows four cards in the panel — not a chain of dialogs:

![The four cards in the assistant panel — Safe analysis (the default, selected, with its model), Full Claude Code, In-app
assistant and Off — the External MCP/REST access line under them, and Close / Apply — Safe
analysis](../assets/assistant-profile-cards.png)

| Card | What it sets up |
|---|---|
| **Safe analysis** (the default) | Claude Code that **reads your evidence; it changes what is open or writes a file only after you approve each call**: Read, Grep and Glob in the project folder and the analyser's own views. No shell, no edits, no web, none of your own skills or servers — safe with logs you did not write. **Not local-only:** what you ask, and the evidence Claude reads through the analyser, is sent to the model provider (Anthropic, on your Claude plan or API key), as in any Claude Code session. Its detail: Claude Code's model. |
| **Full Claude Code** | **Your own Claude Code** — your settings, skills, plugins, hooks and MCP servers, and your own tools under your rules. The card shows the warning in full and the **mode** (`default`, `acceptEdits`, `auto`, `plan`); its button reads **Turn on full Claude Code**. When Claude Code asks for approval, the analyser asks you ([Approvals](../inapp/approvals.md)). Like Safe analysis, it sends what you ask, and the evidence Claude reads, to the model provider. |
| **In-app assistant** | The analyser's own assistant: a **provider**, a **model** and an **API key** (left blank, a key already stored is kept). **Not local-only:** what you ask, and the evidence the assistant reads through the analyser, is sent to the model provider you chose, on your API key. |
| **Off** | **No assistant in the analyser.** No question is sent anywhere from the analyser; every view, chart, report and walk still works. A stored key is kept, unused. An external MCP or REST client you connected is **not** affected. |

Under the cards, one line says whether **External MCP/REST access** is on or off, with a pointer to *Settings ▸
Assistant ▸ External MCP/REST access*, where it is changed: the cards choose the assistant *in* the analyser, never the
external loop. Each card's line is shown in full — the card grows to fit it.

- **Choosing a card shows only its two or three details; nothing changes until Apply.** Apply writes the same settings
  *Settings ▸ Assistant* holds — on this machine only, never in a project, a shared settings file or a bundle — and puts
  the tab in that card's mode. *Settings ▸ Assistant* still has every option, grouped the same way.
- **The cards open by themselves once**: the first time you press **Start** or ask a question — in the panel or on the
  [start page](../user-guide/start-page.md#which-assistant-answers) — while nothing is set up on this machine. A question asked on the
  start page **waits for you**: after **Apply** it is placed in the composer of the assistant you chose, with **Start**
  ready for Claude Code — you do not press the button again, and nothing is sent until you press Start or Send. Close the
  cards instead and the question stays in the box. After a card is chosen (or if you close them), they open only from
  **Assistant: … ▾**. A machine that was already set up (Claude Code allowed, or an API key) counts as chosen and is never
  asked.
- **A change while an answer is in progress is refused**, by name — *"Claude is still answering — Stop or wait, then
  change how the assistant works."* — as a mode switch is.

## Where profiles are stored

The cards and *Settings ▸ Assistant* write the user's own `~/.fluxtion-analyser/config`, on this machine. A profile is
never read from a project, a shared settings file or an evidence bundle, so a cloned repository or a received bundle
cannot turn an assistant on. One related switch can travel: a shared settings file's *Assistant* category carries
*External MCP/REST access* and the in-app assistant's view switch ([Projects, profiles and
sharing](../user-guide/projects.md#what-is-kept-where)). What an administrator can and cannot enforce today is on
[Managed policy (planned)](managed-policy.md).

*Settings ▸ Assistant* groups its switches the same way as the cards: *Safe analysis*, *Full Claude Code*, *In-app
assistant*, and *Every assistant* for what all of them share (the bridge, files and projects).

## Set up the in-app assistant

The **In-app assistant** card (**Assistant: … ▾**) sets the provider, model and key in one step. Or open
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
