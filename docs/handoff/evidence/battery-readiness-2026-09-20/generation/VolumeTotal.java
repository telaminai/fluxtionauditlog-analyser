package com.acme.preview.node;

import com.acme.preview.event.PriceEvent;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

public class VolumeTotal extends EventLogNode {
    private final RootNode rootNode;
    private long total;
    public VolumeTotal(RootNode rootNode) { this.rootNode = rootNode; }
    @OnTrigger
    public boolean update() {
        PriceEvent event = (PriceEvent) rootNode.getLatestEvent();
        total += event.volume();
        auditLog.info("totalVolume", total);
        return false;
    }
}
