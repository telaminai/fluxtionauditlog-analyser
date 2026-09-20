package com.example.riskflow.event;

/** A top-of-book quote for a symbol. */
public record PriceUpdate(String symbol, double bid, double ask) {

    public double mid() {
        return (bid + ask) / 2;
    }

    @Override
    public String toString() {
        return "PriceUpdate{" + symbol + " " + bid + "/" + ask + "}";
    }
}
