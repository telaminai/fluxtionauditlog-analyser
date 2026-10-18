# What is stored where, what leaves

The analyser is a single-user desktop tool. It keeps its own files under your home folder, writes into a project only
what a project holds, and sends nothing anywhere unless you choose an assistant or press a button that fetches.

## On this machine

| Where | What | Written when |
|---|---|---|
| `~/.fluxtion-analyser/config` | Your machine settings, in **cleartext**: the in-app assistant's provider, model and **API key**; Claude Code's switches, mode, model, spend limit and the folders you confirmed; External MCP/REST access, file exchange, project creation and the exchange folder; theme, window, recent files and search history; your no-project settings; the bins of recently deleted reports, walks and charts (with the definitions a later call replaced), from which they can be restored | whenever a setting changes |
| `~/.fluxtion-analyser/sessions/` | Session-recovery snapshots, one file per project: the paths of what was open and their SHA-256, and the view, including filters and flags | when you close a project or quit |
| `~/.fluxtion-analyser/rest-endpoint` | The listener's URL, its per-run token, the process id and start time. Mode 600 | while *External MCP/REST access* is on; removed when the window closes |
| `~/.fluxtion-analyser/demo/` | The DEMO log, graph and source, unpacked from the jar, and the sample project's profile | the first time you open the sample project, the tour or the demo |
| `~/.fluxtion-analyser/bundles/` | Disposable working copies of the evidence bundles you open | when you open a bundle |
| `~/.fluxtion-analyser/plugins/` | Log-reader plugin jars, which you put there yourself | never by the analyser |
| `~/.fluxtion-analyser/local-agent/` | A lock per Claude Code session, so two analysers cannot run one session | while a Claude Code session runs |
| your temporary folder | A copy of a log opened from S3 (`fluxtion-s3-*.log`), deleted when the analyser exits; and a private, empty scratch folder for Claude Code when no project folder applies (no project, the sample project, a bundle) | when Claude Code starts there |
| `~/.fluxtion/fluxtion.apiKeyFile` | The Fluxtion build key, for regenerating a processor; the analyser writes it and reports only whether it is present | when you use **AI ▸ Fluxtion API key…** |
| `<project>/.analyser/project.fluxtion-settings` | The project's settings and context: source roots, processors, saved charts, reports, walks, runbook and glossary pointers, environments, analyses, report destinations ([Projects, profiles and sharing](../user-guide/projects.md#what-is-kept-where)). No key, ever | as you edit the project |
| `<project>/CLAUDE.md` | Links to the Fluxtion authoring docs, only if you tick that option when creating a project, and never over an existing file | once, on creation |
| your MCP client's own configuration | The registration **Connect an AI client…** adds, by running `claude mcp add` or `codex mcp add` after you confirm; for any other client, nothing (you copy or save the record yourself) | when you register a client |
| the exchange folder you choose | Screenshots, report PDFs and CSVs, reels and evidence bundles an assistant writes, never over an existing file | only with *Allow assistant file exchange* on |

Claude Code keeps its own conversation history and settings in its own folders, as it does in a terminal; the
analyser never reads or stores your Claude Code credentials. The analyser keeps a conversation in memory, up to its
last 500 entries, and saves turns only when you add them to a walk.

**Protect the config file.** It holds the in-app assistant's API key in cleartext, readable by anything running as
you. A shared settings file, a project profile and an evidence bundle never contain it.

## What leaves the machine

Nothing the analyser does on its own sends your evidence anywhere. What does, and when:

| What | Sends | To | When |
|---|---|---|---|
| **Safe analysis** or **Full Claude Code** | what you ask, and the evidence Claude reads through the analyser | Anthropic, on your Claude plan or API key, as in any Claude Code session | Start contacts Anthropic (the sign-in check and the bridge connection) before your first Send, with no record content; each Send then sends your turn |
| **Full Claude Code**'s own tools and MCP servers | whatever they send | wherever your tools and servers connect | as your permission rules allow |
| **In-app assistant** | what you ask, the selected records' context and the evidence it reads | the provider you chose (or your base URL), with your API key | each Send |
| **Off** | nothing from the analyser | — | — |
| An **outside client** you connected | whatever it reads through the analyser | wherever that client sends it | as you use that client; *Off* does not affect it |
| **Open log from S3…** | your request for that object | your S3, through your local `aws` tool and its credentials | when you open it |
| **Starter templates** | a request for the catalogue, then for one template's archive | the Fluxtion playground, over HTTPS | only when you press *Show templates*, *Start building*, or use *New project from template…* |
| **An example entry** | a request for one archive | GitHub or GitLab, over HTTPS | only when you press **Download example…** and confirm |
| **Help and docs links** | the page address | your web browser opens the docs site | when you click one |

**No assistant profile is local-only**, Safe analysis included: *Safe* is about what Claude Code may do on your
machine, not where your words go ([Security model ▸ Not local-only](security.md#not-local-only)).

**Does it work offline?** Yes, everything except the assistant and the buttons above. With the assistant *Off* and
External MCP/REST access off, nothing in the analyser goes on the network unless you press one of those buttons.

## What you send yourself

- **A report's PDF or CSV** contains the evidence it shows, including record values and any narrative.
- **An evidence bundle** contains the whole log (or an excerpt), the graph, your saved charts, reports and walks, and
  your notes ([Evidence bundles](../evidence-bundles/index.md#what-is-inside)). It leaves out your source roots, keys
  and assistant settings.
- **A reel** contains pictures of your screen at each step.
- **A shared settings file** contains the categories you tick ([Sharing settings with a file](../user-guide/projects.md#sharing-settings-with-a-file)).

A log from a live system names real hosts, services and accounts. Treat anything made from it as a production
artefact.
