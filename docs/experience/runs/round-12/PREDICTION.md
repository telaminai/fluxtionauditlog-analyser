# PREDICTION — round 12: local change vs orchestration change, and vanilla on Opus

**Committed before launch.** Owner's framing: *"a true test is composing existing functions into a usable
solution, then making changes that are local node changes and global orchestration changes."*

## Why round 11 was mush

Round 11's `CHANGE.md` **conflated the two**. M7 and M9 were orchestration changes (a step inserted
between two existing steps); M8 was a new input. All three were implemented as **local edits** — agents
opened `MarginCalculator` and `MarginCallChecker` and added branches, when the framework's answer is to
insert a node and let the order re-derive. The builder change was **6 lines in both runs**; everything
else was imperative editing and scenario data.

So round 11 measured *an agent editing imperatively inside a graph framework*, not Fluxtion's change cost.

Round 11's honest numbers, for reference: **Fluxtion change 1.02× its build; vanilla 1.70×.**

## The design

| arm | change | model | docs |
|---|---|---|---|
| `fx-local` | **local**: minimum lot size — one node's arithmetic, no new edges | Haiku | + one-line idiom hint |
| `fx-orch` | **orchestration**: sub-account netting inserted BETWEEN positions and requirement | Haiku | + one-line idiom hint |
| `vanOpus-1/2` | build from scratch | **Opus** | none |

The idiom hint is one paragraph: *a new behaviour is usually a new node; reserve edits for what a node
itself computes*. Single variable against round 11's Fluxtion arm.

The vanilla-Opus arm tests the owner's other point: **round 11 compared Fluxtion+Haiku against
vanilla+Haiku, and the vanilla deliverable was not equivalent** — neither run ever exercised a margin
call, the audit "nodes" are string literals so ordering is authored rather than derived, and the change
*destroyed* previously-present M2 evidence without the agent noticing.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| L1 | **The LOCAL change is cheap and touches ONE node** — under 40,000 tokens, one file plus scenario | medium-high |
| L2 | Dispatch order is **unchanged**, and the agent says so correctly | high |
| **O1** | **The ORCHESTRATION change, with the hint, adds a node and leaves existing node logic alone** — `PositionBook` and the requirement node unedited except for the parent reference | medium |
| **O2** | **The orchestration change costs LESS than round 11's 1.02×** — under 0.8× the original build — because inserting is cheaper than rewriting | medium |
| O3 | The agent **reads** the new dispatch order out of the generated source rather than deriving it | high |
| V1 | **vanilla+Opus produces materially better evidence than vanilla+Haiku** — margin calls actually exercised, and some real ordering mechanism | medium |
| V2 | **vanilla+Opus still costs more than 91,471** (Fluxtion+Haiku's build), making Fluxtion+Haiku cheaper at equivalent quality once the 5× price multiple is applied | medium |

## The claim this settles

If **O1 and O2 hold**, the framework's central economic claim is demonstrated rather than asserted: **an
orchestration change is additive, and the ordering re-derives itself.** Vanilla cannot match it — round 11
showed why, when its agent inserted two stages and truthfully reported *"dispatch order: unchanged"*,
because there is no dispatch to change, only a method body.

If **O1 fails even with the hint**, the gap is not documentation and the honest conclusion is that the
idiom needs a diagnostic or a template, not a paragraph.
