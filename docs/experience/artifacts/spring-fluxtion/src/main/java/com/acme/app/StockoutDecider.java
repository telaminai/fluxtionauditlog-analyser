package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** R10 (EDGE): On the event that takes a sku's on-hand from >= 0 to < 0. */
public class StockoutDecider extends EventLogNode {

    private final StockStore stockStore;

    public StockoutDecider(StockStore stockStore) {
        this.stockStore = stockStore;
    }

    @OnTrigger
    public boolean trigger() {
        // StockStore exposes the last stock before/after
        // Check if we went from >= 0 to < 0
        if (stockStore.lastStockBefore >= 0 && stockStore.lastStockAfter < 0) {
            DecisionCollector.emit("STOCKOUT", stockStore.lastSkuWithStockChange);
            auditLog.info("sku", stockStore.lastSkuWithStockChange).info("before", stockStore.lastStockBefore)
                    .info("after", stockStore.lastStockAfter).info("stockout", true);
            return true;
        }

        if (stockStore.lastSkuWithStockChange != null) {
            auditLog.info("sku", stockStore.lastSkuWithStockChange).info("before", stockStore.lastStockBefore)
                    .info("after", stockStore.lastStockAfter).info("stockout", false);
        }
        return false;  // no stockout
    }
}
