import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Draw "click here" marks on a GENERATED documentation screenshot: a ring around a rectangle, and a short
 * numbered caption beside it. Run as a single-file program, so the docs harness needs nothing but a JDK:
 *
 * <pre>java tools/AnnotateShot.java in.png out.png scale "x,y,w,h|caption" ["x,y,w,h|caption" ...]</pre>
 *
 * {@code scale} is image pixels per logical pixel (2 for a Retina native capture, 1 for a Robot capture);
 * rectangles are given in LOGICAL pixels, as the app reports them, and scaled here.
 *
 * <p><b>This is a docs annotation, not the product's spotlight</b>, and it is drawn to look different on
 * purpose: no dimming, an orange ring (the docs site's accent), and no "assistant" tag. The analyser's own
 * spotlight cannot reach a menu item or a dialog — it overlays the main window only — so for those two steps
 * of the tutorial the mark is added here, to an image the harness itself just captured under the isolated
 * home. It is never applied to a hand-taken picture: CLAUDE.md rule 1 is about where an image comes from.
 */
public final class AnnotateShot {

    private static final Color RING = new Color(0xFF, 0x6E, 0x40);          // Material deep orange A200 — the site accent
    private static final Color INK = new Color(0x21, 0x21, 0x21);

    private record Mark(Rectangle at, String caption) { }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException("usage: in.png out.png scale \"x,y,w,h|caption\" …");
        }
        BufferedImage in = ImageIO.read(new File(args[0]));
        if (in == null) throw new IllegalArgumentException("not an image: " + args[0]);
        double scale = Double.parseDouble(args[2]);
        List<Mark> marks = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            String[] halves = args[i].split("\\|", 2);
            String[] n = halves[0].split(",");
            marks.add(new Mark(new Rectangle((int) Math.round(Integer.parseInt(n[0].trim()) * scale),
                    (int) Math.round(Integer.parseInt(n[1].trim()) * scale),
                    (int) Math.round(Integer.parseInt(n[2].trim()) * scale),
                    (int) Math.round(Integer.parseInt(n[3].trim()) * scale)), halves.length > 1 ? halves[1] : ""));
        }

        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(in, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        float s = (float) scale;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(13 * s)));
        FontMetrics fm = g.getFontMetrics();

        List<Rectangle> taken = new ArrayList<>();
        for (Mark m : marks) taken.add(m.at());
        for (int i = 0; i < marks.size(); i++) {
            Mark m = marks.get(i);
            Rectangle r = new Rectangle(m.at());
            r.grow(Math.round(4 * s), Math.round(4 * s));
            r = r.intersection(new Rectangle(1, 1, out.getWidth() - 2, out.getHeight() - 2));
            g.setColor(RING);
            g.setStroke(new BasicStroke(3 * s));
            g.draw(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, 10 * s, 10 * s));
            if (marks.size() > 1) badge(g, fm, r, i + 1, s, out.getWidth(), out.getHeight());
            if (m.caption().isBlank()) continue;                  // a ring and its number: for a mark with no room for words

            String text = m.caption();
            int pad = Math.round(8 * s), gap = Math.round(22 * s);
            int w = fm.stringWidth(text) + 2 * pad, h = fm.getHeight() + 2 * pad;
            Rectangle box = place(r, w, h, gap, out.getWidth(), out.getHeight(), taken);
            taken.add(box);

            // the arrow: from the caption's edge nearest the ring to the ring's edge nearest the caption
            int tx = clamp(box.x + box.width / 2, r.x, r.x + r.width), ty = clamp(box.y + box.height / 2, r.y, r.y + r.height);
            int fx = clamp(tx, box.x, box.x + box.width), fy = clamp(ty, box.y, box.y + box.height);
            g.setStroke(new BasicStroke(2.5f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(fx, fy, tx, ty);
            double a = Math.atan2(ty - fy, tx - fx), size = 9 * s, spread = Math.toRadians(26);
            Path2D head = new Path2D.Double();
            head.moveTo(tx, ty);
            head.lineTo(tx - size * Math.cos(a - spread), ty - size * Math.sin(a - spread));
            head.lineTo(tx - size * Math.cos(a + spread), ty - size * Math.sin(a + spread));
            head.closePath();
            g.fill(head);

            g.setColor(new Color(255, 255, 255, 246));
            g.fill(new RoundRectangle2D.Float(box.x, box.y, box.width, box.height, 8 * s, 8 * s));
            g.setColor(RING);
            g.setStroke(new BasicStroke(2 * s));
            g.draw(new RoundRectangle2D.Float(box.x, box.y, box.width, box.height, 8 * s, 8 * s));
            g.setColor(INK);
            g.drawString(text, box.x + pad, box.y + pad + fm.getAscent());
        }
        g.dispose();
        if (!ImageIO.write(out, "png", new File(args[1]))) throw new IllegalStateException("could not write " + args[1]);
    }

    /** The mark's number, on the ring's top-left corner — what the page's text ("① … then ②") refers to. */
    private static void badge(Graphics2D g, FontMetrics fm, Rectangle ring, int n, float s, int imageW, int imageH) {
        int d = Math.round(20 * s);
        int x = clamp(ring.x - d / 2, 2, imageW - d - 2), y = clamp(ring.y - d / 2, 2, imageH - d - 2);
        g.setColor(RING);
        g.fillOval(x, y, d, d);
        g.setColor(Color.WHITE);
        String text = Integer.toString(n);
        g.drawString(text, x + (d - fm.stringWidth(text)) / 2, y + (d - fm.getHeight()) / 2 + fm.getAscent());
        g.setColor(RING);
    }

    /** Right, left, below, above — the first that is on the image and covers no ring or caption already there. */
    private static Rectangle place(Rectangle ring, int w, int h, int gap, int imageW, int imageH, List<Rectangle> taken) {
        int cy = ring.y + (ring.height - h) / 2, cx = ring.x + (ring.width - w) / 2;
        Rectangle[] tries = {
                new Rectangle(ring.x + ring.width + gap, cy, w, h), new Rectangle(ring.x - gap - w, cy, w, h),
                new Rectangle(cx, ring.y + ring.height + gap, w, h), new Rectangle(cx, ring.y - gap - h, w, h)};
        Rectangle image = new Rectangle(4, 4, imageW - 8, imageH - 8);
        Rectangle fallback = null;
        for (Rectangle t : tries) {
            if (!image.contains(t)) continue;
            if (fallback == null) fallback = t;
            boolean clear = true;
            for (Rectangle other : taken) if (other != ring && other.intersects(t)) clear = false;
            if (clear) return t;
        }
        if (fallback != null) return fallback;
        Rectangle below = tries[2];
        below.x = clamp(below.x, 4, imageW - w - 4);
        below.y = clamp(below.y, 4, imageH - h - 4);
        return below;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }
}
