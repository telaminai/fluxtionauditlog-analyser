package com.acme.fx;

/** A currency-pair rate published by the ACME FX feed. DEMO supplier library. */
public final class FxQuote {
    private final String pair;
    private final double rate;

    public FxQuote(String pair, double rate) {
        this.pair = pair;
        this.rate = rate;
    }

    public String pair() { return pair; }
    public double rate() { return rate; }

    @Override
    public String toString() { return "FxQuote{pair=" + pair + ", rate=" + rate + "}"; }
}
