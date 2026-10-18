package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Robot re-walk #1 (2026-10-18), on the REAL generated processor: after the first answer, a spotlight on the topology
 * switched the canvas from the docked assistant to the Topology tab, and nothing said where the answer had gone.
 * {@code assistantLoop} now decides the way back: while a spotlight has the canvas on another tab, the answer is
 * "behind" it and the canvas offers "↩ Back to the answer".
 */
class AnswerAwayTest {

    /** ★ Witness: make {@code onCanvasLeftAssistant} return false without recording the tab — nothing is offered. */
    @Test
    @DisplayName("★ docked: a spotlight that took the canvas to Topology puts the answer behind it, until the tab shows again")
    void dockedAwayAndBack() {
        SessionDriver d = new SessionDriver(new FakeSessionAdapter());
        assertFalse(d.snapshot().assistant().answerBehindTab());
        d.post(new SessionEvents.CanvasLeftAssistant("Topology"));
        assertTrue(d.snapshot().assistant().answerBehindTab(), "★ the way back is offered");
        assertEquals("Topology", d.snapshot().assistant().away());
        d.post(new SessionEvents.AssistantTabShown("test"));
        assertFalse(d.snapshot().assistant().answerBehindTab(), "back at the answer: nothing more to offer");
    }

    /** ★ Witness: drop the docked check — a popped-out answer is said to be behind a tab it is not in. */
    @Test
    @DisplayName("★ popped out, the answer never moved: nothing is offered; docking or popping out takes the offer down")
    void poppedOutNothing() {
        SessionDriver d = new SessionDriver(new FakeSessionAdapter());
        d.post(new SessionEvents.AssistantHostRequested(false, "test"));
        d.post(new SessionEvents.CanvasLeftAssistant("Topology"));
        assertFalse(d.snapshot().assistant().answerBehindTab(), "★ its own window still shows the answer");
        d.post(new SessionEvents.AssistantHostRequested(true, "test"));
        d.post(new SessionEvents.CanvasLeftAssistant("Source"));
        assertTrue(d.snapshot().assistant().answerBehindTab());
        d.post(new SessionEvents.AssistantHostRequested(false, "test"));
        assertFalse(d.snapshot().assistant().answerBehindTab(), "popped out: the answer is in front again");
    }
}
