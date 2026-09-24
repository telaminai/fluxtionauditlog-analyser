package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 round 4 additions, on top of the F1 consolidation: the byte-order-mark rule has ONE text home, {@link AuditText}, and every site that
 * decides something from the head of a file, record or line goes through it.
 *
 * <p>Round 4 found a sixth site the earlier fixes missed: {@code RecordParser} used {@code strip()},
 * which keeps U+FEFF, so a BOM before a record's {@code #} header dropped that record's thread, level
 * and logger — and changed {@code auditLevelFinest}, a coverage verdict. The tests here drive the
 * header path through both stores, at the file head and mid-file, and pin the structural rule so a
 * seventh site cannot reappear silently.
 */
class ByteOrderMarkSitesTest {

    private static final String BOM = "﻿";

    /** Two records, each opened by the documented header comment, DEBUG then INFO. */
    private static final String HEADERED =
            "#10:57:37.431 [worker-1] DEBUG maker\n"
                    + "eventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - a: { v: 1}\n"
                    + "---\n"
                    + "#10:57:37.432 [worker-2] INFO maker\n"
                    + "eventLogRecord:\n  logTime: 1001\n  event: Tick\n  nodeLogs:\n    - a: { v: 2}\n"
                    + "---\n";

    private static MappedLogStore mapped(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return new MappedLogStore(p);
    }

    private static List<String> threads(LogStore s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) out.add(s.record(i).thread());
        return out;
    }

    private static List<String> levels(LogStore s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) out.add(s.record(i).level());
        return out;
    }

    private static List<String> kinds(LogStore store) {
        return ProducerDiagnostics
                .of(store.index(), store::rawText, List.of(), List.of(), false)
                .findings().stream().map(f -> f.kind().name()).toList();
    }

    @Test
    void aBomBeforeTheFirstHeaderCommentKeepsThreadAndLevel_heapAndMapped(@TempDir Path dir) throws IOException {
        HeapLogStore clean = new HeapLogStore(HEADERED);
        assertEquals(List.of("worker-1", "worker-2"), threads(clean));
        assertEquals(List.of("DEBUG", "INFO"), levels(clean));

        HeapLogStore heap = new HeapLogStore(BOM + HEADERED);
        assertEquals(threads(clean), threads(heap), "F1: a BOM'd '#' header must still be read as a header (heap)");
        assertEquals(levels(clean), levels(heap), "F1: the finest level must not change behind a BOM (heap)");

        try (MappedLogStore m = mapped(dir, "bom-head.yaml", BOM + HEADERED)) {
            assertEquals(threads(clean), threads(m), "F1: a BOM'd '#' header must still be read as a header (mapped)");
            assertEquals(levels(clean), levels(m), "F1: the finest level must not change behind a BOM (mapped)");
        }
    }

    @Test
    void aMidFileBomFromConcatenatedRunsKeepsEveryHeader_heapAndMapped(@TempDir Path dir) throws IOException {
        String joined = BOM + HEADERED + BOM + HEADERED;   // `cat run1.yaml run2.yaml`, both BOM'd
        HeapLogStore heap = new HeapLogStore(joined);
        assertEquals(List.of("worker-1", "worker-2", "worker-1", "worker-2"), threads(heap),
                "F1: the record behind a mid-file BOM keeps its header (heap)");
        try (MappedLogStore m = mapped(dir, "bom-joined.yaml", joined)) {
            assertEquals(threads(heap), threads(m), "F1: heap and mapped agree on a mid-file BOM");
        }
    }

    @Test
    void aDoubleLeadingBomIsNotAMissingRecordKey(@TempDir Path dir) throws IOException {
        String doubled = BOM + BOM + HEADERED;              // `cat bom-only.yaml run.yaml`
        assertFalse(kinds(new HeapLogStore(doubled)).contains("NO_RECORD_KEY"),
                "F3: two leading BOMs are still only BOMs (heap)");
        try (MappedLogStore m = mapped(dir, "bom-double.yaml", doubled)) {
            assertFalse(kinds(m).contains("NO_RECORD_KEY"), "F3: two leading BOMs are still only BOMs (mapped)");
            assertEquals(List.of("worker-1", "worker-2"), threads(m));
        }
    }

    @Test
    void theFramersSkipEveryLeadingBom_beforeASeparatorAndInABomOnlyFile(@TempDir Path dir) throws IOException {
        String doubledSeparator = BOM + BOM + "---\n" + HEADERED;
        HeapLogStore heap = new HeapLogStore(doubledSeparator);
        assertEquals(2, heap.size(), "F3: a separator behind two BOMs still separates (string framer)");
        assertFalse(kinds(heap).contains("NO_RECORD_KEY"));
        try (MappedLogStore m = mapped(dir, "bom-double-sep.yaml", doubledSeparator)) {
            assertEquals(2, m.size(), "F3: a separator behind two BOMs still separates (byte framer)");
            assertFalse(kinds(m).contains("NO_RECORD_KEY"));
        }
        assertEquals(0, new HeapLogStore(BOM + BOM).size(), "F3: a file of only BOMs is empty (string framer)");
        try (MappedLogStore m = mapped(dir, "bom-double-only.yaml", BOM + BOM + "\n")) {
            assertEquals(0, m.size(), "F3: a file of only BOMs is empty (byte framer)");
        }
    }

    @Test
    void theHeaderParserItselfSkipsLeadingBoms() {
        RecordHeader h = HeaderParser.parse(BOM + BOM + "#10:57:37.431 [w] WARN x");
        assertEquals("w", h.thread(), "F1: HeaderParser must see a header behind BOMs");
        assertEquals("WARN", h.level());
    }

    @Test
    void followDoesNotFailOnAHalfWrittenCharacter(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("follow.yaml");
        Files.writeString(p, "---\n", StandardCharsets.UTF_8);
        HeapLogStore store = HeapLogStore.fromFile(p).forFollow();
        byte[] rest = ("#10:57:37.431 [wé] INFO x\neventLogRecord:\n  logTime: 1\n---\n")
                .getBytes(StandardCharsets.UTF_8);
        int split = new String(rest, StandardCharsets.ISO_8859_1).indexOf('Ã') + 1;  // inside the 2-byte é
        Files.write(p, java.util.Arrays.copyOfRange(rest, 0, split), StandardOpenOption.APPEND);
        assertDoesNotThrow(() -> store.appendFrom(p),
                "F2: a poll landing mid-character must wait for the rest, not fail the read");
        Files.write(p, java.util.Arrays.copyOfRange(rest, split, rest.length), StandardOpenOption.APPEND);
        assertEquals(1, store.appendFrom(p), "the completed record arrives on the next poll");
        assertEquals("wé", store.record(0).thread());
    }

    @Test
    void genuinelyMalformedBytesStillFailLoudly() {
        byte[] bad = {'a', (byte) 0xC3, 'b', '\n'};                 // a lead byte followed by ASCII, mid-file
        assertThrows(java.nio.charset.CharacterCodingException.class, () -> HeapLogStore.completeUtf8(bad));
    }

    @Test
    void oneControlEventPredicateForTheEmptyLogFamilyAndCoverage() {
        assertTrue(ProducerDiagnostics.isControlEvent("EventLogControlEvent"));
        assertTrue(ProducerDiagnostics.isControlEvent("com.telamin.fluxtion.runtime.audit.EventLogControlEvent"));
        assertFalse(ProducerDiagnostics.isControlEvent("FakeEventLogControlEventX"),
                "F4: a lookalike is not a control record for ONLY_CONTROL_EVENTS either");
        String lookalikes = "eventLogRecord:\n  logTime: 1\n  event: FakeEventLogControlEventX\n---\n";
        assertFalse(kinds(new HeapLogStore(lookalikes)).contains("ONLY_CONTROL_EVENTS"),
                "F4: ONLY_CONTROL_EVENTS uses the same exact predicate as MA-8");
    }

    /**
     * The structural half: no source file other than {@link AuditText} tests for a BOM itself.
     * A new head-of-line site must call it — or this fails and says where.
     *
     * <p><b>This guard matched TEXT for three rounds, and text is the wrong thing to match.</b> Round 4
     * found it blind to {@code 0xFEFF}; adding that string left six further spellings of the same
     * number — lowercase {@code 0xfeff}, the digit separator {@code 0xFE_FF}, lowercase byte constants,
     * the signed bytes {@code -17/-69/-65}, and octal — each of which the final review planted and got
     * past it. Every one of those is the same VALUE, so the guard now lexes integer literals and
     * compares values (see {@link #integerLiterals}). The character forms stay textual, because
     * {@code '\}{@code uFEFF'} is not an integer literal; that check is case- and repetition-insensitive
     * because Java accepts {@code \}{@code ufeff} and {@code \}{@code uuFEFF} as the same escape.
     *
     * <p><b>The byte triple is only a finding when all three values appear in ONE file.</b> A BOM in
     * bytes is the sequence {@code EF BB BF} and a site that reads it must test all three; a lone 191
     * in unrelated code is not a BOM site, and flagging it by value would have made this gate something
     * people silence with exclusions.
     *
     * <p><b>The limit, stated.</b> No lexical check can make "there is no seventh site" true. A
     * constant expression ({@code 0xFE00 + 0xFF}), a value read from a constant in another class, or a
     * decomposition into arithmetic all pass this and always will. The real defence is the behavioural
     * half of this class and its siblings — every site is fed a BOM and asserted on. This guard only
     * makes the CHEAP mistake, writing the rule a seventh time, loud.
     *
     * <p><b>Witnessed, not reasoned.</b> Thirteen spellings were planted into a main source one at a
     * time and the guard re-run against each. Eleven are named with file and line: uppercase and
     * lowercase hex, {@code 0xFE_FF}, decimal, octal, {@code '\}{@code uFEFF'} in three cases, and the
     * byte triple written lowercase, signed and octal. Two pass, both on purpose and both recorded
     * above: a single byte value on its own, and the constant expression.
     */
    @Test
    void theBomRuleLivesInOneClass() throws IOException {
        Path root = Path.of("src/main/java");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                String fn = f.getFileName().toString();
                // AuditText owns the text rule; ByteRecordFramer keeps the byte form and says so.
                if (fn.equals("AuditText.java") || fn.equals("ByteRecordFramer.java")) continue;
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                List<String> here = new ArrayList<>();
                boolean[] bomByte = new boolean[3];                    // EF, BB, BF seen in THIS file
                for (int n = 0; n < lines.size(); n++) {
                    String t = lines.get(n).strip();
                    if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) continue;
                    String at = root.relativize(f) + ":" + (n + 1);
                    if (UNICODE_ESCAPE.matcher(t).find() || t.indexOf('﻿') >= 0) {
                        offenders.add(at);
                        continue;
                    }
                    for (long v : integerLiterals(t)) {
                        if (v == 0xFEFF) {
                            offenders.add(at);
                            break;
                        }
                        // signed and unsigned readings of the same byte
                        if (v == 0xEF || v == 0xEF - 256) { bomByte[0] = true; here.add(at); }
                        if (v == 0xBB || v == 0xBB - 256) { bomByte[1] = true; here.add(at); }
                        if (v == 0xBF || v == 0xBF - 256) { bomByte[2] = true; here.add(at); }
                    }
                }
                if (bomByte[0] && bomByte[1] && bomByte[2]) offenders.addAll(here);
            }
        }
        assertEquals(List.of(), offenders, "BOM handling must go through AuditText (or the byte framer)");
    }

    /**
     * The lexer, directly, for every spelling it claims — so a claim in the javadoc above is a test and
     * not a sentence. Independent review found {@code 0_177377} passing while the javadoc said octal was
     * covered; a planted-spelling run only checks the spellings someone thought to plant.
     */
    @Test
    void theLexerReadsEveryClaimedSpellingAsItsValue() {
        String[] spellings = {"0xFEFF", "0xfeff", "0XFEFF", "0xFE_FF", "65279", "65_279", "0177377",
                "0_177377", "0b1111111011111111", "0B1111_1110_1111_1111", "0xFEFFL", "65279l"};
        for (String s : spellings) {
            assertTrue(integerLiterals("x == " + s + ";").contains(0xFEFFL), s + " must read as 0xFEFF");
        }
        assertTrue(integerLiterals("b == -17").contains(-17L), "a preceding minus is a sign");
        assertFalse(integerLiterals("v0xFEFF").contains(0xFEFFL), "inside an identifier it is not a literal");
        assertFalse(integerLiterals("x == 0xFE00 + 0xFF").contains(0xFEFFL),
                "the stated limit: a constant expression is not evaluated");
    }

    /** {@code \}{@code ufeff}, {@code \}{@code uUFEFF}, {@code \}{@code uuuFEFF} — all the same escape to javac. */
    private static final java.util.regex.Pattern UNICODE_ESCAPE =
            java.util.regex.Pattern.compile("\\\\u+[fF][eE][fF][fF]");

    /**
     * Every Java integer literal on {@code line}, by value — hex, binary, octal, decimal, with
     * underscores and an {@code L} suffix, and with a preceding {@code -} read as a sign.
     *
     * <p>Deliberately a lexer and not a parser. It exists to defeat SPELLING, not arithmetic: reading
     * {@code -} as a sign wherever one precedes over-reads subtraction as negation, which is harmless
     * here because a negative reading only ever contributes to the byte triple, and that needs all
     * three values in one file before it says anything.
     */
    static List<Long> integerLiterals(String line) {
        List<Long> out = new ArrayList<>();
        int n = line.length();
        for (int i = 0; i < n; ) {
            char c = line.charAt(i);
            if (!Character.isDigit(c)) {
                i++;
                continue;
            }
            char prev = i == 0 ? ' ' : line.charAt(i - 1);
            boolean startsAToken = !(Character.isLetterOrDigit(prev) || prev == '_' || prev == '$' || prev == '.');
            int radix = 10;
            int from = i;
            if (c == '0' && i + 1 < n && (line.charAt(i + 1) == 'x' || line.charAt(i + 1) == 'X')) {
                radix = 16;
                from = i + 2;
            } else if (c == '0' && i + 1 < n && (line.charAt(i + 1) == 'b' || line.charAt(i + 1) == 'B')) {
                radix = 2;
                from = i + 2;
            } else if (c == '0' && i + 1 < n
                    && (Character.digit(line.charAt(i + 1), 8) >= 0 || line.charAt(i + 1) == '_')) {
                // `0_177377` is octal too: Java allows an underscore straight after the leading zero.
                // Checking only for an octal DIGIT sent it down the decimal path, where it read as
                // 177377 and passed (independent review, O1).
                radix = 8;
                from = i + 1;
            }
            int j = from;
            StringBuilder digits = new StringBuilder();
            while (j < n && (line.charAt(j) == '_' || Character.digit(line.charAt(j), radix) >= 0)) {
                if (line.charAt(j) != '_') digits.append(line.charAt(j));
                j++;
            }
            if (startsAToken && digits.length() > 0) {
                try {
                    long v = Long.parseLong(digits.toString(), radix);
                    out.add(v);
                    int k = i - 1;
                    while (k >= 0 && (line.charAt(k) == ' ' || line.charAt(k) == '\t')) k--;
                    if (k >= 0 && line.charAt(k) == '-') out.add(-v);
                } catch (NumberFormatException tooBig) {
                    // not a value this guard can be about
                }
            }
            i = Math.max(j, i + 1);
        }
        return out;
    }
}
