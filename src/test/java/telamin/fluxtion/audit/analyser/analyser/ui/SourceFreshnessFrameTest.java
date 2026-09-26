package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/**
 * Edit-loop spec §C in the real frame, through ordinary navigation — no spotlight causes a reread. The session
 * this came from saw the Topology tab's source pane keep a file that a regeneration had rewritten, even after
 * reopening the log; that pane is a second {@link SourcePanel} that filled itself only on first use.
 */
class SourceFreshnessFrameTest {

    private static final String NODE = "com.acme.node";

    private static void node(Path root, String body) throws Exception {
        Path file = root.resolve("com/acme/node.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package com.acme;\npublic class node {\n    " + body + "\n}\n");
    }

    @Test
    void aFailedTypeClickInProcessorModeShowsItsReason(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "real display required");
        Path root = Files.createDirectories(tmp.resolve("src"));
        String processor = "com.acme.Processor";
        String code = "package com.acme;\nimport com.acme.C;\npublic class Processor {\n"
                + "    public Object build() { return new C(); }\n}\n";
        Path file = root.resolve("com/acme/Processor.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, code);
        try (var f = new Frame(tmp)) {
            SourcePanel panel = (SourcePanel) field(f.frame, "sourcePanel");
            var outcome = new java.util.concurrent.LinkedBlockingQueue<String>();
            var clicked = new AtomicReference<String>();
            onEdt(() -> {
                ((SourceService) field(f.frame, "sourceService")).configure(List.of(root.toString()), processor);
                panel.existence = (lookup, fqn) -> {
                    clicked.set(fqn);
                    throw new java.io.UncheckedIOException(new java.io.IOException("fixture lookup failed"));
                };
                panel.decisions = d -> { if (d.startsWith("type-check")) outcome.add(d); };
                panel.setMode(SourcePanel.Mode.PROCESSOR);
                ((javax.swing.JTabbedPane) field(f.frame, "sideTabs")).setSelectedComponent(panel);
                f.frame.setSize(1500, 950); f.frame.setVisible(true); f.frame.validate();
                panel.openFqn(processor);
            });
            await(() -> panel.processorPaneText().equals(code), () -> "processor not loaded: " + panel.processorPaneText());
            Object processorPane = field(panel, "processorPane");
            Object nodePane = field(panel, "nodePane");
            var label = (javax.swing.JTextArea) field(nodePane, "label");
            onEdt(() -> {
                assertFalse(label.isShowing(), "control: Node feedback is hidden in Processor-only mode");
                var text = (javax.swing.JTextPane) field(processorPane, "text");
                assertTrue(text.isShowing(), "control: the real processor text is on screen");
                try {
                    var character = text.modelToView2D(code.indexOf("C()"));
                    assertNotNull(character, "control: the clicked type has screen geometry");
                    text.dispatchEvent(new java.awt.event.MouseEvent(text, java.awt.event.MouseEvent.MOUSE_PRESSED,
                            System.currentTimeMillis(), java.awt.event.InputEvent.CTRL_DOWN_MASK,
                            (int) character.getX() + 2, (int) character.getCenterY(), 1, false,
                            java.awt.event.MouseEvent.BUTTON1));
                } catch (javax.swing.text.BadLocationException e) {
                    throw new AssertionError("cannot locate the type click", e);
                }
            });
            assertEquals("type-check com.acme.C: failed: java.io.IOException: fixture lookup failed",
                    outcome.poll(5, TimeUnit.SECONDS), "the real text mouse listener reached the failure decision");
            assertEquals("com.acme.C", clicked.get(), "the clicked type was resolved by the processor model");
            onEdt(() -> {
                f.frame.validate();
                assertTrue(label.getText().contains("fixture lookup failed"), "the actual failure reason is retained");
                assertTrue(label.isShowing(), "a failed type click must show its reason in Processor-only mode");
                assertFalse(label.getVisibleRect().isEmpty(), "the failure label has visible bounds");
                assertTrue(((javax.swing.JComponent) processorPane).isShowing(), "the processor stays on screen");
                assertEquals(code, panel.processorPaneText(), "showing the failure does not replace processor text");
                assertFalse(panel.typeCheckPending(), "the visible failure finishes the check");
            });
        }
    }

    @Test
    void bothPanesShowTheFileAsItIsAfterTheLogIsReopenedAndOnTheNextNavigation(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "real display required");
        Path root = Files.createDirectories(tmp.resolve("src"));
        node(root, "int limit = 1;");
        Path log = Files.writeString(tmp.resolve("run.yml"), log("node"));
        Path graph = Files.writeString(tmp.resolve("demo.graphml"), graph("node"));
        try (var f = new Frame(tmp)) {
            onEdt(() -> {
                ((AppConfig) field(f.frame, "config")).sourceRoots.add(root.toString());
                ((SourceService) field(f.frame, "sourceService")).configure(List.of(root.toString()), null);
                f.frame.setSize(1500, 950); f.frame.setVisible(true); f.frame.validate();
            });
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok()); awaitLoaded(f.ex);
            assertTrue(f.ex.render("open", Map.of("graphml", graph.toString())).ok());
            SourcePanel sourceTab = (SourcePanel) field(f.frame, "sourcePanel");
            AtomicReference<SourcePanel> embedded = new AtomicReference<>();
            onEdt(() -> {
                var topology = (TopologyPanel) field(f.frame, "topologyPanel");
                topology.ensureSourcePaneVisible();
                embedded.set(topology.sourceViewer());
                sourceTab.openFqn(NODE);
                embedded.get().openFqn(NODE);
            });
            await(() -> sourceTab.nodePaneText().contains("limit = 1") && embedded.get().nodePaneText().contains("limit = 1"),
                    () -> "first read: " + sourceTab.nodePaneText() + " / " + embedded.get().nodePaneText());

            node(root, "int limit = 2;");                      // a regeneration rewrote the class
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok()); awaitLoaded(f.ex);
            await(() -> embedded.get().nodePaneText().contains("limit = 2"),
                    () -> "Topology's embedded pane after the log was reopened: " + embedded.get().nodePaneText());
            await(() -> sourceTab.nodePaneText().contains("limit = 2"),
                    () -> "Source tab after the log was reopened: " + sourceTab.nodePaneText());

            node(root, "int limit = 3;");
            onEdt(() -> embedded.get().openFqn(NODE));          // ordinary navigation to the same class
            await(() -> embedded.get().nodePaneText().contains("limit = 3"),
                    () -> "same-name navigation in the embedded pane: " + embedded.get().nodePaneText());
            onEdt(() -> sourceTab.openFqn(NODE));
            await(() -> sourceTab.nodePaneText().contains("limit = 3"),
                    () -> "same-name navigation in the Source tab: " + sourceTab.nodePaneText());
        }
    }

    private static void await(Supplier<Boolean> done, Supplier<String> why) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        AtomicReference<Boolean> ok = new AtomicReference<>(false);
        do {
            onEdt(() -> ok.set(done.get()));
            if (ok.get()) return;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        AtomicReference<String> reason = new AtomicReference<>();
        onEdt(() -> reason.set(why.get()));
        fail(reason.get());
    }
}
