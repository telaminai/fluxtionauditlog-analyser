package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.risk.RiskComponent;

/** Vendor component: capital charge from the risk exposure. */
public class CapitalComponent {
    public static class Charge extends EventLogNode {
        private final RiskComponent.Exposure exposure;
        public transient double value;
        public Charge(RiskComponent.Exposure exposure) { this.exposure = exposure; }
        @OnTrigger public boolean calc() {
            value = exposure.value * 0.08;
            auditLog.info("stage", "capital.charge").info("value", value);
            return true;
        }
    }
}
