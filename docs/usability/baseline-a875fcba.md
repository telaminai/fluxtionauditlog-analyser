# Usability scorecard: a875fcba

Window 1200×800, docked, Light theme; stub `claude`, recorded Claude Code streams; isolated `user.home`.

## Tasks

| Task | Success | Acts (click/key/drag/scroll) | Hesitations | Dead ends | Words before first act | Clipped | Acts budget | Target |
|---|---|---|---|---|---|---|---|---|
| T1 Fresh start to the first answer | yes | 5 (3/2/0/0) | 2 | 0 | 225 | 1 | ≤ 5 | — |
| T2 Right-click Ask about a record | yes | 5 (4/0/0/1) | 2 | 0 | 273 | 4 | ≤ 5 | ≤ 6 (M77 T19, M77-2) |
| T3 Approve a report write | yes | 1 (1/0/0/0) | 0 | 0 | 325 | 3 | ≤ 1 | — |
| T4 Find out why records are missing (a type filter on, the list hidden) | yes | 2 (1/0/0/1) | 2 | 0 | 259 | 3 | ≤ 2 | ≤ 1, 0 hesitations (records-filter fix, proposed) |
| T5 Switch Safe → Full → Safe | yes | 8 (7/0/0/1) | 2 | 0 | 254 | 2 | ≤ 8 | — |
| T6 Set up an external client | yes | 4 (4/0/0/0) | 1 | 0 | 225 | 0 | ≤ 4 | — |
| T7 Open the sample | yes | 1 (1/0/0/0) | 0 | 0 | 225 | 1 | ≤ 1 | — |
| T8 Load a prompt pack | yes | 1 (0/0/1/0) | 0 | 0 | 225 | 0 | ≤ 1 | — |
| A2 Teach-me diamond pack: the example downloaded, its log and graph paired | yes | 11 (8/1/1/1) | 4 | 0 | 225 | 6 | ≤ 11 | ≤ 3 acts project→evidence, no typed path (M76, as A6) |

## Correctness targets (run, not budgeted)

| Task | Result | Why not |
|---|---|---|
| T4c T4 correct: the cue and the assistant's context name the hidden filter | **FAIL** | nothing on screen says records are hidden by the event-type filter; the assistant's context has no 'hidden' entry naming RiskBreachEvent |

## Authoring milestones

| Task | Acts to | Typed paths | Terminal hand-offs |
|---|---|---|---|
| A2 | {project=7, evidence=11} | 1 | 0 |

## Key states

| State | Region | Chrome words | Controls | Clipped | Whole-window words | Words budget | Clipped budget | Target |
|---|---|---|---|---|---|---|---|---|
| S1 Start page (fresh home) | the start page | 225 | 15 | 0 | 225 | ≤ 225 | ≤ 0 | — |
| S2 Assistant Ready (Safe analysis, docked) | the assistant panel | 31 | 6 | 0 | 229 | ≤ 31 | ≤ 0 | ≤ 15 words, ≤ 4 controls (M78 D1, TQ1) |
| S3 Records with the event-type list shown | the Records section | 25 | 8 | 0 | 273 | ≤ 25 | ≤ 0 | — |

## Steps

**T1 Fresh start to the first answer** — key words: ask, send, question, answer

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | key | type a question in the start page's box | Ask about a log, or choose an entry above | — | — |
| 2 | key | press Enter | Enter sends · Shift+Enter starts a new line | — | — |
| 3 | click | open the sample the page offers | Open the sample | label lacks key word | — |
| 4 | click | apply the default assistant | Apply | label lacks key word | — |
| 5 | click | send the placed question | Send | — | — |

Clipped on the way:

- JLabel "Yours, on this machine. Never written to a profile, never shared." is cut at the side: shows 284 of 336 px

**T2 Right-click Ask about a record** — key words: ask, send, question, answer

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | click | right-click the RiskBreachEvent record | 2026-01-01 09:00:00.300 RiskBreachEvent  com.acme.demo.GenerateFixtures.main() 1 node | below fold, behind menu, label lacks key word | — |
| 2 | click | choose Ask: What happened here? | Ask the assistant: What happened here? | — | — |
| 3 | click | apply the default assistant | Apply | label lacks key word | — |
| 4 | click | send the placed question | Send | — | — |

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
| 1 | click | show the event-type checklist | Event types | label lacks key word | — |
| 2 | — | find the unticked type | RiskBreachEvent (1) | below fold | — |

Clipped on the way:

- JLabel "Yours, on this machine. Never written to a profile, never shared." is cut at the side: shows 284 of 336 px
- JCheckBox "Scaffolding" is cut at the side: shows 68 of 88 px
- JLabel "every node id checked is declared (5/5)   ·   demo-quote-processor.gra…" is narrower than its text: 200 < 2043

**T5 Switch Safe → Full → Safe** — key words: safe, full

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | click | open the assistant chooser | Assistant: Safe analysis ▾ | — | — |
| 2 | click | choose the Full Claude Code card | Full Claude Code | below fold | — |
| 3 | click | turn Full on | Turn on Full | — | — |
| 4 | click | accept the Full consent | Turn on full Claude Code | — | — |
| 5 | click | open the assistant chooser | Full Claude Code ▾ | — | — |
| 6 | click | choose the Safe analysis card | Safe analysis | — | — |
| 7 | click | apply Safe analysis | Apply | label lacks key word | — |

Clipped on the way:

- JLabel "Yours, on this machine. Never written to a profile, never shared." is cut at the side: shows 284 of 336 px
- JLabel "◆ Full Claude Code (default) — on: it runs at the next Start · your to…" is narrower than its text: 200 < 544

**T6 Set up an external client** — key words: client, connect, mcp

| # | Act | What | Visible label | Hesitation | Dead end |
|---|---|---|---|---|---|
| 1 | click | choose Other MCP client | Other MCP client | — | — |
| 2 | click | turn on External MCP/REST access | Turn on External MCP/REST access… | — | — |
| 3 | click | confirm | OK | label lacks key word | — |
| 4 | click | check the connection | Check connection | — | — |

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
| 10 | click | load the project's settings | Yes | label lacks key word | — |

Clipped on the way:

- JButton "Download" is cut at the top or bottom: shows 18 of 23 px
- JButton "Cancel" is cut at the top or bottom: shows 18 of 23 px
- JLabel "Yours, on this machine. Never written to a profile, never shared." is cut at the side: shows 284 of 336 px
- JCheckBox "Scaffolding" is cut at the side: shows 68 of 88 px
- JLabel "no log open — this graph is not compared with any run: nothing here is…" is narrower than its text: 200 < 2380
- JLabel "every node id checked is declared (5/5)   ·   DemoQuoteProcessor.graph…" is narrower than its text: 200 < 2037

