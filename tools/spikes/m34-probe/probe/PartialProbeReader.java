package probe;

import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Throwaway M34.2 probe: claims PARTIAL order and supplies a DECLARED graph. */
public final class PartialProbeReader implements AuditLogReader {
    @Override public String formatId() { return "partial-probe"; }
    @Override public String displayName() { return "partial-order probe"; }
    @Override public boolean canOpen(Path s) { return s.getFileName().toString().endsWith(".probe"); }
    @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
    @Override public Capabilities capabilities() {
        return new Capabilities(false, false, true, Ordering.PARTIAL);
    }
    @Override public Optional<SourceGraph> graph(Path s) {
        List<ProcessorTopology.Node> n = new ArrayList<>();
        for (String id : List.of("ingest", "alpha", "beta", "join")) {
            n.add(new ProcessorTopology.Node(id, id, "com.acme." + id, ProcessorTopology.Kind.NODE));
        }
        return Optional.of(new SourceGraph(n, List.of(
                new ProcessorTopology.Edge("e1", "ingest", "alpha"),
                new ProcessorTopology.Edge("e2", "ingest", "beta"),
                new ProcessorTopology.Edge("e3", "alpha", "join"),
                new ProcessorTopology.Edge("e4", "beta", "join")), Provenance.DECLARED));
    }
    @Override public void read(Path s, Consumer<String> out) {
        for (int i = 1; i <= 6; i++) {
            out.accept("""
                    eventLogRecord:
                      logTime: %d
                      event: Tick
                      nodeLogs:
                        - ingest: { v: %d}
                        - alpha: { v: %d}
                        - beta: { v: %d}
                        - join: { v: %d}
                    """.formatted(1000L * i, i, i * 2, i * 3, i * 4));
        }
    }
}
