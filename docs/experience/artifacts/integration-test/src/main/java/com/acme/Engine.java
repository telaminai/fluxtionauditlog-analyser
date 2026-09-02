package com.acme;
import com.acme.generated.AppProcessor;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.vendor.Events;
import java.util.*;

/** Feeds a scenario in and keeps the framework's audit log verbatim. */
public class Engine {
    public final AppProcessor processor = new AppProcessor();
    public final List<String> auditLog = new ArrayList<>();

    public Engine() {
        processor.setAuditLogProcessor(r -> auditLog.add(r.asCharSequence().toString()));
        processor.setAuditLogLevel(LogLevel.INFO);
        processor.init();
    }
    public void feed(String line) {
        String[] f = line.trim().split(",");
        switch (f[0]) {
            case "TICK"   -> processor.onEvent(new Events.Tick(f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3])));
            case "TRADE"  -> processor.onEvent(new Events.Trade(f[1], Double.parseDouble(f[2]), Double.parseDouble(f[3])));
            case "RATE"   -> processor.onEvent(new Events.Rate(f[1], Double.parseDouble(f[2])));
            case "CONFIG" -> processor.onEvent(new Events.Config(f[1], Double.parseDouble(f[2])));
            default -> throw new IllegalArgumentException(f[0]);
        }
    }
    public void feedAll(String... lines) { for (String l : lines) feed(l); }

    /** The stages that ran for the most recent event, in dispatch order - read from the audit log. */
    public List<String> lastDispatch() {
        for (int i = auditLog.size() - 1; i >= 0; i--) {
            List<String> st = stagesOf(auditLog.get(i));
            if (!st.isEmpty()) return st;
        }
        return List.of();
    }
    public List<String> allDispatch() {
        List<String> out = new ArrayList<>();
        for (String r : auditLog) out.addAll(stagesOf(r));
        return out;
    }
    static List<String> stagesOf(String record) {
        List<String> out = new ArrayList<>();
        for (String line : record.split("\n"))
            if (line.contains("stage: "))
                out.add(line.replaceAll(".*stage: ([\\w.]+).*", "$1").trim());
        return out;
    }
    public int mark() { return auditLog.size(); }
    public List<String> dispatchSince(int mark) {
        List<String> out = new ArrayList<>();
        for (String r : auditLog.subList(mark, auditLog.size())) out.addAll(stagesOf(r));
        return out;
    }
}
