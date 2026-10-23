# Fluxtion philosophy prompt packs (draft)

Start-page prompt packs that help someone get to grips with Fluxtion as a way of writing code — in short,
"what on earth is Fluxtion, as a code philosophy?" — and then with the places a Fluxtion processor lives: its
audit log and the evidence made from it, a Mongoose server, an application it is embedded in, the C++ preview, and
Mongoose plugins. They are drafts: most teaching examples are placeholders until a published example exists (see
*The teaching example* below), and the gaps the sources leave are listed under *Needs owner input*.

## Intent and audience

| Pack | Title | For | What it should leave the person with |
|---|---|---|---|
| 01-basic.md | What is Fluxtion? | A Java developer who has never used Fluxtion | **Moved** to the docs site (M79-7): [`docs/site/start/packs/what-is-fluxtion.md`](../../../site/start/packs/what-is-fluxtion.md) |
| 02-intermediate.md | Local knowledge, global behaviour | Someone who has run a Fluxtion processor and read its records | **Moved** to the docs site (M79-7): [`docs/site/start/packs/local-knowledge-global-behaviour.md`](../../../site/start/packs/local-knowledge-global-behaviour.md) |
| [03-advanced.md](03-advanced.md) | Thinking in Fluxtion | Someone designing a graph, or reviewing one | The closed-world build step and its assumptions; where the model stops (hidden shared state, undeclared dependencies, side effects, threads, mutation from outside); determinism and replay; composing components they did not write. |
| 04-audit-and-evidence.md | Audit logs and evidence | Anyone reading a log in the analyser, or handed one | **Moved** to the docs site (M79-7): [`docs/site/start/packs/audit-logs-and-evidence.md`](../../../site/start/packs/audit-logs-and-evidence.md) |
| [05-deploy-mongoose.md](05-deploy-mongoose.md) | Running it in Mongoose | Someone taking a processor to a Mongoose server | The Fluxtion / Mongoose split; how configuration composes an app; how events reach the processor and on which thread; server and processor lifecycle; sinks and services; getting the audit log to the analyser; observing a running server. |
| [06-deploy-embedded.md](06-deploy-embedded.md) | Embedding the processor | Someone putting the generated processor straight into their own application | Embedded against hosted; creating the processor by name; wiring the event input and its thread; the lifecycle in order; reusing an instance; testing; audit output; changing a node from outside safely. |
| [07-cpp-preview.md](07-cpp-preview.md) | C++ generation (preview) | Someone curious about, or evaluating, the C++ target | That it is a **preview** and evolving: what the path takes and produces, what the supported subset is *today* (asked, never assumed), what is tested against Java, its audit output, and what to check before depending on it. |
| [08-mongoose-plugins.md](08-mongoose-plugins.md) | Mongoose plugins | Someone extending a Mongoose server | What a plugin is against a graph node; connectors, services and libraries; a tour of the reference repository's file connector; how a service reaches a processor; plugin threads against processor threads; which server version the reference targets; adding one; getting audit records out through `svc-admin-web`; writing one. |

The prompts are written for the **person** to send, in their own voice. They are not instructions to the analyser.

**The deployment packs (05, 06, 08) explain; they never deploy.** A standing design decision holds that the analyser
never deploys or controls a server — no server verbs on its action socket; server control is a Mongoose-side tool.
So no prompt asks the analyser or its assistant to deploy, start, stop or reconfigure anything: each explains, points
at the person's own tools and docs, and several say "don't start or change any server" outright. The one prompt that
asks the analyser to *write* something (04/7, an evidence bundle) asks it to explain first and to ask before writing.

## How to use them

1. Open the analyser's start page.
2. Drop one pack file onto it. The analyser's prompt-pack parser checks the file; a pack it refuses is named with
   the line and the cause.
3. Entry 1 of each pack is the teaching example. Download it (once it is pinned — see below), build and run it so
   there is an audit log, its graph and its generated source on screen.
4. Choose an entry and send it. The entries run roughly in order, but each stands on its own.

Check a pack before dropping it, with no analyser built:

```
python3 tools/m74_onboarding_runner.py lint-pack docs/proposals/packs/fluxtion-philosophy/03-advanced.md
```

It prints the parsed pack, or one refusal with its line and cause (the runner's mirror of the Java parser).

## The teaching example each pack expects

Packs 01–04 and 06 assume the same tiny project, so the answers can point at something real:

- **A four-node diamond: A → B, A → C, B → D, C → D.** A handles the event with `@OnEventHandler`; B, C and D are
  `@OnTrigger` nodes that take their parents through their constructors.
- **Change-propagating, stateful nodes.** Each node keeps a little state (a running value or a count) and returns
  `true` only when that state changed, so some events stop part-way and the returned boolean is visible.
- **Every node writes audit output** (`auditLog.info(...)`, with event audit enabled in the builder), because a node
  that writes nothing does not appear in a record and its silence proves nothing.
- **Built by a `FluxtionGraphBuilder`** with `setWriteSourceToFile(true)` and `setWriteGraphMlToFile(true)`, so the
  generated processor and its graph are on disk next to the log — the shape of the golden path.
- **A short event script** that includes a first event, an event that changes only one side of the diamond, and an
  event that changes nothing.

For 06 the same project serves, with a `Main` that loads the generated processor by name and a JUnit test of the
golden-path shape. The others:

- **05 (Mongoose)** — the diamond hosted in a Mongoose server: a YAML configuration with one in-memory or file feed,
  one processor agent, a file sink, and the audit log configured to a `---`-separated text file.
- **07 (C++)** — the diamond generated for the C++ target, with the author-written parts (node bodies, driver) beside
  the generated header, and a binary audit log the analyser can open.
- **08 (plugins)** — the reference repository itself, `telaminai/mongoose-plugins`, **pinned** to its `main` commit
  `ace31f301bfb8b2e035c035a25b8856f9111c873` (2026-09-24, just after its `v1.0.45` release), the one read for this
  draft (2026-10-17). It is the only real pin. The repository has no scheduler module (Mongoose's scheduler is a
  built-in service), so 08's tour uses `connector-file`, the plainest source-and-sink pair.

**The diamond is published**: `telaminai/fluxtion-diamond`, pinned at commit `dad9b43…` (commit `d1e38e2b` here,
2026-10-18). Entry 1 of packs 01–04 and 06 names that archive. Its `sample/diamond-audit.yaml` is byte-identical to
the generic run at the first minute's pin `24d6d74` (`docs/specs/spec-first-minute.md` §6.4), and
`PhilosophyPacksTest` fails if the two references drift apart. Entry 1 of 07 is still a clearly marked
**PLACEHOLDER** (owner `example`, an all-zero commit): replace it with the published project's
`…/archive/<40-hex commit>.zip` URL (and, ideally, its `sha256:`), then re-run the lint.

## Toolchain observations

Facts observed from builds rather than read in a doc. They constrain how the prompts may be phrased:

- **`addEventAudit(LogLevel.INFO)` generates traced records under the pinned toolchain.** The pinned toolchain is
  `fluxtion-runtime` 1.1.0, `fluxtion-builder` 1.0.76, `fluxtion-maven-plugin` 1.3.0 and hosted generator 1.0.79.
  The generated processor sets `eventLogger.trace = true` and `traceLevel = INFO`, so every entry carries
  `method` and `annotation` and every event gets a record. `addEventAudit()` with no level generates
  `trace = false` and `traceLevel = NONE`. This was observed twice: in the built diamond (`demos/fluxtion-diamond`),
  and in a one-node check built for item 8 below (2026-10-17). The fixture generator's README says the same. So
  04/3 (INFO against TRACE) must stay a question checked against the records, never an assertion that INFO records
  only nodes that logged.
- **In the built diamond, C = 3x changes whenever x does**, so no cycle has B changed and C unchanged. On x = 3, C
  changed while B = floor(x/2) stayed 1, and D still ran, reading B's kept value. 02/4 therefore asks for "one
  branch changed and the other did not" and does not name the branch.

## The grounding rule

Every answer should come from these places, and say which:

- **`claude.txt`** — the Fluxtion framework reference (fluxtion repo, `docs/claude.txt`), authoritative on semantics;
- **the golden path** — the playground's blessed shape for an audited AOT graph;
- **the topic's own docs** — the Fluxtion repo's how-to and reference pages, this analyser's docs site (the audit-log
  format specification, evidence bundles), the Mongoose docs, and for 08 the reference repository's code
  (RATIONALE.md lists each);
- **what is on screen** — the audit records, the graph and the generated source of the loaded project.

Where those do not settle a question, the answer should say so rather than fill the gap from general knowledge.
Every prompt ends by asking for that ("tell me if you can't tell from what's here"). The questions most likely to
tempt a confident guess — how often the diamond's downstream node runs, what happens when only one side changes,
first-event behaviour, what a silent node means — are deliberately phrased as "check it against the records".

## Needs owner input

Where no authoritative public source states something, the prompt asks rather than asserts, and the gap is here:

1. **The teaching projects.** Stale for 01–04 and 06, which name the published diamond (above). Still open for 07,
   whose placeholder needs a published, pinned project of the shape described above.
2. **C++ supported subset (07/3).** No public page states which annotations, node shapes, event types, services or DSL
   features the C++ target supports. The public material (FLXA format page, performance pages, the compiler config's
   `targetId`, this repo's latency-kit README) says only that a C++ generator and header-only runtime exist, that the
   C++ audit writer is held to Java by parity tests rather than the conformance corpus, and that several items are
   "deferred to the C++ round". **A public "C++ target: what works today" page is now open for review as
   [telaminai/fluxtion#44](https://github.com/telaminai/fluxtion/pull/44)** (`docs/reference/cpp-target.md`, under
   *Reference guide*). It describes compiler 1.0.79, and every statement in it was checked against that release's
   source and C++ test suite. Until it merges, 07/3 stays a question. Once it merges, 07/3 can cite it.
3. **C++ availability (07/7).** The public README builds against a locally installed `fluxtion-generator-cpp`. On
   2026-10-17 neither it nor `fluxtion-serialiser-cpp` resolves from the public Repsy repository or from Maven
   Central, because the compiler release deploys them to the private repository. The page in #44 states what
   follows from that. Local C++ generation is not public. The remote route needs only the public `fluxtion-builder`
   and `targetId` `"cpp"`, but it returns only the processor source, not the header-only runtime. Whether the hosted
   service answers `"cpp"`, and how a public user gets the runtime headers, are owner-to-confirm items in the PR.
4. **The plugins reference still disagrees with itself on versions (08/6).** `telaminai/mongoose-plugins`' parent
   `pom.xml` builds `1.0.46-SNAPSHOT` against Mongoose `1.0.29` and the Fluxtion BOM `1.0.67`, while its root
   README says `0.2.8-SNAPSHOT`, Mongoose `1.0.8` and Fluxtion `0.9.33`. Several module READMEs link sources under
   an older package path, and the parent pom's `<url>` still names a predecessor repository. 08/6 asks the
   assistant to read the pom and say where they disagree. Whether the repository fixes its READMEs is its owner's
   call.
5. **The plugins reference carries employer-domain addresses.** On 2026-10-17, the SPDX copyright headers of 85 files
   at the pinned commit carry an address on the fourth sweep term's domain. Its git history has authors on both the
   third and the fourth sweep terms' domains. The archive zip that entry 1 downloads has the headers but no
   history. Nothing here copies them, but a capture or transcript of a session on that repository would show them
   (CLAUDE.md rule 1). Decide before using 08 in any recorded run.
6. **Mongoose audit capture: the open steps.** The docs page *From a Mongoose server to the analyser* now covers the
   end-to-end path, from Mongoose and mongoose-plugins source. Its *To confirm* notes need the owner or the Mongoose
   owner:
    - whether Mongoose installs the audit auditor for a `customHandler`;
    - whether the attach-then-set-level order emits a control record before `init()`;
    - the thread-safety of one listener shared across agent groups;
    - the authentication and support status of `svc-admin-web`'s `/api/audit/...` routes.

### Resolved (owner answers, 2026-10-17)

- **The plugins reference repository (was 4).** Retargeted to `telaminai/mongoose-plugins`, pinned as above.
  Entries 3–9 were rewritten against what it contains: `connector-file`, `svc-cache`, `svc-admin-web`'s audit
  export and `mongoose-test-support`. RATIONALE ▸ *PL* lists every file read.
- **Mongoose audit configuration (was 6).** Drafted as the docs page `docs/site/mongoose-audit-to-file.md`
  (*The audit log ▸ From a Mongoose server to the analyser*). It covers the level (per-handler and per-group
  `logLevel`, `INFO` by default, `setAuditLogLevel` at runtime) and two routes. The first is your own
  `LogRecordListener` passed to `bootServer`. The second is `performanceMonitoring.auditCapture` exported by
  `svc-admin-web` as `---`-separated YAML. The steps still open are item 6 above. RATIONALE 05/7 now cites it.
- **Mongoose deployment guide (was 7).** The owner says the guide's **clustering, state replication and
  distributed tracing** sections are **aspirational**: they describe nothing Mongoose supports today. **Pack 05 must
  not teach them.** None of its entries asks about high availability or tracing, and entry 8 asks only about the
  admin commands, performance counters and audit log. The guide lives in **`telaminai/mongoose`**, at
  `docs/architecture/deployment.md`, under *High Availability ▸ Clustering* and *▸ State Replication*, and
  *Monitoring and Observability ▸ Distributed Tracing*. This repo does not edit it.

  *Suggested change, for the owner to apply there:* in `docs/architecture/deployment.md`, remove the
  *Clustering*, *State Replication* and *Distributed Tracing* subsections. Alternatively, move them under one new
  heading, *Not yet supported*, opened by a warning admonition such as "Mongoose does not provide clustering,
  state replication or distributed tracing today; these are approaches you would build outside the server", so
  that no reader or LLM takes them for features. The neighbouring *Failover* subsection has the same shape: generic
  advice with no Mongoose API behind it. Decide whether it belongs under the same heading. Keep *Logging*, which
  shows a real call (`setAuditLogLevel`), and consider linking it to the analyser's *From a Mongoose server to the
  analyser* page for the file sink.
- **`@ExportService` with a non-void method (was 8).** Settled by a build. Neither doc was right: generation fails
  with **FLX-1002** during the plugin's `scan` goal, before javac, and `boolean` is exempt. This was observed with
  `fluxtion-runtime` 1.1.0, `fluxtion-builder` 1.0.76, `fluxtion-maven-plugin` 1.3.0 and hosted generator 1.0.79.
  `sep-lifecycle.md`'s "dropped silently" is wrong. `claude.txt` reaches the right conclusion, but it quotes a javac
  error that is not the one shown, and it omits the `boolean` exemption. Filed as
  [telaminai/fluxtion#43](https://github.com/telaminai/fluxtion/issues/43). No pack depends on it.

[RATIONALE.md](RATIONALE.md) lists, for every entry, the concept it teaches and the section of `claude.txt` or the
golden path it rests on.
