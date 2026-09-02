package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.AfterTrigger;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/**
 * A NODE is an ordinary Java class. Extend EventLogNode to get {@code auditLog}.
 *
 * <p>Two rules the generator enforces:
 * <ul>
 *   <li>every field that is not a parent must be {@code transient}</li>
 *   <li>the class and its constructor must be {@code public}</li>
 * </ul>
 */
public class SensorState extends EventLogNode {

    public transient double last;
    public transient String lastSensorId;          // transient: node-local state, not a parent

    /** Returning false STOPS the cycle here — nothing downstream runs. */
    @OnEventHandler
    public boolean onReading(Reading reading) {
        boolean changed = reading.value() != last;
        last = reading.value();
        lastSensorId = reading.sensorId();
        auditLog.info("sensorId", reading.sensorId()).info("value", reading.value()).info("changed", changed);
        Cycle.evaluated("sensorState");
        return changed;
    }

    /**
     * The AFTER-EVENT phase. Runs once the whole event-in phase is finished, and — this is the point —
     * in REVERSE topological order, so a node commits after everything downstream of it has committed.
     *
     * <p>{@code @AfterTrigger} only fires when this node was actually on the execution path. A cycle
     * this node did not participate in does not commit it, which is what makes the commit record an
     * honest statement of what happened rather than a list of everything that exists.
     */
    @AfterTrigger
    public void commit() {
        auditLog.info("commit", "sensorState");
        Cycle.committed("sensorState");
    }
}
