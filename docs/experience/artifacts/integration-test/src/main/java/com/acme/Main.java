package com.acme;
import com.telamin.fluxtion.runtime.service.Service;
import com.vendor.capital.FeeStrategy;
import java.nio.file.*;

/** Reference application: five bought-in libraries, wired by declaration, run against a scenario. */
public class Main {
    static FeeStrategy named(String n) {
        double pct = "premium".equals(n) ? 0.05 : 0.01;
        return new FeeStrategy() {
            public double fee(double e) { return e * pct; }
            public String name() { return n; }
        };
    }
    public static void main(String[] a) throws Exception {
        Engine e = new Engine();
        for (String line : Files.readAllLines(Path.of(a[0]))) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("STRATEGY,"))
                e.processor.registerService(new Service<>(named(line.split(",")[1].trim()), FeeStrategy.class));
            else e.feed(line);
        }
        Files.writeString(Path.of(a[1]), String.join("\n", e.auditLog));
    }
}
