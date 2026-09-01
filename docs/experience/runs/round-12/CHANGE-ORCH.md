# Change request B — an ORCHESTRATION change

A new processing stage must sit **between** two stages that already exist.

## M7 — sub-account netting

Members trade through sub-accounts. A trade now carries a **sub-account** identifier, and a member's
exposure in a symbol is the **net across all their sub-accounts**.

**The margin requirement must be computed from netted positions and must never see un-netted ones.**
Netting therefore happens after positions are recorded and before the requirement is computed.

## M8 — netting must be observable

The netting stage must record, per member and symbol, the per-sub-account positions it combined and the
netted result — so a reader can see the netting happen between the two stages it sits between.

## What must still be true

All of M1–M6, unchanged and re-evidenced. In particular **M5**: the report is still the final node in
every cycle, even though a new stage now exists upstream of it.

## Done when

The build is green, the scenario re-run with a member holding offsetting positions in two sub-accounts,
and you have audit-log evidence for M1–M8.

## Report

How many build attempts; **exactly which files you changed and which you did not**; **whether the
dispatch order changed, what it changed to, and how you established that** — did you work it out, or read
it somewhere?
