# ACME FX feed — integration notes (supplier library acme-fx 1.0.0)

Maven coordinates: `com.acme.fx:acme-fx:1.0.0`, delivered as a Maven repository in this folder
(`supplier-repo/`, standard layout).

What the library provides:

- `com.acme.fx.FxQuote` — the feed's event: a currency pair (`pair()`) and its rate (`rate()`).
- `com.acme.fx.CsvToFxQuote` — a value mapper (`java.util.function.Function<Object, Object>`) for feed lines of the
  form `PAIR,RATE`, for example `EURUSD,1.0850`.
- `com.acme.fx.FxRateBook` — a ready-made processor node. It handles `FxQuote` events, keeps the latest rate for each
  pair, and records `pair`, `rate` and `pairs` (the number of distinct pairs seen) in the audit log. It needs no
  constructor arguments.

The library is used as supplied. It is not meant to be copied or modified.
