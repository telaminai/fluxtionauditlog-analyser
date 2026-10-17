package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.FakeProvider;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FixtureAgent;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.FactRefs;
import telamin.fluxtion.audit.analyser.analyser.session.ProposalState;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * The M74 documentation captures: the assistant panel in both modes, the profile cards, an approval dialog, the set-up
 * proposal review, Settings ▸ Assistant and the records' right-click Ask menu — in the REAL frame, with no live
 * {@code claude} and no network.
 *
 * <p>Claude Code is the test suite's {@link FixtureAgent}, replaying a capture of the real CLI that
 * {@code tools/capture-docs.py} derives from the sanitised P3 fixture (its probe words replaced by DEMO ones); the
 * in-app assistant is the test suite's loopback {@link FakeProvider} with its DEMO key. The proposal review is the real
 * dialog over a proposal of DEMO items. Run by {@code capture-docs.py --m74} under the isolated {@code user.home}, on the
 * demo fixture only; every image is taken by the harness, by window id ({@link DocShot}).
 *
 * <p>Arguments: the demo project profile, the demo log, its GraphML, the docs fixture file.
 */
public final class M74DocCapture {

    private M74DocCapture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("need: project-profile log graphml docs-fixture.jsonl");
        Path profile = Path.of(args[0]);
        Path log = Path.of(args[1]);
        Path graphml = Path.of(args[2]);
        Path fixture = Path.of(args[3]);

        // the analyser's own theme before any UI is built, as Main does
        ThemeManager.apply(new telamin.fluxtion.audit.analyser.analyser.config.ConfigStore().load().theme);
        var ctor = FixtureAgent.class.getDeclaredConstructor(Path.class, String.class);
        ctor.setAccessible(true);
        FixtureAgent agent = ctor.newInstance(fixture.getParent(), fixture.getFileName().toString());

        MainFrame[] made = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            MainFrame f = new MainFrame();
            f.localAgentLauncher = agent;
            try {
                var show = MainFrame.class.getDeclaredMethod("showWorkspace", boolean.class);
                show.setAccessible(true);
                show.invoke(f, false);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            made[0] = f;
        });
        MainFrame f = made[0];
        ActionExecutor ex = (ActionExecutor) field(f, "actionExecutor");
        onEdt(() -> { f.setBounds(60, 28, 1680, 1050); f.setVisible(true); });

        try (FakeProvider provider = new FakeProvider()) {
            // the demo project, its log and graph — the same door a person uses
            onEdt(() -> render(ex, "open", Map.of("project", profile.toString())));
            awaitProject(f);
            onEdt(() -> render(ex, "open", Map.of("log", log.toString(), "graphml", graphml.toString())));
            awaitRecords(f);
            onEdt(() -> render(ex, "goto", Map.of("recordIndex", 7, "reveal", true)));
            selectAssistantTab(f);
            AssistantLiveFrameTest.configure(f, provider);
            onEdt(() -> ((AppConfig) field(f, "config")).llmModel = "demo-model");

            // 1. the profile cards, from the header, with Safe analysis's details shown
            AssistantPanel a = AssistantLiveFrameTest.panel(f);
            onEdt(() -> a.profileButton().doClick());
            ProfileOverlayFrameTest.await(f, s -> s.open(), "the cards open");
            onEdt(() -> a.profileOverlay().card(AssistantProfile.SAFE).doClick());
            DocShot.window(f, "assistant-profile-cards.png");

            // 2. the in-app assistant: one question, an answer in safe Markdown, one tool line
            onEdt(() -> {
                a.profileOverlay().card(AssistantProfile.IN_APP).doClick();
                a.profileOverlay().applyButton().doClick();
            });
            ProfileOverlayFrameTest.await(f, s -> !s.open() && s.profile() == AssistantProfile.IN_APP, "In-app applied");
            UnifiedAssistantFrameTest.awaitMode(f, AssistantMode.IN_APP);
            provider.replies.add(FakeProvider.action("{\"action\":\"read\",\"params\":{\"recordIndex\":7,\"count\":2}}"));
            provider.replies.add("## What happened in record #7\n"
                    + "Record **#7** is the log's one `RiskBreachEvent`: `breachHandler` logged `breachedOn: ord-2` "
                    + "with `liveOrders: 2`, its first breach today.\n\n"
                    + "- The cycle before it, record **#6**, is where `riskMonitor` saw `liveOrders` reach its `limit` of 2.\n"
                    + "- Nothing in these two records says why a third order went live; that is where to look next.");
            onEdt(() -> {
                a.composerArea().setText("What happened in record #7?");
                a.sendButton().doClick();
            });
            AssistantLiveFrameTest.awaitIdle(f);
            DocShot.window(f, "assistant-in-app.png");

            // 3. the records' right-click: the assistant's questions, labelled for the assistant in force
            JTable table = ((LogTablePanel) field(f, "tablePanel")).table();
            onEdt(() -> {
                Rectangle cell = table.getCellRect(table.getSelectedRow() < 0 ? 0 : table.getSelectedRow(), 1, true);
                table.scrollRectToVisible(cell);
                MouseEvent press = new MouseEvent(table, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                        cell.x + 40, cell.y + cell.height / 2, 1, true, MouseEvent.BUTTON3);
                for (var l : table.getMouseListeners()) l.mousePressed(press);
            });
            DocShot.withPopups(f, "ask-menu.png");
            onEdt(() -> javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath());

            // 4. Claude Code, Safe analysis: Start, the folder question, then a call that needs your approval
            onEdt(() -> a.profileButton().doClick());
            ProfileOverlayFrameTest.await(f, s -> s.open(), "the cards open again");
            onEdt(() -> {
                a.profileOverlay().card(AssistantProfile.SAFE).doClick();
                a.profileOverlay().applyButton().doClick();
            });
            ProfileOverlayFrameTest.await(f, s -> !s.open() && s.profile() == AssistantProfile.SAFE, "Safe applied");
            UnifiedAssistantFrameTest.awaitMode(f, AssistantMode.LOCAL_AGENT);
            LocalAgentPanel panel = (LocalAgentPanel) field(f, "localAgentPanel");
            onEdt(() -> ((JButton) field(panel, "start")).doClick());
            JDialog folder = ClaudeCodeTabFrameTest.awaitDialog(ClaudeCodeTabFrameTest.DIALOG_TITLE);
            onEdt(ClaudeCodeTabFrameTest.findButton(folder, LocalAgentFolderDialog.SAFE_TRUST)::doClick);
            ClaudeCodeTabFrameTest.awaitState(f, s -> "READY".equals(s.phase()), "READY");
            LocalAgentPermissionFrameTest.send(panel, "Write the breach on record #7 up as a finding, demo-breach-finding.pdf");
            LocalAgentPermissionDialog ask = LocalAgentPermissionFrameTest.awaitPermission();
            DocShot.window(ask, "permission-dialog.png");
            LocalAgentPermissionFrameTest.awaitEnabled(ask.allowButton());
            onEdt(() -> ask.allowButton().doClick());
            LocalAgentPermissionFrameTest.awaitTurnEnded(f, 1);
            LocalAgentPermissionFrameTest.awaitTranscript(panel, "approved by you");
            DocShot.window(f, "assistant-claude-code.png");

            // 5. the set-up proposal review: the real dialog over DEMO items (Claude's reasons are labelled unverified)
            FactRefs refs = new FactRefs();
            ProposalState proposal = new ProposalState("p-1", refs.put("DEMO quote service"), refs.put(
                    profile.getParent().getParent().toString()), "…/demo-quote-project", true, true, List.of(
                    item(refs, "p-1.1", "sourceRoot", "src/main/java", 0, "The processor and its nodes are here.",
                            "DIRECTORY (12 .java files)"),
                    item(refs, "p-1.2", "processor", null, refs.put("com.acme.demo.generated.DemoQuoteProcessor"),
                            "The generated processor the audit log names.", "RESOLVES (pairing UNKNOWN)"),
                    item(refs, "p-1.3", "graphml", "src/main/resources/demo-quote-processor.graphml", 0,
                            "The graph written beside the processor.", "READABLE (fit UNKNOWN until opened)"),
                    item(refs, "p-1.4", "runbook", "ops/restart-quote-service.md", 0,
                            "The restart procedure the team follows.", "MARKDOWN"),
                    item(refs, "p-1.5", "logToOpen", "logs/uat/quote-service-uat.yaml", 0,
                            "The most recent UAT audit log.", "READABLE (2048 bytes, not opened)")),
                    "NONE", false, "", "", Map.of());
            JDialog[] review = new JDialog[1];
            onEdt(() -> {
                review[0] = ProposalReviewDialog.dialog(f, proposal, refs::get, false, new ProposalReviewDialog.Answer[1]);
                // wider than the dialog's 760-pixel default, so the five rows' Yes / No answers are in the picture
                review[0].setSize(1120, review[0].getHeight() + 40);
                review[0].setLocationRelativeTo(f);
                review[0].setVisible(true);
            });
            DocShot.window(review[0], "proposal-review.png");
            onEdt(review[0]::dispose);

            // 6. Settings ▸ Assistant, grouped by profile
            onEdt(() -> SwingUtilities.invokeLater(() -> ConfigPanel.show(f, (AppConfig) field(f, "config"), () -> { },
                    null, "Assistant")));
            Window settings = awaitWindow(ConfigPanel.class);
            DocShot.window(settings, "settings-assistant.png");
            onEdt(settings::dispose);
        } finally {
            SwingUtilities.invokeAndWait(f::dispose);
        }
        System.exit(0);
    }

    private static ProposalState.Item item(FactRefs refs, String id, String kind, String path, long classRef,
                                           String reason, String verdict) {
        return new ProposalState.Item(id, kind, path == null ? 0 : refs.put(path), classRef, refs.put(reason),
                ProposalState.ItemState.PENDING, verdict, "");
    }

    private static void selectAssistantTab(MainFrame f) throws Exception {
        onEdt(() -> {
            JTabbedPane tabs = (JTabbedPane) field(f, "sideTabs");
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (tabs.getTitleAt(i).startsWith("Analyser assistant")) tabs.setSelectedIndex(i);
            }
        });
    }

    private static void awaitProject(MainFrame f) throws Exception {
        Thread.sleep(1500);
    }

    private static void awaitRecords(MainFrame f) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < until) {
            int[] rows = new int[1];
            onEdt(() -> rows[0] = ((LogTablePanel) field(f, "tablePanel")).table().getRowCount());
            if (rows[0] > 0) return;
            Thread.sleep(100);
        }
        throw new IllegalStateException("the demo log did not load");
    }

    private static Window awaitWindow(Class<?> type) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until) {
            for (Window w : Window.getWindows()) if (type.isInstance(w) && w.isShowing()) return w;
            Thread.sleep(50);
        }
        throw new IllegalStateException("no " + type.getSimpleName() + " showed");
    }
}
