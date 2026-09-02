package com.vendor;
/** Every subsystem stage implements this. */
public interface Stage {
    String name();
    void evaluate();
    double value();
}
