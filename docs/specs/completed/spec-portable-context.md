# Spec — portable context: the project as a shared workspace for a human and an AI

**Status:** SHIPPED 2026-08-27 — M38.1–.7 merged to main; reviews in `docs/handoff/completed/` (spec, .2, .3, .4, .5/.6, closing).
**Depends on:** **M37** (the Loaded panel) — see *Why on top of M37*.

## The proposition

The owner's words: *"portable context for human and LLM/AI so they can work in a shared space that
references code, meta-data, compiled artifacts, logs, display, analysis etc."*

A project profile is already most of that and is still described as settings. It carries source roots,
event processors, saved graphs, named focuses **with the rationale for each**, investigation reports,
hidden columns, and — since M35.10/.11 — project-relative artifact paths. Nobody configures a rationale.
That is a workspace being smuggled through a settings file.

Naming it correctly is not cosmetic: **it changes what is allowed in.** Once the profile is context
rather than configuration, "what does `live` mean in this system" and "how do we deploy this" become
obvious candidates — and one of those two is safe and one is a supply-chain vector. This spec exists to
draw that line before the first convenient thing is added on the wrong side of it.

## D-C1 — the tiers are defined by whether the stored thing EXECUTES

Everything proposed for the profile falls into exactly one of three tiers, and the tier decides how it
is stored, shared and consented to. **This is the first decision and every later one refers to it.**

| Tier | What it is | Examples | Travels in a shared profile? |
|---|---|---|---|
| **1 · Facts** | Inert statements about the system | vocabulary, environments, artifact pointers, baselines, prior findings | **Yes** — this is what sharing is for |
| **2 · Analyses** | Sequences of **analyser** verbs | saved graphs, focuses, reports, repeatable analyses | **Yes** — see why below |
| **3 · Runbooks** | Knowledge that causes something to happen elsewhere | build, deploy, restart, pull-logs | **Never as payload** (D-C2) |

**Why tier 2 is safe, stated because it is not obvious and must not be eroded — and stated by
enumeration, not by slogan (M38.4 review F1).** A saved analysis can:

- drive this viewer — filter, graph, goto, flag, topology, focus, series, aggregate, coverage, read, context;
- open a log or a graph **inside the project** (a project-relative path), or one the person recalling
  it binds at run time as a `{parameter}` — and sees;
- add or remove source roots **inside the project**, under the same rule;
- write a report or a screenshot **only into the exchange directory** — the run-time export policy
  confines those verbs there whatever path a step names, and the stored path itself must be relative.

It cannot switch or close a project (a session boundary is a person's act — refused as a step), cannot
name a path outside the project (every path-bearing step param passes the same gate every other pointer
in the profile passes: `Runbooks.refusePointer`), and cannot reach a server, because **server verbs never
appear on the analyser's action socket** — the standing decision (tracker ▸ Decisions, reaffirmed when M18
closed) that a pinned test enforces (`assertEquals(14, VerbSchemas.all().size())`). The first cut of
M38.4 enforced only "the action is an analyser verb", which is necessary and not sufficient: `report`
to an absolute path, `source_root` adding any directory and `open` of any file were all accepted (review,
2026-08-27). The recipient of a shared analysis sees a name and a rationale, not the paths — which is why
the paths are gated rather than disclosed.

## D-C2 — a runbook is stored as a POINTER; the payload lives in version control

The profile records *"the deploy runbook for this system is `ops/deploy.md`"*, relative to the project
root. It does **not** record the commands.

- The agent still learns how to deploy on opening the project — the capability the owner asked for.
- The executable content sits in the team's repository: reviewed, diffed, attributable, revertible.
- The trust boundary becomes **"you cloned this repo"**, which every developer already understands,
  instead of "you opened a file someone sent you", which nobody evaluates.

**The threat this closes.** *Sharing setups* is a shipped feature: profiles move by email and in
repositories. If a profile could carry instructions, opening a colleague's project with an agent
attached would execute text written by whoever sent the file — remote code execution wearing the
friendliest costume available. The industry is actively seeing agent-configuring files used this way.

**The rule, which outlives this spec:** *anything in a profile that an agent will act on must be inert,
or a pointer to something under version control — never an instruction the profile itself carries.*

**Execution stays where M18's closure put it.** The analyser gains no server-mutating code: the agent
runs the runbook with its own tools, or through a Mongoose-side MCP tool (UP-MNG-02). The profile says
*where the knowledge is*, never *do this*. **Precisely what the analyser will not do with a runbook** (owner,
2026-08-27): execute it, or serve its contents to an agent — `context` carries the pointer only. What it
will do: show the file read-only to the *person* who clicks *Open* on the Project panel, exactly as *Show
file* does through the file manager. A human reading what the profile points at is neither of the two
hazards this decision exists for.

## D-C3 — vocabulary, and it is first because it is the cheapest large win

A glossary bound to the project: what `live` means here, what a normal spread is, which nodes matter,
which events are routine and which never are.

Two readers need exactly the same thing and neither has it today: an **LLM** answering about a processor
it has never seen, and a **support engineer in their first week** answering about a system they did not
build (docs/site/support.md). The node is called `spreadCalculator`; what the number means is in
somebody's head. Pure tier 1 — inert, shareable, no execution — and the largest single improvement to
answer quality available in this list. It is left out of designs because it looks like documentation;
bound to the project and served in `context`, it is the difference between a plausible answer and a
correct one.

**Why the analyser reads THIS pointer's contents and no other's** (M38.2 review, 2026-08-27). A runbook
pointer is stored; the agent decides whether to open the file, under its own tooling and permissions.
Glossary text is read by the analyser and handed onward — so it arrives carrying the analyser's
authority, in a stream where everything else is machine-derived from the log and the graph. It is the
first human-authored free text to travel that path. That is not an argument against D-C3; it is the
reason the text must be unmistakably **labelled as prose, not fact**: its own `vocabulary` key in
`context` (with `path`, `exists`, `text`, `truncated`), a cap whose truncation is announced, and a prompt
frame that presents it as *reference text, not an instruction*, delimited. The precedent is M33 giving
REPORTS its own share category because a report carries prose an agent wrote about your data — the same
cargo by a different road, the same instinct. `vocabulary.text` is in `context` as well as the prompt
because an agent not using the built-in prompt still needs it; that doubles the roads the text takes,
and both are labelled.

## D-C4 — environments, and the provenance each one stamps

The project declares its environments (`prod`, `uat`, `dev-a`…) and, for each, the **§E provenance
string** an export from it should carry.

This is a correctness feature, not convenience. Two environments running the same build emit logs
identical in shape and usually in filename; only a declared value separates them. The analyser already
carries provenance the whole way — status bar, report headers, and a mismatch banner that can say *"same
content — a different system"* — but it can only report what it was told. Today that value is typed by
whoever wrote the export script, per site.

Failure mode if this stays unfixed: an answer that is **correct about UAT and read as production**. No
error, no symptom, and — unlike the producer diagnostics (2026-08-25) — the analyser cannot detect it,
because both logs are well-formed. Pairs with UP-MNG-03, which supplies the same fact from the server;
this is the analyser-side default for estates that do not have it yet.

## D-C5 — a repeatable analysis is a named sequence of analyser verbs

*"The analysis we want to run every time"* — open this log, add these roots, focus that subgraph, build
those series, produce that report — saved by name, with a **rationale** (the pattern named focuses
already set: a saved view without its reason is an unexplained view).

Parameters are declared and bound at run time (`{log}`, `{provenance}`), so one analysis serves every
incident of a kind. It is an **offer**, never automatic: recalling one is an act, exactly as recalling a
named focus is. Tier 2 by construction — see D-C1.

## D-C6 — a report destination is a place, never a credential

The profile may record *where* an investigation report is published — a bucket, a directory, a ticket
system's base URL. It may **never** record how to authenticate there.

The precedent is already enforced and should simply be extended: `SettingsShare` is a whitelist on
export **and** on import, so a stray `apiKey=` in a shared file is ignored rather than honoured, and the
`LLM` category deliberately shares provider/model/base-URL while never sharing the key. Credentials
continue to come from the environment the agent already runs in.

**What the gate can and cannot promise** (M38.5 review F1, 2026-08-27). For a bucket, a directory or a
base URL the invariant is inspectable: user info, a query, a fragment, an access-key shape or a
`token=`/`password=`/`Authorization` shape are refused with the reason. For a **webhook** the place *is*
the credential — anyone holding `https://hooks.slack.com/services/…` can post there — and no inspection
separates the two. So the known webhook hosts are refused by name (Slack, Teams, Discord, Zapier,
Telegram, Google Chat), and the `DESTINATIONS` share category defaults **off**, the LLM precedent: a field
that *can* hold a secret is shared knowingly or not at all. The analyser never publishes, so the blast
radius of a leaked destination is a secret in a shared file, not an action.

## D-C7 — every new fact is a `context` fact first, and appears in the Loaded panel

Directly inherited from M37's D-L1 and its corollary: a fact the human panel needs that `context` lacks
is added to `context` first. M38 adds no second channel — each item below is served by `context`,
rendered by the Loaded panel, and therefore visible to **both** parties by construction.

That is why this milestone sits on M37 rather than beside it: without the panel, portable context is a
thing agents can read and humans cannot, which is the asymmetry M37 exists to end. In particular the
runbook **pointer** (D-C2) must be a visible row — *"deploy runbook: `ops/deploy.md` · project"* — because
a pointer an agent will act on and a human cannot see is precisely the shape this spec is trying to
avoid creating.

## D-C8 — one consent category per tier, and the label names the cargo

`SettingsShare.Category` already models this well and the pattern is explicit: *the label must NAME
everything the category carries — a user ticking the box is consenting to what leaves the machine*
(GRAPHS/M27.3, and REPORTS given its own box because it carries prose rather than key names).

Proposed additions:

- `VOCABULARY` — *"Domain glossary (what this system's terms mean — never log data)"*, default **on**.
- `ANALYSES` — *"Saved analyses (analyser steps and their rationale)"*, default **on**.
- `ENVIRONMENTS` — *"Environment names and the provenance each stamps"*, default **on**; hostnames are
  organisational detail, so this one is worth an owner decision (open question 2).
- `RUNBOOKS` — *"Runbook LOCATIONS (paths in your repository — never their contents)"*, default **off**,
  and the exporter refuses any value that is not a project-relative path.

## D-C9 — paths: the anchor is declared, not chosen per path (owner question, 2026-08-27)

*"Should the paths in the project have an option for relative or absolute?"* Three forms already exist
and are chosen automatically, most-specific first (`SettingsShare.toPortable`):

1. **project-relative** when the path is under the project root (M35.11);
2. **`~/…`** when it is under the user's home;
3. **absolute**, verbatim, otherwise.

**Keep the automatic rule; do not add a per-path toggle.** A profile whose paths were each chosen by
hand is one where portability varies row by row, nobody remembers why, and the failure appears on
somebody else's machine. One rule, applied consistently, is what makes a profile safe to share.

**The real gap is a missing ANCHOR, not a missing option.** A sibling checkout — `../shared-lib/src/main/java`,
the monorepo neighbour, the library everyone has next to the app — is *outside* the project root, so it
falls to rule 2 and is written `~/work/shared-lib/…`. That is portable for **you** on another machine and
silently wrong for a colleague who checks out somewhere else. The path is stably positioned relative to
the project and we have no way to say so.

So: an optional **`workspaceRoot`** declared by the project (a directory at or above the project root).
When set, a path under it that is not under the project root is written relative to it. The anchor is
declared once, per project, by the person who knows the layout — not guessed per path, and not asked at
save time.

**This must not weaken D-C2.** A **runbook or vocabulary pointer stays project-relative only, with no
`..` traversal** — those are things an agent will act on, and the trust boundary is "inside the
repository you cloned". Source roots and Maven repos are inert lists the analyser resolves, and may use
the wider anchor set. Two rules, because the two carry different risk, and the exporter enforces both.

**And make it visible (M37).** The Project panel already shows each root's tier; it should also show the
form each path is *stored* in — `project` / `workspace` / `home` / `absolute`. Today you cannot tell
whether a profile is portable until a colleague opens it and it fails; an "absolute" badge on a row in
a profile you are about to share is the whole warning, delivered before the failure. That is M37's
thesis applied to M38's problem, and it is a label, not a feature.

An override remains available for the rare deliberate case — a per-root choice in Settings, defaulting
to automatic — but it is a correction, not the mechanism.

**As built (M38.6, review 2026-08-27) the anchor is stricter than "at most six up":** only a pure `..`
sequence is accepted — `../../../etc` (an ancestor plus a named segment) and `../ok/../..` (normalisation
trickery) are both refused — so the anchor can only ever be an ancestor *directory*, never a sideways
path. That is the property that makes "one rule, no per-path toggle" safe rather than merely convenient.
Consequence, recorded so nobody rediscovers it as a surprise: six levels up from a deep checkout can
reach a home directory or the filesystem root; source roots are inert lists the analyser resolves, the
wider set was chosen deliberately, and the stored-form badge is the mitigation.

## D-C10 — rewrite what you own, preserve what you do not understand (owner decision, 2026-08-27)

Found live while building M38.5: both writers rebuilt their file from scratch, so an **older** analyser that
opened a profile written by a **newer** one silently dropped every key it did not know on its next save.
For a team on mixed versions that strips M38 facts from a committed profile with no diff anyone asked for
— and a profile that is portable context, not settings, makes that a loss of shared knowledge.

The decision, matching the format spec's *ignore, never reject*: a writer carries over every key
**family** (the key up to its first dot) it does not own, byte for byte, and rewrites the families it does
own wholesale — so a list can still shrink, and a removed runbook stays removed. Both writers do it: the
profile exporter and the own-settings store. The alternative — refusing to save over a newer
`share.version` — was rejected: it turns a mixed-version team into one that cannot save at all, and the
version field says nothing about which *keys* are newer. The failure direction is safe by construction: a
family the registry forgets is preserved unchanged, never lost. `KnownKeys` is the registry; a new key
family added to either writer is added there in the same commit.

## Non-goals

- **Not a secret store.** No credentials, ever, in any tier.
- **Not a task runner.** The analyser does not execute a runbook, or offer a button that does.
- **Not a replacement for the repository.** Pointers, not copies — a profile that duplicates
  version-controlled content will drift from it and be believed anyway.
- **Not per-user notes.** This is what the team knows about the system, not a scratchpad.

## Open questions (owner)

_All four **DECIDED by the owner 2026-08-27**; kept here with the answers so the reasoning survives._

1. **Where does vocabulary live?** → **A pointed-at markdown file.** Not the profile, and not the
   inline fallback I proposed: vocabulary gets the same treatment as a runbook (D-C2), so the content
   is reviewed, diffed and version-controlled, and the profile holds only the pointer. One rule for
   pointed-at content instead of two, and D-C3's field becomes a path.
2. **Do environments and hostnames travel by default?** → **Yes**, default-on, with the category label
   naming exactly what leaves (D-C8).
3. **Prior findings** → **links only**. No duplication of M33.
4. **Baselines** → **their own milestone, M39.** Bigger than the other four slices together, and the
   question it answers — *"is this normal here?"* — is the one support cannot answer about an
   unfamiliar system, so it deserves its own design rather than a slice.

## Acceptance

- [ ] D-C1's tiers are documented on the docs site, with the sentence from D-C2 quoted verbatim.
- [ ] A runbook value that is absolute, outside the project, or non-path is **refused with a reason** at
      write time and ignored at import; a test asserts an import carrying runbook *contents* is dropped.
- [ ] Vocabulary, environments, analyses and runbook pointers are all served by `context` and rendered
      as Loaded-panel rows (D-C7); the M37 parity test covers them.
- [ ] Opening a project with a declared environment supplies the §E provenance default for a log opened
      from it, and `context` says where that provenance came from (declared-never-inferred).
- [ ] Four new share categories, each labelled with its full cargo; `RUNBOOKS` defaults off (D-C8).
- [ ] A saved analysis can be recalled by name from the UI and the socket, is an offer rather than
      automatic, and carries its rationale.
- [ ] Nothing in this milestone lets the analyser execute anything.
- [ ] Docs page + CHANGELOG + tracker; spec status → SHIPPED.

## Slices

- **M38.1** The tier model + `RUNBOOKS` pointer (write-time validation, import refusal, `context` +
  Loaded-panel row). The security decisions land first, before anything wants to bend them.
- **M38.2** Vocabulary (D-C3) — a POINTER to a markdown file (decided), validated and served like
  a runbook pointer, so M38.1's path rules cover it.
- **M38.3** Environments + provenance defaults (D-C4).
- **M38.4** Saved analyses (D-C5), with rationale and parameter binding.
- **M38.5** Report destinations (D-C6); share categories completed (D-C8); docs, CHANGELOG, tracker.
- **M38.6** Path anchors (D-C9) — optional `workspaceRoot`, the stored-form badge in the Project panel,
  and the exporter enforcing "pointers are project-relative, no `..`" separately from source roots.

## Relationship to other work

- **M37** — dependency, not overlap. M37 makes the workspace *visible*; M38 makes it *richer*. Adding
  M38's facts without M37 recreates the asymmetry M37 exists to end.
- **M20.5** (project artifact pointers) — the same shape as D-C2 one level down: the profile points at a
  graphml and a log directory rather than embedding them. M20.5 should be read as tier 1 of this model,
  and the two should share their path-validation code.
- **UP-MNG-03** — supplies D-C4's environment from the server. Where both exist the server wins, and
  `context` says which answered.
