package telamin.fluxtion.audit.analyser.analyser.diff;

import telamin.fluxtion.audit.analyser.analyser.report.PdfDoc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports a list of monospaced lines as a printable PDF (R13.12) — enough to send a record diff to
 * someone without pulling in a PDF library.
 *
 * <p>The PDF mechanics live in {@link PdfDoc}, which grew out of this class when the finding report
 * needed colour and images (M23.8). Two writers emitting the same file format is a standing invitation
 * for one of them to acquire a bug the other does not have, so there is one.
 */
public final class TextPdf {
    private TextPdf() { }

    private static final float MARGIN = 40;
    private static final float FONT = 9f;
    private static final float LEADING = 11f;
    private static final int LINES_PER_PAGE = (int) ((PdfDoc.PAGE_H - 2 * MARGIN) / LEADING);
    private static final Color INK = Color.BLACK;

    public static byte[] render(String title, List<String> lines) {
        PdfDoc doc = new PdfDoc();
        List<List<String>> pages = paginate(title, lines);
        for (int p = 0; p < pages.size(); p++) {
            if (p > 0) {
                doc.newPage();
            }
            float y = MARGIN + FONT;
            for (String line : pages.get(p)) {
                doc.text(line, MARGIN, y, PdfDoc.Face.COURIER, FONT, INK);
                y += LEADING;
            }
        }
        return doc.toBytes();
    }

    private static List<List<String>> paginate(String title, List<String> lines) {
        List<String> all = new ArrayList<>();
        if (title != null && !title.isBlank()) {
            all.add(title);
            all.add("");
        }
        all.addAll(lines);
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < all.size(); i += LINES_PER_PAGE) {
            pages.add(all.subList(i, Math.min(all.size(), i + LINES_PER_PAGE)));
        }
        if (pages.isEmpty()) pages.add(List.of(""));
        return pages;
    }
}
