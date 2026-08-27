# Portable context — the project as a shared workspace

A project profile started life as *settings*: source roots, Maven repos, event processors. It now also
carries saved graphs, named focuses **with the reason each was saved**, investigation reports and — as
of this milestone — pointers to the team's runbooks. Nobody *configures* a rationale. That is a
**workspace**: what the team knows about this system, portable between a person and an AI working on
the same log, and between one machine and the next.

Naming it correctly changes what is allowed in. This page is the rule.

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

## What comes next

Declared environments and the provenance each stamps, repeatable analyses, report destinations, and path
anchors — each a `context` fact first and a Project-panel row, so
it is visible to both parties by construction. See the tracker's M38 for the order.
