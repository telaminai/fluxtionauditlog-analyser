package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

class FirstMinuteChartsTest {
    @Test void theStructuralBlankTabIsNotPersonalButEveryRealChartEditIs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var graphs = new GraphTabs();
            var store = new HeapLogStore(Samples.sample());
            graphs.bind(store, new FilterState());
            assertFalse(graphs.hasPersonalContent(), "binding's automatic blank tab is not a person's addition");
            assertEquals(1, graphs.specs().size(), "the released persistence shape of ordinary graphs is unchanged");
            graphs.addGraph("DEMO chart");
            assertTrue(graphs.hasPersonalContent(), "even an empty chart deliberately added is personal");
            graphs.bind(store, new FilterState());
            assertFalse(graphs.hasPersonalContent(), "a fresh binding has its own observation");
            graphs.graphNamed(graphs.specs().getFirst().name()).setCaption("DEMO note");
            assertTrue(graphs.hasPersonalContent(), "a note on the structural tab is an actual edit");
            var saved = graphs.specs();
            graphs.bind(store, new FilterState());
            graphs.restore(saved);
            assertTrue(graphs.hasPersonalContent(), "restored or imported definitions are personal too");
        });
    }
}
