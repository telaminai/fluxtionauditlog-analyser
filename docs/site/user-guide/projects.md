# Working across projects

If you support more than one Fluxtion application, you keep swapping the same five things: source roots,
Maven repos, event processors, saved graphs and hidden columns. A **project profile** holds those in a
file beside the project, so moving between them is one click instead of a re-import.

Source roots also authorise XML design and producer JSON reads beneath them. For Spring design rendering,
include the design directory and a root containing `target/` for diagnostic results and receipts. A file
inside the project but outside the configured roots is refused. Project-relative design paths are resolved
without rewriting the profile. See [Source navigation](source-navigation.md#spring-design-and-file-glances).

## The two tiers

| Tier | What's in it | Where it lives |
|---|---|---|
| **Machine** | LLM API key · LLM provider/model · AWS profile/region · theme · recent files · window size · assistant settings · topology display prefs | `~/.fluxtion-analyser/config` |
| **Project** | **source roots · Maven repos · event processors + selected · saved graphs · hidden columns** | `<project>/.analyser/project.fluxtion-settings` |

Nothing appears in both, which is what keeps this simple: there's no "which one wins" rule to remember.
Switching a project swaps the project row and leaves the machine row alone — your key, your theme and
your window stay exactly as they were.

The separate **Fluxtion processor-build key** lives in `~/.fluxtion/fluxtion.apiKeyFile` and is managed
from **AI ▸ Fluxtion API key…**. It is not an analyser setting and cannot enter a project profile. The
Project panel can report whether that canonical file has a configured key, but it cannot know whether a
future Maven build will receive an overriding `-Dfluxtion.apiKey`; it states that precedence rule rather
than guessing a winner.

A generated project may also carry `skills.provenance`: a value-free identity such as
`canonical@<revision>` saying which vendored skill snapshot it received. The Project panel shows that
declaration as an inert fact. It never makes the analyser fetch anything. A hand-edited
`skills.source` in the profile is refused because a shared project must not redirect the instructions an
agent reads; source selection belongs to the playground's build/release process.

With **no project open**, the analyser behaves exactly as it always has. Project profiles are opt-in.

## Start from a playground template

Choose **Project ▸ New project from template…** to start inside the analyser instead of visiting the
playground first, or choose **Author a new project** on the start page. The analyser lists the whole
versioned catalogue. Entries tagged for onboarding are marked **Recommended starting points**, without
hiding the others. A recommendation is advice, not a promise of a walkthrough or keyless generation.

Read the selected template's build/regeneration key requirements and agent entry files before downloading.
These are catalogue declarations, never guesses from AOT mode or project type. Missing means **not declared**;
an empty bootstrap list means **explicitly none**. Unknown future key values are labelled unrecognised.
The declarations describe the default download; verify its actual files before following its runbook.

1. Select a template and read its catalogue description.
2. Confirm or change its artifact, group and base-package defaults.
3. Choose a new or empty project directory.
4. Select **Download and open**. If the download contains a profile, it becomes the active project.
   Legacy or support-disabled downloads instead offer discovery with no choices preselected. They are not
   silently opted back into support. Creating the optional pointers-only reference guide requires its own
   unchecked-by-default checkbox; an existing project guide is never overwritten.

The [earlier picker capture](../assets/template-picker.png) records the former recommendation-only
list. Analyser 1.16.0 shows the whole catalogue. For a new Spring project, follow the
[illustrated authoring path](../spring-authoring-getting-started.md).

The archive boundary is deliberately strict: only the configured playground HTTPS origin is contacted;
absolute and parent-traversing ZIP entries are refused; entry count and expanded sizes are capped; a
non-empty destination is never merged or overwritten; and extraction is staged beside the destination
then moved into place atomically. Archive mode bits are ignored—only the fixed root lifecycle allowlist
(`mvnw`, run, export, stop and key-check shell wrappers) receives executable bits.

A template's own project settings may only point their source folders inside the project being installed.
Before the move, every settings file in the archive is checked — the main profile, a named one beside it, or a
nested module's, each against its own folder: a folder outside the project (including one that leaves and
comes back in through the archive's own folder name), the whole project, a `~`, absolute or Windows-style path
(a backslash or a drive letter, refused on every OS because the project may later be opened on Windows), a
path through a file, or a workspace anchor refuses the installation and nothing is installed. Only source
folders are checked: external CSV files named by a template's saved charts are not. A folder that does not exist
yet, such as a future `target/` directory, is allowed when it would sit inside the project. Maven repositories
are not subject to this rule. Projects you set up yourself may still name folders outside the project on
purpose, such as a neighbouring module.

The analyser **does not run downloaded code**. When the project is ready it shows fixed, copyable build,
run, export and stop commands selected from recognised filenames. You decide whether to paste them into
a terminal. If the catalogue is unreachable, the error gives the manual template-gallery route instead
of leaving an empty picker.

## Opening and switching

![The Project menu, including New project from template and project settings](../assets/projects-file-menu.png)

Project actions have their own top-level menu: they change *which project's settings are in force*.
Use Sources and Audit log for the inputs you inspect. **Save project as…** and **Close project** are greyed out until
a project is open.

**Project ▸ Open project…** — pick the project directory, or its `.fluxtion-settings` file directly.

- **Project ▸ Open recent project** — the last ten, most recent first.
- **Project ▸ New project from template…** — downloads a catalogue-selected starter into a destination
  you approve, safely extracts it and opens its bundled profile. It shows commands but runs none. Its
  destination dialog carries one unchecked option to **create a `CLAUDE.md`** of canonical authoring links
  in a legacy or support-disabled project. Current support-enabled templates ship agent entry files; a template that does
  ship one keeps it — the file is never overwritten, and the status line says which happened.
- **Project ▸ New project…** — chooses a directory, then offers the Java source roots, `SKILL.md` runbooks
  and GraphML it can already see there, plus one option that is different in kind: **creating a
  `CLAUDE.md`** in that directory pointing at the canonical Fluxtion authoring documentation. Every box
  starts off: finding is not adding. Confirm only the facts this analyser should adopt; an empty directory
  produces an ordinary empty offer and can still become an empty profile — the `CLAUDE.md` offer appears
  there too, since it is the case where it helps most. It never inherits whatever you happened to have
  open, or "new project" would just be a slow way to copy one.
- **Project ▸ Save project as…** — forks the current settings to another project, which becomes active.
- **Project ▸ Close project** — returns to the settings you had **before you ever opened a project**.

![The analyser with a project open — the project name appears in the window title after the app name](../assets/projects-active.png)

With a project open, its name sits in the window title — so *which settings am I using right now* is
never a guess.

### The day-two journey

After trying a prepared example, use **Project ▸ New project…** on your own repository. The one offer is
the hand-off from demo to real work:

1. review the detected `src/main/java` roots (including one-level Maven modules);
2. choose any project-owned skills that should become declared runbook pointers;
3. optionally choose one discovered GraphML topology to open;
4. optionally ask it to create a `CLAUDE.md` — see below, this one writes a file;
5. confirm once. Only those checked facts enter the new profile.

The scans are bounded and skip build output, dependencies and symlinks. If a safety cap is reached the
dialog says the offer is partial rather than presenting it as the whole repository.

### The one option that writes to your repository

Choices 1–3 record **pointers** in the analyser's own profile and change nothing in your working tree. The
`CLAUDE.md` option is different and is described separately for that reason:

- it **creates a file in the directory you chose** — the only documentation the analyser ever writes into
  a repository;
- it contains **links and one line each**, to the canonical Fluxtion authoring resources, and deliberately
  restates none of their content. Improving those pages improves the file, which is the point of pointing
  rather than copying;
- a link that applies only to Spring-authored projects is written **only** when the directory looks
  Spring-authored;
- it **never overwrites an existing `CLAUDE.md`.** If you already have one, the dialog says so and the
  option is not offered; if one appears while the dialog is open, the write is refused and you are told;
- it is **unchecked by default**, like everything else in that dialog, and it does **not** enter the
  profile — the profile records pointers to files, and this is a file, not a pointer to one.

!!! info "Switching replaces, it doesn't merge"

    Open project B while A is active and A's source roots are **gone**, not listed underneath B's. That
    is the whole point: importing one project's settings on top of another's is how you end up with
    four projects' source roots and no idea which is which.

    A profile that mentions no graphs leaves you with no graphs — the replace covers every project
    category, not just the ones the file happens to contain.

!!! note "Switching a project closes the log and graph"

    A profile owns your source roots, event processors, named graphs, focuses and reports. Swap it
    and all of those change underneath whatever you were looking at, so the log and its topology are
    closed with it — otherwise you'd be reading one project's log through another's settings.

    The exception is accepting the *"this log sits inside a project"* offer: there the project is
    adopted **because** you opened that log, so the log stays.

## Your own settings are kept

The first time you open a project, the source roots and graphs you had are **not** deleted. They become
your "no project" settings, and **Close project** brings them back. A year of accumulated configuration
is not something a single click should consume.

## Edits save as you make them

There is no **Save project**. Change a source root, add a graph, hide a column — it persists to the
active project, the same way the analyser has always saved its config. Writes are **debounced**, so a
burst of edits becomes one write rather than fifteen; a profile is often a committed file and a legible
diff is what gets it reviewed.

This includes edits made by an **assistant over the socket**. If an agent adds a source root with
`analyser_source_root`, that lands in the active project like any other change.

## Committing a profile to your repo

`.analyser/project.fluxtion-settings` is designed to be committed, like `.vscode/settings.json`. Clone
the repo, open the analyser, and the project's source roots, event processor and curated graphs are
already configured.

For alternative configurations of the same project, keep named profiles alongside it, such as
`.analyser/project.demo.fluxtion-settings`. Open a named profile explicitly with
`open {project: "path/to/project/.analyser/project.demo.fluxtion-settings"}`. Canonical and named
profiles both resolve relative paths from the directory containing `.analyser/`. Automatic discovery
still looks for `project.fluxtion-settings`; other settings filenames retain their own directory as
the relative-path base.

!!! success "Why it's safe to commit"

    **A profile cannot contain either your LLM API key or your Fluxtion build-key value.** The LLM key
    is outside the project tier, while the build key never enters `AppConfig` at all: its dialog writes
    straight to the established local Fluxtion file and then forgets the value.

    The same holds for your AWS profile, your theme and your window size: all machine-tier, none of them
    written to a profile. And paths under your home directory are stored `~`-relative, so a teammate's
    checkout resolves them on their own machine.

    A path that is neither absolute nor `~`-prefixed is **relative to the project root** — the directory
    that holds `.analyser/` — so a hand-written `sourceRoot.0=src/main/java` means what it says, the way
    it would in `.vscode/settings.json`. The analyser writes the file the same way: paths under the
    project come out project-relative, and there is no timestamp, so opening a project and changing
    nothing leaves the committed file **byte-for-byte as it was**. A diff in that file means a setting
    changed.

Treat a committed profile as config-as-code: it evolves, and the diffs are worth reviewing like any
other change.

## Opening a log inside a project

Open an audit log that sits anywhere under a project directory and the analyser offers to load that
project. This is what makes a downloaded example configure itself — get the bundle, open its log, and
the source roots and graphs are set up without touching Settings.

It asks **once per log**, never for a project that's already open, and takes "no" for an answer for the
rest of the session. Opening a log from `s3://` never triggers it: the object is streamed to a temporary
file, and a temp directory isn't a project.

## From an agent

An assistant working over the [action socket](assistant.md) sees the same offer as data —
`context` reports `projectOffer` with the settings path — and accepts it with
`open {project: "<that path>"}`; the same call opens any project by directory. It also reads which
project is in force (`context.project`) and leaves one with `open {close: "project"}`.

The verb **applies rather than asks**, because a question nobody can answer over the socket would
freeze the app. What makes that safe is the answer it gives back: every project-owned category with
its count before and after the switch, the log and graph it closed and where they were (an explicit
switch is a session boundary, exactly as it is from the Project menu), the project that was active before,
and the one call that undoes it. Your MCP client's own approval prompt on `open` is where a human says
yes or no.

## Import: merge or open?

**Project ▸ Import settings…** now asks which of two different things you mean:

| Choice | What it does | Use it when |
|---|---|---|
| **Merge (share)** | *Adds* the file's settings to what you have now | A colleague sent you their setup and you want their graphs alongside yours |
| **Open as project (replace)** | *Replaces* the project tier and makes the file the active project | The file is a project, and you want to work in it |

These used to be one action, which is why setups piled up. See
[Sharing setups](sharing-setups.md) for what a shared file contains and what it can never contain.

## If a project moves

If the active project's file is gone — repository renamed, checkout deleted — the analyser starts
normally, says so in the status bar, and forgets the pointer. It never fails to open because of a
project, and your machine settings are untouched.

Saved chart definitions remain visible in the Project panel and `context.savedGraphs` after reopening
a profile without a log. They are waiting for input, not proof of restored data or valid series bindings.


The project may also declare processor intent through `context.processorDeclarations`: a named generated
class, a runtime processor with no fixed generated class, or a type not yet specified. The Project panel
and project landing show these declarations independently of source discovery and loaded run evidence.
Opening a project with no log now shows its own declarations and saved charts on the start page, with
explicit buttons to open a log, topology, design or diagnostics. This does not run a build or application.

## Restore an earlier session

Closing a project or quitting records a recovery candidate in your local analyser settings. Opening the
project again or relaunching offers **Restore last session** on the start page. Nothing opens until you
accept. **Dismiss** leaves the project declarations available without opening evidence. The Project panel
states the same recovery status; its buttons remain navigation only.

Restoration checks file contents and then opens the unchanged log (or complete rolled set), topology,
design and producer diagnostics. Missing or changed parts are named individually. An incomplete or changed rolled
set is refused as a unit, including a member that changes during the restoring read. Saved record filters, selection and topology navigation require matching input
identities; a replaced file cannot silently acquire the old cursor. Recovery reports the record filter's
visible/total count and any saved chart window wholly outside the log. A pin is preserved deliberately:
unpin that chart explicitly to follow the current data.

`context.restoration` reports the offer, verification, pending opens and final outcome. Use
`open {restore: "last"}` to accept or `open {restore: "dismiss"}` to decline, each alone. A pending response
is not proof that the files loaded: wait for the final state and inspect the outcome. Neither action runs
the project, its build scripts or its runbooks. If a newer open supersedes recovery, its state returns
to `offered`; wait for that open to finish before accepting again, or dismiss the offer. Buttons retain
the generation of the offer they displayed, so a stale button cannot accept another project's offer.

Candidates are keyed by canonical project-profile location, with a separate no-project bucket. They are
not shared in profiles or exported ZIPs. A moved or missing project never inherits another project's
session.

Each candidate also records which profile captured it. The identity is a random `profileNonce` that the
analyser writes into the profile once, when it creates the profile, and keeps on every later save. It is
taken when the session is captured, not later when the candidate is written out. A project deleted and
created again at the same location has a new profile with a new nonce, so it is not offered the old
project's session even though the path matches. The offer is withheld, and `context.restoration` reports
`capturedBy: "different profile at this path"`, when the session was captured (`capturedAt`) and what it
would have opened (`inputs`). A re-extracted download is withheld too: its profile usually has no nonce
yet (see below).

The nonce is part of the file's contents, not a file-system property, so it behaves the same on macOS,
Linux and Windows. The tools that usually rewrite a committed profile are a `git pull` or checkout and an
IDE that saves by writing a temporary file and renaming it. Both keep the identity as long as they keep the
`profileNonce` line. Checking out a version that has a different nonce, or none, changes it. A copy of the
file (a clone, or an archive of a committed profile) carries its nonce. At another location it is a
different recovery key anyway; at the same location it counts as the same profile, and every input is
still checked before anything opens. A share export never includes the nonce, and importing settings never
changes it.

A profile with no nonce, written by an older analyser version or shipped in a downloaded bundle, gets one
the first time the analyser saves a change to it. Opening it alone writes nothing. Until then, and for any
candidate saved by an older analyser version, the session is withheld with `capturedBy: "unknown"`. A
missing identity is never treated as a match.

Legacy global recent-log/topology paths remain explicit recent-menu choices, not automatic
startup opens. A command-line log opens only that requested log.

Identity here means observed file bytes, not proof of the application's build or execution identity.
Record navigation is withheld when an input has no stable local read identity (including a growing log
changed since loading or a temporary remote input). Topology navigation requires an unchanged explicit
graph file; a reader-supplied graph has no separate file identity. Commentary and spotlight captions are
not saved by session recovery. Reports retain their existing evidence and freshness rules.

Built-in YAML and rolled files calculate the full SHA-256 from the indexing read; there is no size cutoff
or metadata-only shortcut. Capture and accepted restoration still verify current file bytes. Plugin
readers that own their I/O retain before/after full-file checks. Thus the indexed-reader optimisation
removes two extra traversals on native opens, not all hashing costs or verification passes everywhere.

### Files changed outside the analyser

The Project panel and `context.log.freshness` / `context.graphPairing.freshness` separate
loaded file observations from metadata on disk. The loaded record count, time range and byte size
do not quietly adopt a newer file's size. Reopen the same log or GraphML path explicitly to replace
the loaded snapshot. The analyser does not select new evidence just because a file changed.

These observations compare size, modification time and file identity. `unchanged-metadata` is
**not** proof of identical bytes; a writer preserving all metadata can evade this cheap check.
Recovery's full SHA-256 verification is unchanged. Unknown observations stay unknown. Follow still
uses its explicit append/rotation route; a growing pending tail can be reported changed before
its next complete record is indexed. Exported reports identify the loaded snapshot and its metadata
observation instead of presenting it as the latest disk contents.

Producer comparisons are **as of intake**. If an observed result, receipt, authoring record or
Java source changes, the Source header and design input facts expire the old comparison and ask
you to reopen diagnostics. `open {discover: "diagnostics"}` lists the candidates; it does not
select a newer result. Context and returning focus to the window refresh these observations.
