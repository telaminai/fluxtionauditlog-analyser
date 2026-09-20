package com.example.riskwatch;

import com.example.riskwatch.RiskWatchGraph.RiskWatch;

import java.util.List;

/** Feeds the scripted session through the graph, one event at a time. */
public class RiskWatchMain {

    public static void main(String[] args) {
        System.out.printf("RiskWatch — default gross exposure limit %,.0f%n%n",
                RiskWatchGraph.DEFAULT_LIMIT);

        RiskWatch riskWatch = RiskWatchGraph.build();
        List<Object> session = EventFeed.scriptedSession();

        for (int i = 0; i < session.size(); i++) {
            Object event = session.get(i);
            System.out.printf("%n[%02d] %s%n", i + 1, EventFeed.describe(event));
            riskWatch.onEvent(event);
        }

        System.out.printf("%n%s%n", "-".repeat(72));
        System.out.printf("events fed        : %d%n", session.size());
        System.out.printf("trades booked     : %d%n", riskWatch.positionBook().getTradeCount());
        System.out.printf("book revaluations : %d%n", riskWatch.markToMarket().getRevaluations());
        System.out.printf("total unreal P&L  : %,.2f%n", riskWatch.markToMarket().getTotalPnl());
        System.out.println("risk alerts       :");
        riskWatch.riskMonitor().getAlerts().forEach(a -> System.out.println("    " + a));
    }
}
