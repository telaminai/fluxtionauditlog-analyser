package com.a;
import com.telamin.fluxtion.runtime.annotations.*;
public class Spread {
    public double value;
    @OnEventHandler public boolean onTick(String t) { value = 1.0; return true; }
}
