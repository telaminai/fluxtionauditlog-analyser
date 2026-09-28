package com.acme.demo.event;

/** The events this demo graph accepts — the last of which the graph raises on itself. */
public final class Events {
    private Events() { }

    /** SPIKE: a JavaBean, because replay records are written and read with SnakeYAML's bean representation. */
    public static final class MarketDataEvent {
        private String symbol; private double bid; private double ask;
        public MarketDataEvent() { }
        public MarketDataEvent(String symbol, double bid, double ask) { this.symbol = symbol; this.bid = bid; this.ask = ask; }
        public String getSymbol() { return symbol; } public void setSymbol(String v) { symbol = v; }
        public double getBid() { return bid; } public void setBid(double v) { bid = v; }
        public double getAsk() { return ask; } public void setAsk(double v) { ask = v; }
        public String symbol() { return symbol; } public double bid() { return bid; } public double ask() { return ask; }
        @Override public String toString() { return "MarketDataEvent[symbol=" + symbol + ", bid=" + bid + ", ask=" + ask + "]"; }
    }

    /** SPIKE: a JavaBean, as above. */
    public static final class OrderUpdateEvent {
        private String orderId; private String state;
        public OrderUpdateEvent() { }
        public OrderUpdateEvent(String orderId, String state) { this.orderId = orderId; this.state = state; }
        public String getOrderId() { return orderId; } public void setOrderId(String v) { orderId = v; }
        public String getState() { return state; } public void setState(String v) { state = v; }
        public String orderId() { return orderId; } public String state() { return state; }
        @Override public String toString() { return "OrderUpdateEvent[orderId=" + orderId + ", state=" + state + "]"; }
    }

    /**
     * Raised <b>from inside</b> the graph by {@code riskMonitor}, not fed in from outside. It reaches the
     * dispatcher through {@code processReentrantEvent}, so it arrives in the audit log as an ordinary
     * record with no external cause — see {@link com.acme.demo.node.Nodes.RiskMonitor}.
     */
    public record RiskBreachEvent(String orderId, int liveOrders) { }
}
