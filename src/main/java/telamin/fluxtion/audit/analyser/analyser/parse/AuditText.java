package telamin.fluxtion.audit.analyser.analyser.parse;

/**
 * THE strip for audit-log text: §1a whitespace, plus a leading byte-order mark.
 *
 * <p><b>Why this class exists.</b> The rule was written six times — in {@code StreamEndMarker},
 * {@code ProducerDiagnostics}, {@code YamlAuditReader}, {@code RecordFramer}, {@code RecordParser} and
 * {@code HeaderParser} — and the copies drifted. Each drift cost something real:
 *
 * <ul>
 *   <li>{@code String.strip()} keeps U+FEFF, because Java treats a BOM as a character rather than as
 *       whitespace. A record behind a BOM therefore failed every test of the form
 *       "does this line start with X".</li>
 *   <li>In the framers a BOM'd leading {@code ---} stopped separating, so a healthy file ran its head
 *       together, and a BOM-only file framed as one record instead of reading as empty.</li>
 *   <li>In {@code RecordParser} the first record's {@code #} header comment went unrecognised, so that
 *       record lost its thread, level and logger — and because the logger's level is read from there,
 *       {@code auditLevelFinest} fell from DEBUG to INFO and coverage then said debug calls might be
 *       missing. <b>A byte-order mark changed a verdict.</b></li>
 *   <li>It is not only a first-line problem: a concatenated file — {@code cat run1.yaml run2.yaml} —
 *       carries a BOM in the middle, and every record behind it was affected the same way.</li>
 * </ul>
 *
 * <p>So the rule lives here once, and callers use it rather than {@code strip()} or {@code trim()}.
 * {@code ByteRecordFramer} necessarily keeps its own — it works on bytes, where the BOM is the sequence
 * {@code EF BB BF} — and says so at its own check.
 */
public final class AuditText {

    private AuditText() {
    }

    /**
     * §1a whitespace and a leading byte-order mark removed from both ends.
     *
     * <p>A BOM is only meaningful at the head, so only a leading one is dropped — and after the
     * whitespace pass, so that a BOM preceded by a space is still found.
     */
    static String strip(String s) {
        String t = asciiStrip(s);
        // EVERY leading mark (round 4 additions): `cat bom-only.yaml run.yaml` gives two, and removing one
        // left the second to hide the record key and a `#` header — the framers already loop, this did not.
        while (!t.isEmpty() && isBom(t.charAt(0))) t = asciiStrip(t.substring(1));
        return t;
    }

    /**
     * §1a whitespace only: space, tab, CR and LF. Nothing else.
     *
     * <p>Deliberately narrower than {@link String#strip()}, which removes every Unicode space and once
     * cost this parser a class of misread lines.
     */
    static String asciiStrip(String s) {
        int a = 0, b = s.length();
        while (a < b && isSpace(s.charAt(a))) a++;
        while (b > a && isSpace(s.charAt(b - 1))) b--;
        return s.substring(a, b);
    }

    /** True when this character is a byte-order mark. */
    static boolean isBom(char c) {
        return c == '﻿';
    }

    /**
     * True when {@code s} holds nothing but whitespace and byte-order marks, wherever they sit. Used only
     * where a reader decides whether there is anything to sniff at all ({@code YamlAuditReader}).
     */
    public static boolean isBlankIgnoringBoms(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!isBom(c) && !Character.isWhitespace(c)) return false;
        }
        return true;
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }
}
