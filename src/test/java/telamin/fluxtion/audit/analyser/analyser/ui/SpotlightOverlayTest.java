package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M64.6 — the overlay's STATE, which is a list and needs no window: numbering, re-lighting in place, putting
 * one out, and re-measuring. (What it PAINTS is held by {@code SpotlightFrameTest}, on a real frame.)
 */
class SpotlightOverlayTest {

    private static final Rectangle A = new Rectangle(10, 10, 50, 20), B = new Rectangle(200, 10, 50, 20);

    private static List<String> names(SpotlightOverlay o) {
        return o.lit().stream().map(l -> l.n() + ":" + l.target()).toList();
    }

    @Test
    void lightReplaces_addKeeps_andNumbersFollowTheOrderLit() {
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.light("status", A, "first");
        o.light("detail", A, "second");
        assertEquals(List.of("1:detail"), names(o), "one at a time unless asked otherwise");

        assertEquals(2, o.add("records", B, null));
        assertEquals(List.of("1:detail", "2:records"), names(o));
        assertTrue(o.isVisible());
    }

    @Test
    void aTargetAlreadyLitIsReLitInPlace_sameNumberNewCallout_neverLitTwice() {
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.add("detail", A, "old words");
        o.add("records", B, null);

        assertEquals(1, o.add("DETAIL", B, "new words"));
        assertEquals(2, o.lit().size());
        assertEquals("new words", o.lit().get(0).caption());
        assertEquals(B, o.lit().get(0).bounds());
    }

    @Test
    void puttingOneOutDoesNotRenumberTheRest_theChatThatNamedThemHasAlreadyBeenRead() {
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.add("detail", A, null);
        o.add("records", B, null);
        o.add("status", A, null);

        assertTrue(o.remove("records"));
        assertEquals(List.of("1:detail", "3:status"), names(o));
        assertEquals(4, o.add("project", B, null), "and a new one never reuses a number that was spoken");
        assertFalse(o.remove("records"), "putting out what is not lit is not an error, just false");
    }

    /**
     * Review of M64.6, F1. The test above removes the MIDDLE member, which cannot expose the defect: the next
     * number was "current maximum + 1", so it was only REUSED when the HIGHEST member went. These remove it.
     */
    @Test
    void puttingOutTheHIGHESTNumber_doesNotFreeItForTheNextTarget_theReviewersReproduction() {
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.add("status", A, null);
        o.add("detail", B, null);

        assertTrue(o.remove("detail"));
        assertEquals(3, o.add("records", B, null), "the chat already said \"2\" about something else");
        assertEquals(List.of("1:status", "3:records"), names(o));
    }

    @Test
    void aTargetThatRemeasureRetires_doesNotFreeItsNumberEither() {
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.add("status", A, null);
        o.add("graph:note:1", B, null);

        assertEquals(List.of("graph:note:1"), o.remeasure(name -> name.equals("status") ? Optional.of(A) : Optional.empty()));
        assertEquals(3, o.add("records", B, null));
    }

    @Test
    void theNumbersRestartOnlyWhenTheSetIsReplacedOrEmptied() {
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.add("status", A, null);
        o.add("detail", B, null);
        o.light("records", A, null);
        assertEquals(List.of("1:records"), names(o), "replaced: nobody refers to the old numbers any more");

        o.add("detail", B, null);
        o.remove("records");
        o.remove("detail");
        assertEquals(1, o.add("status", A, null), "emptied one by one is emptied too");

        o.add("detail", B, null);
        o.remeasure(name -> Optional.empty());
        assertEquals(1, o.add("status", A, null), "and emptied by a re-measure");
    }

    @Test
    void remeasureMovesWhatIsStillThere_andPutsOutWhatIsNot_sayingWhich() {
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.add("detail", A, null);
        o.add("graph:note:1", B, null);

        List<String> out = o.remeasure(name -> name.equals("detail") ? Optional.of(B) : Optional.empty());

        assertEquals(List.of("graph:note:1"), out);
        assertEquals(List.of("1:detail"), names(o));
        assertEquals(B, o.lit().get(0).bounds());

        assertEquals(List.of("detail"), o.remeasure(name -> Optional.of(new Rectangle())), "no area is not there");
        assertFalse(o.isLit());
        assertFalse(o.isVisible(), "an overlay with nothing lit must not go on swallowing clicks");
    }

    @Test
    void clearingStartsTheNumbersAgain() {
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.add("detail", A, null);
        o.add("records", B, null);
        o.clearSpotlight();
        assertNull(o.cutOutOf("detail"));
        assertEquals(1, o.add("status", A, null));
    }
}
