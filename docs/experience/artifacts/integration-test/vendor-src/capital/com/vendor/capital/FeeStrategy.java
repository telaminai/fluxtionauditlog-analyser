package com.vendor.capital;
/** A strategy the OPERATOR supplies at runtime. The graph shape does not depend on which one. */
public interface FeeStrategy {
    double fee(double exposure);
    String name();
}
