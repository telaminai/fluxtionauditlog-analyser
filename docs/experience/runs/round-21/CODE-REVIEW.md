# Code review: do both engines solve the problem?

**Corrected.** An earlier version of this file said the Fluxtion engine had a windowing bug and vanilla
was right. That was wrong. **The specification is ambiguous and the two engines resolved it
differently.** Both are faithful to it; neither is defective.

## The ambiguity, which is mine

The task says:

> *"**E1 overheat streak** — three **or more** consecutive telemetry readings for the same vehicle and
> metric are above that metric's limit, all within 60000ms of each other. **It trips on the reading that
> completes the third.** A reading at or below the limit resets the streak."*

Two clauses that agree everywhere except on the fourth consecutive reading:

- *"three **or more** … all within 60000ms of each other"* — a standing condition. On the fourth
  reading the newest three still satisfy it, so it trips again.
- *"trips on the reading that **completes the third**"* — an edge, fired once per streak. The fourth
  reading completes a fourth, not a third, so it does not trip.

## How each engine read it

**Vanilla — the standing condition.** A sliding window: drop anything more than 60s older than the
newest reading, count what remains, trip on ≥3.

```java
long now = streak.isEmpty() ? 0 : streak.getLast();
while (!streak.isEmpty() && now - streak.getFirst() > 60000) streak.removeFirst();
return streak.size();
```

**Fluxtion — the edge.** The window is anchored at the streak's first reading; exceeding it starts a
new streak, so a given streak can only ever complete a third once.

```java
} else if (timestampMs - reading.highStreakStartTime <= 60000) {
    reading.consecutiveHighCount++;
    if (reading.consecutiveHighCount >= 3) tripped = true;
} else {
    reading.consecutiveHighCount = 1;
    reading.highStreakStartTime = timestampMs;
}
```

Four consecutive above-limit readings at t = 0, 30000, 50000, 80000:

| event | vanilla | Fluxtion |
|---|---|---|
| t=50000 | trip | trip |
| t=80000 | **trip** (condition still holds) | **no trip** (already completed its third) |

Neither is a defect. **Both engines solve the problem as specified; the problem was specified two ways.**

## Everything else agrees, and both are right

| probe | expected | vanilla | Fluxtion |
|---|---|---|---|
| E2 at exactly 100000ms after service ("more than") | no trip | ✓ | ✓ |
| E2 at 100001ms | trip | ✓ | ✓ |
| vehicle with no roster entry | no E3 | ✓ | ✓ |
| vehicle with no service record | no E2 | ✓ | ✓ |

## The real finding, and it is about the method

**An iteration loop cannot converge on an ambiguity.** Both agents built self-consistent engines and
wrote tests encoding their own reading; both suites passed; both passed the hold-out. More build cycles
would not have helped either of them, because there was nothing to discover — the thing that was wrong
was not in the code.

This is the fourth ambiguity or contradiction I have introduced into a behaviour spec in this series
(S3 versus S6/S7 in round 15, the D1 wording in round 18, and now E1 twice over). They surface only when
two implementations disagree.

**So the instrument that finds them is differential testing, not a hold-out.** The hold-out is a sample
and cannot distinguish "wrong" from "read differently" — it never contained a fourth consecutive
reading, so both engines passed it. Running two independent implementations against the same input and
diffing them is what exposed this in minutes, and it is worth doing routinely: **where two faithful
implementations diverge, the specification is under-determined at that point.**

## Correction to the earlier version

The previous text asserted vanilla was correct and Fluxtion had a bug, on the basis of the clause I
happened to weight. That was overconfident: I picked one reading of my own ambiguous sentence and
scored an engine against it.
