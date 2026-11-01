package com.acme.diamond.node;

import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** B = floor(A / 2). Tells its children only when that value changes. */
public class B extends EventLogNode {
    private final A a;
    private transient int value;

    public B(A a) {
        this.a = a;
    }

    @OnTrigger
    public boolean recalculate() {
        int next = Math.floorDiv(a.value(), 2);
        boolean changed = next != value;
        value = next;
        auditLog.info("a", a.value()).info("value", value).info("changed", changed);
        return changed;
    }

    public int value() {
        return value;
    }
}
