package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.SwingUtilities;
import java.util.ArrayDeque;

/**
 * PR #171 review (P2): frame-owned questions queued from effects and snapshot listeners are shown ONE AT A TIME.
 *
 * <p>A modal dialog runs a secondary event loop, so a second {@code invokeLater(() -> showDialog(...))} queued behind the
 * first opens INSIDE it — two questions on screen at once, the later one over the earlier. Seen with the log-offer
 * adoption while Claude Code runs: "Claude Code is running" (the held switch) and "Time-order report" (the newly
 * installed records) both showing. Every modal routed through here waits for the one before it to close, in the order
 * it was queued.
 *
 * <p>This orders the adapter's PERFORMANCE of effects the session already decided; it decides nothing (CLAUDE.md rule
 * 9). EDT only.
 */
final class ModalSequence {

    private final ArrayDeque<Runnable> waiting = new ArrayDeque<>();
    private boolean showing;

    /** Queue {@code modal} to run on the EDT after any modal of this sequence that is showing or waiting. */
    void later(Runnable modal) {
        SwingUtilities.invokeLater(() -> offer(modal));
    }

    /** EDT: run {@code modal} now if none of this sequence is showing, otherwise after the ones before it. */
    void offer(Runnable modal) {
        waiting.add(modal);
        if (showing) return;                  // a nested event loop of the one showing: it runs when that one closes
        while (!waiting.isEmpty()) {
            showing = true;
            try {
                waiting.poll().run();
            } finally {
                showing = false;
            }
        }
    }

    /** Whether a modal of this sequence is showing (tests). */
    boolean showing() {
        return showing;
    }
}
