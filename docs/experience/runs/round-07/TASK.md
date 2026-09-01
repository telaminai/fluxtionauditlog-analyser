# Build: depot throughput and SLA monitor

Build a Fluxtion event processor for a parcel depot, in package `com.acme.depot`.

## Events (create these as Java records)

- `ParcelScanned(String parcelId, String zone, double weightKg)` — a parcel arrives at the depot
- `VanLoaded(String vanId, String zone, int parcelCount)` — a van is loaded and leaves
- `TariffSet(String zone, double pricePerKg)` — the shipping tariff for a zone is updated
- `SlaThresholdSet(String zone, int maxParcelsWaiting)` — the SLA threshold for a zone is updated

## Nodes

1. **`TariffBook`** — current price per kg for each zone. Updated by `TariffSet`. Other nodes read prices
   from it.
2. **`SlaThresholds`** — current max-parcels-waiting for each zone. Updated by `SlaThresholdSet`. Other
   nodes read thresholds from it.
3. **`DepotStock`** — how many parcels, and how many kg, are waiting in each zone. `ParcelScanned` adds;
   `VanLoaded` removes that many from that zone.
4. **`ShippingCost`** — the value of everything currently waiting, using `DepotStock` quantities and
   `TariffBook` prices. Recompute whenever the stock changes **or** a tariff changes.
5. **`SlaMonitor`** — reports a breach when a zone's waiting parcels exceed its threshold.
   **Business rule, and it matters: a breach is a fact about the DEPOT, not about the paperwork.
   Re-publishing an SLA threshold must never, by itself, produce a breach report. Only a change in what
   is actually waiting can do that.**
6. **`DepotReport`** — a summary reacting to `ShippingCost` and `SlaMonitor`: current value waiting, and
   whether any zone is in breach. It must write its values to the audit log so they appear in `nodeLogs`.

## Build requirements

- A `FluxtionGraphBuilder` that wires all six nodes, class `DepotProcessor`, package
  `com.acme.depot.generated`, output directory `src/main/java`.
- The audit log must be enabled so `DepotReport`'s values are recorded.
- `mvn process-classes` must succeed and generate the processor.

## Done when

`mvn process-classes` is green and `src/main/java/com/acme/depot/generated/DepotProcessor.java` exists.
