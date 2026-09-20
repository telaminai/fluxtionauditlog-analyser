package com.example.riskflow.node;

import com.example.riskflow.event.OrderEvent;
import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.audit.EventLogNode;

/** Entry point for {@link OrderEvent}; holds the order being processed this cycle. */
public class OrderIntake extends EventLogNode {

    private OrderEvent latestOrder;

    @OnEventHandler
    public boolean onOrderEvent(OrderEvent event) {
        this.latestOrder = event;
        auditLog.info("orderId", event.orderId())
                .info("symbol", event.symbol())
                .info("signedQuantity", event.signedQuantity());
        return true;
    }

    public OrderEvent getLatestOrder() {
        return latestOrder;
    }
}
