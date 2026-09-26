package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The bean list yields to the XML when the design panel is narrow; it keeps 210 px when there is room. */
class DesignSourcePanelLayoutTest {
    @Test void theBeanListNeverTakesMoreThanThirtyPercentOrItsUsualWidth() {
        assertEquals(63, DesignSourcePanel.beanListWidth(212), "the default-window panel keeps ~150 px of XML");
        assertEquals(210, DesignSourcePanel.beanListWidth(1000), "a wide panel keeps the usual list");
        assertEquals(0, DesignSourcePanel.beanListWidth(0));
        for (int w = 1; w <= 2000; w += 37) assertTrue(w - DesignSourcePanel.beanListWidth(w) >= w * 0.7, "xml at " + w);
    }
}
