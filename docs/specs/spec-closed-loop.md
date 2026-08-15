# Closed Loop — Agent Fix Handoff & Mongoose Server Link (Design Spec)

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-14

Companion to **[tracker.md](tracker.md)** (milestones **M12** and **M18**),
**[spec-assistant-actions-mcp.md](spec-assistant-actions-mcp.md)** (M13 — the MCP door agents use to
query back mid-fix), and **[completed/spec-assistant-actions.md](completed/spec-assistant-actions.md)**
§13 (the original diagnose → fix → prove design that M12 carries).

The docs-site landing page draws the loop: *EventProcessor → audit log → analyser ⇄ assistant →
fix & redeploy → a fresh audit log verifies the fix*. Everything up to "fix" is shipped. This spec
covers the two remaining legs:

- **Part A (M12.4)** — the **agent fix handoff**: one action that puts a complete, evidence-anchored
  fix brief *into a coding agent* (Claude Code / Codex / any CLI agent) running in the user's source
  tree.
- **Part B (M18)** — the **Mongoose server link**: the analyser connects to a locally running Mongoose
  server's admin interface to discover the log, control audit verbosity, and (dev-scoped) restart with
  a fix — closing *redeploy → verify* without leaving the tool.

> **Principle: hand off, don't embed.** Editing Java safely needs repo context, build/test execution,
> git branching and review workflow — that is what a coding agent's harness already is. The analyser's
> job is to be the best **briefing and verification instrument** an agent is ever handed, not to grow
> an editor. Likewise the server link is a client of Mongoose's *existing* admin surface
> (`serverplugin-rest` / `serverplugin-admintelnet`, `AdminCommandRegistry`) — the analyser deploys
> nothing itself.

---

## Part A — Agent fix handoff (M12.4)

### A.1 What exists already

- `PromptBuilder` assembles evidence: selected records, node-type map, resolved source, log path +
  per-record byte anchors, and the action protocol (REST url + token + verbs).
- M12.1 (`export_finding`, specced in spec-assistant-actions §13.1) defines the **fix-brief structure**:
  diagnosis · evidence (records + anchors + file-access seeding) · resolved source targets
  (`instanceId → file:line`, EP FQN, roots) · replay reference · task · acceptance criteria.
- M13 (MCP) gives the coding agent live query-back: `read` / `aggregate` / `graph` against the running
  analyser while it works.

M12.4 is the **delivery mechanism**: get that brief into an agent with one action.

### A.2 UX

- **Records ▸ Fix with agent…** and a button on the assistant panel (enabled when ≥1 record selected).
- Dialog shows: the assembled brief (read-only preview), the **target source root** (from Settings,
  editable), and the **launch method** (below). Confirm → brief is written to
  `<sourceRoot>/.analyser/fix-brief-<timestamp>.md` and the launch fires.
- **The analyser guarantees its own cleanliness**: on first brief-write it also writes
  `<sourceRoot>/.analyser/.gitignore` containing **`fix-brief-*`** — deterministic, no race, and briefs
  can never be committed. (Not delegated to the agent; this is the only place the analyser touches the
  source tree, and it arrives pre-ignored.) **Scoped to briefs, not `*`**: the same `.analyser/` dir
  holds the *deliberately committed* project profile (`project.fluxtion-settings`, M20 /
  spec-project-profiles) — a blanket ignore would silently exclude it.

### A.3 Launch methods (Settings ▸ Assistant ▸ Fix handoff)

| Method | Behaviour | Default |
|---|---|---|
| **Copy command** | brief written to file; a ready-to-paste shell command is copied to the clipboard (and shown) | ✅ v1 default — zero assumptions about the user's terminal |
| **Command template** | run a user-configured template with placeholders: `{brief}` `{sourceRoot}` `{logPath}` — e.g. `claude --add-dir {sourceRoot} "$(cat {brief})"` | opt-in |
| Presets | shipped templates for **Claude Code** and **Codex CLI**; agent-agnostic by construction (same stance as the LLM provider setting) | — |

**No `{restToken}` placeholder** — inlining the per-run token in a shell command leaks it into shell
history and the clipboard. Instead the brief tells the agent to read the live endpoint + token from
**M13.1's `~/.fluxtion-analyser/rest-endpoint`** file (fresh, rotating, pid-checked) — composing with
M13.1 rather than duplicating it.

Launching an interactive CLI from Swing is platform-awkward (needs a terminal); v1 keeps the human in
the loop deliberately: the user pastes one command into their own terminal. A "launch in Terminal"
convenience (macOS `open -a Terminal`, Linux `x-terminal-emulator`) is a v2 nicety, not a blocker.

### A.4 The git-hygiene contract (non-negotiable, embedded in every brief)

The brief's task section instructs the agent, verbatim requirements:

1. work on a **branch**, never the checked-out working tree's current branch;
2. produce a **PR** whose description links the evidence (log path, record anchors, the analyser
   graph rationale if one was created) — the fix arrives with its justification attached;
3. **never merge autonomously** — propose → prove → human approves (the M12 guardrail);
4. prove with the **replay test** (M12.2 fixture when available; until then, the acceptance text
   describes the expected record-level change and requires existing tests green).

This is what makes agent-authored fixes reviewable by exactly the change-management processes our
regulated users run. **To be clear about enforcement**: the brief *instructs*; enforcement lives in the
user's repo controls (branch protection, required PR review). The analyser neither enforces nor could —
stating this plainly so no reader infers a guarantee the tool doesn't make.

### A.5 Acceptance (Part A)

From a diagnosed record: *Fix with agent* → paste one command → Claude Code opens holding the full
brief, edits on a branch, queries the analyser over MCP/REST while working, opens a PR that cites the
evidence. No analyser code edits any source file, ever.

---

## Part B — Mongoose server link (M18)

### B.1 Purpose and posture

Connect the analyser to a **locally running** Mongoose server's admin API so the tool can: discover
the audit log it should open, turn audit verbosity up/down at runtime, and (dev machines) restart the
server to pick up a fix — then verify via Follow mode on the fresh log.

**Posture:** this crosses the line from *instrument* to *control plane*. The design treats that line
explicitly — capabilities are tiered by risk, humans confirm every mutation, and **no server verb is
ever exposed on the assistant action socket** (see B.5). The FAQ's promise ("nothing outside the
loaded log") stays true for agents.

### B.2 Admin surface (dependency check — open question O1)

> **⚠ SUPERSEDED IN PART by the M18.0 spike (2026-08-15) —
> [spike-m18.0-admin-surface.md](spike-m18.0-admin-surface.md).** This section was written against a
> **stale** view of `mongoose-plugins`. On `origin/develop`, **audit level** and **log discovery** are
> both served (a REST endpoint and an audit-file catalog + YAML export respectively); **only lifecycle
> is still a gap**. The "gap" table below is kept for the record — read the spike for what is true.
> Two corrections that change design, not just status: the on-disk audit format is **Chronicle** (so
> M18.2 fetches a projected YAML export, it does not open a path), and core Mongoose has **no
> declarative audit-sink config at all** — `logRecordListener` is a lambda passed to `bootServer`,
> defaulting to dumping audit records into the *application* log.

Mongoose ships `serverplugin-rest` and `serverplugin-admintelnet`; nodes register commands via
`AdminCommandRegistry` (e.g. `mkDataBook.<name>.currentBook`). The analyser talks to the **REST admin
endpoint** only.

**Validated against the shipped [`svc-admin-web`](https://telaminai.github.io/mongoose-plugins/services/admin-web/)
plugin** (the reference admin-web service — the spike's concrete target). Its actual surface:

| Endpoint | Gives |
|---|---|
| `GET /api/server` | identity: **pid, runtime, startTime** (→ uptime) |
| `GET /api/services` · `/api/agents` · `/api/queues` | **enumerate processors/services** (O2's discovery data) |
| `GET /api/jvm` · `WS /ws/monitor` | JVM snapshots |
| `GET /api/commands` · `POST /api/commands/{name}` | **generic** list/invoke over whatever nodes registered with `AdminCommandRegistry` |
| `GET /healthz` · `WS /ws/logs` | liveness · buffered **application** log stream |

Bind default `127.0.0.1:8181` (loopback by default, configurable); `authMode` = `NONE` (default) /
`BASIC` / `BEARER`, HMAC-signed session cookies.

**Two corrections this forces (why the spike still matters):**
1. `svc-admin-web` resolves **identity/uptime and processor enumeration** directly — but it exposes
   **no dedicated audit surface**. Audit-sink path, audit level (`EventLogControlEvent`), and lifecycle
   are **not** REST endpoints; they are reached (if at all) as **registered `AdminCommandRegistry`
   commands via `POST /api/commands/{name}`**, or they are the **gaps M18.0 turns into a
   `fluxtion-server-plugins` PR**. So O1 is *partly* answered (status/enumeration ✔), *not* fully.
2. `WS /ws/logs` is the **application logging** stream — **not** the Fluxtion event-audit sink the
   analyser parses (the `EventLogManager` `LogRecord`/`nodeLogs` file). Do not wire M18.2 to `/ws/logs`;
   the audit log is a separate, pluggable **sink** (below).

> **O1 is load-bearing, not a footnote** — it gates M18.2–M18.4, and any missing endpoint is a
> cross-repo PR into `fluxtion-server-plugins` (expected, not exceptional — budget for plugin updates).
> **M18.0 (spike)** therefore precedes everything else in Part B: verify the four capabilities below
> against a running server *before* committing the M18 delivery order. **The spike's test bench is the
> M19 onboarding-example bundle** (spec-onboarding-example §Synergy) — a disposable local Mongoose
> server with admin REST on and a predictable log, which then also hosts M18.2–18.4's acceptance demos.

Required capabilities, to verify in the spike (and add server-side if missing) — column shows what
`svc-admin-web` confirmed vs. what's an open gap:

| Need | Reality after the `svc-admin-web` check |
|---|---|
| liveness / identity | **✔ served** — `GET /api/server` (pid, runtime, startTime) + `/api/services` `/api/agents` `/api/queues` for the processor/service inventory |
| **log discovery** | **gap** — no audit-sink endpoint. Needs a registered command exposing the **sink descriptor** (type + path), or a plugin PR. **The sink is pluggable and *typed*** — a file sink has a path the analyser can open; a **Chronicle** (or kafka/jdbc) sink does **not**, so discovery must return sink *type*, not assume a file path (see B.4 M18.2) |
| **audit level** | **gap** — `EventLogControlEvent` is not a REST endpoint; reach it via a registered `AdminCommandRegistry` command (`POST /api/commands/{name}`) if present, else a plugin PR. Generated code already handles `calculationLogConfig` |
| stop / start / restart | **gap** — no lifecycle endpoint; registered command (dev scope) or plugin PR |
| _(later)_ deploy | replace processor jar + restart (dev scope) |

The audit log's **writer is pluggable** (the same `EventLogManager` `LogRecordListener` seam M11 uses
for a metrics sink) — one declared processor can log to a file sink, Chronicle, kafka, jdbc… The
analyser consumes the **text file sink** today; discovery and open must **recognise the sink type and
degrade honestly** when it isn't one the analyser reads. Pluggable output sinks mirror Fluxtion's
pluggable compile targets (IR → JVM/native/WASM): one IR, many back-ends, both directions.

### B.3 Configuration & UI

- **Settings ▸ Server link** (per event-processor entry, like source roots): admin base URL
  (default `http://127.0.0.1:8181` — `svc-admin-web`'s default bind), **auth matching `authMode`**
  (`NONE` default / `BASIC` user+password / `BEARER` token). **Localhost-only in v1** — a non-loopback
  URL is refused with an explanatory message (production posture comes later, deliberately).
- **Status-bar chip**: ● connected (server name, uptime) / ○ not configured / ✕ unreachable.
- **Server menu**: Status · Open server's audit log · Audit level ▸ (NONE/INFO/DEBUG/TRACE) ·
  Restart… (dev) — items greyed until linked.

### B.4 Capabilities, tiered by risk

1. **M18.1 — Link + status (read-only).** Connect, show identity/health. Zero risk, ships first.
2. **M18.2 — Log discovery.** "Open server's audit log" resolves the **sink descriptor** from server
   config; if it's a **text file sink**, open it (with Follow offered) — *point the analyser at your
   running system* in one click. If it's a **Chronicle/kafka/jdbc** sink the analyser can't read, say so
   plainly (*"this processor logs to a Chronicle sink — the analyser reads the text file sink; add one,
   or use the matching reader"*) rather than failing opaquely. Read-only. **Future (not committed):**
   pluggable analyser *readers* mirroring the pluggable *sinks* (a Chronicle reader, etc.) — recognised
   here as the boundary, built only if demand appears; the sink plugins themselves live in
   `mongoose-plugins` (the Extend pathway), not the analyser.
3. **M18.3 — Audit level control.** Raise to DEBUG/TRACE while tailing, watch richer `nodeLogs`
   stream in. **Capture-and-restore, never "drop back to INFO"**: the analyser records the level it
   found (which may not be INFO) and **auto-restores it** on disconnect/exit, so a server is never
   stranded at TRACE; the confirm dialog names the cost (volume / latency / disk on a live processor).
   First *mutating* verb → confirm dialog + ops journal (B.6). The weekly-use feature; ships before any
   lifecycle control.
4. **M18.4 — Dev restart.** Stop/start/restart the linked server — but **localhost ≠ disposable**: a
   loopback server can still be shared or stateful. Restart is gated behind an explicit **per-link
   opt-in** ("this is a development server I may restart") set in the server-link settings; the menu
   item doesn't exist otherwise. Confirm dialog carries **live context from the log** the analyser is
   already reading: *"This processor published quotes 2s ago. Restart it?"* — consequence-awareness,
   not just confirmation.
5. **M18.5 — deferred.** Deploy-jar-and-restart; any non-loopback (production) posture — the latter
   only alongside the regulated-tier attestation/approvals story, never as a free desktop feature.

### B.5 The assistant fence

Server verbs are **not** actions. Not in `/manifest`, not reachable via REST or MCP, not parseable
from a reply. If agent-initiated server control is ever wanted (e.g. the fix loop auto-restarting a
dev server), it goes through a **human-approval prompt per action** ("agent requests: restart —
Approve / Deny"), designed then, not now. Rationale: an LLM stopping a trading server is a different
product with a different risk review; the action socket's documented guarantee must stay simple and
true.

### B.6 Ops journal

Every mutating server action (level change, restart) appends a line to
`~/.fluxtion-analyser/ops-log`: timestamp · server · action · initiator (always "user" in v1) ·
outcome. Cheap now; load-bearing later for the regulated story ("every control action is logged").
**Known limit, stated to avoid over-selling**: a plain file in `$HOME` is tamper-trivial — the
regulated-grade version needs tamper-evidence (signed/append-only, or shipped to the server side),
specced with M18.5's production posture, not before.

### B.7 Acceptance (Part B)

Dev-loop demo, one machine: link the local Mongoose server → one click opens its audit log tailing →
raise audit to DEBUG, watch detail appear, drop back → agent fix lands (Part A) → **Server ▸ Restart**
→ Follow picks up the fresh log → the fixed cycle is visibly correct. Every mutation confirmed by a
human and journaled.

---

## How the loop composes (A + B + existing work)

1. Anomaly found (tints / F3 / assistant) → **diagnose** with evidence (shipped).
2. **M12.4** hands the brief to a coding agent; **M13** lets it query back; **M12.2** gives it a red
   replay test; the PR carries the evidence (Part A).
3. Human reviews & merges; build produces the new jar.
4. **M18.4** restarts the dev server; **M18.2/Follow** tails the fresh audit log; **M18.3** turns up
   verbosity if the verdict needs more detail.
5. The record that used to be wrong is now right — the loop's dashed arc, executed.

## Client integration matrix

Two distinct roles: **analysis companion** (queries the analyser: read / aggregate / graph / flag) and
**fix executor** (edits source, runs builds, opens PRs). Some clients do one, some both:

| Client | Analysis companion | Fix executor | Connects via |
|---|---|---|---|
| **In-app assistant** | ✅ (in-process actions) | ✖ — hands off | built in |
| **Claude Code** | ✅ M13 MCP bridge (`.mcp.json` pointing at `analyser.jar --mcp`) or REST from the seeded prompt | ✅ **primary target** — M12.4 launch preset | MCP · REST · CLI launch |
| **Claude Desktop** | ✅ M13 MCP bridge (entry in `claude_desktop_config.json`) — chat-first forensics over the live analyser | ✖ not a repo/build environment; can *author* the fix plan, then the user launches Claude Code | MCP |
| **Codex CLI** | ✅ MCP (Codex is an MCP client — same bridge) or REST | ✅ M12.4 launch preset | MCP · REST · CLI launch |
| **Any other agent** | ✅ REST `/action` + `/manifest` schemas (the universal zero-config door) | ✅ via the copy-command template (agent-agnostic) | REST |

Notes:
- **One bridge serves every MCP client** — Claude Code, Claude Desktop, Codex, and future clients all
  consume the same `--mcp` stdio bridge (M13); per-client work is one config snippet each, delivered
  as docs (M13.4 grows a per-client section: Code / Desktop / Codex).
- **Claude Desktop's niche** is the standing forensic companion: the analyser running all day, Desktop
  configured once, and "why did X happen?" answerable from chat with the results rendered into the
  analyser. It hands *fix* work to Claude Code (its Code integration makes that handoff natural).
- The **copy-prompt** remains the floor for everything else — any model with a chat window gets the
  evidence; any agent with a shell gets the REST protocol block.

## Delivery order

**M13 and M18 are independent tracks and may proceed in parallel** — M13 (MCP) is the tracker's NEXT
for reach; within this spec: **M18.0 (spike, gates the rest) → M18.1 → M18.2 → M18.3** (small,
immediately useful with Follow) · **M12.4** (brief file + copy-command; its floor is the
already-shipped REST + brief file — MCP query-back is an enhancement, not a dependency) · **M18.4**
(dev restart, behind the per-link opt-in) · then M12.2 (replay fixture) upgrades the brief's
acceptance from prose to a red test. M18.5 stays deferred.

## Open questions

- **O1** — exact admin endpoints for status / sink config / `EventLogControlEvent` / lifecycle:
  **partly resolved by `svc-admin-web` (§B.2)** — status/identity and processor enumeration are served
  REST endpoints; **audit-sink discovery, audit level, and lifecycle are the remaining unknowns** (no
  dedicated endpoint — registered `AdminCommandRegistry` commands or `fluxtion-server-plugins` PRs).
  M18.0 confirms those three against a running server before any other Part B work.
- **O2** — multi-processor servers: the link is per-server, the log per-processor; the discovery UI
  lists processors from `GET /api/services`/`/api/agents` and each processor's **sink descriptor
  (type + path)** — remembering the sink is pluggable, so "path" is only meaningful for a file sink.
- **O3** — auth for the admin endpoint beyond localhost trust (defer with M18.5).
- ~~**O4** — brief-file gitignore~~ **resolved**: the analyser writes `.analyser/.gitignore`
  (**`fix-brief-*`** — scoped so M20's committed project profile in the same dir isn't ignored) itself
  at brief-write time (§A.2) — deterministic, no agent delegation, no race.
