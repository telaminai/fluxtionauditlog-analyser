package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import java.util.HashMap;
import java.util.Map;

/** R1: on-hand stock per sku. Tracks previous level for stockout detection (R10). */
public class StockStore extends EventLogNode {
    private transient final Map<String, Integer> stock = new HashMap<>();
    private transient final Map<String, Integer> prevStock = new HashMap<>();
    public transient String lastSkuWithStockChange;
    public transient int lastStockBefore;
    public transient int lastStockAfter;

    @OnEventHandler
    public boolean onReceipt(Receipt event) {
        return updateStock(event.sku(), getStock(event.sku()) + event.quantity());
    }

    @OnEventHandler
    public boolean onAdjust(Adjust event) {
        return updateStock(event.sku(), getStock(event.sku()) + event.delta());
    }

    @OnEventHandler
    public boolean onCount(Count event) {
        return updateStock(event.sku(), event.countedQuantity());
    }

    private boolean updateStock(String sku, int newQuantity) {
        int prev = getStock(sku);
        if (prev == newQuantity) return false;  // no change
        stock.put(sku, newQuantity);
        lastSkuWithStockChange = sku;
        lastStockBefore = prev;
        lastStockAfter = newQuantity;
        auditLog.info("sku", sku).info("before", prev).info("after", newQuantity);
        return true;
    }

    public int getStock(String sku) {
        return stock.getOrDefault(sku, 0);
    }

    public boolean isAllocatable(String sku, int quantity) {
        return getStock(sku) >= quantity;
    }
}

