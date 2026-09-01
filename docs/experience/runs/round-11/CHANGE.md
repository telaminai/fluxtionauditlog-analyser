# Change request — three new requirements for the working margin engine

The engine in this directory works and its behaviours M1–M6 are already evidenced. **Three new
requirements have arrived. M1–M6 must continue to hold, and you must re-evidence them.**

## M7 — a house buffer absorbs small shortfalls

Before a margin call is raised, a **house buffer** absorbs the shortfall. The buffer is an operating
limit set by the build, not by any event: **1000 per member**. A call is raised only for the residual
above the buffer, and only if that residual is positive.

## M8 — credit limits (a new event type)

A new event arrives: **`CreditLimitSet(member, limit)`**. A member's *effective* collateral is their
posted collateral **plus** their credit limit. Every comparison that used posted collateral now uses
effective collateral. A credit limit is market-relevant: changing one **may** raise or clear a call.
Re-publishing an unchanged one must not, exactly as M3 requires for margin parameters.

## M9 — a concentration surcharge

If a member's single largest symbol requirement exceeds **60%** of their total requirement, that member's
total requirement is increased by **20%**. This is applied before the call is assessed, so it can create
or enlarge a call.

## What must still be true

All of M1–M6, unchanged, **plus** M7–M9. In particular:

- **M4** — still exactly one report record per incoming event, including the new event type.
- **M5** — the report is still the final node recorded in every cycle.
- **M6** — no value in a report may mix generations. The surcharge and the buffer must be computed from
  the same event's state as the figures they adjust.

## Done when

The build is green, the scenario has been re-run including the new event type, and you have quoted
audit-log evidence for **M1–M9**.

## Report

State: how many build attempts; **which components you had to change and which you did not**; whether the
dispatch order changed and how you know; and how long the change took relative to the original build.
