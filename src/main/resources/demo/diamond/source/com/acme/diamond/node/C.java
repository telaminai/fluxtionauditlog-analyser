package com.acme.diamond.node;

import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** C = 3 * A. Tells its children only when that value changes. */
public class C extends EventLogNode {
    private final A a;
    private transient int value;

    public C(A a) {
        this.a = a;
    }

    @OnTrigger
    public boolean recalculate() {
        int next = 3 * a.value();
        boolean changed = next != value;
        value = next;
        auditLog.info("a", a.value()).info("value", value).info("changed", changed);
        return changed;
    }

    public int value() {
        return value;
    }
}
