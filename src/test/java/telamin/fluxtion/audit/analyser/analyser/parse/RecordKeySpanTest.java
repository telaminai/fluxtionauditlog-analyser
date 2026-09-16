package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader.TextEncoding;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordKeySpanTest {
    @Test
    void completeKeysKeepTheirIdentityAcrossEscapesUnicodeCrLfAndSkippedComments() {
        List<String> names = List.of("price", "price: adjusted", "desk.price", "@unkeyed", "null", "",
                "with space", "a\"b", "a\\b", "a\nb", "δ😀");
        String prefix = "---\r\neventLogRecord:\r\n  eventToString: price: 42\r\n  nodeLogs:\r\n# ignored\r\n\r\n";
        StringBuilder text = new StringBuilder(prefix).append("    - n: { ");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) text.append(", ");
            text.append(NodeLogTokenizer.quote(names.get(i))).append(": ").append(i);
        }
        text.append(", @unkeyed: 42, @invoked: true, text: \"contains price: 42\"}\r\n  endTime: 2\r\n");
        LogRecord record = RecordParser.parse(text.toString(), 0, TextEncoding.QUOTED_SCALARS);
        int from = prefix.length();
        for (String name : names) {
            String encoded = NodeLogTokenizer.quote(name);
            int start = text.indexOf(encoded, from);
            assertTrue(start >= from);
            for (int offset = start; offset < start + encoded.length(); offset++) {
                assertNotNull(record.keyAt(offset), "offset=" + offset + " name=" + name);
                assertEquals(name, record.keyAt(offset).key());
            }
            assertNull(record.keyAt(start + encoded.length()), "colon is not part of key");
            from = start + encoded.length();
        }
        assertNull(record.keyAt(text.indexOf("eventToString: price") + 15));
        assertNull(record.keyAt(text.indexOf(", @unkeyed: 42") + 3));
        assertNull(record.keyAt(text.indexOf("@invoked: true") + 2));
        assertNull(record.keyAt(text.indexOf("contains price") + 10));
        assertTrue(record.nodeLogs().getFirst().traced());
    }

    @Test
    void legacySpellingAndMultilineFallbackRemainTheDeclaredGrammarsDecision() {
        String text = "eventLogRecord:\n nodeLogs:\n  - n: { \"price\": 1, @unkeyed: 2}\n";
        LogRecord legacy = RecordParser.parse(text, 0);
        assertEquals("\"price\"", legacy.keyAt(text.indexOf("price")).key());
        assertEquals("@unkeyed", legacy.keyAt(text.indexOf("@unkeyed")).key());
        String multiline = "eventLogRecord:\n nodeLogs:\n  - n: { first: 1,\n     second: 2}\n";
        LogRecord record = RecordParser.parse(multiline, 0);
        assertEquals(2, record.nodeLogs().getFirst().entries().size());
        for (int i = 0; i < multiline.length(); i++) assertNull(record.keyAt(i));
    }

    @Test
    void repeatedNodesAndCommentsMapToTheirOwnSourceLine() {
        String text = "eventLogRecord:\n nodeLogs:\n  - n: { a: 1}\n# comment\n\n  - n: { b: 2}\n";
        LogRecord record = RecordParser.parse(text, 0);
        assertEquals("a", record.keyAt(text.indexOf("a: 1")).key());
        assertEquals("b", record.keyAt(text.indexOf("b: 2")).key());
        assertEquals(2, record.nodeLogs().size());
    }
}
