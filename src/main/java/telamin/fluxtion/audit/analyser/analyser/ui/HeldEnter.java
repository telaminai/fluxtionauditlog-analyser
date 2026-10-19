package telamin.fluxtion.audit.analyser.analyser.ui;

import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;

/**
 * Final review of the M74 usability pass (2026-10-17) #7: whether the Enter key is held down NOW, anywhere in the
 * application. Enter in the start page's box places a question in the assistant's composer and moves the focus there;
 * the composer also sends on Enter, so the same held key's auto-repeat must not send what was only placed. The composer
 * asks this when it gains the focus, and ignores Enter until that press is released.
 *
 * <p>It observes key events (it never consumes one) through the focus manager's dispatcher chain, so it sees a press in
 * any component. A press is taken as still held for at most {@link #HELD_MS} without a release — a release lost to a
 * window switch cannot disable Enter for good.
 */
final class HeldEnter {

    /** How long a press without its release counts as held. */
    static final long HELD_MS = 1_000;

    private static boolean installed;
    private static volatile long pressedAt = Long.MIN_VALUE;
    private static volatile boolean down;

    private HeldEnter() {
    }

    /** Observe the application's key events from now on (idempotent). */
    static synchronized void install() {
        if (installed) return;
        installed = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            observe(e);
            return false;                               // observe only: every key goes on to its component
        });
    }

    /** One key event, as the dispatcher sees it (package-private for the tests). */
    static void observe(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.VK_ENTER) return;
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            down = true;
            pressedAt = e.getWhen();
        } else if (e.getID() == KeyEvent.KEY_RELEASED) {
            down = false;
        }
    }

    /** Whether Enter is held at {@code now} (epoch millis): pressed, not released, within {@link #HELD_MS}. */
    static boolean down(long now) {
        return down && now - pressedAt <= HELD_MS;
    }

    static boolean down() {
        return down(System.currentTimeMillis());
    }

    /** For the tests: forget any press. */
    static void reset() {
        down = false;
        pressedAt = Long.MIN_VALUE;
    }
}
