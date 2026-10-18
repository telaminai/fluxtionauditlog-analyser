package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantTranscript;
import telamin.fluxtion.audit.analyser.analyser.assistant.FakeProvider;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * OA-1 in the REAL frame: the assistant panel, the session's assistantLoop, the adapter, the analyser's own provider
 * client against a loopback fake, and the frame's shared dispatcher and action executor. No log is open.
 */
class AssistantLiveFrameTest {

    @Test
    @DisplayName("Saving an OpenAI key in Settings immediately enables the assistant without another session event")
    void savingProviderSettingsRefreshesAssistant(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            f.dialogs.stop();
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            // M78 D2 (§4.1 item 26): the no-key banner belongs to the in-app assistant IN FORCE with no key; with nothing
            // set up the chip is Set up AI… and there is no banner. Put the in-app assistant in force, as Settings does
            FirstRunPolishFrameTest.inAppWithNoKeyFromSettings(f.frame);
            AssistantPanel assistant = panel(f.frame);
            onEdt(() -> {
                assertTrue(((JPanel) field(assistant, "noProvider")).isVisible(), "the one no-key banner, with Add key…");
                SwingUtilities.invokeLater(((JButton) field(assistant, "configure"))::doClick);
            });
            ConfigPanel settings = null;
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (settings == null && System.nanoTime() < until) {
                AtomicReference<ConfigPanel> found = new AtomicReference<>();
                onEdt(() -> found.set(Arrays.stream(Window.getWindows())
                        .filter(w -> w instanceof ConfigPanel && w.isShowing())
                        .map(w -> (ConfigPanel) w).findFirst().orElse(null)));
                settings = found.get();
                if (settings == null) Thread.sleep(20);
            }
            assertNotNull(settings, "the actual Configure provider action opened Settings");
            ConfigPanel dialog = settings;
            onEdt(() -> {
                dialog.selectPage("LLM");
                ((JComboBox<?>) field(dialog, "providerCombo")).setSelectedItem("openai");
                ((JTextField) field(dialog, "apiKeyField")).setText(FakeProvider.KEY);
                dialog.getRootPane().getDefaultButton().doClick();
                assertEquals(FakeProvider.KEY, ((AppConfig) field(f.frame, "config")).apiKey,
                        "control: Settings saved the fake key before the assistant refreshed");
                assertFalse(((JPanel) field(assistant, "noProvider")).isVisible(),
                        "the old no-provider banner must clear as soon as Settings is saved");
                assertTrue(assistant.sendButton().isEnabled(), "Send must become available without New chat or restart");
            });
        }
    }

    static AssistantState assistant(MainFrame f) {
        return ((SessionDriver) field(f, "session")).snapshot().assistant();
    }

    static AssistantPanel panel(MainFrame f) {
        return (AssistantPanel) field(f, "assistantPanel");
    }

    static AssistantTranscript transcript(MainFrame f) {
        return (AssistantTranscript) field(f, "assistantTranscript");
    }

    /** Point the frame's REAL provider client at the fake, with a DEMO key. */
    static void configure(MainFrame f, FakeProvider provider) throws Exception {
        onEdt(() -> {
            AppConfig cfg = (AppConfig) field(f, "config");
            cfg.apiKey = FakeProvider.KEY;
            cfg.llmProvider = "anthropic";
            cfg.llmBaseUrl = provider.url();
            cfg.assistantActionsInProcess = true;
            // M74 P2: the frame reports what can answer when its configuration changes (onConfigChanged does this);
            // a test that sets the config directly reports it the same way, so the panel renders the provider in force
            try {
                var report = MainFrame.class.getDeclaredMethod("postStartAvailability");
                report.setAccessible(true);
                report.invoke(f);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    static void awaitIdle(MainFrame f) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        AtomicReference<AssistantState> s = new AtomicReference<>();
        do {
            Thread.sleep(50);
            onEdt(() -> s.set(assistant(f)));
        } while ((s.get().busy() || "IDLE".equals(s.get().phase())) && System.nanoTime() < until);
        assertFalse(s.get().busy(), "the turn settled: " + s.get());
    }

    @Test
    @DisplayName("OA-A2/OA-A3: with no log, a live turn runs context, refuses aggregate by name, REALLY lights the spotlight, and feeds the results back")
    void aLiveTurnWithNoLog(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (FakeProvider provider = new FakeProvider();
             var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            configure(f.frame, provider);
            onEdt(() -> { f.frame.setSize(1400, 900); f.frame.setVisible(true); });   // a spotlight needs its target on screen
            provider.replies.add("Checking.\n" + FakeProvider.action("{\"action\":\"context\"}") + "\n"
                    + FakeProvider.action("{\"action\":\"aggregate\",\"params\":{\"groupBy\":\"dimension\"}}") + "\n"
                    + FakeProvider.action("{\"action\":\"spotlight\",\"params\":{\"target\":\"status\",\"caption\":\"DEMO\"}}"));
            provider.replies.add("Nothing is loaded; I lit the status line.");
            onEdt(() -> {
                panel(f.frame).composerArea().setText("What is open?");
                panel(f.frame).sendButton().doClick();
            });
            awaitIdle(f.frame);
            AtomicReference<AssistantState> s = new AtomicReference<>();
            onEdt(() -> s.set(assistant(f.frame)));
            assertEquals("COMPLETE", s.get().phase(), s.get().reason());
            List<AssistantState.Entry> actions = s.get().entries().stream()
                    .filter(e -> AssistantState.ACTION.equals(e.kind())).toList();
            assertEquals(List.of("context", "aggregate", "spotlight"), actions.stream().map(AssistantState.Entry::verb).toList());
            assertEquals(List.of("OK", "REFUSED", "OK"), actions.stream().map(AssistantState.Entry::status).toList(),
                    "context and spotlight work with no log; aggregate refuses: "
                            + transcript(f.frame).text(actions.get(2).result()));
            assertTrue(transcript(f.frame).text(actions.get(1).result()).contains("no log loaded"),
                    transcript(f.frame).text(actions.get(1).result()));
            AtomicReference<Boolean> lit = new AtomicReference<>();
            onEdt(() -> lit.set(((SpotlightOverlay) field(f.frame, "spotlight")).isLit()));
            assertTrue(lit.get(), "the spotlight the model asked for is ACTUALLY lit, not just claimed");
            assertEquals(2, provider.bodies.size(), "one request for the question, one with the results");
            Object second = telamin.fluxtion.audit.analyser.analyser.llm.Json.parse(provider.bodies.get(1));
            List<?> messages = (List<?>) telamin.fluxtion.audit.analyser.analyser.llm.Json.at(second, "messages");
            String fedBack = String.valueOf(telamin.fluxtion.audit.analyser.analyser.llm.Json.at(messages.get(messages.size() - 1), "content"));
            for (AssistantState.Entry a : actions) {
                assertTrue(fedBack.contains(transcript(f.frame).text(a.result())),
                        "each ACTUAL result went back to the model, the render success too: " + a.verb());
            }
            AtomicReference<String> draft = new AtomicReference<>();
            onEdt(() -> draft.set(panel(f.frame).draftText()));
            assertEquals("", draft.get(), "the accepted question left the composer");
            // spec §10: context.assistant is the status, with no log open — and never the conversation's words
            AtomicReference<java.util.Map<String, Object>> ctx = new AtomicReference<>();
            onEdt(() -> ctx.set(AsyncOpenInterleavingFrameTest.render(f.ex, "context", java.util.Map.of())));
            Object assistantCtx = ((java.util.Map<?, ?>) ctx.get().get("context")).get("assistant");
            assertNotNull(assistantCtx, "context.assistant is published with no log open");
            String text = String.valueOf(assistantCtx);
            assertTrue(text.contains("phase=COMPLETE") && text.contains("mode=Live assistant"), text);
            assertFalse(text.contains("What is open?") || text.contains("Nothing is loaded") || text.contains(FakeProvider.KEY),
                    "no question, answer or key in context: " + text);
        }
    }

    /**
     * ★ M74 P2 (§5.5, OD-74.14; the pop-up cut by the owner 2026-10-17): a catalogue draft is placed in the composer —
     * APPENDED after the person's own text, only the appended range highlighted — and nothing is sent until Send.
     * Witness: P1's {@code primeDraft} blank-only rule (the draft is dropped when the composer has text) fails the
     * appended assertion.
     */
    @Test
    @DisplayName("★ a catalogue draft is appended after the person's text, highlighted, and nothing is sent")
    void aDraftIsAppendedNotSent(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (FakeProvider provider = new FakeProvider();
             var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            configure(f.frame, provider);
            var compose = MainFrame.class.getDeclaredMethod("composeDraft",
                    telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.Draft.class, String.class);
            compose.setAccessible(true);
            var draft = telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.composeStart("S6",
                    telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.StartFacts.none(""));
            onEdt(() -> {
                panel(f.frame).composerArea().setText("my own question");
                try {
                    compose.invoke(f.frame, draft, "test");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            AtomicReference<String> text = new AtomicReference<>();
            AtomicReference<String> lit = new AtomicReference<>();
            onEdt(() -> {
                var area = panel(f.frame).composerArea();
                text.set(area.getText());
                var h = area.getHighlighter().getHighlights();
                try {
                    lit.set(h.length == 1 ? area.getText(h[0].getStartOffset(), h[0].getEndOffset() - h[0].getStartOffset())
                            : "highlights: " + h.length);
                } catch (javax.swing.text.BadLocationException e) {
                    throw new AssertionError(e);
                }
            });
            assertEquals("my own question\n\n" + draft.text(), text.get(), "appended after the person's own text");
            assertEquals(draft.text(), lit.get(), "exactly the appended range is highlighted");
            Thread.sleep(300);
            assertEquals(0, provider.bodies.size(), "placing sends nothing");
        }
    }
}
