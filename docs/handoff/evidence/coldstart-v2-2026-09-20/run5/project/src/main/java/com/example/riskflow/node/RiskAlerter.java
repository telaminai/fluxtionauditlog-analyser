package com.example.riskflow.node;

import com.fluxtion.runtime.annotations.OnTrigger;
import com.fluxtion.runtime.annotations.builder.AssignToField;
import com.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.fluxtion.runtime.audit.EventLogNode;
import com.fluxtion.runtime.output.SinkPublisher;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Terminal node: publishes a line to the "alerts" sink when a limit is crossed, in either
 * direction. Only transitions are published — a book that stays over its limit does not
 * re-alert on every tick.
 */
public class RiskAlerter extends EventLogNode {

    private static final String TOTAL = "*TOTAL*";

    private final ExposureCalculator exposureCalculator;
    private final SinkPublisher<String> alertPublisher;
    private final double symbolLimit;
    private final double totalLimit;

    @FluxtionIgnore
    private final Set<String> breached = new HashSet<>();

    public RiskAlerter(ExposureCalculator exposureCalculator,
                       SinkPublisher<String> alertPublisher,
                       @AssignToField("symbolLimit") double symbolLimit,
                       @AssignToField("totalLimit") double totalLimit) {
        this.exposureCalculator = exposureCalculator;
        this.alertPublisher = alertPublisher;
        this.symbolLimit = symbolLimit;
        this.totalLimit = totalLimit;
    }

    @OnTrigger
    public boolean checkLimits() {
        for (Map.Entry<String, Double> exposure : exposureCalculator.exposures().entrySet()) {
            check(exposure.getKey(), exposure.getValue(), symbolLimit);
        }
        check(TOTAL, exposureCalculator.getTotalExposure(), totalLimit);
        return true;
    }

    private void check(String key, double exposure, double limit) {
        boolean overLimit = exposure > limit;
        if (overLimit && breached.add(key)) {
            publish("BREACH", key, exposure, limit);
        } else if (!overLimit && breached.remove(key)) {
            publish("CLEARED", key, exposure, limit);
        }
    }

    private void publish(String state, String key, double exposure, double limit) {
        String alert = "%-7s %-7s exposure=%,.2f limit=%,.2f".formatted(state, key, exposure, limit);
        auditLog.info("alert", alert);
        alertPublisher.publish(alert);
    }
}
