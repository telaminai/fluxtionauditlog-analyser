package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import java.util.HashMap;
import java.util.Map;

/** R11: reference data that does not change must not trigger rules. */
public class CarrierStore extends EventLogNode {
    private transient final Map<String, Carrier> carriers = new HashMap<>();

    @OnEventHandler
    public boolean onCarrier(Carrier event) {
        Carrier prev = carriers.get(event.carrierId());
        if (prev != null && prev.equals(event)) {
            return false;  // R11: unchanged, do not re-evaluate
        }
        carriers.put(event.carrierId(), event);
        auditLog.info("carrierId", event.carrierId()).info("maxWeightKg", event.maxWeightKg())
                .info("handlesHazardous", event.handlesHazardous());
        return true;
    }

    public Carrier getCarrier(String carrierId) {
        return carriers.getOrDefault(carrierId, new Carrier(carrierId, 0.0, false));
    }
}
