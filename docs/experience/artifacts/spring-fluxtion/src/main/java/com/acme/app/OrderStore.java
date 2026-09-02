package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import java.util.HashMap;
import java.util.Map;

/** R2: order state. An order is OPEN from ORDER. AMEND replaces quantity. CANCEL makes it CANCELLED (terminal). */
public class OrderStore extends EventLogNode {

    enum OrderState { OPEN, CANCELLED }

    public static class OrderRecord {
        public String orderId;
        public String customerId;
        public String sku;
        public int quantity;
        public OrderState state = OrderState.OPEN;
        public Long releaseTimestamp;  // set when order is RELEASED

        public OrderRecord(String orderId, String customerId, String sku, int quantity) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.sku = sku;
            this.quantity = quantity;
        }
    }

    private transient final Map<String, OrderRecord> orders = new HashMap<>();

    @OnEventHandler
    public boolean onOrder(Order event) {
        if (orders.containsKey(event.orderId())) {
            return false;  // order already exists, ignore
        }
        OrderRecord rec = new OrderRecord(event.orderId(), event.customerId(), event.sku(), event.quantity());
        orders.put(event.orderId(), rec);
        auditLog.info("orderId", event.orderId()).info("customerId", event.customerId())
                .info("sku", event.sku()).info("quantity", event.quantity());
        return true;
    }

    @OnEventHandler
    public boolean onAmend(Amend event) {
        OrderRecord rec = orders.get(event.orderId());
        if (rec == null || rec.state == OrderState.CANCELLED) {
            return false;  // not found or cancelled
        }
        if (rec.quantity == event.newQuantity()) {
            return false;  // no change
        }
        rec.quantity = event.newQuantity();
        auditLog.info("orderId", event.orderId()).info("newQuantity", event.newQuantity());
        return true;
    }

    @OnEventHandler
    public boolean onCancel(Cancel event) {
        OrderRecord rec = orders.get(event.orderId());
        if (rec == null || rec.state == OrderState.CANCELLED) {
            return false;  // not found or already cancelled
        }
        rec.state = OrderState.CANCELLED;
        auditLog.info("orderId", event.orderId());
        return true;
    }

    public OrderRecord getOrder(String orderId) {
        return orders.get(orderId);
    }

    public boolean isOpen(String orderId) {
        OrderRecord rec = orders.get(orderId);
        return rec != null && rec.state == OrderState.OPEN;
    }

    public void setReleaseTimestamp(String orderId, long timestamp) {
        OrderRecord rec = orders.get(orderId);
        if (rec != null) {
            rec.releaseTimestamp = timestamp;
        }
    }

    public Long getReleaseTimestamp(String orderId) {
        OrderRecord rec = orders.get(orderId);
        return rec != null ? rec.releaseTimestamp : null;
    }

    public java.util.Set<String> getAllOrderIds() {
        return orders.keySet();
    }
}
