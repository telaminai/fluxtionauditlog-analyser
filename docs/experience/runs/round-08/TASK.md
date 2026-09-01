# Build: grid battery dispatch

A Fluxtion event processor for a regional battery fleet, package `com.acme.grid`.

## Events (Java records)

- `PriceTick(String zone, double pricePerMWh)`
- `DemandReading(String zone, double megawatts)`
- `BatteryState(String unitId, String zone, double chargePct)`
- `TariffPublished(String zone, double standingChargePerMWh)`

## Nodes

1. **`TariffBook`** — standing charge per zone, from `TariffPublished`. Other nodes read charges from it.
2. **`PriceWindow`** — a rolling mean of the last N prices per zone, from `PriceTick`. **Two instances
   exist in the graph**: a short window over the last 3 ticks and a long window over the last 12.
3. **`DemandTracker`** — current demand per zone, from `DemandReading`.
4. **`Fleet`** — charge percentage per battery unit and the mean charge per zone, from `BatteryState`.
5. **`SpreadSignal`** — the gap between the short and long window means for a zone. Positive spread means
   prices are rising relative to trend. It must be able to tell **which** of the two windows moved, because
   a short-window move and a long-window move mean different things: report `"shortMoved"` or
   `"longMoved"` in its audit output accordingly.
6. **`DispatchPolicy`** — decides CHARGE, DISCHARGE or HOLD per zone from the spread, the demand and the
   fleet's charge.
   **Two operating limits are set by the operator, not by any event, and differ per zone. The build must
   supply them: minimum charge 20% in zone NORTH and 35% in zone SOUTH.** A battery below its zone's
   minimum may never DISCHARGE.
7. **`CostModel`** — the cost of the current dispatch, using `DispatchPolicy`'s decision, `DemandTracker`
   and the standing charge from `TariffBook`. A tariff republication changes future cost, but **a tariff
   is a price-list update, not a grid event: republishing one must not by itself cause a dispatch
   decision to be re-made or re-reported.**
8. **`GridReport`** — extends the audit log with the current spread, the dispatch decision and the cost.
   It must write **exactly one record per event cycle**, however many of its inputs changed.

## Build requirements

- A `FluxtionGraphBuilder` wiring all of the above, class `GridProcessor`, package
  `com.acme.grid.generated`, output directory `src/main/java`, resources output `src/main/resources`.
- Audit logging enabled so `GridReport`'s values land in `nodeLogs`.
- `mvn process-classes` green, and `src/main/java/com/acme/grid/generated/GridProcessor.java` generated.

## Done when

The build is green and the generated processor exists.
