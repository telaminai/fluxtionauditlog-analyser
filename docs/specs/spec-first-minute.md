# M79 — The first minute: the diamond, then three doors

**Status:** ACCEPTED r1, 2026-10-18; **r2 the same day folds in the first independent review** (corrections and code
facts in place; six new questions for the owner, OD-79.11–.16, §16, open; effort revised to 24–32 days, §17). The
owner accepted OD-79.1–.6 on 2026-10-18, then OD-79.7 and the three decisions on the colleague follow-on (OD-79.8–.10,
recorded as an extension of M15 Settings share, §13) the same day (§16). Each phase PR gets its own review (the
reviewed-PR rule).

**Tracker:** M79. **Builds on:** M77 ([`spec-ai-setup-wizard.md`](spec-ai-setup-wizard.md): the wizard, the AI line,
the client path of M77-3; **its OD-77.1 is superseded here**, §3), M69 spotlight walks
([`completed/spec-spotlight-walks.md`](completed/spec-spotlight-walks.md): the `walkPlayback` node, the step view,
record-digest targets), the start page ([`completed/spec-start-page.md`](completed/spec-start-page.md), D-S1), the
DEMO set (`DemoAssets`, M36), evidence bundles (M70), M15 Settings share
([`completed/spec-settings-share.md`](completed/spec-settings-share.md)), M38 portable context, and CLAUDE.md rules 1,
2, 6, 8 and 9.

**Naming.** As in M77, every string that names the product is written `{Product}` (`UiText.PRODUCT`: *"the analyser"*
today, *"Canvas"* after the rename PR).

---

## 1. Why — what the two reviews found

Two independent reviews walked the zero-to-hero journey on 2026-10-18: an **insider**, who knows Fluxtion, and a
**stranger**, who does not. They agreed on one thing above all: **the aha moment is a picture, and today it is hidden**
behind a 225-word start page and an AI-setup question.

- **The stranger's aha:** click a record, and the graph lights up with what the code did.
- **The insider's aha:** the Fluxtion *diamond*. One input feeds `a`; `a` feeds `b` and `c`; both feed `d`. When the
  same input arrives twice, `a` reports no change, so the dispatcher the compiler wired calls none of `b`, `c` and `d`
  the second time. The log proves *"d did not run"* (nor `b`, nor `c`): it is not an inference.

The stranger also found:

| # | Finding | Owned here |
|---|---|---|
| F1 | The Guided tour was demoted: the docs' own screenshot shows it as the primary blue card, the app shows it as one chip among fourteen | **M79-1** (the Learn door) |
| F2 | Docs and app labels disagree: *"Open the demo log"* / *"Take a guided tour"* in the docs, *"Guided tour"* / *"Sample project"* in the app | **M79-4** (a parity test) |
| F3 | The docs never say *canvas*, *build* or *learn*; they run to about 66,000 words, with five getting-started pages | **M79-4** |
| F4 | Packs are invisible: nothing says what a prompt pack is, or lists one | **M79-4**, **M79-5** |
| F5 | The first view uses auditor prose, for example *"unchanged metadata does not prove identical bytes"* | **M79-2** |

The owner added a sixth finding from the field (OD-79.6). **The paying customer's developers reached zero-to-hero
without the owner.** They already trusted Claude, and *"the verbs, project context and MCP just meant Claude, the tool
they trust, was used effectively as the teacher… the analyser MCP dramatically improved efficiency"*. For a developer
who already uses an AI client, the fastest teacher is that client, pointed at the project. {Product} does not need to
teach it again.

## 2. Goals and non-goals

**Goals**

- G1. **The picture first.** On a machine's first run (§5.1), the first launch opens the diamond, with the graph centre stage,
  and plays its four-step walk once. The person sees a node not run, and the record that proves it, before reading any
  paragraph (M79-1).
- G2. **Their own domain.** The same diamond, really run, with names from mathematics, accounting, logistics or trade
  processing, chosen from a dropdown. The problem is universal, and the person sees it in words they use (OD-79.1).
- G3. **Three doors, not fourteen chips.** The walk ends on *Click any record* and three doors: **Learn**,
  **Investigate**, **Build**. The start page shows fewer than 40 words (M79-1).
- G4. **AI is a quiet line, not a gate.** AI setup leaves the first launch. The wizard opens on the first Ask, Send or
  Start building, as M77 already provides (OD-79.5).
- G5. **Quiet evidence.** One chip that names the check made (*Matches the shipped copy ✓*, *Size and date unchanged
  since opened*, *Changed since opened ⚠*, …; §8.1) replaces the Context column's verification prose. The prose stays one click away (M79-2).
- G6. **A real bug to solve.** *Solve a real bug (5 min)*: a teaching bundle from a real run of a deliberately broken
  commit. {Product} confirms the answer by record digest, and the quest works with No AI (M79-3).
- G7. **One front door in the docs.** One *Start here* page, a home page that says canvas, learn, build and understand,
  packs defined and listed, and a test that docs labels match app labels (M79-4).
- G8. **"Connect your own Claude" as a first-class path.** One action connects the person's own client and points it at
  the project context (M79-5, OD-79.6).

**Non-goals**

- No relabelling of a log for display. Each domain is a separate real run (§6, E2).
- No change to the investigation layout. The records table stays the main view for investigation; the graph is centre
  stage only during walks and first contact (OD-79.7).
- No new AI path. M79-5 composes M77-3's client path with a prompt pack; it adds no verb and no transport.
- Not M79: the criterion chip (M76 slice 1), the before/after run comparison, keyless authoring through pre-generated
  variants, and the colleague extension of Settings share (§13). Each is a follow-on (§14).

## 3. Standing decisions this touches

| Decision | What it says | What this spec does |
|---|---|---|
| **M77 OD-77.1** | The first run shows the wizard's first screen *inline on the start page*. | **Superseded by OD-79.5** (owner, 2026-10-18, confirmed the same day). The first-run AI block is removed before it ships: M77-2 builds the AI line only. **Reason:** the reviews found the AI question was the second thing standing between a newcomer and the picture. Asking how AI should work before the person has seen what {Product} shows makes the question abstract, and it cost the stranger the aha. M77's own entry 5 (the wizard opens at the first Send, Ask or Start) already catches everyone who wants AI, at the moment they want it. OD-77.9 (offer the block to upgraders) and OD-77.19 (no picture in the block) fall with it. M77's §3, §5, §6.0 and §20 record this. |
| **M36 D-S1 / onboarding R6** | The start page IS the first run; no first-run dialog. | **Amended by OD-79.2.** The *no dialog* half is kept: the first minute is a load in the main window, through the ordinary open path, with no window or modal. The *start page is the first run* half changes: with no recent project, the first run is the diamond, and the start page is one door away. |
| **M35.7 / M35.9** | No modal in the load path; nothing fires at an empty screen. | Kept. The diamond opens with an `OpenRequest` whose `launch` is a new `OpenRequest.Launch.FIRST_MINUTE` (the record has no *kind* today: `Launch` is `NONE · COMMAND_LINE · RESTORED · EXPLICIT_RESTORE`). `suppressDialogs()` must return true for it, or the load path's own dialogs (time order, project offer) can still fire over the first minute. A failure lands on the start page as one line (§5.5), never a dialog. **Today's sample-project install failure is a `JOptionPane` (`MainFrame.openSampleProject`), so the diamond must not reuse that path.** |
| **M69 §3.3** | The step view is a fixed allow-list. | **Extended by one field:** `stage` (§7.2). Nothing else is added; a walk still never opens a log. |
| **M69 §3.8** | Playback lives in `walkPlayback`. | Kept and extended: the stage, autoplay advance and the first-contact hold are that node's (§7). |
| **M74 S1** | *Run me through a demo* plays the DEMO journey. | Kept; it moves under *Or open directly*. The Guided tour stays the DEMO investigation tour (the Learn door). |
| **M15 Settings share** | Export and import of a whitelisted `.fluxtion-settings`. | Extended later by the colleague follow-on (§13), not by a second share format. |
| **CLAUDE.md rule 1** | Public repo: no real names; screenshots generated, not taken. | Applies to every domain variant, the teaching bundle and the GIF (§18). |

## 4. Prerequisites and dependencies

| Item | Where | Why M79 needs it |
|---|---|---|
| **D-1. The diamond variants** | `telaminai/fluxtion-diamond`, **published on `main` at `24d6d74` (2026-10-18)**, merged from its `domains` branch; the pin and digests are in §6.4 | Five real runs: generic, mathematics, accounting, logistics and trading. Each is one topology with domain-named classes and fields, compiled and run once. M79-1 bundles them **at that commit**, with their GraphML and source, and writes the jar's manifest from §6.4. No stand-in log is ever shipped in their place. |
| **D-2. The broken diamond** | the same repo, branch `teaching/broken-diamond`, commit **`6bff0d67b9d8eb190d404330fe7c5f52d2018e7a`** on top of the `main` pin `24d6d74`: one commit with a deliberate defect, and its run (`teaching/broken-diamond-audit.yaml`). **The owner publishes the branch**; until it is on GitHub, M79-3 cannot pin it (§9.1) | M79-3's teaching bundle (§9). |
| **D-3. M77-2's AI line** | M77 | M79-1 renders the line's short form (§5.4). If M77-2 has not merged, M79-1 shows today's assistant fallback collapsed under *Or open directly*, and adds no AI text of its own. |
| **D-4. M77-3's client path** | M77 | M79-5 starts the client path at Screen 2b. It does not merge before M77-3. |
| **D-5. The capture harness** | `tools/capture-docs.py` | The *Start here* GIF and every new screenshot are generated under an isolated `user.home` (rule 1). |
| **D-7. M76 and M75, not yet on `main`'s line** | M76 `spec-authoring-journey.md` (r2, branch `docs/m76-checkpoint-and-completion`); M75 `spec-document-renderer.md` (branch `docs/m75-document-renderer`) | **Overlaps to settle before M79-1 opens** (review, 2026-10-18): M76 uses **the same diamond** for its S8 *Teach me by building an example* and leaves its pin open (OD-76.7), which §6.4's pin settles; M76 has its own analyser-composed explanation tour of the diamond, which must not become a second set of diamond captions; M76 adds S7/S8 to the start page's numbered list, which §5.4 moves behind *Ask…*; and M76 §5.4/§9.1/§10.5 say an absent entry is *not* proof and ban *proof/proves*, while step 4 here says *proof, not silence*. Both are right in their scope: absence is proof **only in a traced record** (*Format specification* §8; `AuditTrace`), and E4 pins that the diamond's are traced. M76's wording must carry that condition rather than forbid the word, or M79's caption changes (OD-79.14). M75 places bundled lessons, the diamond included, under `/teaching/`, while §6 extends `/demo`: one location is chosen in whichever lands first |
| **D-6. Rule 6 reading** | `docs/claude.txt` | Before M79-1 is implemented, the implementer reads the framework reference and the variants' generated dispatcher, and confirms §6.2's claim from them. The rule is that `@OnTrigger` fires when an upstream node signalled a change. This spec states the claim; it does not infer the generated code. |

## 5. M79-1 — the first launch

### 5.1 When the first minute plays

`firstMinute` (a new session node, §7.1) decides. It plays when all of these hold, as reported once at start-up by
`FirstMinuteStartupObserved`:

- **a first run of this machine**: no config file existed at start-up (`MainFrame.firstRunAtStart`, i.e.
  `!configStore.exists()`), and `recentProjects`, `recentFiles` and `recentBundles` are all empty. *No recent project*
  alone is not enough: the paying customer's developers open logs, often by file and over REST, and many have never
  made a project, so an upgrade would autoplay the diamond over their daily tool (review, 2026-10-18);
- `firstMinute.played` is unset (a machine-tier key);
- the process was not started with `--rest`, and is not the capture harness (which pins `firstMinute.played=true`
  except in its own first-minute run, §18);
- no file was passed on the command line (`java -jar … log` opens that log, as today);
- no session-recovery offer is pending (`SessionRecovery` wins; the first minute is deferred, not marked played).
  `SessionRecovery` is asynchronous (it starts in `checking`), so the decision is not taken from a one-shot start-up
  fact: `firstMinute` has `sessionRecovery` as a parent and decides only once recovery has left `checking`;
- no other start-up surface is due: `maybeShowWhatsNew()` (a dialog on upgrade) and
  `showFirstRunSettingsIfNeeded()` run on a 700 ms start-up timer today, and neither may fire over the first minute.

Otherwise the start page shows, as today, with M79-1's shorter copy (§5.4).

**Session recovery.** Today the open log becomes the captured session (`captureSession`), so the next launch would
offer to recover the diamond. A session whose only content is a manifest-matched diamond opened with
`Launch.FIRST_MINUTE`, with nothing of the person's added (no flag, note, chart, report or walk), is not captured
for recovery. Anything the person added makes it an ordinary session (A1.9). **First run only, never again**
(OD-79.2): `firstMinute.played=true` is written when the walk's first step is shown, so skipping counts as played. A
launch that defers leaves the key unset. Once any project is recent, the first minute never qualifies again. The
diamond stays one act away: *Learn ▸ The diamond*.

### 5.2 What the person sees

1. The diamond opens: the **generic** domain, from the jar (OD-79.3), through the ordinary open path. Its log and
   GraphML are paired by the ordinary pairing.
2. **The graph is centre stage.** The topology canvas fills the centre and the records table becomes a strip beneath
   it, showing at least six rows at 1200×800. This layout is the `walkPlayback` node's `stage` (§7.2, OD-79.7). Today the topology canvas is a tab in the right-hand column (`sideTabs`, beside Summary, Source, Graph and Reports) and the records table and detail pane are the left column of the Facts | Canvas split, so this is a new layout, not a tab change.
3. Above the graph: **Domain: Generic ▾**, with *Generic · Mathematics · Accounting · Logistics · Trade processing*.
   Choosing one opens that domain's own run (§6, §7.1). It does not autoplay again; ▶ replays the walk.
4. The four-step walk plays once, advancing by itself (§7.3). The strip reads *"Step n of 4 · Esc skips"*. ← and →
   take over the pace, and Esc skips to the end state.

**The walk** (generic copy; each domain's walk carries its own nouns, §6.3):

| Step | View | Targets and captions |
|---|---|---|
| 1 | stage `topology`, no record selected | `topology:node:a` *"An input arrives at a."* · `topology:node:d` *"a feeds b and c; both feed d. The compiler wired this from the code."* |
| 2 | record [2] (`X[value=2]`), the first input | `records:row:2` *"A new input."* · `topology:node:d` *"a, b, c and d all ran: each has an entry in this record."* |
| 3 | record [4] (`X[value=3]` again, after record [3]'s `X[value=3]`) | `records:row:4` *"same input — nothing changed"* · `topology:node:a` *"a saw the same value and reported no change, so nothing below it was called"* · `topology:node:d` ***"d did not run"*** (the owner's words, joined: *"same input — nothing changed — d did not run"*) |
| 4 | record [4] | `detail:node:a` *"Only a has an entry here. This log records every node that runs, so no entry for b, c or d means they did not run. That is proof, not silence."* |

Record indexes are 0-based, as the analyser numbers them. Records [0] and [1] are the log's control and `Init` records.
Each pinned run has the same shape: six records, and record [4] is the repeated input, with an entry for the top node
only (§6.4).

Step 3 selects **the record that has no d entry** (record [4]). Because the topology canvas's step cursor is bound to the selected
record (M69 §3.5), the graph lights `a`, the only node that ran, and leaves `b`, `c` and `d` dark. The picture and the record say the
same thing because they are the same thing.

**Every caption says only what the record shows** (reviewed against the pinned logs, 2026-10-18). On the repeated input
the top node logs `changed: false` and *nothing* below it runs: `b` and `c` are skipped as well as `d`, because their
guards read `a`'s dirty flag (the generated `guardCheck_b` / `guardCheck_c` return `isDirty_a`). So no caption may say
or imply that `b` or `c` ran and only `d` was skipped; step 3 names `d` because it is the diamond's join, and step 4
names all three. Record [3], not record [4], is where one side of the diamond is unchanged and `d` still runs, and in
the generic run only. A caption about a domain is checked against that domain's own log: in *Accounting*, `discount`
reports no change in records [2] and [3] alike, and in *Trading* both `pnl` and `position` change in record [3] (§6.2).

**Step 1 and the selection.** M69 §3.3 has no way to clear a selection (a missing `record` means *no selection
change*). Step 1 therefore relies on the open leaving no record selected; whether today's load path selects a row
was not checked by the review, so A1.9 asserts it, and if a row is selected the step's captions must still be true
over it (they do not mention a record).

### 5.3 The end state: one callout, three doors

When the walk ends, whether played through or skipped, one callout points at the records strip: **"Click any record"**.
Clicking one lights the graph with what that cycle ran; that is the stranger's aha. Beside it is a card with three
doors, each one act:

| Door | Button | What it does | Secondary link |
|---|---|---|---|
| **Learn** | **Guided tour** | plays the DEMO investigation tour (unchanged) | *Solve a real bug (5 min)* (from M79-3) |
| **Investigate** | **Open audit log** | the log chooser | *Sample project* |
| **Build** | **Start from a template** | the starter chooser (M47) | *Connect your own Claude* (from M79-5) |

The card stays until the person chooses a door, opens anything else, or closes it (✕). It is the `firstMinute` node's
state, not the view's. The stage stays `graph` for **first contact**, until the same acts, or until the person uses the
**Topology | Records** toggle (§7.2). Then the investigation layout returns: the records table is the main view (OD-79.7).

### 5.4 The start page: under 40 words

The start page is what a returning person sees, and it is one door away for a newcomer. Its always-visible text drops
from about 225 words to **at most 39**. The budget is pinned by A1.6.

> **See what your code did.**
>
> | **Learn** | **Investigate** | **Build** |
> |---|---|---|
> | The diamond · Guided tour · Solve a real bug (5 min) | Open audit log | Start from a template · Connect your own Claude |
>
> *AI: not set up · Set up…* &nbsp;&nbsp; **Recent** … &nbsp;&nbsp; *Or open directly ▸*

- **Or open directly ▸** collapses everything else: the Open, Create and Settings groups, *Run me through a demo*
  (M74 S1), *Sample project*, the evidence-bundle and GraphML openers, and the Fluxtion key card. One act expands
  it, and the expansion is remembered per machine.
- **The AI line, short form.** The start page shows M77 §6.0's line in a short form: *"AI: not set up · Set up…"*,
  *"AI: off · Set up…"*, *"AI: Claude (Safe) · Ask… · Change…"*, *"AI: Claude (Full) · Ask… · Change…"*,
  *"AI: in-app · Ask… · Change…"*. The long form, including *"your own client can connect"*, is the line's tooltip and
  the Context column's row. `aiSetup` composes both forms (rule 9). M77's table stays the truth for what each state
  says.
- **The prompt box** (M74's start prompt and its numbered list) opens from **Ask…**, or from any number key or typing
  on the page. It is one act away, not always visible. With nothing set up, typing opens the box, and Send opens the
  wizard with the question pending (M77 entry 5).
- **Drop targets** are unchanged: a `.fexp`, a log, GraphML, Spring XML or a prompt pack dropped anywhere still opens.
  The drop hint moves into *Or open directly*.
- Before M79-3 and M79-5 merge, their links are absent, not disabled. The budget holds in every phase.
- **Reuse.** *Or open directly* is already the heading over today's groups (`start.or`); M79-1 turns that heading
  into the collapse. The page today has 13 chips in five groups (*Explore DEMO*, *Open*, *Create*, *Connect an
  assistant*, *Settings*), the hero *What would you like to do?* with its lede, and the S1–S8 list always visible.
  No test counts its words today; A1.6 is new.
- **The doors do not set the work mode.** {Product} already has *Work mode: Investigate / Build* (the session's
  posture, `open {posture}`, *AI ▸ Work mode*). In M79 the **Investigate** and **Build** doors open what they say
  and leave the posture as it is, so the doors add no second path to a session intent; whether a door should also
  set it is OD-79.13.

**Counting rule** (A1.6): count whitespace-separated tokens that contain at least one letter or digit, in every
visible, non-collapsed text component of the start page at 1200×800. Recent entries' names and paths are the person's
data and are not counted; the *Recent* heading is. Count in every AI-line state, and take the maximum.

### 5.5 When it cannot play

If the shipped diamond fails its manifest check (§6.1), or its open fails, `firstMinute` does not play the walk and
does not mark it played. The start page shows one line: *"The bundled diamond could not be opened: {reason}."* The
reason comes from the open's own refusal. Nothing else changes.

## 6. Evidence integrity — the domain variants (OD-79.1, the lead decision)

{Product} **never fabricates or rewrites evidence**. A diamond shown with accounting words is therefore an accounting
diamond that ran, not the generic log with its labels swapped.

### 6.1 Rules

| # | Rule | Check |
|---|---|---|
| **E1** | **Each domain is a real run.** One variant per domain: the same topology, with domain-named classes and fields, generated by the Fluxtion compiler and run **once**. The shipped bytes are that run's bytes, as committed at the pin (§6.4). Regenerating produces a new set, a new pin and a new manifest, never an edit. | `DiamondAssetsTest`: every shipped file's SHA-256 equals the manifest's, and the manifest's equal §6.4's; the manifest names the `fluxtion-diamond` commit, the compiler and runtime versions, and each file's repo path |
| **E2** | **No relabelling.** {Product} shows the names the log and graph carry. No code path maps one domain's names to another's. | A1.4: every node id each domain's log writes (its four) is declared in that domain's GraphML, and the pairing reads *declared, matching* (the GraphML also declares the event type and the framework's own nodes, so the sets are not equal); a static rule fails on any name table keyed by domain (`OneDispatchModelTest` style) |
| **E3** | **One topology.** The five graphs are isomorphic to the generic graph: the same shape (15 nodes and 15 edges each at the pin, framework nodes included), one event type entering the top node, the diamond below it. | A1.4: graph isomorphism over the five GraphMLs, under the mapping event type ↦ `X`, top ↦ `a`, the two siblings ↦ `b`/`c`, the join ↦ `d` |
| **E4** | **Absence is proof only when traced.** In every variant, every node writes an audit entry each time it runs (the pinned README says so, and record [2] shows all four), as in the DEMO traced log. Each pinned run has a cycle where every node ran (record [2]) and the repeated input where only the top node ran (record [4]). Records [3] and [5] are the held-out cycles of SC-C1 (§12). | A1.5: per domain, `AuditTrace.tracesEveryInvocation` is true for records [2]–[5] (the analyser's own traced-record rule, *Format specification* §8; checked by the review on all five logs and the teaching log, and false only for the control and `Init` records [0] and [1]), as `DemoAssetsTest.theTracedLogReallyIsTraced_soDidNotRunIsPROOF` pins for the DEMO log; coverage reports every node as able to log; record [2] has an entry for each of the four nodes; record [4] has an entry for the top node only. Only a traced record lets the topology canvas classify `b`, `c` and `d` as `DID_NOT_RUN` (dimmed) rather than `RAN_SILENTLY` or `MAY_HAVE_RUN` (`ProcessorTopology.classifyCycle`) |
| **E5** | **Walk captions are bound to the evidence.** Each domain's walk targets its records by record digest (M69 §3.5), with the digests of §6.4. A changed log makes the step *not available*. It is never re-pointed or captioned anyway. **Today that holds only for record-basis targets** (`records:row:N`, `detail`, `detail:node:…`): a `topology:node:<id>` target has a *graph* basis and stays lit, with its caption, whatever record is selected (`WalkPresenter.dependsOnSelection` covers only the detail pane and the whole canvas). So step 3's *"d did not run"* on `topology:node:d` would survive a substituted record. M79-1 adds one `walkPlayback` rule for walks of origin `FIRST_MINUTE`: **a step shows none of its captions unless every record-basis target in it is current** (the step is *not shown*, with the record's reason). | A1.5's witness: play the shipped walk over a log whose record [4] has a `d` entry (E6 bypassed in the test); step 3 is *not shown* and neither `records:row:4`'s nor `topology:node:d`'s caption appears. Mutation: drop the new rule, and `topology:node:d` lights *"d did not run"* |
| **E6** | **The jar's copy is recognised by digest, not by name.** The domain dropdown and the *bundled* label appear only when the open log's read-time SHA-256 (`FileReadIdentity`, from `LogStore.readIdentities()`; hex without the `sha256:` prefix that record digests carry) matches a manifest entry. A copied, renamed or edited file opens as an ordinary log. **Extraction:** the assets are extracted as the DEMO set is (`DemoAssets`, `/demo` → `~/.fluxtion-analyser/demo`), but `DemoAssets.install()` today re-extracts a file only when it is missing or empty, so an edited extracted copy, or a stale one after a re-pin, would be reused and fail the manifest on every launch (§5.5, forever). M79-1 makes `install()` re-extract any file whose SHA-256 differs from the manifest. | A1.5: an edited copy opens without the dropdown; an edited *extracted* copy is replaced on the next extraction and the first minute plays |
| **E7** | **The source ships too.** Each variant's source and generated processor ship beside the log, as the DEMO source does, so clicking a node opens the code that ran. | `DiamondAssetsTest` lists them |

### 6.2 The claim, stated once

From the framework reference (`docs/claude.txt`, *Conditional dispatch*): **`@OnTrigger` fires when an upstream node
signalled a change.** In the pinned variants, `a`, `b` and `c` each return `true` only when their value changed. On the
repeated input, `a` reports `changed: false`, so the generated dispatcher calls neither `b` nor `c`, nor `d` below them.
The log has one entry, `a`'s, for that cycle. Record [3] shows the other half of the rule: in the generic run, `b`
reports no change but `c` does, and `d` still runs, because one changed parent is enough (the generated guard is
`isDirty_b | isDirty_c`). The domains do not all repeat that half: *Mathematics* (`quotient`) and *Logistics* (`pick`)
do; in *Accounting* `discount` reports no change in records [2] and [3] alike; in *Trading* both siblings change in
record [3]. Only record [4]'s half is common to all five, which is why the walk's claim rests on record [4] alone.
Every caption here is written from the pinned logs, which were read for this spec, and not assumed (D-6, rule 6); the
generated dispatchers' `handleEvent` and guards were read too (independent review, 2026-10-18).

### 6.3 The domains

The names are the pinned runs' own, read from their logs at `24d6d74`. They follow the naming rules, which bind any
future variant too: generic English; no real company, venue, bank, carrier, exchange, ticker or product; packages
under `com.acme.diamond`; instruments, if named, `DEMO-1`; currencies, if any, `XXX` (the ISO code for "no currency").
The dropdown says *Trading* for the owner's *stock/trade processing*.

| Domain | Input (record [4]) | a | b, c | d | Step 3, joined |
|---|---|---|---|---|---|
| Generic (default) | `X[value=3]` | `a` | `b`, `c` | `d` | *same input — nothing changed — d did not run* |
| Mathematics | `Operand[value=5]` | `dividend` | `quotient`, `remainder` | `recombination` | *same operand — nothing changed — recombination did not run* |
| Accounting | `InvoiceLine[net=600]` | `line` | `discount`, `tax` | `total` | *same invoice line — nothing changed — total did not run* |
| Logistics | `Order[items=18]` | `order` | `pack`, `pick` | `dispatch` | *same order — nothing changed — dispatch did not run* |
| Trading | `Trade[price=103]` | `trade` | `pnl`, `position` | `risk` | *same trade — nothing changed — risk did not run* |

Each domain's walk is its own shipped walk definition. Its words are authored once, from that domain's log, and bound
by digest (E5). The chosen domain is remembered per machine (`firstMinute.domain`).

### 6.4 The pin and its provenance

**Source:** `telaminai/fluxtion-diamond`, `main` at **`24d6d74340d45a3b4856297a135c57c88d4243b1`** (2026-10-18, *Merge
the diamond in four domains*, from the `domains` branch). That repo's README records the toolchain: Fluxtion runtime and
builder API 1.1.2, Fluxtion BOM and builder 1.0.79 (the generated header records `api version 1.1.2`, `target generator
version 1.0.79`), and fluxtion-maven-plugin 1.3.0. Each run's console output is committed beside its log
(`run-console.txt`). The SHA-256 of each shipped file, as committed at the pin and read for this spec (one log and one
GraphML per domain; the generic run is `sample/`, each domain's log is `domains/<domain>/`):

```
2b77309939347912f947a0a9fe38f1472161b742e6b8ac98fde1e737f271154a  sample/diamond-audit.yaml
58f30c57c50780bee72f0d2470a954f89c5f5efdc043229971bb356d51ea4b46  domains/mathematics/mathematics-audit.yaml
97c32adb30cab73d5bf91708e2d239b5f68c3ad47132b4ece82a3f163ccbd20b  domains/accounting/accounting-audit.yaml
23d530adfb1ed2a9e803e7633a136308076ba974b5d59b50aa41fa02e1191926  domains/logistics/logistics-audit.yaml
2ce065c442430bcbc8864afa8e3f76e3cddae0dcb505ca5afbfd9ff34dd4ffcc  domains/trading/trading-audit.yaml
7ebd36b0c39ad016cc552b1b54c7cd743c797dbceff9e7e4b37ba7cf8d6b5dec  src/main/resources/com/acme/diamond/generated/DiamondProcessor.graphml
4c430f57350d8f12c17b7baff4986e5fcb2a065c6cd550e6e6aa2a81f6276227  src/main/resources/com/acme/diamond/domain/mathematics/generated/MathematicsProcessor.graphml
ce91e16a8da3d753c32401968b8697a5d9264e0f78cfc9c53515021a81214104  src/main/resources/com/acme/diamond/domain/accounting/generated/AccountingProcessor.graphml
30ea85e65bceb3a1c407c21e38a473c1f144db09897a67794541e26d874b703c  src/main/resources/com/acme/diamond/domain/logistics/generated/LogisticsProcessor.graphml
976dbec2e6a40771de79d3ca2082418607efdc5a68f1841994f694ecb612d779  src/main/resources/com/acme/diamond/domain/trading/generated/TradingProcessor.graphml
```

**Record digests** (M69 §3.5: SHA-256 over the UTF-8 bytes of `LogStore.rawText(N)`, computed with
`WalkIdentity.recordDigest(HeapLogStore.fromFile(log).rawText(N))` by the independent review). These are the bases the
shipped walks bind to (E5); A1.5 asserts them, so a drifted asset or a changed store representation fails by name:

```
                 record [2] (all four ran)                                            record [4] (the repeat)
generic      sha256:e03d4591eae073430471a4e4f967c0346117dba2b51514ef116b3429d222f1f2  sha256:d0e68caf5f8859e7435f90b43c1c9cfdbb486aa35225e4fff54ffd959d2a1ced
mathematics  sha256:d4021d81670aaf22d8764c70f2f1b372bd3969509bef0ec286deb474a27e7b24  sha256:9da8e95c05e2f39a4d3d5368b76d69a08b8d07b00037982b481f43a56927a314
accounting   sha256:f8039949008cb6cf5128973b7e1fb3187ae11490faf46abcd47f41a9a609433b  sha256:47e5f454fdafb890057d9f8bb89d09c2733f09e15029888d1a5d835e962ab278
logistics    sha256:2ce1dec459b5a23ae67085b1f465a1d8f3784267395461066dc1981c6396db94  sha256:10ed9ebeb23fe7e4fdb4a9c844b0207299cbd0b02ced0bd3d05cc5c39b39ec00
trading      sha256:b9b62c864fb18eee6b11c22e815340ab4742c9ef1855cfe212a328472c7cdfc5  sha256:791dbacc2c79982348459181de7a08aea01c613396b91c5b62527e73f2598766
```

The generic run's record [3], the SC-C1 held-out cycle, is `sha256:2c07ecfbdf78ed1d36fb60a3c9ae5d8285dc66d1f43aa9d39c29be5bb654e902`.

- **What was read.** Each log has six records. Record [4] is the repeated input, with an entry for the top node only.
  The node names are §6.3's. The pinned repo passes the four-term sweep (rule 1), and its three commits are authored
  with the personal address. Each log ends with the stream-end trailer `streamEnd: normal`, `streamEndRecords: 6`
  (the pinned README's *"streamEnd: complete"* is the analyser's reading of it, not the file's word).
- **Verified by the independent review** (2026-10-18): the pin resolves to that full SHA on `main`; all ten SHA-256s
  above were recomputed from a fresh clone at the pin and match; the four GraphMLs of the domains and the generic one
  each declare 15 nodes and 15 edges; the generic `sample/diamond-audit.yaml` is byte-identical at `dad9b43`, the
  commit the M74 *Fluxtion philosophy* packs pin (PR #155), so the packs and M79 describe the same generic run.
- **One honest caveat about the source.** The pinned repo's README says that the only edit to generated code is the
  licence comment at the top of each processor, which its `regen.sh` swaps for a neutral one. The dispatcher body is
  the generator's. The shipped source is therefore the pinned file, header and all, and the jar's manifest repeats the
  README's sentence, so that nobody mistakes the header for the generator's own output.
- **M79-1 copies, never rebuilds.** The jar carries the pinned bytes. A new pin is a new commit here, with this list
  and the manifest updated together, and A1.10 fails until they agree.

## 7. Rule 9 — the session processor owns it

No hand-placed dispatch. Every *when* below is a node's decision; adapters perform effects and report facts, and
surfaces render the `SessionSnapshot`. Regenerate with `mvn -Pregen process-classes`.

### 7.1 `firstMinute` — a new node

- **Parents:** `openLog` (the generation, open state and read identity) and `walkPlayback` (the showing walk and its
  end). It is downstream of both, so there is no cycle.
- **Parents also include `sessionRecovery`** (its state leaving `checking` is the moment of decision, §5.1).
- **Facts in:**
  - `FirstMinuteStartupObserved(firstRunAtStart, recentProjects, recentFiles, recentBundles, played, restLaunch, launchFile, rememberedDomain)`;
  - `DiamondDomainChosen(domain)`;
  - `FirstMinuteDoorChosen(door, link)`;
  - `FirstMinuteCardClosed`.
- **Decisions:**
  - whether to play (§5.1);
  - when to request `OpenDiamondEffect(domain)`, whose open carries `OpenRequest.Launch.FIRST_MINUTE` (§3);
  - when the diamond's generation is installed, and its read identity matches the manifest (E6): request
    `PlayBundledWalkEffect(walkName, origin=FIRST_MINUTE)`. The adapter reads the shipped definition and posts the
    ordinary `WalkPlayRequested`; M69's path does the rest. **Reuse:** this is today's `PlayDemoJourneyEffect`
    (`DemoJourney.load` reads a shipped `.fluxtion-settings` fragment with pre-authored run and record digests),
    generalised to take the walk's resource. Two parts of today's path must **not** be copied: `persistWalks` (it
    saves the shipped walk into `config.walks`, which would put five diamond walks into a no-project person's
    Reports) and the hand-placed `pendingSampleJourney` / `pendingSampleTour` frame flags (rule 9). The frozen
    definition travels in `WalkPlayRequested`, as M69 R6 already allows. The Guided tour's pattern (`DemoTour`,
    digests bound at first play from whatever log is loaded) is the wrong model for E5;
  - when `walkPlayback` shows step 1 of that walk: request `MarkFirstMinutePlayedEffect`;
  - when that walk ends (any reason): publish the end state (callout and doors);
  - on a door: request the door's existing action effect (the Guided tour, the log chooser, the starter chooser, the
    quest, the client path);
  - on a domain: request the open and `RememberDiamondDomainEffect`.
- **Snapshot:** `FirstMinuteState(phase, domain, domains, doors, callout, reason)`, where `phase` is one of `NONE`,
  `OPENING`, `PLAYING`, `DOORS`, `DONE` or `FAILED`. The dropdown, the card, the callout and §5.5's line render this
  and nothing else.

### 7.2 The stage: `walkPlayback` owns it (OD-79.7)

- **Naming.** In {Product} *Graph* means charts (the *Graph* side tab, `graph:` spotlight targets, a step view's
  `graph` field), and the processor's node graph is *Topology*. The stage is therefore `topology`, and the toggle
  **Topology | Records** (review, 2026-10-18; r1 said `graph`, which would collide with all three).
- **Where the field lives.** M69 §3.3 is the *step* view allow-list (`WalkSteps.VIEW_FIELDS`); a walk-level field
  goes on `WalkSpec`, beside `title` and `conversation`. **In M79 only shipped walk definitions carry it**: the
  `walk` verb's schema (`VerbSchemas`), saved walks in `ConfigStore`, Settings share and bundles do not gain it, so
  no reply or stored shape the customer's tools read changes. A saved walk that carries an unknown `stage` key keeps
  it in `extras`, as today. Steps can already bring the topology tab forward (`view.tab=topology`, as the shipped
  first-breach journey does); only the layout proportion is new.
- **Ticket.** The stage change a walk requests carries the walk's presentation ticket, as its view changes do, so it
  is not read as an outside view change (`WalkViewChanged`), which ends a walk today.
- While a walk with `stage=topology` is showing, the node publishes `stage=TOPOLOGY`. When it ends, the node restores the stage it recorded at play time, **except** for a walk
  of origin `FIRST_MINUTE`, whose stage is held for **first contact** (§5.3).
- **Facts:**
  - `StageToggleRequested(TOPOLOGY|RECORDS)`, from the canvas header's **Topology | Records** toggle;
  - `FirstMinuteDoorChosen` and `FirstMinuteCardClosed` (also handled by `firstMinute`);
  - a new log generation, which also ends first contact, **except** a generation opened by `firstMinute`'s own
    `OpenDiamondEffect` (a domain chosen from the dropdown, §5.2 item 3; its `OpenRequest` launch is `FIRST_MINUTE`).
    Without this exception the first domain change would drop the graph out of centre stage. A domain change does
    end the showing walk, as every new generation does (M69 §3.8); the card and the stage stay, and ▶ replays the
    domain's own walk.
- **Snapshot:** `WalkPlaybackState` gains `stage` and `origin`. `WalkPlayRequested.origin` is a free string today
  (`"assistant"`, `"the Reports tab"`, `"reel"`, `"start-page"`, `"start-page demo"`) that the node only logs; M79
  stores it in the node so that `FIRST_MINUTE` can be branched on. The frame lays out from `stage`. `MainFrame` sets
  no layout of its own beyond the initial build.
- **Revisitable** (the owner: *"we can always change"*). The default is decided in one place, so changing it later is
  a one-line change in the node, with A1.3's tests moved to match.

### 7.3 Autoplay advance

- For a walk of origin `FIRST_MINUTE`, when a step reaches `SHOWN` the node requests `ScheduleWalkAdvanceEffect(ticket,
  step, dwellMs)`. The dwell is 250 ms per word of all the step's captions together, clamped to 4–8 s, and is a
  constant in the node. With the generic copy that is about 4.8 s, 4 s, 5.8 s and 7.5 s: about 22 s for the walk. A
  step that is `PARTLY_SHOWN` or `NOT_SHOWN` does not advance by itself; it waits for the person (a step whose claim
  could not be lit must not scroll past unread).
- **The last step.** Its `WalkAdvanceDue` ends the walk (`EndWalkEffect`, reason `COMPLETED`), which is the
  *played through* end of §5.3. A walk the person paced by hand ends as M69 walks do, on Esc, ✕ or an outside press.
- The adapter's non-blocking Swing timer posts `WalkAdvanceDue(ticket, step)`. The node advances only if the ticket and
  step are still current, so a late timer changes nothing.
- ← or → (`WalkNavigated`) switches the walk to manual pace for the rest of the showing. Esc is the ordinary
  `WalkEndRequested`. A click outside ends the walk, as it does today, and lands in the end state.

### 7.4 Static rules (`OneDispatchModelTest` gains)

- No view calls the diamond's open, the walk's play, or a layout change directly. Only the effects' performers do.
- No `firstMinute.*` key is written outside `MarkFirstMinutePlayedEffect`'s and `RememberDiamondDomainEffect`'s
  performers.
- No start-page or callout string about the first minute is composed outside `FirstMinuteLines`.
- No timer advances a walk except through `WalkAdvanceDue`. **One exists today:** `WalkReelRecorder` (the capture
  tool) advances a walk on its own `javax.swing.Timer` by posting `WalkPlayRequested` for the next step. M79-1 moves
  it onto `ScheduleWalkAdvanceEffect` (it is the same need), or the rule names it as the one exemption; it may not
  pass by being missed. (`TopologyPanel`'s 700 ms *▶ Play* record stepper is not a walk and is out of scope; the
  first minute's *▶ replays the walk* control must be named so that it is not confused with it.)
- `firstMinute.*` keys are a machine family in `KnownKeys.CONFIG_FAMILIES` (never `PROFILE_FAMILIES`), so they are
  owned rather than preserved as unknown keys, and are never exported by Settings share.

## 8. M79-2 — quiet evidence wording

### 8.1 The chip

The Context column's verification rows (file identity, the bundle's *"verified on open; unsigned…"*, the observation
basis) collapse into **one chip** at the top of the column. **The chip names the check that was made, and only a
byte or digest check earns ✓** (OQ-1, resolved by the review of 2026-10-18; it follows M68 D-E6: *"Unverified is
never reported as proven unchanged"*, and unchanged metadata is one of the unverified cases).

**What exists, read from the code** (`feature/claude-code-p3`). There are two vocabularies, and r1 mixed them:

- the **session's identity verdict**, `SessionSnapshot.logIdentity`: `VERIFIED` (Follow compared every byte already
  read), `UNVERIFIED`, `REPLACEMENT`, `REOPENED`, or **null**. Without Follow it stays null for the life of an
  ordinary open, the diamond's and every bundle's included (`ReadThroughIdentity` is metadata-only and reports
  nothing when it sees no change);
- the **metadata observation**, `FileObservation` (`log.freshness`): `unchanged-metadata`, `changed-on-disk` or
  `unknown`, over size, modification time and file key, with the basis *"…unchanged metadata does not prove
  identical bytes"*. It is **computed in the frame** (`MainFrame.logFreshness()`, on each `context` build and
  Project-panel refresh), not in the session;
- separately, the **read identity** (`FileReadIdentity`: SHA-256 of the exact bytes the indexer consumed, null if
  size, time or key moved during the read) and, for a bundle, `EvidenceBundle.verify` (every member's size and
  SHA-256 against the manifest, at open; unsigned; the unpacked working copy is not re-checked afterwards).

| What the session holds (in order; the first that applies) | Chip |
|---|---|
| `logIdentity` is `UNVERIFIED` or `REPLACEMENT`, the metadata observation is `changed-on-disk`, a bundle failed to verify, or the read identity is missing | **Changed since opened ⚠** (or *Could not be checked ⚠* when the cause is a missing read identity or a failed bundle) |
| a shipped asset (§6, §9) whose read identity matches the jar's manifest | **Matches the shipped copy ✓** |
| an evidence bundle that verified on open | **Bundle checked on open ✓ · unsigned** |
| `logIdentity` is `VERIFIED` (Follow) | **Re-read: unchanged ✓** |
| an ordinary log, `logIdentity` null, metadata `unchanged-metadata` | **Size and date unchanged since opened** (no ✓: a metadata check, stated as one) |
| an ordinary log with no observation yet (`unknown`) | **Not checked since opened** (no glyph) |

- **Rule 9.** The chip renders the snapshot only, so M79-2 moves the metadata observation into the session: the
  frame performs the observation as an effect and reports `FileObservationObserved(generation, state, basis)`, the
  node publishes it, and `context`, the Project panel and the chip all read the published value (no second
  computation in `MainFrame.logFreshness()`). The `context` field keeps its name, shape and words (A2.3).
- **Details on click:** a popover lists each existing row with its prose **verbatim**, including
  *"size, modification time and file identity; unchanged metadata does not prove identical bytes"* and the unsigned
  caveat. The auditor's words are kept, one click down, and the summary never contradicts them.
- **Reuse.** The ⚠ row's words agree with the identity banner the records table already shows
  (`LogTablePanel.identityBannerText`) and with M68.7's chart and detail-pane verdict; `EvidenceChipLines` takes them
  from the same source. The chip is a `Chip`.
- **The chip's words** come from `EvidenceChipLines`, which reads the snapshot only. The glyph is never the only
  signal: each row carries words.
- **Machine readers keep everything.** `context` and the MCP replies keep their full fields, and
  `sample-conversations.md` does not change. This is a display change.

### 8.2 Product-term tooltips

**Not "glossary"**: in {Product} the glossary is M38.2's pointer to the *project's* domain vocabulary
(`AppConfig`, *Project ▸ Glossary…*, a Settings-share category). Product terms are a different thing and get a
different name. One resource, `help/terms.properties` (beside the existing `help/help.html`), feeds the tooltips
and the docs site's terms page (DOCS-IA's `reference/glossary.md`, under its own heading *Product terms*). A test pins that they agree
(A2.4):

| Term | Tooltip |
|---|---|
| Mongoose | *The Fluxtion server that hosts event processors and can write their audit logs.* |
| GraphML | *A file describing a processor's graph: its nodes and how they connect.* |
| `.fexp` | *An evidence bundle: one file holding an investigation (logs, graph and notes, with their checksums), checked against those checksums before it opens. Unsigned: it shows a file was changed, not who sent it.* |
| Fluxtion key | *The licence file needed only to regenerate a processor. Reading logs never needs it.* |

Tooltips attach where the term first appears on a surface: the start page's openers, the Context column and Settings.
They also attach as each control's accessible description. **Reuse:** `UiText.tooltip()` and `ActionChip`, which
already sets the accessible name and description; the *Fluxtion key* entry reuses the key card's existing `why`
text, and the `.fexp` entry the *Evidence bundles* page's definition, so there is one wording for each.

## 9. M79-3 — the broken-diamond quest (OD-79.4)

### 9.1 The teaching bundle

- **A real run of a deliberately buggy commit** in `fluxtion-diamond` (D-2), packaged as a `.fexp` by the ordinary
  capture, with the commit SHA in its provenance.
- **The bug** (commit `6bff0d67`, *"Teaching: deliberately broken diamond (do not merge)"*): in `C.recalculate()` one
  pair of lines is swapped, so `value = next` is assigned *before* `changed = next != value` is computed, and `c`
  always reports `changed: false`. Regenerating produced dispatch code and GraphML identical to `main` (the dispatcher
  does not depend on node bodies), so no generated file changes. The unchanged `DiamondTest` fails on that commit,
  which is the defect's own regression witness.
- **What the run shows** (read for this revision): the event script is the generic one (x = 2, 3, 3, 4).

  | Record | Event | Entries | What it means |
  |---|---|---|---|
  | [2] | `X[value=2]` | `a`, `b` (changed), `c` (`value: 6, changed: false`), `d` (106, invocations 1) | `c` already lies, but `b` changed, so `d` runs anyway: no symptom |
  | **[3]** | `X[value=3]` | `a` (changed), `b` (`value: 1, changed: false`), `c` (`value: 9, changed: false`) — **no `d`** | **the answer**: `c` went 6 → 9 yet reported no change, so neither parent was dirty and `d` was not called. It should have run and shown 109 |
  | [4] | `X[value=3]` | `a` (`changed: false`) only | correct: the repeat stops at the top, as on `main` |
  | [5] | `X[value=4]` | all four; `d` 212, **invocations 2** | `b` changed, so `d` runs; the counter shows it missed one |

  On `main`, record [3] has `d` with `value: 109, invocations: 2`.
- **Why the goal is *"did not run when it should have"*.** With a single planted bug the reverse (*d ran when it
  should not have*) cannot be shown on the repeated input: there `a` returns `false`, so the guards never call `c` at
  all, whatever `c`'s code does. The quest's goal is therefore the absence the first minute taught, now as a fault.
- **Exactly one record is the answer.** Record [3] is the only record where `d` has no entry although a parent's
  value changed. It is findable by reading the log and the graph, without AI: the walk taught that a missing entry
  means *not called*, and `c`'s own entry contradicts itself (`value: 9`, `changed: false`).
- **Pinned facts** (computed for this revision and checked independently):
  - log `teaching/broken-diamond-audit.yaml`, SHA-256 `8a2817ee2644e382f3b362ff926cfeef6124bbf361350dd9baa515a7f0afc0f4`;
  - GraphML: byte-identical to §6.4's generic `DiamondProcessor.graphml` (`7ebd36b0…`);
  - the answer's record digest, `WalkIdentity.recordDigest(HeapLogStore.fromFile(log).rawText(3))`:
    `sha256:7e31f039a2e5aa891bf9243a87c73922ea73c83f73121f7fe01e9db1fa870d11`;
  - its salted form, `SHA-256("m79-quest-v1:" + recordDigest)`:
    `883c529b6b139c1083e157272d85060fc874aa7c6cef802ea8ff2cb2366bb380`;
  - the fixed comparison: `main`'s record [3] digest is
    `sha256:2c07ecfbdf78ed1d36fb60a3c9ae5d8285dc66d1f43aa9d39c29be5bb654e902`, with `d = 109`.

  The bundle (`.fexp`) is built from these files by the ordinary capture in M79-3, and its own SHA-256 goes into the
  quest manifest then; it is not known before.
- **Labelled a teaching bundle, by digest.** The bundle's notes say *"Teaching bundle: a planted bug in a demo
  processor"*. That sentence is the exporter's claim, so {Product} does not rely on it. The label *"Teaching bundle
  (ships with {Product})"* appears in the Context column and the bundle's open line only because the bundle's SHA-256
  matches the quest manifest in the jar. A copy that has been edited is an ordinary bundle.
- It ships in the jar beside the diamond set, under the same naming rules (§6.3, rule 1).

### 9.2 The quest

- **The door:** *Solve a real bug (5 min)* on the start page (Learn) and on the first-minute card. One act opens the
  bundle (verify, then a disposable copy, as M70 does) and shows the quest card.
- **The colleague's note** (the bundle's framing, shown first): *"After x went from 2 to 3, d still says 106. It
  should be 109, since b is 1 and c is 9. Which record shows where it went wrong, and why?"*
- **The goal:** *"d did not run when it should have. Flag the record that proves it."* Three hints, revealed one at a
  time, are words from the quest manifest. They may point at the method (*which nodes have an entry in each
  record?*; *compare each node's value with its `changed`*), never name the record.
- **Completion is {Product}'s, by record digest.** The quest manifest stores
  `answer = SHA-256("m79-quest-v1:" + recordDigest)` (above). It does not store the index. **This hides the answer
  from a casual reader; it is not a secret**: the log has six records and the salt is in the jar, so anyone can test
  all six. That is acceptable for a teaching quest and the spec claims no more. **There is no flag fact today**
  (`feature/claude-code-p3`): flags are frame state (`MainFrame.flaggedRows`, a set of model row indexes, and
  `findings`), set by `toggleFlags` (F key, menu, toolbar) and `flagRowsFromAction` (the socket), with nothing
  recording which. M79-3 therefore routes flags through the session first (rule 9): a `FlagToggled(generation,
  row, recordDigest, source)` fact posted by both entrances, the digest computed from `LogStore.rawText(row)` in the
  same EDT turn, and a node that owns the flag set the table renders. That is a migration of an existing surface the
  customer uses daily, so it is sized as one (§17) and carries its own no-change checks (below). `quest` compares the two and publishes *Solved*, naming the record
  and where the flag came from (*this window*, or *over the socket*: an AI client or a script; the socket cannot tell
  which). **A model's words never complete it.** An assistant that says "solved" changes nothing; only the flag on the
  right record does.
- **Works with No AI.** No step needs an assistant, a key or a network. A1-style tests run with the profile `OFF`.
- **Rule 9:** a `quest` node, with parents `openBundle` and `openLog`. Its facts are `QuestStarted(questId)`,
  `QuestHintRequested`, and the flag fact. Its effects are `ShowQuestEffect` and `RecordQuestSolvedEffect` (machine
  tier: `quest.<id>.solved`). The card renders `QuestState`.
- **The customer's flags are unchanged.** The new digest field is additive on the flag fact only; the `flag` verb's
  request and reply, `context.flags` and saved flags keep their shape (A2.3's byte-identity check extends to them in
  M79-3).

## 10. M79-4 — the docs front door

- **One *Start here* page** (`docs/site/start-here.md`, first in the nav). It opens with a GIF: a click on a record,
  then the graph lighting, with `d` dark on the repeated input. Under the GIF are three cards, **Learn / Investigate /
  Build**, with the app's labels. The GIF is generated by the capture harness under an isolated home, from the
  diamond's generic run only, and every frame is read before commit (rule 1, §18).
- **The five getting-started pages become one.** *Quick start*, *Guided start*, *Playground to analyser* and *Spring
  authoring step by step* fold into *Start here* and the user guide. *Install & run* stays as reference. The old pages
  stay out of the nav for one release as one-line pointers, for external links, then go. This lands with, or after,
  the owner-approved DOCS-IA restructure, and M79-4 supplies its front door.
- **Conflict with DOCS-IA, for the owner (OD-79.11).** The nav the owner approved on 2026-10-17
  ([`docs-audit-2026-10-17.md`](../handoff/docs-audit-2026-10-17.md) §5.1–§5.3) already has a `start-here.md`, but
  with four or five doors (incident with your own client, ask inside, build with Claude, the demo, and admin), a home
  page of four doors (*Diagnose, Build, Start, Admin*), **eight** *Get started* pages kept, `start/prompt-packs.md`,
  `teach/philosophy-packs.md` and `reference/glossary.md`. M79-4's three doors, *five pages become one* and SC-D1's
  target of 1 contradict that approval, and *Investigate* (the app's work-mode word, `CanvasHandoff`) is DOCS-IA's
  *Diagnose*. M79-4 does not override an approved decision by being written later: it either amends DOCS-IA §5.1/§5.3
  explicitly, with the owner's decision, or it adopts DOCS-IA's doors and the start page's three doors map onto them.
  Until OD-79.11 is decided, SC-D1's target reads *the getting-started entries DOCS-IA approved; 0 label mismatches*.
- **The nav's current names** (`mkdocs.yml`): *Guided start with an AI assistant* and *Playground to analyser in 10
  minutes* are the real titles of two of the five.
- **The home page says what it is:** a **canvas** where you **learn** how an event-driven system behaves, **build**
  one, and **understand** what it did, from its own evidence. The words *canvas*, *learn*, *build* and *understand*
  each appear above the fold (A4.2).
- **Packs defined and listed:** a *Packs* page says what a **prompt pack** is. It is learning entries: words placed in a
  draft, or copied for your own client. Nothing runs them. The page lists the packs that exist: the sample pack (a
  docs page today, `user-guide/sample-prompt-pack.md`; no pack ships in the jar yet), M79-5's *Your project, for your
  own Claude*, and the M74 *Fluxtion philosophy* packs when PR #155 merges (they teach with this same diamond, at
  `dad9b43`, whose generic log is byte-identical to §6.4's). DOCS-IA puts this page at `start/prompt-packs.md`. **Packs stay what they are**; sharing a set-up is Settings share's job
  (§13).
- **Labels match:** `DocsLabelParityTest` reads every bold control label on the docs pages that describe the start
  page, the first-minute card and the quest. Each must equal a **visible** label in the app's label registry. Today
  `StartPanel`'s labels are inline literals, and each chip has a *title* (*Take a guided tour*, *Open sample
  project*, *Open audit log*, the accessible name) and a shorter visible *label* (*Guided tour*, *Sample project*,
  **Audit log**): M79-4 moves them to constants (start-page copy already lives in `AskCatalogue.properties` `start.*`,
  where `FirstMinuteLines` belongs too) and the test compares the visible label. **Reuse** the pattern of
  `MenuDocumentationTest`, which checks the docs' menu paths against `MenuInventory`. The fix for F2 is the app's
  labels everywhere: *Guided tour*, *Sample project*, and *Open audit log*, which M79-1 makes the visible label (it is
  the title today). Known mismatches the test will meet: *Open the sample* (`StartPageNoLogHint`, `start-page.md`,
  and M77 §6.4), and M79's own *Start from a template* against the app's *From template* chip and the menu's *New
  project from template…*; M79-1 picks one wording for all three. F2's *Open the demo log* is in a screenshot, not in
  docs text, so the capture harness's regenerated shots, not this test, retire it.

## 11. M79-5 — "Connect your own Claude" (OD-79.6)

- **One action:** **Connect your own Claude** (the Build door, the start page). It opens the M77 wizard at Screen 2b
  with *Claude Code* chosen. That is M77-3's path, unchanged: detection, the external-access confirmation, *Register
  for me…* with the exact command, and the live check. It is the person's act, so OD-79.5 holds: nothing about AI
  appears unasked.
- **Pointed at the project.** When the live check is ✓, the Summary's suggested first action (M77 §6.4) becomes **Copy
  the first prompt**, from the shipped pack *Your project, for your own Claude*. Its entries are words for the person to
  paste into their own client. For example: *"Call analyser_context first. Read this project's runbooks and vocabulary
  from the pointers it returns, then tell me what this system does and what its audit log covers."* The pack also loads
  into the start list for the session. **Today a pack loads only by a drop** (`StartPromptPackDropped`); M79-5 adds a
  fact for loading a shipped pack (`StartPromptPackShipped(packId)`, handled by `startPagePrompt` as a drop is), not a
  view call. M77 §6.4 is amended to match (its *your own client* row's *Copy a first question* becomes this action).
- **The project context is already there** (M38): `context` returns the runbook, vocabulary and environment pointers.
  The bridge's `instructions` (`McpBridge.INSTRUCTIONS`, also served at `/manifest`) **do not** say to call `context`
  first today (checked 2026-10-18; only the Ask catalogue's templates do). M79-5 adds that one sentence and pins it
  beside the existing pins of that text, `SpotlightGuidanceIsAtEveryEntranceTest` (there is no
  `BridgeInstructionsTest`). **This is the one M79 change the customer's external Claude sees**: the instructions are
  read by every MCP client at connect. It adds a sentence and changes no verb, field or reply, but it is listed for
  the owner (OD-79.12) because their developers' daily client reads it.
- **No new verb, transport or consent.** M79-5 is composition only.

## 12. The scorecard

Scored by the robot walk (a virgin LLM driving the real `Main` under an isolated `user.home` at 1200×800) and, where
marked *(insider)*, by repeating the insider review. Where the existing machinery fits, it is reused: the M74
onboarding runner and scorecard (PR #155, `tools/m74_onboarding_runner.py`, `SCORECARD.md`) score from the
analyser's own facts and a sealed oracle, and its `pointer` predicate is exactly SC-T9's *answered by pointing at the
record* (a selection, flag or spotlight resolved to record [4]); its dead-end types replace the informal meaning
below wherever a run is scored by it. Each phase's run is recorded under `docs/specs/evidence/m79-<phase>/`. The
two reviews of 2026-10-18 are the baseline. *Hesitation* and *dead end* keep those reviews' meanings: a pause to work
out what to do, and an act that leads nowhere.

| # | Task | Baseline (2026-10-18) | Target | Phase |
|---|---|---|---|---|
| **SC-T9** *(new)* | **Understand the skip:** "why did d not run on the second input?", answered by pointing at the record | not reachable without reading docs | **≤ 1 act, 0 hesitations, 0 dead ends** from first launch | M79-1 |
| **SC-S1** | Start page always-visible words | about 225 | **< 40** (≤ 39 in every AI-line state, §5.4) | M79-1 |
| **SC-T1** | First answer from an assistant | M77's robot walkthrough, **T19**: 6 acts, no dead end (M77 §16) | **unchanged or better.** The wizard still opens at the first Ask; the first minute adds no act to that path | M79-1, M79-5 |
| **SC-C1** *(insider)* | **Prediction correctness:** after the walk, for the two held-out cycles of the generic run, record [3] (`b` unchanged, `c` changed) and record [5] (a fresh change), predict which nodes run, then check by clicking. The prediction is scored only if recorded before any selection of record [3] or [5], and before the shipped README or `run-console.txt` is read (both state the answer) | — | **2 of 2 correct in ≥ 4 of 5 runs** | M79-1 |
| **SC-C2** *(insider)* | **The right flag:** the broken-diamond quest with No AI | — | {Product} confirms the flag in **≥ 4 of 5 runs**, **median ≤ 5 min** | M79-3 |
| SC-E1 | Auditor-prose hesitations on the first view | the stranger's F5 | **0**; F5's sentence not visible without a click | M79-2 |
| SC-D1 | Getting-started pages in the nav; docs/app label mismatches | 5; F2's pairs | **1; 0** | M79-4 |
| SC-T10 *(new)* | **Connect your own Claude:** from the start page to the person's client making its first `context` call, with `claude` installed | the M77-3 count (not yet measured: M77-3 is unbuilt) | **≤ 5 acts in {Product}, 0 dead ends**; the acts in the person's own client (opening it, pasting the prompt) are counted separately, since M77-3's own confirmations (external access, then registration) already use two | M79-5 |

**About the ids and baselines** (review, 2026-10-18). The `SC-` ids are this spec's own; no earlier table defines
SC-T1…SC-T8, so *(new)* means new to M79's list, and SC-T1's baseline is M77's T19. The two reviews of 2026-10-18 that
give the other baselines (*about 225 words*, F5's hesitation, F2's pairs) are not in the repo. Before the first
scored run, their relevant passages, sanitised under rule 1, go in `docs/specs/evidence/m79-baseline/`, or the
baselines are re-measured there by the robot walk on the current build. A target with no recorded baseline is a
target, not an improvement claim. **On the targets:** five runs (SC-C1, SC-C2) detect only a large failure rate; they
are a smoke gate, not a measurement, and are reported with their counts. SC-S1 is tight: the start page as drafted in
§5.4 counts about 38 words in its longest AI-line state, so any added word (an empty *Recent* placeholder such as
*No recent projects*, for example) breaks it; §5.4's rule counts such a placeholder, so the *Recent* block is hidden
when it is empty.

## 13. Follow-on, not M79: the colleague extension of Settings share

The owner liked a *colleague starter pack*: a colleague sends a ready set-up, and the newcomer starts where the team
is. An early design added `from:` and `setup:` lines to prompt packs. **It is replaced here, before it was built,
because M15 Settings share already does most of it.** M15 already has Project ▸ *Export settings…* with ticked
categories (source roots, Maven repositories, event processors, graphs and focuses, reports, view, assistant caps, LLM
fields but never the key, the glossary pointer, environments, saved analyses, report destinations and runbook
locations). It also has a `.fluxtion-settings` file sent by Copy, Save or Email, and Project ▸ *Import settings…*,
which shows a per-category summary before anything changes, offers Merge or Open as project, and enforces a whitelist
on import. Extending M15 keeps **one share format, not two**. **Packs stay what they are: learning entries.**

The extension:

- **New export categories:**
  - an **AI set-up recommendation**: the mode (safe / full / client / none) and whether external access is recommended.
    It is off by default and never carries a token;
  - **starting points**: a pinned example (a commit, plus an optional sha256) and pack or bundle pointers, as
    project-relative paths or pinned example entries.
- **A *from* line:** the exporter's claim, shown as *"says it is from …"*, plus the file's short sha256 fingerprint in
  the import summary (OD-79.9).
- **Drop to import:** dropping a `.fluxtion-settings` file on the start page opens the existing import summary. Today
  it is reached only from the Project menu.
- **Consequential categories are never applied by import** (OD-79.8). Ticking AI mode, external access or *fetch an
  example* opens the M77 wizard, or the example's download confirmation, preselected and tagged *suggested by the
  settings file*, with the same consents. The existing categories keep their current import behaviour. *Harmless rows
  default to Yes* maps onto the existing ticked categories.
- **Signing** is one later decision for settings files, packs and bundles together (OD-79.9). **No `.fexp` fetch by
  URL in v1:** the bundle is sent alongside (OD-79.10).

**Proposed, not decided: a *Repository* category** (owner idea, 2026-10-18; recorded by the review, not yet an owner
decision). It answers *"the colleague sent a set-up; where is the code?"*, so that pairing and source spotlights work
on the newcomer's machine.

- **What it carries:** a repository URL, a pinned commit, and an optional SHA-256 of the archive. Nothing else: no
  branch name to follow, no command, no path outside the folder the person chooses.
- **On import it is consequential** (OD-79.8): its row starts at **No**. Ticking it offers *"Fetch
  <host>/<owner>/<repo> @ <commit> into <folder> and use it as the source root"*, and the confirmation **names the
  publisher first**, marked third-party unless it is the Telamin organisation, as the example download does.
- **A public repository** reuses the pinned example download whole: `ExampleSource` (an HTTPS archive URL on
  `github.com`, `codeload.github.com` or `gitlab.com`, a pinned commit, an optional SHA-256), `ExampleArchiveClient`
  (the one fetch: no git, no clone, no redirect off an allowed host, a byte cap, the digest computed before
  anything is unpacked), `ExampleDownloadDialog` (the publisher line, the integrity line), the `ExampleDownload`
  session node (rule 9: every step decided there, fetch and unpack as effects answering with facts) and
  `TemplateArchive` in its example mode (every containment check and cap, and **no executable bit on any file**).
  New: the confirmation also lists the files that look runnable (root scripts, wrappers), so the person knows
  what they would be choosing to run themselves; nothing marks or runs them.
- **A private repository** cannot use the archive fetch: it would need credentials {Product} must never hold. The
  confirmation instead shows the exact commands, built by {Product} from the parsed URL and commit, never copied as
  text from the file: `git clone --no-checkout <url> <folder>` then `git -C <folder> checkout <commit>`, run with the
  person's own git credentials. The person runs them, or **Run it for me** runs exactly the commands shown, after a
  confirmation, as M77-3's *Register for me…* does. The URL is accepted only as `https://` or `ssh://`/`git@` on a
  host the person confirms (never `file:`, `ext::` or a local path), with no options or arguments taken from the
  file, so a settings file cannot smuggle a flag or a transport into the command. The commands do not recurse
  into submodules and run with hooks switched off (`-c core.hooksPath=/dev/null`). Git never installs a cloned
  repository's own hooks, but the person's own git configuration still applies (an init template's hook, or a
  globally installed filter such as Git LFS's smudge), and the confirmation says so.
- **After the fetch:** the source root is set to the fetched folder through the existing machinery, not a new path:
  `PathForm` and the project's workspace anchor (`workspaceRoot`, M38.6; Settings share already exports it, and source
  roots as project-relative, `~/…` or absolute forms) and the M73 project offer for an unpacked folder, so the
  person confirms that too. Then pairing and source spotlights work as for any source root.
- **Never:** run anything from the repository; import without the person's confirmation; or take a path from the
  file outside the chosen folder. **Full mode later still needs folder trust**: fetching a repository grants Claude
  Code nothing.
- **Open for its own spec:** whether a private repository's host must be one the person has used before; how a
  fetched root is shown in the Project panel (fetched from …, at commit …); and signing (OD-79.9).

Its own spec amends `completed/spec-settings-share.md` when it is scheduled, and the whitelist test grows by the new
categories.

## 14. Other follow-ons (recorded, not in M79)

- **The criterion chip** (M76 slice 1), and **the before/after run comparison**: the diamond is their natural first
  fixture.
- **Keyless authoring through pre-generated variants:** the domain variants show that a set of real, pre-generated
  runs can stand in for a compile the person cannot run without a key.
- **More domains:** each is a new real run under §6. None may be added by relabelling.
- **Conversation journeys** (`spec-onboard-assistant-journeys.md`, OA-D4–D6) already ship a walk with pre-authored
  digests inside a verified `.fexp`, built from a real run by `tools/capture-journey.py`, with a catalogue page whose
  facts a test re-derives. A later revision may ship each domain as such a journey bundle (the `.fexp` check would
  then make *Bundle checked on open ✓* true of the first minute too). M79 keeps plain assets, because OA-D6 says
  opening a journey never autoplays it, and the first minute's autoplay is the point; if they converge, that rule
  needs the same `FIRST_MINUTE` exception.

## 15. Acceptance tests (rule 8: ★ marks a test with a wrong-result witness)

Headless unless marked *frame* (a registered frame suite under Xvfb in CI). Each phase's tests ship in its PR.

**M79-1**

| # | Test | Catches | Witness (the mutation that must turn it red) |
|---|---|---|---|
| ★ A1.1 | `firstMinute` over every combination of §5.1's inputs: it plays only when all hold; it marks played on step 1 shown and not before; a deferred launch leaves the key unset | autoplay on a returning user, under `--rest`, over a recovery offer, or twice | mark played on the startup fact; or ignore `recoveryPending` |
| ★ A1.2 | autoplay advance: a `WalkAdvanceDue` for an old ticket or step changes nothing; ← or → stops auto-advance; Esc lands in `DOORS` with the callout and the three doors | a late timer skipping a step; a skipped walk with no way on | drop the ticket comparison |
| ★ A1.3 | stage: `TOPOLOGY` while the first-minute walk shows and through first contact; `RECORDS` after a door, a new generation or the toggle; an ordinary walk restores its prior stage; the frame lays out from the snapshot alone | the graph stuck centre stage for investigation (OD-79.7), or a layout set by the view | keep `TOPOLOGY` after a door; or set the layout in `MainFrame` |
| ★ A1.4 | E2 + E3: per domain, GraphML node ids equal the log's, and the pairing is declared and matching; the five graphs are isomorphic; no name table keyed by domain exists | a relabelled log, a drifted topology | ship the generic log under the accounting GraphML |
| ★ A1.5 | E4–E6: per domain, records [2]–[5] are traced; record [2] has four node entries and record [4] only the top node's; the record digests equal §6.4's; coverage reports every node able to log; an edited copy opens without the dropdown or the bundled label; an edited extracted copy is re-extracted; a substituted step-3 record makes step 3 *not shown*, with no caption on any of its targets | "d did not run" shown over evidence that does not say it | drop the record-basis rule of E5, so `topology:node:d` keeps its caption |
| ★ A1.6 | the start page's word budget (§5.4's counting rule): ≤ 39 in every AI-line state, at 1200×800 *(frame)* | the page growing back | restore the *Connect an assistant* group's caption above the fold |
| ★ A1.7 | §5.5: a manifest mismatch or a failed open → no walk, not marked played, one start-page line with the open's reason, no dialog | a broken asset hidden, or a modal at an empty screen | autoplay without the manifest check |
| ★ A1.8 | `OneDispatchModelTest`'s §7.4 rules | hand-placed dispatch | call `openDiamond` from the dropdown's listener |
| ★ A1.9 *(frame, real `Main`, isolated `user.home`, 1200×800)* | **The first-minute walkthrough:** first launch → the diamond opens with the graph centre stage → four steps → step 3 selects the record with no `d` entry and `d` is dark → the end state → one click on a record lights its cycle. Then relaunch → no autoplay, and no session-recovery offer for the diamond (the start page) | SC-T9's path regressing; the diamond offered back as "your session" | write `played` on Esc only, so a played-through walk replays |
| A1.10 | `DiamondAssetsTest`: every file's SHA-256 matches the manifest; the source ships (E7); the sweep covers the set (§18) | a hand-edited asset, a leaked name | — |

**M79-2**

| # | Test | Catches | Witness |
|---|---|---|---|
| ★ A2.1 | the chip for every row of §8.1, from the snapshot alone; no row without a byte or digest check shows ✓; the metadata observation is computed once (in the effect) and `context` reads the published value | a metadata check shown as verified (OQ-1); an unassessed log shown as checked | map `unchanged-metadata` to ✓; or recompute freshness in the view |
| ★ A2.2 | the details popover holds every existing row's prose verbatim | the caveat lost behind the summary | drop the basis sentence |
| A2.3 | `context` and the MCP replies are byte-identical before and after M79-2 for the demo fixture | a display change leaking into the protocol | — |
| ★ A2.4 | glossary parity: the four tooltips equal the docs glossary entries; each term's first appearance on the start page, the Context column and Settings carries the tooltip and the accessible description | a definition drifting | edit one side only |

**M79-3**

| # | Test | Catches | Witness |
|---|---|---|---|
| ★ A3.1 | completion: a flag on record [3] (digest `7e31f039…`) → *Solved*; a flag on record [2], [4] or [5], or an assistant message saying "solved", → not solved; a flag over the socket is attributed to the socket; `main`'s record [3] (digest `2c07ecfb…`, same index) → not solved | a model completing the quest, a near miss accepted, an index matched across runs | compare record indexes instead of digests (the `main` log's record [3] then passes) |
| ★ A3.2 | the teaching label appears only when the bundle's SHA-256 matches the quest manifest; an edited copy is an ordinary bundle | a label taken from the bundle's own notes | read the label from the notes |
| ★ A3.3 | the whole quest with the profile `OFF` and no network | a hidden AI or network dependency | require an assistant for hint 3 |
| ★ A3.4 | the manifest does not name the answer's index; the answer is salted and equals `883c529b…`; the manifest's log SHA-256 equals `8a2817ee…` | a spoiler in the jar, a re-run substituted for the pinned one | store the unsalted digest; or ship a re-run log |

**M79-4**

| # | Test | Catches | Witness |
|---|---|---|---|
| ★ A4.1 | `DocsLabelParityTest` | F2 returning | the docs' *Open the demo log* |
| ★ A4.2 | the home page's four words above the fold; the nav has one getting-started entry; `mkdocs build --strict` passes | F3 returning | — |
| A4.3 | the GIF is generated by the harness under an isolated home; every frame is read (rule 1) | a real name in a frame | — |

**M79-5**

| # | Test | Catches | Witness |
|---|---|---|---|
| ★ A5.1 | **Connect your own Claude** opens the wizard at 2b with Claude Code chosen and nothing applied; it shows the same confirmations as M77-3 (no consent skipped) | a shortcut around M77's consents | apply external access on the button press |
| ★ A5.2 | after the live check ✓, the Summary's first action is the pack's first entry; the pack is words only (`PromptPack` parses it, with no example entry) | a pack that runs something | an entry with a link |
| A5.3 | the bridge `instructions` say to call `context` first (pinned with `SpotlightGuidanceIsAtEveryEntranceTest`), and the rest of the text is unchanged | the teacher not pointed at the project | — |

## 16. Owner decisions

| # | Decision | Owner (2026-10-18) |
|---|---|---|
| **OD-79.1** | The aha is the diamond, in the person's own **domain**: the same topology with domain names, a dropdown (mathematics, accounting, logistics, trade processing, a generic default). **Lead, evidence integrity:** each domain is a real run of a real variant, generated and run once; display relabelling of one log is not allowed. The variants are built in `fluxtion-diamond`; their logs and GraphML ship in the jar. | **Accepted** |
| **OD-79.2** | The first launch **autoplays** the diamond walk once (Esc skips); first run only (no recent project); never again. Amends M36 D-S1 (§3). | **Accepted** |
| **OD-79.3** | The diamond log and GraphML ship inside the jar, like the DEMO set. | **Accepted** |
| **OD-79.4** | A planted-bug teaching bundle, *the broken diamond*: a real run of a deliberately buggy commit, labelled as teaching; {Product}, never the model, confirms the right record by digest. | **Accepted** |
| **OD-79.5** | **The pivot:** AI setup leaves the first launch; **supersedes M77 OD-77.1** (§3, with the reason). The wizard opens at the first Ask, Send or Start building; the start page offers AI only as a quiet line. | **Accepted; confirmed** |
| **OD-79.6** | **The customer insight:** for developers who already use an AI client, the fastest path is their own Claude plus project context; the start page and packs make it first-class, as one action (M79-5). | **Accepted** |
| **OD-79.7** | The graph is centre stage **only during walks and first contact**; the records table stays the main view for investigation, because the paying customer's developers use it daily. The toggle is `walkPlayback`'s (rule 9). **Revisitable** (*"we can always change"*). | **Accepted** |
| **OD-79.8** | Colleague follow-on (§13): harmless rows default to Yes, which maps onto M15's existing ticked categories; consequential rows (AI mode, external access, fetching an example) start at No and only preselect the M77 wizard or the download confirmation. | **Accepted** |
| **OD-79.9** | Colleague follow-on: provenance shown as *"says it is from …"* plus a short sha256 fingerprint; signing is one later decision for settings files, packs, starters and bundles together. | **Accepted** |
| **OD-79.10** | Colleague follow-on: no `.fexp` fetch by URL in v1; the bundle is sent alongside. | **Accepted** |

**The two questions left for the first review, answered** (independent review, 2026-10-18):

- **OQ-1 — yes, *verified* overclaims; resolved in §8.1.** For an ordinary log the only on-disk check is metadata
  (size, modification time, file key), computed in the frame, and the session's identity verdict is null unless
  Follow is on; M68 D-E6 already rules that unchanged metadata is *unverified* and never reported as unchanged. A
  bundle is checked byte-for-byte at open (SHA-256 per member, unsigned) and its working copy is not re-checked. The
  chip therefore states the check (*Size and date unchanged since opened*, with no ✓) and keeps ✓ for byte or digest
  checks (*Matches the shipped copy ✓*, *Bundle checked on open ✓ · unsigned*, *Re-read: unchanged ✓*).
- **OQ-2 — accept the 4–8 s dwell, with three refinements (§7.3).** The dwell counts all of a step's captions; a
  step not fully shown waits for the person; the last step's timer ends the walk into the doors. Advance with **→**
  or the strip's **Next ▶**, go back with **←**, skip with **Esc**. **Not** *any click advances*: M69 §3.7 classifies
  every press outside the strip as *end the walk*, and here that is right, because the press the end state invites is
  a click on a record; a click that advanced instead would swallow it. Tune the constant on the first robot run.

**Raised by the review, for the owner** (none blocks writing M79-1's node; each blocks the part it names):

| # | Question | Recommendation |
|---|---|---|
| **OD-79.11** | M79-4 against the approved DOCS-IA nav (§10): amend DOCS-IA §5.1/§5.3 to three doors and one getting-started page, or map M79's doors onto DOCS-IA's? | map onto DOCS-IA (it is approved and its audit is the newer evidence about the docs); keep *Learn / Investigate / Build* as the app's words and make DOCS-IA's *Diagnose* door read *Investigate* |
| **OD-79.12** | M79-5's added sentence in the bridge `instructions`, which the customer's external Claude reads at every connect (§11) | add it; it changes no verb or reply, and it is what their developers' prompts already do |
| **OD-79.13** | Should the Investigate and Build doors also set *Work mode* (§5.4)? | not in M79; a door opens a thing, the posture stays the person's |
| **OD-79.14** | Step 4's *"That is proof, not silence"* against M76's ban on *proof* (D-7) | keep it, conditioned: M76's rule becomes *absence is proof only in a traced record*, which is the format spec's rule |
| **OD-79.15** | The first-run condition (§5.1): a fresh config **and** no recent project, file or bundle, not *no recent project* alone | yes; it is what protects the paying customer's developers on upgrade |
| **OD-79.16** | A first-minute-only session is not offered for recovery (§5.1) | yes |

## 17. Phasing and effort — one PR each, under about 5,000 hand-written lines

Generated `SessionProcessor` lines are not counted. Each PR is a reviewed feature branch with its CHANGELOG lines
(rule 2), its tests green (rule 4), and its scorecard run recorded (§12).

| PR | Content | After | Est. days | Est. lines |
|---|---|---|---|---|
| **M79-1a — the first minute** | `firstMinute` (with `sessionRecovery` as a parent); `Launch.FIRST_MINUTE`; `stage`, `origin`, autoplay advance, the record-basis caption rule and first contact in `walkPlayback`; the **new topology-centre layout** (the canvas moves out of the right-hand tabs); `WalkReelRecorder` onto the advance effect; the diamond set, its manifest and digest-checked extraction; five shipped walks through the generalised journey effect; the domain dropdown; the end card; no recovery offer for a first-minute session; A1.1–A1.5, A1.7–A1.10 | D-1, D-6, D-7 | 8–10 | ~3,500 |
| **M79-1b — the start page** | the start page under 40 words (`StartPanel`, 1,322 lines today), the collapse, the visible-label changes and constants, the short AI line (or D-3's fallback); A1.6 | M79-1a; coordinate with M77-2 and M77-4, which change the same page | 2–3 | ~900 |
| **M79-2 — quiet evidence wording** | the chip, `EvidenceChipLines`, the details popover; **the metadata observation moved into the session** (§8.1, rule 9); the terms resource and tooltips; A2.1–A2.4 | — | 3–4 | ~1,200 |
| **M79-3 — the broken-diamond quest** | the teaching bundle and quest manifest; **flags routed through the session** (a fact, a node, both entrances; §9.2); the `quest` node; the quest card; A3.1–A3.4 | D-2 (published), M79-1a | 6–8 | ~2,600 |
| **M79-4 — the docs front door** | *Start here* and its GIF; the home page; the folded getting-started pages; *Packs*; the terms page; `DocsLabelParityTest`; A4.1–A4.3 | M79-1b (labels, GIF); DOCS-IA; OD-79.11 | 3–4 | docs, plus ~300 of test |
| **M79-5 — connect your own Claude** | the action and its wizard `origin`; the pack and the shipped-pack load fact; the Summary's first action (M77 §6.4 amended); the bridge sentence; A5.1–A5.3 | M77-3; OD-79.12 | 2–3 | ~700 |
| **Total** | | | **24–32 days (about 5–6½ weeks)**; r1 said 17–23 | |

**Why the estimate rose** (review, 2026-10-18). r1 assumed three things the code does not have: a stage (the
topology is a right-hand tab, so centre stage is a new layout), a flag fact (flags are frame state; making them the
session's is a rule-9 migration of a surface the customer uses daily), and a session-held metadata verdict (it is
computed in the frame). It also put the start-page rewrite, which collides with M77-2 and M77-4 on the same page,
into the first PR; it is split out as M79-1b so that the first minute can be reviewed on its own.

## 18. Public-repo rules for every M79 asset (rule 1)

- The domain variants, the teaching bundle, the walks, the GIF and the screenshots use generic names only (§6.3). No
  real company name appears anywhere, including in prose about the rule.
- `DemoAssetsTest`'s sweep, which parses CLAUDE.md's sweep line at runtime, extends to the diamond and quest assets,
  with their GraphML, source and manifests. It iterates `DemoAssets.files()` only, so the new files join that list
  (or the test iterates the manifest too); a file outside both would ship unswept.
- The capture harness runs under an isolated `user.home`, with only the demo and diamond fixtures, from a jar copied
  under the isolated root, and `firstMinute.played` unset only for its first-minute run. Every frame of the GIF is read
  before commit.

## Revision history

- **r1, 2026-10-18** — **ACCEPTED.** Written from the insider and stranger reviews of 2026-10-18 and the owner's
  decisions OD-79.1–.6 of the same day, read against `docs/m77-ai-setup-wizard` (M77 r4), the M69 walk contract and
  the framework reference's conditional-dispatch rule. Before publication the owner added OD-79.7 (the graph centre
  stage only for walks and first contact, revisitable), confirmed OD-79.5, and accepted three decisions on the
  colleague follow-on. That follow-on was then recast, at the owner's correction, as an extension of M15 Settings share
  rather than a pack with set-up lines, so that there is one share format (§13, OD-79.8–.10). M77 OD-77.1 is recorded
  as superseded in both specs. The diamond variants were published at `fluxtion-diamond` `24d6d74` the same day; §5.2,
  §6.2–§6.4 and SC-C1 are written from those pinned logs, which were read for this revision.
- **r2, 2026-10-18** — **the first independent review**, folded in place. Verified: the pin and all ten SHA-256s
  from a fresh clone; every log's six records, the traced status of records [2]–[5] (`AuditTrace`), and the record
  digests now listed in §6.4; the generated guards; the teaching commit `6bff0d67` (D-2, §9.1), its log digest, the
  answer's record digest and salted answer, recomputed. Corrected: captions say `b`, `c` and `d` all skip on the
  repeat (§1, §5.2); E2's *equal ids* (the GraphML declares more nodes than the log writes); E5 for `topology:node`
  targets, which are not record-bound today; the quest's goal, flipped to *d did not run when it should have* (lead's
  decision, from the real run); the first-run condition and the recovery race (§5.1); `Launch.FIRST_MINUTE`
  (`OpenRequest` has no kind); `stage=topology` (*Graph* means charts); the chip (OQ-1, after M68 D-E6); the
  glossary renamed *product terms* (M38.2 owns *glossary*); the bridge test that does not exist; the flag fact that
  does not exist; the effort (§17). Recorded: reuse of `PlayDemoJourneyEffect`, `DemoAssets`, `MenuDocumentationTest`,
  the M74 scorecard and runner, and conversation journeys; overlaps with M75, M76, DOCS-IA and M77 (whose r5 notes are
  completed in that spec). OQ-1 and OQ-2 answered (§16).
