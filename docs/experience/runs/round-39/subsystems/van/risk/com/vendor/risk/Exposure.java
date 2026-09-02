package com.vendor.risk;
import com.vendor.liquidity.Score;
public class Exposure {
    private final Notional notional; private final Score score;
    public double value;
    public Exposure(Notional notional, Score score) { this.notional = notional; this.score = score; }
    public boolean calc() {
        value = notional.value * (1 + score.value / 1000);
        com.vendor.Audit.log("risk.exposure", value); return true; }
}
