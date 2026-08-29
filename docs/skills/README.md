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

`name` and `description` are what the analyser reads. **`x-analyser-min-version` must have an enforcer or
it is a comment** (D-X9, review F4): the **bundle generator** checks it when assembling a bundle and
refuses a skill newer than the analyser the bundle targets. The analyser does not enforce it at runtime —
it does not fetch skills — so if the generator does not check it, nothing does.

## Overriding the source

`skills.source` selects where a generator takes these from — the canonical URL, a corporate mirror, a
local directory, or `none`. It is **machine-tier only** and is never read from a project profile, because
a profile travels between people and must not be able to redirect the instructions an agent reads
(D-R4). Whichever source was used is recorded in the project and shown.

The published canonical root for build/release tooling is:

```text
https://raw.githubusercontent.com/telaminai/fluxtionauditlog-analyser/main/docs/skills
```

Its versioned machine index is `m19-skills/1/index.json`. The index records the immutable analyser
revision of the selected source bytes. It intentionally publishes `common/load-audit-log` plus the
Mongoose host skill for the M19 bundle: `common/replay-a-run` is conditional on a bundle claiming a real
replay entry point, which this bundle does not, and the embedded tier remains explicitly not publishable.
The playground retrieves and commits a snapshot at build/release time; generated projects never fetch
this root.

When either published skill changes, update the index revision to the commit containing those new bytes
and update the parity test in `CanonicalSkillsTest`. A revision change without matching content, or
content drift without the matching revision/hash update, fails the analyser build.
