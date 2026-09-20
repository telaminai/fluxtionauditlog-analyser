package com.example.riskwatch.node;

import com.example.riskwatch.node.MarkToMarket.SymbolRisk;
import com.fluxtion.runtime.annotations.OnTrigger;

/**
 * Prints the marked book. Declared downstream of the monitor as well as the valuation so that any
 * alert for an event is printed above the table it belongs to.
 */
public class ConsoleReporter {

    private final MarkToMarket markToMarket;
    private final RiskMonitor riskMonitor;

    public ConsoleReporter(MarkToMarket markToMarket, RiskMonitor riskMonitor) {
        this.markToMarket = markToMarket;
        this.riskMonitor = riskMonitor;
    }

    @OnTrigger
    public boolean print() {
        StringBuilder sb = new StringBuilder();
        sb.append("   ┌───────┬────────┬───────────┬──────────┬──────────────┬──────────────┐\n");
        sb.append("   │ sym   │    qty │  avg cost │    price │     exposure │  unreal P&L  │\n");
        sb.append("   ├───────┼────────┼───────────┼──────────┼──────────────┼──────────────┤\n");
        for (SymbolRisk r : markToMarket.getRisk()) {
            sb.append(String.format("   │ %-5s │ %6d │ %9.2f │ %8.2f │ %,12.0f │ %,12.2f │%s%n",
                    r.symbol(), r.quantity(), r.averageCost(), r.price(), r.exposure(),
                    r.unrealisedPnl(),
                    riskMonitor.getBreachedSymbols().contains(r.symbol()) ? "  <-- OVER LIMIT" : ""));
        }
        sb.append("   └───────┴────────┴───────────┴──────────┴──────────────┴──────────────┘\n");
        sb.append(String.format("   total unrealised P&L: %,.2f%n", markToMarket.getTotalPnl()));
        System.out.print(sb);
        return true;
    }
}
