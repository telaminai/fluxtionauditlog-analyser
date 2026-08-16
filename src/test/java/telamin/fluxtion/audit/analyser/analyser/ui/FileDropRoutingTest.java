package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drop-routing rule: .graphml goes to the Topology tab, everything else opens as a log. */
class FileDropRoutingTest {

    @Test
    void graphmlByExtensionCaseInsensitive() {
        assertTrue(MainFrame.isGraphml("processor.graphml"));
        assertTrue(MainFrame.isGraphml("PROCESSOR.GraphML"));
    }

    @Test
    void everythingElseIsALog() {
        assertFalse(MainFrame.isGraphml("audit.yaml"));
        assertFalse(MainFrame.isGraphml("audit.graphml.yaml"));   // suffix must be terminal
        assertFalse(MainFrame.isGraphml("graphml"));              // no dot — a file named "graphml"
        assertFalse(MainFrame.isGraphml(null));
    }
}
