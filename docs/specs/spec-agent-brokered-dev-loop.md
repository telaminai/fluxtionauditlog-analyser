# Agent-Brokered Dev Loop — an alternative to M18 (Design Spec)

_Status: **ACCEPTED v2 — 2026-08-22.** Written 2026-08-21 as an alternative to
[spec-closed-loop.md](spec-closed-loop.md) Part B (M18); assessed and **adopted** in
[review_spec_agent_brokered_dev_loop.txt](../handoff/review_spec_agent_brokered_dev_loop.txt), whose
amendments are folded in below and marked **(review)**. M18 as specced is closed in favour of this
document. The
question this document exists to answer is not "is this nice" but **"is this materially better than
M18, and what does it cost?"** §F answers that in the author's own words, including where it is not
better; the assessor should attack §F first._

_Scope constraint set by the owner, and it does most of the work: **this is a developer-only
workflow.** Not production support. An agent is assumed present. If that assumption is wrong, most
of this spec is wrong with it — see D-B6._

---

## The proposition

M18 has the analyser learn Mongoose's admin REST: link, auth, log discovery, audit-level control
with capture-and-restore, dev restart, source/graphml fetch. Six slices of foreign-API integration
inside a released desktop application.

**The alternative: the analyser learns nothing. The agent brokers.**

Claude discovers or deploys Mongoose servers, exports the log and graph, drops them on disk, and
drives the analyser through verbs that **already shipped** — `open {log, graphml}`,
`source_root {add}`. Mongoose exposes its own admin surface as an MCP tool in its own repo, where
its version churn belongs. A playground-hosted **template catalogue** removes the setup friction at
the front.

The analyser gains exactly one thing (§E), and it is a thing this design *breaks* rather than needs.

## The principle it is an instance of

The same rule settled three times already, applied one layer out:

- **M29** — the analyser never learns FIX; an agent adapts foreign data into `(timestamp, value)`.
- **M31** — a plugin can only be a **reader**; it cannot add verbs.
- **M11 (2026-08-20)** — the analyser emits a neutral promotion manifest; **the agent renders the
  Grafana JSON**, because a versioned foreign schema is a permanent maintenance tax on a hermetic
  core.

Mongoose's admin REST is a versioned foreign API. M18 is the fourth instance of the same question,
and M11's reasoning applies unchanged: *if it is wrong to teach the loader FIX on the way in, and
wrong to teach it Grafana on the way out, it is wrong to teach it Mongoose in the middle.*

**Corroborating evidence, from M18's own spike:** M18.0 found the local checkout stale and **two of
its three identified gaps already closed upstream**. The surface moved between the spec being
written and the spike being run. It will move again, and a released fatjar cannot move with it.

---

## A — what changes, slice by slice

The assessor's first table. "Where" is the only column that matters.

| M18 slice | Today's plan | Under this proposal | Where the work goes |
|---|---|---|---|
| **18.1** Link + status | Settings UI, admin base URL, loopback enforcement, auth (`authMode`, `POST /api/session/login`), status chip | **deleted** | endpoint files (§C1) |
| **18.2** Log discovery | `GET /api/audit/files` → pick → `export?format=yaml` → open | **deleted** | agent fetches, writes to disk, calls `open {log}` |
| **18.3** Audit level + capture-and-restore | Analyser raises/lowers level, records baseline, restores on exit | **moved** | Mongoose MCP tool |
| **18.3a** Missing `GET` companion for audit level | Blocking decision for the analyser | **dissolved** | the server's problem, in the server's repo |
| **18.4** Dev restart | Analyser stop/start, dev opt-in flag, confirm dialog with live log context | **moved** | Mongoose MCP tool; the agent composes context from *both* sides |
| **18.4a** Why `start`/`stop` are commented out | Blocking ask before the analyser can restart | **dissolved** | same |
| **18.6** Source + graphml from server | `GET /api/source?fqn=`, `/graphml` | **deleted** | agent fetches, `open {graphml}` + `source_root {add}` |
| **O3** admin auth beyond localhost | Needed from day one in the analyser | **dissolved** for the analyser | the MCP tool's concern |
| — | — | **NEW: provenance** (§E) | the only analyser-side slice |

**Net analyser change: six slices become one, and the one is a single field.**

## B — it does not conflict with the standing decision; it strengthens it

Tracker ▸ Decisions records: *"Server control is not an assistant capability (spec-closed-loop §B.5)
— server verbs never appear on the action socket; any future agent-initiated server action requires
per-action human approval."*

That decision constrains **the analyser's** socket, protecting the FAQ guarantee that the analyser
touches nothing outside the loaded log. Under this proposal the agent talks to **Mongoose directly**;
the analyser never acquires server-mutating code at all. The guarantee stops being carefully scoped
and becomes **literally true** — there is nothing to scope.

The second clause survives intact and improves: per-action human approval is **native to MCP
clients**, which prompt per tool call. M18 planned to hand-build that as Swing confirm dialogs plus a
dev-opt-in flag plus an ops journal. Two of those three come free (D-B5 covers the third, which does
not).

---

## C — the mechanism

### C1 · The registry is a directory of endpoint files, owned by nobody

**Do not put the server registry in the analyser** (it re-acquires the coupling this spec removes)
**and do not put it in MCP client config** (which is static — a server deployed mid-session cannot
register itself there).

Mirror the mechanism the analyser already ships and that is proven in daily use. The analyser
publishes, mode 600:

```
~/.fluxtion-analyser/rest-endpoint
{"url":"http://127.0.0.1:62897","token":"…","pid":26937,"startedAt":"2026-08-20T21:27:16Z"}
```

Every capture and drive script in `tools/` finds the app this way, with no configuration.

**Symmetric proposal — each Mongoose home publishes its own:**

```
~/.mongoose/servers/<name>          (mode 600)
{
  "name":       "risk-engine",
  "home":       "/Users/dev/work/risk-engine",
  "url":        "http://127.0.0.1:8081",
  "token":      "…",
  "authMode":   "TOKEN",
  "pid":        41221,
  "startedAt":  "2026-08-21T09:14:02Z",
  "processors": [{"group":"main","name":"RiskProcessor","graphml":"/api/processors/main/RiskProcessor/graphml"}]
}
```

**Trust model, stated because these tokens are not like the analyser's (review F5).** The files are
mode 600 and readable by any process running as that user — the same posture as
`~/.fluxtion-analyser/rest-endpoint`, with one difference that matters: the analyser's token gates
*render* verbs, these gate *restarts*. The boundary is therefore the local user account, and it is
only sufficient because D-B7 puts the real refusal server-side — a non-dev deployment declines admin
control regardless of who holds the file.

**The directory is the registry.** Nobody owns it. The agent globs it. Properties that fall out
rather than being designed in: N servers cost nothing; a dead server is a stale file with a `pid` to
check; the binding survives the agent session that created it; and the discovery code on both ends
is a file read.

### C2 · The template catalogue — it already exists; this is an additive ask

**Corrected 2026-08-21 after reading the live site (rule 6).** An earlier draft of this section
invented a catalogue format. It did not need to: the playground already publishes one, and the real
shape is better than the invention because it separates *listing* from *generation*.

**The index — `https://fluxtion-playground.dev/starter-templates/index.json`:**

```json
{"templates": [
  {"name": "Fluxtion embedded",
   "description": "The graph inline in your app — flow.onEvent(...), no server. Interpreted + keyless; the fastest way to feel the model in the Playground.",
   "file": "fluxtion-embedded.starter.json",
   "type": "fluxtion",
   "mode": "interpreted"}
]}
```

Fields: `name`, `description`, `file`, `type`, `mode`, and `tags` on some entries. Referenced from
[`/build-with-ai`](https://fluxtion-playground.dev/build-with-ai) as *"the template menu tells the
model which shape to pick and whether it needs a key"* — so **it is already designed to be read by
an agent**, which is the premise of this spec.

**Each `file` is a generator config, not a downloadable project** —
`/starter-templates/fluxtion-embedded.starter.json`:

```
build "maven" · group · artifact · version · javaRelease 21 · basePackage · versions {} · type {…}
fluxtion { processorName, nodeClassName, builderStyle, compileMode, auditLogging: true,
           addTestShell: true, events: [...] }
```

It is input to [`/start`](https://fluxtion-playground.dev/start), which emits the Maven skeleton.
That is a better factoring than a zip: the template is a *shape*, and the project is generated to
the user's package and artifact names.

**Two things it already gives the dev loop for free, which the invented version duplicated:**

- **`mode` is the key indicator.** `interpreted` is keyless and runs in the Playground; anything else
  is AOT and needs a subscribed compiler key. An invented `requires.compilerKey` flag would have
  been a second source of truth for a fact already encoded.
- **`fluxtion.auditLogging`** is already a template concern, already `true`. The audit log — the
  thing this entire product depends on existing — is on by default at the front door.

**What the dev loop actually needs added — TWO fields, not five (review §2).** An earlier draft of
this table listed five. The assessor did what Q-B6 asked and read the live catalogue, and three of
them were already answered — the same mistake this section's own correction avoided for `mode`, made
again one paragraph later:

| Was asked for | Verdict after reading the live source |
|---|---|
| `mongoose.adminRest` | **Withdrawn.** `type` already admits **`mongoose`** (3 entries) and **`hosted`** (2), and the starters carry a **`web-admin`** tag; the generated config declares `mongoose.services.adminWebService` (WebAdminService, host, listenPort) plus `serverAdmin` and `adminCommandRegistry`. A boolean would be a **second source of truth** for a fact the services block and the tag already encode. |
| `analyser.sourceRoot` | **Withdrawn.** Already declared: `adminWebService.config.sourceRoots: ["src/main/java"]`. |
| `run` | **Withdrawn.** `mongoose.addRunScript: true` — the command is a generated-project artifact, not a catalogue fact. |
| `analyser.graphml` | **Moved, not withdrawn.** Genuinely absent from the starter — but §C1's registry file already carries `processors[].graphml`, which is the better home: it is a **runtime** fact about a running server, not a template fact. The catalogue ask dissolves into the registry design. |
| `agentBootstrap` | **STANDS.** The M19.1 `CLAUDE.md`-stack pointer has no equivalent. |
| `catalogue` version integer | **STANDS, and is newly urgent (review F6).** D-B4 below governs additive evolution of a version field that **does not exist** in the live index — the only top-level key is `templates`. The rule is right and currently governs nothing. Adding `catalogue: 1` is part of the adoption. |

**Convergence worth noting (review §2).** The Mongoose starters set `auditBackend: "chronicle"`,
which confirms why §C3 step 5 exports YAML. It also means the step is **temporary**: once an
out-of-tree Chronicle reader exists (M31.4r's sibling), the agent opens `./audit` directly through
the reader SPI and step 5 disappears entirely. This design gets *simpler* over time through a
mechanism the analyser already shipped. M18 could never have inherited that.

**Q-B6 — ANSWERED (review §2), and the answer was "mostly yes".** `type` already admits `mongoose`
and `hosted`. The lesson generalises: this section was twice written from assumption and twice
corrected by reading, which is rule 6 arriving through the front door of a spec that cites it.

### C3 · The loop, end to end

```
1  agent GETs /starter-templates/index.json, picks a shape; `mode` says whether a key is needed
2  agent feeds the starter config to /start, gets a Maven project, builds it
3  server starts, publishes ~/.mongoose/servers/<name>
4  agent globs the registry, picks the server
5  agent exports:  GET /api/audit/file/{id}/export?format=yaml   -> /tmp/<name>-<ts>.yaml
                   GET /api/processors/{g}/{n}/graphml           -> /tmp/<name>-<ts>.graphml
6  agent drives:   open {log, graphml, provenance}   source_root {add}      <- SHIPPED VERBS
7  human + agent investigate in the analyser (series, topology, coverage, report)
8  agent proposes an evidence-linked PR (M12 guardrail, unchanged)
9  agent restarts via the Mongoose MCP tool, human approves the call
10 agent re-exports and re-opens; Follow verifies on the fresh log
```

Steps 6 and 7 need **no new analyser code except the `provenance` field**. Everything else is
outside the analyser.

---

## D — decisions

- **D-B1 · The analyser acquires no Mongoose knowledge.** No base URL, no auth, no endpoint paths,
  no version assumptions. It opens files. *Rationale: M11's, unchanged — a versioned foreign schema
  is a permanent tax on a hermetic core, and M18.0 already caught the surface moving.*
- **D-B2 · The registry is a directory of endpoint files, not a service and not app config.**
  Mirrors `~/.fluxtion-analyser/rest-endpoint`, which is proven. *Rationale: it must absorb a server
  deployed mid-session, survive the agent session, and cost nothing per server.*
- **D-B3 · Every server mutation is an MCP tool call on the Mongoose side**, approved per call by the
  MCP client. *Rationale: preserves the standing decision's second clause using the transport's own
  permission model rather than a hand-built one.*
- **D-B4 · The template catalogue is additive-only within a `catalogue` version.** Fields may be
  added; none removed or retyped. A breaking change increments the integer and the old file stays
  served. *Rationale: agents parse it, and this is rule 6 pointed outward — we would be the party
  shipping the breaking revision.*
- **D-B5 · The ops journal does not disappear; it moves server-side.** Every mutation the MCP tool
  performs is journaled by **Mongoose**, not by the agent transcript. *Rationale: "who restarted
  this and why" is an audit question. A chat log is not an audit trail, and this design would
  otherwise silently delete a capability M18 had.*
  **Tier, settled (review F4):** the **free dev MCP journals to the server's own log** — cheap,
  local, no retention promise; the **paid production MCP journals durably** with retention and
  export, which is part of what is bought (D-B7). Saying "every mutation is journaled" without
  saying by whom left a requirement funded on one tier and unfunded on the other.
- **D-B7 · The dev MCP is free; production control is a paid MCP — and the line is enforced by the
  SERVER, not by the tool.** _(owner decision, 2026-08-21)_ This resolves the enforcement problem a
  source-available analyser cannot solve on its own: a licence check inside a readable client is an
  `if` statement anyone can delete, but a **server that refuses admin control outside dev mode
  without a licence token** has nothing to patch on the customer's side. Consequences the assessor
  should test:
  - **The free/paid boundary must be self-declared by the deployment, not by the client.** Otherwise
    the free dev MCP can simply be pointed at production and the line evaporates. Mongoose knows
    whether it is a dev instance; the client does not, and cannot be trusted to.
  - **It fits the funnel rather than gating it.** Dev is where evaluation happens and stays free;
    production is where incidents cost money and budget exists. Contrast the current analyser
    licence, where *processing real operational data* — the only convincing evaluation of a forensics
    tool — is Production Use.
  - **It gives D-B5 a home.** A paid production MCP is a service, so journaling every mutation
    server-side stops being an unfunded requirement and becomes part of what is bought.
  - **Open:** what makes a deployment "production"? Non-loopback is the obvious signal and is
    probably too crude — a developer on a remote box is not production. A declared environment in
    the server's own config is more honest and matches D-A2's declared-never-inferred rule.

- **D-B8 · Export before you restart. Never the reverse.** _(review Q-B5)_ A restart can roll or
  truncate the very log that is the evidence. §C3 happens to order correctly — export at step 5,
  restart at step 9 — and that must be a **rule the agent is told**, not a property of the example
  it was shown. The MCP tool's restart description says so in its own words, because the agent reads
  the tool, not this spec.

- **D-B6 · Developer-only is a stated precondition, not an assumption to discover.** There is no
  no-agent path. If production support ever needs one, it is a **different design**, and this spec
  is not it. *Rationale: recorded so the constraint is falsifiable rather than implicit — this is the
  single assumption most likely to be wrong later.*

---

## E — the only analyser-side slice, and this design creates the need for it

**One analyser, many Mongoose homes, an agent swapping logs between them.** Today
`LogFingerprint` carries `logName`, which is a **file name**. An agent exporting to
`/tmp/export-1.yaml` produces:

- a status bar reading `export-1.yaml`
- an M33 report headed *"written against export-1.yaml · 4,206 records"*
- a PDF and screenshots carrying the same

The human cannot tell which of three systems they are looking at, and the artefact they carry to a
meeting cannot either. **That is exactly the defect class this product exists to prevent** — the M33
review of 2026-08-20 caught two instances, one of which was a fingerprint banner naming the wrong
log. This design would create a third, structurally, on every multi-server session.

**Slice: `provenance`.**

- `open {log, graphml, provenance: "risk-engine · localhost:8081 · ~/dev/risk · exported 09:14Z"}`
- stored on `LogFingerprint` beside `logName`; free text, supplied by whoever opened the log
- rendered wherever `logName` is rendered: status bar, `context`, report header, PDF footer,
  screenshot captions
- carried into the M33 mismatch comparison, so *"the loaded log differs"* can name a **system**
  rather than a temp-file name
- **absent means absent** — no invention, no inference from the path (D-A2's rule: declared or
  nothing, and the view says which)
- **the share disclosure row moves in the SAME commit (review §1.9).** A provenance string names
  internal systems — `risk-engine · localhost:8081 · ~/dev/risk`. M33's reports share under their own
  category whose row promises *"definitions + narrative — never log data"*; provenance is
  business-context cargo and the row must say so, enforced by `ShareDisclosureContractTest` exactly
  as the F1 checklist required for reports.
- **Q1's soft banner treats provenance like the NAME, not like content (review §1.9).** Same content
  from a *different system* gets the soft announce naming both. That is §E's whole payoff: without
  it, two servers running the same build are indistinguishable, which is the case the field exists
  for.

Cost: one field, one constructor parameter, its rendering sites, and tests. Compare with M18.1–18.6.

---

## F — is this materially better than M18? The author's own assessment

The assessor should attack this section first.

### Where it is clearly better

1. **Six slices of foreign-API integration become one field.** Not a refactor — a deletion. This is
   the largest single scope reduction available anywhere in the open tracker.
2. **A whole breakage class leaves the released artefact.** M18.0 already observed the admin surface
   moving underneath the spec. A fatjar cannot follow it; an MCP tool in the server's own repo ships
   with it.
3. **The security guarantee becomes literally true.** "Nothing outside the loaded log" stops needing
   a carve-out for the Server menu.
4. **M18 and M19 converge into one build.** The template catalogue + endpoint files are
   simultaneously the onboarding example and the dev workflow. Two roadmap items, one implementation,
   and the tutorial becomes a transcript of the real loop rather than a separate artefact.
5. **Approval and confirmation come free** from the MCP client instead of being hand-built in Swing.
6. **Consistency with the direction already chosen — and this is now STRONGER than first written
   (review §1).** When this spec was drafted the contradiction was between two *specs*. Since then
   **M34.0 ran and passed its gate, and M34.1's ordering slice shipped**. The engine-agnostic
   direction is no longer an accepted plan; it is merged code. Shipping M18 as specced would
   contradict **the codebase**, not a proposal.

### Where it is not better, and one where it is worse

7. **Total system complexity does not drop — it moves, and it fragments.** Someone still writes the
   MCP tool, the endpoint publication, and the catalogue. That is **three repos** (analyser,
   mongoose, playground) with three cadences, coordinated by a very small team. M18 was one repo.
   *This is the honest cost and it should not be waved away.*
   **The hazard inside it, which the first draft missed (review §1):** *failure attribution.* When
   the loop breaks the user experiences "the analyser doesn't work with my server", and the fix may
   live in any of the three. M18's one-repo failure was diagnosable in one place. This is what §H
   exists to bound — and without §H, point 7's risk is unbounded rather than merely honest.
8. **A hard agent dependency.** M18 degraded to "a human clicks a button". This does not degrade —
   no agent, no loop. Mitigated by scope (D-B6) and by the fact that a developer already needs a
   subscribed compiler key, so no new barrier is introduced. But it is a bet on availability, cost
   and rate limits that M18 did not make.
9. **It creates the provenance defect** (§E). M18's analyser knew which server it linked to; this one
   does not, so honesty has to be re-added deliberately. A design that *creates* a correctness hole
   and then patches it is weaker than one that never had it — even when the patch is one field.
10. **The ops journal survives only if D-B5 is honoured**, and it lives in a repo the analyser team
    does not control. That is a real transfer of risk.

11. **It creates a paid surface M18 did not have.** Under D-B7 the production control plane is a
    licensed MCP service — enforceable because it is a server, not a patchable local check. M18's
    control plane lived inside a source-available desktop app, where the same capability could not
    have been charged for at all. That is a strategic gain the slice-count table does not show.

### Verdict

**Materially better for the analyser: yes, decisively.** Six slices to one, and the deleted ones
carry the auth, the version risk and the two blocking upstream questions.

**Materially better for the system as a whole: roughly neutral.** The work moves rather than
vanishing, and it fragments across three repos.

**Materially better strategically: yes** — and this is the deciding argument. Point 6 is not a
preference. Shipping M18 as specced while building M34 would be internally contradictory, and the
contradiction would end up visible in the product.

**Recommendation: adopt, with §E built first** — provenance before any agent starts swapping logs
between servers, so the honesty is in place before the workflow that needs it.

---

## G — it resolves O5, which the first draft left unstated (review §3)

M18.0 raised **O5** as gating: the audit-capture plugin's Phase 2 puts a web audit viewer with graph
replay inside `svc-admin-web`, overlapping this analyser. Under M18 that overlap is a *positioning
problem* — two tools bolted to one server, competing on the same surface, and the tracker resolved it
only by asserting they complement.

Under this proposal the split stops needing an assertion and becomes structural:

- **the server's web viewer is the LIVE surface** — glanceable, in-situ, one running server, its own
  log, no export step;
- **the analyser is the DEEP surface** — fed by exports and adapters, across many logs and many
  systems, where reports, coverage, replay-diff and the agent surface live.

They do not compete because they are not fed by the same thing. **O5 closes with the adoption**, and
its strongest positioning consequence is that the analyser stops being "the other viewer for your
server" and becomes the instrument the server exports *into*.

## H — the conformance harness, named before any cross-repo work starts (review §1.7, Q-B2)

Point 7's failure-attribution hazard is bounded the way this codebase bounds every cross-boundary
contract — M34's D-A6 already made the move for the record format. **The loop is a contract, so it
gets a conformance suite:** a scripted end-to-end of §C3 steps 3–7 (server publishes its endpoint
file → agent globs the registry → exports log and graph → drives the analyser) that runs in CI.

**Home: the M19 bench**, because it is the one place that already needs a real running Mongoose and a
real analyser in the same job. A break then fails in the owning repo rather than in a user's session,
which is the entire difference between three repos being a cost and three repos being a risk.

**Q-B2's dependency on the mongoose repo is acceptable BECAUSE of this, and not otherwise.**

## I — the open questions, all answered (review §4)

- **Q-B1 — ANSWERED (review §4): safe today, but M18.2 is PARKED, not deleted.** The funnel is
  agent-first (the playground's `/build-with-ai` is the front door and the compiler key precedes all
  of this), so D-B6 holds. Delete-forever is more finality than the evidence supports, so M18.2
  carries a stated revival trigger: *revive as an onboarding affordance only if evidence shows
  no-agent developers bouncing off the export step.* Falsifiable, like D-B6 itself. Everything else
  deletes cleanly.
- **Q-B2 — ANSWERED (review §4):** an `upstream-asks.md` item, and the dependency is acceptable
  **because** the loop gets a conformance harness (§H). Without the harness, no.
- **Q-B3** ~~Where should the catalogue live?~~ **Resolved — it already lives at
  `/starter-templates/index.json` and is already agent-facing.** The live question is narrower:
  the additive fields in §C2, and whether `mode` should stay the sole key indicator.
- **Q-B4 — ANSWERED (review §4):** free text for v1 as specced. Structured earns its complexity only
  when someone needs the mismatch line to reason about systems, and nobody does yet. Revisit with
  evidence.
- **Q-B5 — ANSWERED (review §4):** no; it strengthens the restart leg with native per-call approval.
  The one rule it surfaced is now **D-B8**.

## Non-goals

- Production posture, non-loopback, deploy-jar-and-restart — unchanged from M18.5, still deferred.
- Any agent-initiated action without per-call human approval.
- Teaching the analyser any Mongoose endpoint, including read-only ones.

## Acceptance

1. The analyser contains **no** Mongoose URL, endpoint path, auth mode or version assumption.
2. A developer with no server reaches an open log in the analyser via the catalogue, with no manual
   configuration and no tutorial step that requires reading a doc first.
3. Three Mongoose homes can be driven from one analyser in a single session, and at every moment the
   status bar, `context` and any exported report name **which system** the evidence came from.
4. Every server mutation is approved per call and journaled server-side (D-B5).
5. M18.3a and M18.4a are closed as "not the analyser's questions".
6. **O5 is closed** on §G's terms, in the tracker, as part of the adoption.
7. **The conformance harness (§H) has a named home before any cross-repo work begins**, and a
   failing loop fails in CI rather than in a user's session.
