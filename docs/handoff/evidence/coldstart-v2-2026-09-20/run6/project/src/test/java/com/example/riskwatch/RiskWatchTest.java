package com.example.riskwatch;

import com.example.riskwatch.RiskWatchGraph.RiskWatch;
import com.example.riskwatch.event.MarketTick;
import com.example.riskwatch.node.MarkToMarket.SymbolRisk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskWatchTest {

    private static RiskWatch runScriptedSession() {
        RiskWatch riskWatch = RiskWatchGraph.build();
        EventFeed.scriptedSession().forEach(riskWatch::onEvent);
        return riskWatch;
    }

    @Test
    void booksPositionsAndCostBasis() {
        RiskWatch riskWatch = runScriptedSession();

        assertEquals(5, riskWatch.positionBook().getTradeCount());
        assertEquals(2000, riskWatch.positionBook().quantityOf("ACME"));
        assertEquals(-6000, riskWatch.positionBook().quantityOf("BETA"));
        // (100.00 * 1000 + 103.00 * 1500) / 2500, untouched by the later sell
        assertEquals(101.80, riskWatch.positionBook().averageCostOf("ACME"), 1e-9);
        assertEquals(290_000d / 6000, riskWatch.positionBook().averageCostOf("BETA"), 1e-9);
    }

    @Test
    void marksTheBookAgainstLatestPrices() {
        RiskWatch riskWatch = runScriptedSession();
        List<SymbolRisk> risk = riskWatch.markToMarket().getRisk();

        assertEquals(2, risk.size());
        assertEquals(16_400, risk.get(0).unrealisedPnl(), 1e-9);   // (110.00 - 101.80) * 2000
        assertEquals(50_000, risk.get(1).unrealisedPnl(), 1e-9);   // (40.00 - 48.333..) * -6000
        assertEquals(66_400, riskWatch.markToMarket().getTotalPnl(), 1e-9);
    }

    @Test
    void raisesAnAlertOnEachLimitCrossing() {
        RiskWatch riskWatch = runScriptedSession();

        assertEquals(List.of(
                        "BREACH  ACME  exposure 256,250 > limit 250,000",
                        "CLEARED ACME  exposure 256,250 <= limit 400,000",
                        "BREACH  BETA  exposure 285,000 > limit 250,000",
                        "CLEARED BETA  exposure 240,000 <= limit 250,000"),
                riskWatch.riskMonitor().getAlerts());
        assertTrue(riskWatch.riskMonitor().getBreachedSymbols().isEmpty());
    }

    @Test
    void repeatedPriceDoesNotRevalueTheBook() {
        RiskWatch riskWatch = RiskWatchGraph.build();
        EventFeed.scriptedSession().forEach(riskWatch::onEvent);

        int revaluations = riskWatch.markToMarket().getRevaluations();
        riskWatch.onEvent(new MarketTick("BETA", 40.00)); // same price as the last tick
        assertEquals(revaluations, riskWatch.markToMarket().getRevaluations());

        riskWatch.onEvent(new MarketTick("BETA", 41.00));
        assertEquals(revaluations + 1, riskWatch.markToMarket().getRevaluations());
    }

    @Test
    void replayingTheSameFeedGivesTheSameResult() {
        RiskWatch first = runScriptedSession();
        RiskWatch second = runScriptedSession();

        assertEquals(first.riskMonitor().getAlerts(), second.riskMonitor().getAlerts());
        assertEquals(first.markToMarket().getTotalPnl(), second.markToMarket().getTotalPnl());
        assertEquals(first.markToMarket().getRevaluations(),
                second.markToMarket().getRevaluations());
    }
}
