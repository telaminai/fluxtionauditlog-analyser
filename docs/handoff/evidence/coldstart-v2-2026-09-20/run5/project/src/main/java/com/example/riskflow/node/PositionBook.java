package com.example.riskflow.node;

import com.example.riskflow.event.OrderEvent;
import com.fluxtion.runtime.annotations.OnTrigger;
import com.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.fluxtion.runtime.audit.EventLogNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Net position per symbol, accumulated from every order {@link OrderIntake} accepts. */
public class PositionBook extends EventLogNode {

    private final OrderIntake orderIntake;

    @FluxtionIgnore
    private final Map<String, Integer> positionBySymbol = new LinkedHashMap<>();

    public PositionBook(OrderIntake orderIntake) {
        this.orderIntake = orderIntake;
    }

    @OnTrigger
    public boolean onOrder() {
        OrderEvent order = orderIntake.getLatestOrder();
        int position = positionBySymbol.merge(order.symbol(), order.signedQuantity(), Integer::sum);
        auditLog.info("symbol", order.symbol()).info("netPosition", position);
        return true;
    }

    public int positionFor(String symbol) {
        return positionBySymbol.getOrDefault(symbol, 0);
    }

    public Map<String, Integer> positions() {
        return Collections.unmodifiableMap(positionBySymbol);
    }
}
