# EB.F9 — the moved-generation rule, provoked from the frame

**Result: the rule works. Provoked by a real second log open, off the event thread, and the bundle was
deleted.** EB.F9 asked for this because the existing frame test provokes only a *close*, in the same
event-thread task — the repo's own trap, since the copy must run off that thread or the check would be vacuous.

## What was run

A released-shape build, an isolated `-Duser.home`, exports enabled, and a DEMO log large enough to keep the copy
in flight: `demo-quote-series.yaml` repeated 120 times — **35 MiB, 87,120 records**. Then, over the action
socket:

```
t0   report {bundle: {path, notes}}          → {"phase":"WRITING", …}
t1   open {log: <a tiny DEMO log>}           → generation 1 → 2, while the copy ran
t2   context.capture
       phase  : REFUSED
       reason : "another log was opened while the bundle was being written — it was
                 deleted, because it would mix two sessions"
     on disk  : no bundle
```

The provocation is genuine: a second log opened through the ordinary verb, its `LogOpened` bumping the
generation on the event thread while `BundleWriter.write` ran on a `Background` thread. `BundleWritten` then
arrived carrying the generation the capture started with, `EvidenceCapture.onBundleWritten` compared it against
the open log's, refused, and `DeleteBundleEffect` removed the file.

**This closes the only path either review found to a bundle that is WRONG rather than absent.** Every other
failure in this feature fails closed — the capture errors, the output is deleted, nothing is produced. A bundle
mixing two sessions would have looked valid.

## What is still missing, and why it is not this

**A committed, deterministic regression.** The experiment above is timing-dependent: it works because a 35 MiB
copy outlasts a 430-byte log open, which is a large margin but a margin. A test built that way would be flaky
under load, and a flaky test here is worse than none.

The copy has no seam to hold. `Background.run` submits to an unbounded cached pool, so a test cannot occupy it,
and `BundleWriter.write` takes no hook. **The smallest honest seam is a package-private no-op hook in
`BundleWriter.write`, called before the copy, which a test sets to a latch.** Then the sequence is: arm the
latch, start the capture, open the second log, release the latch, assert the refusal — with no timing at all.

Until that exists, EB.F9 is **demonstrated but not guarded**: the behaviour is proven to work today, and nothing
would tell you if it stopped.

## For the release decision

Not a blocker. The rule fires, and the failure it prevents is the only one that would have produced misleading
evidence rather than no evidence.
