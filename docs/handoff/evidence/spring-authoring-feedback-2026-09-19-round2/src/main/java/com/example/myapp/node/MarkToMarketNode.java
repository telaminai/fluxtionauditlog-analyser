package com.example.myapp.node;

import java.util.HashMap;
import java.util.Map;

/**
 * Marks positions to market. PnL per symbol is net cash plus position valued at the
 * latest mark; the mark is the last PriceUpdate, or the last trade price until one arrives.
 */
public class MarkToMarketNode extends com.telamin.fluxtion.runtime.audit.EventLogNode {

    public MarkToMarketNode(com.example.myapp.node.PositionNode arg0, com.example.myapp.node.RootNode arg1) {
        this.positionNode = arg0;
        this.rootNode = arg1;
    }

    private final com.example.myapp.node.PositionNode positionNode;

    private final com.example.myapp.node.RootNode rootNode;

    private final transient Map<String, Double> marks = new HashMap<>();
    private double totalPnl;

    public double getTotalPnl() {
        return totalPnl;
    }

    public double getPnl(String symbol) {
        Double mark = marks.containsKey(symbol) ? marks.get(symbol) : positionNode.getLastTradePrice(symbol);
        double marketValue = mark == null ? 0.0 : positionNode.getPosition(symbol) * mark;
        return positionNode.getCash(symbol) + marketValue;
    }

    @com.telamin.fluxtion.runtime.annotations.OnTrigger
@javax.annotation.processing.Generated("fluxtion-starter")
public boolean onMarkToMarketNode() {
    if (rootNode.getLatestEvent() instanceof com.example.myapp.event.PriceUpdate price) {
        marks.put(price.symbol(), price.price());
    }
    double pnl = 0;
    for (String symbol : positionNode.getPositions().keySet()) {
        double symbolPnl = getPnl(symbol);
        auditLog.info("pnl_" + symbol, symbolPnl);
        pnl += symbolPnl;
    }
    totalPnl = pnl;
    auditLog.info("openSymbols", positionNode.getPositions().size());
    auditLog.info("totalPnl", totalPnl);
    return true;
}
}
