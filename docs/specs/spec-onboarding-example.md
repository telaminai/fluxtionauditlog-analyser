# Onboarding Example — Playground Download → Running Mongoose → Analyser (Design Spec)

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-15

Companion to **[tracker.md](tracker.md)** (milestone **M19**) and
**[../admin/docs-site.md](../admin/docs-site.md)** (the site this lands on). Touches the
**playground** (<https://fluxtion-playground.dev/playground>) — its Download feature is the
distribution point, so part of this spec is a *contract* on what that bundle contains (cross-repo,
like M18's O1).

## The gap

Today's onboarding tops out at the **static sample log**: download `sample-audit-log.yaml`, browse it.
Good, but it skips the experience that actually sells the loop — *a live system emitting audit records
that the analyser explains*. And it can't demo Follow, source navigation against code you can edit, or
(later, M18) the server link. Meanwhile the playground already compiles and runs Mongoose examples in
the browser **and has a Download button** — the two best demos in the stack still don't touch
(long-standing observation; this spec finally wires them).

## The experience (target: <10 minutes, nothing pre-installed but a JDK)

1. **Get an example** — open the playground, pick an example flow, **Download**. You get a runnable
   Mongoose example project.
2. **Run it** — one command from the bundle's README (`mvn -q exec` / `java -jar …` — see O1). The
   server starts and writes an **audit log to a predictable path** (`./logs/audit-<name>.yaml`).
3. **Open the analyser** — `jbang analyser@telaminai/fluxtionauditlog-analyser`.
4. **Import the bundle's settings** — File ▸ Import settings… on the `.fluxtion-settings` file shipped
   in the bundle: source roots (relative, they ship in the zip), the event processor FQN — **zero
   manual setup** (this is M15's import doing onboarding duty).
5. **Watch it live** — open `./logs/audit-<name>.yaml` with **Follow** on: records stream in; click a
   node line → the bundled source opens; graph a value; **Explain** a cycle (copy-prompt works without
   a key).

Every headline feature — tail, trace-to-source, graph, assistant — demonstrated against a system the
user is running, on code the user can edit. Step 5's "edit the example, re-run, watch the log change"
is the seed of the whole closed-loop story.

## Part 1 — the bundle contract (playground-side; tracked there, specced here)

The Download bundle MUST contain:

| Item | Why |
|---|---|
| runnable Mongoose example (source + build, or jar + config — O1) | the thing that runs |
| **audit logging pre-enabled** — `EventLogManager` → file sink at `./logs/audit-<name>.yaml`, level INFO | no configuration step; the log path the docs can name |
| the **generated EventProcessor source** + the example's node sources | source navigation works out of the box |
| **`analyser-settings.fluxtion-settings`** — relative source roots + EP FQN | File ▸ Import = zero-setup (M15) |
| **`README.md`** — run command, the log path, and "open this with the analyser" linking the tutorial page | the bundle itself funnels to the analyser |
| **admin REST enabled** (`serverplugin-rest` on a known localhost port, named in the README and the settings file) | the example doubles as **M18's validation bench** (below) |

Contract notes:
- Paths in the settings file are **bundle-relative** (`~`-relative won't survive; M15 import already
  handles relative roots — verify, else this is a small analyser fix).
- The bundle README's analyser link is the **reverse funnel**: every playground download advertises
  the analyser, not just vice versa.
- Version pinning: the bundle names the Mongoose version it was generated against (O3).

## Part 2 — analyser-side and docs-site work

- **Tutorial page** `docs/site/tutorial-playground.md` — "From playground to analyser in 10 minutes",
  teaching in three parts (each with a screenshot):
    1. **Run the example** — download from the playground, one run command, where the audit log
       appears; import the bundled settings file (one dialog, everything configured).
    2. **Analyse & tail** — open the log with **Follow** on, watch records stream, filter to an event
       type, click a node line to land in the bundled source, graph a value over time.
    3. **The LLM assistant** — select records, **Explain** (works keyless via copy-prompt), and the
       round trip: watch the assistant plot/flag/filter its findings into the views you're reading.
  Nav: under **Getting started** (third child).
- **Cross-links**: getting-started Quick start step 2 ("No log yet?") gains the playground option next
  to the static sample; producing-a-log.md links it as "want a live producer to try?"; landing page
  "Get going" mentions it.
- **Analyser changes: none required** for v1 (Follow, import, source nav all shipped). One candidate
  polish item if testing shows friction: a **File ▸ Open example…** helper that takes the bundle
  folder and does import + open + Follow in one action (defer unless the tutorial reads clunky
  without it).

## Synergy: this example is M18's validation bench

The same bundle validates the **Mongoose server link** (spec-closed-loop Part B) end to end: a
known-good local server with the admin REST on, a predictable log, restart-safe (it's disposably
local — the per-link dev opt-in is honestly true here). Concretely:

- **M18.0 (spike)** runs *against this example* — verify status / sink-path discovery /
  `EventLogControlEvent` / lifecycle on it; **any missing admin capability becomes a
  `fluxtion-server-plugins` PR** (updates to the Mongoose plugin are expected, not exceptional —
  budget for them).
- M18.2's "Open server's audit log", M18.3's level control (watch Follow get chattier live), and
  M18.4's dev restart all get their acceptance demos on this bundle.
- Later, the tutorial gains an optional part 4 ("control the server from the analyser") once M18.1–3
  ship — the onboarding page and the feature validate each other.

## Acceptance

A fresh machine with only a JDK: playground → Download → one run command → jbang one-liner → import →
Follow shows live records → click-to-source lands in the bundled example code → Explain produces a
grounded answer. Timed under 10 minutes by someone who isn't us.

## Open questions

- **O1** — bundle form: full Maven project (edit-and-rerun works, heavier) vs runnable jar + config
  (fastest to run, no edit story). Leaning **Maven project** — the edit-rerun-rewatch moment is the
  point.
- **O2** — which example: needs to be small enough to read, busy enough to graph (a periodic
  price-feed / order-flow toy that emits a few events per second — visible motion in Follow).
- **O3** — version pinning between bundle and Mongoose release; who regenerates bundles on release.
- **O4** — does M15 import accept bundle-relative source roots today, or only absolute/`~`-relative?
  (Small analyser fix if not.)
