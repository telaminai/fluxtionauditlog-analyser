package com.bench;

/** Third event type: a rare control event that touches exactly one node and nothing downstream. */
public class LimitEvent {
    public double newLimit;

    public LimitEvent set(double newLimit) {
        this.newLimit = newLimit;
        return this;
    }
}
