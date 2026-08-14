package telamin.fluxtion.audit.analyser.analyser.ui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import java.awt.Color;

/** FlatLaf theme selection + a light/dark probe the highlighters/chart use to pick palettes. */
public final class ThemeManager {

    public static final String[] THEMES = {"Light", "Dark", "IntelliJ", "Darcula"};

    private ThemeManager() {
    }

    public static void apply(String name) {
        try {
            LookAndFeel laf = switch (name == null ? "Light" : name) {
                case "Dark" -> new FlatDarkLaf();
                case "IntelliJ" -> new FlatIntelliJLaf();
                case "Darcula" -> new FlatDarculaLaf();
                default -> new FlatLightLaf();
            };
            UIManager.setLookAndFeel(laf);
        } catch (Exception ignore) {
            // keep the current look and feel on failure
        }
    }

    /** True when the active theme is dark — so text/code colouring can adapt for contrast. */
    public static boolean isDark() {
        Color bg = UIManager.getColor("Panel.background");
        if (bg == null) return false;
        double luminance = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return luminance < 128;
    }
}
