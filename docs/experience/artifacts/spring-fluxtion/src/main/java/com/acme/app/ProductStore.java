package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import java.util.HashMap;
import java.util.Map;

/** R11: reference data that does not change must not trigger rules. */
public class ProductStore extends EventLogNode {
    private transient final Map<String, Product> products = new HashMap<>();

    @OnEventHandler
    public boolean onProduct(Product event) {
        Product prev = products.get(event.sku());
        if (prev != null && prev.equals(event)) {
            return false;  // R11: unchanged, do not re-evaluate
        }
        products.put(event.sku(), event);
        auditLog.info("sku", event.sku()).info("unitPrice", event.unitPrice())
                .info("hazardous", event.hazardous());
        return true;
    }

    public Product getProduct(String sku) {
        return products.getOrDefault(sku, new Product(sku, 0.0, false));
    }
}
