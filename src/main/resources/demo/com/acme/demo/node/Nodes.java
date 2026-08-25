package com.acme.demo.node;

import com.acme.demo.api.QuoteControl;
import com.acme.demo.event.Events;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.ExportService;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.telamin.fluxtion.runtime.node.SingleNamedNode;

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
     * Raises an event <b>on the graph itself</b> when too many orders are live.
     *
     * <p>{@code processReentrantEvent} does not dispatch inline. The event is queued, the current cycle
     * finishes and <em>publishes its audit record</em>, and only then is the queued event dispatched —
     * into a brand new cycle with its own record. So a re-dispatch appears in the log as a separate
     * {@code eventLogRecord} that looks exactly like one fed in from outside, even though nothing outside
     * the processor sent it. Reading the log alone, the only clue is that the graph contains a node able
     * to raise it.
     */
    public static class RiskMonitor extends SingleNamedNode {
        private final OrderTracker orders;
        private final int limit;

        /**
         * Constructor arguments must correspond to the mapped fields, in order — that is how the generator
         * reconstructs the node in source. {@code SingleNamedNode}'s own {@code name} is
         * {@code @FluxtionIgnore}d, so it is <b>not</b> one of them and has to be supplied here rather than
         * taken as a parameter.
         */
        public RiskMonitor(OrderTracker orders, int limit) {
            super("riskMonitor");
            this.orders = orders;
            this.limit = limit;
        }

        @OnTrigger
        public boolean checkLimit() {
            if (orders.getLive() < limit) {
                return false;                  // under the limit: stop the branch here
            }
            auditLog.info("liveOrders", orders.getLive()).info("limit", limit).info("redispatch", true);
            processReentrantEvent(new Events.RiskBreachEvent("ord-" + orders.getLive(), orders.getLive()));
            return true;
        }
    }

    /** Handles the event {@link RiskMonitor} raised — the far side of a re-dispatch. */
    public static class BreachHandler extends EventLogNode {
        private int breaches;

        @OnEventHandler
        public boolean onBreach(Events.RiskBreachEvent event) {
            breaches++;
            auditLog.info("breachedOn", event.orderId())
                    .info("liveOrders", event.liveOrders())
                    .info("breachesToday", breaches);
            return true;
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
