# PR #70 review — implementer's predictions, recorded before the fixes

Written against `01e9907b`: the reviewed head `faf0fd2c`, two fixes made from the owner's first brief
(`caebf31f` content pairing, `abc55322` strict runner grammar), and the independent review commit `f5eafc4a`
cherry-picked unchanged. The baseline is the review's own `reproduce.py`, run at that head. Each line below states
what it showed, and what I predict it shows after the fix. The response on PR #70 compares these with what happened.

| # | Probe / check | Baseline at `01e9907b` | Predicted after the fix |
|---|---|---|---|
| 1 | `nested-comment-compare` | exit 0, AGREES 8/8, one record excepted | DIVERGES at record 1, naming `nodeLogs.priceListener.thread`; a header-only `thread` change still AGREES |
| 2 | `aggregate-memory` (`-Xmx64m`, twelve extra 6 MiB `replay/` members) | `OutOfMemoryError` | exit 1, a refusal naming the second `replay/` member, no OOM and no output file |
| 3 | `payload-runner` (symbol `DEMO event: EventLogControlEvent`) | 7 audit records written, diverges at record 0 | 8 records written, `--replay-compare` AGREES; the runner's own setup record is still excluded |
| 4 | `missing-separator`, `duplicate-fields` | already refused by the runner (`abc55322`); the probe treats the DEMO reader's refusal as an uncaught exception | unchanged refusal in both readers, before any processor runs; a declared/`records` count mismatch also refused |
| 5 | assistant page and `capture-bundle-conversations.py` | "cannot be from another run"; no service calls read as nothing missing | bounded wording (consistency evidence, not identity; no service calls ≠ complete); a docs test fails if the stronger wording returns; graph check worded as node ids and edges |
| 6 | identity-guard mutant (`event != expected` removed) | 47/0/0/0 in `Replay*Test`: survives | a new live test drives an external and a graph-raised `RiskBreachEvent` through the generated processor; the mutant fails it by a named assertion (9 recorded ≠ 8) |
| 7 | `pretty-runner` (pretty-printed manifest) | exit 1 "graph/DEMO.graphml is not listed" | replays; compact, reordered and pretty manifests all replay; malformed JSON refused |
| 8 | four `bundle-conv-*.png` | painted previews | native captures under the isolated DEMO home. This display is 3840×2160 at 1×, so the images will be 1680×1050, not the 3360×2100 of the existing site images; I report this rather than upscale |
| 9 | `git diff --check v1.27.0...HEAD` | 32 trailing-space lines, all in captured producer YAML | the bytes kept; a scoped `.gitattributes` exception for the captured fixtures, documented, so the check is green without trimming evidence |

Also: the earlier PR comment named `mouse-loss-table-timer` for the `2f71c970` CI failure; the survivor there was
`mouse-loss-table-hook` (the timer case was attempt 1 at `e948f7b6`). The response corrects it.

The content pairing of `caebf31f` was asked for in the owner's first brief. It is disclosed as a change to the
policy, not presented as the review's bounded policy: pairing remains consistency evidence, and an input whose log
does not print its content is counted as matched by type and instant only.
