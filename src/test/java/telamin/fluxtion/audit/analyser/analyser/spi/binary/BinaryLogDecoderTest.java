package telamin.fluxtion.audit.analyser.analyser.spi.binary;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The analyser's own decoder, against bytes this test writes itself.
 *
 * <p><b>The encoder here is deliberately independent of the runtime's.</b> The point of the owner's
 * "own decoder" decision is that two implementations agreeing is evidence the FORMAT is right; if this
 * test called the writer that the decoder was modelled on, it would only prove the two agree with each
 * other, which they would even if both misread the specification.
 */
class BinaryLogDecoderTest {

    /** Writes the format as specified: header, then DICT and RECORD frames. */
    private static final class Encoder {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Encoder() {
            out.writeBytes(BinaryLogDecoder.MAGIC);
            out.writeBytes(new byte[]{0, 1, 0, 0});   // formatVersion 1, reserved
        }

        Encoder dict(int id, String name) {
            byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
            out.write(BinaryLogDecoder.FRAME_DICT);
            out.writeBytes(u16(id));
            out.writeBytes(u16(utf8.length));
            out.writeBytes(utf8);
            return this;
        }

        Encoder record(int eventTypeId, long eventTime, long logTime, long endTime, long[] slots) {
            out.write(BinaryLogDecoder.FRAME_RECORD);
            out.writeBytes(u16(slots.length / 2));
            out.writeBytes(u16(eventTypeId));
            out.writeBytes(i64(eventTime));
            out.writeBytes(i64(logTime));
            out.writeBytes(i64(endTime));
            for (long slot : slots) {
                out.writeBytes(i64(slot));
            }
            return this;
        }

        byte[] bytes() {
            return out.toByteArray();
        }

        private static byte[] u16(int v) {
            return new byte[]{(byte) (v >>> 8), (byte) v};
        }

        private static byte[] i64(long v) {
            return ByteBuffer.allocate(8).putLong(v).array();
        }
    }

    /** slot0 as the spec lays it out: nodeId 63..48, keyId 47..32, reserved, tag 7..0. */
    private static long slot0(int nodeId, int keyId, int tag) {
        return ((long) (nodeId & 0xFFFF) << 48) | ((long) (keyId & 0xFFFF) << 32) | (tag & 0xFFL);
    }

    private static final class Collector implements BinaryLogDecoder.Visitor {
        final List<String> events = new ArrayList<>();

        @Override
        public void recordStart(long eventTime, long logTime, long endTime, String eventType) {
            events.add("record " + eventType + " " + eventTime + "/" + logTime + "/" + endTime);
        }

        @Override
        public void entry(String node, String key, int tag, long rawBits) {
            events.add("  " + node + "." + key + "=" + BinaryLogDecoder.renderValue(tag, rawBits));
        }

        @Override
        public void recordEnd() {
            events.add("end");
        }
    }

    @Test
    void decodesEveryValueTagIncludingDoublesBitExact() {
        double awkward = 0.1 + 0.2;                       // not representable; must survive exactly
        byte[] file = new Encoder()
                .dict(1, "TickEvent").dict(2, "pricer").dict(3, "price").dict(4, "count")
                .dict(5, "live").dict(6, "initial")
                .record(1, 100, 101, 102, new long[]{
                        slot0(2, 3, BinaryLogDecoder.TAG_DOUBLE), Double.doubleToRawLongBits(awkward),
                        slot0(2, 4, BinaryLogDecoder.TAG_INT), -7,
                        slot0(2, 5, BinaryLogDecoder.TAG_BOOL), 1,
                        slot0(2, 6, BinaryLogDecoder.TAG_LONG), Long.MIN_VALUE})
                .bytes();

        Collector seen = new Collector();
        BinaryLogDecoder.Result result = BinaryLogDecoder.read(file, seen);

        assertEquals(1, result.records);
        assertEquals(4, result.entries);
        assertEquals(0, result.unresolvedIds);
        assertEquals(0, result.truncatedBytes);
        assertEquals(List.of(
                "record TickEvent 100/101/102",
                "  pricer.price=" + awkward,
                "  pricer.count=-7",
                "  pricer.live=true",
                "  pricer.initial=" + Long.MIN_VALUE,
                "end"), seen.events);
    }

    @Test
    void aTraceEntryHasNoKeyAndIsNotCountedAsAnUnresolvedId() {
        // The distinction the format spec calls out: keyId 0 means NO KEY. Counting it as an id that
        // failed to resolve would make every traced log look corrupt, and the unresolved count is how a
        // reader tells a rolled file from a damaged one.
        byte[] file = new Encoder()
                .dict(1, "TickEvent").dict(2, "pricer")
                .record(1, 1, 2, 3, new long[]{slot0(2, 0, BinaryLogDecoder.TAG_TRACE), 0})
                .bytes();

        Collector seen = new Collector();
        BinaryLogDecoder.Result result = BinaryLogDecoder.read(file, seen);

        assertEquals(0, result.unresolvedIds, "keyId 0 is 'no key', not an unresolved id");
        assertEquals(List.of("record TickEvent 1/2/3", "  pricer.null=", "end"), seen.events);
    }

    @Test
    void anIdWithNoDictionaryEntryIsCountedAndNamedRatherThanDropped() {
        byte[] file = new Encoder()
                .dict(1, "TickEvent")
                .record(1, 1, 2, 3, new long[]{slot0(99, 98, BinaryLogDecoder.TAG_INT), 5})
                .bytes();

        Collector seen = new Collector();
        BinaryLogDecoder.Result result = BinaryLogDecoder.read(file, seen);

        assertEquals(2, result.unresolvedIds, "both the node and the key ids are unresolved");
        assertEquals("  id:99.id:98=5", seen.events.get(1));
    }

    @Test
    void aTruncatedTrailingRecordIsReportedRatherThanThrown() throws IOException {
        // A half-written trailing record is the expected end state of a crashed process, and the usual
        // reason to open an audit log is that a process crashed.
        byte[] whole = new Encoder()
                .dict(1, "TickEvent").dict(2, "pricer").dict(3, "price")
                .record(1, 1, 2, 3, new long[]{slot0(2, 3, BinaryLogDecoder.TAG_INT), 11})
                .record(1, 4, 5, 6, new long[]{slot0(2, 3, BinaryLogDecoder.TAG_INT), 22})
                .bytes();
        byte[] cut = new byte[whole.length - 9];
        System.arraycopy(whole, 0, cut, 0, cut.length);

        Collector seen = new Collector();
        BinaryLogDecoder.Result result = BinaryLogDecoder.read(cut, seen);

        assertEquals(1, result.records, "the complete record is still delivered");
        assertTrue(result.truncatedBytes > 0, "and the unusable tail is reported: " + result);
        assertEquals("  pricer.price=11", seen.events.get(1));
    }

    @Test
    void aFileWithoutTheMagicIsRefusedByName() {
        byte[] notOurs = "eventLogRecord:\n  eventTime: 1\n".getBytes(StandardCharsets.UTF_8);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> BinaryLogDecoder.read(notOurs, new Collector()));
        assertTrue(e.getMessage().contains("FLXA"), e.getMessage());
    }

    @Test
    void anUnknownFrameTagStopsTheScanInsteadOfGuessingALength() {
        byte[] good = new Encoder()
                .dict(1, "TickEvent").dict(2, "pricer").dict(3, "price")
                .record(1, 1, 2, 3, new long[]{slot0(2, 3, BinaryLogDecoder.TAG_INT), 11})
                .bytes();
        byte[] withJunk = new byte[good.length + 4];
        System.arraycopy(good, 0, withJunk, 0, good.length);
        withJunk[good.length] = 0x7F;   // no such frame tag

        Collector seen = new Collector();
        BinaryLogDecoder.Result result = BinaryLogDecoder.read(withJunk, seen);

        assertEquals(1, result.records);
        assertEquals(4, result.truncatedBytes, "the unknown frame and its tail are unusable");
    }

    @Test
    void aRecordStraddlingAChunkBoundaryIsDecodedWhole(@org.junit.jupiter.api.io.TempDir Path dir)
            throws IOException {
        // The specification requires this test to exist. It is only runnable because the chunk size is
        // adjustable - a 2 GiB fixture would make it a test nobody runs.
        Encoder encoder = new Encoder()
                .dict(1, "TickEvent").dict(2, "pricer").dict(3, "price");
        for (int i = 0; i < 40; i++) {
            encoder.record(1, i, i, i, new long[]{slot0(2, 3, BinaryLogDecoder.TAG_INT), i});
        }
        Path file = dir.resolve("straddle.flxa");
        Files.write(file, encoder.bytes());

        int previous = BinaryLogDecoder.chunkBytes;
        long previousLimit = BinaryLogDecoder.singleMapLimit;
        try {
            // BOTH knobs matter. Setting only the chunk size leaves the file under the single-map
            // threshold, so the chunked path never runs and this test passes without testing anything -
            // which is what it did when first written.
            BinaryLogDecoder.singleMapLimit = 16;
            // Small enough that records land across boundaries, and not a multiple of the record size.
            BinaryLogDecoder.chunkBytes = 37;
            Collector seen = new Collector();
            BinaryLogDecoder.Result result = BinaryLogDecoder.read(file, seen);

            assertEquals(40, result.records, "every record survives the boundaries: " + result);
            assertEquals(40, result.entries);
            assertEquals(0, result.truncatedBytes);
            assertEquals("  pricer.price=39", seen.events.get(seen.events.size() - 2));
        } finally {
            BinaryLogDecoder.chunkBytes = previous;
            BinaryLogDecoder.singleMapLimit = previousLimit;
        }
    }
}
