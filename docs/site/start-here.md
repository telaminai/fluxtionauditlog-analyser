# Start here

The analyser is a **desktop canvas for Fluxtion audit evidence**. A Mongoose/Fluxtion processor records every cycle it
runs: the event that arrived, each node that reacted, in order, and what each one computed. The analyser opens that
record, and you, or an AI you connect, read it, filter it, chart it, step through it and report on it. What an AI finds
appears on the canvas as something you can click and check. The analyser does not run your application. Claude Code
can run commands on your computer only if you turn on *Full Claude Code*, and then only as your own Claude Code rules
allow.

## Pick your door

<div class="grid cards" markdown>

-   :material-lan-connect: **I have an incident, and I use Claude Code or Codex**

    ---

    1. [Install and run the analyser](install.md)
    2. [Connect your client](connect-an-llm.md)
    3. [Your first diagnosis session](diagnose/first-session.md)

    [:octicons-arrow-right-24: Diagnose an incident](diagnose/index.md)

-   :material-chat-question-outline: **I want to ask questions inside the analyser**

    ---

    1. [Install and run the analyser](install.md)
    2. [Choose an assistant profile](admin/profiles.md). *Safe analysis* is the default card.
    3. [Ask the assistant](inapp/ask.md)

    [:octicons-arrow-right-24: Diagnose in the analyser](inapp/index.md)

-   :material-hammer-wrench: **I'm building a Fluxtion application with Claude**

    ---

    1. [Install and run the analyser](install.md)
    2. [Set up a project with Claude](build/set-up-with-claude.md)
    3. [Validate the intent with evidence](build/validate-intent.md)

    [:octicons-arrow-right-24: Build with Claude](build/index.md)

-   :material-presentation-play: **I'm looking around, or running a demo**

    ---

    1. [Install and run the analyser](install.md)
    2. Press **1. Run me through a demo** on the [start page](user-guide/start-page.md). It needs no AI and no key.
    3. [Running a demo](teach/index.md)

    [:octicons-arrow-right-24: Demo and teach](teach/index.md)

</div>

**Administering it for a team?** Read [Security model and limits](admin/security.md) and the
[Settings reference](admin/settings.md). Every assistant switch is per user on each machine.
[Managed policy](admin/managed-policy.md) says what an administrator can and cannot enforce today.

## What you need

- **Java 21 or later.** [JBang](https://jbang.dev) is the easiest way to run it; a plain jar works too
  ([Install & run](install.md)).
- **Optionally, an AI.** Any one of these works: [Claude Code](https://docs.claude.com/en/docs/claude-code/setup)
  installed and logged in; Codex or another MCP client; or an API key for Anthropic or OpenAI.

Every AI is **off by default**. With the assistant *Off* the analyser is still the whole tool: every view, chart,
report, walk and evidence bundle works, and nothing in the analyser sends your evidence anywhere.

## The start page in one picture

The analyser opens on the start page when nothing is open. **Help ▸ Start page** brings it back.

![The start page: What would you like to do? with eight numbered entries, the box and its Send button; under it, Or open
directly with the Explore DEMO, Open, Create, Connect an assistant and Settings blocks, and Recent](assets/start-page.png)

| On the start page | Your door |
|---|---|
| *Connect an assistant*: **Codex**, **Claude**, **Other MCP client** · **7. Explain an audit log I already have** | Diagnose an incident |
| **7. Explain an audit log I already have** · **8. What can this tool do?** · your own question in the box | Ask inside the analyser |
| **2. Build a new application with me** · **4. Set up a project from my code** · **6. Which starter template suits what I'm building?** · *Create* | Build with Claude |
| **1. Run me through a demo** · **3. Teach me by building an example** · **5. Open an evidence bundle someone sent me** · *Explore DEMO* | Look around, or run a demo |

Choosing an entry sends nothing and opens nothing. Only the button does that.
[The start page](user-guide/start-page.md) describes each entry.

## Three things that are always true

- **Nothing you open goes to a model until you choose an assistant and press Send.** Choosing an entry, opening a log
  or a bundle, and playing a demo send nothing. Claude Code's **Start** contacts Anthropic to check you are signed in
  before your first Send, but sends no record content. Once you do ask, no profile is local-only: what you ask, and the
  evidence the assistant reads, go to that assistant's model provider. A client you connect from outside (Claude Code,
  Codex) sends what it reads to its own provider.
- **What an AI changes on the canvas, you can see.** Filters, charts, flags, selections and highlights happen in the
  window in front of you, and you can undo them. The calls that replace what is open, write a file or merge saved state
  are marked *destructive*, so your MCP client can ask you first. In *Safe analysis*, the analyser itself asks you
  about each of those calls.
- **An assistant writes a file only where you allowed it.** Screenshots, reports, reels and bundles are written only
  with *Allow assistant file exchange* on (off by default), only inside the exchange folder you chose, and never over an
  existing file. *Full Claude Code* is the one exception, by design: its own tools (shell, edits) follow your Claude
  Code rules, not the analyser's.

## Where help lives

- **This site** is the authoritative guide. Each page here is the reference for its subject.
- **Help ▸ User guide** in the analyser is a short in-app guide. When it and this site disagree, this site is right.
- **Prompt packs** are questions to start from: the start page's numbered list, or a pack you drop on it
  ([Prompt packs](start/prompt-packs.md)).
- **[Release notes](release-notes.md)** list what changed in each version.
