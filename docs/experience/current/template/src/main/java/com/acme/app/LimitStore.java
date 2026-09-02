package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.AfterTrigger;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** Holds the current limit. Returning false for an unchanged republish stops the cycle here. */
public class LimitStore extends EventLogNode {

    public transient double threshold = 100;

    @OnEventHandler
    public boolean onLimit(Limit limit) {
        boolean changed = limit.threshold() != threshold;
        threshold = limit.threshold();
        auditLog.info("threshold", threshold).info("changed", changed);
        Cycle.evaluated("limitStore");
        return changed;
    }

    /** After-event phase; see SensorState.commit(). */
    @AfterTrigger
    public void commit() {
        auditLog.info("commit", "limitStore");
        Cycle.committed("limitStore");
    }
}
