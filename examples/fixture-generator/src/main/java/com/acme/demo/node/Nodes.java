package com.acme.demo.node;

import com.acme.demo.api.QuoteControl;
import com.acme.demo.event.Events;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.ExportService;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/**
 * A small quoting graph, written so the compiler emits a genuine topology and a genuine audit log.
 *
 * <p>The boolean returned by a handler or trigger is Fluxtion's <b>dirty/propagation control</b>:
 * {@code true} marks this node dirty and propagates to its dependents, {@code false} stops the branch
 * here. That is why an audit log cannot be read as a complete record of execution — a node can run,
 * decide nothing changed, and say nothing.
 */
public final class Nodes {
    private Nodes() { }

    /** Handles market data and audits what it saw. */
    public static class PriceListener extends EventLogNode {
        private double mid;

        @OnEventHandler
        public boolean marketData(Events.MarketDataEvent event) {
            mid = (event.bid() + event.ask()) / 2;
            auditLog.info("symbol", event.symbol()).info("mid", mid);
            return true;                       // propagate to downstream nodes
        }

        public double getMid() {
            return mid;
        }
    }

    /**
     * Deliberately <b>silent</b>: an ordinary node, not an {@code EventLogNode}, so it runs on every
     * market-data cycle and never appears in {@code nodeLogs}. This is the case the analyser's topology
     * view must not render as "did not run".
     */
    public static class SpreadCalculator {
        private final PriceListener prices;
        private double spread;

        public SpreadCalculator(PriceListener prices) {
            this.prices = prices;
        }

        @OnTrigger
        public boolean calculate() {
            spread = prices.getMid() * 0.0002;
            return true;
        }

        public double getSpread() {
            return spread;
        }
    }

    public static class OrderTracker extends EventLogNode {
        private int live;

        @OnEventHandler
        public boolean orderUpdate(Events.OrderUpdateEvent event) {
            live += "LIVE".equals(event.state()) ? 1 : -1;
            auditLog.info("orderId", event.orderId()).info("live", live);
            return true;
        }

        public int getLive() {
            return live;
        }
    }

    /**
     * Publishes quotes, and exports a control surface. {@code @ExportService} makes this an <b>entry
     * point</b>: an outside caller invokes {@link QuoteControl} and the call dispatches into the graph
     * exactly as an event does, which is why the topology draws it with nothing above it.
     */
    public static class QuotePublisher extends EventLogNode implements @ExportService QuoteControl {
        private final SpreadCalculator spread;
        private final OrderTracker orders;
        private boolean suspended;

        public QuotePublisher(SpreadCalculator spread, OrderTracker orders) {
            this.spread = spread;
            this.orders = orders;
        }

        @OnTrigger
        public boolean publish() {
            auditLog.info("spread", spread.getSpread())
                    .info("liveOrders", orders.getLive())
                    .info("suspended", suspended);
            return true;
        }

        @Override
        public void suspendQuoting(String reason) {
            suspended = true;
            auditLog.info("suspended", true).info("reason", reason);
        }

        @Override
        public void resumeQuoting() {
            suspended = false;
            auditLog.info("suspended", false);
        }
    }
}
