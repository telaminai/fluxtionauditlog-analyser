package com.bench;

/** Carries the operand that changes per event; the matrices themselves are node state. */
public class MatrixEvent {
    public double scale;
    public long seq;

    public MatrixEvent set(double scale, long seq) {
        this.scale = scale;
        this.seq = seq;
        return this;
    }
}
