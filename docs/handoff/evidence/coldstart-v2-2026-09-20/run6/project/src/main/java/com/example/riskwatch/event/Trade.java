package com.example.riskwatch.event;

/** A fill on the desk. Positive quantity is a buy, negative a sell. */
public record Trade(String trader, String symbol, int quantity, double price) {

    public double notional() {
        return Math.abs(quantity) * price;
    }
}
