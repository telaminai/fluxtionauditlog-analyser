# Canonical skills — the set a generated project is seeded with

These are the skill files a Fluxtion bundle ships in `.claude/skills/`, and the source of truth for what
gets published to the website. They are authored here, reviewed here, and **baked into a bundle when the
playground generates it** — never fetched at runtime (spec-onboarding-example ▸ D-R3).

## Why they exist

A generated project should arrive with an LLM already able to run it, stop it, find its audit log and
replay a run, without the person having to explain any of that. `CLAUDE.md` gives an agent knowledge; a
skill gives it a **procedure**. The analyser reads the `name`/`description` frontmatter (M43) and serves
it in `context.runbooks[]`, so a model can pick the right one without opening all of them.

## The tiers

| Directory | For | Selected when |
|---|---|---|
| `common/` | any Fluxtion processor writing an audit log | always |
| `mongoose/` | a processor hosted by a Mongoose server | the bundle is a Mongoose deployment |
| `embedded/` | a processor run in-process via `DataFlowConnector` | the bundle embeds the runtime |

A bundle takes `common/` plus **one** host tier. It does not author its own set (D-R2).

## The rule these files must obey

**Describe the project's own entry points; never invent a CLI.** A skill saying *"run
`./scripts/run-server.sh`"* is true of the project that ships it. A skill that invents a command is
fiction that fails on first use, and an agent cannot tell the difference — it will simply report that the
tool is broken.

Where a step cannot be grounded without the host in front of you, the file says so in place rather than
guessing. A `TODO(bundle)` marker in a shipped skill is a bug; a marker here is an instruction to whoever
generates the bundle.

## Frontmatter contract

```markdown
---
name: start-server
description: One line saying WHEN to use this, so a model can choose without opening the file.
x-analyser-min-version: 1.12.0
---
```

`name` and `description` are what the analyser reads. `x-analyser-min-version` is ignored by the analyser
and read by humans and by the bundle generator: a skill written against verbs that did not exist in an
older analyser breaks silently otherwise (D-R3).

## Overriding the source

`skills.source` selects where a generator takes these from — the canonical URL, a corporate mirror, a
local directory, or `none`. It is **machine-tier only** and is never read from a project profile, because
a profile travels between people and must not be able to redirect the instructions an agent reads
(D-R4). Whichever source was used is recorded in the project and shown.
