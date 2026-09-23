package telamin.fluxtion.audit.analyser.analyser.ui;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.Optional;

/** Geometry only: a logical Java line may occupy several visual rows and be partly visible. */
final class JavaLineGeometry {
    private JavaLineGeometry() { }
    static Rectangle band(Rectangle2D first, Rectangle2D last, Rectangle viewport) {
        int y = (int) Math.floor(first.getY());
        return new Rectangle(viewport.x, y, viewport.width, (int) Math.ceil(last.getMaxY()) - y);
    }
    record Visible(Rectangle bounds, boolean partial) { }
    static Optional<Visible> visible(Rectangle band, Rectangle viewport) {
        Rectangle clipped = band.intersection(viewport);
        return clipped.isEmpty() ? Optional.empty() : Optional.of(new Visible(clipped, !viewport.contains(band)));
    }
}
