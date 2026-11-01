package com.acme.diamond.node;

import com.acme.diamond.event.X;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** Holds the latest x. Tells its children only when x is different from the value it already held. */
public class A extends EventLogNode {
    private transient int value;

    @OnEventHandler
    public boolean onX(X x) {
        boolean changed = x.value() != value;
        value = x.value();
        auditLog.info("x", x.value()).info("value", value).info("changed", changed);
        return changed;
    }

    public int value() {
        return value;
    }
}
