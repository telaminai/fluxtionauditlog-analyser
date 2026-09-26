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
