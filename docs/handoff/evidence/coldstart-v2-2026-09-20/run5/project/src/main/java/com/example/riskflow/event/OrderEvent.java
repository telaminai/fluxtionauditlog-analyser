package com.example.riskflow.event;

/** A filled order arriving from the trading desk. */
public record OrderEvent(String orderId, String symbol, Side side, int quantity) {

    public enum Side { BUY, SELL }

    /** Position delta this order applies: buys add, sells subtract. */
    public int signedQuantity() {
        return side == Side.BUY ? quantity : -quantity;
    }

    @Override
    public String toString() {
        return "OrderEvent{" + orderId + " " + side + " " + quantity + " " + symbol + "}";
    }
}
