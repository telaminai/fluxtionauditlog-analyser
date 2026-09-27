package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PR #51 CI (run 36332664650, mutation shard 1 baseline): {@code SourcePanelFreshnessTest} failed with
 * {@code nodePaneText()} returning null. The accessor read the pane's document from the TEST thread while the EDT was
 * replacing it; {@code JTextComponent.getText()} turns the resulting {@code BadLocationException} into null. It lost
 * that race only on a loaded runner, in a shard whose partition had just changed.
 *
 * <p>The accessors now read on the EDT. This witness is deterministic: it holds the EDT and shows a read from another
 * thread waits for it — an accessor that reads off the EDT returns at once, and this fails.
 */
class SourcePanelPaneTextIsReadOnTheEdtTest {

    @Test
    @DisplayName("A pane's text read from another thread is read on the EDT, so it never races a replacement")
    void paneTextWaitsForTheEdt() throws Exception {
        SourcePanel[] panel = new SourcePanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new SourcePanel());
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            held.countDown();
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(held.await(5, TimeUnit.SECONDS), "control: the EDT is held");
            var node = CompletableFuture.supplyAsync(() -> panel[0].nodePaneText());
            var processor = CompletableFuture.supplyAsync(() -> panel[0].processorPaneText());
            Thread.sleep(300);
            assertFalse(node.isDone(), "nodePaneText read the document off the EDT — the race CI lost");
            assertFalse(processor.isDone(), "processorPaneText read the document off the EDT");
            release.countDown();
            assertNotNull(node.get(5, TimeUnit.SECONDS), "and once the EDT is free it answers");
            assertNotNull(processor.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }
}
