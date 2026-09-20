# Journal — copy this file into your working directory and fill it in as you go

You are building something. Alongside the work, keep this journal. It is not a summary written at the end:
it is a running record, and the timing of each entry is part of what it records.

## Three rules

1. **Append only.** Never edit or delete an entry once written, even when it turns out to be wrong. A wrong
   entry followed by a correction is exactly the data this is for.
2. **Write the entry BEFORE you act**, not after. Before you open a file, before you run a command, before
   you choose between two approaches. If you find yourself writing an entry about something already done,
   mark it `(retro)` and carry on.
3. **`from:` is the important field.** Be honest about it. `prior` is a perfectly good answer and is more
   useful to us than a flattering one.

## Entry format

Four lines. Keep them short — this should cost you seconds, not minutes.

```
## E12 · 2026-09-21T10:44:07Z · RUN
what: ./validate.sh
why:  check the design parses before generating anything
from: runbook:RUNBOOK.md#step-2
```

`kind` is one of:

| kind | when |
|---|---|
| `READ` | opening a file or page to learn something |
| `DECIDE` | choosing between two or more approaches |
| `WRITE` | creating or editing a file |
| `RUN` | executing a command |
| `ERROR` | something failed — put the first line of the message in `what:` |
| `ASK` | you asked the operator anything at all |
| `CHECK` | you verified a result against something independent |

`from:` must be exactly one of these, with the detail filled in:

| `from:` | Use when |
|---|---|
| `runbook:<path>#<section>` | a runbook or bootstrap file pointed you here |
| `readme:<path>` | a README pointed you here |
| `contract:<path>#<section>` | a reference or contract document you looked up |
| `error:<first few words>` | an error message told you what to do |
| `stub:<path>` | generated code showed you the shape to follow |
| `example:<path>` | you copied or adapted an existing file in the project — **name the file** |
| `search:<what you searched for>` | you went looking, grep/find/listing |
| `prior` | you already knew it, or inferred it from general Java/Spring/other-framework knowledge |
| `operator` | you asked the human |

If two things led you there, name the one that actually decided it.

---

# Predictions — write these BEFORE starting task 1, then never change them

Answer in one line each. You will be wrong about some of these; that is fine and expected.

```
P1 how will I run data through the application? A main() that builds the Fluxtion graph and calls onEvent() with sample events, run via mvn exec:java.
P2 where will the application's output go? stdout (System.out from a handler node).
P3 what will tell me whether my change was correct? mvn compile succeeding, then the printed output matching hand-computed expected values.
P4 what do I expect to be the hardest part? Getting the right Fluxtion dependency coordinates/version and API (Fluxtion.interpret vs compile-time AOT generation) from memory.
P5 what do I expect to get wrong first? Maven coordinates/version or an API name that changed between Fluxtion versions.
```

---

# Entries

<!-- append below this line, never above -->

## E1 · 2026-09-20T20:03:00Z · READ
what: JOURNAL.md (already in the directory; read it, filled predictions in place)
why:  task said to keep the journal as described in it
from: readme:JOURNAL.md

## E2 · 2026-09-20T20:03:30Z · RUN
what: query Maven Central for latest com.fluxtion:fluxtion-builder / runtime version
why:  I don't know the current version; need real coordinates
from: prior

---

# Close — fill in at the end of each task

```
T<n> outcome: complete | incomplete | abandoned
first checked result at: <entry id>
what I would have wanted and could not find:
what I read that turned out to be irrelevant:
which existing file in the project most influenced what I wrote, and why:
predictions that were wrong, and what was actually true:
```

The last two questions matter most. Be specific and name files.
