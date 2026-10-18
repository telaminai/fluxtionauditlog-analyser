package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpLaunchCommand;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpRegistrar;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Access;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.LiveVerdict;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Registration;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AiSetupWizardFrameTest.all;
import static telamin.fluxtion.audit.analyser.analyser.ui.AiSetupWizardFrameTest.button;
import static telamin.fluxtion.audit.analyser.analyser.ui.AiSetupWizardFrameTest.checkScreen;
import static telamin.fluxtion.audit.analyser.analyser.ui.AiSetupWizardFrameTest.shot;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * M77-3 (spec-ai-setup-wizard.md §6.3, §9, §16) — Screen 2b, the two confirmations and the external-access doors in the
 * REAL frame, under an isolated {@code user.home}, with a FAKE registrar (no client CLI runs, nothing is registered
 * anywhere) and the confirmations shown modeless so the test thread stays free. The live check is driven end to end: the
 * test is the external client, calling this window's real action server with the token from the isolated endpoint file.
 *
 * <p>Set {@code -Dm77.shots=<dir>} to paint Screen 2b and both confirmations, light and dark, at 640 and 520 px.
 */
class AiSetupClientFrameTest {

    /** Records what it was asked; never starts a process. The vector carries a counter so a re-render is visible. */
    static final class FakeRegistrar extends McpRegistrar {
        int vectorCalls;
        List<List<String>> firstShown;
        final List<List<List<String>>> ran = new ArrayList<>();

        FakeRegistrar() {
            super(Map.of(), Path.of("/DEMO/home"), Optional.of(McpLaunchCommand.of(List.of("/DEMO/home/.jbang/bin/analyser"))),
                    () -> Path.of("/DEMO/scratch"), (c, f) -> {
                        throw new AssertionError("the fake starts no process: " + c);
                    });
        }

        @Override public Optional<Path> executable(boolean codex) {
            return Optional.of(Path.of(codex ? "/DEMO/home/.local/bin/codex" : "/DEMO/home/.local/bin/claude"));
        }

        @Override public List<List<String>> vectors(boolean codex, boolean replace) {
            vectorCalls++;
            List<List<String>> v = List.of(List.of("/DEMO/home/.local/bin/claude", "mcp", "add", "--scope", "user",
                    "--transport", "stdio", "fluxtion-analyser", "--", "/DEMO/home/.jbang/bin/analyser", "--mcp",
                    "v" + vectorCalls));
            if (firstShown == null) firstShown = v;
            return v;
        }

        @Override public boolean run(boolean codex, List<List<String>> vectors) {
            ran.add(vectors);
            return true;
        }

        @Override public Found check(boolean codex) {
            return Found.ABSENT;
        }
    }

    static AiSetupWizardFrameTest.Robot robot(Path tmp, FakeRegistrar fake) throws Exception {
        AiSetupWizardFrameTest.Robot r = new AiSetupWizardFrameTest.Robot(tmp, 1200, 800);
        onEdt(() -> {
            r.frame.mcpRegistrarForTest = fake;
            r.frame.launchCommandForTest = Optional.of(McpLaunchCommand.of(List.of("/DEMO/home/.jbang/bin/analyser")));
            r.frame.setupConfirmModal = false;
        });
        return r;
    }

    static SafeChoice.Asked awaitShown(Supplier<SafeChoice.Asked> asked, SafeChoice.Asked before, String what)
            throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until) {
            SafeChoice.Asked[] a = new SafeChoice.Asked[1];
            onEdt(() -> a[0] = asked.get());
            if (a[0] != null && a[0] != before && a[0].dialog().isShowing()) return a[0];
            Thread.sleep(40);
        }
        fail(what + " was not shown");
        return null;
    }

    /** The SafeChoice rule: Cancel the default at the right; the risky choice alone at the left, never default. */
    static void assertSafeByDefault(SafeChoice.Asked a, String risky) throws Exception {
        onEdt(() -> {
            assertSame(a.safeButton(), a.dialog().getRootPane().getDefaultButton(), "★ Cancel is the default (Enter)");
            assertEquals(AiSetupLines.CANCEL, a.safeButton().getText());
            assertEquals(risky, a.riskyButton().getText());
            assertFalse(a.riskyButton().isDefaultCapable(), "the risky choice is never default-capable");
            int riskyX = SwingUtilities.convertPoint(a.riskyButton(), 0, 0, a.dialog()).x;
            int safeX = SwingUtilities.convertPoint(a.safeButton(), 0, 0, a.dialog()).x;
            assertTrue(riskyX < safeX, "risky at the left, safe at the right");
        });
    }

    static void escape(SafeChoice.Asked a) throws Exception {
        onEdt(() -> a.dialog().getRootPane().getActionMap().get("safe-choice").actionPerformed(null));
    }

    static List<String> texts(Component root) {
        List<String> out = new ArrayList<>();
        if (root instanceof java.awt.Container c) {
            for (javax.swing.text.JTextComponent t : all(c, javax.swing.text.JTextComponent.class)) out.add(t.getText());
            for (javax.swing.JLabel l : all(c, javax.swing.JLabel.class)) out.add(l.getText());
        }
        return out;
    }

    /**
     * ★ T9 (frame), ★ T10 (frame), ★ T11 (end to end) and ★ T17 for Screen 2b. Your own client → 2b. The access switch
     * raises the SafeChoice (Cancel the default; Escape is Cancel and turns nothing on); confirmed, access is on. Register
     * for me shows the verbatim vector, Cancel the default, and the vector RUN is the one shown. The live check waits,
     * and a real authenticated external call to this window's server turns it ✓ with the client's claimed name. Each
     * screen fits at 640 and 520 px, light and dark. Witnesses: write {@code assistant.rest} on the switch press (the
     * "Escape turns nothing on" assertion fails); re-render the vector in the RegisterMcpEffect performer (the run gets
     * "v2"); count a call before arming in ActionServer (the check is ✓ before the test calls).
     */
    @Test
    @DisplayName("★ T9/T10/T11/T17: Screen 2b — access and registration behind SafeChoice; the live check sees a real call")
    void screenTwoBEndToEnd(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        FakeRegistrar fake = new FakeRegistrar();
        javax.swing.LookAndFeel lafBefore = javax.swing.UIManager.getLookAndFeel();
        try (AiSetupWizardFrameTest.Robot r = robot(tmp, fake)) {
            r.await(AiSetupState::firstRun, "first run");
            onEdt(() -> r.assistant().setUpAiButton().doClick());
            r.await(AiSetupState::open, "open");
            onEdt(() -> r.window().choiceRadio(Choice.CLIENT).doClick());
            r.await(s -> s.choice() == Choice.CLIENT, "chosen");
            onEdt(() -> r.window().primaryButton().doClick());
            r.await(s -> s.step() == Step.CLIENT, "★ Screen 2b, not today's dialog");
            onEdt(() -> {
                for (Window w : Window.getWindows()) {
                    assertFalse(w instanceof McpSetupDialog && w.isShowing(), "no interim dialog");
                }
                assertFalse(r.window().primaryButton().isEnabled(), "Finish needs part 2 on");
                // M77-4: M77-5's picture fills the slot, with the switch — Your own AI client, its arrow not on yet
                JComponent slot = r.window().accessDiagramSlot();
                assertTrue(slot.isShowing() && slot.getWidth() > 0, "★ the picture is on Screen 2b, with the switch");
                assertSame(r.window().accessDiagram, slot.getComponent(0));
                assertEquals(telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.Option.CLIENT,
                        r.window().accessDiagram.model().option());
                assertEquals(telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.Line.NOT_ON,
                        clientArrow(r.window()), "access is off: the client's arrow is not on");
            });

            // part 2: the switch asks; Escape on the confirmation turns nothing on
            AiSetupWindow w = r.window();
            screens(w, "screen2b-off");
            onEdt(() -> accessSwitch(w).doClick());
            SafeChoice.Asked first = awaitShown(() -> r.frame.externalAccessAsked, null, "the access confirmation");
            assertSafeByDefault(first, AiSetupLines.ACCESS_CONFIRM_TURN_ON);
            onEdt(() -> assertTrue(texts(first.dialog()).contains(AiSetupLines.ACCESS_CONFIRM_TEXT), "its facts, verbatim"));
            for (String theme : List.of("Light", "Dark")) {
                onEdt(() -> { ThemeManager.apply(theme); SwingUtilities.updateComponentTreeUI(first.dialog()); first.dialog().pack(); });
                shot(first.dialog(), "confirm-access-" + theme.toLowerCase());
            }
            onEdt(() -> { ThemeManager.apply("Light"); for (Window x : Window.getWindows()) SwingUtilities.updateComponentTreeUI(x); });
            escape(first);
            r.await(s -> s.external().access() == Access.OFF && !s.external().access().equals(Access.CONFIRMING),
                    "Escape is Cancel");
            Thread.sleep(200);
            onEdt(() -> assertFalse((Boolean) AsyncOpenInterleavingFrameTest.field(
                    AsyncOpenInterleavingFrameTest.field(r.frame, "config"), "assistantActionsRest"),
                    "★ T9: Escape turned nothing on"));
            onEdt(() -> accessSwitch(w).doClick());
            SafeChoice.Asked second = awaitShown(() -> r.frame.externalAccessAsked, first, "the access confirmation again");
            onEdt(() -> second.riskyButton().doClick());
            r.await(s -> s.external().access() == Access.ON, "★ confirmed: on");
            onEdt(() -> assertTrue(w.primaryButton().isEnabled(), "Finish is enabled once part 2 is on"));
            onEdt(() -> assertEquals(telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.Line.AUTO,
                    clientArrow(w), "★ M77-4: the picture follows the switch — the client's arrow is on"));

            // part 3: Register for me — the verbatim vector, Cancel the default; the vector run is the vector shown
            onEdt(() -> button(w.contentPanel(), AiSetupLines.REGISTER_FOR_ME).doClick());
            SafeChoice.Asked reg = awaitShown(() -> r.frame.registerAsked, null, "the registration confirmation");
            assertSafeByDefault(reg, AiSetupLines.REGISTER_BUTTON);
            String shown = McpRegistrar.display(false, fake.firstShown.get(0));
            onEdt(() -> assertTrue(all(reg.dialog().getContentPane(), JTextArea.class).stream()
                    .anyMatch(t -> t.getText().equals(shown)), "★ the exact command is shown: " + texts(reg.dialog())));
            for (String theme : List.of("Light", "Dark")) {
                onEdt(() -> { ThemeManager.apply(theme); SwingUtilities.updateComponentTreeUI(reg.dialog()); reg.dialog().pack(); });
                shot(reg.dialog(), "confirm-register-" + theme.toLowerCase());
            }
            onEdt(() -> { ThemeManager.apply("Light"); for (Window x : Window.getWindows()) SwingUtilities.updateComponentTreeUI(x); });
            onEdt(() -> reg.riskyButton().doClick());
            r.await(s -> s.external().registration() == Registration.REGISTERED, "registered");
            assertEquals(1, fake.ran.size());
            assertEquals(fake.firstShown, fake.ran.get(0), "★ T10: the run is the shown vector, never re-rendered");

            // part 4: waiting — then this test is the external client, calling the real server with this run's token
            AiSetupState waiting = r.await(s -> s.external().live().verdict() == LiveVerdict.WAITING, "armed");
            onEdt(() -> assertEquals(AiSetupLines.FINISH_WITHOUT_CHECKING, w.primaryButton().getText()));
            screens(w, "screen2b-waiting");
            assertEquals(LiveVerdict.WAITING, waiting.external().live().verdict(), "★ T11: nothing counted before the call");
            RestEndpointFile.Endpoint endpoint = RestEndpointFile.wellKnown().read();
            assertNotNull(endpoint, "the isolated home's endpoint file");
            HttpResponse<String> resp = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(endpoint.url()
                    + "/action")).header("X-Analyser-Token", endpoint.token()).header("X-Analyser-Client", "DEMO-client")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"action\":\"context\",\"params\":{}}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(resp.statusCode() == 200 || resp.statusCode() == 400, "the call reached this window");
            r.await(s -> s.external().live().verdict() == LiveVerdict.SEEN, "★ T11: seen");
            onEdt(() -> {
                List<String> words = texts(w.getContentPane());
                assertTrue(words.stream().anyMatch(t -> t.contains("A client reached this window through the bridge at")
                        && t.contains("It calls itself \"DEMO-client\".")), "★ OD-77.8: the claim, labelled: " + words);
            });
            screens(w, "screen2b-seen");
            onEdt(() -> w.primaryButton().doClick());                  // Finish
            r.await(s -> s.step() == Step.SUMMARY, "the Summary");
            onEdt(() -> assertTrue(texts(w.getContentPane()).stream().anyMatch(t -> t.startsWith(
                    "• Your own client: Claude Code · registered")), "the Summary says what was set up"));
            shot(w, "summary-client-light-640");
            onEdt(() -> w.primaryButton().doClick());                  // Done
            r.await(s -> !s.open(), "Done");
        } finally {
            onEdt(() -> {
                try {
                    javax.swing.UIManager.setLookAndFeel(lafBefore);
                } catch (javax.swing.UnsupportedLookAndFeelException e) {
                    throw new AssertionError(e);
                }
                for (Window x : Window.getWindows()) SwingUtilities.updateComponentTreeUI(x);
            });
        }
    }

    /** The picture's client → product arrow on Screen 2b, as drawn. */
    static telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.Line clientArrow(AiSetupWindow w) {
        return w.accessDiagram.model().edges().stream()
                .filter(e -> e.from() == telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.NodeId.CLIENT
                        && e.to() == telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram.NodeId.ANALYSER)
                .findFirst().orElseThrow().line();
    }

    /**
     * ★ OD-77.4 (T9, the AI menu door) and ★ OD-77.11. The AI menu's checkbox raises the same confirmation and writes
     * nothing until it is confirmed (the box follows the setting, not the press); after the wizard turned access on and
     * was cancelled, the status bar offers Turn it off — one click, no confirmation. Witness: today's menu listener
     * ({@code config.assistantActionsRest = transport.isSelected()}) — the setting is on before any answer.
     */
    @Test
    @DisplayName("★ OD-77.4/.11: the AI menu asks first; Cancel after the wizard turned access on offers Turn it off")
    void theMenuAsksAndCancelOffersTurnItOff(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (AiSetupWizardFrameTest.Robot r = robot(tmp, new FakeRegistrar())) {
            r.await(AiSetupState::firstRun, "first run");
            JCheckBoxMenuItem[] item = new JCheckBoxMenuItem[1];
            onEdt(() -> {
                for (int i = 0; i < r.frame.getJMenuBar().getMenuCount(); i++) {
                    JMenu m = r.frame.getJMenuBar().getMenu(i);
                    if (m == null || !"AI".equals(m.getText())) continue;
                    for (Component c : m.getMenuComponents()) {
                        if (c instanceof JCheckBoxMenuItem b && "External MCP/REST access".equals(b.getText())) item[0] = b;
                    }
                }
                assertNotNull(item[0], "the AI menu's checkbox");
                item[0].doClick();
            });
            SafeChoice.Asked asked = awaitShown(() -> r.frame.externalAccessAsked, null, "the menu's confirmation");
            assertSafeByDefault(asked, AiSetupLines.ACCESS_CONFIRM_TURN_ON);
            onEdt(() -> {
                assertFalse(item[0].isSelected(), "★ the box follows the setting, not the press");
                assertFalse((Boolean) AsyncOpenInterleavingFrameTest.field(AsyncOpenInterleavingFrameTest.field(r.frame,
                        "config"), "assistantActionsRest"), "★ nothing is written before the answer");
            });
            escape(asked);

            // the wizard turns it on, then is cancelled: it stays on, and Turn it off is offered
            onEdt(() -> r.assistant().setUpAiButton().doClick());
            r.await(AiSetupState::open, "open");
            onEdt(() -> r.window().choiceRadio(Choice.CLIENT).doClick());
            r.await(s -> s.choice() == Choice.CLIENT, "chosen");
            onEdt(() -> r.window().primaryButton().doClick());
            r.await(s -> s.step() == Step.CLIENT, "2b");
            onEdt(() -> accessSwitch(r.window()).doClick());
            SafeChoice.Asked on = awaitShown(() -> r.frame.externalAccessAsked, asked, "the wizard's confirmation");
            onEdt(() -> on.riskyButton().doClick());
            r.await(s -> s.external().access() == Access.ON, "on");
            onEdt(() -> r.window().cancelButton().doClick());
            r.await(s -> !s.open() && s.external().offerTurnOff(), "★ OD-77.11: offered");
            AbstractButton[] offer = new AbstractButton[1];
            onEdt(() -> {
                // the start page hides the status bar, so its AI line carries the offer; a workspace shows it in the bar
                AbstractButton line = r.start().aiSetupBlock().turnOffButton();
                offer[0] = line.isShowing() ? line : r.frame.aiSetupTurnOff;
                assertTrue(offer[0].isShowing(), "★ Turn it off is on screen");
                assertEquals(AiSetupLines.TURN_IT_OFF, offer[0].getText());
                assertEquals(AiSetupLines.ACCESS_STILL_ON, ((javax.swing.JLabel) AsyncOpenInterleavingFrameTest.field(
                        r.frame, "status")).getText(), "the closing line names what is still on");
            });
            shot(r.frame, "start-turn-it-off");
            onEdt(() -> offer[0].doClick());
            r.await(s -> s.external().access() == Access.OFF && !s.external().offerTurnOff(), "turned off, one click");
            onEdt(() -> {
                assertFalse(r.start().aiSetupBlock().turnOffButton().isShowing());
                assertFalse(r.frame.aiSetupTurnOff.isVisible());
            });
        }
    }

    /**
     * ★ §9.3 (review R1-8): the client dialog's registration confirmation is a SafeChoice now — Cancel the default, the
     * change alone at the left. Witness: put back {@code showWideRegistrationConfirmation} (an OK-default JOptionPane).
     */
    @Test
    @DisplayName("★ the client dialog's confirmations are SafeChoice: Cancel the default")
    void theClientDialogConfirmsSafely() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        boolean modal = McpSetupDialog.confirmModal;
        McpSetupDialog.confirmModal = false;
        McpSetupDialog[] d = new McpSetupDialog[1];
        try {
            onEdt(() -> {
                try {
                    var c = McpSetupDialog.class.getDeclaredConstructor(Window.class,
                            telamin.fluxtion.audit.analyser.analyser.config.AppConfig.class, Runnable.class,
                            McpSetupDialog.Target.class, java.awt.Dialog.ModalityType.class);
                    c.setAccessible(true);
                    d[0] = c.newInstance(null, new telamin.fluxtion.audit.analyser.analyser.config.AppConfig(),
                            (Runnable) () -> { }, McpSetupDialog.Target.GENERIC, java.awt.Dialog.ModalityType.MODELESS);
                    var m = McpSetupDialog.class.getDeclaredMethod("confirmRegistrationChange", String.class,
                            String.class, String.class, String.class, JTextArea.class);
                    m.setAccessible(true);
                    assertEquals(false, m.invoke(d[0], "Confirm Claude Code registration", "Add", "Claude Code will add …",
                            "Cancel means nothing is written.", new JTextArea("claude mcp add …")));
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            SafeChoice.Asked a = McpSetupDialog.lastConfirmation;
            assertNotNull(a);
            assertSafeByDefault(a, "Add");
            onEdt(() -> a.dialog().dispose());
        } finally {
            McpSetupDialog.confirmModal = modal;
            onEdt(() -> { if (d[0] != null) d[0].dispose(); });
        }
    }

    /** ★ T17 for Screen 2b: light and dark, 640 and 520 px — no clipped text, the buttons in view; top and bottom shots. */
    static void screens(AiSetupWindow w, String name) throws Exception {
        for (String theme : List.of("Light", "Dark")) {
            onEdt(() -> { ThemeManager.apply(theme); for (Window x : Window.getWindows()) SwingUtilities.updateComponentTreeUI(x); });
            for (int width : new int[]{AiSetupWindow.DEFAULT_WIDTH, AiSetupWindow.MIN_WIDTH}) {
                int height = width == AiSetupWindow.MIN_WIDTH ? AiSetupWindow.MIN_HEIGHT : AiSetupWindow.DEFAULT_HEIGHT;
                onEdt(() -> { w.setSize(width, height); w.validate(); w.relayout(); });
                onEdt(() -> { });
                checkScreen(w, name + " " + theme + " " + width);
                String tag = name + "-" + theme.toLowerCase() + "-" + width;
                javax.swing.JScrollBar bar = ((javax.swing.JScrollPane) SwingUtilities.getAncestorOfClass(
                        javax.swing.JScrollPane.class, w.contentPanel())).getVerticalScrollBar();
                onEdt(() -> bar.setValue(0));
                shot(w, tag + "-top");
                onEdt(() -> bar.setValue(bar.getMaximum()));
                onEdt(() -> { });
                shot(w, tag + "-bottom");
                onEdt(() -> bar.setValue(0));
            }
        }
        onEdt(() -> { ThemeManager.apply("Light"); for (Window x : Window.getWindows()) SwingUtilities.updateComponentTreeUI(x); });
    }

    static AbstractButton accessSwitch(AiSetupWindow w) {
        for (JCheckBox b : all(w.contentPanel(), JCheckBox.class)) {
            if (Boolean.TRUE.equals(b.getClientProperty("aiSetup.access"))) return b;
        }
        throw new AssertionError("the access switch");
    }
}
