# M79 — The first minute: the diamond, then three doors

**Status:** ACCEPTED r1, 2026-10-18; **r7, 2026-10-19, brings the spec up to date for implementation** and adds the
Fluxtion philosophy packs to M79's scope as **M79-7** (owner, 2026-10-19): M77's wizard (M77-2's AI line, M77-3's
client path) shipped complete in 1.36.0, so D-3/D-4 are satisfied, not conditional; M75 and M76's specs are now on
`main`, not on branches, and M76's OD-76.7 publish condition is met by §6.4's pin; a new §4 D-8 records the M80
Design-pane overlap (M80 is PROPOSED, not yet merged); M79-7 brings packs 01, 02 and 04 of the already-drafted
`docs/proposals/packs/fluxtion-philosophy/` set into M79, to be published on the docs site as valid, build-tested
prompt packs, with owner questions OD-79.26–.28 raised; **all three accepted by the owner the same day** (move the packs, 04 gets the first `teach:` entry after M79-2, lint fixtures for the published packs). No earlier owner decision is revisited. **r2
the same day folds in the first independent review** (corrections and code
facts in place; owner questions OD-79.11–.19, §16: all accepted; effort as relative sizes, §17). The
owner accepted OD-79.1–.6 on 2026-10-18, then OD-79.7 and the three decisions on the colleague follow-on (OD-79.8–.10,
recorded as an extension of M15 Settings share, §13) the same day (§16). **r5, the same day**: the owner accepted every open decision, OD-79.11–.19, and decided the key questions. **r3, the same day, folds in the lead's creative round 2**: the teacher path moves to phase 2 with a
teacher kit (§11.6), the chip has three states (OQ-1), domain tiles, one caption template, the tooltips cut, the docs
slice narrowed, SC-T11; OD-79.17–.18 and two key questions are open. **r4, the same day, the owner's correction**: phase 2 is the teaching session in the onboard assistant (§11), the external teacher kit becomes M79-6; OD-79.19 is open. **r6, the same day, folds in the second independent review**: code facts re-derived (the start page, the AI line, the launch carrier, the stage field), the `@AfterEvent` probe recorded, and OD-79.20–.25 accepted by the owner: composition-first teaching led by the Spring XML contract (§11.7), the generic run re-pinned to its Spring-authored twin, M79-2's artifact renamed the **teaching conversation**, M79-1a split, the passive log gated on a shipped conversation, and a security review before the private-repository category. Each phase PR gets its own review (the
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
| F2 | Docs and app labels disagree: *"Open the demo log"* / *"Take a guided tour"* in the docs, *"Guided tour"* / *"Sample project"* in the app | **M79-5** (a parity test) |
| F3 | The docs never say *canvas*, *build* or *learn*; they run to about 66,000 words, with five getting-started pages | **M79-5** |
| F4 | Packs are invisible: nothing says what a prompt pack is, or lists one | **M79-5**, **M79-2** (a pack that starts a teaching conversation), **M79-6** |
| F5 | The first view uses auditor prose, for example *"unchanged metadata does not prove identical bytes"* | **M79-3** |

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
  processing, chosen from five picture tiles. The problem is universal, and the person sees it in words they use (OD-79.1).
- G3. **Three doors, not fourteen chips.** The walk ends on *Click any record* and three doors: **Learn**,
  **Investigate**, **Build**. The start page shows fewer than 40 words (M79-1).
- G4. **AI is a quiet line, not a gate.** AI setup leaves the first launch. The wizard opens on the first Ask, Send or
  Start building, as M77 already provides (OD-79.5).
- G5. **Quiet evidence.** One chip with three states, *Evidence ✓ checked* (a byte or digest check), *Evidence · as
  read* (metadata only) and *⚠ changed / unknown* (§8.1), replaces the Context column's verification prose. The prose stays one click away (M79-3).
- G6. **A real bug to solve.** *Solve a real bug (5 min)*: a teaching bundle from a real run of a deliberately broken
  commit. {Product} confirms the answer by record digest, and the quest works with No AI (M79-4).
- G7. **One front door in the docs.** One *Start here* page, a home page that says canvas, learn, build and understand,
  packs defined and listed, and a test that docs labels match app labels (M79-5).
- G8. **Teach in the onboard assistant, then continue live.** A pack (or the Learn door) plays a real, recorded
  teaching conversation in the docked panel, with spotlights on the diamond; *Continue with Claude* starts the live
  session, with the teaching conversation kept as a passive log (M79-2, owner, 2026-10-18).
- G9. **"Connect your own Claude" as a secondary path.** One action connects the person's own client and points it at
  the project context (M79-6, OD-79.6).

**Non-goals**

- No relabelling of a log for display. Each domain is a separate real run (§6, E2).
- No change to the investigation layout. The records table stays the main view for investigation; the graph is centre
  stage only during walks and first contact (OD-79.7).
- No new AI path. M79-6 composes M77-3's client path with a prompt pack; it adds no verb and no transport.
- Not M79: the criterion chip (M76 slice 1), the before/after run comparison, the teaching-scoped hosted key, and the
  colleague extension of Settings share (§13). There are no keyless runs (owner, §16). Each is a follow-on (§14).

## 3. Standing decisions this touches

| Decision | What it says | What this spec does |
|---|---|---|
| **M77 OD-77.1** | The first run shows the wizard's first screen *inline on the start page*. | **Superseded by OD-79.5** (owner, 2026-10-18, confirmed the same day). The first-run AI block is removed before it ships: M77-2 builds the AI line only. **Reason:** the reviews found the AI question was the second thing standing between a newcomer and the picture. Asking how AI should work before the person has seen what {Product} shows makes the question abstract, and it cost the stranger the aha. M77's own entry 5 (the wizard opens at the first Send, Ask or Start) already catches everyone who wants AI, at the moment they want it. OD-77.9 (offer the block to upgraders) and OD-77.19 (no picture in the block) fall with it. M77's §3, §5, §6.0 and §20 record this. |
| **M36 D-S1 / onboarding R6** | The start page IS the first run; no first-run dialog. | **Amended by OD-79.2.** The *no dialog* half is kept: the first minute is a load in the main window, through the ordinary open path, with no window or modal. The *start page is the first run* half changes: with no recent project, the first run is the diamond, and the start page is one door away. |
| **M35.7 / M35.9** | No modal in the load path; nothing fires at an empty screen. | Kept. The diamond opens with an `OpenRequest` whose `launch` is a new `OpenRequest.Launch.FIRST_MINUTE` (the record has no *kind* today: `Launch` is `NONE · COMMAND_LINE · RESTORED · EXPLICIT_RESTORE`). `suppressDialogs()` must return true for it, or the load path's own dialogs (time order, project offer) can still fire over the first minute. `OpenRequest` is frame-side and stops at the frame: the session learns of the launch only through the new `LogOpened.launch` field (§7.2). A failure lands on the start page as one line (§5.5), never a dialog. **Today's sample-project install failure is a `JOptionPane` (`MainFrame.openSampleProject`), so the diamond must not reuse that path.** |
| **M69 §3.3** | The step view is a fixed allow-list. | **Kept unchanged.** The step allow-list (`WalkSteps.VIEW_FIELDS`: `tab`, `filter`, `record`, `graph`, `focus`) gains nothing. `stage` is a **walk-level** field on `WalkSpec`, beside `title` and `conversation`, and only shipped walk definitions carry it (§7.2). A walk still never opens a log. |
| **M69 §3.8** | Playback lives in `walkPlayback`. | Kept and extended: the stage, autoplay advance and the first-contact hold are that node's (§7). |
| **M74 S1** | *Run me through a demo* plays the DEMO journey. | Kept; it moves under *Or open directly*. The Guided tour stays the DEMO investigation tour (the Learn door). |
| **M15 Settings share** | Export and import of a whitelisted `.fluxtion-settings`. | Extended later by the colleague follow-on (§13), not by a second share format. |
| **CLAUDE.md rule 1** | Public repo: no real names; screenshots generated, not taken. | Applies to every domain variant, the teaching bundle and the GIF (§18). |

## 4. Prerequisites and dependencies

| Item | Where | Why M79 needs it |
|---|---|---|
| **D-1. The diamond variants** | `telaminai/fluxtion-diamond`, **published on `main` at `24d6d74` (2026-10-18)**, merged from its `domains` branch; the pin and digests are in §6.4 | Five real runs: generic, mathematics, accounting, logistics and trading. Each is one topology with domain-named classes and fields, compiled and run once. M79-1 bundles them **at that commit**, with their GraphML and source, and writes the jar's manifest from §6.4. No stand-in log is ever shipped in their place. **The generic domain ships its Spring-authored run** (OD-79.21): the same pin also carries the diamond declared as Spring XML (`src/main/spring/diamond.xml`), compiled and run once (`sample/diamond-spring-audit.yaml`), so the generic first minute can show the XML beside the topology (§6.4). |
| **D-2. The broken diamond** | the same repo, branch `teaching/broken-diamond`, commit **`6bff0d67b9d8eb190d404330fe7c5f52d2018e7a`** on top of the `main` pin `24d6d74`: one commit with a deliberate defect, and its run (`teaching/broken-diamond-audit.yaml`). **Published** on GitHub (`git ls-remote` shows `refs/heads/teaching/broken-diamond` at `6bff0d67`, checked 2026-10-18), so M79-4 can pin it (§9.1) | M79-4's teaching bundle (§9). |
| **D-3. M77-2's AI line** | M77 | **Satisfied** (r7): M77-2 shipped complete in 1.36.0 (PR #159, tracker ▸ M77). M79-1 renders the line's short form (§5.4) against the real six-state `AiSetupLines.aiLine()` on `main`; the fallback clause (*"if M77-2 has not merged"*) no longer applies. |
| **D-4. M77-3's client path** | M77 | **Satisfied** (r7): M77-3 shipped complete in 1.36.0 (PR #162, tracker ▸ M77). M79-6 starts the client path at Screen 2b against the real wizard; the fallback clause (*"before M77-2 merges, that branch opens today's assistant set-up instead"*) no longer applies, since M77-2 has also shipped. |
| **D-5. The capture harness** | `tools/capture-docs.py` | The *Start here* GIF and every new screenshot are generated under an isolated `user.home` (rule 1). |
| **D-7. M76 and M75, now on `main`** | M76 [`spec-authoring-journey.md`](spec-authoring-journey.md) (DRAFT r2, merged to `main` via PR #153, 2026-10-19); M75 [`spec-document-renderer.md`](spec-document-renderer.md) (DRAFT r2, merged to `main` the same PR) | **Both specs are DRAFT r2 on `main`** (r7: checked with `git log --oneline -- docs/specs/spec-authoring-journey.md docs/specs/spec-document-renderer.md` and `gh pr list --state all`; neither spec is ACCEPTED, but both are implementable reference, not branch-only text). **Overlaps still to settle before M79-1a-ii or M76/M75's own implementation PRs land** (unchanged substance from r6, re-confirmed against the merged text): M76 uses **the same diamond** for its S8 *Teach me by building an example*, and **OD-76.7's own precondition is now met**: the diamond is published and pinned (§6.4's `24d6d74`), so M76's spec can cite that pin directly rather than leaving S8 disabled; M76 has its own analyser-composed explanation tour of the diamond, which must not become a second set of diamond captions; M76 adds S7/S8 to the start page's numbered list, which §5.4 moves behind *Ask…*; and M76 §5.4/§9.1/§10.5 say an absent entry is *not* proof and avoid *proof/proves*, while step 4 here says *proof, not silence* (the wording-policy difference, not a test collision, stands as r6 recorded it). M75's OD-75.7 is **decided** (not open, as r6's phrasing implied): *"Bundled `/teaching/` plus a dropped `.lesson.md`"* — so M75 ships bundled lessons under `/teaching/`, while M79's §6 ships the diamond assets under `/demo`. **This is still two bundled-asset locations, unresolved between the two specs**; r7 does not decide it (no owner decision names one), and flags it for whichever of M75, M76 or M79-1a-i lands first to either adopt the other's location or record why both are needed. |
| **D-6. Rule 6 reading** | `docs/claude.txt` | Before M79-1 is implemented, the implementer reads the framework reference and the variants' generated dispatcher, and confirms §6.2's claim from them. The rule is that `@OnTrigger` fires when an upstream node signalled a change. This spec states the claim; it does not infer the generated code. |
| **D-8. M80 — Design shows the graph's definition** (new, r7) | `spec-design-definitions.md` on branch `docs/spec-design-definitions` — **PROPOSED**, not yet on `main` (no relative link here: the file does not exist on `main` for `SpecLinksResolveTest` to resolve): spec PR [#170](https://github.com/telaminai/fluxtionauditlog-analyser/pull/170) open, first implementation PR1 [#175](https://github.com/telaminai/fluxtionauditlog-analyser/pull/175) **merged to `main` 2026-10-19** (`2ab4f1e6`: source-root discovery of Spring XML and Java builders), PR2/PR3 in progress | M80 extends M66's Design canvas from Spring XML only to a Java `FluxtionGraphBuilder` and multi-file designs, and changes how a design is **discovered** (a source-root scan, with an opportunistic header check). M79-1a-i's generic domain already opens `diamond.xml` read-only beside the topology through M66's existing `open {design}` path (§5.2 item 1, OD-79.21) — that is unchanged by M80. **The coordination M80 needs from M79, stated so neither builds a second path:** the Design pane and its discovery stay M66/M80's alone; M79 is a **consumer** of `open {design}` and never adds its own design-reading or discovery code (the other four domains, which have no Spring variant, open with no design, per §5.2 item 1 — not a second, M79-owned discovery route for when one is added later). If M80 ships a Java-authored design before M79-1a-ii adds Spring variants for the other four domains, a domain's design (if any) is opened the M80 way, not a new M79 way. No action is needed from M79-1a-i today beyond this statement; a future domain's design support reads this row first. |

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
   GraphML are paired by the ordinary pairing. **For the generic domain the run is the Spring-authored one, and its
   XML is shown beside the topology** (OD-79.21, accepted): `diamond.xml` opens as the session's design (M66,
   `open {design}`'s path), read-only, so the person sees the declaration (`nodeBeans`, the `X` event type, the
   handler binding on `a`, and each `constructor-arg ref`) next to the graph the compiler built from it. Where the
   design sits in the stage is M79-1a-i's layout decision (§7.2). The other four domains have no Spring variant yet
   and open without a design; adding one is a new real run under §6, deferred.
2. **The graph is centre stage.** The topology canvas fills the centre and the records table becomes a strip beneath
   it, showing at least six rows at 1200×800. This layout is the `walkPlayback` node's `stage` (§7.2, OD-79.7). Today the topology canvas is a tab in the right-hand column (`sideTabs`, beside Summary, Source, Graph and Reports) and the records table and detail pane are the left column of the Facts | Canvas split, so this is a new layout, not a tab change.
3. Above the graph: **five picture tiles**, *Generic · Mathematics · Accounting · Logistics · Trading*, each a small
   picture of its own diamond with its own node names (lead's decision, creative round 2: tiles, not a dropdown), the
   current one marked.
   Choosing one opens that domain's own run (§6, §7.1). It does not autoplay again; ▶ replays the walk.
4. The four-step walk plays once, advancing by itself (§7.3). The strip reads *"Step n of 4 · Esc skips"*. ← and →
   take over the pace, and Esc skips to the end state.

**The walk** (generic copy; each domain's walk carries its own nouns, §6.3):

| Step | View | Targets and captions |
|---|---|---|
| 1 | stage `topology`, no record selected | `topology:node:a` *"An input arrives at a."* · `topology:node:d` *"a feeds b and c; both feed d. The compiler wired this from the declaration."* |
| 2 | record [2] (`X[value=2]`), the first input | `records:row:2` *"A new input."* · `topology:node:d` *"a, b, c and d all ran: each has an entry in this record."* |
| 3 | record [4] (`X[value=3]` again, after record [3]'s `X[value=3]`) | `records:row:4` *"same input — nothing changed"* · `topology:node:a` *"a saw the same value and reported no change, so nothing below it was called"* · `topology:node:d` ***"d did not run"*** (the owner's words, joined: *"same input — nothing changed — d did not run"*) |
| 4 | record [4] | `detail:node:a` *"Only a has an entry here. This log records every node that runs, so no entry for b, c or d means they did not run. That is proof, not silence."* |

**The generic walk is written by hand, composition first** (OD-79.20, OD-79.23). Its step 1 also lights the
declaration: `source:design:bean:d` *"d declares two parents, b and c, with constructor-arg refs: those are its
edges."* The caption says what the XML declares, not how the dispatcher works (§11.7). The domain walks of
M79-1a-ii come from the caption template (§6.3) and carry no design target.

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
| **Learn** | **Guided tour** | plays the DEMO investigation tour (unchanged) | *Teach me the diamond* (from M79-2) · *Solve a real bug (5 min)* (from M79-4) |
| **Investigate** | **Open audit log** | the log chooser | *Sample project* |
| **Build** | **Start from a template** | the starter chooser (M47) | *Connect your own Claude* (from M79-6) |

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
- **The AI line, short form.** The start page shows M77 §6.0's line in a short form, one per state of
  `AiSetupState.Choice` (`NONE`, `NO_AI`, `CLIENT`, `INSIDE`/`BOTH`, `IN_APP`), **six states** with the profile split:
  *"AI: not set up · Set up…"*, *"AI: off · Set up…"*, *"AI: your own client · Ask… · Change…"*,
  *"AI: Claude (Safe) · Ask… · Change…"*, *"AI: Claude (Full) · Ask… · Change…"*, *"AI: in-app · Ask… · Change…"*.
  (r5 listed five and missed *your own client*, which `AiSetupLines.aiLine()` already renders for `CLIENT`.) The
  long form is today's `aiLine()` text, with its qualifiers: *"· Claude Code found"* on `NONE`, *"— no key yet"* on
  `IN_APP`, *"· your own client can connect"* when external access is on, and *"· another {Product} window holds the
  client connection"*. It is the line's tooltip and the Context column's row. `aiSetup` composes both forms
  (rule 9). M77's table stays the truth for what each state says; the link follows `aiLineLink()` (*Set up AI…* for
  `NONE` and `NO_AI`, *Change…* otherwise).
- **The prompt box** (M74's start prompt and its numbered list) opens from **Ask…**, or from any number key or typing
  on the page. It is one act away, not always visible. With nothing set up, typing opens the box, and Send opens the
  wizard with the question pending (M77 entry 5).
- **Drop targets** are unchanged: a `.fexp`, a log, GraphML, Spring XML or a prompt pack dropped anywhere still opens.
  The drop hint moves into *Or open directly*.
- Before M79-2, M79-4 and M79-6 merge, their links are absent, not disabled. The budget holds in every phase.
- **Reuse.** *Or open directly* is already the heading over today's groups (`start.or`); M79-1 turns that heading
  into the collapse. **Today's baseline, re-derived from `feature/m78-d2-one-chip`** (second review), **re-confirmed
  on `main` at 1.37.0** (r7: that branch merged in 1.36.0, PR #168, and nothing in 1.37.0 touches `StartPanel`;
  `StartPanel.java` is still 1,358 lines): the strip under
  *Or open directly* has **4 groups and 10 chips**: *Explore DEMO* (Guided tour, Sample project), *Open* (Project,
  Evidence bundle, Audit log, GraphML), *Create* (Project profile, From template) and *Settings* (Settings, and the
  Fluxtion key card). M77-4 removed the *Connect an assistant* group (§14.4 of that spec); r5's *13 chips in five
  groups* was `feature/claude-code-p3`'s page. The hero keeps *What would you like to do?* (`start.title`), its lede
  (`start.lede`), the prompt and its numbered list, M77-2's AI block on a first run or its AI line otherwise
  (`AiSetupBlock`; OD-79.5 removes the block), and two always-visible paragraphs (*Open evidence, return to a
  project…* and the drop hint). No test counts its words today; A1.6 is new. **The AI line's six `Choice` states**
  (`NONE`, `NO_AI`, `CLIENT`, `INSIDE`, `BOTH`, `IN_APP`) are confirmed unchanged on `main`
  (`AiSetupState.Choice`), so §5.4's six short-form lines still cover every state.
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
| **E6** | **The jar's copy is recognised by digest, not by name.** The domain tiles and the *bundled* label appear only when the open log's read-time SHA-256 (`FileReadIdentity`, from `LogStore.readIdentities()`; hex without the `sha256:` prefix that record digests carry) matches a manifest entry. A copied, renamed or edited file opens as an ordinary log. **Extraction:** the assets are extracted as the DEMO set is (`DemoAssets`, `/demo` → `~/.fluxtion-analyser/demo`), but `DemoAssets.install()` today re-extracts a file only when it is missing or empty, so an edited extracted copy, or a stale one after a re-pin, would be reused and fail the manifest on every launch (§5.5, forever). M79-1 makes `install()` re-extract any file whose SHA-256 differs from the manifest. | A1.5: an edited copy opens without the tiles; an edited *extracted* copy is replaced on the next extraction and the first minute plays |
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
The tiles say *Trading* for the owner's *stock/trade processing*.

| Domain | Input (record [4]) | a | b, c | d | Step 3, joined |
|---|---|---|---|---|---|
| Generic (default) | `X[value=3]` | `a` | `b`, `c` | `d` | *same input — nothing changed — d did not run* |
| Mathematics | `Operand[value=5]` | `dividend` | `quotient`, `remainder` | `recombination` | *same operand — nothing changed — recombination did not run* |
| Accounting | `InvoiceLine[net=600]` | `line` | `discount`, `tax` | `total` | *same invoice line — nothing changed — total did not run* |
| Logistics | `Order[items=18]` | `order` | `pack`, `pick` | `dispatch` | *same order — nothing changed — dispatch did not run* |
| Trading | `Trade[price=103]` | `trade` | `pnl`, `position` | `risk` | *same trade — nothing changed — risk did not run* |

**One caption template, filled from each pinned log** (lead's decision, creative round 2; it replaces five
hand-written walks). Since r6 it serves the four domains of M79-1a-ii; the generic walk is written by hand, composition
first (§5.2, OD-79.23). The walk is written once, with roles in place of names: *the top node* (the event handler),
*the two siblings*, *the join*, and *the input* (the event type, in words: `InvoiceLine` → *invoice line*). For each
domain the roles are filled with **that log's own node ids**, found from the structure of its GraphML (the node fed
by the event type; its two children; their common child), not from a table, so this is the log's own words and not
relabelling (E2's static rule still holds: no name table keyed by domain). What stays per domain is the evidence:
each domain's walk is bound to its own record digests (§6.4, E5) from the manifest. Two guards:

- the template's claims are the ones true in **every** pinned log (§6.2: record [2] all four ran; record [4] only the
  top node); a claim true in some domains only (record [3]'s half) is never in the template;
- A1.5 renders the five filled walks and compares each caption with its log: every node id a caption names has an
  entry in the record it is about, or is the node the caption says did not run. The chosen domain is remembered per machine (`firstMinute.domain`).

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

**The generic domain's Spring-authored run** (OD-79.21, accepted; it **replaces** `sample/diamond-audit.yaml` as the
generic first minute's log). Present at the same pin `24d6d74` (checked 2026-10-18 from a clone of the pin):

```
6291ba0566a9539c6e128cb929161c8cfc21e12b57919f5df28d4f00c2c70487  sample/diamond-spring-audit.yaml
7ebd36b0c39ad016cc552b1b54c7cd743c797dbceff9e7e4b37ba7cf8d6b5dec  src/main/resources/com/acme/diamond/generated/spring/DiamondSpringProcessor.graphml
f050de05aa8d815e2cbb0a48abe2901cffcddffcba0e3f43f34de5de92cd9bd4  src/main/spring/diamond.xml
```

- **The GraphML is byte-identical to the builder-authored `DiamondProcessor.graphml`** (the same `7ebd36b0…`): the XML
  declares the same graph, and the compiler built the same one. E3's isomorphism check therefore holds unchanged.
- **The log differs from `sample/diamond-audit.yaml` only in its timestamps** (`logTime`, `eventTime`, `endTime`):
  the same six records, events, node entries and stream-end trailer. Every caption claim of §5.2 holds for it. Its
  console output is `sample/run-console-spring.txt` and its processor `DiamondSpringProcessor.java`, beside it.
- **Its record digests** (computed as above, with `WalkIdentity.recordDigest(HeapLogStore.fromFile(log).rawText(N))`
  at the current build, 2026-10-18; records [2]–[5] traced by `AuditTrace.tracesEveryInvocation`, [0] and [1] not):
  record [2] `sha256:bedfb8f29fd37715b160f675461f03df716e06295794fd20a8049b93dadc996f`, record [3] (SC-C1's held-out
  cycle) `sha256:7fea6075da3d6c030796679647610063d9d770098fc9ec04cc6883af2460de16`, record [4]
  `sha256:254885974d5dac3ab2a9e8a56887ddf8ce672c5e77b60c78b3d4578c5cfd4322`, record [5]
  `sha256:272c3ea448282270ba80259c64e4bde20e723719a796296689b1709652966a89`. The generic walk binds to these, not to the
  builder run's digests above; A1.5 asserts them. Because the timestamps differ, these digests differ from the
  builder run's although the entries match: a walk bound to one run is *not available* on the other, as E5 intends.
- **What does not move.** The broken-diamond quest compares against the builder run's record [3] (§9.1), which stays
  the witness of a same-index record from another run. The M74 philosophy packs pin the builder run's log (at
  `dad9b43`), not the Spring one, so they and the first minute now show twin runs of one graph, not one file.
- The manifest lists both generic logs only if both ship; M79-1a-i ships the Spring run, its XML and its GraphML.

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
  `OPENING`, `PLAYING`, `DOORS`, `DONE` or `FAILED`. The tiles, the card, the callout and §5.5's line render this
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
  of origin `FIRST_MINUTE`, whose stage is held for **first contact** (§5.3). For the generic domain the stage also
  shows the session's design, read-only, beside the topology (§5.2 item 1, OD-79.21); the proportion is the node's
  constant, like the strip's.
- **Facts:**
  - `StageToggleRequested(TOPOLOGY|RECORDS)`, from the canvas header's **Topology | Records** toggle;
  - `FirstMinuteDoorChosen` and `FirstMinuteCardClosed` (also handled by `firstMinute`);
  - a new log generation, which also ends first contact, **except** a generation opened by `firstMinute`'s own
    `OpenDiamondEffect` (a domain chosen from the tiles, §5.2 item 3; its `OpenRequest` launch is `FIRST_MINUTE`).
    **What carries the launch into the session** (second review): today nothing does. `OpenRequest` (with its
    `Launch`) is frame state that travels with the asynchronous load and is read in `MainFrame`'s load completion;
    the session facts carry no launch: `OpenLogRequested(opId, location, format, provenance, fromSocket,
    assistantOrigin)`, `OpenLogEffect` (the same fields) and `LogOpened(opId, logPath, provenance, loggedNodeIds,
    sampled, total, mostVerboseLevel, provenanceSource, followable, nodeLogsWithheld, openedAtMillis)`. M79-1a-i
    adds **`LogOpened.launch`**, the request's `Launch` name, set at the one place `LogOpened` is submitted (the
    load completion, where the `OpenRequest` is in scope), with the existing shorter constructors defaulting it to
    `NONE`. `openLog` publishes it with the generation, and `walkPlayback` and `firstMinute` read that published
    value. No node infers the launch from the path or from a pending flag, and `firstMinute` does not match opIds of
    its own effect against arrivals, which a superseded open would confuse.
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

## 8. M79-3 — quiet evidence wording

### 8.1 The chip

The Context column's verification rows (file identity, the bundle's *"verified on open; unsigned…"*, the observation
basis) collapse into **one chip** at the top of the column. **Only a byte or digest check earns ✓** (OQ-1, resolved by the review of 2026-10-18; it follows M68 D-E6: *"Unverified is
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

**Three states** (lead's decision, creative round 2, 2026-10-18; it answers OQ-1):

| What the session holds (in order; the first that applies) | Chip |
|---|---|
| `logIdentity` is `UNVERIFIED` or `REPLACEMENT`; the metadata observation is `changed-on-disk` or `unknown`; a bundle failed to verify; or the read identity is missing | **⚠ changed** (something moved) or **⚠ unknown** (nothing was observed, or the read could not be identified) |
| a byte or digest check passed: an evidence bundle verified on open (every member's SHA-256; unsigned), a shipped asset whose read identity matches the jar's manifest (§6, §9), or Follow's byte comparison (`logIdentity` `VERIFIED`) | **Evidence ✓ checked** |
| an ordinary log, metadata `unchanged-metadata`, no byte or digest check | **Evidence · as read** |

*As read* says exactly what is true of a metadata-only log: the records shown are the bytes as they were read, and
nothing has re-checked them since. The popover (below) says which check each state rests on, in the existing words.

- **Rule 9.** The chip renders the snapshot only, so M79-3 moves the metadata observation into the session: the
  frame performs the observation as an effect and reports `FileObservationObserved(generation, state, basis)`, the
  node publishes it, and `context`, the Project panel and the chip all read the published value (no second
  computation in `MainFrame.logFreshness()`). The `context` field keeps its name, shape and words (A3.3).
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

### 8.2 Accessible descriptions (the tooltips are cut)

**Cut** (lead's decision, creative round 2): no product-term tooltips, no `help/terms.properties` and no docs terms
page in M79. What stays is the **accessible description** on the controls where *Mongoose*, *GraphML*, `.fexp` and
the *Fluxtion key* first appear (the start page's openers, the Context column, Settings), reusing `ActionChip`, which
already sets the accessible name and description, and the existing wording (the key card's `why`; the *Evidence
bundles* page's definition of a `.fexp`, which says it is unsigned). (r2 had renamed the resource away from
*glossary*, which M38.2 owns; with the cut, that question does not arise.)

## 9. M79-4 — the broken-diamond quest (OD-79.4)

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

  The bundle (`.fexp`) is built from these files by the ordinary capture in M79-4, and its own SHA-256 goes into the
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
  recording which. M79-4 therefore routes flags through the session first (rule 9): a `FlagToggled(generation,
  row, recordDigest, source)` fact posted by both entrances, the digest computed from `LogStore.rawText(row)` in the
  same EDT turn, and a node that owns the flag set the table renders. That is a migration of an existing surface the
  customer uses daily, so it is sized as one (§17) and carries its own no-change checks (below). `quest` compares the two. **Teacher mode** (§11.6 d, OD-79.17, **accepted**; both routes): only a flag from this window
  publishes *Solved*, naming the record; a flag over the socket on the answer record shows *"Your AI client flagged
  this — try it yourself"* and leaves the quest open (the session records *socket*: it cannot tell an AI client from
  a script). This covers the onboard session as well as an external client: the onboard Claude Code session flags
  through the hosted bridge, which is the socket entrance. **A model's words never complete it.** An assistant that says "solved" changes nothing; only the flag on the
  right record does.
- **Works with No AI.** No step needs an assistant, a key or a network. A1-style tests run with the profile `OFF`.
- **Rule 9:** a `quest` node, with parents `openBundle` and `openLog`. Its facts are `QuestStarted(questId)`,
  `QuestHintRequested`, and the flag fact. Its effects are `ShowQuestEffect` and `RecordQuestSolvedEffect` (machine
  tier: `quest.<id>.solved`). The card renders `QuestState`.
- **The customer's flags are unchanged.** The new digest field is additive on the flag fact only. Four surfaces keep
  their bytes, and A4.6 pins each before and after the migration (second review):
  - the `flag` verb: its schema (`VerbSchemas`; `byteOffsets`, `recordIndexes`, `note`) and its reply;
  - `context.flags`: a list, in record order, of `{recordIndex, kind, note?, fix?}` (`kind` defaults to `fault`);
  - the saved-flag format: the session-resume view's `findings` list (`Finding.toMap()` per flagged row);
  - session recovery: `restoreRecoveryView` restores flags and findings only against an unchanged log identity, and
    its outcome lines (*"Restored n flags/findings against the unchanged log"*, *"Flags/findings withheld: no
    unchanged log identity"*) keep their words.

## 10. M79-5 — the docs front door

**The M79 slice is three things** (lead's decision, creative round 2): the *Start here* page, its GIF, and
`DocsLabelParityTest`. **The full restructure follows separately**, as DOCS-IA: folding the getting-started pages,
the home page's words and the *Packs* page are recorded below as what M79 asks of it, not built in M79-5.

- **One *Start here* page** (`docs/site/start-here.md`, first in the nav). It opens with a GIF: a click on a record,
  then the graph lighting, with `d` dark on the repeated input. Under the GIF are three cards, **Learn / Investigate /
  Build**, with the app's labels. The GIF is generated by the capture harness under an isolated home, from the
  diamond's generic run only, and every frame is read before commit (rule 1, §18).
- *For DOCS-IA, not M79-5:* **the five getting-started pages become one.** *Quick start*, *Guided start*, *Playground to analyser* and *Spring
  authoring step by step* fold into *Start here* and the user guide. *Install & run* stays as reference. The old pages
  stay out of the nav for one release as one-line pointers, for external links, then go. This lands with, or after,
  the owner-approved DOCS-IA restructure, and M79-5 supplies its front door.
- **Conflict with DOCS-IA, for the owner (OD-79.11).** The nav the owner approved on 2026-10-17
  ([`docs-audit-2026-10-17.md`](../handoff/docs-audit-2026-10-17.md) §5.1–§5.3) puts the **four doors** (*Diagnose,
  Build, Start, Admin*) on the home page, `index.md` (a rewrite), and plans `start-here.md` as a **new** page (no
  such page exists today), whose outline (§5.3) has four task cards (incident with your own client, ask inside,
  build with Claude, the demo) plus an administrators' line. It also keeps **eight** *Get started* pages, `start/prompt-packs.md`,
  `teach/philosophy-packs.md` and `reference/glossary.md`. M79-5's three doors, *five pages become one* and SC-D1's
  target of 1 contradict that approval, and *Investigate* (the app's work-mode word, `CanvasHandoff`) is DOCS-IA's
  *Diagnose*. M79-5 does not override an approved decision by being written later: it either amends DOCS-IA §5.1/§5.3
  explicitly, with the owner's decision, or it adopts DOCS-IA's doors and the start page's three doors map onto them.
  With the restructure left to DOCS-IA (above), what remains for OD-79.11 is the *Start here* page's doors.
- **The nav's current names** (`mkdocs.yml`): *Guided start with an AI assistant* and *Playground to analyser in 10
  minutes* are the real titles of two of the five.
- *For DOCS-IA, not M79-5:* **the home page says what it is:** a **canvas** where you **learn** how an event-driven system behaves, **build**
  one, and **understand** what it did, from its own evidence. The words *canvas*, *learn*, *build* and *understand*
  each appear above the fold (DOCS-IA's check, not M79's).
- *For DOCS-IA, not M79-5:* **packs defined and listed:** a *Packs* page says what a **prompt pack** is. It is learning entries: words placed in a
  draft, or copied for your own client. Nothing runs them. The page lists the packs that exist: the sample pack (a
  docs page today, `user-guide/sample-prompt-pack.md`; no pack ships in the jar yet), M79-6's *Your project, for your
  own Claude*, and **M79-7's three philosophy packs** (01, 02, 04 — §19), published once M79-7 ships on the docs
  site (the eight drafted packs and their tooling merged at PR #155, 2026-10-19; M79-7, not PR #155 by itself, is
  what publishes 01/02/04). They teach with this same diamond, at `dad9b43`, whose generic log is byte-identical to
  §6.4's (§19 reconciles the two pins). DOCS-IA puts this page at `start/prompt-packs.md`. **Packs stay what they
  are**; sharing a set-up is Settings share's job (§13).
- **Labels match:** `DocsLabelParityTest` reads every bold control label on the docs pages that describe the start
  page, the first-minute card and the quest. Each must equal a **visible** label in the app's label registry. Today
  `StartPanel`'s labels are inline literals, and each chip has a *title* (*Take a guided tour*, *Open sample
  project*, *Open audit log*, the accessible name) and a shorter visible *label* (*Guided tour*, *Sample project*,
  **Audit log**): M79-5 moves them to constants (start-page copy already lives in `AskCatalogue.properties` `start.*`,
  where `FirstMinuteLines` belongs too) and the test compares the visible label. **Reuse** the pattern of
  `MenuDocumentationTest`, which checks the docs' menu paths against `MenuInventory`. The fix for F2 is the app's
  labels everywhere: *Guided tour*, *Sample project*, and *Open audit log*, which M79-1 makes the visible label (it is
  the title today). Known mismatches the test will meet: *Open the sample* (`StartPageNoLogHint`, `start-page.md`,
  and M77 §6.4), and M79's own *Start from a template* against the app's *From template* chip and the menu's *New
  project from template…*; M79-1 picks one wording for all three. F2's *Open the demo log* is in a screenshot, not in
  docs text, so the capture harness's regenerated shots, not this test, retire it.

## 11. M79-2 — the teaching conversation in the onboard assistant (owner, 2026-10-18)

**The name** (OD-79.22, accepted). M79-2's artifact is a **teaching conversation**, not a *lesson*: M75
(`spec-document-renderer.md`, branch `docs/m75-document-renderer`) owns *lesson* for its rendered `.lesson.md`
documents and their pack entry kind `read:`. *Teaching conversation* is used nowhere else in the repository (checked
2026-10-18). The other candidates are taken: *Guided tour* is the DEMO tour's label; *guided session* names a coached
run in `spec-spring-authoring-edit-loop.md`; *walkthrough* is A1.9's word and appears in code; *journey* is OA-D4's
conversation journeys; *walk* is M69's. Its pack entry kind is `teach:`, its snapshot record `TeachingLog`, its
hand-off origin `TEACHING`, its door link *Teach me the diamond*, and its band *Recorded teaching*. Revision history
before r6 keeps the old word.

**The owner's intent** (correction of 2026-10-18; it overrides the lead's round-2 re-phase). A pack carries a
**recorded conversation**. Following it plays a teaching session in the **onboard** assistant panel, with
spotlights on the real diamond records, and needs no external Claude and no live model. Once it has been followed,
the onboard assistant connects to live Claude, and the recorded conversation stays in the window as a **passive
log**. The diagnosis experience (the onboard assistant) drives the teaching experience. The external client is the
secondary route, for people who prefer it (§11.6, phase M79-6).

### 11.1 What the person sees

1. **Start the teaching conversation:** a pack entry, or the first-minute **Learn** door's *Teach me the diamond* link, plays a
   shipped, recorded teaching conversation in the **docked** assistant panel. The panel's header labels it every
   time it plays (*Recorded conversation*, or *Edited recorded conversation*; §11.4), the composer is hidden, as in
   any demonstration, and each step spotlights the real diamond records (record digests, §6.4).
2. **Step through it:** ◀ ▶ as in any walk with a conversation; nothing is sent to a provider and nothing is written.
3. **At the end, one action: Continue with Claude.** It starts the live onboard session, or attaches to the one
   already running. With nothing set up it opens the M77 wizard (entry 5's path, with a new `origin`, `TEACHING`), and
   the hand-off completes when the wizard finishes.
4. **The recorded turns stay above, as a passive, read-only log**, visually distinct from the live turns (a labelled
   band, *Recorded teaching · not part of this conversation*), and are **never sent to the model** as if it had said
   them.
5. **The live session's basis says what was covered**, in {Product}'s fixed words, not the recorded ones: for
   example *"The person followed the recorded teaching conversation 'The diamond' (4 steps; records [2] and [4] of
   the bundled generic diamond)."*
6. **Items 4 and 5 apply only to a shipped teaching conversation** (OD-79.24, accepted). The band and the basis
   sentence are published only when the walk that ended by hand-off is a teaching conversation **shipped in the jar
   whose definition matched the manifest's digest when it played**. They are not keyed on the conversation's
   `kind` (`WalkSpec.Conversation.kind`: `scripted`, `recorded` or `edited-recording`): a customer's own walk,
   recorded with the conversation editor, is `recorded` too, and must never be relabelled with {Product}'s band or
   summarised in {Product}'s words. Such a walk's hand-off behaves as `AssistantHandoffRequested` does today.
7. **What it teaches: composition first** (OD-79.20, accepted). The conversation starts from how the diamond is put
   together, the XML beside the topology (OD-79.21), and only then from what ran. §11.7 gives the rules and the
   order; the callback detail of §11.6(a) is a secondary note, not the frame.

### 11.2 What it reuses (searched first)

| Exists | Where | Used for |
|---|---|---|
| **Walks that carry a conversation** (conversation journeys, OA-D4): typed turns bound to steps, *Simulated* / *Recorded* / *Edited recorded* labels in the panel header, the composer hidden, ◀ ▶ revealing the prefix, nothing sent or written; *"the conversation travels with the walk"* through projects, share, the bin and bundles | `WalkSpec.conversation`, `AssistantPanel`, `docs/site/user-guide/assistant.md` ▸ *Walks with a conversation* | the teaching conversation **is** a walk with a conversation; no second playback format |
| **Recording a Claude Code conversation** (conversation version 2): only sent turns and finished answers, never tool lines; the source recorded as *claude-code*, model and version | the walk conversation editor | how the real recording is captured (§11.4) |
| **"Run me through a demo"** (M74 S1) and `DemoJourney` / `PlayDemoJourneyEffect`: a shipped walk with pre-authored digests and a *Simulated conversation*, played from the start page | `journey/first-breach.fluxtion-settings`, `MainFrame.finishSampleJourney` | the shipping and play path for the teaching conversation (without `persistWalks` and the `pending*` flags, §7.1) |
| **"Ask about this evidence"** (OA-D4 §6.2): ends the demonstration and opens a fresh live thread that inherits none of its words | `AssistantHandoffRequested`, handled by `walkPlayback` and `assistantLoop` | the hand-off's start; *Continue with Claude* is this, extended (§11.3) |
| **Conversation journeys' catalogue rule:** opening a journey never autoplays it (OA-D6) | `spec-onboard-assistant-journeys.md` §8 | kept: the teaching conversation plays only on the person's act (unlike the first minute's four-step walk, which has no conversation) |
| **Prompt packs:** dropped Markdown, words-only entries plus one pinned *example* entry kind; session-only | `PromptPack`, `StartPromptPackDropped` | the entry that starts the teaching conversation (§11.3) |
| **Real captures:** `tools/capture-conversations.py` (tool calls and echoes recorded live under an isolated home) and `tools/capture-journey.py` (a journey built and played from a real run, re-checked by `JourneyCatalogueTest`) | `tools/` | the recording harness and its re-derivation test (§11.4) |
| **Replay for tests:** `FixtureAgent` replays recorded Claude Code streams through the real adapter and session processor | `src/test/…/localagent/FixtureAgent.java` | the witness that recorded turns are never resent (A2.3); it is a test fake, not a product path |

**Two 1.37.0 additions M79-2 builds on, not around** (r7, new facts since r6):

- **The docked panel now renders Markdown tables, not raw pipes** (1.37.0): a GFM table draws as an aligned grid, or
  stacks `Header: value` lines when it would not fit. If the recorded teaching conversation's turns ever include a
  table (for example, listing the four node ids against their record entries), it renders through this path like any
  live turn; M79-2 adds no second table renderer.
- **The docked assistant's chrome budgets are pinned by `AssistantChromeBudgetFrameTest`** (M78 TQ1, extended for
  M81's activity line): Ready/Safe/Full states hold to 15 words and 4 controls, a running turn or a waiting
  permission to 15 words, and an alert state (exited, no key, a restart offered) to 20 words. **The passive log's
  band label (*"Recorded teaching · not part of this conversation"*) and the basis sentence (§11.3) are chrome too**:
  M79-2's own acceptance (A2.1, A2.3) must show them render within whichever of these budgets is current when the
  band or the hand-off's opening context is visible, so M79-2 extends `AssistantChromeBudgetFrameTest` with a
  *recorded teaching, before hand-off* state rather than asserting the band's word count separately. Which bucket
  the band belongs in (closest to Ready, since nothing is running) is the implementer's call, recorded in that test.
- **Restart now recovers a stuck or interrupted Claude Code process and preserves its conversation** (1.37.0, and
  #171's fix: a project switch while Claude Code is running always offers the same restart question, including from
  the log-offer path). *Continue with Claude*'s hand-off (§11.3) starts or attaches to the live session through the
  same path a person's own Start/Restart uses; M79-2 does not special-case the attach because of this fix, it
  benefits from it: an interrupted hand-off recovers like any other interrupted turn, with the last request preserved
  and re-sent rather than silently dropped.

### 11.3 The gaps, and how each is closed

- **A pack that carries a conversation.** `PromptPack` has no such entry. M79-2 adds one entry kind, `n. **label** —
  teach: <conversation id>`, naming a teaching conversation **shipped in the jar** and recognised by digest, as the diamond assets are
  (E6). A pack dropped from elsewhere may name a shipped teaching conversation, but cannot bring its own recorded conversation in
  v1: a conversation inside an untrusted file would carry the exporter's *Recorded* claim, which {Product} cannot
  check (OD-79.19, **accepted**: a pack's own recorded conversations come later, with signing).
- **The hand-off to live.** `AssistantHandoffRequested` today ends the demonstration and opens a fresh **in-app**
  thread (`assistantLoop`). *Continue with Claude* posts it with `origin=TEACHING`, and M79-2 adds the Claude Code side
  (`localAgentSession` starts or attaches) and the nothing-set-up branch (the M77 wizard, then the hand-off). Which
  mode it continues in is the profile in force, decided by the session, not the button.
- **The passive log.** Today the fresh thread starts empty and the demonstration's turns leave the panel. M79-2 keeps
  them: a `TeachingLog(conversationId, label, turns)` published in the snapshot, rendered above the live turns in a
  distinct, read-only band, **only for a shipped, digest-verified teaching conversation** (§11.1 item 6). **It is
  never part of a request:** the in-app request builder and the Claude Code adapter read only
  their own transcript stores, and a static rule (`OneDispatchModelTest`) fails if any request path reads
  `TeachingLog`.
- **The basis line.** The live session's opening context gains one fixed sentence composed by the node from the
  teaching conversation's id, step count and the records it showed, never from the recorded words.

### 11.4 The recording (owner rule)

- **A real capture.** The teaching conversation's turns are captured from a real onboard Claude Code session, under an isolated
  `user.home` holding only the diamond, with the conversation editor's Claude Code capture (version 2), driven by an
  extension of `tools/capture-conversations.py`. Note the gap: that harness today **authors** the asks and answers
  and records only the tool calls and echoes, so its output is not a *Recorded conversation* in the panel's sense.
  The teaching conversation's capture records the model's finished answers too, untouched; any edit makes it *Edited recorded
  conversation*, labelled so.
- **Labelled every time it plays** (the panel header, §11.1), and in the catalogue and the pack.
- **Re-recorded when the app changes.** A test in the style of `JourneyCatalogueTest` re-derives every fact a turn
  states (record indexes, node names, entries) from the bundled diamond log on every build; the capture is
  regenerated with the release screenshots, and a teaching conversation whose tool lines no longer match the current verbs fails
  that test rather than playing.
- **Bound by digest**, as walks are: each step's record targets carry §6.4's digests, and E5's record-basis rule
  applies (a step with a non-current record shows none of its captions or turns).
- **Rule 1.** The capture is read in full before commit, like a screenshot: model output can carry anything.

### 11.5 Rule 9

`walkPlayback` already owns playback, including a dialogue walk's end on a live question or a hand-off, and it owns
the teaching conversation's playback too. The **hand-off and the passive log** are new state: `walkPlayback` records the teaching
conversation that ended by hand-off and, if it is a shipped one (§11.1 item 6), publishes `TeachingLog`; `assistantLoop` and `localAgentSession` decide the live session's
start or attach on `AssistantHandoffRequested(origin=TEACHING)`, as `assistantLoop` already does for the in-app thread;
`aiSetup` handles the nothing-set-up branch. The panel renders `TeachingLog` and the live transcript and decides
nothing.

### 11.6 M79-6 — the external teacher kit (phase 3 or later)

**Secondary, not primary** (owner correction, 2026-10-18). The same teaching content, offered to people who prefer
their own external client. It is numbered M79-6 so that the other phases keep their ids; it may be scheduled any time
after M79-2.

- **One action:** **Connect your own Claude** (the Build door, the start page). It opens the M77 wizard at Screen 2b
  with *Claude Code* chosen. That is M77-3's path, unchanged: detection, the external-access confirmation, *Register
  for me…* with the exact command, and the live check. It is the person's act, so OD-79.5 holds: nothing about AI
  appears unasked.
- **Pointed at the project.** When the live check is ✓, the Summary's suggested first action (M77 §6.4) becomes **Copy
  the first prompt**, from the shipped pack *Your project, for your own Claude*. Its entries are words for the person to
  paste into their own client. For example: *"Call analyser_context first. Read this project's runbooks and vocabulary
  from the pointers it returns, then tell me what this system does and what its audit log covers."* The pack also loads
  into the start list for the session. **Today a pack loads only by a drop** (`StartPromptPackDropped`); M79-6 adds a
  fact for loading a shipped pack (`StartPromptPackShipped(packId)`, handled by `startPagePrompt` as a drop is), not a
  view call. M77 §6.4 is amended to match (its *your own client* row's *Copy a first question* becomes this action).
- **The project context is already there** (M38): `context` returns the runbook, vocabulary and environment pointers.
- **No new verb, transport or consent.** The connection is M77-3's; the kit below is words, one skill and one
  published field.
- **Teacher mode applies to both routes** (d, below): onboard or external, only the person's own flag solves the
  quest.

**(a) The bridge `instructions`: a short addition, about 60 words.** `McpBridge.INSTRUCTIONS` (also served at
`/manifest`) does **not** say to call `context` first today; only the Ask catalogue's templates do. M79-6 appends one
short paragraph and nothing more, because every MCP client reads the text at every connect and it costs tokens on
every session (owner direction). Draft:

> *Call analyser_context first: it names the project, its runbooks and what the log covers. Reading rules: a node's
> edges are its declared parents (constructor arguments; in Spring XML, constructor-arg refs). A handler's returned
> boolean decides whether its @OnTrigger children run. A node with no entry did not run only in a traced record;
> otherwise it may have run silently. Reference: telaminai/fluxtion docs/claude.txt.*

**Composition first** (OD-79.20, accepted): the paragraph states how a graph is declared and how to read its log,
and drops r5's dispatch-mechanics clause (*@OnTrigger(dirty=false) runs on false*), which belongs in the skill. The
membership rule (§11.7 rule 2) is Spring-specific and too long for every connect, so it is the skill's as well.

- **Rule 6.** The edge rule is the Spring contract's (*"`constructor-arg ref` between node beans — static
  topology"*) and the playground orientation's (*"Constructor takes upstream references — that IS the wiring
  declaration"*); the boolean rule is `claude.txt`'s (*Conditional dispatch*); the traced rule is the format spec's
  (§8). Sources in §11.7.
- **The callback rule is confirmed, and stays out of this text.** r5 held a third reading rule (*@AfterEvent and
  lifecycle callbacks run without propagation*) pending an empirical probe. The probe ran (2026-10-18; a generated
  processor and its audit log, Fluxtion runtime 1.1.2 and builder 1.0.79, on a local `fluxtion-diamond` probe branch,
  `probe/afterevent` at `f7ab5ea`, not published) and confirmed it:
  - **`@AfterEvent`** runs on every node at the end of every handled event and of every lifecycle phase, in reverse
    topological order, whatever the dirty state, and **never propagates**: a node with only `@AfterEvent` or
    `@AfterTrigger` gets no dirty flag, and its `@OnTrigger` children are never dispatched;
  - **`@AfterTrigger`** runs only when the node's parent was dirty in that cycle;
  - **lifecycle callbacks** run once per phase: `@Initialise` and `@Start` in topological order, `@Stop` and
    `@TearDown` in reverse.

  So `claude.txt` line 206 (*Bridging DSL flows into imperative `@OnTrigger` nodes*) is wrong where it lists
  `@AfterEvent` among the trigger annotations through which a node propagates; the playground orientation
  (`fluxtion-playground.dev/CLAUDE.md`) repeats the same sentence. The upstream notes are drafted as **UP-FLX-56**
  (that sentence), **UP-FLX-57** (the `@AfterTrigger` javadoc's conditions) and **UP-FLX-58** (one `nodeLogs` entry
  holding two callbacks' writes) in [`upstream-asks.md`](../proposals/upstream-asks.md) on the
  `fix/merged-nodelog-entries` branch; this spec does not repeat them. Under OD-79.20 the result is a **secondary
  note in the skill** (b), worded exactly as above, and is **not** added to `INSTRUCTIONS`: the paragraph teaches
  composition, not callback mechanics.
- **Pinned** in a new `BridgeInstructionsTest`: the paragraph is present, under a word cap (70), and the rest of the
  text is byte-identical to today's, so the customer's client reads one added paragraph and nothing changed.
- **This is the one M79 change the customer's external Claude sees** (OD-79.12): it changes no verb, field or reply.

**(b) A `teach-fluxtion` skill**, `docs/skills/common/teach-fluxtion/SKILL.md`, shipped through the existing skill
distribution: `common/` skills are baked into every generated project's `.claude/skills/`, published to the website,
and served by name and description in `context.runbooks[]` (M43); for a project that already exists, *AI ▸ Find
skills…* offers it. The full rules live here, not in the instructions: §11.7's four teaching rules in their order,
composition first (OD-79.20), then, as a **secondary note**, the confirmed callback rules of (a). Its procedure:
1. start from the declaration: with the generic diamond open, light its XML (`source:design:bean:<id>`) and ask the
   person which nodes `d` depends on and which node handles `X`; check the answer against the topology, never by
   assertion;
2. play the diamond walk (`walk`), one step at a time;
3. before each of records [3] and [5], ask the person in chat to predict which nodes run, then check the prediction
   with `goto` and `spotlight` on the record, never by assertion;
4. set the broken-diamond quest and give hints only when asked, never the record;
5. end with *"now ask about your own log"*.

A change under `docs/skills/` is not docs-only in CI (rule 4). Step 4 needs the quest (M79-4): until it merges, the
skill ends after step 3, and M79-4 adds step 4 in the same PR as the quest.

**(c) Learner progress in `context`.** A `learning` object, written by the `firstMinute` and `quest` nodes into the
snapshot and read by `context` from there: the first minute's phase and domain, whether the walk played, and, per
quest, started, hints shown, solved and by whom. The **Project panel gets a *Learning* row** rendered from the same
snapshot (the human-surface rule: what a client can read, a person can see). **Both are omitted when empty**, so a
developer who never touches the first minute or a quest gets a byte-identical `context` reply (A6.3).
**A known hazard** (ONBOARDING ▸ *`MainFrame.context()` returns early on a fresh start*): `context()` puts an empty
`filter` and returns as soon as `filter == null`, so any field `put` below that line is invisible on a fresh start;
twice before, a correct fact sat below it. `learning` is therefore put **above** the early return, and A6.3 checks
it with no log loaded, as well as with the quest's bundle open and none of the person's own logs loaded.

**(d) Teacher mode: only the person's own flag solves the quest** (OD-79.17, **accepted** by the owner,
2026-10-18; it applies on both routes, onboard and external). A flag from this window solves it. A flag from the socket does not; the quest card reads *"Your AI client
flagged this — try it yourself."* The socket cannot tell an AI client from a script, so the words name the client
because that is the case the quest is for, and the session records only *socket*. The onboard session's flags
arrive by the same entrance and read the same (§9.2, A4.5).

**(e) MCP prompts: later, not M79.** Offering the teaching steps as server-side MCP prompts is gated on rule 6: a
live check of which clients actually show a server's prompts, which is research in progress.

### 11.7 Composition first, led by the Spring XML contract (OD-79.20, OD-79.21)

**Read under rule 6** (2026-10-18; public sources only): the Spring authoring contract,
<https://fluxtion-playground.dev/spring-authoring/contract.md>; its design-conversation procedure,
<https://fluxtion-playground.dev/spring-authoring/skill.md>, and worked example,
<https://fluxtion-playground.dev/spring-authoring/example.md>; the playground orientation,
<https://fluxtion-playground.dev/CLAUDE.md>; the compiler's public diagnostic page,
<https://telaminai.github.io/fluxtion/troubleshooting/errors/SPRING_BEAN_NOT_SELECTED/>; `src/main/spring/diamond.xml`
at the `fluxtion-diamond` pin `24d6d74`; the analyser's M66 Design view ([`completed/spec-design-render.md`](completed/spec-design-render.md)
D-4, `design/DiagnosticLocation`); and `docs/skills/spring/add-a-node/SKILL.md`.

**The teaching rules, in this order** (the teaching conversation, the `teach-fluxtion` skill and, in short, the
instructions paragraph):

1. **Edges are declared parents.** A node depends on what it is constructed with: `new B(a)` in Java, and
   `<constructor-arg ref="a"/>` (or a `property ref`) between node beans in Spring XML. The compiler builds the graph
   from these declarations, at compile time (contract, *Three kinds of relationship*; skill.md step 4).
2. **Membership is declared too.** With a `nodeBeans` list in force, only the beans it names are added as explicit
   nodes, and the beans they reference are still discovered (contract, field table). A bean that is declared, in
   neither `nodeBeans` nor `ignoredBeans`, and referenced by nothing, is left out of the graph. (The review's short
   form, *a bean in no list is not in the graph*, is wrong for a referenced bean, so the teaching uses the contract's
   form, as `add-a-node` already does.)
3. **The returned boolean decides propagation.** A handler or trigger that returns `true` marks its node changed, so
   its `@OnTrigger` children run; `false` stops the cascade below it. That is the diamond's record [4]
   (`claude.txt`, *Conditional dispatch*).
4. **Silence is not *did not run* unless the record is traced** (*Format specification* §8; E4).

Then, as a secondary note only: the confirmed callback rules (§11.6 a).

**The Spring dialect, as the public contract states it.** Standard Spring `<beans>` XML with no custom namespace, plus
one configuration bean of class `FluxtionSpringConfig` (the dialect's processor configuration element). The diamond's
XML uses five of its parts: `logLevel` (`INFO`), `eventTypes` (`com.acme.diamond.event.X`), `nodeBeans` (`a`, `b`,
`c`, `d`), `eventHandlers` (one `EventHandlerBinding`: event `X`, node `a`), and the edges as `constructor-arg ref`s on
`b`, `c` and `d`. The full set (contract, *What each `FluxtionSpringConfig` field means* and *Local authoring workflow
and extended declarations*):

| Concern | Properties |
|---|---|
| Graph | `nodeBeans` (bean ids); `ignoredBeans` (support beans that are never nodes; never services); `eventBeans` (compatibility: beans that only define event types); `referenceBindings` (`ReferenceBinding`: `ownerBean`, `referencedBean`, `mode` `TRIGGER` by default, `DATA` or `PUSH`, optional `referenceField`, `parentUpdateCallback`) |
| Inbound | `eventTypes` (FQCN strings; never `eventClasses` in XML for the starter, which skill.md calls *"the single most important rule for the skeleton flow"*); `eventHandlers` (`EventHandlerBinding`: `event`, `nodeBeans`, optional `filterString` or `filterId`, `propagate`, default true); `strictEventHandlerBindings`; `signalHandlers` (`SignalBinding`) |
| Services | `serviceTypes` (interface FQCN strings, never beans); `serviceRegistrations` (`ServiceBinding`: `service`, `serviceName`, `nodeBeans`; always name services); `strictServiceBindings`; `exportedServices` (`ExportedService`) |
| Outputs and lifecycle | `sinks` (`SinkBinding`); `lifecycleNodes` |
| Audit | `logLevel`; `auditors` |

`eventHandlers` states a design-time expectation, not routing: Fluxtion still discovers dispatch from the Java
annotations (contract, *Handler expectations are not routing*).

**Semantic checking, in two tiers** (contract, *Structural validation*):

- **At drop time, before anything is generated, with no class loaded:** well-formed, schema-valid XML; unique bean
  ids; every class a name string; the configuration lists read as strings; cross-references by name (every
  `nodeBeans`, `ignoredBeans` and `eventHandlers` node is a declared bean id, and every handler's event is a declared
  event type); no dangling `ref` in constructor arguments or properties; and the service rules (a service is never a
  bean and never constructor-wired). Locally this is `validate.sh`, the offline XML tier.
- **After generation, at compile:** whether a node really has the declared handler (*declared event has no handler*
  is a build warning, not a drop-time error); for classes that already exist, that they satisfy the XML
  (`SPRING_DECLARATION_UNSATISFIED_BY_EXISTING_CLASS`); and, with `strictEventHandlerBindings` or
  `strictServiceBindings`, a node that handles a declared event, or registers a declared service, outside its binding
  fails the build.

**Is "a bean in no list" silent? Not caught at drop time, and not silent at compile.** The drop-time cross-references
run one way: every *listed* name must be a declared bean, and nothing requires a declared bean to be listed, so an
orphan bean passes validation. The compiler reports it: **`SPRING_BEAN_NOT_SELECTED`**, severity **WARN**, for a bean
*"declared, in neither list, and referenced by nothing"*, telling the author to list it or ignore it (the public
diagnostic page above). The build stays green, and the warning is in the compiler's messages, which `add-a-node`
already tells an author to read. The teaching says exactly that. The `add-a-node` skill's heading *"The two things
that fail SILENTLY here"* predates the diagnostic; correcting it is a separate fix outside M79 (a `docs/skills/`
change, not docs-only in CI, rule 4).

**Template generation.** The starter (a drop on `/start`, or `generate.sh` locally) generates the project from the
XML: a node class per `nodeBeans` entry, an event class per declared event type, and an interface stub per declared
service. A node named in `eventHandlers` gets an `@OnEventHandler` stub; a node with constructor refs gets that
constructor dependency and an `@OnTrigger` method; `lifecycleNodes` get `@Initialise`, `@Start`, `@Stop` and
`@TearDown` stubs; a `serviceRegistrations` consumer gets an `@ServiceRegistered` callback. A class already present
is checked against the XML, not shadowed, and a developer's implemented bodies are kept (reconciliation, with the
`fluxtion-authoring.json` ownership record). The XML carries structure, never logic.

**What this changes in M79.** The teaching rules lead with the XML contract, not with annotations: the generic walk's
step 1 lights the declaration (§5.2), the skill starts from it (§11.6 b), and the instructions paragraph states the
edge rule first (§11.6 a). Template generation reshapes *Make the diamond yours* (§14).

## 12. The scorecard

Scored by the robot walk (a virgin LLM driving the real `Main` under an isolated `user.home` at 1200×800) and, where
marked, by human or stranger runs (*Who scores what*, below). Where the existing machinery fits, it is reused: the M74
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
| **SC-T1** | First answer from an assistant | M77's robot walkthrough, **T19**: 6 acts, no dead end (M77 §16) | **unchanged or better.** The wizard still opens at the first Ask; the first minute adds no act to that path | M79-1, M79-2, M79-6 |
| **SC-C1** *(insider; human or stranger runs only)* | **Prediction correctness:** after the walk, for the two held-out cycles of the generic run, record [3] (`b` unchanged, `c` changed) and record [5] (a fresh change), predict which nodes run, then check by clicking. The prediction is scored only if recorded before any selection of record [3] or [5], and before the shipped README or `run-console.txt` is read (both state the answer) | — | **2 of 2 correct in ≥ 4 of 5 runs** | M79-1 |
| **SC-C2** *(insider; human or stranger runs only)* | **The right flag:** the broken-diamond quest with No AI, the person's own flag | — | {Product} confirms the flag in **≥ 4 of 5 runs**, **median ≤ 5 min** | M79-4 |
| SC-E1 | Auditor-prose hesitations on the first view | the stranger's F5 | **0**; F5's sentence not visible without a click | M79-3 |
| SC-D1 | Docs/app label mismatches on the pages M79-5 touches (*Start here* and the start-page pages) | F2's pairs | **0** (the nav's page count is DOCS-IA's, not M79's) | M79-5 |
| SC-T10 *(new)* | **Connect your own Claude:** from the start page to the person's client making its first `context` call, with `claude` installed | the M77-3 count (r7: M77-3 shipped complete in 1.36.0, PR #162; its own acceptance count was not run as an SC-T10 scorecard task, so the baseline is still not yet measured — it is now measurable against the real wizard, not blocked on M77-3 being built) | **≤ 5 acts in {Product}, 0 dead ends**; the acts in the person's own client (opening it, pasting the prompt) are counted separately, since M77-3's own confirmations (external access, then registration) already use two | M79-6 |
| **SC-T11** *(new)* | **The teacher:** with the person's own Claude and the `teach-fluxtion` skill, solve the broken diamond | — | measured, not yet targeted: **minutes** to *Solved*; **solved by the person's own flag** (yes/no); **the client's claims graded against §11.7's teaching rules and §11.6(a)'s confirmed callback rules** from the transcript, captured by the `capture-conversations` harness under an isolated home. **Witness:** a run in which only the client flags the answer record must score *not solved* | M79-6 (skill), M79-4 (quest) |
| **SC-T12** *(new)* | **The onboard teaching conversation, then live:** follow the teaching pack in the docked assistant panel to its end, press *Continue with Claude*, and ask one question live | — | measured, not yet targeted: **acts** from the pack entry to the first live answer; **the hand-off works** (a live turn completes, and its opening context carries the fixed basis sentence); **the recorded turns are never resent**: none of their text appears in any request the session sends (the in-app request, or the stream written to Claude Code). **Witness:** a build that appends the passive log to the request must score *resent* | M79-2 |

**The Build door's tasks are not here.** Authoring from zero (the person's own processor generated, compiled, run,
explained and changed: SC-B1–SC-B6) is scored by [`spec-authoring-scorecard.md`](spec-authoring-scorecard.md) (M82,
DRAFT r2), under this section's rules.

**Who scores what** (lead's decision, creative round 2). The comprehension tasks, **SC-C1 and SC-C2, are scored
only by human or stranger runs**: a model predicting a dispatch it can read from the shipped source measures the
model, not the product. **The robot walk counts acts only** (SC-T1, SC-T9, SC-T10, SC-S1, and SC-T11's minutes and
flag source).

**About the ids and baselines** (review, 2026-10-18). The `SC-` ids are this spec's own; no earlier table defines
SC-T1…SC-T8, so *(new)* means new to M79's list, and SC-T1's baseline is M77's T19. The two reviews of 2026-10-18 that
give the other baselines (*about 225 words*, F5's hesitation, F2's pairs) are not in the repo. Before the first
scored run, their relevant passages, sanitised under rule 1, go in `docs/specs/evidence/m79-baseline/`, or the
baselines are re-measured there by the robot walk on the current build. A target with no recorded baseline is a
target, not an improvement claim. **On the targets:** five runs (SC-C1, SC-C2) detect only a large failure rate; they
are a smoke gate, not a measurement, and are reported with their counts. SC-S1 is tight: the start page as drafted in
§5.4 counts about 38 words in its longest AI-line state as r5 listed them, and the sixth state r6 adds (*AI: your own client · Ask… · Change…*) is one word longer than *Claude (Safe)*, so about 39: the budget has no slack, and any added word (an empty *Recent* placeholder such as
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
- **A security review before acceptance** (OD-79.25, accepted; the second review's M9). The private-repository path
  builds and, on *Run it for me*, runs `git` commands from a file someone else sent, under the person's own
  credentials and git configuration. It is not accepted with this follow-on: it has its own security review first
  (URL and host validation, argument construction, hooks, filters and templates, credential prompts, the folder's
  containment), recorded with its spec, before any of it is built. The public-repository path reuses the reviewed
  example download and is not gated on it.
- **Open for its own spec:** whether a private repository's host must be one the person has used before; how a
  fetched root is shown in the Project panel (fetched from …, at commit …); and signing (OD-79.9).

Its own spec amends `completed/spec-settings-share.md` when it is scheduled, and the whitelist test grows by the new
categories.

## 14. Other follow-ons (recorded, not in M79)

- **The criterion chip** (M76 slice 1), and **the before/after run comparison**: the diamond is their natural first
  fixture.
- **No keyless runs** (owner, 2026-10-18): interpreted Fluxtion also needs the key, so the keyless authoring
  options are withdrawn. **A teaching-scoped hosted key** is the preferred entry to paid usage and the route by which
  a newcomer first generates their own diamond; it is a commercial follow-on for the owner's gateway / RapidAPI work.
- **More domains:** each is a new real run under §6. None may be added by relabelling.
- **N2 — a recorded diagnosis session of the broken diamond** (**accepted**, OD-79.18; one live capture run on the
  owner's plan, under an isolated home): a real session,
  captured, replayable, and labelled *"recorded session · not live"*, as conversation journeys label recorded
  dialogue (OA-D4).
- **N3 — *Share what you proved*:** a bundle of a green criterion chip with its deciding record and a two-step walk;
  after M76 slice 1.
- ***Make the diamond yours*:** the Build door's first authoring step. **It needs a key**: the teaching-scoped hosted
  key above is its intended route. **The designed shape** (r6, from §11.7): skill.md's six-step design conversation,
  held in the onboard assistant (purpose → the events coming in → the nodes and which handles which event → the
  dependencies, which are the edges → any external services → emit the XML and restate it in plain English). It
  produces **one** XML that passes drop-time validation, shown in the M66 Design view beside the topology; the starter
  generates the stubs; the person (or the assistant) fills in the logic; generation runs (the key); and the run's
  audit log is the evidence that the diamond in the person's domain does what they described. **Wire first, stubs
  second**: because the starter generates classes for whatever the XML declares, the domain classes need not exist
  before the wiring is right. That is the owner's intent-verified thesis made concrete: intent in words, structure
  as checked XML, mechanics written by Fluxtion, intent validated by evidence. A design point for its own spec, not
  M79.
- **An analogy legend** beside the real names (for example *a = "the input"*), never replacing them (E2). It is
  allowed without a key only because it runs nothing.
- **MCP prompts** for the teaching steps (§11.6 e), gated on rule 6.
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
| ★ A1.5 | E4–E6: per domain, records [2]–[5] are traced; record [2] has four node entries and record [4] only the top node's; the record digests equal §6.4's; coverage reports every node able to log; an edited copy opens without the tiles or the bundled label; an edited extracted copy is re-extracted; a substituted step-3 record makes step 3 *not shown*, with no caption on any of its targets | "d did not run" shown over evidence that does not say it | drop the record-basis rule of E5, so `topology:node:d` keeps its caption |
| ★ A1.6 | the start page's word budget (§5.4's counting rule): ≤ 39 in every AI-line state, at 1200×800 *(frame)* | the page growing back | build `directStrip` (the *Or open directly* groups) expanded, without the expanding act; or restore the hero's always-visible drop-hint paragraph (*Drop an .fexp bundle, audit log…*) above the fold |
| ★ A1.7 | §5.5: a manifest mismatch or a failed open → no walk, not marked played, one start-page line with the open's reason, no dialog | a broken asset hidden, or a modal at an empty screen | autoplay without the manifest check |
| ★ A1.8 | `OneDispatchModelTest`'s §7.4 rules | hand-placed dispatch | call `openDiamond` from a tile's listener |
| ★ A1.9 *(frame, real `Main`, isolated `user.home`, 1200×800)* | **The first-minute walkthrough:** first launch → the diamond opens with the graph centre stage → four steps → step 3 selects the record with no `d` entry and `d` is dark → the end state → one click on a record lights its cycle. Then relaunch → no autoplay, and no session-recovery offer for the diamond (the start page) | SC-T9's path regressing; the diamond offered back as "your session" | write `played` on Esc only, so a played-through walk replays |
| A1.10 | `DiamondAssetsTest`: every file's SHA-256 matches the manifest; the source ships (E7), and for the generic domain its Spring XML too (`diamond.xml`, `f050de05…`, OD-79.21); the sweep covers the set (§18) | a hand-edited asset, a leaked name | — |

**M79-2**

| # | Test | Catches | Witness |
|---|---|---|---|
| ★ A2.1 | the teaching conversation plays from a pack's `teach:` entry and from the Learn door, in the docked panel, with the composer hidden and the header's *Recorded conversation* label on every play; nothing is sent or written; it never autoplays on open (OA-D6) | a teaching conversation that looks live, or runs a model | drop the header label; or autoplay on open |
| ★ A2.2 | a `teach:` entry naming an id that is not a shipped teaching conversation, or a shipped teaching conversation whose digest differs from the jar's manifest, is refused with its line and cause; a pack cannot carry its own conversation (OD-79.19) | an untrusted file claiming *Recorded* | accept an inline conversation |
| ★ A2.3 | *Continue with Claude*: the live session starts or attaches (in-app and Claude Code, through `FixtureAgent`); the recorded turns stay above in the read-only band; **no request contains any recorded turn's text**; the opening context carries the fixed basis sentence and not the recorded words | the model fed words it never said; a hand-off that loses the teaching conversation or reaches no session | append `TeachingLog` to the request; or build the basis from the turns |
| ★ A2.4 | with nothing set up, *Continue with Claude* opens the M77 wizard (`origin=TEACHING`) and completes the hand-off on Finish; Cancel leaves the teaching log and starts nothing | a dead end after the teaching conversation | start the in-app mode without the wizard |
| ★ A2.5 | the teaching conversation's recording: every fact a turn states re-derives from the bundled diamond log (`JourneyCatalogueTest` style); each step's record targets carry §6.4's digests; a substituted record withholds the step's turns and captions (E5) | a stale or hand-typed recording | edit a recorded number |
| A2.6 | `OneDispatchModelTest`: no request path reads `TeachingLog`; the panel posts intents only | hand-placed dispatch | read the log in `PromptBuilder` |
| ★ A2.7 | the band and the basis sentence appear only for a shipped teaching conversation whose definition matched the manifest's digest when it played (OD-79.24): a customer's own walk with a `recorded` conversation, ended by hand-off, gets neither, and a shipped conversation whose digest no longer matches gets neither | a customer's recording relabelled as {Product}'s teaching | gate the band on `conversation.kind == recorded` |

**M79-3**

| # | Test | Catches | Witness |
|---|---|---|---|
| ★ A3.1 | the chip's three states for every row of §8.1, from the snapshot alone; no row without a byte or digest check shows ✓; `unknown` is ⚠; the metadata observation is computed once (in the effect) and `context` reads the published value | a metadata check shown as verified (OQ-1); an unassessed log shown as checked | map `unchanged-metadata` to ✓; or recompute freshness in the view |
| ★ A3.2 | the details popover holds every existing row's prose verbatim | the caveat lost behind the summary | drop the basis sentence |
| A3.3 | `context` and the MCP replies are byte-identical before and after M79-3 for the demo fixture | a display change leaking into the protocol | — |
| A3.4 | each of the four terms' first appearance on the start page, the Context column and Settings carries an accessible description, in the existing wording (§8.2) | a control a screen reader cannot explain | — |

**M79-4**

| # | Test | Catches | Witness |
|---|---|---|---|
| ★ A4.1 | completion: a flag on record [3] (digest `7e31f039…`) → *Solved*; a flag on record [2], [4] or [5], or an assistant message saying "solved", → not solved; a flag over the socket is attributed to the socket; `main`'s record [3] (digest `2c07ecfb…`, same index) → not solved | a model completing the quest, a near miss accepted, an index matched across runs | compare record indexes instead of digests (the `main` log's record [3] then passes) |
| ★ A4.2 | the teaching label appears only when the bundle's SHA-256 matches the quest manifest; an edited copy is an ordinary bundle | a label taken from the bundle's own notes | read the label from the notes |
| ★ A4.3 | the whole quest with the profile `OFF` and no network | a hidden AI or network dependency | require an assistant for hint 3 |
| ★ A4.4 | the manifest does not name the answer's index; the answer is salted and equals `883c529b…`; the manifest's log SHA-256 equals `8a2817ee…` | a spoiler in the jar, a re-run substituted for the pinned one | store the unsalted digest; or ship a re-run log |
| ★ A4.5 | teacher mode on the onboard route: a flag on the answer record made by the onboard Claude Code session (through the hosted bridge, which reaches the socket entrance) leaves the quest open and shows *"Your AI client flagged this — try it yourself"*; the person's own flag then solves it (OD-79.17) | the onboard teacher solving the learner's quest | let a bridge flag solve it |
| ★ A4.6 | **flags are byte-identical across the migration** (§9.2): for the demo fixture, a fixed script of flags set by the F key and by the socket (`flag {recordIndexes}`, `flag {byteOffsets, note}`) yields the same bytes before and after M79-4 for the `flag` verb's schema and reply, `context.flags`, the saved session-resume `findings` list, and a recovery's restored flags and outcome line (the before-bytes captured from `main` as a golden file in the PR) | the rule-9 migration changing what the customer's tools read or what a saved session restores | add the record digest to `context.flags` entries, or to `Finding.toMap()` |

**M79-5**

| # | Test | Catches | Witness |
|---|---|---|---|
| ★ A5.1 | `DocsLabelParityTest` | F2 returning | the docs' *Open the demo log* |
| A5.2 | *Start here* is first in the nav, its three cards carry the app's visible labels, and `mkdocs build --strict` passes (the home page's words and the nav's page count are DOCS-IA's tests) | the front door missing or mislabelled | — |
| A5.3 | the GIF is generated by the harness under an isolated home; every frame is read (rule 1) | a real name in a frame | — |

**M79-6**

| # | Test | Catches | Witness |
|---|---|---|---|
| ★ A6.1 | **Connect your own Claude** opens the wizard at 2b with Claude Code chosen and nothing applied; it shows the same confirmations as M77-3 (no consent skipped) | a shortcut around M77's consents | apply external access on the button press |
| ★ A6.2 | after the live check ✓, the Summary's first action is the pack's first entry; the pack is words only (`PromptPack` parses it, with no example entry) | a pack that runs something | an entry with a link |
| ★ A6.3 | `BridgeInstructionsTest`: the added paragraph says to call `analyser_context` first and carries the reading rules of §11.6(a), within 70 words, edge rule first and with no callback-mechanics clause (OD-79.20); the rest of `INSTRUCTIONS` is byte-identical to today's; `context` with no learning activity is byte-identical to today's reply; and `context.learning` is present both with no log loaded at all (the fresh-start early return, §11.6 c) and with the quest's bundle open and none of the person's own logs loaded | the teacher not pointed at the project; a token-heavy or drifting instruction; a protocol change leaking to the customer | drop the sentence; or emit an empty `learning` object |
| ★ A6.4 *(periodic, measured)* | `teach-fluxtion` skill: frontmatter served in `context.runbooks[]`; its prediction step checks with `goto`/`spotlight`, never by assertion (the capture-conversations harness transcript, SC-T11) | a teacher that tells instead of checking | a skill step that states record [3]'s answer |
| ★ A6.5 | learner progress: `context.learning` and the *Learning* row render the same snapshot; a socket flag on the answer record leaves the quest unsolved and shows *"Your AI client flagged this — try it yourself"* (OD-79.17; the same rule for the onboard route, A4.5) | a client completing the learner's quest | let a socket flag solve it |

A6.4 is a **periodic, measured check, not a CI gate**: its transcript comes from a live client run under the capture harness (SC-T11), on the release-screenshot cadence. Its frontmatter half (served in `context.runbooks[]`) is a static check and runs in CI.

## 16. Owner decisions

| # | Decision | Owner (2026-10-18) |
|---|---|---|
| **OD-79.1** | The aha is the diamond, in the person's own **domain**: the same topology with domain names, a dropdown (mathematics, accounting, logistics, trade processing, a generic default; r3: five picture tiles, the lead's decision). **Lead, evidence integrity:** each domain is a real run of a real variant, generated and run once; display relabelling of one log is not allowed. The variants are built in `fluxtion-diamond`; their logs and GraphML ship in the jar. | **Accepted** |
| **OD-79.2** | The first launch **autoplays** the diamond walk once (Esc skips); first run only (no recent project); never again. Amends M36 D-S1 (§3). | **Accepted** |
| **OD-79.3** | The diamond log and GraphML ship inside the jar, like the DEMO set. | **Accepted** |
| **OD-79.4** | A planted-bug teaching bundle, *the broken diamond*: a real run of a deliberately buggy commit, labelled as teaching; {Product}, never the model, confirms the right record by digest. | **Accepted** |
| **OD-79.5** | **The pivot:** AI setup leaves the first launch; **supersedes M77 OD-77.1** (§3, with the reason). The wizard opens at the first Ask, Send or Start building; the start page offers AI only as a quiet line. | **Accepted; confirmed** |
| **OD-79.6** | **The customer insight:** for developers who already use an AI client, the fastest path is their own Claude plus project context; the start page and packs make it first-class, as one action (M79-6; r5 said M79-2, from before r4 moved the external kit). | **Accepted** |
| **OD-79.7** | The graph is centre stage **only during walks and first contact**; the records table stays the main view for investigation, because the paying customer's developers use it daily. The toggle is `walkPlayback`'s (rule 9). **Revisitable** (*"we can always change"*). | **Accepted** |
| **OD-79.8** | Colleague follow-on (§13): harmless rows default to Yes, which maps onto M15's existing ticked categories; consequential rows (AI mode, external access, fetching an example) start at No and only preselect the M77 wizard or the download confirmation. | **Accepted** |
| **OD-79.9** | Colleague follow-on: provenance shown as *"says it is from …"* plus a short sha256 fingerprint; signing is one later decision for settings files, packs, starters and bundles together. | **Accepted** |
| **OD-79.10** | Colleague follow-on: no `.fexp` fetch by URL in v1; the bundle is sent alongside. | **Accepted** |

**The two questions left for the first review, answered** (independent review, 2026-10-18):

- **OQ-1 — yes, *verified* overclaims; resolved in §8.1.** For an ordinary log the only on-disk check is metadata
  (size, modification time, file key), computed in the frame, and the session's identity verdict is null unless
  Follow is on; M68 D-E6 already rules that unchanged metadata is *unverified* and never reported as unchanged. A
  bundle is checked byte-for-byte at open (SHA-256 per member, unsigned) and its working copy is not re-checked. The
  chip therefore has three states (lead's decision): *Evidence ✓ checked* only for a byte or digest check,
  *Evidence · as read* for metadata only, and *⚠ changed / unknown*.
- **OQ-2 — accept the 4–8 s dwell, with three refinements (§7.3).** The dwell counts all of a step's captions; a
  step not fully shown waits for the person; the last step's timer ends the walk into the doors. Advance with **→**
  or the strip's **Next ▶**, go back with **←**, skip with **Esc**. **Not** *any click advances*: M69 §3.7 classifies
  every press outside the strip as *end the walk*, and here that is right, because the press the end state invites is
  a click on a record; a click that advanced instead would swallow it. Tune the constant on the first robot run.

**Raised by the review, for the owner** (none blocks writing M79-1's node; each blocks the part it names):

| # | Question | Recommendation | Owner (2026-10-18) |
|---|---|---|---|
| **OD-79.11** | The *Start here* page's doors (§10) against the approved DOCS-IA nav, whose home page (`index.md`) has four doors and whose new `start-here.md` outline has four task cards and an administrators' line: M79's three, or DOCS-IA's? (The page count is no longer M79's question: the restructure is DOCS-IA's.) | map onto DOCS-IA (it is approved and its audit is the newer evidence about the docs); keep *Learn / Investigate / Build* as the app's words and make DOCS-IA's *Diagnose* door read *Investigate* | **Accepted** (owner, 2026-10-18): the doors map onto DOCS-IA's |
| **OD-79.12** | M79-6's added sentence in the bridge `instructions`, which the customer's external Claude reads at every connect (§11) | add it; it changes no verb or reply, and it is what their developers' prompts already do | **Accepted**: the ~60-word addition, with the third reading rule held out until the empirical probe confirms it (§11.6 a). *r6: the probe confirmed it, and OD-79.20 keeps it out of the text as mechanics; the paragraph is composition-first.* |
| **OD-79.13** | Should the Investigate and Build doors also set *Work mode* (§5.4)? | not in M79; a door opens a thing, the posture stays the person's | **Accepted**: doors do not set the work mode |
| **OD-79.14** | Step 4's *"That is proof, not silence"* against M76's ban on *proof* (D-7) | keep it, conditioned: M76's rule becomes *absence is proof only in a traced record*, which is the format spec's rule | **Accepted**: keep *proof*, for traced records only |
| **OD-79.15** | The first-run condition (§5.1): a fresh config **and** no recent project, file or bundle, not *no recent project* alone | yes; it is what protects the paying customer's developers on upgrade | **Accepted**: the stricter first-run condition is confirmed |
| **OD-79.16** | A first-minute-only session is not offered for recovery (§5.1) | yes | **Accepted**: no recovery offer for a diamond-only session |
| **OD-79.17** | Teacher mode: only the person's own flag solves the quest; a client's flag shows *"Your AI client flagged this — try it yourself"* (§11.6 d) | yes (the lead's recommendation) | **Accepted**: only the person's own flag solves the quest; a flag from an AI client or the onboard session reads *"your AI client flagged this — try it yourself"*; both routes |
| **OD-79.19** | May a dropped pack carry its own recorded conversation, or only name a teaching conversation shipped in the jar (§11.3)? | only a shipped teaching conversation in v1: an untrusted file's *Recorded* label is the exporter's claim, which {Product} cannot check; a pack's own conversation, if allowed later, plays as *declared recorded* | **Accepted**: in v1 a pack may only name a teaching conversation shipped in the jar; a pack's own recorded conversations come later, with signing |
| **OD-79.18** | N2, a recorded diagnosis session of the broken diamond, as a follow-on (§14) | — (the lead proposes it) | **Accepted**: it needs one live capture run on the owner's plan, under an isolated home |

**Raised by the second independent review, decided** (owner, 2026-10-18; each accepted as the review recommended):

| # | Decision | Review's recommendation and reason | Owner (2026-10-18) |
|---|---|---|---|
| **OD-79.20** | **Composition-first teaching** (the review's B1): re-centre the teaching conversation, the `teach-fluxtion` skill and `INSTRUCTIONS` on how a graph is composed (§11.7): edges are declared parents; membership is declared; the returned boolean decides propagation; silence is not *did not run* unless traced. The `@AfterEvent` detail becomes a secondary note | yes, and drop the mechanics framing from `INSTRUCTIONS`: a newcomer learns what they declare before how the dispatcher runs it, and every connect pays for each word | **Accepted**. Applied in §11.1, §11.6 a–b and §11.7, led by the Spring XML contract. The review's wording of the membership rule (*a bean in no list is not in the graph*) is corrected to the contract's (§11.7 rule 2) |
| **OD-79.21** | **Spring XML beside the topology, for the generic domain only**: the generic walk re-pinned to the Spring-authored run at the same pin; Spring variants for the other four domains deferred | yes: the XML is the composition, so showing it beside the graph it produced teaches rule 1 by sight; one domain keeps the change small | **Accepted**. §4 D-1, §5.2, §6.4 (the files exist at `24d6d74`: log `6291ba05…`, GraphML `7ebd36b0…`, byte-identical to the builder run's, XML `f050de05…`; record digests recomputed) |
| **OD-79.22** | **The name clash with M75's *lesson*** (`.lesson.md`, `read:`): rename M79-2's artifact | yes: two features under one word, in one pack format, would confuse both the docs and the parser | **Accepted**. Renamed the **teaching conversation** throughout (§11 opening gives the check against existing terms): `teach:`, `TeachingLog`, origin `TEACHING`, *Teach me the diamond* |
| **OD-79.23** | **Split M79-1a** into 1a-i (the generic domain only, one hand-written walk) and 1a-ii (the four other domains, the tiles and the caption template) | yes: 1a was the largest PR, and the template and tiles are separable from the first minute itself | **Accepted** (§17) |
| **OD-79.24** | **Gate the passive-log band and the fixed basis sentence on a shipped, digest-verified teaching conversation**, not on `conversation.kind` | yes, so that a customer's own recorded walks are never relabelled in {Product}'s words | **Accepted** (§11.1 item 6, §11.3, A2.7) |
| **OD-79.25** | **The private-repository Settings-share category** (§13) needs its own security review before it is accepted (the review's M9) | yes: it builds and may run `git` commands from a received file under the person's credentials | **Accepted** (§13) |

**The key questions, decided** (owner, 2026-10-18):
- **Interpreted (non-AOT) Fluxtion also requires the key. There are no keyless runs**: that is the commercial moat.
  The keyless authoring option through interpreted mode is removed. The shipped diamonds are pre-generated runs that
  the person *reads*; nothing in M79 compiles or runs a processor without a key.
- **A teaching-scoped hosted key is the owner's preferred entry path to paid usage.** It is the route by which a
  newcomer first generates their *own* diamond (*Make the diamond yours*, §14), and a commercial follow-on for the
  owner's gateway / RapidAPI work, outside M79.

**Raised by the philosophy-packs scope (r7, 2026-10-19), for the owner — OPEN, not decided here** (§19 gives the
full reasoning each recommendation rests on):

| # | Question | Recommendation | Owner |
|---|---|---|---|
| **OD-79.26** | Do 01, 02 and 04 **move** out of `docs/proposals/packs/fluxtion-philosophy/` into their docs-site location (§19.2 item 1), or stay there and get **copied**, with the site holding the published copy? | Move, with a one-line pointer left in the README's table (the CLAUDE.md rule 7 / DOCS-IA pattern for a retired location): a copy drifts, and `proposals/` is pre-acceptance text these three have left. | **Accepted** (owner, 2026-10-19) |
| **OD-79.27** | Which pack (if any) gets the first `teach:` entry once M79-2 ships the *The diamond* teaching conversation, and does that land as part of M79-2's own PR or a small follow-up after it? | 04 (*Audit logs and evidence*) is the closest fit — the teaching conversation's content is composition-and-silence, which 04's entries already probe — as a small follow-up PR after M79-2, not inside it, so M79-2 is not gated on a packs edit. | **Accepted** (owner, 2026-10-19) |
| **OD-79.28** | Should the Python lint harness (`tools/m74_onboarding_runner.py lint-pack`, cross-checked against the Java parser by `tools/m74_record_pack_crosscheck.py`) also gain fixture rows for 01, 02 and 04 at their new published paths, duplicating A7.1 in the existing dual-check pattern? | Yes, for parity: the existing 89-input cross-check would otherwise cover every pack except the three that actually ship. | **Accepted** (owner, 2026-10-19) |

## 17. Phasing and size — one PR each, under about 5,000 hand-written lines

Generated `SessionProcessor` lines are not counted. Each PR is a reviewed feature branch with its CHANGELOG lines
(rule 2), its tests green (rule 4), and its scorecard run recorded (§12).

| PR | Content | After | Size |
|---|---|---|---|
| **M79-1a-i — the first minute, generic domain** | `firstMinute` (with `sessionRecovery` as a parent); `Launch.FIRST_MINUTE` and `LogOpened.launch`; `stage`, `origin`, autoplay advance, the record-basis caption rule and first contact in `walkPlayback`; the **new topology-centre layout** (the canvas moves out of the right-hand tabs), with the generic design beside it (OD-79.21); `WalkReelRecorder` onto the advance effect; the generic Spring-authored run, its XML and GraphML, the manifest and digest-checked extraction; **one hand-written generic walk** (§5.2), played through the generalised journey effect; the end card; no recovery offer for a first-minute session; A1.1–A1.3, A1.5 and A1.7–A1.10 for the generic domain | D-1, D-6, D-7 | **L** |
| **M79-1a-ii — the four other domains** | the mathematics, accounting, logistics and trading runs and GraphMLs; the caption template filled from each GraphML's structure (§6.3); the five tiles and `firstMinute.domain`; A1.4, and A1.5 per domain | M79-1a-i | **M** |
| **M79-1b — the start page** | the start page under 40 words (`StartPanel`, 1,358 lines, re-confirmed on `main` at 1.37.0), the collapse, the visible-label changes and constants, the short AI line against the real six-state `aiLine()` (D-3, satisfied); A1.6 | M79-1a-i; M77-2 and M77-4 have already shipped (1.36.0), so this PR edits the page as it stands, not a moving target | **M** |
| **M79-2 — the teaching conversation in the onboard assistant** (phase 2, owner) | the teaching conversation as a shipped walk with a recorded conversation, its real capture (the `capture-conversations` extension) and its re-derivation test; the pack's `teach:` entry; *Continue with Claude* (`AssistantHandoffRequested(origin=TEACHING)` for in-app and Claude Code, the wizard branch); `TeachingLog` and its read-only band; the basis sentence; A2.1–A2.7 | M79-1a-i; M77-2 for the wizard branch (D-4) | **L** |
| **M79-3 — quiet evidence wording** | the three-state chip, `EvidenceChipLines`, the details popover; **the metadata observation moved into the session** (§8.1, rule 9); the accessible descriptions (tooltips cut); A3.1–A3.4 | — | **M** |
| **M79-4 — the broken-diamond quest** | the teaching bundle and quest manifest; **flags routed through the session** (a fact, a node, both entrances; §9.2); the `quest` node with teacher mode (OD-79.17); the quest card; A4.1–A4.6 | D-2 (published at `6bff0d67`), M79-1a-i | **L** |
| **M79-5 — the docs front door** | *Start here*, its GIF and `DocsLabelParityTest` only (the restructure is DOCS-IA's); A5.1–A5.3 | M79-1b (labels, GIF); OD-79.11 | **S** |
| **M79-6 — the external teacher kit** (phase 3 or later) | *Connect your own Claude* and its wizard `origin` (`START_CONNECT`); the shipped pack and its load fact; the Summary's first action (M77 §6.4 amended); the instructions paragraph after its rule-6 check and `BridgeInstructionsTest`; the `teach-fluxtion` skill (its quest step once M79-4 has merged); `context.learning` and the *Learning* row; A6.1–A6.5 | M79-2; M77-3 (shipped, 1.36.0); OD-79.12 | **M** |
| **M79-7 — the first philosophy packs** (owner, 2026-10-19, §19) | 01, 02 and 04 published on the docs site as valid, build-tested prompt packs; both *Coming* notes retired; A7.1–A7.3. No `teach:` entry in this PR (§19.2 item 4; OD-79.27 is a later follow-up, not this PR) | M79-1a-i (the diamond this round's example and the first minute's own diamond are the same evidence, §19.1); **not** M79-2 | **S** |
| **Total** | **9 PRs**, in the order below | | |

**Sizes, not days** (owner, 2026-10-18). S, M and L are relative: an L carries a new node or a migration of a
surface the customer uses, an M extends existing nodes and surfaces, an S is docs and tests. Calendar-day estimates
are left out on purpose: at today's agent-assisted pace (M77-1…5 and M78 D1 were built in about a day), a day count
misleads more than it informs. What the sizes do say is where review effort goes.

**Why the work grew** (review, 2026-10-18). r1 assumed three things the code does not have: a stage (the
topology is a right-hand tab, so centre stage is a new layout), a flag fact (flags are frame state; making them the
session's is a rule-9 migration of a surface the customer uses daily), and a session-held metadata verdict (it is
computed in the frame). It also put the start-page rewrite, which collides with M77-2 and M77-4 on the same page,
into the first PR; it is split out as M79-1b so that the first minute can be reviewed on its own. **r3** (the lead's
round 2) adds the teacher kit to phase 2 and teacher mode to the quest, and takes out the tooltips, four of the five
hand-written walks and the docs restructure; the net change is small. **r4** (the owner's correction) makes phase 2 the onboard teaching session and moves the external kit
to M79-6, adding the teaching conversation's real capture and the hand-off. **r6** (the second review) splits M79-1a into 1a-i and 1a-ii (OD-79.23), so the first minute can ship on the
generic domain while the template and the tiles follow. **r7** adds M79-7 (§19, owner, 2026-10-19): three of the
eight drafted philosophy packs, published and build-tested, with no code dependency beyond the diamond M79-1a-i
already ships; it also records D-3/D-4 as satisfied (M77-2 and M77-3 shipped in 1.36.0), D-7's two specs as now on
`main`, and a new D-8 for M80's Design-pane overlap. **Order:** M79-1a-i, M79-1b, M79-1a-ii, M79-2, M79-3, M79-4,
M79-5, then M79-6 (phase 3 or later, any time after M79-2). M79-1a-ii and M79-7 may each land any time after
M79-1a-i; M79-7 needs nothing else. **The first slice to start: M79-1a-i** — it is the one PR every other phase
depends on, directly or through the diamond it ships.

## 18. Public-repo rules for every M79 asset (rule 1)

- The domain variants, the teaching bundle, the walks, the GIF and the screenshots use generic names only (§6.3). No
  real company name appears anywhere, including in prose about the rule.
- `DemoAssetsTest`'s sweep, which parses CLAUDE.md's sweep line at runtime, extends to the diamond and quest assets,
  with their GraphML, source and manifests. It iterates `DemoAssets.files()` only, so the new files join that list
  (or the test iterates the manifest too); a file outside both would ship unswept.
- The capture harness runs under an isolated `user.home`, with only the demo and diamond fixtures, from a jar copied
  under the isolated root, and `firstMinute.played` unset only for its first-minute run. Every frame of the GIF is read
  before commit.

## 19. M79-7 — the first philosophy packs (owner, 2026-10-19)

**Scope, named by the owner.** Of the eight drafted *Fluxtion philosophy* prompt packs
(`docs/proposals/packs/fluxtion-philosophy/`, drafted and merged at PR #155, 2026-10-19), **three ship with M79**:

| File | Title | Audience |
|---|---|---|
| `01-basic.md` | **What is Fluxtion?** | a Java developer who has never used Fluxtion |
| `02-intermediate.md` | **Local knowledge, global behaviour** | someone who has run a processor and read its records |
| `04-audit-and-evidence.md` | **Audit logs and evidence** | anyone reading a log in the analyser, or handed one |

They teach with the diamond and the broken diamond, the same evidence M79-1 and M79-4 already ship. **03 and 05–08
stay drafts**, out of M79-7's scope:

- **03 — *Thinking in Fluxtion*** also pins the same teaching example, but the owner's list names only 01, 02 and
  04 for this round; it is left for a later round, not excluded for a technical reason;
- **05 (Mongoose), 06 (embedded), 08 (plugins)** depend on material outside this repo (a Mongoose server
  configuration, a `Main` plus a JUnit test for 06, and the `telaminai/mongoose-plugins` reference repository for
  08 — whose README item 5, employer-domain SPDX headers at the pinned commit, is a rule-1 concern for *that*
  repository, not this one, and stays open there);
- **07 (C++ preview)** depends on `telaminai/fluxtion#44` (the public *C++ target: what works today* page), still
  open for review, and on the C++ toolchain's public availability (README items 2–3).

None of README's six *Needs owner input* items touch 01, 02 or 04: items 2–6 are 07's, 08's and 05's; item 1 (*"none
of the seven placeholder examples exists"*) is now **stale for 01, 02, 03, 04 and 06** — commit `d1e38e2b` (2026-10-18,
before PR #155 merged) pinned their entry 1 to the real archive — and still true, unchanged, for 07 only. This spec
does not edit the README; its own open questions are below (OD-79.26–.28).

### 19.1 What was replaced, and what was found already done

The task this section was written to close — *"replace each pack's placeholder teaching examples with the published
runs"* — **is already done for 01, 02 and 04**, found so by reading them (r7, 2026-10-19), not by editing them. Each
pack's entry 1 reads:

```
1. **Teaching example: the diamond** — example: https://github.com/telaminai/fluxtion-diamond/archive/dad9b4382099a9ff26d9fe624f01fbbc80f67c50.zip
```

**`dad9b43…` is the commit §6.4 already reconciles**, not a second, unpinned reference: §6.4 records that
`sample/diamond-audit.yaml` is byte-identical at `dad9b43` to the generic run at the first-minute's pin, `24d6d74`,
*"the commit the M74 Fluxtion philosophy packs pin"* — written in r6, before this section existed, from the same
fact. The packs pin the **builder-authored** run (`sample/diamond-audit.yaml`), not the Spring-authored twin M79-1
re-pins the generic first minute to (OD-79.21); §6.4 already says why that is fine (*"twin runs of one graph, not
one file"*) and this section makes no change to it. **Verified again for r7**: `python3
tools/m74_onboarding_runner.py lint-pack docs/proposals/packs/fluxtion-philosophy/01-basic.md` (and `02-`, `04-`)
each print a parsed pack with that example entry and no refusal, 2026-10-19.

### 19.2 What M79-7 actually adds

1. **Publish 01, 02 and 04 on the docs site**, as downloadable `.md` files, the way `user-guide/sample-prompt-pack.md`
   is today (§10, *packs defined and listed*). They move out of `docs/proposals/packs/fluxtion-philosophy/` — a
   `proposals/` location is pre-acceptance text, and these three are now accepted, shipping packs — leaving a
   one-line pointer in the README's table for each moved file, in the pattern CLAUDE.md's rule 7 and the DOCS-IA
   restructure already use for a retired page. 03 and 05–08 keep their README entries and stay under `proposals/`
   unmoved.
2. **A build test per pack, mirroring `PromptPackTest.theDocsSampleIsValid`**: each of the three published files is
   read with `PromptPack.parse` and asserted `ok()`, with its entry count re-checked (01 has 8 entries, 02 and 04
   have 9; `MAX_ENTRIES` is 9, so none has room to grow without trimming). A pack that stops parsing — a bad edit, a
   stray link, a label over 60 characters — fails the build, exactly as a bad edit to the sample pack would (★
   A7.1).
3. **The "Coming: the Fluxtion philosophy packs" note retires in both places it exists** — `docs/site/start/prompt-packs.md`
   (the `!!! info` admonition right after *The sample pack* section) and `docs/site/teach/index.md` (its *2. A
   prompt pack* step carries the same admonition) — and
   is replaced by three links to the published files, in the pattern the sample pack already uses (a relative link
   plus a `raw.githubusercontent.com` download link). This is this PR's scope, as the owner asked (not a separate
   DOCS-IA follow-up): both notes are small, self-contained edits, and leaving either stale after publishing would
   repeat F4 (*"packs are invisible"*, §1).
4. **The teach: constraint, stated for these packs specifically** (not a new rule — OD-79.19 and A2.2 already state
   it generally): a pack may name, with a `teach:` entry (§11.3), only a teaching conversation **shipped in the
   jar** when the pack is read. None of 01, 02 or 04 has a `teach:` entry today, and **M79-7 adds none**: it ships
   before M79-2 in §17's order (M79-7 depends only on M79-1a-i, below), so no teaching conversation exists yet to
   name. Whichever pack later gains a `teach:` entry pointing at M79-2's *The diamond* teaching conversation is a
   small follow-up **scheduled with or after M79-2**, per the owner's instruction; this spec does not pick which
   pack or build it now (OD-79.27).
5. **Acceptance:** ★ A7.1–A7.3, in §15's style, added below.

### 19.3 Acceptance tests

| # | Test | Catches | Witness |
|---|---|---|---|
| ★ A7.1 | 01, 02 and 04, at their published docs-site paths, each parse as a valid `PromptPack` (`theDocsSampleIsValid`'s pattern, one test per file or one parameterised test); each has at least one entry and at most `MAX_ENTRIES` | a pack that silently stops being droppable | edit one pack's entry 1 to drop the leading `example:` keyword, or push its entry count to 10 |
| ★ A7.2 | a pack with a `teach:` entry naming an id that is not a shipped teaching conversation is refused by the pack parser (A2.2's rule, exercised against a philosophy pack rather than a synthetic fixture) | a published pack that looks droppable but fails at runtime, or a `teach:` entry added before M79-2 ships | add a `teach:` entry to a fixture copy of 01 naming an unshipped id, and assert the parse refuses it |
| ★ A7.3 | the commit named in 01/02/04's entry 1 (`dad9b43…`) equals the commit §6.4 records as byte-identical to the generic run at the pin `24d6d74` | the packs' example drifting from the pin §6.4 documents, silently | change one pack's pinned commit without updating §6.4, or vice versa, and the cross-reference test must fail |

### 19.4 Owner questions this round raises (OPEN — not decided here)

Recorded with the spec's other owner decisions, as **OD-79.26–.28**, in §16 (*Raised by the philosophy-packs scope*).
None blocks writing M79-7's tests or its docs-site publish step; each blocks only the part it names (the move vs
copy question, which pack gets the first `teach:` entry, and whether the Python lint harness grows fixtures to
match).

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
- **r3, 2026-10-18** — **the lead's creative round 2**, folded with the review's fixes. Phases renumbered: the
  teacher path ("connect your own Claude" plus project context, the commercially proven one) is now **M79-2**, right
  after the first launch, followed by the evidence wording (M79-3), the quest (M79-4) and the docs (M79-5); the
  acceptance ids follow their phases (A2 = teacher, A3 = evidence, A4 = quest, A5 = docs). Added the teacher kit
  (§11.1): a ~60-word instructions paragraph pinned by a new `BridgeInstructionsTest`, whose third reading rule
  (`@AfterEvent` and lifecycle callbacks without propagation) is not in `claude.txt` and must be confirmed under rule
  6 first; the `teach-fluxtion` skill; `context.learning` and a *Learning* row, omitted when empty; teacher mode
  (OD-79.17); MCP prompts later. The chip has three states (*Evidence ✓ checked*, *Evidence · as read*, *⚠ changed /
  unknown*). Domains are five picture tiles. The five walks become one caption template filled from each log's own
  node ids, found from the graph's structure. Tooltips cut to accessible descriptions. M79-5 is *Start here*, its GIF
  and the label test; the restructure is DOCS-IA's. SC-T11 added; SC-C1 and SC-C2 scored by people only. The
  Settings-share follow-on records a proposed *Repository* category. Follow-ons N2, N3, *Make the diamond yours* and
  the analogy legend; two key questions for the owner.
- **r4, 2026-10-18** — **the owner's correction** (it overrides r3's re-phase). Phase 2 (**M79-2**) is now the
  **teaching session in the onboard assistant**: a pack entry or the Learn door plays a shipped, really captured
  teaching conversation in the docked panel, labelled every time, with spotlights on the diamond's records by digest;
  *Continue with Claude* starts or attaches the live onboard session (the M77 wizard if nothing is set up); the
  recorded turns stay above as a passive, read-only log that no request ever contains; the live session's basis is
  {Product}'s fixed sentence. §11.2 lists what it reuses (walks with a conversation, the Claude Code conversation
  capture, `DemoJourney`, *Ask about this evidence*, OA-D6, prompt packs, the capture harnesses, `FixtureAgent` for
  the witness) and §11.3 the three gaps (a pack that names a lesson, the hand-off, the passive log). The external
  teacher kit of r3 becomes **M79-6** (phase 3 or later), its tests A6.1–A6.5; teacher mode applies to both routes
  (A4.5). SC-T12 added. OD-79.19 (a pack's own conversation) open.
- **r5, 2026-10-18** — **owner answers.** OD-79.11–.16 accepted as recommended (doors mapped onto DOCS-IA; the
  instructions addition, gated on rule 6; doors do not set the work mode; *proof* only for traced records; the
  stricter first-run condition; no recovery offer for a diamond-only session). OD-79.18 (N2) accepted, with one live
  capture run on the owner's plan. OD-79.19 accepted (a pack names only a shipped lesson in v1; its own recorded
  conversations come later, with signing). OD-79.17 (teacher mode) accepted: only the person's own flag solves the quest, on both routes. The third reading rule is held out of
  the instructions, pending an empirical probe (the owner: *test it*). Key questions decided: interpreted
  Fluxtion also needs the key, so there are no keyless runs and the keyless authoring option is withdrawn; the
  analogy legend stays because it runs nothing; *Make the diamond yours* needs a key; a teaching-scoped hosted key is
  the preferred entry to paid usage, a commercial follow-on. §17 gives relative sizes (S/M/L) instead of days; the
  7 PRs and their order are unchanged.
- **r6, 2026-10-18** — **the second independent review**, folded in place, with the owner's answers to the six
  decisions it raised. The reviewer's report (findings M1–M9, N1–N5 and B1, on r5 at `6ca55014`) went to the lead
  and is not in the repo; this revision is the commits after `6ca55014` on `docs/m79-first-minute`. Every claim was
  re-checked against `feature/m78-d2-one-chip`, `feature/claude-code-p3`, `feature/m77-4-coexistence`, the
  `fluxtion-diamond` pin and the public Spring authoring pages before it was written. **Corrected:** §5.4's start-page
  baseline (4 groups and 10 chips; *Connect an assistant* is gone) and the AI line's six states (*your own client*
  was missing), with A1.6's witness now naming code that exists (M1); the launch carrier, `LogOpened.launch`, since
  no session fact carries `OpenRequest.Launch` today (M2); §3's `stage` row (a walk-level `WalkSpec` field, not the
  step allow-list, M4); OD-79.6's phase (M79-6, M5); OD-79.11's description of DOCS-IA (the four doors are
  `index.md`'s; `start-here.md` is a new page with four task cards and an administrators' line, N1); D-7's M76 clash
  (wording policy, not a test collision, N2). **Added:** A4.6, flags byte-identical across the M79-4 migration (M6);
  the `context()` early-return hazard and an A6.3 case for it (M7); the `@AfterEvent` probe's confirmed result, with
  `claude.txt` line 206 recorded as wrong and the upstream notes UP-FLX-56–58 cross-referenced (M8); A6.4 labelled a
  periodic, measured check (N3); A2.7. **Accepted by the owner:** OD-79.20–.25, applied: composition-first teaching led
  by the Spring XML contract, with the dialect, its two tiers of checking and its template generation stated from
  the public contract (§11.7); the generic run re-pinned to its Spring-authored twin, whose files and record digests
  are in §6.4; *lesson* renamed the **teaching conversation**; M79-1a split into 1a-i and 1a-ii (8 PRs); the passive
  log and basis sentence gated on a shipped, digest-verified conversation; a security review before the
  private-repository category. *Make the diamond yours* is recorded as skill.md's six-step design conversation.
  **Found wrong in the review when checked:** the membership rule's short form (*a bean in no list is not in the
  graph*; a referenced bean is in it); N1's *start-here.md is new* is right, but its outline does carry four cards;
  N4's *17 verbs* is already *18* in ONBOARDING on this branch (only `main` still says 17). N5 left as it was.
- **r7, 2026-10-19** — **brings the spec up to date for implementation, and adds M79-7** (owner, 2026-10-19). No
  OD-79.1–.25 decision is revisited. **Facts updated, each re-checked against `main` at 1.37.0:** D-3 and D-4 are
  **satisfied**, not conditional (M77-2 and M77-3 shipped complete in 1.36.0, PRs #159 and #162); D-7's two specs
  (M75, M76) are **on `main`**, DRAFT r2, not on branches, and M76's OD-76.7 publish condition is met by §6.4's pin;
  a new **D-8** records M80 (*Design shows the graph's definition*) as PROPOSED, not merged (spec PR #170, PR1 #175
  open), and states that the Design pane and its discovery stay M66/M80's, with M79 only a consumer of `open
  {design}`; SC-T10's baseline note corrected (M77-3 is built, just not yet scored); the start-page baseline (4
  groups, 10 chips; `StartPanel` 1,358 lines) and the AI line's six states are **re-confirmed unchanged** on `main`
  post-1.36.0/1.37.0; §11.2 gains two 1.37.0 facts M79-2 builds on (the transcript's Markdown tables; the docked
  panel's chrome-budget test, `AssistantChromeBudgetFrameTest`, which M79-2's passive-log band and basis sentence
  must fit within) and a note that M79-2's hand-off benefits from 1.37.0's restart-and-recovery fix (#171) without
  needing to special-case it. **New scope, §19: M79-7 — the first philosophy packs** (owner, 2026-10-19). Of the
  eight drafted *Fluxtion philosophy* packs (PR #155, merged 2026-10-19), three ship with M79 — 01 *What is
  Fluxtion?*, 02 *Local knowledge, global behaviour*, 04 *Audit logs and evidence* — published on the docs site as
  valid, build-tested prompt packs (★ A7.1–A7.3), with both *Coming* notes retired. 03 and 05–08 stay drafts (03 by
  the owner's chosen scope; 05, 06 and 08 need material outside this repo; 07 awaits `fluxtion#44`). **Found already
  done, not left for this PR to redo:** 01, 02 and 04's entry-1 teaching example is **already pinned** to the
  published diamond (`dad9b43…`, commit `d1e38e2b`, before PR #155 merged) — the task's premise that they still held
  a placeholder did not hold once the files were read; §6.4 already reconciles that commit against the first
  minute's pin, so no pin conflict existed to fix. M79-7 adds no `teach:` entry (none exists yet to point at, and
  none is shipped until M79-2); OD-79.27 leaves which pack gets one, and when, open. §17 gains M79-7 (9 PRs,
  depending only on M79-1a-i) and a *first slice to start* pointer (M79-1a-i). §16 gains **OD-79.26–.28** (where the
  published packs live; which pack's `teach:` follow-up; whether the Python lint harness grows matching fixtures),
  each **OPEN**, not decided here.
