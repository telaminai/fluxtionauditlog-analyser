package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review M74 usability §4.1 (WCAG 2.2 AA), computed headless from the tokens themselves: every foreground token on every
 * surface it sits on — 4.5:1 for text, 3:1 for a control's boundary and the focus ring — in light AND dark, and the
 * FlatLaf defaults the app corrects. The surfaces are the theme's panel, the content surface, the start page's band,
 * the profile cards' washes and a text field's fill.
 */
class ThemeContrastTest {

    static final double TEXT = 4.5, UI = 3.0;

    record Pair(String where, Color fg, Color bg, double need) {
        double ratio() {
            return TranscriptStyle.contrast(fg, bg);
        }
    }

    static Color field(boolean dark) {
        return dark ? new Color(0x46494B) : Color.WHITE;
    }

    static List<Pair> pairs(boolean dark) {
        Color panel = UiTheme.panelOf(dark), band = UiTheme.bandOf(dark), surface = UiTheme.surface(dark);
        Color card04 = UiTheme.accentWash(0.04f, dark), card20 = UiTheme.accentWash(0.20f, dark);
        Color chip22 = UiTheme.accentWash(0.22f, dark);
        List<Pair> p = new ArrayList<>();
        for (var bg : Map.of("panel", panel, "band", band, "surface", surface, "card", card04, "selected card", card20)
                .entrySet()) {
            p.add(new Pair("muted on " + bg.getKey(), UiTheme.mutedForeground(dark), bg.getValue(), TEXT));
        }
        for (var bg : Map.of("panel", panel, "band", band, "surface", surface).entrySet()) {
            p.add(new Pair("control border on " + bg.getKey(), UiTheme.controlBorder(dark), bg.getValue(), UI));
            p.add(new Pair("focus ring on " + bg.getKey(), UiTheme.focusRing(dark), bg.getValue(), UI));
        }
        p.add(new Pair("placeholder on its field", UiTheme.placeholderForeground(dark), field(dark), TEXT));
        p.add(new Pair("start box border on the band (the field's edge)", UiTheme.controlBorder(dark), band, UI));
        for (boolean strong : new boolean[]{false, true}) {
            p.add(new Pair("full cue " + (strong ? "strong" : "standard") + " on panel", UiTheme.fullCue(strong, dark), panel, TEXT));
            p.add(new Pair("full cue " + (strong ? "strong" : "standard") + " on band", UiTheme.fullCue(strong, dark), band, TEXT));
            p.add(new Pair("transcript full cue " + strong + " on panel", TranscriptStyle.fullPosture(strong, dark), panel, TEXT));
        }
        p.add(new Pair("note on panel", UiTheme.noteForeground(dark), panel, TEXT));
        p.add(new Pair("note on surface", UiTheme.noteForeground(dark), surface, TEXT));
        p.add(new Pair("error on panel", UiTheme.errorForeground(dark), panel, TEXT));
        p.add(new Pair("error on band", UiTheme.errorForeground(dark), band, TEXT));
        p.add(new Pair("warn on panel", UiTheme.warnForeground(dark), panel, TEXT));
        p.add(new Pair("warn on band", UiTheme.warnForeground(dark), band, TEXT));
        p.add(new Pair("attention on panel", UiTheme.attentionForeground(dark), panel, TEXT));
        p.add(new Pair("ok on panel", UiTheme.okForeground(dark), panel, TEXT));
        p.add(new Pair("accent text on a chip", UiTheme.accentText(dark), chip22, TEXT));
        p.add(new Pair("accent text on the selected card", UiTheme.accentText(dark), card20, TEXT));
        p.add(new Pair("the primary button's text on the accent", UiTheme.onAccent(dark), UiTheme.accent(dark), TEXT));
        TranscriptStyle.Palette t = TranscriptStyle.palette(dark);
        p.add(new Pair("transcript denied mark", t.denied(), t.background(), TEXT));
        p.add(new Pair("transcript code comment", t.comment(), t.background(), TEXT));
        p.add(new Pair("transcript code comment on the code block", t.comment(), t.codeBackground(), TEXT));
        if (dark) {
            p.add(new Pair("dark default button's text on its fill", ThemeManager.DARK_DEFAULT_BUTTON_TEXT,
                    ThemeManager.DARK_DEFAULT_BUTTON_FILL, TEXT));
        }
        return p;
    }

    /**
     * ★ Every pair the review measured below AA, and their neighbours, meet it now. Witness: the review's old values —
     * FlatLaf's disabled grey #8C8C8C as {@code mutedForeground}, the #B35900 full cue, FlatLaf's #C2C2C2 border — fail
     * {@link #failures} at the first row (asserted in {@link #theOldValuesFail}).
     */
    @ParameterizedTest(name = "dark={0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("★ every token pair meets WCAG AA: 4.5:1 text, 3:1 control boundaries and focus")
    void everyPairMeetsAA(boolean dark) {
        List<String> failed = failures(pairs(dark));
        assertTrue(failed.isEmpty(), (dark ? "dark" : "light") + ": " + failed);
    }

    static List<String> failures(List<Pair> pairs) {
        List<String> out = new ArrayList<>();
        for (Pair p : pairs) {
            if (p.ratio() < p.need()) out.add(String.format("%s %.2f < %.1f", p.where(), p.ratio(), p.need()));
        }
        return out;
    }

    @Test
    @DisplayName("★ the witness: the old muted grey, full cue and FlatLaf border fail the same check")
    void theOldValuesFail() {
        Color oldGrey = new Color(0x8C8C8C);
        assertFalse(failures(List.of(new Pair("old muted on light panel", oldGrey, UiTheme.panelOf(false), TEXT))).isEmpty());
        assertFalse(failures(List.of(new Pair("old muted on light band", oldGrey, UiTheme.bandOf(false), TEXT))).isEmpty());
        assertFalse(failures(List.of(new Pair("old muted on dark panel", oldGrey, UiTheme.panelOf(true), TEXT))).isEmpty());
        assertFalse(failures(List.of(new Pair("old full cue", new Color(0xB35900), UiTheme.panelOf(false), TEXT))).isEmpty());
        assertFalse(failures(List.of(new Pair("old field border", new Color(0xC2C2C2), UiTheme.panelOf(false), UI))).isEmpty());
        assertFalse(failures(List.of(new Pair("old dark box on band", new Color(0x46494B), UiTheme.bandOf(true), UI))).isEmpty(),
                "the dark start box against its band, 1.52:1");
        assertFalse(failures(List.of(new Pair("old card focus border", UiTheme.mix(new Color(0xD6DBE1), new Color(0x1F6FEB),
                0.6f), UiTheme.panelOf(false), UI))).isEmpty(), "the profile card's focus border, 2.55:1");
    }

    /** The FlatLaf corrections are in the look and feel's defaults for both themes, and meet the same bars. */
    @ParameterizedTest(name = "dark={0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("FlatLaf's control borders, focus ring and placeholders are corrected, per theme")
    void theFlatLafOverrides(boolean dark) {
        Map<String, Object> o = ThemeManager.overrides(dark);
        Color panel = UiTheme.panelOf(dark);
        for (String k : List.of("Component.borderColor", "Button.borderColor", "CheckBox.icon.borderColor")) {
            assertTrue(TranscriptStyle.contrast((Color) o.get(k), panel) >= UI, k);
        }
        for (String k : List.of("Component.focusColor", "Button.default.borderColor")) {
            assertTrue(TranscriptStyle.contrast((Color) o.get(k), panel) >= UI, k);
        }
        assertTrue(TranscriptStyle.contrast((Color) o.get("TextField.placeholderForeground"), field(dark)) >= TEXT);
        assertEquals(dark, o.containsKey("Button.default.foreground"), "the dark default button's text only");
    }

    /**
     * ★ Review §2 / #17: one colour meant full mode, a denied call AND a status note. Each has its own token now, and none
     * is the only cue. Witness: give the denied mark the amber back — it equals neither, but sits beside full's orange.
     */
    @ParameterizedTest(name = "dark={0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("★ full mode, a denied call and a note each have their own colour")
    void eachMeaningHasItsOwnColour(boolean dark) {
        Color full = UiTheme.fullCue(false, dark), strong = UiTheme.fullCue(true, dark);
        Color denied = TranscriptStyle.mark(TranscriptStyle.Mark.DENIED, dark);
        Color note = UiTheme.noteForeground(dark);
        assertEquals(UiTheme.deniedForeground(dark), denied, "the transcript's denied mark is the denied token");
        for (Color c : List.of(denied, note)) {
            assertNotEquals(full, c);
            assertNotEquals(strong, c);
            assertTrue(hueDistance(full, c) > 30 || saturation(c) < 0.25f,
                    "not another orange: " + Integer.toHexString(c.getRGB()));
        }
        assertNotEquals(denied, note);
        assertEquals(TranscriptStyle.fullPosture(false, dark), full, "the transcript and the badge share full's token");
    }

    static float hue(Color c) {
        return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[0] * 360f;
    }

    static float saturation(Color c) {
        return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null)[1];
    }

    static float hueDistance(Color a, Color b) {
        float d = Math.abs(hue(a) - hue(b));
        return Math.min(d, 360 - d);
    }
}
