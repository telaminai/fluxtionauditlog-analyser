package com.acme.fulfil;

import java.util.*;

public class Engine {
    private State state = new State();
    private List<Decision> decisions = new ArrayList<>();
    private int eventNumber = 0;

    // Track if order was releasable before this event
    private Map<String, Boolean> wasReleasableBeforeEvent = new HashMap<>();

    // For R7 and R8, track if we've fired for this order
    private Set<String> hazardBlockedOrders = new HashSet<>();
    private Set<String> overweightOrders = new HashSet<>();

    // For R10 STOCKOUT, track previous stock levels
    private Map<String, Long> previousStockLevels = new HashMap<>();

    // For R9 SLA_BREACH, track per-order if we've already emitted it
    private Set<String> slaBreachEmitted = new HashSet<>();

    // Current event being processed
    private Event currentEvent = null;

    public void processEvent(Event event) {
        if (event == null) {
            return;
        }

        currentEvent = event;
        eventNumber++;

        // Check if reference data changed (R11)
        if (event instanceof Event.ProductEvent pe) {
            boolean changed = state.updateProduct(pe.sku, pe.unitPrice, pe.hazardous);
            if (!changed) {
                eventNumber--; // Don't count this as an event if reference data unchanged
            }
            return; // Reference data doesn't emit decisions
        } else if (event instanceof Event.CustomerEvent ce) {
            boolean changed = state.updateCustomer(ce.customerId, ce.tier, ce.creditLimit);
            if (!changed) {
                eventNumber--;
            }
            return;
        } else if (event instanceof Event.CarrierEvent ce) {
            boolean changed = state.updateCarrier(ce.carrierId, ce.maxWeightKg, ce.handlesHazardous);
            if (!changed) {
                eventNumber--;
            }
            return;
        }

        // Non-reference events: update state and check rules
        updateStateAndCheckRules(event);
    }

    private void updateStateAndCheckRules(Event event) {
        if (event instanceof Event.ReceiptEvent re) {
            state.addStock(re.sku, re.quantity);
            ();
        } else if (event instanceof Event.AdjustEvent ae) {
            long oldStock = state.getOnHandStock(ae.sku);
            state.addStock(ae.sku, ae.delta);
            checkStockoutRule(ae.sku, oldStock);
            checkReleaseRuleForAllOrders();
        } else if (event instanceof Event.CountEvent ce) {
            long oldStock = state.getOnHandStock(ce.sku);
            state.setStock(ce.sku, ce.countedQuantity);
            checkStockoutRule(ce.sku, oldStock);
            checkReleaseRuleForAllOrders();
        } else if (event instanceof Event.OrderEvent oe) {
            state.createOrder(oe.orderId, oe.customerId, oe.sku, oe.quantity);
            checkReleaseRule(oe.orderId);
        } else if (event instanceof Event.AmendEvent ae) {
            if (!state.isOrderCancelled(ae.orderId)) {
                state.amendOrderQuantity(ae.orderId, ae.newQuantity);
                checkReleaseRule(ae.orderId);
            }
        } else if (event instanceof Event.CancelEvent ce) {
            state.cancelOrder(ce.orderId);
            // Cancellation ends releasable state, but we don't un-fire RELEASE
        } else if (event instanceof Event.PaidEvent pe) {
            if (!state.isOrderCancelled(pe.orderId)) {
                state.addPaidAmount(pe.orderId, pe.amount);
                checkReleaseRule(pe.orderId);
            }
        } else if (event instanceof Event.PayfailEvent pf) {
            // PAYFAIL doesn't add to paid amount, so no rule effects
        } else if (event instanceof Event.PickstartEvent ps) {
            // PICKSTART doesn't trigger rules
        } else if (event instanceof Event.PickdoneEvent pd) {
            checkSLABreachRule(pd.orderId, pd.timestampMs);
        } else if (event instanceof Event.DispatchEvent de) {
            checkHazardBlockRule(de.orderId, de.carrierId);
            checkOverweightRule(de.orderId, de.carrierId, de.weightKg);
        } else if (event instanceof Event.DeliveredEvent de) {
            // DELIVERED doesn't trigger rules
        }
    }

    private void checkReleaseRulcheckReleaseRuleForAllOrderseForAllOrders() {
        for (String orderId : new ArrayList<>(state.orders.keySet())) {
            checkReleaseRule(orderId);
        }
    }

    private void checkReleaseRule(String orderId) {
        // R6 RELEASE: Emit when order first becomes allocatable AND credit-ok
        // Emit once per order unless it becomes non-releasable then releasable again

        State.OrderData order = state.getOrder(orderId);
        if (order == null || !state.isOrderOpen(orderId)) {
            return;
        }

        boolean isNowReleasable = state.isReleasable(orderId);
        boolean wasReleasable = wasReleasableBeforeEvent.getOrDefault(orderId, false);

        if (isNowReleasable && !wasReleasable) {
            // Transition from not releasable to releasable
            emitDecision("RELEASE", orderId);
            state.markReleased(orderId, getCurrentTimestamp()); // Store the current timestamp
        }

        // Update state for next event
        wasReleasableBeforeEvent.put(orderId, isNowReleasable);
    }

    private void checkHazardBlockRule(String orderId, String carrierId) {
        // R7 HAZARD_BLOCK: On DISPATCH with hazardous product and non-handling carrier
        // Fire once per order (EDGE)

        if (hazardBlockedOrders.contains(orderId)) {
            return; // Already fired for this order
        }

        State.OrderData order = state.getOrder(orderId);
        if (order == null) {
            return;
        }

        State.ProductData product = state.getProduct(order.sku);
        if (product == null || !product.hazardous) {
            return;
        }

        State.CarrierData carrier = state.getCarrier(carrierId);
        if (carrier == null || carrier.handlesHazardous) {
            return;
        }

        // Conditions met: emit HAZARD_BLOCK
        emitDecision("HAZARD_BLOCK", orderId);
        hazardBlockedOrders.add(orderId);
    }

    private void checkOverweightRule(String orderId, String carrierId, double weightKg) {
        // R8 OVERWEIGHT: On DISPATCH with weight > maxWeightKg
        // Fire once per order (EDGE)

        if (overweightOrders.contains(orderId)) {
            return; // Already fired for this order
        }

        State.CarrierData carrier = state.getCarrier(carrierId);
        if (carrier == null) {
            return;
        }

        if (weightKg > carrier.maxWeightKg) {
            emitDecision("OVERWEIGHT", orderId);
            overweightOrders.add(orderId);
        }
    }

    private void checkSLABreachRule(String orderId, long pickdoneTimestamp) {
        // R9 SLA_BREACH: On PICKDONE when timestamp > release_timestamp + 3600000
        // Fire if order was released and time exceeded

        if (slaBreachEmitted.contains(orderId)) {
            return; // Already emitted for this order
        }

        State.OrderData order = state.getOrder(orderId);
        if (order == null || order.releaseTimestampMs == -1) {
            return; // Order was never released
        }

        if (pickdoneTimestamp > order.releaseTimestampMs + 3600000) {
            emitDecision("SLA_BREACH", orderId);
            slaBreachEmitted.add(orderId);
        }
    }

    private long getCurrentTimestamp() {
        if (currentEvent != null) {
            return currentEvent.timestampMs;
        }
        return 0;
    }

    private void checkStockoutRule(String sku, long oldStock) {
        // R10 STOCKOUT: On event that takes stock from >=0 to <0
        long newStock = state.getOnHandStock(sku);

        if (oldStock >= 0 && newStock < 0) {
            emitDecision("STOCKOUT", sku);
        }
    }

    private void emitDecision(String type, String key) {
        decisions.add(new Decision(eventNumber, type, key));
    }

    public List<Decision> getDecisions() {
        return decisions;
    }

    public void processAll(List<Event> events) {
        for (Event event : events) {
            if (event != null) {
                processEvent(event);
            }
        }
    }
}
