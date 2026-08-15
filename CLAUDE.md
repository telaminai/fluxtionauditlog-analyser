# Claude Code guidance — Fluxtion Audit Log Analyser

**Read [`docs/ONBOARDING.md`](docs/ONBOARDING.md) first** — one-page orientation (what this is,
architecture, conventions). This file is only the rules that must never be skipped.

## Hard rules

1. **This repo is PUBLIC.** Never commit real venue/vendor/book/thread/logger/account names — use
   `DEMO` / `marketMaker-DEMO` / `com.acme…` placeholders. Before committing anything containing log
   data, fixtures, screenshots or examples: `grep -ri "aquis\|talos\|nonco" --exclude-dir=target .`
   must be empty.
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
     this way (docs/handoff/handoff_15_aug_2026_1_report.txt §3).
   - Fluxtion: this app's model of dispatch, audit logging and propagation is only correct if the
     framework's is. Authoritative: **[`docs/claude.txt`](https://raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt)**
     (framework reference), then the [golden path](https://fluxtion-playground.dev/fluxtion-golden-path.md).
     Every defect in the M21 topology work came from inferring instead of reading — including two
     unsound inferences that shipped. See ONBOARDING § *Understand Fluxtion's execution model*.
7. **Tracker discipline**: finished items → ☑ in `docs/specs/tracker.md`; fully-shipped
   milestones/rounds → move to `docs/specs/completed/tracker.md`. The live tracker holds only
   in-progress + future work.

## Current work

`docs/specs/tracker.md` has the delivery order. **M13.1–13.4 (MCP bridge) shipped 2026-08-15.** Next up
(independent tracks): the **M18.0** admin-surface spike (`docs/specs/spec-closed-loop.md`) and the
**M19.1** playground bundle contract (`docs/specs/spec-onboarding-example.md`) — both cross-repo;
analyser-side next is **M20.1** (project profiles, `docs/specs/spec-project-profiles.md`). Two standing design decisions (tracker ▸ Decisions): server verbs
are **never** assistant actions; agent fixes arrive as **evidence-linked PRs**, never direct edits.

## Build & run

```bash
mvn package                                              # runnable fatjar (FlatLaf shaded)
java -jar target/fluxtion-auditlog-analyser-*.jar [log]  # run; src/test/resources/sample.yml to open
```
