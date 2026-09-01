# Change request A — a LOCAL change

One rule changes. Nothing else about the system does.

## M7 — minimum lot size

A member's position in a symbol contributes to the margin requirement **only if the absolute net
position is 10 or greater**. Positions below that are treated as zero for requirement purposes. They
still exist, are still tracked, and still appear wherever positions are reported — they simply do not
attract margin.

## What must still be true

All of M1–M6, unchanged and re-evidenced.

## Done when

The build is green, the scenario re-run with at least one below-minimum position, and you have
audit-log evidence for M1–M7.

## Report

How many build attempts; **exactly which files you changed and which you did not**; whether the
dispatch order changed and how you know.
