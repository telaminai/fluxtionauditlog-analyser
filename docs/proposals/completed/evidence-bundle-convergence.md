# Converging on `.fexp` — how the skills shrink to nothing

**Goal, from the owner (2026-09-28): the `.fexp` is the product. Few skills, or none.** Friday's
`audit-experiments/<slug>/` convention proved the utility with a pragmatic build; it is scaffolding, not the
destination. This is the path from two conventions to one artefact.

## The verdict this changes, and why

My review of the packaging spec recommended **shape C** — skills do the assembly, the analyser owns only
verification and two `context` fields. That verdict was right for its question and **wrong for this goal**, and
the reason is worth stating rather than quietly reversing: I was asked *what is the smallest thing that works*,
and a skill was an acceptable delivery vehicle. It no longer is. A capability you intend to ship as a product
should not be implemented in prose an agent re-derives on every run.

The evidence has not changed. The goal has. **If the target is no skills, the capture core belongs in the
analyser** — which is what B-minimal said, minus the UI, and I now agree with that half of it.

## The test: mechanism or judgement?

> **Mechanism moves into the tool. Judgement stays with a person.**

Applied to the shipped `capture-evidence-bundle` skill, step by step:

| step | what it is | verdict |
|---|---|---|
| pause Follow, restore it afterwards | a rule | **mechanism** |
| refuse on: no log, load pending, identity `replacement`/`unverified`, `changed-on-disk`, not one plain file | five rules with definite answers | **mechanism** |
| wait for `project.unsavedEdits` to clear, then poll | a rule with a timeout | **mechanism** |
| record `log.generation`, assemble `log/ graph/ profile/` | a fixed layout | **mechanism** |
| call `--bundle-profile`, relay `left out:` / `dangling:` | a command and its output | **mechanism** |
| call `--pack`, report the identity | a command and its output | **mechanism** |
| re-read the generation; delete the bundle if it moved | the walk-save rule, restated | **mechanism** |

**Every step is mechanism. There is no judgement in it at all.** That is the finding: the capture skill is a
script written in prose. Prose is the worst medium for a script — it cannot be tested, it drifts from the code
it calls, and it is re-interpreted on every run by a different reader.

Contrast Friday's skill, which contains real judgement: *which* time window to excerpt, whether to keep or gzip
the full log, what belongs in `EXPERIMENT.md`. Those are decisions about an investigation, and no tool should
make them.

## The destination

**Two operations, no skills.**

```
capture   → one operation on the running analyser, producing a .fexp
--verify  → shipped
--unpack  → shipped
```

Capture must be an operation on the **running** analyser, not a CLI command, because every refusal above is a
fact only the live session holds. That is the one thing this plan concedes to B-minimal. It still needs **no UI**:
the socket is the entrance, and a menu item is a later, separate decision driven by whether a demo recipient has
an agent.

The recipient side is already there. `--verify` and `--unpack` are headless, tested, and were the strongest part
of the v1 review — every structural attack was refused.

## What cannot move in, and what to do with it

Two things, and both are about the investigation rather than the log:

- **The narrative.** `EXPERIMENT.md` is the author's account. Make it an **input**, not a skill: the capture
  operation takes optional notes and packs them as a member. The bundle then carries the account without anyone
  writing a procedure for producing it.
- **The rerun recipe.** Friday's `rerun/commands.sh` and config describe how to restart the *system under test*.
  The analyser knows nothing about that system and should not pretend to. **Leave it out of `.fexp` v1** and say
  so: a bundle is evidence of a run, not a way to re-run it. That is already D-0's position on replay.

## Getting there

| step | what | what it removes |
|---|---|---|
| 1 | Move the capture skill's seven steps into a capture operation on the socket, with the refusals as named errors | the whole `capture-evidence-bundle` skill |
| 2 | Take optional notes and pack them as a member | the reason to hand-write `EXPERIMENT.md` |
| 3 | Reduce `open-evidence-bundle` to one call, or delete it once `--unpack` plus three open verbs is a documented one-liner | the second skill |
| 4 | Retire Friday's folder convention once 1–3 land: keep `slice-audit-log.py` (it is a genuine tool), keep the excerpt judgement as guidance, drop the profile-pointer step entirely — `--bundle-profile` replaced it | one of the two conventions |

**Nothing here is thrown away.** Friday's §2a — the silent re-anchoring of a moved profile — is the reason
`--bundle-profile` exists and exports no paths at all. That lesson is now enforced in code instead of remembered
in prose, which is the whole point of the exercise.

## Two decisions this forces

**1. Excerpt versus whole log, and it is now urgent.** The spec mandates whole-log for v1, correctly, because
excerpting invalidates `recordIndex` and every walk digest. Friday's skill excerpts by time window precisely
because real logs here are 64MB and 142MB. The v1 review's **F1** makes this worse than a size annoyance: the
verifier currently reads every member whole into heap, so a legitimate whole-log bundle needs more memory than
it occupies on disk. Decide before capture moves into the tool, because the capture operation is where the
choice becomes a parameter or an absence.

**2. What the bundle claims to be.** With the narrative inside it and the rerun recipe outside, a `.fexp` is
*"this is what the log said, and here is what I think it means"*. That is a narrower, more defensible artefact
than Friday's folder, which also implied *"and here is how to make it happen again"*. Worth saying in the spec,
because the narrower claim is the one that survives a sceptic.

## What stays true whichever way these go

The rule that produced the original verdict still holds, and it is what makes "no skills" reachable rather than
merely desirable:

> **Put it in the analyser if a skill would have to guess, or would have to re-implement something a recipient
> must trust. Otherwise put it in a skill — and if nothing is left, there is no skill.**

For this piece of work, nothing is left.
