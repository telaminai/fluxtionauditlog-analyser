package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.export.EvidenceText;

import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The <b>Logical</b> rendering of a record's {@code nodeLogs}: the cycle as a propagation, one node per
 * block, its values indented beneath it.
 *
 * <p>The raw YAML is the other view and stays available, because it is the evidence — but it is laid out
 * for a parser, not a reader: one node's whole state is a single long {@code { a: 1, b: 2}} line, so
 * comparing what two nodes saw means scanning sideways. Here each value is on its own line under the node
 * that logged it, in dispatch order, which is the order the question is usually asked in.
 *
 * <p><b>Framework-added keys are muted, not hidden.</b> Under {@code addEventAudit(LogLevel.TRACE)} every
 * entry starts with {@code thread} and {@code method} (and, from runtime 1.1.1, {@code annotation}); only
 * that leading run is muted ({@link Layout#traceLines()}), so a node's own key spelled the same is not.
 * They are what makes the traced regime readable
 * (absence becomes evidence) but they are not what the node computed, so they are toned down rather than
 * dropped — dropping them would hide the very marker that tells you the record is traced.
 *
 * <p>Layout is separated from styling so the awkward part — offsets, which drive click-to-source and the
 * step cursor's highlight — is testable without a display.
 */
public final class LogicalLogView {

    private LogicalLogView() { }

    /** A node's block in the rendered text: {@code [start, end)} covering its header and values. */
    public record Block(int start, int end, int headerStart, int headerEnd, String instanceId, String method) { }

    /** Rendered text plus the offsets callers need to map clicks and cursor positions back to nodes. */
    public record Layout(String text, List<Block> blocks, List<Integer> recordStarts, java.util.Set<Integer> traceLines) {

        public Layout(String text, List<Block> blocks, List<Integer> recordStarts) {
            this(text, blocks, recordStarts, java.util.Set.of());
        }

        /** The block containing {@code offset}, or null when the offset is between blocks. */
        public Block blockAt(int offset) {
            for (Block b : blocks) {
                if (offset >= b.start() && offset < b.end()) return b;
            }
            return null;
        }

        /** The {@code occurrence}-th block for this instance id — a node can log more than once. */
        public Block block(String instanceId, int occurrence) {
            int seen = 0;
            for (Block b : blocks) {
                if (!b.instanceId().equals(instanceId)) continue;
                if (seen++ == occurrence) return b;
            }
            return null;
        }
    }

    /** Lay out the records. Pure: no Swing, no colours — just text and the offsets into it. */
    public static Layout layout(List<LogRecord> records) {
        StringBuilder sb = new StringBuilder();
        List<Block> blocks = new ArrayList<>();
        List<Integer> recordStarts = new ArrayList<>();
        java.util.Set<Integer> traceLines = new java.util.HashSet<>();
        if (records == null || records.isEmpty()) {
            return new Layout("", List.of(), List.of());
        }

        for (int i = 0; i < records.size(); i++) {
            if (i > 0) sb.append('\n');
            LogRecord record = records.get(i);
            recordStarts.add(sb.length());

            sb.append(record.event() == null ? "(no event)" : record.event());
            String time = record.headerTime();
            if (time != null && !time.isBlank()) sb.append("   ").append(time);
            sb.append('\n');
            if (record.eventToString() != null && !record.eventToString().isBlank()) {
                // a signature spans lines; indent every one of them so the block stays a block
                for (String line : record.eventToString().split("\n")) {
                    sb.append("  ").append(line.strip()).append('\n');
                }
            }
            if (record.brokenAtLine() > 0) {
                // UPS-1: not "no node logged" — the node logs are there and were deliberately not read
                sb.append("  (node logs not read: this record's structure breaks at its line ").append(record.brokenAtLine())
                        .append(" — a value was written unquoted with a line break in it. The full record is on the Text tab.)\n");
                continue;
            }
            if (record.nodeLogs().isEmpty()) {
                sb.append("  (no node logged in this cycle)\n");
                continue;
            }

            for (NodeLog node : record.nodeLogs()) {
                int start = sb.length();
                int headerStart = sb.length();
                sb.append("  ").append(EvidenceText.name(node.instanceId())).append('\n');
                int headerEnd = sb.length() - 1;

                // muted by POSITION, not spelling: only the leading run tracing wrote (AuditTrace.traceEntryCount),
                // in the legacy text grammar or wherever a wire TRACE marker says the node ran (the binary log
                // writes the same run after its marker)
                int run = record.textEncoding() == telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader.TextEncoding.LEGACY
                        || node.traced()
                        ? telamin.fluxtion.audit.analyser.analyser.topology.AuditTrace.traceEntryCount(node.entries()) : 0;
                String method = null;
                for (KV kv : node.entries().subList(0, run)) {
                    if ("method".equals(kv.key()) || "asyncMethod".equals(kv.key())) method = kv.rawValue();
                }
                if (method == null && run == 0) {
                    for (KV kv : node.entries()) {
                        if ("method".equals(kv.key())) { method = kv.rawValue(); break; }   // first, never a later business key
                    }
                }
                if (method == null) method = record.callback();
                if (method == null && !node.entries().isEmpty()) method = node.entries().get(0).key();

                int index = 0;
                for (KV kv : node.entries()) {
                    if (index++ < run) traceLines.add(sb.length());
                    sb.append("      ").append(EvidenceText.name(kv.key()))
                            .append(": ").append(EvidenceText.value(kv)).append('\n');
                }
                blocks.add(new Block(start, sb.length(), headerStart, headerEnd, node.instanceId(), method));
            }
        }
        return new Layout(sb.toString(), List.copyOf(blocks), List.copyOf(recordStarts), java.util.Set.copyOf(traceLines));
    }

    /** How a keyless entry is shown: the binary reader's own marker, so the two views agree. */
    public static final String UNKEYED_MARKER = EvidenceText.UNKEYED_MARKER;

    /**
     * True when invocation tracing can write a key of this spelling — one list owned by
     * {@link telamin.fluxtion.audit.analyser.analyser.topology.AuditTrace#isTraceKey}. Spelling only: what is
     * muted is decided by position ({@link Layout#traceLines()}).
     */
    public static boolean isFrameworkKey(String key) {
        return telamin.fluxtion.audit.analyser.analyser.topology.AuditTrace.isTraceKey(key);
    }

    // ---- styling ----------------------------------------------------------------------------------

    /** Colour a laid-out document. Theme-aware, and re-run on theme change like the other highlighters. */
    public static void render(StyledDocument doc, Layout layout) {
        boolean dark = ThemeManager.isDark();
        SimpleAttributeSet base = attr(dark ? 0xC9D1D9 : 0x1F2328, false, false);
        SimpleAttributeSet eventName = attr(dark ? 0xFFA657 : 0xBC4C00, true, false);
        SimpleAttributeSet eventText = attr(dark ? 0x8B949E : 0x6A737D, false, true);
        SimpleAttributeSet nodeName = attr(dark ? 0x7EE787 : 0x116329, true, false);
        SimpleAttributeSet key = attr(dark ? 0x79C0FF : 0x0550AE, false, false);
        SimpleAttributeSet muted = attr(dark ? 0x6E7681 : 0x8C959F, false, true);
        SimpleAttributeSet number = attr(dark ? 0xD2A8FF : 0x8250DF, false, false);
        SimpleAttributeSet keyword = attr(dark ? 0xFF7B72 : 0xB31D28, false, false);

        String text = layout.text();
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, text, base);
        } catch (BadLocationException e) {
            return;
        }

        int pos = 0;
        for (String line : text.split("\n", -1)) {
            styleLine(doc, line, pos, layout, eventName, eventText, nodeName, key, muted, number, keyword);
            pos += line.length() + 1;
        }
        UiTheme.applyReadingRhythm(doc);
    }

    private static void styleLine(StyledDocument doc, String line, int offset, Layout layout,
                                  SimpleAttributeSet eventName, SimpleAttributeSet eventText,
                                  SimpleAttributeSet nodeName, SimpleAttributeSet key,
                                  SimpleAttributeSet muted, SimpleAttributeSet number,
                                  SimpleAttributeSet keyword) {
        if (line.isEmpty()) return;

        int indent = line.length() - line.stripLeading().length();
        String body = line.strip();

        if (indent == 0) {                                     // event header: "MarketDataEvent   09:00:00"
            int sp = line.indexOf("   ");
            doc.setCharacterAttributes(offset, sp < 0 ? line.length() : sp, eventName, true);
            if (sp >= 0) doc.setCharacterAttributes(offset + sp, line.length() - sp, muted, true);
            return;
        }
        if (indent == 2) {                                     // node header, or the event's toString
            boolean isNode = layout.blocks().stream().anyMatch(b -> b.headerStart() == offset);
            doc.setCharacterAttributes(offset + indent, body.length(), isNode ? nodeName : eventText, true);
            return;
        }

        // a value line: "      key: value"
        int colon = line.indexOf(':', indent);
        if (colon < 0) return;
        String keyText = line.substring(indent, colon);
        boolean trace = layout.traceLines().contains(offset);
        doc.setCharacterAttributes(offset + indent, keyText.length(), trace ? muted : key, true);
        int valueStart = colon + 1;
        String value = line.substring(valueStart);
        if (trace) {
            doc.setCharacterAttributes(offset + valueStart, value.length(), muted, true);
            return;
        }
        java.util.regex.Matcher nm = NUM.matcher(value);
        while (nm.find()) {
            doc.setCharacterAttributes(offset + valueStart + nm.start(), nm.end() - nm.start(), number, true);
        }
        java.util.regex.Matcher wm = WORD.matcher(value);
        while (wm.find()) {
            doc.setCharacterAttributes(offset + valueStart + wm.start(), wm.end() - wm.start(), keyword, true);
        }
    }

    private static final java.util.regex.Pattern NUM = java.util.regex.Pattern.compile(
            "(?<![\\w.])[+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?(?![\\w.])");
    private static final java.util.regex.Pattern WORD = java.util.regex.Pattern.compile(
            "\\b(true|false|null|NaN|Infinity)\\b");

    private static SimpleAttributeSet attr(int rgb, boolean bold, boolean italic) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, new Color(rgb));
        StyleConstants.setFontFamily(a, UiTheme.monoFamily());
        StyleConstants.setBold(a, bold);
        StyleConstants.setItalic(a, italic);
        return a;
    }
}
