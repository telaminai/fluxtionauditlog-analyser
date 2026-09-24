# PR #12 round-2 predictions — frozen before implementation

## R12-6 — two fixes shipped with no regression test

The reviewer is right, and the claim in my commit message ("Each fix has a regression test") was false:
the round-2 diff touched only `DeleteConfirmationAndRevealerTest` (R12-1) and
`WindowControlsSayWhatIsKeptTest` (R12-4). R12-2 and R12-3 have none.

**Predictions, before writing them.** The reviewer expects both mutations to pass today. I agree, and
state what I expect after the tests exist:

- Removing the hold at `MainFrame` (`sayAtMillis`/`SAY_HOLD_MILLIS`) makes the new Follow test fail at
  *"an explanation must survive an idle follow tick"*. I predict it fails **only** there — no other suite
  reads the status label after a follow tick.
- Reverting `revealGraphByName` to a plain `selectGraph` makes the new report-link test fail at
  *"a report link to a closed chart must open it"*.

**Where I am least confident.** The Follow test needs real elapsed time — a tick with nothing new, then a
read more than a second later. I expect it to be slow (2s+) rather than flaky, but timing tests are the
usual source of intermittents, and if it proves flaky the right answer is to make the hold observable
rather than to sleep longer.

I also predict the display gate will still report **one skipped** test in
`PersonAtTheScreenFrameTest`, unrelated to this work and reproducible on `main`.
