package com.acme.fx;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

import java.util.Map;
import java.util.TreeMap;

/** Keeps the latest rate for each currency pair and records it in the audit log. DEMO supplier library. */
public class FxRateBook extends EventLogNode {
    private final transient Map<String, Double> latest = new TreeMap<>();

    @OnEventHandler
    public boolean onFxQuote(FxQuote quote) {
        latest.put(quote.pair(), quote.rate());
        auditLog.info("pair", quote.pair());
        auditLog.info("rate", quote.rate());
        auditLog.info("pairs", latest.size());
        return true;
    }

    public Double latestRate(String pair) { return latest.get(pair); }
}
