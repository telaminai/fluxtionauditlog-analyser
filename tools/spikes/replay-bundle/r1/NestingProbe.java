package com.acme.demo.probe;

import com.acme.demo.event.Events;
import com.acme.demo.replay.ReplayCapture;
import com.telamin.fluxtion.runtime.CloneableDataFlow;

import java.util.ArrayList;

public class NestingProbe {
    public static void main(String[] a) throws Exception {
        CloneableDataFlow<?> p = (CloneableDataFlow<?>) Class.forName("com.acme.demo.generated.DemoQuoteCaptureProcessor")
                .getDeclaredConstructor().newInstance();
        p.init();
        p.start();
        for (Object e : new Object[]{new Events.OrderUpdateEvent("ord-1", "LIVE"), new Events.OrderUpdateEvent("ord-2", "LIVE")}) {
            p.onEvent(e);
        }
        ReplayCapture.trace = new ArrayList<>();
        System.out.println(">> onEvent(ord-3) — takes the book over the limit, the graph raises RiskBreachEvent");
        p.onEvent(new Events.OrderUpdateEvent("ord-3", "LIVE"));
        System.out.println("<< returned");
        ReplayCapture.trace.forEach(t -> System.out.println("   " + t));
    }
}
