package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/**
 * A node's PARENTS ARE ITS FIELDS. {@code sensorState} is a field, so it is a parent, so this node
 * is triggered when it propagates. A constructor argument you do not keep as a field is NOT a
 * parent, and the generator rejects the class with
 * {@code FLX-1001: cannot find a matching constructor}.
 */
public class ThresholdAlert extends EventLogNode {

    private final SensorState sensorState;

    public ThresholdAlert(SensorState sensorState) {
        this.sensorState = sensorState;
    }

    @OnTrigger
    public boolean onValueChanged() {
        boolean high = sensorState.last > 100;
        auditLog.info("value", sensorState.last).info("alert", high);
        return high;
    }
}
