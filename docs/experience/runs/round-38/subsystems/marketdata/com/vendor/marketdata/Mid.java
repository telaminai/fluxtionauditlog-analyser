package com.vendor.marketdata;
import com.vendor.Stage; import com.vendor.Tick;
public class Mid implements Stage {
    private double value; private Tick last;
    public void onTick(Tick t) { last = t; }
    public String name() { return "marketdata.mid"; }
    public void evaluate() { if (last != null) value = (last.bid() + last.ask()) / 2; }
    public double value() { return value; }
}
