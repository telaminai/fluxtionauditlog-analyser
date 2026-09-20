# Cold-start v2 preview evidence — 2026-09-20

**T1 matrix completed; the full T1–T6 acceptance battery is not completed. No v2 acceptance or release gate closes.**
Read the [operator report](../../report_coldstart_v2_preview_2026_09_20.md) before interpreting a green fingerprint.

The owner selected a branch-preview rehearsal after the public Spring download was found to lack the
v2 entry/profile/runbooks. The website revision was `47b9952`, analyser `c06742c` before evidence-only
commits. No private compiler build was supplied. No implementation was changed to make a trial pass.

Protocol: [HARNESS at 8c488c8](https://github.com/telaminai/fluxtionauditlog-analyser/blob/8c488c8/docs/proposals/beta-testing/coldstart/HARNESS.md),
[cold-start prompts](https://github.com/telaminai/fluxtionauditlog-analyser/blob/8c488c8/docs/proposals/beta-testing/coldstart/PROMPTS-COLDSTART.md).
The exact copied files are identified by [protocol-manifest.json](protocol-manifest.json). The scorer
and task prompts were not changed. [Predictions](OPERATOR-JOURNAL.md) were frozen before setup; commit
`0682585` published them before this six-trial matrix.

## Files and reading order

Each `run1` through `run6` directory contains the exact supplied input, operator timestamps/model alias,
subject journal, scorer JSON/text, and a visible-event transcript with `rawLine` pointers into the original
client JSONL. Runs 3, 5 and 6 also preserve application source for checking static findings.

| Trial | Model observed | Scoresheet | Transcript |
|---|---|---|---|
| 1 | claude-sonnet-5 | [score](run1/scoresheet.txt) | [events](run1/transcript.events.jsonl) |
| 2 | claude-sonnet-5 | [score](run2/scoresheet.txt) | [events](run2/transcript.events.jsonl) |
| 3 | claude-sonnet-5 | [score](run3/scoresheet.txt) | [events](run3/transcript.events.jsonl) |
| 4 | claude-opus-5 | [score](run4/scoresheet.txt) | [events](run4/transcript.events.jsonl) |
| 5 | claude-opus-5 | [score](run5/scoresheet.txt) | [events](run5/transcript.events.jsonl) |
| 6 | claude-opus-5 | [score](run6/scoresheet.txt) | [events](run6/transcript.events.jsonl) |

Public transcripts omit thinking/signature blocks and provider telemetry. Rule-1 terms are replaced by
numbered markers. The [export manifest](export-manifest.json) records raw and published hashes and every
affected file. The public export also removes trailing whitespace and terminal blank lines. **The unedited, verbatim originals are available
locally** under `.local-evidence/coldstart-v2-2026-09-20/operator/results/` in the analyser checkout. That
git-ignored directory also retains the exact protocol, runner, complete client transcripts, discarded
environmental attempts, ZIPs and extracted baselines. `subjects/` retains final projects and dependency
caches. This archive is outside `/tmp`; it is not included in a clone or the public commit.

## Scope and isolation

Every subject got a fresh session, its own directory, the unmodified journal and verbatim T1. The
additional preview-only environmental text is in each `input.txt`: preview origin, Java/Maven, writable
current directory, public network availability and **no compilation-service key**. This is an explicitly
keyless local environment, not evidence that all catalogue entries promise to work without a key.

The final OS profile denies source checkout contents, the staged sample and operator materials, as
recorded in [the probe](final-isolation-probe.log). It allows directory metadata needed by the toolchain.
No MCP/IDE tools, prior client history, resumed sessions or compilation keys were supplied. Only the
filtered preview origin was reachable among local services; source-serving routes were refused. The
subjects had shell/file tools and public HTTP access. No browser was driven.

The public download and preview were checked separately:
[public preflight](public-endpoint-preflight.json), [preview preflight](preview-endpoint-preflight.json).
Those operator downloads do **not** count as subject acquisitions.

The proxy preserved each returned ZIP before the subject could edit it. The first three entries in
[acquisition-requests.jsonl](acquisition-requests.jsonl) belong to discarded environmental attempts.
Only the final entry belongs to this matrix: trial 5's `riskflow` download, SHA-256
`2c669208b8794c612764a087cbe9454533860133b5864688928b2d4d7bf6d428`. Its extracted `riskflow/` is the
baseline used by the scorer. Trials 1–4 and 6 did not acquire a ZIP: their explicitly unbaselined
scoresheets are diagnostic output, **not valid baseline-adjusted acceptance scores**.

## Interpretation constraints

- Five client sessions ended with a provider `reasoning_extraction` API refusal. Its cause was not
  established; the payloads and outcomes are preserved. This is not evidence of a starter defect.
- Trials 3 and 6 produced application output on older dependency lines. Only trial 3 ended normally;
  trial 6 emitted output and five passing tests before its provider refusal. Neither validates v2.
- Journal timestamps and several pre-action claims disagree with client event timestamps. Use the
  transcript and `meta.json`, not the scorer's elapsed time. The manual audit is in the report.
- Static `ok` can mean “not exercised” or “pattern missed”; static `HIT` can be a false positive.
  The report records both directions. No fingerprint was silently corrected in the scoresheets.
- T2–T4 and T6 were not run: no trial produced a verified project on the v2 workflow to continue from.
  T5 is separately ineligible because the vendor catalogue entry has not shipped. No mutations ran.

The analyser regression gate run for these documentation/evidence changes was `mvn -q test`:
1,718 tests, zero failures/errors, 40 display skips. This is a repository gate, not a cold-start result.
