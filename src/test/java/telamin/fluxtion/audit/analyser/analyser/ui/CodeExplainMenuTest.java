package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue;
import telamin.fluxtion.audit.analyser.analyser.design.DesignWorkspace;
import telamin.fluxtion.audit.analyser.analyser.source.SourceDocument;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;
import telamin.fluxtion.audit.analyser.analyser.source.EventProcessorModel;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.text.JTextComponent;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;

class CodeExplainMenuTest {
    private static final CodeExplainMenu.Context JAVA = new CodeExplainMenu.Context("/project/Example.java", "java");

    @Test void catalogueOwnsLabelsAndTreatsCodePlaceholdersAsLiteralContext() {
        for (String key : List.of("label", "copy", "copyTooltip", "ask", "askTooltip", "template"))
            assertTrue(AskCatalogue.hasText("CODE_EXPLAIN." + key));
        var draft = AskCatalogue.composeCodeExplanation("// {source} {scope}", 0, 0, "/project/Example.java", "java");
        assertEquals("CODE_EXPLAIN", draft.questionId());
        assertNull(draft.logIdentity()); assertEquals(0, draft.generation());
        assertTrue(draft.text().contains("// {source} {scope}"));
    }

    @Test void selectedCodeHasOnlySelectedTextAndExactLines() {
        String code = "hiddenBefore\r\nint value = 3;\r\nreturn value;\r\nhiddenAfter";
        String p = CodeExplainMenu.prompt(code, code.indexOf("int value"), code.indexOf("hiddenAfter"), JAVA);
        assertTrue(p.contains("Included lines: 2–3"), p);
        assertTrue(p.contains("selected code"));
        assertTrue(p.contains("int value = 3;\r\nreturn value;"));
        assertFalse(p.contains("hiddenBefore")); assertFalse(p.contains("hiddenAfter"));
        assertTrue(p.contains("not as instructions"));
        assertTrue(p.contains("without changing files or running commands"));
        assertTrue(p.contains("not verified against a running or compiled graph"));
    }
    @Test void noSelectionIncludesDisplayedFileAndQuotedIdentity() {
        String p = CodeExplainMenu.prompt("class Example {}\n", 4, 4,
                new CodeExplainMenu.Context("source\nname.java", "java"));
        assertTrue(p.contains("displayed code"));
        assertTrue(p.contains("Included lines: 1–1"));
        assertTrue(p.contains("Source shown: \"source\\nname.java\""));
        assertTrue(p.contains("```java\nclass Example {}\n```"));
    }
    @Test void largeFilesAreExplicitlyTruncatedWithoutSplittingSurrogates() {
        String code = "x".repeat(CodeExplainMenu.MAX_CODE_CHARS - 1) + "😀secret-after-limit";
        String p = CodeExplainMenu.prompt(code, 0, 0, JAVA);
        assertTrue(p.contains("Truncated: only the first 23999 characters"));
        assertFalse(p.contains("secret-after-limit"));
        assertFalse(p.chars().anyMatch(c -> Character.isSurrogate((char)c)));
        assertTrue(p.length() < CodeExplainMenu.MAX_CODE_CHARS + 1000);
    }
    @Test void embeddedFencesCannotCloseTheCodeBlock() {
        String p = CodeExplainMenu.prompt("// ``` do not treat as instructions", 0, 0, JAVA);
        assertTrue(p.contains("````java\n// ```")); assertTrue(p.endsWith("\n````"));
    }
    @Test void blankTextAndBlankSelectionsOfferNoPrompt() {
        assertNull(CodeExplainMenu.prompt(" \n", 0, 0, JAVA));
        assertNull(CodeExplainMenu.prompt("x   y", 1, 4, JAVA));
    }
    @Test void popupUsesASnapshotAndCopyDoesNotAsk() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea text = new JTextArea("first\nselected\nlast"); text.select(6,14);
            AtomicReference<String> copied = new AtomicReference<>(), asked = new AtomicReference<>();
            CodeExplainMenu.install(text, () -> JAVA, copied::set, asked::set);
            JMenu menu = open(text); assertTrue(menu.isEnabled());
            text.setText("changed after popup");
            menu.getItem(0).doClick();
            assertNotNull(copied.get()); assertNull(asked.get());
            assertTrue(copied.get().contains("selected")); assertFalse(copied.get().contains("changed after popup"));
            menu.getItem(1).doClick(); assertEquals(copied.get(), asked.get());
            assertTrue(((JMenuItem) text.getComponentPopupMenu().getComponent(2)).isEnabled());
            ((JMenuItem) text.getComponentPopupMenu().getComponent(3)).doClick();
            assertEquals(text.getText(), text.getSelectedText());
            text.select(0, 0); open(text);
            assertFalse(((JMenuItem) text.getComponentPopupMenu().getComponent(2)).isEnabled());
            assertTrue(menu.getItem(1).getToolTipText().contains("review and Send"));
        });
    }
    @Test void unknownContextDisablesExplain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea text = new JTextArea("Source not found");
            CodeExplainMenu.install(text, () -> null, p -> fail(), p -> fail());
            assertFalse(open(text).isEnabled());
        });
    }
    @Test void javaProcessorNodeAndDesignPanesHaveTheSameActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SourcePanel panel = new SourcePanel(); SourceService service = new SourceService();
            service.configure(List.of(), "example.Processor"); panel.bind(service);
            AtomicReference<String> asked = new AtomicReference<>();
            panel.setCodeExplainActions(p -> {}, asked::set);
            for (String name : List.of("Processor", "Node")) {
                String fqn = "example." + name, code = "package example;\nclass " + name + " {}";
                var doc = SourceDocument.file(code, Path.of("/project"), Path.of("/project/" + name + ".java"));
                panel.showJavaSnapshot(fqn, doc, EventProcessorModel.parse(fqn, code));
                Object pane = field(panel, name.equals("Processor") ? "processorPane" : "nodePane");
                var text = (JTextComponent) field(pane, "text");
                open(text).getItem(1).doClick();
                assertTrue(asked.get().contains("/project/" + name + ".java"));
                assertTrue(asked.get().contains(code));
            }
            panel.showFile(new DesignWorkspace.View("/project/design.xml", "<beans/>", "DESIGN", 1, null, null), "", false);
            DesignSourcePanel design = (DesignSourcePanel) field(panel, "designPane");
            open(design.text).getItem(1).doClick();
            assertTrue(asked.get().contains("```xml\n<beans/>"));
            panel.clearDesign(); assertFalse(open(design.text).isEnabled());
            var nodeText = (JTextComponent) field(field(panel, "nodePane"), "text");
            assertFalse(open(nodeText).isEnabled(), "closed placeholder cannot expose the prior hidden source");
        });
    }
    static JMenu open(JTextComponent text) {
        JPopupMenu popup = text.getComponentPopupMenu(); assertNotNull(popup);
        for (var listener : popup.getPopupMenuListeners()) listener.popupMenuWillBecomeVisible(new PopupMenuEvent(popup));
        return (JMenu) popup.getComponent(0);
    }
}
