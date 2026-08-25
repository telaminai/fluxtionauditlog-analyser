package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The start page's reflow rule, pinned.
 *
 * <p>Swing is not unit-tested in this repo (headless CI, CLAUDE.md rule 4) and this is not an
 * exception to that: {@link StartPanel.CardFlow} is a {@code LayoutManager}, which is arithmetic over
 * a width. It needs no display, no peer and no event thread — only lightweight containers, which
 * construct fine headless.
 *
 * <p>It is tested because the layout it replaced failed TWICE in ways a wide screen could not show.
 * {@code FlowLayout} kept each card's preferred width and pushed the last one past the right edge;
 * {@code GridLayout} kept all three on one line and divided a narrow pane into slivers. Both looked
 * correct at 1200px, so both survived review and a screenshot, and the second was only caught by the
 * owner resizing the window. A test asks the question the wide screen cannot.
 */
class CardFlowTest {

    /**
     * Cards the size the real ones ask for, in a row padded the way the real column is.
     *
     * <p>The padding is not incidental. The first cut of this test used a bare panel and expected
     * two-up at 760px because that is what the screenshot showed — but a bare 760px panel fits three,
     * and it was the page's 26px side margins that made the difference on screen. A layout test whose
     * widths do not mean the same thing as the page's widths tests a layout nobody ships.
     */
    private static JPanel row(int count) {
        JPanel p = new JPanel(new StartPanel.CardFlow());
        p.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 26, 0, 26));
        for (int i = 0; i < count; i++) {
            JPanel card = new JPanel();
            card.setPreferredSize(new Dimension(220, 70));
            p.add(card);
        }
        return p;
    }

    private static JPanel laidOut(int count, int width) {
        JPanel p = row(count);
        p.setSize(width, 1000);
        p.doLayout();
        return p;
    }

    /** How many cards share the top row. */
    private static int firstRowCount(JPanel p) {
        int y = p.getComponent(0).getY();
        int n = 0;
        for (Component c : p.getComponents()) if (c.getY() == y) n++;
        return n;
    }

    @Test
    void threeAcrossWhenThereIsRoom() {
        assertEquals(3, firstRowCount(laidOut(3, 1100)));
    }

    @Test
    void wrapsToTwoThenOneAsThePaneNarrows() {
        assertEquals(2, firstRowCount(laidOut(3, 760)),
                "at 760 three cards cannot each be legible — two up, one under");
        assertEquals(1, firstRowCount(laidOut(3, 520)),
                "at 520 only one card fits at a readable width");
    }

    @Test
    void everyCardStaysInsideThePane() {
        // the actual reported bug: cards LOST off the right-hand edge. 220 is below the width of one
        // legible card and is here deliberately — the pane boundary wins over the legibility floor
        for (int width : new int[]{1400, 1100, 900, 760, 620, 520, 430, 380, 300, 220}) {
            JPanel p = laidOut(3, width);
            for (Component c : p.getComponents()) {
                assertTrue(c.getX() >= 0 && c.getX() + c.getWidth() <= width,
                        "card escaped the pane at width " + width + ": x=" + c.getX()
                                + " w=" + c.getWidth());
            }
        }
    }

    @Test
    void noCardIsEverNarrowerThanLegible() {
        // GridLayout's failure: it fitted everything by making everything unreadable. The one
        // exception is a pane narrower than a single card, where there is no honest answer and the
        // card takes what there is.
        for (int width : new int[]{1100, 760, 620, 520, 430, 380}) {
            for (Component c : laidOut(3, width).getComponents()) {
                assertTrue(c.getWidth() >= StartPanel.CardFlow.MIN,
                        "card squeezed to " + c.getWidth() + "px at pane width " + width);
            }
        }
    }

    @Test
    void heightGrowsWithTheWrapSoNothingIsClipped() {
        int wide = laidOut(3, 1100).getLayout()
                .preferredLayoutSize(laidOut(3, 1100)).height;
        JPanel narrow = laidOut(3, 520);
        int tall = narrow.getLayout().preferredLayoutSize(narrow).height;
        assertTrue(tall > wide,
                "three rows must ask for more height than one (" + tall + " vs " + wide
                        + ") — otherwise the wrapped cards are laid out and then clipped");
    }

    @Test
    void aPartialLastRowKeepsTheCardWidthOfAFullRow() {
        // the lone third card must not stretch across the whole pane: a card that is suddenly twice
        // the width of its siblings reads as a different KIND of thing rather than a third peer
        JPanel p = laidOut(3, 760);
        int first = p.getComponent(0).getWidth();
        int last = p.getComponent(2).getWidth();
        assertTrue(Math.abs(first - last) <= 2,
                "last-row card is " + last + "px against " + first + "px for a full-row card");
    }

    @Test
    void oneCardTakesTheWholeRow() {
        JPanel p = laidOut(1, 900);
        assertEquals(1, firstRowCount(p));
        assertTrue(p.getComponent(0).getWidth() > 800 - 52,
                "a single action — the primary one — should span its row, not sit in a third of it");
    }
}
