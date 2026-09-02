# Build: a real-time credit-risk engine

Behaviour only. **But unlike a rule list, this specification is about a graph: several rules read the
outputs of other rules, and the order in which things are evaluated is part of correctness, not an
implementation detail.**

## Events

- `TRADE,book,instrument,quantity,price,timestampMs`
- `PRICE,instrument,price,timestampMs`
- `FX,ccy,rate,timestampMs`
- `INSTRUMENT,instrument,ccy,multiplier` — reference data
- `LIMIT,book,creditLimit` — reference data
- `HALT,book,active,timestampMs` — trading halt for a book
- `EOD,timestampMs` — end of day

## The derived chain

Each of these reads the one before it, or reads two things at once. **They are values, not decisions.**

**D1 — position (per book+instrument).** Signed sum of `TRADE` quantities.

**D2 — mark (per book+instrument).** `position × latest PRICE × instrument multiplier`.

**D3 — base value (per book+instrument).** `mark × FX rate for the instrument's currency`. An unknown
rate is 1.0.

**D4 — book exposure (per book).** Sum of base values across that book's instruments.

**D5 — utilisation (per book).** `book exposure ÷ credit limit`. Unknown or zero limit means 0.

## What must be true

**O1 — one evaluation per node per event.** However many of its inputs changed, a node evaluates **at
most once** per incoming event. An engine that re-evaluates a node once per changed parent is wrong,
and will be detected.

**O2 — no stale reads.** When a node evaluates, every value it reads must already reflect this event.
`RISK` below reports both a mark and a utilisation; if they were computed from different generations of
the same event, that is a failure, whether or not the number looks plausible.

**O3 — halting stops the subtree.** While a book is halted (`HALT,book,true` until `HALT,book,false`),
nothing downstream of the halt gate may run for that book: **no evaluation, no decision, no side
effect.** A `TRADE` for a halted book updates the position and goes no further.

**O4 — after-event commit.** After all evaluation for an event is finished, each book that changed
records a commit line. **Commits run in the reverse of evaluation order** — deepest-derived first,
positions last.

**O5 — reference data is not activity.** An `INSTRUMENT` or `LIMIT` event whose values are unchanged
must not cause any node to evaluate.

## Decisions

**R1 — RISK (per book, EDGE).** On the event where a book's utilisation first goes at or above 0.80,
and again after it has fallen below and risen again. The decision carries the utilisation and the book's
exposure at that instant.

**R2 — BREACH (per book, EDGE).** As R1, at or above 1.00.

**R3 — CONCENTRATION (per book+instrument, EDGE).** On the event where one instrument's base value
first exceeds 60% of its book's exposure, while the book's exposure is above zero.

**R4 — EOD_SUMMARY (per book).** On `EOD`, one line per book that has any position, in ascending book
name. Not an EDGE — it fires every `EOD`.

## What it must produce

**A decisions file**, one line per decision, in the order decided:

```
<eventNumber>,RISK,<book>,<utilisation>,<exposure>
<eventNumber>,BREACH,<book>,<utilisation>,<exposure>
<eventNumber>,CONCENTRATION,<book>:<instrument>,<share>
<eventNumber>,EOD_SUMMARY,<book>,<exposure>
```

Numbers to two decimal places, `HALF_UP`. `share` is the fraction, so `0.75` not `75%`.

**And an evaluation file**, one line per event, recording what actually ran:

```
<eventNumber>,<nodeName>|<nodeName>|...
```

The node names that evaluated during that event, **in evaluation order**, each appearing **once**.
Commit entries are written as `commit:<nodeName>` at the end of the same line, in commit order.
An event where nothing evaluated writes the event number and an empty list.

## Running it

```
java -cp <classpath> <your.Main> <scenario-file> <decisions-file> <evaluation-file>
```
One event per line, comma-separated, `#` starts a comment. **Do not hardcode a scenario.**

## Deliverables

1. The engine. Report your main class's fully-qualified name.
2. `Main` as above, writing both files.
3. **JUnit tests**; `mvn clean test` green.
