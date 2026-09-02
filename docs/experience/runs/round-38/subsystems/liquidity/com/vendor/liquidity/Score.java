package com.vendor.liquidity;
import com.vendor.Stage; import com.vendor.pricing.Adjusted;
public class Score implements Stage {
    private final Adjusted adjusted; private double value;
    public Score(Adjusted adjusted) { this.adjusted = adjusted; }
    public String name() { return "liquidity.score"; }
    public void evaluate() { value = adjusted.value() / 10; }
    public double value() { return value; }
}
