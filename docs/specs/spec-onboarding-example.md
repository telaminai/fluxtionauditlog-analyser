# Onboarding Example — Playground Download → Running Mongoose → Analyser (Design Spec)

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-25 (aligned with the agent-brokered dev loop; M18 closed)

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

## The experience (target: <10 minutes)

**Prerequisites, corrected 2026-08-29 (review F4).** This section was headed *"nothing pre-installed but
a JDK"*, and C3 identified that as understated without repairing it. Repaired here, because a promise the
reader discovers is false at step 6 costs more than one they were told:

| Path | Needs |
|---|---|
| **Run and analyse** | a JDK **and JBang** (review F7). The one-line install *invokes* `jbang`; it does not install it. Either declare it as a prerequisite with its install line, or give the plain `java -jar <released jar>` alternative for someone who has only a JDK. Saying "a JDK is all you need" and then printing a `jbang` command is the same shape of error as the key: true for the author, false for the reader. |
| **The AI half** | a JDK **plus an MCP-capable client** (Claude Code, Codex or another), configured and subscribed |
| **Change the graph** | the above, plus a Fluxtion API key (R4) |

Say which path a step belongs to rather than implying one prerequisite covers all three.

1. **Get an example** — open the playground, pick an example flow, **Download**. You get a runnable
   Mongoose example project.
2. **Run it** — one command from the bundle's README (`mvn -q exec` / `java -jar …` — see O1). The
   server starts keylessly with Chronicle audit capture enabled.
3. **Export the captured run** — one bundle-owned command exports analyser-readable YAML to the
   generated concrete path (`./logs/audit-<name>.yaml`). This is the v2 export beat: Mongoose has no
   text-file capture backend today; UP-RDR-01 later removes this step by letting the analyser read
   Chronicle directly.
4. **Open the analyser** — `jbang analyser@telaminai/fluxtionauditlog-analyser`.
5. **Load the bundle's project** — the bundle ships a **project profile** at
   `.analyser/project.fluxtion-settings` (M20's canonical path): source roots (relative, they ship in
   the zip), the event processor FQN — **zero manual setup**. Once M20 lands the analyser auto-detects
   it beside the log; until then it's File ▸ Import settings… on that file (M15's import doing
   onboarding duty).
6. **Inspect the export** — open `./logs/audit-<name>.yaml`; click a node line → the bundled source
   opens; graph a value; **Explain** a cycle (copy-prompt works without a key). Follow becomes the
   default route when UP-RDR-01 removes the export beat; v2 does not call a static export live.

7. **Edit it — in your IDE, with your own LLM** — open the Maven project in IntelliJ/VS Code; the
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
| **audit logging pre-enabled** — Mongoose `performanceMonitoring.auditCapture`, Chronicle backend, plus one bundle-owned YAML export command targeting `./logs/audit-<name>.yaml` | verified live-source constraint: Mongoose has no text-file capture backend; `/api/audit/file/{id}/export?format=yaml` is the analyser-readable v2 route. UP-RDR-01 later deletes the export beat |
| the **generated EventProcessor source** + the example's node sources | source navigation works out of the box |
| **`.analyser/project.fluxtion-settings`** — relative source roots + EP FQN, at **M20's canonical project-profile path** so the bundle *is* a project profile (not a separately-named file the detector also accepts) | zero-setup: M20 auto-detects it; M15 import until then |
| **`README.md`** — run command, export command, concrete YAML path, and "open this with the analyser" linking the tutorial page | the bundle itself funnels to the analyser without pretending run and export are one operation |
| **admin REST enabled**, and the server **publishes its registry file** `~/.mongoose/servers/<name>` (upstream-asks **UP-MNG-01**; the M18 admin link is closed in favour of the agent-brokered loop) | the example doubles as the **loop's conformance bench** (below): an agent globs the registry, exports, and drives the analyser — `tools/bench/` runs exactly that, today against a stub |
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
   connected agents can refresh and humans can browse. Include
   <https://fluxtion-playground.dev/build-with-ai>, which is the entry point a human lands on.

**Snapshot the Spring-authoring interface too, for the XML-defined example (O2).** Beyond `claude.txt`
and the golden path there is a *design-conversation* interface — `spring-authoring/skill.md` (how to run
the describe → events → nodes → wiring → emit conversation), `contract.md` (the exact
`FluxtionSpringConfig` shape, `eventTypes` as FQCN strings, nodes in `nodeBeans`, dependencies as
constructor-arg `ref`) and `example.md` (a worked run). Those three are what make **tutorial part 4's
design-level edit** possible at all: without the contract an agent cannot emit XML the starter accepts.
Design work lives in `fluxtion-compiler/design/spring-authoring`.

> **The build is generated output.** The starter emits the pom; an author writes nodes and (for the XML
> shape) the Spring file. Any bundle guidance that encourages hand-authoring a pom is steering the user
> off the supported path — the bundle ships a generated project precisely so nobody has to.

Contract notes:
- Paths in the settings file are **bundle-relative** — and this is a **committed analyser precondition,
  not a "verify"**: `SettingsShare` as shipped expands only `~`-prefixed paths ("anything else is
  verbatim" — a bare `src/main/java` would resolve against the CWD and break unless the analyser is
  launched from the bundle dir). Fix: relative roots resolve against the **project root** — the parent
  of `.analyser/` — for the canonical profile, and against the file's own directory for a loose
  `.fluxtion-settings` file (`ProjectProfile.baseDirFor`, M35.10; the first cut anchored at the file's
  own directory, so a canonical profile's `src/main/java` landed at `<bundle>/.analyser/src/main/java`).
  This fix is what makes "one dialog, everything configured" true.
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
       the in-app assistant's job.) _Once the Mongoose MCP tool exists (UP-MNG-02) this part becomes the
       full loop of the dev-loop spec: the agent restarts the server through the client's approval
       prompt and the analyser's Follow picks up the fresh log — the tutorial should then say so._

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

  **Reality correction after the released bundle was exercised (2026-08-30).** The v3 bundle is already
  a project profile, so the tutorial opens it with **File ▸ Open project…**; it does not import the same
  profile through the older settings-summary route. Its Chronicle-to-YAML export is a fixed snapshot,
  not a file the running server appends to, so **Follow** cannot demonstrate live streaming on this
  bundle. The screenshot set uses project + explicit GraphML, records/filter, source navigation, graph,
  Explain/copy-prompt and MCP setup; Follow remains documented for directly-growing YAML logs. This is
  the consequence of the accepted export-beat contract, not a missing analyser feature.
- **Cross-links**: getting-started Quick start step 2 ("No log yet?") gains the playground option next
  to the static sample; producing-a-log.md links it as "want a live producer to try?"; landing page
  "Get going" mentions it.
- **Analyser changes: none required** for v1 (Follow, import, source nav all shipped). One candidate
  polish item if testing shows friction: a **File ▸ Open example…** helper that takes the bundle
  folder and does import + open + Follow in one action (defer unless the tutorial reads clunky
  without it).

## Synergy: this example is the loop's conformance bench (was: M18's validation bench)

_**Updated 2026-08-25.** M18 is closed (tracker); the server link is now the agent-brokered dev loop
(`spec-agent-brokered-dev-loop.md`), whose §H names a conformance harness "homed in the M19 bench". That
harness exists: `tools/bench/loop-bench.py` plays the agent (registry → export → drive → assert) and
`tools/bench/mongoose-stub.py` plays a server reduced to the contract, so it runs today with no Mongoose.
When this bundle ships, the bundle's server is the real thing the bench points at — and UP-MNG-01/02 are
done when the bench passes against it. The paragraphs below are the M18-era text, kept for the gaps they
record (sink descriptor → UP-MNG-04, `EventLogControlEvent` → UP-MNG-02, lifecycle → UP-MNG-02)._

### The M18-era text

The same bundle validates the **Mongoose server link** (spec-closed-loop Part B) end to end: a
known-good local server with the admin REST on, a predictable log, restart-safe (it's disposably
local — the per-link dev opt-in is honestly true here). Concretely:

- **M18.0 (spike)** runs *against this example* — `svc-admin-web` already serves status + processor
  enumeration; verify the three gaps on it (**sink-descriptor discovery, `EventLogControlEvent`,
  lifecycle** — registered admin commands or none); **any missing admin capability becomes a
  `fluxtion-server-plugins` PR** (updates to the Mongoose plugin are expected, not exceptional —
  budget for them). The v2 export beat keeps discovery explicit: the registry identifies the capture,
  and the bundle-owned command materialises the declared YAML path.
- M18.2's "Open server's audit log", M18.3's level control (watch a later export get chattier), and
  M18.4's dev restart all get their acceptance demos on this bundle.
- Later, the tutorial gains an optional part 4 ("control the server from the analyser") once M18.1–3
  ship — the onboarding page and the feature validate each other.

## Acceptance

A fresh machine with only a JDK: playground → Download → one run command → jbang one-liner → import →
Follow shows live records → click-to-source lands in the bundled example code → Explain produces a
grounded answer. Timed under 10 minutes by someone who isn't us.

## Revision 2026-08-29 — bringing this current with M38, M42 and M43

_Owner-directed. This spec was written before **M38** (portable context), **M42** (connect an AI client)
and **M43** (the AI menu, runbook descriptions, skills). Its target experience is still the right one —
"<10 minutes, nothing pre-installed but a JDK" — but three of its steps describe a world where those
milestones do not exist, and one paragraph is now actively wrong. Revised additively; nothing above is
deleted, because the reasoning that produced it is still the reasoning._

The goal, restated in the owner's words: **from a standing start, one download produces a project where
an LLM opened in that directory already knows how to design with Fluxtion, is connected to the analyser
over MCP, and is told by the analyser which skills deploy, start, stop and read the local application.**
Everything below is what M19 needs in order to be that.

### R1 — the bundle ships SKILLS, not only a `CLAUDE.md`

`CLAUDE.md` bootstraps knowledge; a **skill** is an invocable procedure. M43 taught the analyser to read
the shape, so the bundle should use it:

```
.claude/skills/<name>/SKILL.md      ---  name: … / description: … ---  then the steps
```

At minimum: **run**, **stop**, **where the audit log is**, and **regenerate after a graph change** (R4).
`AGENTS.md` remains the mirror for non-Claude harnesses; the skills directory is the executable half.

**The description is not decoration.** It is what lets a model choose the right skill without opening
all of them (M43.2), and it is the only part the analyser serves.

### R2 — the shipped profile REGISTERS those skills

The bundle already carries `.analyser/project.fluxtion-settings`. It should now also carry, for each
shipped skill:

```
runbook.N.name=start-server
runbook.N.path=.claude/skills/start-server/SKILL.md
runbook.N.description=Start the example and write its audit log to ./logs/…
```

so `context.runbooks[]` names them **from the first open**, with no adoption step.

**This does not violate M43's "offers, never selects" (D-AI5), and the spec must say why**, because it
looks like it does. That rule constrains **the analyser** adopting something it discovered — it must not
turn a file it found into a declared fact. A bundle author declaring their own runbooks in the profile
they ship is the *author* declaring, which is precisely the declared-not-inferred model working. Different
actor, different act. The analyser still reads only what someone wrote down.

### R3 — the agent is connected to the analyser, and step 6's division of labour is now wrong

The bundle should carry a **client-neutral** instruction, not a command line.

**Corrected 2026-08-29 (review F1).** An earlier draft here printed
`claude mcp add fluxtion-analyser -- java -jar /path/to/…jar --mcp`. That is wrong twice over: the shipped
route builds `mcp add --scope user --transport stdio fluxtion-analyser -- <resolved bridge>`
(`ClaudeMcpClient.addCommand`), and — more importantly — **a copied generic jar path is not an
installation instruction.** M42 exists precisely because the analyser resolves the real local command
(installed JBang launcher, else the running packaged jar) and hands it over already correct. Hard-coding a
path defeats that and only serves one of the three supported clients.

What the bundle should say instead:

> Start the analyser with local transport enabled, then use **AI ▸ Connect an AI client…** — it detects
> Claude Code, Codex or a generic MCP client and gives you the resolved registration for this machine.

That is client-neutral, cannot go stale, and is the route the docs already teach.

**Correction to step 6.** It reads: *code editing happens in the user's IDE with their own agent*,
framed as a deliberate division from the in-app assistant. **M42 partly dissolved that.** It is now one
agent that both edits code and drives the analyser over fourteen verbs. The principle that survives is
narrower and should be stated as such: **the analyser still edits no code** — hand-off-don't-embed is a
constraint on the *analyser*, not a claim about how many agents the user has. As written, the paragraph
describes a separation that no longer exists, and it is the sort of stale-by-success text that misleads
precisely because it used to be right.

### R4 — the licence key, which is the most likely first-run failure

The template ships with the processor **already generated**, so it builds and runs with no key. The
moment the graph changes, regeneration calls the hosted service and needs one — and the build stops at
`process-classes` with no obvious cause. That is the first wall an LLM hits after its first success.

Two consequences for this spec:

1. **The seeded `CLAUDE.md` must say it, before the agent hits it.** One paragraph: the processor is
   pre-generated so this runs immediately; changing the graph needs a key; here is where it goes. An
   agent that hits `process-classes` without that context will conclude the project is broken.
2. **The key has ONE canonical home and it is already established** — verified in the starter's own
   `check-fluxtion-key.sh` and in `fluxtion-visualiser`'s account dialog:

   **Corrected 2026-08-29 (review F3), verified against `fluxtion-builder` 1.0.64's
   `FluxtionConfigManager` rather than against a shell script:**

   ```
   -Dfluxtion.apiKey=…                  system property   [WINS when set]
   ~/.fluxtion/fluxtion.apiKeyFile      apiKey=…          [used when the property is absent]
   ```

   Two errors in the earlier draft, both from taking `check-fluxtion-key.sh` as the builder's contract:
   **`FLUXTION_API_KEY` is not read by the build at all** (`FluxtionConfigManager` makes no
   `System.getenv` call), and the file is not "preferred" — the **system property overrides it**.

   The consequence is a real trap and the bundle must say so: a preflight script that reads the
   environment variable can PASS on a value the build never receives. It is still not
   `~/.m2/settings.xml`.

### R5 — the analyser opens BEFORE the first run, and has something to say

M19's sequence opens the analyser on a log. There is no log until the example runs once. Better, and it
costs nothing: **open the analyser on the GraphML first.** M35 supports a graph with no log, and M40.1
then answers *"will this processor produce an audit log at all?"* from the graph alone.

That makes the analyser's first sentence to a newcomer a useful one, before anything has run — and it
demonstrates the product's actual thesis (a verdict from declared structure) at minute two rather than
minute ten.

### R7 — DAY TWO: making an existing project analyser-ready (owner, 2026-08-29)

Everything above makes **day one** excellent. Day two is the user's own project, and today it is a
cul-de-sac — which is the classic way a good on-ramp converts nobody: the demo works and cannot be
reproduced on real work.

**The pieces all exist. The journey does not.** Verified in code:

| Step | Today |
|---|---|
| create a profile | `File ▸ New project…` — exists, and creates an **empty** profile ("Start an empty project profile") |
| find the skills already in the repo | `AI ▸ Runbooks… ▸ Find skills…` — exists (M43.7) |
| register each | manual confirm — by design (D-AI5) |
| source roots | manual, via Settings |
| the graph | manual, via `open {graphml}` |

So a user who has just seen a primed bundle work must then perform four unrelated actions, none of which
anything tells them about, to reach the same state on their own code.

**Proposal, and it is the pattern this app already has rather than a new one: `New project…` should OFFER
what it found.** It creates an empty profile today; it could create one that says *"I found two
`SKILL.md` files, a `src/main/java`, and a `.graphml` beside your log — add them?"* — offering, never
selecting (M35.4, D-AI5). Every ingredient is built: `SkillDiscovery` for skills, the M35.4 graph
discovery that already offers and never chooses, and the build layout for a source-root guess.

That turns day two from four undocumented steps into one dialog a person confirms, and it is the same act
the bundle performs — the difference being who declares it, which is exactly the D-AI5 distinction R2
already relies on.

**Acceptance for R7 is in the list below.** It is small, it is inside this repo, and it is the one part of
M19 that does not need three parties to agree.

### Delivery: two steps, and the second is a MEASUREMENT (owner, 2026-08-29)

The owner's sequencing, and it is better than the recommendation it replaced. My advice had been to author
the seeded `CLAUDE.md` first, on the grounds that the payload matters more than the plumbing. The owner's
plan inverts it:

> **1.** Get the template/new-project process working.
> **2.** Have a **context-free LLM** develop and analyse a real Fluxtion project using it. The gaps it
> hits rewrite the static context assets.

**Why this is the better order.** Authoring the context up front means guessing what an LLM will lack.
Step 2 *observes* it. That is the same discipline as the prediction files: a measured gap beats an
imagined one, and the things a fresh model actually stumbles on are reliably not the things its author
expected. It is also the only way to find out whether the assets are wrong versus merely incomplete.

**What step 2 must capture to be evidence rather than impression** — record it, or it becomes a story
about how it went:

- the **question the model could not answer** from the seeded context, and what it did instead (guessed,
  read source, asked, gave up)
- whether it **invented an API** — the M21 failure mode, and the one the canon exists to prevent
- where it needed the **audit log to correct itself**, which is the loop working rather than a gap
- what it never used, because unused context is cost without benefit and the assets should shrink as well
  as grow

**A caution worth stating once.** "Context-free" is a property of the *session*, not of the model: a model
with Fluxtion in its training data is not context-free about Fluxtion, and will paper over gaps a genuinely
naive reader would fall into. That does not invalidate the exercise — it means an easy pass is weaker
evidence than a hard failure, and the failures are the useful output.

### The seeding prompt for step 2 (owner, 2026-08-29)

Step 2 is only a measurement if the prompt does not contaminate it. Four ways it can, and the prompt is
built to avoid each:

| Failure | What it produces |
|---|---|
| **Leading the witness** — *"try our great new template"* | agreement. Models are agreeable by default and will report success. |
| **Manufactured hostility** — *"try to break it"* | adversarial theatre. Real users are not attacking the docs. |
| **Instructing the task** — *"read CLAUDE.md, then add a node"* | tests the prompt. The DOCUMENTATION is supposed to be what tells it that. |
| **Revealing it is a test** | the model evaluates documentation instead of using it, which is a different activity with different behaviour. |

**And the risk that is easy to miss: the danger is not that it fails — it is that it SUCCEEDS by
compensating.** A capable model will fill a documentation gap from training data, or by reading the
generated source, and finish the task. The docs then look adequate and the gap is invisible. So the
prompt's real job is to make the compensation *visible*, not to prevent it.

**The measurement is mostly external, and that is deliberate.** The model's account of how it went is
testimony; the git history, the code it wrote and the audit log are evidence. Prefer the second — the same
distinction `spec-trust-structure.md` D-T3 draws for the product applies to assessing the product.

#### The prompt

Give it the project directory and this. Nothing else — no tool names praised, no instruction to read any
particular file, and no mention that documentation is under test.

```text
You have been handed this project directory. You have not worked with this stack before and you
have no stake in whether it is any good.

Task: <one small, concrete, verifiable change — e.g. "add a node that flags readings above a
threshold, run the project, and show me evidence from the audit log that it fired">

Work from what is in the project. If something is not explained there you may look it up or read
the generated source — but record it, because what the project failed to tell you is the thing we
need to know.

Append a line to NOTES.md whenever any of these happens:
  - you could not find something the project should have told you
  - you guessed at an API, a command or a file location instead of reading it
  - you went outside the project to proceed (training knowledge, the web, reading generated code)
  - the project told you something that turned out to be wrong
  - something worked and you are not sure why

Do not review the documentation. Do the task and record the friction.

If you cannot complete the task, stop and say why. Do not substitute an easier one.
```

The last line is not filler: substituting a reachable task for the assigned one is a common model
behaviour and it converts a hard failure into an apparent success, which is exactly the signal being
bought.

#### Reading the result

- **A clean run is weak evidence.** It may mean the docs are good, or that the model already knew Fluxtion.
  Check `NOTES.md` and the diff for compensation before concluding anything.
- **An invented API is the strongest single finding** — it is the M21 failure mode, and it says the canon
  did not reach the author.
- **Where the audit log corrected it, the loop worked.** That is a success of the product, not a gap in the
  docs, and the two should not be scored together.
- **What it never opened is as informative as what it needed.** Unused context is cost; the assets should
  shrink as well as grow.
- **Run it more than once, on different models.** One session is an anecdote; the gaps that recur are the
  ones to write against.

### R8 — the key surface, as a thing to build (owner asked 2026-08-29: is key management in here?)

It was not. D-R1 resolved the *decision* — the analyser owns provenance, writes the file, never holds the
value — and R4 says what the bundle's `CLAUDE.md` must warn about. Neither described the feature, and more
seriously **the safety limit existed only as prose with no acceptance criteria**, which for a credential is
the half that matters. This section is the buildable part.

#### What it is

**Three surfaces, no dialog at first run** (R6 — D-S1 removed that modal and it must not return):

1. **A start-page card.** *"Fluxtion API key — not found. Needed to regenerate a processor; this project
   runs without one."* States the fact, names the remedy, gates nothing.
2. **An `AI` menu item** — *Fluxtion API key…* — opening the management dialog. Same owner, one place.
3. **A Project-panel row stating only what the analyser can OBSERVE** (review F2, 2026-08-29 —
   corrected twice, and the second correction removes a claim the first left in). The analyser is a
   separate process from the Maven JVM that will run the build. It **cannot know** which source that
   future build resolves, so it must not say *"overridden by `-Dfluxtion.apiKey`"* — that is a claim
   about a process that has not started.

   What it may state, because it can check it:

   > *"key file: present at `~/.fluxtion/fluxtion.apiKeyFile`"* — or absent
   > *"note: a `-Dfluxtion.apiKey` system property passed to the build overrides this file, and
   > `FLUXTION_API_KEY` is not read by the build at all"*

   The second line is **documentation of the precedence rule**, not an observation of which source won.
   That distinction is the whole of D-T4 applied to this row: state the fact you have, name the rule, and
   do not narrate a process you cannot see.

   **It must not report `FLUXTION_API_KEY` as the answering source**, because the builder never reads it
   — that would state a provenance that did not answer the build, which is worse than saying nothing.
   Reporting it as *"set, but not read by the build"* is legitimate and useful, since a user who set only
   that variable is in the confusing state this row exists to resolve. The analyser also cannot observe a
   `-D` passed to a future Maven run, so where the value is not locally determinable the row says so
   rather than guessing.

#### What the dialog does

- Writes `~/.fluxtion/fluxtion.apiKeyFile` in the established `apiKey=…` format — **lift
  `fluxtion-visualiser`'s `FluxtionAccountDialog` rather than re-derive it**, including its
  `~/.fluxtion/profiles/` concept, which is how one machine holds a work key and an evaluation key without
  editing a file between builds.
- Masks the field on screen (`JPasswordField`), as that dialog already does.
- Reports **presence and provenance** afterwards; never re-displays the stored value.
- Never validates the key against a service. Presence is a local fact; validity is the build's business,
  and an analyser that phoned home to check a key would be doing the licence-enforcement this product
  argues against.

#### The limit, now with acceptance rather than prose

The value is written and forgotten. It must never reach any surface that leaves the machine or the moment:

| Surface | Why it is a real exposure |
|---|---|
| `context` / any verb echo | goes to an agent, and into its transcript |
| the project profile | is committed and shared — M38's entire purpose |
| `SettingsShare` export | goes to a colleague |
| the status bar / console | lands in screenshots and screen-shares |
| a screenshot | **the four-term sweep cannot see inside a PNG** — how real names reached the public site in August |

`AppConfig` must not hold it. `KnownKeys` must not own a family for it. The M42 review found JVM options
carrying secrets into a client config file, so this is a demonstrated failure mode in this codebase, not a
hypothetical one.

#### What would change the design

If the key ever becomes a **signed licence the analyser must enforce** — an expiry it checks, a capability
it gates — this stops being setup convenience and becomes licence enforcement inside source-available
desktop code, which is theatre: an `if` statement anyone can remove, teaching honest users the licence is
nominal. Writing a key is fine. **Checking one to decide what the analyser will do is a different product
and needs its own spec.**

### The documents themselves

Authored in **[`docs/skills/`](../skills/README.md)** — the source of truth for what is published to the
website and baked into a bundle. Four to start: `common/load-audit-log`, `common/replay-a-run`,
`embedded/run-embedded`, `mongoose/run-mongoose-server`. Verified discoverable by the shipped
`SkillDiscovery`, each with a description the analyser reads.

They obey the rule in D-R2: they describe the project's own entry points and never invent a CLI. Where a
step cannot be grounded without the host in front of you it carries a `TODO(bundle)` marker — an
instruction to the generator, and a bug if it ever reaches a shipped bundle.

### R6 — the first-run surface is the START PAGE, not a dialog (correcting my own draft)

Worth stating because I got it wrong twice in one session, and because the next reader will make the same
assumption. **The analyser has no first-run dialog.** `showFirstRunSettingsIfNeeded()` is now a no-op for
a human: M36 **D-S1** removed the modal after the owner reported *"no config found modal popped"*, and
what remains is the `--rest` stdout note for the case where nobody is at the screen.

The start page **is** the first run — it is the no-log STATE — and it already carries the MCP setup cards
(Connect Codex / Connect Claude / Generic). So the behaviour we want on first launch exists; it is a
surface, not a modal, and that is the better shape.

**Therefore: licence registration belongs on the start page and on the AI menu, not in a new dialog.**
Adding a first-run modal to offer key registration would reverse D-S1 — an owner-directed removal — and
would re-introduce the exact thing M35 spent a milestone deleting: something that fires at a screen where
the user has not yet asked for anything. A start-page card ("Fluxtion API key: not set — needed to
regenerate a processor") states the fact and names the remedy without gating anything.

### Cold test, 2026-08-29 — run rather than argued

The owner's instruction after I hedged twice: *"if you aren't sure about C1, create a small Fluxtion
project and try it."* Done, from a **fresh `HOME`** with no analyser config, against the **released**
1.12.0 pulled by jbang — not a local build.

| Step | Result |
|---|---|
| `jbang analyser@telaminai/fluxtionauditlog-analyser --rest` | **PASS** — installed and running from a fresh HOME, endpoint published |
| a **hand-written** audit log, typed from *Format specification* | **PASS** — parsed, 3 records |
| bare project: skills on disk, **no profile** | `context.runbooks` = none — **as designed** |
| the same project with a **seeded profile** (the M19 bundle simulated) | **PASS** — both skills listed, `exists=true`, descriptions served |

**The finding that matters, and it sharpens R2.** A project can carry `.claude/skills/` and the analyser
will say nothing about them until a project profile declares them. That is correct — *offers, never
selects* — but it means the two halves of "the LLM knows about the skills" work by **different
mechanisms**:

- the **agent harness** reads `.claude/skills/` off the filesystem itself, which is why the owner's
  Friday run worked with skills that were already there for another purpose;
- the **analyser** only reports what the profile declares, which is what `context.runbooks[]` serves.

The owner's stated goal — *"the analyser tells the LLM about skills"* — is specifically the second, and
therefore specifically needs the seeding in R2. Without it the skills still work, but the analyser is not
the one telling anyone about them. Worth stating plainly, because "it worked" is true of both mechanisms
and they fail differently.

**And a correction to my own R6, which was half wrong.** I wrote that the analyser shows no dialog on
first run. That is true of a **fresh install** and false of an **upgrade**: `maybeShowWhatsNew()` guards
with *"fresh install, not an upgrade"* (`MainFrame:1438`), so a first-ever run is silent — confirmed by
the cold run's log, which contains only the REST line — while an existing user who upgrades sees
*"What's new in 1.12.0"*. The owner's report and my reading were about different situations and both
were accurate. The onboarding path is unaffected; the correction is recorded because "a dialog appears on
install" is exactly the sort of thing that gets remembered without its condition.

### Concerns for the handoff review

Recorded rather than resolved. Each is falsifiable and none blocks the design.

**C1 — CORRECTED 2026-08-29. It HAS been run, and the claim I made was wrong.** I wrote that the
headline had never been tested cold. The owner had already run it on the Friday before: a **bare Java
project**, a **single `jbang` install** of the analyser, **existing Claude skills** that were simply
picked up, and **Codex** — not Claude Code — driving the analyser over MCP. It worked.

That is stronger evidence than anything else this spec rests on, and it is worth being precise about why:

- **A different LLM.** Every other data point in this project comes from a Claude session. Codex driving
  fourteen verbs is the first evidence that the MCP surface is not accidentally shaped around the client
  that built it — which is exactly what M42 claimed and could not previously demonstrate.
- **A project not prepared for it.** The skills were already there for another reason and the analyser
  found and used them. M43's discovery working on a project that was never designed for the convention is
  a better test than the fixture I wrote for it.
- **One command.** The install half of this spec's promise is demonstrated, not projected.

**What it does not yet cover, and this is the only part of C1 that survives:** the bundle half. The
playground download, the seeded profile, the seeded skills and the tier selection do not exist yet, so
nothing has exercised them. The evidence covers *analyser + MCP + skills already present*; it cannot
cover *a generated project that arrives primed*, because no such project has been generated.

**So the bench's argument changes, and weakens.** It is no longer "nobody has tried this" — it is the
ordinary regression case: this works today, three repositories will change independently, and nothing
will say when it stops. That is worth having once the bundle exists, and is not worth blocking on before
then. Scope it to the bundle path rather than to the whole claim.

**C2 — the analyser side is one line; the BUNDLE side still has steps.** The analyser is
`jbang analyser@telaminai/fluxtionauditlog-analyser` and a start page, which is genuinely low friction (I
previously counted this wrongly and the count is corrected above). What remains is on the project: download,
build, run, find the log. Worth considering a single `./go` in the bundle that builds, runs, prints where
the audit log landed and prints the exact `jbang` line to open the analyser. Two commands, the second
copy-paste. That is a larger win than anything else in this revision and it costs the bundle generator one
script.

**C3 — "nothing pre-installed but a JDK" is now understated** _(and the Friday run shows why it still matters)_. The experience this revision describes needs
a JDK **plus an MCP-capable agent, configured, with a subscription**. That is the target user and the
assumption is fine — but the promise no longer describes what is delivered. Someone arriving with only a
JDK gets the analyser and the log and none of the AI story, and should be told that up front rather than
discovering it. Suggest the headline becomes explicit about the two paths. The Friday run is the illustration rather than the counter-example: it worked because an MCP-capable client was already there and already subscribed. That is the assumption, and it held — it just should not be silent.

**C4 — three repositories have to agree, and that is a sequencing risk rather than a design one.** M19 needs
playground bundle generation, the Mongoose starter's behaviour, and this analyser. The owner has
deliberately not started the cross-repo briefs until the project structure stabilised, which is the right
order — starting them earlier would have meant briefing against a moving target. The risk transfers rather
than disappears: once started, three parties must stay aligned across releases, and every cross-repo item
in this project so far has moved slowly (the upstream asks were drafted 2026-08-25 and are not all filed).
C1's bench is the cheapest available guard, because it fails when the three drift.

**C5 — `TODO(bundle)` markers are a deliberate admission, and need an owner.** The canonical skills carry
them where a step cannot be grounded without the host in front of you. Acceptance says a shipped bundle
containing one is a bug — but nothing currently *checks* that, and the check belongs to whoever generates
bundles, not to this repo.

## M19 BUNDLE CONTRACT v3 — normative (2026-08-29)

_Raised as blocking by three reviews. This is the artefact a generator in another repository can be
checked against; everything above it is rationale. v2 incorporated the live-source finding that Mongoose
capture is Chronicle-only and analyser-readable YAML is an export. **v3 corrects the profile ABI before
publication:** v2 accidentally specified one-based list keys and a singular processor key that the
analyser never reads. No v2 bundle was published or accepted. The analyser side of every v3 key below is
pinned by a real `ProjectProfile.load` test, not only compared with prose._

**Contract version:** `m19-bundle/3`. A bundle declares it; a checker refuses an unknown version.

### Files the bundle MUST contain

| Path | Required | Owner | Verified against |
|---|---|---|---|
| `.analyser/project.fluxtion-settings` | yes | generator | `ProjectProfile.CANONICAL_RELATIVE` — this exact relative path, no alternative accepted |
| `<the analyser-readable YAML export>` | yes, at a **generated concrete path** after the export command | generator | Mongoose Chronicle capture + `/api/audit/file/{id}/export?format=yaml`; D-X6 still holds because the analyser cannot infer an unopened export path |
| `<the processor's GraphML>` | yes, at a generated concrete path | generator | pairing needs it; M40.1 gives a verdict from it before any run |
| the generated processor source | yes | generator | source navigation, and it is what makes the bundle keyless |
| `.claude/skills/<name>/SKILL.md` × n | yes | canonical library | `SkillDiscovery` finds `SKILL.md` case-insensitively, ≤ depth 7 |
| `CLAUDE.md` (+ `AGENTS.md` mirror) | yes | canonical + generator | R3, R4 |
| `README.md` | yes | generator | run command, log path, the client-neutral analyser route |

### Profile keys the generator MUST write

Exact key names, verified in `ConfigStore`:

| Key | Value | Notes |
|---|---|---|
| `share.version` | `1` | explicit rather than relying on the missing-version compatibility default |
| `sourceRoot.count` / `sourceRoot.0`…`sourceRoot.(count-1)` | project-relative roots | zero-based; must resolve in the unzipped bundle |
| `eventProcessorFqn.count` / `eventProcessorFqn.0`…`eventProcessorFqn.(count-1)` | processor classes | zero-based `ConfigStore` list family |
| `selectedEventProcessor` | the primary processor class | must also be present in the processor list |
| `runbook.count` | number of skills registered | |
| `runbook.0.name`…`runbook.(count-1).name` | matches the skill directory name | zero-based; 1–40 of `[A-Za-z0-9_-]`, `Runbooks.refuse` |
| `runbook.0.path`…`runbook.(count-1).path` | e.g. `.claude/skills/<name>/SKILL.md` | project-relative; `..`, absolute and URLs refused |
| `runbook.0.description`…`runbook.(count-1).description` | the skill's frontmatter `description` | optional, one line, ≤ 300 chars |
| `vocabulary` | glossary path, if the bundle ships one | same pointer rules |
| `skills.provenance` | value-free source identity + revision, or `none` | inert declared fact only; it never controls retrieval |

**The profile MUST NOT contain:** any log/export path (no such category exists), any API key, or
`skills.source` (build/release-tier only, D-R4/D-X8).

There is no `projectName` profile key. The analyser declares the active project from the canonical
profile's project-root directory (`ProjectProfile.baseDirFor` / `ProjectState.activeName`); an unknown
`projectName` property is preserved for forward compatibility but does not name the project in context.

### Commands the bundle MUST document

| Purpose | Contract |
|---|---|
| run | **one command**, from the bundle's own script; works with **no API key** |
| export | **one command**, from the bundle's own script; selects the recorded audit file through the published registry/API and writes YAML to the bundle's declared concrete path |
| open the analyser | `jbang` line **plus** the JBang prerequisite, or the plain `java -jar` alternative (F7) |
| connect an AI client | **client-neutral**: start with local transport, then *AI ▸ Connect an AI client…*. Never a hard-coded `claude mcp add` (D-X7) |
| regenerate | separate, and the **only** step that mentions a key (F2/F6) |

### Acceptance a generated bundle must pass

- [ ] unzip → run → export using the bundle's own one command → analyser-readable YAML exists at the
      declared path, **with no API key present**
- [ ] the analyser opens that log and its GraphML from the paths the profile/skills name, **on a machine
      that has never opened either** (this is what F3 was about)
- [ ] `analyser_context` reports the project, the log, the pairing, and every registered runbook with its
      description and `exists: true`
- [ ] *Find skills…* lists exactly the shipped skills and marks them already declared
- [ ] no `TODO(bundle)` marker survives anywhere in the bundle
- [ ] the contract version is declared and matches this table

### Ownership

| Part | Repo |
|---|---|
| this contract, the profile format, the analyser behaviour | **analyser** (here) |
| bundle generation, the run script, the concrete paths | **playground** |
| canonical skill content | **analyser** (here) |
| selected/substituted skill copies and the project operations they name | **playground**, from the canonical library |

## BUNDLE CONTRACT v4 — DELTA from v3 (2026-08-30) · the playground-side work

_Why a delta and not a rewrite: v3's tables are still correct except where this session changed them.
Everything not listed here is unchanged and still normative. **Contract version:** `m19-bundle/4`._

!!! warning "Not yet buildable against — `m19-skills/2` is DRAFT"

    v2's selected skills still carry `TODO(bundle)` markers that only a real generator can substitute,
    and v3's acceptance already refuses a surviving marker. **v1 remains the published skills contract**
    until a generator selects a real template, substitutes every marker and passes that gate on a
    generated bundle (review C1). This section specifies the target; it does not declare it met.

**This section is the answer to "where is the playground work specified?" — it was not, until now.**
`m19-skills/2` and `reference-set.json` were built here and existed only as artefacts; nothing told a
generator what to do with them.

### D-B1 · Selection moves from ONE HOST TIER to COMMON + SPECIALISATIONS

v3 consumed `m19-skills/1`: `common` plus one host tier. v4 consumes **`m19-skills/2`**, where `common` is
always selected and a **template names the specialisations it wants**.

| Playground obligation | Detail |
|---|---|
| read `m19-skills/2/index.json` | `common` (list) + `specialisations` (map). Selection = `common` + the template's named specialisations |
| own the **template → specialisations** mapping for its real catalogue | **entirely playground-side.** The canonical index deliberately declares **no** `templates` map (review C1): centralising one of fourteen catalogue templates in an analyser-owned file is the second source of truth this design exists to avoid. The analyser exercises the rule with a test fixture only |
| **never select `replay` without a real replay entry point** | `common/replay-a-run/SKILL.md` carries a required `TODO(bundle)` the bundle could not substitute. v3's acceptance *"no `TODO(bundle)` survives"* already forbids shipping it unsubstituted; this makes the selection rule match |
| verify vendored bytes against the pinned provenance | **on publication.** v2 is `DRAFT — NOT PUBLISHED` and makes no provenance claim; a pinned draft goes stale on the next edit and then either lies or forces a red commit. When v2 is published it must pin a `revision` containing every selected byte plus a per-path `sha256`, which `CanonicalSkillsTest` enforces conditionally today |
| record `skills.provenance` as `canonical@<revision>` | unchanged from v3; applies once v2 is published |

### D-B2 · The bundle's `CLAUDE.md` REFERENCES the agreed set; it does not restate it

Measured basis (spec-authoring-experience D-AX1b): most Fluxtion authoring material is already published
and the bundle did not point at it. Four wrong versions of the audit contract were written locally as a
result.

| Playground obligation | Detail |
|---|---|
| render the reference block from `reference-set.json` | canonical copy ships in the analyser jar at `/reference-set.json`. **Retrieval path for the generator:** `https://raw.githubusercontent.com/telaminai/fluxtionauditlog-analyser/main/src/main/resources/reference-set.json` — it is deliberately NOT under the skills root, because a second copy would drift. Vendor a snapshot at build time (D-R3), never fetch at runtime. `ReferenceSet.markdown(kind)` is the reference rendering |
| ship **only `agreed` entries** | `proposed` awaits sign-off; `excluded` carries its reason. Today: four agreed |
| let `appliesTo` **select**, not annotate | a Spring-only link must not appear in a non-Spring project — an always-in-context file is a tax on every turn (review N1) |
| below the block, only what the set does **not** cover | this project's paths, commands, graph — and the audit contract, until [fluxtion#22](https://github.com/telaminai/fluxtion/issues/22) lands upstream |
| mirror to `AGENTS.md` by **generation**, never by hand | two hand-maintained copies diverge silently (D-AX8) |

**A generated `CLAUDE.md` must not restate a rule the agreed set already carries.** That is the D-AX1b
duplication rule, and it is what makes an upstream edit improve every project instead of one.

### D-B3 · New `TODO(bundle)` markers to substitute

| Marker | In | Substitute with |
|---|---|---|
| regeneration command | `spring/add-a-node/SKILL.md` | this project's exact regeneration command, and whether it needs a Fluxtion API key |

(`common/replay-a-run` keeps its marker and is now only selected by templates that can substitute it.)

### D-B4 · Acceptance ADDED by v4

- [ ] the generated `CLAUDE.md` contains every **agreed** reference-set URL that applies to this template,
      and **no** URL whose `appliesTo` does not match
- [ ] it restates no rule already carried by an agreed resource
- [ ] the selected skill set equals `common` + the template's declared specialisations, resolved from
      `m19-skills/2`
- [ ] every vendored skill's bytes match its `sha256` at the declared `revision`
- [ ] a template without a replay entry point ships **no** `replay-a-run`
- [ ] `AGENTS.md` is byte-identical to `CLAUDE.md` and was generated, not written

### D-B5 · Still open, and NOT specified here

- **`agentBootstrap`** (UP-PG-02, [upstream-asks §6](../proposals/upstream-asks.md)) — where a generated
  project's agent instructions live, as a catalogue field. Adjacent to D-B2 and still unfiled.
- **A non-Claude skills location.** Bundles write `.claude/skills/` because it is the one layout that
  auto-loads. The analyser is harness-neutral by construction and serves runbooks through
  `context.runbooks[]`, so nothing is blocked — but if another harness's convention is verified, the
  generator may write there too and this contract does not need to change.
- **Guided start** ships in `common`, so every bundle carries it. It drives the analyser's own demo set
  and needs no substitution.

### Ownership for v4

Unchanged from v3, with two additions: **`reference-set.json` and `m19-skills/2` are analyser-owned**
(authored, reviewed and pinned here); **the template → specialisations mapping and the rendered
`CLAUDE.md` are playground-owned**, built from those inputs.

### Skills-source retrieval contract — `m19-skills/1`

`skills.source` is a **playground build/release input**, never a downloaded-project setting, profile
setting, browser-download fetch or analyser-runtime fetch. The playground vendors the selected snapshot
before deployment; its client-side Download generator reads only that committed snapshot.

The source modes are: the canonical HTTPS root, a credential-free corporate HTTPS mirror with the same
layout, a local `file:` root for air-gapped builds, or the literal `none`. HTTPS URLs with user-info,
query or fragment, credential-shaped text, redirects outside the configured origin, and project-supplied
values are refused before fetch or display. `none` performs no fetch and emits no skills/runbooks.

Every non-`none` root contains `m19-skills/1/index.json`:

```json
{
  "contract": "m19-skills/1",
  "revision": "immutable source revision",
  "skills": [
    {"tier": "common", "path": "common/load-audit-log/SKILL.md"},
    {"tier": "mongoose", "path": "mongoose/run-mongoose-server/SKILL.md"}
  ]
}
```

Paths are unique, relative, stay below the root, and select only `common` plus the requested host tier.
The index contains at most 32 entries; each skill is at most 64 KiB and the selected set at most 512 KiB.
Each selected file must have valid `name`, `description` and `x-analyser-min-version` frontmatter. The
generator refuses a minimum newer than the analyser version named by the bundle, substitutes every
project-owned command/path, then refuses `TODO(bundle)` and `/path/to/` in the result.

The build reports `none`, not-found, invalid-index/content and transport failure as distinct results; it
never silently falls back from an explicitly configured source. The generated profile records only a
sanitised identity in `skills.provenance`: `canonical@<revision>`,
`mirror:<scheme+host+path>@<revision>`, `local@<revision>`, or `none`. It records no local absolute path,
query, fragment, user-info, credential, response header or key material. Hash pinning and signing remain
recommended hardening, not v2 acceptance.

### Start signal and bounded deferral

The owner issued the implementation start signal on 2026-08-29 after the live reconnaissance report and
the export-beat decision. Mongoose bundles select `common + mongoose`; the embedded tier is explicitly
not part of M19 acceptance and remains NOT PUBLISHABLE until its listener/file lifecycle has an end-to-end
key-holder run. That bounded deferral does not block the Mongoose bundle.

### Acceptance added by this revision

- [ ] The bundle contains `.claude/skills/*/SKILL.md` using the `name`/`description` frontmatter M43
      reads, covering at least run / stop / log location / regenerate.
- [ ] The shipped profile registers each skill as `runbook.N.*` including its description, so
      `context.runbooks[]` names them on first open with no adoption step.
- [ ] `Find skills…` on the freshly downloaded project lists exactly the shipped skills and marks them
      already declared — the M43.7 path, exercised by the bundle rather than by a fixture.
- [ ] The bundle's `CLAUDE.md` carries the **client-neutral** setup route (start the analyser with local
      transport, then *AI ▸ Connect an AI client…*) and the key paragraph from R4. **Not a command line** —
      R3 removed that and this item contradicted it (D-X7).
- [ ] Acceptance for "the agent is connected" is: the analyser's resolved registration completed for the
      chosen client, the client lists the analyser's tools, and an `analyser_context` call returns this
      project's facts. A README line is not evidence of a connection.
- [ ] `skills.source` is honoured, defaults to canonical, accepts `none`, and is REFUSED from a project
      profile with the reason stated — a test asserts a profile carrying it is ignored (D-R4).
- [ ] The source actually used is recorded in the generated project and shown, so a corporate mirror is
      distinguishable from the canonical set without diffing (D-R4).
- [ ] No shipped bundle contains a `TODO(bundle)` marker (D-R2) — and something CHECKS it (C5).
- [ ] Licence registration is offered on the START PAGE and the AI menu, never as a first-run modal
      (R6 — D-S1 removed that modal on owner report and it must not return).
- [ ] **R8:** the key row states only **locally observable setup facts** (D-X3) — the key file is present
      or absent, and the precedence rule is documented beside it. It must NOT claim which source a future
      Maven invocation resolved, and must not list `FLUXTION_API_KEY` as an answering source, since the
      builder never reads it.
- [ ] **R8 safety, each asserted by a test rather than reviewed by eye:** the key value appears in no
      `context` output, no verb echo, no project profile, no `SettingsShare` export, and no status-bar or
      console text. `AppConfig` does not hold it; `KnownKeys` owns no family for it.
- [ ] **R8:** the analyser never validates the key against a service — presence is local, validity is the
      build's business.
- [ ] The <10-minute claim is exercised by a bench, not asserted (C1).
- [ ] **R7 (day two):** `New project…` offers what it found — skills via `SkillDiscovery`, a graph via
      M35.4's discovery, a source-root guess from the build layout — and adds nothing without a person
      confirming it. A test asserts an empty directory produces an empty offer rather than an error.
- [ ] **R7:** the day-two path is documented as a JOURNEY on the site, not as four separate features.
- [ ] **Step 2:** the context-free run records the four things above, and the assets are rewritten from
      what it recorded rather than from what we expected.
- [ ] **Step 2:** the seeding prompt is used verbatim — no tool names praised, no file named, no mention
      that documentation is under test — and the run is repeated on at least two different models, because
      one session is an anecdote and only recurring gaps are worth writing against.
- [ ] Step 6's division-of-labour paragraph is rewritten per R3.
- [ ] The tutorial opens the graph before the first run and shows M40.1's verdict (R5).

### D-R1 — the analyser owns key PROVENANCE ✅ RESOLVED (owner, 2026-08-29)

**Resolved: yes, and stronger than the original recommendation.** `fluxtion-visualiser` will not ship as a
product — it was too IDE-specific — and its tools fold into the analyser as capabilities. That removes the
one objection worth having (two Telamin tools disagreeing about a file neither owns alone) and makes the
analyser the single desktop surface for this.

**"Provenance" is the precise word, and it is a pattern this codebase already implements.** §E and M38.3
established: state a fact AND who supplied it. The key has three sources, and which one wins is exactly
the thing that costs an afternoon:

```
-Dfluxtion.apiKey=…               system property   [WINS when set]
~/.fluxtion/fluxtion.apiKeyFile   apiKey=…          [used when the property is absent]
```
_(Corrected 2026-08-29 — see R4. `FLUXTION_API_KEY` is read by scripts, NOT by the builder.)_

*"Your build is using the environment variable, not the file you just edited"* is `provenanceSource`
applied to a credential, and it is worth more than the setting UI.

**One distinction to hold.** The analyser owns the management surface and the provenance statement; it
does **not** own the FORMAT. The Maven plugin and the starter's `check-fluxtion-key.sh` also read that
file. Owning the writing does not make it yours to change unilaterally.

**The limit is unchanged by the promotion:** manage it, state where it came from, and the VALUE must
never reach `AppConfig`, `context`, a verb echo, the project profile, `SettingsShare` export, the status
bar or a screenshot. The four-term sweep cannot see inside a PNG — which is how real names reached the
public docs site in August.

_Original reasoning, kept because the check is the useful part._ My first instinct was to refuse this outright, on M42's precedent — *"Claude Code owns `~/.claude.json`;
this class never parses or edits it"*, so the analyser shells out to the client's own CLI instead. That
objection dissolves on inspection: `~/.fluxtion/` is **Fluxtion's own directory**, not a third party's,
and this app already owns and publishes `~/.fluxtion-analyser/rest-endpoint`. Writing a Fluxtion key to a
Fluxtion file is in-family; editing Maven's `settings.xml` would not be, and is not proposed.

The friction case is real. Today the newcomer's instruction is *"create a properties file in a dot
directory"*, at the exact moment they have just watched the tool work and want to change one line of
their graph. That is the worst possible place to lose someone.

**The limit, and why it is not fussiness.** A credential is a new class of thing for this codebase, which
currently has a strict no-credentials posture — D-C6 refuses credential SHAPES in report destinations, and
M42's own review found JVM options carrying secrets into a client config file. Holding a key inverts that
posture, so the shape should be:

- the analyser **writes** `~/.fluxtion/fluxtion.apiKeyFile` and then forgets the value
- it **reads back presence only** — *key present* / *no key found* — never the value
- the value **never** enters `AppConfig`, `context`, any verb echo, the project profile, `SettingsShare`
  export, the status bar or the console

Each of those is a real exposure, not a hypothetical: `context` goes to an agent, share export goes to a
colleague, and **the four-term sweep cannot see inside a screenshot** — which is how real names reached
the public docs site in August. A key on screen during a capture would be the same failure with worse
consequences.

Presence/absence is the fact worth surfacing, and it is the fact the newcomer needs. It has an obvious
home in the Project panel beside the graph, in the shape this app already uses well: **state the fact,
name the remedy** — *"Fluxtion API key: not found — regenerating this processor will fail. AI ▸ … to
set one."* That is D-AI3's disabled-with-a-reason applied to a precondition rather than a menu item.

**Prior art to LIFT, not merely match:** `fluxtion-visualiser`'s `FluxtionAccountDialog` already reads and
writes this file and supports named profiles under `~/.fluxtion/profiles/`. Since the visualiser is not
shipping, that dialog is the thing to bring across rather than re-derive — including the profile concept,
which is how one machine holds a work key and an evaluation key without editing a file between builds.

**What would change my recommendation:** if the key is ever more than a token — a signed licence with an
expiry the analyser would have to *enforce* — then this stops being a setup convenience and becomes
licence enforcement inside source-available desktop code, which is the theatre the product assessment
argues against. Writing a key is fine. Checking a key to decide what the analyser will do is a different
product, and should be specced as one.

## Owner decisions this revision needs

- **D-R1 — does the analyser manage the key?** See the separate note below; my recommendation is that it
  helps write the file and never holds the value.
### D-R2 — the skills are a LIBRARY KEYED BY HOST, not per-project authoring ✅ RESOLVED (owner, 2026-08-29)

My R1 framed skills as something a bundle author writes. That is the wrong shape and it would have
produced a different, drifting set per template. Almost none of it is domain-specific: what varies is
**how the processor is hosted**.

| Tier | Skills | Grounded in |
|---|---|---|
| **common** | load an audit log · record a run · replay a run | the record/replay mechanism, and the analyser's own open path |
| **mongoose-hosted** | deploy · start · stop · where its audit log lands | the starter's own scripts and admin endpoint |
| **embedded** | start/stop in-process · where its audit log lands | `DataFlow.setAuditLogProcessor(LogRecordListener)` — **NOT** `addSink`/`FileMessageSink`, which is business output and cannot be an audit listener (review F1/F5, D-X4). Tier is **NOT PUBLISHABLE** until run end to end. |

A bundle **selects a tier**; it does not author a set. The domain-specific layer on top is thin and may be
empty.

**Skills describe the project's own entry points; they never invent a CLI.** A skill that says *"run
`./scripts/run-server.sh`"* is true of the project that ships it; one that invents a command is fiction
that fails on first use, and an agent has no way to tell the difference. Where a canonical skill cannot
be grounded without the host in front of it, it says so rather than guessing.

### D-R3 — content is late-bound at DOWNLOAD, never at RUNTIME (owner, 2026-08-29)

The owner's question: if the authoring context, skills and LLM onboarding live as documents on the
website, can they be updated without releasing the analyser? **Yes — and the mechanism is already
decided, which is why this works.**

**The line that must hold.** Separate content the analyser must **BEHAVE BY** from content that
**GUIDES** a human or an LLM:

| | Where it lives | Why |
|---|---|---|
| verb schemas, format-spec conformance, anything a licence decision rests on | **in the jar**, versioned with it | a tool that fetches the definition of its own behaviour is asserting things whose meaning it does not hold |
| prose, tutorials, build-with-AI authoring context, `CLAUDE.md` canon, **skills** | **on the website** | they guide; they do not define what the analyser does |

**And they are baked in at BUNDLE-GENERATION time, not fetched at use time.** O3 already resolved that
bundles are generated at Download. So: update the website, the next download carries it, no analyser
release — without anything fetching at runtime.

Runtime fetching would cost three things currently being sold, and none of them is recoverable later:
**the offline/air-gapped story** that answers regulated buyers, **reproducibility** (the same log
analysed twice must not give different guidance), and **version safety** — a skill written for v1.12's
verbs, used with someone's v1.9 analyser, breaks silently. That last one is cheap to mitigate and should
be a line of frontmatter: the minimum analyser version a skill expects. The analyser already reports its
own.

This is not a new pattern. M19's prompt stack already says **embed a snapshot, reference the canon**; this
extends the same rule to skills.

### D-R4 — the skills SOURCE is overridable, and it is a supply-chain surface (owner, 2026-08-29)

The owner's requirement: in test, and in a corporate environment, the skills written into a project must
be **controlled by a URL** rather than always the canonical one. Correct, and it needs designing rather
than adding, because a configurable source of *procedures an LLM will follow* is a supply-chain surface.

**The property.**

```
skills.source = https://fluxtion-playground.dev/skills     (default, canonical)
             | https://internal.example/fluxtion-skills    (a corporate mirror)
             | file:/opt/fluxtion/skills                   (air-gapped)
             | none                                        (write no skills)
```

**Machine tier only — and this is the load-bearing rule.** It is settable from the user's own settings,
an environment variable, or a system property. It is **NEVER** read from a project profile, because a
profile is the one file in this system designed to TRAVEL between people (M38's entire purpose). A shared
profile that could set your skills URL would let a colleague's file redirect the instructions your agent
reads. `KnownKeys` must therefore keep this key out of the profile tier, and a test should assert that a
profile carrying `skills.source` has it ignored, with the reason surfaced.

**Provenance, again, because it is the same problem.** Whatever URL was used is recorded in the project
and shown: *"skills from https://internal.example/fluxtion-skills"*. A reader must be able to tell the
canonical set from an internal mirror without diffing files, and an auditor must be able to tell later.

**Blast radius, stated honestly.** Even a hostile source cannot make the analyser DO anything: it never
executes a runbook or a skill (D-C2, D-AI4), and a pointer's contents are never served to an agent with
the analyser's authority. The exposure is that an agent may READ a procedure and choose to follow it with
its own tools — real, bounded, and the reason the source is machine-tier and its provenance is stated.

**`none` must be a first-class value, not an omission.** An air-gapped or policy-controlled site needs a
way to say "write no skills" that is distinguishable from "the fetch failed".

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
  expands only `~`-prefixed paths; the fix (resolve relative roots against the project root for the
  canonical profile — **M19.2**, corrected by **M35.10**) gates the tutorial's "zero manual setup" claim.
