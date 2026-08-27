# Claude Code guidance — Fluxtion Audit Log Analyser

**Read [`docs/ONBOARDING.md`](docs/ONBOARDING.md) first** — one-page orientation (what this is,
architecture, conventions). This file is only the rules that must never be skipped.

## Hard rules

1. **This repo is PUBLIC.** Never commit real venue/vendor/book/thread/logger/account names — use
   `DEMO` / `marketMaker-DEMO` / `com.acme…` placeholders. Before committing anything containing log
   data, fixtures, screenshots or examples, run the sweep — four terms:
   `grep -ri "aquis\|talos\|nonco\|v12technology" --exclude-dir=target .`
   **It necessarily matches the two files that STATE the rule** — this one and `docs/ONBOARDING.md` —
   because they have to spell the terms to name them. "Clean" therefore means *no other file*, and the
   check that has no exemption to remember is:
   ```
   git ls-files -z | xargs -0 grep -ril "aquis\|talos\|nonco\|v12technology" \
     | grep -vE '^(CLAUDE\.md|docs/ONBOARDING\.md)$'
   ```
   That must print nothing. The unwritten version of this exemption was applied silently and
   differently by two sessions, each reporting "sweep clean" (2026-08-25); it is written down now so
   the phrase means one thing.
   **The cost of a term is that it may not be spelled anywhere else — including in prose ABOUT the
   rule.** A mechanical rule cannot tell a mention from a leak. Describe the term ("the fourth sweep
   term", "a vendor-domain header") or, in code, read it: `DemoAssetsTest` parses the sweep line above
   at runtime, so it follows the rule when the rule changes.
   **The sweep cannot see inside images.** It passed for the whole life of the repo while the release
   screenshots carried real venue, vendor and project names onto the public docs site (found 2026-08-16).
   Screenshots are therefore generated, not taken: `python3 tools/capture-docs.py` drives a real analyser
   loaded **only** with the demo fixture, **under an isolated `user.home`** — until 2026-08-27 it ran under
   the real one, and the moment a surface listed the configured processors (M37) three shots carried a real
   venue's class name and an employer's package. Reading the images caught it; the isolation makes it
   impossible. Capture by hand only when the harness cannot reach the surface,
   and then read every visible string — title bar, status bar, paths — before committing.
   **The sweep cannot see git metadata either.** **214** commits carry an employer-domain author
   email into the public history — 132 on the third sweep term's domain, 82 on the fourth's. Rewriting
   is ruled out by rule 3, so that history is accepted and recorded here.
   **Counted again 2026-08-25** (M36/M19 release check): the third-term total has not moved since the
   config was pinned, so the mitigation is holding; the fourth is 82, not the 81 this paragraph
   claimed, and the newest such commit is still dated 2026-08-20 — a miscount, not a new leak. The
   personal-address count is deliberately no longer recorded here: it climbs with every commit, so a
   fixed number is guaranteed to rot, and it was never the number that mattered.
   **This paragraph previously claimed 110 commits and that the repo-local `user.email` was pinned.
   Both were wrong** (found 2026-08-20, during the M33 release check): the config was never pinned,
   so the leak kept growing — every commit made on 2026-08-20 before that check carries it.
   The config is pinned now. Verify it, and do not take this file's word for it:
   `git config user.email` must print the personal address, and
   `git log --format='%ae' | sort | uniq -c` must show no new employer-domain commits. **Run both
   before every release**, because a recorded mitigation that stopped being true reads exactly like
   one that is.
2. **CHANGELOG.md**: every user-visible change adds a line under `## [Unreleased]` in the same commit.
   The release workflow stamps it; it feeds the GitHub release, the in-app notes, and the docs site.
3. **Branch**: `main` only (trunk-based, always releasable); `pull.rebase` is set — no merge bubbles.
   Never force-push; never resurrect `master`.
4. **Tests gate everything**: `mvn test` green before commit. Pure logic gets unit tests; Swing does
   not (headless CI) — verify UI by building and running the jar.
5. **Docs site** (`docs/site/`, root `mkdocs.yml`, MkDocs Material): `mkdocs build --strict` must pass
   before pushing site changes (CI link-checks). Local: `pip3 install -r docs-requirements.txt &&
   mkdocs serve`.
6. **Protocol *and framework* work**: before implementing against any external protocol, vendor API
   (MCP, admin REST, …) **or Fluxtion's own execution semantics**, **read the live source of truth
   first** — never infer it.
   - Protocols: MCP shipped a breaking revision within months of our spec being written, caught only
     this way (docs/handoff/completed/handoff_15_aug_2026_1_report.txt §3).
   - Fluxtion: this app's model of dispatch, audit logging and propagation is only correct if the
     framework's is. Authoritative: **[`docs/claude.txt`](https://raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt)**
     (framework reference), then the [golden path](https://fluxtion-playground.dev/fluxtion-golden-path.md).
     Every defect in the M21 topology work came from inferring instead of reading — including two
     unsound inferences that shipped. See ONBOARDING § *Understand Fluxtion's execution model*.
7. **Tracker discipline**: finished items → ☑ in `docs/specs/tracker.md`; fully-shipped
   milestones/rounds → move to `docs/specs/completed/tracker.md`. The live tracker holds only
   in-progress + future work.

## Current work

`docs/specs/tracker.md` has the delivery order; fully-shipped milestones live in
`docs/specs/completed/tracker.md`. **Shipped through 2026-08-25 (v1.7.0 released; M34.0–.2, M35.1–.8/.10/.11 and §E merged to main, unreleased):** MCP bridge
(M13.1–13.4), topology view + step-through (M21 core), topology usability (M22, 36 of 41), project
profiles (M20), focus-as-filter + named focuses (M27), agent-efficiency verbs (M26), expression
conditionals + rolling windows + guides/bands (M28), external series (M29 core), rolled log sets +
time-order validation (M30), log-source plugins (M31 core), marker series + point-snapped mouseover
(M32, now COMPLETE: legend, rug, PDF markers table, external-CSV source), investigation reports
(M33 core: typed sections, D-I3a authoring context, Reports tab, own share category), **log + graph
lifecycle (M35.1–.8: close/reset, re-pair on open, switch processor, discovery that offers and never
selects, project-as-session-boundary, no modal in the load path, the pairing stated persistently,
`open {project}` / `open {close: "project"}`; .9 an `OpenRequest` travels with every load so no dialog ever
fires at an empty screen — six such modals found and closed across the milestone; .10/.11 committed profiles
are project-relative and never rewritten by a no-op open)**, **§E provenance** (a log declares which SYSTEM it came from, not
just which file) and **source adapters M34.1–.2** (`graph(Path)` on the reader SPI, DECLARED/INFERRED
provenance, `GraphSource` precedence — opened beats supplied, a reader's graph clears with its log —
ordering claim honoured by the view, coverage refusing an inferred graph) and **M34.3** (the record format's
normative specification — *The audit log ▸ Format specification* — with a conformance suite the built-in
reader and the SPI both pass). **M37 the Project panel** (shipped 2026-08-27: what is in force — project, log, graph, processors,
roots, reports — rendered from `context` for people; the west column is draggable) and **M38.1** (runbook
POINTERS — the profile records a location, never contents; `docs/specs/spec-portable-context.md`). **M38.2–.6**
(vocabulary pointer, environments + provenance defaults, repeatable analyses, report destinations, path anchors)
are implemented on `feat/m38-portable-context` — .1–.4 reviewed and closed, .5–.6 under review; the branch
rebases onto main once at merge. **M40.1** (audit readiness read from the graph) shipped 2026-08-27 on main.

Also merged: **M19.6/.7** (`tools/bench/` — the dev loop's conformance bench, §H's home, runnable today
against a stub; `analyser --rest` for an agent-driven fresh start) and **M36.1–.4** (the start page: the
no-log STATE, four sections each ending in an action against a demo set that ships in the jar; the
first-run dialog is gone for everyone). Open analyser-side: **M36.5** (docs page + generated shot),
**M19.3/.4** (tutorial, publish-gated on the playground Download), **M19.8** (bench in CI), **M34.4/.5** (first foreign adapter;
per-cycle concurrency marker); **M36** start page (spec'd, `feat/m36-start-page`); M22 remnants — PNG export (22.3), alternative layouts
(22.6), re-dispatch cause (22.11, needs `UP-FLX-10` in
[`docs/proposals/upstream-asks.md`](docs/proposals/upstream-asks.md) — the holding pen for anything
belonging to another repo); **M20.5** (project artifact pointers); **M29.5** (optional embed);
**M33.5** (fix-brief fold, gated on journal↔log pairing) and **M33.6** (marker-CSV chooser dialog,
owner call); the golden-fixture corpus follow-ups (N1 + the clamp fixture); the un-started **polish
round** (`docs/handoff/completed/handoff_17_aug_2026_1.txt`). **M18 is CLOSED** in favour of
[`spec-agent-brokered-dev-loop.md`](docs/specs/spec-agent-brokered-dev-loop.md) (ACCEPTED v2) —
its cross-repo asks (Mongoose MCP + `~/.mongoose/servers/`, two playground catalogue fields, the
Chronicle reader) **were** gated on that spec's §H conformance harness having a home — **that gate is
met** (M19.6, `tools/bench/loop-bench.py`, merged 2026-08-25), so they are ready to file and are not
yet filed. Cross-repo tracks:
the **M31.4r** example reader (playground) and the **M19.1** playground bundle contract
(`docs/specs/spec-onboarding-example.md`). Two standing design decisions (tracker ▸ Decisions), both intact after M18's closure: server verbs
never appear on **the analyser's** action socket — the adopted design *strengthens* this by moving
server control to a Mongoose-side MCP tool, so the analyser gains no server-mutating code at all;
and agent fixes arrive as **evidence-linked PRs**, never direct edits.

## Build & run

```bash
mvn package                                              # runnable fatjar (FlatLaf shaded)
java -jar target/fluxtion-auditlog-analyser-*.jar [log]  # run; src/test/resources/sample.yml to open
```
