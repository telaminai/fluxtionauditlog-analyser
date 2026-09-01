package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/**
 * A node's PARENTS ARE ITS FIELDS. {@code priceBook} is a field, so it is a parent, so this node
 * is triggered when it propagates. A constructor argument you do not keep as a field is NOT a
 * parent, and the generator rejects the class with
 * {@code FLX-1001: cannot find a matching constructor}.
 */
public class Alerter extends EventLogNode {

    private final PriceBook priceBook;

    public Alerter(PriceBook priceBook) {
        this.priceBook = priceBook;
    }

    @OnTrigger
    public boolean onPriceChanged() {
        boolean high = priceBook.last > 100;
        auditLog.info("price", priceBook.last).info("alert", high);
        return high;
    }
}
