package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Distinct dropped evidence types must reach their normal open paths. */
class FileDropRoutingTest {

    @Test
    void graphmlByExtensionCaseInsensitive() {
        assertTrue(MainFrame.isGraphml("processor.graphml"));
        assertTrue(MainFrame.isGraphml("PROCESSOR.GraphML"));
    }

    @Test
    void bundleAndSpringDesignDoNotFallThroughToTheLogReader() {
        assertEquals(MainFrame.DropKind.BUNDLE, MainFrame.dropKind("incident.FEXP"));
        assertEquals(MainFrame.DropKind.DESIGN, MainFrame.dropKind("spring-design.XML"));
        assertEquals(MainFrame.DropKind.GRAPHML, MainFrame.dropKind("processor.graphml"));
        assertEquals(MainFrame.DropKind.LOG, MainFrame.dropKind("audit.yaml"));
        assertEquals(MainFrame.DropKind.LOG, MainFrame.dropKind("audit-with-no-extension"));
        assertFalse(MainFrame.isGraphml("audit.yaml"));
        assertFalse(MainFrame.isGraphml("audit.graphml.yaml"));   // suffix must be terminal
        assertFalse(MainFrame.isGraphml("graphml"));              // no dot — a file named "graphml"
        assertFalse(MainFrame.isGraphml(null));
    }
}
