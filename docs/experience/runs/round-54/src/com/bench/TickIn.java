package com.bench;
import com.telamin.fluxtion.runtime.annotations.*;
public class TickIn {
    public double bid, ask;
    @OnEventHandler public boolean onTick(MarketTick t) { bid = t.bid; ask = t.ask; return true; }
}
