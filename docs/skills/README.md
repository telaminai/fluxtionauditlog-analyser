# Canonical skills — the set a generated project is seeded with

These are the skill files a Fluxtion bundle ships in `.claude/skills/`, and the source of truth for what
gets published to the website. They are authored here, reviewed here, and **baked into a bundle when the
playground generates it** — never fetched at runtime (spec-onboarding-example ▸ D-R3).

## Why they exist

A generated project should arrive with an LLM already able to run it, stop it, find its audit log and
replay a run, without the person having to explain any of that. `CLAUDE.md` gives an agent knowledge; a
skill gives it a **procedure**. The analyser reads the `name`/`description` frontmatter (M43) and serves
it in `context.runbooks[]`, so a model can pick the right one without opening all of them.

## Common + specialisations

| Directory | When it is selected |
|---|---|
| `common/` | **always** — any Fluxtion processor writing an audit log |
| `mongoose/` | the processor is hosted by a Mongoose server |
| `embedded/` | the processor runs in-process via `DataFlowConnector` |
| `spring/` | the graph is authored as Spring XML and compiled ahead of time |
| `replay/` (`common/replay-a-run`) | the project has a **real replay entry point** the bundle can name |

**Selection is by TEMPLATE, from `m19-skills/2`** (owner, 2026-08-30: *"skills added depending upon the
template the user selects"*, and *"we have common and specialisation"*). v1 took `common/` plus one **host**
tier, which is too coarse: a template like *spring + mongoose* is Spring-**authored** and
Mongoose-**hosted**, and the skills that differ by authoring style are not the ones that differ by host.

So: **`common` is always selected, and a template names the specialisations it wants.** Selection is
`common` + those. The index shape says so — `common` is a list, `specialisations` is a map.

There is deliberately **no classification** of a specialisation as host-or-style. Nothing selects on it, and
naming the axes invites a question the rule does not need to answer (*can a template have two hosts?*).

The mapping lives with the **template**, not as a list of template ids on every skill — otherwise adding a
template means editing every entry in this library.

`spring` means the same thing here as `appliesTo: "spring"` in `reference-set.json`. One word meaning two
things is how these drift apart.

**`m19-skills/2` is DRAFT and unpublished** (review C1). Its selected skills still carry `TODO(bundle)`
markers that only a generator can substitute, and the bundle gate refuses a surviving marker — so **v1
remains the published contract** until a real consumer proves substitution end to end. A draft also makes
no provenance claim: unlike v1 it pins no revision or hashes, because a pinned draft goes stale on the next
edit.

**Replay is a specialisation, not common** (review F3, 2026-08-30). `common/replay-a-run` carries a
required `TODO(bundle)` marker, and the accepted M19 bundle deliberately has no replay entry point — so
making it unconditional would either trip the fail-on-`TODO` bundle gate or ship a procedure that does not
exist. Only a template with a real replay entry point selects it. An earlier draft of v2 had it in
`common` and reversed that accepted decision.

A bundle still does not author its own set (D-R2). **v1 is byte-pinned and must not be edited** — it is
consumed by a released playground build; add a version instead.

## Where they go, and why the analyser does not care

A bundle writes its skills to `.claude/skills/` because that is the one layout a harness **auto-loads**
today; putting them somewhere neutral would help nobody natively while breaking the tool that does. When
another harness's convention is verified, the generator can write there too — nothing here changes.

**The analyser deliberately does not encode any of that.** A skill is recognised by its **file name**
anywhere under the project root, so `.claude/skills/x/SKILL.md`, `.agents/skills/x/SKILL.md` and
`ops/x/SKILL.md` are found on identical terms — pinned by a test, because a hardcoded list of vendor
directories would rank one tool above another and exclude every project that uses neither. It is the same
rule M42 set for MCP clients: the analyser never parses `~/.claude.json` or Codex's `config.toml` either.

**The harness-neutral channel is `context.runbooks[]`** (owner, 2026-08-30: *"the analyser context tells
the LLM which runbooks to load as a skill"*). Confirmed runbooks are served there with their descriptions,
so an agent is **told** what to load and never has to know a convention. That is what makes this work for a
harness nobody here has heard of — and it is why the location question is much less important than it
looks.

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

### The reference set has its own retrieval path

`reference-set.json` is **not** under the skills root — it lives on the analyser's classpath so the jar can
render it, and the canonical copy is therefore:

```text
https://raw.githubusercontent.com/telaminai/fluxtionauditlog-analyser/main/src/main/resources/reference-set.json
```

One file, two consumers: the analyser reads it from the classpath, the playground fetches it at
build/release time and vendors a snapshot exactly as it does for skills (D-R3 — never at runtime). A second
copy under `docs/` was rejected: it would drift, which is the failure this whole design exists to avoid.

When either published skill changes, update the index revision to the commit containing those new bytes
and update the parity test in `CanonicalSkillsTest`. A revision change without matching content, or
content drift without the matching revision/hash update, fails the analyser build.
