# Runbooks, glossary and saved analyses — how an AI uses them

A project profile carries three things an AI acts on: **runbook pointers**, a **glossary** pointer and
**saved analyses** (*User guide ▸ Portable context*). This page is the operating manual for the AI side:
how the in-app assistant or an LLM connected over MCP **finds** them, **uses** them, and **creates or
updates** them — and the one thing it never does.

## The rule that shapes everything here

> anything in a profile that an agent will act on must be inert, or a pointer to something under
> version control — never an instruction the profile itself carries.

So the analyser stores **where** a runbook is (`ops/deploy.md`), never what it says, and it **never
opens, renders or runs** one. The AI does the reading and the doing, with its own tools and its own
permissions, from the repository it has cloned. That is also why there is deliberately **no action-socket
verb** to write a runbook: an agent that could both record a pointer and act on it is a loop with no human
in it. The pointer lives in a committed file, so it goes through the same review as the runbook.

## Finding them — `context`

Both agents read the same payload: the in-app assistant through its in-process actions, an external LLM
through `analyser_context`. With a project open it carries:

```json
"runbooks": [
  {"name": "deploy",  "path": "ops/deploy.md",  "resolved": "/work/quote/ops/deploy.md",  "exists": true,  "from": "project",
   "note": "a pointer — read the file from the repository; the analyser stores no instructions and executes nothing"},
  {"name": "restart", "path": "ops/restart.md", "resolved": "/work/quote/ops/restart.md", "exists": false, "from": "project", "note": "…"}
],
"vocabulary": {"path": "docs/glossary.md", "exists": true, "text": "# Glossary\n- **live**: …", "from": "project"},
"analyses":   [{"name": "spread breach", "rationale": "…", "parameters": [{"name": "log"}], "steps": ["open", "filter", "graph"]}]
```

`exists: false` is a fact, not a failure: the profile points at a file this checkout does not have. An
agent should say so rather than guess at the contents. The Project panel shows the same rows to the
person — a warning where the file is missing — so both parties are looking at one set of facts.

## Using a runbook

1. Read `context.runbooks`; pick the pointer by name.
2. **Read the file yourself** — `resolved` is the path on this machine — with your file tools.
3. Do what it says with your own tools or through the deployment side's own MCP surface. Nothing in the
   analyser will do it for you, by design (the analyser gains no server-side code; see *The build-with-AI
   loop*).

A prompt that uses this well:

> Read `context`. If a `deploy` runbook exists, read it and tell me the steps we would take to roll back
> the change in this log's time range. Do not run anything.

## Using the glossary

The glossary is the **one** pointed-at file whose contents the analyser reads — a glossary is read, a
runbook is acted on. The in-app assistant gets its text **first** in every *Explain* prompt, framed as
reference text (not an instruction), so `live` is read the way this system means it. An external LLM
gets the same text as `context.vocabulary.text`, capped at 16 000 characters with truncation announced.
An agent that does not use the built-in prompt should put it first too.

## Using a saved analysis

`context.analyses` is the **offer**. To run one:

```json
{"action": "open", "params": {"analysis": "spread breach", "bind": {"log": "/path/to/audit.yaml"}}}
```

Steps run through the action socket in order and stop at the first failure; the echo reports each step
and, if it stopped, how many did not run and that the earlier ones **have** changed the view. A parameter
with no value and no default refuses the run and names itself.

## Creating or updating one — write the files, not the socket

An agent creates a runbook the way a person does, because the profile is a committed file:

1. Write the runbook itself into the repository — `ops/restart.md` — with your file tools.
2. Add the pointer to `.analyser/project.fluxtion-settings`:
   ```properties
   runbook.count=2
   runbook.1.name=restart
   runbook.1.path=ops/restart.md
   ```
   The path must be **project-relative, no `..`, no spaces or shell characters** — anything else is
   refused when the project is next opened, with the reason in the status bar, and the entry is dropped.
3. Commit both. The analyser reloads the profile when the project is (re)opened — `open {project: …}`
   over the socket does it — and the Project panel gains the row.

The same steps create a glossary (`vocabulary=docs/glossary.md`) or a saved analysis (`analysis.N.*`,
whose steps must be analyser verbs with project-relative paths or declared `{parameters}` — see the
Portable context guide for the full gate). A prompt that does this well:

> We just walked through restarting the quote service by hand. Write that up as `ops/restart.md`, add a
> `restart` runbook pointer to the project profile, and show me the diff before you commit.

The human review step — *show me the diff* — is the point. The pointer and the runbook arrive together,
through version control, and the person who approves the commit is the person the rule protects.

## Write runbooks in the skill shape

A runbook file is ordinary markdown, and the analyser only ever needs its path — but write it in the shape
an AI harness already knows how to load as a **skill**: a frontmatter block with `name` and `description`,
then the steps.

```markdown
---
name: restart-quote-service
description: Restart the quote service after a config change; when to use, what to check first, how to verify.
---
1. …
```

Two things follow. A runbook pointer can target a skill file directly — `runbook.0.path=.claude/skills/restart/SKILL.md`
— so one file is both the team's runbook and a Claude Code skill, and any harness that reads that shape gets
it too. And the `description` is what lets a model decide *which* runbook is relevant before opening any
of them; a planned slice (M38.8) surfaces it in `context.runbooks[]` as `runbook.N.description`, so
runbooks written this way today need no rewriting then. Nothing in the analyser parses the frontmatter;
the convention just makes the two worlds the same file.

## What the analyser will refuse

- a runbook or glossary path that is absolute, uses `..`, is a URL, or looks like a command
- an analysis step that is not an analyser verb, opens or closes a **project**, or names a path outside
  the project
- a report destination shaped like a credential (a webhook URL is one)

Each refusal names the reason and is announced when the project opens; the sound entries beside it still
load. Nothing degrades silently.
