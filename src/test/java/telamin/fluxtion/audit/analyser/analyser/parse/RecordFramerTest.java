package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordFramerTest {

    @Test
    void framesTheSampleInto21Records() {
        List<RawRecord> records = RecordFramer.frame(Samples.sample());
        assertEquals(21, records.size(), "sample.yml has 21 eventLogRecords between --- separators");
        for (RawRecord r : records) {
            assertTrue(r.text().contains("eventLogRecord:"), "each slice holds an eventLogRecord");
            assertFalse(r.text().contains("\n---"), "a slice must not span a separator");
        }
        assertTrue(records.get(0).text().contains("StartComplete"), "first record is the StartComplete lifecycle");
    }

    @Test
    void offsetsPointBackAtTheOriginalText() {
        String file = Samples.sample();
        for (RawRecord r : RecordFramer.frame(file)) {
            String slice = file.substring((int) r.offset(), (int) r.offset() + r.length());
            assertEquals(r.text(), slice, "offset+length must re-slice the exact record text");
        }
    }

    @Test
    void handlesCrlfBlankLinesAndNoTrailingSeparator() {
        String doc = "---\r\n#00:00:00.000 [t] INFO L\r\neventLogRecord:\r\n  logTime: 5\r\n\r\n"
                + "---\n#00:00:01.000 [t] INFO L\neventLogRecord:\n  logTime: 6\n";  // no trailing ---
        List<RawRecord> records = RecordFramer.frame(doc);
        assertEquals(2, records.size());
        assertTrue(records.get(1).text().contains("logTime: 6"));
    }

    @Test
    void emptyInputYieldsNoRecords() {
        assertEquals(0, RecordFramer.frame("").size());
        assertEquals(0, RecordFramer.frame("---\n---\n").size());
    }
}
