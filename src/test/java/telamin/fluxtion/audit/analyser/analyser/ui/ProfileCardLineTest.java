package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review M74 usability #1 (2026-10-17): the Safe analysis card's promise stopped at "…after you approve each call. No" —
 * {@code ProfileOverlay.wrap} broke out of its loop at the line limit, dropping the rest, and its ellipsis branch could
 * never fire. The card's line is never cut silently now: the card is as tall as its text, and where room ever runs out
 * the last line ends in "…" (the whole line is the tooltip and the accessible description).
 */
class ProfileCardLineTest {

    static FontMetrics metrics() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).getGraphics()
                .getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    }

    static String joined(List<String> lines) {
        return String.join(" ", lines).replace("…", "").strip();
    }

    /**
     * ★ Witness: the old wrap (break at {@code maxLines}, ellipsis only if the kept line is too wide) — at the card's
     * width and two lines the Safe line ends "No" with no "…", and the first assertion fails.
     */
    @Test
    @DisplayName("★ at a line limit the last line ends in …; with no limit every word is kept, in order")
    void neverDropsWordsSilently() {
        FontMetrics fm = metrics();
        String line = ProfileOverlay.SAFE_CARD_LINE;
        List<String> two = ProfileOverlay.wrap(fm, line, 310, 2);
        List<String> all = ProfileOverlay.wrap(fm, line, 310, 0);
        assertTrue(all.size() > 2, "control: at this width the Safe line needs more than two lines: " + all);
        assertEquals(2, two.size());
        assertTrue(two.get(1).endsWith("…"), "the cut is visible: " + two);
        assertTrue(fm.stringWidth(two.get(1)) <= 310, "and still fits");
        assertEquals(line, joined(all), "with no limit, every word, in order");
        for (String l : all) assertTrue(fm.stringWidth(l) <= 310 || !l.contains(" "), "each line fits: " + l);
        List<String> roomy = ProfileOverlay.wrap(fm, line, 310, all.size());
        assertEquals(all, roomy, "with room for every line, nothing is ellipsised");
    }

    @Test
    @DisplayName("every card's line fits the card: its preferred height has room for every line at its width")
    void eachCardIsAsTallAsItsText() {
        ProfileOverlay o = new ProfileOverlay();
        FontMetrics fm = metrics();
        for (AssistantProfile p : ProfileOverlay.CARDS) {
            var card = o.card(p);
            card.setSize(330, card.getPreferredSize().height);
            int lines = ProfileOverlay.wrap(card.getFontMetrics(card.getFont().deriveFont(card.getFont().getSize2D() - 1f)),
                    ProfileOverlay.cardLine(p), 330 - 2 * ProfileOverlay.TEXT_INSET, 0).size();
            assertTrue(card.getPreferredSize().height >= lines * fm.getHeight(), p + " has room for " + lines + " lines");
            assertEquals(ProfileOverlay.cardLine(p), card.getAccessibleContext().getAccessibleDescription(),
                    "the whole line is the card's description, whatever is drawn");
        }
    }
}
