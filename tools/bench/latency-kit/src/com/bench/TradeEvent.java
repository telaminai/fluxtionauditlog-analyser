package com.bench;

/** Second event type. Reaches the shared risk nodes by a different route than {@link MarketTick}. */
public class TradeEvent {
    public double price;
    public double qty;
    public int side;          // +1 buy, -1 sell

    public TradeEvent set(double price, double qty, int side) {
        this.price = price;
        this.qty = qty;
        this.side = side;
        return this;
    }
}
