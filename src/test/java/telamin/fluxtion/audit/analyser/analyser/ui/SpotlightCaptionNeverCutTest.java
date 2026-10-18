package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner decision, 2026-10-17: a spotlight caption is never truncated silently. It wraps at about 40 characters a line
 * (a theme token), shows at most three lines, ends the third with "…" when there is more, EXPANDS in place to the whole
 * caption on hover or keyboard focus, and its accessible name is always the whole caption. Headless: the overlay is
 * painted into an image, and hover and focus are delivered as the events Swing would send.
 */
class SpotlightCaptionNeverCutTest {

    static final String LONG = "The breach handler logged the first breach at record sixteen, one cycle after the spread "
            + "widened past its limit at record fifteen, which is why the chart's step sits one record late";

    /** Every word of {@code caption}, in order, with the lines joined — what a reader of the lines is told. */
    static String joined(List<String> lines) {
        return String.join(" ", lines).replace("…", "").replaceAll("\\s+", " ").strip();
    }

    /** No lone surrogate anywhere in {@code lines}: every high surrogate is followed by its low one. */
    static void noSplitPair(List<String> lines) {
        for (String l : lines) {
            for (int i = 0; i < l.length(); i++) {
                char c = l.charAt(i);
                if (Character.isHighSurrogate(c)) {
                    assertTrue(i + 1 < l.length() && Character.isLowSurrogate(l.charAt(i + 1)), "a split pair in " + l);
                    i++;
                } else {
                    assertFalse(Character.isLowSurrogate(c), "a lone low surrogate in " + l);
                }
            }
        }
    }

    /**
     * ★ Review PR151 F5 (2026-10-18): a run broken at the line's width, and the collapsed last line cut before its "…",
     * are cut between graphemes, never inside an emoji's surrogate pair. Witness: break by char ({@code substring(0,
     * width)}) — the 40th char is the high half of an emoji, and the line ends in a lone surrogate.
     */
    @Test
    @DisplayName("★ a wrap or a collapse never splits an emoji or another astral character")
    void anEmojiIsNeverSplit() {
        String emoji = new String(Character.toChars(0x1F600));
        int w = UiTheme.CALLOUT_LINE_CHARS;
        String run = "a".repeat(w - 1) + emoji + "b".repeat(w) + emoji + emoji;    // the emoji straddles the width
        List<String> expanded = SpotlightOverlay.calloutLines(run, true);
        noSplitPair(expanded);
        assertEquals(run, String.join("", expanded), "every character kept, in order");
        assertTrue(expanded.stream().allMatch(l -> l.length() <= w), expanded.toString());
        String cjk = new String(Character.toChars(0x20000));                           // an astral CJK ideograph
        String many = (cjk + " ").repeat(4 * w);
        noSplitPair(SpotlightOverlay.calloutLines(many, false));
        // the collapsed third line is r…r😀 (w chars) and is cut to w - 1 before its "…": the cut lands inside the pair
        String third = "p".repeat(w) + " " + "q".repeat(w) + " " + "r".repeat(w - 2) + emoji + "s".repeat(10);
        assertEquals("r".repeat(w - 2) + emoji, SpotlightOverlay.calloutLines(third, true).get(2), "the setup");
        List<String> collapsed = SpotlightOverlay.calloutLines(third, false);
        noSplitPair(collapsed);
        assertEquals("r".repeat(w - 2) + "…", collapsed.get(2), "cut before the emoji, never through it");
    }

    /**
     * ★ Witness: today's wrap — a pixel wrap with no limit, laid into a box the layout then clamps into the frame — let
     * a long caption run off the frame; and a word longer than the box ran out of it. Here: collapsed is at most three
     * lines, none longer than the token, the last ends "…", and the EXPANDED lines carry every word.
     */
    @Test
    @DisplayName("★ collapsed: at most three lines of about 40 characters, the last ending …; expanded: every word")
    void collapsedThenExpandedLosesNothing() {
        List<String> collapsed = SpotlightOverlay.calloutLines(LONG, false);
        assertEquals(UiTheme.CALLOUT_MAX_LINES, collapsed.size(), collapsed.toString());
        for (String l : collapsed) assertTrue(l.length() <= UiTheme.CALLOUT_LINE_CHARS, "'" + l + "' fits a line");
        assertTrue(collapsed.get(collapsed.size() - 1).endsWith("…"), "the cut is visible: " + collapsed);
        assertTrue(SpotlightOverlay.calloutTruncated(LONG));
        List<String> expanded = SpotlightOverlay.calloutLines(LONG, true);
        assertEquals(LONG.replaceAll("\\s+", " "), joined(expanded), "the expanded text equals the full caption");
        for (String l : expanded) assertTrue(l.length() <= UiTheme.CALLOUT_LINE_CHARS, l);

        String shortOne = "breachHandler never logged";
        assertEquals(List.of(shortOne), SpotlightOverlay.calloutLines(shortOne, false), "a short caption is as written");
        assertFalse(SpotlightOverlay.calloutTruncated(shortOne));

        String run = "x".repeat(95);
        List<String> broken = SpotlightOverlay.calloutLines(run, true);
        assertEquals(run, String.join("", broken), "a run longer than a line is broken, never clipped by the card");
        for (String l : broken) assertTrue(l.length() <= UiTheme.CALLOUT_LINE_CHARS);
    }

    static SpotlightOverlay painted(SpotlightOverlay o) {
        BufferedImage img = new BufferedImage(o.getWidth(), o.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        o.paint(g);
        g.dispose();
        return o;
    }

    /** ★ Witness: name the target with the drawn (collapsed) lines — the accessible name ends in "…" and this fails. */
    @Test
    @DisplayName("★ the callout's accessible name is always the whole caption; hover and focus expand it, leaving collapses")
    void hoverAndFocusExpandAndTheNameIsWhole() {
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.setSize(900, 600);
        o.light("records:row:16", new Rectangle(100, 100, 300, 24), LONG);
        painted(o);
        List<SpotlightOverlay.CalloutTarget> targets = o.calloutTargets();
        assertEquals(1, targets.size());
        SpotlightOverlay.CalloutTarget t = targets.get(0);
        assertEquals(LONG, t.getAccessibleContext().getAccessibleName(), "the whole caption, never the drawn lines");
        assertTrue(t.isFocusable(), "a keyboard user can reach it");
        Rectangle collapsed = new Rectangle(o.lastCallouts().get(0));
        assertEquals(collapsed, t.getBounds(), "the target is where the card is drawn");

        t.dispatchEvent(new MouseEvent(t, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 5, 5, 0, false));
        assertEquals(0, o.expandedCallout(), "hover expands it");
        painted(o);
        Rectangle open = o.lastCallouts().get(0);
        assertTrue(open.height > collapsed.height, "the same card, grown to the whole caption: " + open + " vs " + collapsed);
        assertTrue(open.x >= 0 && open.y >= 0 && open.x + open.width <= 900 && open.y + open.height <= 600,
                "and kept inside the frame: " + open);
        assertEquals(open, t.getBounds(), "the pointer stays over the grown card, so it stays open");

        t.dispatchEvent(new MouseEvent(t, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, 5, 5, 0, false));
        assertEquals(-1, o.expandedCallout(), "leaving collapses it");
        painted(o);
        assertEquals(collapsed, o.lastCallouts().get(0));

        // a component that is not on a screen gets no focus events from the focus manager: the listener is called as it
        // would be
        for (var l : t.getFocusListeners()) l.focusGained(new FocusEvent(t, FocusEvent.FOCUS_GAINED));
        assertEquals(0, o.expandedCallout(), "keyboard focus expands it too");
        for (var l : t.getFocusListeners()) l.focusLost(new FocusEvent(t, FocusEvent.FOCUS_LOST));
        assertEquals(-1, o.expandedCallout());

        o.clearSpotlight();
        assertTrue(o.calloutTargets().isEmpty(), "no callout, nothing left to focus");
    }

    @Test
    @DisplayName("a short caption does not grow on hover: there is nothing more to show")
    void aShortCaptionStaysAsItIs() {
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.setSize(900, 600);
        o.light("status", new Rectangle(100, 100, 300, 24), "here");
        painted(o);
        Rectangle before = new Rectangle(o.lastCallouts().get(0));
        SpotlightOverlay.CalloutTarget t = o.calloutTargets().get(0);
        t.dispatchEvent(new MouseEvent(t, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 5, 5, 0, false));
        painted(o);
        assertEquals(before, o.lastCallouts().get(0));
        assertEquals("here", t.getAccessibleContext().getAccessibleName());
    }
}
