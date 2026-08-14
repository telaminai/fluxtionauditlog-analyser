package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Follow/tail mode (H8.7): {@link HeapLogStore#appendFrom} must pick up newly-<b>completed</b> records
 * from a growing file, ignore a record still being written (no closing {@code ---}), and signal a
 * shrink/rotation with {@code -1}.
 */
class FollowAppendTest {

    /** A minimal record block terminated by a separator line. */
    private static String rec(int n) {
        return "#00:00:0" + n + ".000 [t] INFO L\neventLogRecord:\n  logTime: " + n + "\n---\n";
    }

    private static Path tempWith(String content) throws IOException {
        Path p = Files.createTempFile("follow", ".log");
        p.toFile().deleteOnExit();
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private static void append(Path p, String more) throws IOException {
        Files.writeString(p, more, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    @Test
    void appendsNewlyCompletedRecords() throws IOException {
        Path p = tempWith("---\n" + rec(1) + rec(2));
        HeapLogStore store = HeapLogStore.fromFile(p);
        assertEquals(2, store.size());

        append(p, rec(3));
        assertEquals(1, store.appendFrom(p), "one completed record appended");
        assertEquals(3, store.size());

        // the appended record's index columns and re-sliced raw text are correct
        assertEquals(3L, (long) store.index().logTime(2));
        assertTrue(store.rawText(2).contains("logTime: 3"), "offset+length re-slice the new record");
        // earlier records are untouched
        assertEquals(1L, (long) store.index().logTime(0));
    }

    @Test
    void doesNotIndexARecordStillBeingWritten() throws IOException {
        Path p = tempWith("---\n" + rec(1) + rec(2));
        HeapLogStore store = HeapLogStore.fromFile(p);

        // a third record whose closing separator hasn't been flushed yet
        append(p, "#00:00:03.000 [t] INFO L\neventLogRecord:\n  logTime: 3\n");
        assertEquals(0, store.appendFrom(p), "un-terminated trailing record is not indexed yet");
        assertEquals(2, store.size());

        // once its separator arrives it is picked up
        append(p, "---\n");
        assertEquals(1, store.appendFrom(p));
        assertEquals(3, store.size());
        assertEquals(3L, (long) store.index().logTime(2));
    }

    @Test
    void noGrowthReturnsZeroAndShrinkReturnsMinusOne() throws IOException {
        Path p = tempWith("---\n" + rec(1) + rec(2));
        HeapLogStore store = HeapLogStore.fromFile(p);

        assertEquals(0, store.appendFrom(p), "unchanged file appends nothing");

        Files.writeString(p, "---\n" + rec(1), StandardCharsets.UTF_8);   // rewrite shorter (rotation)
        assertEquals(-1, store.appendFrom(p), "a shrunk/rotated file signals a reload");
    }
}
