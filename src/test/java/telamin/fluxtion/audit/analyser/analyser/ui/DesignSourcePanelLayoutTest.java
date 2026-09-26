package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import static org.junit.jupiter.api.Assertions.*;

/** The bean list yields to the XML when the design panel is narrow; it keeps 210 px when there is room. */
class DesignSourcePanelLayoutTest {
    @Test void theBeanListNeverTakesMoreThanThirtyPercentOrItsUsualWidth() {
        assertEquals(63, DesignSourcePanel.beanListWidth(212), "the default-window panel keeps ~150 px of XML");
        assertEquals(210, DesignSourcePanel.beanListWidth(1000), "a wide panel keeps the usual list");
        assertEquals(0, DesignSourcePanel.beanListWidth(0));
        for (int w = 1; w <= 2000; w += 37) assertTrue(w - DesignSourcePanel.beanListWidth(w) >= w * 0.7, "xml at " + w);
    }

    /** PR #35 review: a theme switch replaced the divider, and the first drag after it was reset to 210 px on layout. */
    @Test void aDraggedWidthIsKeptWithAndWithoutAThemeSwitchFirst() throws Exception {
        LookAndFeel before = UIManager.getLookAndFeel();
        try {
            SwingUtilities.invokeAndWait(() -> assertEquals(210, layOut(new DesignSourcePanel(), false),
                    "control: without a drag the fit is active and resets 25 px to the usual 210"));
            SwingUtilities.invokeAndWait(() -> assertEquals(25, dragThenLayOut(false), "control: a drag is kept"));
            SwingUtilities.invokeAndWait(() -> assertEquals(25, dragThenLayOut(true),
                    "a first drag after a theme switch keeps the person's width"));
        } finally {
            // restore on the EDT, where every other look-and-feel change in this JVM happens
            SwingUtilities.invokeAndWait(() -> {
                try { UIManager.setLookAndFeel(before); } catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            });
        }
    }

    /** Drag the divider to 25 px (a press on the divider, then the move), lay out 1000 px wide, return the divider. */
    private static int dragThenLayOut(boolean switchThemeFirst) {
        var panel = new DesignSourcePanel();
        if (switchThemeFirst) {
            try { UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf()); }
            catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            SwingUtilities.updateComponentTreeUI(panel);
        }
        return layOut(panel, true);
    }

    /** Optionally press the divider, move it to 25 px, then lay the split out 1000 px wide (headless: no peer). */
    private static int layOut(DesignSourcePanel panel, boolean press) {
        JSplitPane split = find(panel);
        if (press) {
            var divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
                    1, 1, 1, false, MouseEvent.BUTTON1));
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_RELEASED, 0, 0, 1, 1, 1, false, MouseEvent.BUTTON1));
        }
        split.setDividerLocation(25);
        split.setSize(1000, 500);
        assertEquals(1000, split.getWidth(), "control: the split has width, so the fit runs");
        split.doLayout();
        return split.getDividerLocation();
    }

    private static JSplitPane find(Container c) {
        for (var child : c.getComponents()) {
            if (child instanceof JSplitPane s) return s;
            if (child instanceof Container k) { var s = find(k); if (s != null) return s; }
        }
        return null;
    }
}
