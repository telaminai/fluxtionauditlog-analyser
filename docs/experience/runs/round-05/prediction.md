# Round 05 — the control arm. Predictions registered BEFORE the run

**This is round 04's hypothesis tested properly.** Round 04 measured WENT-OUTSIDE at **4 vs 0** from a
single pair — a result with no variance behind it. This is the same comparison at **n = 3 per arm**, which
makes it the loop's first **attribution round** (D-AX7) rather than an exploration one.

## The claim under test

> Supplying the published Fluxtion authoring resources reduces how often an author has to leave the
> project to answer a question.

One variable. Six fresh agents, same bundle, same task; three with the resources, three without.

## Harness fixes carried from round 04

Round 04's harness perturbed its own subject and manufactured a finding. Corrected here:

- **`serverName` is left alone.** Changing it broke `export-audit.sh` and produced a symptom I caused.
  Isolation instead comes from `MONGOOSE_SERVERS_DIR`, the documented override that `run-server.sh`
  already honours (line 16) — **isolation as a property of the environment, not a promise by the subject**
  (D-G8).
- **Each arm gets its own registry directory and its own port** (8300–8305). No shared `~/.mongoose`.
- **Both arms are told the API key is configured** and that `check-fluxtion-key.sh` verifies it. Round
  04's control read `~/.fluxtion/fluxtion.apiKeyFile` directly, breaching a stated rule; removing the
  motive is better than restating the rule. Symmetric across arms, so it cannot favour either.

Remaining known perturbation: `listenPort` is edited per copy, unavoidably and symmetrically.

## Predictions

- **P1 — the headline.** Mean WENT-OUTSIDE is **lower in T than in C**, and the arms do not overlap:
  every T run below every C run. *(Round 04: 4 vs 0.)*
- **P2 — the failure still happens in both.** At least one T run hits the constructor-match error. Round
  04 established that the resources resolve rather than prevent it; if all three T runs now avoid it, that
  finding was a one-off and I should say so.
- **P3 — the audit gap is universal.** **All six** runs learn to log values by copying the project's
  `RootNode`, not from any resource. Any run that learns it from a supplied resource falsifies UP-FLX-35.
- **P4 — build attempts do NOT separate the arms.** Round 04 was 2 and 2. I expect overlap, and if build
  count separates them as cleanly as WENT-OUTSIDE, my claim that success metrics are near-ceiling and
  uninformative is weaker than I have been asserting.
- **P5 — dispatch order.** At most one run explains sibling ordering, and only by going outside. Zero
  explanations from the supplied resources.
- **P6 — variance is real.** The three runs within an arm will **not** agree exactly. If they do, n=1 was
  a better instrument than I credited and the cost of n=3 was wasted.

## What would falsify the write-up

- **Arms overlap on WENT-OUTSIDE** → round 04's 4-vs-0 was noise, and D-AX5's designation of it as the
  primary signal rests on one lucky pair.
- **A T run finds the audit contract in a supplied resource** → UP-FLX-35 is wrong about at least one of
  the five.
- **Any arm fails the task** → then task success *is* discriminating, and my "near-ceiling, uninformative"
  claim needs retracting.

## Known limits

One task, one model family, one machine. Six concurrent regenerations hit the hosted generation service,
so an environment failure is possible and will be reported as an environment failure rather than quietly
dropped. ~~n=3 supports a direction, not an effect size.~~

> **STRUCK AFTER THE RUN (review F4/C2, 2026-08-30).** Even a direction is more than n=3 supports here.
> As a 2x2 the observed 3/3 vs 1/3 is Fisher's exact **p = 0.20 one-sided**, on one task, one model family
> and one machine. The preregistered sentence is kept struck rather than deleted, because a preregistration
> that is quietly edited afterwards is worth nothing — but it is **not** the current claim. See `round.md`.
