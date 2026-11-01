package com.acme.diamond.node;

import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** D = 100 * B + C. Counts how many times it has been run. */
public class D extends EventLogNode {
    private final B b;
    private final C c;
    private transient int value;
    private transient int invocations;

    public D(B b, C c) {
        this.b = b;
        this.c = c;
    }

    @OnTrigger
    public boolean recalculate() {
        invocations++;
        value = 100 * b.value() + c.value();
        auditLog.info("b", b.value()).info("c", c.value()).info("value", value).info("invocations", invocations);
        return true;
    }

    public int value() {
        return value;
    }

    public int invocations() {
        return invocations;
    }
}
