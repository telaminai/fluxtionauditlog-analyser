package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles;
import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantTranscript;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession;

import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantState;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;
import telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.QuickSwitch;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M78 D1, the quiet assistant panel (spec-quiet-assistant-panel.md §3, §4, §7), headless: what the panel shows on every
 * use is a few words, what it shows once is where it is read once, and every word comes from the snapshot.
 */
class QuietPanelTest {

    static LocalAgentState state(String phase, long ticket, List<LocalAgentState.Entry> entries,
                                 Map<Long, LocalAgentState.Turn> turns, long headerRef) {
        LocalAgentState s = new LocalAgentState(AssistantMode.LOCAL_AGENT, 1, 1, phase, "", "", ticket, 1, headerRef, 1, 0, 0,
                0, 0, 0, false, false, true, 0, entries, LocalAgentState.Answer.NONE, LocalAgentPosture.ANALYSER_ASSISTANT, 0,
                SessionEvents.Billing.SUBSCRIPTION, null, null,
                new LocalAgentState.Extra(0, null, 0, 0, 0, turns, 0, LocalAgentState.Attention.NONE));
        return LocalAgentPanelCueTest.published(s);
    }

    /**
     * ★ TQ3: "Claude · unverified" on EVERY answer, the whole {@code NOT_VERIFIED} sentence as the word's accessible
     * description and tooltip. Witnesses: draw the mark only for the first answer (or only when the answer is the first
     * of its turn) — the second answer has none and the count is 1; drop {@code setAccessibleDescription} and the
     * tooltip (a JLabel's description falls back to its tooltip) — the description assertion fails.
     */
    @Test
    @DisplayName("★ TQ3: \"Claude · unverified\" on every answer, with NOT_VERIFIED as its accessible description")
    void everyAnswerIsMarkedUnverified() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.render(List.of(
                    new TranscriptView.Item("y1", TranscriptStyle.Role.YOU, "What happened?", "", 0, 0, false),
                    new TranscriptView.Item("a1", TranscriptStyle.Role.CLAUDE, "First part.", "", 0, 0, false),
                    new TranscriptView.Item("a2", TranscriptStyle.Role.CLAUDE, "Second answer of the same turn.", "", 0, 0,
                            false)));
            List<JLabel> marks = v.unverifiedMarks();
            assertEquals(2, marks.size(), "★ one mark per answer");
            for (JLabel l : marks) {
                assertEquals(TranscriptStyle.UNVERIFIED, l.getText());
                assertEquals(TranscriptStyle.NOT_VERIFIED, l.getAccessibleContext().getAccessibleDescription(),
                        "★ the whole sentence is its description");
                assertTrue(l.getToolTipText().contains(TranscriptStyle.NOT_VERIFIED), "and its tooltip");
            }
            assertTrue(v.shownText(1).startsWith("Claude · "), "the answer is headed \"Claude · unverified\": "
                    + v.shownText(1));
            assertFalse(v.getText().contains(TranscriptStyle.NOT_VERIFIED), "the sentence is not repeated in the line");
            return null;
        });
    }

    /**
     * ★ Copy on hover or focus (OD-Q.11), and the turn time as the answer's tooltip (OD-Q.8). Witness: paint Copy always
     * (return true from {@code shown()}) — the "not shown until hovered" assertion fails.
     */
    @Test
    @DisplayName("★ an answer's Copy shows only under the pointer; its turn time is a tooltip, not a line")
    void copyOnHoverAndTheTurnTimeIsATooltip() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            LocalAgentTranscript store = new LocalAgentTranscript();
            long words = store.add(LocalAgentTranscript.Kind.ANSWER, "The finding is written.");
            long question = store.add(LocalAgentTranscript.Kind.USER, "Write it up");
            LocalAgentPanel p = new LocalAgentPanel(store);
            p.render(state("READY", 0, List.of(new LocalAgentState.Entry(question, LocalAgentState.USER, 1, "", "", 0),
                    new LocalAgentState.Entry(words, LocalAgentState.ANSWER, 1, "DONE", "", 1)),
                    Map.of(1L, new LocalAgentState.Turn(-1, 6_100)), 0));
            TranscriptView v = p.conversationView();
            assertFalse(v.getText().contains("Turn:"), "★ no turn line: " + v.getText());
            List<TranscriptView.Item> items = p.items(p.lastState());
            assertEquals("Turn: 6.1 s", items.get(1).tip(), "the time is the answer's tooltip");
            String key = items.get(1).key();
            assertEquals(1, v.buttons("Copy").size(), "the answer has its Copy");
            assertFalse(v.copyShown(key), "★ not shown until hovered or focused");
            v.hoverForTest(1);
            assertTrue(v.copyShown(key), "shown under the pointer");
            v.hoverForTest(-1);
            assertFalse(v.copyShown(key));
            return null;
        });
    }

    /**
     * ★ TQ5 (panel half): Stop REPLACES Send in the same slot only while the node's primary action is Stop; the status
     * line is the node's banner only; the composer's hint is "Draft — not sent". Witness: show Stop whatever the primary
     * (the old always-there disabled Stop) — the READY assertion fails.
     */
    @Test
    @DisplayName("★ TQ5: Stop in Send's slot only while busy; no status line without a banner; the PLAN note stands alone")
    void theConditionalsRenderTheNode() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            LocalAgentTranscript store = new LocalAgentTranscript();
            AssistantPanel a = new AssistantPanel(new AssistantTranscript());
            LocalAgentPanel p = new LocalAgentPanel(store, a.surface());
            a.setLocalAgentPanel(p);
            a.renderMode(AssistantMode.LOCAL_AGENT);
            LocalAgentState.Entry plan = new LocalAgentState.Entry(0, LocalAgentState.NOTE, 0, LocalAgentSession.PLAN,
                    "Claude Code · Safe · uses your Claude plan", 0);
            p.render(state("READY", 0, List.of(plan), Map.of(), 0));
            assertTrue(a.sendButton().isVisible() && !a.cancelButton().isVisible(), "★ READY: Send, no Stop");
            assertFalse(p.instructionLine().isVisible(), "no \"Ready\" line: the chip word says nothing");
            assertFalse(a.surface().start.isVisible(), "Start / Resume is in ⋯");
            assertTrue(a.conversationView().getText().contains("Claude Code · Safe · uses your Claude plan"));
            assertFalse(a.conversationView().getText().contains(": Claude Code · Safe"), "the PLAN note has no label");
            long q = store.add(LocalAgentTranscript.Kind.USER, "q");
            p.render(state("BUSY", 2, List.of(plan, new LocalAgentState.Entry(q, LocalAgentState.USER, 2, "", "", 0)),
                    Map.of(), 0));
            assertTrue(a.cancelButton().isVisible() && !a.sendButton().isVisible(), "★ busy: Stop in Send's slot");
            assertSame(a.sendButton().getParent(), a.cancelButton().getParent(), "the same slot");
            assertEquals(LocalAgentState.Guide.CHIP_ANSWERING, p.lastState().extra().guide().chipWord());
            assertTrue(a.profileButton().getText().contains(LocalAgentState.Guide.CHIP_ANSWERING),
                    "the control joins the node's word: " + a.profileButton().getText());
            assertEquals("Draft — not sent", telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue
                    .text("composer.hint.CLAUDE_CODE"));
            return null;
        });
    }

    /**
     * TQ7: About this session… holds what the header used to repeat — the limits, the not-local-only sentence, the folder,
     * the model and the plan — through the same pinned constants.
     */
    @Test
    @DisplayName("TQ7: About this session… shows the limits, the external-access sentence, the folder, the model and the plan")
    void aboutHoldsWhatTheHeaderRepeated() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            LocalAgentTranscript store = new LocalAgentTranscript();
            long ref = store.putHeader(LocalAgentDetailsDisclosureTest.HEADER);
            AssistantPanel a = new AssistantPanel(new AssistantTranscript());
            LocalAgentPanel p = new LocalAgentPanel(store, a.surface());
            a.setLocalAgentPanel(p);
            a.renderMode(AssistantMode.LOCAL_AGENT);
            p.render(state("READY", 0, List.of(), Map.of(), ref));
            String about = a.aboutText();
            assertTrue(about.contains(LocalAgentPanel.SAFE_LIMITS), "the limits: " + about);
            assertTrue(about.contains(AssistantProfiles.CLAUDE_CODE_SENDS), "where questions go (not local-only)");
            assertTrue(about.contains("/work/proj"), "the folder");
            assertTrue(about.contains("claude-sonnet-5"), "the model");
            assertTrue(about.contains(LocalAgentPanel.PLAN_USAGE), "the plan");
            assertTrue(about.contains(LocalAgentPanel.LOCK_WARNING), "the fork warning, when a conversation exists");
            assertTrue(java.util.Arrays.stream(a.moreMenu().getComponents()).anyMatch(c -> c instanceof javax.swing.JMenuItem m
                    && AssistantPanel.ABOUT.equals(m.getText())), "opened from ⋯");
            return null;
        });
    }

    // ---- M78 D2: one chip -------------------------------------------------------------------------------------------

    /** ⋯'s item labels, as the menu is built now. */
    static List<String> moreItems(AssistantPanel a) {
        List<String> out = new ArrayList<>();
        for (Component c : a.moreMenu().getComponents()) if (c instanceof JMenuItem m) out.add(m.getText());
        return out;
    }

    /** An in-app state in {@code phase}, carrying the node's chip word for it (as assistantLoop publishes it). */
    static AssistantState inApp(String phase) {
        AssistantState i = AssistantState.IDLE;
        return new AssistantState(1, 1, phase, 1, 0, null, List.of(), true, false, "", "", i.answer(), "",
                telamin.fluxtion.audit.analyser.analyser.session.node.AssistantLoop.chipWord(phase));
    }

    private static List<AbstractButton> buttons(java.awt.Container root) {
        List<AbstractButton> out = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof AbstractButton b) out.add(b);
            if (c instanceof java.awt.Container k) out.addAll(buttons(k));
        }
        return out;
    }

    /**
     * ★ D2 (§4.1 item 1, OD-77.3, OD-Q.12): ONE mode chip in both modes — the profile's short title, ◆ under Full, the
     * node's chip word joined ("Safe · answering… ▾", "In-app · answering… ▾") — and it is the button that opens M77-4's
     * quick switch. Witnesses: build the chip from {@code title()} instead of {@code shortTitle()} ("Safe analysis ▾") —
     * the first assertion fails; drop {@code renderControl()} from {@code render} — the in-app "answering…" assertion
     * fails; detach the quick switch from the chip — no menu is shown.
     */
    @Test
    @DisplayName("★ D2: one mode chip — Safe ▾, ◆ Full ▾, In-app ▾, Off ▾, with the node's word — and it opens the quick switch")
    void theOneModeChip() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            AssistantPanel a = new AssistantPanel(new AssistantTranscript());
            // in-app: the same chip, with assistantLoop's word
            a.renderProfiles(ProfileState.of(AssistantProfile.IN_APP));
            a.render(inApp("IDLE"), null);
            assertEquals("In-app ▾", a.profileButton().getText(), "★ the short title");
            a.render(inApp("REQUESTING"), null);
            assertEquals("In-app · answering… ▾", a.profileButton().getText(), "★ the in-app assistant's chip word");
            assertTrue(a.profileButton().getAccessibleContext().getAccessibleName().startsWith("In-app · answering…"),
                    "the name starts with the visible label (WCAG 2.5.3)");
            assertTrue(a.profileButton().getAccessibleContext().getAccessibleName().contains("In-app assistant"),
                    "and names the profile in full");
            a.render(inApp("COMPLETE"), null);
            assertEquals("In-app ▾", a.profileButton().getText(), "answered: nothing");
            a.renderProfiles(ProfileState.of(AssistantProfile.OFF));
            assertEquals("Off ▾", a.profileButton().getText());

            // Claude Code: Safe, the node's word, then ◆ Full
            LocalAgentPanel p = new LocalAgentPanel(new LocalAgentTranscript(), a.surface());
            a.setLocalAgentPanel(p);
            a.renderMode(AssistantMode.LOCAL_AGENT);
            a.renderProfiles(ProfileState.of(AssistantProfile.SAFE));
            p.render(state("READY", 0, List.of(), Map.of(), 0));
            assertEquals("Safe ▾", a.profileButton().getText(), "★ Ready: the mode word alone");
            p.render(state("BUSY", 2, List.of(), Map.of(), 0));
            assertEquals("Safe · answering… ▾", a.profileButton().getText());
            a.renderProfiles(ProfileState.of(AssistantProfile.FULL));
            p.render(LocalAgentPanelCueTest.published(LocalAgentPanelCueTest.state("READY",
                    LocalAgentPosture.full(LocalAgentPosture.FullMode.DEFAULT),
                    LocalAgentPosture.full(LocalAgentPosture.FullMode.DEFAULT), null)));
            assertEquals("◆ Full ▾", a.profileButton().getText(), "★ ◆ on the chip under Full");
            p.render(LocalAgentPanelCueTest.published(LocalAgentPanelCueTest.state("READY",
                    LocalAgentPosture.full(LocalAgentPosture.FullMode.AUTO),
                    LocalAgentPosture.full(LocalAgentPosture.FullMode.AUTO), null)));
            assertEquals("◆◆ Full ▾", a.profileButton().getText(), "◆◆ for a mode that asks least");

            // the chip IS the quick switch
            assertNull(a.quickSwitch().lastShown(), "nothing shown yet");
            a.profileButton().doClick();
            assertNotNull(a.quickSwitch().lastShown(), "★ the chip opens the quick switch");
            List<String> rows = new ArrayList<>();
            for (Component c : a.quickSwitch().lastShown().getComponents()) if (c instanceof JMenuItem m) rows.add(m.getText());
            assertEquals(QuickSwitch.items(ProfileState.of(AssistantProfile.FULL)).stream()
                    .filter(it -> it.kind() != QuickSwitch.Kind.SEPARATOR).map(QuickSwitch.Item::label).toList(), rows,
                    "its rows are M77-4's, rendered from the profile state");
            return null;
        });
    }

    /**
     * ★ D2 (§2.2, items 2–4, 23, 39, 42; M77 §14.4): ⋯ holds what is used occasionally, each item only in the mode it
     * belongs to — Claude Code's Start / Resume and Set up a project…; the in-app assistant's Copy prompt and Connect a
     * CLI assistant… — and the header has no ⓘ, no Pop out and no Copy prompt beside Send. Witnesses: offer the in-app
     * items whatever the mode (drop the {@code else if (!demoShowing)} guard so they follow Claude Code's) — the Claude
     * Code ⋯ contains Copy prompt; put Copy prompt back in the composer row — the in-app button assertion fails.
     */
    @Test
    @DisplayName("★ D2: ⋯ holds New chat, Pop out, About, Switch, then the mode's own items — Start / Set up, or Copy prompt / Connect CLI")
    void moreHoldsTheMovedItems() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            AssistantPanel a = new AssistantPanel(new AssistantTranscript());
            a.renderProfiles(ProfileState.of(AssistantProfile.IN_APP));
            a.render(inApp("IDLE"), null);
            List<String> inApp = moreItems(a);
            for (String item : List.of(AssistantSurface.NEW_CHAT, "Pop out", AssistantPanel.ABOUT, AssistantPanel.SWITCH,
                    AssistantPanel.COPY_PROMPT, AssistantPanel.CONNECT_CLI, AssistantPanel.SETTINGS)) {
                assertTrue(inApp.contains(item), "★ in-app ⋯ has " + item + ": " + inApp);
            }
            assertFalse(inApp.contains(AssistantPanel.SET_UP_PROJECT), "Set up a project is Claude Code's");
            assertTrue(inApp.stream().noneMatch(i -> i.endsWith("Claude Code")), "no Start Claude Code in-app: " + inApp);
            for (AbstractButton b : buttons(a)) {
                if (!b.isVisible() || b.getText() == null) continue;
                assertFalse(b.getText().contains("ⓘ"), "★ ⓘ is gone: About this session… is in ⋯");
                assertFalse(b.getText().equals("Copy prompt"), "★ Copy prompt is in ⋯, not beside Send");
                assertFalse(b.getText().startsWith("Pop out"), "Pop out is in ⋯ (OD-Q.10)");
                assertFalse(b.getText().startsWith("Connect a CLI"), "★ Connect a CLI assistant is in ⋯");
            }

            LocalAgentPanel p = new LocalAgentPanel(new LocalAgentTranscript(), a.surface());
            a.setLocalAgentPanel(p);
            a.renderMode(AssistantMode.LOCAL_AGENT);
            a.renderProfiles(ProfileState.of(AssistantProfile.SAFE));
            p.render(state("OFF", 0, List.of(), Map.of(), 0));
            List<String> claude = moreItems(a);
            for (String item : List.of(AssistantSurface.NEW_CHAT, "Pop out", AssistantPanel.ABOUT, AssistantPanel.SWITCH,
                    AssistantPanel.SET_UP_PROJECT, AssistantPanel.SETTINGS)) {
                assertTrue(claude.contains(item), "★ Claude Code's ⋯ has " + item + ": " + claude);
            }
            assertTrue(claude.stream().anyMatch(i -> i.endsWith("Claude Code")), "Start / Resume in ⋯: " + claude);
            assertFalse(claude.contains(AssistantPanel.COPY_PROMPT), "★ Copy prompt is the in-app mode's (§14.4)");
            assertFalse(claude.contains(AssistantPanel.CONNECT_CLI), "★ Connect a CLI assistant is the in-app mode's");
            assertFalse(((JButton) AsyncOpenInterleavingFrameTest.field(a, "configure")).isVisible()
                    && ((javax.swing.JPanel) AsyncOpenInterleavingFrameTest.field(a, "noProvider")).isVisible(),
                    "★ Configure provider is not shown under Claude Code");
            return null;
        });
    }

    /**
     * ★ D2 (§4.1 items 12, 26): the in-app assistant has no chips; with no key it is ONE conditional banner — the node's
     * sentence and Add key… — and with a provider configured there is no banner. Witnesses: show the banner whenever no
     * provider is configured (the old {@code needsKey || !configured}) — the no-provider assertion fails; put back the
     * "In-app assistant" chip — the chips assertion fails.
     */
    @Test
    @DisplayName("★ D2: no in-app chips; no key is one banner — \"No API key — nothing is sent.\" with Add key…")
    void theNoKeyBanner() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            AssistantPanel a = new AssistantPanel(new AssistantTranscript());
            a.setProviderConfigured(() -> false);
            a.renderProfiles(new ProfileState(AssistantProfile.IN_APP, AiSetup.IN_APP_NO_KEY, false, true, false));
            a.render(inApp("IDLE"), null);
            assertEquals("No API key — nothing is sent. Add key…", a.guideLine(), "★ the node's sentence and Add key…");
            assertEquals(0, a.surface().chips.getComponentCount(), "★ no in-app chips");
            assertFalse(a.sendButton().isEnabled(), "nothing is sent");
            assertTrue(ChromeCount.words(a.guideLine()) <= 8, "a short banner: " + a.guideLine());

            a.renderProfiles(ProfileState.of(AssistantProfile.OFF));
            a.render(inApp("IDLE"), null);
            assertEquals("", a.guideLine(), "Off: the limits line says it, no banner");
            a.renderProfiles(ProfileState.of(AssistantProfile.IN_APP));
            a.render(inApp("IDLE"), null);
            assertEquals("", a.guideLine(), "★ no key reported by the node: no banner");
            a.setProviderConfigured(() -> true);
            a.render(inApp("COMPLETE"), null);
            assertEquals("", a.guideLine(), "a provider configured: no banner");
            assertEquals(0, a.surface().chips.getComponentCount(), "and still no chips");
            return null;
        });
    }
}
