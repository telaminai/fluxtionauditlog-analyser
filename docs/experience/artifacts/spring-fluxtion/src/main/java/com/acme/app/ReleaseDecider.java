package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import java.util.HashMap;
import java.util.Map;

/** R6 (EDGE): Emit RELEASE when an order first becomes both allocatable and credit-ok. */
public class ReleaseDecider extends EventLogNode {

    private final OrderStore orderStore;
    @NoTriggerReference private final StockStore stockStore;
    @NoTriggerReference private final PaidStore paidStore;
    @NoTriggerReference private final ProductStore productStore;
    @NoTriggerReference private final CustomerStore customerStore;

    private transient final Map<String, Boolean> lastReleasable = new HashMap<>();
    private transient long lastEventTimestamp = 0;

    public ReleaseDecider(OrderStore orderStore, StockStore stockStore, PaidStore paidStore, ProductStore productStore, CustomerStore customerStore) {
        this.orderStore = orderStore;
        this.stockStore = stockStore;
        this.paidStore = paidStore;
        this.productStore = productStore;
        this.customerStore = customerStore;
    }

    @OnEventHandler
    public boolean onOrder(Order event) {
        lastEventTimestamp = event.timestampMs();
        return checkAndEmit(event.orderId());
    }

    @OnEventHandler
    public boolean onAmend(Amend event) {
        lastEventTimestamp = event.timestampMs();
        return checkAndEmit(event.orderId());
    }

    @OnEventHandler
    public boolean onCancel(Cancel event) {
        lastEventTimestamp = event.timestampMs();
        lastReleasable.remove(event.orderId());
        return false;  // cancellation doesn't trigger release check
    }

    @OnEventHandler
    public boolean onReceipt(Receipt event) {
        lastEventTimestamp = event.timestampMs();
        return checkAllOrders();
    }

    @OnEventHandler
    public boolean onAdjust(Adjust event) {
        lastEventTimestamp = event.timestampMs();
        return checkAllOrders();
    }

    @OnEventHandler
    public boolean onCount(Count event) {
        lastEventTimestamp = event.timestampMs();
        return checkAllOrders();
    }

    @OnEventHandler
    public boolean onPaid(Paid event) {
        lastEventTimestamp = event.timestampMs();
        return checkAndEmit(event.orderId());
    }

    @OnEventHandler
    public boolean onPayfail(Payfail event) {
        lastEventTimestamp = event.timestampMs();
        return checkAndEmit(event.orderId());
    }

    @OnEventHandler
    public boolean onProduct(Product event) {
        // Product events don't have timestampMs
        return checkAllOrders();
    }

    @OnEventHandler
    public boolean onCustomer(Customer event) {
        // Customer events don't have timestampMs
        return checkAllOrders();
    }

    private boolean checkAndEmit(String orderId) {
        if (!orderStore.isOpen(orderId)) {
            lastReleasable.remove(orderId);
            return false;
        }

        boolean nowReleasable = isReleasable(orderId);
        boolean wasReleasable = lastReleasable.getOrDefault(orderId, false);

        if (!wasReleasable && nowReleasable) {
            DecisionCollector.emit("RELEASE", orderId);
            orderStore.setReleaseTimestamp(orderId, lastEventTimestamp);
            auditLog.info("orderId", orderId).info("released", true);
        }

        lastReleasable.put(orderId, nowReleasable);
        return nowReleasable;
    }

    private boolean checkAllOrders() {
        boolean anyChanged = false;
        for (String orderId : orderStore.getAllOrderIds()) {
            if (checkAndEmit(orderId)) {
                anyChanged = true;
            }
        }
        return anyChanged;
    }

    private boolean isReleasable(String orderId) {
        OrderStore.OrderRecord order = orderStore.getOrder(orderId);
        if (order == null || !orderStore.isOpen(orderId)) {
            return false;
        }

        // R4: allocatable
        boolean allocatable = stockStore.getStock(order.sku) >= order.quantity;

        // R5: credit-ok
        double paidAmount = paidStore.getPaidAmount(orderId);
        double orderTotal = order.quantity * productStore.getProduct(order.sku).unitPrice();

        Customer customer = customerStore.getCustomer(order.customerId);
        boolean creditOk = paidAmount >= orderTotal ||
                          (customer.tier().equals("GOLD") && orderTotal <= customer.creditLimit());

        return allocatable && creditOk;
    }
}
