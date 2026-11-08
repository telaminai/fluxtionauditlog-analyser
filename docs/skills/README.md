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
| `common/` | **always** — any Fluxtion processor writing an audit log: `load-audit-log`, `guided-start` (the tour), `point-at-the-fault` (the SHAPE of a fault-finding runbook that ends by spotlighting the evidence it matched — the one a project's own runbooks copy; M64.8) |
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

**`m19-skills/2` is PUBLISHED (2026-08-30).** A playground generator consumed it end to end and deployed a
bundle from it, which is the evidence the draft was waiting on, so the provenance guarantee now binds: v2
pins the revision containing every selected byte plus a per-path `sha256`, and `CanonicalSkillsTest`
refuses a non-DRAFT index that pins nothing.

*Correction worth keeping:* while drafting, a test here held v2 back on the grounds that its selected
skills still carry `TODO(bundle)` markers. **That condition was wrong** and contradicted this file — a
marker *here* is an instruction to the generator, and the bundle gate refuses only a marker that
**survives into a generated bundle**. Held to marker-freeness, v2 could never have been published at all.

**Replay is a specialisation, not common** (review F3, 2026-08-30). `common/replay-a-run` carries a
required `TODO(bundle)` marker, and the accepted M19 bundle deliberately has no replay entry point — so
making it unconditional would either trip the fail-on-`TODO` bundle gate or ship a procedure that does not
exist. Only a template with a real replay entry point selects it. An earlier draft of v2 had it in
`common` and reversed that accepted decision.

A bundle still does not author its own set (D-R2). **v1 is byte-pinned and must not be edited** — it is
consumed by a released playground build; add a version instead.

## Contract `m19-skills/3` — versioned skills (beside `/2`, not replacing it)

`m19-skills/3/index.json` gives every skill its own **versions**, so a skill can be improved and published
without a starter or analyser release, and a project gets the newest version that is **compatible with its
own pins**, never simply the newest. It is additive: **`/2` is still published, unchanged, and is still what
every consumer reads** (the website's `vendor-skills.mjs` and the analyser's bundled `/starter-skills/` copy).
No consumer reads `/3` yet; those changes come later, one step at a time.

**Selection by template is unchanged.** `common` is always selected and a template names the
`specialisations` it wants, as in `/2`, except that both now list skill **names**, which key the `skills` map.
For each selected skill a consumer then picks a version:

```json
"run-mongoose-server": { "versions": [ {
  "version": "1.0.0",
  "revision": "<full analyser commit holding these bytes>",
  "files": [ { "path": "mongoose/run-mongoose-server/SKILL.md", "sha256": "<64 hex>" } ],
  "compatibility": { "analyser": "1.12.0", "authoring": "any", "host": "mongoose",
                     "mongoose": "*", "mongoosePlugins": "*" },
  "evidence": { "kind": "m19-skills/2-pin", "label": "...", "index": "m19-skills/2",
                "revision": "<same commit>", "date": "2026-11-07" }
} ] }
```

| Field | Meaning |
|---|---|
| `version` | Semantic Versioning 2.0.0 (`MAJOR.MINOR.PATCH`, optional pre-release). Unique per skill. |
| `revision` | The full analyser commit holding every byte of this version. Each version pins its own. |
| `files` | `{path, sha256}` per file: exactly one `SKILL.md`, and any supporting file beside it (`/2`'s `files` moved here). |
| `compatibility.analyser` | The minimum analyser version. It replaces the front-matter `x-analyser-min-version`, and must equal it while both exist. |
| `compatibility.authoring` | `java`, `spring` or `any`. |
| `compatibility.host` | `mongoose`, `embedded` or `any`. |
| `compatibility.mongoose`, `compatibility.mongoosePlugins` | Tested version ranges, on **separate** axes (never assumed equal). `*`, or space-separated comparators that must all hold: `>=1.1.3 <1.2.0`. No `\|\|`, `^` or `~`. A project that pins no Mongoose (an embedded host) matches only `*`. A pre-release pin (`1.2.0-SNAPSHOT`, `1.2.0-rc.1`) is inside a non-`*` range only when a comparator names a pre-release of the same `MAJOR.MINOR.PATCH`, so `<1.2.0` does not admit `1.2.0-SNAPSHOT`. |
| `evidence` | What gated the version: `kind: "gate"` with `tests` and `benchmark`, each `{label, passed, total}`, or `kind: "m19-skills/2-pin"` (naming `m19-skills/2` and the version's own `revision`) for a version carried over from `/2`. Always a `label` and a `yyyy-mm-dd` `date`. Evidence of any other shape makes the version not selectable, exactly as no evidence does. |
| `yanked` | Optional. `true` removes the version from selection without deleting it. |

**The rule.** The highest version that is not yanked, has evidence, and whose compatibility contains every
one of the project's effective pins. If there is none, the project gets **none** of that skill, and the
consumer says so; it never falls back to an incompatible version. **A version without evidence is not
selectable.** Anything that cannot be read (a malformed version, range or compatibility block) counts as
incompatible. The rule is `SkillRegistry.select` (`src/main/java/.../template/SkillRegistry.java`), a pure
function with its own tests.

**Today every skill has one version, `1.0.0`, which is exactly its `/2` pin**: the same revision, the same
digests, and that pin as its evidence. Its compatibility reproduces what `/2` already implied (the
specialisation's authoring or host, the front-matter analyser minimum). Every Mongoose and plugin range is `*`:
`/2` constrains neither, and a `/2` pin is evidence of what `/2` served, not of a tested range, so the test refuses a
range on a `/2`-pin version. `*` here means "unconstrained", not "tested on every version". The first tested range
arrives with the first version published under `gate` evidence (for `run-mongoose-server`, whose text describes
mongoose-plugins 1.0.43 and 1.0.44, that is where a range such as `>=1.0.43` belongs).

**What `CanonicalSkillsTest` enforces for `/3`:** the index is well formed (fields, semver, compatibility,
evidence, file placement); every digest matches the bytes at its version's revision; each version's
`SKILL.md` names its skill and agrees with its front-matter minimum; a version without evidence is
flagged not selectable, and every listed skill has a selectable version; and **`/2` and `/3` agree** — the
same `common`, every `/2` specialisation with the same skills (`/3` may add specialisations, as step 2 will), and
every `/2` pin present in `/3` as a selectable version at the same revision with the same digests. So while `/2` is
served, a `/2` re-pin must add or update the matching `/3` version in the same change, and yanking a version `/2`
still pins means re-pinning `/2` in the same change. A later `/3` version may move ahead of `/2`; `/3` must never
contradict it.

**To publish a new version of a skill:** add an entry to its `versions` with a higher `version`, the commit
holding its bytes as `revision`, the digests, its compatibility, and the `gate` evidence of its tests and
benchmark round. Never edit a published version's bytes or digests; yank it and add another.

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
`./run-server.sh`"* is true of the project that ships it. A skill that invents a command is
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

### Supporting files

A skill may tell a model to run a file that ships beside it — `spring/add-a-node/audit-compare.py` is the
first. In `m19-skills/2` such a file is listed under its specialisation's **`files`**, next to `skills`,
and is selected and hash-pinned exactly like a skill: a template that selects the specialisation gets the
file, at the same revision, verified by the same `sha256` map. A file must sit in the directory of a skill
its own specialisation selects, so a generator knows where it goes (beside that skill's `SKILL.md`).
`CanonicalSkillsTest` refuses a file beside a skill that no `files` list ships. `files` is additive: a
generator that reads only `skills` keeps working, and does not ship the file until it learns the field.

When either published skill changes, update the index revision to the commit containing those new bytes
and update the parity test in `CanonicalSkillsTest`. A revision change without matching content, or
content drift without the matching revision/hash update, fails the analyser build.
