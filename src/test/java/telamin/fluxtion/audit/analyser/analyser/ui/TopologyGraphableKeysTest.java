package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which of a node's values the topology's Graph menu offers (M21.5). This is logic, not painting: the
 * panel is constructed but never shown, which is what the repo's Swing convention allows.
 *
 * <p>The rule under test is that "graphable" means the same thing here as everywhere else in the app —
 * {@link KV#graphValue()} decides — so a number buried inside a {@code toString()} stays text, exactly as
 * it does in the detail viewer.
 */
class TopologyGraphableKeysTest {

    /** A real record from the sample log, so the shapes are the ones the parser actually produces. */
    private static TopologyPanel panelShowing(int recordIndex) {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        TopologyPanel panel = new TopologyPanel();
        panel.showRecord(store.record(recordIndex));
        return panel;
    }

    @Test
    void offersOnlyValuesThatCanBePlotted() {
        TopologyPanel panel = panelShowing(1);
        for (KV kv : panel.graphableEntries("hedgeConnectionMonitor")) {
            assertTrue(kv.graphValue().isPresent(),
                    kv.key() + "=" + kv.rawValue() + " is not a plottable value");
        }
    }

    @Test
    void aNumberInsideAToStringIsNotOffered() {
        // quotePublisherNode_0 logs publishMMQuote: QuoteLadder(quoteId=-1, bidPrice=NaN, …) — the
        // numbers in there are text, not keys, and must not appear as series
        TopologyPanel panel = panelShowing(1);
        assertTrue(panel.graphableEntries("quotePublisherNode_0").stream()
                        .noneMatch(kv -> kv.key().equals("publishMMQuote")),
                "a toString() payload is not a graphable key");
    }

    @Test
    void booleansAreOfferedBecauseTheAppPlotsThem() {
        TopologyPanel panel = panelShowing(1);
        List<KV> keys = panel.graphableEntries("hedgePositionMonitor");
        assertTrue(keys.stream().anyMatch(kv -> kv.key().equals("hedgePositionBreach")),
                "booleans map to +1/-1 and are plottable: " + keys);
    }

    @Test
    void keysAreNotOfferedTwiceWhenANodeFiresTwice() {
        TopologyPanel panel = panelShowing(1);
        List<KV> keys = panel.graphableEntries("bidMakerOrder");
        assertEquals(keys.stream().map(KV::key).distinct().count(), keys.size(),
                "a node firing twice must not double its menu: " + keys);
    }

    @Test
    void anUnknownNodeOffersNothingRatherThanFailing() {
        assertTrue(panelShowing(1).graphableEntries("noSuchNode_99").isEmpty());
    }

    @Test
    void withNoRecordSelectedThereIsNothingToOffer() {
        TopologyPanel panel = new TopologyPanel();
        panel.showRecord(null);
        assertTrue(panel.graphableEntries("hedgeConnectionMonitor").isEmpty());
    }
}
