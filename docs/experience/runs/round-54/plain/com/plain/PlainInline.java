package com.plain;
import com.bench.MarketTick;

/** What an idiomatic Java author writes: one class, one method, all the arithmetic inline.
 *  Semantics identical to the Fluxtion graph, including the detector arresting the tail. */
public class PlainInline {
    public double mid, spread, ewma, vol, notional, exposure, charge, buffer;
    public double limit = 108_000.0;
    public long breaches, updates, n;

    public void onTick(MarketTick t) {
        mid      = (t.bid + t.ask) * 0.5;
        ewma     = n++ == 0 ? mid : 0.3 * mid + 0.7 * ewma;
        spread   = t.ask - t.bid;
        notional = mid * 1000.0 - spread;
        double d = mid - ewma;
        vol      = d < 0 ? -d : d;
        exposure = notional * (1.0 + vol * 0.001);
        if (exposure <= limit) return;          // the detector arrests
        breaches++;
        charge = exposure * 0.08;
        buffer = charge * 1.25;
        updates++;
    }
}
