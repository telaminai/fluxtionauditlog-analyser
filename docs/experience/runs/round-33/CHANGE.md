# Change request: customer discounts

The engine you have been given is working and its tests pass. Add the following, without breaking any
existing behaviour.

## New event

`DISCOUNT,customerId,pct` — reference data. `pct` is a fraction, so `0.25` means 25% off. A customer
with no `DISCOUNT` has a discount of 0. It is reference data: an unchanged republish must not cause any
rule to be re-evaluated or any decision to be emitted.

## What changes

**The value of an order is now discounted.** Everywhere the engine currently uses
`quantity × unitPrice`, it must now use `quantity × unitPrice × (1 − discount)` for that order's
customer. That affects:

- **R5 credit-ok** — the amount that must be paid, or that must fit inside a GOLD customer's credit
  limit, is the discounted value.
- **anything else deriving from order value.**

A change to a customer's discount must re-evaluate the orders affected by it, exactly as a payment or a
stock movement would. An order that was not releasable and becomes releasable because a discount was
applied must emit `RELEASE` at that point.

## New decision

**R13 — DISCOUNT_ABUSE (EDGE).** On the event where an order's discount first exceeds 0.50 while its
**undiscounted** value is at or above 10,000. Emitted once per order, and again if it stops being true
and becomes true again.

```
<eventNumber>,DISCOUNT_ABUSE,<orderId>
```

## What must still be true

Every rule the engine already implements must behave exactly as before for scenarios containing no
`DISCOUNT` event. Your existing tests must still pass.
