# Portable context — the project as a shared workspace

A project profile started life as *settings*: source roots, Maven repos, event processors. It now also
carries saved graphs, named focuses **with the reason each was saved**, investigation reports and — as
of this milestone — pointers to the team's runbooks. Nobody *configures* a rationale. That is a
**workspace**: what the team knows about this system, portable between a person and an AI working on
the same log, and between one machine and the next.

Naming it correctly changes what is allowed in. This page is the rule.

For the AI side — how the assistant or an LLM over MCP finds, uses and creates these — see
[Runbooks, glossary and saved analyses with an AI](../ai-and-runbooks.md).

## Three tiers, decided by whether the stored thing EXECUTES

| Tier | What it is | Examples | Travels in a shared profile? |
|---|---|---|---|
| **1 · Facts** | Inert statements about the system | vocabulary, environments, artifact pointers, runbook *locations* | **Yes** — this is what sharing is for |
| **2 · Analyses** | Sequences of **analyser** verbs | saved graphs, focuses, reports, repeatable analyses | **Yes** — a saved analysis can only drive a viewer, because server verbs never appear on the analyser's action socket |
| **3 · Runbooks** | Knowledge that causes something to happen elsewhere | build, deploy, restart, pull-logs | **Never as payload** — only as a pointer |

## A runbook is a pointer

The profile records *"the deploy runbook for this system is `ops/deploy.md`"*, relative to the project
root. It does **not** record the commands.

> anything in a profile that an agent will act on must be inert, or a pointer to something under
> version control — never an instruction the profile itself carries.

Why it matters: profiles move by email and in repositories (*Sharing setups*). If a profile could carry
instructions, opening a colleague's project with an agent attached would execute text written by
whoever sent the file. With a pointer, the executable content stays in your repository — reviewed,
diffed, attributable, revertible — and the trust boundary becomes *"you cloned this repo"*, which every
developer already evaluates, instead of *"you opened a file someone sent you"*, which nobody does.

**What the analyser does with a pointer: shows it.** It never opens, renders or runs the runbook. An
agent that wants to deploy reads the file from the repository with its own tools; the analyser only says
where it is.

### Recording one

In the profile itself — `.analyser/project.fluxtion-settings`, committed with the repository:

```properties
runbook.count=2
runbook.0.name=deploy
runbook.0.path=ops/deploy.md
runbook.1.name=pull-logs
runbook.1.path=docs/runbooks/pull-logs.md
```

That is deliberate, and it is the whole point of the tier: the file that says *where the runbooks are*
is reviewed, diffed and attributed like the runbooks themselves. There is no action-socket verb for it —
an agent recording pointers into a profile would be the one unreviewed path into a reviewed file, and the
analyser's verb surface is a compatibility surface that grows only when a verb has no home in an existing
one (`open` absorbed close, discover and project for the same reason).

The value is validated **at every entrance** — the profile loader, share import and share export — and
refused **with a reason** when it is anything but a plain relative path inside the project:

- absolute paths, drive letters, `~`, URLs
- `..` anywhere in the path
- spaces, quotes, `$`, `;`, `|`, `&`, `<`, `>`, `*`, `?`, backticks, line breaks — everything a command
  line needs
- more than 200 characters

Opening a project whose profile carries such an entry says so in the status bar — *"⚠ runbooks: 1 entry
REFUSED — not a project-relative path: …"* — and the pointer beside it still loads. A share file that
carries runbook *contents* imports the same way.

### Write it in the skill shape

The analyser needs only the path, but write the runbook file the way an AI harness loads a **skill** —
frontmatter `name` and `description`, then the steps (see *Working with AI ▸ Runbooks, glossary and saved
analyses with an AI*). A pointer may target a skill file directly (`.claude/skills/deploy/SKILL.md`), so one
file serves the team, Claude Code and the analyser; and the `description` is what a later slice (M38.8) will
surface in `context` so a model can pick the right runbook without opening all of them.

### Where you see it

The **Project panel** lists each pointer under the project row — *`deploy runbook: ops/deploy.md ·
project`* — with *Copy* / *Show* acting on where it lands on your machine, and a warning when the file
is not there. `context` reports the same under `runbooks` (`name`, `path`, `resolved`, `exists`, `from`),
so an agent and a person read one set of facts.

### Sharing it

*File ▸ Export settings…* has a **Runbook LOCATIONS (paths in your repository — never their contents)**
checkbox. It is **off by default**: a path such as `ops/deploy.md` says something about how your
repository is laid out, so it leaves only when you tick it.

## Vocabulary — what `live` means *here*

Two readers need exactly the same thing and neither has it: an **LLM** answering about a processor it has
never seen, and a **support engineer in their first week** answering about a system they did not build.
The node is called `spreadCalculator`; what the number means is in somebody's head.

The project points at a glossary — a markdown file in the repository, the same rule as a runbook (owner
decision, 2026-08-27: one rule for pointed-at content, not two):

```properties
vocabulary=docs/glossary.md
```

The file is yours to shape. What earns its place: what each domain term means in *this* system (`live`,
`suspended`, `breach`), what a normal value looks like (a typical spread, an unusual one), which nodes
matter and which are plumbing, which events are routine and which never are.

**What the analyser does with it — this is the one pointer whose contents it reads.** A glossary is
tier-1 fact: inert, read, never acted on. So its text is served in `context` (`vocabulary.text`, capped at
16 000 characters) and placed **first** in the assistant's prompt for *Explain* — before the record, because
what `live` means decides how every number in the record reads. A runbook's contents are never read; a
glossary's are the point.

The pointer is validated at every entrance like a runbook's; a refused value is announced in the status
bar on project open. It travels by default under **Domain glossary LOCATION (a markdown file in your
repository — never its contents)** — inert, so there is nothing to consent to beyond a path.

The Project panel shows it under the project row — *`vocabulary · docs/glossary.md`*, warning when the
file is missing.

## Environments — which system this log came from

Two environments running the same build emit logs identical in shape and usually in filename. Only a
declared value separates them, and today that value is typed by whoever wrote the export script, per
site. The failure this prevents has no symptom: an answer **correct about UAT and read as production**.

The project declares its environments and, for each, the **§E provenance** a log from it carries:

```properties
environment.count=2
environment.0.name=prod
environment.0.provenance=risk-engine · prod · ldn
environment.0.logDir=logs/prod
environment.1.name=uat
environment.1.provenance=risk-engine · uat
environment.1.logDir=logs/uat
environment.default=uat
```

When a log is opened **without** a declared provenance, the analyser looks for the first environment
whose `logDir` contains the file, then the default; the match supplies the provenance that rides the
status bar, report headers and the mismatch banner. It never guesses: with no `logDir` match and no
default, the log has no provenance, as before.

**A declaration always wins.** `open {log, provenance}` from an agent — or a server that knows which
environment it is (UP-MNG-03) — beats the project's environments, and `context.provenanceSource` says
which answered: *declared by the opener*, *project environment 'prod' — the log is under logs/prod*, or
*project default environment 'uat'*. The Project panel's log row carries the same words.

A matched provenance is **qualified wherever it leaves the session**: a report's header reads *"risk-engine ·
prod (matched by directory, not declared) · 5821 record(s)"*, because directory matching is a heuristic
about the filesystem standing in for a claim about a system — a prod log copied into `logs/uat/` to be
looked at matches honestly and wrongly, and the report is read by someone who was not there. Two rules
worth knowing: with nested log directories the environment declared **first** wins, so declare the more
specific one first; and a remote open (S3) with no local copy takes only the **default**, which is then
reported as exactly that — a project that does not want remote logs stamped declares no default.

Environments **travel by default** in a shared profile, under **Environments (names, the provenance
string each stamps — which may name systems and hosts — and their log directories; never log data)**:
the label says exactly what leaves, because a provenance string is estate detail even if it is not a
secret. `logDir` is a pointer and passes the same gate as a runbook's.
## Repeatable analyses — the one we run every time

*Open this log, filter to that node, build those series, produce that report* — saved by name, with
its **rationale**, because a saved view without its reason is an unexplained view (the pattern named
focuses set). Parameters are declared and bound when it runs, so one analysis serves every incident of a
kind.

```properties
analysis.count=1
analysis.0.name=spread breach
analysis.0.rationale=every breach incident starts the same way: the spread before it
analysis.0.param.count=2
analysis.0.param.0.name=log
analysis.0.param.1.name=node
analysis.0.param.1.default=quotePublisher
analysis.0.step.count=3
analysis.0.step.0.action=open
analysis.0.step.0.params={"log": "{log}"}
analysis.0.step.1.action=filter
analysis.0.step.1.params={"text": "{node}"}
analysis.0.step.2.action=graph
analysis.0.step.2.params={"name": "Spread — {node}", "series": ["{node}.spread"]}
```

Each step is an action-socket verb with its params exactly as the socket would receive them; `{name}`
anywhere in a value is replaced by the bound parameter.

**Tier 2, by construction — and by enumeration.** A saved analysis can drive this viewer; open a log or
graph **inside the project** (or one you bind at run time as a `{parameter}`); add source roots inside
the project; and write a report or screenshot **only into the exchange directory**. It cannot switch or
close a project (a session boundary is a person's act — refused as a step), cannot name a path outside
the project (every path in a step — `open.log/logs/graphml`, `source_root.add/remove`, `report.path`,
`screenshot.path` — passes the same gate as a runbook pointer: project-relative, no `..`, no command
shapes), and cannot reach a server, because server verbs never appear on the action socket. Project-
relative paths in `open` and `source_root` steps resolve against the project root when the analysis runs,
so the same analysis works from any checkout; report and screenshot paths are relative to the exchange
directory. Closing a log or a graph is a legitimate step.

**Recall is an offer.** `context.analyses` lists each analysis with its rationale and the parameters it
declares; nothing runs by itself. To run one:

- **File ▸ Run analysis ▸ *name*** — a small dialog asks for the parameters (defaults prefilled).
- `open {analysis: "spread breach", bind: {log: "/path/to/audit.yaml"}}` over the socket.

Steps run in order through the same dispatcher the socket uses, so every guard a verb has applies, and
the run **stops at the first failure** — the echo reports each step and where it stopped. A parameter
with no value and no default refuses the run and names itself.

Analyses travel by default under **Saved analyses (named analyser-verb sequences with their rationale —
they can only drive this viewer, never a server)**. The Project panel lists them under *Analyses* — the
offer stated, with no run button, because a button that runs verbs would change what the app shows.

## Report destinations — a place, never a credential

The project may record **where** an investigation report is published — a bucket, a directory, a ticket
system's base URL:

```properties
destination.count=2
destination.0.name=incident-bucket
destination.0.location=s3://acme-incident-reports/quote-service
destination.1.name=shared-drive
destination.1.location=/mnt/shared/reports/quote-service
```

It may **never** record how to authenticate there, and the gate makes that structural rather than
polite: a URL with user info, a query or a fragment is refused (tokens travel there); anything matching
a credential's shape (`AKIA…`, `token=`, `password=`, `Authorization:`) is refused with the reason; S3
locations must be `s3://bucket[/prefix]` and directories plain paths. Credentials continue to come from
the environment the publisher already runs in.

**The analyser does not publish.** It states the place — in `context.reportDestinations` and the
Project panel's Reports section (*publish to incident-bucket: s3://… · s3*) — so the agent that rendered a
report knows where it belongs and publishes with its own credentials. File-writing verbs stay inside the
exchange directory, and the analyser gains no server-side code, which is the standing decision M18's
closure rests on.

**One shape no inspection can catch: a webhook.** `https://hooks.slack.com/services/…` is a credential
in path form — anyone holding it can post to the channel — and "publish the incident report to the team's
channel" is the first thing a support team would paste. The known webhook hosts (Slack, Teams, Discord,
Zapier, Telegram, Google Chat) are refused by name, and the **Report destinations** share checkbox is
**off by default**, like *LLM*: a field that can hold a secret is shared knowingly or not at all. Publish
to a channel through the agent's own configured integration; record the *place* the report belongs.

## Path anchors — one rule, and the anchor that was missing

Three forms of path already exist in a profile and are chosen automatically, most specific first:
**project-relative** when the path is under the project root; **`~/…`** when it is under your home;
**absolute** otherwise. There is deliberately no per-path toggle: a profile whose paths were each chosen
by hand is one where portability varies row by row, nobody remembers why, and the failure appears on a
colleague's machine.

The gap was an **anchor**. A sibling checkout — `../shared-lib/src/main/java`, the monorepo neighbour —
is outside the project root, so it was written `~/work/shared-lib/…`: portable for *you* on another
machine, silently wrong for a colleague who checks out somewhere else. Declare once, per project, where
the workspace is:

```properties
workspaceRoot=..
```

A root under that anchor (and not under the project) is then written relative to the project with `..`
steps — `sourceRoot.0=../shared-lib/src/main/java` — and resolves against the profile's own directory on
every machine. The anchor must be `.`, `..`, `../..` … (at or above the project root, at most six up);
anything else is refused and announced. It rides the **Source roots** share category and applies to Maven
repos too.

**This does not weaken the pointer rule.** Runbook and vocabulary pointers stay project-relative with no
`..` — those are things an agent acts on, and the trust boundary is the repository you cloned. Source roots
and Maven repos are inert lists the analyser resolves, and may use the wider anchor.

**And it is visible.** The Project panel's *Source roots* section shows each root's **stored form** —
*project-relative*, *workspace-relative*, *~*, *absolute* — and, under a project, marks *absolute* and *~*
roots as a warning: this profile will not resolve them on a colleague's machine. That badge, on a row in a
profile you are about to share, is the whole warning, delivered before the failure.

## Mixed versions — a newer profile survives an older analyser

A profile is committed with the repository, and not everyone on a team runs the same analyser build. An
older build that opens a profile written by a newer one **keeps every key it does not understand** when it
saves: it rewrites only the key families it owns (so removing a runbook still removes it) and carries the
rest over byte for byte. The loader, likewise, ignores what it does not know — never rejects. So a project
can adopt a new M38 fact without waiting for every teammate to upgrade, and nobody's save quietly strips
it.

## The share categories, complete

| Category | Tier | Default | What leaves |
|---|---|---|---|
| Source roots (existing) | 1 | on | roots and the **workspace anchor** (`..`) that makes a sibling checkout portable |
| Runbook LOCATIONS | 1 | off | project-relative paths — never contents |
| Domain glossary LOCATION | 1 | on | one project-relative path — never contents |
| Environments | 1 | on | names, provenance strings (may name systems and hosts), log directories |
| Report destinations | 1 | **off** | places — bucket, directory, base URL; a webhook URL is a secret in path form, refused when recognised, and the reason the box is off |
| Saved analyses | 2 | on | analyser-verb sequences with their rationale — they can only drive this viewer |

Every label names its cargo (D-C8): ticking the box is consenting to exactly what it says.

## What comes next

Repeatable analyses, report destinations, and path anchors — each a `context` fact first and a Project-panel row, so
it is visible to both parties by construction. See the tracker's M38 for the order.
