# A subsystem fingerprint, tested

The commercial thesis: **a component market where integration cost is near zero for the consumer, and
the supplier proves their subsystem works using audit logs and fingerprints.** Two of the three parts
are testable today and were tested.

## 1. Integration cost — measured, small scale

Composing two mutually dependent vendor components is **four bean declarations and no code**, with the
generator deriving an order that alternates between them:

```
pricing.mid → risk.notional → pricing.adjusted → risk.score
```

Nothing declares that order. Nothing declares the events either — `eventTypes` is optional, and
removing it from a working 12/12 engine took its bean file from 86 lines to 69 with no other change.

## 2. The fingerprint — it works, and it catches a miswiring

A subsystem's identity can be computed from the generated graphml: **its own nodes, the edges among
them, and the edges crossing its boundary.** The supplier publishes the hash from their validated
build; the consumer computes it from *their* composed graph.

From one validated four-component build:

| subsystem | fingerprint |
|---|---|
| pricing | `de2776be410f96d2` |
| risk | `e529e0a6fd5ba557` |
| liquidity | `d6e1a420d5f04f87` |
| capital | `faa28a4a4a9b7c79` |

**The test that matters.** A consumer wires `pricing.adjusted` to a second, unvalidated `Depth`
instance rather than the one the supplier expects — a plausible mistake, and the build stays green:

```
build errors: 0
pricing fingerprint now: d4d29e4c16ef2855
supplier published:      de2776be410f96d2
```

**The fingerprint changes.** The consumer can detect, mechanically and before running anything, that
the graph they assembled is not the graph the supplier validated.

Boundary edges are what make this work. A hash over the subsystem's internals alone would not have
moved — `pricing`'s own nodes and its internal `mid → adjusted` edge are unchanged. What changed is
what feeds it, and that is exactly the class of integration error a consumer makes.

## 3. Audit logs as the supplier's proof — the honest limit

A supplier can ship the audit log from their validation run: which stages ran, in what order, with what
values, for a stated input. That is a real behavioural claim and it is checkable.

**But it is only as good as the inputs it covers**, and this project has repeatedly found self-authored
evidence to be the weak point — a 29-test suite that got 0 of 5 behaviours right, an engine that self
-verified its halt gate on the one path it had thought about. A supplier's audit log proves what the
supplier tried. The consumer still needs their own probes for what the supplier did not.

## What is not yet shown

The fingerprint here is computed from **graph structure**, which detects miswiring and version drift.
It does **not** prove the subsystem computes correctly — that is what the audit log and the supplier's
tests are for, and those are self-authored.

And the whole demonstration is at four components in one build. The claim that integration cost stays
near zero as components multiply is the untested half, and it is the half the thesis rests on.
