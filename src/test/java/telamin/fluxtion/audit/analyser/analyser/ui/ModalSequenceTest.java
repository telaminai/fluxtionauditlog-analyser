package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PR #171 review (P2): a modal queued while another of the sequence shows waits for it to close. Headless — the
 * nested event loop is modelled by offering from inside the running modal, which is where a real one is pumped.
 */
class ModalSequenceTest {

    @Test
    void aModalQueuedWhileOneShowsRunsAfterItCloses() throws Exception {
        ModalSequence seq = new ModalSequence();
        List<String> order = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> seq.offer(() -> {
            order.add("restart:open");
            seq.offer(() -> order.add("time-order:open"));   // what the secondary loop would run
            order.add("restart:closed");
        }));
        assertEquals(List.of("restart:open", "restart:closed", "time-order:open"), order);
    }

    @Test
    void modalsRunInTheOrderTheyWereQueued() throws Exception {
        ModalSequence seq = new ModalSequence();
        List<String> order = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> seq.offer(() -> {
            seq.offer(() -> order.add("b"));
            seq.offer(() -> order.add("c"));
            order.add("a");
        }));
        assertEquals(List.of("a", "b", "c"), order);
    }

    @Test
    void aFailingModalDoesNotBlockTheNext() throws Exception {
        ModalSequence seq = new ModalSequence();
        List<String> order = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                seq.offer(() -> { throw new IllegalStateException("boom"); });
            } catch (IllegalStateException expected) {
                order.add("threw");
            }
            seq.offer(() -> order.add("next"));
        });
        assertEquals(List.of("threw", "next"), order);
    }
}
