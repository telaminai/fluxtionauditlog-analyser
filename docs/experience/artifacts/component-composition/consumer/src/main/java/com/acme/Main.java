package com.acme;
import com.acme.generated.AppProcessor;
import com.telamin.fluxtion.runtime.audit.*;
import com.vendor.Events;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] a) throws Exception {
        AppProcessor p = new AppProcessor();
        StringBuilder log = new StringBuilder();
        p.setAuditLogProcessor(r -> log.append(r.asCharSequence()).append('\n'));
        p.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
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
                default -> throw new IllegalArgumentException("unknown: " + f[0]);
            }
        }
        Files.writeString(Path.of(a[1]), log.toString());
    }
}
