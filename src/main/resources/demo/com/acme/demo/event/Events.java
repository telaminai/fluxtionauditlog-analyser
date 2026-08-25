package com.acme.demo.event;

/** The events this demo graph accepts — the last of which the graph raises on itself. */
public final class Events {
    private Events() { }

    public record MarketDataEvent(String symbol, double bid, double ask) { }

    public record OrderUpdateEvent(String orderId, String state) { }

    /**
     * Raised <b>from inside</b> the graph by {@code riskMonitor}, not fed in from outside. It reaches the
     * dispatcher through {@code processReentrantEvent}, so it arrives in the audit log as an ordinary
     * record with no external cause — see {@link com.acme.demo.node.Nodes.RiskMonitor}.
     */
    public record RiskBreachEvent(String orderId, int liveOrders) { }
}
