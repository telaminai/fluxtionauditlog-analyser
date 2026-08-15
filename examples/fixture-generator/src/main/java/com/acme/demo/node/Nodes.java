package com.acme.demo.node;

import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.annotations.OnTrigger;
import com.fluxtion.runtime.audit.EventLogNode;

/** A small but realistic quoting pipeline, written only so the compiler emits a genuine graph. */
public class Nodes {

    public record MarketDataEvent(String symbol, double bid, double ask) { }
    public record OrderUpdateEvent(String orderId, String state) { }

    /** Takes market data in. Logs, so it appears in the audit log. */
    public static class PriceListener extends EventLogNode {
        private double mid;
        @OnEventHandler
        public boolean marketData(MarketDataEvent event) {
            mid = (event.bid() + event.ask()) / 2;
            auditLog.info("symbol", event.symbol()).info("mid", mid);
            return true;
        }
        public double getMid() { return mid; }
    }

    /** Deliberately silent: it runs, and never writes audit output. */
    public static class SpreadCalculator {
        private final PriceListener prices;
        private double spread;
        public SpreadCalculator(PriceListener prices) { this.prices = prices; }
        @OnTrigger
        public boolean calculate() { spread = prices.getMid() * 0.0002; return true; }
        public double getSpread() { return spread; }
    }

    public static class OrderTracker extends EventLogNode {
        private int live;
        @OnEventHandler
        public boolean orderUpdate(OrderUpdateEvent event) {
            live += "LIVE".equals(event.state()) ? 1 : -1;
            auditLog.info("orderId", event.orderId()).info("live", live);
            return true;
        }
        public int getLive() { return live; }
    }

    public static class QuotePublisher extends EventLogNode {
        private final SpreadCalculator spread;
        private final OrderTracker orders;
        public QuotePublisher(SpreadCalculator spread, OrderTracker orders) {
            this.spread = spread; this.orders = orders;
        }
        @OnTrigger
        public boolean publish() {
            auditLog.info("spread", spread.getSpread()).info("liveOrders", orders.getLive());
            return true;
        }
    }
}
