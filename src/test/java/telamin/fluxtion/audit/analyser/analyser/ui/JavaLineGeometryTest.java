package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import static org.junit.jupiter.api.Assertions.*;

class JavaLineGeometryTest {
    @Test void fullLogicalBandHasWidthAndIncludesWrappedRows() {
        var viewport = new Rectangle(80, 20, 300, 100);
        var band = JavaLineGeometry.band(new Rectangle2D.Double(100, 40, 1, 15),
                new Rectangle2D.Double(200, 70, 1, 15), viewport);
        assertEquals(new Rectangle(80, 40, 300, 45), band);
        var visible = JavaLineGeometry.visible(band, viewport).orElseThrow();
        assertEquals(band, visible.bounds()); assertFalse(visible.partial());
    }
    @Test void clippingIsExplicitAndNoIntersectionIsNotVisible() {
        var viewport = new Rectangle(100, 30, 200, 90);
        var visible = JavaLineGeometry.visible(new Rectangle(100, 20, 200, 300), viewport).orElseThrow();
        assertEquals(viewport, visible.bounds()); assertTrue(visible.partial());
        assertTrue(JavaLineGeometry.visible(new Rectangle(100, 130, 200, 10), viewport).isEmpty());
        assertTrue(JavaLineGeometry.visible(new Rectangle(100, 30, 0, 10), viewport).isEmpty());
    }
}
