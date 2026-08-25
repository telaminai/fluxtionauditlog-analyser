package probe;

import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Throwaway M34 probe: TOTAL order, and a graph() that THROWS — the registry-unreachable case. */
public final class BrokenProbeReader implements AuditLogReader {
    @Override public String formatId() { return "broken-probe"; }
    @Override public String displayName() { return "graph-throws probe"; }
    @Override public boolean canOpen(Path s) { return s.getFileName().toString().endsWith(".bprobe"); }
    @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
    @Override public Capabilities capabilities() { return new Capabilities(false, false, true); }
    @Override public Optional<SourceGraph> graph(Path s) throws IOException {
        throw new IOException("workflow registry unreachable (probe)");
    }
    @Override public void read(Path s, Consumer<String> out) {
        for (int i = 1; i <= 3; i++) {
            out.accept("""
                    eventLogRecord:
                      logTime: %d
                      event: Tick
                      nodeLogs:
                        - ingest: { v: %d}
                    """.formatted(1000L * i, i));
        }
    }
}
