package com.vendor;
public class Events {
    public record Tick(String symbol, double bid, double ask) {}
    public record Trade(String symbol, double qty, double price) {}
    public record Rate(String ccy, double rate) {}
    public record Config(String key, double value) {}
}
