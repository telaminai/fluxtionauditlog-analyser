package com.benchv;

import com.bench.LimitEvent;
import com.bench.MarketTick;
import com.bench.TradeEvent;

/**
 * Hand-rolled equivalent of the three-event-type graph: one object, primitive fields, one method per
 * event type. The floor the generated processor is measured against.
 *
 * <p><b>The firing order below was READ OFF the generated source, not reasoned about.</b> Two of the
 * orderings are not what a person would write by hand and both would have produced a silently
 * different number:
 * <ul>
 *   <li>the tick path fires {@code mid, ewma, spread, notional, vol} — not mid/spread/ewma/vol/notional;</li>
 *   <li>the shared tail fires {@code exposure, charge, buffer, limit} — {@code limit} last, not
 *       straight after {@code exposure}.</li>
 * </ul>
 * Both are topologically valid, and the arithmetic happens to be insensitive to them here — but that
 * is a fact to be checked by the correctness harness, not assumed. The generator also factors the
 * tail shared by the tick and trade paths into one method rather than emitting it twice.
 */
public class HandMulti {
    // tick path
    public double bid, ask, mid, spread, ewma, vol, notional;
    private long ewmaN;
    // trade path
    public double tradePrice, tradeQty, net, traded;
    public int tradeSide;
    public long fills;
    // control
    public double limit = 108_000.0;
    // shared tail
    public double exposure, charge, buffer;
    public long breaches, updates;

    public void onTick(MarketTick t) {
        bid = t.bid;
        ask = t.ask;
        mid = (bid + ask) * 0.5;
        ewma = ewmaN++ == 0 ? mid : 0.3 * mid + 0.7 * ewma;
        spread = ask - bid;
        notional = mid * 1000.0 - spread;
        double d = mid - ewma;
        vol = d < 0 ? -d : d;
        tail();
    }

    public void onTrade(TradeEvent t) {
        tradePrice = t.price;
        tradeQty = t.qty;
        tradeSide = t.side;
        net += tradeSide * tradeQty;
        traded += tradePrice * tradeQty;
        fills++;
        tail();
    }

    public void onLimit(LimitEvent e) {
        limit = e.newLimit;
        if (exposure > limit) {
            breaches++;
        }
    }

    private void tail() {
        exposure = notional * (1.0 + vol * 0.001) + net * 10.0;
        charge = exposure * 0.08;
        buffer = charge * 1.25;
        updates++;
        if (exposure > limit) {
            breaches++;
        }
    }
}
