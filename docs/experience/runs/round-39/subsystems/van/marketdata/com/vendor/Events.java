package com.vendor;
/** The four shared event types. Every one is consumed by at least two subsystems. */
public class Events {
    public record Tick(String symbol, double bid, double ask) {}
    public record Trade(String symbol, double qty, double price) {}
    public record Rate(String ccy, double rate) {}
    public record Config(String key, double value) {}
}
