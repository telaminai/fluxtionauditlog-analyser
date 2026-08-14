package telamin.fluxtion.audit.analyser.analyser.index;

import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LogIndex#snapshot()} must be a stable, isolated view (spec-assistant-actions §6): follow-mode
 * appends after the snapshot must not be seen by it, and a concurrent append — including one that grows
 * the dictionaries with a new dimension — must not tear a reader iterating the snapshot.
 */
class LogIndexSnapshotTest {

    private static String rec(int n, String dim) {
        // 'event' drives the derived dimension; use a distinct event name per dim
        return "#00:00:0" + (n % 10) + ".000 [t] INFO L\neventLogRecord:\n"
                + "  logTime: " + n + "\n  event: " + dim + "\n---\n";
    }

    private static Path tempWith(String content) throws IOException {
        Path p = Files.createTempFile("snap", ".log");
        p.toFile().deleteOnExit();
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void snapshotIsIsolatedFromLaterAppends() throws IOException {
        Path p = tempWith("---\n" + rec(1, "alpha") + rec(2, "alpha"));
        HeapLogStore store = HeapLogStore.fromFile(p);
        LogIndex.Snapshot before = store.index().snapshot();
        assertEquals(2, before.size());

        // append two records, one introducing a brand-new dimension
        Files.writeString(p, rec(3, "alpha") + rec(4, "brandNewDim"), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(2, store.appendFrom(p));

        // the old snapshot is unchanged and resolves its rows without seeing the new dimension
        assertEquals(2, before.size());
        assertEquals("alpha", before.dimension(0));
        assertEquals("alpha", before.dimension(1));

        LogIndex.Snapshot after = store.index().snapshot();
        assertEquals(4, after.size());
        assertEquals("brandNewDim", after.dimension(3));
    }

    @Test
    void rowForOffsetIsAFloorSearch() throws IOException {
        Path p = tempWith("---\n" + rec(1, "a") + rec(2, "b") + rec(3, "c"));
        LogIndex.Snapshot s = HeapLogStore.fromFile(p).index().snapshot();
        assertEquals(3, s.size());
        for (int i = 0; i < s.size(); i++) {
            assertEquals(i, s.rowForOffset(s.offset(i)), "exact start of record " + i);
            assertEquals(i, s.rowForOffset(s.offset(i) + 1), "a byte inside record " + i);
        }
        assertEquals(0, s.rowForOffset(-100), "before the first record clamps to 0");
        assertEquals(2, s.rowForOffset(Long.MAX_VALUE), "past EOF clamps to the last record");
    }

    @Test
    void concurrentAppendDoesNotTearAReader() throws Exception {
        Path p = tempWith("---\n" + rec(1, "alpha"));
        HeapLogStore store = HeapLogStore.fromFile(p);
        LogIndex index = store.index();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                for (int r = 0; r < 400; r++) {
                    LogIndex.Snapshot s = index.snapshot();
                    for (int i = 0; i < s.size(); i++) {
                        s.dimension(i);           // resolves through the captured dictionary copy
                        s.thread(i);
                        s.logTime(i);
                    }
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        reader.start();

        // grow the file (and the dictionaries, via new dimensions) while the reader scans
        for (int n = 2; n < 120; n++) {
            Files.writeString(p, rec(n, "dim" + n), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            store.appendFrom(p);
        }
        reader.join();

        assertNull(failure.get(), "a concurrent append must not tear a snapshot reader");
        assertTrue(index.snapshot().size() > 1);
    }
}
