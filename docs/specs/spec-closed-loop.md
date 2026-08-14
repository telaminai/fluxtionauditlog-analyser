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

### A.3 Launch methods (Settings ▸ Assistant ▸ Fix handoff)

| Method | Behaviour | Default |
|---|---|---|
| **Copy command** | brief written to file; a ready-to-paste shell command is copied to the clipboard (and shown) | ✅ v1 default — zero assumptions about the user's terminal |
| **Command template** | run a user-configured template with placeholders: `{brief}` `{sourceRoot}` `{logPath}` `{restUrl}` `{restToken}` — e.g. `claude --add-dir {sourceRoot} "$(cat {brief})"` | opt-in |
| Presets | shipped templates for **Claude Code** and **Codex CLI**; agent-agnostic by construction (same stance as the LLM provider setting) | — |

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
regulated users run.

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

Mongoose ships `serverplugin-rest` and `serverplugin-admintelnet`; nodes register commands via
`AdminCommandRegistry` (e.g. `mkDataBook.<name>.currentBook`). The analyser talks to the **REST admin
endpoint** only. Required capabilities, to verify against the plugin (and add server-side if missing):

| Need | Admin call (expected shape) |
|---|---|
| liveness / identity | server status: name, uptime, processors, versions |
| **log discovery** | per-processor audit sink config → the log **file path** |
| **audit level** | send `EventLogControlEvent` (level, optionally per-logger) — the generated code already handles `calculationLogConfig` |
| stop / start / restart | server lifecycle command (dev scope) |
| _(later)_ deploy | replace processor jar + restart (dev scope) |

### B.3 Configuration & UI

- **Settings ▸ Server link** (per event-processor entry, like source roots): admin base URL
  (default `http://127.0.0.1:<port>`), optional auth header. **Localhost-only in v1** — a non-loopback
  URL is refused with an explanatory message (production posture comes later, deliberately).
- **Status-bar chip**: ● connected (server name, uptime) / ○ not configured / ✕ unreachable.
- **Server menu**: Status · Open server's audit log · Audit level ▸ (NONE/INFO/DEBUG/TRACE) ·
  Restart… (dev) — items greyed until linked.

### B.4 Capabilities, tiered by risk

1. **M18.1 — Link + status (read-only).** Connect, show identity/health. Zero risk, ships first.
2. **M18.2 — Log discovery.** "Open server's audit log" resolves the sink path from server config and
   opens it (with Follow offered). Kills the last onboarding friction — *point the analyser at your
   running system* becomes one click. Read-only.
3. **M18.3 — Audit level control.** Raise to DEBUG/TRACE while tailing, watch richer `nodeLogs`
   stream in, drop back to INFO after. Diagnosis-grade telemetry on demand, no restart. First
   *mutating* verb → confirm dialog + ops journal (B.6). This is the weekly-use feature; ship before
   any lifecycle control.
4. **M18.4 — Dev restart.** Stop/start/restart the linked local server. Confirm dialog carries **live
   context from the log** the analyser is already reading: *"This processor published quotes 2s ago.
   Restart it?"* — consequence-awareness, not just confirmation.
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

## Delivery order

**M18.1 → M18.2 → M18.3** (small, independent, immediately useful with Follow) · **M12.4** (brief file
+ copy-command; presets) · **M18.4** (dev restart) · then M12.2 (replay fixture) upgrades the brief's
acceptance from prose to a red test. M18.5 stays deferred.

## Open questions

- **O1** — exact `serverplugin-rest` endpoints for status / sink config / `EventLogControlEvent` /
  lifecycle: verify against `fluxtion-server-plugins`; anything missing is a small server-side PR
  (tracked there, not here).
- **O2** — multi-processor servers: the link is per-server, the log per-processor; the discovery UI
  must list processors and their sinks.
- **O3** — auth for the admin endpoint beyond localhost trust (defer with M18.5).
- **O4** — brief file lifecycle: `.analyser/` in the source root needs a `.gitignore` entry in the
  brief's instructions (the agent should add it) so briefs never get committed.
