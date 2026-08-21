# Agent-Brokered Dev Loop — an alternative to M18 (Design Spec)

_Status: **PROPOSED — ALTERNATIVE, FOR ASSESSMENT.** Written 2026-08-21 to be assessed against
[spec-closed-loop.md](spec-closed-loop.md) Part B (M18) by a second reviewer. **Not accepted.** The
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

**The directory is the registry.** Nobody owns it. The agent globs it. Properties that fall out
rather than being designed in: N servers cost nothing; a dead server is a stale file with a `pid` to
check; the binding survives the agent session that created it; and the discovery code on both ends
is a file read.

### C2 · The template catalogue — a static JSON on the playground

Friction at the front is the other half. A developer with no Mongoose server has nothing to link to,
and M19.1's bundle is a full Maven project the agent should be able to find, check and instantiate
without a human reading a tutorial first.

Proposal: a **static, versioned JSON** served from the playground site — data, not an API.

```json
{
  "catalogue": 1,
  "generatedAt": "2026-08-21T00:00:00Z",
  "templates": [{
    "id": "market-maker-quickstart",
    "title": "Market maker — quickstart",
    "summary": "Two-sided quoting with a risk monitor. 24 nodes, audit on, admin REST on.",
    "tags": ["trading", "quickstart"],
    "nodes": 24,
    "download": "https://fluxtion-playground.dev/templates/market-maker-quickstart.zip",
    "repo": "https://github.com/telaminai/fluxtion-playground/tree/main/templates/market-maker-quickstart",
    "requires": { "java": "21+", "maven": "3.9+", "compilerKey": true },
    "provides": { "auditLog": true, "adminRest": true, "graphml": true },
    "run": "mvn package && java -jar target/market-maker-quickstart.jar",
    "analyser": {
      "graphml":    "src/main/resources/com/example/generated/MarketMaker.graphml",
      "sourceRoot": "src/main/java",
      "logHint":    "the server publishes ~/.mongoose/servers/market-maker on start"
    },
    "agentBootstrap": "CLAUDE.md"
  }]
}
```

Why each part earns its place:

- **`requires`** lets the agent check preconditions *before* spending the user's time — the
  `compilerKey` flag in particular, since a missing subscription key fails late and confusingly.
- **`analyser`** makes wiring mechanical: the agent knows the graphml path and source root without
  guessing, so `open {graphml}` + `source_root {add}` need no search.
- **`agentBootstrap`** points at M19.1's layered prompt stack rather than duplicating it.
- **Static file, not an endpoint** — cacheable, diffable, reviewable in a PR, works offline once
  fetched, and cannot develop a version-negotiation protocol by accident.

**D-B4 (below) governs its evolution**, because a published JSON that agents parse is a contract with
the same rule-6 hazard as any other — in the other direction.

### C3 · The loop, end to end

```
1  agent GETs the catalogue, checks `requires` against the machine
2  agent instantiates the template (download/clone), builds it
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
6. **Consistency with the direction already chosen.** M34 exists to *remove* Fluxtion-specific
   knowledge from the analyser so it can serve foreign engines. M18 *adds* Mongoose-specific
   knowledge in the same quarter. **Those pull in opposite directions**, and this is the strongest
   argument in the document: shipping both as specced would mean arguing, in the same release, that
   the analyser should be engine-agnostic and that it should know one server's REST API.

### Where it is not better, and one where it is worse

7. **Total system complexity does not drop — it moves, and it fragments.** Someone still writes the
   MCP tool, the endpoint publication, and the catalogue. That is **three repos** (analyser,
   mongoose, playground) with three cadences, coordinated by a very small team. M18 was one repo.
   *This is the honest cost and it should not be waved away.*
8. **A hard agent dependency.** M18 degraded to "a human clicks a button". This does not degrade —
   no agent, no loop. Mitigated by scope (D-B6) and by the fact that a developer already needs a
   subscribed compiler key, so no new barrier is introduced. But it is a bet on availability, cost
   and rate limits that M18 did not make.
9. **It creates the provenance defect** (§E). M18's analyser knew which server it linked to; this one
   does not, so honesty has to be re-added deliberately. A design that *creates* a correctness hole
   and then patches it is weaker than one that never had it — even when the patch is one field.
10. **The ops journal survives only if D-B5 is honoured**, and it lives in a repo the analyser team
    does not control. That is a real transfer of risk.

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

## G — open questions for the assessor

- **Q-B1** Is D-B6 (developer-only, no no-agent path) safe? It is the assumption most likely to be
  regretted. Does M18.2 in particular — *point the analyser at your running system* — deserve to
  survive in the analyser purely as an onboarding affordance, at the cost of re-acquiring the auth
  problem (O3)?
- **Q-B2** Who owns `~/.mongoose/servers/`? It is proposed here but must be implemented in the
  mongoose repo. Is that an `upstream-asks.md` item, and is it acceptable to depend on another
  repo's release for the whole loop to function?
- **Q-B3** Does the catalogue belong on the playground site or in the playground **repo** (raw
  GitHub)? The site is nicer; the repo is diffable and versioned for free.
- **Q-B4** Should `provenance` be free text (proposed) or structured (`{server, url, home, exportedAt}`)?
  Free text renders anywhere and never lies; structured enables the M33 mismatch comparison to
  reason about *systems* rather than strings. The spec chose free text for honesty; the assessor
  should push back if the comparison matters more.
- **Q-B5** Does anything here weaken the M12 flywheel, whose guardrail (evidence-linked PRs, never
  direct edits) is unchanged but now sits alongside an agent that can also restart the server?

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
