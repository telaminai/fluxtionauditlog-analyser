package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import java.util.HashMap;
import java.util.Map;

/** R11: reference data that does not change must not trigger rules. */
public class CustomerStore extends EventLogNode {
    private transient final Map<String, Customer> customers = new HashMap<>();

    @OnEventHandler
    public boolean onCustomer(Customer event) {
        Customer prev = customers.get(event.customerId());
        if (prev != null && prev.equals(event)) {
            return false;  // R11: unchanged, do not re-evaluate
        }
        customers.put(event.customerId(), event);
        auditLog.info("customerId", event.customerId()).info("tier", event.tier())
                .info("creditLimit", event.creditLimit());
        return true;
    }

    public Customer getCustomer(String customerId) {
        return customers.getOrDefault(customerId, new Customer(customerId, "STANDARD", 0.0));
    }
}
