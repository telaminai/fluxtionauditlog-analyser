package telamin.fluxtion.audit.analyser.analyser.spi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MA-0.1 at the app's open path: an empty audit file must be OPENABLE, or the empty-log finding can
 * never be shown.
 *
 * <p>Review found this capping MA-0. {@code canOpen} rejected zero bytes outright and anything without
 * {@code eventLogRecord:} in its first 4 KB, so three of MA-0's six shapes — zero bytes, whitespace, and
 * an empty export — were refused by every reader. The app said "no installed reader recognises this
 * file", and the finding that exists to explain an empty file never appeared.
 *
 * <p>It also blocked MA-0.5: a file opened in Follow BEFORE its first record could not be opened at all,
 * which is exactly when a person most wants to be told the file is still empty.
 */
class EmptyFileOpensTest {

    private static final ReaderRegistry REGISTRY = new ReaderRegistry();

    private static Path write(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p;
    }

    @Test
    void aZeroByteAuditFileIsRecognised(@TempDir Path dir) throws IOException {
        assertNotNull(REGISTRY.readerFor(write(dir, "empty.yaml", ""), null),
                "a zero-byte .yaml must open, or MA-0 cannot be shown for it");
    }

    @Test
    void aWhitespaceOnlyAuditFileIsRecognised(@TempDir Path dir) throws IOException {
        assertNotNull(REGISTRY.readerFor(write(dir, "blank.yaml", "\n  \n\t\n"), null),
                "whitespace only is still an empty audit file");
    }

    @Test
    void anEmptyExportIsRecognised(@TempDir Path dir) throws IOException {
        // what svc-admin-web writes when there is nothing to export
        assertNotNull(REGISTRY.readerFor(write(dir, "export.yaml", ""), null));
    }

    /**
     * The narrowness matters: accepting by extension applies ONLY when there is nothing to sniff. An
     * empty file this reader would not own anyway must not be claimed.
     */
    @Test
    void anEmptyFileOfAnotherTypeIsNotClaimed(@TempDir Path dir) throws IOException {
        assertNull(REGISTRY.readerFor(write(dir, "empty.png", ""), null),
                "an empty .png is not an audit log — readerFor returns null when nothing claims it");
    }

    /**
     * And a file WITH content is still recognised by content, not by its name — the extension path must
     * not become a general fallback.
     */
    @Test
    void aRealLogIsStillRecognisedByItsContentWhateverItIsCalled(@TempDir Path dir) throws IOException {
        Path oddName = write(dir, "audit.dat",
                "eventLogRecord:\n  logTime: 1\n  nodeLogs:\n    - n: { v: 1}\n---\n");
        assertNotNull(REGISTRY.readerFor(oddName, null),
                "content recognition must still work for a name this reader does not own");
    }
}
