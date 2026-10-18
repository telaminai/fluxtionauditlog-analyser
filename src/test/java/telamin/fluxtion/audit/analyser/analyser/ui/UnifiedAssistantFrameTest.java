package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.FakeProvider;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * M74 UX (owner, 2026-10-17) in the REAL frame: the in-app assistant and Claude Code are ONE assistant panel — one
 * transcript (the safe, colourised {@link TranscriptView}), one composer, one header of chips with the mode as one
 * selector — and each mode says what it cannot do in the same place. Driven by direct dispatch (posted key events and
 * Robot presses are dropped on this Mac); the in-app turn runs the real provider client against a loopback fake.
 */
class UnifiedAssistantFrameTest {

    static <T> List<T> all(Container root, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) out.add(type.cast(c));
            if (c instanceof Container k) out.addAll(all(k, type));
        }
        return out;
    }

    static List<String> chips(AssistantPanel a) {
        return all(a.surface().chips, Chip.class).stream().map(Chip::getText).toList();
    }

    static AssistantMode mode(MainFrame f) {
        return ((SessionDriver) field(f, "session")).snapshot().localAgent().mode();
    }

    static void awaitMode(MainFrame f, AssistantMode want) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        AtomicReference<AssistantMode> m = new AtomicReference<>();
        do {
            onEdt(() -> m.set(mode(f)));
            if (m.get() == want) return;
            Thread.sleep(30);
        } while (System.nanoTime() < until);
        fail("the mode never became " + want + ": " + m.get());
    }

    /**
     * ★ One transcript and one composer, in both modes. Witness: give Claude Code its own conversation and composer again
     * (construct {@code localAgentPanel} standalone in MainFrame, as before the M74 UX step) — the identity assertions
     * fail; or render the in-app answer as plain text (the old JTextPane rendering) — the Markdown assertions fail.
     */
    @Test
    @DisplayName("★ one transcript, one composer, one header of chips: the in-app answer in safe Markdown; Claude Code in the same parts")
    void oneTranscriptOneComposerInBothModes(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (FakeProvider provider = new FakeProvider(); var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            AssistantLiveFrameTest.configure(f.frame, provider);
            onEdt(() -> { f.frame.setSize(1300, 900); f.frame.setVisible(true); });
            provider.replies.add("## Findings\nThe **first breach** is not loaded yet.\n"
                    + FakeProvider.action("{\"action\":\"context\"}"));
            provider.replies.add("- nothing is open\n- start with `analyser_context`");
            onEdt(() -> {
                AssistantPanel a = AssistantLiveFrameTest.panel(f.frame);
                a.composerArea().setText("What is open?");
                a.sendButton().doClick();
            });
            AssistantLiveFrameTest.awaitIdle(f.frame);
            onEdt(() -> {
                AssistantPanel a = AssistantLiveFrameTest.panel(f.frame);
                LocalAgentPanel agent = (LocalAgentPanel) field(f.frame, "localAgentPanel");
                assertEquals(List.of(a.conversationView()), all(a, TranscriptView.class), "ONE transcript in the panel");
                assertSame(a.conversationView(), field(agent, "view"), "and it is Claude Code's too");
                List<JTextArea> composers = all(a, JTextArea.class).stream().filter(t -> t.isEditable()).toList();
                assertEquals(List.of(a.composerArea()), composers, "ONE composer in the panel");
                assertSame(a.composerArea(), field(agent, "draft"), "and it is Claude Code's too");
                assertSame(a.sendButton(), field(agent, "send"), "one Send");
                String shown = a.conversationView().getText();
                assertTrue(shown.contains("Assistant") && shown.contains("Findings") && shown.contains("first breach"), shown);
                assertFalse(shown.contains("## Findings") || shown.contains("**first breach**"),
                        "the in-app answer is safe Markdown now, not raw: " + shown);
                assertTrue(shown.contains("analyser_context") && shown.contains("✓"),
                        "the action is a tool line with Claude Code's marks: " + shown);
                assertTrue(shown.contains("• nothing is open"), "a list is a list: " + shown);
                assertEquals("In-app assistant", chips(a).get(0), "the header's chips: " + chips(a));
                assertTrue(chips(a).contains("API key"), chips(a).toString());
                assertTrue(a.profileButton().getText().startsWith("Assistant: "), "one control for how the assistant "
                        + "works: " + a.profileButton().getText());
                assertEquals(AssistantPanel.IN_APP_LIMITS, a.limitsLine().getText(), "what in-app cannot do, said");
                assertTrue(((javax.swing.JButton) field(a, "copyPrompt")).isVisible());
                assertFalse(a.surface().start.isVisible(), "in-app has no Start");
                a.chooseModeForTest(AssistantMode.LOCAL_AGENT);                       // the person chooses Claude Code
            });
            awaitMode(f.frame, AssistantMode.LOCAL_AGENT);
            onEdt(() -> {
                AssistantPanel a = AssistantLiveFrameTest.panel(f.frame);
                assertEquals(List.of(a.conversationView()), all(a, TranscriptView.class), "still one transcript");
                assertEquals("Claude Code", chips(a).get(0), "the same chips row, Claude Code's: " + chips(a));
                assertTrue(a.surface().start.isVisible(), "Start, in the one composer's row");
                assertFalse(((javax.swing.JButton) field(a, "copyPrompt")).isVisible(), "Copy prompt is the in-app mode's");
                assertEquals(LocalAgentPanel.OFF_LIMITS.equals(a.limitsLine().getText()) || LocalAgentPanel.SAFE_LIMITS
                        .equals(a.limitsLine().getText()), true, "Claude Code's limits, in the same place: "
                        + a.limitsLine().getText());
                assertFalse(a.conversationView().getText().contains("What is open?"),
                        "Claude Code's conversation is its own: the in-app turns are not shown as Claude Code's");
                a.chooseModeForTest(AssistantMode.IN_APP);
            });
            awaitMode(f.frame, AssistantMode.IN_APP);
            onEdt(() -> {
                AssistantPanel a = AssistantLiveFrameTest.panel(f.frame);
                assertTrue(a.conversationView().getText().contains("What is open?"), "back in-app, its conversation returns");
                assertEquals("In-app assistant", chips(a).get(0));
            });
        }
    }

    /**
     * Where a mode cannot do something, the panel says so in the same place for both: no key in-app; Claude Code not
     * allowed. Witness: show the in-app limits in Claude Code mode (drop the claudeCodeShown branch of renderLimits).
     */
    @Test
    @DisplayName("each mode's limits line, in the same place: no key in-app, Claude Code not set up")
    void eachModeSaysWhatItCannotDo(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            f.dialogs.stop();
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            AtomicReference<WrapLine> line = new AtomicReference<>();
            onEdt(() -> {
                AssistantPanel a = AssistantLiveFrameTest.panel(f.frame);
                line.set(a.limitsLine());
                // owner, 2026-10-18: nothing chosen on a fresh machine — the node's one line, not three
                assertEquals(telamin.fluxtion.audit.analyser.analyser.session.node.AssistantProfileChoice.NO_ASSISTANT,
                        a.limitsLine().getText());
                a.chooseModeForTest(AssistantMode.LOCAL_AGENT);
            });
            awaitMode(f.frame, AssistantMode.LOCAL_AGENT);
            onEdt(() -> {
                AssistantPanel a = AssistantLiveFrameTest.panel(f.frame);
                assertSame(line.get(), a.limitsLine(), "the same line");
                assertNotEquals(AssistantPanel.IN_APP_NO_KEY, a.limitsLine().getText(), "Claude Code's own words there");
                // review M74 usability A2: Safe analysis says what it does; it no longer presses toward Full on every view
                assertTrue(a.limitsLine().getText().contains("Assistant ▾")
                        || a.limitsLine().getText().equals(LocalAgentPanel.SAFE_LIMITS), a.limitsLine().getText());
            });
        }
    }
}
