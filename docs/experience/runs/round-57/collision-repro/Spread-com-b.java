package com.b;
import com.telamin.fluxtion.runtime.annotations.*;
public class Spread {
    public double value;
    @OnEventHandler public boolean onTick(Integer t) { value = 2.0; return true; }
}
