package com.acme.fulfil;

import java.util.*;

public class Decision {
    public int eventNumber;
    public String type; // RELEASE, HAZARD_BLOCK, OVERWEIGHT, SLA_BREACH, STOCKOUT
    public String key; // orderId or sku

    public Decision(int eventNumber, String type, String key) {
        this.eventNumber = eventNumber;
        this.type = type;
        this.key = key;
    }

    public String toCsvLine() {
        return eventNumber + "," + type + "," + key;
    }
}
