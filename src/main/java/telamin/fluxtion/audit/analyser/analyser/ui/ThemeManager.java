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
            // review M74 usability §4.1: FlatLaf's control borders, focus ring and placeholders are under WCAG AA; the
            // corrections go into the LOOK AND FEEL's defaults, so the next theme switch replaces them with its own
            boolean dark = laf instanceof com.formdev.flatlaf.FlatLaf f && f.isDark();
            UIManager.getLookAndFeelDefaults().putAll(overrides(dark));
        } catch (Exception ignore) {
            // keep the current look and feel on failure
        }
    }

    /**
     * The FlatLaf defaults this app corrects, per theme (review M74 usability §4.1, computed by
     * {@code ThemeContrastTest}): every control boundary 3:1 against the panel, the focus ring the accent, placeholder
     * text 4.5:1 on its field, and the dark default button's text 4.5:1 on its fill. One place, never per component.
     */
    static java.util.Map<String, Object> overrides(boolean dark) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        javax.swing.plaf.ColorUIResource border = new javax.swing.plaf.ColorUIResource(UiTheme.controlBorder(dark));
        javax.swing.plaf.ColorUIResource focus = new javax.swing.plaf.ColorUIResource(UiTheme.focusRing(dark));
        javax.swing.plaf.ColorUIResource placeholder = new javax.swing.plaf.ColorUIResource(UiTheme.placeholderForeground(dark));
        for (String k : new String[]{"Component.borderColor", "Button.borderColor", "CheckBox.icon.borderColor",
                "ToggleButton.borderColor"}) {
            m.put(k, border);
        }
        for (String k : new String[]{"Component.focusColor", "Component.focusedBorderColor", "Button.focusedBorderColor",
                "Button.default.borderColor", "Button.default.focusedBorderColor", "CheckBox.icon.focusedBorderColor"}) {
            m.put(k, focus);
        }
        for (String k : new String[]{"TextField.placeholderForeground", "PasswordField.placeholderForeground",
                "FormattedTextField.placeholderForeground", "ComboBox.placeholderForeground", "TextArea.placeholderForeground"}) {
            m.put(k, placeholder);
        }
        if (dark) m.put("Button.default.foreground", new javax.swing.plaf.ColorUIResource(DARK_DEFAULT_BUTTON_TEXT));
        return m;
    }

    /** The dark default button's text: FlatLaf's #BBBBBB on its #375A81 fill was 3.72:1. */
    static final Color DARK_DEFAULT_BUTTON_TEXT = new Color(0xFFFFFF);
    /** FlatDarkLaf's default-button fill, which the text above is measured against. */
    static final Color DARK_DEFAULT_BUTTON_FILL = new Color(0x375A81);

    /** True when the active theme is dark — so text/code colouring can adapt for contrast. */
    public static boolean isDark() {
        Color bg = UIManager.getColor("Panel.background");
        if (bg == null) return false;
        double luminance = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return luminance < 128;
    }
}
