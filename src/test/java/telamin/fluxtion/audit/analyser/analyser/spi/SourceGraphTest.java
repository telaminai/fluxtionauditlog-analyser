package telamin.fluxtion.audit.analyser.analyser.spi;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** M34.1 slice 3 — a reader's graph reaches the store, and a broken one does not take the log down. */
class SourceGraphTest {

    private static final String RECORD = """
            eventLogRecord:
              logTime: %d
              event: Probe
              nodeLogs:
                - n: { v: 1}
            """;

    private static ProcessorTopology.Node node(String id) {
        return new ProcessorTopology.Node(id, id, "com.acme." + id, ProcessorTopology.Kind.NODE);
    }

    private abstract static class Base implements AuditLogReader {
        @Override public String formatId() { return "probe"; }
        @Override public String displayName() { return "probe"; }
        @Override public boolean canOpen(Path source) { return true; }
        @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
        @Override public Capabilities capabilities() { return new Capabilities(false, false, true); }
        @Override public void read(Path source, Consumer<String> out) {
            out.accept(RECORD.formatted(1000));
            out.accept(RECORD.formatted(2000));
        }
    }

    @Test
    void aDeclaredGraphReachesTheStore() throws Exception {
        var reader = new Base() {
            @Override public Optional<SourceGraph> graph(Path s) {
                return Optional.of(new SourceGraph(List.of(node("a"), node("b")),
                        List.of(new ProcessorTopology.Edge("e", "a", "b")), Provenance.DECLARED));
            }
        };
        try (var store = SpiLogStore.open(reader, Path.of("unused"))) {
            assertTrue(store.sourceGraph().isPresent());
            assertEquals(AuditLogReader.Provenance.DECLARED, store.sourceGraph().get().provenance());
            assertEquals(2, store.sourceGraph().get().nodes().size());
            assertEquals(2, store.size(), "and the log still loaded");
        }
    }

    @Test
    void aReaderWithNoGraphIsNotAFailure() throws Exception {
        try (var store = SpiLogStore.open(new Base() { }, Path.of("unused"))) {
            assertTrue(store.sourceGraph().isEmpty(), "empty is an answer, not an error");
            assertEquals(2, store.size());
        }
    }

    @Test
    void aReaderThatThrowsLosesItsGraphButNotTheLog() throws Exception {
        var reader = new Base() {
            @Override public Optional<SourceGraph> graph(Path s) throws IOException {
                throw new IOException("registry unreachable");
            }
        };
        try (var store = SpiLogStore.open(reader, Path.of("unused"))) {
            assertTrue(store.sourceGraph().isEmpty());
            assertEquals(2, store.size(),
                    "an unreadable graph is a missing graph — the log is still evidence");
            assertTrue(store.graphNote().contains("registry unreachable"),
                    "and it says why rather than looking like a source with no graph: "
                            + store.graphNote());
        }
    }

    @Test
    void aNativeStoreOffersNoGraph() {
        var store = new telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore(
                RECORD.formatted(1000));
        assertTrue(store.sourceGraph().isEmpty(),
                "a text container is a stream of records and knows nothing about structure — which "
                        + "is why the GraphML has always been a separate file");
    }

    @Test
    void duplicateIdsKeepTheFirst_becauseTheIdIsTheJoinKey() {
        var t = ProcessorTopology.of(
                List.of(node("dup"), new ProcessorTopology.Node("dup", "second", "com.acme.Other",
                        ProcessorTopology.Kind.NODE)),
                List.of());
        assertEquals(1, t.nodeCount());
        assertEquals("dup", t.node("dup").label(),
                "two nodes claiming one id would make step-through ambiguous with nothing "
                        + "downstream able to detect it");
    }
}
