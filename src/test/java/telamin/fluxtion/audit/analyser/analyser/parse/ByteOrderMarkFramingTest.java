package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A UTF-8 byte-order mark must not change how a file is framed — in EITHER store.
 *
 * <p>U+FEFF is a character, not whitespace, so neither framer's separator test skipped it. Two
 * consequences, both measured:
 * <ul>
 *   <li>a healthy file whose first line is a separator behind a BOM did not separate there, so the head
 *       ran together and record 1 raised {@code NO_RECORD_KEY};</li>
 *   <li>a BOM-only file opened as ONE record with {@code NO_NODE_LOGS}, instead of reading as empty and
 *       raising {@code EMPTY_LOG}.</li>
 * </ul>
 *
 * <p>Driven through {@link HeapLogStore} and {@link MappedLogStore} rather than through the reader
 * registry, because the two framers are separate implementations of the same rule and only the store
 * exercises them.
 */
class ByteOrderMarkFramingTest {

    private static final String BOM = "﻿";

    private static final String TWO_RECORDS =
            "---\n"
                    + "eventLogRecord:\n  logTime: 1000\n  event: Tick\n"
                    + "  nodeLogs:\n    - a: { v: 1}\n"
                    + "---\n"
                    + "eventLogRecord:\n  logTime: 1001\n  event: Tick\n"
                    + "  nodeLogs:\n    - a: { v: 2}\n"
                    + "---\n";

    private static MappedLogStore mapped(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return new MappedLogStore(p);
    }

    private static List<String> kinds(LogStore store) {
        return ProducerDiagnostics
                .of(store.index(), store::rawText, List.of(), List.of(), false)
                .findings().stream().map(f -> f.kind().name()).toList();
    }

    /** A leading separator behind a BOM still separates — heap store. */
    @Test
    void aBomBeforeTheFirstSeparatorDoesNotRunTheHeadTogetherInTheHeapStore() {
        HeapLogStore clean = new HeapLogStore(TWO_RECORDS);
        HeapLogStore bomd = new HeapLogStore(BOM + TWO_RECORDS);

        assertEquals(clean.size(), bomd.size(),
                "a BOM must not change the record count: " + clean.size() + " vs " + bomd.size());
        assertFalse(kinds(bomd).contains("NO_RECORD_KEY"),
                "a healthy BOM'd file must raise nothing: " + kinds(bomd));
    }

    /** And in the mapped store, which frames bytes rather than chars. */
    @Test
    void aBomBeforeTheFirstSeparatorDoesNotRunTheHeadTogetherInTheMappedStore(@TempDir Path dir)
            throws IOException {
        try (MappedLogStore clean = mapped(dir, "clean.yaml", TWO_RECORDS);
             MappedLogStore bomd = mapped(dir, "bom.yaml", BOM + TWO_RECORDS)) {
            assertEquals(clean.size(), bomd.size(), "a BOM must not change the record count");
            assertFalse(kinds(bomd).contains("NO_RECORD_KEY"),
                    "a healthy BOM'd file must raise nothing: " + kinds(bomd));
        }
    }

    /** A BOM-only file is EMPTY, not a one-record file — heap store. */
    @Test
    void aBomOnlyFileIsEmptyInTheHeapStore() {
        HeapLogStore store = new HeapLogStore(BOM);
        assertEquals(0, store.size(),
                "a lone byte-order mark is not a record; it framed one before this fix");
        assertTrue(kinds(store).contains("EMPTY_LOG"),
                "and it must read as empty, not as a record with no node logs: " + kinds(store));
    }

    /** And in the mapped store. */
    @Test
    void aBomOnlyFileIsEmptyInTheMappedStore(@TempDir Path dir) throws IOException {
        try (MappedLogStore store = mapped(dir, "bomonly.yaml", BOM)) {
            assertEquals(0, store.size(), "a lone byte-order mark is not a record");
            assertTrue(kinds(store).contains("EMPTY_LOG"), "it reads as empty: " + kinds(store));
        }
    }

    /**
     * A DOUBLE leading mark is real — two BOM'd files concatenated, or a tool adding one to a file that
     * already had it — and stripping only one left the second, so a healthy record read as having no
     * record key.
     */
    @Test
    void aDoubleByteOrderMarkDoesNotRaiseAFalseWarning() {
        HeapLogStore store = new HeapLogStore(BOM + BOM + TWO_RECORDS);
        assertFalse(kinds(store).contains("NO_RECORD_KEY"),
                "two marks are still not a missing record key: " + kinds(store));
    }
}
