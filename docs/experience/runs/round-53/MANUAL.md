# Assembling these components — everything you need

## Read the catalogue first — one command

```
unzip -p lib/vendor.jar META-INF/MANIFEST.MF | perl -0pe 's/\r?\n //g'
```

The `perl` unfolds manifest values, which wrap at 72 bytes. Each entry gives you a component's
`Provides`, `Requires`, `Constructor`, `Consumes`, and what it `Reads` from its neighbours.

## The one thing that will catch you

**Nothing propagates.** These are ordinary Java objects. Calling `MarketData.onTick(...)` computes
marketdata's figures and **nothing else** — `Pricing`, `Liquidity`, `Risk` and `Capital` are not
notified and do not recompute. Every component that should react to an event is a call **you** make.

The consequence that is easy to miss: **a component only recomputes on the events it `Consumes`.**
`Risk` consumes Tick, Trade and Rate — **not Config.** So when a `Config[volFactor]` changes
marketdata's `vol`, `Risk.var` keeps the value it computed from the *old* vol until the next Tick,
Trade or Rate arrives. If a figure must be current after an event, **something you call must
recompute it.**

Read the `Consumes` line of every component against every event type and satisfy yourself that each
figure the business asks for is fresh after each event.

**When a figure would be stale, use `refresh()`.** A component with a `Refresh:` line in the catalogue
can recompute its derived figures **without advancing any counter and without publishing an alert** —
that is what it is for. Re-dispatching a stored event is still wrong and still double-counts;
`refresh()` is the supported way to bring a component up to date after a neighbour's input moved.

## Dispatch order

`Requires` is the dependency graph: `MarketData → Pricing → Liquidity → Risk → Capital`. A component
reads its neighbours' **current** getters, so call them in that order or you price today's tick off
yesterday's book.

## Exactly once

The counters (`streak`, `breachCount`, `alertCount`) live inside the components and advance on each
call. **Call each component at most once per incoming event.** Calling twice double-counts;
re-dispatching a stored event to "refresh" a stale figure advances every counter for an event that
never arrived.

## Ownership

`onConfig` returns `true` if the component owns that key and acted. Offer a config to every component
that consumes Config; a component that does not own the key does nothing and returns `false`.

## Alerts and the fee

`Capital` publishes alerts itself to the `AlertSink` you install — **do not publish alerts yourself**
or every breach is reported twice. `FeeStrategies.byName("default"|"premium")` gives you a strategy;
`Capital.feeStrategy(s)` installs it, effective from the next recompute.
