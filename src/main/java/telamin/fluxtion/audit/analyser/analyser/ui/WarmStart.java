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
}
