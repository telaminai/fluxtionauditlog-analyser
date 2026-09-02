package com.acme.app;

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
        return changed;
    }
}
