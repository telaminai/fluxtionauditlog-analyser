package com.example.riskwatch;

import com.example.riskwatch.event.Trade;
import com.example.riskwatch.node.ConsoleReporter;
import com.example.riskwatch.node.LimitStore;
import com.example.riskwatch.node.MarkToMarket;
import com.example.riskwatch.node.PositionBook;
import com.example.riskwatch.node.PriceBook;
import com.example.riskwatch.node.RiskMonitor;
import com.fluxtion.compiler.Fluxtion;
import com.fluxtion.compiler.builder.dataflow.DataFlow;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.dataflow.groupby.GroupBy;
import com.fluxtion.runtime.dataflow.helpers.Aggregates;

/**
 * Builds the RiskWatch event processor.
 *
 * <p>Nothing here says what order anything runs in. The only thing declared is which node needs
 * which other node; Fluxtion derives the dispatch order from those references.
 */
public final class RiskWatchGraph {

    public static final double DEFAULT_LIMIT = 250_000;

    private RiskWatchGraph() {
    }

    /** The processor plus the node instances, so a caller (or a test) can read final state. */
    public record RiskWatch(EventProcessor<?> processor,
                            PositionBook positionBook,
                            MarkToMarket markToMarket,
                            RiskMonitor riskMonitor) {

        public void onEvent(Object event) {
            processor.onEvent(event);
        }
    }

    public static RiskWatch build(double defaultLimit) {
        PriceBook priceBook = new PriceBook();
        PositionBook positionBook = new PositionBook();
        LimitStore limitStore = new LimitStore(defaultLimit);
        MarkToMarket markToMarket = new MarkToMarket(priceBook, positionBook);
        RiskMonitor riskMonitor = new RiskMonitor(markToMarket, limitStore);
        ConsoleReporter reporter = new ConsoleReporter(markToMarket, riskMonitor);

        EventProcessor<?> processor = Fluxtion.interpret(config -> {
            // imperative half: the risk book. Adding the leaf pulls in everything it references.
            config.addNode(reporter, "consoleReporter");

            // functional half: running traded notional per trader, off the same Trade stream
            DataFlow.subscribe(Trade.class)
                    .groupBy(Trade::trader, Trade::notional, Aggregates.doubleSumFactory())
                    .map(GroupBy::toMap)
                    .console("   traded notional by trader: {}");
        });
        processor.init();
        return new RiskWatch(processor, positionBook, markToMarket, riskMonitor);
    }

    public static RiskWatch build() {
        return build(DEFAULT_LIMIT);
    }
}
