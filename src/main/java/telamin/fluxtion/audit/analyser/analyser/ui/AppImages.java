package telamin.fluxtion.audit.analyser.analyser.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the app icon and splash artwork at runtime (no image files needed). The motif: a
 * rounded blue tile with stacked "log record" rows and a magnifier — analysing audit logs.
 */
public final class AppImages {

    private static final Color BG_TOP = new Color(0x0B4DA2);
    private static final Color BG_BOT = new Color(0x0969DA);
    private static final Color ROW = new Color(0xFFFFFF);
    private static final Color GLASS = new Color(0xEAF2FF);

    private AppImages() {
    }

    /** Icon images at the common sizes for {@code setIconImages}. */
    public static List<Image> icons() {
        List<Image> out = new ArrayList<>();
        for (int size : new int[]{16, 32, 48, 64, 128, 256}) out.add(icon(size));
        return out;
    }

    public static BufferedImage icon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawTile(g, 0, 0, size, size);
        g.dispose();
        return img;
    }

    /** Draws the icon motif into the given rectangle (reused by the splash). */
    static void drawTile(Graphics2D g, int x, int y, int w, int h) {
        int arc = Math.max(6, w / 5);
        g.setPaint(new GradientPaint(x, y, BG_TOP, x, y + h, BG_BOT));
        g.fillRoundRect(x, y, w, h, arc, arc);

        // stacked "records"
        int pad = Math.max(3, w / 6);
        int rowH = Math.max(2, h / 12);
        int gap = Math.max(2, h / 12);
        int rx = x + pad;
        int ry = y + pad;
        int[] widths = {w - 2 * pad, (int) ((w - 2 * pad) * 0.75), (int) ((w - 2 * pad) * 0.55)};
        g.setColor(new Color(255, 255, 255, 235));
        for (int i = 0; i < widths.length; i++) {
            g.fillRoundRect(rx, ry + i * (rowH + gap), Math.max(4, widths[i]), rowH, rowH, rowH);
        }

        // magnifier bottom-right
        int r = Math.max(6, w / 3);
        int cx = x + w - pad - r / 2;
        int cy = y + h - pad - r / 2;
        g.setStroke(new BasicStroke(Math.max(1.5f, w / 24f)));
        g.setColor(GLASS);
        g.drawOval(cx - r / 2, cy - r / 2, r, r);
        g.drawLine(cx + (int) (r * 0.35), cy + (int) (r * 0.35), x + w - pad / 2, y + h - pad / 2);
    }

    /** The splash banner image. */
    public static BufferedImage splash(int w, int h, String title, String subtitle) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x0D1117));
        g.fillRoundRect(0, 0, w, h, 18, 18);
        g.setColor(new Color(0x30363D));
        g.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);

        int tile = h - 60;
        drawTile(g, 30, 30, tile, tile);

        int tx = 30 + tile + 28;
        g.setColor(Color.WHITE);
        g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 22f));
        g.drawString(title, tx, 74);
        g.setColor(new Color(0x9DA7B3));
        g.setFont(g.getFont().deriveFont(java.awt.Font.PLAIN, 13f));
        g.drawString(subtitle, tx, 100);
        g.dispose();
        return img;
    }
}
