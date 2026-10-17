import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Composite window captures into one image: {@code java tools/CompositeShot.java out.png scale base.png bx by [over.png x y]...}.
 *
 * <p>Why: a menu shot needs the frame AND its popup, which macOS keeps as two windows. {@code screencapture -R} (a screen
 * region) photographs both, but on a machine where it answers "could not create image from rect" only the per-window
 * {@code screencapture -l <id>} works, and that takes one window. So capture-docs.py takes each window by id and this
 * draws the popups onto the frame at their screen offsets (CG points; {@code scale} is image pixels per point). Every
 * pixel is one of the analyser's own windows — nothing else on the screen can reach the image.
 */
public final class CompositeShot {
    public static void main(String[] a) throws Exception {
        if (a.length < 5 || (a.length - 5) % 3 != 0) {
            throw new IllegalArgumentException("out.png scale base.png bx by [over.png x y]...");
        }
        double scale = Double.parseDouble(a[1]);
        BufferedImage base = ImageIO.read(new File(a[2]));
        double bx = Double.parseDouble(a[3]), by = Double.parseDouble(a[4]);
        BufferedImage out = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(base, 0, 0, null);
        for (int i = 5; i < a.length; i += 3) {
            BufferedImage over = ImageIO.read(new File(a[i]));
            int x = (int) Math.round((Double.parseDouble(a[i + 1]) - bx) * scale);
            int y = (int) Math.round((Double.parseDouble(a[i + 2]) - by) * scale);
            g.drawImage(over, x, y, null);
        }
        g.dispose();
        if (!ImageIO.write(out, "png", new File(a[0]))) throw new IllegalStateException("could not write " + a[0]);
    }
}
