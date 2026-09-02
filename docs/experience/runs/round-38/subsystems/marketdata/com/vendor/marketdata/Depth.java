package com.vendor.marketdata;
import com.vendor.Stage; import com.vendor.Tick;
public class Depth implements Stage {
    private double value; private Tick last;
    public void onTick(Tick t) { last = t; }
    public String name() { return "marketdata.depth"; }
    public void evaluate() { if (last != null) value = (last.ask() - last.bid()) * 100; }
    public double value() { return value; }
}
