# M74 onboarding — the virgin-LLM protocol

Status: **proposal, 2026-10-17; not run.** It designs the 10–20 virgin-LLM cycles planned for M74's onboarding (the
start-page prompt, prompt packs, `propose` onboarding, example entries, and from P3 approvals in the analyser). Every
claim is tagged as in the beta proposal: *measured* means observed in a run or read from code; *proposed* is a design
choice. Its review of what came before is [REVIEW-2026-10-17-m74](../g14/REVIEW-2026-10-17-m74.md); the scorecard is
[SCORECARD.md](SCORECARD.md); the pack is [virgin-pack.md](virgin-pack.md), with one variant per track in
[packs/](packs/). The offline half of the runner is `tools/m74_onboarding_runner.py`, tested in CI.

The discipline is G14's and the release checks' ([g14/PROTOCOL.md](../g14/PROTOCOL.md),
[coldstart/METHOD.md](../coldstart/METHOD.md)), kept on purpose so a reviewer who knows those knows this: sealed
predictions, a pinned artefact, an isolated instance per run, observer timing, a scan before anything is kept, and a
VOID that is preserved rather than re-labelled. **What is new is forced by M74 itself**: the analyser now starts
Claude Code, so the harness no longer composes the subject's command line, and the person — who alone may press Send,
Yes and Allow — has to be played by something.

---

## 1. What is measured

**The claim:** *a person who has never used the analyser, with the analyser's own Claude Code and a pack of plain
questions, reaches a correct answer about their evidence, and every change to their project is one they approved.*

| Track | Question | Phase it needs |
|---|---|---|
| **T1** cold newcomer | Drop the pack, ask about a DEMO log, reach the first correct answer | P2 (log open at launch); P3 for the cold arm |
| **T2** project from code | Claude proposes a set-up; every item is reviewed; the analyser's own check passes | P2 |
| **T3** example entry | A pinned example is downloaded, opened through M73's offer, and asked about | P2 |
| **T4** authoring | A template, a first component edit, compile, diagnostics, run, then *"why did it fire once?"* answered from records | **P3** |
| **T5** diagnosis regression | The external-MCP diagnosis loop paying users rely on does not regress | the released jar today; P2 for the coexistence arm |

**What it does not measure.** Whether the answer is *good* beyond its oracle; human usability (the beta proposal's
second-person test does that); website discovery; the in-app (API-key) assistant. A virgin model is a proxy for a
newcomer's *assistant*, not for the newcomer.

---

## 2. Isolation and pins

### 2.1 One base per run *(proposed)*

`<base>` is a fresh directory under `/private/tmp` (`fx-m74-<cycle>-<run>`), never reused, so nothing a run leaves —
history, memory, trust records, a lock, a server — can reach the next.

| Isolated | How | Why, specifically for M74 |
|---|---|---|
| the analyser's `user.home` | `-Duser.home=<base>/home` | folder-trust records, `assistant.claudeCode.lastMode`, session locks and the hosted endpoint files are all machine-tier and live here |
| `HOME` | `HOME=<base>/home` | the CLI's own `~/.claude.json` (per-project trust, onboarding state) |
| `CLAUDE_CONFIG_DIR` | `<base>/home/.claude-virgin`, **empty at Start** (`assert_virgin_config`) | the CLI's settings, skills, plugins, agents, history, and **auto-memory under `projects/<cwd>/memory`**: a later run in the same path would otherwise read an earlier run's memory |
| the login | **L1 (the default):** the trial OAuth token in `CLAUDE_CODE_OAUTH_TOKEN`, read from its file by the runner and never printed (as `g14_runner.child_env` does) | the analyser keeps `CLAUDE_CONFIG_DIR` and `CLAUDE_CODE_OAUTH_TOKEN` for its child (*measured*: `ChildEnvironment` on the P2 branch). An isolated config with the token is the only way to have a virgin configuration **and** a login together |
| MCP servers | **`--strict-mcp-config` on every line**: the default posture's line carries it; for the full posture the runner puts a wrapper first on `PATH` that adds it (OD-74.18, `write_wrapper`) | account-level claude.ai connectors arrive with the login whatever the config directory holds |
| the source tree, the real home, other instances | the G14 sandbox profile, applied to the **analyser JVM** (its children inherit it) | the subject is the analyser's child now; wrapping `claude` alone is no longer possible |
| Maven (T4 only) | a per-run repository seeded from a provenance-checked frozen seed (§2.3) | T1–T3 build nothing: no repository, no Repsy dependency |

**L2, the real login with `--strict-mcp-config`** (the spec's capture method) is permitted only for the **default
posture** (T1–T3), where `--restricted` loads only the folder's `CLAUDE.md` and none of the person's settings, skills
or commands (*measured*: spec §3 and P1a's smoke). It is **not** permitted for T4: the full posture loads the
person's user `CLAUDE.md`, skills, plugins and agents, which on the owner's machine are full of this project. A full
posture run under the real login is not a virgin run, however it is labelled. L2 also writes to the real
`~/.claude/projects/`, which must be deleted after the run, as the P2b smoke did.

### 2.2 Pins, recorded in `config.json` before launch *(proposed)*

The analyser jar's sha256 and version (a **released** jar for anything reported as acceptance; a branch jar is
labelled *branch evidence*); the Claude Code binary's sha256 and `--version` (a read-only per-run copy, as
`--claude-bin` does for G14, with `DISABLE_AUTOUPDATER=1`); the **model id in full**, never an alias such as `haiku`
that can move; the posture and mode; the pack's sha256; the oracle's sha256; the operator script's sha256; the wrapper's
sha256; the DEMO fixture's sha256; for T3 the pinned commit and, after the run, the **unpacked tree's digest**; for T4
the template's download digest and the Maven seed's tree digest; the JDK and the OS.

**On the example's digest.** GitHub states that a generated `/archive/<commit>.zip` keeps its file contents but may
change its compressed bytes (spec, r11 note). So the identity of a T3 example is the **commit plus the unpacked tree
digest**, not the zip's sha256. A pack entry's optional `sha256:` is used only in the digest-control variant (§5.3).

### 2.3 The warm cache *(owner decision 2026-10-12; implemented here as tested functions)*

Only T4 builds. Its runs start warm unless they are the occasional first-run check, exactly as
[g14/PROTOCOL ▸ Cache state](../g14/PROTOCOL.md) says: seed `<base>/tmp/m2` from a frozen seed filled from an empty
start by a run of the same template; check provenance first — every jar and pom listed in `_remote.repositories`
under `central` or `repsy-fluxtion-public`, no SNAPSHOT, no locally installed artefact, nothing without a source
(`m2_provenance`, which refuses an empty seed too); record the seed's name and tree digest in `config.json`
(`seed_m2`, which also refuses to merge into a used repository and checks the copy's digest); check sufficiency
once per seed. The owner's `~/.m2` fails the check by construction and is never a seed. These functions are the
G14-W runner flag's check, in a tested home; the G14 runner can import them.

---

## 3. Predictions before results

### 3.1 The cycle folder *(proposed)*

```
docs/handoff/evidence/m74-onboarding-<date>-c<NN>/
  CYCLE.md              what changed since the last cycle (the confounds), and which track is held out
  predictions.md        written from measurement, numbered, failures predicted too
  virgin-pack.md        the pack this cycle drops (a copy, so its digest is the cycle's)
  operator.json         the operator script and policies for every track in the cycle
  oracle.sha256         the oracle's digest — committed; the oracle itself is not (§3.3)
  seal.json             written once by `m74_onboarding_runner.py seal`
  runs/<run>/           config.json, operator.jsonl, snapshots.jsonl, session-record.meta.json, meta.json, score.json
  RESULTS.md            generated from runs/*/score.json
```

`seal` digests `predictions.md`, `virgin-pack.md`, `oracle.json` and `operator.json`, refuses to run twice, and
`init-run` refuses a cycle whose seal does not verify. **The seal commit is pushed before the first launch** (the release
ladder did: `a445666d`), because a `date -u` line in a file the operator wrote is not evidence of when it was
written; the push is. Pushing is the owner's.

### 3.2 What a prediction must state

```
1. Per track and model: PASS k of n, and the first failure expected (its dead-end type).
2. T1: median Sends to the first correct answer; whether the first turn points before it explains.
3. T2: items proposed; which kinds; how many the analyser refuses or marks outside; the after-check pairing.
4. T3: whether the download confirmation names the publisher as predicted; time from the entry to the project open.
5. T4: approvals asked per run, by class; how many the policy denies; whether a denial is a dead end.
6. T5: candidate versus control pass counts; the deterministic contract checks unchanged.
7. Refusals expected by reason, and one the subject will hit that a person would not.
8. Notional cost per track and model, and the cycle's total.
9. Which prediction you are least sure of, and why.
```

The 2026-10-12 ladder's predictions underestimated Haiku on every rung (*measured*: 5 of its 9 sealed predictions
were wrong, 4 of them in that one direction, and the rung-5 set repeated it). **Each cycle's `CYCLE.md` therefore states the previous cycle's prediction misses and what was changed
because of them**; predictions that never move after misses are a ritual, not an instrument.

### 3.3 The oracle stays private until the cycle closes *(proposed)*

This repository is public, and a T4 or T5 subject can have web tools. A committed oracle — the record a correct
answer points at, the phrases it must not say — is an answer key a subject can fetch. So the cycle commits only
`oracle.sha256`; `oracle.json` lives beside the run folders, git-ignored, and is committed when the cycle closes,
when the seal proves it was not edited. The scorer counts fetches of this repository's protocol and evidence pages in
the CLI transcript (`oracleLeakHits`), and any hit voids the run. T1–T3's default posture has no web tool at all.

### 3.4 Post-seal changes

Allowed only as in the release ladder: a scorer or oracle correction is disclosed with its UTC time and the runs
it touches, applied to every run alike, and the sealed verdict is kept beside the corrected one (`ORACLE_r1`). A
correction made after a result it changes is reported as such, never folded in.

---

## 4. The pack

### 4.1 The virgin pack

[virgin-pack.md](virgin-pack.md) is one file in the strict pack grammar (spec §5.8, OD-74.28–74.30): a title, one
description line, then `N. **Label** — text`, 1–9 entries, labels ≤ 60 characters, text ≤ 500, no HTML, no links, no
`{placeholders}`, and example entries `N. **Label** — example: <https URL of a pinned commit archive> [sha256:<64 hex>]`.
Its entries, and the track each serves: 1 and 2 T1; 3 T2; 4 and 5 T3; 6 T4; 7 a free question kept for the held-out
rotation (§8).

**Entry 4 is a placeholder** — `https://github.com/example/demo/archive/0000000000000000000000000000000000000000.zip`
— and says so in the description line, the only place the grammar allows a note. A literal
`…/archive/<40-hex>.zip` cannot be used: the grammar refuses it (*measured*, §4.3). Before a T3 run the cycle's
`virgin-pack.md` copy pins a real commit of the chosen example; with the placeholder, T3 is a **zero-cost negative
control**: the fetch must fail by name and nothing may be unpacked.

### 4.2 Leakage rules for a virgin prompt *(proposed; tested)*

A prompt says what the person wants, never how the tool does it: no verb names, no record numbers, no file names,
no node names, no answers, no menu paths. The test `test_virgin_prompts_name_no_verb_and_no_record` holds every
committed pack to it. The analyser's **own** drafts (S5's *"Start with analyser_context"*, Q1's verb names) are
product and are what is under test when the pack is not in force; a pack entry is the person's words.

Prompts are deliberately the words a newcomer would use, including a mistaken premise in entry 2 (*"Does that mean
they never ran?"*): the correct answer is that silence in an audit log does not prove a node did not run (ONBOARDING,
*the three that bite*, 1), and the oracle forbids the opposite claim.

### 4.3 Checked against the analyser's own parser *(measured)*

`lint-pack` is a Python mirror of `assistant/PromptPack` and `ExampleSource` on the P2 branch, so a pack is checked
without a built analyser. The mirror is pinned to the Java parser's verdicts on 37 inputs, recorded on 2026-10-17 by
running the P2 build's classes (`tools/fixtures/m74-onboarding/pack-crosscheck.json`); a test fails on any divergence.
**Recording them found one**: `…/archive/<40-hex>.zip` is `EXAMPLE_NOT_ARCHIVE` in Java (`URI.create` refuses `<`)
and was `EXAMPLE_MOVING_REF` in the first mirror. Fixed, with a control. All seven committed packs parse in both.
Re-record when `PromptPack` changes.

**Re-recorded 2026-10-17 for the forgiving reader** (`69eb2fac`, `feature/claude-code-p3` at `031545cc`): 89 inputs,
adding one for each normalisation (the four other dashes and `**Label:**`, a plain or missing label, `-`/`*`/`+` bullets
and `1)`, blank lines, trailing whitespace, no description) and each refusal that stays (HTML, images, hidden
characters, the caps, placeholders, links, an unclosed `**`, every loose shape of an example entry). Each verdict now
carries the title, description, labels, texts and the reader's `normalised` fixes, and the mirror must match all of
them. The verdicts are recorded, never typed: `python3 tools/m74_record_pack_crosscheck.py <checkout>` runs
`tools/m74_record_pack_crosscheck/RecordPackVerdicts.java` against a checkout's compiled classes.

---

## 5. The tracks

Each track lists its setup, the operator script, the decisions it may take, the success criteria (as scorecard
checks), and its stop conditions. **Caps are proposed and sealed per cycle.** *Interventions* are canned replies and
nudges (§6); a run with any is reported as *assisted*, beside the unassisted count.

### 5.1 T1 — cold newcomer

**Two arms.** *T1-open* (P2): the analyser is launched with the DEMO log as its argument, which is the person's act of
opening a file, then *Help ▸ Start page*. *T1-cold* (P3): nothing is open; Claude must ask to open something, and the
operator decides. In P2 the cold arm is predicted to dead-end by design: `open` is hidden from the hosted bridge until
P3, so Claude can only tell the person what to open.

**Script.** Drop `packs/t1-cold-newcomer.md` → choose entry 1 → press the start page's button → press Start (an armed
Start in the scratch folder, OD-74.22) → confirm the scratch folder if asked → press Send → wait for the turn to end →
snapshot. Entry 2 is sent as the second question only after entry 1's oracle holds or entry 1's budget is spent.

**Decisions.** Folder confirmation: Yes only for the sealed folder. P3 `open` (cold arm): Allow once only when the
input names the DEMO set's files or the analyser's sample project; deny anything else.

**Success.** `oracle` (a pointer at the first breach record, by `event` and `logTime`) and `noContradiction`.
Entry 2's oracle is a forbid claim (the silent node *did not run*) plus a required qualification.

**Stop.** Success; 6 Sends; 600 s; two consecutive turns with no hosted call and no new pointer; the same refusal
three times; any posture verdict other than `OK` (FAIL, product finding).

### 5.2 T2 — set up a project from code

**Setup.** A copy of `examples/fixture-generator/` (a real starter-shaped project) **with its `CLAUDE.md` replaced by
the published template's**, so the folder's instructions are what a customer's would be and nothing of this
repository's process reaches the subject. A DEMO-shaped log and its GraphML produced by that project.

**Script.** Drop `packs/t2-project-from-code.md` → **← Return to main options** → choose S2 *Set up a project from my
code* → choose the fixture folder in the chooser → confirm → press Start (armed with the SETUP draft) → press Send →
when the banner appears, open *Review…* → decide every item → *Apply chosen* → snapshot → send entry 1 of the pack as a
follow-up only if no proposal was made within the budget → after the apply, send entry 2 (*Is it set up right?*).

The pack cannot start S2 itself (an entry only fills the box: *measured*, `PromptPack` and §5.8), so the route to
onboarding is the person's choice of S2; whether a newcomer *finds* S2 is what T2's guided arm measures: there the
operator does not return to the main options and presses *Set up a project with Claude…* only if Claude names it
(§6.2).

**Decisions (the review dialog).** The analyser's own create row: Yes when the folder has no project. Each item: Yes
when its state is `pending` and its analyser verdict is one of `DIRECTORY`, `RESOLVES`, `SOURCE_FILE`, `READABLE`,
`MARKDOWN`; No otherwise. The policy reads only the analyser's verdict words, never Claude's reasons (which the dialog
labels *unverified*). Every item gets a recorded `review` act, Yes or No.

**Success.** `oracle` (`proposal`: `graphml`, `processor` and `sourceRoot` applied, and the analyser's after-check
pairing `PAIRED`) and `everyItemReviewed`. A wrong but readable proposal is accepted by the policy and caught by the
analyser's pairing, which is the point: the person approves; the analyser checks.

**Predicted, from the P2b smoke** *(measured there, once)*: creating the project closes the open log (M35.5), so the
after-check reads the pairing as `UNKNOWN` unless Claude also proposed a `logToOpen` item. That is a known product
question for the owner, and T2's oracle names it rather than hiding it.

**Stop.** Success; 8 Sends; 900 s; a second proposal refused while one is open, twice; `NOTHING_APPLICABLE_PROPOSED`.

### 5.3 T3 — example entry

**Setup.** The cycle pins one example commit (a Telamin repository or a third party's), measured on a throwaway run:
the publisher line, the third-party mark, the unpacked tree digest, the processor class and its node count.

**Script.** Drop `packs/t3-example-entry.md` (the cycle's copy, with the real commit) → choose entry 1 → press its
Download button → on the confirmation, decide (below) and choose the sealed target folder → wait for the unpack → M73's
project offer: decide → choose entry 2 → press the button → Start → Send.

**Decisions.** Download: Yes only when the confirmation's URL equals the sealed URL character for character, the
publisher line equals the sealed one, and the third-party mark is as sealed. Project offer: Yes only for the unpacked
root. Nothing else.

**Success.** `oracle` = `example` (done, the sealed commit, the project open at the unpacked root) **and** the first
question's pointer or claims, and `noContradiction`. The unpacked tree digest equals the sealed one.

**Controls, zero model cost.** The placeholder pack: the fetch fails by name, nothing is unpacked, no project opens.
`packs/t3-digest-control.md` with a real commit: a wrong `sha256` must be refused by name **before** any unpack
(OD-74.30); acceptance of a mismatch is a FAIL of the product, not of the subject.

**Stop.** Success; 8 Sends; 900 s; a fetch failure (VOID-infra when the host is down, FAIL when the analyser accepts
what it should refuse).

### 5.4 T4 — authoring *(needs P3)*

**Setup.** The full posture (`default` mode, the person's own tools, each asked in the analyser), virgin config (L1
only), the wrapper adding `--strict-mcp-config`, the analyser JVM under the G14 profile **with the compilation key
file readable** and every G14 control that comes with it (the key-leak scan, process-group reaping and the base
sweep). The template is the standalone Spring starter, downloaded through *New project from template…* by the person,
then opened through M73's offer; the warm Maven seed for that template.

**Script.** Drop `packs/t4-authoring.md` → choose entry 1 → button → Start → Send → answer each permission dialog by
policy → after the run is built and run and its log opened, choose entry 2 (*Why only once?*) → Send.

**Decisions (the permission dialog, P3).** Allow once only when the dialog's tool and input match the sealed table;
deny everything else. The table, proposed:

| Request | Allow once when |
|---|---|
| `Bash` | the command is one of the template's own scripts (`./setup.sh`, `./generate.sh`, `./validate.sh`, `./run.sh`) or `./mvnw …`, or a read-only `ls`/`cat`/`grep`/`find`/`head`/`tail`/`sed -n` whose paths are inside the project — and it does not match the G14 key-path pattern |
| `Edit`, `Write` | the file is inside the project root |
| `WebFetch`, `WebSearch` | the host is the playground or the analyser's docs site; **never** this repository on GitHub (§3.3) |
| hosted `open` | the input names the project's own log, GraphML or the project itself |
| hosted `source_root` | the path is inside the project |
| hosted `report`, `screenshot`, `walk`, `import` | never in T4 |
| anything else | never |

**Success.** `oracle` = `node-logged-once` on `@new` — the node the subject added, as the observer finds it from the
GraphML difference — logged exactly once, and a pointer at its record; `noContradiction` (forbid: the node *ran only
once* when the record shows only that it *logged* once — *the three that bite*, 1); `keyScan` clean. The authoring
ladder's ORACLE and VALID checks (an offline rebuild and a behavioural oracle) are run on the final project as
supporting evidence.

**Stop.** Success; 25 Sends; 2400 s (the ladder's cap); a key-leak hit (FAIL, rotate); the same denial three times;
`APPROVAL_UNAVAILABLE` (a P2 build: VOID by design, this track needs P3).

### 5.5 T5 — the external-MCP diagnosis loop must not regress

**Why it is a track.** Paying users diagnose with their own Claude Code and the `fluxtion-analyser` MCP bridge, today.
M74 adds a hosted bridge, a hosted-only listener, a verb (`propose`) reserved to it, and refusals keyed to the hosted
credential. Every one of those is a way to break the external loop without any M74 test failing.

**The deterministic gate (decides; no model).** On the candidate jar, against the released control jar:
- the MCP bridge's `tools/list` is the control's public verb set (18 on the P2 branch), and `propose` is absent (`VerbSchemasTest` pins it; the
  check here is the **built jar's** bridge, through `tools/bench/bundle-client-bench.py` or `loop-bench.py --launch`);
- an external `propose` over REST is `unknown verb 'propose'` (the P2b smoke's item 6, made repeatable);
- `/manifest` and its `instructions` are byte-identical to the control's, except for changes the CHANGELOG names;
- with REST on and no hosted session, `context` from an external client has the control's keys;
- *(P2 merged)* **coexistence**: an external client's calls succeed while a hosted session runs, `graph {delete}` stays
  available to the external client, and the well-known endpoint file is the REST server's.

**The model arms (indicative).** The release-check instrument of [coldstart/METHOD.md](../coldstart/METHOD.md),
unchanged: a held-out model over MCP only (`--tools ''`), one isolated instance per run, the DEMO log open, the
candidate and the control jar in the same cell, three runs each. The task is T1's first question, sent as the input
file (not a pack, which an external client never sees), so **T1-open and T5 share one oracle**: the same question,
answered through the hosted route and the external route, is the cleanest comparison the cycle has.

**Who presses.** Nobody: `claude -p`, one shot, as today. No operator, so no interventions.

**Success.** The deterministic gate identical to the control; the model arms not worse than the control by more than
one run in three on `oracle`.

---

## 6. The operator: who presses Send, Yes and Allow

The analyser's rules give these acts to the person (OD-74.1, OD-74.13, OD-74.17; D-AI5 *discovery offers, and never
selects*). In a virgin run the person is a **scripted operator**, never the subject and never another model, and the
script is sealed with the predictions.

### 6.1 What the operator may do

| Kind | When | Recorded as |
|---|---|---|
| **Scripted act** | the track's sealed sequence: drop a pack, choose an entry, press a button, Start, Send the entry's text | `source: script` |
| **Decision** | a dialog the analyser raised: folder, proposal items, download, project offer, permission. Decided by the sealed policy from what **the analyser** shows — its verdict words, the tool's class, the input — never from Claude's reasons | `source: policy`, with the shown verdict |
| **Guided act** | Claude's latest answer names a control by its exact label from the track's sealed table (`Set up a project with Claude…`, `Return to main options`, `New project from template…`); the operator presses it once. At most three per run | `source: guided`, with the label |
| **Canned reply** | Claude's latest answer ends by asking the person something; the operator sends the next reply from the sealed list: *"Use what the analyser already has open; I have no preference."*, then *"Please go ahead with what you suggest."* At most two per run | `source: reply` — an **intervention** |
| **Nudge** | a turn completed, the oracle does not hold, no question was asked: *"Please continue."* At most once | `source: nudge` — an **intervention** |

**Never:** compose text; read record content to decide; follow an instruction that does not name a control
verbatim; edit a file, run a command or open anything on Claude's behalf; press Allow because Claude asked for it; use
the action socket or a verb (the operator's acts are the person's controls only; the observer may call `context` and
`read`, which change nothing).

### 6.2 Why not let a model play the person *(proposed decision)*

A second model choosing what to type, and especially when to say Yes, is attractive and wrong here. It is
non-deterministic, so two runs differ in the person as well as the subject; it can be persuaded by the subject (which
is the prompt-injection risk the default posture exists to contain, now inside the experiment); and it would make
*who approved this?* unanswerable from the record. **Yes and Allow are always the sealed policy.** A *persona arm* —
a second model, with no tools and no access to the analyser, choosing only the **words** of follow-up questions from a
sealed persona prompt — is permitted as a separate, labelled arm for T1 once the scripted arm is stable, never in the
same cell, and its words are kept with the run.

### 6.3 Keeping the boundary honest

- **The operator presses the analyser's own controls in its own JVM**, as the P1a, P1b and P2b smokes did
  (`M74P2bSmoke`: *"the harness pressed the application's own controls … and read only the published snapshot and the
  bounded store"*). No act goes through the socket, so the person's path is the one a person takes.
- **Every person-act fact must be explained by an operator act, and every act by a fact** (`boundary` on the
  scorecard). The person-act facts are the ones no verb can post; a fact the operator did not press means something
  else pressed, and the run is VOID. R3 (an `origin` on each such fact) would let the record prove this by itself.
- **The subject cannot reach the operator.** The operator reads Claude's words only to match a sealed label or a
  trailing question mark; it never parses an instruction, a path or a command out of an answer.
- **The decision policies use only analyser-shown data.** For T2 that is the review dialog's verdict words; for T4 the
  permission dialog's tool, class and the input as the analyser shows it. A policy that read Claude's `reason` would let
  the subject approve its own proposal.

### 6.4 The observer

After every turn end and flow end, the observer (in the same JVM) records a snapshot: `context`, the resolved
pointers, the node-log counts of the open log, and `recordSeq`. At the end it exports the session record with its
completeness counts, the transcript store's answers, and the runner copies the CLI's session transcript from the
isolated config directory. The observer never changes state.

---

## 7. A run, step by step

1. `verify-seal` the cycle; `init-run <cycle> <run> <track> --pins pins.json` (refuses an unsealed cycle, an unpinned
   run or a used folder).
2. Stage the base: the jar, the per-run CLI copy, the wrapper, an empty `CLAUDE_CONFIG_DIR`, for T4 the seeded
   repository, the fixture or template. Record digests.
3. **Measure** (once per cycle, on a throwaway instance of the same jar): the oracle's records, the example's tree, the
   proposal verdicts the fixture produces. Write them into `oracle.json` before sealing.
4. Launch the analyser JVM under the profile with `analyser_env` and the operator harness; the harness runs the
   track's script; the runner enforces the cap and reaps the tree (the G14 reaper and base sweep).
5. Collect: the session record and its counts, `operator.jsonl`, `snapshots.jsonl`, `answers.jsonl`,
   `cli-session.jsonl`; run the key scan (T4) and the redaction (§7.1).
6. `score` the run; nothing else writes `score.json`.
7. Delete the base; keep only the run folder's committed files, and the uncommitted ones under `.local-evidence/`.

### 7.1 Redaction before anything is kept

The session record can hold paths and Claude's words (SCORECARD R6), and the transcripts hold both by nature. Before a
file leaves the base: home paths to `~`, the base to `<base>`, then the four-term sweep and a grep for the macOS home-directory prefix, the
user name, e-mail shapes and token shapes, which must come back empty. Only the committed files in §3.1 are committed;
transcripts never are.

---

## 8. Cadence, sample size and cost

**Per cycle** *(proposed)*: T1-open ×3, T2 ×3, T3 ×1 (and its two zero-cost controls), T5 ×3 per arm on a release
candidate only, T4 ×3 Haiku and ×1 Sonnet only once P3 is merged and when authoring changed. One model is held fixed
across the 10–20 cycles (the cheapest that passes T1), and a second only when a cycle asks a model question.

**Held out.** One of entries 1, 2 and 7 is held out per cycle and rotated, as the loop-handoff rule requires: a
held-out pass is the only pass worth anything. A finding counts when it **recurs** across two cycles or two tracks.

**Cost** *(proposed, from measured neighbours)*: T1–T3 runs are a few turns; the whole P2b smoke (onboarding, the
switch offer and the transcript items) took four model turns. The authoring ladder measured Haiku 5.5 at a median of US$0.19 notional and Sonnet 5 at US$3.43 per
authoring run (*measured*, 2026-10-12). A cycle without T4 should stay under a few dollars notional; with T4, under
fifteen. The cycle's cap is sealed in `predictions.md`.

**n = 3 is a hypothesis per cell**, as METHOD.md says of n = 1: inconclusive means run more, not proceed.

---

## 9. What must exist before the first run

| # | Needed | For | Status |
|---|---|---|---|
| E1 | M74 P2 merged and released | T1-open, T2, T3 | on `feature/claude-code-p2` |
| E2 | M74 P3 merged | T4, T1-cold | spec only |
| E3 | The in-JVM operator harness (scripted acts, decisions, guided acts, replies, observer, export) — the P2b smoke harness generalised and driven by `operator.json` | T1–T4 | **not built**: it must be built against the merged code, not P2's branch |
| E4 | `launch` in the runner: base staging, the profile around the JVM, the cap, reaping, collection | T1–T4 | not built; `launch` refuses and says why |
| E5 | **Probe PV-1** *(zero model cost)*: the analyser JVM under the G14 profile draws its window, binds the hosted bridge and reaches `READY` with an empty config | T1–T4 | not run |
| E6 | **Probe PV-2** *(one cheap turn)*: with L1 (isolated config + trial token) the analyser's child is logged in, `billing` reads `SUBSCRIPTION`, and the CLI writes its session transcript under the isolated config | T1–T4 | not run. The G14 runs show the token works with an isolated `HOME` (*measured*); the spec's *"an isolated config loses the login"* is about the keychain login, not the token |
| E7 | R1, R2, R5 and R8 from SCORECARD §7, or a recorded decision to score without them | the fields they name | open |
| E8 | The owner's answer on R6 (words and paths in facts) | publishing any session record | open |

**Built now** (`tools/m74_onboarding_runner.py`, 38 tests in CI): the pack grammar pinned to the Java parser, the
provenance check and seeding, the seal and its verification, run initialisation with pins, the isolated environment,
the `--strict-mcp-config` wrapper, the virgin-config check, the session-record parser, and the whole scorecard with a
wrong-result control for every verdict-deciding check. Five mutations of those checks were each caught by their named
test and restored green (2026-10-17).

---

## 10. Limits, stated

- **A scripted person is an idealised person.** It never misreads a dialog and never gives up. The scorecard measures
  the product and the subject under a fixed, sealed person; it says nothing about a real newcomer's patience.
- **The policies decide what "approved" means.** A permissive T4 table measures less than a strict one. Seal it, and
  report denials as data.
- **One template, one DEMO log, one example per cycle.** A pass generalises to nothing beyond them.
- **The default posture cannot open a log until P3**, so T1-cold in P2 measures that limit, not the subject.
- **Narrow provenance continues**: every run is operated by the author's team. The real-user diagnostic cycles remain
  the outside check, and T5 exists to keep them working.
