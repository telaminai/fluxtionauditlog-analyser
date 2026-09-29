package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.FakeProvider;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.WalkBin;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;
import static telamin.fluxtion.audit.analyser.analyser.ui.AssistantLiveFrameTest.configure;
import static telamin.fluxtion.audit.analyser.analyser.ui.AssistantLiveFrameTest.panel;

/**
 * OA-3 / OA-A12 in the real frame (spec §7): a completed live turn is OFFERED to a walk's dialogue — its question and
 * answer, never the hidden manifest or context — nothing is preselected, and adding it saves a RECORDED conversation.
 */
class ConversationEditorFrameTest {

    @Test
    @DisplayName("capture from a live chat: the visible words are offered, none preselected, and saved as recorded")
    void captureFromALiveChat(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (FakeProvider provider = new FakeProvider();
             var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            configure(f.frame, provider);
            onEdt(() -> { f.frame.setSize(1300, 850); f.frame.setVisible(true); });
            provider.replies.add("The status line says nothing is open.");
            onEdt(() -> {
                panel(f.frame).composerArea().setText("What is open right now?");
                panel(f.frame).sendButton().doClick();
            });
            AssistantLiveFrameTest.awaitIdle(f.frame);
            onEdt(() -> render(f.ex, "walk", Map.of("name", "journey", "steps", List.of(
                    Map.of("caption", "the status line", "targets", List.of(Map.of("target", "status")))))));
            onEdt(() -> f.frame.openConversationEditor("journey"));
            AtomicReference<ConversationEditor> editor = new AtomicReference<>();
            onEdt(() -> editor.set(f.frame.lastConversationEditor));
            assertNotNull(editor.get());
            var offered = ConversationEditor.candidates(AssistantLiveFrameTest.assistant(f.frame),
                    AssistantLiveFrameTest.transcript(f.frame));
            assertEquals(List.of("What is open right now?", "The status line says nothing is open."),
                    offered.stream().map(ConversationEditor.Candidate::text).toList(), "the visible words, in order");
            assertTrue(offered.stream().noneMatch(c -> c.text().contains("analyser-action") || c.text().contains("Context follows")),
                    "the hidden manifest and context are never offered");
            onEdt(() -> assertNull(editor.get().draft().conversation(), "nothing is preselected"));
            onEdt(() -> {
                for (var c : offered) editor.get().draft().addCaptured(c.role(), c.text());
                editor.get().draft().reveal(0, 1);
            });
            AtomicReference<String> refused = new AtomicReference<>();
            onEdt(() -> {
                for (java.awt.Component c : ((javax.swing.JPanel) editor.get().getContentPane()).getComponents()) {
                    if (c instanceof javax.swing.JPanel p) for (java.awt.Component b : p.getComponents()) {
                        if (b instanceof javax.swing.JButton button && "Save dialogue".equals(button.getText())) button.doClick();
                    }
                }
                refused.set(editor.get().lastRefusal);
            });
            assertNull(refused.get(), "saved");
            AtomicReference<WalkSpec> saved = new AtomicReference<>();
            onEdt(() -> saved.set(WalkBin.find(((AppConfig) field(f.frame, "config")).walks, "journey")));
            assertEquals(WalkSpec.RECORDED, saved.get().conversation().kind());
            assertEquals("t2", saved.get().steps().get(0).through());
            assertEquals("The status line says nothing is open.", saved.get().conversation().turns().get(1).text());
        }
    }
}
