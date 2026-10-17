package telamin.fluxtion.audit.analyser.analyser.ui;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * M74 OD-74.1 (r5 amendment): warm start fires ONLY on the person's direct selection of the Assistant tab — a click or
 * a key on the tab strip. A tab-selection change also fires for {@code revealAssistant()}, a journey handoff and the
 * tab being restored at launch, none of which is the person's act; what tells them apart is the event being dispatched
 * when the selection changes. A mouse or key event whose source is the tab strip itself is the person's; an invocation
 * event, a menu's action event or a button elsewhere is not.
 */
final class WarmStart {

    private WarmStart() {
    }

    /** Whether {@code current} (the event being dispatched when the tab changed) is the person acting on {@code tabs}. */
    static boolean isDirectSelection(AWTEvent current, Component tabs) {
        if (tabs == null || current == null) return false;
        if (!(current instanceof MouseEvent) && !(current instanceof KeyEvent)) return false;
        return current.getSource() == tabs;
    }

    /**
     * The P1b live smoke's finding: {@code EventQueue.getCurrentEvent()} is not reliable for this. With the Aqua tab UI the
     * person's click selects the tab inside the {@code MOUSE_RELEASED} dispatch, yet the queue's current event then reads
     * as a {@code HierarchyEvent} a nested dispatch left behind — so the person's click was never recognised and warm
     * start never fired. So the decision also uses the tab strip's own input, held by {@link Tracker}: it counts only
     * while no later input or invocation has been dispatched ({@code mostRecentEventTime} still the held event's time),
     * so a programmatic reveal after the click is still not the person's act.
     *
     * @param current          {@code EventQueue.getCurrentEvent()} when the selection changed
     * @param held             the last mouse or key event on the tab strip ({@link Tracker#held()}), or null
     * @param mostRecentTime   {@code EventQueue.getMostRecentEventTime()} when the selection changed
     */
    static boolean isDirectSelection(AWTEvent current, AWTEvent held, long mostRecentTime, Component tabs) {
        if (isDirectSelection(current, tabs)) return true;
        return isDirectSelection(held, tabs) && held instanceof java.awt.event.InputEvent ie && ie.getWhen() == mostRecentTime;
    }

    /**
     * Holds the person's latest mouse or key event on the tab strip. An {@code AWTEventListener} is told of an event
     * before the component processes it, so the selection that event causes happens while it is held; it is let go once
     * the queue has moved on.
     */
    static final class Tracker implements java.awt.event.AWTEventListener {
        private final Component tabs;
        private volatile AWTEvent held;

        Tracker(Component tabs) {
            this.tabs = tabs;
        }

        @Override
        public void eventDispatched(AWTEvent e) {
            if (e.getSource() != tabs || !(e instanceof MouseEvent || e instanceof KeyEvent)) return;
            held = e;
            java.awt.EventQueue.invokeLater(() -> {
                if (held == e) held = null;
            });
        }

        AWTEvent held() {
            return held;
        }

        static final long MASK = AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK;
    }
}
