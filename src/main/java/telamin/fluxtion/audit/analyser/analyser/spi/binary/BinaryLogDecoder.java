package telamin.fluxtion.audit.analyser.analyser.spi.binary;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes the {@code FLXA} binary audit format.
 *
 * <p><b>This is the analyser's OWN decoder, written from the format specification rather than shared
 * with the writer's.</b> The owner's call, 2026-09-09. The analyser already depends on
 * {@code fluxtion-runtime} — that arrived with M44 — so this is not about avoiding a dependency; it is
 * about not coupling the analyser's ability to open a file to a class whose evolution is driven by the
 * writing side. The format is specified, so two implementations that agree are evidence the
 * specification is right; one implementation used twice is evidence of nothing.
 *
 * <p>The format, from <i>spec-binary-audit-encoding</i> §6.3 and the framing it references:
 * <pre>
 *   file    := header, frame*
 *   header  := "FLXA", formatVersion:u16, reserved:u16          (8 bytes)
 *   frame   := DICT | RECORD
 *   DICT    := 0x02, id:u16, len:u16, utf8[len]
 *   RECORD  := 0x01, entryCount:u16, eventTypeId:u16,
 *              eventTime:i64, logTime:i64, endTime:i64,
 *              slots:i64[entryCount * 2]
 *
 *   entry   := slot0, slot1                                     always exactly 16 bytes
 *   slot0   : nodeId u16 (63..48), keyId u16 (47..32), reserved (31..8), tag u8 (7..0)
 *   slot1   : the value's raw bits
 * </pre>
 *
 * <p><b>Truncation is a normal end state, not an error.</b> The usual reason to read an audit log is
 * that something went wrong, so the file is exactly as complete as the process managed to make it. A
 * partial trailing frame stops the scan cleanly and is counted in {@link Result#truncatedBytes}.
 *
 * <p><b>{@code keyId == 0} means NO KEY, not an id that failed to resolve.</b> A trace entry says only
 * that a node ran. Counting those as unresolved would make every traced log look corrupt, and the
 * unresolved count is how a reader tells a rolled file from a damaged one.
 */
public final class BinaryLogDecoder {

    public static final byte[] MAGIC = {'F', 'L', 'X', 'A'};
    public static final int HEADER_BYTES = 8;
    public static final int FRAME_RECORD = 0x01;
    public static final int FRAME_DICT = 0x02;
    /** tag, entryCount, eventTypeId, eventTime, logTime, endTime. */
    public static final int RECORD_FIXED_BYTES = 1 + 2 + 2 + 8 + 8 + 8;

    public static final int TAG_DOUBLE = 1, TAG_LONG = 2, TAG_INT = 3, TAG_CHAR = 4,
            TAG_CHARSEQ = 5, TAG_OBJECT = 6, TAG_BOOL = 7, TAG_TRACE = 8;

    /**
     * A single {@code FileChannel.map} addresses at most {@link Integer#MAX_VALUE} bytes, because
     * {@code MappedByteBuffer} inherits {@code Buffer}'s int capacity. Larger files are read in chunks
     * with a reassembly buffer for records that straddle a boundary.
     */
    public static final long MAX_SINGLE_MAP = Integer.MAX_VALUE;

    /**
     * Chunk size for the large-file path. Package-visible and non-final ONLY so the straddle case can
     * be tested without producing a 2 GiB fixture — the specification requires that test to exist, and
     * a test that cannot run is not a test.
     */
    static int chunkBytes = 1 << 20;

    /**
     * The size above which the chunked path is used. Package-visible and non-final for the same reason
     * as {@link #chunkBytes}: without it the straddle test writes a small file, takes the single-map
     * path, and passes without exercising any of the code it exists to cover. It did exactly that once.
     */
    static long singleMapLimit = MAX_SINGLE_MAP;

    private BinaryLogDecoder() {
    }

    /** What the caller is told about a file after reading it. */
    public static final class Result {
        public long records;
        public long entries;
        public long truncatedBytes;
        public long unresolvedIds;
        public final List<String> dictionary = new ArrayList<>();

        @Override
        public String toString() {
            return "records=" + records + " entries=" + entries
                    + " truncatedBytes=" + truncatedBytes + " unresolvedIds=" + unresolvedIds
                    + " dictionary=" + dictionary.size();
        }
    }

    /** Called as the file is walked. Nothing here is allocated per entry. */
    public interface Visitor {
        void recordStart(long eventTime, long logTime, long endTime, String eventType);

        /** {@code key} is null for a trace entry — the node ran and logged no property. */
        void entry(String node, String key, int tag, long rawBits);

        void recordEnd();
    }

    public static Result read(Path file, Visitor visitor) throws IOException {
        long size = Files.size(file);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            if (size <= singleMapLimit) {
                ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
                buffer.order(ByteOrder.BIG_ENDIAN);
                return decode(buffer, new Result(), visitor);
            }
            return decodeChunked(channel, size, visitor);
        }
    }

    /** For tests and small in-memory sources. */
    public static Result read(byte[] data, Visitor visitor) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        return decode(buffer, new Result(), visitor);
    }

    private static Result decode(ByteBuffer buffer, Result result, Visitor visitor) {
        Map<Integer, String> dictionary = new HashMap<>();
        requireMagic(buffer);
        buffer.position(HEADER_BYTES);
        int stopped = scan(buffer, dictionary, result, visitor);
        // Whatever the scan could not turn into a frame is reported, not thrown.
        result.truncatedBytes += buffer.limit() - stopped;
        return finish(result, dictionary);
    }

    /**
     * Walks frames until the buffer ends or a frame cannot be completed. Returns the offset it stopped
     * at, so the chunked path knows where the straddling frame began.
     */
    private static int scan(ByteBuffer buffer, Map<Integer, String> dictionary,
                            Result result, Visitor visitor) {
        while (buffer.remaining() > 0) {
            int frameStart = buffer.position();
            int tag = buffer.get() & 0xFF;
            if (tag == FRAME_DICT) {
                if (buffer.remaining() < 4) return frameStart;
                int id = buffer.getShort() & 0xFFFF;
                int length = buffer.getShort() & 0xFFFF;
                if (buffer.remaining() < length) return frameStart;
                byte[] name = new byte[length];
                buffer.get(name);
                dictionary.put(id, new String(name, StandardCharsets.UTF_8));
            } else if (tag == FRAME_RECORD) {
                if (buffer.remaining() < RECORD_FIXED_BYTES - 1) return frameStart;
                int entryCount = buffer.getShort() & 0xFFFF;
                int eventTypeId = buffer.getShort() & 0xFFFF;
                long eventTime = buffer.getLong();
                long logTime = buffer.getLong();
                long endTime = buffer.getLong();
                if (buffer.remaining() < entryCount * 16L) return frameStart;
                visitor.recordStart(eventTime, logTime, endTime,
                        resolve(dictionary, eventTypeId, result));
                for (int i = 0; i < entryCount; i++) {
                    long slot0 = buffer.getLong();
                    long slot1 = buffer.getLong();
                    int nodeId = (int) ((slot0 >>> 48) & 0xFFFF);
                    int keyId = (int) ((slot0 >>> 32) & 0xFFFF);
                    int entryTag = (int) (slot0 & 0xFF);
                    // keyId 0 is "no key" and must not be counted as an unresolved id.
                    String key = keyId == 0 ? null : resolve(dictionary, keyId, result);
                    visitor.entry(resolve(dictionary, nodeId, result), key, entryTag, slot1);
                    result.entries++;
                }
                visitor.recordEnd();
                result.records++;
            } else {
                // An unknown frame tag cannot be skipped, because its length is unknown. Stop here and
                // report the rest as unusable rather than guessing a length and mis-reading the file.
                return frameStart;
            }
        }
        return buffer.position();
    }

    /**
     * The large-file path. A frame that straddles a chunk boundary is handled by re-mapping from where
     * that frame STARTS, so the decoder always sees it contiguously. Copying into a reassembly buffer
     * would work too; re-mapping keeps one decode path and cannot get a copy length wrong.
     */
    private static Result decodeChunked(FileChannel channel, long size, Visitor visitor)
            throws IOException {
        Result result = new Result();
        Map<Integer, String> dictionary = new HashMap<>();
        long position = 0;
        boolean first = true;
        while (position < size) {
            long length = Math.min(chunkBytes, size - position);
            ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, position, length);
            buffer.order(ByteOrder.BIG_ENDIAN);
            if (first) {
                requireMagic(buffer);
                buffer.position(HEADER_BYTES);
                first = false;
            }
            int stopped = scan(buffer, dictionary, result, visitor);
            if (stopped == buffer.limit()) {
                position += length;
                continue;
            }
            if (stopped == 0) {
                // One frame is larger than a whole chunk. Map the rest of the file for it rather than
                // growing the chunk, since this is the last thing we will need to read anyway.
                long remaining = size - position;
                ByteBuffer rest = channel.map(FileChannel.MapMode.READ_ONLY, position, remaining);
                rest.order(ByteOrder.BIG_ENDIAN);
                int end = scan(rest, dictionary, result, visitor);
                result.truncatedBytes += remaining - end;
                return finish(result, dictionary);
            }
            // Resume at the straddling frame's first byte.
            position += stopped;
        }
        return finish(result, dictionary);
    }

    private static void requireMagic(ByteBuffer buffer) {
        if (buffer.remaining() < HEADER_BYTES || !magicMatches(buffer)) {
            throw new IllegalArgumentException(
                    "not a Fluxtion binary audit log: expected magic FLXA at offset 0");
        }
    }

    private static Result finish(Result result, Map<Integer, String> dictionary) {
        result.dictionary.addAll(dictionary.values());
        return result;
    }

    private static boolean magicMatches(ByteBuffer buffer) {
        for (byte b : MAGIC) {
            if (buffer.get() != b) {
                return false;
            }
        }
        return true;
    }

    private static String resolve(Map<Integer, String> dictionary, int id, Result result) {
        String name = dictionary.get(id);
        if (name == null) {
            result.unresolvedIds++;
            return "id:" + id;
        }
        return name;
    }

    /** Renders a value slot for display. Doubles are rendered from their exact bits. */
    public static String renderValue(int tag, long rawBits) {
        switch (tag) {
            case TAG_DOUBLE: return String.valueOf(Double.longBitsToDouble(rawBits));
            case TAG_LONG:   return String.valueOf(rawBits);
            case TAG_INT:    return String.valueOf((int) rawBits);
            case TAG_CHAR:   return String.valueOf((char) rawBits);
            case TAG_BOOL:   return rawBits != 0 ? "true" : "false";
            case TAG_TRACE:  return "";
            default:         return "tag" + tag + ":" + rawBits;
        }
    }

    public static boolean knownTag(int tag) {
        return tag >= TAG_DOUBLE && tag <= TAG_TRACE;
    }
}
