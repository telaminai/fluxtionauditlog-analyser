# Onboarding — get up to speed fast (for humans and LLMs)

A one-page orientation to this repo. Read this first, then dive into the specs for detail.

## What this is

A **Java 21 + Swing desktop app** that reads **Fluxtion event-audit logs** — the per-cycle execution
records a Mongoose/Fluxtion `EventProcessor` emits — and lets you browse, filter, summarise, **graph**,
navigate to source, and **explain** them with an LLM. Index-first, so it stays fast on multi-GB logs.
One UI dependency (FlatLaf); everything else is the JDK.

- Entry point: `telamin.fluxtion.audit.analyser.Main`
- Maven coordinates: `com.telamin:fluxtion-auditlog-analyser` (pom `<version>` is a `0.0.0-SNAPSHOT`
  placeholder; real versions are stamped at release).

## Build, test, run

```bash
mvn package                                             # build the runnable fatjar (FlatLaf shaded in)
java -jar target/fluxtion-auditlog-analyser-*.jar [log] # run it
mvn test                                                # run the unit tests
mvn -o clean verify                                     # offline full build + tests
```

`src/test/resources/sample.yml` is a representative audit log to open.

## Architecture — packages under `telamin.fluxtion.audit.analyser.analyser`

| Package | Role |
|---|---|
| `parse` | lenient, depth-aware tokenizer → `LogStore` (heap or memory-mapped) |
| `index` | compact columnar `LogIndex` for fast browse/filter/summary |
| `model` | `LogRecord`, `NodeLog` and friends |
| `filter` | `FilterState` — shared time-range + dimensions + text + group mode |
| `summary` | `SummaryBuilder` — per-dimension counts/rates |
| `graph` | `SeriesExtractor`, `Expr` (formula engine), `GraphKey`, `Series` |
| `source` | `SourceService`, `EventProcessorModel`, `SourceNavigation`, Maven source resolver |
| `llm` | prompt building + the two-way assistant action loop |
| `net` | localhost REST transport for the assistant actions |
| `config` | `AppConfig`, `ConfigStore`, `GraphSpec`, `SettingsShare` (export/import) |
| `export` | CSV/YAML record export |
| `core` | `Background` (off-EDT work) |
| `ui` | all Swing (`MainFrame`, panels, dialogs, `ChartPanel`, theming) |

Data flows: `parse` → `LogStore`/`index` → `filter` scopes everything → `summary`/`graph`/`ui` read it.

## Understand Fluxtion's execution model before touching the analyser's model of it

The analyser interprets logs a Fluxtion processor emits, so several of its behaviours are only correct if
the framework's semantics are. **Read these rather than inferring them** — every defect found in the
topology work (M21) came from inferring:

- **[`docs/claude.txt`](https://raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt)** in the
  fluxtion repo — the framework reference, and authoritative on semantics.
- **[golden path](https://fluxtion-playground.dev/fluxtion-golden-path.md)** — the blessed shape of an
  audited AOT graph, and the gotchas.
- **[playground CLAUDE.md](https://fluxtion-playground.dev/CLAUDE.md)** — the agent-facing guide.

The three that bite hardest here:

1. **A node appears in `nodeLogs` only if it writes audit output**, at the level in force. Silence is not
   absence of execution — most of `topology`'s `Execution` model exists for this.
2. **The boolean a handler/trigger returns is the dirty/propagation control**, and
   `@OnTrigger(dirty=false)` fires on the **inverse**. A child running proves its parent ran, not which
   way the parent answered.
3. **`@AfterEvent` and lifecycle callbacks (`@Initialise`/`@Start`) fire without upstream propagation**, so
   "something downstream logged, therefore this ran" is unsound for them. GraphML carries no annotations,
   so the analyser cannot detect them.

`examples/fixture-generator/` is a real, minimal Fluxtion project (starter-shaped, AOT via
`fluxtion-maven-plugin`) that regenerates the topology fixtures — the fastest way to see the model
behave, and to produce a paired graph + log to test against.

## Conventions that matter

- **Pure logic is unit-tested; GUI is not** — anything headless-testable has tests (parse, index,
  filter, graph, expr, config, llm parsing). Swing panels are constructed in tests but never shown; the
  CI runner is headless. Don't expect to verify UI interactions in tests — build the jar and run it.
- **Off-EDT work** goes through `core.Background`; results are applied back on the EDT.
- **Theme-aware**: colours derive from FlatLaf via `ThemeManager.isDark()` / `UiTheme`. Support light
  and dark.
- **Graphable values are TOP-LEVEL numeric/boolean nodeLog keys** (`{ key: value }`). A number nested
  inside a Java `toString()` (e.g. `price=` in `QuoteLadder(price=…)`) is text, not a key — not graphable.
- **Config** persists to `~/.fluxtion-analyser/config` (cleartext, single-user). The API key lives there
  and is never exported by `SettingsShare`.
- **A new `context` key names its human surface and its docs page IN THE SAME COMMIT.** The socket is
  usually the easiest surface to build, so features ship agent-first and the human half lags — which
  reads to a user as a capability the tool does not have. It has now bitten three times in two
  milestones: M40 shipped a verdict to `context` while the CHANGELOG and docs promised a Topology tab
  that showed nothing (caught in review), M40 again with three site pages unmentioning a shipped row,
  and M38.3's report header dropping a qualification `context` and the panel both carried. So when you
  add a key, answer three questions in the commit: **which panel shows it, which page says so, and does
  a report that outlives the session carry it?** "Agents only, for now" is a decision worth making
  deliberately — it is just never worth making by accident.

## Reviewing rather than changing?

[`docs/handoff/REVIEWER-ORIENTATION.md`](handoff/REVIEWER-ORIENTATION.md) is the map for a session
arriving to **judge** a body of work: the four-layer stack and who owns each, the vocabulary, the plugin
SPI, what is currently live and unreviewed. It does not repeat this file — the decisions and blind spots
below serve both jobs.

## Standing decisions — the ones a change must not break by accident

Each is argued somewhere; this list exists so you meet them before you meet the code that depends on
them. Breaking one deliberately is a decision to record. Breaking one by accident has happened, which is
why they are collected here.

| Decision | In one line | Argued in |
|---|---|---|
| **Declared, never inferred** (D-A2, D-A1a, §E) | With no evidence the answer is UNKNOWN — never "probably fine". A graph, a provenance, an audit verdict is what someone declared, not what we guessed. | `spec-source-adapters.md`, M40's `AuditReadiness` |
| **A surface renders the model; it is never a second model** (D-L1) | The Project panel reads only keys `context` puts. Enforced by a source-reading test, not by memory. | `spec-loaded-panel.md` |
| **Reveal-only** (D-L3) | Every Project-panel button navigates or copies. Nothing on it mutates state; a bytecode test asserts it never names `MainFrame`. | `spec-loaded-panel.md` |
| **Pointers, never contents; never executed** (D-C2) | A runbook or glossary is a location in the repo. The analyser stores no instructions and runs nothing. | `spec-portable-context.md` |
| **Discovery offers, and never selects** (M35.4, D-AI5) | Found a graph, a skill, a frontmatter description? Offer it. A person declares it. Applies to facts as well as files. | `spec-ai-menu.md` |
| **The panel STATES; the menu ACTS** (D-AI1) | Mutation has one home, so a reader never wonders whether a button will change their project. | `spec-ai-menu.md` |
| **Nothing on the AI menu runs anything** (D-AI4) | Recorded at the surface where "just add a Run item" will be tempting. Enforced by a source-text test. | `spec-ai-menu.md` |
| **The verb surface is pinned** | `assertEquals(14, VerbSchemas.all().size())`. Adding a verb is a decision, not a convenience. | tracker ▸ Decisions |
| **Server verbs never appear on the action socket** | The analyser acquires no server-mutating code at all. Agents drive Mongoose directly. | `spec-agent-brokered-dev-loop.md` §B |
| **Agent fixes arrive as evidence-linked PRs, never direct edits** | | tracker ▸ Decisions |
| **The refusals are load-bearing** (D-T4) | Every place the analyser declines to assert something is now part of the market position. Loosening one is a position change, not a tweak. | `spec-trust-structure.md` |

## The gates are each blind outside their own reach

This is the most useful thing to know if your job is checking. Three gates, each sound within its scope
and silent outside it — and the silence is what gets trusted:

- **The four-term sweep cannot see inside images.** It passed for the whole life of the repo while
  release screenshots carried real names onto the public site (found 2026-08-16). Screenshots are now
  generated under an isolated `user.home`, never taken.
- **The sweep cannot see git metadata.** 214 commits carry an employer-domain author address; the count
  is pinned in CLAUDE.md and re-checked before every release.
- **`mkdocs build --strict` cannot see `docs/specs/`.** It is not part of the built site, so the link
  checker never visits it. Twelve stale links in two days were found by reading before
  `SpecLinksResolveTest` was added.

If you are relying on a gate, check first that it can see the thing you are relying on it for.

## Process

- **This repo is PUBLIC** (since 2026-08-14, fresh single-commit history). Everything committed is
  world-readable. **Anonymisation is policy**: no real venue / vendor / book / thread / logger /
  account names anywhere — samples, fixtures, screenshots, javadoc examples all use neutral
  placeholders (`DEMO`, `marketMaker-DEMO`, `com.acme…`). Sweep before committing:
  `grep -ri "aquis\|talos\|nonco\|v12technology" --exclude-dir=target .` — four terms. It matches
  this file and CLAUDE.md by necessity (they name the terms); "clean" means no OTHER file. CLAUDE.md
  rule 1 has the exemption-free form of the check.
- **Trunk-based**: `main` is the only long-lived branch and is always releasable; short-lived feature
  branches optional (see `docs/admin/release-process.md`). `pull.rebase` is on — keep history linear.
- **Changelog is the one manual habit**: add a line under `[Unreleased]` in `CHANGELOG.md` with any
  user-visible change; the release workflow stamps it. It's bundled into the jar (Help ▸ Release notes)
  **and** injected into the docs site's release-notes page at deploy.
- **Releases**: Actions ▸ Release ▸ type a version — CI tests, stamps, tags, builds, publishes, and
  re-deploys the docs site. v1.0.0 shipped 2026-08-14. JBang users need `--fresh` to pick up a new
  release (cached stable-name jar).
- **Docs site**: **MkDocs Material** (matches mongoose-plugins; migrated from Jekyll — the
  `github-pages` gem can't run on modern Ruby). Live at
  <https://telaminai.github.io/fluxtionauditlog-analyser/>. Sources in `docs/site/`, config in root
  `mkdocs.yml`. Local preview: `pip3 install -r docs-requirements.txt && mkdocs serve`. Before pushing
  site changes: `mkdocs build --strict` must pass (CI enforces; it link-checks).

## Where to read next

- `docs/specs/spec.md` — the product spec (start here for the "what/why").
- `docs/specs/tracker.md` — **live** work items only, with the current delivery order (next up:
  **M13** MCP bridge and the **M18.0** admin-surface spike — independent tracks); finished milestones
  are archived under `docs/specs/completed/`.
- `docs/specs/spec-closed-loop.md` — the active design: agent fix handoff (M12.4) + Mongoose server
  link (M18). Its two load-bearing decisions are recorded in the tracker's Decisions: server verbs are
  **never** assistant actions, and agent fixes arrive as **evidence-linked PRs**, never direct edits.
- `docs/specs/spec-assistant-actions-mcp.md` — the M13 MCP transport design.
- `docs/specs/completed/` — shipped milestones and their design specs (history).
- `docs/admin/release-process.md`, `docs/admin/docs-site.md` — ops.

## Handoffs between sessions/machines

Work delegated to another session (human or LLM, any machine) travels through the repo:

- **Brief**: `docs/handoff/handoff_<date>_<n>.txt` — self-contained task brief (orient-first reading
  list, slices, constraints, process). Commit and **push** it; the receiving session's starting prompt
  is just "Read docs/handoff/<file> and do what it says."
- **Report**: when the work completes, the receiving session writes
  `docs/handoff/handoff_<date>_<n>_report.txt` — what shipped, what was skipped/deferred and why, and
  any spec-vs-reality mismatches found (fix the spec too; never silently diverge) — committed with its
  final change.

The paired brief/report trail makes every delegated work block auditable from the repo itself.
When a cycle is fully done (merged/shipped, review answered), its brief/report/review files move to
`docs/handoff/completed/` — the live directory holds only in-flight correspondence.

Screenshots *and* the sample-conversation transcripts are generated (`tools/capture-docs.py`,
`tools/capture-conversations.py`) from a real run under an isolated home, and read before committing — a
typed transcript rots the release after it is written and nothing fails.

Two review lessons from M37–M40 (2026-08-27), each found twice by two sessions independently, so they
are checklist items rather than anecdotes:

- **A feature that ships agent-first lags its human surface and its docs.** For every new `context` key,
  name in the same commit the human surface that shows it (usually a Project-panel row) and the docs page
  that says so — and if the docs promise a surface, that surface must exist (M40.1 promised the Topology
  tab; M38.3 dropped the qualification exactly where it leaves the session, the report header).
- **`MainFrame.context()` returns early on a fresh start** (`filter == null`). Anything a fresh start
  should report — a graph, the roots, a verdict — goes ABOVE that return; twice a correctly `put` fact was
  invisible to agents because it sat below it.

### Loop handoffs — many iterations, one review

A third style, alongside brief → report → review and the ad-hoc ledger. Introduced 2026-08-30 for the
**experience loop** ([`docs/experience/`](experience/README.md)), and reusable for anything measured the
same way.

**When it applies.** The work is *documentation or content*, the blast radius is low, and the only honest
test is running it rather than reading it. Reviewing each edit costs more than it catches; reviewing the
**trend** is what tells you anything.

**How it differs.**

| | brief → report → review | ad-hoc ledger | **loop handoff** |
|---|---|---|---|
| Reviewed | every slice | every entry | **the trend, at the end** |
| Evidence | the report | the entry | **each round's recorded run** |
| Reviewer judges | is this change right | is this change safe | **is it converging, and did it shrink** |

**Obligations on the author** — these replace per-slice review, so they are not optional:

- **Record every round before editing**, including the environment (key present? analyser reachable? which
  artefact?). A run whose conditions are unknown proves nothing.
- **Rotate the task, and hold one out.** Repeat a task and the docs will pass because they now describe it.
  A held-out pass is the only pass worth anything.
- **Record what went UNUSED**, and delete it. A loop that only adds produces documentation nobody reads.
- **Archive superseded sets**, so a reviewer can see whether a later one got *worse*.
- **Touch no code.** That is what lets another session work the same repo in parallel.

**A finding counts when it RECURS.** Rounds are non-deterministic; one agent hitting something once is
noise, the same friction across two different tasks is a defect.

**Stop and hand off** when a round yields no new recurring findings, or the held-out task runs clean — or
early, saying so, if findings alternate rather than reduce, which means editing will not fix it.

### Ad-hoc un-reviewed changes
Sometimes a session working primarily in **another repo** (a downstream consumer of the analyser) hits an
analyser-side bug and fixes it in passing — too small for a full brief, but it still lands on `main`
without a review. Log
every such change in **`docs/handoff/unreviewed-changes.md`**: one entry with the commit SHA, what & why,
files, what was verified, and — the load-bearing part — **what the reviewer must still check** (Swing UI
is not unit-tested, so "build and run the jar and confirm X" belongs here). The change still obeys the
normal gates (`mvn test` green, CHANGELOG line, leak sweep, `main`); the only thing it skips is
*pre*-merge review.

The next session to pull **reviews the open (`☐`) entries first**: read the commit, run `mvn test`,
verify what the entry flagged, then tick it `☑ reviewed <date>` with a verdict (or file a follow-up).
This keeps ad-hoc fixes moving without losing the second-pair-of-eyes guarantee — the review just happens
after the fact, and the ledger makes the debt explicit rather than silent.

## Tracker discipline

When you finish work: mark items ☑ in `docs/specs/tracker.md`; when a whole milestone/round is done,
**move it to `docs/specs/completed/tracker.md`** and keep the live tracker to in-progress + future
only. New designs get a `spec-<name>.md`; superseded ones move to `completed/`.

## Gotchas

- The fatjar version comes from the manifest via `ReleaseNotes.version()` — use
  `getPackage().getImplementationVersion()`, NOT a classpath manifest scan (that returns a dependency's).
- Shade regenerates `dependency-reduced-pom.xml` on `package`; it's tracked, so commit or revert it
  deliberately.
