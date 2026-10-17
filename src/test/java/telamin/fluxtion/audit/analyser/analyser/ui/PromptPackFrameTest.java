package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.TransferHandler;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * M74 P2 (spec §5.8 "A dropped prompt pack", owner request 2026-10-17) on a REAL frame: a Markdown pack dropped on the
 * start page replaces the numbered list under its title; "← Return to main options" restores it; a refused pack keeps the
 * list and names its one cause; and nothing of the pack is ever persisted. The drop is a DIRECT call of the page's
 * TransferHandler with a file-list Transferable — not a real drag.
 */
class PromptPackFrameTest {

    static boolean drop(StartPanel page, Path file) {
        Transferable t = new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.javaFileListFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.javaFileListFlavor.equals(f); }
            @Override public Object getTransferData(DataFlavor f) { return List.<File>of(file.toFile()); }
        };
        return page.getTransferHandler().importData(new TransferHandler.TransferSupport(page, t));
    }

    /**
     * ★ Witness: keep the dropped pack in the config (persist it as a profile family) — the "never persisted" assertion
     * finds its label in the saved config file and fails.
     */
    @Test
    @DisplayName("★ drop → the list replaced under the pack's title → Return restores it; a refusal names its line; nothing saved")
    void dropReplaceReturn(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path pack = Files.writeString(tmp.resolve("triage.md"), """
                # DEMO triage
                Asked first.

                1. **Quiet nodes** — Which nodes never logged, and why is silence not proof?
                2. **Spread** — Where does the spread jump?
                """);
        Path bad = Files.writeString(tmp.resolve("bad.md"), "# Bad\n1. Quiet nodes — no bold label\n");
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            StartPromptFrameTest.showStart(f.frame);
            StartPromptFrameTest.session(f.frame);
            StartPanel page = StartPromptFrameTest.startPanel(f.frame);
            AtomicReference<Boolean> dropped = new AtomicReference<>();
            onEdt(() -> dropped.set(drop(page, pack)));
            assertTrue(dropped.get());
            AtomicReference<List<String>> rows = new AtomicReference<>();
            AtomicReference<String> title = new AtomicReference<>();
            onEdt(() -> {
                var list = page.prompt().promptList();
                List<String> labels = new java.util.ArrayList<>();
                for (int i = 0; i < list.getModel().getSize(); i++) labels.add(list.getModel().getElementAt(i).label());
                rows.set(labels);
                title.set(page.prompt().packTitle().getText());
            });
            assertEquals(List.of("Quiet nodes", "Spread"), rows.get(), "the list is the pack's");
            assertEquals("DEMO triage", title.get());
            onEdt(() -> {
                page.prompt().choose(1);
                assertEquals("Where does the spread jump?", page.prompt().box().getText(), "a pack entry fills the box");
                assertTrue(page.prompt().returnButton().isShowing(), "the way back is on screen");
                assertTrue(page.prompt().returnButton().isFocusable(), "and reachable from the keyboard");
            });

            onEdt(() -> dropped.set(drop(page, bad)));
            AtomicReference<String> refusal = new AtomicReference<>();
            onEdt(() -> {
                refusal.set(page.prompt().packRefusal().getText());
                rows.set(List.of(String.valueOf(page.prompt().promptList().getModel().getSize())));
            });
            assertTrue(refusal.get().contains("line 2 — an entry needs a bold label"), refusal.get());
            assertEquals(List.of("2"), rows.get(), "the pack in force stays");

            onEdt(() -> page.prompt().returnButton().doClick());
            onEdt(() -> rows.set(List.of(String.valueOf(page.prompt().promptList().getModel().getSize()))));
            assertEquals(List.of("6"), rows.get(), "Return: the built-in S1–S6");

            onEdt(() -> {
                try {
                    var save = MainFrame.class.getDeclaredMethod("saveConfigQuietly");
                    save.setAccessible(true);
                    save.invoke(f.frame);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            Path config = tmp.resolve("home/.fluxtion-analyser/config");
            String saved = Files.exists(config) ? Files.readString(config) : "";
            assertFalse(saved.contains("Quiet nodes") || saved.contains("DEMO triage"), "never persisted");
        }
    }
}
