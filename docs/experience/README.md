# Experience loop — refining the LLM/user/analyser experience by measurement

A **loop handoff**: many short internal iterations, one review at the end. Distinct from the repo's
brief → report → review cycle, which reviews every slice.

## Why this exists as its own process

Documentation cannot be verified by reading it — the author always knows the answer. The only honest test
is to hand a **fresh** model a real artefact and a real task, and record where it stumbles. That is slow
to review per iteration and cheap to run, which inverts the usual economics: the loop should run several
times unreviewed, and the review should judge the *trend* rather than each edit.

## The loop

1. **Run** a fresh-context agent against a real generated bundle, with the seeding prompt from
   `spec-onboarding-example.md` verbatim and one concrete task.
2. **Record** the run under `runs/round-NN/` — prompt, task, `NOTES.md`, findings, and what changed.
3. **Improve** the doc set in `current/` — and delete as well as add.
4. **Repeat** with a *different* task.
5. **Hand off** for review when the stopping rule is met.

## The three risks this design guards against

**Overfitting to the task.** Run the same task twice and the docs will pass because they now describe
that task. So: **the task rotates every round**, and one task is **held out** — never used to drive an
edit, only to check. A held-out pass is the only pass that means anything.

**Growth without pruning.** Every finding tempts an addition, and documentation that grows every round
becomes documentation nobody reads. Each round records **what the agent never opened**, and unused
material is a deletion candidate. The doc set should be able to shrink.

**A closed loop.** I write the docs, choose the task, read the results and rewrite the docs. The fresh
agent is the only external signal, and it is not a naive one — every model has Fluxtion in its training
data, so **an easy pass is weak evidence and the failures are the output**. Passes are recorded as
non-events.

## Rules

- **Docs only.** This loop never changes analyser code. That keeps it conflict-free with a parallel
  session working on the analyser itself.
- **A finding counts when it RECURS.** One agent hitting something once is noise; the same friction in two
  rounds with different tasks is a defect. Rounds are non-deterministic by nature.
- **Archive, never overwrite.** A superseded doc set moves to `archive/vN/` so the trend is inspectable —
  including whether a later set got worse.
- **Record the environment.** Whether a key was present, whether the analyser was reachable, which bundle
  SHA. A run whose conditions are unknown proves nothing.

## Stopping rule

Stop and hand off when **either**:

- a round produces no new *recurring* findings, **or**
- the held-out task completes with no COULD-NOT-FIND and no WENT-OUTSIDE entries.

Stop early and say so if the loop is thrashing — findings alternating rather than reducing is a signal the
doc set has a structural problem that editing will not fix.

## Layout

```
current/        the doc set under test — the candidate for shipping into bundles
runs/round-NN/  prompt, task, NOTES.md, findings, changes made, environment
archive/vN/     superseded doc sets, kept so the trend can be read
```

## Where the findings go — and the redirect after round 03

Roughly half of rounds 01–03's findings were **Fluxtion authoring** friction, not analyser friction. The
owner's call on seeing that: *"It's descending into how do I author Fluxtion? Luckily the compiler gives
feedback we will extend to compiler codes on failure."*

That is the right repair and it changes what this loop is for. Documenting around a bad error message is
papering over it, and round 03 measured the cost of doing so: the agent **never opened** the bootstrap
document containing round 02's fix. So the authoring findings were rewritten as compiler diagnostics —
[`docs/proposals/upstream-asks.md` §1c](../proposals/upstream-asks.md), UP-FLX-32…34, each naming the
class in `fluxtion-builder` that already computes the fact its message omits — and the prose that
compensates for them should be deleted from the doc set once the messages land.

**The loop's own design defect, which explains the drift.** None of the three rounds had an **analyser
reachable** — the agent used `grep` every time. A loop set up to measure the LLM/analyser experience never
put the analyser in it, so it measured what was present: the bundle and the framework. A further round
should wire the analyser over MCP and use a task that needs it, or the loop should stop and let §1c be
its output.

## What a reviewer should judge

Not the individual edits. **The trend across rounds**, whether the held-out task passes, whether the doc
set shrank as well as grew, and whether the findings that recur are being fixed at the root or papered
over one symptom at a time.
