# Reviewer orientation

_For a session arriving to **judge** a body of work rather than to change code. If you are about to
change code, read [`docs/ONBOARDING.md`](../ONBOARDING.md) instead — it has the architecture, the
conventions, the standing decisions and the process, and this document does not repeat them._

**Deliberately thin on rationale.** You are worth having because you do not yet share our assumptions.
This gives you the facts, the vocabulary and the map; where a decision is argued, it points at the
argument rather than summarising it, so you form your own view of whether it is right. If something here
reads as persuasion, treat that as a defect in this document.

---

## 1. The four layers, and which repository owns each

| Layer | What it is | Repository | Changed by us? |
|---|---|---|---|
| **Fluxtion** | AOT compiler + runtime. You declare a node's own inputs; the compiler derives the total dispatch order and generates the Java that runs it. | <https://github.com/telaminai/fluxtion> | **No.** Used as shipped; shortfalls are filed as upstream asks. |
| **Mongoose** | The deployment layer that hosts a compiled processor. | <https://github.com/telaminai/mongoose> · docs <https://telaminai.github.io/mongoose/> | **No.** Dual-licensed (GPL + commercial). |
| **Mongoose plugins** | Services a deployment composes — admin web console, event sources, sinks. Where "deploy / start / stop / read logs" actually live. | <https://github.com/telaminai/mongoose-plugins> · docs <https://telaminai.github.io/mongoose-plugins/> | **No.** |
| **The audit log** | What the runtime wrote, per event cycle: which nodes ran, in order, and what each logged. | a file format — [spec'd here](https://telaminai.github.io/fluxtionauditlog-analyser/format-spec/), with a conformance suite | Spec'd and enforced here. |
| **The analyser** | This repo. Reads the log and the graph; answers questions about a run after the fact. Also an MCP server. | <https://github.com/telaminai/fluxtionauditlog-analyser> · docs <https://telaminai.github.io/fluxtionauditlog-analyser/> | **Yes — this is the one under review.** |

### Getting up to speed on Fluxtion itself

Read these before judging anything that models Fluxtion's execution semantics — inferring them instead of
reading them is where every defect in the M21 topology work came from:

- **<https://fluxtion-playground.dev/build-with-ai>** — the authoring context: how to design with Fluxtion
  using an LLM, and the compile → build → run → review-the-audit-log → change → recompile loop that the
  rest of this stack exists to serve. Start here.
- **<https://raw.githubusercontent.com/telaminai/fluxtion/main/docs/claude.txt>** — the framework canon.
- **<https://fluxtion-playground.dev/fluxtion-golden-path.md>** — the golden path.
- **<https://fluxtion-playground.dev/starter-templates/index.json>** — the template catalogue a generated
  project comes from.

Two facts that surprise people:

- **Generation is a hosted service.** The Maven plugin calls it at `process-classes` and needs a
  subscribed key. Generated processors are committed, so *reading and running* a project needs no key;
  *regenerating* does.
- **The analyser writes no code and mutates no server.** It reads logs, draws graphs, answers verbs. That
  is a standing decision, not an accident — see ONBOARDING's decisions table.

## 2. Vocabulary you will hit in the first hour

| Term | Means |
|---|---|
| **cycle / record** | One event dispatched through the graph, and the log record describing it |
| **nodeLogs** | What each node wrote during that cycle, in dispatch order |
| **topology / GraphML** | The declared graph the compiler emitted |
| **coverage** | Declared nodes minus nodes that logged — "which nodes never ran" |
| **provenance** | Which SYSTEM a log came from, declared by whoever opened it |
| **the profile** | `.analyser/project.fluxtion-settings` — portable context: source roots, processors, runbook pointers, environments |
| **runbook / skill** | A pointer to a markdown file in the repo. Never executed, never served as contents |
| **verb** | One of the 14 actions on the socket (`read`, `coverage`, `topology`, `open`, `context`, …) |
| **the socket** | localhost REST + MCP, how an agent drives the app |
| **PARTIAL ordering** | A source that cannot supply a dispatch order; position is arrival, and ordinal badges are suppressed |

## 3. Plugins and adapters — small in the code, large in the strategy

- **`spi.AuditLogReader`** — a third party can teach the analyser to read a foreign log. Its
  `Capabilities` record declares `follow`, `byteAnchors`, `randomAccess` and **`ordering`
  (`TOTAL`/`PARTIAL`)**; the core degrades loudly rather than assuming.
- **`GraphSource`** — where a graph came from, and what it can therefore support. `supportsCoverage()`
  is why coverage refuses an inferred graph.
- **Why it matters beyond the code:** the reader SPI is the only route by which someone meets the
  analyser *without* first adopting Fluxtion. See `spec-trust-structure.md` D-T5.

**Known gap worth knowing before you review adapter work:** a translated concurrent source (e.g.
LangGraph) currently gets dispatch badges it has not earned when it arrives as a *file*, because the file
format has no way to declare `PARTIAL` — the reader SPI does, and a file-writing translator never gets
the chance to use it.

## 4. Where things are

```
docs/ONBOARDING.md          architecture, conventions, standing decisions, gate blind spots, process
CLAUDE.md                   hard rules. Rule 1 (public repo / the sweep) is not optional
docs/specs/tracker.md       what is in flight; completed/ holds shipped milestones
docs/specs/*.md             one spec per milestone; decisions are numbered (D-A2, D-L1, D-C2, D-AI5…)
docs/handoff/               briefs, reports, reviews — the cycle
docs/handoff/unreviewed-changes.md   the ledger: changes on main that have NOT had a review
docs/site/                  the published documentation (mkdocs)
tools/bench/loop-bench.py   the cross-repo loop, as a PASS/FAIL test
tools/bean-resolver.py      resolves a Fluxtion bean file from jar manifests (M48); no model involved
tools/fluxtion-harness.py   derives the authoring mode from the catalogue; --json is the handoff record
tools/verify-m43.py         drives the real app over the socket; --eyeball for the checks a script cannot make
```

## 5. How review works here

The cycle is brief → report → review, all in `docs/handoff/`. Small ad-hoc changes may go straight to
`main` **provided** they get a ledger entry in `unreviewed-changes.md` carrying: commit SHA, what and why,
files, what was verified, and **what the reviewer must still check**. Your first act should be to read
that file and look for `☐`.

Things this project expects of a review, learned from ones that went wrong:

- **Check claims against the source, not against the report.** Two reviews of the same work converged
  last week precisely because each read `loop-bench.py` and the code rather than each other's summaries.
- **Say what you did NOT check.** "I could not verify this" is a finding. Silence reads as verified.
- **A test that matches nothing passes for the wrong reason.** Several tests here deliberately fail if
  their own regexes stop matching.
- **Swing is not unit-tested** (rule 4, headless CI). UI claims rest on model tests, source-text checks
  and someone actually clicking. If a UI behaviour matters, say so rather than assuming coverage.

## 6. What is live right now — 2026-09-03

**Released:** 1.12.0. **On main, unreleased:** the M19 revision, `spec-trust-structure.md`, the Mongoose
review resolution, `SpecLinksResolveTest`.

Recently landed and **already reviewed** (do not re-review unless you disagree): M43 the AI menu +
runbook descriptions + skill discovery; M40 audit readiness and coverage scope; M42 connect-an-AI-client;
M33.7 report table sources.

**Landed 2026-09-03, wholly unreviewed — `f645cce^..3c39c6c`, 32 commits, ledger entry filed.** Two
independent bodies of work; review them separately:

- **M48 · authoring modes** (`spec-authoring-modes.md`, `spec-authoring-mode-selector.md`,
  `spec-authoring-session-walkthrough.md`). The claim to test: **the bean-file half of component
  integration is a constraint solve, not a model task.** `tools/bean-resolver.py` reproduces the
  measured-optimal selection and wiring from manifests alone, at zero token cost, with byte-identical
  alerts. If that holds, every "authoring cost" figure this project has published was measuring a model
  doing a resolver's job. **The strategy argument rests on it, and it has had no independent read.**
- **M49 · runtime performance** (`docs/experience/runs/round-54/BLOG-NUMBERS.md`). The first runtime
  measurement here — 8.44 ns/event, zero allocation, 500M events under a no-op collector. **No analyser
  code changed.** Numbers are reproducible from the directory.
- **`analyser.score.ExpectationScorer`** — the only `src/` change in the range. Worth scepticism
  precisely because it exists to correct **five scoring defects in this project, three in one session,
  every one of which erred toward agreeing with its author**. Its figure-extraction rule is heuristic and
  asserted rather than derived from the format spec; check it.
- **`docs/proposals/assessment-playground-ai-prompts.md`** — a reading of
  <https://fluxtion-playground.dev/build-with-ai> (which §1 above tells you to read) against prior
  measurements. It is a *reading*, not a measurement, and says so. One finding worth your own judgement:
  the Spring contract's wiring rule is correct for scaffolded nodes and **harmful** for bought-in
  components, and nothing tells a reader which they are in.

**Open and worth your attention:**

- `spec-trust-structure.md` — **new, unreviewed.** A framing spec that constrains code: it argues the
  analyser's refusals are load-bearing (D-T4). If you think that framing is wrong, say so — it is the
  most consequential document written this week and it has had no independent read.
- `spec-onboarding-example.md` ▸ *Revision 2026-08-29* — **new, unreviewed.** Five additions, one
  correction, five recorded concerns, and a cold test whose results are in the document.
- The Mongoose bootstrap artefacts (`docs/specs/mongoose-bootstrap-artefacts/`) — reviewed twice
  independently; **F1 remains open** (the snapshot records no source revision, so parity with the real
  starter is unverifiable by anyone, including its author).
- `docs/specs/spec-baselines.md` (M39) — spec'd, four open owner questions, not started.

## 7. Two habits of the previous sessions to be sceptical of

Stated so you can check rather than inherit:

- **Stale factual claims.** The outgoing session asserted current product behaviour from memory and was
  wrong three times in one day — the first-run dialog, the install path, and the dialog again. Each was
  checkable in under a minute. Treat *architectural* reasoning normally and **verify any statement about
  how the product behaves today**; the documents are written so you can.
- **Long commit messages and long specs.** Volume is not rigour. If a document argues at length for
  something that could be checked in one command, the check is the better contribution.
