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
    }

    private static long processLine(ByteArrayOutputStream line, long lineStart, long recStart,
                                    ByteArrayOutputStream rec, Sink sink) {
        byte[] lb = line.toByteArray();
        if (isSeparator(lb)) {
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

    /** True if the line (with its EOL) trims to exactly {@code ---}. */
    private static boolean isSeparator(byte[] b) {
        int a = 0, e = b.length;
        while (a < e && isWs(b[a])) a++;
        while (e > a && isWs(b[e - 1])) e--;
        return (e - a) == 3 && b[a] == '-' && b[a + 1] == '-' && b[a + 2] == '-';
    }

    private static boolean isBlank(byte[] b) {
        for (byte value : b) if (!isWs(value)) return false;
        return true;
    }

    private static boolean isWs(byte b) {
        return b == ' ' || b == '\t' || b == '\r' || b == '\n';
    }
}
