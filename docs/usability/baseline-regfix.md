# Usability scorecard: regfix

Window 1200×800, docked, Light theme; stub `claude`, recorded Claude Code streams; isolated `user.home`.

## Tasks

| Task | Success | Acts (click/key/drag/scroll) | Hesitations | Dead ends | Words before first act | Clipped | Acts budget | Target |
|---|---|---|---|---|---|---|---|---|
| T1 Fresh start to the first answer | yes | 6 (4/2/0/0) | 2 | 0 | 224 | 1 | ≤ 6 | — |
| T2 Right-click Ask about a record | yes | 5 (5/0/0/0) | 2 | 0 | 262 | 4 | ≤ 5 | ≤ 6 (M77 T19, M77-2): met |
| T3 Approve a report write | yes | 1 (1/0/0/0) | 0 | 0 | 314 | 3 | ≤ 1 | — |
| T4 Find out why records are missing (a type filter on, the list hidden) | yes | 1 (1/0/0/0) | 1 | 0 | 272 | 3 | ≤ 1 | ≤ 1, 0 hesitations (records-filter fix, proposed) |
| T5 Switch Safe → Full → Safe | yes | 5 (5/0/0/0) | 0 | 0 | 199 | 1 | ≤ 5 | — |
| T6 Set up an external client | yes | 4 (4/0/0/0) | 1 | 0 | 224 | 0 | ≤ 4 | ≤ 4, ≤ 1 hesitation (fix/wizard-scorecard-regressions): met |
| T7 Open the sample | yes | 1 (1/0/0/0) | 0 | 0 | 224 | 1 | ≤ 1 | — |
| T8 Load a prompt pack | yes | 1 (0/0/1/0) | 0 | 0 | 224 | 0 | ≤ 1 | — |
| A2 Teach-me diamond pack: the example downloaded, its log and graph paired | yes | 10 (7/1/1/1) | 3 | 0 | 224 | 6 | ≤ 10 | ≤ 3 acts project→evidence, no typed path (M76, as A6) |

## Correctness targets (run, not budgeted)

| Task | Result | Why not |
|---|---|---|
| T4c T4 correct: the cue and the assistant's context name the hidden filter | pass | — |

## Authoring milestones

| Task | Acts to | Typed paths | Terminal hand-offs |
|---|---|---|---|
| A2 | {project=7, evidence=10} | 1 | 0 |

## Key states

| State | Region | Chrome words | Controls | Clipped | Whole-window words | Words budget | Clipped budget | Target |
|---|---|---|---|---|---|---|---|---|
| S1 Start page (fresh home) | the start page | 224 | 16 | 0 | 224 | ≤ 224 | ≤ 0 | — |
| S2 Assistant Ready (Safe analysis, docked) | the assistant panel | 6 | 4 | 0 | 195 | ≤ 6 | ≤ 0 | ≤ 15 words, ≤ 4 controls (M78 D1, TQ1): met |
| S3 Records with the event-type list shown | the Records section | 26 | 9 | 0 | 274 | ≤ 26 | ≤ 0 | — |

## Steps

**T1 Fresh start to the first answer** — key words: ask, send, question, answer

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | key | type a question in the start page's box | Ask about a log, or choose an entry above | — | — |
| 2 | key | press Enter | Enter sends · Shift+Enter starts a new line | — | — |
| 3 | click | open the sample the page offers | Open the sample | label lacks key word | — |
| 4 | click | check Claude Code (Claude inside preselected) | Check Claude Code | label lacks key word | — |
| 5 | click | finish setting up AI | Finish, then send my question | — | — |
| 6 | click | send the placed question | Send | — | — |

Clipped on the way:

- JLabel "Yours, on this machine. Never written to a profile, never shared." is cut at the side: shows 284 of 336 px

**T2 Right-click Ask about a record** — key words: ask, send, question, answer

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | click | right-click the RiskBreachEvent record | 2026-01-01 09:00:00.300 RiskBreachEvent  com.acme.demo.GenerateFixtures.main() 1 node | behind menu, label lacks key word | — |
| 2 | click | choose Ask: What happened here? | Ask the assistant: What happened here? (set up AI first) | — | — |
| 3 | click | check Claude Code (Claude inside preselected) | Check Claude Code | label lacks key word | — |
| 4 | click | finish setting up AI | Finish, then send my question | — | — |
| 5 | click | send the placed question | Send | — | — |

Clipped on the way:

- JLabel "Yours, on this machine. Never written to a profile, never shared." is cut at the side: shows 284 of 336 px
- JCheckBox "Scaffolding" is cut at the side: shows 68 of 88 px
- JLabel "every node id checked is declared (5/5)   ·   demo-quote-processor.gra…" is narrower than its text: 200 < 2043
- JLabel "every node id checked is declared (5/5)   ·   RiskBreachEvent — 1 node…" is narrower than its text: 200 < 2066

**T3 Approve a report write** — key words: approve, allow

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | click | allow the report write once | Allow once | — | — |

Clipped on the way:

- JLabel "Yours, on this machine. Never written to a profile, never shared." is cut at the side: shows 284 of 336 px
- JCheckBox "Scaffolding" is cut at the side: shows 68 of 88 px
- JLabel "every node id checked is declared (5/5)   ·   demo-quote-processor.gra…" is narrower than its text: 200 < 2043

**T4 Find out why records are missing (a type filter on, the list hidden)** — key words: filter, hidden, missing

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | click | show the event-type checklist | ▸ Event types | label lacks key word | — |
| 2 | — | find the unticked type | RiskBreachEvent (1) | — | — |

Clipped on the way:

- JLabel "Yours, on this machine. Never written to a profile, never shared." is cut at the side: shows 284 of 336 px
- JCheckBox "Scaffolding" is cut at the side: shows 68 of 88 px
- JLabel "every node id checked is declared (5/5)   ·   demo-quote-processor.gra…" is narrower than its text: 200 < 2043

**T5 Switch Safe → Full → Safe** — key words: safe, full

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | click | open the mode chip | Safe ▾ | — | — |
| 2 | click | choose Full Claude Code | ◆ Full Claude Code… | — | — |
| 3 | click | accept the Full consent | Turn on full Claude Code | — | — |
| 4 | click | open the mode chip | ◆ Full ▾ | — | — |
| 5 | click | choose Safe analysis | Safe analysis | — | — |

Clipped on the way:

- JLabel "Yours, on this machine. Never written to a profile, never shared." is cut at the side: shows 284 of 336 px

**T6 Set up an external client** — key words: client, connect, mcp

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | click | choose your own AI client | Your own AI client connects | — | — |
| 2 | click | set up my client | Set up my client | — | — |
| 3 | click | check the connection | Check connection | — | — |
| 4 | click | confirm turning access on | Turn on access | label lacks key word | — |

**T7 Open the sample** — key words: sample

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | click | open the sample project | Sample project | — | — |

Clipped on the way:

- JLabel "Yours, on this machine. Never written to a profile, never shared." is cut at the side: shows 284 of 336 px

**T8 Load a prompt pack** — key words: prompt pack

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | drag | drop the pack on the start page | Drop an .fexp bundle, audit log, GraphML, Spring XML or a prompt pack (.md) anywhere on th… | — | — |

**A2 Teach-me diamond pack: the example downloaded, its log and graph paired** — key words: teaching, example, download, open, log

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | drag | drop the teach-me pack on the start page | Drop an .fexp bundle, audit log, GraphML, Spring XML or a prompt pack (.md) anywhere on th… | — | — |
| 2 | click | choose the teaching example | 1. ⤓ Teaching example: the diamond | — | — |
| 3 | click | download it | Download example… | — | — |
| 4 | click | confirm the download | Download | — | — |
| 5 | click | choose the project's graph | <work>/A2/home/fluxtion-diamond-dad9b43/src/main/resources/c… | below fold, label lacks key word | — |
| 6 | click | adopt the project | OK | label lacks key word | — |
| 7 | click | open a log | Open | — | — |
| 8 | key | type the sample log's path | File name | label lacks key word | — |
| 9 | click | open it | Open | — | — |

Clipped on the way:

- JButton "Download" is cut at the top or bottom: shows 18 of 23 px
- JButton "Cancel" is cut at the top or bottom: shows 18 of 23 px
- JLabel "Yours, on this machine. Never written to a profile, never shared." is cut at the side: shows 284 of 336 px
- JCheckBox "Scaffolding" is cut at the side: shows 68 of 88 px
- JLabel "no log open — this graph is not compared with any run: nothing here is…" is narrower than its text: 200 < 2380
- JLabel "every node id checked is declared (5/5)   ·   DemoQuoteProcessor.graph…" is narrower than its text: 200 < 2037

