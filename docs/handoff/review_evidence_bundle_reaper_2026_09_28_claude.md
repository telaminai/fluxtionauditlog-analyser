# Review — the reaper (`b50802d3..a8c37972`)

Adversarial review of the author's answer to my convergence review. Predictions committed before running, in
`review_reaper_predictions_claude.md` on this branch.

**Verdict: merge after F1, which I have fixed here.** The rejection of my age gate is **accepted in full** — the
author was right on all three counts. The replacement mechanism is better in principle and has one defect that
only a second process can see.

## 1. Commands and outputs

| command | result |
|---|---|
| `mvn -o -q clean test` on `a8c37972`, fresh Surefire XML | **2774 / 0 / 0 / 159** — matches the author |
| `--mode preflight` | **429 anchors** — matches |
| four `cv-` reaper controls | 4 of 4 caught (after my change: **431 anchors**, 4 of 4) |
| the same suite on this review branch | **2776 / 0 / 0 / 159** (+2, my regressions) |
| a two-process `FileLock` probe (below) | the defect, reproduced |

**Skips separate:** 159, all frame suites needing a display.

**Not run:** the display gate, the driver, the full 79-control sweep, `-Pregen`, `mkdocs --strict`. Budget. §5.

## 2. Predictions, scored

| # | prediction | outcome |
|---|---|---|
| P1 | `reapCorpses` releases a live capture's own lock, because closing any descriptor drops POSIX `fcntl` locks | **RIGHT**, and worse than stated — see F1 |
| P2 | SIGKILL untested | **NOT CHECKED** |
| P3 | duplicate hostnames reap each other | **NOT CHECKED** |
| P4 | empty `HOST` disables reaping silently | **PARTLY**: `reapCorpses` opens with `if (HOST.isEmpty()) return;` and the comment says "this host cannot be named: nothing is provable". Stated in code, not in the spec. ADVISORY at most. |
| P5 | `windowRecords` is duplicated work | **NOT CHECKED** |
| P6 | a new control written from intent | **NOT CHECKED** |
| P7 | the `bundle/` move broke an identity fixture | **WRONG** — the suite is green, including `EvidenceBundleTest` |
| P8 | EB.F9–F11 still open leaves the moved-generation risk unprovoked | **RIGHT, trivially** — the tracker says so |

Two right, one wrong, one partial, four unchecked.

## 3. Findings

### F1 — REQUIRED. Reaping disarms a capture running in the same JVM *(fixed here)*

**`BundleWriter.reapCorpses`.** On POSIX an `fcntl` lock belongs to the **process**, not the descriptor: closing
*any* descriptor to the file releases **every** lock the process holds on it. `reapCorpses` opens the marker of
each candidate — including one this JVM owns. The `OverlappingFileLockException` branch correctly concludes the
capture is live, and then try-with-resources closes the channel, releasing the lock that had just proved it.

**The holder cannot detect this.** Its `FileLock.isValid()` still returns `true`, because the JDK keeps a
per-JVM lock table the operating system knows nothing about. Only another process sees the truth — and another
process is exactly what the mechanism exists to coordinate with.

**Reproduction,** two JVMs, one marker:

```
1. holder locks the marker
2. another process asks:  could take the lock = false      (protected)
3. holder opens and closes a second channel to its own marker   <-- what reapCorpses does
4. holder's own lock.isValid() = true                      (it has no idea)
5. another process asks:  could take the lock = TRUE       <-- disarmed
```

A second analyser's reaper now reads the marker as free, concludes the owner is dead, and deletes a capture that
is still running. And because `reapCorpses` runs at the **start of every `write()`**, one analyser's second
capture disarms its own first.

**A second instance the obvious fix misses.** My first fix put the ownership check after the host comparison and
**still failed the regression** — because `Files.readString(marker)` opens and closes a descriptor too, and that
alone is enough. The check must come before anything touches the file. I only found this because the test was
cross-process; an in-JVM test would have passed at every stage.

**The fix here:** ownership is settled by path, in memory, before any open — and keyed on the **channel's own
lifetime**, not on a caller remembering to release. My first attempt used an explicit release and broke
`BundleWriterReapTest#…`'s "released, the same folder is a corpse", because ownership outlived the channel. An
entry whose channel is closed is no longer ownership, so a capture that ends by any route becomes reapable like
anyone else's.

**Regressions:** `ReapDoesNotDisarmItsOwnLockTest` (2 tests, with `CaptureLockTaker`, a second process — the
only vantage point that can see it). **Controls:** `cv-reap-never-opens-its-own-marker`,
`cv-a-claim-records-ownership`, plus `cv-the-working-folder-always-goes` re-anchored — **4 of 4 caught**,
including the author's `cv-reap-only-when-the-owner-is-dead`, which still passes unchanged.

## 4. The rejection of my age gate: accepted, without reservation

All three grounds hold, and the third is the one I should have seen:

- **no bound on a capture's length** — true; I picked ten minutes from "a capture takes about a second", which is
  an observation about the happy path, not a bound;
- **the folder's mtime stops moving once its children exist** — true, and it makes the signal measure the wrong
  moment entirely;
- **clocks and network mounts** — true, and an exchange directory on a network mount is precisely the
  shared-directory case the mechanism is for.

An owner marker with a lock is the right shape: it asks "is the owner alive?" instead of guessing from age.
**I was wrong and the author was right.** That the replacement has a defect does not change that — the defect is
in the POSIX detail, not in the choice.

## 5. What I could not check

SIGKILL versus a clean exit (P2 — the brief flags it, and the premise is "however it ends"); duplicate hostnames
on a shared mount (P3); `windowRecords` as observation versus duplicated work (P5); the new controls' quality
(P6); the two declared-unwitnessed branches; Windows delete-while-locked ordering; the display gate; the driver;
the full 79-control sweep; `-Pregen`; `mkdocs --strict`. **None of these is a pass.**

EB.F9–F11 remain open, correctly recorded. The moved-generation provocation is still the one I would do first.

## 6. Verdict

**Merge after F1.** The fix is on this branch with its regressions and controls green, and the author's own tests
pass unchanged. Everything else I attacked held, and the one thing I was sure of going in — my own age gate —
turned out to be the weaker design.
