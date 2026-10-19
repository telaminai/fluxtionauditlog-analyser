package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Window;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AiSetupWizardFrameTest.all;
import static telamin.fluxtion.audit.analyser.analyser.ui.AiSetupWizardFrameTest.checkScreen;
import static telamin.fluxtion.audit.analyser.analyser.ui.AiSetupWizardFrameTest.shot;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * M77-5 (spec-ai-setup-wizard.md §6.1a, T29, T30): <i>Where data goes</i> in the REAL wizard, under an isolated
 * {@code user.home} with the stub {@code claude} of {@link AiSetupWizardFrameTest}. For each of Screen 1's four cards,
 * at 640 and 520 px, light and dark: exactly one picture is on screen, under the selected card, every card keeps its
 * sentence, nothing is clipped, and the screen needs no more than 730 px. The picture is never in the start page's
 * first-run block or the docked panel (OD-77.19). Set {@code -Dm77.shots=<dir>} to paint each screen (T30).
 */
class OptionDiagramFrameTest {

    /** T29's height budget on macOS: the spec's 730 px, unchanged (measured 724 at 520 px with the BOTH picture). */
    static final int SCREEN1_MAX_MACOS = 730;
    /**
     * T29's height budget on Linux (INTEG-R11, owner 2026-10-19): 732 px, measured on the unchanged screen in the CI
     * runner's ui-frame job (run 39175442784: 520 px, BOTH, light and dark). The 8 px over macOS is the platform's font
     * metrics — taller line boxes across Screen 1's wrapped sentences — not layout: every other choice is 6–8 px taller
     * by the same cause. Shrinking a gap to buy 2 px back would tie the layout to one platform's fonts and change the
     * screen on Linux for no reader's benefit, so the budget is per platform instead; a picture added to every card
     * (the witness) still breaks both.
     */
    static final int SCREEN1_MAX_LINUX = 732;

    static int screen1Max(String osName) {
        return osName != null && osName.toLowerCase(java.util.Locale.ROOT).contains("linux") ? SCREEN1_MAX_LINUX : SCREEN1_MAX_MACOS;
    }

    /** The window's height if it were packed: what Screen 1 needs, chrome included. */
    static int neededHeight(AiSetupWindow w) {
        Insets in = w.getInsets();
        return w.getContentPane().getPreferredSize().height + in.top + in.bottom;
    }

    /**
     * T29. Witness: show all four pictures (fill the slot on every card, not only the selected one) — the one-picture
     * assertion fails, and at 520 px the height budget does too.
     */
    @Test
    @DisplayName("T29: one picture, under the selected card, Screen 1 within 730 px at 640 and 520, light and dark")
    void onePictureUnderTheSelectedCard(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        javax.swing.LookAndFeel lafBefore = javax.swing.UIManager.getLookAndFeel();
        List<String> tooTall = new java.util.ArrayList<>(), heights = new java.util.ArrayList<>();
        try (AiSetupWizardFrameTest.Robot r = new AiSetupWizardFrameTest.Robot(tmp, 1200, 800)) {
            r.await(AiSetupState::firstRun, "first run");
            onEdt(() -> assertEquals(List.of(), all(r.frame.getContentPane(), FlowDiagram.class),
                    "OD-77.19: no picture on the start page's first-run block or the docked panel"));
            for (String theme : List.of("Light", "Dark")) {
                onEdt(() -> {
                    ThemeManager.apply(theme);
                    for (Window w : Window.getWindows()) SwingUtilities.updateComponentTreeUI(w);
                });
                onEdt(() -> r.assistant().setUpAiButton().doClick());
                r.await(AiSetupState::open, "open");
                AiSetupWindow w = r.window();
                for (int width : new int[]{AiSetupWindow.DEFAULT_WIDTH, AiSetupWindow.MIN_WIDTH}) {
                    onEdt(() -> { w.setSize(width, AiSetupWindow.DEFAULT_HEIGHT); w.validate(); });
                    for (Choice c : List.of(Choice.NO_AI, Choice.CLIENT, Choice.INSIDE, Choice.BOTH)) {
                        String tag = theme.toLowerCase() + "-" + width + "-" + c.name().toLowerCase();
                        onEdt(() -> w.choiceRadio(c).doClick());
                        r.await(s -> s.choice() == c, c + " chosen");
                        onEdt(w::relayout);
                        onEdt(() -> { });
                        checkScreen(w, "Screen 1 with the picture " + tag);
                        onEdt(() -> {
                            List<FlowDiagram> shown = all(w.contentPanel(), FlowDiagram.class).stream()
                                    .filter(Component::isShowing).toList();
                            assertEquals(1, shown.size(), tag + ": exactly one picture");
                            FlowDiagram d = shown.get(0);
                            assertSame(w.diagram, d);
                            Container card = w.selectedOptionExtra().getParent();
                            assertTrue(SwingUtilities.isDescendingFrom(w.choiceRadio(c), card), tag + ": on the selected card");
                            assertTrue(SwingUtilities.isDescendingFrom(d, card), tag + ": under the selected card");
                            assertEquals(FlowDiagram.Option.of(c), d.model().option(), tag + ": the selected option's picture");
                            assertTrue(d.getHeight() >= d.heightFor(d.getWidth()), tag + ": given the height it needs");
                            assertTrue(d.getWidth() >= 340, tag + ": at least the narrowest layout's width: " + d.getWidth());
                            assertEquals(c == Choice.INSIDE || c == Choice.BOTH, w.usageLink.isShowing(),
                                    tag + ": OD-77.17's FAQ link under Claude's pictures only");
                            long sentences = all(w.contentPanel(), WrapLine.class).stream()
                                    .filter(t -> t.isShowing() && !t.getText().isBlank()).count();
                            assertTrue(sentences >= 4, tag + ": every card's sentence is visible: " + sentences);
                            for (Choice k : List.of(Choice.NO_AI, Choice.CLIENT, Choice.INSIDE, Choice.BOTH)) {
                                String sentence = AiSetupLines.sentenceFor(k);
                                assertTrue(all(w.contentPanel(), WrapLine.class).stream()
                                        .anyMatch(t -> t.isShowing() && t.getText().equals(sentence)), tag + ": " + k);
                            }
                            assertEquals("Where data goes", d.getAccessibleContext().getAccessibleName());
                            int need = neededHeight(w);
                            heights.add(tag + " " + need);
                            int max = screen1Max(System.getProperty("os.name"));
                            if (need > max) tooTall.add(tag + ": Screen 1 needs " + need + " px, more than " + max);
                        });
                        // T30: the whole screen, at the height it needs (the default window scrolls)
                        onEdt(() -> { w.setSize(width, Math.min(730, neededHeight(w))); w.relayout(); });
                        onEdt(() -> { });
                        shot(w, "m775-screen1-" + tag);
                        onEdt(() -> { w.setSize(width, AiSetupWindow.DEFAULT_HEIGHT); w.relayout(); });
                    }
                }
                onEdt(() -> w.cancelButton().doClick());
                r.await(s -> !s.open(), "cancelled");
            }
            System.out.println("M77-5 Screen 1 heights: " + heights);
            assertEquals(List.of(), tooTall, "T29: the picture never pushes Continue off a "
                    + screen1Max(System.getProperty("os.name")) + " px screen");
            onEdt(() -> {
                for (Window x : Window.getWindows()) {
                    if (x == r.window() || !x.isShowing()) continue;
                    assertEquals(List.of(), all(x, FlowDiagram.class), "OD-77.19: no picture outside the wizard");
                }
            });
        } finally {
            onEdt(() -> {
                try {
                    javax.swing.UIManager.setLookAndFeel(lafBefore);
                } catch (javax.swing.UnsupportedLookAndFeelException e) {
                    throw new AssertionError(e);
                }
                for (Window w : Window.getWindows()) SwingUtilities.updateComponentTreeUI(w);
            });
        }
    }
}
