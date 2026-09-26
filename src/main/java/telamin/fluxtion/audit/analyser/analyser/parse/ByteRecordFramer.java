package telamin.fluxtion.audit.analyser.analyser.parse;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Streaming, byte-oriented record framer for the memory-mapped path (spec §7). Scans a file for
 * {@code ---} separator lines without loading it whole, emitting each record's <b>byte</b> offset,
 * byte length and decoded text. Mirrors {@link RecordFramer} but with 64-bit byte offsets so it
 * scales past 2 GB.
 */
public final class ByteRecordFramer {

    /** Receives one framed record: byte offset, byte length, decoded UTF-8 text. */
    public interface Sink {
        void accept(long offset, int length, String text);
    }

    private ByteRecordFramer() {
    }

    public static void frame(Path file, Sink sink) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
            frame(in, sink);
        }
    }

    static void frame(InputStream in, Sink sink) throws IOException {
        frameWithEof(in, sink);
    }

    static boolean frameWithEof(InputStream in, Sink sink) throws IOException {
        byte[] buf = new byte[1 << 16];
        long pos = 0;                       // byte offset of the next byte to read
        long lineStart = 0;                 // byte offset of the current line's first byte
        long recStart = -1;                 // byte offset of the current record, or -1
        ByteArrayOutputStream line = new ByteArrayOutputStream(256);
        ByteArrayOutputStream rec = new ByteArrayOutputStream(4096);

        int n;
        while ((n = in.read(buf)) != -1) {
            for (int i = 0; i < n; i++) {
                byte b = buf[i];
                line.write(b);
                pos++;
                if (b == '\n') {
                    recStart = processLine(line, lineStart, recStart, rec, sink);
                    line.reset();
                    lineStart = pos;
                }
            }
        }
        if (line.size() > 0) {
            recStart = processLine(line, lineStart, recStart, rec, sink);
        }
        if (recStart >= 0) emit(recStart, rec, sink);
        return recStart >= 0;
    }

    private static long processLine(ByteArrayOutputStream line, long lineStart, long recStart,
                                    ByteArrayOutputStream rec, Sink sink) {
        byte[] lb = line.toByteArray();
        if (isSeparator(lb, lineStart)) {
            if (recStart >= 0) {
                emit(recStart, rec, sink);
                rec.reset();
            }
            return -1;
        }
        if (recStart < 0) {
            if (isBlank(lb)) return -1;     // skip blank lines before a record starts
            recStart = lineStart;
        }
        rec.write(lb, 0, lb.length);
        return recStart;
    }

    private static void emit(long recStart, ByteArrayOutputStream rec, Sink sink) {
        byte[] bytes = rec.toByteArray();
        int end = bytes.length;
        while (end > 0 && (bytes[end - 1] == '\n' || bytes[end - 1] == '\r')) end--;   // trim trailing EOL
        if (end <= 0) return;
        sink.accept(recStart, end, new String(bytes, 0, end, StandardCharsets.UTF_8));
    }

    /**
     * True if the line (with its EOL) trims to exactly {@code ---}.
     *
     * <p>A leading UTF-8 byte-order mark is skipped, for the reason given in {@code RecordFramer}: it is
     * a character rather than whitespace, so a BOM before the file's first separator stopped it
     * separating. In bytes the BOM is the three-byte sequence {@code EF BB BF}.
     */
    private static boolean isSeparator(byte[] b, long lineStart) {
        // Only at byte 0 of the FILE, for the reason RecordFramer.isSeparator gives: a payload can never
        // sit there, and mid-file the shipped 1.0.45 exporter does not escape a BOM'd separator (review F1).
        int a = lineStart == 0 ? skipBom(b) : 0, e = b.length;
        while (a < e && isWs(b[a])) a++;
        while (e > a && isWs(b[e - 1])) e--;
        return (e - a) == 3 && b[a] == '-' && b[a + 1] == '-' && b[a + 2] == '-';
    }

    /** A lone byte-order mark is not content: a BOM-only file is empty, not a one-record file. */
    private static boolean isBlank(byte[] b) {
        for (int i = skipBom(b); i < b.length; i++) if (!isWs(b[i])) return false;
        return true;
    }

    /** The index after a UTF-8 BOM at the head of this line, or 0 when there is none. */
    private static int skipBom(byte[] b) {
        int i = 0;
        // Repeated marks are real: two BOM'd files concatenated, or a tool adding one to a file that
        // already had it. Skipping only the first left the second as content.
        while (b.length >= i + 3 && (b[i] & 0xFF) == 0xEF && (b[i + 1] & 0xFF) == 0xBB
                && (b[i + 2] & 0xFF) == 0xBF) {
            i += 3;
        }
        return i;
    }

    private static boolean isWs(byte b) {
        return b == ' ' || b == '\t' || b == '\r' || b == '\n';
    }
}
