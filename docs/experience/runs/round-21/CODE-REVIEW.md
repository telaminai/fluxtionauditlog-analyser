# Code review: do both engines actually solve the problem?

Both passed their hold-out. Both were then read and probed with scenarios the hold-out does not contain.
**They are not equally correct.** Vanilla is right on every dimension probed; the Fluxtion engine has one
real defect the hold-out did not catch.

## The defect: E1's 60-second window

The spec: *"three or more consecutive readings … above that metric's limit, **all within 60000ms of each
other**. A reading at or below the limit resets the streak."*

**Vanilla — a sliding window.** Prune anything more than 60s older than the newest reading, then count
what is left:

```java
public int getStreakCount(String vehicleId, String metric) {
    Deque<Long> streak = streaks.get(streakKey(vehicleId, metric));
    if (streak == null) return 0;
    long now = streak.isEmpty() ? 0 : streak.getLast();
    while (!streak.isEmpty() && now - streak.getFirst() > 60000) streak.removeFirst();
    return streak.size();
}
```

**Fluxtion T4 — an anchored window.** The span is measured from the *first* reading of the streak, and
exceeding it hard-resets the count to 1:

```java
if (reading.consecutiveHighCount == 0) {
    reading.consecutiveHighCount = 1;
    reading.highStreakStartTime = timestampMs;
} else if (timestampMs - reading.highStreakStartTime <= 60000) {
    reading.consecutiveHighCount++;
    if (reading.consecutiveHighCount >= 3) tripped = true;
} else {
    reading.consecutiveHighCount = 1;          // <-- discards readings still inside the window
    reading.highStreakStartTime = timestampMs;
}
```

Four consecutive above-limit readings at t = 0, 30000, 50000, 80000:

| event | last three readings | span | spec says | vanilla | Fluxtion |
|---|---|---|---|---|---|
| t=50000 | 0, 30000, 50000 | 50000ms | trip | **trip** | **trip** |
| t=80000 | 30000, 50000, 80000 | 50000ms | **trip** | **trip** | **no trip** |

At t=80000 the newest three readings are consecutive, above the limit, and span 50 seconds — inside the
window. The Fluxtion engine drops the streak because the *original* start is 80s old, discarding two
readings that are still valid. Verified by running both engines on that scenario.

## Everything else agrees, and both are right

| probe | expected | vanilla | Fluxtion |
|---|---|---|---|
| E2 at exactly 100000ms after service ("more than") | no trip | ✓ | ✓ |
| E2 at 100001ms | trip | ✓ | ✓ |
| vehicle with no roster entry at all | no E3 | ✓ | ✓ |
| vehicle with no service record | no E2 | ✓ | ✓ |

## What this says about the method, not the engines

**The hold-out was too weak.** 21 events and five decisions, and it passed a build with a real
windowing bug. Both engines scored full marks; only reading the code separated them.

That is the same lesson as every previous round, one level up: a self-authored test suite cannot be the
correctness gate, and **neither can a small hold-out.** The hold-out is strictly better than
self-grading — it caught the E3 over-fire that broke two cells — but it is a sample, and a sample missed
this. Boundary and window semantics need cases built to attack them, not a plausible scenario.

**Both engines' own test suites missed it too** — 29 tests in vanilla, 26 in Fluxtion. Neither wrote a
case where a streak outlives the window while remaining valid.
