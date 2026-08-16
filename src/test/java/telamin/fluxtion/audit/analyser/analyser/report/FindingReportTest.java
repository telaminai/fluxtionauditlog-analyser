package telamin.fluxtion.audit.analyser.analyser.report;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The finding value, the PDF writer and the report layout (M23.7–M23.8). */
class FindingReportTest {

    private static String body(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    // ---- Finding ---------------------------------------------------------------------------------

    @Test
    void aFindingNormalisesEmptyText() {
        Finding f = new Finding(3, null, "   ");
        assertEquals("", f.note());
        assertNull(f.fix());
        assertTrue(f.isEmpty());
        assertFalse(f.hasNote());
        assertFalse(f.hasFix());
    }

    /**
     * The whole reason merge exists: adding a fix must not silently delete the explanation it is a fix
     * for. Two calls that each set one field have to compose.
     */
    @Test
    void mergeKeepsWhatTheCallerDidNotSupply() {
        Finding first = new Finding(7, "revenue posts before the shelf is checked", null);
        Finding withFix = first.merge(null, "move the stock check upstream of BasketAccumulator");

        assertEquals("revenue posts before the shelf is checked", withFix.note());
        assertEquals("move the stock check upstream of BasketAccumulator", withFix.fix());

        Finding reworded = withFix.merge("oversell: fulfilled exceeds stock on hand", null);
        assertEquals("oversell: fulfilled exceeds stock on hand", reworded.note());
        assertEquals("move the stock check upstream of BasketAccumulator", reworded.fix(),
                "rewording the note must not drop the fix");
        assertEquals(7, reworded.recordIndex());
    }

    // ---- PdfDoc ----------------------------------------------------------------------------------

    @Test
    void pdfDocEmitsAWellFormedDocument() {
        PdfDoc doc = new PdfDoc();
        doc.text("hello", 40, 60, PdfDoc.Face.HELVETICA_BOLD, 12, Color.BLACK);
        doc.fillRect(10, 10, 100, 4, new Color(0x1F2A44));
        byte[] pdf = doc.toBytes();

        assertEquals("%PDF-1.4", new String(pdf, 0, 8, StandardCharsets.ISO_8859_1));
        assertTrue(body(pdf).endsWith("%%EOF"));
        assertTrue(body(pdf).contains("/Type /Catalog"));
        assertTrue(body(pdf).contains("(hello) Tj"));
        assertTrue(body(pdf).contains("trailer"));
    }

    /**
     * Callers lay out in top-left coordinates; PDF's origin is bottom-left. A baseline 60 points from the
     * top must be written as 792-60. Getting this backwards puts every report upside down, and it is the
     * kind of thing that looks fine until the page is not full.
     */
    @Test
    void topLeftCoordinatesAreFlippedIntoPdfSpace() {
        PdfDoc doc = new PdfDoc();
        doc.text("x", 40, 60, PdfDoc.Face.COURIER, 9, Color.BLACK);
        assertTrue(body(doc.toBytes()).contains("40 732 Td"),
                "y=60 from the top should serialise as 792-60");
    }

    @Test
    void parenthesesAndBackslashesAreEscaped() {
        PdfDoc doc = new PdfDoc();
        doc.text("MutableOrder(price=1) \\ end", 10, 10, PdfDoc.Face.COURIER, 9, Color.BLACK);
        String out = body(doc.toBytes());
        assertTrue(out.contains("MutableOrder\\(price=1\\) \\\\ end"),
                "an unescaped ')' truncates the string and corrupts the page");
    }

    /**
     * The standard-14 fonts are single-byte, and this codebase writes em dashes and curly quotes
     * everywhere. Replacing them with '?' — the obvious fallback — makes a report read like a corrupted
     * file rather than a typographic limitation, which is exactly how it shipped in the first draft.
     */
    @Test
    void typographicPunctuationIsTransliteratedNotReplacedWithQuestionMarks() {
        PdfDoc doc = new PdfDoc();
        doc.text("evidence — the “claim” it rests on… 100 → 200", 10, 10,
                PdfDoc.Face.HELVETICA, 9, Color.BLACK);
        String out = body(doc.toBytes());

        assertTrue(out.contains("evidence - the \"claim\" it rests on... 100 -> 200"), out);
        assertFalse(out.contains("?"), "no character should degrade to a question mark here");
    }

    /** Without WinAnsi the viewer reads bytes 0x80-0xFF from StandardEncoding and renders the wrong glyph. */
    @Test
    void fontsDeclareWinAnsiEncoding() {
        String out = body(new PdfDoc().toBytes());
        assertTrue(out.contains("/BaseFont /Helvetica /Encoding /WinAnsiEncoding"));
        assertTrue(out.contains("/BaseFont /Courier /Encoding /WinAnsiEncoding"));
    }

    @Test
    void pagesAreCountedAndSelectable() {
        PdfDoc doc = new PdfDoc();
        doc.newPage();
        doc.newPage();
        assertEquals(3, doc.pageCount());
        doc.selectPage(0);
        doc.text("first", 10, 10, PdfDoc.Face.COURIER, 9, Color.BLACK);
        String out = body(doc.toBytes());
        assertTrue(out.contains("/Count 3"));
        assertTrue(out.contains("(first) Tj"));
    }

    @Test
    void anImageIsEmbeddedAsAFlateRgbXObject() {
        PdfDoc doc = new PdfDoc();
        BufferedImage img = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        String name = doc.addImage(img);
        doc.drawImage(name, 0, 0, 200, 150);
        String out = body(doc.toBytes());

        assertTrue(out.contains("/Subtype /Image"));
        assertTrue(out.contains("/Width 4 /Height 3"));
        assertTrue(out.contains("/ColorSpace /DeviceRGB"));
        assertTrue(out.contains("/Filter /FlateDecode"));
        assertTrue(out.contains("/XObject << /Im1"));
        assertTrue(out.contains("/Im1 Do"));
    }

    @Test
    void wrapBreaksOnWordsAndHonoursNewlines() {
        List<String> lines = PdfDoc.wrap("alpha beta\ngamma", PdfDoc.Face.COURIER, 10, 60);
        // 60pt at 10pt Courier ≈ 10 characters
        assertEquals(List.of("alpha beta", "gamma"), lines);
    }

    @Test
    void wrapHardSplitsAWordWiderThanTheBox() {
        List<String> lines = PdfDoc.wrap("supercalifragilistic", PdfDoc.Face.COURIER, 10, 30);
        assertTrue(lines.size() > 1, "a word wider than the box must not run off the page");
        for (String line : lines) {
            assertTrue(PdfDoc.Face.COURIER.width(line, 10) <= 30.001f, line);
        }
    }

    // ---- FindingReport ---------------------------------------------------------------------------

    private static FindingReport.Evidence evidence(Finding finding, BufferedImage topology,
                                                   BufferedImage chart, String graphName) {
        List<FindingReport.Picture> pictures = new ArrayList<>();
        if (topology != null) {
            pictures.add(new FindingReport.Picture("The cycle", "Only the nodes this event reached.",
                    topology));
        }
        if (chart != null) {
            pictures.add(new FindingReport.Picture("Trend \u00b7 " + graphName, null, chart));
        }
        return new FindingReport.Evidence(
                "Oversell at record 99", finding, "demo-store.yaml", "com.acme.demo.StoreProcessor",
                "2026-08-16T09:15:00Z", "SaleEvent",
                List.of("eventTime: 1755334500000", "event: SaleEvent"),
                List.of("  1. stockLedger  onHand=-4  fulfilled=12"),
                pictures, "2026-08-16T18:40:00Z");
    }

    @Test
    void aReportCarriesTheExplanationAndTheFix() {
        Finding f = new Finding(99, "revenue is posted before the shelf is checked",
                "move the stock check upstream of BasketAccumulator");
        String out = body(FindingReport.render(evidence(f, null, null, null)));

        assertTrue(out.contains("Oversell at record 99"));
        assertTrue(out.contains("WHAT IS WRONG"));
        assertTrue(out.contains("LIKELY CAUSE / SUGGESTED FIX"));
        assertTrue(out.contains("revenue is posted before the shelf is checked"));
        assertTrue(out.contains("stockLedger"), "the node log is the evidence the explanation rests on");
        assertTrue(out.contains("SaleEvent"));
    }

    /**
     * A report with nothing written on it must still be a valid document that says so, rather than a
     * page with a silent gap a reader has to interpret.
     */
    @Test
    void anUnexplainedRecordStillProducesAReadableReport() {
        String out = body(FindingReport.render(evidence(new Finding(99, "", null), null, null, null)));
        assertTrue(out.contains("No explanation was recorded"));
        assertTrue(out.endsWith("%%EOF"));
    }

    @Test
    void picturesAreIncludedWhenSupplied() {
        BufferedImage topology = new BufferedImage(400, 260, BufferedImage.TYPE_INT_RGB);
        BufferedImage chart = new BufferedImage(500, 200, BufferedImage.TYPE_INT_RGB);
        String out = body(FindingReport.render(
                evidence(new Finding(99, "trend diverges", null), topology, chart, "Stock vs revenue")));

        assertTrue(out.contains("/Im1 Do"));
        assertTrue(out.contains("/Im2 Do"));
        assertTrue(out.contains("The cycle"));
        assertTrue(out.contains("Stock vs revenue"));
    }

    /**
     * The two graph views are the point of including a picture at all, and each needs its caption: the
     * same image of three lit nodes means "this is all that ran" or "this is a filtered slice" depending
     * entirely on which view you are looking at, and those support opposite conclusions.
     */
    @Test
    void bothGraphViewsAreCaptionedDistinctly() {
        FindingReport.Evidence e = new FindingReport.Evidence(
                "Two views", new Finding(3, "the check never fired", null), "log.yaml", null, null, null,
                List.of("event: Sale"), List.of("  1. till  total=12"),
                List.of(new FindingReport.Picture("The cycle",
                                "Only the nodes this event reached.",
                                new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB)),
                        new FindingReport.Picture("Where it sits in the processor",
                                "What stayed grey is what this event did not reach.",
                                new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB))), null);
        String out = body(FindingReport.render(e));

        assertTrue(out.contains("The cycle"));
        assertTrue(out.contains("Where it sits in the processor"));
        assertTrue(out.contains("Only the nodes this event reached."));
        assertTrue(out.contains("did not reach."));
        assertTrue(out.contains("/Im1 Do") && out.contains("/Im2 Do"), "both views must be embedded");
    }

    /** A view that could not be rendered is dropped, not drawn as an empty box with a confident caption. */
    @Test
    void picturesWithNoImageAreDropped() {
        FindingReport.Evidence e = new FindingReport.Evidence(
                "Missing view", new Finding(3, "x", null), "log.yaml", null, null, null,
                List.of("event: Sale"), List.of("  1. till  total=12"),
                java.util.Arrays.asList(
                        new FindingReport.Picture("The cycle", "caption", null),
                        null,
                        new FindingReport.Picture("Trend", null,
                                new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB))), null);
        assertEquals(1, e.pictures().size());
        String out = body(FindingReport.render(e));
        assertFalse(out.contains("(The cycle) Tj"), "a heading with no picture under it is a lie");
    }

    /** A long node log paginates rather than being cut off — that is where the interesting line lives. */
    @Test
    void aLongNodeLogPaginates() {
        List<String> nodeLog = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            nodeLog.add(String.format("%3d. node%02d  value=%d", i, i % 20, i));
        }
        FindingReport.Evidence e = new FindingReport.Evidence(
                "Long cycle", new Finding(1, "lots happened", null), "log.yaml", null, null, null,
                List.of("event: Tick"), nodeLog, List.of(), null);
        String out = body(FindingReport.render(e));

        assertTrue(out.contains("399. node19"), "the last line must survive pagination");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/Count (\\d+)").matcher(out);
        assertTrue(m.find());
        assertTrue(Integer.parseInt(m.group(1)) >= 4,
                "400 log lines should span several pages, got " + m.group(1));
    }

    /**
     * A short node log must not be split across a page boundary. The first draft put the "Node log"
     * heading at the foot of page 2 with room for one of its three lines and widowed the other two onto
     * a page that was otherwise blank — visible immediately in the output, invisible to every test.
     */
    @Test
    void aShortBlockIsNotWidowedAcrossAPageBreak() {
        // The break depends on exactly where the cursor lands, so sweep the thing that moves it rather
        // than guess one value. Every event-record length must keep the 3-line node log together.
        for (int eventLines = 1; eventLines <= 70; eventLines++) {
            List<String> event = new ArrayList<>();
            for (int i = 0; i < eventLines; i++) event.add("  field" + i + ": value" + i);

            FindingReport.Evidence e = new FindingReport.Evidence(
                    "Widow check", new Finding(5, "something is wrong", null), "log.yaml", null, null,
                    null, event,
                    List.of("  1. alpha  x=1", "  2. beta  y=2", "  3. gamma  z=3"),
                    List.of(), null);
            String out = body(FindingReport.render(e));

            String[] pages = out.split("/Type /Page /Parent");
            int alpha = -1, gamma = -1;
            for (int i = 0; i < pages.length; i++) {
                if (pages[i].contains("alpha")) alpha = i;
                if (pages[i].contains("gamma")) gamma = i;
            }
            assertTrue(alpha > 0 && alpha == gamma,
                    "with " + eventLines + " event lines the 3-line node log split across pages "
                            + alpha + " and " + gamma);
        }
    }

    /** Every page carries the anchor, because printed pages get separated from each other. */
    @Test
    void everyPageIsFootedWithTheRecordAnchor() {
        List<String> nodeLog = new ArrayList<>();
        for (int i = 0; i < 200; i++) nodeLog.add("line " + i);
        FindingReport.Evidence e = new FindingReport.Evidence(
                "Multi page", new Finding(42, "x", null), "log.yaml", null, null, null,
                List.of("event: Tick"), nodeLog, List.of(), null);
        byte[] pdf = FindingReport.render(e);
        String out = body(pdf);

        int anchors = out.split("\\(record #42", -1).length - 1;
        assertTrue(anchors >= 2, "expected a footer per page, found " + anchors);
        assertTrue(out.contains("1 / "), "pages should be numbered");
        assertNotNull(pdf);
    }
}
