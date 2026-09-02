# Round 27 — vanilla Java scores 12/12 on the same spec

**No prediction was committed before launch.** I wrote the task, built the scaffold and launched the
agent without one — the same process miss recorded and supposedly fixed after round 07. The expectation
I stated afterwards (7–9 of 12) is marked as late and is not scored.

| cell | route | `mvn` | tests | **12-probe score** | output tok |
|---|---|---|---|---|---|
| G | Fluxtion, Java builder | 7 | 1 | 11/12 | 12,395 |
| H | Fluxtion, Java builder | ~17 | 4 | 11/12 | 21,719 |
| SPRING | Fluxtion, XML wiring | 4 | 6 | **12/12** | 14,322 |
| **VAN** | **plain Java, JDK only** | **6** | **14** | **12/12** | **13,343** |

**Vanilla matched the best Fluxtion cell on correctness, at comparable cost.** It also passed probe 12 —
several decisions of one kind in a single event — which two of the three Fluxtion cells failed.

## The comparison I have owed since round 21, answered

At three detectors (round 21) vanilla tied on correctness and won on cost. At **fifteen event types and
twelve interdependent rules** the answer is the same: **a tie on correctness, no cost advantage either
way.** The framework did not win here, and the harness that took Fluxtion from 5/11 to 12/12 is not
what separated them — vanilla reached the same score with no harness at all.

What vanilla reports about the shape of the work is more useful than the score:

> **Effort split: 25% rule logic, 75% surrounding machinery** — event parsing and type hierarchy 15%,
> state management 20%, **EDGE state tracking 20%**, test infrastructure 15%, event loop and decision
> collection 5%.

So three quarters of the work was machinery, and the largest single slice of it was hand-tracking
"did this just become true" for five EDGE rules. That is the cost Fluxtion is supposed to absorb, and
at this size absorbing it did not produce a better outcome.

## My scorer nearly reported the opposite

The first run scored vanilla **1 of 12** — every probe showing identical output. The cause was mine:
`score.sh` passes three arguments and vanilla's `Main` takes two, so the engine exited without writing
and the scorer read a **stale `/tmp/sc.txt` from the previous probe**. Every probe therefore compared
against the last successful engine's output.

Fixed: remove the output file before each probe, fall back to two arguments, and fail loudly when the
engine writes nothing. All cells re-verified with the corrected scorer; the Fluxtion scores are
unchanged (G 11/12, H 11/12, SPRING 12/12), so the earlier results were not corrupted.

**Fifth defect found in my own measuring instruments in this project.** Every one has been found by
checking a surprising number rather than reporting it.
