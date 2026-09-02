package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import java.util.HashMap;
import java.util.Map;

/** R3: per-order sum of PAID amounts. PAYFAIL adds nothing. */
public class PaidStore extends EventLogNode {

    private final OrderStore orderStore;
    private transient final Map<String, Double> paidAmounts = new HashMap<>();

    public PaidStore(OrderStore orderStore) {
        this.orderStore = orderStore;
    }

    @OnEventHandler
    public boolean onPaid(Paid event) {
        if (!orderStore.isOpen(event.orderId())) {
            return false;  // order not open or doesn't exist
        }
        double newAmount = getPaidAmount(event.orderId()) + event.amount();
        paidAmounts.put(event.orderId(), newAmount);
        auditLog.info("orderId", event.orderId()).info("amount", event.amount())
                .info("total", newAmount);
        return true;
    }

    @OnEventHandler
    public boolean onPayfail(Payfail event) {
        if (!orderStore.isOpen(event.orderId())) {
            return false;  // order not open or doesn't exist
        }
        auditLog.info("orderId", event.orderId()).info("failed", true);
        return false;  // R3: PAYFAIL adds nothing, and should not trigger downstream
    }

    public double getPaidAmount(String orderId) {
        return paidAmounts.getOrDefault(orderId, 0.0);
    }
}
