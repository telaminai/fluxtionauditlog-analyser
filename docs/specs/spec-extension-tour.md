# Spec — the extension tour: extend a running application with a jar you already built, and watch it prove itself

**Status:** PROPOSED 2026-09-19 (owner-directed); **D-X7–D-X10 decided by the owner the same day** (below) — the tour
publishes with the declaration lit. M66 is now merged on main, unreleased; the tour remains queued behind
the Spring-authoring release. **Milestone:** M67. **Tracker:** [tracker.md](tracker.md) ▸ M67.
**Builds on:** [`spec-guided-start.md`](spec-guided-start.md) (the tour form: point, then speak; the held-out
harness), [`spec-spotlight.md`](spec-spotlight.md) (M64, the vocabulary), [`spec-template-from-analyser.md`](spec-template-from-analyser.md)
(a live, versioned catalogue read by the analyser — the pattern this spec copies for jars),
[M66 design render](completed/spec-design-render.md) (the beat that lights the declaration), [`spec-component-catalogue.md`](spec-component-catalogue.md) (M48.13 — a
jar's self-description, an **optimisation, not a requirement** of this tour), the public Spring-authoring documents
(`contract.md`, `skill.md`, the project `RUNBOOK.md`) and the compiler diagnostics contract.

Owner, 2026-09-19: *"as soon as developers realise they can extend an application with jars they have built before, use
LLM generated spring XML and validate with audit logs, we have given the developer an easy to use way to extend
application functionality … it is like FP but for whole application construction. Now we have spotlight working we
can literally step a developer through an interactive demo. We would need a basic catalogue of 'vendor' jars that is
like the playground libs."*

## The claim the tour demonstrates

An application is a composition of jars. A developer extends it by declaring a new bean in Spring XML — written by
the LLM from intent — that names a class in a jar built independently of this graph, possibly by someone else,
possibly never opened. The **compiler is the boundary**: it verifies the declaration against the real class and
refuses what does not fit. The **audit log is the proof**: the first record where the new node fired is evidence the
developer reads off the screen, not a claim anyone makes. The LLM's working set is the XML, the intent, the
diagnostics and the log — never the vendor's source, never the generated Java.

The tour is that claim, performed on the developer's own screen by the assistant they already use, with the analyser
pointing at each piece of evidence as it appears (M64). It is not a video and not a slide: every number is on the
screen before anyone says it (guided-start D-G2).

## D-X1 · The "vendor" catalogue — a public, versioned list of jars, like the playground libs

- **Shape.** A public git repository in Maven layout plus one manifest, on the pattern of
  `fluxtion-playground-libs` + `web/scripts/lib-manifest.json` (`repoBase`, `jars[]` with `src`, `dest`, `size`) and
  the starter-template catalogue (`catalogue: 1`, `name`, `description`). Proposed name `fluxtion-vendor-jars`;
  proposed manifest `catalogue.json`:

  ```json
  { "catalogue": 1,
    "repoBase": "https://raw.githubusercontent.com/telaminai/fluxtion-vendor-jars/main/libs",
    "jars": [ {
      "id": "quote-limit-check",
      "name": "Quote limit check",
      "description": "Refuses a quote whose size breaches a per-symbol limit; publishes a LimitBreach.",
      "coordinate": "com.acme.vendor:quote-limit-check:1.0.0",
      "src": "com/acme/vendor/quote-limit-check/1.0.0/quote-limit-check-1.0.0.jar", "size": 12345,
      "provides": { "nodes": ["com.acme.vendor.limit.LimitCheck"],
                    "events": ["com.acme.vendor.limit.LimitBreach"],
                    "services": [] },
      "consumes": { "events": ["com.acme.demo.event.Quote"] },
      "licence": "Apache-2.0",
      "source": "https://github.com/telaminai/fluxtion-vendor-jars/tree/main/src/quote-limit-check"
    } ] }
  ```

- **"Vendor" is a role, not an origin.** The first entries are jars we build, in placeholder packages
  (`com.acme.vendor…`, rule 1), so that the tour never depends on a third party and the sweep can read every name.
  A real vendor's jar joins the catalogue the same way; nothing in the shape is ours.
- **`provides`/`consumes` are the human-and-LLM index**, hand-written for the first entries. When the jar carries a
  compiler-generated `Fluxtion-*` manifest (M48.13) the catalogue entry may be derived from it; until then it is
  written by the jar's author. The tour must work either way — the descriptor lowers the LLM's cost, the compiler
  decides correctness (owner, 2026-09-19).
- **Who reads it.** The LLM (to choose and declare), the project's `pom.xml` (the coordinate, from a public
  repository the template already knows), and a person. **The analyser reads nothing new in M67.1–.4**: no verb,
  no panel. A *Project ▸ available jars* row is a later slice if the tour shows people looking for it.
- **Versioned like the template catalogue:** `catalogue` is an integer bumped only for an incompatible shape; a
  consumer refuses an integer it does not know rather than guessing. Entries are immutable per coordinate.
- **First three entries** (owner to confirm): a **limit check** node (consumes the demo's `Quote`, publishes a breach
  event — the tour's extension), a **notifier sink** (an exported service/sink target, so the tour can show a
  service edge), and a **feed adapter** (an event producer, for the day-two "add an input" story). Three is enough
  to make "catalogue" true and "which one?" a real question for the LLM.

## D-X2 · The tour runs in a downloaded project, not the in-jar demo

- The analyser's in-jar demo set (`src/main/resources/demo/`) is offline and build-free by design; the tour needs
  a build, because the compiler's refusal and the regenerated processor are two of its beats. So the tour's ground
  is a **template project from the playground catalogue** — the Spring AOT template, or a dedicated
  *"Extend an application"* onboarding entry (owner's call, D-X8) — obtained through *File ▸ New project from
  template…* (M19.5), which already makes its profile the active project.
- The project ships **running**: one small graph, an audit log written on first run, the vendor catalogue's public
  repository in its `pom.xml`. The tour's edit is one declaration.
- A **look-only** variant of beats 1, 2 and 5 can run against the in-jar demo with no build and no key, as the
  guided-start does; it is the fallback when the person has no project yet, and it is not the tour.

## D-X3 · The six beats

Each beat is the guided-start's three moves: spotlight, one sentence, the screenshot that proves it. Targets are
M64's vocabulary; nothing new is needed for beats 1, 2, 5, 6.

| # | Beat | What is lit | What the person sees |
|---|---|---|---|
| 1 | **The application** | `graph` / `topology`, then `records:row:<first>` | the graph that runs today, and a record it produced |
| 2 | **The jar you already built** | `project:processors`, then the catalogue entry's `provides.nodes` FQCN named in the caption | "nothing in this jar was written for this graph" |
| 3 | **The declaration** | until M66: the design file opened in the Source tab; **with M66:** `source:design:bean:<id>` | the one bean the LLM added, and its edges |
| 4 | **The refusal** | the LLM first declares it **wrong** (a reference mode the class does not carry, or a missing edge) and runs `validate.sh` / `generate.sh`; the diagnostic is read into the Assistant tab; `topology:node:<id>` or the design line is lit when the diagnostic names it | the compiler saying no, with the reason, before anything ran |
| 5 | **The proof** | after the corrected `generate.sh` and a run: `records:row:<n>` of the first record where the new node fired, then `topology:node:<id>`, then `detail:node:<id>` | the new node's own audit line, read off the screen |
| 6 | **Hand-off** | `flag` on that record; `toolbar:flag` lit | a place to come back to, and the tour's end |

- **Beat 3 before M66** opens the file and says so ("the analyser cannot light a declaration yet; here is the file").
  A tour that pretends is worth less than one that names a limitation (guided-start's rule).
- **Beat 4 is the beat that earns trust, and it depends on the refusal's quality.** Today the compiler's B0 check
  stops at the first unmet declaration and its element names no bean (Spring-branch review G7). Until that is fixed
  the beat lights the node by the id the LLM extracts from the message text; when the element carries the bean, the
  beat lights it from the diagnostic. The spec records this so the beat's evidence is never overstated.
- **The LLM does the extension; the person watches.** The XML edit, the scripts and the run are the LLM's, through
  its own shell and the project's `RUNBOOK.md` (Spec 2's local loop). The analyser runs nothing (standing decision:
  server verbs never on the analyser's socket; the analyser edits no code).

## D-X4 · No new verbs, one new skill

- Guided-start D-G1 holds: the tour needs no verb the socket does not have. It needs a **skill**,
  `extend-with-a-jar`, in the **spring** tier of the pinned skills (`docs/skills/spring/`; the index moves once,
  additively, and is re-vendored into the playground as M64.8 was), plus one line in `guided-start`'s "what next"
  pointing at it.
- The skill is written as the guided-start is: point before you speak; say so when a beat cannot be lit; read the
  catalogue before choosing; declare wrong on purpose once, then right; never state a figure the screen does not
  show.

## D-X5 · Evidence, and what "done" means

- **A verify script**, `tools/verify-m67-extension-tour.py`, on the built jar, in a downloaded project under an
  isolated home: beat by beat, each target lights (or refuses with the reason), the refusal beat produces the expected
  diagnostic code, the proof beat's record exists and carries the new node's audit line. Reuses the M46/M64 harness.
- **A held-out record**, like [`heldout_m64_2026-09-17.md`](../handoff/completed/heldout_m64_2026-09-17.md): a
  context-free client (`tools/heldout-client.py`, local only, the owner's key, never CI) given the skill and a fresh
  project, outcome per beat in the harness's own words, unreplicated observations labelled as such.
- **A docs page** written by `tools/capture-conversations.py` from a real run under the isolated home (rule 1: a
  hand-typed transcript is a screenshot the sweep cannot read). Every visible string is a placeholder.
- **Not evidence:** a screencast, a hand-written transcript, or the author's own run without the harness.

## D-X6 · What is deliberately out

- No analyser surface for the catalogue in this milestone (D-X1). No new verb (D-X4). No YAML design. No
  jar-fetching by the analyser: the template's build fetches from the public repository, as every template does.
- Not a measurement of authoring cost: that is M48.9/.16. The tour demonstrates; the harness records outcomes, not
  tokens.

## Acceptance

1. The catalogue resolves publicly; each entry's `coordinate` downloads from the repository the template names;
   `size` matches; a `catalogue` integer this consumer does not know is refused with the URL.
2. A fresh template project builds and runs with no edit, writes an audit log, and its profile is the active project
   after *New project from template…*.
3. The skill, given to a context-free client with the project open, completes beats 1, 2, 5 and 6 with the right
   thing lit before each sentence (verify script and held-out record agree); beat 3 opens the file (until M66) or
   lights the bean (with M66); beat 4 produces the expected diagnostic and lights the node it names.
4. The wrong declaration never reaches a run: the refusal is at `validate.sh` or `generate.sh`, and the receipt says
   which.
5. The proof record's audit line belongs to the vendor node's class, and the developer can reach that class's
   source from the record (`goto` → source) only if its source is under a root — otherwise the tour says the jar
   is opaque and that is the point.
6. Rule 1: every string on every captured image and page is a placeholder; the sweep passes; the vendor packages
   are `com.acme.vendor…`.
7. The tour's docs page is generated, not typed; the held-out record names its limitations.

## Slices

- **M67.1** the catalogue repository, its manifest and the first three jars (cross-repo; source under `src/` in
  the same repository so the placeholder rule can be read).
- **M67.2** the template project the tour runs in (playground; onboarding subset entry or the Spring AOT template
  extended — D-X8).
- **M67.3** the `extend-with-a-jar` skill, the index move, the verify script (analyser).
- **M67.4** the held-out record and the generated docs page (analyser, owner's key, local).
- **M67.5** beat 3 lights the declaration (after M66) — **the tour publishes with this slice, not before (D-X10)**.
- **M67.6** beat 4 lights the bean from the diagnostic's element (after the compiler's B0 refusal names it).

## Decided by the owner, 2026-09-19

- **D-X7 · Where the catalogue lives:** a new `fluxtion-vendor-jars` repository beside `fluxtion-playground-libs`
  — same fetch pattern, same public raw URL.
- **D-X8 · The tour's project:** a dedicated *"Extend an application"* onboarding template entry, which pre-declares
  the catalogue's repository and ships its first run's log. _(Recorded from "I agree with 1–3"; the alternative — the
  Spring AOT template plus a first run — stays available if this reading was wrong.)_
- **D-X9 · The first three jars:** a limit check (the tour's extension), a notifier sink, a feed adapter — and one
  of them a jar the owner actually built before, in a placeholder package, so "you already have jars like this" is
  literally true on stage. **2026-09-23:** owner selected the feed adapter but has no existing
  artifact to hand. Building a new adapter is authorised; it must be labelled new. Reusing an
  owner-built artifact remains a tracked D-X9 follow-up, not fulfilled by the new example.
- **D-X10 · Timing:** the tour **waits for M66**. Beat 3 lights the declaration on first publication; the honest
  "here is the file" form is for rehearsal only. M67.3/.4 may be built and rehearsed against the branch before M66
  merges, but the skill is not pinned and the page is not published until beat 3 is lit.

## Proposed, not yet decided

- **D-X11 · The wizard: a spotlight that waits for the person.** Owner's idea, 2026-09-19: *"make spotlight
  interactive, like a wizard, next/previous buttons that could drive the LLM."* MCP lets the LLM call the analyser
  and never the reverse, so the buttons cannot push a turn into the LLM; what they can do is **return the call the
  LLM is already waiting on**. Proposal: `spotlight` gains `wait: true` (a parameter — M64's fifteenth-verb rule
  holds). With it the callout carries **Next · Stop** — **no Back** (owner, 2026-09-19): Back would rely on the LLM
  re-lighting a previous beat from its own memory, the one control whose truth the screen could not prove — and the
  verb blocks until one is pressed, bounded (60 s) so client tool timeouts never bite; the echo carries
  `{advance: "next" | "stop" | "dismissed" | "timeout"}`. The skill's loop is then on the LLM's side only: light, say
  one sentence, wait, go on or end. Escape and any click still dismiss, and now report it. The analyser learns
  nothing about tours; D-SP3's dumb overlay stays dumb; the person chooses, the analyser does not.
  **The unknown is behavioural, not mechanical:** whether a context-free client obeys "the wait is the only way
  forward" instead of narrating three beats at once. Mechanism: about a day on `SpotlightOverlay` and one schema
  line; the answer: one held-out run. If adopted it is M64.14, and the extension tour is its first user.
