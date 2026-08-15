# Onboarding Example — Playground Download → Running Mongoose → Analyser (Design Spec)

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-15

Companion to **[tracker.md](tracker.md)** (milestone **M19**) and
**[../admin/docs-site.md](../admin/docs-site.md)** (the site this lands on). Touches the
**playground** (<https://fluxtion-playground.dev/playground>) — its Download feature is the
distribution point, so part of this spec is a *contract* on what that bundle contains (cross-repo,
like M18's O1).

## The bigger picture — one of three pathways

This tutorial is the **"How do I support a Fluxtion system"** pathway — the third leg of the Telamin
learning journey, each owned by its own property and eventually tied together by the umbrella Telamin
site:

| Pathway | Question it answers | Lives at |
|---|---|---|
| **Architect** | How do I *build* a Fluxtion system (with AI)? | <https://fluxtion-playground.dev/build-with-ai> |
| **Extend** | How do I write Mongoose plugins? | <https://telaminai.github.io/mongoose-plugins/> |
| **Support** | How do I run, observe, diagnose and fix one? | **this tutorial** (analyser docs site) |

Implication for the tutorial: alongside the own-system end-bridge (§Part 2), the closing section also
links the **sibling pathways** ("Want to build one from scratch? → build-with-ai · Extending the
server? → mongoose-plugins") — every pathway routes to the other two, so the umbrella site only has to
route by intent, not re-teach.

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
4. **Load the bundle's project** — the bundle ships a **project profile** at
   `.analyser/project.fluxtion-settings` (M20's canonical path): source roots (relative, they ship in
   the zip), the event processor FQN — **zero manual setup**. Once M20 lands the analyser auto-detects
   it beside the log; until then it's File ▸ Import settings… on that file (M15's import doing
   onboarding duty).
5. **Watch it live** — open `./logs/audit-<name>.yaml` with **Follow** on: records stream in; click a
   node line → the bundled source opens; graph a value; **Explain** a cycle (copy-prompt works without
   a key).

6. **Edit it — in your IDE, with your own LLM** — open the Maven project in IntelliJ/VS Code; the
   bundled `CLAUDE.md` bootstraps the IDE's agent with Fluxtion knowledge. Change a node, re-run,
   watch the log change in Follow. **Division of labour is deliberate**: the *in-app assistant*
   analyses the log; *code editing* happens in the user's IDE with their own agent (the
   hand-off-don't-embed principle from spec-closed-loop, experienced on day one).

Every headline feature — tail, trace-to-source, graph, assistant — demonstrated against a system the
user is running, on code the user can edit. Step 6's edit → re-run → re-watch is the seed of the whole
closed-loop story.

## Part 1 — the bundle contract (playground-side; tracked there, specced here)

The Download bundle MUST contain:

| Item | Why |
|---|---|
| runnable Mongoose example (source + build, or jar + config — O1) | the thing that runs |
| **audit logging pre-enabled** — `EventLogManager` → **text file sink** at `./logs/audit-<name>.yaml`, level INFO | no configuration step; deliberately the file sink (not Chronicle/binary) because that's the sink the analyser reads |
| the **generated EventProcessor source** + the example's node sources | source navigation works out of the box |
| **`.analyser/project.fluxtion-settings`** — relative source roots + EP FQN, at **M20's canonical project-profile path** so the bundle *is* a project profile (not a separately-named file the detector also accepts) | zero-setup: M20 auto-detects it; M15 import until then |
| **`README.md`** — run command, the log path, and "open this with the analyser" linking the tutorial page | the bundle itself funnels to the analyser |
| **admin REST enabled** (`svc-admin-web` / `serverplugin-rest` — default `127.0.0.1:8181`, named in the README and the settings file) | the example doubles as **M18's validation bench** (below) — `svc-admin-web` is the concrete surface M18.0 spikes against |
| **agent bootstrap — `CLAUDE.md` (+ `AGENTS.md` mirror), layered** (see below) | the user opens the project in their IDE and **their own LLM already knows Fluxtion** — the edit loop needs zero prompting |

**The agent-bootstrap prompt stack — embed a snapshot, reference the canon.** Hosted canonicals
already exist and are maintained: <https://fluxtion-playground.dev/CLAUDE.md>,
<https://fluxtion-playground.dev/fluxtion-golden-path.md>, and the framework canon **`claude.txt`**
(<https://github.com/telaminai/fluxtion/blob/main/docs/claude.txt> — raw:
`https://raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt`; the golden path itself
defers to it on framework semantics). The bundle layers them:

1. **`CLAUDE.md` (thin, example-specific, written at generation)** — this example's flow, the run
   command, the audit-log path, the admin port, "the analyser's endpoint file for query-back" — plus
   an instruction to read layer 2.
2. **Embedded snapshot** of the Fluxtion authoring guidance (`claude.txt` + the golden path's
   non-playground sections — the CheerpJ/portability material is playground-flavoured and wrong for a
   standalone Maven project), **snapshotted at bundle-generation time** so it matches the Mongoose
   version the pom pins. Rationale: agents reliably load local files, not URLs (offline/sandboxed
   agents can't fetch), and "always current" hosted text can describe APIs newer than the bundle.
3. **One canonical-reference line** — the hosted URLs above, labelled "canonical, possibly newer" — so
   connected agents can refresh and humans can browse.

Contract notes:
- Paths in the settings file are **bundle-relative** — and this is a **committed analyser precondition,
  not a "verify"**: `SettingsShare` as shipped expands only `~`-prefixed paths ("anything else is
  verbatim" — a bare `src/main/java` would resolve against the CWD and break unless the analyser is
  launched from the bundle dir). Fix: `SettingsShare.fromPortable` resolves relative roots against the
  **import file's parent directory**. This fix is what makes "one dialog, everything configured" true.
- **Bundles are generated at Download time** by the playground service — never pre-built artifacts —
  so every bundle (code, settings, embedded prompt snapshot) is pinned to the playground's
  then-current Mongoose version at the moment of download. There is no regeneration cadence to own and
  nothing to rot; already-downloaded bundles are self-consistent snapshots, which is the correct
  semantic. _(Resolves O3.)_
- The bundle README's analyser link is the **reverse funnel**: every playground download advertises
  the analyser, not just vice versa.
- **Prefer a Spring-XML-defined example (O2 tiebreaker).** If the example is authored via the
  **design IR** (the [build-with-ai](https://fluxtion-playground.dev/build-with-ai) Spring-authoring
  contract), the bundle also ships its **design XML**, and two things follow: (a) tutorial part 4
  gains a second edit variant — *change the design, regenerate, re-run* — so the ten-minute journey
  demonstrates **all three IRs** (design → graph → record); (b) the bundle becomes a working example
  of **design-to-execution provenance** (committed design XML → generated system → audit record), the
  chain the regulated story will later sell. If the example is XML-defined, the bundle `CLAUDE.md`
  (layer 1) also references the spring-authoring `contract.md`/`skill.md` so the IDE agent can edit at
  the design level, not just the node level.
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
    4. **Edit with your IDE's AI** — open the Maven project in the IDE; the bundled `CLAUDE.md` means
       the IDE agent knows Fluxtion; change a node, re-run, watch Follow pick it up. (Explicitly *not*
       the in-app assistant's job.)

  **The tutorial must end with a bridge, not a full stop.** After the demo the user's real question is
  *"now, my processor?"* — the closing section is **"Do this on your own system →"** linking
  [producing-a-log](../site/producing-a-log.md) (enable auditing on their processor) and the server
  link once M18 ships. Without the bridge the experience ends at a toy and the wow evaporates on
  Monday morning; with it, the tutorial's last click starts the user's real adoption.
  Nav: under **Getting started** (third child). **Screenshot set** (captured once, anonymised per
  policy): playground Download button · terminal run + log path · Import-settings summary dialog ·
  Follow streaming with an event-type filter · click-to-source landing in example code · a graph ·
  an Explain answer · the IDE with `CLAUDE.md` open beside a node edit.
  **Publish gate:** the page is *written* against the bundle contract but **published only when the
  playground's Download actually ships the bundle** — a tutorial that promises a Download that isn't
  there is worse than no tutorial.
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

- **M18.0 (spike)** runs *against this example* — `svc-admin-web` already serves status + processor
  enumeration; verify the three gaps on it (**sink-descriptor discovery, `EventLogControlEvent`,
  lifecycle** — registered admin commands or none); **any missing admin capability becomes a
  `fluxtion-server-plugins` PR** (updates to the Mongoose plugin are expected, not exceptional —
  budget for them). The bundle's file sink keeps discovery simple (a real path to resolve).
- M18.2's "Open server's audit log", M18.3's level control (watch Follow get chattier live), and
  M18.4's dev restart all get their acceptance demos on this bundle.
- Later, the tutorial gains an optional part 4 ("control the server from the analyser") once M18.1–3
  ship — the onboarding page and the feature validate each other.

## Acceptance

A fresh machine with only a JDK: playground → Download → one run command → jbang one-liner → import →
Follow shows live records → click-to-source lands in the bundled example code → Explain produces a
grounded answer. Timed under 10 minutes by someone who isn't us.

## Open questions

- ~~**O1** — bundle form~~ **resolved: full Maven project** — the user views/edits it in their IDE
  with their own LLM session; the edit-rerun-rewatch moment is the point. (Jar-only was rejected: no
  edit story.)
- **O2** — which example: needs to be small enough to read, busy enough to graph (a periodic
  price-feed / order-flow toy that emits a few events per second — visible motion in Follow).
  **Tiebreaker: prefer a Spring-XML-defined example** (§Contract notes) — same effort, and the
  tutorial then demonstrates the design IR too.
- ~~**O3** — version pinning / regeneration owner~~ **resolved**: bundles are generated at Download
  time, pinned to the playground's current Mongoose version; no pre-built artifacts, no cadence to own
  (§Contract notes).
- ~~**O4** — relative source roots~~ **resolved as a committed precondition**: verified `SettingsShare`
  expands only `~`-prefixed paths; the fix (resolve relative roots against the import file's parent)
  is tracker item **M19.2** and gates the tutorial's "zero manual setup" claim.
