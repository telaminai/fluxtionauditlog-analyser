package com.vendor.risk;
import com.vendor.Stage; import com.vendor.liquidity.Score;
public class Exposure implements Stage {
    private final Notional notional; private final Score score; private double value;
    public Exposure(Notional notional, Score score) { this.notional = notional; this.score = score; }
    public String name() { return "risk.exposure"; }
    public void evaluate() { value = notional.value() * (1 + score.value() / 1000); }
    public double value() { return value; }
}
