package com.acme.demo.event;

/** The two events this demo graph accepts. */
public final class Events {
    private Events() { }

    public record MarketDataEvent(String symbol, double bid, double ask) { }

    public record OrderUpdateEvent(String orderId, String state) { }
}
