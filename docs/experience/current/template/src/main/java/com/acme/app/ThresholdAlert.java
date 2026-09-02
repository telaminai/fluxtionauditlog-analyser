package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/**
 * A node's PARENTS ARE ITS FIELDS, so both fields below are parents. But they play different roles,
 * and getting that distinction wrong is the commonest correctness bug in this framework.
 *
 * <p><b>{@code sensorState} is the trigger.</b> A new reading is what should make this node evaluate.
 *
 * <p><b>{@code limitStore} is data only</b> — this node reads the limit, but a limit arriving must not
 * make it evaluate. Without {@link NoTriggerReference} it would: a plain field reference IS a trigger,
 * so a `Limit` event would run this node with no new reading, and it would re-judge the *previous*
 * reading. That produces alerts on cycles where nothing was measured. The build stays green and the
 * tests pass; only the audit log shows it.
 *
 * <p>The inverse annotation, {@code @TriggerEventOverride}, is worth knowing when a node has one
 * trigger and several data parents: put it on the single triggering field and every other field is
 * treated as {@code @NoTriggerReference} automatically.
 */
public class ThresholdAlert extends EventLogNode {

    /** "Did it just go over?" — one EdgeDetector per rule, keyed by subject. */
    private final transient EdgeDetector breached = new EdgeDetector();


    private final SensorState sensorState;                 // trigger: a new reading
    @NoTriggerReference private final LimitStore limitStore;  // data only: read, never triggers

    public ThresholdAlert(SensorState sensorState, LimitStore limitStore) {
        this.sensorState = sensorState;
        this.limitStore = limitStore;
    }

    /** Returning false ARRESTS the cycle — nothing downstream of this node runs. */
    @OnTrigger
    public boolean onValueChanged() {
        boolean over = sensorState.last > limitStore.threshold;
        // EDGE: fires on the reading that first goes over, not on every reading while it stays over
        boolean breach = breached.roseFor(sensorState.lastSensorId, over);
        if (breach) Decisions.add("ALERT", sensorState.lastSensorId);
        auditLog.info("value", sensorState.last).info("threshold", limitStore.threshold)
                .info("over", over).info("alert", breach);
        return breach;
    }
}
