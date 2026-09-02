package com.acme.app;

import com.acme.generated.AppProcessor;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.service.Service;
import com.vendor.capital.FeeStrategies;
import com.vendor.contract.*;

import java.nio.file.*;
import java.util.*;

/**
 * SUPPLIED — do not modify. In real use you do not write this: a generated processor drops into an
 * existing harness. It names no component; it only pumps events and collects what the engine emits.
 */
public class Main {
    public static void main(String[] a) throws Exception {
        AppProcessor p = new AppProcessor();
        List<String> audit = new ArrayList<>(), alerts = new ArrayList<>();

        p.setAuditLogProcessor(r -> audit.add(r.asCharSequence().toString()));
        p.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
        p.registerService(new Service<>((AlertSink) alerts::add, AlertSink.class));
        p.init();

        for (String line : Files.readAllLines(Path.of(a[0]))) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split(",");
            switch (f[0]) {
                case "TICK"   -> p.onEvent(new Events.Tick(f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3])));
                case "TRADE"  -> p.onEvent(new Events.Trade(f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3])));
                case "RATE"   -> p.onEvent(new Events.Rate(f[1], Double.parseDouble(f[2])));
                case "CONFIG" -> p.onEvent(new Events.Config(f[1], Double.parseDouble(f[2])));
                case "STRATEGY" -> p.registerService(
                        new Service<>(FeeStrategies.byName(f[1].trim()), FeeStrategy.class));
                default -> throw new IllegalArgumentException(f[0]);
            }
        }
        Files.writeString(Path.of(a[1]), String.join("\n", audit));
        Files.writeString(Path.of(a[2]), String.join("\n", alerts));
    }
}
