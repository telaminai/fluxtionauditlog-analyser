package telamin.fluxtion.audit.analyser.analyser.ui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * A rendered image as PNG bytes, in memory. Everything that writes an image in this app writes a FILE; a reel
 * embeds its frames in one page (issue #82) and never puts them on disk, so it needs the bytes rather than a path.
 */
final class Png {

    private Png() {
    }

    /** PNG bytes for {@code image}; empty when the encoder refuses, which callers render as "no frame". */
    static byte[] bytes(BufferedImage image) {
        if (image == null) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
        try {
            return javax.imageio.ImageIO.write(image, "png", out) ? out.toByteArray() : new byte[0];
        } catch (java.io.IOException e) {
            return new byte[0];
        }
    }
}
