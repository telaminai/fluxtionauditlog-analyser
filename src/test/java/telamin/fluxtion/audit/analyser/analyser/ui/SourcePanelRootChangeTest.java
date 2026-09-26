package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Source panel after the roots change under an unchanged processor name (found 2026-09-16: a project
 * switch left "No source to show … root searched: &lt;the previous project's root&gt;" on screen while the
 * new roots already resolved the file), and the placeholder that says where the roots came from.
 */
class SourcePanelRootChangeTest {

    private static final String FQN = "com.acme.generated.MarketProcessor";

    private static Path rootWithProcessor(Path dir) throws Exception {
        Path file = dir.resolve("src/main/java/com/acme/generated/MarketProcessor.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package com.acme.generated;\npublic class MarketProcessor {\n}\n");
        return dir.resolve("src/main/java");
    }

    @Test
    void sameProcessorName_rootsNowResolveIt_paneReReadsInsteadOfKeepingTheMiss(@TempDir Path tmp) throws Exception {
        Path oldRoot = Files.createDirectories(tmp.resolve("old/src/main/java"));   // no processor here
        Path newRoot = rootWithProcessor(tmp.resolve("new"));

        SourceService service = new SourceService();
        SourcePanel panel = new SourcePanel();
        panel.bind(service);

        service.configure(List.of(oldRoot.toString()), FQN);
        panel.showSelectedProcessor();
        settle(panel);
        assertFalse(panel.hasProcessorOpen(), "control: the old root has no such file");

        // the switch — same selected name, different roots — exactly what a project switch does
        service.configure(List.of(newRoot.toString()), FQN);
        panel.showSelectedProcessor();
        settle(panel);
        assertTrue(panel.hasProcessorOpen(),
                "the pane must re-read an unchanged name once the roots resolve it; the stale placeholder was the bug");
    }

    @Test
    void rootsChangeToOnesWithoutTheFile_paneDropsTheStaleSource(@TempDir Path tmp) throws Exception {
        Path withFile = rootWithProcessor(tmp.resolve("a"));
        Path without = Files.createDirectories(tmp.resolve("b/src/main/java"));

        SourceService service = new SourceService();
        SourcePanel panel = new SourcePanel();
        panel.bind(service);
        service.configure(List.of(withFile.toString()), FQN);
        panel.showSelectedProcessor();
        settle(panel);
        assertTrue(panel.hasProcessorOpen());

        service.configure(List.of(without.toString()), FQN);
        panel.showSelectedProcessor();
        settle(panel);
        assertFalse(panel.hasProcessorOpen(), "a file the new roots cannot see must not stay on screen as if they could");
    }

    @Test
    void placeholderListsTheRootsAndTheFrameHint() {
        Path a = Path.of("/proj/one/src/main/java");
        Path b = Path.of("/proj/two/src/main/java");
        String hint = "These roots come from the project \"one\" at\n    /proj/one\n"
                + "The open log sits inside a project whose settings are NOT in force:\n    /proj/three";

        String text = SourcePanel.nothingToShowText(FQN, List.of(a, b), hint);

        assertTrue(text.startsWith("No source to show\n\n" + FQN + "\n"));
        assertTrue(text.contains("Source roots searched:\n    " + a + "\n    " + b + "\n"), text);
        int roots = text.indexOf("Source roots searched:");
        int hintAt = text.indexOf("These roots come from the project \"one\"");
        int remedy = text.indexOf("Add one:  Sources ▸ Source roots…");
        assertTrue(roots < hintAt && hintAt < remedy, "roots, then where they came from, then how to add one:\n" + text);
        assertTrue(text.contains("NOT in force:\n    /proj/three"), text);
    }

    @Test
    void placeholderWithoutAHintOrRootsSaysSo() {
        String text = SourcePanel.nothingToShowText(FQN, List.of(), "");
        assertTrue(text.contains("No source roots are configured yet."), text);
        assertFalse(text.contains("\n\n\n"), "a blank hint adds no blank block:\n" + text);
        String single = SourcePanel.nothingToShowText(FQN, List.of(Path.of("/r")), null);
        assertTrue(single.contains("Source root searched:\n    /r\n"), single);
    }

    @Test
    void nodePaneMissingUnderBothRoots_placeholderNamesTheNewRoots(@TempDir Path tmp) throws Exception {
        Path oldRoot = Files.createDirectories(tmp.resolve("old/src/main/java"));
        Path newRoot = Files.createDirectories(tmp.resolve("new/src/main/java"));
        String nodeFqn = "com.acme.node.RiskCheck";

        SourceService service = new SourceService();
        SourcePanel panel = new SourcePanel();
        panel.bind(service);
        service.configure(List.of(oldRoot.toString()), FQN);
        panel.showSelectedProcessor();
        settle(panel);
        panel.openFqn(nodeFqn);                       // the node pane: a miss under the old roots
        settle(panel);
        assertTrue(panel.nodePaneText().contains(oldRoot.toString()), panel.nodePaneText());

        service.configure(List.of(newRoot.toString()), FQN);
        panel.showSelectedProcessor();                 // still a miss — but the roots searched changed
        settle(panel);
        String node = panel.nodePaneText();
        assertTrue(node.contains(newRoot.toString()), "review F4: the placeholder must name the roots NOW searched:\n" + node);
        assertFalse(node.contains(oldRoot.toString()), "and not the previous project's:\n" + node);
        String proc = panel.processorPaneText();
        assertTrue(proc.contains(newRoot.toString()) && !proc.contains(oldRoot.toString()), proc);
    }

    /** Review R2-F5: a configuration refresh that changes nothing must not move the reader. */
    @Test
    void unchangedHit_keepsItsCaretAcrossAConfigRefresh(@TempDir Path tmp) throws Exception {
        Path root = rootWithProcessor(tmp.resolve("p"));
        Files.writeString(root.resolve("com/acme/generated/MarketProcessor.java"),
                "package com.acme.generated;\n" + "// filler\n".repeat(200) + "public class MarketProcessor {\n}\n");
        SourceService service = new SourceService();
        SourcePanel panel = new SourcePanel();
        panel.bind(service);
        service.configure(List.of(root.toString()), FQN);
        panel.showSelectedProcessor();
        settle(panel);
        assertTrue(panel.hasProcessorOpen());
        panel.setProcessorCaretPosition(1000);

        service.configure(List.of(root.toString()), FQN);     // same roots, same processor: nothing changed
        panel.showSelectedProcessor();
        settle(panel);
        assertEquals(1000, panel.processorCaretPosition(), "an unchanged hit is not re-navigated");
    }

    /**
     * Wait for the panel's reads to land. Panes read their files off the EDT and install on it (edit-loop
     * spec §C), so a test must let that happen before looking at what they show.
     */
    static void settle(SourcePanel panel) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        do {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            if (!panel.reading()) { javax.swing.SwingUtilities.invokeAndWait(() -> { }); return; }
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        fail("source reads did not settle");
    }
}
