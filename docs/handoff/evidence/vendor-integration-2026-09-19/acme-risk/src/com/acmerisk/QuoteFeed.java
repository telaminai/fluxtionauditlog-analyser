package com.acmerisk;

import com.acmerisk.api.Quote;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

import java.util.HashMap;
import java.util.Map;

/** Last price and EWMA volatility of simple returns per symbol (lambda 0.94, RiskMetrics convention). */
public class QuoteFeed extends EventLogNode implements com.telamin.fluxtion.runtime.node.NamedNode {
    @Override
    public String getName() { return "acmeQuoteFeed"; }

    static final double LAMBDA = 0.94;
    private final transient Map<String, Double> price = new HashMap<>();
    private final transient Map<String, Double> variance = new HashMap<>();

    double price(String symbol) { return price.getOrDefault(symbol, 0.0); }
    double volatility(String symbol) { return Math.sqrt(variance.getOrDefault(symbol, 0.0)); }

    @OnEventHandler
    public boolean onQuote(Quote quote) {
        Double previous = price.put(quote.symbol(), quote.price());
        if (previous != null && previous != 0) {
            double r = quote.price() / previous - 1;
            variance.merge(quote.symbol(), (1 - LAMBDA) * r * r, (old, add) -> LAMBDA * old + add);
        }
        auditLog.info("symbol", quote.symbol());
        auditLog.info("price", quote.price());
        auditLog.info("volatility", volatility(quote.symbol()));
        return true;
    }
}
