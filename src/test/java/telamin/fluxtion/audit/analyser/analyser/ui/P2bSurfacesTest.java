package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue;
import telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.ProposalState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.StartPageState;

import javax.swing.UIManager;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2b (§10 P2): the surfaces render the session's decisions — the proposal review and banner, the example row, the
 * header's carried items, the hero title — and decide nothing. Headless, on the event thread.
 */
class P2bSurfacesTest {

    /** R6: Claude's words by reference, resolved by the dialog as the frame's store does. */
    static final telamin.fluxtion.audit.analyser.analyser.session.FactRefs REFS =
            new telamin.fluxtion.audit.analyser.analyser.session.FactRefs();

    static ProposalState proposal(boolean projectExists, ProposalState.Item... items) {
        return new ProposalState("p-1", REFS.put("s"), REFS.put("/work/medmon"), "…/medmon", projectExists, true,
                List.of(items), projectExists ? "NONE" : "PENDING", false, "", "", Map.of());
    }

    static ProposalState.Item item(String id, String kind, ProposalState.ItemState s) {
        return new ProposalState.Item(id, kind, REFS.put("path/" + id), 0, REFS.put("REASON " + id), s, "READABLE", "");
    }

    /** ★ Witness: a review that starts an item at Yes ({@code new JRadioButton("Yes", true)}) — the all-No assertion fails. */
    @Test
    @DisplayName("★ the review: every answer starts at No; outside or refused items have no Yes; project items wait for Create")
    void theReview() throws Exception {
        StartPromptViewTest.onEdt(() -> {
            var c = ProposalReviewDialog.content(REFS::get, proposal(false,
                    item("p-1.1", "sourceRoot", ProposalState.ItemState.PENDING),
                    item("p-1.2", "graphml", ProposalState.ItemState.PENDING),
                    item("p-1.3", "runbook", ProposalState.ItemState.OUTSIDE),
                    item("p-1.4", "logToOpen", ProposalState.ItemState.REFUSED)));
            assertEquals(List.of("p-1.1", "p-1.2"), new ArrayList<>(c.yes.keySet()), "only applicable items have a Yes");
            assertTrue(c.yes.values().stream().noneMatch(javax.swing.AbstractButton::isSelected), "every answer starts No");
            assertFalse(c.createYes.isSelected());
            assertFalse(c.yes.get("p-1.1").isEnabled(), "a source root needs a project: disabled until Create is chosen");
            assertTrue(c.yes.get("p-1.2").isEnabled(), "a GraphML opens without one");
            c.createYes.setSelected(true);
            assertTrue(c.yes.get("p-1.1").isEnabled());
            c.yes.get("p-1.1").setSelected(true);
            // scorecard R5: every row the person could answer is in the answer, Yes AND No — each becomes its own fact
            assertEquals(new ProposalReviewDialog.Answer(List.of("p-1.1"), true,
                    Map.of("create", true, "p-1.1", true, "p-1.2", false)), c.answer());
            c.yes.get("p-1.1").doClick();
            assertEquals("REASON p-1.1", c.reason.getText(), "Claude's reason, in its own labelled box");
            return null;
        });
    }

    /**
     * ★ Review M74 usability #16: plain words for each item's kind and the analyser's verdict, never the codes; and
     * Claude's reason — still labelled unverified — is visible BEFORE an answer is chosen. Witness: render
     * {@code i.kind()} and {@code i.verdict()} as before — "sourceRoot" and "DIRECTORY (12 .java files)" are on screen;
     * or show the reason only on a choice — the reason box is empty at first.
     */
    @Test
    @DisplayName("★ the review says what each item is and what the analyser found, in words; the reason shows before answering")
    void theReviewSpeaksPlainly() throws Exception {
        StartPromptViewTest.onEdt(() -> {
            var c = ProposalReviewDialog.content(REFS::get, proposal(true,
                    new ProposalState.Item("p-1.1", "sourceRoot", REFS.put("src"), 0, REFS.put("REASON one"),
                            ProposalState.ItemState.PENDING, "DIRECTORY (12 .java files)", ""),
                    new ProposalState.Item("p-1.2", "processor", 0, REFS.put("com.acme.DemoProcessor"),
                            REFS.put("REASON two"), ProposalState.ItemState.PENDING, "RESOLVES (pairing UNKNOWN)", "")));
            java.util.List<String> shown = new java.util.ArrayList<>();
            for (java.awt.Component k : TranscriptStyleTest.components(c.panel)) {
                if (k instanceof javax.swing.JLabel l && l.getText() != null) shown.add(l.getText());
                if (k instanceof javax.swing.JTextArea t && t.getText() != null) shown.add(t.getText());   // the check wraps
            }
            assertTrue(shown.contains("Source folder") && shown.contains("Processor"), shown.toString());
            assertTrue(shown.contains("found: a folder with 12 Java files"), shown.toString());
            assertTrue(shown.contains("found in the source; whether it matches the log is not known yet"), shown.toString());
            for (String code : java.util.List.of("sourceRoot", "DIRECTORY (12 .java files)", "RESOLVES", "UNKNOWN")) {
                assertTrue(shown.stream().noneMatch(t -> t.contains(code)), "no code on screen: " + code + " in " + shown);
            }
            assertEquals("REASON one", c.reason.getText(), "the first row's reason, before any answer");
            assertTrue(c.reasonLabel.getText().startsWith(ProposalReviewDialog.REASON_LABEL), c.reasonLabel.getText());
            assertTrue(c.reasonLabel.getText().contains("item 1"));
            for (var l : c.no.get("p-1.2").getFocusListeners()) {
                l.focusGained(new java.awt.event.FocusEvent(c.no.get("p-1.2"), java.awt.event.FocusEvent.FOCUS_GAINED));
            }
            assertEquals("REASON two", c.reason.getText(), "focusing a row shows its reason — still unanswered");
            assertFalse(c.yes.get("p-1.2").isSelected());
            assertEquals("Item 2, Processor: No", c.no.get("p-1.2").getAccessibleContext().getAccessibleName());
            return null;
        });
        assertEquals("found: 2048 bytes, not opened yet", ProposalReviewDialog.verdictWords("READABLE (2048 bytes, not opened)"));
        assertEquals("not a GraphML file", ProposalReviewDialog.verdictWords("NOT_GRAPHML"));
        assertEquals("Log to open", ProposalReviewDialog.kindWords("logToOpen"));
        assertEquals("Graph file", ProposalReviewDialog.kindWords("graphml"));
        assertEquals("Runbook", ProposalReviewDialog.kindWords("runbook"));
    }

    static void layoutAll(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component k : c.getComponents()) if (k instanceof java.awt.Container kc) layoutAll(kc);
    }

    /**
     * ★ Final review #13 (2026-10-17): at the dialog's default width (760 px, so 736 for its content) every Yes / No is
     * in view without scrolling, however long the paths and the analyser's checks are. Witness: the old layout — the
     * answer the LAST column ({@code COL_ANSWER = 4}, the others one to the left) — puts the answers past the visible
     * width.
     */
    @Test
    @DisplayName("★ the review at its default width shows every answer: the Yes / No column is never scrolled out of view")
    void theAnswersAreInViewAtTheDefaultWidth() throws Exception {
        StartPromptViewTest.onEdt(() -> {
            String deep = "modules/pricing/src/main/java/com/acme/demo/pricing/engine/adapters";
            var c = ProposalReviewDialog.content(REFS::get, proposal(false,
                    new ProposalState.Item("p-1.1", "sourceRoot", REFS.put(deep), 0, REFS.put("REASON one"),
                            ProposalState.ItemState.PENDING, "RESOLVES (pairing UNKNOWN)", ""),
                    new ProposalState.Item("p-1.2", "graphml", REFS.put(deep + "/demo-graph.graphml"), 0,
                            REFS.put("REASON two"), ProposalState.ItemState.PENDING, "READABLE (fit UNKNOWN until opened)",
                            "")));
            c.panel.setSize(ProposalReviewDialog.DEFAULT_WIDTH - 24, 520);
            layoutAll(c.panel);
            javax.swing.JViewport port = null;
            for (java.awt.Component k : TranscriptStyleTest.components(c.panel)) {
                if (k instanceof javax.swing.JScrollPane sp && sp.getViewport().getView() instanceof javax.swing.JPanel) {
                    port = sp.getViewport();
                    break;
                }
            }
            assertNotNull(port, "the rows' scroll pane");
            java.util.List<javax.swing.JRadioButton> answers = new java.util.ArrayList<>(c.yes.values());
            answers.addAll(c.no.values());
            answers.add(c.createYes);
            for (javax.swing.JRadioButton b : answers) {
                java.awt.Rectangle at = javax.swing.SwingUtilities.convertRectangle(b.getParent(), b.getBounds(), port);
                assertTrue(at.x >= 0 && at.x + at.width <= port.getWidth(), b.getAccessibleContext().getAccessibleName()
                        + " at " + at + " in a view " + port.getWidth() + " wide");
            }
            return null;
        });
    }

    /** ★ Witness: show the banner whenever a proposal exists — the applied one still shows it. */
    @Test
    @DisplayName("★ the banner shows only while something is the person's to answer; its words say nothing was applied")
    void theBanner() throws Exception {
        StartPromptViewTest.onEdt(() -> {
            LocalAgentPanel panel = new LocalAgentPanel(new LocalAgentTranscript());
            panel.renderProposal(proposal(true, item("p-1.1", "sourceRoot", ProposalState.ItemState.PENDING),
                    item("p-1.2", "graphml", ProposalState.ItemState.OUTSIDE)));
            assertTrue(panel.proposalBanner().isVisible());
            String words = panel.proposalText().getText();
            assertTrue(words.startsWith("Claude Code proposes 1 change to set up …/medmon"), words);
            assertTrue(words.contains("and 1 that cannot be applied") && words.contains("Nothing has been applied"), words);
            panel.renderProposal(proposal(true, item("p-1.1", "sourceRoot", ProposalState.ItemState.APPLIED)));
            assertFalse(panel.proposalBanner().isVisible(), "answered: no banner");
            panel.renderProposal(ProposalState.NONE);
            assertFalse(panel.proposalBanner().isVisible());
            return null;
        });
    }

    static LocalAgentState withExtra(SessionEvents.Billing billing, LocalAgentState.Extra x) {
        return new LocalAgentState(AssistantMode.LOCAL_AGENT, 1, 1, "READY", "", "", 0, 1, 0, 0, 0, 0, 0, 0, 0, false,
                false, false, 0, List.of(), LocalAgentState.Answer.NONE, null, 0, billing, null, null, x);
    }

    /** ★ Witness: a dollar figure on a Claude plan (drop the API_KEY condition in turnText) — the plan row shows US$. */
    @Test
    @DisplayName("★ per turn: the time always, the cost only with an API key — never a dollar figure on a Claude plan")
    void perTurnText() {
        var x = new LocalAgentState.Extra(0, null, 0, 0, 0, Map.of(4L, new LocalAgentState.Turn(30_000, 4_250)), 0);
        assertEquals("4.3 s · US$0.03", LocalAgentPanel.turnText(withExtra(SessionEvents.Billing.API_KEY, x), 4));
        assertEquals("4.3 s", LocalAgentPanel.turnText(withExtra(SessionEvents.Billing.SUBSCRIPTION, x), 4));
        assertNull(LocalAgentPanel.turnText(withExtra(SessionEvents.Billing.API_KEY, x), 5));
    }

    @Test
    @DisplayName("the diagnostics line only when a count is non-zero; the endpoint line names the host only")
    void diagnosticsAndEndpoint() {
        assertNull(LocalAgentPanel.diagnosticsText(withExtra(SessionEvents.Billing.UNKNOWN, LocalAgentState.Extra.NONE), null));
        var h = new LocalAgentTranscript.Header("m", "2.1", "", "firstParty", List.of("foo"), "/w", null, List.of(), false,
                "https://proxy.example:8443");
        String d = LocalAgentPanel.diagnosticsText(withExtra(SessionEvents.Billing.UNKNOWN,
                new LocalAgentState.Extra(0, null, 3, 1, 1, Map.of(), 0)), h);
        assertEquals("Diagnostics: 3 unknown stream events · 1 command not classified for the analyser (/foo) · 1 line "
                + "over 16 MB dropped", d);
        assertEquals("requests go to https://proxy.example:8443",
                LocalAgentPanel.endpointText(h, withExtra(SessionEvents.Billing.UNKNOWN, LocalAgentState.Extra.NONE)));
        assertNull(LocalAgentPanel.providerWords("firstParty"), "internal jargon, not shown");
        assertEquals("bedrock", LocalAgentPanel.providerWords("bedrock"));
    }

    /** ★ Witness: render an example row as a prompt row (append on choose) — choosing it would change the box. */
    @Test
    @DisplayName("★ an example entry shows its download badge; choosing it fetches nothing; only its own button asks")
    void theExampleRow() throws Exception {
        String pack = "# DEMO\n\n1. **Breach** — Where did it first breach?\n2. **DEMO example** — example: "
                + "https://github.com/example/demo/archive/0123456789abcdef0123456789abcdef01234567.zip\n";
        var parsed = PromptPack.parse(pack.getBytes(StandardCharsets.UTF_8));
        assertTrue(parsed.ok());
        List<Integer> downloads = new ArrayList<>();
        StartPromptViewTest.onEdt(() -> {
            var view = new StartPromptView(new StartPromptView.Host() {
                @Override public void press(String entryId, String text) { }
                @Override public void fallback(AskCatalogue.Fallback f, String entryId, String text) { }
                @Override public String claudeSearched() { return ""; }
                @Override public PromptPack.Pack pack(long ref) { return parsed.pack(); }
                @Override public void downloadExample(int entryIndex) { downloads.add(entryIndex); }
            });
            view.render(new StartPageState(StartPageState.Route.CLAUDE_CODE, false, null, "", 0, 7, null, 0),
                    LocalAgentState.IDLE);
            var row = view.promptList().getModel().getElementAt(1);
            assertTrue(row.label().startsWith(StartPromptView.DOWNLOAD_BADGE + " "), row.label());
            view.choose(1);
            assertEquals("", view.box().getText(), "choosing appends nothing");
            assertTrue(downloads.isEmpty(), "choosing fetches nothing");
            assertEquals(AskCatalogue.text("example.verb"), view.actionButton().getText());
            assertTrue(view.chipLabel().getText().contains("DEMO example"));
            view.actionButton().doClick();
            assertEquals(List.of(1), downloads, "the entry's own button, once");
            return null;
        });
    }

    /** ★ Witness: style the title once at construction (no recolour entry) — after a look-and-feel change it is plain. */
    @Test
    @DisplayName("★ the hero title is bold and 5 pt larger in every theme, including after the look and feel is applied")
    void heroTitleIsConsistent() throws Exception {
        StartPanel[] holder = new StartPanel[1];
        Font before = UIManager.getFont("Label.font");
        try {
        StartPromptViewTest.onEdt(() -> {
            holder[0] = new StartPanel(new StartPanel.Actions() {
                public void openDemo(java.nio.file.Path p, boolean graph) { }
                public void showTab(String n) { }
                public void openOwnLog() { }
                public void openSettings() { }
                public void openFluxtionKey() { }
                public boolean fluxtionKeyPresent() { return false; }
                public void backToRecords() { }
            }, s -> { });
            // what applying a theme after construction does: a new base font, then the tree updated
            UIManager.put("Label.font", new javax.swing.plaf.FontUIResource(before.getName(), Font.PLAIN, before.getSize() + 7));
            javax.swing.SwingUtilities.updateComponentTreeUI(holder[0]);
            return null;
        });
        StartPromptViewTest.onEdt(() -> null);                     // the queued recolour
        StartPromptViewTest.onEdt(() -> {
            Font f = holder[0].heroTitle().getFont();
            Font label = UIManager.getFont("Label.font");
            assertTrue(f.isBold(), "bold: " + f);
            assertEquals(label.getSize2D() + 5f, f.getSize2D(), 0.01f, "5 pt larger than the theme's label font now");
            return null;
        });
        } finally {
            UIManager.put("Label.font", before);
        }
        assertTrue(StartPanelTextHolder.dropLine().contains("a prompt pack (.md)"));
    }

    /** The hero's drop line, as shipped. */
    static final class StartPanelTextHolder {
        static String dropLine() throws Exception {
            String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/StartPanel.java"));
            int at = src.indexOf("Drop an .fexp bundle");
            return src.substring(at, at + 200);
        }
    }
}
