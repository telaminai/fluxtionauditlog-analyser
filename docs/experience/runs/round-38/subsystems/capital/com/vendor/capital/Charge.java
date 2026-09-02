package com.vendor.capital;
import com.vendor.Stage; import com.vendor.risk.Exposure;
public class Charge implements Stage {
    private final Exposure exposure; private double value;
    public Charge(Exposure exposure) { this.exposure = exposure; }
    public String name() { return "capital.charge"; }
    public void evaluate() { value = exposure.value() * 0.08; }
    public double value() { return value; }
}
