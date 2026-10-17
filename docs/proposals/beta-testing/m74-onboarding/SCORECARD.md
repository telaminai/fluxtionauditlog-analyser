# M74 onboarding — the scorecard

Status: **proposal, 2026-10-17.** The fields below are computed by `tools/m74_onboarding_runner.py score <run>
<oracle.json>` over a recorded run folder, and the computation is tested over a recorded fixture
(`tools/fixtures/m74-onboarding/run-t1-pass`) with a wrong-result control for each verdict-deciding check
(`tools/test_m74_onboarding_runner.py`). **Nothing on the scorecard is typed by a person or taken from a model's
account of what it did.** A field whose fact the analyser does not record yet says `unavailable: Rn`, naming the
requirement at the end of this page, and is never filled in by hand.

The protocol that produces the inputs is [PROTOCOL.md](PROTOCOL.md). The facts are read by the names the M74 P2
branch uses (`SessionEvents` records, and the keys the session nodes write with `auditLog.info`) as of
`feature/claude-code-p2` at `71e0c57f`. P3's names are the spec's (§8.3), not code yet. **When P2 or P3 merges, re-read
the names before the first run**: the scorer matches strings, and a renamed fact reads as zero, not as an error.
The tests pin the shapes the scorer expects, so a rename is fixed in one place.

## 1. Inputs: the run folder

| File | Written by | Holds | Words? | Committed? |
|---|---|---|---|---|
| `config.json` | `init-run` | every pin (§2 of the protocol), the track, the seal's digest | no | yes |
| `session-record.yaml` | the operator harness, at the end of the run, through `SessionAuditSink.export` | the session processor's own audit record: every fact (`event`, `eventToString`) and the node logs, in arrival order | **some** (§5, R6) | **no**, unless redacted |
| `session-record.meta.json` | the operator harness | the sink's `total()`, `dropped()`, `sinkFailures()` | no | yes |
| `operator.jsonl` | the operator harness | every act it performed: kind, source (`script`, `policy`, `guided`, `reply`, `nudge`), observer time `t`, the digest of any text it typed, and for a decision the analyser-shown verdict it decided on | digests only | yes |
| `snapshots.jsonl` | the observer, after every turn end and every flow end | the observer time, the ticket, `recordSeq` (how many session records existed), the analyser's `context`, and `pointers`: each record the analyser is pointing at, resolved to `{via, event, logTime, nodes}` | no | yes |
| `answers.jsonl` | the operator harness, from the transcript store | Claude's rendered answer per ticket | **yes** | **no** |
| `cli-session.jsonl` | the runner, copied from the isolated `CLAUDE_CONFIG_DIR/projects/` | the CLI's own session transcript, tool calls and results included | **yes** | **no** |
| `meta.json` | the runner | wall time, cap, process-tree reaping, key scan (T4), infrastructure voids | no | yes |
| `score.json` | `score` | this scorecard | no | yes |

`pointers` is how correctness is read without reading prose. The analyser already says what it is pointing at:
`context.selection` carries each selected record's `event` and `logTime`; `context.flags` carries `recordIndex`, which
the observer resolves with `read`; a spotlight's record targets are resolved the same way. A pointer is a fact about
the analyser's state, not a claim in an answer.

## 2. Fields

**Time** is the observer's clock (`tools/coldstart_clock.py`, the same `ObserverClock` the G14 runner uses), never a
fact's own timestamp and never the model's. `t0` is the operator's first act.

| Field | Definition | Source, exactly | Status |
|---|---|---|---|
| `timeToFirstCorrectSeconds` | `t` of the first snapshot at which **every** primary oracle predicate holds, minus `t0` | `snapshots.jsonl` `t`; `operator.jsonl` first `t`; the predicates of §3 | available |
| `firstCorrectAfterTicket` | the turn after which it first held | that snapshot's `afterTicket` | available |
| `sendsToFirstCorrect` | Sends in the record before that snapshot | count of `event: LocalAgentSendRequested` among the first `recordSeq` records | available |
| `sendCount` | every composer Send | count of `LocalAgentSendRequested` | available |
| `startPageSends` | the start page's button | count of `StartPagePromptSent` | available |
| `starts` | the tab's Start (OD-74.1's one act) | count of `LocalAgentStartRequested` | available |
| `turns.count`, `turns.outcomes` | each turn and how it ended | node log `localAgentTurn: <ticket>` (with `generation`) opens a turn; `localAgentTurnEnded: <COMPLETED\|CANCELLED\|ERROR\|BUDGET\|PROCESS_EXIT>` with `ticket` closes it | available |
| `hostedCalls.admitted`, `.refused` | the hosted bridge's calls the session admitted or refused, attributed to the open turn (OD-74.7: one turn at a time) | node log `localAgentCall: ADMITTED\|REFUSED` between a turn's open and close | available |
| `hostedCalls.byVerb` | calls per verb and per outcome | — | **unavailable: R1** (the admission fact names no verb) |
| `refusalsByReason` | every refusal, counted by reason | node logs `localAgentRefused: <verdict>`, `localAgentSpawnFailed: <verdict>`, `exampleRefused: <reason>`, `localAgentCall: REFUSED`, `proposal: REFUSED` with `why`, `startPromptPack: REFUSED` with `cause`, `localAgentPosture: <not OK>`, `localAgentMode: MODE_MISMATCH`; fact `LocalAgentNoted` `note` (`PERMISSION_DENIED`, `APPROVAL_UNAVAILABLE`, `AUTH_FAILED`, `RATE_LIMITED`, `COMPACTED`) | available; the **tool** behind a posture denial is **unavailable: R2** |
| `proposals.offered`, `.items` | proposals and their items | node log `proposal: p-<n>` with `items` | available |
| `proposals.applied`, `.createApplied` | items the analyser applied through the person's path | node log `proposalItem: <id>` with `outcome: APPLIED` (`…create` is the analyser's own create row) | available |
| `proposals.finalStates` | each item's last state: `applied`, `declined`, `refused`, `outside`, `failed` | the last snapshot's `context.proposals` rows (ids, kinds, states, verdicts; never Claude's reasons) | available |
| `proposals.pairing`, `.fit` | the analyser's own check after the last item | node log `proposalChecked` with `pairing`, `fit` | available |
| `proposals.itemsShown`, `.itemsReviewed`, `.everyItemReviewed` | items the person could answer, and whether the operator recorded a decision for each | shown = rows not `refused`/`outside`; reviewed = `operator.jsonl` acts of kind `review` naming the item | available, **from the operator log**: the record cannot show a No (**R5**) |
| `approvals.asked`, `.byClass` | permission requests that reached the analyser (P3) | fact `LocalAgentPermissionRequested` `toolClass` | **P3** (spec §8.3 names; **R8**) |
| `approvals.granted`, `.deniedBy` | Allow once by the person; denials by `PERSON`, `TIMEOUT`, `STOP`, `POLICY`, `BUSY`, `TOO_LARGE` | fact `LocalAgentPermissionAnswered` `decision`, `by` | **P3** (**R8**) |
| `approvals.approvalUnavailable` | P2's stand-in: an action the mode would have asked about, denied because nobody can be asked | `LocalAgentNoted` `note: APPROVAL_UNAVAILABLE` | available |
| `operator.guidedActs`, `.cannedReplies`, `.nudges` | person acts taken because Claude named a control, because Claude asked a question, or to unstick a stalled turn | `operator.jsonl` `guided`, `reply`, `nudge` | available |
| `boundary` | every person-act fact has an operator act of the same kind, in order, and every press that should post a fact did | the facts `StartPromptPackDropped`, `ReturnToMainOptions`, `StartPagePromptSent`, `LocalAgentStartRequested`, `LocalAgentFolderAnswered`, `LocalAgentSendRequested`, `LocalAgentStopRequested`, `ProposalApplyRequested`, `ProposalDismissed`, `ExampleDownloadRequested`, `ExampleDownloadConfirmed`, `LocalAgentSwitchChosen` and (P3) `LocalAgentPermissionChosen`, against `operator.jsonl` | available; stronger with **R3** |
| `postureIntegrity` | every turn's `system/init` shows only the hosted bridge, connected, and no other server's tools; every posture verdict `OK` | fact `LocalAgentInitObserved` `servers`, `tools`; node log `localAgentPosture` | available; skills and plugins loaded are **unavailable: R7** |
| `correctness.primaryHeld` | the oracle's primary predicates held at some snapshot | §3 | available |
| `correctness.claims` | required phrases found, and **contradictions**: a forbidden phrase in any answer | `answers.jsonl`, against the oracle's `claims.require` / `claims.forbid` regular expressions | available (harness reads the store; **R4** for a product path) |
| `cost.totalUsd`, `.basis` | the session's running total and what it means | fact `LocalAgentCostObserved` `totalCostMicros` (max: it is already a running total, §4.6), fact `LocalAgentInitialized` `billing` | available |
| `deadEnds` | §4 | the record, the snapshots | available |
| `oracleLeakHits` | fetches of this repository's protocol, evidence or oracle material | `cli-session.jsonl`, `answers.jsonl`, against `ORACLE_LEAK_PATTERN` | available |
| `complete` | the session record holds the whole run | `session-record.meta.json`: `dropped == 0` and `sinkFailures == 0` | available |
| `wallSeconds`, `capped`, `reaped`, `keyScanClean` | the run's envelope | `meta.json` (the G14 runner's reaping, sweep and key scan, reused) | available |

**Cost, honestly.** On a Claude plan (`billing: SUBSCRIPTION`, which the trial OAuth token is) the figure is the CLI's
own `total_cost_usd`: a notional price for comparison, not a charge, and the header shows none (OD-74.24). It is the
same figure the 2026-10-12/13 ladders reported, so it compares with them. With `API_KEY` it is billed. With
`UNKNOWN` it is not reported as money. An interrupted turn may under-report (`costUnderReported`), so a capped run's
cost is *at least* the figure.

## 3. The oracle

An oracle is a JSON file, **written and sealed before the cycle's first run** (protocol §3), measured on a throwaway
instance of the pinned build. It has **primary predicates** (all must hold for `correctness.primaryHeld`) and **claims**.

| Predicate `kind` | Holds when | Used by |
|---|---|---|
| `pointer` | some resolved pointer has every key in `match` (`event`, `logTime`, …) | T1, T3's first question, T5 |
| `proposal` | every kind in `appliedKinds` is `applied` in `context.proposals`, and the analyser's own `proposalChecked` pairing equals `pairing` | T2 |
| `example` | the example node logged `example: DONE`, the `ExampleDownloadRequested` source's commit is the sealed one, and (optionally) the open project's root ends with `projectRootSuffix` | T3 |
| `node-logged-once` | the named node (or `@new`: the node the subject added, which the observer finds from the GraphML difference) logged exactly once **and** a pointer is at the record where it did | T4 |

`claims.require` and `claims.forbid` are regular expressions over the answers. **A required phrase does not make an
answer correct** (the primary predicates do); it is reported. **A forbidden phrase fails the run**: it is the
mechanical form of *no claim the record does not support* — for example, saying a node that never logged *did not run*
(ONBOARDING ▸ *the three that bite*, rule 1). Writing forbid lists is the one place a person's judgement enters, and it
is spent before the run, sealed, not after.

**Why pointers, and not "the answer names record #16".** An index in prose is an answer's claim; the selection or a
flag is the analyser's state, read by the analyser. It also follows the product's own rule, *point before you explain*.
A model that names the right record without pointing at it scores `requiredFound` but not `primaryHeld`, and that
difference is worth seeing.

## 4. Dead ends

| Type | Means | Detected from |
|---|---|---|
| `TURN_NOT_COMPLETED` | a turn ended `CANCELLED`, `ERROR`, `BUDGET` or `PROCESS_EXIT` | `localAgentTurnEnded` |
| `ANSWERED_WITHOUT_LOOKING` | a completed turn, before the first correct answer, made no hosted call | the turn's `localAgentCall` count |
| `REPEATED_REFUSAL` | the same refusal twice in a row | the refusal sequence (§2) |
| `FLOW_ENDED` | a start-page flow ended `CANCELLED`, `REFUSED` or `FAILED` | node log `startPageFlow` with `outcome` |
| `NOTHING_APPLICABLE_PROPOSED` | a proposal whose every item was refused or outside | `context.proposals` |
| `APPROVAL_UNAVAILABLE` | the subject needed an approval the build cannot ask for (P2) | `LocalAgentNoted` |
| `NO_CORRECT_ANSWER` | the run ended without the oracle holding | §3 |

## 5. Verdicts

- **VOID** — the run measures nothing about the product and the subject, and is kept as evidence:
  `INCOMPLETE_RECORD` (the session ring dropped records, or the sink failed); `BOUNDARY` (a person-act fact with no
  operator act, or the reverse: something other than the operator pressed, or the harness misfired);
  `POSTURE_OR_CONTAMINATION` (a server, tool or posture the run was not started with); `ORACLE_LEAK`; `NOT_REAPED`;
  `INFRA:<cause>` (an outage, a rate limit, a host that would not serve the pinned example), recorded by the runner.
  A VOID run is re-run once; a second VOID of the same cause is a finding about the rig.
- **PASS** — not VOID, and every check the track names holds (`TRACKS` in the runner): T1 and T3 `oracle` and
  `noContradiction`; T2 `oracle` and `everyItemReviewed`; T4 those plus a clean `keyScan`; T5 `oracle` and
  `noContradiction`.
- **FAIL** — otherwise. The scorecard says which check failed; the reason is read from the dead ends and refusals,
  not from the model's summary.

A product refusal that ends a run (`TOOLS_MISMATCH`, `MODE_MISMATCH`, `SERVERS_MISMATCH`, `CAPABILITY_MISSING`) is
**FAIL with a product finding**, never VOID: it is the analyser deciding, which is what is under test.

## 6. Across runs: `RESULTS.md`

Generated from `score.json` files, never typed. Per track and model: PASS `k/n` (VOID excluded and listed), median
`timeToFirstCorrectSeconds` over passing runs, median `sendCount`, the union of `refusalsByReason`, dead ends by type,
interventions (`nudges + cannedReplies`) and guided acts, notional cost total and median. **Never pool tracks**, and
never compare two cycles without the confounds table the release ladder used (everything that changed between them).
With `n = 3` a one-run difference is noise; say so beside it.

## 7. Requirements for M74 (facts the scorecard needs that the code does not record)

Read from the P2 branch, not inferred. Each says what is lost without it and whether the operator harness can stand in.

| # | Requirement | Without it | Harness stand-in |
|---|---|---|---|
| **R1** | The hosted call's **verb** and **ticket** on the admission fact (`LocalAgentCallAdmissionRequested{call, generation}` names neither), and the dispatcher's own refusal of an admitted call (`graph {delete}` from the hosted bridge, a hidden verb, an unknown verb) as a fact with a reason code | calls per verb; which refusal was the dispatcher's | `cli-session.jsonl` tool calls (subject-side, secondary) |
| **R2** | A fact for a built-in tool use and a posture denial naming the tool from a fixed vocabulary (`Read`, `Grep`, `Glob`, …): `LocalAgentNoted(PERMISSION_DENIED)` names none, and tool lines live only in the transcript store as words | which tool was denied, so a "denied by the posture" dead end can be told from a confused model | the transcript store's tool lines (words) |
| **R3** | An `origin` (the control's id) on every person-act fact, as `LocalAgentStopRequested` and `AssistantModeChosen` already carry | the boundary check relies on the operator log matching the record; with an origin the record proves it alone | none needed while the operator log is complete |
| **R4** | A product path to the conversation's words for claim checks (P5's conversation log, or a v2 walk recording) | claim checks need the in-JVM harness | the harness reads the bounded store by reference |
| **R5** | The person's per-item answer in the review dialog as a fact (spec §8.2 lists `ProposalItemAnswered{proposalId, itemId, yes}`; the code posts `ProposalApplyRequested{yes: […]}` only) — **a spec-versus-code divergence** | a No and an item never looked at are the same in the record, so "every item reviewed" is shown only by the operator log | `operator.jsonl` `review` acts |
| **R6** | **Words and paths in facts.** `ProposalReceived` carries Claude's `summary` and each item's `reason` and `path`; `LocalAgentSetupDirectoryChosen.dir`, `ExampleDownloadConfirmed.targetDir`, `ExampleUnpacked.root` and `ProposalPreviewDone.setupDir` carry real paths. The session processor records each event's `eventToString`, so these reach the session record — against spec §4.1 (*"facts carry … never words, account data or paths"*). Read from the source, **not verified on a live export**. For the owner: keep (and say so in §4.1) or move to references | a session record cannot be committed or shared unredacted | the scorer reads only enums, ids and counts; the protocol redacts before anything leaves the run |
| **R7** | The skills, plugins and agents a turn's `system/init` reports, as a count or digest on `LocalAgentInitObserved` | a virgin full-posture run cannot prove from the record that none of the owner's skills loaded | the isolated `CLAUDE_CONFIG_DIR` and the sandbox (protocol §2) make it true; nothing records it |
| **R8** | P3's facts exactly as §8.3 names them (`LocalAgentPermissionRequested{… toolClass, verb …}`, `LocalAgentPermissionAnswered{… decision, by}`, `HostedCallPresented`) | T4 and T1-cold cannot be scored | none |
| **R9** | `context` has no local-agent key (phase, verdict, armed Start): an observer over REST cannot see the session | an external observer must be in-JVM | the in-JVM harness reads the snapshot |
| **R10** | The session record's completeness travels with an export (the export writes no header, by design) | a truncated record could be scored as whole | `session-record.meta.json` from `total()`, `dropped()` |

R1, R2, R5 and R8 change what the scorecard can say; R6 changes what may be published. R3, R4, R7, R9 and R10 have
stand-ins that work while the operator harness runs in the analyser's JVM.
