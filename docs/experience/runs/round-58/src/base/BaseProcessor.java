package com.benchv;
import com.bench.MarketTick;
/** What the generator emits with void triggers + setSupportDirtyFiltering(false):
 *  total order, unconditional, no dirty flags, no auditors, no re-entrancy wrapper. */
public class BaseProcessor {
    public final Nodes.TickIn   tickIn   = new Nodes.TickIn();
    public final Nodes.Mid      mid      = new Nodes.Mid(tickIn);
    public final Nodes.Spread   spread   = new Nodes.Spread(tickIn);
    public final Nodes.Ewma     ewma     = new Nodes.Ewma(mid);
    public final Nodes.Vol      vol      = new Nodes.Vol(mid, ewma);
    public final Nodes.Notional notional = new Nodes.Notional(mid, spread);
    public final Nodes.Exposure exposure = new Nodes.Exposure(notional, vol);
    public final Nodes.Limit    limit    = new Nodes.Limit(exposure);
    public final Nodes.Charge   charge   = new Nodes.Charge(exposure);
    public final Nodes.Buffer   buffer   = new Nodes.Buffer(charge);
    public void handleEvent(MarketTick e) {
        tickIn.onTick(e);
        mid.calc();
        ewma.calc();
        spread.calc();
        notional.calc();
        vol.calc();
        exposure.calc();
        limit.calc();
        charge.calc();
        buffer.calc();
    }
}
