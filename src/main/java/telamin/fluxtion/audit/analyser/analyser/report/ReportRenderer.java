package telamin.fluxtion.audit.analyser.analyser.report;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lays a resolved {@link ReportSpec} out as a printable PDF (spec-investigation-reports M33.2).
 * {@link PdfDoc} is unchanged; the visual language is {@link FindingReport}'s — one ink, light theme,
 * evidence in panels — with two additions this document type needs:
 *
 * <ul>
 *   <li><b>Narrative is VISIBLY narrative (D-I2)</b>: prose sections render with their own accent, a
 *       standing label and an italic-adjacent muted treatment, in the PDF exactly as on screen — an
 *       assertion must never pass as a record, and a reader scanning page 3 does not carry a byline
 *       down from page 1.</li>
 *   <li><b>Tables are declared presentation over derived rows (D-I7/D-I8)</b>: headings, widths,
 *       alignment and formats come from the column spec; numbers set in the monospace face so figures
 *       are tabular; a highlighted row's RULE is printed with the table; the header repeats on every
 *       page a long table spills onto.</li>
 * </ul>
 *
 * <p>Pure apart from the images handed in — content is assembled by the caller (the layer that can
 * read records and screenshot panels), so the whole layout is testable headlessly.
 */
public final class ReportRenderer {

    private ReportRenderer() {
    }

    // FindingReport's restrained palette, plus one accent for the thing this document adds: prose
    private static final Color INK = new Color(0x1F2A44);
    private static final Color MUTED = new Color(0x57606A);
    private static final Color RULE = new Color(0xD8DEE4);
    private static final Color PANEL = new Color(0xF6F8FA);
    private static final Color WARN = new Color(0xB45309);
    private static final Color WARN_BG = new Color(0xFFF8EC);
    private static final Color PROBLEM = new Color(0xB45309);
    private static final Color PROBLEM_BG = new Color(0xFFF8EC);
    private static final Color FIX = new Color(0x15803D);
    private static final Color FIX_BG = new Color(0xF1FAF3);
    private static final Color NARRATIVE = new Color(0x6741D9);
    private static final Color NARRATIVE_BG = new Color(0xF6F3FE);
    private static final Color HIGHLIGHT_BG = new Color(0xFFF1F0);

    private static final float MARGIN = 46;
    private static final float CONTENT_W = PdfDoc.PAGE_W - 2 * MARGIN;
    private static final float BOTTOM = PdfDoc.PAGE_H - 40;

    /** The standing label every narrative block carries (D-I2) — the words are part of the contract. */
    public static final String NARRATIVE_LABEL = "NARRATIVE — the author's account, not log evidence";

    /**
     * Per-section content, assembled by the caller and aligned by index with the spec's sections.
     * Every field is optional; the renderer draws what it is given and never fetches.
     *
     * @param heading   an optional heading for the section
     * @param monoLines record/series evidence as log lines
     * @param picture   a chart or topology image with its caption
     * @param table     derived rows under a declared presentation (TABLE sections)
     */
    public record SectionContent(String heading, List<String> monoLines,
                                 FindingReport.Picture picture, TableData table) {
        public static final SectionContent EMPTY = new SectionContent(null, null, null, null);
    }

    /**
     * A table's DERIVED rows plus the highlight evaluation the caller ran (D-I8: {@code rowWhen} is
     * evaluated STRICTLY against each row's own record, which needs record access the renderer does
     * not have — the renderer only draws the verdicts and PRINTS the rule).
     */
    public record TableData(List<ReportSpec.ColumnSpec> columns, List<List<String>> rows,
                            boolean[] highlighted, String rowWhen, String rowWhenLabel) {
        public TableData {
            columns = columns == null ? List.of() : List.copyOf(columns);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public static byte[] render(ReportSpec spec, ReportResolver.Resolution resolution,
                                List<SectionContent> content, String logFile, String analysedAt) {
        PdfDoc doc = new PdfDoc();
        Cursor c = new Cursor();

        header(doc, c, spec);
        metaStrip(doc, c, spec, logFile, analysedAt);

        // D-I3a: the announce lines come BEFORE any section (acceptance 9) — a reader must know they
        // are looking at a different log or a different view before a single claim renders
        if (resolution.fingerprintMismatch() != null) {
            callout(doc, c, "THIS IS NOT THE LOG THE REPORT WAS WRITTEN AGAINST",
                    resolution.fingerprintMismatch(), WARN, WARN_BG);
        }
        if (resolution.filterDifference() != null) {
            callout(doc, c, "VIEW", resolution.filterDifference(), WARN, WARN_BG);
        }
        if (resolution.summary() != null) {
            callout(doc, c, "UNRESOLVED REFERENCES", resolution.summary(), WARN, WARN_BG);
        }
        if (!spec.notes().isBlank()) {
            callout(doc, c, NARRATIVE_LABEL, spec.notes(), NARRATIVE, NARRATIVE_BG);
        }

        for (int i = 0; i < spec.sections().size(); i++) {
            ReportSpec.SectionSpec s = spec.sections().get(i);
            ReportResolver.SectionResolution r = resolution.sections().get(i);
            SectionContent body = i < content.size() && content.get(i) != null
                    ? content.get(i) : SectionContent.EMPTY;
            section(doc, c, s, r, body);
        }

        footers(doc, spec, logFile);
        return doc.toBytes();
    }

    // ---- sections ----------------------------------------------------------------------------------

    private static void section(PdfDoc doc, Cursor c, ReportSpec.SectionSpec s,
                                ReportResolver.SectionResolution r, SectionContent body) {
        if (!r.resolved()) {
            // loud, in place, and the rest of the report still renders (D-I3, acceptance 3)
            callout(doc, c, "DID NOT RESOLVE", r.reason(), WARN, WARN_BG);
            return;
        }
        switch (s.kind()) {
            case NARRATIVE -> callout(doc, c, NARRATIVE_LABEL, s.text(), NARRATIVE, NARRATIVE_BG);
            case FINDING -> {
                Finding f = r.finding();
                // BYTE-IDENTICAL to what flag wrote (D-I1): the strings pass through untouched
                if (f.hasNote()) {
                    callout(doc, c, "What is wrong — record #" + f.recordIndex(), f.note(),
                            PROBLEM, PROBLEM_BG);
                }
                if (f.hasFix()) {
                    callout(doc, c, "Likely cause / suggested fix", f.fix(), FIX, FIX_BG);
                }
                if (body.monoLines() != null) {
                    mono(doc, c, "Record #" + f.recordIndex(), body.monoLines());
                }
            }
            case RECORD, SERIES -> {
                if (body.picture() != null) picture(doc, c, body.picture());
                if (body.monoLines() != null) {
                    mono(doc, c, body.heading() != null ? body.heading()
                            : s.kind() == ReportSpec.Kind.RECORD
                                    ? "Record #" + s.recordIndex() : "Series", body.monoLines());
                }
            }
            case CHART, TOPOLOGY -> {
                if (body.picture() != null) picture(doc, c, body.picture());
            }
            case TABLE -> {
                if (body.table() != null) {
                    table(doc, c, body.heading() == null ? "Table" : body.heading(), body.table());
                }
            }
        }
        if (r.warning() != null) {
            callout(doc, c, "WARNING", r.warning(), WARN, WARN_BG);
        }
    }

    // ---- the table (D-I7/D-I8) -----------------------------------------------------------------------

    private static final float ROW_H = 13.5f;
    private static final float CELL_SIZE = 8.2f;
    private static final float HEADER_H = 16;

    private static void table(PdfDoc doc, Cursor c, String heading, TableData t) {
        if (t.columns().isEmpty()) return;
        float[] widths = columnWidths(t);
        c.ensure(doc, HEADER_H + ROW_H * Math.min(3, t.rows().size()) + 30);
        sectionHeading(doc, c, heading);

        int row = 0;
        while (row < t.rows().size() || row == 0) {
            tableHeader(doc, c, t, widths);             // repeated on every page the table touches
            while (row < t.rows().size()) {
                if (c.y + ROW_H > BOTTOM) break;
                drawRow(doc, c, t, widths, row);
                row++;
            }
            if (row < t.rows().size()) {
                c.page(doc);
            } else {
                break;
            }
        }
        if (t.rows().isEmpty()) {
            doc.text("(no rows)", MARGIN + 8, c.y + 10, PdfDoc.Face.HELVETICA, 9f, MUTED);
            c.y += 20;
        }
        // D-I8: every emphasis that carries meaning carries its reason ON THE PAGE. A highlighted
        // row without its rule is a judgement wearing evidence styling.
        if (t.rowWhen() != null) {
            c.ensure(doc, 16);
            String label = t.rowWhenLabel() == null ? "highlighted" : t.rowWhenLabel();
            doc.fillRect(MARGIN, c.y + 2, 8, 8, HIGHLIGHT_BG);
            doc.strokeRect(MARGIN, c.y + 2, 8, 8, PROBLEM, 0.6f);
            doc.text(PdfDoc.clip(label + " — rows where " + t.rowWhen(),
                            PdfDoc.Face.HELVETICA, 8f, CONTENT_W - 16),
                    MARGIN + 13, c.y + 9, PdfDoc.Face.HELVETICA, 8f, MUTED);
            c.y += 18;
        }
        c.y += 8;
    }

    private static void tableHeader(PdfDoc doc, Cursor c, TableData t, float[] widths) {
        doc.fillRect(MARGIN, c.y, CONTENT_W, HEADER_H, PANEL);
        doc.strokeRect(MARGIN, c.y, CONTENT_W, HEADER_H, RULE, 0.6f);
        float x = MARGIN;
        for (int i = 0; i < t.columns().size(); i++) {
            ReportSpec.ColumnSpec col = t.columns().get(i);
            String h = PdfDoc.clip(col.heading(), PdfDoc.Face.HELVETICA_BOLD, 7.8f, widths[i] - 10);
            float tx = rightAligned(t, i)
                    ? x + widths[i] - 5 - PdfDoc.Face.HELVETICA_BOLD.width(h, 7.8f) : x + 5;
            doc.text(h, tx, c.y + 11.5f, PdfDoc.Face.HELVETICA_BOLD, 7.8f, MUTED);
            x += widths[i];
        }
        c.y += HEADER_H;
    }

    private static void drawRow(PdfDoc doc, Cursor c, TableData t, float[] widths, int row) {
        boolean hot = t.highlighted() != null && row < t.highlighted().length && t.highlighted()[row];
        if (hot) {
            doc.fillRect(MARGIN, c.y, CONTENT_W, ROW_H, HIGHLIGHT_BG);
        }
        doc.fillRect(MARGIN, c.y + ROW_H - 0.5f, CONTENT_W, 0.5f, RULE);
        List<String> cells = t.rows().get(row);
        float x = MARGIN;
        for (int i = 0; i < t.columns().size(); i++) {
            ReportSpec.ColumnSpec col = t.columns().get(i);
            String raw = i < cells.size() ? cells.get(i) : "";
            String v = formatCell(raw, col.format());
            boolean right = rightAligned(t, i);
            // numbers set in the monospace face: Helvetica's digits are not tabular, and a column of
            // prices that does not line up is a column nobody reads (D-I8)
            PdfDoc.Face face = right
                    ? ("bold".equals(col.emphasis()) ? PdfDoc.Face.COURIER_BOLD : PdfDoc.Face.COURIER)
                    : ("bold".equals(col.emphasis()) ? PdfDoc.Face.HELVETICA_BOLD : PdfDoc.Face.HELVETICA);
            Color ink = "muted".equals(col.emphasis()) ? MUTED : INK;
            String clipped = PdfDoc.clip(v, face, CELL_SIZE, widths[i] - 10);
            float tx = right ? x + widths[i] - 5 - face.width(clipped, CELL_SIZE) : x + 5;
            doc.text(clipped, tx, c.y + 9.8f, face, CELL_SIZE, ink);
            x += widths[i];
        }
        c.y += ROW_H;
    }

    private static boolean rightAligned(TableData t, int i) {
        ReportSpec.ColumnSpec col = t.columns().get(i);
        if ("left".equals(col.align())) return false;
        if ("right".equals(col.align())) return true;
        if (!col.format().isEmpty()) return true;          // a formatted column is a numeric column
        for (List<String> row : t.rows()) {                // undeclared: numbers right, text left
            if (i < row.size() && !row.get(i).isBlank()) {
                return isNumeric(row.get(i));
            }
        }
        return false;
    }

    /** Declared widths honoured; the rest share by content, everything scaled to the page. */
    private static float[] columnWidths(TableData t) {
        int n = t.columns().size();
        float[] w = new float[n];
        for (int i = 0; i < n; i++) {
            ReportSpec.ColumnSpec col = t.columns().get(i);
            if (col.width() > 0) {
                w[i] = col.width();
                continue;
            }
            float max = PdfDoc.Face.HELVETICA_BOLD.width(col.heading(), 7.8f);
            for (List<String> row : t.rows()) {
                if (i < row.size()) {
                    String v = formatCell(row.get(i), col.format());
                    max = Math.max(max, PdfDoc.Face.COURIER.width(v, CELL_SIZE));
                }
            }
            w[i] = Math.min(max + 12, CONTENT_W / 2);
        }
        float total = 0;
        for (float x : w) total += x;
        for (int i = 0; i < n; i++) w[i] = w[i] / total * CONTENT_W;
        return w;
    }

    static String formatCell(String raw, String format) {
        if (raw == null) return "";
        if (format.isEmpty() || !isNumeric(raw)) return raw;
        double v = Double.parseDouble(raw.trim());
        return switch (format) {
            case "percent" -> String.format(Locale.ROOT, "%.1f%%", v * 100);
            case "duration" -> formatDuration((long) v);
            case "time" -> java.time.Instant.ofEpochMilli((long) v).atZone(java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            default -> {
                int dot = format.indexOf('.');
                int decimals = dot < 0 ? 0 : format.length() - dot - 1;
                yield String.format(Locale.ROOT, "%,." + decimals + "f", v);
            }
        };
    }

    private static String formatDuration(long millis) {
        if (millis < 1000) return millis + "ms";
        if (millis < 60_000) return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
        return String.format(Locale.ROOT, "%dm %02ds", millis / 60_000, (millis % 60_000) / 1000);
    }

    private static boolean isNumeric(String s) {
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- shared furniture (FindingReport's language) ---------------------------------------------

    private static void header(PdfDoc doc, Cursor c, ReportSpec spec) {
        float bandH = 66;
        doc.fillRect(0, 0, PdfDoc.PAGE_W, bandH, INK);
        doc.text("INVESTIGATION", MARGIN, 27, PdfDoc.Face.HELVETICA_BOLD, 8.5f, new Color(0x93A3BF));
        doc.text(PdfDoc.clip(spec.title(), PdfDoc.Face.HELVETICA_BOLD, 16, CONTENT_W),
                MARGIN, 50, PdfDoc.Face.HELVETICA_BOLD, 16, Color.WHITE);
        c.y = bandH + 22;
    }

    private static void metaStrip(PdfDoc doc, Cursor c, ReportSpec spec, String logFile,
                                  String analysedAt) {
        List<String[]> cells = new ArrayList<>();
        if (spec.fingerprint() != null) {
            cells.add(new String[]{"WRITTEN AGAINST", spec.fingerprint().describe()});
        }
        cells.add(new String[]{"AUTHORED VIEW", spec.filter().describe()});
        if (logFile != null) cells.add(new String[]{"LOG", logFile});
        if (!spec.createdAt().isBlank()) cells.add(new String[]{"CREATED", spec.createdAt()});
        if (analysedAt != null) cells.add(new String[]{"RENDERED", analysedAt});

        float rowH = 15;
        float boxH = cells.size() * rowH + 14;
        doc.fillRect(MARGIN, c.y, CONTENT_W, boxH, PANEL);
        doc.strokeRect(MARGIN, c.y, CONTENT_W, boxH, RULE, 0.6f);
        float labelW = 108;
        float ty = c.y + 17;
        for (String[] cell : cells) {
            doc.text(cell[0], MARGIN + 10, ty, PdfDoc.Face.HELVETICA_BOLD, 7.5f, MUTED);
            doc.text(PdfDoc.clip(cell[1], PdfDoc.Face.COURIER, 8.5f, CONTENT_W - labelW - 20),
                    MARGIN + labelW, ty, PdfDoc.Face.COURIER, 8.5f, INK);
            ty += rowH;
        }
        c.y += boxH + 20;
    }

    private static void callout(PdfDoc doc, Cursor c, String heading, String body,
                                Color accent, Color background) {
        float pad = 11;
        float textW = CONTENT_W - pad * 2 - 6;
        List<String> lines = PdfDoc.wrap(body, PdfDoc.Face.HELVETICA, 10.5f, textW);
        float boxH = pad * 2 + 15 + lines.size() * 14;
        c.ensure(doc, boxH);

        doc.fillRect(MARGIN, c.y, CONTENT_W, boxH, background);
        doc.fillRect(MARGIN, c.y, 4, boxH, accent);
        doc.text(heading.toUpperCase(Locale.ROOT), MARGIN + pad + 4, c.y + pad + 8,
                PdfDoc.Face.HELVETICA_BOLD, 8.5f, accent);
        float ty = c.y + pad + 26;
        for (String line : lines) {
            doc.text(line, MARGIN + pad + 4, ty, PdfDoc.Face.HELVETICA, 10.5f, INK);
            ty += 14;
        }
        c.y += boxH + 18;
    }

    private static void picture(PdfDoc doc, Cursor c, FindingReport.Picture p) {
        java.awt.image.BufferedImage img = p.image();
        if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) return;
        if (BOTTOM - c.y - 50 < 200) c.page(doc);
        sectionHeading(doc, c, p.heading());
        if (p.caption() != null) {
            for (String line : PdfDoc.wrap(p.caption(), PdfDoc.Face.HELVETICA, 8.5f, CONTENT_W)) {
                doc.text(line, MARGIN, c.y + 8, PdfDoc.Face.HELVETICA, 8.5f, MUTED);
                c.y += 11;
            }
            c.y += 4;
        }
        float available = BOTTOM - c.y - 20;
        float aspect = img.getHeight() / (float) img.getWidth();
        float w = CONTENT_W;
        float h = w * aspect;
        if (h > available) {
            h = available;
            w = h / aspect;
        }
        float x = MARGIN + (CONTENT_W - w) / 2;
        String name = doc.addImage(img);
        doc.drawImage(name, x, c.y, w, h);
        doc.strokeRect(x, c.y, w, h, RULE, 0.6f);
        c.y += h + 20;
    }

    private static void mono(PdfDoc doc, Cursor c, String heading, List<String> lines) {
        List<String> flowed = new ArrayList<>();
        for (String line : lines) {
            flowed.addAll(PdfDoc.wrap(line, PdfDoc.Face.COURIER, 8.2f, CONTENT_W - 20));
        }
        c.ensure(doc, 30 + Math.min(5, Math.max(1, flowed.size())) * 11f + 26);
        sectionHeading(doc, c, heading);
        if (flowed.isEmpty()) {
            doc.text("(nothing recorded)", MARGIN + 10, c.y + 12, PdfDoc.Face.HELVETICA, 9.5f, MUTED);
            c.y += 26;
            return;
        }
        int i = 0;
        while (i < flowed.size()) {
            float available = BOTTOM - c.y - 12;
            int fits = Math.max(1, (int) ((available - 12) / 11f));
            int take = Math.min(fits, flowed.size() - i);
            float boxH = take * 11f + 12;
            doc.fillRect(MARGIN, c.y, CONTENT_W, boxH, PANEL);
            doc.strokeRect(MARGIN, c.y, CONTENT_W, boxH, RULE, 0.6f);
            float ty = c.y + 14;
            for (int k = 0; k < take; k++) {
                doc.text(flowed.get(i + k), MARGIN + 10, ty, PdfDoc.Face.COURIER, 8.2f, MUTED);
                ty += 11f;
            }
            c.y += boxH + 14;
            i += take;
            if (i < flowed.size()) c.page(doc);
        }
    }

    private static void sectionHeading(PdfDoc doc, Cursor c, String heading) {
        doc.text(heading, MARGIN, c.y + 10, PdfDoc.Face.HELVETICA_BOLD, 11.5f, INK);
        c.y += 22;
        doc.fillRect(MARGIN, c.y, CONTENT_W, 0.8f, RULE);
        c.y += 8;
    }

    private static void footers(PdfDoc doc, ReportSpec spec, String logFile) {
        int total = doc.pageCount();
        String label = spec.name() + (logFile == null ? "" : " · " + logFile);
        label = PdfDoc.clip(label, PdfDoc.Face.HELVETICA, 7.5f, CONTENT_W - 70);
        for (int i = 0; i < total; i++) {
            doc.selectPage(i);
            float y = PdfDoc.PAGE_H - 24;
            doc.fillRect(MARGIN, y - 12, CONTENT_W, 0.6f, RULE);
            doc.text(label, MARGIN, y, PdfDoc.Face.HELVETICA, 7.5f, MUTED);
            String page = (i + 1) + " / " + total;
            doc.text(page, MARGIN + CONTENT_W - PdfDoc.Face.HELVETICA.width(page, 7.5f), y,
                    PdfDoc.Face.HELVETICA, 7.5f, MUTED);
        }
    }

    private static final class Cursor {
        private float y = MARGIN;

        void ensure(PdfDoc doc, float needed) {
            if (y + needed > BOTTOM) page(doc);
        }

        void page(PdfDoc doc) {
            doc.newPage();
            y = MARGIN;
        }
    }
}
