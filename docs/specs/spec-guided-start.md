# Spec — guided start: an install prompt, and an LLM that tutors by driving the UI

**Status:** PROPOSED 2026-08-30 (owner idea). **Tracker:** [tracker.md](tracker.md).
**Related:** [spec-onboarding-example.md](spec-onboarding-example.md) (the M19 pathway this joins),
[spec-authoring-experience.md](spec-authoring-experience.md) (D-AX1c — the agreed reference set this
becomes the first entry in), [spec-trust-structure.md](spec-trust-structure.md) (D-T3, which constrains
what a tutor may say).

## The idea, in the owner's terms

> *One thing we could add is an install-analyser prompt for an LLM: it does all the jbang, downloads a
> template project, sets up MCP access, runs the Mongoose server to generate some data, has the correct
> skills, and then fires the UI up. The LLM can then act like a tutor driving the UI to show the
> capabilities. This last point is really interesting for getting people started.*

Two halves with very different costs. The **setup** half is a script that already exists in pieces. The
**tutor** half is the novel one, and it turns out to need no new product surface at all.

## D-G1 — the tutor needs NO new verbs; the socket was already built for this

**Verified against `VerbSchemas.all()`, 2026-08-30** — the surface is pinned at 14 and is already split by
whether a verb moves the UI:

| Verb | Its own description |
|---|---|
| `open` | open an audit log and/or a processor `.graphml` — or close what is open |
| `filter` | *"Narrow **every view**"* |
| `topology` | *"**Drive the Topology tab**: what is shown, what is selected"* |
| `goto` | *"Select the record containing an anchor **in the table**"* |
| `graph` | create/append a named time-series graph |
| `flag` | *"Bookmark records so your findings are **reviewable in the UI**"* |
| `context` | *"Read-only: **what the user is currently looking at**"* |
| `screenshot` | *"Write a PNG of the app's own window. **Painted by the app**"* |
| `aggregate` | *"Read-only … **never mutates the UI**"* |

So an agent can already open a log, narrow it, drive the topology, select a record, draw a chart and flag
a finding — and `context` + `screenshot` close the loop by telling it **what the user can actually see**.
The tutor is a *prompt over the existing socket*, not a feature.

## D-G2 — the tutor POINTS; the screen PROVES

The load-bearing design rule, and it comes straight from D-T3 (evidence versus testimony).

A tutor that narrates is **testimony** — precisely the thing this product argues a buyer should not have to
trust. So:

- **The tutor may not assert a number the user cannot see on screen.** It drives the view, then says
  *"look at the coverage panel"* — it does not say *"54 nodes never ran"* while the user is on another tab.
- **Before making a claim about a view, the tutor calls `context`** (and `screenshot` where it matters) to
  confirm the view is actually showing it. An unverified "as you can see…" is the failure mode.
- **A capability the tutor cannot show on screen, it does not claim.**

This is not decoration. Done this way the tutorial *is* the pitch: the newcomer watches an LLM make claims
and immediately checks each one against the instrument. Done the other way it is a chatbot describing
software, and it quietly contradicts the thing the product is for.

## D-G3 — the setup half is SHELL, not analyser surface

The LLM client runs these; none needs a new analyser capability:

| Step | Status |
|---|---|
| Install the analyser | **exists** — the jbang one-liner |
| Get a project with a log and a graph | **exists** — a released bundle, or the demo fixture that ships **in the jar** (M36) |
| Run the server to generate data | **exists** — the bundle's `run-server.sh` (background it — R1-B) |
| Export the audit log | **exists** — `export-audit.sh` |
| Open the analyser on it | **exists** — `--rest` (M19.7), then `open` |
| Skills | **exists** — the canonical `m19-skills/1` index (M19.10) |
| Register MCP with the client | **M42, but GUI** — see below |

**No Fluxtion API key is needed anywhere on this path.** A bundle ships its generated processor, so
building and running need no key; only *regenerating* does, and the tutorial never changes the graph. The
demo can therefore run for someone who has no account at all — which is the point of a first-run
experience.

**The one real gap: MCP registration is an in-app flow.** M42 registers Claude Code / Codex / Claude
Desktop from the UI. A prompt-driven install would want to do that without a human walking a dialog. Two
honest options — a headless registration path, or the prompt simply *instructs the human* to use
*AI ▸ Connect an AI client…* and waits. **The second needs no new code and should be the first version**;
build the first only if the pause turns out to be where people drop out.

*Not yet checked: whether the analyser can fetch a starter template headlessly. There is no template verb
on the socket, so today that is a GUI action (M19.5) — but the prompt does not need it, because the client
can download a released bundle itself. Confirm before designing anything around it.*

## D-G4 — the install prompt is a supply-chain surface, and must behave like one

A prompt that says *"run this, download that, register this"* is an instruction set a stranger executes.
The standing decisions already cover the shape:

- **The analyser never fetches or executes it.** It is a document a person opens or a client is pointed at
  — consistent with D-C2 (pointers are never executed, never served as contents).
- **Pinned origin**, versioned, and the same overridability discipline as the skills source (D-R4).
- **Every command it issues is visible in the prompt text.** No step that says "run the script at $URL".

## D-G5 — this is the loop's ideal held-out task

[spec-authoring-experience.md](spec-authoring-experience.md) needs a held-out task and has never run one.
*"Install from nothing and show me three capabilities"* is the best candidate available: it is the real
user journey end to end, it exercises the analyser (which no round has), and its outcome is **objective** —
did the UI finish in the state the tutorial claims? `context` and `screenshot` make that machine-checkable
rather than a matter of the agent's report.

It is also the honest test of this spec: if a fresh model cannot follow the install prompt to a working UI,
the prompt is wrong, and that is exactly what the loop is for.

## Sketch — the tutorial's three beats

Each beat: drive the view, then let the user read it.

1. **What ran** — `open` the demo log and graph, `topology` to the first record, step a cycle. *"That
   ordering is derived by the compiler, not observed."*
2. **What never ran** — `coverage`. The proof-of-absence, which needs the graph **and** the log and is
   available from neither alone. This is the moment the product is unlike a log viewer.
3. **A question answered** — `series` or `aggregate` with a threshold, `goto` the crossing, `flag` it.
   The user ends with a bookmarked record they can re-open.

Then stop. Three capabilities shown on screen beats ten narrated.

## Acceptance

- [ ] The tutor introduces **no new verb**; if one is proposed, that is a signal the design drifted.
- [ ] The tutor never states a figure not visible in the user's current view, and calls `context` before
      claiming a view shows something (D-G2).
- [ ] The whole path runs **without a Fluxtion API key** and without an account.
- [ ] Every command in the install prompt is written out in the prompt; no fetch-and-run step (D-G4).
- [ ] The demo data is identifiable as demo data on screen.
- [ ] Version 1 asks the human to complete MCP registration in the UI rather than adding a headless path.
- [ ] The prompt is run end to end by a fresh-context model as the held-out task, and the finishing UI
      state is checked with `context`, not taken from the agent's report (D-G5).

## Open for the owner

- Does the install prompt join the **agreed reference set** (D-AX1c)? It is arguably the first thing a
  newcomer meets, which would make it entry zero rather than an extra.
- Is the tutor a **prompt**, or a shipped **skill** in the canonical index? A skill is discoverable from
  the analyser's *Find skills…* (M43.7) and versioned with the others; a prompt is easier to paste into a
  cold client that has nothing yet. They are not exclusive — the prompt can install the skill.
