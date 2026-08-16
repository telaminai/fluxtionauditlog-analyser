package telamin.fluxtion.audit.analyser.analyser.report;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays a {@link Finding} and its evidence out as a printable PDF.
 *
 * <p>The point is not prettiness. A diagnosis is only worth anything if the person receiving it can
 * check it, and checking it means seeing the same four things the diagnoser saw: the <b>event</b> that
 * started the cycle, the <b>values every node logged</b> while handling it, the <b>shape of the graph</b>
 * that carried it, and — when the problem is a trend rather than an instant — the <b>plot</b>. Put the
 * explanation next to those and it is an argument. Send the explanation alone and it is an assertion.
 *
 * <p>Deliberately always light-themed: this is a document, not a screen. It will be printed, pasted into
 * a ticket and read on someone else's machine, and a dark page serves none of those.
 *
 * <p>Pure apart from the images handed to it — no Swing, no file IO — so the whole layout is testable
 * headlessly.
 */
public final class FindingReport {

    private FindingReport() {
    }

    // a restrained palette: one ink, one structural accent, and a colour each for the two things a
    // reader is looking for — what went wrong, and what to do about it
    private static final Color INK = new Color(0x1F2A44);
    private static final Color MUTED = new Color(0x57606A);
    private static final Color RULE = new Color(0xD8DEE4);
    private static final Color PANEL = new Color(0xF6F8FA);
    private static final Color PROBLEM = new Color(0xB45309);
    private static final Color PROBLEM_BG = new Color(0xFFF8EC);
    private static final Color FIX = new Color(0x15803D);
    private static final Color FIX_BG = new Color(0xF1FAF3);
    private static final Color WHITE = Color.WHITE;

    private static final float MARGIN = 46;
    private static final float CONTENT_W = PdfDoc.PAGE_W - 2 * MARGIN;
    private static final float BOTTOM = PdfDoc.PAGE_H - 40;

    /**
     * Everything the report draws. Assembled by the caller, which is the only layer that knows how to
     * screenshot a panel or read a record — keeping this class pure.
     *
     * @param title        the headline, e.g. "Oversell at record 99"
     * @param finding      the explanation and optional suggested fix
     * @param logFile      which audit log, for provenance
     * @param processor    the event processor the graph belongs to, or {@code null}
     * @param recordTime   formatted record time, or {@code null}
     * @param eventSummary the one-line event description shown in the header strip
     * @param eventLines   the raw event record
     * @param nodeLogLines the node log for the cycle
     * @param pictures     captioned images — the cycle, the whole graph, a plot — in the order to show
     * @param analysedAt   when this report was produced, or {@code null}
     */
    public record Evidence(String title,
                           Finding finding,
                           String logFile,
                           String processor,
                           String recordTime,
                           String eventSummary,
                           List<String> eventLines,
                           List<String> nodeLogLines,
                           List<Picture> pictures,
                           String analysedAt) {

        public Evidence {
            eventLines = eventLines == null ? List.of() : List.copyOf(eventLines);
            nodeLogLines = nodeLogLines == null ? List.of() : List.copyOf(nodeLogLines);
            List<Picture> pics = pictures == null ? List.of() : new ArrayList<>(pictures);
            pics.removeIf(p -> p == null || p.image() == null);
            pictures = List.copyOf(pics);
        }
    }

    /**
     * An image with the sentence that says what it is.
     *
     * <p>The caption is not decoration. A picture of a graph with three nodes lit and the rest grey means
     * nothing until you know whether you are looking at the whole processor or a filtered slice of it —
     * and those two readings support opposite conclusions about what did <em>not</em> run.
     *
     * @param heading what this picture is
     * @param caption one line on how to read it, or {@code null}
     * @param image   the picture
     */
    public record Picture(String heading, String caption, BufferedImage image) { }

    public static byte[] render(Evidence e) {
        PdfDoc doc = new PdfDoc();
        Cursor c = new Cursor(doc);

        header(doc, c, e);
        metaStrip(doc, c, e);

        if (e.finding().hasNote()) {
            calloutBlock(doc, c, "What is wrong", e.finding().note(), PROBLEM, PROBLEM_BG);
        }
        if (e.finding().hasFix()) {
            calloutBlock(doc, c, "Likely cause / suggested fix", e.finding().fix(), FIX, FIX_BG);
        }
        // no finding text at all: say so rather than leaving a gap the reader has to interpret
        if (e.finding().isEmpty()) {
            calloutBlock(doc, c, "What is wrong",
                    "No explanation was recorded for this record.", MUTED, PANEL);
        }

        for (Picture picture : e.pictures()) {
            image(doc, c, picture);
        }

        monoBlock(doc, c, "Event record", e.eventLines(),
                "The event that started this cycle, exactly as the log recorded it.");
        monoBlock(doc, c, "Node log", e.nodeLogLines(),
                "Every value each node published while handling the event — the evidence the "
                        + "explanation above rests on.");

        footers(doc, e);
        return doc.toBytes();
    }

    // ---- sections --------------------------------------------------------------------------------

    private static void header(PdfDoc doc, Cursor c, Evidence e) {
        float bandH = 66;
        doc.fillRect(0, 0, PdfDoc.PAGE_W, bandH, INK);
        doc.text("FINDING", MARGIN, 27, PdfDoc.Face.HELVETICA_BOLD, 8.5f, new Color(0x93A3BF));
        doc.text(PdfDoc.clip(e.title(), PdfDoc.Face.HELVETICA_BOLD, 16, CONTENT_W),
                MARGIN, 50, PdfDoc.Face.HELVETICA_BOLD, 16, WHITE);
        c.y = bandH + 22;
    }

    /** Provenance: which log, which processor, which record. Without it the report proves nothing. */
    private static void metaStrip(PdfDoc doc, Cursor c, Evidence e) {
        List<String[]> cells = new ArrayList<>();
        cells.add(new String[]{"RECORD", "#" + e.finding().recordIndex()});
        if (e.recordTime() != null) cells.add(new String[]{"TIME (UTC)", e.recordTime()});
        if (e.eventSummary() != null) cells.add(new String[]{"EVENT", e.eventSummary()});
        if (e.logFile() != null) cells.add(new String[]{"LOG", e.logFile()});
        if (e.processor() != null) cells.add(new String[]{"PROCESSOR", e.processor()});
        // the two times are different questions and an archived log makes them months apart: when the
        // event happened, and when somebody looked at it
        if (e.analysedAt() != null) cells.add(new String[]{"ANALYSED", e.analysedAt()});

        float rowH = 15;
        float boxH = cells.size() * rowH + 14;
        doc.fillRect(MARGIN, c.y, CONTENT_W, boxH, PANEL);
        doc.strokeRect(MARGIN, c.y, CONTENT_W, boxH, RULE, 0.6f);
        float labelW = 78;
        float ty = c.y + 17;
        for (String[] cell : cells) {
            doc.text(cell[0], MARGIN + 10, ty, PdfDoc.Face.HELVETICA_BOLD, 7.5f, MUTED);
            doc.text(PdfDoc.clip(cell[1], PdfDoc.Face.COURIER, 8.5f, CONTENT_W - labelW - 20),
                    MARGIN + labelW, ty, PdfDoc.Face.COURIER, 8.5f, INK);
            ty += rowH;
        }
        c.y += boxH + 20;
    }

    /**
     * A coloured callout: the human sentence, with a bar down its left edge so it reads as commentary
     * rather than as more log output.
     */
    private static void calloutBlock(PdfDoc doc, Cursor c, String heading, String body,
                                     Color accent, Color background) {
        float pad = 11;
        float textW = CONTENT_W - pad * 2 - 6;
        List<String> lines = PdfDoc.wrap(body, PdfDoc.Face.HELVETICA, 10.5f, textW);
        float boxH = pad * 2 + 15 + lines.size() * 14;
        c.ensure(doc, boxH);

        doc.fillRect(MARGIN, c.y, CONTENT_W, boxH, background);
        doc.fillRect(MARGIN, c.y, 4, boxH, accent);
        doc.text(heading.toUpperCase(java.util.Locale.ROOT), MARGIN + pad + 4, c.y + pad + 8,
                PdfDoc.Face.HELVETICA_BOLD, 8.5f, accent);
        float ty = c.y + pad + 26;
        for (String line : lines) {
            doc.text(line, MARGIN + pad + 4, ty, PdfDoc.Face.HELVETICA, 10.5f, INK);
            ty += 14;
        }
        c.y += boxH + 18;
    }

    /**
     * A picture, fitted to the space actually left on the page.
     *
     * <p>Shrunk to fit rather than pushed wholesale to the next page whenever there is a usable amount of
     * room, because the alternative is a report whose first page is a third full and whose second page is
     * one screenshot. Below {@link #MIN_IMAGE_H} the picture would be too small to read anything in, and
     * an unreadable screenshot is worse than a page break — so that is where it breaks instead.
     */
    private static final float MIN_IMAGE_H = 260;

    private static void image(PdfDoc doc, Cursor c, Picture picture) {
        BufferedImage img = picture.image();
        if (img.getWidth() <= 0 || img.getHeight() <= 0) {
            return;
        }
        float headingH = 30 + (picture.caption() == null ? 0 : 11 * PdfDoc.wrap(
                picture.caption(), PdfDoc.Face.HELVETICA, 8.5f, CONTENT_W).size());
        if (BOTTOM - c.y - headingH - 20 < MIN_IMAGE_H) {
            c.page(doc);
        }
        sectionHeading(doc, c, picture.heading(), picture.caption());

        float available = BOTTOM - c.y - 20;
        float aspect = img.getHeight() / (float) img.getWidth();
        float w = CONTENT_W;
        float h = w * aspect;
        if (h > available) {
            h = available;
            w = h / aspect;
        }
        // centre a picture narrower than the text column: left-aligned it reads as a mis-set block
        float x = MARGIN + (CONTENT_W - w) / 2;
        String name = doc.addImage(img);
        doc.drawImage(name, x, c.y, w, h);
        doc.strokeRect(x, c.y, w, h, RULE, 0.6f);
        c.y += h + 20;
    }

    /**
     * A monospace evidence block. Log lines are paginated rather than truncated: a node log cut off at
     * the page break is exactly where the interesting line tends to be.
     */
    private static final float MONO_LINE_H = 11f;
    private static final float MONO_SIZE = 8.2f;
    /** Never start a block that can only fit a line or two — the rest widows onto an empty page. */
    private static final int MIN_LINES_WITH_HEADING = 5;

    private static void monoBlock(PdfDoc doc, Cursor c, String heading, List<String> lines, String blurb) {
        // pre-flow before deciding where the heading goes: how much room this needs is a property of the
        // wrapped lines, not of the raw ones
        List<String> flowed = new ArrayList<>();
        for (String line : lines) {
            flowed.addAll(PdfDoc.wrap(line, PdfDoc.Face.COURIER, MONO_SIZE, CONTENT_W - 20));
        }
        float headingH = 30 + (blurb == null ? 0 : 11 * PdfDoc.wrap(
                blurb, PdfDoc.Face.HELVETICA, 8.5f, CONTENT_W).size());
        int wanted = Math.max(1, Math.min(flowed.isEmpty() ? 1 : flowed.size(), MIN_LINES_WITH_HEADING));
        c.ensure(doc, headingH + wanted * MONO_LINE_H + 26);

        sectionHeading(doc, c, heading, blurb);
        if (lines.isEmpty()) {
            doc.text("(nothing recorded)", MARGIN + 10, c.y + 12, PdfDoc.Face.HELVETICA, 9.5f, MUTED);
            c.y += 26;
            return;
        }
        float lineH = MONO_LINE_H;
        float size = MONO_SIZE;
        int i = 0;
        while (i < flowed.size()) {
            float available = BOTTOM - c.y - 12;
            int fits = Math.max(1, (int) ((available - 12) / lineH));
            int take = Math.min(fits, flowed.size() - i);
            float boxH = take * lineH + 12;
            doc.fillRect(MARGIN, c.y, CONTENT_W, boxH, PANEL);
            doc.strokeRect(MARGIN, c.y, CONTENT_W, boxH, RULE, 0.6f);
            float ty = c.y + 14;
            for (int k = 0; k < take; k++) {
                doc.text(flowed.get(i + k), MARGIN + 10, ty, PdfDoc.Face.COURIER, size, MUTED);
                ty += lineH;
            }
            c.y += boxH + 14;
            i += take;
            if (i < flowed.size()) {
                c.page(doc);
            }
        }
    }

    private static void sectionHeading(PdfDoc doc, Cursor c, String heading, String blurb) {
        doc.text(heading, MARGIN, c.y + 10, PdfDoc.Face.HELVETICA_BOLD, 11.5f, INK);
        c.y += 16;
        if (blurb != null) {
            for (String line : PdfDoc.wrap(blurb, PdfDoc.Face.HELVETICA, 8.5f, CONTENT_W)) {
                doc.text(line, MARGIN, c.y + 8, PdfDoc.Face.HELVETICA, 8.5f, MUTED);
                c.y += 11;
            }
        }
        c.y += 6;
        doc.fillRect(MARGIN, c.y, CONTENT_W, 0.8f, RULE);
        c.y += 8;
    }

    /**
     * Drawn in a second pass, once the page count is known. Every page carries the record anchor because
     * pages get separated the moment anyone prints one, and a page of node log with nothing saying which
     * cycle it came from is evidence of nothing.
     */
    private static void footers(PdfDoc doc, Evidence e) {
        int total = doc.pageCount();
        String label = "record #" + e.finding().recordIndex()
                + (e.logFile() == null ? "" : " · " + e.logFile());
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

    /** Where the next block goes, and what to do when it does not fit. */
    private static final class Cursor {
        private float y;

        Cursor(PdfDoc doc) {
            this.y = MARGIN;
        }

        void ensure(PdfDoc doc, float needed) {
            if (y + needed > BOTTOM) {
                page(doc);
            }
        }

        void page(PdfDoc doc) {
            doc.newPage();
            y = MARGIN;
        }
    }
}
