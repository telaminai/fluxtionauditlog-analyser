# Re-review — analyser tool agreement (`feat/tool-agreement`)

Reviewer: Claude Opus 5, 2026-09-21. Brief: [brief_rereview_tool_agreement_2026_09_21.md](brief_rereview_tool_agreement_2026_09_21.md).
Original review: [review_tool_agreement_2026_09_21_opus.md](review_tool_agreement_2026_09_21_opus.md).

| | SHA |
|---|---|
| Head re-reviewed | `d8cf52e7` |
| Response source / main integration | `41b77650` |
| Combined skill index | `b6633048` |
| Original reviewed head | `fcaf14ad` |
| End-marker revisions used | `02fa62b3` (rehearsed by the author), `955b90ed` (current head) |
| Playground companion | `telaminai/fluxtion-web` `fix/tool-agreement-skill-vendor` at `d917a7a` |

Worktrees: `/private/tmp/ta-rr` (gates, this report), `/private/tmp/ta-rr-probe` (store probes),
`/private/tmp/ta-rehearse-{02fa62b3,955b90ed}` (rehearsals), `/private/tmp/fw-rr` (playground). No
fix, merge, publish, deploy or client session. JDK 21.0.11.

## Verdict

**Mergeable, after one mechanical gate fix.** F1–F6 are closed, three of them with follow-ups. F7 is
withdrawn as my error. The response is thorough and its evidence reproduces.

The one red gate is `git diff --check`: seven trailing-whitespace lines inside the preserved evidence
patch (see N1). Exempt the file rather than editing evidence.

**One follow-up gates the next step, not this branch.** The recorded end-marker integration recipe is
correct for `02fa62b3` but **no longer compiles** against the end-marker branch's current head (F3).
It must be redone before the two branches are combined.

**Baseline counts, judged independently:** analyser **1 open** (D20, producer-blocked), upstream
**8 open**, unchanged. D6's closure is now sound (F1).

## Disposition

| Item | Disposition |
|---|---|
| F1 ordinary opens dropped the EOF record | **CLOSED WITH FOLLOW-UP** (N2, the cost of the Follow transition) |
| F2 skill index had to be re-pinned for integration | **CLOSED WITH FOLLOW-UP** (N3, a missing length rule in the playground test) |
| F3 overlap with the end-marker branch | **CLOSED WITH FOLLOW-UP** (the recipe is stale against the current end-marker head) |
| F4 stale D12 cause, unqualified D13 | **CLOSED** |
| F5 environmental display skip | **CLOSED** (CI log shows 50 run / 0 skipped) |
| F6 witness sites | **CLOSED** |
| F7 skip count not recorded | **WITHDRAWN: my error.** The reviewed `fcaf14ad` report states "49 display skips" at lines 335 and 345, and the tracker records it. I missed it. |

## F1 — reproduced

A probe on the branch build (`/private/tmp/ta-rr-probe`) used the shipped
`~/.fluxtion-analyser/demo/demo-quote-series.yaml`, a byte-exact 25-record Mongoose export (last record
unclosed), and the same records closed:

| File | heap | mapped | rolled (heap) | rolled (mapped) | EOF record included | **mutation: strict framing** |
|---|---|---|---|---|---|---|
| shipped demo | 726 | 726 | 726 | 726 | 1 | **725** |
| export layout | 25 | 25 | 25 | 25 | 1 | **24** |
| closed | 25 | 25 | 25 | 25 | 0 | 25 |

The mutation, which constructs through `HeapLogStore`'s strict path, loses the final record exactly as
the original finding said. The committed witness reports the same 25 against 24.

**Ordinary open → Follow → late fields → split separator:**
- A partial file opened as a snapshot holds 25 records. `forFollow()` returns a **separate** live view
  holding 24 with 1 pending.
- A quiet poll adds 0.
- A late `endTime` plus a half separator (`--`) still adds 0, with the record still pending.
- Completing the separator adds 1, and the published record **includes the late `endTime`**.
- The **old snapshot is untouched**: still 25 records, with its last record byte-identical.

## N2 · Medium · the Follow transition silently clears the person's flags

Starting Follow on a snapshot that includes an EOF record reopens it through the session gate as a live
read (`MainFrame.loadFile`, `heap.forFollow()` at :3462). By design, that clears record-bound flags,
selection and filters. It is documented, but only in `docs/site/faq.md:37`. **Nothing is shown when
the person clicks Follow.**

Every Mongoose export has an EOF record, and following an export is the M65 workflow. So a person who
flags records in an export and then presses Follow loses the flags without warning. The records before
the tail are byte-identical in the reopened view, so the flags could be carried across by record
identity, or the click could at least confirm. This is not a regression of F1's fix, and not a blocker.

## F2 — verified

- All seven `m19-skills/2` pins match their bytes both at the pinned revision `41b77650` (an ancestor of
  HEAD) and in the working tree.
- `main`'s `point-at-the-fault` and `add-a-node` are **byte-identical** to `origin/main`, and the
  Mongoose skill carries the new hash.
- Playground `d917a7a`: the five vendored skills match the analyser pins byte for byte.
- The manifest's provenance is `mirror:https://raw.githubusercontent.com/…/b6633048…/docs/skills@41b77650…`
  (170 characters). `current-status.md` says "prepared, not deployed".
- Tests: 37 files, **543 passed, 5 skipped**; the production build passes.

## N3 · Low · the playground provenance test omits one of ProjectProfile's rules

`ProjectProfile.skillsProvenance` (`config/ProjectProfile.java`, around :346) rejects a provenance longer
than **300 characters**. The playground test checks everything else in that grammar:
- `@revision` last, and the revision alphabet;
- no control characters;
- `mirror:` with an http(s) scheme and a host;
- no user-info, query or fragment.

It does not check the length. A longer mirror URL would pass the test and then be dropped by the
analyser. The current string is 170 characters, so nothing is broken today.

## F3 — the rehearsal reproduces, and it is now stale

- **Against `02fa62b3`, the rehearsal reproduces exactly as recorded.** Merging it into `b6633048`, taking
  ours for the four files in the JSON and applying the preserved patch leaves 0 unresolved conflicts. The
  focused gate passes, even extended with `StatusLineTest`, `RolledLogStoreTest` and
  `PublishedSpecExamplesTest`. The clean gate gives **1,806 run, 0 failures, 49 skipped**, the recorded
  number. Marker recognition, pending framing and ordinary EOF semantics all survive.
- **Against the current end-marker head `955b90ed`, the same recipe does not compile.** The conflict set
  is the same four files and the patch applies cleanly, but `StatusLineTest.java` (lines 25, 32 and 43)
  cannot find the symbols it tests. Taking ours for `MainFrame.java` discards the end-marker branch's
  round-3 and round-4 additions (`statusText`, and `streamEndFacts` with `member` nesting), and the
  patch, built against `02fa62b3`, does not restore them.

Nothing is claimed about main, and the record says so, so this is not a false claim. But the recipe must
be regenerated against whichever end-marker head is actually merged. As written, it would silently
delete that branch's latest fixes.

## F4 — verified

- `spec-tool-agreement.md:78` (D12) now names the observed cross-thread `ThreadingIllegalStateException`.
- `:79` (D13) says the later endpoint test did not reproduce the staleness, and marks it an unresolved
  observation. The row stays open, so upstream remains 8.
- The Mongoose skill's D12 and D13 bullets match.
- No server or client session was run, per the brief.

## F5 — verified

- CI run 35610006347 on `b6633048`: `ui-frame`, `loop-bench` and `build` all succeeded, and the job log
  itself reads **"Tests run: 50, Failures: 0, Errors: 0, Skipped: 0"**.
- The commits after that head (`b6633048..d8cf52e7`) change no source or build files.
- My local display run also gave 50 run with 0 skipped this time.

## F6 — verified

`d16-mutation.json` and `d18-mutation.json` now carry `sourceSite` entries, with reviewed-revision lines:
`MarkerExtractor.java:90` (the STRICT `carry.clear()`) and `ChartPanel.java:417` (the second
occurrence, in the `setViewWindow` axis partition). They match the sites I used to reproduce both
mutations.

## N1 · Low (gate) · `git diff --check` is red on the evidence patch

`git diff --check $(git merge-base HEAD origin/main)..HEAD` exits 2, with all seven hits in
`docs/handoff/evidence/tool-agreement-2026-09-21/end-marker-integration-resolution.patch`. Those are
added blank or context lines that carry trailing spaces by nature. The brief forbids editing evidence,
so exempt the file (for example, `*.patch -whitespace` in `.gitattributes`). The end-marker branch has
the same class of issue with its real-export fixture.

## Gates (run by me on `d8cf52e7`)

| Gate | Result |
|---|---|
| `mvn -q clean test` | exit 0 · **1,767 run, 0 failures, 0 errors, 49 skipped** |
| display set (11 frame classes, `-Djava.awt.headless=false`) | exit 0 · **50 run, 0 failures, 0 skipped** |
| `mvn -q package -DskipTests` | exit 0 |
| `python3 tools/test_tools.py` | "all passed" |
| `python3 tools/verify-m64-spotlight.py` | "all M64 spotlight checks pass" |
| `mkdocs build --strict` | exit 0 |
| `git diff --check` (merge-base..HEAD) | **exit 2**, N1 |
| rule-1 sweep (exact tracked-file command) | prints nothing; this report checked before commit |

## Run versus read

- **Run:** all gates above; the F1 store probe and mutation; the Follow sequence; the F2 pin and
  byte comparisons, both repositories; the playground tests and build; both F3 rehearsals, with the clean
  gate on the reproduced one; CI log retrieval.
- **Read:** the response; the D12/D13 wording; the FAQ and the transition code (N2); ProjectProfile's
  grammar (N3); the witness `sourceSite` fields.
- **Not done, by instruction:** endpoint or client sessions, deployment.

## Handoff to the author

1. N1: exempt the evidence `.patch` from whitespace checks.
2. F3: regenerate the integration recipe against the end-marker head you will actually merge. Against
   `955b90ed`, "take ours" for `MainFrame.java` drops `statusText` and the `member`-nested
   `streamEndFacts`.
3. N2: confirm or preserve flags across the Follow reopen, or at least say so at the moment of the
   click.
4. N3: add the 300-character rule to the playground provenance test.
