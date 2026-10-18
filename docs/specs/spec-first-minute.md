# M79 — The first minute: the diamond, then three doors

**Status:** ACCEPTED r1, 2026-10-18. The owner accepted OD-79.1–.6 on 2026-10-18, then OD-79.7 and the three
decisions on the colleague follow-on (OD-79.8–.10, recorded as an extension of M15 Settings share, §13) the same day
(§16). Not yet independently reviewed: like M77, it should get one independent read before the M79-1 branch opens,
and each phase PR gets its own review (the reviewed-PR rule).

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
  same input arrives twice, the graph the compiler wired skips `d` the second time. The log proves *"d did not run"*:
  it is not an inference.

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

- G1. **The picture first.** With no recent project, the first launch opens the diamond, with the graph centre stage,
  and plays its four-step walk once. The person sees a node not run, and the record that proves it, before reading any
  paragraph (M79-1).
- G2. **Their own domain.** The same diamond, really run, with names from mathematics, accounting, logistics or trade
  processing, chosen from a dropdown. The problem is universal, and the person sees it in words they use (OD-79.1).
- G3. **Three doors, not fourteen chips.** The walk ends on *Click any record* and three doors: **Learn**,
  **Investigate**, **Build**. The start page shows fewer than 40 words (M79-1).
- G4. **AI is a quiet line, not a gate.** AI setup leaves the first launch. The wizard opens on the first Ask, Send or
  Start building, as M77 already provides (OD-79.5).
- G5. **Quiet evidence.** One chip, *Evidence: verified ✓* or *unverified ⚠*, replaces the Context column's
  verification prose. The prose stays one click away (M79-2).
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
| **M35.7 / M35.9** | No modal in the load path; nothing fires at an empty screen. | Kept. The diamond opens with an `OpenRequest` of kind `FIRST_MINUTE`. A failure lands on the start page as one line (§5.5), never a dialog. |
| **M69 §3.3** | The step view is a fixed allow-list. | **Extended by one field:** `stage` (§7.2). Nothing else is added; a walk still never opens a log. |
| **M69 §3.8** | Playback lives in `walkPlayback`. | Kept and extended: the stage, autoplay advance and the first-contact hold are that node's (§7). |
| **M74 S1** | *Run me through a demo* plays the DEMO journey. | Kept; it moves under *Or open directly*. The Guided tour stays the DEMO investigation tour (the Learn door). |
| **M15 Settings share** | Export and import of a whitelisted `.fluxtion-settings`. | Extended later by the colleague follow-on (§13), not by a second share format. |
| **CLAUDE.md rule 1** | Public repo: no real names; screenshots generated, not taken. | Applies to every domain variant, the teaching bundle and the GIF (§18). |

## 4. Prerequisites and dependencies

| Item | Where | Why M79 needs it |
|---|---|---|
| **D-1. The diamond variants** | `telaminai/fluxtion-diamond`, **published on `main` at `24d6d74` (2026-10-18)**, merged from its `domains` branch; the pin and digests are in §6.4 | Five real runs: generic, mathematics, accounting, logistics and trading. Each is one topology with domain-named classes and fields, compiled and run once. M79-1 bundles them **at that commit**, with their GraphML and source, and writes the jar's manifest from §6.4. No stand-in log is ever shipped in their place. |
| **D-2. The broken diamond** | the same repo: one commit with a deliberate defect, and its run | M79-3's teaching bundle (§9). |
| **D-3. M77-2's AI line** | M77 | M79-1 renders the line's short form (§5.4). If M77-2 has not merged, M79-1 shows today's assistant fallback collapsed under *Or open directly*, and adds no AI text of its own. |
| **D-4. M77-3's client path** | M77 | M79-5 starts the client path at Screen 2b. It does not merge before M77-3. |
| **D-5. The capture harness** | `tools/capture-docs.py` | The *Start here* GIF and every new screenshot are generated under an isolated `user.home` (rule 1). |
| **D-6. Rule 6 reading** | `docs/claude.txt` | Before M79-1 is implemented, the implementer reads the framework reference and the variants' generated dispatcher, and confirms §6.2's claim from them. The rule is that `@OnTrigger` fires when an upstream node signalled a change. This spec states the claim; it does not infer the generated code. |

## 5. M79-1 — the first launch

### 5.1 When the first minute plays

`firstMinute` (a new session node, §7.1) decides. It plays when all of these hold, as reported once at start-up by
`FirstMinuteStartupObserved`:

- no recent project (the recent-projects list is empty);
- `firstMinute.played` is unset (a machine-tier key);
- the process was not started with `--rest`, and is not the capture harness (which pins `firstMinute.played=true`
  except in its own first-minute run, §18);
- no file was passed on the command line (`java -jar … log` opens that log, as today);
- no session-recovery offer is pending (`SessionRecovery` wins; the first minute is deferred, not marked played).

Otherwise the start page shows, as today, with M79-1's shorter copy (§5.4). **First run only, never again**
(OD-79.2): `firstMinute.played=true` is written when the walk's first step is shown, so skipping counts as played. A
launch that defers leaves the key unset. Once any project is recent, the first minute never qualifies again. The
diamond stays one act away: *Learn ▸ The diamond*.

### 5.2 What the person sees

1. The diamond opens: the **generic** domain, from the jar (OD-79.3), through the ordinary open path. Its log and
   GraphML are paired by the ordinary pairing.
2. **The graph is centre stage.** The topology canvas fills the centre and the records table becomes a strip beneath
   it, showing at least six rows at 1200×800. This layout is the `walkPlayback` node's `stage` (§7.2, OD-79.7).
3. Above the graph: **Domain: Generic ▾**, with *Generic · Mathematics · Accounting · Logistics · Trade processing*.
   Choosing one opens that domain's own run (§6, §7.1). It does not autoplay again; ▶ replays the walk.
4. The four-step walk plays once, advancing by itself (§7.3). The strip reads *"Step n of 4 · Esc skips"*. ← and →
   take over the pace, and Esc skips to the end state.

**The walk** (generic copy; each domain's walk carries its own nouns, §6.3):

| Step | View | Targets and captions |
|---|---|---|
| 1 | stage `graph`, no record selected | `topology:node:a` *"An input arrives at a."* · `topology:node:d` *"a feeds b and c; both feed d. The compiler wired this from the code."* |
| 2 | record [2] (`X[value=2]`), the first input | `records:row:2` *"A new input."* · `topology:node:d` *"a, b, c and d all ran: each has an entry in this record."* |
| 3 | record [4] (`X[value=3]` again, after record [3]'s `X[value=3]`) | `records:row:4` *"same input — nothing changed"* · `topology:node:a` *"a saw the same value and reported no change"* · `topology:node:d` ***"d did not run"*** (the owner's words, joined: *"same input — nothing changed — d did not run"*) |
| 4 | record [4] | `detail:node:a` *"Only a has an entry here. This log records every node that runs, so no entry for d means d did not run. That is proof, not silence."* |

Record indexes are 0-based, as the analyser numbers them. Records [0] and [1] are the log's control and `Init` records.
Each pinned run has the same shape: six records, and record [4] is the repeated input, with an entry for the top node
only (§6.4).

Step 3 selects **the record that has no d entry** (record [4]). Because the topology canvas's step cursor is bound to the selected
record (M69 §3.5), the graph lights `a`, the only node that ran, and leaves `b`, `c` and `d` dark. The picture and the record say the
same thing because they are the same thing.

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
**Graph | Records** toggle (§7.2). Then the investigation layout returns: the records table is the main view (OD-79.7).

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
| **E2** | **No relabelling.** {Product} shows the names the log and graph carry. No code path maps one domain's names to another's. | A1.4: the node ids in each domain's GraphML equal the ids in its log, and the pairing reads *declared, matching*; a static rule fails on any name table keyed by domain (`OneDispatchModelTest` style) |
| **E3** | **One topology.** The five graphs are isomorphic to the generic graph: same nodes, same edges, same event entry. | A1.4: graph isomorphism over the five GraphMLs |
| **E4** | **Absence is proof only when traced.** In every variant, every node writes an audit entry each time it runs (the pinned README says so, and record [2] shows all four), as in the DEMO traced log. Each pinned run has a cycle where every node ran (record [2]) and the repeated input where only the top node ran (record [4]). Records [3] and [5] are the held-out cycles of SC-C1 (§12). | A1.5: per domain, coverage reports every node as able to log; record [2] has an entry for each of the four nodes; record [4] has an entry for the top node only |
| **E5** | **Walk captions are bound to the evidence.** Each domain's walk targets its records by record digest (M69 §3.5). A changed log makes the step *not available*. It is never re-pointed or captioned anyway. | A1.5's witness: replace the step-3 record with one that has a `d` entry; the step shows *not available* and the caption does not appear |
| **E6** | **The jar's copy is recognised by digest, not by name.** The domain dropdown and the *bundled* label appear only when the open log's read-time SHA-256 matches a manifest entry. A copied, renamed or edited file opens as an ordinary log. | A1.5: an edited copy opens without the dropdown |
| **E7** | **The source ships too.** Each variant's source and generated processor ship beside the log, as the DEMO source does, so clicking a node opens the code that ran. | `DiamondAssetsTest` lists them |

### 6.2 The claim, stated once

From the framework reference (`docs/claude.txt`, *Conditional dispatch*): **`@OnTrigger` fires when an upstream node
signalled a change.** In the pinned variants, `a`, `b` and `c` each return `true` only when their value changed. On the
repeated input, `a` reports `changed: false`, so the generated dispatcher calls neither `b` nor `c`, nor `d` below them.
The log has one entry, `a`'s, for that cycle. Record [3] shows the other half of the rule: in the generic run, `b`
reports no change but `c` does, and `d` still runs, because one changed parent is enough. Every caption here is written
from the pinned logs, which were read for this spec, and not assumed (D-6, rule 6).

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

- **What was read.** Each log has six records. Record [4] is the repeated input, with an entry for the top node only.
  The node names are §6.3's. The pinned repo passes the four-term sweep (rule 1).
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
- **Facts in:**
  - `FirstMinuteStartupObserved(recentProjects, played, restLaunch, launchFile, recoveryPending, rememberedDomain)`;
  - `DiamondDomainChosen(domain)`;
  - `FirstMinuteDoorChosen(door, link)`;
  - `FirstMinuteCardClosed`.
- **Decisions:**
  - whether to play (§5.1);
  - when to request `OpenDiamondEffect(domain, OpenRequest(FIRST_MINUTE))`;
  - when the diamond's generation is installed, and its read identity matches the manifest (E6): request
    `PlayBundledWalkEffect(walkName, origin=FIRST_MINUTE)`. The adapter reads the shipped definition and posts the
    ordinary `WalkPlayRequested`; M69's path does the rest;
  - when `walkPlayback` shows step 1 of that walk: request `MarkFirstMinutePlayedEffect`;
  - when that walk ends (any reason): publish the end state (callout and doors);
  - on a door: request the door's existing action effect (the Guided tour, the log chooser, the starter chooser, the
    quest, the client path);
  - on a domain: request the open and `RememberDiamondDomainEffect`.
- **Snapshot:** `FirstMinuteState(phase, domain, domains, doors, callout, reason)`, where `phase` is one of `NONE`,
  `OPENING`, `PLAYING`, `DOORS`, `DONE` or `FAILED`. The dropdown, the card, the callout and §5.5's line render this
  and nothing else.

### 7.2 The stage: `walkPlayback` owns it (OD-79.7)

- **M69 §3.3 gains one field**, walk-level: `stage = graph`. While a walk with `stage=graph` is showing, the node
  publishes `stage=GRAPH`. When it ends, the node restores the stage it recorded at play time, **except** for a walk
  of origin `FIRST_MINUTE`, whose stage is held for **first contact** (§5.3).
- **Facts:**
  - `StageToggleRequested(GRAPH|RECORDS)`, from the canvas header's **Graph | Records** toggle;
  - `FirstMinuteDoorChosen` and `FirstMinuteCardClosed` (also handled by `firstMinute`);
  - a new log generation, which also ends first contact.
- **Snapshot:** `WalkPlaybackState` gains `stage`. The frame lays out from it. `MainFrame` sets no layout of its own.
- **Revisitable** (the owner: *"we can always change"*). The default is decided in one place, so changing it later is
  a one-line change in the node, with A1.3's tests moved to match.

### 7.3 Autoplay advance

- For a walk of origin `FIRST_MINUTE`, when a step reaches `SHOWN` the node requests `ScheduleWalkAdvanceEffect(ticket,
  step, dwellMs)`. The dwell is 250 ms per caption word, clamped to 4–8 s, and is a constant in the node.
- The adapter's non-blocking Swing timer posts `WalkAdvanceDue(ticket, step)`. The node advances only if the ticket and
  step are still current, so a late timer changes nothing.
- ← or → (`WalkNavigated`) switches the walk to manual pace for the rest of the showing. Esc is the ordinary
  `WalkEndRequested`. A click outside ends the walk, as it does today, and lands in the end state.

### 7.4 Static rules (`OneDispatchModelTest` gains)

- No view calls the diamond's open, the walk's play, or a layout change directly. Only the effects' performers do.
- No `firstMinute.*` key is written outside `MarkFirstMinutePlayedEffect`'s and `RememberDiamondDomainEffect`'s
  performers.
- No start-page or callout string about the first minute is composed outside `FirstMinuteLines`.
- No timer advances a walk except through `WalkAdvanceDue`.

## 8. M79-2 — quiet evidence wording

### 8.1 The chip

The Context column's verification rows (file identity, the bundle's *"verified on open; unsigned…"*, the observation
basis) collapse into **one chip** at the top of the column:

| Verdict (from the session, never recomputed in the view) | Chip |
|---|---|
| The records shown come from bytes with a recorded SHA-256 (the read identity), and the session's file-identity verdict is `unchanged-metadata`. For a bundle, it also verified on open. For a shipped asset, its digest also matches the manifest. | **Evidence: verified ✓** |
| `changed-on-disk`, `unknown` (an unassessed log: *a verdict not formed is not a clean bill of health*, M69 §3.5), a bundle that failed to verify, or a missing read identity | **Evidence: unverified ⚠** |

- **Details on click:** a popover lists each existing row with its prose **verbatim**, including
  *"size, modification time and file identity; unchanged metadata does not prove identical bytes"* and the unsigned
  caveat. The auditor's words are kept, one click down, and the summary never contradicts them.
- **The chip's words** come from `EvidenceChipLines`, which reads the snapshot only. The glyph is never the only signal
 : ✓ and ⚠ each carry a word.
- **Machine readers keep everything.** `context` and the MCP replies keep their full fields, and
  `sample-conversations.md` does not change. This is a display change.
- **Open for the first review (OQ-1):** whether *verified* overclaims for a log whose only on-disk check is metadata.
  The chip's ✓ row is defined above so that the review has one sentence to test. The M68 evidence-integrity spec owns
  the answer, and the chip follows it.

### 8.2 Glossary tooltips

One resource, `help/glossary.properties`, feeds the tooltips and the docs site's glossary. A test pins that they agree
(A2.4):

| Term | Tooltip |
|---|---|
| Mongoose | *The Fluxtion server that hosts event processors and can write their audit logs.* |
| GraphML | *A file describing a processor's graph: its nodes and how they connect.* |
| `.fexp` | *An evidence bundle: one file holding an investigation (logs, graph and notes, with their checksums), verified before it opens.* |
| Fluxtion key | *The licence file needed only to regenerate a processor. Reading logs never needs it.* |

Tooltips attach where the term first appears on a surface: the start page's openers, the Context column and Settings.
They also attach as each control's accessible description.

## 9. M79-3 — the broken-diamond quest (OD-79.4)

### 9.1 The teaching bundle

- **A real run of a deliberately buggy commit** in `fluxtion-diamond` (D-2), packaged as a `.fexp` by the ordinary
  capture, with the commit SHA in its provenance. An indicative bug: `c` reports a change when its input did not, so
  `d` runs on the repeated input. The answer is the first record where `d` has an entry it should not. The bug itself
  is the repo's; the spec requires only that **exactly one record is the answer**, and that the record is findable by
  reading the log and the graph, without AI.
- **Labelled a teaching bundle, by digest.** The bundle's notes say *"Teaching bundle: a planted bug in a demo
  processor"*. That sentence is the exporter's claim, so {Product} does not rely on it. The label *"Teaching bundle
  (ships with {Product})"* appears in the Context column and the bundle's open line only because the bundle's SHA-256
  matches the quest manifest in the jar. A copy that has been edited is an ordinary bundle.
- It ships in the jar beside the diamond set, under the same naming rules (§6.3, rule 1).

### 9.2 The quest

- **The door:** *Solve a real bug (5 min)* on the start page (Learn) and on the first-minute card. One act opens the
  bundle (verify, then a disposable copy, as M70 does) and shows the quest card: the goal (*"d ran when it should not
  have. Flag the record that proves it."*), and three hints revealed one at a time. The hints are words from the quest
  manifest.
- **Completion is {Product}'s, by record digest.** The quest manifest stores
  `answer = SHA-256("m79-quest-v1:" + recordDigest)`. It does not store the index, so the manifest does not give the
  answer away to a reader. When a record is flagged, the flag's fact carries the record's digest (M69's
  `WalkIdentity.recordDigest`; the fact gains the field if it lacks it). `quest` compares the two and publishes
  *Solved*, naming the record and who flagged it (*you*, or *your AI client* for a flag that arrived over the socket).
  **A model's words never complete it.** An assistant that says "solved" changes nothing; only the flag on the right
  record does.
- **Works with No AI.** No step needs an assistant, a key or a network. A1-style tests run with the profile `OFF`.
- **Rule 9:** a `quest` node, with parents `openBundle` and `openLog`. Its facts are `QuestStarted(questId)`,
  `QuestHintRequested`, and the flag fact. Its effects are `ShowQuestEffect` and `RecordQuestSolvedEffect` (machine
  tier: `quest.<id>.solved`). The card renders `QuestState`.

## 10. M79-4 — the docs front door

- **One *Start here* page** (`docs/site/start-here.md`, first in the nav). It opens with a GIF: a click on a record,
  then the graph lighting, with `d` dark on the repeated input. Under the GIF are three cards, **Learn / Investigate /
  Build**, with the app's labels. The GIF is generated by the capture harness under an isolated home, from the
  diamond's generic run only, and every frame is read before commit (rule 1, §18).
- **The five getting-started pages become one.** *Quick start*, *Guided start*, *Playground to analyser* and *Spring
  authoring step by step* fold into *Start here* and the user guide. *Install & run* stays as reference. The old pages
  stay out of the nav for one release as one-line pointers, for external links, then go. This lands with, or after,
  the owner-approved DOCS-IA restructure, and M79-4 supplies its front door.
- **The home page says what it is:** a **canvas** where you **learn** how an event-driven system behaves, **build**
  one, and **understand** what it did, from its own evidence. The words *canvas*, *learn*, *build* and *understand*
  each appear above the fold (A4.2).
- **Packs defined and listed:** a *Packs* page says what a **prompt pack** is. It is learning entries: words placed in a
  draft, or copied for your own client. Nothing runs them. The page lists the packs that ship: the sample pack and
  M79-5's *Your project, for your own Claude*. **Packs stay what they are**; sharing a set-up is Settings share's job
  (§13).
- **Labels match:** `DocsLabelParityTest` reads every bold control label on the docs pages that describe the start
  page, the first-minute card and the quest. Each must equal a label in the app's label registry (`StartPanel`'s and
  `FirstMinuteLines`' constants). The fix for F2 is the app's labels everywhere: *Guided tour*, *Sample project*, *Open
  audit log*.

## 11. M79-5 — "Connect your own Claude" (OD-79.6)

- **One action:** **Connect your own Claude** (the Build door, the start page). It opens the M77 wizard at Screen 2b
  with *Claude Code* chosen. That is M77-3's path, unchanged: detection, the external-access confirmation, *Register
  for me…* with the exact command, and the live check. It is the person's act, so OD-79.5 holds: nothing about AI
  appears unasked.
- **Pointed at the project.** When the live check is ✓, the Summary's suggested first action (M77 §6.4) becomes **Copy
  the first prompt**, from the shipped pack *Your project, for your own Claude*. Its entries are words for the person to
  paste into their own client. For example: *"Call analyser_context first. Read this project's runbooks and vocabulary
  from the pointers it returns, then tell me what this system does and what its audit log covers."* The pack also loads
  into the start list for the session.
- **The project context is already there** (M38): `context` returns the runbook, vocabulary and environment pointers.
  If the bridge's `instructions` (`/manifest`) do not already say to call `context` first, M79-5 adds that sentence and
  pins it in `FaqSecurityContractTest`'s neighbour, `BridgeInstructionsTest`.
- **No new verb, transport or consent.** M79-5 is composition only.

## 12. The scorecard

Scored by the robot walk (a virgin LLM driving the real `Main` under an isolated `user.home` at 1200×800) and, where
marked, by repeating the stranger review. Each phase's run is recorded under `docs/specs/evidence/m79-<phase>/`. The
two reviews of 2026-10-18 are the baseline. *Hesitation* and *dead end* keep those reviews' meanings: a pause to work
out what to do, and an act that leads nowhere.

| # | Task | Baseline (2026-10-18) | Target | Phase |
|---|---|---|---|---|
| **SC-T9** *(new)* | **Understand the skip:** "why did d not run on the second input?", answered by pointing at the record | not reachable without reading docs | **≤ 1 act, 0 hesitations, 0 dead ends** from first launch | M79-1 |
| **SC-S1** | Start page always-visible words | about 225 | **< 40** (≤ 39 in every AI-line state, §5.4) | M79-1 |
| **SC-T1** | First answer from an assistant | the M77 walkthrough's count | **unchanged or better.** The wizard still opens at the first Ask; the first minute adds no act to that path | M79-1, M79-5 |
| **SC-C1** *(insider)* | **Prediction correctness:** after the walk, for the two held-out cycles of the generic run, record [3] (`b` unchanged, `c` changed) and record [5] (a fresh change), predict which nodes run, then check by clicking | — | **2 of 2 correct in ≥ 4 of 5 runs** | M79-1 |
| **SC-C2** *(insider)* | **The right flag:** the broken-diamond quest with No AI | — | {Product} confirms the flag in **≥ 4 of 5 runs**, **median ≤ 5 min** | M79-3 |
| SC-E1 | Auditor-prose hesitations on the first view | the stranger's F5 | **0**; F5's sentence not visible without a click | M79-2 |
| SC-D1 | Getting-started pages in the nav; docs/app label mismatches | 5; F2's pairs | **1; 0** | M79-4 |
| SC-T10 *(new)* | **Connect your own Claude:** from the start page to the person's client making its first `context` call, with `claude` installed | the M77-3 count | **≤ 5 acts, 0 dead ends** | M79-5 |

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

Its own spec amends `completed/spec-settings-share.md` when it is scheduled, and the whitelist test grows by the new
categories.

## 14. Other follow-ons (recorded, not in M79)

- **The criterion chip** (M76 slice 1), and **the before/after run comparison**: the diamond is their natural first
  fixture.
- **Keyless authoring through pre-generated variants:** the domain variants show that a set of real, pre-generated
  runs can stand in for a compile the person cannot run without a key.
- **More domains:** each is a new real run under §6. None may be added by relabelling.

## 15. Acceptance tests (rule 8: ★ marks a test with a wrong-result witness)

Headless unless marked *frame* (a registered frame suite under Xvfb in CI). Each phase's tests ship in its PR.

**M79-1**

| # | Test | Catches | Witness (the mutation that must turn it red) |
|---|---|---|---|
| ★ A1.1 | `firstMinute` over every combination of §5.1's inputs: it plays only when all hold; it marks played on step 1 shown and not before; a deferred launch leaves the key unset | autoplay on a returning user, under `--rest`, over a recovery offer, or twice | mark played on the startup fact; or ignore `recoveryPending` |
| ★ A1.2 | autoplay advance: a `WalkAdvanceDue` for an old ticket or step changes nothing; ← or → stops auto-advance; Esc lands in `DOORS` with the callout and the three doors | a late timer skipping a step; a skipped walk with no way on | drop the ticket comparison |
| ★ A1.3 | stage: `GRAPH` while the first-minute walk shows and through first contact; `RECORDS` after a door, a new generation or the toggle; an ordinary walk restores its prior stage; the frame lays out from the snapshot alone | the graph stuck centre stage for investigation (OD-79.7), or a layout set by the view | keep `GRAPH` after a door; or set the layout in `MainFrame` |
| ★ A1.4 | E2 + E3: per domain, GraphML node ids equal the log's, and the pairing is declared and matching; the five graphs are isomorphic; no name table keyed by domain exists | a relabelled log, a drifted topology | ship the generic log under the accounting GraphML |
| ★ A1.5 | E4–E6: per domain, record [2] has four node entries and record [4] only the top node's; coverage reports every node able to log; an edited copy opens without the dropdown or the bundled label; a substituted step-3 record makes the step *not available* | "d did not run" shown over evidence that does not say it | a step-3 record with a `d` entry still captioned |
| ★ A1.6 | the start page's word budget (§5.4's counting rule): ≤ 39 in every AI-line state, at 1200×800 *(frame)* | the page growing back | restore the *Connect an assistant* group's caption above the fold |
| ★ A1.7 | §5.5: a manifest mismatch or a failed open → no walk, not marked played, one start-page line with the open's reason, no dialog | a broken asset hidden, or a modal at an empty screen | autoplay without the manifest check |
| ★ A1.8 | `OneDispatchModelTest`'s §7.4 rules | hand-placed dispatch | call `openDiamond` from the dropdown's listener |
| ★ A1.9 *(frame, real `Main`, isolated `user.home`, 1200×800)* | **The first-minute walkthrough:** first launch → the diamond opens with the graph centre stage → four steps → step 3 selects the record with no `d` entry and `d` is dark → the end state → one click on a record lights its cycle. Then relaunch → the start page, no autoplay | SC-T9's path regressing | write `played` on Esc only, so a played-through walk replays |
| A1.10 | `DiamondAssetsTest`: every file's SHA-256 matches the manifest; the source ships (E7); the sweep covers the set (§18) | a hand-edited asset, a leaked name | — |

**M79-2**

| # | Test | Catches | Witness |
|---|---|---|---|
| ★ A2.1 | the chip for every verdict row of §8.1, from the snapshot alone; `unknown` is ⚠ | an unassessed log shown as verified | map `unknown` to ✓ |
| ★ A2.2 | the details popover holds every existing row's prose verbatim | the caveat lost behind the summary | drop the basis sentence |
| A2.3 | `context` and the MCP replies are byte-identical before and after M79-2 for the demo fixture | a display change leaking into the protocol | — |
| ★ A2.4 | glossary parity: the four tooltips equal the docs glossary entries; each term's first appearance on the start page, the Context column and Settings carries the tooltip and the accessible description | a definition drifting | edit one side only |

**M79-3**

| # | Test | Catches | Witness |
|---|---|---|---|
| ★ A3.1 | completion: a flag on the answer record → *Solved*; a flag on its neighbour, or an assistant message saying "solved", → not solved; a flag over the socket is attributed to the AI client | a model completing the quest, a near miss accepted | compare record indexes instead of digests |
| ★ A3.2 | the teaching label appears only when the bundle's SHA-256 matches the quest manifest; an edited copy is an ordinary bundle | a label taken from the bundle's own notes | read the label from the notes |
| ★ A3.3 | the whole quest with the profile `OFF` and no network | a hidden AI or network dependency | require an assistant for hint 3 |
| A3.4 | the manifest does not name the answer's index; the answer is salted | a spoiler in the jar | — |

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
| A5.3 | the bridge `instructions` say to call `context` first (`BridgeInstructionsTest`) | the teacher not pointed at the project | — |

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

**Open for the first review** (not owner decisions; none blocks M79-1): **OQ-1**, the chip's *verified* wording for a
metadata-only on-disk check (§8.1); **OQ-2**, the autoplay dwell (4–8 s by caption length), to be tuned on the first
robot run.

## 17. Phasing and effort — one PR each, under about 5,000 hand-written lines

Generated `SessionProcessor` lines are not counted. Each PR is a reviewed feature branch with its CHANGELOG lines
(rule 2), its tests green (rule 4), and its scorecard run recorded (§12).

| PR | Content | After | Est. days | Est. lines |
|---|---|---|---|---|
| **M79-1 — first launch** | `firstMinute`; `stage`, autoplay advance and first contact in `walkPlayback`; the diamond set and its manifest in the jar; five shipped walks; the domain dropdown; the end card; the start page under 40 words; A1.1–A1.10 | D-1, D-6 | 6–8 | ~3,000 |
| **M79-2 — quiet evidence wording** | the chip, `EvidenceChipLines`, the details popover, the glossary resource and tooltips; A2.1–A2.4 | — | 2–3 | ~900 |
| **M79-3 — the broken-diamond quest** | the teaching bundle and quest manifest; the `quest` node; the quest card; the flag's digest field; A3.1–A3.4 | D-2, M79-1 | 4–5 | ~1,800 |
| **M79-4 — the docs front door** | *Start here* and its GIF; the home page; the folded getting-started pages; *Packs*; the glossary page; `DocsLabelParityTest`; A4.1–A4.3 | M79-1 (labels, GIF); DOCS-IA | 3–4 | docs, plus ~300 of test |
| **M79-5 — connect your own Claude** | the action; the pack; the Summary's first action; the bridge sentence; A5.1–A5.3 | M77-3 | 2–3 | ~600 |
| **Total** | | | **17–23 days (about 3½–4½ weeks)** | |

## 18. Public-repo rules for every M79 asset (rule 1)

- The domain variants, the teaching bundle, the walks, the GIF and the screenshots use generic names only (§6.3). No
  real company name appears anywhere, including in prose about the rule.
- `DemoAssetsTest`'s sweep, which parses CLAUDE.md's sweep line at runtime, extends to the diamond and quest assets,
  with their GraphML, source and manifests.
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
