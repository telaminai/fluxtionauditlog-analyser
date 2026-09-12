package telamin.fluxtion.audit.analyser.analyser.ui;

import com.telamin.fluxtion.runtime.audit.BinaryLogRecord;
import com.telamin.fluxtion.runtime.audit.BinaryLogWriter;
import com.telamin.fluxtion.runtime.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.spi.SpiLogStore;
import telamin.fluxtion.audit.analyser.analyser.spi.binary.BinaryAuditReader;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REVIEWER PROBE (round 9). A keyless number reached the topology's Graph menu, and the next named
 * key's {@code equals} on its null key threw. The menu offers NAMED series only, like the series path.
 */
class TopologyPanelKeylessTest {

    public static final class Tick { }

    private interface Body { void log(BinaryLogRecord r); }

    private static LogRecord record(Path dir, String name, Body body) throws Exception {
        Path p = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(p); BinaryLogWriter w = new BinaryLogWriter(out)) {
            Clock clock = new Clock();
            clock.init();
            BinaryLogRecord r = new BinaryLogRecord(clock);
            r.triggerObject(new Tick());
            body.log(r);
            w.processLogRecord(r);
        }
        return SpiLogStore.open(new BinaryAuditReader(), p).record(0);
    }

    private static List<KV> graphable(LogRecord record, String node) throws Exception {
        AtomicReference<List<KV>> out = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                TopologyPanel panel = new TopologyPanel();
                panel.showRecord(record);
                out.set(panel.graphableEntries(node));
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (failure.get() != null) throw new AssertionError("the menu threw", failure.get());
        return out.get();
    }

    @Test
    void keylessNumbersAreNotOfferedAndDoNotThrow(@TempDir Path dir) throws Exception {
        LogRecord before = record(dir, "before.flxa", r -> { r.addRecord("n", (String) null, 42); r.addRecord("n", "price", 77); });
        assertEquals(List.of("price"), graphable(before, "n").stream().map(KV::key).toList());
        LogRecord after = record(dir, "after.flxa", r -> { r.addRecord("n", "price", 77); r.addRecord("n", (String) null, 42); });
        assertEquals(List.of("price"), graphable(after, "n").stream().map(KV::key).toList());
        LogRecord repeated = record(dir, "repeated.flxa", r -> { r.addRecord("n", (String) null, 1); r.addRecord("n", (String) null, 2); r.addRecord("n", "price", 3); r.addRecord("n", (String) null, 4); });
        assertEquals(List.of("price"), graphable(repeated, "n").stream().map(KV::key).toList());
        LogRecord only = record(dir, "only.flxa", r -> { r.addRecord("n", (String) null, 42); });
        assertTrue(graphable(only, "n").isEmpty(), "a keyless-only node offers nothing to graph");
        assertEquals(1, only.nodeLogs().get(0).entries().size(), "but the evidence is still in the model");
    }
}
