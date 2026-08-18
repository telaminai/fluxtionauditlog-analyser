package telamin.fluxtion.audit.analyser.analyser.spi;

import telamin.fluxtion.audit.analyser.analyser.parse.RecordFramer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The shipped text format, behind the same SPI (M31.1's acceptance: the seam proven on the format
 * that matters). The registry special-cases this reader's STORE construction — text files keep the
 * existing size-thresholded Heap/Mapped backends with real byte anchors and follow support — but its
 * identity, {@code canOpen} and capability flags live here like any other reader's, so the open path
 * consults one registry, not a registry plus a special case.
 */
public final class YamlAuditReader implements AuditLogReader {

    public static final String FORMAT_ID = "yaml";

    @Override
    public String formatId() {
        return FORMAT_ID;
    }

    @Override
    public String displayName() {
        return "Fluxtion audit log (YAML)";
    }

    @Override
    public boolean canOpen(Path source) {
        // content, not extension: audit logs arrive as .yaml, .yml, .log and bare names alike.
        // The head of a real file reaches "eventLogRecord:" within the first record.
        try {
            byte[] head = new byte[4096];
            try (var in = Files.newInputStream(source)) {
                int n = in.read(head);
                if (n <= 0) return false;
                return new String(head, 0, n, StandardCharsets.UTF_8).contains("eventLogRecord:");
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public TimeBase timeBase() {
        // the native log declares nothing (UP-FLX-25 asks upstream to fix that) — this is the
        // analyser's long-standing working assumption, now stated in one place instead of six
        return TimeBase.wallClockMillisUtc();
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, true, true);
    }

    @Override
    public void read(Path source, Consumer<String> recordText) throws IOException {
        // the generic path — used only if someone routes a text file through SpiLogStore; the
        // registry sends text files to the thresholded Heap/Mapped stores instead
        RecordFramer.frame(Files.readString(source, StandardCharsets.UTF_8),
                raw -> recordText.accept(raw.text()));
    }
}
