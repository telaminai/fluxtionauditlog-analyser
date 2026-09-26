package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F1 — a byte-order mark must not cost a record its header, because that changes a VERDICT.
 *
 * <p>{@code String.strip()} keeps U+FEFF, so the {@code #} header comment behind a BOM was never
 * recognised. The record then lost its thread, level and logger — and the logger's level is where
 * {@code auditLevelFinest} comes from, so it fell from DEBUG to INFO and coverage went on to say that
 * debug calls might be missing. A byte-order mark changed what the analyser concluded.
 *
 * <p><b>Not only the first line.</b> A concatenated file — {@code cat run1.yaml run2.yaml} — carries a
 * BOM in the middle, and every record behind it was affected the same way.
 *
 * <p>The rule now lives once, in {@link AuditText}. The framing tests could not have caught this
 * because they use no header comments.
 */
class ByteOrderMarkHeaderTest {

    private static final String BOM = "﻿";

    private static final String HEADER =
            "#00:00:00.000 [main] DEBUG com.acme.Pricer\n";

    private static final String BODY =
            "eventLogRecord:\n  logTime: 1000\n  event: Tick\n"
                    + "  nodeLogs:\n    - pricer: { price: 1.5}\n";

    private static telamin.fluxtion.audit.analyser.analyser.model.LogRecord parse(String recordText) {
        return RecordParser.parse(recordText, 0);
    }

    @Test
    void aHeaderCommentIsReadWithoutAByteOrderMark() {
        var h = parse(HEADER + BODY);
        assertEquals("main", h.thread(), "precondition: the header parses at all");
        assertEquals("DEBUG", h.level());
        assertEquals("com.acme.Pricer", h.logger());
    }

    /** The defect: the same header behind a BOM. */
    @Test
    void aHeaderCommentBehindAByteOrderMarkIsStillRead() {
        var h = parse(BOM + HEADER + BODY);

        assertEquals("main", h.thread(),
                "a BOM cost this record its thread — String.strip() keeps U+FEFF, so the '#' test failed");
        assertEquals("DEBUG", h.level(),
                "and its LEVEL, which is where auditLevelFinest comes from: DEBUG became INFO and "
                        + "coverage then said debug calls might be missing. A BOM changed a verdict");
        assertEquals("com.acme.Pricer", h.logger(), "and its logger");
    }

    /**
     * The concatenation case: a BOM in the MIDDLE of a file, at a record boundary, which is what
     * {@code cat run1.yaml run2.yaml} produces.
     */
    @Test
    void aHeaderBehindAMidFileByteOrderMarkIsStillRead() {
        String concatenated = HEADER + BODY + "---\n" + BOM + HEADER + BODY;
        HeapLogStore store = new HeapLogStore(concatenated);

        assertEquals(2, store.size(), "both records frame");
        assertEquals("DEBUG", store.record(1).level(),
                "the record behind the mid-file BOM keeps its level; every record after a "
                        + "concatenation point was affected, not only the first in the file");
    }

    /** A BOM must not be mistaken for content when the line is otherwise only a comment. */
    @Test
    void aBomOnlyHeaderLineIsNotTreatedAsARecordBody() {
        var h = parse(BOM + "#00:00:00.000 [worker-1] INFO com.acme.Risk\n" + BODY);
        assertEquals("worker-1", h.thread());
        assertEquals("INFO", h.level());
    }
}
