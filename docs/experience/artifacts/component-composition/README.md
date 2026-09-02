# Component composition — five subsystems, twelve stages, four shared events

**What this artifact is.** Five subsystems, each built and validated on its own, published as **jars
with no source**. A consumer composes them into one running engine. It exists to test one claim:

> A component market needs the integration cost for the consumer to be near zero. The supplier proves
> their subsystem works with audit logs and a fingerprint; the consumer should not have to re-derive
> the global dispatch.

**Round 39 shape** (round 37's was 7 stages, one event type, one chain):

| | |
|---|---|
| subsystems | 5 — marketdata, pricing, liquidity, risk, capital |
| nodes | **20** (12 compute stages + 8 event adapters) |
| event types | **4**, each consumed by **two** subsystems — no subsystem owns an entry point |
| generated | **913 lines** from a 20-bean file and 5 jars |
| consumer Java | none for the graph; only a `Main` that feeds events in |

## The events, and who consumes each

| event | consumed by | fan-in measured |
|---|---|---|
| `TICK` | marketdata, liquidity | **14 nodes** |
| `TRADE` | risk, capital | 7 |
| `RATE` | pricing, risk | 5 |
| `CONFIG` | marketdata, capital | 4 / 3 (each subsystem filters on its own key) |

Every subsystem is interleaved with at least two others, and three have stages at three different
depths. A composition that ran subsystems as units would be wrong in several places at once.

## The verified dispatch

`generated/audit-trace.log` is the real audit log of `scenario.txt`. One `TICK` reaches all twelve
stages across all five subsystems, in an order no one wrote down:

```
marketdata.depth  liquidity.book  marketdata.mid  pricing.adjusted  risk.notional
liquidity.score   risk.exposure   capital.charge  pricing.spread    marketdata.vol
risk.var          capital.buffer
```

All twelve values were checked against an independent hand computation and match exactly.

## Two things this artifact records that cost a build each

**1. A node never triggers itself.** The first version had six stages carrying both an
`@OnEventHandler` and an `@OnTrigger`. A `CONFIG` event set the node's field, marked it dirty and
propagated to its children — **without recomputing its own value**. `risk.var` read a `vol` of `0.0`
and nothing failed. The fix is the eight adapter nodes: an adapter holds the event, a stage only ever
computes under `@OnTrigger`, so nothing computes before its parents have settled.

The cheaper alternative — have the event handler call the trigger method — is correct on final values
but **glitches wherever a node handles an event that also reaches it through a parent**. Here that is
`Book` (handles `TICK`, parent `Depth` derives from `TICK`) and `Buffer`; the other four are safe
either way. The glitch is visible in the audit log as a stale intermediate value, so the split is
what this artifact uses.

**2. Colliding simple names break the generated code.** Two vendors each shipping a `TickIn` is the
ordinary case in a component market, and it does not compile — the generator qualifies the declared
type and not the constructor. Filed as `UP-FLX-27` in `docs/proposals/upstream-asks.md`; worked
around here by renaming the adapters (`MdTick`, `LqTick`, …), which a real consumer could not do.

## Layout

```
consumer/lib/*.jar      the five subsystems, binaries only
consumer/src/main/fluxtion/designer/application-context.xml   the whole integration
generated/AppProcessor.java                                   913 lines, nobody wrote them
generated/audit-trace.log                                     the proof
subsystems/fx/          the Fluxtion sources - NOT given to the consumer
subsystems/van/         plain-Java equivalents, identical constructor signatures, for the control arm
```
