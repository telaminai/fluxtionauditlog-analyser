package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M65 D-F0 — the store is publishable while it grows. A walker (series extraction, the {@code series} verb)
 * runs off the EDT while follow appends on it; before this, the index gained rows before the text that
 * contained them was swapped in, and the index arrays were read unsynchronised while being grown by
 * reallocation. The {@link LogStore.ReadView} closes both: size and spans captured under the index lock,
 * the text read after it, rows served from the capture.
 */
class HeapLogStoreReadViewTest {

    private static String rec(int n) {
        return "#00:00:00.000 [t] INFO L\neventLogRecord:\n  logTime: " + n
                + "\n  nodeLogs:\n    - nodeA: { x: " + n + "}\n---\n";
    }

    private static Path tempWith(String content) throws IOException {
        Path p = Files.createTempFile("readview", ".log");
        p.toFile().deleteOnExit();
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private static void append(Path p, String more) throws IOException {
        Files.writeString(p, more, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    @Test
    void aViewIsBoundedWhenTaken_appendsAfterItAreNotSeen() throws IOException {
        Path p = tempWith("---\n" + rec(1) + rec(2));
        HeapLogStore store = HeapLogStore.fromFile(p);
        LogStore.ReadView view = store.readView();

        append(p, rec(3) + rec(4));
        assertEquals(2, store.appendFrom(p));
        assertEquals(4, store.size(), "the store grew");
        assertEquals(2, view.size(), "the view did not");
        assertEquals(2L, view.record(1).logTime());
        assertEquals(store.rawText(0), view.rawText(0), "rows below the captured size read identically");
        assertThrows(IndexOutOfBoundsException.class, () -> view.record(2), "a row past the capture is refused, not read live");

        LogStore.ReadView later = store.readView();
        assertEquals(4, later.size(), "a fresh view sees the appended rows");
        assertEquals(4L, later.record(3).logTime());
    }

    @Test
    void theDefaultViewOnANonGrowingStoreIsLiveButBounded() {
        HeapLogStore backing = new HeapLogStore("---\n" + rec(1) + rec(2));
        // a store that does NOT override readView(): the interface default, over a delegate that never grows
        LogStore fixed = new LogStore() {
            @Override public int size() { return backing.size(); }
            @Override public telamin.fluxtion.audit.analyser.analyser.index.LogIndex index() { return backing.index(); }
            @Override public telamin.fluxtion.audit.analyser.analyser.model.LogRecord record(int row) { return backing.record(row); }
            @Override public String rawText(int row) { return backing.rawText(row); }
            @Override public Long minLogTime() { return backing.minLogTime(); }
            @Override public Long maxLogTime() { return backing.maxLogTime(); }
        };
        LogStore.ReadView view = fixed.readView();
        assertEquals(2, view.size());
        assertEquals(1L, view.record(0).logTime());
        assertThrows(IndexOutOfBoundsException.class, () -> view.rawText(2));
    }

    /**
     * The race itself: a walker taking fresh views and reading every row while the store is appended to
     * hundreds of times. Before D-F0 this threw {@code StringIndexOutOfBoundsException} (a row whose span lay
     * past the old text) or {@code ArrayIndexOutOfBoundsException} (a new size against a stale array) on the
     * walker's thread — swallowed by the graph's best-effort error path, so the chart silently stopped updating.
     */
    @Test
    void aWalkerNeverThrowsWhileTheStoreGrows() throws Exception {
        Path p = tempWith("---\n" + rec(1));
        HeapLogStore store = HeapLogStore.fromFile(p);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread walker = new Thread(() -> {
            try {
                while (!stop.get()) {
                    LogStore.ReadView v = store.readView();
                    for (int row = 0; row < v.size(); row++) {
                        Long lt = v.record(row).logTime();
                        if (lt == null || lt != row + 1) {
                            throw new AssertionError("row " + row + " read logTime " + lt);
                        }
                    }
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "walker");
        walker.start();
        try {
            for (int n = 2; n <= 400; n++) {
                append(p, rec(n));
                assertEquals(1, store.appendFrom(p));
            }
        } finally {
            stop.set(true);
            walker.join(10_000);
        }
        assertNull(failure.get(), () -> "walker failed: " + failure.get());
        assertEquals(400, store.size());
    }
}
