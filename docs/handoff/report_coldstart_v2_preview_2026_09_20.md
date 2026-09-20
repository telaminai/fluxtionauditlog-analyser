# Cold-start v2 preview rehearsal — operator report

Subsequent operator-only work: [one supported bundle and preserved-corpus re-score](report_supported_v2_preflight_2026_09_20.md).
The original trial scores below remain the record of the original instrument; the later report records corrections separately.

**Verdict: the T1 matrix is complete; v2 acceptance is NOT ESTABLISHED. The T1–T6 battery is incomplete.**
This does not change the implementation's independent review verdict. Publication, public acquisition
and held-out workflow acceptance remain open. No product or scorer changes were made during the trial.

The owner selected a branch-preview rehearsal because the public Spring ZIP lacked the v2 files.
The six fresh sessions used two model levels, three trials each, with the exact T1 prompt and unchanged
journal/scorer from `main@8c488c8`. Main's pending protocol commits were pushed first. Operator
[predictions](evidence/coldstart-v2-2026-09-20/OPERATOR-JOURNAL.md) were frozen before setup and published
as `0682585` before this matrix. [Evidence index](evidence/coldstart-v2-2026-09-20/README.md).

## Results and stopping point

| Trial | Observed model | Client duration | Outcome | v2 acquisition |
|---|---|---:|---|---|
| 1 | claude-sonnet-5 | 22 s | Provider refusal, before a build | None |
| 2 | claude-sonnet-5 | 26 s | Provider refusal; journal helper also failed outside writable scope | None |
| 3 | claude-sonnet-5 | 4 m 27 s | Completed application, output checked against ten literal orders | None; hand-written project on `com.telamin.fluxtion:0.9.17` |
| 4 | claude-opus-5 | 5 m 24 s | Provider refusal after dependency/generator exploration and a legacy spike | None |
| 5 | claude-opus-5 | 9 m 10 s | Downloaded/read v2 project; missing-key path, then legacy rewrite; provider refusal before final build | One captured `fluxtion-dag-multi-io` ZIP |
| 6 | claude-opus-5 | 7 m 8 s | Application output and five passing tests, then provider refusal | None; hand-written project on `com.fluxtion:9.7.12` |

Five sessions ended with the provider's `reasoning_extraction` API refusal. The exact responses are in
the transcripts. The cause is not established; it is **not** a Fluxtion/analyser defect. The prompts
were not rewritten to work around the refusals. Trial 6's refusal does not erase its earlier observed
output/tests, but those ran on the older library, not the v2 workflow.

Only trial 5 acquired a preview ZIP. The proxy retained it before edits, SHA-256
`2c669208b8794c612764a087cbe9454533860133b5864688928b2d4d7bf6d428`. Its pristine extracted `riskflow/`
was supplied to `--baseline`. Other trials did not acquire a starter; their unbaselined diagnostic
scores cannot be promoted into baseline-adjusted acceptance evidence. The operator's own preflight
download is not a substitute for the subject's acquisition.

**T2–T4 and T6 were not issued.** No trial produced a verified project on the v2 workflow to continue
from. Continuing the older-library applications would test a different target; providing a replacement
project, key or answer would require a new, explicitly scoped experiment. No injected-error results are
claimed. T5 is separately ineligible: the vendor catalogue entry has not shipped. No task hit its
wall-clock or build-cycle cap; these were client/target failures, not timeout successes.

## Scoresheets — unchanged output, then manual interpretation

The complete scorer output is preserved for every trial:
[1](evidence/coldstart-v2-2026-09-20/run1/scoresheet.txt),
[2](evidence/coldstart-v2-2026-09-20/run2/scoresheet.txt),
[3](evidence/coldstart-v2-2026-09-20/run3/scoresheet.txt),
[4](evidence/coldstart-v2-2026-09-20/run4/scoresheet.txt),
[5](evidence/coldstart-v2-2026-09-20/run5/scoresheet.txt),
[6](evidence/coldstart-v2-2026-09-20/run6/scoresheet.txt).
The public copy of trial 3 replaces one rule-1 term; public exports also trim trailing whitespace. Other scoresheet content is unchanged; the
**verbatim originals**, including trial 3, are in the durable local archive documented in the index.
The export manifest records original/published hashes and redactions.

| Trial | Parsed entries | Raw routed : unrouted | Important qualification |
|---|---:|---:|---|
| 1 | 2 | 1 : 1 | The routed item is the experiment's journal, not a starter document |
| 2 | 0 | 0 : 0 | No usable journal; zero fingerprints is not a pass |
| 3 | 13 | 4 : 7 | External examples drove the work; T-MAIN false negative |
| 4 | 14 | 10 : 3 | `(retro)` headings are skipped; fields can bleed into another entry |
| 5 | 17 | 11 : 5 | Genuine starter route, then legacy rewrite; four T-TRANSIENT false positives |
| 6 | 16 | 5 : 11 | No starter acquired; T-MAIN false negative and six non-auditing custom nodes |

Raw aggregate **31 : 27**, with three imitation entries and one missing `from:` separately counted.
This is not an adoption metric: incomplete sessions, retrospective entries, parser errors, external
references and experiment instructions all contribute. The published v1 control also gives no numeric
unrouted total, so a numerical reduction from v1 cannot be established from it. Do not infer improvement
from smaller totals in an unfinished battery.

## Manual audit and numbered findings

**CS-1 — The instrument/client pairing did not complete reliably.** Five provider refusals across both
model levels. Preserve these as instrument outcomes. Do not conclude that v2 failed five builds or that
another provider would behave identically. Initial operator setup failures are separately archived:
temporary-directory scope, nested sandboxing, loopback access, login keychain and directory metadata.
Fresh sessions/projects replaced those attempts. The final direct probes pass source/staged-answer
read denial, preview/public Maven HTTP, JDK/Maven execution and login. Some final subjects still used
unwritable `/tmp` paths or hit wrapper temporary-directory errors; those remain environmental friction,
not starter defects, and disqualify speed comparisons.

**CS-2 — A green T-MAIN fingerprint misses the actual trap.** The scorer checks only `.onEvent(new ...)`.
Trial 3's [OrderMonitorApp.java](evidence/coldstart-v2-2026-09-20/run3/project/src/main/java/com/example/ordermonitor/OrderMonitorApp.java)
lines 40–42 feed `sampleOrders()` through a variable; lines 88–100 construct the literal list. Its
scoresheet says T-MAIN `ok`. Trial 6 repeats the shape across
[RiskWatchMain.java](evidence/coldstart-v2-2026-09-20/run6/project/src/main/java/com/example/riskwatch/RiskWatchMain.java)
line 20 and [EventFeed.java](evidence/coldstart-v2-2026-09-20/run6/project/src/main/java/com/example/riskwatch/EventFeed.java)
line 19. **Manual T-MAIN: HIT in 3 and 6.** Reproduction: run the pinned scorer over either preserved
project, then inspect the feed source. No scorer mutation or fix was applied.

**CS-3 — The headline routing/time measures are not trustworthy without the transcript.** The scorer's
`ENTRY` regex requires a bare word at the end of the heading. Trial 4's `READ (retro)` and `DECIDE (retro)`
headings do not match. Their `from:` fields are then collected into the preceding recognized entry;
the dictionary keeps the last one. For example, the journal's first E1 says `operator`, but the parsed
E1 attributes a later external quickstart contract. The protocol permits marking retrospective work;
the parser does not safely handle this placement. Trial 3's journal claims 53 minutes while the client
records 4 m 27 s; trial 6's date-only timestamps yield zero elapsed time for a 7 m 8 s session.
These are instrument/data-quality findings, not evidence that the subjects completed work instantly.

**CS-4 — T-TRANSIENT flags correctly ignored fields.** Trial 5's four flagged maps/sets have
`@FluxtionIgnore` on the preceding line. Example:
[MarketData.java](evidence/coldstart-v2-2026-09-20/run5/project/src/main/java/com/example/riskflow/node/MarketData.java)
lines 14–15. The regex excludes annotations only on the field's own line. **Manual: those four hits
are false positives.** Trial 6's seven listed collection fields have neither marker nor `transient`;
that structural fingerprint stands, but the application was interpreted. No AOT serialization failure
was demonstrated by this run.

**CS-5 — A running application can conceal a missed starter journey.** Trial 3 never downloaded a ZIP;
trial 6 did the same. Trial 5 did follow `/build-with-ai` → `CLAUDE.md` → catalogue → scaffold →
`PROJECT.md`/runbook, and recorded imitation of `PricingDagBuilder.java`. It then migrated to older
dependencies. This is evidence about routing and prerequisites, not proof that the preview emitter
produced broken code. The catalogue already distinguishes local `keyNeed: run` and `build`, and reserves
its keyless wording for the browser Playground. Trial 5 knowingly chose a build-key template without
a key. Trial 6 explicitly read that interpreted local projects need a key. Their subsequent search for
older artifacts does not establish a false key promise. Subjects' claims that a repository is private
or that an artifact does not exist were not adopted as operator findings from one endpoint lookup.

Journal spot-checks cover at least 10% of parsed entries in each nonempty journal:

| Trial | Checked entries / evidence | Result |
|---|---|---|
| 1 | E1, E2 | E1 records an already completed journal read; E2 precedes its lookup. Experiment guidance must not count as v2 routing |
| 2 | All five tool calls; no parsed entries | Helper write outside scope failed; no journal-based routing result is usable |
| 3 | E2, E11; transcript raw lines 138, 178 | Actual source/example use agrees, but entries were written after reads/run; timestamps are fictitious |
| 4 | E1, E12; raw lines 76, 129 | Sources are observable, but retrospective batches and skipped headers corrupt the parsed attribution |
| 5 | E2, E7; raw lines 55, 84 | Bootstrap read was logged afterwards; completed E7 precedes the build attempt. Starter pointers were actually followed |
| 6 | E2, E14; raw lines 92, 270 | Source/intent is consistent with tools, but both are retrospective; E14 follows the test file and test execution |

This is ten checked entries out of 62, plus all of the empty-journal trial's actions. The transcript is
authoritative. A statement in a journal is not accepted merely because it parses.

Manual acceptance: trials 3 and 6 reached observable application results unassisted, but not on the v2
workflow. T3's per-parent mechanism, T4's independent report expectations/thresholds/dual triggers/empty
state/non-regression/mutations, and T6's evidence-before-edit behaviour are **NOT RUN**. No survivor is
classified as a scenario or check gap because no mutation experiment ran. Journal degradation is
observed, not silently treated as compliant recording.

## Frozen predictions scored

| Prediction | Observed result / disposition |
|---|---|
| T-MAIN recurs | Confirmed in the partial T1 corpus, manually in trials 3 and 6; scorer misses both |
| T-EVENTLOG recurs | Confirmed as a source fingerprint in trial 6's six custom nodes; these were hand-written outside the starter, not regenerated v2 stubs |
| T-RETURN-TRUE recurs | Unscored: the relevant signal/report task was not reached |
| T-TRANSIENT does not recur | Miss: trial 6 retains seven unmarked collection fields. It bypassed the starter and used older interpreted APIs; no AOT failure is inferred. Trial 5's four hits are excluded manually |
| T-TWOFEEDS does not recur | Unscored: no hosted multi-feed scenario was exercised; absence is not a successful test |
| T-NODEBEANS-JAR recurs | Unscored: T5 ineligible, as the pre-registered qualification stated |
| Routed : unrouted = 2 : 1 | Raw result is 31 : 27, below the forecast; no reliable acceptance ratio because the journal/parser and trial completion failed |
| Zero substantive interventions | Confirmed: none. The scorer's two `operator` entries refer to the initial preview URL, not substantive help |
| T4 takes longest | Unscored: T4 not run |
| v2 falls short of improvement bar | Bar not met, but this is not evidence that v2 is worse: the comparison is incomplete and partly unmeasurable |

The important misses are the assumption that this instrument would produce comparable routing data,
and that subjects would stay on a current starter after seeing the key requirements. The former failed
at client/parser/journal boundaries; the latter failed even where the requirement was visible. Neither
is repaired by declaring all static `ok` rows a success.

## Interventions, verbatim

**Substantive interventions: none.** No coaching message followed a valid task prompt. The initial
preview environment text, identical across the matrix, was:

> Environment for this preview rehearsal: the website base URL is http://127.0.0.1:5187 . Java 21 and Maven are installed. Work in the current directory. Network access to public dependencies and documentation is available. No compilation-service key is provisioned.

The environment is intentionally disclosed: this is not a fully public empty-directory test, and the
missing compilation key is a limitation of this rehearsal, not a defect in an accurately keyed template.
Operator setup repairs and stopped environmental attempts are recorded separately, not erased.

## For each remaining HIT: absent pointer or pointer not followed?

| HIT | What the evidence can establish |
|---|---|
| T-MAIN, trials 3/6 | A supported acquisition pointer exists in the preview's `CLAUDE.md`, but these subjects did not follow it into a project. They copied external examples or used priors. This does not prove a shipped project's harness guidance is absent |
| T-MAIN, trial 5's spike | A temporary diagnostic main after a genuine download; the later application rewrite uses its own feed path. Preserve the static hit without labelling it a final shipped-feed regression |
| T-EVENTLOG, trial 6 | No v2 project/entry was acquired, so project-specific guidance never reached the subject. Custom node source lacks audit writes. No evidence here about a v2 generator dropping an audit superclass |
| T-TRANSIENT, trial 6 | The subject bypassed starter guidance and authored unmarked collections using older interpreted APIs. The preview reference contains the ignore/transient rule, but this subject did not read that reference. Trial 5 read the reference and used the marker correctly |

There is useful positive evidence: trial 5 followed real bootstrap/runbook pointers, inspected the
catalogue's key metadata, copied a named shipped builder, and wrote auditable nodes with ignored state
collections. Those observations survive its eventual refusal. They still do not close the end-to-end gate.

## First change I would make

**Repair and validate the measurement harness before changing product guidance from these scores.**
Use observable tool/file events as the timing record, require only a short source pointer from the
subject, and test the scorer against this corpus: retro headers must not corrupt adjacent entries,
variable-fed literal harnesses must be detected or explicitly left to manual scoring, and preceding-line
ignore annotations must not be reported as missing. Do not seek private reasoning or rephrase a blocked
request simply to force it through; validate a revised action-recording protocol independently.

Prediction for that next instrument check: all hand-audited cases above match the scores, no elapsed
time depends on invented subject timestamps, and each client either completes a minimal action-recording
smoke test or is declared incompatible before a product trial. Provider compatibility remains to be
measured, not promised. After that, rerun the same starter task with an explicitly recorded, supported
prerequisite configuration and retain target migration as a failure to validate v2—not a successful pass
just because some older Fluxtion application ran. Product changes remain hypotheses until that run.

## Verification and outstanding work

Verified: filesystem/source/answer isolation probes; preview and public ZIP contents; actual supplied
prompts; baseline ZIP/hash; all six client transcripts and scorer outputs; source witnesses for both
scorer directions; the manual entry sample; runtime output in trials 3/6 and trial 6's five-test result.
Read: preview catalogue/guide/key declarations. Not verified: any current-v2 full workflow, Spring XML
reconciliation by these subjects, browser UI, analyser/MCP canvas use, public publication or T2–T6.

Repository gate: `mvn -q test` — 1,718 tests, zero failures/errors, 40 display skips. This verifies the
unchanged analyser tree, not starter acceptance. The evidence includes raw-score qualifications and
rule-1 redaction hashes; the durable local originals retain all unedited data for another operator.
