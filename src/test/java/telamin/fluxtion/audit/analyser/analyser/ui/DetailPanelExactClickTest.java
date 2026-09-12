package telamin.fluxtion.audit.analyser.analyser.ui;

import com.telamin.fluxtion.runtime.audit.BinaryLogRecord;
import com.telamin.fluxtion.runtime.audit.BinaryLogWriter;
import com.telamin.fluxtion.runtime.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.spi.SpiLogStore;
import telamin.fluxtion.audit.analyser.analyser.spi.binary.BinaryAuditReader;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REVIEWER PROBE (round 10, R10-2). The record detail's exact-key click extracted an identifier from
 * the displayed line, so a click on the reader's {@code @unkeyed: 42} marker offered the series
 * {@code n.unkeyed} — a DIFFERENT, named property when the node also logged {@code unkeyed: 99}.
 * The click is resolved through the pane's real geometry (model → view → model) on the EDT, against the
 * parsed record: a token is a key only when the node logged an entry with exactly that name, and the
 * reader's {@code @} markers are never keys.
 */
class DetailPanelExactClickTest {

    public static final class Tick { }

    private static LogRecord record(Path dir) throws Exception {
        Path p = dir.resolve("click.flxa");
        try (OutputStream out = Files.newOutputStream(p); BinaryLogWriter w = new BinaryLogWriter(out)) {
            Clock clock = new Clock();
            clock.init();
            BinaryLogRecord r = new BinaryLogRecord(clock);
            r.triggerObject(new Tick());
            r.addRecord("n", (String) null, 42);          // keyless: shown as @unkeyed: 42
            r.addRecord("n", "unkeyed", 99);              // a business key with the marker's spelling
            r.addTrace("n");                              // shown as @invoked: true
            r.addRecord("n", "price", 7);
            w.processLogRecord(r);
        }
        return SpiLogStore.open(new BinaryAuditReader(), p).record(0);
    }

    /** Runs {@code probe} against a panel showing {@code rec} in the Text view, on the EDT. */
    private static <T> T onEdt(LogRecord rec, boolean textView, Function<DetailPanel, T> probe) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                DetailPanel panel = new DetailPanel();
                panel.setGraphTargets(new DetailPanel.GraphTargets() {
                    @Override public String currentName() { return null; }
                    @Override public List<String> names() { return List.of(); }
                    @Override public void addSeries(String g, String id, String key) { }
                });
                panel.setSize(1000, 800);
                panel.doLayout();
                panel.showRecords(List.of(rec));
                panel.selectTextView(textView);
                out.set(probe.apply(panel));
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (failure.get() != null) throw new AssertionError("the click path threw", failure.get());
        return out.get();
    }

    private static String pairs(List<String[]> offered) {
        StringBuilder sb = new StringBuilder("[");
        for (String[] p : offered) sb.append(p[0]).append('.').append(p[1]).append(' ');
        return sb.append(']').toString();
    }

    private static List<String[]> click(LogRecord rec, String needle, int within) throws Exception {
        int at = rec.rawText().indexOf(needle);
        assertTrue(at >= 0, "the evidence shows '" + needle + "':\n" + rec.rawText());
        int offset = at + within;
        return onEdt(rec, true, panel -> {
            try {
                return panel.graphKeysAtDocumentOffset(offset);
            } catch (javax.swing.text.BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void aClickOnTheKeylessMarkerNeverOffersTheNamedPropertyWithTheSameSpelling(@TempDir Path dir) throws Exception {
        LogRecord rec = record(dir);
        assertTrue(rec.rawText().contains("@unkeyed: 42"), rec.rawText());
        assertTrue(rec.rawText().contains(" unkeyed: 99"), rec.rawText());

        // on the 'u' of @unkeyed — an identifier the old code read as the key "unkeyed" and offered ALONE,
        // as if the click had landed on the named property. It is not an exact-key click: the node's
        // NAMED keys are offered together, the forgiving fallback for any click on a node line, and the
        // keyless value itself is never among them
        List<String[]> onMarker = click(rec, "@unkeyed: 42", 1);
        assertEquals(2, onMarker.size(), "the marker is not a key; every named key of the node is offered: "
                + pairs(onMarker) + "\n" + rec.rawText());
        assertEquals("unkeyed", onMarker.get(0)[1]);
        assertEquals("price", onMarker.get(1)[1]);

        // the real business key, one column in from its start, is an exact click on n.unkeyed
        List<String[]> onNamed = click(rec, " unkeyed: 99", 2);
        assertEquals(1, onNamed.size(), "an exact-key click offers that key alone: " + pairs(onNamed));
        assertEquals("unkeyed", onNamed.get(0)[1]);

        // an ordinary named entry is an exact click on itself
        List<String[]> onPrice = click(rec, " price: 7", 2);
        assertEquals(1, onPrice.size(), pairs(onPrice));
        assertEquals("price", onPrice.get(0)[1]);

        // the trace marker is provenance, not a series: the same fallback, never an exact n.invoked
        List<String[]> onTrace = click(rec, "@invoked: true", 1);
        assertEquals(2, onTrace.size(), pairs(onTrace));
        for (String[] p : onTrace) assertNotEquals("invoked", p[1], pairs(onTrace));
    }

    @Test
    void theExactClickIsResolvedAgainstTheParsedRecord_notTheSpelling() {
        // the pure resolver behind the click, so the rule reads without a pane: a token that is not an
        // entry of that node in the PARSED record is not a key, whatever the line spells
        String text = "---\neventLogRecord:\n  logTime: 1\n  nodeLogs:\n    - n: { @unkeyed: 42, unkeyed: 99, other: 1}\n";
        LogRecord rec = new telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore(
                "---\neventLogRecord:\n  logTime: 1\n  nodeLogs:\n    - n: { unkeyed: 99}\n").record(0);
        int marker = text.indexOf("@unkeyed") + 1;
        int named = text.indexOf(" unkeyed: 99") + 2;
        int other = text.indexOf("other: 1") + 1;
        assertNull(DetailPanel.exactKeyAt(text, marker, rec), "the marker's identifier is not a key");
        assertArrayEquals(new String[]{"n", "unkeyed"}, DetailPanel.exactKeyAt(text, named, rec));
        assertNull(DetailPanel.exactKeyAt(text, other, rec), "spelled like a key, but the record has no such entry");
        assertNull(DetailPanel.exactKeyAt(text, named, null), "no record, no key");
    }

    @Test
    void theLogicalViewOffersTheNodesNamedKeysFromItsOwnLayout(@TempDir Path dir) throws Exception {
        // the logical view shows the LAYOUT text; its offsets belong to the layout, not to the raw text
        LogRecord rec = record(dir);
        LogicalLogView.Layout layout = LogicalLogView.layout(List.of(rec));
        String shown = layout.text();
        assertTrue(shown.contains("@unkeyed: 42"), "keyless evidence is shown as the marker, not as 'null':\n" + shown);
        assertFalse(shown.contains("null: 42"), shown);
        int offset = shown.indexOf("@unkeyed: 42") + 1;
        List<String[]> offered = onEdt(rec, false, panel -> {
            try {
                return panel.graphKeysAtDocumentOffset(offset);
            } catch (javax.swing.text.BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(2, offered.size(), pairs(offered));
        assertEquals("unkeyed", offered.get(0)[1]);
        assertEquals("price", offered.get(1)[1]);
    }
}
