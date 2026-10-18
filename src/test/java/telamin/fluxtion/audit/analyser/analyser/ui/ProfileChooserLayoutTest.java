package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;
import telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner hands-on feedback, 2026-10-18, on the profile chooser in the popped-out Assistant window (about 520 px wide):
 * "lots of clipping and non-obvious expansion required". Headless: the real assistant panel, laid out at a narrow and a
 * wide width with the cards open, and every visible piece of text measured against the room it was given.
 *
 * <p>★ Witness: the layout before the fix — the Full card's warning in a 120 px scroll strip (a nested JScrollPane), the
 * details in a CardLayout as tall as the tallest card inside a second scroll pane, two columns of cards at 520 px and
 * the composer still showing — fails the nested-scroll, columns and composer assertions.
 *
 * <p>Screenshots: run with {@code -DprofileChooser.captureDir=<dir>} to paint the chooser with FlatLaf light and dark at
 * both widths (an isolated {@code user.home} is set for the run).
 */
class ProfileChooserLayoutTest {

    static final int HEIGHT = 820;

    static List<Component> all(Container root) {
        List<Component> out = new ArrayList<>();
        for (Component c : root.getComponents()) {
            out.add(c);
            if (c instanceof Container k) out.addAll(all(k));
        }
        return out;
    }

    /** Visible here and in every parent up to {@code root}: what a person would see (headless has no isShowing). */
    static boolean visibleTo(Component c, Container root) {
        for (Component k = c; k != null && k != root; k = k.getParent()) {
            if (!k.isVisible()) return false;
        }
        return true;
    }

    static void layOut(Container root, int width) {
        root.setSize(width, HEIGHT);
        for (int pass = 0; pass < 4; pass++) {      // wrapped text needs its width before its height settles
            for (Component c : all(root)) c.invalidate();
            doLayoutTree(root);                     // headless: validate() needs a peer, so lay out top-down by hand
        }
    }

    static void doLayoutTree(Container c) {
        c.doLayout();
        for (Component k : c.getComponents()) if (k instanceof Container kc) doLayoutTree(kc);
    }

    static AssistantPanel openChooser(int width, AssistantProfile chosen) {
        AssistantPanel a = new AssistantPanel(new AssistantTranscript());
        JPanel host = new JPanel(new BorderLayout());
        host.add(a, BorderLayout.CENTER);
        a.renderProfiles(new ProfileState(AssistantProfile.NONE, true, ProfileState.FIRST_USE, 0, "", true,
                AiSetup.NO_ASSISTANT_CHOOSING, false));
        a.render(telamin.fluxtion.audit.analyser.analyser.session.AssistantState.IDLE, null);   // as the frame does
        a.profileOverlay().choose(chosen);
        layOut(host, width);
        return a;
    }

    @ParameterizedTest(name = "★ at {0} px: one scroll pane, cards sized to their text, details in full, no composer")
    @ValueSource(ints = {520, 1100})
    @DisplayName("★ the chooser is a focused view whose text is never clipped, narrow or wide")
    void nothingIsClipped(int width) throws Exception {
        TranscriptViewTest.onEdt(() -> {
            for (AssistantProfile p : ProfileOverlay.CARDS) {
                AssistantPanel a = openChooser(width, p);
                ProfileOverlay o = a.profileOverlay();
                assertTrue(visibleTo(o, a), "the cards are open");

                // one scroll pane at most, and none inside it
                List<JScrollPane> scrolls = all(o).stream().filter(JScrollPane.class::isInstance)
                        .map(JScrollPane.class::cast).toList();
                assertEquals(List.of(o.scrollPane()), scrolls, "one outer scroll pane and no nested one (" + p + ")");

                // the composer and Send are hidden; the header says one line
                assertFalse(visibleTo(a.composerRow(), a), "the composer is hidden while choosing");
                assertFalse(visibleTo(a.sendButton(), a), "and Send with it");
                assertEquals(AiSetup.NO_ASSISTANT_CHOOSING, a.limitsLine().getText(), "the one status line");
                List<String> saying = all(a).stream().filter(c -> visibleTo(c, a) && !o.isAncestorOf(c))
                        .map(ProfileChooserLayoutTest::textOf).filter(t -> t != null)
                        .filter(t -> t.toLowerCase().contains("provider") || t.contains("nothing is sent")
                                || t.contains("API key")).toList();
                assertEquals(List.of(), saying, "outside the cards, no second 'no provider' line while choosing");

                // the cards: one column narrow, two wide; each as tall as its whole line needs at its width
                List<Integer> xs = ProfileOverlay.CARDS.stream().map(k -> o.card(k).getX()).distinct().toList();
                assertEquals(width < ProfileOverlay.TWO_COLUMNS_MIN ? 1 : 2, xs.size(), "columns at " + width + ": " + xs);
                for (AssistantProfile k : ProfileOverlay.CARDS) {
                    var card = (AbstractButton) o.card(k);
                    int need = cardHeightFor(card, card.getWidth());
                    assertTrue(card.getHeight() >= need, k + " card is " + card.getHeight() + " px; its text needs " + need);
                }

                // the chosen card's details show in full, under the cards
                JComponent details = o.detailPanel(p);
                assertTrue(visibleTo(details, o), p + " details shown");
                for (AssistantProfile k : ProfileOverlay.CARDS) {
                    if (k != p) assertFalse(visibleTo(o.detailPanel(k), o), k + " details hidden while " + p + " is chosen");
                }
                Component grid = o.card(AssistantProfile.SAFE).getParent();
                assertTrue(details.getParent().getY() >= grid.getY() + grid.getHeight(), "below the cards");

                // no visible text is cut: each has at least its preferred size
                for (Component c : all(o)) {
                    if (!visibleTo(c, o) || textOf(c) == null || textOf(c).isBlank()) continue;
                    if (c.getParent() instanceof JComponent pj
                            && pj.getLayout() instanceof ProfileOverlay.CardGrid) continue;     // the cards: above
                    Dimension pref = c.getPreferredSize();
                    assertTrue(pref.width <= c.getWidth() + 1 && pref.height <= c.getHeight() + 1,
                            "clipped at " + width + " px (" + p + "): " + c.getClass().getSimpleName() + " \"" + textOf(c)
                                    + "\" needs " + pref + ", has " + c.getSize());
                }
                if (p == AssistantProfile.FULL) {
                    String all = String.join("\n", all(o).stream().filter(c -> visibleTo(c, o)).map(ProfileChooserLayoutTest::textOf)
                            .filter(t -> t != null).toList());
                    assertTrue(all.contains(ConfigPanel.FULL_POSTURE_WARNING), "the full warning is shown whole, as text");
                }
            }
            return null;
        });
        String dir = System.getProperty("profileChooser.captureDir");
        if (dir != null) capture(Path.of(dir), width);
    }

    /**
     * ★ Owner, 2026-10-18: with nothing set up and the cards shut, the panel says so ONCE — the node's line — not as a
     * provider line, a limits line and a "No provider is configured" line. Witness: show the no-provider bar's sentence
     * whatever the status ({@code noProviderLine.setVisible(true)}) — a second line is found.
     */
    @org.junit.jupiter.api.Test
    @DisplayName("★ nothing set up, cards shut: one status line, in the node's words")
    void oneStatusLineWhenNothingIsSetUp() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            AssistantPanel a = new AssistantPanel(new AssistantTranscript());
            JPanel host = new JPanel(new BorderLayout());
            host.add(a, BorderLayout.CENTER);
            a.setRoute("no provider configured");
            a.renderProfiles(new ProfileState(AssistantProfile.NONE, false, "", 0, "", false,
                    AiSetup.NO_ASSISTANT, false));
            a.render(telamin.fluxtion.audit.analyser.analyser.session.AssistantState.IDLE, null);
            layOut(host, 520);
            List<String> lines = all(a).stream().filter(c -> visibleTo(c, a) && !(c instanceof AbstractButton))  // actions stay
                    .map(ProfileChooserLayoutTest::textOf)
                    .filter(t -> t != null).filter(t -> t.toLowerCase().contains("provider")
                            || t.toLowerCase().contains("nothing is sent") || t.contains("No assistant")).toList();
            assertEquals(List.of(AiSetup.NO_ASSISTANT), lines, "said once");
            assertTrue(visibleTo(a.composerRow(), a), "the cards are shut: the composer is back");
            return null;
        });
    }

    static int cardHeightFor(AbstractButton card, int width) {
        try {
            var m = card.getClass().getDeclaredMethod("heightFor", int.class);
            m.setAccessible(true);
            return (int) m.invoke(card, width);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static String textOf(Component c) {
        if (c instanceof JTextComponent t) return t.getText();
        if (c instanceof JLabel l) return l.getText();
        if (c instanceof AbstractButton b && !(b.getParent() instanceof JComponent pj
                && pj.getLayout() instanceof ProfileOverlay.CardGrid)) return b.getText();
        return null;
    }

    /** Paint the chooser with FlatLaf, light and dark, Safe and Full chosen, under an isolated home. */
    static void capture(Path dir, int width) throws Exception {
        Files.createDirectories(dir);
        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createTempDirectory("profile-chooser-home").toString());
        try {
            for (boolean dark : new boolean[]{false, true}) {
                TranscriptViewTest.onEdt(() -> {
                    javax.swing.UIManager.setLookAndFeel(dark ? new com.formdev.flatlaf.FlatDarkLaf()
                            : new com.formdev.flatlaf.FlatLightLaf());
                    for (AssistantProfile p : List.of(AssistantProfile.SAFE, AssistantProfile.FULL)) {
                        AssistantPanel a = openChooser(width, p);
                        Container host = a.getParent();
                        BufferedImage img = new BufferedImage(width, HEIGHT, BufferedImage.TYPE_INT_RGB);
                        var g = img.createGraphics();
                        g.setColor(javax.swing.UIManager.getColor("Panel.background"));
                        g.fillRect(0, 0, width, HEIGHT);
                        host.printAll(g);
                        g.dispose();
                        javax.imageio.ImageIO.write(img, "png", dir.resolve("profile-chooser-" + width + "-"
                                + (dark ? "dark" : "light") + "-" + p.name().toLowerCase() + ".png").toFile());
                    }
                    return null;
                });
            }
        } finally {
            System.setProperty("user.home", home);
            TranscriptViewTest.onEdt(() -> {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getCrossPlatformLookAndFeelClassName());
                return null;
            });
        }
    }
}
