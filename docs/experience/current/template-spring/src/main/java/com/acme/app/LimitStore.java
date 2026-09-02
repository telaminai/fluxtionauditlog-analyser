package com.acme.app;

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
        return changed;
    }
}
