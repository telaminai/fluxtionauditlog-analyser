# PR #70 re-review — the fixes for review findings 1–9

Reviewed `3330f9f2..0624fe30` (11 commits) on `feat/evidence-bundle-replay`, 2026-09-29, against the review
[`review_pr70_evidence_bundle_replay_2026_09_28_codex.md`](review_pr70_evidence_bundle_replay_2026_09_28_codex.md),
its probes, the implementer's predictions (`d02f6faf`), spec §10c and the response on PR #70. The response and §10c
were treated as claims. Evidence: [`evidence/pr70-rereview-2026-09-29/`](evidence/pr70-rereview-2026-09-29/README.md).
No merge, rebase, force-push, release, key, provider, LLM trial or `-Pregen` was used; nothing in the implementation
was edited.

**Verdict: merge after named corrections.** Findings 4, 5, 6, 7 and 9 are fixed; 1 and 2 are partly fixed; 8 was
blocked for the implementer and this review supplies the native captures; and the fix for finding 3 works as
reported but introduced a new defect: a recipient build generated **with tracing on** now falsely
DIVERGES from its own bundle (C1 below). That, and adopting native screenshots (C2), are the corrections. The
SHOULD items can follow in M70.R6.

Environment: macOS arm64, Corretto JDK 21.0.11, Retina display with Screen Recording (native capture available).
RAN = executed by this review; READ = source inspection; REPORTED = the implementer's claim, not re-run.

## Corrections before merge

**C1 — REQUIRED, new defect from `7621157d`: a tracing build diverges from its own bundle.** The commit removed the
set-up text filter on the claim that "the runner's set-up emits no audit record into the sink on runtime 1.0.16".
That holds only for builds generated with tracing off, as the DEMO is (`DemoQuoteRecordedProcessor.java:173,176`).
`EventLogManager.processingComplete` writes a record when `canTrace | terminateRecord()`, and the runner's own
`setAuditLogProcessor` control event is processed after the sink is swapped. RAN, same bundle, same runner (head):

| build | records written | `--replay-compare` |
|---|---|---|
| committed DEMO (trace off) | 8 | AGREES, 8 of 8 |
| the same DEMO with `trace = true; traceLevel = INFO` | 9, one `EventLogControlEvent` | **DIVERGES at record 0**: `eventTime: '1767258000060' ≠ '-1'` |

With `7621157d^`'s runner the tracing build AGREES 8/8 (RAN by the finding-3 check). The divergence text then tells
the recipient their build behaves differently, which is false. The "equivalent mutant" the response cites was
equivalent only because no test uses a tracing build; `LiveRecordingDriver` discards set-up output
(`audit.setLength(0)`). Fix: install the sink without emitting a control event through the processor (set the log
sink on the `EventLogManager` auditor directly), or discard only what is written before the first replay input, and
add a regression with a tracing-on build and a control that fails it. Probe: `tracing-build-probe.sh`.

**C2 — REQUIRED (the merge condition recorded in `4ac1552d`): native screenshots.** The four `bundle-conv-*.png` at
head are unchanged since `4ac1552d` ("PAINTED … regenerate natively before merge"): 1680×998, no title bar. This
review ran `python3 tools/capture-bundle-conversations.py` under the isolated DEMO home on a Retina display: exit 0,
four window captures, 3360×2100, each with the window title bar. I opened and read every one: DEMO data only
(`demo-quote-project`, `/tmp/analyser-docs/…`, `DEMO-A`, `ord-2`), no other window, no personal path; each matches
its caption (step 2 of the walk on the 09:00:00.180 breach; the recorded run's 8 records; the received bundle's walk
on the breach; record 6 lit with its risk-monitor entry). They are in `evidence/…/native-bundle-conv/` with the page
regenerated in the same run. Adopt the four images **together with that page**: `bundle-conv-received.png`'s title
bar shows its bundle identity (`bundle-f5b4dbcb0bc6…`), which the page quotes; the prose is otherwise byte-identical
to head (only identities and JSON key order differ).

## Findings 1–9

| # | Finding | Verdict |
|---|---|---|
| 1 | comment moved the header scope (false AGREES) | **partly fixed** |
| 2 | aggregate memory / cardinality | **partly fixed** |
| 3 | a record dropped for its text | fixed as reported; **new defect C1** |
| 4 | malformed or short replay reaches the processor | fixed |
| 5 | guidance stronger than the checks | fixed, nits |
| 6 | RB-2 identity guard had no live regression | fixed |
| 7 | strict JSON vs the analyser's laxer reader | fixed |
| 8 | native screenshots | not done by the implementer (permission); **done by this review, see C2** |
| 9 | `.gitattributes` whitespace exceptions | fixed |

### 1 — header scope (`a30ad7bb`): partly fixed

- Failing-first RAN: with `a30ad7bb^`'s `ReplayCompare.java`, `aCommentDoesNotSetTheHeaderScope` fails by a named
  assertion (`:183 expected <…priceListener.thread: 'DEMO-old' ≠ 'DEMO-new'> but was <null>`). Control
  `rc-a-comment-sets-no-header-scope` caught at that assertion; it restores the reviewed defect, so it is meaningful.
- Holds (RAN through the real `--replay-compare`): header `endTime`/`thread` changes still AGREE; nested
  `thread` changes DIVERGE in block style, flow mapping `{ thread: … }`, after a block scalar, after `...`, and with a
  tab before a deep line. Quoted keys, `thread :`, a tab-indented header line and a record with no top-level key fail
  safe (false DIVERGES, never false AGREES).
- **Still moves the scope (SHOULD).** `fieldIndent` (`ReplayCompare.java:121-129`) takes *any* indent-0 line as the
  top-level key, then the first indented non-comment line as the field indent; it never anchors on
  `eventLogRecord:`, contrary to its javadoc (`:115-120`). Each of these exits 0, `AGREES 8 of 8`, although
  `nodeLogs.priceListener.thread` changed `DEMO-old` → `DEMO-new` (record 0's own `endTime` equal):
  a second top-level key first (`demoPreamble:` + an indented line, then `eventLogRecord:`); an indented
  non-field first line under `eventLogRecord:`; a list item `- DEMO item` as the first line. The same shapes also
  stop excepting the record's own `endTime`/`thread` (false DIVERGES). The producer does not write these shapes, so
  this is a contract gap rather than a demo failure. Fix: except only the key paths `eventLogRecord.endTime` and
  `eventLogRecord.thread`, anchored on the `eventLogRecord:` line; add the three shapes as regressions.
- **Pre-existing, outside the delta (SHOULD, or state as a limit):** runtime 1.0.16 writes a logged String's newline
  raw (RAN: `addRecord("priceListener","note","DEMO line one\n    thread: DEMO-old")` prints a line `    thread:
  DEMO-old, …`). A business value can therefore put a line named `thread`/`endTime` at the field indent, and a
  change to it is excepted: AGREES 8/8 on a changed value (RAN).
- The comment skip added to `path()` (`:178`) has no control; removing it leaves `ReplayCompareTest` 13/0. It
  changes only the name printed with a divergence. Nit.

### 2 — bounded memory (`c40a41b4`): partly fixed

RAN, every attack JVM `-Xmx64m` with a timeout, fresh `java.io.tmpdir` and output directory inspected afterwards.

- Refused by name in ~0.1 s, nothing left behind: JSON nested 100k deep (limit 32, `:537`); a 2 MiB replay line;
  a member declaring 10 bytes that inflates to 1 GiB (`BoundedStream`, `:430-433`); a truncated archive; a duplicate
  entry; a 64 MiB unlisted entry placed first (never read). The review's 12×6 MiB case: `the bundle lists 13 replay/
  members, not one`, no OOM, no output.
- Accepted within limits, clean afterwards: 30,000 tiny listed members (0.7 s), a ~4 MiB manifest, 300 records just
  under 1 MiB each (7.6 s).
- **The graph is not bounded in memory (SHOULD).** A 65 KiB bundle whose listed, correctly hashed graph is ~32 MiB
  of `<node id="n"/>` (within `MAX_GRAPH_BYTES` = 32 MiB, `:251`) ends in `OutOfMemoryError` at 64, 256 and 512 MiB
  of heap; it parses only at 1 GiB (then refused as a graph mismatch). `members()` holds it (`readAllBytes`, `:374`)
  and `graph()` builds a DOM (`:678`). `run()` catches `Exception` only (`:84-92`), so the error escapes as a stack
  trace, not a named refusal. Cleanup still ran. Fix: a far lower graph bound or a streaming (StAX) parse, and a named
  refusal for exhaustion.
- Directory entries are skipped (`:358`) but still inflated and unbounded by the manifest: a 2 MiB bundle with a
  `pad/` entry of 2 GiB of zeros takes 1.8 s in the runner and in `--verify`. Time, not memory. Nit.
- Cleanup: tmpdir and output clean on every refusal and after the OOM. `kill -9` mid-replay leaves the spool and a
  `.part` (proportionate). `Files.move(part, out)` (`:229`) sits outside the block that deletes `.part`; if `out`
  appears between the check (`:141`) and the move, the `.part` remains. Nit.
- Failing-first RAN: with `c40a41b4^`'s runner and reader, `ReplayRunnerEndToEndTest` 5/5/0/0 and
  `bothReadersRequireTheWholeGrammar` 1/1/0/0, all `AssertionFailedError` (three with the OOM in the message).

### 3 — dropped record (`7621157d`): fixed as reported, new defect C1

The payload record is kept and a same-build DEMO replay AGREES (RAN). With `7621157d^`'s runner,
`anInputNamingTheControlEventKeepsItsRecord` fails by a named assertion (`:569`, "the first input's record was
dropped for its text"). `rn-no-record-is-dropped-for-its-text` re-adds the filter and is caught there: meaningful.
The claim that set-up emits nothing is true only for non-tracing builds: correction C1.

### 4 — pre-execution reading and the count (`c40a41b4`): fixed

RAN: missing separator, one record short or extra, a count as `"7"`, `-1`, `7.5` or `99999999999999999999`, a
nameless component, trailing text, a bad final time line, invalid UTF-8 and a string ending in a backslash are all
refused before the processor runs, with no output file. The DEMO reader refuses the same grammar cases. CRLF, a BOM
and `7.0`/`7e0` counts are accepted by design. Nits: the count check is skipped when the manifest has no
`records` (`BundleWriter.java:187` always writes it and the digest binds the member), and `--verify` then prints
"the run's null recorded inputs"; the response's "must equal" means "when present". Invalid UTF-8 is reported at
line 1 (decoder read-ahead).

### 5 — guidance (`a48efb5a`): fixed, nits

The regenerated page and the script now say pairing is consistency evidence, not identity; that no service calls is
not completeness; that a graph match is wiring, not the same code; and that an agreement is evidence for these
inputs. The content-pairing description is accurate for what `ReplayPairing` does: the replay record is rebuilt in
the record `toString` form and compared with the log's printed event (`ReplayPairing.java:105-113,152-175`), and an
event the log does not print is matched by type and instant and counted as such; "whose printed event is exactly
the recorded one" is right for this DEMO run, where all seven are printed. Nits, none blocking:
`capture-bundle-conversations.py:309` still says **"The graph is the same, so it replays"**; the analyser's own
output line and the script say **"the run's 7 recorded inputs"** (`with-an-assistant.md:148,155,175,182,217`), a
provenance phrasing capture does not establish; and `EvidenceBundleDocsTest`'s guard is an exact-phrase deny-list,
so neither of these trips it. Suggest "the graph matches" and "7 recorded inputs".

### 6 — RB-2 regression (`33738275`): fixed

READ: `ReplayFixtureTest#onlyTheExternalObjectIsRecorded:127-142` → `LiveRecording.run` compiles and runs
`LiveRecordingDriver.java` against the built DEMO: the committed generated `DemoQuoteRecordedProcessor`, its
`ReplayCapture` taken with `getAuditorById`, `writer.expect(e)` then `p.onEvent(e)` → `handleEvent(RiskBreachEvent)`
(`:381`) → `auditEvent` (`:481-483`) → `replayCapture.eventReceived`, guarded at `ReplayCapture.java:66`. The graph's
own breach re-enters `onEvent` unexpected. This is the real consumption path. RAN: `rq-records-only-the-named-object`
is caught at `:136` (expected 8), not an equivalent mutant.

### 7 — strict JSON (`c40a41b4`): fixed

RAN, 21 spellings through the runner and `analyser --verify`. **No manifest is accepted by both and read
differently.** Both accept and read alike `3375e0`, `3375.0`, `"format":2.0` and `/` in a path. The runner
refuses what the analyser reads laxly (a duplicate key, trailing content, `.5`, `+`, a leading zero, `-0` records,
format `2.5`, unknown escapes, a raw tab, `"7"`). The runner accepts two spellings `--verify` refuses (a BOM;
member paths `notes/a:b` and `notes//x`), so the runner can replay a bundle `--verify` rejects; harmless while the
runner writes no member by path, but worth aligning in M70.R6. Both accept `\u+02f` (`Integer.parseInt` at `:622`).
**Pre-existing, outside the delta:** the analyser's `llm/Json.java` recurses without a bound; a 5 KiB manifest of
1M `[` ends `--verify` in an uncaught `StackOverflowError` (`EvidenceBundle.check` catches `RuntimeException`
only). Belongs with the shared strict reader of M70.R6.

### 8 — native screenshots

See C2. The implementer's report (blocked, images unchanged and painted) is confirmed: hashes and `4ac1552d` as
last commit.

### 9 — whitespace (`10a8e256`): fixed

Exactly two single-file `whitespace=-blank-at-eol` lines, no globs: `demo-quote-recorded-audit.yaml` and
`demo-quote-recorded.replayed-audit.yaml`. Each has 16 trailing-space lines (8 `eventLogRecord: `, 8 `nodeLogs: `),
no tabs, identical to fresh runtime 1.0.16 output (RAN), from `f6dadbad` (GenerateFixtures). `rf-the-producers-
bytes-are-kept` caught (`expected <8> but was <7>`). Nit: `theProducersBytesAreKept:151` checks that the narrow line
exists but not that no broader exception does, despite "and only that".

## Controls

RAN, fast engine, all 51 `rp-` (12), `rc-` (9), `rn-` (20), `rq-` (6), `eb-format1` (1), `ax-` (1), `dg-` (1) and
`rf-` (1) cases taken from the registry, not from the response: **51 requested, 51 caught**, the name lists
identical, every mutant red by `failure` (no error or skip), every source and class restore byte-identical, 88.6 s.
Three are near-equivalent: `rn-an-unlisted-member-is-never-read`, `rn-a-component-needs-its-name` and
`rq-demo-reader-a-component-needs-its-name` change only the refusal's text (a `NullPointerException` or
`StringIndexOutOfBoundsException` refusal, still before anything runs). They guard the message, not the behaviour.

## Gates at `0624fe30` (RAN, JDK 21)

| Gate | Result (total / failures / errors / skips) |
|---|---|
| `mvn -o -q test` | 2871 / 0 / 0 / 170, 380 reports |
| `EvidenceCaptureFrameTest`, real display | 15 / 0 / 0 / 0 |
| `mvn -o -q package -DskipTests` + `tools/evidence-bundle-demo.py` | ok; 57 passed, 0 failed |
| the review's `reproduce.py` | every probe meets its corrected expectation (below) |
| fast-engine controls | 51 requested, 51 caught |
| `mkdocs build --strict` | clean |
| `git diff --check v1.27.0...HEAD` | empty |
| rule-one sweep, tracked files and delta additions | empty; 11 commits by the personal address |

`reproduce.py`: normal and pretty manifests replay 8 records and AGREE 8/8; nested comment DIVERGES at record 0
naming `nodeLogs.priceListener.thread`; payload 8 records; missing separator and duplicate fields refused;
aggregate memory `the bundle lists 13 replay/ members, not one`, no OOM, no output or `.part`; the live recorder
records 8 and the identity mutant is caught.

## CI run 36492046613

Attempt 1, shard 3: for `mouse-loss-column-adjustment` the mutant failed by its named assertion, and the **byte-restored run was skipped**, not red:
`Assumption failed: native mouse press must reach the table, not be dropped by the desktop`. The gate counts a
restored skip as not-restored, as it should; the response's "red on the byte-restored source" is inaccurate. **No
causal link to this delta:** it does not touch `LogTablePanel.java`, the test, or the mutation engine; its new
registry entries only move this control to position 66 of 67 in shard 3. The cause is the native test's reliance
on a Robot press landing under Xvfb, the known weakness of that family (timer, hook, column-adjustment). Attempt 2
was green (REPORTED; the job list confirms success). Fixing that family belongs to the frame test, not to PR #70.
