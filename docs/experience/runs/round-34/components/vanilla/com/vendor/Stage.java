package com.vendor;
/** VENDOR — every internal stage of a component implements this. */
public interface Stage {
    String name();
    void evaluate();
    double value();
}
