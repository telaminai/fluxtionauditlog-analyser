package telamin.fluxtion.audit.analyser.analyser.ui.setup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.QuickSwitch.Item;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.QuickSwitch.Kind;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M77-4 (spec-ai-setup-wizard.md §14.3, OD-77.3): the Assistant ▾ quick switch, headless — its pure model and the menu
 * built from it. It renders {@link ProfileState} and reports a press; it decides nothing.
 *
 * <p>Each ★ test names its witness: the mutation of {@link QuickSwitch} or {@link AiSetupLines} that turns it red.
 */
class QuickSwitchTest {

    static ProfileState observed(AssistantProfile p) {
        return ProfileState.of(p);
    }

    /**
     * ★ Witness: tick a row whatever the state says (drop {@code s.profile() == p}) — every row is selected; or drop
     * {@code s.observed()} — "Safe analysis" is ticked before the frame reported anything.
     */
    @Test
    @DisplayName("★ the rows: Safe · ◆ Full… · In-app · Off · ─── · Set up AI…, the profile in force ticked, only once observed")
    void theRowsRenderTheState() {
        List<Item> none = QuickSwitch.items(ProfileState.NONE);
        assertEquals(List.of(Kind.PROFILE, Kind.PROFILE, Kind.PROFILE, Kind.PROFILE, Kind.SEPARATOR, Kind.SET_UP),
                none.stream().map(Item::kind).toList());
        assertEquals(List.of("Safe analysis", "◆ Full Claude Code…", "In-app assistant", "Off", "", "Set up AI…"),
                none.stream().map(Item::label).toList(), "★ the spec's words, in its order");
        assertTrue(none.stream().noneMatch(Item::selected), "nothing reported: nothing ticked");
        assertTrue(QuickSwitch.items(new ProfileState(AssistantProfile.SAFE, "", false, false, false)).stream()
                .noneMatch(Item::selected), "★ not observed yet: not a fact, so not ticked");

        for (AssistantProfile p : QuickSwitch.ORDER) {
            List<Item> rows = QuickSwitch.items(observed(p));
            List<AssistantProfile> ticked = rows.stream().filter(Item::selected).map(Item::profile).toList();
            assertEquals(List.of(p), ticked, "★ exactly the profile in force: " + p);
            Item row = rows.stream().filter(i -> i.profile() == p).findFirst().orElseThrow();
            assertEquals(row.label() + ", in force", row.accessibleName(), "said in words, not by a tick alone");
        }
        assertEquals("◆ Full Claude Code", QuickSwitch.items(observed(AssistantProfile.FULL)).get(1).label(),
                "★ in force, choosing Full again asks nothing: no …");
        assertEquals(AiSetupLines.QUICK_SET_UP_TIP, none.get(5).tooltip());
    }

    /** ★ Witness: keep the rows enabled while Full's confirmation shows (drop {@code !s.confirming()}). */
    @Test
    @DisplayName("★ every row is disabled while Full's confirmation is showing, as a window behind a modal is")
    void disabledWhileConfirming() {
        List<Item> rows = QuickSwitch.items(new ProfileState(AssistantProfile.SAFE, "", true, true, false));
        assertTrue(rows.stream().filter(i -> i.kind() != Kind.SEPARATOR).noneMatch(Item::enabled), "★ " + rows);
        assertTrue(QuickSwitch.items(observed(AssistantProfile.SAFE)).stream().filter(i -> i.kind() != Kind.SEPARATOR)
                .allMatch(Item::enabled));
    }

    /**
     * ★ A press reports only: the row's profile with the switch's own request id, never reused, or Set up AI….
     * Witness: report the profile in force instead of the row's ({@code host.choose(++nextRequest, s.profile())}) — the
     * profiles reported are wrong; or reuse the id — the ids are not increasing.
     */
    @Test
    @DisplayName("★ a row reports its profile with a fresh request id; Set up AI… reports itself; nothing else happens")
    void aPressOnlyReports() throws Exception {
        List<String> calls = new ArrayList<>();
        QuickSwitch q = new QuickSwitch(new QuickSwitch.Host() {
            @Override public void choose(long request, AssistantProfile p) { calls.add(request + ":" + p); }
            @Override public void setUpAi() { calls.add("setUp"); }
        });
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu m = q.menu(observed(AssistantProfile.SAFE));
            List<JMenuItem> rows = new ArrayList<>();
            for (Component c : m.getComponents()) if (c instanceof JMenuItem i) rows.add(i);
            assertEquals(5, rows.size(), "four profiles and Set up AI…");
            assertTrue(rows.get(0) instanceof JRadioButtonMenuItem rb && rb.isSelected(), "Safe is ticked");
            assertEquals(AiSetupLines.QUICK_SWITCH_NAME, m.getAccessibleContext().getAccessibleName());
            rows.get(1).doClick();
            rows.get(3).doClick();
            rows.get(4).doClick();
            q.menu(observed(AssistantProfile.OFF));
        });
        assertEquals(List.of("1:FULL", "2:OFF", "setUp"), calls, "★ reported, in order, with fresh ids");
    }

    /** The quick switch's tooltips say where words go — the cards' lines, kept (FaqSecurityContractTest pins them). */
    @Test
    @DisplayName("each profile row's tooltip says what it does and, where something is sent, that it is not local-only")
    void theTooltipsCarryTheCardsLines() {
        for (AssistantProfile p : List.of(AssistantProfile.SAFE, AssistantProfile.FULL, AssistantProfile.IN_APP)) {
            assertTrue(AiSetupLines.quickTip(p).contains("Not local-only"), p + ": " + AiSetupLines.quickTip(p));
        }
        assertTrue(AiSetupLines.QUICK_FULL_TIP.contains("You confirm a warning"), "Full says it asks first");
        assertTrue(AiSetupLines.QUICK_OFF_TIP.contains("external MCP and REST clients are not affected"),
                "Off is about the assistant in the analyser (review B6)");
    }

    /**
     * ★ Owner, 2026-10-18 (kept from the cards' layout test): with nothing set up the panel says so ONCE — the node's line —
     * not as a provider line, a limits line and a "No provider is configured" line. Witness: show the no-provider bar's
     * sentence whatever the status ({@code noProviderLine.setVisible(true)}) — a second line is found.
     */
    @Test
    @DisplayName("★ nothing set up: one status line in the panel, in the node's words")
    void oneStatusLineWhenNothingIsSetUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            telamin.fluxtion.audit.analyser.analyser.ui.AssistantPanel a =
                    new telamin.fluxtion.audit.analyser.analyser.ui.AssistantPanel(new AssistantTranscript());
            javax.swing.JPanel host = new javax.swing.JPanel(new java.awt.BorderLayout());
            host.add(a, java.awt.BorderLayout.CENTER);
            a.setRoute("no provider configured");
            a.renderProfiles(new ProfileState(AssistantProfile.NONE, AiSetupLines.PANEL_NOTHING_SET_UP, false, true, false));
            a.render(telamin.fluxtion.audit.analyser.analyser.session.AssistantState.IDLE, null);
            host.setSize(520, 700);
            host.doLayout();
            layOut(host);
            List<String> lines = new ArrayList<>();
            collect(a, a, lines);
            List<String> said = lines.stream().filter(t -> t.toLowerCase().contains("provider")
                    || t.toLowerCase().contains("nothing is sent") || t.contains("No AI set up")).toList();
            assertEquals(List.of(AiSetupLines.PANEL_NOTHING_SET_UP), said, "said once: " + lines);
        });
    }

    private static void layOut(java.awt.Container c) {
        c.doLayout();
        for (Component k : c.getComponents()) if (k instanceof java.awt.Container kc) layOut(kc);
    }

    /** Visible text, as a person reads it: labels and read-only text that are visible all the way up (no buttons). */
    private static void collect(Component c, Component root, List<String> out) {
        for (Component p = c; p != null && p != root.getParent(); p = p.getParent()) if (!p.isVisible()) return;
        if (c instanceof javax.swing.JLabel l && l.getText() != null) out.add(l.getText());
        if (c instanceof javax.swing.text.JTextComponent t && !t.isEditable()) out.add(t.getText());
        if (c instanceof java.awt.Container ct && !(c instanceof javax.swing.text.JTextComponent)
                && !(c instanceof javax.swing.AbstractButton)) {
            for (Component k : ct.getComponents()) collect(k, root, out);
        }
    }
}
