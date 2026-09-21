package com.acmerisk;

import com.acmerisk.api.RiskPosition;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

import java.util.Map;
import java.util.TreeMap;

/** Customer positions as notified by RiskPosition events. */
public class PositionFeed extends EventLogNode implements com.telamin.fluxtion.runtime.node.NamedNode {
    @Override
    public String getName() { return "acmePositionFeed"; }

    private final transient Map<String, RiskPosition> positions = new TreeMap<>();

    Map<String, RiskPosition> positions() { return positions; }

    @OnEventHandler
    public boolean onPosition(RiskPosition position) {
        positions.put(position.symbol(), position);
        auditLog.info("symbol", position.symbol());
        auditLog.info("quantity", position.quantity());
        return true;
    }
}
