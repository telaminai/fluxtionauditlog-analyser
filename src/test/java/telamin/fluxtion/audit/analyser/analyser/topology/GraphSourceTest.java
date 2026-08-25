package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M34.1 — who wins the topology slot, and where coverage is meaningful. */
class GraphSourceTest {

    @Test
    void aReaderGraphFillsAnEmptySlot() {
        assertTrue(GraphSource.NONE.replacedBy(GraphSource.READER_DECLARED));
        assertTrue(GraphSource.NONE.replacedBy(GraphSource.OPENED));
    }

    @Test
    void anOpenedGraphIsNeverDisplacedByAReader() {
        // M35.3's asymmetry, one level out: someone NAMED that file. A reader quietly swapping it is
        // the analyser deciding which system you meant to look at.
        assertFalse(GraphSource.OPENED.replacedBy(GraphSource.READER_DECLARED));
        assertFalse(GraphSource.OPENED.replacedBy(GraphSource.READER_INFERRED));
    }

    @Test
    void anOpenedGraphReplacesAReaderSuppliedOne() {
        assertTrue(GraphSource.READER_DECLARED.replacedBy(GraphSource.OPENED));
        assertTrue(GraphSource.READER_INFERRED.replacedBy(GraphSource.OPENED));
    }

    @Test
    void oneReaderGraphDoesNotChurnAnother() {
        assertFalse(GraphSource.READER_DECLARED.replacedBy(GraphSource.READER_INFERRED),
                "re-reading the same source must not swap a declared graph for an inferred one");
    }

    @Test
    void coverageIsMeaninglessAgainstAnInferredGraph() {
        // "declared minus observed" over a set BUILT from the observed is always 100%. The feature
        // that found 54 dead nodes in the POC would become a tautology that still prints a number.
        assertTrue(GraphSource.OPENED.supportsCoverage());
        assertTrue(GraphSource.READER_DECLARED.supportsCoverage());
        assertFalse(GraphSource.READER_INFERRED.supportsCoverage());
        assertFalse(GraphSource.NONE.supportsCoverage());
    }

    @Test
    void everySourceCanSayWhatItIs() {
        for (GraphSource g : GraphSource.values()) {
            assertNotNull(g.describe);
            assertFalse(g.describe.isBlank(), "D-A2: the view says which, so there must be words");
        }
    }

    @Test
    void aSourceGraphMustDeclareItsProvenance() {
        // an unmarked graph is the one thing coverage cannot safely consume, so it cannot be built
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new AuditLogReader.SourceGraph(List.of(), List.of(), null));
        assertTrue(ex.getMessage().contains("DECLARED or INFERRED"), ex.getMessage());
    }

    @Test
    void theSpiDefaultsToNoGraph_soExistingReadersKeepCompiling() throws Exception {
        AuditLogReader bare = new AuditLogReader() {
            @Override public String formatId() { return "bare"; }
            @Override public String displayName() { return "bare"; }
            @Override public boolean canOpen(java.nio.file.Path source) { return false; }
            @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
            @Override public Capabilities capabilities() { return new Capabilities(false, false, false); }
            @Override public void read(java.nio.file.Path s, java.util.function.Consumer<String> c) { }
        };
        assertTrue(bare.graph(java.nio.file.Path.of("x")).isEmpty(),
                "empty is the honest answer for a source that cannot say — and it is a DIFFERENT "
                        + "statement from returning an inferred graph");
    }
}
