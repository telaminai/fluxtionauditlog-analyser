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
| **verb** | One of the 16 actions on the socket (`read`, `coverage`, `topology`, `open`, `source`, `spotlight`, …) |
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
tools/bean-resolver.py      reviewed prototype for M48; production target is the existing builder jar
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

## 6. Current review — the integration of 2026-10-18

The [live tracker](../specs/tracker.md) owns status and delivery order; the changelog separates released work from
**Unreleased**. **The latest release is 1.32.0 (2026-10-06).** Nothing below is released. This section replaces the
2026-09-24 map (M68.1), which had gone stale; check this date against the tracker before trusting it.

**What you are reviewing:** one integration branch plus the PRs it gathers, together. Every branch, tip, base, claim
and piece of evidence is in the [review map](review_map_integration_2026_10_18.md); this section tells you how to
read it.

### 6.1 The stack's shape

- **Base:** `feature/claude-code-p3` at `a875fcba` — M74 (Claude Code as the assistant) in layer PRs #145, #146, #147,
  #150 (P2), #151 (P3) and #152 (the docs restructure, `b1c22704`, reconciled at integration), under the umbrella
  draft #156. P3 already contains two merged fixes: first-run polish and the Facts-column floors.
- **Fix branches** (no PR; straight into integration): `fix/no-log-offer-cancel`, `fix/records-filter-visibility`,
  `fix/event-types-default-collapsed` (on the records fix), `fix/authoring-walk-defects` (on M77-2).
- **Feature PRs, stacked:** #157 M77-1 → #159 M77-2 → {#161 M77-5, #162 M77-3, #163 M78 D1}; M77-4 and M78 D2 follow.
  #160, the usability scorecard, sits on P3 beside them.
- **Spec PRs (docs only):** `docs/m77-ai-setup-wizard` (no PR) → #158 M78 and #164 M79; #154 M76, #153 M75 and
  #155 the virgin-LLM pack. Each spec carries its owner decisions (OD-77, OD-Q, OD-79, OD-76) — judge the code against
  the spec, and the spec against itself.

### 6.2 Review by seams, not by PR

Each PR was quick-gated on its own; the full gates run once, on integration. The defects that survive that are at the
seams, where two branches each did the right thing alone:

- **The generated session processor.** FIX-1, FIX-2, M77-1, M77-2 and M77-3 each regenerated
  `session/generated/SessionProcessor.java`. On integration it must be **regenerated** (`mvn -Pregen process-classes`)
  from the merged nodes, never hand-merged. Diff the merged node set against the regenerated dispatch.
- **Shared surfaces:** `MainFrame`, `AssistantPanel`, `StartPanel`, `SessionSnapshot`, `SessionEffects`,
  `SessionEvents`, `FakeSessionAdapter`, the CI frame-suite list, and `CHANGELOG.md` (every branch adds lines; a clean
  merge can file them under a released section — read it after merging).
- **Words and layout at 1200×800 / 1200×680:** the quiet panel (M78 D1), the wizard (M77-2/-3/-5) and the records
  fixes all change the docked column. The word budgets and T19 must hold on the merged build, not only per branch.

### 6.3 Rule 9 and the dispatch-order checks

State and the decision of *when* belong to a session node; adapters perform `SessionEffects` and report facts; surfaces
render `SessionSnapshot` (CLAUDE.md rule 9). Check, on the merged build: `OneDispatchModelTest` green with its
per-branch additions intact; that each new node (`aiSetup`, `recordsView`, `startPageNoLogHint`, the quiet panel's
state) reads its parents **after** they handled the fact in the generated order — the M77 spec §8.1 names the
`AssistantProfileApplied` and `RestSwitchObserved` orders M77-1 checked; and that no listener posts a second fact or
composes a status line where an event happens. Rule 6: read the generated dispatcher; do not infer it.

### 6.4 The customer API invariants

External clients (the customer's developers and their agents) read the MCP/REST schemas, tool lists, server
instructions, manifests and `context`. `WorkModeKeepsTheApiTest` pins those surfaces by SHA-256; its digests must not
move on integration. It does **not** cover `context`'s view fields or HTTP headers, so check by reading: `hidden` is
additive (FIX-2, `ContextSections`), and `X-Analyser-Client` / `X-Analyser-Probe` are additive (M77-3, `McpBridge`,
`ActionServer`). Anything that renames, removes or re-types an existing field is a blocker.

### 6.5 The safety surfaces

The ones this stack touches: the Full consent and folder trust (`FullFolderTrust`, the `STAY_IN_PROJECT_NOTE` — a
request to the model, not a sandbox); the permission dialog and single-use server-side grants (`HostedGrantServerTest`);
the narrow pre-allow of an in-project `open` in Full (a real-path check, with a time-of-check residual); downloaded
examples' executable bits and archive containment; the one owner of external access and its confirmations at every
door (M77-3); the auth-probe parse that must drop email and organisation (M77-1); the FAQ's claims
(`FaqSecurityContractTest`, `DocsExternalAccessClaimsTest`). Each change of behaviour here is one of the map's flags.

### 6.6 Hygiene, and where the evidence lives

- **The public-repo sweep** (CLAUDE.md rule 1) on the merged tree, plus reading every new screenshot — the sweep cannot
  see inside images. User-visible text is plain: no milestone ids, no internal jargon (`UserVisibleJargonTest`).
- **Evidence:** each PR's description and tests; the scorecard in `docs/usability/` (#160); M74's probe and smoke
  reports in `docs/specs/evidence/m74-*`; the dispositions of earlier reviews in `docs/handoff/review_m74_*`,
  `review_m76_r1_k4m9.md` and `review_m77_r1_7f3c.md`. The map lists them per branch.

### 6.7 The fix policy for this review

**Reviewers fix, unless the fix needs a redesign** — then the finding goes to the owner with the failure scenario and
a proposed design. Fix on a review branch, one commit per finding, and **give the owner the commit range of every fix**:
authorship cannot identify a reviewer's commit, so the owner reviews the delta from the range. Do not merge, release or
force-push.

## 7. Two habits of the previous sessions to be sceptical of

Stated so you can check rather than inherit:

- **Stale factual claims.** The outgoing session asserted current product behaviour from memory and was
  wrong three times in one day — the first-run dialog, the install path, and the dialog again. Each was
  checkable in under a minute. Treat *architectural* reasoning normally and **verify any statement about
  how the product behaves today**; the documents are written so you can.
- **Long commit messages and long specs.** Volume is not rigour. If a document argues at length for
  something that could be checked in one command, the check is the better contribution.
