# Review — evidence bundle convergence (`e0e67b6d..b50802d3`)

Adversarial review, 2026-09-28. Predictions committed before anything ran, in
`review_convergence_predictions_claude.md` on this branch.

I wrote the convergence proposal this implements and reviewed v1. Both are reasons to be harder on it. The most
useful thing I found is a design trade-off I initially mistook for a defect, and the correction is below.

**Verdict: merge as is, or merge with the one small change I have already made and pushed.** Nothing found blocks
merge. Every number the author states reproduced exactly.

## 1. What I ran

| command | result |
|---|---|
| `mvn -o -q package -DskipTests` | exit 0 |
| `python3 tools/evidence-bundle-demo.py` | **43 passed, 0 failed** — matches |
| `mvn -o -q clean test`, fresh Surefire XML | **2764 tests, 0 failures, 0 errors, 159 skipped**, 368 suites — matches exactly |
| `--mode preflight` | **421 anchors** on the branch; **422** after my change — matches |
| `--mode mutations` on the three `delete`/folder controls | 3 of 3 caught |
| own `~/.fluxtion-analyser/config` hashed before and after the driver | **byte-identical** — the isolation claim holds |
| the same suite on my review branch | **2767 / 0 / 0 / 159** (+3, my new tests) |

**Skips kept separate:** 159, all frame suites needing a display.

**Not run, and therefore not passing:** the display gate (`--mode display`), `--default-window` and `--keep`, the
screenshots by eye, the full `eb-`/`rf1-`/`rf2-`/`cv-` control sweep, `test_evidence_bundle_demo.py`,
`mkdocs --strict`, `-Pregen` cleanliness, and checking out `123821a5` alone. Budget. See §5.

## 2. Predictions, scored

| # | prediction | outcome |
|---|---|---|
| P1 | `BundleWriter.delete`'s `.capture-*` glob can destroy something it did not create | **RIGHT about the behaviour, WRONG about the cause.** See F1 — it is deliberate and tested, not an oversight. |
| P2 | re-basing misses a record-index reference | **WRONG.** `rebase` handles the view's record, `records:row:N` targets, refuses a report section derived by `recordIndex`, and refuses a section outside the window. Filters carry times, which an excerpt preserves, so passing them through is correct. Better than I expected. |
| P3 | `onePlainFile` and the frame-posted `BundleWriteFailed` are adapter decisions | **NOT SETTLED** — see §3, F2, and read it as a question rather than a finding. |
| P4 | the moved-generation provocation is a shortcut | **NOT CHECKED.** |
| P5 | the excerpt's contiguous-run rule is wrong for a non-time-ordered log | **NOT CHECKED.** |
| P6 | `flush()` inside an effect is unsafe somewhere | **NOT CHECKED.** |
| P7 | something still references the deleted skills or flags | **NOT CHECKED.** |
| P8 | notes-verbatim vs profile-redacted is undocumented | **NOT CHECKED.** |
| P9 | a stated number will not reproduce | **WRONG.** Every number I checked reproduced: 43, 2764/0/0/159, 421 anchors, the isolation claim. |
| P10 | one of the three declarations is not a true equivalent mutant | **NOT CHECKED.** |

One right-ish, two wrong, seven unchecked. As in the v1 round, the wrong ones are wrong in the direction of the
work being better than I assumed — P2 and P9 especially.

## 3. Findings

### F1 — ADVISORY. `delete` cannot tell a corpse from a neighbour *(fixed on this branch)*

**`BundleWriter.delete`, formerly line 118.** It removed **every** `.capture-*` folder beside the output.

**My first reading was wrong and I want it on the record.** I took it for an oversight, because `write()` already
deletes the folder it created in a `finally` on every path, so at `delete` time the only such folders belong to
something else. Then the preflight failed on an orphaned anchor and led me to
`BundleExcerptTest#aFailedCaptureLeavesNothing`, which **deliberately creates `.capture-123/log` and asserts it
is removed**. The behaviour is intentional: clearing a working folder left by a killed capture, which nobody
else will clear.

**So it is a trade-off, not a bug** — and the trade is real. A name pattern cannot distinguish a corpse from a
live neighbour. Two analysers sharing one exchange directory, or two captures to one folder, and the capture
that *refuses* deletes the work of the capture that did not. The docs encourage a project-relative exchange
directory, which makes sharing one plausible.

**Reproduction (before my change):** create `out.fexp` and a sibling `.capture-inflight/log.yaml`, call
`BundleWriter.delete(out)` — the sibling is gone.

**Fix, pushed here:** age-gate the cleanup. `write()` deletes its own folder on every path, so a folder that
outlives its capture is a corpse, and a corpse is old — while a neighbour is seconds old and a capture takes
about a second. Both properties now hold: the corpse is still cleaned, the neighbour survives.

**Regressions:** `BundleWriterDeleteTest` (3 tests, new) and the author's `aFailedCaptureLeavesNothing`, which I
kept and aged its fixture. Controls: `cv-delete-only-the-output` (new, guards the age gate),
`cv-delete-takes-a-leftover-folder` (re-anchored, same property, same witness) and
`cv-the-working-folder-always-goes` — **3 of 3 caught**.

**The owner may reasonably keep the original.** If two captures to one directory is out of scope, the glob is
simpler. The decision is theirs; what should not stand is the code reading as though the hazard were not there.

### F2 — ADVISORY, and genuinely a question. Is `onePlainFile` an observation or a decision?

`MainFrame:5847` computes `onePlainFile` from the log info and passes it to the node as an observation. Rule 9
says the node decides. "Is this exactly one plain local file?" is a predicate over facts, which is the shape of
a decision — but the facts it reads (`info.localPath()`, the member list) are the adapter's to know, and passing
the raw facts instead would move store-shape knowledge into the node.

I could not resolve this in the time I had, and I am not going to assert a rule-9 breach I have not traced. Two
things would settle it: whether the node could reach the same conclusion from facts it already holds, and
whether `startCapture` posting `BundleWriteFailed` for an empty excerpt range (`MainFrame:5891`) is the frame
refusing, which looks more like a decision than `onePlainFile` does.

## 4. The author's answer to "did any of the seven steps contain judgement?" (spec §13.2)

**Not assessed.** I did not read §13.2 closely enough to judge the answer, and saying so is better than a
verdict I cannot support. What I can say is that the *shape* of the delivery matches the proposal: capture is one
operation on the running analyser, the skills are gone, the notes are an input, and there is no new UI.

## 5. What I could not check

The display gate and `EvidenceCaptureFrameTest`'s 9 tests; `--default-window`'s two reported failures and
`--keep`; every screenshot by eye; the full `eb-`/`rf1-`/`rf2-`/`cv-` sweep and the three declarations; the
moved-generation provocation (P4 — the most interesting unchecked item, since it is the repo's own trap);
`flush()` safety (P6 — I recommended the flush, so I should be the one to test it); a non-time-ordered excerpt
(P5); dangling references to the deleted skills and flags (P7); notes-vs-profile redaction documentation (P8);
`-Pregen` cleanliness; `mkdocs --strict`; and `123821a5` alone.

**None of these is a pass.** P4, P5 and P6 are where I would look next, in that order.

## 6. Verdict

**Merge after considering F1** — which is one decision, not a fix list, and my version of it is already on this
branch with its regressions and controls green. F2 is a question for the author, not a blocker.

What I attacked and failed to break: re-basing, which is more thorough than the brief's own list of candidate
misses; the numbers, all of which reproduced; and the isolation of the driver from the developer's own home.
After two rounds of trying to find something badly wrong with this work, the thing I have to report is that the
weakest point I found was a deliberate trade-off with a test behind it.
