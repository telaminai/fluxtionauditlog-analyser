package telamin.fluxtion.audit.analyser.analyser.report;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

/**
 * A small, dependency-free PDF writer: pages, coloured text and rectangles, and embedded images.
 *
 * <p>Grown from the monospace-only writer that exported a record diff (R13.12), because a finding report
 * is not a wall of text — it is a document with headings, colour-coded sections and pictures of the
 * topology and the chart. Everything else in this repo is near-zero-dependency; adding a PDF library to
 * draw a dozen boxes would be the largest dependency in the build for the smallest reason.
 *
 * <p><b>Coordinates are top-left origin, y increasing downwards</b> — the way layout code is actually
 * written — and flipped into PDF's bottom-left space on the way out. Callers never see the flip. The
 * alternative, exposing PDF's own axes, moves that arithmetic into every call site and guarantees at
 * least one of them gets it wrong.
 *
 * <p>Only the standard-14 fonts are used, so nothing is embedded and any reader can open the result.
 */
public final class PdfDoc {

    /** US Letter, in points. */
    public static final float PAGE_W = 612;
    public static final float PAGE_H = 792;

    /** The standard-14 faces this writer offers — one monospace, two proportional. */
    public enum Face {
        /** Monospace. The only honest way to show log lines: columns line up. */
        COURIER("Courier", 0.600f),
        COURIER_BOLD("Courier-Bold", 0.600f),
        HELVETICA("Helvetica", 0.520f),
        HELVETICA_BOLD("Helvetica-Bold", 0.560f);

        final String baseFont;
        final float widthFactor;   // mean glyph width as a fraction of the point size

        Face(String baseFont, float widthFactor) {
            this.baseFont = baseFont;
            this.widthFactor = widthFactor;
        }

        /**
         * Approximate width of {@code text} at {@code size}. Exact for the Courier faces (monospace);
         * an average for the proportional ones, which is all a wrap decision needs — and which keeps this
         * class headless and deterministic instead of reaching for AWT font metrics.
         */
        public float width(String text, float size) {
            return text.length() * widthFactor * size;
        }
    }

    private final List<StringBuilder> pages = new ArrayList<>();
    private final Map<String, Integer> imageIds = new LinkedHashMap<>();   // resource name → index
    private final List<byte[]> imageObjects = new ArrayList<>();
    private StringBuilder current;

    public PdfDoc() {
        newPage();
    }

    /** Start a new page; subsequent drawing lands on it. */
    public void newPage() {
        current = new StringBuilder();
        pages.add(current);
    }

    public int pageCount() {
        return pages.size();
    }

    /**
     * Re-open an already-written page so later drawing lands on it.
     *
     * <p>Exists for footers and "page n of m": both need a total that is only known once the document is
     * finished, so they are drawn in a second pass over pages that already have content.
     */
    public void selectPage(int index) {
        if (index < 0 || index >= pages.size()) {
            throw new IndexOutOfBoundsException("no page " + index + " (of " + pages.size() + ")");
        }
        current = pages.get(index);
    }

    // ---- drawing ---------------------------------------------------------------------------------

    /** A filled rectangle, top-left anchored. */
    public void fillRect(float x, float y, float w, float h, Color c) {
        current.append(rgb(c, true))
                .append(num(x)).append(' ').append(num(flip(y + h))).append(' ')
                .append(num(w)).append(' ').append(num(h)).append(" re f\n");
    }

    /** A stroked rectangle outline, top-left anchored. */
    public void strokeRect(float x, float y, float w, float h, Color c, float lineWidth) {
        current.append(rgb(c, false)).append(num(lineWidth)).append(" w\n")
                .append(num(x)).append(' ').append(num(flip(y + h))).append(' ')
                .append(num(w)).append(' ').append(num(h)).append(" re S\n");
    }

    /**
     * One line of text, with {@code y} the <b>baseline</b> measured from the top of the page.
     */
    public void text(String s, float x, float y, Face face, float size, Color c) {
        if (s == null || s.isEmpty()) {
            return;
        }
        current.append("BT\n").append(rgb(c, true))
                .append('/').append(resourceOf(face)).append(' ').append(num(size)).append(" Tf\n")
                .append(num(x)).append(' ').append(num(flip(y))).append(" Td\n")
                .append('(').append(escape(s)).append(") Tj\nET\n");
    }

    /**
     * Register an image and return the resource name to draw it with. Encoded as raw RGB samples under
     * Flate — no JPEG re-encoding, so a screenshot of a graph keeps its exact pixels rather than growing
     * compression artefacts around the thin lines that are the entire point of the picture.
     */
    public String addImage(BufferedImage image) {
        String name = "Im" + (imageIds.size() + 1);
        int w = image.getWidth();
        int h = image.getHeight();
        byte[] samples = new byte[w * h * 3];
        int i = 0;
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int rgb = image.getRGB(xx, yy);
                samples[i++] = (byte) (rgb >> 16);
                samples[i++] = (byte) (rgb >> 8);
                samples[i++] = (byte) rgb;
            }
        }
        byte[] deflated = deflate(samples);
        byte[] head = bytes("<< /Type /XObject /Subtype /Image /Width " + w + " /Height " + h
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length "
                + deflated.length + " >>\nstream\n");
        imageIds.put(name, imageObjects.size());
        imageObjects.add(concat(head, deflated, bytes("\nendstream")));
        return name;
    }

    /** Draw a registered image into a top-left anchored box. */
    public void drawImage(String name, float x, float y, float w, float h) {
        current.append("q\n")
                .append(num(w)).append(" 0 0 ").append(num(h)).append(' ')
                .append(num(x)).append(' ').append(num(flip(y + h))).append(" cm\n")
                .append('/').append(name).append(" Do\nQ\n");
    }

    /**
     * Break {@code text} into lines no wider than {@code maxWidth}, honouring existing newlines and
     * splitting on whitespace. A single word longer than the box is hard-split rather than allowed to run
     * off the page.
     */
    public static List<String> wrap(String text, Face face, float size, float maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                while (face.width(word, size) > maxWidth && word.length() > 1) {
                    int fit = Math.max(1, (int) (maxWidth / (face.widthFactor * size)));
                    out.add(word.substring(0, fit));
                    word = word.substring(fit);
                }
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (face.width(candidate, size) > maxWidth && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    /** Shorten {@code text} with an ellipsis until it fits {@code maxWidth}. */
    public static String clip(String text, Face face, float size, float maxWidth) {
        if (text == null || face.width(text, size) <= maxWidth) {
            return text;
        }
        String out = text;
        while (out.length() > 1 && face.width(out + "…", size) > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    // ---- serialisation ---------------------------------------------------------------------------

    public byte[] toBytes() {
        // 1 catalog, 2 pages, 3..6 fonts, then images, then (page, content) per page
        int fontStart = 3;
        int imageStart = fontStart + Face.values().length;
        int pageStart = imageStart + imageObjects.size();

        List<byte[]> objects = new ArrayList<>();
        objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            kids.append(pageStart + 2 * i).append(" 0 R ");
        }
        objects.add(bytes("<< /Type /Pages /Kids [ " + kids.toString().trim() + " ] /Count "
                + pages.size() + " >>"));

        for (Face f : Face.values()) {
            // WinAnsi, not the default StandardEncoding: without it bytes 0x80-0xFF are read from a
            // 1980s glyph table, so a middot or an accented name renders as something else entirely
            objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /" + f.baseFont
                    + " /Encoding /WinAnsiEncoding >>"));
        }
        objects.addAll(imageObjects);

        StringBuilder fontRes = new StringBuilder();
        for (int i = 0; i < Face.values().length; i++) {
            fontRes.append('/').append(resourceOf(Face.values()[i])).append(' ')
                    .append(fontStart + i).append(" 0 R ");
        }
        StringBuilder xobjRes = new StringBuilder();
        for (Map.Entry<String, Integer> e : imageIds.entrySet()) {
            xobjRes.append('/').append(e.getKey()).append(' ')
                    .append(imageStart + e.getValue()).append(" 0 R ");
        }
        String resources = "<< /Font << " + fontRes.toString().trim() + " >>"
                + (xobjRes.isEmpty() ? "" : " /XObject << " + xobjRes.toString().trim() + " >>") + " >>";

        for (int i = 0; i < pages.size(); i++) {
            objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + num(PAGE_W) + " "
                    + num(PAGE_H) + "] /Resources " + resources + " /Contents "
                    + (pageStart + 2 * i + 1) + " 0 R >>"));
            byte[] stream = bytes(pages.get(i).toString());
            objects.add(concat(bytes("<< /Length " + stream.length + " >>\nstream\n"),
                    stream, bytes("\nendstream")));
        }

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
        write(out, "xref\n0 " + n + "\n0000000000 65535 f \n");
        for (int off : offsets) {
            write(out, String.format("%010d 00000 n \n", off));
        }
        write(out, "trailer\n<< /Size " + n + " /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF");
        return out.toByteArray();
    }

    // ---- internals -------------------------------------------------------------------------------

    private static float flip(float yFromTop) {
        return PAGE_H - yFromTop;
    }

    private static String resourceOf(Face face) {
        return "F" + (face.ordinal() + 1);
    }

    private static String rgb(Color c, boolean fill) {
        return num(c.getRed() / 255f) + " " + num(c.getGreen() / 255f) + " " + num(c.getBlue() / 255f)
                + (fill ? " rg\n" : " RG\n");
    }

    private static String num(float f) {
        if (f == Math.rint(f) && Math.abs(f) < 1e7) {
            return Integer.toString((int) f);
        }
        return String.format("%.3f", f);
    }

    /**
     * Escape for a PDF literal string, in WinAnsi.
     *
     * <p>The standard-14 fonts are single-byte, so anything above U+00FF has to become something. The
     * obvious "replace it with {@code ?}" is wrong in the one case that actually occurs: this codebase
     * writes em dashes and curly quotes everywhere, and a report reading "the evidence ? the explanation
     * rests on" looks like a corrupted file rather than a typographic limitation. Common punctuation is
     * therefore transliterated to its ASCII equivalent, and only genuinely unrepresentable characters
     * fall back to {@code ?}.
     */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '(' -> sb.append("\\(");
                case ')' -> sb.append("\\)");
                case '\\' -> sb.append("\\\\");
                case '\t' -> sb.append("    ");
                case '—', '–', '−' -> sb.append('-');       // em/en dash, minus
                case '‘', '’', '‛' -> sb.append('\'');      // curly single quotes
                case '“', '”' -> sb.append('"');                 // curly double quotes
                case '…' -> sb.append("...");                         // ellipsis
                case '•' -> sb.append('·');                      // bullet → middot (WinAnsi)
                case ' ', ' ', ' ' -> sb.append(' ');       // non-breaking / thin spaces
                case '→' -> sb.append("->");                          // rightwards arrow
                case '✓', '✔' -> sb.append('v');                 // check marks
                default -> sb.append(c > 0xFF ? '?' : c);
            }
        }
        return sb.toString();
    }

    private static byte[] deflate(byte[] raw) {
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 4 + 64);
        byte[] buf = new byte[16 * 1024];
        while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf));
        }
        d.end();
        return out.toByteArray();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            b.writeBytes(p);
        }
        return b.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(bytes(s));
    }
}
