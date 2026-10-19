package telamin.fluxtion.audit.analyser.analyser.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/** Review S7 (P2, 2026-10-17): a response body that stops arriving ends at its deadline, never holding the fetch for ever. */
class ExampleArchiveDeadlineTest {

    /** A body that sends a few bytes, then blocks until it is closed — a server that stalls mid-body. */
    static final class Stalled extends InputStream {
        final CountDownLatch closed = new CountDownLatch(1);
        int sent;

        @Override public int read() throws IOException {
            if (sent < 3) return ++sent;
            try {
                closed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("stream closed");
        }

        @Override public void close() {
            closed.countDown();
        }
    }

    /** ★ Witness: read through {@code readCapped(in, max)} with no deadline — the test times out (assertTimeoutPreemptively). */
    @Test
    @DisplayName("★ a stalled body ends at its deadline with TimedOut")
    void aStalledBodyTimesOut() {
        Stalled body = new Stalled();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var e = assertThrows(ExampleArchiveClient.TimedOut.class,
                    () -> ExampleArchiveClient.readCapped(body, 1024, Duration.ofMillis(200)));
            assertTrue(e.getMessage().contains("did not arrive"), e.getMessage());
        });
    }

    @Test
    @DisplayName("a body that arrives in time is read whole, and the size cap still applies")
    void aPromptBodyIsRead() throws Exception {
        assertArrayEquals(new byte[]{1, 2, 3},
                ExampleArchiveClient.readCapped(new ByteArrayInputStream(new byte[]{1, 2, 3}), 10, Duration.ofSeconds(5)));
        assertThrows(ExampleArchiveClient.TooLarge.class,
                () -> ExampleArchiveClient.readCapped(new ByteArrayInputStream(new byte[20]), 10, Duration.ofSeconds(5)));
    }

    /** The fetch turns the timeout into a named failure, not an exception. */
    @Test
    @DisplayName("fetch reports a stalled download by name")
    void fetchNamesTheStall() {
        String c = "0123456789abcdef0123456789abcdef01234567";
        var src = ExampleSource.parse("https://github.com/example/demo/archive/" + c + ".zip", null).source();
        var client = new ExampleArchiveClient((uri, max) -> {
            throw new ExampleArchiveClient.TimedOut(Duration.ofSeconds(300));
        });
        var f = client.fetch(src, 1024);
        assertFalse(f.ok());
        assertEquals("the download stalled and was stopped after 300 s", f.failure());
    }
}
