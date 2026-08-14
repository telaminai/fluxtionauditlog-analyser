package telamin.fluxtion.audit.analyser.analyser.diff;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A tiny, dependency-free PDF writer for monospaced text (R13.12). Emits a valid PDF 1.4 using the
 * standard-14 Courier font (no embedding), paginating a list of lines onto US-Letter pages. Enough for
 * exporting a diff as a printable document without pulling in a PDF library (near-zero-dep ethos).
 */
public final class TextPdf {
    private TextPdf() { }

    private static final int PAGE_W = 612;   // US Letter, points
    private static final int PAGE_H = 792;
    private static final int MARGIN = 40;
    private static final float FONT = 9f;
    private static final float LEADING = 11f;
    private static final int LINES_PER_PAGE = (int) ((PAGE_H - 2 * MARGIN) / LEADING);

    public static byte[] render(String title, List<String> lines) {
        List<List<String>> pages = paginate(title, lines);

        // object numbers: 1=catalog, 2=pages, 3=font, then per page: pageObj, contentObj
        int pageCount = pages.size();
        List<byte[]> objects = new ArrayList<>();
        // 1: catalog
        objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
        // 2: pages (kids filled below once we know numbers)
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) kids.append(4 + 2 * i).append(" 0 R ");
        objects.add(bytes("<< /Type /Pages /Kids [ " + kids.toString().trim() + " ] /Count " + pageCount + " >>"));
        // 3: font
        objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>"));
        // per page: page + content
        for (int i = 0; i < pageCount; i++) {
            int contentObj = 5 + 2 * i;
            objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_W + " " + PAGE_H + "] "
                    + "/Resources << /Font << /F1 3 0 R >> >> /Contents " + contentObj + " 0 R >>"));
            byte[] stream = contentStream(pages.get(i)).getBytes(StandardCharsets.ISO_8859_1);
            byte[] head = bytes("<< /Length " + stream.length + " >>\nstream\n");
            byte[] tail = "\nendstream".getBytes(StandardCharsets.ISO_8859_1);
            objects.add(concat(head, stream, tail));
        }

        // assemble with an xref table
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n");
            out.writeBytes(objects.get(i));
            write(out, "\nendobj\n");
        }
        int xrefOffset = out.size();
        int n = objects.size() + 1;   // +1 for the free object 0
        write(out, "xref\n0 " + n + "\n");
        write(out, "0000000000 65535 f \n");
        for (int off : offsets) write(out, String.format("%010d 00000 n \n", off));
        write(out, "trailer\n<< /Size " + n + " /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF");
        return out.toByteArray();
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

    private static String contentStream(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("BT\n/F1 ").append(fmt(FONT)).append(" Tf\n").append(fmt(LEADING)).append(" TL\n");
        sb.append(MARGIN).append(' ').append(PAGE_H - MARGIN).append(" Td\n");
        for (String line : lines) {
            sb.append('(').append(escape(line)).append(") Tj\nT*\n");
        }
        sb.append("ET");
        return sb.toString();
    }

    /** Escape for a PDF literal string and coerce to a printable single-byte encoding. */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '(' -> sb.append("\\(");
                case ')' -> sb.append("\\)");
                case '\\' -> sb.append("\\\\");
                default -> sb.append(c > 0xFF ? '?' : c);   // Courier is single-byte; drop non-Latin-1
            }
        }
        return sb.toString();
    }

    private static String fmt(float f) {
        return f == Math.rint(f) ? Integer.toString((int) f) : Float.toString(f);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (byte[] p : parts) b.writeBytes(p);
        return b.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
