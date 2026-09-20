package com.example.riskwatch;

import com.example.riskwatch.event.MarketTick;
import com.example.riskwatch.event.RiskLimitUpdate;
import com.example.riskwatch.event.Trade;

import java.util.List;

/**
 * A fixed morning of events. Scripted rather than random so the printed output can be checked by
 * hand and replayed to the same result — see {@code RiskWatchTest}.
 */
public final class EventFeed {

    private EventFeed() {
    }

    public static List<Object> scriptedSession() {
        return List.of(
                new MarketTick("ACME", 100.00),
                new Trade("alice", "ACME", 1000, 100.00),
                new MarketTick("ACME", 102.50),
                new MarketTick("BETA", 50.00),
                new Trade("bob", "BETA", -2000, 50.00),
                // takes ACME exposure to 256,250 against the 250,000 default limit
                new Trade("alice", "ACME", 1500, 103.00),
                new MarketTick("BETA", 47.50),
                // risk desk raises the ACME limit, which clears the breach with no new trade
                new RiskLimitUpdate("ACME", 400_000),
                // same price as the last ACME tick: PriceBook stops it, nothing downstream runs
                new MarketTick("ACME", 102.50),
                new Trade("bob", "ACME", -500, 101.00),
                new MarketTick("ACME", 110.00),
                // BETA short grows to 6,000 and breaches
                new Trade("carol", "BETA", -4000, 47.50),
                // BETA sells off, exposure drops back under the limit
                new MarketTick("BETA", 40.00));
    }

    public static String describe(Object event) {
        if (event instanceof MarketTick t) {
            return String.format("TICK   %-5s %8.2f", t.symbol(), t.price());
        }
        if (event instanceof Trade t) {
            return String.format("TRADE  %-5s %+6d @ %8.2f  (%s)", t.symbol(), t.quantity(),
                    t.price(), t.trader());
        }
        if (event instanceof RiskLimitUpdate t) {
            return String.format("LIMIT  %-5s max exposure %,.0f", t.symbol(), t.maxExposure());
        }
        return event.toString();
    }
}
