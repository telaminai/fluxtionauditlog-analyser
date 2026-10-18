# Usability scorecard

The owner approved this on 2026-10-18: usability improvement is to be **measured, not asserted**. The scorecard is a
battery of eight scripted tasks and three key states, run in a real analyser window. It records numbers that a re-run
reproduces exactly, and CI keeps those numbers from getting worse.

- **Baseline:** [`baseline-integration.md`](baseline-integration.md) (the table and every step) and
  [`baseline-integration.json`](baseline-integration.json) (one result per task and per state), measured on the
  2026-10-18 integration branch `test/usability-2026-10-18` (section *The integration build* below). The earlier
  baselines stay as the before points: [`baseline-a875fcba`](baseline-a875fcba.md), measured on
  `feature/usability-scorecard` (product code byte-identical to `feature/claude-code-p3` @ `a875fcba`), and
  [`baseline-b1c22704`](baseline-b1c22704.md).
- **Harness:** `src/test/java/…/ui/Usability.java` (the instrument), `UsabilityTasks.java` (the scripts),
  `UsabilityBattery.java` (the runner) and `tools/usability/run.py` (the command).
- **Budgets in CI:** `UsabilityBudgetFrameTest` with the budgets in `UsabilityBudgets.java`. The test is registered in
  both frame lists in `ci.yml`.

## Re-run it

```bash
python3 tools/usability/run.py            # results in target/usability/usability-<commit>.{json,md}
```

- `--build` rebuilds the jar first.
- `--record` also copies the result here, as `baseline-<label>.{json,md}`.
- `USABILITY_SHOTS=<dir>` paints every screen the battery meets to a PNG, so you can read a measure. Never commit
  those PNGs: they are not swept (CLAUDE.md rule 1).

The command needs a display. On Linux, use `xvfb-run -a python3 tools/usability/run.py`. It takes about a minute.
Three consecutive runs gave byte-identical JSON on 2026-10-18. The JSON also carries the correctness target T4c
and the authoring milestones.

## Method

**The window.** Each task and each state gets a fresh `MainFrame`:

- 1200×800, the default window, with the assistant docked;
- the app's default (Light) theme;
- its own isolated `user.home`, created inside the runner's own isolated home under `/tmp/analyser-usability`, a
  neutral path.

The runner starts the **built shaded jar**. Its classpath is the jar first, then `target/test-classes` (the harness
and the fixture agent) and the test-scope dependencies. The product classes come from the jar, never from
`target/classes`. The frame test runs the same scripts on the Maven classpath, and the numbers were identical.

**No live model.** Each home's `PATH` holds a `claude` stub. Since the integration build it answers Set up AI's two
read-only checks (`claude --version`, `claude auth status --json`) with fixed DEMO values, as the wizard's own frame test
does, and the analyser's environment for those checks is a GUI-shaped `PATH` that cannot reach a real `claude`. Claude
Code is the test suite's
`FixtureAgent`, which replays recorded streams:

- the M74 P0 multi-turn recording, for answers;
- the P3 recording's *allow* turn, for the report write.

No prompt is ever sent, and nothing is registered with any client.

**The launcher.** For task 6, each home's `~/.jbang/bin/analyser` is a working launcher for this same build. It is
a stand-in for the installed launcher, so the bridge check really starts a bridge and reaches the window. It adds no
product hook.

**Acts.** Every counted act is a Swing event on a control that is found **by what it shows**, as the frame tests do:

- a click is a press, release and click dispatched to the control's centre;
- a right-click is a popup-trigger press;
- a key goes through the component's own key bindings (posted key events need a focus owner, which this Mac does not
  give a test);
- typed text is set on the box;
- a drop is delivered to the page's `TransferHandler`, which is what a native drop calls.

**Preconditions are not counted.** These are a log open, an assistant chosen, a filter set and a request waiting.
They are set the quickest way, and sometimes that is the action socket's own executor. Everything after
`Run.begin()` is counted.

### The tasks

| # | Task | Starts from | Key words | Success (the end state, checked) |
|---|---|---|---|---|
| T1 | A fresh start to the first answer | the start page of a fresh home | ask, send, question, answer | an answer from Claude Code, finished, with its transcript on screen |
| T2 | Right-click *Ask* about a record | the demo log open; no assistant chosen yet | ask, send, question, answer | as T1, **and** the record the Ask is about is the breach record, by digest (the right-record check below) |
| T3 | Approve a report write | Safe analysis running; Claude Code's approval dialog asking to write the DEMO report | approve, allow | the CLI was told *allow*, and the dialog closed |
| T4 | Find out why records are missing | the demo log open; `RiskBreachEvent` unticked and the event-type checklist hidden | filter, hidden, missing | the unticked type is on screen |
| T5 | Switch Safe → Full → Safe | the demo log open; Safe analysis applied | safe, full | Safe analysis applied and Full off in the config, after Full was applied through its full consent |
| T6 | Set up an external client | the start page of a fresh home | client, connect, mcp | the generic MCP set-up says *"Bridge works now"* |
| T7 | Open the sample | the start page of a fresh home | sample | the sample project's log open and its records on screen |
| T8 | Load a prompt pack | the start page of a fresh home | prompt pack | the pack's title is shown and its entries replace the list |

The authoring task A2 and the correctness target T4c are described in their own sections below.

The key words are the task's own words plus the one synonym the product's domain uses (*allow* for approve). They
were written down with the tasks and are frozen with the baseline. Changing a set changes the baseline, so it is
done in the same commit as a new baseline.

### The key states

| # | State | Region measured |
|---|---|---|
| S1 | The start page, fresh home | the start page |
| S2 | The assistant Ready: Safe analysis applied, Claude Code started, docked | the assistant panel (M78's "Ready, Safe analysis") |
| S3 | Records with the event-type list shown, demo log open | the section titled *Records* (its search row, the checklist and the table) |

Each state also records the whole window's words and clipped strings, plus the list of every text it counted with
that text's words (`counted` in the JSON). That list lets anyone audit a number.

## Metric definitions

All of these are mechanical, so a re-run gives the same numbers.

- **Chrome words.** Count the words of every visible piece of chrome:
  - labels, buttons, tab titles, section (titled-border) titles, list rows, combo values and table column headers;
  - read-only text, counting only the part inside its visible rectangle;
  - painted text whose source is known: profile cards, start-page chips, the rotated rail labels, and placeholders
    as far as they are drawn.

  "Visible" means the component shows at least a 4×6 px patch, the M74 inventory rule. Excluded: tooltips, the
  transcript, table cells, and text the person typed. This is the M78 review's rule.

  A **word** is a run of letters or digits, joined by an apostrophe, a hyphen or a dot. So "Shift+Enter" is two
  words, "(4)" is one, and "ⓘ" is none.
- **Words on screen before the first act.** The chrome words of every showing window at `Run.begin()`.
- **Act.** One click, one key, one typed string (whatever its length), one drag (a drop) or one scroll. A scroll is
  counted when the next control's centre is outside its visible rectangle inside a scroll pane; the harness then
  scrolls to it.
- **Hesitation.** It is judged before each act on the control that act needs. The act counts as one hesitation if
  any of these holds:
  - **not visible**: the control is not showing, or a press at its centre would land on something else;
  - **below the fold**: its centre is scrolled out of view, in a scroll pane with room to show it (a viewport
    squeezed to no rows makes it *not visible* instead, and no scroll act is counted);
  - **behind menu**: it is reached through a context menu, which has no visible cue, or it is an overflow glyph
    with no letters (`…`, `ⓘ`);
  - **label lacks key word**: its visible label holds none of the task's key words. The visible label is the
    painted text, never the accessible name.

  A *look* step is reading something to go on, such as the unticked type in T4. It is judged on visibility only, and
  it costs a scroll act when it is below the fold.
- **Dead end.** The next control is not visible, and **nothing visible** names any of the task's key words. The
  person has no visible next step. A control that cannot be found at all fails the task.
- **Clipped string.** `TextFit`'s verdict, from the `fix/first-run-polish` work: a label or button narrower than
  its text, a wrapping text area shorter than its text, text past a right edge, or text cut at a side outside a
  scroll pane. It is applied to every text with **some** part on screen. A text with no visible patch at all (in a
  collapsed column, or under a closed card) is hidden, not clipped. A task's clipped strings are the union over every
  screen it meets.

## Baseline at `a875fcba`

| Task | Success | Acts (click/key/drag/scroll) | Hesitations | Dead ends | Words before first act | Clipped |
|---|---|---|---|---|---|---|
| T1 Fresh start to the first answer | yes | 5 (3/2/0/0) | 2 | 0 | 225 | 1 |
| T2 Right-click Ask about a record | yes | 5 (4/0/0/1) | 2 | 0 | 273 | 4 |
| T3 Approve a report write | yes | 1 (1/0/0/0) | 0 | 0 | 325 | 3 |
| T4 Why records are missing | yes | 2 (1/0/0/1) | 2 | 0 | 259 | 3 |
| T5 Safe → Full → Safe | yes | 8 (7/0/0/1) | 2 | 0 | 254 | 2 |
| T6 Set up an external client | yes | 4 (4/0/0/0) | 1 | 0 | 225 | 0 |
| T7 Open the sample | yes | 1 (1/0/0/0) | 0 | 0 | 225 | 1 |
| T8 Load a prompt pack | yes | 1 (0/0/1/0) | 0 | 0 | 225 | 0 |

| State | Chrome words | Controls | Clipped | Whole-window words |
|---|---|---|---|---|
| S1 Start page | 225 | 15 | 0 | 225 |
| S2 Assistant Ready (Safe, docked) | 31 | 6 | 0 | 229 |
| S3 Records with the list shown | 25 | 8 | 0 | 273 |

### What the numbers point at

These are findings for the owner. None is fixed here.

- **T2 and T5 each cost a scroll.**
  - T2: at 1200×800 the records table shows three rows, so the breach record is below the fold.
  - T5: the *Full Claude Code* card sits below the docked panel's fold.
- **T4 is the costliest per act.**
  - The cause of the missing records is behind a toggle named *Event types*, whose label says nothing about a filter.
  - With the list shown, the unticked type is the fourth of four, and the checklist shows three rows, so it is still
    below the fold.
  - Nothing outside the list says that records are hidden by type. *"showing 9 of 10"* says that some are, but not
    why.
- **T1 and T2 each hesitate twice**, on *Open the sample* (T1) and on *Apply* (both). A newcomer wants an answer,
  and is asked to choose how the assistant works. That is the cost M77's wizard addresses.
- **Clipped strings outside the key states:**
  - *"Yours, on this machine. Never written to a profile, never shared."* is cut at the side in the Context column,
    on every workspace screen;
  - the topology status line is drawn at 200 px for about 2,050 px of text;
  - the *Scaffolding* check box is cut at the side;
  - the Full badge *"◆ Full Claude Code (default) — on: …"* is drawn at 200 px for 544 px of text; the M78 review noted it too.
- **S2's composer placeholder** is drawn cut: *"Your question — Enter sends, Sh"*. `TextFit` cannot see painted text,
  so it does not count as clipped (see the limits).

### Against the robot walks

The robot walks are `robot-journey` and `robot-rewalk`, scratch reports from 2026-10-18 that are not in the repo.

- The re-walk counted **5 acts** from the sample project's click to the first answer: *Sample project*, right-click,
  *Ask*, *Apply*, *Send*.
- T2 starts with the log already open, so it does not count *Sample project*. It adds the scroll to the breach row,
  which the robot did silently with `scrollRectToVisible`. That gives the same 5.
- The robot's first walk needed 16 acts, including four *Send*s that did nothing. The difference is
  `fix/assistant-ux-followups` and `fix/first-run-polish`, which are both in this baseline.

## The integration build (2026-10-18): `b1c22704` → `a875fcba` → integration

The integration branch `test/usability-2026-10-18` gathers M77-1–5 (Set up AI, the quick switch, your own client),
M78 D1/D2 (the quiet panel, one mode chip), the records-filter fixes, the Event types list collapsed by default and the
authoring-walk fixes on top of `a875fcba`. Its record is [`baseline-integration.md`](baseline-integration.md) /
[`.json`](baseline-integration.json), from `python3 tools/usability/run.py --record --label integration` on the built
jar; two runs gave identical JSON.

| Task | Success | Acts | Hesitations | Dead ends | Words before first act | Clipped |
|---|---|---|---|---|---|---|
| T1 Fresh start to the first answer | yes → yes → yes | 4 → 5 → 6 | 1 → 2 → 3 | 0 → 0 → 0 | 225 → 225 → 222 | 0 → 1 → 1 |
| T2 Right-click Ask about a record | yes → yes → yes | 4 → 5 → 5 | 2 → 2 → 3 | 0 → 0 → 0 | 265 → 273 → 262 | 6 → 4 → 4 |
| T3 Approve a report write | yes → yes → yes | 1 → 1 → 1 | 0 → 0 → 0 | 0 → 0 → 0 | 317 → 325 → 314 | 3 → 3 → 3 |
| T4 Find out why records are missing | yes → yes → yes | 3 → 2 → 1 | 4 → 2 → 1 | 0 → 0 → 0 | 259 → 259 → 272 | 3 → 3 → 3 |
| T5 Switch Safe → Full → Safe | yes → yes → yes | 9 → 8 → 5 | 2 → 2 → 0 | 0 → 0 → 0 | 246 → 254 → 199 | 5 → 2 → 1 |
| T6 Set up an external client | yes → yes → yes | 4 → 4 → 7 | 1 → 1 → 4 | 0 → 0 → 0 | 225 → 225 → 222 | 0 → 0 → 0 |
| T7 Open the sample | yes → yes → yes | 1 → 1 → 1 | 0 → 0 → 0 | 0 → 0 → 0 | 225 → 225 → 222 | 1 → 1 → 1 |
| T8 Load a prompt pack | yes → yes → yes | 1 → 1 → 1 | 0 → 0 → 0 | 0 → 0 → 0 | 225 → 225 → 222 | 0 → 0 → 0 |
| A2 Teach-me diamond pack | — → yes → yes | — → 11 → 10 | — → 4 → 3 | — → 0 → 0 | — → 225 → 222 | — → 6 → 6 |

| State | Chrome words | Controls | Clipped | Whole-window words |
|---|---|---|---|---|
| S1 Start page (fresh home) | 225 → 225 → 222 | 15 → 15 → 16 | 0 → 0 → 0 | 225 → 225 → 222 |
| S2 Assistant Ready (Safe analysis, docked) | 32 → 31 → 6 | 6 → 6 → 4 | 2 → 0 → 0 | 224 → 229 → 195 |
| S3 Records with the event-type list shown | 17 → 25 → 26 | 5 → 8 → 9 | 0 → 0 → 0 | 265 → 273 → 274 |

T4c, the correctness target: — → **FAIL** → **pass**.

**Script changes for the integration UI.** The profile cards are gone (M77-4), so the scripts take the route this
build offers; the success criteria and the key words are unchanged.

- **T1, T2:** the first use opens *Set up AI*: **Continue** (Claude inside preselected), then **Finish** once the two
  read-only checks pass, where the cards had **Apply**.
- **T5:** the mode chip (*Safe ▾*, *◆ Full ▾*) opens the quick switch; a row applies at once, Full after its
  confirmation.
- **T6:** the first-run block's *Your own AI client connects*, **Continue**, the switch *Let programs on this computer
  reach this window*, its confirmation, then **More options…** for *Connect an AI client*'s **Check connection**; the
  success criterion is still *"Bridge works now"*.
- **T4's precondition** shows the checklist before unticking, since the list now starts hidden, and checks the type
  really is unticked.
- **The `claude` stub** answers the wizard's two checks (see *No live model*); the painted words of Set up AI's
  *Where data goes* picture are counted as chrome.

**What changed, and why** (each budget change is in `UsabilityBudgets.java` with its reason):

- **Better:** T4 2 → 1 act and 2 → 1 hesitation, and **T4c passes** (the records-filter fix: the floor, the hidden
  line, `context.hidden`). T5 8 → 5 acts, 2 → 0 hesitations (the quick switch). S2 31 → **6** words, 6 → 4 controls
  (the quiet panel and one chip: M78 TQ1's ≤ 15 / ≤ 4 met). A2 11 → 10 (*Load this project?* is no longer asked for the
  project already open). T2 keeps 5 acts with the wizard's extra act, because the collapsed list gives the breach
  record its row back (no scroll). M77 T19's ≤ 6 for the newcomer path holds: T2's 5 plus *Sample project*.
- **Worse, with a reason:**
  - **T1 5 → 6 acts, 2 → 3 hesitations:** Continue and Finish replace Apply (M77-2, as the M77-2 target row
    predicted). Neither label holds a key word.
  - **T2 2 → 3 hesitations:** the same Continue and Finish.
  - **S3 25 → 26 words:** *Records: all 10 shown* (+3) and the fourth row the floor now shows (+2), less the repeated
    *Event types* heading (−4).
- **A finding for the owner — T6 4 → 7 acts, 1 → 4 hesitations.** M77-4 made Set up AI the one door for your own
  client, so the bridge check is now *Continue* → the switch → its confirmation → *More options…* (below Screen 2b's
  fold: a scroll) → *Check connection*. Each step has a reason (the one door; the confirmation at every door, M77-3),
  but together they make the external client the costliest task in the battery, and *More options…* is the one control
  that names none of the task's words and sits below a fold. The budget is raised to the measure so CI holds it; it is
  not a target. Screen 2b's own live check (*Can your client see the analyser?*) is the route the spec intends, and it
  needs a real client to call, which the battery cannot script.

## The wizard fix: integration → `regfix` (fix/wizard-scorecard-regressions)

The two regressions the integration recorded, answered. The record is [`baseline-regfix.md`](baseline-regfix.md) /
[`.json`](baseline-regfix.json); the before point is [`baseline-integration.md`](baseline-integration.md).

| Task | Success | Acts | Hesitations | Dead ends | Words before first act | Clipped |
|---|---|---|---|---|---|---|
| T1 Fresh start to the first answer | yes → yes | 6 → 6 | 3 → **2** | 0 → 0 | 222 → 224 | 1 → 1 |
| T2 Right-click Ask about a record | yes → yes | 5 → 5 | 3 → **2** | 0 → 0 | 262 → 262 | 4 → 4 |
| T6 Set up an external client | yes → yes | 7 → **4** | 4 → **1** | 0 → 0 | 222 → 224 | 0 → 0 |
| S1 Start page (fresh home), chrome words | — | — | — | — | 222 → 224 | 0 → 0 |

Every other task and state is unchanged (T3–T5, T7, T8, A2's acts and hesitations; S2, S3), apart from the two words
before the first act that S1 adds to every task starting on the start page. M77 T19 (the robot walkthrough,
`AiSetupWizardFrameTest`) stays at **6 acts**, re-measured.

- **T6 7 → 4 acts, 4 → 1 hesitations.** Screen 2b carries **Check connection** itself, under the access switch and
  above the fold at 640×560 and 520×480; it was behind *More options…*, below the fold. With access off, its press raises
  the **same** OD-77.4 confirmation (*Turn on External MCP/REST access?*, *Cancel* the default, its words unchanged), and
  that confirmation's **Turn on access** also runs the check, so the switch and the check are one act. The first-run
  block's and Screen 1's **Continue** reads **Set up my client**. The route: *Your own AI client connects* → *Set up my
  client* → *Check connection* → *Turn on access*. The one hesitation left is *Turn on access*: the owner's
  confirmation, kept as it is. The success criterion is now Screen 2b's own line, *"✓ Bridge works now: the command your
  client will run reached this window."* — the same probe (`McpConnectionProbe`) the dialog ran.
- **T1 and T2 3 → 2 hesitations.** While a question waits, the last wizard press reads **Finish, then send my
  question** (OD-77.2 already closes the window without the Summary then; the question is placed, never sent, so the
  label says the person sends it). Screen 1's press reads **Check Claude Code**: honest, and still none of T1's words,
  so it stays a hesitation. No act could honestly go: Screen 1's press is the consent that starts the two checks (G7),
  and 2a's is the Safe / Full choice and its Apply.
- **S1 222 → 224 words, a budget raised.** The first-run block's button says what it does for the preselected choice
  (*Check Claude Code*, three words where *Continue* was one). M79 OD-79.5 removes the block, and the two words with it.

## Before → after: `b1c22704` → `a875fcba`

`a875fcba` already contains the first-run polish and the Event types fixes, so this section records a true before
point. The before point is `b1c22704` (P3 plus the docs restructure), the build the owner tested before those
fixes. Its record is [`baseline-b1c22704.md`](baseline-b1c22704.md) / [`.json`](baseline-b1c22704.json).

**How it was run.** `b1c22704` was checked out in a scratch worktree and built from source with
`mvn -o package -DskipTests`. The scorecard's six test files (the harness and `TextFit`) and `tools/usability/run.py`
were copied in unchanged, and the battery ran against that jar with the same command. The owner's own jar of that
build was not used.

The *budget* and *target* columns in the before record are today's budgets, shown for reference. The before build
breaks four of them: T4, T5, S2's words and S2's clipped strings.

| Task | Success | Acts | Hesitations | Dead ends | Words before first act | Clipped |
|---|---|---|---|---|---|---|
| T1 Fresh start to the first answer | yes → yes | 4 → 5 (+1) | 1 → 2 (+1) | 0 → 0 | 225 → 225 | 0 → 1 (+1) |
| T2 Right-click Ask about a record | yes → yes | 4 → 5 (+1) | 2 → 2 | 0 → 0 | 265 → 273 (+8) | 6 → 4 (−2) |
| T3 Approve a report write | yes → yes | 1 → 1 | 0 → 0 | 0 → 0 | 317 → 325 (+8) | 3 → 3 |
| T4 Why records are missing | yes → yes | 3 → 2 (−1) | 4 → 2 (−2) | 0 → 0 | 259 → 259 | 3 → 3 |
| T5 Safe → Full → Safe | yes → yes | 9 → 8 (−1) | 2 → 2 | 0 → 0 | 246 → 254 (+8) | 5 → 2 (−3) |
| T6 Set up an external client | yes → yes | 4 → 4 | 1 → 1 | 0 → 0 | 225 → 225 | 0 → 0 |
| T7 Open the sample | yes → yes | 1 → 1 | 0 → 0 | 0 → 0 | 225 → 225 | 1 → 1 |
| T8 Load a prompt pack | yes → yes | 1 → 1 | 0 → 0 | 0 → 0 | 225 → 225 | 0 → 0 |

| State | Chrome words | Controls | Clipped | Whole-window words |
|---|---|---|---|---|
| S1 Start page | 225 → 225 | 15 → 15 | 0 → 0 | 225 → 225 |
| S2 Assistant Ready (Safe, docked) | 32 → 31 (−1) | 6 → 6 | 2 → 0 (−2) | 224 → 229 (+5) |
| S3 Records with the list shown | 17 → 25 (+8) | 5 → 8 (+3) | 0 → 0 | 265 → 273 (+8) |

A2 and T4c were added after this before point was recorded, so `baseline-b1c22704` has neither.

**What changed:**

- **T4 is the real gain: 3 → 2 acts, 4 → 2 hesitations.**
  - Before, the shown checklist could open with **no rows**. The unticked type was not visible at all, and no amount
    of scrolling could show it. The person had to drag an unlabelled divider (a grip with no words), then scroll.
  - Now three rows always show, and one scroll finds it. S3's +8 words and +3 controls are those three rows, now
    visible.
- **T5 and S2: fewer clipped strings and one act less.**
  - Before, S2 cut the model chip and the plan chip at the side. Now neither is cut.
  - In T5, the *Safe analysis* card was below the fold on the way back. It no longer is.
- **T1 and T2 each cost one act more, for a better answer.**
  - **T1:** before, a start-page question with no log open went straight to the cards and was answered generically.
    Now the page says *"No log is open — the answer will be general"* and offers *Open the sample* first, and that
    offer is the extra act. Its label holds none of the task's key words, which is the extra hesitation.
  - **T2:** before, the breach record was visible, because the collapsed checklist left the table more rows. With
    three checklist rows it is below the fold: one scroll. This is a trade-off between the two layout fixes.
- **No dead end, before or after.** The robot walks' dead ends were the four *Send*s that did nothing and the
  hidden Send at 1200×800. They predate `b1c22704`: the assistant-UX follow-ups, already in this build, fixed them.

**Script changes for the old UI (case a).** Each selector now uses whatever the build offers, so both builds run the
same scripts. The baseline at `a875fcba` was re-run after these changes and is byte-identical.

- **T1:** *Open the sample* is an optional step, clicked when the page offers it. The old build had no such offer;
  its route was straight to the cards.
- **T5:** the Full card's button is *"Turn on Full"* or, on the old build, *"Turn on full Claude Code"*.
- **T4:** when the checklist shows no rows, the person drags its divider down (one drag act).
  - The hesitation rule was sharpened for this: a control counts as **below the fold** only when its scroll pane
    has room to show it. A viewport squeezed to no rows is **not visible**, and it costs no scroll act.

**Real failures on the old UI (case b):** none. Every task completed on `b1c22704` by the route that build offered.

## Correctness checks: is the RESULT right?

The tasks above count acts to a UI state. These checks ask whether the state is the right one.

- **T4c, the result of T4** (a target; not budgeted). It uses T4's set-up: `RiskBreachEvent` unticked and the
  checklist hidden. Two things must both hold:
  1. **The visible cue names the hidden filter.** Some text on screen says records are *hidden* and names the
     event-type filter or the type. *"showing 9 of 10"* does not: it says that something is missing, not what hides it.
  2. **The assistant's context names the excluded type.** `context`, as the action socket serves it to any assistant,
     has a `hidden` entry naming `RiskBreachEvent`.

  The check reads the context itself, not a model's answer. The FixtureAgent replays a recorded stream, so its words
  cannot depend on this log; what an assistant can know is what `context` gives it. *"Nothing is missing"*, meaning no
  cue or no `hidden` entry, is a **FAIL**.
  - **On `a875fcba` it fails, as expected.** Neither half holds.
  - **On `fix/records-filter-visibility` @ `87551460` it passes.** That run was a witness only, not a baseline.
- **The right-record check, for M79's flag-and-select tasks.**
  - `Usability.expectedRecordDigest(frame, rawText -> …)` names the expected record by its content.
  - `Usability.selectedRecordDigest(frame)` gives the digest of the record the person selected.
  - `Usability.rightRecordSelected(frame, expected)` compares the two.

  The digest is the product's own record identity (`WalkIdentity.recordDigest` of the record's raw text, as a walk
  step binds a record), so a sort or a filter cannot fool it the way a row number would. T2 uses it now: the record
  the Ask is about must be the breach record.

## The authoring battery (A-tasks)

These come from the authoring walk's proposal. Only what runs deterministically with a stub assistant against this
build is scripted and budgeted. The rest are targets, each with its success criterion.

| # | Task | Status | Success criterion | Metrics | Measured at `a875fcba` |
|---|---|---|---|---|---|
| A1 | Build from a description, no AI | **target** | a runnable app built from the description, its log opened and paired with its graph | acts, terminal hand-offs, typed paths, dead ends | not scripted: the template download has no test transport (`MainFrame.templateClient` is fixed, and adding a seam would be a product hook), and the build and run happen in a terminal (the walk needed one) |
| A2 | Teach-me diamond pack | **scripted, budgeted** | the example downloaded and adopted as a project; its shipped sample log open and **paired** with the project's graph (the session's pairing verdict *applies*) | acts to the project, acts to the evidence, typed paths, terminal hand-offs, failed commands | **11 acts** (8 clicks, 1 key, 1 drag, 1 scroll): **7 to the project, 11 to the evidence**; 1 typed path; 0 terminal hand-offs; 0 failed commands; 4 hesitations; 0 dead ends |
| A3 | Set up AI for authoring | **target** | from *Start building* to a first answered Full turn in a project folder | acts, lost drafts, loops, modals | not scripted: *Start building* goes through the same template download as A1 |
| A4 | Extend the diamond with Full | **manual, occasional** (live only) | the diamond extended by Claude Code in Full, built and re-opened, with zero terminal for the person | approvals asked (allowed / denied), model turns, terminal hand-offs | the authoring walk's live run (2026-10-18, scratch evidence, approval log `decisions.txt`): **14 approval requests, 10 allowed and 4 denied** (three reads outside the scratch folder, one environment read); the build and run were approved calls, so the person needed no terminal; model turns were not recorded |
| A5 | Keyless change | **target**, pending compiler 1.0.80 | a change built and re-run without a Fluxtion key in the person's environment | acts, refusals | — |
| A6 | Pair evidence after a run | **target**, M76 | from a finished run to log and graph paired in **≤ 3 acts, no typed path** | acts, typed paths | A2's project → evidence leg is the nearest measure today: 4 acts, 1 typed path |
| A7 | Explain the change | **target** | the assistant's spotlight lands on the RIGHT record: the right-record check against the spotlit record | acts, the right-record verdict | not scripted: a recorded stream cannot point at a record of this log; needs a stub whose spotlight call is built from the log, or a live run |

**How A2 runs.**

- **The pack.** It is the walk's *What is Fluxtion?* pack, entries 1 and 2. Entry 1 is the published diamond, pinned
  by commit.
- **The download.** It goes through the frame's own **recording transport**, the `exampleClient` field the frame
  tests already replace. It is not a new hook. The pinned GitHub address is answered with GitHub's redirect, and the
  redirect with an archive.
- **The archive.** It is laid out as the diamond is: a README, a source root, the generated GraphML under
  `src/main/resources` and a shipped `sample/` log. The DEMO fixture's log and graph are inside, so nothing
  third-party is committed and no network is used. What is measured is the analyser's route, not the diamond's bytes.
- **The route,** each step a counted act:
  1. drop the pack;
  2. choose *Teaching example*;
  3. *Download example…*;
  4. *Download* in the confirmation;
  5. scroll to the project's graph in the adopt dialog. It is offered **unselected** and sits below that dialog's
     fold;
  6. pick the graph;
  7. *OK* (the project is open);
  8. the toolbar's *Open*;
  9. type the sample log's path in the file chooser;
  10. *Open*;
  11. *Yes* to *Load this project?* (the evidence is open and paired).

## Budgets enforced in CI

`UsabilityBudgetFrameTest` runs on the display, at 1200×800 and docked. The budgets are the integration build's
measured values (they were `a875fcba`'s until 2026-10-18), so any regression fails. A change that makes a number better lowers its budget in `UsabilityBudgets.java` in the same
commit. A change that needs a worse number raises it there, with the reason, where review sees it.

| Budget | Value |
|---|---|
| S1 chrome words | ≤ 224 (was 222; 225 before the integration) |
| S2 chrome words | ≤ 6 (was 31) |
| S3 chrome words | ≤ 26 (was 25) |
| S1, S2, S3 clipped strings | 0 |
| Acts per task (each task must also succeed) | T1 ≤ 6 (was 5) · T2 ≤ 5 · T3 ≤ 1 · T4 ≤ 1 (was 2) · T5 ≤ 5 (was 8) · **T6 ≤ 4** (was 7) · T7 ≤ 1 · T8 ≤ 1 · **A2 ≤ 10** (was 11) |
| T4c | must pass, 0 acts (since the integration) |
| Hesitations per task | **T1 ≤ 2 · T2 ≤ 2 · T6 ≤ 1** (since the wizard fix; the other tasks' are recorded only) |

Dead ends, words before the first act, A2's milestones and the hesitations of tasks other than T1, T2 and T6 are
recorded but not enforced. T4c was a
target until the records-filter fix made it pass; the budget test now runs it and requires it.

### Witnesses

Rule 8 requires a wrong-result witness for each budget. These were run on 2026-10-18.

| Witness | How | Result |
|---|---|---|
| A label more breaks a word budget | in the test, `aBudgetCatchesAnExtraLabelAndAnExtraStep`: a three-word `JLabel` added to the start page | S1 goes over 225, and the violation is reported |
| A step more breaks an act budget | in the same test: T7 with one more required click (the question box first) | 2 acts, over T7's 1, and the violation is reported |
| A real product change: more words | `StartPromptView.PLACEHOLDER` with *", then press Send"* appended, then `mvn clean` and the frame test | **red**: *"S1 Start page (fresh home): 228 chrome words > budget 225"* |
| The right-record check tells records apart | `theRightRecordCheckTellsRecordsApart`: the first record selected, then the breach record | *not the breach record*, then *the breach record*, by digest |
| T4c fails on this base and passes on the fix | the battery on `a875fcba`, and on `fix/records-filter-visibility` @ `87551460` (built in a scratch worktree, harness copied in) | **FAIL** here (both halves). **pass** on the fix: its *"1 hidden by event types"* line and its context's `hidden` entry |
| A real product change: a path broken | `StartPromptView`'s Enter binding (`start-send`) removed | **red**: *"T1 … did not succeed (step 3 (open the sample the page offers): no such control on screen)"* |
| Integration: a word more on the quiet panel | `AssistantProfile.shortTitle()` SAFE → *"Safe analysis"* (the chip reads *Safe analysis ▾*), the integration budgets | **red**: *"S2 Assistant Ready (Safe analysis, docked): 7 chrome words > budget 6"* |
| Wizard fix: the old T6 route | `aBudgetCatchesAnExtraLabelAndAnExtraStep`: the switch, its confirmation, then *Check connection* | 5 acts and 2 hesitations: over T6's 4 and 1, both reported |
| Wizard fix: a bare *Finish* again | `AiSetupLines.FINISH_THEN_SEND` set to *"Finish"*, `mvn clean`, `tasksStayInBudget` | **red**: *"T1 … 3 hesitations > budget 2"*, *"T2 … 3 hesitations > budget 2"* |
| Integration: T4c is enforced | `MainFrame`'s context assembly with its `hidden` put disabled | **red**: *"T4c … did not succeed (the end state was not reached)"* |

## Targets per milestone (listed, not yet enforced)

| Milestone | Target | Measured now | When it lands |
|---|---|---|---|
| **M77-2**, the Set up AI wizard's inside path; spec T19 | the newcomer path in **≤ 6 acts**: *Sample project*, right-click, *Ask (set up AI first)*, **Continue**, **Finish**, *Send* | T2: 5 acts from an open log, so 6 with the *Sample project* click | Continue and Finish replace *Apply*, one act more. Raise T2's budget to the measured value in the M77-2 PR, with its reason. T1's path changes the same way. M77-2 also rewrites the start page's AI line and first-run block, so S1 is re-baselined then |
| **M78 D1**, the quiet assistant panel; spec TQ1 | S2: **≤ 15 chrome words, ≤ 4 controls** (Ready, Safe and Full); ≤ 15 while running, waiting or answered; ≤ 20 for an error, no key or a restart | S2: 31 words, 6 controls | D1 lowers S2's budget to its measure. TQ1's own frame test adds the other states and the dark theme |
| **The records-filter fix** (`fix/records-filter-visibility`; the act numbers are proposed, so the owner should confirm them) | T4: **≤ 1 act and 0 hesitations**. **T4c passes**: the Records area says, with the list hidden, that records are hidden by the event-type filter, and `context` names the type under `hidden` | T4: 2 acts, 2 hesitations; T4c: **FAIL**, expected on `a875fcba` | T4c joins the budgeted tasks (it must pass), and T4's budget is lowered to its measure |
| **M76**, pairing evidence after a run (A6) | from the project open to log and graph paired in **≤ 3 acts, no typed path** | A2's project → evidence: 4 acts (*Open*, the typed path, *Open*, *Yes*), 1 typed path | A2's budget is lowered, and A6 is scripted as its own task |

## SUS survey (owner; now, and after M77)

The System Usability Scale (Brooke, 1986) is ten statements, each answered on a five-point scale from
1 (*strongly disagree*) to 5 (*strongly agree*):

1. I think that I would like to use this system frequently.
2. I found the system unnecessarily complex.
3. I thought the system was easy to use.
4. I think that I would need the support of a technical person to be able to use this system.
5. I found the various functions in this system were well integrated.
6. I thought there was too much inconsistency in this system.
7. I would imagine that most people would learn to use this system very quickly.
8. I found the system very cumbersome to use.
9. I felt very confident using the system.
10. I needed to learn a lot of things before I could get going with this system.

**Scoring.**

- Odd items (1, 3, 5, 7, 9) score *answer − 1*.
- Even items (2, 4, 6, 8, 10) score *5 − answer*.
- The SUS score is the sum × 2.5, from 0 to 100.
- About 68 is the commonly cited average. A score is not a percentage.

Answer it after doing the eight tasks yourself, in one sitting, on the build named in the row.

| Respondent | Build | Date | Q1 | Q2 | Q3 | Q4 | Q5 | Q6 | Q7 | Q8 | Q9 | Q10 | SUS |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| owner (baseline) | `a875fcba` | | | | | | | | | | | | |
| owner (after M77) | | | | | | | | | | | | | |

## Fresh-LLM persona run (to be filled later)

This is a second, independent reading: a model that knows nothing about the analyser tries each task from the
screen alone.

**Protocol.**

- **No repository knowledge.** It gets no CLAUDE.md, no docs, no source and no skills. It runs in a clean session
  with no project memory, and its working folder is empty.
- **It sees only the screen and a goal.**
  - The harness paints the current window to a PNG. Each turn, the persona gets that image and the goal, stated as
    the task's name in plain words, for example *"Find out why some records are missing."*
  - It answers with **one act**: the visible label it would click or the key it would press, or "I don't know what
    to do next".
  - The harness performs that act through the same finders as the scripts (`Usability.button(label)` and so on),
    then shows the next screen.
  - It stops at success, after 15 acts, or after three *don't know* answers in a row.
- **Record per task:** success, acts, *don't know* answers (the persona's hesitations), and the acts that left the
  scripted path (detours). Write them beside the scripted baseline; the difference is what the script did not
  foresee.
- **Same isolation as the battery:** an isolated home, the stub `claude`, a recorded Claude Code. The persona's own
  model calls are the only network traffic.

| Persona model | Build | Date | T1 | T2 | T3 | T4 | T5 | T6 | T7 | T8 | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|
| | | | | | | | | | | | |

## The honest limits

- **A robot doesn't get bored.** It reads every word and never skims, so a 225-word start page costs it nothing.
  Word counts are therefore a **proxy for text a person ignores**, not a measure of it. Fewer words on screen is a
  hypothesis about attention that the SUS and the persona run test. The word budget only stops the count from
  growing unnoticed.
- **The hesitation rules are a stand-in for judgement.** They are reproducible because they are crude:
  - a person may well press *Apply* without hesitating;
  - a person may miss a visible control that the rules count as fine;
  - the rules do not model reading order, attention, or what a person already knows.
- **The key words are the author's.** They are written down and frozen, but another author would pick other
  synonyms.
- **The scripts know the path.** Discovery is not measured: the battery never wanders, so a task's acts are the
  shortest scripted route. Preconditions skip how the person got there; T3, for example, does not measure noticing
  the dialog. The persona run exists to measure that.
- **Painted text is only partly seen.** The counter reads the Swing tree and the painters it knows (cards, chips,
  rail labels, titled borders and placeholders). It does not read chart and topology labels, spotlight callouts,
  the empty-table message, or the OS title bar. `TextFit` does not judge painted text, so S2's cut placeholder is not
  counted as clipped.
- **One window, one theme, one platform per number.** All numbers are at 1200×800 and in Light, measured on macOS.
  Fonts differ under Linux and Xvfb. CI checks the same budgets there; a font that clips or folds more would show up
  as a red build, not silently.
- **The fixture agent is not a model.** Answers arrive at once, so time to the first answer is not measured, and
  this scorecard has no timing at all.
- **A2's archive is not the diamond.** It has the diamond's layout and the DEMO fixture's content. A change in the
  real archive's layout, such as a graph that is no longer under `src/main/resources`, would not show up here.
- **T4c reads the context, not an answer.** Whether a model then *says* the right thing needs a live run or the
  persona run.
- **A frame's late work still uses the JVM's `user.home`.** Each session keeps its home for 1.5 s after its window
  closes, so a late write cannot land in the next task's fresh home. Before this grace, one of the first five runs saw T6 start
  on a workspace that the previous task had left behind.
- **A drop is simulated** at its `TransferHandler`. The file manager the person drags from is outside the app and is
  not counted.
