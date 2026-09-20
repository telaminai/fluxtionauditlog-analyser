package com.example.riskwatch.event;

/** A new mid price for a symbol arriving from the market data feed. */
public record MarketTick(String symbol, double price) {
}
