package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** R7 (EDGE): On DISPATCH, if product is hazardous and carrier does not handle hazardous. */
public class HazardBlockDecider extends EventLogNode {

    @NoTriggerReference private final OrderStore orderStore;
    @NoTriggerReference private final ProductStore productStore;
    @NoTriggerReference private final CarrierStore carrierStore;

    public HazardBlockDecider(OrderStore orderStore, ProductStore productStore, CarrierStore carrierStore) {
        this.orderStore = orderStore;
        this.productStore = productStore;
        this.carrierStore = carrierStore;
    }

    @OnEventHandler
    public boolean onDispatch(Dispatch event) {
        OrderStore.OrderRecord order = orderStore.getOrder(event.orderId());
        if (order == null) {
            return false;  // order not found
        }

        Product product = productStore.getProduct(order.sku);
        Carrier carrier = carrierStore.getCarrier(event.carrierId());

        if (product.hazardous() && !carrier.handlesHazardous()) {
            DecisionCollector.emit("HAZARD_BLOCK", event.orderId());
            auditLog.info("orderId", event.orderId()).info("blocked", true);
            return true;
        }

        auditLog.info("orderId", event.orderId()).info("blocked", false);
        return false;  // no block
    }
}
