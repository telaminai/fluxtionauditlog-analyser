# Round 02 — 2026-08-30

## Environment

| | |
|---|---|
| Bundle | clean re-unzip of the verified P3 zip (**not** round 1's modified copy) |
| Doc set under test | `current/` **v1** — the set written from round 01's findings |
| Key | present · **Analyser** | not reachable · **Port 8181** | free before and after |
| Task | **different by design** — per-symbol running count and max (stateful across events), where round 01 was a single-event predicate |

## Outcome: TASK SUCCEEDED — on a harder task, and the pass is still a non-event

## Round 01 findings: did they recur?

| | Finding | Recurred? |
|---|---|---|
| R1-A | `EventLogNode` contract undocumented | **No** — used correctly, unremarked |
| R1-B | documented lifecycle unrunnable | **No** — started, exported, stopped cleanly |
| R1-C | registry/port recovery | **Not exercised** — no failed boot occurred. Unfixed upstream, untested here |
| R1-D | no analyser → stuck | **No** — read the YAML directly without friction |
| R1-E | dispatch order invisible | **No** — and the agent used the doc's own framing back: *"after `rootNode` in dispatch order, in the same cycle, on the same event"* |
| R1-F | severity absent from export | **No** |
| R1-G | `nodeBeans`, scalar args, two generated copies | **No** — *"wired it with both edits from `CLAUDE.md` §4"* |

**Six of seven fixed; one untested.** The doc set moved the needle.

## New findings — and the two that matter are MINE

**R2-A · `CLAUDE.md` §4 "two edits, both required" is WRONG. There is a third.** The AOT generator
reconstructs each node by matching its **non-transient instance fields** to a constructor. A node holding
state (two `HashMap`s) fails the build unless those fields are `transient` or appear as constructor args:

```
cannot find matching constructor for: Field{name=symbolStats, ...}
failed to match for these fields:[countBySymbol, maxPriceBySymbol, rootNode]
```

Both shipped examples hold only null-at-construction state, so **there is nothing to copy from**, and the
agent inferred `transient` from the generated source. The error names *constructor matching*, so "add a
constructor taking the maps" was an equally plausible guess. I asserted "two edits, both required"
without ever testing a stateful node. **The single most expensive thing in the round.**

**R2-B · My `regenerate` skill actively misled.** It says *"If the build stops at `process-classes`, the
key is why."* The build stopped exactly there for an unrelated reason — `fluxtion-maven-plugin:scan` is
bound to that phase (`pom.xml:174`). The key file was present. Following my rule sends a reader hunting a
licence problem that does not exist. **Worse than an omission: a false positive pointing the wrong way.**

**R2-C · The shipped fixture cannot demonstrate accumulation.** `data/input.txt` has one row per symbol,
so a running count is always 1 and a running max is always the only price — indistinguishable from a
broken implementation. The agent had to extend the data to make the log *evidence rather than
coincidence*. Bundle defect, not a doc defect.

**R2-D · Hidden feed offset threatens reproducibility.** On boot: *"Found previous offset, trying to skip
to file offset 0"*. The file feed persists a read offset somewhere undocumented — not in `.analyser/`, not
in `data/`. It happened to be 0. *"Had it been non-zero my appended rows might have been the only ones
seen, or none."* A silent determinant of what a run produces.

**R2-E · `auditLog` value-type overloads undocumented.** To log an `int` and a `double` without
stringifying, the agent unpacked `fluxtion-runtime-sources.jar` from `~/.m2` and read `EventLogger`.

**R2-F · `RootNode.getLatestEvent()` returns `Object`**, forcing an `instanceof` cast though the graph
declares one event type. No stated convention.

## Files never opened

`AGENTS.md` only. **With the agent's own caveat, which I accept:** it is byte-identical to `CLAUDE.md`, so
this measures duplication, not uselessness — it was never *needed* because its twin had been read. The
duplicate exists so different harnesses find a file they recognise. **Not a deletion candidate**; the
measurement is confounded and should be read that way rather than acted on.
