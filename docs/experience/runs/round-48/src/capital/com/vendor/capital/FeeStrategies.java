package com.vendor.capital;
import com.vendor.contract.FeeStrategy;
/** The fee strategies this component supports. Select one; do not write one. */
public final class FeeStrategies {
    public static final FeeStrategy DEFAULT = of("default", 0.01);
    public static final FeeStrategy PREMIUM = of("premium", 0.05);
    public static FeeStrategy byName(String name) {
        return switch (name) {
            case "premium" -> PREMIUM;
            case "default" -> DEFAULT;
            default -> throw new IllegalArgumentException("unknown fee strategy: " + name);
        };
    }
    private static FeeStrategy of(String n, double pct) {
        return new FeeStrategy() {
            public double fee(double e) { return e * pct; }
            public String name() { return n; }
        };
    }
    private FeeStrategies() {}
}
